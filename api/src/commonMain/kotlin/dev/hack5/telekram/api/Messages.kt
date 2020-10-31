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

import dev.hack5.telekram.api.iter.iter
import dev.hack5.telekram.core.client.TelegramClient
import dev.hack5.telekram.core.tl.*

private const val MAX_CHUNK_SIZE = 100

suspend fun TelegramClient.getMessages(
    peer: InputPeerType,
    firstId: Int? = null,
    beforeDate: Int? = null,
    skipCount: Int? = null,
    limit: Int? = null,
    reverse: Boolean = false,
    lastId: Int? = null
) = iter<MessageType, Pair<Int, Int?>?>(null) {
    var (offsetId, remainingCount) = it ?: Pair(firstId ?: 0, limit)
    var addOffset = skipCount ?: 0
    val offsetDate = if (it == null) beforeDate ?: 0 else 0
    if (reverse && it == null) {
        if (firstId == null && beforeDate == null) {
            offsetId = 1
        }
        addOffset -= MAX_CHUNK_SIZE
    }
    val request = Messages_GetHistoryRequest(peer, offsetId, offsetDate, addOffset, remainingCount ?: MAX_CHUNK_SIZE, 0, 0, 0)
    var messagesList = when (val messages = this(request)) {
        is Messages_MessagesObject -> messages.messages
        is Messages_MessagesSliceObject -> {
            messages.messages
        }
        is Messages_ChannelMessagesObject -> {
            messages.messages
        }
        is Messages_MessagesNotModifiedObject -> TODO()
    }
    if (reverse) {
        messagesList = messagesList.asReversed()
    }
    val length = messagesList.size
    if (lastId != null) {
        if (reverse)
            messagesList.takeWhile { msg -> msg.id <= lastId }
        else
            messagesList.takeWhile { msg -> msg.id >= lastId }
    }
    if (messagesList.size != length)
        return@iter messagesList to null
    if (remainingCount != null) {
        messagesList = messagesList.subList(0, remainingCount.coerceAtMost(messagesList.size))
    }
    val lastMessageId = messagesList.last().id
    val data = Pair(lastMessageId, remainingCount?.minus(messagesList.size)?.coerceAtLeast(0))
    messagesList to if (data.second == 0) null else data
}