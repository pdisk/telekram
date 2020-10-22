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

import dev.hack5.telekram.core.client.TelegramClient
import dev.hack5.telekram.core.tl.*
import kotlin.random.Random

val MessageType.date: Int?
    get() = when (this) {
        is MessageEmptyObject -> null
        is MessageObject -> date
        is MessageServiceObject -> date
    }

val MessageType.id: Int
    get() = when (this) {
        is MessageEmptyObject -> id
        is MessageObject -> id
        is MessageServiceObject -> id
    }

val MessageType.toId: PeerType?
    get() = when (this) {
        is MessageEmptyObject -> null
        is MessageObject -> toId
        is MessageServiceObject -> toId
    }

suspend fun TelegramClient.getChatId(message: MessageType): Int? = when (message) {
    is MessageEmptyObject -> null
    is MessageObject -> if (getInputMe().userId == message.toId.id) message.fromId else message.toId.id
    is MessageServiceObject -> if (getInputMe().userId == message.toId.id) message.fromId else message.toId.id
}

suspend fun TelegramClient.sendMessage(
    toPeer: InputPeerType,
    message: MessageObject,
    replyToMsgId: Int? = null,
    clearDraft: Boolean = true,
    sendTime: Int? = null
) =
    if (message.media?.toInputMedia() == null)
        this(
            Messages_SendMessageRequest(
                message.media as? MessageMediaWebPageObject == null,
                message.silent,
                false,
                clearDraft,
                toPeer,
                replyToMsgId,
                message.message,
                Random.nextLong(),
                message.replyMarkup,
                message.entities,
                sendTime
            )
        )
    else
        this(
            Messages_SendMediaRequest(
                message.silent,
                false,
                clearDraft,
                toPeer,
                replyToMsgId,
                message.media!!.toInputMedia(),
                message.message,
                Random.nextLong()
            )
        )

suspend fun TelegramClient.sendMessage(
    toPeer: InputPeerType,
    message: String,
    replyToMsgId: Int? = null,
    clearDraft: Boolean = true,
    sendTime: Int? = null,
    noWebpage: Boolean = false,
    silent: Boolean = false,
    replyMarkup: ReplyMarkupType? = null,
    entities: List<MessageEntityType>? = null
) =
    this(
        Messages_SendMessageRequest(
            noWebpage,
            silent,
            false,
            clearDraft,
            toPeer,
            replyToMsgId,
            message,
            Random.nextLong(),
            replyMarkup,
            entities,
            sendTime
        )
    )

suspend fun TelegramClient.sendMessage(
    toPeer: InputPeerType,
    media: InputMediaType,
    caption: String = "",
    replyToMsgId: Int? = null,
    clearDraft: Boolean = true,
    sendTime: Int? = null,
    silent: Boolean = false,
    replyMarkup: ReplyMarkupType? = null,
    entities: List<MessageEntityType>? = null
) =
    this(
        Messages_SendMediaRequest(
            silent,
            false,
            clearDraft,
            toPeer,
            replyToMsgId,
            media,
            caption,
            Random.nextLong(),
            replyMarkup,
            entities,
            sendTime
        )
    )
