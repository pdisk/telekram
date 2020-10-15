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

@file:Suppress("EXPERIMENTAL_API_USAGE") // TODO

package dev.hack5.telekram.core.exports

import dev.hack5.telekram.core.client.TelegramClient
import dev.hack5.telekram.core.crypto.AuthKey
import dev.hack5.telekram.core.state.MTProtoStateImpl
import dev.hack5.telekram.core.state.MemorySession
import dev.hack5.telekram.core.tl.Auth_ExportAuthorizationRequest
import dev.hack5.telekram.core.tl.Auth_ExportedAuthorizationObject
import dev.hack5.telekram.core.tl.DcOptionObject
import org.gciatto.kt.math.BigInteger

suspend fun TelegramClient.exportDC(dc: Int, cdn: Boolean, mediaOnly: Boolean): TelegramClient {
    val session = getExportSession(dc, cdn, mediaOnly) ?: return this
    val client = exportSession(session)
    client.connect()
    return client
}

suspend fun TelegramClient.getExportSession(dc: Int, cdn: Boolean, mediaOnly: Boolean): MemorySession? {
    if (dc == serverConfig.value?.thisDc) return null
    val options = serverConfig.value!!.dcOptions.filterIsInstance<DcOptionObject>().filter {
        it.id == dc && it.cdn == cdn && it.mediaOnly == mediaOnly
                && !it.tcpoOnly // TODO support tcpo
    }
    require(options.isNotEmpty()) { "Unable to get DC options for $dc (cdn=$cdn, mediaOnly=$mediaOnly, all=${serverConfig.value!!.dcOptions})" }
    val auth = this(Auth_ExportAuthorizationRequest(dc)) as Auth_ExportedAuthorizationObject
    val option = options.random()
    return MemorySession(dc, option.ipAddress, option.port, MTProtoStateImpl(AuthKey(BigInteger(auth.bytes))))
}