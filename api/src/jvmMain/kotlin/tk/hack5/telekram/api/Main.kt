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

package tk.hack5.telekram.api

import com.github.aakira.napier.DebugAntilog
import com.github.aakira.napier.Napier
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.single
import tk.hack5.telekram.core.mtproto.PingRequest
import tk.hack5.telekram.core.state.JsonSession
import tk.hack5.telekram.core.state.invoke
import tk.hack5.telekram.core.tl.*
import tk.hack5.telekram.core.updates.Update
import tk.hack5.telekram.core.utils.toInputChannel
import tk.hack5.telekram.core.utils.toInputPeer
import java.io.File
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

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
            session = JsonSession(File("telekram.json")) /*.setDc(2, "149.154.167.40", 443)*/,
            maxFloodWait = 15,
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
    client.eventCallbacks += {
        try {
            println(it)
            when (it) {
                is NewMessage.NewMessageEvent -> {
                    if (it.out) {
                        if (it.message == ".ping") {
                            var update: UpdatesType? = null
                            val time = measureNanoTime {
                                update = client(Messages_EditMessageRequest(false, it.getInputChat(), it.id, "Pong"))
                                /*for (i in 0 until 100)
                                    client(PingRequest(i.toLong()))*/
                            }
                            println(update)
                            val dispatchTime = measureNanoTime {
                                client.sendUpdate(update!!)
                            }
                            update =
                                client(
                                    Messages_EditMessageRequest(
                                        false,
                                        it.getInputChat(),
                                        it.id,
                                        "Pong\nRTT=${time}ns\nDispatch=${dispatchTime}ns"
                                    )
                                )
                            client.sendUpdate(update!!)
                        }
                    }

                    if (it.toId is PeerChannelObject) {
                        client(Channels_ReadHistoryRequest(it.toId.toInputChannel(client), it.id))
                    } else {
                        client(Messages_ReadHistoryRequest(it.getInputChat(), it.id))
                    }

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
                        client.getDialogs().flatMapMerge {
                            when (it) {
                                is DialogChat -> TODO()
                                is DialogFolder -> {
                                    client.getDialogs(folderId = it.folder.id)
                                }
                            }
                        }
                    }
                }
                is EditMessage.EditMessageEvent -> {
                }
                is RawUpdate.RawUpdateEvent -> {
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("error", e)
        }
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