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

package dev.hack5.telekram.core.updates

import dev.hack5.telekram.core.client.TelegramClient
import dev.hack5.telekram.core.tl.*
import dev.hack5.telekram.core.utils.TLWalker


enum class PeerType {
    USER,
    CHANNEL,
    PHOTO,
    ENCRYPTED_FILE_LOCATION,
    DOCUMENT_FILE_LOCATION,
    SECURE_FILE_LOCATION,
    PHOTO_FILE_LOCATION,
    PHOTO_LEGACY_FILE_LOCATION,
    WALLPAPER,
    ENCRYPTED_CHAT,
    ENCRYPTED_FILE,
    DOCUMENT,
    BOT_INLINE,
    THEME,
    SECURE_FILE
}

class AccessHashGetter :
    TLWalker<Triple<MutableMap<String, MutableMap<Long, Long>>, MutableMap<Int, Pair<dev.hack5.telekram.core.tl.PeerType, Int>?>, MutableMap<Int, Pair<dev.hack5.telekram.core.tl.PeerType, Int>?>>>() {
    override val result get() = Triple(map, minUsers, minChannels)
    val map = mutableMapOf<String, MutableMap<Long, Long>>()
    val minUsers = mutableMapOf<Int, Pair<dev.hack5.telekram.core.tl.PeerType, Int>?>()
    val minChannels = mutableMapOf<Int, Pair<dev.hack5.telekram.core.tl.PeerType, Int>?>()

    override fun handle(key: String, value: TLObject<*>?): Boolean {
        val peerType: PeerType
        val id: Long
        val accessHash: Long
        when (value) {
            is InputPeerUserObject -> {
                peerType = PeerType.USER
                id = value.userId.toLong()
                accessHash = value.accessHash
            }
            is InputPeerChannelObject -> {
                peerType = PeerType.CHANNEL
                id = value.channelId.toLong()
                accessHash = value.accessHash
            }
            is InputUserObject -> {
                peerType = PeerType.USER
                id = value.userId.toLong()
                accessHash = value.accessHash
            }
            is InputPhotoObject -> {
                peerType = PeerType.PHOTO
                id = value.id
                accessHash = value.accessHash
            }
            is InputEncryptedFileLocationObject -> {
                peerType = PeerType.ENCRYPTED_FILE_LOCATION
                id = value.id
                accessHash = value.accessHash
            }
            is InputDocumentFileLocationObject -> {
                peerType = PeerType.DOCUMENT_FILE_LOCATION
                id = value.id
                accessHash = value.accessHash
            }
            is InputSecureFileLocationObject -> {
                peerType = PeerType.SECURE_FILE_LOCATION
                id = value.id
                accessHash = value.accessHash
            }
            is InputPhotoFileLocationObject -> {
                peerType = PeerType.PHOTO_FILE_LOCATION
                id = value.id
                accessHash = value.accessHash
            }
            is InputPhotoLegacyFileLocationObject -> {
                peerType = PeerType.PHOTO_LEGACY_FILE_LOCATION
                id = value.id
                accessHash = value.accessHash
            }
            is UserObject -> {
                if (value.min) {
                    minUsers[value.id] = null
                    return true
                } else {
                    peerType = PeerType.USER
                    id = value.id.toLong()
                    accessHash = value.accessHash ?: return true
                }
            }
            is ChannelObject -> {
                if (value.min) {
                    minChannels[value.id] = null
                    return true
                } else {
                    peerType = PeerType.CHANNEL
                    id = value.id.toLong()
                    accessHash = value.accessHash ?: return true
                }
            }
            is ChannelForbiddenObject -> {
                peerType = PeerType.CHANNEL
                id = value.id.toLong()
                accessHash = value.accessHash
            }
            is PhotoObject -> {
                peerType = PeerType.PHOTO
                id = value.id
                accessHash = value.accessHash
            }
            is EncryptedChatWaitingObject -> {
                peerType = PeerType.ENCRYPTED_CHAT
                id = value.id.toLong()
                accessHash = value.accessHash
            }
            is EncryptedChatRequestedObject -> {
                peerType = PeerType.ENCRYPTED_CHAT
                id = value.id.toLong()
                accessHash = value.accessHash
            }
            is EncryptedChatObject -> {
                peerType = PeerType.ENCRYPTED_CHAT
                id = value.id.toLong()
                accessHash = value.accessHash
            }
            is InputEncryptedChatObject -> {
                peerType = PeerType.ENCRYPTED_CHAT
                id = value.chatId.toLong()
                accessHash = value.accessHash
            }
            is EncryptedFileObject -> {
                peerType = PeerType.ENCRYPTED_FILE
                id = value.id
                accessHash = value.accessHash
            }
            is InputEncryptedFileObject -> {
                peerType = PeerType.ENCRYPTED_FILE
                id = value.id
                accessHash = value.accessHash
            }
            is InputDocumentObject -> {
                peerType = PeerType.DOCUMENT
                id = value.id
                accessHash = value.accessHash
            }
            is DocumentObject -> {
                peerType = PeerType.DOCUMENT
                id = value.id
                accessHash = value.accessHash
            }
            is InputChannelObject -> {
                peerType = PeerType.CHANNEL
                id = value.channelId.toLong()
                accessHash = value.accessHash
            }
            is InputBotInlineMessageIDObject -> {
                peerType = PeerType.BOT_INLINE
                id = value.id
                accessHash = value.accessHash
            }
            is InputSecureFileObject -> {
                peerType = PeerType.SECURE_FILE
                id = value.id
                accessHash = value.accessHash
            }
            is SecureFileObject -> {
                peerType = PeerType.SECURE_FILE
                id = value.id
                accessHash = value.accessHash
            }
            is InputWallPaperObject -> {
                peerType = PeerType.WALLPAPER
                id = value.id
                accessHash = value.accessHash
            }
            is InputThemeObject -> {
                peerType = PeerType.THEME
                id = value.id
                accessHash = value.accessHash
            }
            is ThemeObject -> {
                peerType = PeerType.THEME
                id = value.id
                accessHash = value.accessHash
            }

            else -> return true
        }
        map.getOrPut(peerType.toString()) { mutableMapOf() }[id] = accessHash
        return true
    }
}

class MinGetter(
    val minUsers: MutableMap<Int, Pair<dev.hack5.telekram.core.tl.PeerType, Int>?>,
    val minChannels: MutableMap<Int, Pair<dev.hack5.telekram.core.tl.PeerType, Int>?>
) : TLWalker<Nothing>() {
    // TODO: reduce code duplication
    override fun handle(key: String, value: TLObject<*>?): Boolean {
        when (value) {
            /* there are some things that don't make sense to handle:
               - if a user was seen in a private chat, constructing an InputPeerUserFromMessage
                 would require us to already have their InputPeer
             */
            is MessageObject -> {
                value.fromId?.let {
                    if (minUsers.containsKey(it)) minUsers[it] = value.toId to value.id
                }
                (value.fwdFrom as? MessageFwdHeaderObject?)?.run {
                    fromId?.let {
                        if (minUsers.containsKey(it)) minUsers[it] = value.toId to value.id
                    }
                    channelId?.let {
                        if (minChannels.containsKey(it)) minChannels[it] = value.toId to value.id
                    }
                    when (savedFromPeer) {
                        is PeerUserObject -> {
                            savedFromPeer.userId.let {
                                if (minUsers.containsKey(it)) minUsers[it] = value.toId to value.id
                            }
                        }
                        is PeerChannelObject -> {
                            savedFromPeer.channelId.let {
                                if (minChannels.containsKey(it)) minChannels[it] = value.toId to value.id
                            }
                        }
                        else -> {
                        }
                    }
                }
                value.viaBotId?.let {
                    if (minUsers.containsKey(it)) minUsers[it] = value.toId to value.id
                }
            }
            is MessageServiceObject -> {
                value.fromId?.let {
                    if (minUsers.containsKey(it)) minUsers[it] = value.toId to value.id
                }
                when (value.toId) {
                    is PeerUserObject -> {
                        value.toId.userId.let {
                            if (minUsers.containsKey(it)) minUsers[it] = value.toId to value.id
                        }
                    }
                    is PeerChannelObject -> {
                        value.toId.channelId.let {
                            if (minChannels.containsKey(it)) minChannels[it] = value.toId to value.id
                        }
                    }
                    else -> {
                    }
                }
                when (value.action) {
                    is MessageActionChatCreateObject -> value.action.users.forEach {
                        if (minUsers.containsKey(it)) minUsers[it] = value.toId to value.id
                    }
                    is MessageActionChatAddUserObject -> value.action.users.forEach {
                        if (minUsers.containsKey(it)) minUsers[it] = value.toId to value.id
                    }
                    is MessageActionChatDeleteUserObject -> value.action.userId.let {
                        if (minUsers.containsKey(it)) minUsers[it] = value.toId to value.id
                    }
                    is MessageActionChatJoinedByLinkObject -> value.action.inviterId.let {
                        if (minUsers.containsKey(it)) minUsers[it] = value.toId to value.id
                    }
                    is MessageActionChatMigrateToObject -> value.action.channelId.let {
                        if (minChannels.containsKey(it)) minChannels[it] = value.toId to value.id
                    }
                }
            }
            is UpdateShortMessageObject -> {
                val toId = PeerUserObject(value.userId)
                (value.fwdFrom as? MessageFwdHeaderObject?)?.run {
                    fromId?.let {
                        if (minUsers.containsKey(it)) minUsers[it] = toId to value.id
                    }
                    channelId?.let {
                        if (minChannels.containsKey(it)) minChannels[it] = toId to value.id
                    }
                    when (savedFromPeer) {
                        is PeerUserObject -> {
                            savedFromPeer.userId.let {
                                if (minUsers.containsKey(it)) minUsers[it] = toId to value.id
                            }
                        }
                        is PeerChannelObject -> {
                            savedFromPeer.channelId.let {
                                if (minChannels.containsKey(it)) minChannels[it] = toId to value.id
                            }
                        }
                        else -> {
                        }
                    }
                }
                value.viaBotId?.let {
                    if (minUsers.containsKey(it)) minUsers[it] = toId to value.id
                }
            }
            is UpdateShortChatMessageObject -> {
                val toId = PeerChatObject(value.chatId)
                value.fromId.let {
                    if (minUsers.containsKey(it)) minUsers[it] = toId to value.id
                }
                (value.fwdFrom as? MessageFwdHeaderObject?)?.run {
                    fromId?.let {
                        if (minUsers.containsKey(it)) minUsers[it] = toId to value.id
                    }
                    channelId?.let {
                        if (minChannels.containsKey(it)) minChannels[it] = toId to value.id
                    }
                    when (savedFromPeer) {
                        is PeerUserObject -> {
                            savedFromPeer.userId.let {
                                if (minUsers.containsKey(it)) minUsers[it] = toId to value.id
                            }
                        }
                        is PeerChannelObject -> {
                            savedFromPeer.channelId.let {
                                if (minChannels.containsKey(it)) minChannels[it] = toId to value.id
                            }
                        }
                        else -> {
                        }
                    }
                }
                value.viaBotId?.let {
                    if (minUsers.containsKey(it)) minUsers[it] = toId to value.id
                }
            }
            else -> return true
        }
        return false // if we actually found a message, there can't be a nested message, so save time by not iterating
    }
}

class PtsGetter : TLWalker<Map<Int, Int>>() {
    override val result = mutableMapOf<Int, Int>()

    override fun handle(key: String, value: TLObject<*>?): Boolean {
        when (value) {
            is ChannelFullObject -> result[value.id] = value.pts
            is DialogObject -> value.pts?.let {
                result[(value.peer as PeerChannelObject).channelId] = it
            }
            is Messages_ChannelMessagesObject -> {
                // TODO do something?
            }
        }
        return true
    }
}



val UpdatesType.date
    get() = when (this) {
        is UpdateShortMessageObject -> date
        is UpdateShortChatMessageObject -> date
        is UpdateShortObject -> date
        is UpdatesCombinedObject -> date
        is UpdatesObject -> date
        is UpdateShortSentMessageObject -> date
        else -> null
    }
val UpdatesType.seq
    get() = when (this) {
        is UpdatesCombinedObject -> seq
        is UpdatesObject -> seq
        else -> null
    }
val UpdatesType.seqStart
    get() = when (this) {
        is UpdatesCombinedObject -> seqStart
        is UpdatesObject -> seq
        else -> null
    }
val UpdateType.channelId
    get() = when (this) {
        is UpdateNewChannelMessageObject -> (message.toId as? PeerChannelObject)?.channelId
        is UpdateChannelTooLongObject -> channelId
        is UpdateReadChannelInboxObject -> channelId
        is UpdateDeleteChannelMessagesObject -> channelId
        is UpdateEditChannelMessageObject -> (message.toId as? PeerChannelObject)?.channelId
        is UpdateChannelWebPageObject -> channelId
        else -> null
    }
val UpdateType.pts
    get() = when (this) {
        is UpdateNewChannelMessageObject -> pts
        is UpdateNewMessageObject -> pts
        is UpdateDeleteMessagesObject -> pts
        is UpdateReadHistoryInboxObject -> pts
        is UpdateReadHistoryOutboxObject -> pts
        is UpdateWebPageObject -> pts
        is UpdateReadMessagesContentsObject -> pts
        is UpdateChannelTooLongObject -> pts
        is UpdateReadChannelInboxObject -> pts
        is UpdateDeleteChannelMessagesObject -> pts
        is UpdateEditChannelMessageObject -> pts
        is UpdateEditMessageObject -> pts
        is UpdateChannelWebPageObject -> pts
        is UpdateFolderPeersObject -> pts
        else -> null
    }
val UpdateType.ptsCount
    get() = when (this) {
        is UpdateNewChannelMessageObject -> ptsCount
        is UpdateNewMessageObject -> ptsCount
        is UpdateDeleteMessagesObject -> ptsCount
        is UpdateReadHistoryInboxObject -> ptsCount
        is UpdateReadHistoryOutboxObject -> ptsCount
        is UpdateWebPageObject -> ptsCount
        is UpdateReadMessagesContentsObject -> ptsCount
        is UpdateChannelTooLongObject -> null
        is UpdateReadChannelInboxObject -> 0
        is UpdateDeleteChannelMessagesObject -> ptsCount
        is UpdateEditChannelMessageObject -> ptsCount
        is UpdateEditMessageObject -> ptsCount
        is UpdateChannelWebPageObject -> ptsCount
        is UpdateFolderPeersObject -> ptsCount
        else -> null
    }

sealed class UpdateOrSkipped(open val update: ActualOrSyntheticUpdate?) {
    abstract suspend fun commit()
}

class Update(override val update: ActualOrSyntheticUpdate, private val onCommit: suspend () -> Unit) : UpdateOrSkipped(update) {
    override suspend fun commit() = onCommit()

    // TODO: KT-42807
    override fun equals(other: Any?): Boolean {
        if (other !is Update)
            return false
        return other.update == update
    }

    override fun hashCode() = update.hashCode()

    override fun toString(): String {
        return "Update(update=$update)"
    }
}

data class Skipped(val channelId: Int?, private val onCommit: suspend () -> Unit) : UpdateOrSkipped(null) {
    override suspend fun commit() = onCommit()

    // TODO: KT-42807
    override fun equals(other: Any?): Boolean {
        if (other !is Skipped)
            return false
        return other.channelId == channelId
    }

    override fun hashCode() = channelId.hashCode()

    override fun toString(): String {
        return "Skipped(channelId=$channelId)"
    }
}

sealed class ActualOrSyntheticUpdate {
    abstract val pts: Int?
    abstract val ptsCount: Int?
    abstract val channelId: Int?
}
data class ActualUpdate(val update: UpdateType) : ActualOrSyntheticUpdate() {
    override val pts get() = update.pts
    override val ptsCount get() = update.ptsCount
    override val channelId = update.channelId
}

sealed class SyntheticUpdate : ActualOrSyntheticUpdate() {
    abstract val originalRequest: TLMethod<*>
    abstract val result: TLObject<*>
    abstract override val pts: Int

    companion object {
        suspend inline operator fun <T : TLObject<*>>invoke(originalRequest: TLMethod<T>, result: T, client: TelegramClient) = when (originalRequest) {
            is Messages_DeleteHistoryRequest -> HistoryDeletedSyntheticUpdate(originalRequest, result as Messages_AffectedHistoryType)
            is Messages_ReadMentionsRequest -> MentionsReadSyntheticUpdate(originalRequest, result as Messages_AffectedHistoryType)
            is Channels_DeleteUserHistoryRequest -> DeleteUserHistorySyntheticUpdate(originalRequest, result as Messages_AffectedHistoryType)
            is Messages_ReadHistoryRequest -> {
                result as Messages_AffectedMessagesObject
                val peer = when (originalRequest.peer) {
                    is InputPeerEmptyObject -> error("Empty peer was accepted by server for $originalRequest -> $result")
                    is InputPeerSelfObject -> PeerUserObject(client.getInputMe().userId)
                    is InputPeerChatObject -> PeerChatObject(originalRequest.peer.chatId)
                    is InputPeerUserObject -> PeerUserObject(originalRequest.peer.userId)
                    is InputPeerChannelObject -> PeerChannelObject(originalRequest.peer.channelId)
                    is InputPeerUserFromMessageObject -> PeerUserObject(originalRequest.peer.userId)
                    is InputPeerChannelFromMessageObject -> PeerChannelObject(originalRequest.peer.channelId)
                }
                ActualUpdate(UpdateReadHistoryOutboxObject(peer, originalRequest.maxId, result.pts, result.ptsCount))
            }
            is Messages_DeleteMessagesRequest -> {
                result as Messages_AffectedMessagesObject
                ActualUpdate(UpdateDeleteMessagesObject(originalRequest.id, result.pts, result.ptsCount))
            }
            is Messages_ReadMessageContentsRequest -> {
                result as Messages_AffectedMessagesObject
                ActualUpdate(UpdateReadMessagesContentsObject(originalRequest.id, result.pts, result.ptsCount))
            }
            is Channels_DeleteMessagesRequest -> {
                result as Messages_AffectedMessagesObject
                val channelId = when (originalRequest.channel) {
                    is InputChannelEmptyObject -> error("Empty channel was accepted by server for $originalRequest -> $result")
                    is InputChannelObject -> originalRequest.channel.channelId
                    is InputChannelFromMessageObject -> originalRequest.channel.channelId
                }
                ActualUpdate(UpdateDeleteChannelMessagesObject(channelId, originalRequest.id, result.pts, result.ptsCount))
            }
            else -> error("Invalid synthetic update $originalRequest -> $result")
        }
    }
}

data class HistoryDeletedSyntheticUpdate(override val originalRequest: Messages_DeleteHistoryRequest, override val result: Messages_AffectedHistoryType) : SyntheticUpdate() {
    override val pts get() = (result as Messages_AffectedHistoryObject).pts
    override val ptsCount get() = (result as Messages_AffectedHistoryObject).ptsCount
    override val channelId get() = when (originalRequest.peer) {
        is InputPeerEmptyObject -> null
        is InputPeerSelfObject -> null
        is InputPeerChatObject -> null
        is InputPeerUserObject -> null
        is InputPeerChannelObject -> originalRequest.peer.channelId
        is InputPeerUserFromMessageObject -> null
        is InputPeerChannelFromMessageObject -> originalRequest.peer.channelId
    }
}
data class MentionsReadSyntheticUpdate(override val originalRequest: Messages_ReadMentionsRequest, override val result: Messages_AffectedHistoryType) : SyntheticUpdate() {
    override val pts get() = (result as Messages_AffectedHistoryObject).pts
    override val ptsCount get() = (result as Messages_AffectedHistoryObject).ptsCount
    override val channelId get() = when (originalRequest.peer) {
        is InputPeerEmptyObject -> null
        is InputPeerSelfObject -> null
        is InputPeerChatObject -> null
        is InputPeerUserObject -> null
        is InputPeerChannelObject -> originalRequest.peer.channelId
        is InputPeerUserFromMessageObject -> null
        is InputPeerChannelFromMessageObject -> originalRequest.peer.channelId
    }
}
data class DeleteUserHistorySyntheticUpdate(override val originalRequest: Channels_DeleteUserHistoryRequest, override val result: Messages_AffectedHistoryType) : SyntheticUpdate() {
    override val pts get() = (result as Messages_AffectedHistoryObject).pts
    override val ptsCount get() = (result as Messages_AffectedHistoryObject).ptsCount
    override val channelId get() = when (originalRequest.channel) {
        is InputChannelEmptyObject -> null
        is InputChannelObject -> originalRequest.channel.channelId
        is InputChannelFromMessageObject -> originalRequest.channel.channelId
    }
}

private val MessageType.toId: dev.hack5.telekram.core.tl.PeerType?
    get() = when (this) {
        is MessageEmptyObject -> null
        is MessageObject -> toId
        is MessageServiceObject -> toId
    }
