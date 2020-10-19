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

package dev.hack5.telekram.core.client

import com.github.aakira.napier.Napier
import dev.hack5.telekram.core.mtproto.MessageObject
import dev.hack5.telekram.core.tl.TLMethod
import dev.hack5.telekram.core.tl.TLObject
import dev.hack5.telekram.core.utils.BaseActor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

open class GroupingTelegramClient(
    val client: TelegramClient,
    protected val autoSendTime: Long = 0,
    initialCapacity: Int = 100
) : TelegramClient by client, BaseActor(client.scope!!) {
    protected var pendingRequests = ArrayDeque<PendingRequest<*>>(initialCapacity)
    protected var sendJob: Job? = null

    override suspend operator fun <N, R : TLObject<N>> invoke(
        request: TLMethod<R>,
        skipEntities: Boolean,
        forUpdate: Boolean,
        packer: (suspend (TLMethod<*>) -> TLObject<*>)?
    ): N {
        require(packer == null) { "packer ($packer) not null in grouped request" } // TODO consider if this needs changing/'fixing'
        val pendingRequest =
            PendingRequest(client.packer!!.wrap(request), skipEntities, forUpdate, CompletableDeferred())
        act {
            pendingRequests.addFirst(pendingRequest)
            sendJob?.cancel()
            sendJob = scope!!.launch {
                delay(autoSendTime)
                act {
                    sendJob = null // prevent cancellation from a new request being added
                }
                sendPending()
            }
        }
        return client(request, skipEntities, forUpdate) { pendingRequest.result.await() }
    }

    suspend fun sendPending() {
        // TODO deal with containers that are too large to fit in the max size allowed, and split them
        val containerMaxSize = client.packer!!.containerMaxSize
        val containerMaxMessages = client.packer!!.containerMaxMessages

        val requests: List<PendingRequest<*>> = act {
            val requests = mutableListOf<PendingRequest<*>>()
            var length = 0
            while (true) {
                val request = pendingRequests.removeLastOrNull() ?: break
                length += request.request.first.toTlRepr().size * Int.SIZE_BYTES // TODO: don't evaluate the body twice
                if (length >= containerMaxSize || requests.size + 1 >= containerMaxMessages) {
                    pendingRequests.addLast(request)
                    break
                }
                requests.add(request)
            }
            Napier.d({ "Sending ${requests.size} requests ($length bytes)" }, tag = tag)
            requests
        }
        if (requests.isEmpty())
            return
        val results = requests.zip(packer!!.sendAndRecvContainer(requests.map(PendingRequest<*>::request::get)))
        for ((pendingRequest, result) in results) {
            result.invokeOnCompletion {
                if (it != null) {
                    pendingRequest.result.completeExceptionally(it)
                } else {
                    @Suppress("UNCHECKED_CAST") // we zipped it, so it should be right. if this breaks, kill me.
                    pendingRequest.result as CompletableDeferred<TLObject<*>>
                    pendingRequest.result.complete(result.getCompleted())
                }
            }
        }

    }

    protected data class PendingRequest<R : TLObject<*>>(
        val request: Pair<MessageObject, Long>,
        val skipEntities: Boolean,
        val forUpdate: Boolean,
        val result: CompletableDeferred<R>
    )
}

private const val tag = "GroupingTelegramClient"