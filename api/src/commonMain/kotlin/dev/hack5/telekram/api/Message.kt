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
import dev.hack5.telekram.core.utils.toInputPeer
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

val MessageType.fromId: Int?
    get() = when (this) {
        is MessageEmptyObject -> null
        is MessageObject -> fromId
        is MessageServiceObject -> fromId
    }

val MessageType.out: Boolean
    get() = when (this) {
        is MessageEmptyObject -> false
        is MessageObject -> out
        is MessageServiceObject -> out
    }

val MessageType.editDate: Int?
    get() = when (this) {
        is MessageEmptyObject -> null
        is MessageObject -> editDate
        is MessageServiceObject -> null
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
) = message.run {
    if (message.media?.toInputMedia() == null)
        sendMessage(
            toPeer,
            message.message,
            replyToMsgId,
            clearDraft,
            sendTime,
            message.media as? MessageMediaWebPageObject == null,
            silent,
            replyMarkup,
            entities,
        )
    else
        sendMessage(
            toPeer,
            media!!.toInputMedia(),
            message.message,
            replyToMsgId,
            clearDraft,
            sendTime,
            silent,
            replyMarkup,
            entities
        )
}

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

suspend fun TelegramClient.editMessage(
    peer: InputPeerType,
    id: Int,
    message: MessageObject
) = message.run {
    editMessage(
        peer,
        id,
        message.message,
        media?.toInputMedia(),
        date,
        message.media as? MessageMediaWebPageObject == null,
        replyMarkup,
        entities
    )
}

suspend fun TelegramClient.editMessage(
    peer: InputPeerType,
    id: Int,
    message: String? = null,
    media: InputMediaType? = null,
    sendTime: Int? = null,
    noWebpage: Boolean = false,
    replyMarkup: ReplyMarkupType? = null,
    entities: List<MessageEntityType>? = null
) = this(
    Messages_EditMessageRequest(
        noWebpage,
        peer,
        id,
        message,
        media,
        replyMarkup,
        entities,
        sendTime
    )
)

suspend fun MessageObject.edit(
    client: TelegramClient,
    message: MessageObject
) = client.editMessage(
    toId.toInputPeer(client),
    id,
    message
)

suspend fun MessageObject.edit(
    client: TelegramClient,
    message: String? = null,
    media: InputMediaType? = null,
    sendTime: Int? = null,
    noWebpage: Boolean = false,
    replyMarkup: ReplyMarkupType? = null,
    entities: List<MessageEntityType>? = null
) = client.editMessage(
    toId.toInputPeer(client),
    id,
    message,
    media,
    sendTime,
    noWebpage,
    replyMarkup,
    entities
)