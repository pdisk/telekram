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

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.NameAllocator
import dev.hack5.telekram.generator.generator.*
import dev.hack5.telekram.generator.parser.*
import kotlinx.ast.common.*
import kotlinx.ast.common.ast.AstNode
import kotlinx.ast.parser.antlr.kotlin.AntlrKotlinParserExtractor
import kotlinx.ast.parser.antlr.kotlin.antlrKotlinParser
import org.antlr.v4.kotlinruntime.tree.ParseTree
import tk.hack5.telekram.generator.tl.TLLexer
import tk.hack5.telekram.generator.tl.TLParser
import java.nio.file.Path


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
fun parseAndSave(inputPath: String, outputDir: Path, packageName: String) {
    val source = AstSource.File(inputPath)

    val ast = antlrKotlinParser(source, TLParserExtractor, TLParserType.tl_file, ::TLLexer, ::TLParser) as AstNode
    val constrs = ast.flatten("constr_declarations").flatten("declaration").map {
        when (val decl = Declaration(it)) {
            is Combinator -> decl
            is BuiltinCombinator -> Combinator(
                decl.id.copy(givenId = decl.crc),
                emptyList(),
                listOf(Arg.SimpleArg(VarIdentOpt(decl.id.name), null, TypeTerm(false, Term.TypeIdentTerm(decl.id.name!!)))),
                decl.resultType
            )
        }
    }
    val byType = constrs.groupBy { it.resultType.name to it.resultType.generics.size }
    for (type in byType) {
        val args = parseArgs(type.value, NameAllocator(), packageName)
        println(type)
        val builder = FileSpec.builder(packageName, type.key.first)
        builder.suppressWarnings("RedundantVisibilityModifier", "RedundantUnitReturnType", "EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE", "UNNECESSARY_SAFE_CALL", "BooleanLiteralArgument")
        for (comb in type.value) {
            val (parameters, userParameters, context) = args.getValue(comb)
            println(comb)
            builder.addType(
                generateType(
                    comb,
                    parameters,
                    userParameters,
                    context,
                    NameAllocator()
                )
            )
        }
        builder.addType(generateBaseType(type.key.first, args, type.key.second, NameAllocator(), packageName))
        val built = builder.build()
        built.writeTo(outputDir.resolve(built.name))
    }
    val funcs = ast.flatten("fun_declarations").flatten("declaration")
    for (decl in funcs) {
        val comb = Declaration(decl) as? Combinator ?: continue
        val (parameters, userParameters, context) = parseArgs(comb, NameAllocator(), packageName)
        val builder = FileSpec.builder(packageName, comb.id.name ?: continue)
        builder.suppressWarnings("RedundantVisibilityModifier", "RedundantUnitReturnType", "EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE", "UNNECESSARY_SAFE_CALL", "BooleanLiteralArgument")
        println(comb)
        builder.addType(
            generateRequest(
                comb,
                parameters,
                userParameters,
                context,
                NameAllocator()
            )
        )
        val built = builder.build()
        built.writeTo(outputDir.resolve(built.name))
    }
}



fun writeErrors(input: String, outputPath: Path, packageName: String) {
    val file = outputPath.toFile()
    file.parentFile.mkdirs()
    val writer = file.bufferedWriter()
    //ErrorsWriter({ writer.write(it) }, packageName, File(input).readLines().drop(1).map { Error(it) }).build()
    writer.close()
}

@ExperimentalUnsignedTypes
fun main() {
    parseAndSave(
        "resources/schema-mtproto.tl",
        Path.of("..", "core", "generated", "commonMain", "dev", "hack5", "telekram", "core", "mtproto"),
        "dev.hack5.telekram.core.mtproto"
    )
    println("===========")
    parseAndSave(
        "resources/schema.tl",
        Path.of("..", "core", "generated", "commonMain", "dev", "hack5", "telekram", "core", "tl"),
        "dev.hack5.telekram.core.tl"
    )
    writeErrors(
        "resources/errors.csv",
        Path.of("..", "core", "generated", "commonMain", "dev", "hack5", "telekram", "core", "errors", "Errors.kt"),
        "dev.hack5.telekram.core.errors"
    )
}