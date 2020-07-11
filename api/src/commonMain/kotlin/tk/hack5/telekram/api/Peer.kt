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

import tk.hack5.telekram.core.client.TelegramClient
import tk.hack5.telekram.core.tl.*
import tk.hack5.telekram.core.utils.toInputChannel
import tk.hack5.telekram.core.utils.toInputChat
import tk.hack5.telekram.core.utils.toInputPeer
import tk.hack5.telekram.core.utils.toInputUser

val PeerType.id
    get() = when (this) {
        is PeerUserObject -> userId
        is PeerChatObject -> chatId
        is PeerChannelObject -> channelId
    }

sealed class Peer(val id: Int, val inputPeer: InputPeerType)

data class PeerUser(val user: UserObject, val inputUser: InputUserType) :
    Peer(user.id, inputUser.toInputPeer())

data class PeerChat(val chat: ChatObject) : Peer(chat.id, chat.toInputChat())
data class PeerChannel(val channel: ChannelObject, val inputChannel: InputChannelType) :
    Peer(channel.id, inputChannel.toInputPeer())

suspend fun UserObject.toPeer(client: TelegramClient) = PeerUser(this, toInputUser(client))
fun ChatObject.toPeer() = PeerChat(this)
suspend fun ChannelObject.toPeer(client: TelegramClient) = PeerChannel(this, toInputChannel(client))

suspend fun ChatType.toPeer(client: TelegramClient) = when (this) {
    is ChatObject -> this.toPeer()
    is ChannelObject -> this.toPeer(client)
    is ChatEmptyObject -> null
    is ChatForbiddenObject -> null
    is ChannelForbiddenObject -> null
}

suspend fun Messages_DialogsType.getPeer(dialog: DialogObject, client: TelegramClient): Peer {
    val id = dialog.peer.id
    return when (this) {
        is Messages_DialogsObject -> when (dialog.peer) {
            is PeerUserObject -> (users.first { (it as? UserObject)?.id == id } as UserObject).toPeer(client)
            is PeerChannelObject -> (chats.first { (it as? ChannelObject)?.id == id } as ChannelObject).toPeer(client)
            is PeerChatObject -> (chats.first { (it as? ChatObject)?.id == id } as ChatObject).toPeer()
        }
        is Messages_DialogsSliceObject -> when (dialog.peer) {
            is PeerUserObject -> (users.first { (it as? UserObject)?.id == id } as UserObject).toPeer(client)
            is PeerChannelObject -> (chats.first { (it as? ChannelObject)?.id == id } as ChannelObject).toPeer(client)
            is PeerChatObject -> (chats.first { (it as? ChatObject)?.id == id } as ChatObject).toPeer()
        }
        is Messages_DialogsNotModifiedObject -> TODO("dialogs caching")
    }
}