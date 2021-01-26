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

package dev.hack5.telekram.core.client

import dev.hack5.telekram.core.tl.TLMethod
import dev.hack5.telekram.core.tl.TLObject

open class SkippingUpdatesTelegramClient(
    val client: TelegramClient
) : TelegramClient by client {
    override suspend operator fun <N, R : TLObject<N>> invoke(
        request: TLMethod<R>,
        skipEntities: Boolean,
        skipUpdates: Boolean,
        packer: (suspend (TLMethod<*>) -> TLObject<*>)?
    ): N {
        return client.invoke(request, skipEntities, true, packer)
    }
}