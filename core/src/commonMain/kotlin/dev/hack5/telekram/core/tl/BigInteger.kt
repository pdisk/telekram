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

import dev.hack5.telekram.core.mtproto.Int128Object
import dev.hack5.telekram.core.mtproto.Int256Object
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.gciatto.kt.math.BigInteger

fun BigInteger.asTlObject128() =
    Int128Object(TODO(), TODO(), TODO(), TODO())

fun BigInteger.asTlObject256() =
    Int256Object(TODO(), TODO(), TODO(), TODO())

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