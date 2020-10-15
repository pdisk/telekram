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

package dev.hack5.telekram.core.connection

import com.github.aakira.napier.Napier
import dev.hack5.telekram.core.network.TCPClient
import dev.hack5.telekram.core.network.TCPClientImpl
import dev.hack5.telekram.core.tl.toByteArray
import dev.hack5.telekram.core.utils.calculateCRC32
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val tag = "Connection"

class ConnectionClosedError(message: String? = null, cause: Exception? = null) : Exception(message, cause)
class AlreadyConnectedError(message: String? = null, cause: Exception? = null) : Exception(message, cause)

@ExperimentalCoroutinesApi
abstract class Connection(protected val scope: CoroutineScope, protected val host: String, protected val port: Int) {
    private val mutableConnectionState = MutableStateFlow<Boolean?>(false)
    val connectionState: StateFlow<Boolean?> = mutableConnectionState
    private val sendLock = Mutex()
    private val recvLock = Mutex()
    private var recvLoopTask: Job? = null

    private fun notifyConnectionStatus(status: Boolean?) {
        Napier.i("New connection state for $this: $status", tag = tag)
        mutableConnectionState.value = status
    }

    suspend fun connect() {
        if (mutableConnectionState.value != false)
            throw AlreadyConnectedError("Still connected to the sever. Please wait for `connected == false`")
        notifyConnectionStatus(null)
        connectInternal()
        notifyConnectionStatus(true)
    }

    suspend fun disconnect() {
        mutableConnectionState.value = null
        disconnectInternal()
        mutableConnectionState.value = false
    }

    suspend fun send(data: ByteArray) {
        sendLock.withLock {
            sendInternal(data)
        }
    }

    suspend fun recvLoop(output: Channel<ByteArray>) = recvLock.withLock {
        while (mutableConnectionState.value == true)
            output.send(recvInternal())
    }

    suspend fun recv(): ByteArray {
        return recvLock.withLock {
            return@withLock recvInternal()
        }
    }

    protected abstract suspend fun sendInternal(data: ByteArray)
    protected abstract suspend fun recvInternal(): ByteArray
    protected abstract suspend fun connectInternal()
    protected abstract suspend fun disconnectInternal()
}

@ExperimentalCoroutinesApi
abstract class TcpConnection(
    scope: CoroutineScope,
    host: String,
    port: Int,
    private val network: (CoroutineScope, String, Int) -> TCPClient
) : Connection(scope, host, port) {
    private var socket: TCPClient? = null
    protected val readChannel get() = socket?.readChannel!!
    protected val writeChannel get() = socket?.writeChannel!!

    override suspend fun connectInternal() {
        network(scope, host, port).let {
            it.connect()
            socket = it
        }
    }

    override suspend fun disconnectInternal() {
        socket?.close()
    }
}

@ExperimentalCoroutinesApi
class TcpFullConnection(
    scope: CoroutineScope,
    host: String,
    port: Int,
    network: (CoroutineScope, String, Int) -> TCPClient = ::TCPClientImpl
) : TcpConnection(scope, host, port, network) {
    constructor(scope: CoroutineScope, host: String, port: Int) : this(scope, host, port, ::TCPClientImpl)

    private var counter = 0
    override suspend fun sendInternal(data: ByteArray) {
        val len = data.size + 12
        val ret = byteArrayOf(*len.toByteArray(), *counter++.toByteArray(), *data)
        val crc = calculateCRC32(ret)
        writeChannel.writeFully(ret + crc.toByteArray(), 0, len)
        writeChannel.flush()
    }

    override suspend fun recvInternal(): ByteArray {
        // The ugly try-catch statements here are because the exception is missing some line-number metadata
        // To recover this metadata we add it by throwing the exception from a different line
        val len: Int
        try {
            len = readChannel.readIntLittleEndian()
        } catch (e: ClosedReceiveChannelException) {
            throw ConnectionClosedError(cause=e)
        }
        val seq: Int
        try {
            seq = readChannel.readIntLittleEndian()
        } catch (e: ClosedReceiveChannelException) {
            throw ConnectionClosedError(cause=e)
        }
        val ret = ByteArray(len - 12)
        try {
            readChannel.readFully(ret, 0, ret.size)
        } catch (e: ClosedReceiveChannelException) {
            throw ConnectionClosedError(cause=e)
        }
        val crc: Int
        try {
            crc = readChannel.readIntLittleEndian()
        } catch (e: ClosedReceiveChannelException) {
            throw ConnectionClosedError(cause=e)
        }
        val full = byteArrayOf(*len.toByteArray(), *seq.toByteArray(), *ret)
        val calculatedCrc = calculateCRC32(full)
        if (crc != calculatedCrc)
            error("Invalid CRC in recv! $crc != $calculatedCrc")
        return ret // We don't care that there is extra data (the CRC) at the end, the fromTlRepr will ignore it.
    }
}