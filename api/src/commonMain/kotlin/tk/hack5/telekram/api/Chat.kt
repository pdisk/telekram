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
import tk.hack5.telekram.core.utils.toInputPeer
import tk.hack5.telekram.core.utils.toInputUser

interface ChatGetter {
    val chatPeer: PeerType
    val client: TelegramClient

    suspend fun getChat(): Peer = chatPeer.let {
        when (it) {
            is PeerUserObject -> (client(
                Users_GetUsersRequest(
                    listOf(it.toInputUser(client))
                )
            ).single() as UserObject).toPeer(client)
            is PeerChatObject -> ((client(
                Messages_GetChatsRequest(
                    listOf(it.chatId)
                )
            ) as Messages_ChatsObject).chats.single() as ChatObject).toPeer()
            is PeerChannelObject -> ((client(
                Channels_GetChannelsRequest(
                    listOf(it.toInputChannel(client))
                )
            ) as Messages_ChatsObject).chats.single() as ChannelObject).toPeer(client)
        }
    }
    suspend fun getInputChat(): InputPeerType = chatPeer.toInputPeer(client)!!
}