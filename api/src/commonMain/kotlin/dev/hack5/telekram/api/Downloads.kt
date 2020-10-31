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

import dev.hack5.telekram.api.iter.RandomAccessBulkIter
import dev.hack5.telekram.api.iter.iter
import dev.hack5.telekram.core.client.TelegramClient
import dev.hack5.telekram.core.exports.exportDC
import dev.hack5.telekram.core.tl.*
import dev.hack5.telekram.core.utils.toInputPeer
import dev.hack5.telekram.core.utils.toInputUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlin.math.exp

suspend fun UserObject.downloadProfilePhoto(client: TelegramClient): DownloadIter {
    photo.let {
        when (it) {
            is UserProfilePhotoEmptyObject -> TODO()
            is UserProfilePhotoObject -> {
                val photo = it.photoBig as FileLocationToBeDeprecatedObject
                val ref = InputPeerPhotoFileLocationObject(true, toInputUser(client).toInputPeer(), photo.volumeId, photo.localId)
                return client.getFile(ref, 4096, it.dcId)
            }
            null -> TODO()
        }
    }
}

suspend fun TelegramClient.getFile(location: InputFileLocationType, partSize: Int, dcId: Int): DownloadIter {
    val exported = exportDC(dcId, null, null)
    return DownloadIter(exported, { location }, false)
}

class DownloadIter(val exportedClient: TelegramClient, val fileRefGetter: () -> InputFileLocationType, val precise: Boolean, val defaultChunkSize: Int = 65536) : RandomAccessBulkIter<Int, Byte, Data>() {
    protected var fileRef = fileRefGetter()

    override suspend fun get(data: Data): Pair<Collection<Pair<ClosedRange<Int>, Collection<Byte>>>, Data> {
        val result = exportedClient(Upload_GetFileRequest(precise, false /* TODO */, fileRef, data.first,
            data.third ?: data.second
        )) as Upload_FileObject
        val range = data.first .. (data.first + result.bytes.size)
        return listOf(range to result.bytes.asList()) to Triple(range.last + 1, data.second, null)
    }

    override suspend fun getInitialParameters(start: Int, endInclusive: Int?): Data {
        val end = endInclusive ?: start + defaultChunkSize
        val limitDivisor = 1048576
        val (offset, limit, chunkSize) = if (precise) {
            // TODO
            Triple(0, 0, 0)
        } else {
            val chunkSize = 4096
            val newStart = start / chunkSize * chunkSize
            val targetLimit = end - start
            var newLimit = chunkSize
            while (newLimit < limitDivisor && newLimit < targetLimit)
                newLimit *= 2
            Triple(newStart, newLimit, chunkSize)
        }
        val newEndInclusiveChunk = (offset + limit - 1) / limitDivisor
        val (finalOffset, firstLimit) = if (newEndInclusiveChunk != offset / limitDivisor) {
            // chunks the file into 1mb chunks starting at 0 and checks whether start and end are in the same chunk
            // move the end to be the exact end of the chunk and move the start left in chunks of 4096 while maintaining a valid limit
            val fixedEndInclusive = (newEndInclusiveChunk + 1) * limitDivisor // end of desired chunk
            var pow = chunkSize
            while (fixedEndInclusive - pow + 1 > offset)
                pow *= 2
            fixedEndInclusive - pow + 1 to pow
        } else {
            offset to null
        }
        return Data(finalOffset, limit, firstLimit)
    }

    override fun subtractIndices(left: Int, right: Int) = left - right
}

private typealias Data = Triple<Int, Int, Int?>