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

package dev.hack5.telekram.core.packer

import com.github.aakira.napier.Napier
import dev.hack5.telekram.core.connection.Connection
import dev.hack5.telekram.core.encoder.MTProtoEncoderWrapped
import dev.hack5.telekram.core.mtproto.*
import dev.hack5.telekram.core.mtproto.MessageObject
import dev.hack5.telekram.core.state.MTProtoState
import dev.hack5.telekram.core.tl.*
import dev.hack5.telekram.core.utils.GZIPImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

private const val tag = "MessagePackerUnpacker"

interface MessagePackerUnpacker {
    suspend fun sendAndRecv(message: TLMethod<*>): TLObject<*>
    suspend fun wrap(message: TLMethod<*>): Pair<MessageObject, Long>
    suspend fun sendAndRecvContainer(messages: List<Pair<MessageObject, Long>>): List<Deferred<TLObject<*>>>
    suspend fun pump(input: Channel<ByteArray>)

    val updatesChannel: Channel<UpdatesType>
    val containerMaxMessages: Int
    val containerMaxSize: Int
}

class MessagePackerUnpackerImpl(
    private val connection: Connection,
    private val encoder: MTProtoEncoderWrapped,
    private val state: MTProtoState,
    override val updatesChannel: Channel<UpdatesType>,
    private val scope: CoroutineScope
) : MessagePackerUnpacker {
    private val pendingMessages: MutableMap<Long, CompletableDeferred<MessageUnpackAction>> = HashMap(5)

    override val containerMaxMessages
        get() = 1020
    override val containerMaxSize: Int
        get() = 1044456 - 8 // idek what these numbers are, ask lonami.

    override suspend fun sendAndRecv(message: TLMethod<*>): TLObject<*> {
        val encoded = encoder.wrapAndEncode(message)
        val deferred = CompletableDeferred<MessageUnpackAction>()
        pendingMessages[encoded.second] = deferred
        connection.send(encoded.first)
        return when (val action = deferred.await()) {
            is MessageUnpackActionRetry -> sendAndRecv(message)
            is MessageUnpackActionReturn -> action.value
        }
    }

    override suspend fun wrap(message: TLMethod<*>): Pair<MessageObject, Long> = encoder.wrap(message)

    override suspend fun sendAndRecvContainer(messages: List<Pair<MessageObject, Long>>): List<Deferred<TLObject<*>>> {
        val results = mutableListOf<Deferred<TLObject<*>>>()
        val containedMessages = messages.map { encoded ->
            val deferred = CompletableDeferred<MessageUnpackAction>()
            val result = CompletableDeferred<TLObject<*>>()
            results += result
            scope.launch {
                when (val action = deferred.await()) {
                    /* "A container may only be accepted or rejected by the other party as a whole." */
                    is MessageUnpackActionRetry -> sendAndRecvContainer(messages)
                    is MessageUnpackActionReturn -> result.complete(action.value)
                }
            }
            pendingMessages[encoded.second] = deferred
            encoded
        }
        val container = MsgContainerObject(containedMessages.map { it.first })
        val encoded = encoder.wrapAndEncode(container, false)
        val deferred = CompletableDeferred<MessageUnpackAction>()
        scope.launch {
            when (val action = deferred.await()) {
                /* "A container may only be accepted or rejected by the other party as a whole." */
                is MessageUnpackActionRetry -> sendAndRecvContainer(messages)
                is MessageUnpackActionReturn -> error("Server sent a response (${action.value}) to a container")
            }
        }
        connection.send(encoded.first)
        return results
    }

    override suspend fun pump(input: Channel<ByteArray>) {
        while (true) {
            try {
                val b = input.receive()
                val m = encoder.decodeMessage(b)
                state.updateSeqNo(m.seqno)
                unpackMessage(m)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Napier.e("Dropped packet due to exception", e, tag = tag)
            }
        }
    }

    private suspend fun unpackMessage(message: TLObject<*>, msgId: Long? = null) {
        try {
            if (message is MessageObject)
                return unpackMessage(message.body, message.msgId)
            else
                state.updateMsgId(msgId!!)
            Napier.d("Got message $message")
            when (message) {
                is ObjectType -> {
                    unpackMessage(handleMaybeGzipped(message), msgId)
                }
                is BadServerSaltObject -> {
                    // Fix the salt and retry the message
                    Napier.d("Bad server salt, corrected to ${message.newServerSalt}", tag = tag)
                    state.act {
                        state.salt = message.newServerSalt.asTlObject().toTlRepr().toByteArray()
                    }
                    pendingMessages.getValue(message.badMsgId).complete(MessageUnpackActionRetry)
                }
                is NewSessionCreatedObject -> return // We don't care about new sessions, AFAIK
                is MsgContainerObject -> {
                    // Recurse the container
                    message.messages.sortedBy { it.seqno }.forEach { unpackMessage(it, msgId) }
                }
                is RpcResultObject -> {
                    val result = handleMaybeGzipped(message.result)
                    //resultsChannel.send(result)
                    pendingMessages[message.reqMsgId]?.complete(
                        MessageUnpackActionReturn(
                            result
                        )
                    )
                }
                is PongObject -> pendingMessages.getValue(message.msgId).complete(MessageUnpackActionReturn(message))
                is BadMsgNotificationObject -> {
                    Napier.e("Bad msg ${message.badMsgId} (${message.errorCode})", tag = tag)
                    when (message.errorCode) {
                        in 16..17 -> {
                            TODO("sync time")
                        }
                        18 -> Napier.e("Server says invalid msgId", tag = tag)
                        19 -> Napier.e("Server says duped msgId", tag = tag)
                        20 -> {
                            Napier.d("Server complains message too old", tag = tag)
                        } // Just re-send it
                        32 -> state.act {
                            state.seq += 16
                        }
                        33 -> state.act {
                            state.seq -= 16
                        }
                        in 34..35 -> error("Server says relevancy incorrect")
                        48 -> {
                            Napier.e("BadMsgNotification related to bad server salt ignored", tag = tag)
                            return
                        } // We will get a BadServerSalt and re-send then
                        64 -> Napier.e("Server says invalid container", tag = tag)
                        else -> Napier.e("Server sent invalid BadMsgNotification", tag = tag)
                    }
                    pendingMessages[message.badMsgId]?.complete(MessageUnpackActionRetry)
                }
                is MsgDetailedInfoObject -> {
                    Napier.e("Detailed msg info", tag = tag)
                    TODO("implement")
                }
                is MsgNewDetailedInfoObject -> {
                    Napier.e("New detailed msg info", tag = tag)
                    TODO("implement")
                }
                is MsgsAckObject -> {
                    message.msgIds.forEach {
                        // TODO incomingMessages.send(MessageUnpackActionReturn(it, null))
                    }
                }
                is FutureSaltsObject -> {
                    // TODO store and handle future salts
                }
                is MsgsStateReqObject -> {
                    // TODO actually store some data so we can do retries properly
                    connection.send(
                        encoder.wrapAndEncode(
                            MsgsStateInfoObject(
                                msgId,
                                ByteArray(message.msgIds.size) { 1 })
                        ).first
                    )
                }
                is UpdatesType -> updatesChannel.send(message)
                else -> Napier.e("Unknown message type - $message")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Napier.e("Dropped message due to exception", e, tag = tag)
        }
    }

    private fun handleMaybeGzipped(message: ObjectType): TLObject<*> {
        return when (message) {
            is ObjectObject -> {
                message.innerObject
            }
            is GzipPackedObject -> {
                handleMaybeGzipped(
                    ObjectObject.fromTlRepr(
                        GZIPImpl.decompress(message.packedData).toIntArray()
                    )!!.second
                )
            }
        }
    }
}

sealed class MessageUnpackAction

object MessageUnpackActionRetry : MessageUnpackAction()
class MessageUnpackActionReturn(val value: TLObject<*>) : MessageUnpackAction()