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

package dev.hack5.telekram.api

import com.github.aakira.napier.DebugAntilog
import com.github.aakira.napier.Napier
import dev.hack5.telekram.core.exports.exportDC
import dev.hack5.telekram.core.mtproto.PingRequest
import dev.hack5.telekram.core.state.JsonSession
import dev.hack5.telekram.core.state.invoke
import dev.hack5.telekram.core.tl.*
import dev.hack5.telekram.core.utils.toInputChannel
import dev.hack5.telekram.core.utils.toInputPeer
import dev.hack5.telekram.core.utils.toInputUser
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.single
import java.io.File
import kotlin.system.measureNanoTime

@FlowPreview
@ExperimentalCoroutinesApi
fun main(): Unit = runBlocking {
    DebugProbes.install()
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
            maxFloodWait = 15000,
            parentScope = this
        )
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
    client.eventCallbacks += {
        launch {
            try {
                it.originalUpdate?.commit()
                println(it)
                when (it) {
                    is NewMessage.NewMessageEvent -> {
                        if (it.out) {
                            if (it.message == ".ping") {
                                var update: UpdatesType? = null
                                val chat = it.getInputChat()
                                val editTime = measureNanoTime {
                                    update =
                                        client(Messages_EditMessageRequest(false, chat, it.id, "Pong"))
                                }
                                val pingTime = measureNanoTime {
                                    for (i in 0L until 100L)
                                        client(PingRequest(i))
                                }
                                println(update)
                                val job = CompletableDeferred<EditMessage.EditMessageEvent>()
                                pendingEdits[it.chatPeer to it.id] = job
                                val dispatchTime = measureNanoTime {
                                    client.sendUpdate(update!!)
                                }
                                val edit = job.await()
                                val serverTime = edit.editDate!! - it.date
                                update =
                                    client(
                                        Messages_EditMessageRequest(
                                            false,
                                            it.getInputChat(),
                                            it.id,
                                            "Pong\nERTT=${editTime}ns\nSRTT=${serverTime}s\nDT=${dispatchTime}ns\nPRTT=${pingTime}"
                                        )
                                    )
                                client.sendUpdate(update!!)
                            }
                            if (it.message == ".download") {
                                when (val resp = client(Contacts_ResolveUsernameRequest("blank_x"))) {
                                    is Contacts_ResolvedPeerObject -> {
                                        val file = File("test.jpg")
                                        (resp.users[0] as UserObject).downloadProfilePhoto(client).collect {
                                            file.writeBytes(it)
                                        }
                                    }
                                }
                            }
                        }

                        if (it.toId is PeerChannelObject) {
                            client(Channels_ReadHistoryRequest(it.toId.toInputChannel(client), it.id))
                        } else {
                            client(Messages_ReadHistoryRequest(it.getInputChat(), it.id))
                        }

                    }
                    is EditMessage.EditMessageEvent -> {
                        pendingEdits.remove(it.chatPeer to it.id)?.complete(it)
                    }
                    is SkippedUpdate.SkippedUpdateEvent -> {
                        if (it.channelId != null) {
                            client(
                                Channels_ReadHistoryRequest(
                                    PeerChannelObject(it.channelId).toInputChannel(client),
                                    client.getMessages(PeerChannelObject(it.channelId).toInputPeer(client), limit = 1)
                                        .single().id
                                )
                            )
                        } else {
                            client.getDialogs().flatMapMerge { dialog ->
                                when (dialog) {
                                    is DialogChat -> TODO()
                                    is DialogFolder -> {
                                        client.getDialogs(folderId = dialog.folder.id)
                                    }
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