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
import dev.hack5.telekram.core.utils.toInputChannel
import dev.hack5.telekram.core.utils.toInputPeer
import dev.hack5.telekram.core.utils.toInputUser

// TODO this feels stupid
suspend fun Messages_DialogsType.getInputPeer(dialog: DialogObject, client: TelegramClient): InputPeerType {
    val id = dialog.peer.id
    return when (this) {
        is Messages_DialogsObject -> when (dialog.peer) {
            is PeerUserObject -> (users.first { (it as? UserObject)?.id == id } as UserObject).toInputUser(client)
                .toInputPeer()
            is PeerChannelObject -> (chats.first { (it as? ChannelObject)?.id == id } as ChannelObject).toInputChannel(
                client
            ).toInputPeer()
            is PeerChatObject -> InputPeerChatObject(id)
        }
        is Messages_DialogsSliceObject -> when (dialog.peer) {
            is PeerUserObject -> (users.first { (it as? UserObject)?.id == id } as UserObject).toInputUser(client)
                .toInputPeer()
            is PeerChannelObject -> (chats.first { (it as? ChannelObject)?.id == id } as ChannelObject).toInputChannel(
                client
            ).toInputPeer()
            is PeerChatObject -> InputPeerChatObject(id)
        }
        is Messages_DialogsNotModifiedObject -> TODO("dialogs caching")
    }
}