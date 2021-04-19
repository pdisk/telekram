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

import com.squareup.kotlinpoet.NameAllocator
import dev.hack5.telekram.generator.generator.generateRequest
import dev.hack5.telekram.generator.generator.generateType
import dev.hack5.telekram.generator.parser.Combinator
import kotlinx.ast.common.*
import kotlinx.ast.common.ast.AstNode
import kotlinx.ast.parser.antlr.kotlin.AntlrKotlinParserExtractor
import kotlinx.ast.parser.antlr.kotlin.antlrKotlinParser
import org.antlr.v4.kotlinruntime.tree.ParseTree
import dev.hack5.telekram.generator.parser.Declaration
import tk.hack5.telekram.generator.tl.TLLexer
import tk.hack5.telekram.generator.tl.TLParser
import java.io.File


@Suppress("EnumEntryName")
enum class TLParserType : AstParserType{
    tl_file,
    tl_program,
    declaration
}


object TLParserExtractor: AntlrKotlinParserExtractor<TLParser, TLParserType> {
    override fun extractor(type: TLParserType): (TLParser) -> ParseTree {
        return when (type) {
            TLParserType.tl_file -> TLParser::tl_file
            TLParserType.tl_program -> TLParser::tl_program
            TLParserType.declaration -> TLParser::declaration
        }
    }
}


@ExperimentalUnsignedTypes
fun parseAndSave(inputPath: String, outputDir: String, packageName: String) {
    val source = AstSource.File(inputPath)

    val ast = antlrKotlinParser(source, TLParserExtractor, TLParserType.tl_file, ::TLLexer, ::TLParser) as AstNode
    ast.flatten("constr_declarations").flatten("declaration").forEach {
        val decl = Declaration(it)
        println("constr")
        println(decl.toString(true))
        println((decl as? Combinator)?.let { comb ->
            generateType(comb, NameAllocator())
        })
    }
    ast.flatten("fun_declarations").flatten("declaration").forEach {
        val decl = Declaration(it)
        println("fun")
        println(decl.toString(true))
        println((decl as? Combinator)?.let { comb ->
            generateRequest(comb, NameAllocator())
        })
    }
    val constrs = ast.flatten("constr_declarations").flatten("declaration")
    val byType = constrs.map { Declaration(it) as? Combinator }.filterNotNull().groupBy { it.resultType.name to it.resultType.generics.size }
    for (type in byType) {
        for (comb in type.value) {
            File("/home/penn/telekramtest/${type.key.first}.kt").writeText(
                generateType(
                    comb,
                    NameAllocator()
                ).toString()
            )
        }
    }
    val funcs = ast.flatten("fun_declarations").flatten("declaration")
    for (decl in funcs) {
        val comb = Declaration(decl) as? Combinator ?: continue
        File("/home/penn/telekramtest/${comb.id.name}.kt").writeText(
            generateRequest(
                comb,
                NameAllocator()
            ).toString()
        )
    }
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
    println("===========")
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