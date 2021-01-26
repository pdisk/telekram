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

open class ConnectionException(message: String, cause: Exception? = null) : Exception(message, cause)
class ConnectionClosedException(message: String, cause: Exception? = null) : ConnectionException(message, cause)
class AlreadyConnectedException(message: String, cause: Exception? = null) : ConnectionException(message, cause)

abstract class Connection(protected val scope: CoroutineScope, protected val host: String, protected val port: Int) {
    @ExperimentalCoroutinesApi
    private val mutableConnectionState = MutableStateFlow<Boolean?>(false)

    @ExperimentalCoroutinesApi
    val connectionState: StateFlow<Boolean?> = mutableConnectionState
    private val sendLock = Mutex()
    private val recvLock = Mutex()
    private var recvLoopTask: Job? = null

    @ExperimentalCoroutinesApi
    private fun notifyConnectionStatus(status: Boolean?) {
        Napier.i("New connection state for $this: $status", tag = tag)
        mutableConnectionState.value = status
    }

    @ExperimentalCoroutinesApi
    protected fun onError(e: Throwable) {
        Napier.e("Exception in connection for $this: $e", tag = tag)
        mutableConnectionState.value = false
    }

    @ExperimentalCoroutinesApi
    suspend fun connect() {
        if (mutableConnectionState.value != false)
            throw AlreadyConnectedException("Still connected to the sever. Please wait for `connected == false`")
        notifyConnectionStatus(null)
        connectInternal()
        notifyConnectionStatus(true)
    }

    @ExperimentalCoroutinesApi
    suspend fun disconnect() {
        notifyConnectionStatus(null)
        disconnectInternal()
        notifyConnectionStatus(false)
    }

    suspend fun send(data: ByteArray) {
        sendLock.withLock {
            try {
                sendInternal(data)
            } catch (e: ConnectionException) {
                @Suppress("EXPERIMENTAL_API_USAGE")
                notifyConnectionStatus(false)
                Napier.e("Disconnected", e)
            }
        }
    }

    @ExperimentalCoroutinesApi
    suspend fun recvLoop(output: Channel<ByteArray>) = recvLock.withLock {
        while (mutableConnectionState.value == true)
            try {
                output.send(recvInternal())
            } catch (e: ConnectionClosedException) {
                notifyConnectionStatus(false)
                throw e
            }
    }

    suspend fun recv(): ByteArray {
        return recvLock.withLock {
            try {
                return@withLock recvInternal()
            } catch (e: ConnectionClosedException) {
                @Suppress("EXPERIMENTAL_API_USAGE")
                notifyConnectionStatus(false)
                throw e
            }
        }
    }

    protected abstract suspend fun sendInternal(data: ByteArray)
    protected abstract suspend fun recvInternal(): ByteArray
    protected abstract suspend fun connectInternal()
    protected abstract suspend fun disconnectInternal()
}

abstract class TcpConnection(
    scope: CoroutineScope,
    host: String,
    port: Int,
    private val network: (CoroutineScope, String, Int, (Throwable) -> Unit) -> TCPClient
) : Connection(scope, host, port) {
    private var socket: TCPClient? = null
    protected val readChannel
        get() = socket?.readChannel ?: throw ConnectionClosedException("Connection was closed earlier")
    protected val writeChannel
        get() = socket?.writeChannel ?: throw ConnectionClosedException("Connection was closed earlier")

    @ExperimentalCoroutinesApi
    override suspend fun connectInternal() {
        network(scope, host, port, ::onError).let {
            it.connect()
            socket = it
        }
    }

    override suspend fun disconnectInternal() {
        socket?.close()
    }
}

class TcpFullConnection(
    scope: CoroutineScope,
    host: String,
    port: Int,
    network: (CoroutineScope, String, Int, (Throwable) -> Unit) -> TCPClient = ::TCPClientImpl
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
            throw ConnectionClosedException("Unable to read len", cause = e)
        }
        val seq: Int
        try {
            seq = readChannel.readIntLittleEndian()
        } catch (e: ClosedReceiveChannelException) {
            throw ConnectionClosedException("Unable to read seq", cause = e)
        }
        val ret = ByteArray(len - 12)
        try {
            readChannel.readFully(ret, 0, ret.size)
        } catch (e: ClosedReceiveChannelException) {
            throw ConnectionClosedException("Unable to read data", cause = e)
        }
        val crc: Int
        try {
            crc = readChannel.readIntLittleEndian()
        } catch (e: ClosedReceiveChannelException) {
            throw ConnectionClosedException("Unable to read CRC", cause = e)
        }
        val full = byteArrayOf(*len.toByteArray(), *seq.toByteArray(), *ret)
        val calculatedCrc = calculateCRC32(full)
        if (crc != calculatedCrc)
            error("Invalid CRC in recv! $crc != $calculatedCrc")
        return ret // We don't care that there is extra data (the CRC) at the end, the fromTlRepr will ignore it.
    }
}