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

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.util.*


internal actual fun CharArray.toByteArray(): ByteArray {
    val charBuffer = CharBuffer.wrap(this)
    val byteBuffer = Charsets.UTF_8.encode(charBuffer)
    val bytes: ByteArray = Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit())
    Arrays.fill(byteBuffer.array(), 0)
    return bytes
}

internal actual fun ByteArray.toCharArray(): CharArray {
    val byteBuffer = ByteBuffer.wrap(this)
    val charBuffer = Charsets.UTF_8.decode(byteBuffer)
    val chars = Arrays.copyOfRange(charBuffer.array(), charBuffer.position(), charBuffer.limit())
    Arrays.fill(charBuffer.array(), 0.toChar())
    return chars
}