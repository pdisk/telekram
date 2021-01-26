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
import dev.hack5.telekram.core.errors.InternalServerError
import dev.hack5.telekram.core.state.UpdateState
import dev.hack5.telekram.core.tl.*
import dev.hack5.telekram.core.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

interface UpdateHandler {
    suspend fun getEntities(value: TLObject<*>): Map<String, MutableMap<Long, Long>>
    suspend fun <R : TLObject<*>>handleUpdates(request: TLMethod<R>?, response: R)
    val updates: Channel<UpdateOrSkipped>
    suspend fun catchUp()
}

open class UpdateHandlerImpl(
    protected val scope: CoroutineScope,
    protected val updateState: UpdateState,
    protected val client: TelegramClient,
    protected val maxDifference: Int? = 100000,
    protected val maxChannelDifference: Int = 100,
    protected val debug: Boolean = false,
    protected val forceFetch: Boolean = false
) : BaseActor(scope), UpdateHandler {
    // TODO: implement qts stuff

    // IMPORTANT: DO NOT modify this file without running it past hackintosh5! You have CERTAINLY made a mistake.

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

    override suspend fun <R : TLObject<*>>handleUpdates(request: TLMethod<R>?, response: R) {
        when (response) {
            is UpdatesType -> handleUpdates(updates = response)
            is Messages_AffectedHistoryType -> handleUpdates(null, syntheticUpdates = listOf(SyntheticUpdate(request!!, response, client)))
            is Messages_AffectedMessagesType -> handleUpdates(null, syntheticUpdates = listOf(SyntheticUpdate(request!!, response, client)))
        }
    }

    override suspend fun getEntities(value: TLObject<*>): Map<String, MutableMap<Long, Long>> {
        val (ret, minUsers, minChannels) = AccessHashGetter().walk(value)!!
        MinGetter(minUsers, minChannels).walk(value)
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
                client(Users_GetUsersRequest(users)) // TODO handle slices
            } catch(e: BadRequestError.UserInvalidError) {
                // TODO divide-and-conquer to get the ones that do work
            }
        if (channels.isNotEmpty())
            try {
                client(Channels_GetChannelsRequest(channels)) // TODO handle slices
            } catch(e: BadRequestError.ChannelInvalidError) {
                // TODO divide-and-conquer to get the ones that do work
            }
        return ret
    }

    protected suspend fun handleUpdates(
        updates: UpdatesType?,
        skipChecks: Boolean = false,
        skipAllChecks: Boolean = false,
        skipDispatch: Boolean = false,
        endPts: Int? = null,
        endSeq: Int? = null,
        syntheticUpdates: List<ActualOrSyntheticUpdate>? = null
    ) = coroutineScope {
        require((updates == null) != (syntheticUpdates == null)) { "Got both or neither $updates and $syntheticUpdates" }
        suspend fun <R> actIfNeeded(block: suspend () -> R): R = if (skipChecks) block() else act { block() }
        println("====== preact")
        actIfNeeded {
            println("====== act")
        }
        var refetch: Int? = null
        val innerUpdates = when (updates) {
            is UpdatesTooLongObject -> {
                if (!skipChecks)
                    fetchUpdates()
                return@coroutineScope
            }
            is UpdateShortMessageObject -> {
                try {
                    PeerUserObject(updates.userId).toInputUser(client)
                } catch (e: Exception) {
                    refetch = updates.userId
                }
                listOf(
                    ActualUpdate(
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
                )
            }
            is UpdateShortChatMessageObject -> {
                if (client.getAccessHash(PeerType.USER, updates.fromId) == null) {
                    refetch = updates.fromId
                }
                listOf(
                    ActualUpdate(
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
                )
            }
            is UpdateShortObject -> listOf(ActualUpdate(updates.update))
            is UpdatesCombinedObject -> updates.updates.map(::ActualUpdate)
            is UpdatesObject -> updates.updates.map(::ActualUpdate)
            is UpdateShortSentMessageObject -> return@coroutineScope // handled by rpc caller
            null -> {
                syntheticUpdates!!
            }
        }.filter {
            if (it is ActualUpdate && it.update is UpdateChannelTooLongObject) {
                launch {
                    fetchChannelUpdates(it.update.channelId)
                }
                false
            } else {
                true
            }
        }

        if (!skipChecks) {
            act {
                updates?.date?.let { checkDateLocked(it) }
            }
        }
        val (hasPts, hasNoPts) = innerUpdates.partition { it.pts != null }
        val totalUpdatedPts = endPts?.let { BatchUpdateState(hasPts.size, it) }
        for (update in hasPts) {
            val pts = update.pts!!
            val ptsCount = update.ptsCount
            val applicablePts = pts - ptsCount!!
            if (!skipChecks || (update.channelId != null && !skipAllChecks)) {
                val job = actIfNeeded {
                    val localPts = updatesPts[update.channelId]

                    when {
                        (ptsCount == 0 && (localPts == null || pts >= localPts))
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
                    actIfNeeded {
                        pendingUpdatesPts.remove(update.channelId to applicablePts)
                    }
                    if (join == null) {
                        if (update.channelId != null) {
                            fetchChannelUpdates(update.channelId!!)
                        } else {
                            fetchUpdates()
                        }
                        return@coroutineScope // server will resend this update too
                    }

                    actIfNeeded {
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
                    totalUpdatedPts
                )
            }
        }
        println("====== done pts")
        if (updates != null) {
            val totalUpdatedSeq = endSeq?.let { BatchUpdateState(hasNoPts.size, it) }
            @Suppress("UNCHECKED_CAST")
            hasNoPts as List<ActualUpdate> // always safe because updates!=null
            val applicableSeq = updates.seqStart?.minus(1)
            if (!skipChecks) {
                val (localSeq, job) = actIfNeeded {
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
                        actIfNeeded {
                            pendingUpdatesSeq.remove(applicableSeq)
                        }
                        fetchUpdates()
                        return@coroutineScope // server will resend this update too
                    }
                    actIfNeeded {
                        pendingUpdatesSeq.remove(applicableSeq)
                        handleSingleSeqLocked(hasNoPts, applicableSeq!!, updates, false, skipDispatch)
                    }
                }
            } else {
                handleSingleSeqLocked(hasNoPts, null, updates, false, skipDispatch, totalUpdatedSeq)
            }
        }
        println("====== done seq")
    }

    protected suspend fun handleSinglePtsLocked(
        refetch: Int?,
        applicablePts: Int?,
        commitNoOp: Boolean,
        update: ActualOrSyntheticUpdate,
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
                        if (debug)
                            scope.launch {
                                delay(1000)
                                if (job.isActive)
                                    Napier.w("Still waiting to commit pts ${update.channelId} $applicablePts ${update.pts} ${update.ptsCount}")
                            }
                        job.join()
                        Napier.v("Waiting to commit pts finished ${update.channelId} $applicablePts ${update.pts} ${update.ptsCount}")
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
                    }
                }
            }
        }
        if (!skipPts) {
            Napier.v("Setting pts to ${update.pts} ($skipDispatch)")
            updatesPts[update.channelId] = update.pts!!
            pendingUpdatesPts[update.channelId to update.pts!!]?.complete()
        }
        if (!skipDispatch) {
            dispatchUpdate(
                update,
                onCommit::invoke
            )
        } else {
            scope.launch {
                onCommit()
            }
        }
    }

    protected suspend fun commitPts(update: ActualOrSyntheticUpdate, pts: Int) {
        updateState.pts[update.channelId] = pts
        processingUpdatesPts.filterKeys { it.first == update.channelId && it.second <= pts }.forEach {
            // TODO: this is ugly, refactor processingUpdatesPts to a map?
            it.value.complete()
        }
    }

    protected suspend fun handleSingleSeqLocked(
        hasNoPts: List<ActualUpdate>,
        applicableSeq: Int?,
        updates: UpdatesType,
        skipSeq: Boolean,
        skipDispatch: Boolean,
        totalUpdated: BatchUpdateState? = null
    ) {
        val onCommit: suspend () -> Unit = when {
            applicableSeq != null -> {
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
                        if (debug)
                            scope.launch {
                                delay(1000)
                                if (job.isActive)
                                    Napier.w("Still Waiting to commit seq $applicableSeq ${updateState.seq} ${updates.seq}")
                            }
                        job.join()
                        Napier.v("Waiting to commit seq finished $applicableSeq ${updateState.seq} ${updates.seq}")
                        act {
                            processingUpdatesSeq.remove(applicableSeq)
                            commitSeq(updates)
                        }
                    }
                }
            }
            totalUpdated != null -> {
                {
                    act {
                        if (++totalUpdated.current == totalUpdated.total) {
                            Napier.v("Committing seq for batch $applicableSeq ${updateState.seq} ${updates.seq}")
                            commitSeq(updates, totalUpdated.end)
                        }
                    }
                }
            }
            else -> {
                {}
            }
        }
        if (!skipSeq) {
            updatesSeq = updates.seq!!
            updates.date?.let { checkDateLocked(it) }
            pendingUpdatesSeq[updates.seq!!]?.complete()
        }
        if (skipDispatch || hasNoPts.isEmpty()) {
            scope.launch {
                onCommit()
            }
        } else {
            for (update in hasNoPts) {
                dispatchUpdate(
                    update,
                    onCommit::invoke
                )
            }
        }
    }

    protected suspend fun commitSeq(updates: UpdatesType, seq: Int = updates.seq!!) {
        println("commit seq ${updates.seq} $seq")
        updateState.seq = seq
        updates.date?.let { checkDateLockedCommit(it) }
        processingUpdatesSeq[seq]?.complete()
    }

    protected suspend fun fetchHashes(fromPts: Int, limit: Int) {
        client(
            Updates_GetDifferenceRequest(
                fromPts,
                limit,
                updatesDate,
                updatesQts
            )
        )
        // no matter the result, we can't do anything about it
    }

    override suspend fun catchUp() = fetchUpdates()

    protected fun checkDateLocked(date: Int) {
        if (date > updatesDate)
            updatesDate = date
    }

    protected fun checkDateLockedCommit(date: Int) {
        if (date > updatesDate) {
            updateState.date = date
        }
    }

    protected fun dispatchUpdate(update: ActualOrSyntheticUpdate, onCommit: suspend () -> Unit) {
        Napier.d("dispatching update $update")
        check(updates.offer(Update(update, onCommit))) { "Failed to offer update" }
    }

    protected suspend fun fetchUpdates() {
        val updates = mutableListOf<UpdateType>()
        var tmpState: Updates_StateObject? = null
        suspend fun drop(): Job {
            val job = Job()
            require(this.updates.offer(Skipped(null) {
                act {
                    val serverPts = (client(
                        Updates_GetStateRequest(),
                        skipUpdates = true
                    ) as Updates_StateObject).pts
                    updatesPts[null] = serverPts
                    updateState.pts[null] = serverPts
                    job.complete()
                }
            })) { "Failed to offer drop message" }
            return job
        }
        val job = act {
            loop@while (true) {
                val seqStart = (tmpState?.seq ?: updatesSeq) + 1
                val previousPts = tmpState?.pts ?: updatesPts[null]!!
                val difference = try {
                    client(
                        Updates_GetDifferenceRequest(
                            previousPts,
                            maxDifference,
                            tmpState?.date ?: updatesDate,
                            tmpState?.qts ?: updatesQts
                        ), skipUpdates = true
                    )
                } catch (e: BadRequestError.PersistentTimestampEmptyError) {
                    return@act drop()
                } catch (e: BadRequestError.PersistentTimestampInvalidError) {
                    return@act drop()
                } catch (e: InternalServerError.PersistentTimestampOutdatedError) {
                    return@act drop()
                }
                when (difference) {
                    is Updates_DifferenceObject -> {
                        val state = difference.state as Updates_StateObject
                        println("====== begin end")
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
                            ), skipChecks = true, endPts = state.pts, endSeq = state.seq
                        )
                        updatesDate = state.date
                        println("====== end")
                        println(state)

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
                        updatesDate = difference.date
                        break@loop
                    }
                    is Updates_DifferenceTooLongObject -> {
                        val job = Job()
                        require(this.updates.offer(Skipped(null) {
                            act {
                                updatesPts[null] = difference.pts
                                updateState.pts[null] = difference.pts
                            }
                            job.complete()
                        })) { "Failed to offer drop message" }
                        return@act job
                    }
                }
            }
            null
        }
        job?.join()
    }

    protected suspend fun fetchChannelUpdates(channelId: Int) {
        val inputChannel = PeerChannelObject(channelId).toInputChannel(client)
        while (true) {
            val ret = act {
                fetchChannelUpdatesInnerLocked(channelId, inputChannel)
            }
            if (ret.first) {
                ret.second?.join()
                break
            }
        }
    }


    protected suspend fun fetchChannelUpdatesInnerLocked(channelId: Int, inputChannel: InputChannelType): Pair<Boolean, Job?> {
        val pts = updatesPts[channelId]?.minus(1)
        suspend fun drop(): Job {
            val job = Job()
            require(this.updates.offer(Skipped(channelId) {
                act {
                    val serverPts = ((client(
                        Messages_GetPeerDialogsRequest(listOf(InputDialogPeerObject(inputChannel.toInputPeer()))),
                        skipUpdates = true
                    ) as Messages_PeerDialogsObject).dialogs.single() as DialogObject).pts!!
                    updatesPts[channelId] = serverPts
                    updateState.pts[channelId] = serverPts
                    job.complete()
                }
            })) { "Failed to offer drop message" }
            return job
        }
        if (pts == null) {
            return true to drop()
        }
        val result = try {
            client(
                Updates_GetChannelDifferenceRequest(
                    !forceFetch,
                    inputChannel,
                    ChannelMessagesFilterEmptyObject(),
                    pts,
                    maxChannelDifference
                ),
                skipUpdates = true
            )
        } catch (e: BadRequestError.PersistentTimestampEmptyError) {
            return true to drop()
        } catch (e: BadRequestError.PersistentTimestampInvalidError) {
            return true to drop()
        } catch (e: InternalServerError.PersistentTimestampOutdatedError) {
            return true to drop()
        }
        when (result) {
            is Updates_ChannelDifferenceEmptyObject -> return result.final to null
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
                    ), skipChecks = true, skipAllChecks = true, endPts = result.pts
                )
                updatesPts[channelId] =
                    result.pts // updates sent in the difference have wrong pts, but are sorted
                if (result.final) {
                    return true to null
                }
            }
            is Updates_ChannelDifferenceTooLongObject -> {
                val job = Job()
                require(updates.offer(Skipped(channelId) {
                    act {
                        result.dialog as DialogObject
                        updatesPts[channelId] = result.dialog.pts!!
                        updateState.pts[channelId] = result.dialog.pts
                        job.complete()
                    }
                })) { "Failed to offer drop message" }

                return true to job
            }
        }
        return false to null
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

    protected data class BatchUpdateState(val total: Int, val end: Int, var current: Int = 0)
}

