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

@file:Suppress("MemberVisibilityCanBePrivate")

package tk.hack5.telekram.core.client

import com.github.aakira.napier.Napier
import com.soywiz.krypto.SecureRandom
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import tk.hack5.telekram.core.auth.PasswordAuthenticator
import tk.hack5.telekram.core.auth.authenticate
import tk.hack5.telekram.core.connection.Connection
import tk.hack5.telekram.core.connection.TcpFullConnection
import tk.hack5.telekram.core.crypto.doPBKDF2SHA512Iter100000
import tk.hack5.telekram.core.encoder.EncryptedMTProtoEncoder
import tk.hack5.telekram.core.encoder.MTProtoEncoder
import tk.hack5.telekram.core.encoder.MTProtoEncoderWrapped
import tk.hack5.telekram.core.encoder.PlaintextMTProtoEncoder
import tk.hack5.telekram.core.errors.BadRequestError
import tk.hack5.telekram.core.errors.RedirectedError
import tk.hack5.telekram.core.errors.RpcError
import tk.hack5.telekram.core.mtproto.PingRequest
import tk.hack5.telekram.core.mtproto.RpcErrorObject
import tk.hack5.telekram.core.packer.MessagePackerUnpacker
import tk.hack5.telekram.core.state.*
import tk.hack5.telekram.core.tl.*
import tk.hack5.telekram.core.updates.PeerType
import tk.hack5.telekram.core.updates.UpdateHandler
import tk.hack5.telekram.core.updates.UpdateHandlerImpl
import tk.hack5.telekram.core.updates.UpdateOrSkipped
import kotlin.random.Random

private const val tag = "TelegramClient"

abstract class TelegramClient {
    internal abstract val secureRandom: SecureRandom
    abstract suspend fun connect()

    internal abstract suspend fun <R : TLObject<*>> send(request: TLMethod<R>, encoder: MTProtoEncoder): R
    protected abstract suspend fun <R : TLObject<*>> sendWrapped(
        request: TLMethod<R>,
        encoder: MTProtoEncoderWrapped
    ): R

    abstract suspend operator fun <N, R : TLObject<N>> invoke(request: TLMethod<R>, skipEntities: Boolean = false, forUpdate: Boolean = false): N
    abstract suspend fun start(
        phoneNumber: () -> String,
        signUpConsent: (Help_TermsOfServiceObject?) -> Pair<String, String>? = { null },
        phoneCode: () -> String,
        password: () -> CharArray
    ): Pair<Boolean?, UserType>

    abstract suspend fun disconnect()

    abstract suspend fun getMe(): UserObject
    abstract suspend fun getInputMe(): InputUserObject

    abstract suspend fun getAccessHash(constructor: PeerType, peerId: Int): Long?
    abstract suspend fun getAccessHash(constructor: PeerType, peerId: Long): Long?
    abstract var updateCallbacks: List<suspend (UpdateOrSkipped) -> Unit>
    abstract suspend fun catchUp()
    abstract suspend fun sendUpdate(update: UpdatesType)
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
    protected val encryptedEncoderConstructor: (MTProtoState) -> EncryptedMTProtoEncoder = { EncryptedMTProtoEncoder(it) },
    protected val deviceModel: String = "ktg",
    protected val systemVersion: String = "0.0.1",
    protected val appVersion: String = "0.0.1",
    protected val systemLangCode: String = "en",
    protected val langPack: String = "",
    protected val langCode: String = "en",
    protected var session: Session<*> = MemorySession(),
    protected val maxFloodWait: Int = 0
) : TelegramClient() {
    override var secureRandom = SecureRandom()
    protected var connection: Connection? = null
    protected var encoder: EncryptedMTProtoEncoder? = null
    protected var unpacker: MessagePackerUnpacker? = null
    protected var serverConfig: ConfigObject? = null
    protected val updatesChannel = Channel<UpdatesType>(Channel.UNLIMITED)
    protected val resultsChannel = Channel<TLObject<*>>(Channel.UNLIMITED)
    protected lateinit var inputUserSelf: InputUserObject
    protected var updatesHandler: UpdateHandler? = null
    lateinit var scope: CoroutineScope

    override var updateCallbacks = listOf<suspend (UpdateOrSkipped) -> Unit>()

    override suspend fun connect() {
        scope = parentScope + SupervisorJob(parentScope.coroutineContext[Job])
        session.state?.scope = scope
        session.updates?.let {
            updatesHandler = UpdateHandlerImpl(scope, it, this)
        }
        connectionConstructor(scope, session.ipAddress, session.port).let {
            it.connect()
            this@TelegramClientCoreImpl.connection = it
            if (session.state?.authKey == null)
                session = session.setState(
                    MTProtoStateImpl(
                        authenticate(
                            this@TelegramClientCoreImpl,
                            plaintextEncoderConstructor(scope)
                        )
                    ).also { state ->
                        state.scope = scope
                        encoder = encryptedEncoderConstructor(state)
                        unpacker = MessagePackerUnpacker(it, encoder!!, state, updatesChannel, resultsChannel)
                    })
            else {
                encoder = encryptedEncoderConstructor(session.state!!)
                unpacker = MessagePackerUnpacker(it, encoder!!, session.state!!, updatesChannel, resultsChannel)
            }

            startRecvLoop()

            Napier.d(this(Help_GetNearestDcRequest()).toString()) // First request has to be an unchanged request from the first layer
            serverConfig = this(
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
            Napier.d(serverConfig.toString())
        }
    }

    override suspend fun disconnect() {
        session.save()
        connection?.disconnect()
        connection = null
        scope.coroutineContext[Job]!!.cancelAndJoin()
        updatesHandler?.updates?.close()
    }

    override suspend fun start(
        phoneNumber: () -> String,
        signUpConsent: (Help_TermsOfServiceObject?) -> Pair<String, String>?,
        phoneCode: () -> String,
        password: () -> CharArray
    ): Pair<Boolean?, UserType> {
        val (loggedIn, ret) = logIn(phoneNumber, signUpConsent, phoneCode, password)
        val state = (this(Updates_GetStateRequest()) as Updates_StateObject)
        if (session.updates == null) {
            session =
                session.setUpdateState(UpdateState(state.seq, state.date, state.qts, mutableMapOf(null to state.pts)))
        }
        updatesHandler = UpdateHandlerImpl(scope, session.updates!!, this)
        startUpdateLoop()
        scope.launch {
            while (true) {
                coroutineScope {
                    val update = updatesHandler!!.updates.receive()
                    this@TelegramClientCoreImpl.updateCallbacks.forEach {
                        launch { it(update) }
                    }
                }
            }
        }
        scope.launch {
            var i = 0
            while (true) {
                delay(30000)
                invoke(PingRequest(Random.nextLong()))
                session.save()
                i = (i + 1) % 60
                if (i == 0) {
                    updatesHandler?.catchUp()
                }
            }
        }
        return loggedIn to ret
    }

    override suspend fun sendUpdate(update: UpdatesType) {
        scope.launch {
            updatesHandler!!.handleUpdates(update)
        }
    }

    protected suspend fun logIn(
        phoneNumber: () -> String,
        signUpConsent: (Help_TermsOfServiceObject?) -> Pair<String, String>?,
        phoneCode: () -> String,
        password: () -> CharArray
    ): Pair<Boolean?, UserType> {
        if (connection?.connected != true)
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
                val newDc = serverConfig!!.dcOptions.map { it as DcOptionObject }.filter { it.id == e.dc }.random()
                session = session.setDc(e.dc, newDc.ipAddress, newDc.port).setState(null)
                return start({ phone }, signUpConsent, phoneCode, password)
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
        scope.launch {
            connection!!.recvLoop(byteChannel)
        }
        scope.launch {
            unpacker!!.pump(byteChannel)
        }
    }

    protected suspend fun startUpdateLoop() {
        scope.launch {
            while (true)
                try {
                    unpacker!!.updatesChannel.receive().let {
                        updatesHandler?.getEntities(it, true)?.let { entities ->
                            session.addEntities(entities)
                        }
                        updatesHandler?.handleUpdates(it)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException)
                        throw e
                    Napier.e("Error in update receiver", e, tag = tag)
                    // TODO possibly send a notification about missing updates
                }
        }
    }

    override suspend fun <R : TLObject<*>> send(request: TLMethod<R>, encoder: MTProtoEncoder): R {
        Napier.d(request.toString(), tag = tag)
        connection!!.send(encoder.encode(request.toTlRepr().toByteArray()))
        val response = encoder.decode(connection!!.recv()).toIntArray()
        return request.constructor.fromTlRepr(response)!!.second
    }

    override suspend fun <R : TLObject<*>> sendWrapped(request: TLMethod<R>, encoder: MTProtoEncoderWrapped): R {
        Napier.d(request.toString(), tag = tag)
        val ret = unpacker!!.sendAndRecv(request)
        if (ret is RpcErrorObject)
            throw RpcError(ret.errorCode, ret.errorMessage, request)
        @Suppress("UNCHECKED_CAST")
        return ret as R
    }

    suspend fun <R : TLObject<*>> sendAndUnpack(request: TLMethod<R>, skipEntities: Boolean = false, forUpdate: Boolean = false): R {
        val ret: R = try {
            sendWrapped(request, encoder!!)
        } catch (e: BadRequestError.FloodWaitError) {
            val seconds = if (e.seconds == 0) 1 else e.seconds
            if (seconds > maxFloodWait) throw e
            delay(seconds.toLong())
            sendAndUnpack(request)
        }
        Napier.d("Got response $ret", tag = tag)
        if (!skipEntities) {
            updatesHandler?.getEntities(ret, forUpdate)?.let {
                session.addEntities(it)
            }
        }
        return ret
    }

    override suspend operator fun <N, R : TLObject<N>> invoke(request: TLMethod<R>, skipEntities: Boolean, forUpdate: Boolean): N {
        return sendAndUnpack(request, forUpdate).native
    }
}