/*
 *     This file is part of Telekram (Telegram MTProto client library)
 *     Copyright (C) 2020 Hackintosh Five
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

@file:Suppress("MemberVisibilityCanBePrivate", "EXPERIMENTAL_API_USAGE") // TODO remove experimental suppression when StateFlow is non-experimental (it is already safe to consume, just not to subclass - https://github.com/Kotlin/kotlinx.coroutines/issues/1973#issuecomment-658293490)

package dev.hack5.telekram.core.client

import com.github.aakira.napier.Napier
import com.soywiz.krypto.SecureRandom
import dev.hack5.telekram.core.auth.PasswordAuthenticator
import dev.hack5.telekram.core.auth.authenticate
import dev.hack5.telekram.core.connection.Connection
import dev.hack5.telekram.core.connection.ConnectionException
import dev.hack5.telekram.core.connection.TcpFullConnection
import dev.hack5.telekram.core.crypto.doPBKDF2SHA512Iter100000
import dev.hack5.telekram.core.encoder.EncryptedMTProtoEncoder
import dev.hack5.telekram.core.encoder.MTProtoEncoder
import dev.hack5.telekram.core.encoder.MTProtoEncoderWrapped
import dev.hack5.telekram.core.encoder.PlaintextMTProtoEncoder
import dev.hack5.telekram.core.errors.BadRequestError
import dev.hack5.telekram.core.errors.RedirectedError
import dev.hack5.telekram.core.errors.RpcError
import dev.hack5.telekram.core.mtproto.PingRequest
import dev.hack5.telekram.core.mtproto.RpcErrorObject
import dev.hack5.telekram.core.packer.MessagePackerUnpacker
import dev.hack5.telekram.core.packer.MessagePackerUnpackerImpl
import dev.hack5.telekram.core.state.*
import dev.hack5.telekram.core.tl.*
import dev.hack5.telekram.core.updates.*
import dev.hack5.telekram.core.updates.PeerType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlin.random.Random

private const val tag = "TelegramClient"

interface TelegramClient {
    var scope: CoroutineScope?
    var connectionScope: CoroutineScope?
    val packer: MessagePackerUnpacker?

    suspend fun connect()
    suspend fun reconnect()
    suspend fun disconnect()

    suspend operator fun <N, R : TLObject<N>> invoke(
        request: TLMethod<R>,
        skipEntities: Boolean = false,
        skipUpdates: Boolean = false,
        packer: (suspend (TLMethod<*>) -> TLObject<*>)? = null
    ): N

    suspend fun <R : TLObject<*>>sendUpdate(request: TLMethod<R>?, update: R)

    suspend fun <R : TLObject<*>> send(request: TLMethod<R>, encoder: MTProtoEncoder): R

    suspend fun start(
        phoneNumber: () -> String,
        signUpConsent: (Help_TermsOfServiceObject?) -> Pair<String, String>? = { null },
        phoneCode: () -> String,
        password: () -> CharArray
    ): Pair<Boolean?, UserType>

    suspend fun getMe(): UserObject
    suspend fun getInputMe(): InputUserObject

    suspend fun getAccessHash(constructor: PeerType, peerId: Int): Long?
    suspend fun getAccessHash(constructor: PeerType, peerId: Long): Long?
    var updateCallbacks: List<suspend (UpdateOrSkipped) -> Unit>
    suspend fun catchUp()

    val serverConfig: StateFlow<ConfigObject?>

    suspend fun exportSession(newSession: Session<*>, untrusted: Boolean): TelegramClientCoreImpl
}

open class TelegramClientCoreImpl(
    protected val apiId: String, protected val apiHash: String,
    protected val parentScope: CoroutineScope = GlobalScope,
    protected val connectionConstructor: (CoroutineScope, String, Int) -> Connection = ::TcpFullConnection,
    protected val plaintextEncoderConstructor: (CoroutineScope) -> MTProtoEncoder = {
        PlaintextMTProtoEncoder(
            MTProtoStateImpl()
        ).apply { state.scope = it }
    },
    protected val encryptedEncoderConstructor: (MTProtoState, CoroutineScope) -> EncryptedMTProtoEncoder = { state, scope ->
        EncryptedMTProtoEncoder(state, scope)
    },
    protected val updateHandlerConstructor: (CoroutineScope, UpdateState, TelegramClient) -> UpdateHandler? = { scope, state, client ->
        UpdateHandlerImpl(
            scope,
            state,
            client
        )
    },
    protected val packerConstructor: (
        Connection,
        MTProtoEncoderWrapped,
        MTProtoState,
        Channel<UpdatesType>,
        CoroutineScope
    ) -> MessagePackerUnpacker = ::MessagePackerUnpackerImpl,
    protected val deviceModel: String = "ktg",
    protected val systemVersion: String = "0.0.1",
    protected val appVersion: String = "0.0.1",
    protected val systemLangCode: String = "en",
    protected val langPack: String = "",
    protected val langCode: String = "en",
    protected var session: Session<*> = MemorySession(),
    protected val maxFloodWait: Long = 0
) : TelegramClient {
    var secureRandom = SecureRandom()
    protected var connection: Connection? = null
    protected var encoder: EncryptedMTProtoEncoder? = null
    override var packer: MessagePackerUnpacker? = null
    protected val mutableServerConfig = MutableStateFlow<ConfigObject?>(null)

    @ExperimentalCoroutinesApi
    override val serverConfig: StateFlow<ConfigObject?>
        get() = mutableServerConfig

    protected val updatesChannel = Channel<UpdatesType>(Channel.UNLIMITED)
    protected lateinit var inputUserSelf: InputUserObject
    protected var updatesHandler: UpdateHandler? = null
    override var scope: CoroutineScope? = parentScope + SupervisorJob(parentScope.coroutineContext[Job])
    override var connectionScope: CoroutineScope? = null

    override var updateCallbacks = listOf<suspend (UpdateOrSkipped) -> Unit>()

    suspend fun init() {
        session.state?.scope = scope!!
        session.updates?.let {
            updatesHandler = updateHandlerConstructor(scope!!, it, this)
        }
    }

    override suspend fun connect() {
        connectionScope = parentScope + SupervisorJob(parentScope.coroutineContext[Job])
        connectionConstructor(connectionScope!!, session.ipAddress, session.port).let {
            it.connect()
            this@TelegramClientCoreImpl.connection = it
            // TODO refactor this
            if (session.state?.authKey == null)
                session = session.setState(
                    MTProtoStateImpl(
                        authenticate(
                            this@TelegramClientCoreImpl,
                            plaintextEncoderConstructor(scope!!),
                            secureRandom
                        )
                    ).also { state ->
                        state.scope = scope!!
                        encoder = encryptedEncoderConstructor(state, scope!!)
                        packer = packerConstructor(it, encoder!!, state, updatesChannel, scope!!)
                    })
            else if (encoder == null) {
                encoder = encryptedEncoderConstructor(session.state!!, scope!!)
                packer = packerConstructor(it, encoder!!, session.state!!, updatesChannel, scope!!)
            } else {
                packer!!.resetConnection(it)
            }

            startRecvLoop()

            mutableServerConfig.value = this(
                InvokeWithLayerRequest(
                    113,
                    InitConnectionRequest(
                        apiId.toInt(),
                        deviceModel,
                        systemVersion,
                        appVersion,
                        systemLangCode,
                        langPack,
                        langCode,
                        null,
                        null,
                        Help_GetConfigRequest()
                    )
                )
            ) as ConfigObject
            Napier.d(serverConfig.value.toString())
        }
    }

    override suspend fun disconnect() {
        session.save()
        connection?.disconnect()
        connection = null
        connectionScope?.coroutineContext?.get(Job)?.cancelAndJoin()
    }

    override suspend fun reconnect() {
        connection?.disconnect()
        connectionScope?.coroutineContext?.get(Job)?.cancelAndJoin()
        connect()
    }

    override suspend fun start(
        phoneNumber: () -> String,
        signUpConsent: (Help_TermsOfServiceObject?) -> Pair<String, String>?,
        phoneCode: () -> String,
        password: () -> CharArray
    ): Pair<Boolean?, UserType> {
        val (loggedIn, ret) = logIn(phoneNumber, signUpConsent, phoneCode, password)
        updatesHandler = if (session.updates == null) {
            val state = (this(Updates_GetStateRequest()) as Updates_StateObject)
            session =
                session.setUpdateState(UpdateState(state.seq, state.date, state.qts, mutableMapOf(null to state.pts)))
            session.updates?.let {
                updateHandlerConstructor(scope!!, it, this)
            }?.also {
                // TODO cleanup
                it.updates.send(Skipped(null) { }) // always send global skipped on session init
            }
        } else {
            session.updates?.let {
                updateHandlerConstructor(scope!!, it, this)
            }
        }
        startUpdateLoop()
        scope!!.launch {
            while (true) {
                coroutineScope {
                    val update = updatesHandler!!.updates.receive()
                    this@TelegramClientCoreImpl.updateCallbacks.forEach {
                        launch { it(update) }
                    }
                }
            }
        }
        scope!!.launch {
            var i = 0
            while (true) {
                delay(10000)
                session.save()
                withTimeoutOrNull(30000) {
                    invoke(PingRequest(Random.nextLong()))
                } ?: scope!!.launch(NonCancellable) { reconnect() }
                i = (i + 1) % 6
                if (i == 0) {
                    updatesHandler?.catchUp()
                }
            }
        }
        return loggedIn to ret
    }

    protected suspend fun logIn(
        phoneNumber: () -> String,
        signUpConsent: (Help_TermsOfServiceObject?) -> Pair<String, String>?,
        phoneCode: () -> String,
        password: () -> CharArray
    ): Pair<Boolean?, UserType> {
        if (connection?.connectionState?.value != true)
            connect()
        try {
            return null to getMe()
        } catch (e: BadRequestError.AuthKeyUnregisteredError) {
            Napier.v("Beginning sign-in", e, tag = tag)
        }
        val phone = phoneNumber()
        val sentCode =
            try {
                this(
                    Auth_SendCodeRequest(
                        phone, apiId.toInt(), apiHash, CodeSettingsObject(
                            allowFlashcall = false,
                            currentNumber = false,
                            allowAppHash = false
                        )
                    )
                ) as Auth_SentCodeObject
            } catch (e: RedirectedError.PhoneMigrateError) {
                Napier.d("Phone migrated to ${e.dc}", tag = tag)
                disconnect()
                val newDcOptions = serverConfig.value!!.dcOptions.map { it as DcOptionObject }.filter { it.id == e.dc && !it.cdn && !it.mediaOnly }
                for (newDc in newDcOptions) {
                    session = session.setDc(e.dc, newDc.ipAddress, newDc.port).setState(null)
                    withTimeoutOrNull(5000) {
                        connect()
                    } ?: continue
                    return start({ phone }, signUpConsent, phoneCode, password)
                }
                error("All applicable connections failed")
            }
        val auth = try {
            this(Auth_SignInRequest(phone, sentCode.phoneCodeHash, phoneCode()))
        } catch (e: BadRequestError.SessionPasswordNeededError) {
            val sessionPassword = password()
            val pwd = this(Account_GetPasswordRequest()) as Account_PasswordObject
            val auth = PasswordAuthenticator(::doPBKDF2SHA512Iter100000)
            val srp = when (pwd.currentAlgo) {
                is PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPowObject -> {
                    auth.hashPasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow(
                        sessionPassword,
                        pwd,
                        pwd.currentAlgo,
                        secureRandom
                    )
                }
                is PasswordKdfAlgoUnknownObject -> TODO()
                null -> TODO()
            }
            this(Auth_CheckPasswordRequest(srp))
        }
        when (auth) {
            is Auth_AuthorizationSignUpRequiredObject -> {
                val name = signUpConsent(auth.termsOfService as Help_TermsOfServiceObject)
                require(name != null) { "Terms of Service were not accepted" }
                when (val newAuth = this(Auth_SignUpRequest(phone, sentCode.phoneCodeHash, name.first, name.second))) {
                    is Auth_AuthorizationObject -> {
                        session.save()
                        return true to newAuth.user
                    }
                    else -> error("Signup failed (unknown $newAuth)")
                }
            }
            is Auth_AuthorizationObject -> {
                session.save()
                return false to auth.user
            }
            else -> error("Login failed (unknown $auth)")
        }
    }

    override suspend fun getMe(): UserObject {
        return (this(Users_GetUsersRequest(listOf(InputUserSelfObject()))).single() as UserObject).also {
            inputUserSelf = InputUserObject(it.id, it.accessHash!!)
        }
    }

    override suspend fun getInputMe(): InputUserObject {
        if (::inputUserSelf.isInitialized)
            return inputUserSelf
        getMe()
        return inputUserSelf
    }

    override suspend fun getAccessHash(constructor: PeerType, peerId: Int): Long? =
        getAccessHash(constructor, peerId.toLong())

    override suspend fun getAccessHash(constructor: PeerType, peerId: Long): Long? =
        session.entities[constructor.name]?.get(peerId)

    override suspend fun catchUp() = updatesHandler!!.catchUp()

    protected suspend fun startRecvLoop() {
        val byteChannel = Channel<ByteArray>()
        connectionScope!!.launch {
            connection!!.connectionState.filter { it == false }.collect {
                scope!!.launch(NonCancellable) {
                    reconnect()
                }
            }
        }
        connectionScope!!.launch {
            try {
                connection!!.recvLoop(byteChannel)
            } catch (e: ConnectionException) {
                /* no-op */
            }
        }
        connectionScope!!.launch {
            packer!!.pump(byteChannel)
        }
    }

    protected suspend fun startUpdateLoop() {
        scope!!.launch {
            while (true)
                try {
                    packer!!.updatesChannel.receive().let {
                        updatesHandler?.getEntities(it)?.let { entities ->
                            session.addEntities(entities)
                        }
                        updatesHandler?.handleUpdates(null, it)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException)
                        throw e
                    Napier.e("Error in update receiver", e, tag = tag)
                    // TODO possibly send a notification about missing updates, or catchUp()
                }
        }
    }

    override suspend fun <R : TLObject<*>>sendUpdate(request: TLMethod<R>?, update: R) {
        updatesHandler?.handleUpdates(request, update)
    }

    override suspend fun exportSession(newSession: Session<*>, untrusted: Boolean): TelegramClientCoreImpl {
        val new = TelegramClientCoreImpl(
            apiId,
            apiHash,
            parentScope,
            connectionConstructor,
            plaintextEncoderConstructor,
            encryptedEncoderConstructor,
            { _, _, _ -> null },
            packerConstructor,
            if (untrusted) "" else deviceModel,
            if (untrusted) "" else systemVersion,
            if (untrusted) "" else appVersion,
            if (untrusted) "" else systemLangCode,
            if (untrusted) "" else langPack,
            if (untrusted) "" else langCode,
            newSession,
            maxFloodWait
        )
        new.connect()
        return new
    }

    override suspend fun <R : TLObject<*>> send(request: TLMethod<R>, encoder: MTProtoEncoder): R {
        Napier.d(request.toString(), tag = tag)
        connection!!.send(encoder.encode(request.toTlRepr().toByteArray()))
        val response = encoder.decode(connection!!.recv()).toIntArray()
        return request.constructor.fromTlRepr(response)!!.second
    }

    protected suspend inline fun <R : TLObject<*>> sendWrapped(
        request: TLMethod<R>,
        crossinline sendAndRecv: suspend (TLMethod<*>) -> TLObject<*>
    ): R {
        val ret = sendAndRecv(request)
        if (ret is RpcErrorObject)
            throw RpcError(ret.errorCode, ret.errorMessage, request)
        @Suppress("UNCHECKED_CAST")
        return ret as R
    }

    // careful when changing signature, must update the recursion to preserve parameters
    protected suspend fun <R : TLObject<*>> sendAndUnpack(
        request: TLMethod<R>,
        skipEntities: Boolean,
        skipUpdates: Boolean,
        packer: (suspend (TLMethod<*>) -> TLObject<*>)?
    ): R {
        Napier.d(request.toString(), tag = tag)
        val ret: R = try {
            sendWrapped(request, packer ?: this.packer!!::sendAndRecv)
        } catch (e: BadRequestError.FloodWaitError) {
            val ms = if (e.seconds == 0) 500 else e.seconds * 1000
            if (ms > maxFloodWait) throw e
            delay(ms.toLong())
            sendAndUnpack(request, skipEntities, skipUpdates, packer)
        }
        if (!skipEntities) {
            updatesHandler?.getEntities(ret)?.let {
                session.addEntities(it)
            }
        }
        if (!skipUpdates) {
            updatesHandler?.handleUpdates(request, ret)
        }
        Napier.d(ret.toString(), tag = tag)
        return ret
    }

    override suspend operator fun <N, R : TLObject<N>> invoke(
        request: TLMethod<R>,
        skipEntities: Boolean,
        skipUpdates: Boolean,
        packer: (suspend (TLMethod<*>) -> TLObject<*>)?
    ): N {
        return sendAndUnpack(request, skipEntities, skipUpdates, packer).native
    }
}