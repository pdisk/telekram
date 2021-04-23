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

interface TLBase {
    val fields: Map<String, TLBase?>
}

private class TLSimpleBase : TLBase {
    override val fields: Map<String, TLBase?>
        get() = emptyMap()
}

data class TLInt(val int: Int) : TLBase by TLSimpleBase()
data class TLLong(val long: Long) : TLBase by TLSimpleBase()
data class TLDouble(val double: Double) : TLBase by TLSimpleBase()
data class TLString(val string: String) : TLBase by TLSimpleBase()
data class TLBytes(val bytes: ByteArray) : TLBase by TLSimpleBase()
data class TLList(val list: List<*>) : TLBase by TLSimpleBase()
data class TLBool(val bool: Boolean) : TLBase by TLSimpleBase()


interface TLSerializable : TLBase {
    fun toTlRepr(buffer: Buffer, bare: Boolean)
    fun toTlRepr(bare: Boolean) = Buffer(tlSize + if (bare) 4 else 0).also {
        toTlRepr(it, bare)
    }

    val tlSize: Int
}

interface TLDeserializer<T : TLBase> {
    fun fromTlRepr(buffer: Buffer): T
}

interface TLObject : TLBase, TLSerializable {
}

interface TLFunction<R : TLObject> : TLBase, TLSerializable {
    val constructor: R

    @Suppress("UNCHECKED_CAST")
    fun castResult(result: TLObject) = result as R
}

interface Buffer {
    val offset: Int
    val length: Int
    fun readInt(): Int
    fun readLong(): Long
    fun readDouble(): Double
    fun readBytes(): ByteArray
    fun readBoolean(): ByteArray

    fun write(int: Int, bare: Boolean)
    fun write(long: Long, bare: Boolean)
    fun write(double: Double, bare: Boolean)
    fun write(bytes: ByteArray, bare: Boolean)
    fun write(bool: Boolean, bare: Boolean)
    fun write(list: List<TLSerializable>, bare: Boolean, innerBare: Boolean)
    fun write(tlSerializable: TLSerializable, bare: Boolean)
}

expect fun Buffer(length: Int): Buffer
