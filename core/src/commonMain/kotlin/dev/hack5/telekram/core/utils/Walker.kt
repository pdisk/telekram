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

package dev.hack5.telekram.core.utils

import dev.hack5.telekram.core.tl.TLObject

abstract class TLWalker<T> {
    open val result: T? = null

    fun walk(tlObject: TLObject<*>): T? {
        for (field in tlObject.fields) {
            if (handle(field.key, field.value))
                field.value?.let { walk(it) }
        }
        return result
    }

    protected open fun handle(key: String, value: TLObject<*>?): Boolean = true
}