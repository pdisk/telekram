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

import com.github.aakira.napier.Napier
import dev.hack5.telekram.core.client.TelegramClient
import dev.hack5.telekram.core.errors.BadRequestError
import dev.hack5.telekram.core.state.UpdateState
import dev.hack5.telekram.core.tl.*
import dev.hack5.telekram.core.utils.*
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

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
    // TODO: implement qts stuff
    // IMPORTANT: DO NOT invoke any request that returns any type handled by [PtsWalker] while locked in act {} without forUpdate=true
    // IMPORTANT: Also, if the code will result in a recursive network request, pass skipEntities=true (exclusive with forUpdate)

    // updates we are waiting on the server for
    protected val pendingUpdatesSeq = mutableMapOf<Int, CompletableJob>()
    protected val pendingUpdatesPts = mutableMapOf<Pair<Int?, Int>, CompletableJob>()

    // in-memory seq numbers, stored to disk when .commit() is called
    protected var updatesSeq = updateState.seq
    protected var updatesQts = updateState.qts // TODO
    protected val updatesPts = updateState.pts.toMutableMap() // copy
    protected var updatesDate = updateState.date

    // updates we are waiting on the client for
    protected val processingUpdatesSeq = mutableMapOf<Int, CompletableJob>()
    protected val processingUpdatesPts = mutableMapOf<Pair<Int?, Int>, CompletableJob>()

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
                    pts.forEach {
                        if (updatesPts.containsKey(it.key)) {
                            // if pts is unknown, add to both cache and storage
                            updatesPts[it.key] = it.value
                            updateState.pts[it.key] = it.value
                        }
                    }
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
        skipDispatch: Boolean = false,
        endPts: Int? = null
    ) {
        Napier.d({ "got updates $updates" })
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
        val totalUpdated = endPts?.let { BatchUpdateState(hasPts.size, it) }
        for (update in hasPts) {
            val pts = update.pts!!
            val ptsCount = update.ptsCount
            val applicablePts = pts - ptsCount!!
            if (!skipChecks) {
                val job = act {
                    val localPts = updatesPts[update.channelId]

                    when {
                        (ptsCount == 0 && pts >= localPts?.minus(1) ?: 0)
                                || applicablePts == 0 || skipChecks -> {
                            // update doesn't need to change the pts
                            handleSinglePtsLocked(refetch, null, true, update, true, skipDispatch)
                            null
                        }
                        applicablePts == localPts || localPts == null -> {
                            handleSinglePtsLocked(refetch, applicablePts, false, update, false, skipDispatch)
                            null
                        }
                        applicablePts < localPts -> {
                            Napier.d("Duplicate update $update (localPts=$localPts)")
                            null
                        }
                        else -> {
                            pendingUpdatesPts.getOrPut(update.channelId to applicablePts, ::Job)
                        }
                    }
                }
                job?.let {
                    Napier.d("Waiting for update with pts=$applicablePts, channelId=${update.channelId}")
                    val join = withTimeoutOrNull(500) {
                        it.join()
                    }
                    act {
                        pendingUpdatesPts.remove(update.channelId to applicablePts)
                    }
                    if (join == null) {
                        if (update.channelId != null) {
                            fetchChannelUpdates(update.channelId!!)
                        } else {
                            fetchUpdates()
                        }
                        return // server will resend this update too
                    }

                    act {
                        handleSinglePtsLocked(refetch, applicablePts, false, update, false, skipDispatch)
                    }
                }
            } else {
                handleSinglePtsLocked(
                    refetch,
                    null,
                    false,
                    update,
                    true,
                    skipDispatch,
                    totalUpdated
                )
            }
        }
        val applicableSeq = updates.seqStart?.minus(1)
        if (!skipChecks) {
            val (localSeq, job) = act {
                val localSeq = updatesSeq
                val job = when {
                    applicableSeq == null || applicableSeq == -1 -> {
                        // update order doesn't matter
                        handleSingleSeqLocked(hasNoPts, null, updates, true, skipDispatch)
                        null
                    }
                    applicableSeq == localSeq -> {
                        handleSingleSeqLocked(hasNoPts, applicableSeq, updates, false, skipDispatch)
                        null
                    }
                    applicableSeq < localSeq -> {
                        Napier.d("Duplicate updates $updates (localSeq=$localSeq)")
                        null
                    }
                    else -> {
                        val job = Job()
                        pendingUpdatesSeq[applicableSeq] = job
                        job
                    }
                }
                localSeq to job
            }
            job?.let {
                Napier.d("Waiting for update with seq=$applicableSeq (current=$localSeq, updates=$updates)")
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
                    pendingUpdatesSeq.remove(applicableSeq)
                    handleSingleSeqLocked(hasNoPts, applicableSeq!!, updates, false, skipDispatch)
                }
            }
        } else {
            handleSingleSeqLocked(hasNoPts, applicableSeq, updates, true, skipDispatch)
        }
    }

    protected suspend fun handleSinglePtsLocked(
        refetch: Int?,
        applicablePts: Int?,
        commitNoOp: Boolean,
        update: UpdateType,
        skipPts: Boolean,
        skipDispatch: Boolean,
        totalUpdated: BatchUpdateState? = null
    ) {
        refetch?.let {
            // cannot refetch if in catchup, but that's fine as it always sends the whole thing
            if (client.getAccessHash(PeerType.USER, it) == null) {
                fetchHashes(applicablePts!!, update.ptsCount ?: 1)
            }
        }
        val onCommit: suspend () -> Unit = when {
            commitNoOp -> {
                { }
            }
            applicablePts != null -> {
                {
                    val job = act {
                        val actualPts = updateState.pts[update.channelId]
                        when {
                            actualPts == null || actualPts > applicablePts -> null
                            actualPts < applicablePts -> {
                                Napier.v("Waiting to commit pts ${update.channelId} $applicablePts $actualPts ${update.pts} ${update.ptsCount}")
                                processingUpdatesPts.getOrPut(update.channelId to applicablePts, ::Job)
                            }
                            else -> {
                                Napier.v("Not waiting to commit pts ${update.channelId} $applicablePts $actualPts ${update.pts} ${update.ptsCount}")
                                commitPts(update, update.pts!!)
                                null
                            }
                        }
                    }
                    job?.let {
                        job.join()
                        Napier.v("Waiting finished to commit pts ${update.channelId} $applicablePts ${update.pts} ${update.ptsCount}")
                        act {
                            processingUpdatesPts.remove(update.channelId to applicablePts)
                            commitPts(update, update.pts!!)
                        }
                    }
                }
            }
            totalUpdated != null -> {
                {
                    act {
                        if (++totalUpdated.current == totalUpdated.total) {
                            Napier.v("Committing pts for batch ${update.channelId} $applicablePts ${update.pts} ${update.ptsCount} $totalUpdated")
                            commitPts(update, totalUpdated.end)
                        }
                    }
                }
            }
            else -> {
                {
                    act {
                        Napier.v("Committing pts ${update.channelId} $applicablePts ${update.pts} ${update.ptsCount}")
                        commitPts(update, update.pts!!)
                        Napier.v("Finished commit")
                    }
                }
            }
        }
        if (!skipDispatch) {
            dispatchUpdate(
                update,
                onCommit::invoke
            )
        } else {
            onCommit()
        }
        if (!skipPts) {
            Napier.d("Setting pts to ${update.pts} ($skipDispatch)")
            updatesPts[update.channelId] = update.pts!!
            pendingUpdatesPts[update.channelId to update.pts!!]?.complete()
        }
    }

    protected suspend fun commitPts(update: UpdateType, pts: Int) {
        println("start commit")
        updateState.pts[update.channelId] = pts
        processingUpdatesPts.filterKeys { it.first == update.channelId && it.second <= pts }.forEach {
            // TODO: this is ugly, refactor processingUpdatesPts to a map?
            it.value.complete()
        }
        println("end commit")
    }

    protected suspend fun handleSingleSeqLocked(
        hasNoPts: List<UpdateType>,
        applicableSeq: Int?,
        updates: UpdatesType,
        skipSeq: Boolean,
        skipDispatch: Boolean
    ) {
        val onCommit: suspend () -> Unit = if (applicableSeq != null) {
            {
                val job = act {
                    when {
                        updateState.seq > applicableSeq -> null
                        updateState.seq < applicableSeq -> {
                            Napier.v("Waiting to commit seq $applicableSeq ${updateState.seq} ${updates.seq}")
                            processingUpdatesSeq.getOrPut(applicableSeq, ::Job)
                        }
                        else -> {
                            commitSeq(updates)
                            null
                        }
                    }
                }
                job?.let {
                    job.join()
                    Napier.v("Waiting finished to commit seq $applicableSeq ${updateState.seq} ${updates.seq}")
                    act {
                        processingUpdatesSeq.remove(applicableSeq)
                        commitSeq(updates)
                    }
                }
            }
        } else {
            {}
        }
        if (!skipDispatch) {
            for (update in hasNoPts) {
                dispatchUpdate(
                    update,
                    onCommit::invoke
                )
            }
        } else {
            onCommit()
        }
        if (!skipSeq) {
            updatesSeq = updates.seq!!
            updates.date?.let { checkDateLocked(it) }
            pendingUpdatesSeq[updates.seq!!]?.complete()
        }
    }

    protected suspend fun commitSeq(updates: UpdatesType) {
        updateState.seq = updates.seq!!
        updates.date?.let { checkDateLockedCommit(it) }
        processingUpdatesSeq[updates.seq!!]?.complete()
    }

    protected suspend fun fetchHashes(fromPts: Int, limit: Int) {
        client(
            Updates_GetDifferenceRequest(
                fromPts,
                limit,
                updatesDate,
                updatesQts
            ), forUpdate = true
        )
        // no matter the result, we can't do anything about it
    }

    override suspend fun catchUp() = fetchUpdates()

    protected fun checkDateLocked(date: Int) {
        if (date > updatesDate)
            updatesDate = date
    }

    protected fun checkDateLockedCommit(date: Int) {
        if (date > updatesDate)
            updateState.date = date
    }

    protected fun dispatchUpdate(update: UpdateType, onCommit: suspend () -> Unit) {
        Napier.d("dispatching update $update")
        check(updates.offer(Update(update, onCommit))) { "Failed to offer update" }
    }

    protected suspend fun fetchUpdates() {
        val updates = mutableListOf<UpdateType>()
        var tmpState: Updates_StateObject? = null
        act {
            loop@while (true) {
                val seqStart = (tmpState?.seq ?: updatesSeq) + 1
                val previousPts = tmpState?.pts ?: (updatesPts[null]!! - 1)
                val difference = client(
                    Updates_GetDifferenceRequest(
                        previousPts,
                        maxDifference,
                        tmpState?.date ?: updatesDate,
                        tmpState?.qts ?: (updatesQts - 1)
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
                            ), true, endPts = state.pts
                        )
                        updatesDate = state.date
                        updatesPts[null] = state.pts
                        updatesQts = state.qts
                        updatesSeq = state.seq
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
                        updatesSeq = difference.seq
                        updatesDate = difference.date
                        break@loop
                    }
                    is Updates_DifferenceTooLongObject -> {
                        require(this.updates.offer(Skipped(null))) { "Failed to offer drop message" }
                        updatesPts[null] = difference.pts
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
        val pts = updatesPts[channelId]?.minus(1)
        if (pts == null) {
            updatesPts[channelId] =
                ((client(
                    Channels_GetFullChannelRequest(inputChannel),
                    forUpdate = true
                ) as Messages_ChatFullObject).fullChat as ChannelFullObject).pts
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
                        -1,
                        0
                    ), true, endPts = result.pts
                )
                updatesPts[channelId] =
                    result.pts // updates sent in the difference have wrong pts, but are sorted
                if (result.final) {
                    return true
                }
            }
            is Updates_ChannelDifferenceTooLongObject -> {
                require(updates.offer(Skipped(channelId))) { "Failed to offer drop message" }
                updatesPts[channelId] = (result.dialog as DialogObject).pts!!
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

    protected data class BatchUpdateState(val total: Int, val end: Int, var current: Int = 0)
}

sealed class UpdateOrSkipped(open val update: UpdateType?)

data class Update(override val update: UpdateType, private val onCommit: suspend () -> Unit) : UpdateOrSkipped(update) {
    suspend fun commit() = onCommit()
}

data class Skipped(val channelId: Int?) : UpdateOrSkipped(null)

private val MessageType.toId: dev.hack5.telekram.core.tl.PeerType?
    get() = when (this) {
        is MessageEmptyObject -> null
        is MessageObject -> toId
        is MessageServiceObject -> toId
    }