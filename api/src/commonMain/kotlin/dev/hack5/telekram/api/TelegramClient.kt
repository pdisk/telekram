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

package dev.hack5.telekram.api

import com.github.aakira.napier.Napier
import dev.hack5.telekram.core.client.TelegramClient
import dev.hack5.telekram.core.client.TelegramClientCoreImpl
import dev.hack5.telekram.core.connection.Connection
import dev.hack5.telekram.core.connection.TcpFullConnection
import dev.hack5.telekram.core.encoder.EncryptedMTProtoEncoder
import dev.hack5.telekram.core.encoder.MTProtoEncoder
import dev.hack5.telekram.core.encoder.MTProtoEncoderWrapped
import dev.hack5.telekram.core.encoder.PlaintextMTProtoEncoder
import dev.hack5.telekram.core.packer.MessagePackerUnpacker
import dev.hack5.telekram.core.packer.MessagePackerUnpackerImpl
import dev.hack5.telekram.core.state.*
import dev.hack5.telekram.core.tl.UpdatesType
import dev.hack5.telekram.core.updates.UpdateHandler
import dev.hack5.telekram.core.updates.UpdateHandlerImpl
import dev.hack5.telekram.core.updates.UpdateOrSkipped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
// TODO use delegation
open class TelegramClientApiImpl(
    apiId: String, apiHash: String,
    parentScope: CoroutineScope = GlobalScope,
    connectionConstructor: (CoroutineScope, String, Int) -> Connection = ::TcpFullConnection,
    plaintextEncoderConstructor: (CoroutineScope) -> MTProtoEncoder = {
        PlaintextMTProtoEncoder(MTProtoStateImpl()).apply {
            state.scope = it
        }
    },
    encryptedEncoderConstructor: (MTProtoState, CoroutineScope) -> EncryptedMTProtoEncoder = { state, scope -> EncryptedMTProtoEncoder(state, scope) },
    updateHandlerConstructor: (CoroutineScope, UpdateState, TelegramClient) -> UpdateHandler? = { scope, state, client ->
        UpdateHandlerImpl(
            scope,
            state,
            client
        )
    },
    packerConstructor: (
        Connection,
        MTProtoEncoderWrapped,
        MTProtoState,
        Channel<UpdatesType>,
        CoroutineScope
    ) -> MessagePackerUnpacker = ::MessagePackerUnpackerImpl,
    deviceModel: String = "ktg",
    systemVersion: String = "0.0.1",
    appVersion: String = "0.0.1",
    systemLangCode: String = "en",
    langPack: String = "",
    langCode: String = "en",
    session: Session<*> = MemorySession(),
    maxFloodWait: Long = 0
) : TelegramClientCoreImpl(
    apiId, apiHash,
    parentScope,
    connectionConstructor,
    plaintextEncoderConstructor,
    encryptedEncoderConstructor,
    updateHandlerConstructor,
    packerConstructor,
    deviceModel,
    systemVersion,
    appVersion,
    systemLangCode,
    langPack,
    langCode,
    session,
    maxFloodWait
) {

    protected val handleUpdate: suspend (UpdateOrSkipped) -> Unit = handleUpdate@{
        for (handler in EventHandler.defaultHandlers) {
            handler.constructEvent(this, it)?.let { event ->
                eventCallbacks.forEach { callback ->
                    callback(event)
                }
                Napier.d("dispatched!")
                return@handleUpdate
            }
        }
    }

    override var updateCallbacks: List<suspend (UpdateOrSkipped) -> Unit> = listOf(handleUpdate)
        set(value) {
            field = if (value.contains(handleUpdate)) value else value + handleUpdate
        }

    var eventCallbacks: List<suspend (Event) -> Unit> = listOf()
}