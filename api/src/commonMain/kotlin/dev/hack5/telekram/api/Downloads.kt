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

import dev.hack5.telekram.api.iter.iter
import dev.hack5.telekram.core.client.TelegramClient
import dev.hack5.telekram.core.exports.exportDC
import dev.hack5.telekram.core.tl.*
import dev.hack5.telekram.core.utils.toInputPeer
import dev.hack5.telekram.core.utils.toInputUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion

suspend fun UserObject.downloadProfilePhoto(client: TelegramClient): Flow<ByteArray> {
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

@ExperimentalCoroutinesApi
suspend fun TelegramClient.getFile(location: InputFileLocationType, partSize: Int, dcId: Int): Flow<ByteArray> {
    val exported = exportDC(dcId, null, null)
    val file = exported(Upload_GetFileRequest(false, true, location, 0, 4096))
    return iter<ByteArray, Int> {

        //exported()

        listOf<ByteArray>() to it
    }.onCompletion {
        exported.disconnect()
    }
}