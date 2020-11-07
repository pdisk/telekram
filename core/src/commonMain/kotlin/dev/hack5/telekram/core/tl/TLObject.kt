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

@file:Suppress("PropertyName", "FunctionName")

package dev.hack5.telekram.core.tl

interface TLObject {
    fun toTlRepr(buffer: Buffer, bare: Boolean)

    val fields: Map<String, TLObject?>
}

interface TLConstructor<T : TLObject> {
    fun fromTlRepr(data: Buffer, bare: Boolean)

    val id: Int?
}

interface TLTypeConstructor<T : TLObject> : TLConstructor<T> {
    @Suppress("ImplicitNullableNothingType")
    override val id: Int? get() = null

    val constructors: Map<Int, TLConstructor<out T>>
}

interface TLFunction<R : TLObject> : TLObject {
    val constructor: TLConstructor<R>

    @Suppress("UNCHECKED_CAST")
    fun castResult(result: TLObject) = result as R
}

interface Buffer {
    val offset: Int
    val length: Int
    fun readByte()
    fun readInt()
    fun readLong()
    fun readDouble()
    fun readBytes()

    fun writeByte(byte: Byte)
    fun writeInt(int: Int)
    fun writeLong(long: Long)
    fun writeDouble(double: Double)
    fun writeBytes(bytes: ByteArray)
}