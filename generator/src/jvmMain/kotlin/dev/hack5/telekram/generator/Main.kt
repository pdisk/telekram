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

package dev.hack5.telekram.generator

import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.File


@ExperimentalUnsignedTypes
fun parseAndSave(inputPath: String, outputDir: String, packageName: String) {
    val inputFile = File(inputPath)
    val inputData = inputFile.readText()
    println(TLData(inputData))
}

fun writeErrors(input: String, outputPath: String, packageName: String) {
    val file = File(outputPath)
    file.parentFile.mkdirs()
    val writer = file.bufferedWriter()
    //ErrorsWriter({ writer.write(it) }, packageName, File(input).readLines().drop(1).map { Error(it) }).build()
    writer.close()
}

@ExperimentalUnsignedTypes
fun main() {
    parseAndSave(
        "resources/schema-mtproto.tl",
        "../core/generated/commonMain/dev/hack5/telekram/core/mtproto",
        "dev.hack5.telekram.core.mtproto"
    )
    parseAndSave(
        "resources/schema.tl",
        "../core/generated/commonMain/dev/hack5/telekram/core/tl",
        "dev.hack5.telekram.core.tl"
    )
    writeErrors(
        "resources/errors.csv",
        "../core/generated/commonMain/dev/hack5/telekram/core/errors/Errors.kt",
        "dev.hack5.telekram.core.errors"
    )
}