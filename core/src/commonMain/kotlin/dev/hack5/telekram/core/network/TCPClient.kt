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

package dev.hack5.telekram.core.network

import kotlinx.coroutines.CoroutineScope

expect class ByteWriteChannel {
    suspend fun writeFully(src: ByteArray, offset: Int, length: Int)
    fun flush()
}

expect class ByteReadChannel {
    suspend fun readFully(dst: ByteArray, offset: Int, length: Int)
    suspend fun readIntLittleEndian(): Int
}

@Suppress("UNUSED_PARAMETER") // It can't see that the expected/actual definitions use this
abstract class TCPClient protected constructor(targetAddress: String, targetPort: Int) {
    abstract val readChannel: ByteReadChannel?
    abstract val writeChannel: ByteWriteChannel?

    abstract val address: String
    abstract val port: Int

    abstract suspend fun connect()
    abstract suspend fun close()
}

expect class TCPClientImpl(
    scope: CoroutineScope,
    targetAddress: String,
    targetPort: Int,
    onError: (Throwable) -> Unit
) : TCPClient
