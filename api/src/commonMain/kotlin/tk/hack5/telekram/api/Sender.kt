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

package tk.hack5.telekram.api

import tk.hack5.telekram.core.client.TelegramClient
import tk.hack5.telekram.core.tl.InputUserType
import tk.hack5.telekram.core.tl.PeerUserObject
import tk.hack5.telekram.core.tl.UserObject
import tk.hack5.telekram.core.tl.Users_GetUsersRequest
import tk.hack5.telekram.core.utils.toInputUser

interface SenderGetter {
    val senderId: Int?
    val client: TelegramClient

    suspend fun getSender(): UserObject? = getInputSender()?.let {
        client(Users_GetUsersRequest(listOf(it))).single() as UserObject
    }

    suspend fun getInputSender(): InputUserType? = senderId?.let {
        PeerUserObject(it).toInputUser(client)
    }
}