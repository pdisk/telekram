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

package dev.hack5.telekram.sample

import com.github.aakira.napier.DebugAntilog
import com.github.aakira.napier.Napier
import dev.hack5.telekram.api.*
import dev.hack5.telekram.core.client.GroupingTelegramClient
import dev.hack5.telekram.core.client.TelegramClient
import dev.hack5.telekram.core.mtproto.PingRequest
import dev.hack5.telekram.core.state.JsonSession
import dev.hack5.telekram.core.state.invoke
import dev.hack5.telekram.core.tl.*
import dev.hack5.telekram.core.utils.toInputChannel
import dev.hack5.telekram.core.utils.toInputPeer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import javax.script.ScriptContext
import javax.script.ScriptEngineManager
import kotlin.system.measureNanoTime

@FlowPreview
@ExperimentalCoroutinesApi
fun main(): Unit = runBlocking {
    //DebugProbes.install()
    //System.setProperty("java.util.logging.SimpleFormatter.format", "[%1\$tT.%1\$tL] [%4$-7s] %5\$s %n")
    Napier.base(DebugAntilog())
    val (apiId, apiHash) = File("apiToken").readLines()
    val client =
        TelegramClientApiImpl(
            apiId,
            apiHash,
            deviceModel = "Linux",
            systemVersion = "5.8.15-201.fc32.x86_64",
            appVersion = "1.16.0",
            session = JsonSession(File("telekram.json")).setDc(2, "149.154.167.40", 443),
            maxFloodWait = Long.MAX_VALUE,
            parentScope = this
        )
    client.init()
    /*client.updateCallbacks += { or ->
        or.update?.let {
            println(it)
            val peer = (((it as? UpdateNewChannelMessageObject)?.message as? MessageObject)?.toId as? PeerChannelObject)
            if (peer?.channelId == 1330858993 && (it.message as? MessageObject)?.fromId != client.getInputMe().userId)
                client.sendMessage(
                    peer.toInputPeer(client)!!,
                    ((it.message as MessageObject).message.toInt() + 1).toString()
                )
            if (peer?.channelId == 1392066769)
                client.sendMessage(
                    peer.toInputPeer(client)!!,
                    "testing! sorry for any spam i do, its automated and i can't stop it for 60 seconds"
                )

        }
    }*/
    val pendingEdits = mutableMapOf<Pair<PeerType, Int>, CompletableDeferred<EditMessage.EditMessageEvent>>()
    client.eventCallbacks += { event ->
        launch {
            try {
                when (event) {
                    is NewMessage.NewMessageEvent -> {
                        (event.message as? MessageObject)?.let {
                            if (it.out) {
                                if (it.message == ".ping") {
                                    var update: UpdatesType?
                                    val editTime = measureNanoTime {
                                        update =
                                            it.edit(client, message = "Pong")
                                    }
                                    val groupedClient = GroupingTelegramClient(client, initialCapacity = 1000)
                                    val pingTime = measureNanoTime {
                                        coroutineScope {
                                            for (i in 0L until 1000L) {
                                                launch {
                                                    groupedClient(PingRequest(i))
                                                }
                                            }
                                        }
                                    }
                                    println(update)
                                    val job = CompletableDeferred<EditMessage.EditMessageEvent>()
                                    pendingEdits[it.toId to it.id] = job
                                    val dispatchTime = measureNanoTime {
                                        client.sendUpdate(update!!)
                                    }
                                    val edit = job.await()
                                    val serverTime = edit.message.editDate!! - it.date
                                    update =
                                        client(
                                            Messages_EditMessageRequest(
                                                false,
                                                event.getInputChat()!!,
                                                it.id,
                                                "Pong\nERTT=${editTime}ns\nSRTT=${serverTime}s\nDT=${dispatchTime}ns\nPRTT=${pingTime}ns"
                                            )
                                        )
                                    client.sendUpdate(update!!)
                                }
                                if (it.message == ".download") {
                                    when (val resp = client(Contacts_ResolveUsernameRequest("blank_x"))) {
                                        is Contacts_ResolvedPeerObject -> {
                                            val file = File("test.jpg")
                                            (resp.users[0] as UserObject).downloadProfilePhoto(client).collect { data ->
                                                file.writeBytes(data)
                                            }
                                        }
                                    }
                                }
                                if (it.message.startsWith(".eval")) {
                                    val engine = ScriptEngineManager().getEngineByExtension("kts")!!
                                    val bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE)
                                    bindings["bootstrap"] =
                                        EvalDataWrapper(this + SupervisorJob(coroutineContext[Job]), client, event)
                                    engine.context.errorWriter = System.err.writer()
                                    engine.context.writer = System.err.writer()
                                    engine.context.reader = System.`in`.reader()
                                    val input = it.message.substringAfter(" ")
                                    val (header, body) = input.split("###", limit = 2)
                                    val code = """
                                    $header
                                    bootstrap.wrapAsync { client, event ->
                                    $body
                                    }
                                """.trimIndent()
                                    try {
                                        val deferred = engine.eval(code) as Deferred<*>
                                        val output = deferred.await()
                                        val update =
                                            client(
                                                Messages_EditMessageRequest(
                                                    false,
                                                    event.getInputChat()!!,
                                                    it.id,
                                                    "Input: $input\nOutput: $output"
                                                )
                                            )
                                        client.sendUpdate(update)
                                    } catch (e: Throwable) {
                                        val update =
                                            client(
                                                Messages_EditMessageRequest(
                                                    false,
                                                    event.getInputChat()!!,
                                                    it.id,
                                                    "Input: $input\nError: ${e.stackTraceToString()}"
                                                )
                                            )
                                        client.sendUpdate(update)
                                    }
                                }
                            }

                            if (!it.out) {
                                it.toId.let { toId ->
                                    if (toId is PeerChannelObject) {
                                        client(Channels_ReadHistoryRequest(toId.toInputChannel(client), it.id))
                                    } else {
                                        client(Messages_ReadHistoryRequest(event.getInputChat()!!, it.id))
                                    }
                                }
                            }
                        }
                    }
                    is EditMessage.EditMessageEvent -> {
                        pendingEdits.remove(event.chatPeer to event.message.id)?.complete(event)
                    }
                    is SkippedUpdate.SkippedUpdateEvent -> {
                        event.channelId.let { channelId ->
                            if (channelId != null) {
                                client(
                                    Channels_ReadHistoryRequest(
                                        PeerChannelObject(channelId).toInputChannel(client),
                                        client.getMessages(
                                            PeerChannelObject(channelId).toInputPeer(client),
                                            limit = 1
                                        )
                                            .single().id
                                    )
                                )
                            } else {
                                client.getDialogs().flatMapMerge { dialog ->
                                    when (dialog) {
                                        is DialogChat -> flowOf(dialog)
                                        is DialogFolder -> {
                                            client.getDialogs(folderId = dialog.folder.id)
                                                .filterIsInstance/*<DialogChat>*/()
                                        }
                                    }
                                }.filter {
                                    it.dialog.unreadCount > 0
                                }.collect {
                                    client(
                                        Messages_ReadHistoryRequest(
                                            it.inputPeer,
                                            client.getMessages(
                                                it.inputPeer,
                                                limit = 1
                                            )
                                                .single().id
                                        )
                                    )
                                }
                            }
                        }
                    }
                    is RawUpdate.RawUpdateEvent -> {
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Napier.e("error", e)
            } finally {
                event.originalUpdate?.commit()
            }
        }
        Unit
    }
    println(client.start(
        phoneNumber = {
            print("Phone Number: ")
            readLine()!!
        },
        signUpConsent = { Pair("test", "account") },
        phoneCode = {
            print("Enter the code you received: ")
            readLine()!!
        }, password = {
            print("Enter your password: ")
            System.console()?.readPassword() ?: readLine()!!.toCharArray()
        }
    ))
    client.catchUp()
    withContext(Dispatchers.IO) {
        readLine()
    }
    //delay(1800000)
    //DebugProbes.dumpCoroutines()
    //val dialogs = client.getDialogs()
    //println(dialogs.first())
    //client.sendMessage((dialogs.filter { (it as? DialogChat)?.peer?.fullName?.contains("Programmers") == true }.first() as DialogChat).peer, "hello from my new kotlin mtproto library")
    //println(client(Channels_GetChannelsRequest(listOf(InputChannelObject(channelId=1173753783, accessHash=-3214895137574953081)))))
    client.disconnect()
    Unit
}

@Suppress("unused") // used by kts
class EvalDataWrapper(
    private val scope: CoroutineScope,
    private val client: TelegramClient,
    private val event: NewMessage.NewMessageEvent
) {
    fun wrapAsync(block: suspend (TelegramClient, NewMessage.NewMessageEvent) -> Any?): Deferred<Any?> {
        return scope.async { block(client, event) }
    }
}