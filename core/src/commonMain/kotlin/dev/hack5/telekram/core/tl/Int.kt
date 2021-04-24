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

data class IntObject(val int: Int) : TLObject {
    override val fields: Map<String, TLBase?>
        get() = emptyMap()

    override fun toTlRepr(buffer: Buffer, bare: Boolean) {
        buffer.write(int, bare)
    }

    override val tlSize: Int
        get() = 4

    companion object {

    }
}

public sealed class IntType : TLObject {
    public abstract val data_: String

    public companion object : TLDeserializer<DataJSONType> {
        public override fun fromTlRepr(buffer: Buffer): DataJSONType = when (val id = buffer.readInt())
        {
            2104790276 -> DataJSONObject
            else -> throw TypeNotFoundError(id, buffer)
        }
            .fromTlRepr(buffer)
    }
}
