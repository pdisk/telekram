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

fun Int.asTlObject() = IntObject(this, true)

data class IntObject(private val int: Int, override val bare: Boolean) :
    TLObject<Int> {
    override fun _toTlRepr(): IntArray {
        return intArrayOf(int)
    }

    override val native = int

    override val _id = id

    override val fields by lazy { mapOf<String, TLObject<*>>() }

    companion object :
        TLConstructor<IntObject> {
        override fun _fromTlRepr(data: IntArray, offset: Int): Pair<Int, IntObject>? {
            return data.getOrNull(offset)?.let {
                Pair(1, IntObject(it, true))
            }
        }

        override val id: Int? = null
    }
}