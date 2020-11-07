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

package dev.hack5.telekram.generator.generator

import com.squareup.kotlinpoet.*
import dev.hack5.telekram.generator.parser.Arg
import dev.hack5.telekram.generator.parser.FullCombinatorId
import dev.hack5.telekram.generator.parser.VarIdentOpt

@ExperimentalUnsignedTypes
fun flatMapParameters(parameters: List<Arg>): List<Arg> {
    return parameters.map {
        when (it) {
            is Arg.SimpleArg -> listOf(it)
            is Arg.BracketArg -> flatMapParameters(it.innerArgs)
            is Arg.TypeArg -> listOf(it)
        }
    }.flatten()
}

@ExperimentalUnsignedTypes
val Arg.name: VarIdentOpt
    get() = when (this) {
        is Arg.SimpleArg -> name
        is Arg.BracketArg -> name
        is Arg.TypeArg -> VarIdentOpt(null)
    }



@ExperimentalUnsignedTypes
fun stripImplicitParameters(parameters: List<Arg>): List<Arg> {
    val toStrip = flatMapParameters(parameters).mapNotNull { (it as? Arg.SimpleArg)?.conditionalDef?.name }
    return parameters.filter { it.name.ident !in toStrip }
}


fun transformUserFacingName(name: String, initialCaps: Boolean): String {
    return name.fold(initialCaps to "") { acc, char ->
        when (char) {
            '_' -> true to acc.second
            '.' -> true to acc.second + '_'
            else -> false to acc.second + if (acc.first) char.toUpperCase() else char
        }
    }.second
}

fun transformUserFacingParameterName(name: VarIdentOpt) = transformUserFacingName(name.ident!!, false)

@ExperimentalUnsignedTypes
fun transformUserFacingCombinatorName(combinatorId: String, extension: String): String {
    return transformUserFacingName(combinatorId, true) + extension
}

@ExperimentalUnsignedTypes
fun transformUserFacingCombinatorName(combinatorId: FullCombinatorId, extension: String) = transformUserFacingCombinatorName(combinatorId.name!!, extension)

@ExperimentalUnsignedTypes
fun getNativeType(ident: String, context: Context): UnnamedParsedArg {
    if (context[ident] != null) {
        return UnnamedParsedArg(TypeVariableName(ident))
    }
    val bare = ident.first() == ident.first().toLowerCase()
    return when (ident.toLowerCase()) {
        "#" -> return UnnamedParsedArg(ClassName("kotlin", "UInt"), true)
        "int" -> UnnamedParsedArg(INT, bare)
        "long" -> UnnamedParsedArg(LONG, bare)
        "double" -> UnnamedParsedArg(DOUBLE, bare)
        "string" -> UnnamedParsedArg(STRING, bare)
        "bytes" -> UnnamedParsedArg(BYTE_ARRAY, bare)
        "vector" -> UnnamedParsedArg(LIST, bare)
        else -> return UnnamedParsedArg(ClassName(OUTPUT_PACKAGE_NAME, transformUserFacingCombinatorName(ident, CONSTRUCTOR)))
    }
}

const val CONSTRUCTOR = "Object"
const val FUNCTION = "Request"

const val OUTPUT_PACKAGE_NAME = "dev.hack5.telekram.core.tl" // TODO make dynamic-ish
val BUFFER = ClassName("dev.hack5.telekram.core.tl", "Buffer")