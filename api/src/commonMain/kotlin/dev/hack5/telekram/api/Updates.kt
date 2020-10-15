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
import dev.hack5.telekram.core.updates.Skipped
import dev.hack5.telekram.core.updates.Update
import dev.hack5.telekram.core.updates.UpdateOrSkipped


interface Event {
    val originalUpdate: Update?
}

interface EventHandler<E : Event> {
    fun constructEvent(client: TelegramClient, update: UpdateOrSkipped): E? = when (update) {
        is Update -> constructEvent(client, update)
        is Skipped -> constructEvent(client, update.channelId)
    }

    fun constructEvent(client: TelegramClient, update: Update): E? = null
    fun constructEvent(client: TelegramClient, channelId: Int?): E? = null

    companion object {
        val defaultHandlers = listOf(
            NewMessage,
            EditMessage,
            SkippedUpdate,
            RawUpdate
        )
    }
}

object NewMessage : EventHandler<NewMessage.NewMessageEvent> {
    data class NewMessageEvent(
        override val client: TelegramClient,
        override val originalUpdate: Update,
        val originalMessage: MessageType,
        val out: Boolean,
        val mentioned: Boolean,
        val mediaUnread: Boolean,
        val silent: Boolean,
        val post: Boolean,
        val fromScheduled: Boolean,
        val legacy: Boolean,
        val editHide: Boolean,
        val id: Int,
        val fromId: Int?,
        val toId: PeerType,
        val fwdFrom: MessageFwdHeaderType?,
        val viaBotId: Int?,
        val replyToMsgId: Int?,
        val date: Int,
        val message: String,
        val media: MessageMediaType?,
        val replyMarkup: ReplyMarkupType?,
        val entities: List<MessageEntityType>,
        val views: Int?,
        val editDate: Int?,
        val postAuthor: String?,
        val groupedId: Long?,
        val restrictionReason: List<RestrictionReasonType>
    ) : Event, SenderGetter, ChatGetter {
        override val senderId: Int? get() = fromId
        override val chatPeer: PeerType
            get() = when (toId) {
                is PeerUserObject -> {
                    if (out) toId else PeerUserObject(fromId!!)
                }
                is PeerChatObject -> toId
                is PeerChannelObject -> toId
            }
    }

    override fun constructEvent(client: TelegramClient, update: Update): NewMessageEvent? = update.update.let {
        when (it) {
            is UpdateNewMessageObject -> constructFromMessage(client, update, it.message)
            is UpdateNewChannelMessageObject -> constructFromMessage(client, update, it.message)
            else -> null
        }
    }

    private fun constructFromMessage(
        client: TelegramClient,
        update: Update,
        message: MessageType
    ): NewMessageEvent? = when (message) {
        is MessageEmptyObject -> null
        is MessageObject -> message.run {
            NewMessageEvent(
                client,
                update,
                message,
                out,
                mentioned,
                mediaUnread,
                silent,
                post,
                fromScheduled,
                legacy,
                editHide,
                id,
                fromId,
                toId,
                fwdFrom,
                viaBotId,
                replyToMsgId,
                date,
                message.message,
                media,
                replyMarkup,
                entities ?: emptyList(),
                views,
                editDate,
                postAuthor,
                groupedId,
                restrictionReason ?: emptyList()
            )
        }
        is MessageServiceObject -> message.run {
            NewMessageEvent(
                client,
                update,
                message,
                out,
                mentioned,
                mediaUnread,
                silent,
                post,
                false,
                legacy,
                false,
                id,
                fromId,
                toId,
                null,
                null,
                replyToMsgId,
                date,
                "",
                null,
                null,
                emptyList(),
                null,
                null,
                null,
                null,
                emptyList()
            )
        }
    }
}

object EditMessage : EventHandler<EditMessage.EditMessageEvent> {
    data class EditMessageEvent(
        override val client: TelegramClient,
        override val originalUpdate: Update,
        val originalMessage: MessageType,
        val out: Boolean,
        val mentioned: Boolean,
        val mediaUnread: Boolean,
        val silent: Boolean,
        val post: Boolean,
        val fromScheduled: Boolean,
        val legacy: Boolean,
        val editHide: Boolean,
        val id: Int,
        val fromId: Int?,
        val toId: PeerType,
        val fwdFrom: MessageFwdHeaderType?,
        val viaBotId: Int?,
        val replyToMsgId: Int?,
        val date: Int,
        val message: String,
        val media: MessageMediaType?,
        val replyMarkup: ReplyMarkupType?,
        val entities: List<MessageEntityType>,
        val views: Int?,
        val editDate: Int?,
        val postAuthor: String?,
        val groupedId: Long?,
        val restrictionReason: List<RestrictionReasonType>
    ) : Event, SenderGetter, ChatGetter {
        override val senderId: Int? get() = fromId
        override val chatPeer: PeerType
            get() = when (toId) {
                is PeerUserObject -> {
                    if (out) toId else PeerUserObject(fromId!!)
                }
                is PeerChatObject -> toId
                is PeerChannelObject -> toId
            }
    }

    override fun constructEvent(client: TelegramClient, update: Update): EditMessageEvent? = update.update.let {
        when (it) {
            is UpdateEditMessageObject -> constructFromMessage(client, update, it.message)
            is UpdateEditChannelMessageObject -> constructFromMessage(client, update, it.message)
            else -> null
        }
    }

    private fun constructFromMessage(
        client: TelegramClient,
        update: Update,
        message: MessageType
    ): EditMessageEvent? = when (message) {
        is MessageEmptyObject -> null
        is MessageObject -> message.run {
            EditMessageEvent(
                client,
                update,
                message,
                out,
                mentioned,
                mediaUnread,
                silent,
                post,
                fromScheduled,
                legacy,
                editHide,
                id,
                fromId,
                toId,
                fwdFrom,
                viaBotId,
                replyToMsgId,
                date,
                message.message,
                media,
                replyMarkup,
                entities ?: emptyList(),
                views,
                editDate,
                postAuthor,
                groupedId,
                restrictionReason ?: emptyList()
            )
        }
        is MessageServiceObject -> message.run {
            EditMessageEvent(
                client,
                update,
                message,
                out,
                mentioned,
                mediaUnread,
                silent,
                post,
                false,
                legacy,
                false,
                id,
                fromId,
                toId,
                null,
                null,
                replyToMsgId,
                date,
                "",
                null,
                null,
                emptyList(),
                null,
                null,
                null,
                null,
                emptyList()
            )
        }
    }
}

object SkippedUpdate : EventHandler<SkippedUpdate.SkippedUpdateEvent> {
    data class SkippedUpdateEvent(val client: TelegramClient, val channelId: Int?) : Event {
        override val originalUpdate: Nothing?
            get() = null
    }

    override fun constructEvent(client: TelegramClient, channelId: Int?) = SkippedUpdateEvent(client, channelId)
}

object RawUpdate : EventHandler<RawUpdate.RawUpdateEvent> {
    data class RawUpdateEvent(val client: TelegramClient, override val originalUpdate: Update) : Event {
        val update
            get() = originalUpdate.update
    }

    override fun constructEvent(client: TelegramClient, update: Update) = RawUpdateEvent(client, update)
}