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

package dev.hack5.telekram.core.tl

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.gciatto.kt.math.BigInteger

fun BigInteger.asTlObject128(): Int128Object =
    Int128Object(this, true)

fun BigInteger.asTlObject256(): Int256Object =
    Int256Object(this, true)

data class Int128Object(private val int128: BigInteger, override val bare: Boolean) :
    TLObject<BigInteger> {
    init {
        if (int128.bitLength >= 128)
            error("Cannot serialize integers with more than 128 bits (including sign) as an int128")
    }

    override fun _toTlRepr(): IntArray {
        val bigByteMask = BigInteger.of(0xFF)
        return ByteArray(16) {
            int128.shr(it * Byte.SIZE_BITS)
                .and(bigByteMask).toByte()
        }.toIntArray()
    }

    override val native = int128

    override val _id = id

    override val fields by lazy { mapOf<String, TLObject<*>>() }

    companion object :
        TLConstructor<Int128Object> {
        override val id: Int? = null

        override fun _fromTlRepr(data: IntArray, offset: Int): Pair<Int, Int128Object>? {
            if (data.size < offset + 4)
                return null
            return Pair(
                4,
                Int128Object(
                    BigInteger(data.sliceArray(offset until offset + 4).toByteArray().reversedArray()),
                    true
                )
            )
        }
    }

}

data class Int256Object(private val int256: BigInteger, override val bare: Boolean) :
    TLObject<BigInteger> {
    init {
        if (int256.bitLength >= 256)
            error("Cannot serialize integers with more than 256 bits (including sign) as an int256")
    }

    override fun _toTlRepr(): IntArray {
        val bigByteMask = BigInteger.of(0xFF)
        return ByteArray(32) {
            int256.shr(it * Byte.SIZE_BITS)
                .and(bigByteMask).toByte()
        }.toIntArray()
    }

    override val native = int256

    override val _id = id

    override val fields by lazy { mapOf<String, TLObject<*>>() }

    companion object :
        TLConstructor<Int128Object> {
        override val id: Int? = null

        override fun _fromTlRepr(data: IntArray, offset: Int): Pair<Int, Int128Object>? {
            if (data.size < offset + 8)
                return null
            return Pair(
                8,
                Int128Object(
                    BigInteger(data.sliceArray(offset until offset + 4).toByteArray().reversedArray()),
                    true
                )
            )
        }
    }
}

fun BigInteger.toByteArray(size: Int): ByteArray {
    val ret = toByteArray()
    if (ret.size == size)
        return ret
    if (ret.size == size + 1) {
        require(ret[0] == 0.toByte())
        return ret.drop(1).toByteArray()
    }
    require(ret.size < size) { "Size $size is larger than ${ret.size} ($this=${ret.contentToString()})" }
    return ByteArray(size - ret.size) { 0 } + ret
}

object BigIntegerSerializer : KSerializer<BigInteger> {
    override val descriptor = PrimitiveSerialDescriptor("rawBytes", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): BigInteger {
        return BigInteger(String.serializer().deserialize(decoder), 16)
    }

    override fun serialize(encoder: Encoder, value: BigInteger) {
        String.serializer().serialize(encoder, value.toString(16))
    }
}