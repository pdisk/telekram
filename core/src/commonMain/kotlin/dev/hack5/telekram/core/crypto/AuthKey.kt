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

package dev.hack5.telekram.core.crypto

import com.soywiz.krypto.sha1
import dev.hack5.telekram.core.tl.BigIntegerSerializer
import dev.hack5.telekram.core.tl.LongObject
import dev.hack5.telekram.core.tl.toIntArray
import dev.hack5.telekram.core.utils.pad
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.gciatto.kt.math.BigInteger

@Serializable
data class AuthKey(@Serializable(with = BigIntegerSerializer::class) private val data: BigInteger) {
    val key = data.toByteArray().pad(256)

    @Transient
    private val hash = key.sha1().bytes

    @Transient
    val auxHash = LongObject.fromTlRepr(hash.toIntArray())!!.second.native // 64 high order bits
    @Transient
    val keyId = hash.sliceArray(12 until 20) // 64 low order bits
}