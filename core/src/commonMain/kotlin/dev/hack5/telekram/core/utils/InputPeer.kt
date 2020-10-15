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

package dev.hack5.telekram.core.utils

import dev.hack5.telekram.core.client.TelegramClient
import dev.hack5.telekram.core.tl.*
import dev.hack5.telekram.core.updates.PeerType
import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.ExperimentalCoroutinesApi

fun ChatObject.toInputChat(): InputPeerChatObject = InputPeerChatObject(id)
fun PeerChatObject.toInputChat(): InputPeerChatObject = InputPeerChatObject(chatId)

suspend fun UserObject.toInputUser(client: TelegramClient): InputUserType {
    return if (min) {
        PeerUserObject(id).toInputUser(client)
    } else {
        InputUserObject(id, accessHash!!)
    }
}

suspend fun PeerUserObject.toInputUser(client: TelegramClient): InputUserType {
    return InputUserObject(
        userId, client.getAccessHash(PeerType.USER, userId)
            ?: return getInputUser(client)
                ?: throw EntityNotFoundException(PeerType.USER, userId)
    )
}

private suspend fun PeerUserObject.getInputUser(client: TelegramClient): InputUserType? {
    return (client(
        Users_GetUsersRequest(
            listOf(
                InputUserObject(
                    userId,
                    0
                )
            )
        )
    ).singleOrNull() as? UserObject)?.toInputUser(client)
}

suspend fun ChannelObject.toInputChannel(client: TelegramClient): InputChannelType {
    return if (min) {
        PeerChannelObject(id).toInputChannel(client)
    } else {
        InputChannelObject(id, accessHash!!)
    }
}

suspend fun PeerChannelObject.toInputChannel(client: TelegramClient): InputChannelType {

    return InputChannelObject(
        channelId, client.getAccessHash(PeerType.CHANNEL, channelId)
            ?: return getInputChannel(client)
                ?: throw EntityNotFoundException(PeerType.CHANNEL, channelId)
    )
}

private suspend fun PeerChannelObject.getInputChannel(client: TelegramClient): InputChannelType? {
    return ((client(
        Channels_GetChannelsRequest(
            listOf(
                InputChannelObject(
                    channelId,
                    0
                )
            )
        )
    ) as? Messages_ChatsObject)?.chats?.singleOrNull() as? ChannelObject)?.toInputChannel(client)
}

suspend fun dev.hack5.telekram.core.tl.PeerType.toInputPeer(client: TelegramClient): InputPeerType = when (this) {
    is PeerUserObject -> toInputUser(client).toInputPeer()
    is PeerChatObject -> toInputChat()
    is PeerChannelObject -> toInputChannel(client).toInputPeer()
}

fun InputUserType.toInputPeer() = when (this) {
    is InputUserEmptyObject -> InputPeerEmptyObject()
    is InputUserSelfObject -> InputPeerSelfObject()
    is InputUserObject -> InputPeerUserObject(userId, accessHash)
    is InputUserFromMessageObject -> InputPeerUserFromMessageObject(peer, msgId, userId)
}

fun InputChannelType.toInputPeer() = when (this) {
    is InputChannelEmptyObject -> InputPeerEmptyObject()
    is InputChannelObject -> InputPeerChannelObject(channelId, accessHash)
    is InputChannelFromMessageObject -> InputPeerChannelFromMessageObject(peer, msgId, channelId)
}

@Suppress("EXPERIMENTAL_API_USAGE")
class EntityNotFoundException(val peerType: PeerType, val peerId: Int) : CopyableThrowable<EntityNotFoundException>,
    NoSuchElementException("Failed to find $peerType with ID $peerId") {
    @ExperimentalCoroutinesApi
    override fun createCopy(): EntityNotFoundException =
        EntityNotFoundException(peerType, peerId)
}