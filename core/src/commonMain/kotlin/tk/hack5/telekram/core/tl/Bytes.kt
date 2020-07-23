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

package tk.hack5.telekram.core.tl

fun ByteArray.asTlObject() = BytesObject(this, true)

class BytesObject(private val bytes: ByteArray, override val bare: Boolean) :
    TLObject<ByteArray> {
    @ExperimentalUnsignedTypes
    override fun _toTlRepr(): IntArray {
        return if (bytes.size >= 254) {
            val len = bytes.size.toByteArray(3)
            byteArrayOf(0xFE.toByte(), *len, *bytes)
        } else {
            byteArrayOf(bytes.size.toUByte().toByte(), *bytes)
        }.pad().toIntArray()
    }

    override val native = bytes

    override val _id: Int? = null

    override val fields by lazy { mapOf<String, TLObject<*>>() }

    companion object :
        TLConstructor<BytesObject> {
        @ExperimentalUnsignedTypes
        override fun _fromTlRepr(data: IntArray, offset: Int): Pair<Int, BytesObject>? {
            val arr = data.toByteArray()
            val off: Int
            val len = if (arr[offset * Int.SIZE_BYTES] != 0xFE.toByte()) {
                off = 1
                arr[offset * Int.SIZE_BYTES].toUByte().toInt()
            } else {
                off = 4
                byteArrayOf(
                    arr[offset * Int.SIZE_BYTES + 1],
                    arr[offset * Int.SIZE_BYTES + 2],
                    arr[offset * Int.SIZE_BYTES + 3]
                ).toInt()
            }
            val ret = arr.sliceArray(off + offset * Int.SIZE_BYTES until off + offset * Int.SIZE_BYTES + len)
            return Pair(
                (off + len + 3) / 4,
                BytesObject(ret, true)
            )
        }

        override val id: Int? = null
    }
}

private fun ByteArray.pad(multiple: Int = 4, padding: Byte = 0): ByteArray = this +
        ByteArray((multiple - (size % multiple)) % multiple) { padding }
