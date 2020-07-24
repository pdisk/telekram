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

package tk.hack5.telekram.core.updates

import com.github.aakira.napier.Napier
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import tk.hack5.telekram.core.client.TelegramClient
import tk.hack5.telekram.core.errors.BadRequestError
import tk.hack5.telekram.core.state.UpdateState
import tk.hack5.telekram.core.tl.*
import tk.hack5.telekram.core.utils.*

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
    TLWalker<Triple<MutableMap<String, MutableMap<Long, Long>>, MutableMap<Int, Pair<tk.hack5.telekram.core.tl.PeerType, Int>?>, MutableMap<Int, Pair<tk.hack5.telekram.core.tl.PeerType, Int>?>>>() {
    override val result get() = Triple(map, minUsers, minChannels)
    val map = mutableMapOf<String, MutableMap<Long, Long>>()
    val minUsers = mutableMapOf<Int, Pair<tk.hack5.telekram.core.tl.PeerType, Int>?>()
    val minChannels = mutableMapOf<Int, Pair<tk.hack5.telekram.core.tl.PeerType, Int>?>()

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
                if (!value.min) {
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
    val minUsers: MutableMap<Int, Pair<tk.hack5.telekram.core.tl.PeerType, Int>?>,
    val minChannels: MutableMap<Int, Pair<tk.hack5.telekram.core.tl.PeerType, Int>?>
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

interface UpdateHandler {
    suspend fun getEntities(value: TLObject<*>, forUpdate: Boolean): Map<String, MutableMap<Long, Long>>
    suspend fun handleUpdates(update: TLObject<*>)
    val updates: Channel<UpdateOrSkipped>
    suspend fun catchUp()
}


open class UpdateHandlerImpl(
    protected val scope: CoroutineScope,
    protected val updateState: UpdateState,
    val client: TelegramClient,
    protected val maxDifference: Int? = null,
    val maxChannelDifference: Int = 100
) : BaseActor(scope), UpdateHandler {
    // WARNING: do NOT invoke any request that returns any type handled by [PtsWalker] while locked in act {} without skipEntities=true
    val pendingUpdatesSeq = mutableMapOf<Int, CompletableJob>()
    val pendingUpdatesPts = mutableMapOf<Pair<Int?, Int>, CompletableJob>()

    override val updates = Channel<UpdateOrSkipped>(Channel.UNLIMITED)

    override suspend fun handleUpdates(update: TLObject<*>) {
        if (update is UpdatesType) handleUpdates(update)
    }

    override suspend fun getEntities(value: TLObject<*>, forUpdate: Boolean): Map<String, MutableMap<Long, Long>> {
        val (ret, minUsers, minChannels) = AccessHashGetter().walk(value)!!
        MinGetter(minUsers, minChannels).walk(value)
        if (!forUpdate) {
            val pts = PtsGetter().walk(value)!!
            if (pts.isNotEmpty()) {
                act {
                    updateState.pts.putAll(pts)
                }
            }
        }
        val users = minUsers.map {
            val inputPeer = try {
                it.value?.first?.toInputPeer(client) ?: return@map null
            } catch (e: EntityNotFoundException) {
                return@map null
            }
            InputUserFromMessageObject(inputPeer, it.value!!.second, it.key)
        }.filterNotNull()
        val channels = minChannels.map {
            val inputPeer = try {
                it.value?.first?.toInputPeer(client) ?: return@map null
            } catch (e: EntityNotFoundException) {
                return@map null
            }
            InputChannelFromMessageObject(inputPeer, it.value!!.second, it.key)
        }.filterNotNull()

        // we will be called for the result but it wont be min
        if (users.isNotEmpty())
            try {
                client(Users_GetUsersRequest(users), forUpdate = true) // TODO handle slices
            } catch(e: BadRequestError.MsgIdInvalidError) {
                // TODO divide-and-conquer to get the ones that do work
            }
        if (channels.isNotEmpty())
            try {
                client(Channels_GetChannelsRequest(channels), forUpdate = true) // TODO handle slices
            } catch(e: BadRequestError.MsgIdInvalidError) {
                // TODO divide-and-conquer to get the ones that do work
            }
        return ret
    }

    protected suspend fun handleUpdates(
        updates: UpdatesType,
        skipChecks: Boolean = false,
        skipDispatch: Boolean = false
    ) {
        Napier.d({ "got updates $updates" }, tag = tag)
        var refetch: Int? = null
        val innerUpdates = when (updates) {
            is UpdatesTooLongObject -> {
                if (!skipChecks)
                    fetchUpdates()
                return
            }
            is UpdateShortMessageObject -> {
                try {
                    PeerUserObject(updates.userId).toInputUser(client)
                } catch (e: Exception) {
                    refetch = updates.userId
                }
                listOf(
                    UpdateNewMessageObject(
                        MessageObject(
                            out = updates.out,
                            mentioned = updates.mentioned,
                            mediaUnread = updates.mediaUnread,
                            silent = updates.silent,
                            post = false,
                            fromScheduled = false,
                            legacy = false,
                            editHide = false,
                            id = updates.id,
                            fromId = if (updates.out) client.getInputMe().userId else updates.userId,
                            toId = PeerUserObject(if (updates.out) updates.userId else client.getInputMe().userId),
                            fwdFrom = updates.fwdFrom,
                            viaBotId = updates.viaBotId,
                            replyToMsgId = updates.replyToMsgId,
                            date = updates.date,
                            message = updates.message,
                            media = null,
                            replyMarkup = null,
                            entities = updates.entities
                        ), updates.pts, updates.ptsCount
                    )
                )
            }
            is UpdateShortChatMessageObject -> {
                if (client.getAccessHash(PeerType.USER, updates.fromId) == null) {
                    refetch = updates.fromId
                }
                listOf(
                    UpdateNewMessageObject(
                        MessageObject(
                            out = updates.out,
                            mentioned = updates.mentioned,
                            mediaUnread = updates.mediaUnread,
                            silent = updates.silent,
                            post = false,
                            fromScheduled = false,
                            legacy = false,
                            editHide = false,
                            id = updates.id,
                            fromId = updates.fromId,
                            toId = PeerChatObject(updates.chatId),
                            fwdFrom = updates.fwdFrom,
                            viaBotId = updates.viaBotId,
                            replyToMsgId = updates.replyToMsgId,
                            date = updates.date,
                            message = updates.message,
                            media = null,
                            replyMarkup = null,
                            entities = updates.entities
                        ), updates.pts, updates.ptsCount
                    )
                )
            }
            is UpdateShortObject -> listOf(updates.update)
            is UpdatesCombinedObject -> updates.updates
            is UpdatesObject -> updates.updates
            is UpdateShortSentMessageObject -> return // handled by rpc caller
        }.filter {
            if (it is UpdateChannelTooLongObject) {
                if (skipChecks)
                    fetchChannelUpdatesLocked(it.channelId)
                else
                    fetchChannelUpdates(it.channelId)
                false
            } else {
                true
            }
        }

        if (!skipChecks) {
            act {
                updates.date?.let { checkDateLocked(it) }
            }
        }
        val (hasPts, hasNoPts) = innerUpdates.partition { it.pts != null }
        for (update in hasPts) {
            val pts = update.pts!!
            val ptsCount = update.ptsCount
            if (!skipChecks) {
                val (applicablePts, job) = act {
                    val localPts = updateState.pts[update.channelId]
                    val applicablePts = pts - ptsCount!!

                    val job = when {
                        (ptsCount == 0 && pts >= localPts?.minus(1) ?: 0)
                                || applicablePts == 0 || skipChecks -> {
                            // update doesn't need to change the pts
                            handleSinglePtsLocked(refetch, applicablePts, update, true, skipDispatch)
                            null
                        }
                        applicablePts == localPts || localPts == null -> {
                            if (update is UpdateNewMessageObject) {
                                Napier.d("$refetch, $applicablePts, $update, false")
                            }
                            handleSinglePtsLocked(refetch, applicablePts, update, false, skipDispatch)
                            null
                        }
                        applicablePts < localPts -> {
                            Napier.d("Duplicate update $update (localPts=$localPts)", tag = tag)
                            null
                        }
                        else -> {
                            val job = Job()
                            pendingUpdatesPts[update.channelId to applicablePts] = job
                            job
                        }
                    }
                    Pair(applicablePts, job)
                }
                job?.let {
                    Napier.d("Waiting for update with pts=$applicablePts, channelId=${update.channelId}")
                    val join = withTimeoutOrNull(500) {
                        it.join()
                    }
                    pendingUpdatesPts.remove(update.channelId to applicablePts)
                    if (join == null) {
                        if (update.channelId != null) {
                            fetchChannelUpdates(update.channelId!!)
                        } else {
                            fetchUpdates()
                        }
                        return // server will resend this update too
                    }

                    act {
                        handleSinglePtsLocked(refetch, applicablePts, update, false, skipDispatch)
                    }
                }
            } else {
                handleSinglePtsLocked(refetch, 0 /* we always have the full update */, update, true, skipDispatch)
            }
        }
        if (!skipChecks) {
            val (localSeq, applicableSeq, job) = act {
                val applicableSeq = updates.seqStart?.minus(1)
                val localSeq = updateState.seq
                val job = when {
                    applicableSeq == null || applicableSeq == -1 || skipChecks -> {
                        // update order doesn't matter
                        handleSingleSeqLocked(hasNoPts, updates, true, skipDispatch)
                        null
                    }
                    applicableSeq == localSeq -> {
                        handleSingleSeqLocked(hasNoPts, updates, false, skipDispatch)
                        null
                    }
                    applicableSeq < localSeq -> {
                        Napier.d("Duplicate updates $updates (localSeq=$localSeq)", tag = tag)
                        null
                    }
                    else -> {
                        val job = Job()
                        pendingUpdatesSeq[applicableSeq] = job
                        job
                    }
                }
                Triple(localSeq, applicableSeq, job)
            }
            job?.let {
                Napier.d("Waiting for update with seq=$applicableSeq (current=$localSeq, updates=$updates)", tag = tag)
                val join = withTimeoutOrNull(500) {
                    it.join()
                }
                if (join == null) {
                    act {
                        pendingUpdatesSeq.remove(applicableSeq)
                    }
                    fetchUpdates()
                    return // server will resend this update too
                }
                act {
                    handleSingleSeqLocked(hasNoPts, updates, false, skipDispatch)
                }
            }
        } else {
            handleSingleSeqLocked(hasNoPts, updates, true, skipDispatch)
        }
    }

    protected suspend fun handleSinglePtsLocked(
        refetch: Int?,
        applicablePts: Int?,
        update: UpdateType,
        skipPts: Boolean,
        skipDispatch: Boolean
    ) {
        refetch?.let {
            if (client.getAccessHash(PeerType.USER, it) == null) {
                fetchHashes(applicablePts!!, update.ptsCount ?: 1)
            }
        }
        if (!skipDispatch)
            dispatchUpdate(update)
        if (!skipPts) {
            Napier.d("setting pts to ${update.pts}")
            updateState.pts[update.channelId] = update.pts!!
            pendingUpdatesPts[update.channelId to update.pts!!]?.complete()
        }
    }

    protected fun handleSingleSeqLocked(
        hasNoPts: List<UpdateType>,
        updates: UpdatesType,
        skipSeq: Boolean,
        skipDispatch: Boolean
    ) {
        if (!skipDispatch) {
            for (update in hasNoPts) {
                dispatchUpdate(update)
            }
        }
        if (!skipSeq) {
            updateState.seq = updates.seq!!
            updates.date?.let { checkDateLocked(it) }
            pendingUpdatesSeq[updates.seq!!]?.complete()
        }
    }

    protected suspend fun fetchHashes(fromPts: Int, limit: Int) {
        client(
            Updates_GetDifferenceRequest(
                fromPts,
                limit,
                updateState.date,
                updateState.qts
            ), forUpdate = true
        )
        // no matter the result, we can't do anything about it
    }

    override suspend fun catchUp() = fetchUpdates()

    protected fun checkDateLocked(date: Int) {
        if (date > updateState.date)
            updateState.date = date
    }

    protected fun dispatchUpdate(update: UpdateType) {
        Napier.d("dispatching update $update")
        check(updates.offer(Update(update))) { "Failed to offer update" }
    }

    protected suspend fun fetchUpdates() {
        val updates = mutableListOf<UpdateType>()
        var tmpState: Updates_StateObject? = null
        act {
            loop@while (true) {
                val seqStart = (tmpState?.seq ?: updateState.seq) + 1
                val difference = client(
                    Updates_GetDifferenceRequest(
                        tmpState?.pts ?: updateState.pts[null]!!,
                        maxDifference,
                        tmpState?.date ?: updateState.date,
                        tmpState?.qts ?: updateState.qts
                    ), forUpdate = true
                )
                Napier.d("difference=$difference")
                when (difference) {
                    is Updates_DifferenceObject -> {
                        val state = difference.state as Updates_StateObject
                        handleUpdates(
                            UpdatesCombinedObject(
                                updates + generateUpdates(
                                    difference.otherUpdates,
                                    difference.newMessages,
                                    difference.newEncryptedMessages,
                                    ::UpdateNewMessageObject
                                ),
                                difference.users,
                                difference.chats,
                                state.date,
                                seqStart,
                                state.seq
                            ), true
                        )
                        updateState.date = state.date
                        updateState.pts[null] = state.pts
                        updateState.qts = state.qts
                        updateState.seq = state.seq
                        break@loop
                    }
                    is Updates_DifferenceSliceObject -> {
                        tmpState = difference.intermediateState as Updates_StateObject
                        updates += generateUpdates(
                            difference.otherUpdates,
                            difference.newMessages,
                            difference.newEncryptedMessages,
                            ::UpdateNewMessageObject
                        )
                    }
                    is Updates_DifferenceEmptyObject -> {
                        updateState.seq = difference.seq
                        updateState.date = difference.date
                        break@loop
                    }
                    is Updates_DifferenceTooLongObject -> {
                        require(this.updates.offer(Skipped(null))) { "Failed to offer drop message" }
                        updateState.pts[null] = difference.pts
                        break@loop
                    }
                }
            }
        }
    }

    protected suspend fun fetchChannelUpdates(channelId: Int) {
        val inputChannel = PeerChannelObject(channelId).toInputChannel(client)
        while (true) {
            val ret = act {
                fetchChannelUpdatesInnerLocked(channelId, inputChannel)
            }
            if (ret) break
        }
    }

    protected suspend fun fetchChannelUpdatesLocked(channelId: Int) {
        val inputChannel = PeerChannelObject(channelId).toInputChannel(client)
        while (true) {
            val ret = fetchChannelUpdatesInnerLocked(channelId, inputChannel)
            if (ret) break
        }
    }

    protected suspend fun fetchChannelUpdatesInnerLocked(channelId: Int, inputChannel: InputChannelType): Boolean {
        val pts = updateState.pts[channelId]
        if (pts == null) {
            updateState.pts[channelId] =
                ((client(Channels_GetFullChannelRequest(inputChannel), forUpdate = true) as Messages_ChatFullObject).fullChat as ChannelFullObject).pts
            return true
        }
        val result = client(
            Updates_GetChannelDifferenceRequest(
                true,
                inputChannel,
                ChannelMessagesFilterEmptyObject(),
                pts,
                maxChannelDifference
            ), forUpdate = true
        )
        Napier.d("difference = $result")
        when (result) {
            is Updates_ChannelDifferenceEmptyObject -> return result.final
            is Updates_ChannelDifferenceObject -> {
                handleUpdates(
                    UpdatesObject(
                        generateUpdates(
                            result.otherUpdates,
                            result.newMessages,
                            listOf(),
                            ::UpdateNewChannelMessageObject
                        ),
                        result.users,
                        result.chats,
                        0,
                        0
                    ), true
                )
                updateState.pts[channelId] =
                    result.pts // updates sent in the difference have wrong pts, but are sorted
                if (result.final) {
                    return true
                }
            }
            is Updates_ChannelDifferenceTooLongObject -> {
                require(updates.offer(Skipped(channelId))) { "Failed to offer drop message" }
                updateState.pts[channelId] = (result.dialog as DialogObject).pts!!
                return true
            }
        }
        return false
    }

    protected fun generateUpdates(
        otherUpdates: List<UpdateType>,
        newMessages: List<MessageType>,
        newEncryptedMessages: List<EncryptedMessageType>,
        constructor: (MessageType, Int, Int, Boolean) -> UpdateType
    ): List<UpdateType> =
        newMessages.map {
            constructor(
                it,
                0,
                0,
                false
            )
        } + newEncryptedMessages.map { UpdateNewEncryptedMessageObject(it, 0) } + otherUpdates


    private val UpdatesType.date
        get() = when (this) {
            is UpdateShortMessageObject -> date
            is UpdateShortChatMessageObject -> date
            is UpdateShortObject -> date
            is UpdatesCombinedObject -> date
            is UpdatesObject -> date
            is UpdateShortSentMessageObject -> date
            else -> null
        }
    private val UpdatesType.seq
        get() = when (this) {
            is UpdatesCombinedObject -> seq
            is UpdatesObject -> seq
            else -> null
        }
    private val UpdatesType.seqStart
        get() = when (this) {
            is UpdatesCombinedObject -> seqStart
            is UpdatesObject -> seq
            else -> null
        }
    private val UpdateType.channelId
        get() = when (this) {
            is UpdateNewChannelMessageObject -> (message.toId as PeerChannelObject).channelId
            is UpdateChannelTooLongObject -> channelId
            is UpdateReadChannelInboxObject -> channelId
            is UpdateDeleteChannelMessagesObject -> channelId
            is UpdateEditChannelMessageObject -> (message.toId as PeerChannelObject).channelId
            is UpdateChannelWebPageObject -> channelId
            else -> null
        }
    private val UpdateType.pts
        get() = when (this) {
            is UpdateNewChannelMessageObject -> pts
            is UpdateNewMessageObject -> pts
            is UpdateDeleteMessagesObject -> pts
            is UpdateReadHistoryInboxObject -> pts
            is UpdateReadHistoryOutboxObject -> pts
            is UpdateWebPageObject -> pts
            is UpdateReadMessagesContentsObject -> pts
            is UpdateChannelTooLongObject -> pts
            is UpdateReadChannelInboxObject -> pts - 1 // this one is messed up, but -1 seems to fix it
            is UpdateDeleteChannelMessagesObject -> pts
            is UpdateEditChannelMessageObject -> pts
            is UpdateEditMessageObject -> pts
            is UpdateChannelWebPageObject -> pts
            is UpdateFolderPeersObject -> pts
            else -> null
        }
    private val UpdateType.ptsCount
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
}

sealed class UpdateOrSkipped(open val update: UpdateType?)
data class Update(override val update: UpdateType) : UpdateOrSkipped(update)
data class Skipped(val channelId: Int?) : UpdateOrSkipped(null)

private val MessageType.toId: tk.hack5.telekram.core.tl.PeerType?
    get() = when (this) {
        is MessageEmptyObject -> null
        is MessageObject -> toId
        is MessageServiceObject -> toId
    }

private const val tag = "UpdateHandler"