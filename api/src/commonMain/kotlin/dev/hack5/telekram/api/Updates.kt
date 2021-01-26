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
import dev.hack5.telekram.core.tl.PeerType
import dev.hack5.telekram.core.updates.*


interface Event {
    val originalUpdate: Update?
    suspend fun commit()
}

interface EventHandler<E : Event> {
    fun constructEvent(client: TelegramClient, update: UpdateOrSkipped): E? = when (update) {
        is Update -> when (update.update) {
            is ActualUpdate -> constructEvent(client, update)
            is SyntheticUpdate -> constructSyntheticEvent(client, update)
        }
        is Skipped -> constructSkippedEvent(client, update)
    }

    fun constructEvent(client: TelegramClient, update: Update): E? = null
    fun constructSyntheticEvent(client: TelegramClient, update: Update): E? = null
    fun constructSkippedEvent(client: TelegramClient, update: Skipped): E? = null

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
        val message: MessageType
    ) : Event, SenderGetter, ChatGetter {
        override val senderId: Int? get() = message.fromId
        override val chatPeer: PeerType?
            get() = when (val toId = message.toId) {
                is PeerUserObject -> {
                    if (message.out) toId else PeerUserObject(message.fromId!!)
                }
                is PeerChatObject -> toId
                is PeerChannelObject -> toId
                null -> null
            }

        override suspend fun commit() = originalUpdate.commit()
    }

    override fun constructEvent(client: TelegramClient, update: Update): NewMessageEvent? = (update.update as ActualUpdate).update.let {
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
                message
            )
        }
        is MessageServiceObject -> message.run {
            NewMessageEvent(
                client,
                update,
                message
            )
        }
    }
}

object EditMessage : EventHandler<EditMessage.EditMessageEvent> {
    data class EditMessageEvent(
        override val client: TelegramClient,
        override val originalUpdate: Update,
        val message: MessageType
    ) : Event, SenderGetter, ChatGetter {
        override val senderId: Int? get() = message.fromId
        override val chatPeer: PeerType?
            get() = when (val toId = message.toId) {
                is PeerUserObject -> {
                    if (message.out) toId else PeerUserObject(message.fromId!!)
                }
                is PeerChatObject -> toId
                is PeerChannelObject -> toId
                null -> null
            }

        override suspend fun commit() = originalUpdate.commit()
    }

    override fun constructEvent(client: TelegramClient, update: Update): EditMessageEvent? = (update.update as ActualUpdate).update.let {
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
                message
            )
        }
        is MessageServiceObject -> message.run {
            EditMessageEvent(
                client,
                update,
                message
            )
        }
    }
}

object SkippedUpdate : EventHandler<SkippedUpdate.SkippedUpdateEvent> {
    data class SkippedUpdateEvent(val client: TelegramClient, val originalSkipped: Skipped) : Event {
        override val originalUpdate: Nothing?
            get() = null

        val channelId get() = originalSkipped.channelId

        override suspend fun commit() = originalSkipped.commit()
    }

    override fun constructSkippedEvent(client: TelegramClient, update: Skipped) = SkippedUpdateEvent(client, update)
}

object RawUpdate : EventHandler<RawUpdate.RawUpdateEvent> {
    data class RawUpdateEvent(val client: TelegramClient, override val originalUpdate: Update) : Event {
        val update
            get() = originalUpdate.update

        override suspend fun commit() = originalUpdate.commit()
    }

    override fun constructEvent(client: TelegramClient, update: Update) = RawUpdateEvent(client, update)
    override fun constructSyntheticEvent(client: TelegramClient, update: Update) = RawUpdateEvent(client, update)
}