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

package dev.hack5.telekram.core.encoder

import com.soywiz.krypto.sha256
import dev.hack5.telekram.core.crypto.AES
import dev.hack5.telekram.core.crypto.AESMode
import dev.hack5.telekram.core.crypto.AESPlatformImpl
import dev.hack5.telekram.core.mtproto.MessageObject
import dev.hack5.telekram.core.mtproto.ObjectObject
import dev.hack5.telekram.core.state.MTProtoState
import dev.hack5.telekram.core.tl.*
import dev.hack5.telekram.core.tl.toByteArray
import dev.hack5.telekram.core.tl.toIntArray
import kotlin.random.Random

open class EncryptedMTProtoEncoder(
    state: MTProtoState,
    val aesConstructor: (AESMode, ByteArray) -> AES = ::AESPlatformImpl
) : MTProtoEncoderWrapped(state) {
    private val authKeyId = state.authKey!!.keyId

    private fun calcKey(msgKey: ByteArray, client: Boolean): Pair<ByteArray, ByteArray> {
        val x = if (client) 0 else 8
        val sha256a = (msgKey + state.authKey!!.key.sliceArray(x until x + 36)).sha256().bytes
        val sha256b = (state.authKey!!.key.sliceArray(x + 40 until x + 76) + msgKey).sha256().bytes

        val key = sha256a.sliceArray(0 until 8) + sha256b.sliceArray(8 until 24) + sha256a.sliceArray(24 until 32)
        val iv = sha256b.sliceArray(0 until 8) + sha256a.sliceArray(8 until 24) + sha256b.sliceArray(24 until 32)
        return Pair(key, iv)
    }

    override suspend fun encode(data: ByteArray): ByteArray {
        val internalHeader =
            state.salt + state.sessionId
        val paddingLength = -(internalHeader.size + data.size + 12) % 16 + 28
        val fullData = internalHeader + data + Random.nextBytes(paddingLength)
        val msgKey = (state.authKey!!.key.sliceArray(88 until 120) + fullData).sha256().bytes.sliceArray(8 until 24)
        val aesKey = calcKey(msgKey, true)
        val encrypted = aesConstructor(AESMode.ENCRYPT, aesKey.first).doIGE(aesKey.second, fullData)
        return authKeyId + msgKey + encrypted
    }

    override suspend fun encodeMessage(data: MessageObject): ByteArray = encode(data.toTlRepr().toByteArray())
    override suspend fun wrapAndEncode(data: TLObject<*>, isContentRelated: Boolean): Pair<ByteArray, Long> {
        val seq =
            (if (isContentRelated) state.act { state.seq++ } else state.act { state.seq }) * 2 + if (isContentRelated) 1 else 0
        val encoded = data.toTlRepr().toByteArray()
        val msgId = state.getMsgId()
        return Pair(encodeMessage(MessageObject(msgId, seq, encoded.size, ObjectObject(data), bare = true)), msgId)
    }

    override suspend fun decode(data: ByteArray): ByteArray {
        if (data.size == 4) {
            when (val error = data.toInt()) {
                // A server error occurred, the error is given as a HTTP status code
                -404 -> {
                    // auth key unknown, regenerate it
                    // TODO regen auth key and resend request (reauthenticating if needed, or doing cdn stuff)
                    error("Auth key regeneration not implemented")
                }
                else -> {
                    error("Unknown server error $error")
                }
            }
        }
        require(data.size >= 8) { "Data ${data.contentToString()} too small" }
        require(data.sliceArray(0 until 8).contentEquals(authKeyId)) { "Invalid authKeyId" }
        val msgKey = data.sliceArray(8 until 24)
        val aesKey = calcKey(msgKey, false)
        val decrypted =
            aesConstructor(AESMode.DECRYPT, aesKey.first).doIGE(aesKey.second, data.sliceArray(24 until data.size))
        require(
            (state.authKey!!.key.sliceArray(96 until 128) + decrypted).sha256().bytes.sliceArray(8 until 24)
                .contentEquals(
                    msgKey
                )
        ) { "Invalid msgKey" }
        // TODO implement future salt support so we can verify its a valid salt, and handle it right if its wrong
        /*require(
            decrypted.sliceArray(0 until 8)
                .contentEquals(state.salt) || state.salt.all { it == 0.toByte() }) { "Invalid salt" }*/
        require(decrypted.sliceArray(8 until 16).contentEquals(state.sessionId)) { "Invalid sessionId" }
        // We cannot validate the msgId yet if there's a container because the container contents will be lower
        return decrypted.sliceArray(16 until decrypted.size)
    }

    override suspend fun decodeMessage(data: ByteArray): MessageObject {
        return MessageObject.fromTlRepr(decode(data).toIntArray(), bare = true)!!.second
    }
}