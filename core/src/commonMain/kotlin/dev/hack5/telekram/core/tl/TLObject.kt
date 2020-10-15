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

interface TLObject<N> {
    val bare: Boolean
    fun _toTlRepr(): IntArray
    @Suppress("EXPERIMENTAL_API_USAGE") // @UseExperimental is experimental itself
    // and the -Xuse-experimental doesn't work properly
    fun toTlRepr(): IntArray = if (!bare) intArrayOf(
        _id ?: error("Boxed serialization not possible on bare types")
    ) + _toTlRepr() else _toTlRepr()

    fun asTlObject() = this
    val native: N

    /*
    Sample implementation:
    @ExperimentalUnsignedTypes
    override val _id: UInt?
        get() = Companion._id
    If you don't use a getter, it crashes with an obscure ClassCastException. No idea why.
     */
    val _id: Int?

    val fields: Map<String, TLObject<*>?>
}

interface TLConstructor<T : TLObject<*>> {
    fun _fromTlRepr(data: IntArray, offset: Int = 0): Pair<Int, T>?
    @Suppress("EXPERIMENTAL_API_USAGE") // @UseExperimental is experimental itself
                                                // and the -Xuse-experimental doesn't work properly
    fun fromTlRepr(data: IntArray, bare: Boolean = false, offset: Int = 0): Pair<Int, T>? {
        // the Int is the count of bytes consumed, for Vectors
        if (!bare && id != null) {
            if (data[offset] != id)
                return null
            return _fromTlRepr(data, offset + 1)?.let {
                it.first + 1 to it.second
            }
        } else return _fromTlRepr(data, offset)
    }

    val id: Int?
}

interface TLTypeConstructor<T : TLObject<*>> : TLConstructor<T> {
    @Suppress("ImplicitNullableNothingType")
    override val id get() = null

    val constructors: Map<Int, TLConstructor<out T>>

    override fun _fromTlRepr(data: IntArray, offset: Int): Pair<Int, T>? {
        val constructor = constructors[data[offset]] ?: error("Attempting to deserialize unrecognized datatype (data=${data.contentToString()}, offset=$offset, constructors=$constructors)")
        return constructor.fromTlRepr(data, true, offset + 1)?.let {
            it.first + 1 to it.second
        }
    }
}

interface TLMethod<R : TLObject<*>> :
    TLObject<TLMethod<R>> {
    override val _id: Int

    val constructor: TLConstructor<R>

    @Suppress("UNCHECKED_CAST")
    fun castResult(result: TLObject<Any?>) = result as R
}