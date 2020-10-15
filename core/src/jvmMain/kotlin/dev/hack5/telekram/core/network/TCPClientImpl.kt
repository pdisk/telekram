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

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.net.InetSocketAddress
import io.ktor.utils.io.readIntLittleEndian as readIntLittleEndianOriginal

actual class ByteWriteChannel(channel: io.ktor.utils.io.ByteWriteChannel) : io.ktor.utils.io.ByteWriteChannel by channel
actual class ByteReadChannel(channel: io.ktor.utils.io.ByteReadChannel) : io.ktor.utils.io.ByteReadChannel by channel {
    actual suspend inline fun readIntLittleEndian(): Int = readIntLittleEndianOriginal()
}

actual class TCPClientImpl actual constructor(
    scope: CoroutineScope,
    private val targetAddress: String,
    private val targetPort: Int
) : TCPClient(targetAddress, targetPort) {
    private lateinit var socket: Socket

    override var readChannel: ByteReadChannel? = null
    override var writeChannel: ByteWriteChannel? = null

    override val address get() = (socket.remoteAddress as InetSocketAddress).hostString!!
    override val port get() = (socket.remoteAddress as InetSocketAddress).port

    @KtorExperimentalAPI
    override suspend fun connect() {
        socket = aSocket(actor).tcp().connect(targetAddress, targetPort)
        readChannel = ByteReadChannel(socket.openReadChannel())
        writeChannel = ByteWriteChannel(socket.openWriteChannel())
    }

    @KtorExperimentalAPI
    override suspend fun close() {
        // TODO be careful about the order of this, null it then close
        readChannel?.cancel(null)
        readChannel = null
        writeChannel?.close(null)
        println("closed write")
        writeChannel = null
        println("nulled write")
        @Suppress("BlockingMethodInNonBlockingContext") // incorrect
        socket.close()
        socket.awaitClosed()
        println("closed sock")
        actor.close()
    }

    @KtorExperimentalAPI
    val actor = ActorSelectorManager(scope.coroutineContext + Dispatchers.IO)
}