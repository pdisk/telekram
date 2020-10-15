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