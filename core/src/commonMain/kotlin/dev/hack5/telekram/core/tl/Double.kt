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

fun Double.asTlObject() = DoubleObject(this, true)

data class DoubleObject(private val double: Double, override val bare: Boolean) :
    TLObject<Double> {
    @ExperimentalUnsignedTypes
    override fun _toTlRepr(): IntArray {
        return LongObject(double.toRawBits(), bare)._toTlRepr()
    }

    override val native = double

    override val _id = id

    override val fields by lazy { mapOf<String, TLObject<*>>() }

    companion object :
        TLConstructor<DoubleObject> {
        @ExperimentalUnsignedTypes
        override fun _fromTlRepr(data: IntArray, offset: Int): Pair<Int, DoubleObject>? {
            val ret = LongObject._fromTlRepr(data, offset)
            return ret?.let {
                Pair(
                    it.first,
                    DoubleObject(Double.fromBits(it.second.native), true)
                )
            }
        }

        override val id: Int? = null
    }
}