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

@file:JvmName("RSAJvm")

package dev.hack5.telekram.core.crypto

import com.soywiz.krypto.sha1
import dev.hack5.telekram.core.tl.LongObject
import dev.hack5.telekram.core.tl.asTlObject
import dev.hack5.telekram.core.tl.toByteArray
import dev.hack5.telekram.core.tl.toIntArray
import org.gciatto.kt.math.BigInteger
import kotlin.jvm.JvmName

interface RSAEncoder {
    fun loadPubKeys()
    fun loadPubKey(key: RSAPublicKey)
    fun computeFingerprint(key: RSAPublicKey): Long
    fun encrypt(data: ByteArray, fingerprint: Long): ByteArray?
}

open class RSAEncoderImpl : RSAEncoder {
    private val keys = mutableMapOf<Long, RSAPublicKey>()

    override fun loadPubKeys() {
        trustedFingerprints.forEach {
            loadPubKey(it)
        }
    }

    override fun loadPubKey(key: RSAPublicKey) {
        keys[key.fingerprint] = key
    }

    override fun computeFingerprint(key: RSAPublicKey): Long {
        val n = key.n.toByteArray()
        val e = key.e.toByteArray()
        val sha = (n.asTlObject().toTlRepr() + e.asTlObject().toTlRepr()).toByteArray().sha1().bytes
        return LongObject.fromTlRepr(sha.sliceArray(sha.size - 8 until sha.size).toIntArray())!!.second.native
    }

    override fun encrypt(data: ByteArray, fingerprint: Long): ByteArray? {
        val key = keys.getOrElse(fingerprint) { return null }
        val ret = BigInteger(byteArrayOf(0) + data).modPow(key.exponent, key.modulo).toByteArray()
        if (ret.size > 256) {
            require(ret.size == 257) { "Unexpected encrypted data size ${data.size}" }
            require(ret.first() == 0.toByte()) { "Encrypted data is too big, caused by an RSA internal error (data = ${ret.contentToString()})" }
            return ret.drop(1).toByteArray()
        }
        if (ret.size < 256) {
            return ByteArray(256 - ret.size) { 0 } + ret
        }
        return ret
    }

    companion object : RSAEncoderImpl() {
        init {
            loadPubKeys()
        }
    }
}

//expect open class RSAEncoderPlatformImpl() : RSAEncoderImpl

data class RSAPublicKey(val modulo: BigInteger, val exponent: BigInteger, val fingerprint: Long) {
    val n = modulo
    val e = exponent
}