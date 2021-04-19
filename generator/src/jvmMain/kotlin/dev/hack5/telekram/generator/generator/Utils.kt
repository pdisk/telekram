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
import dev.hack5.telekram.generator.parser.*

@ExperimentalUnsignedTypes
fun flattenParameters(parameters: List<Arg>): List<Arg> {
    return parameters.map {
        when (it) {
            is Arg.SimpleArg -> listOf(it)
            is Arg.BracketArg -> flattenParameters(List((it.multiplicity as Term.NatConstTerm?)?.natConst?.toInt() ?: 1) { _ -> it.innerArgs }.flatten())
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
fun stripImplicitParameters(parameters: Map<String, ParsedArg<OptArgOrArg.Arg>>): Map<String, ParsedArg<OptArgOrArg.Arg>> {
    val toStrip = parameters.values.mapNotNull { it.conditionalDef?.name }
    return parameters.filterKeys { it !in toStrip }
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

fun transformUserFacingParameterName(name: VarIdentOpt, index: Int): Pair<Any, String> {
    val ret = transformUserFacingName(name.ident ?: return UnnamedParameter(index) to "unnamed_$index", false)
    return ParameterName(ret) to ret
}

@ExperimentalUnsignedTypes
fun transformUserFacingCombinatorName(combinatorId: String, extension: String): String {
    return transformUserFacingName(combinatorId, true) + extension
}

@ExperimentalUnsignedTypes
fun transformUserFacingCombinatorName(combinatorId: FullCombinatorId, extension: String) = transformUserFacingCombinatorName(combinatorId.name!!, extension)

@ExperimentalUnsignedTypes
fun getNativeType(ident: String, context: Context, conditionalDef: ConditionalDef?): UnnamedParsedArg {
    if (context[ident] != null) {
        return UnnamedParsedArg(TypeVariableName(ident))
    }
    val bare = ident.first() == ident.first().toLowerCase()
    return when (ident.toLowerCase()) {
        "#" -> return UnnamedParsedArg(UINT.copy(nullable = conditionalDef != null), true, conditionalDef)
        "int" -> UnnamedParsedArg(INT.copy(nullable = conditionalDef != null), bare, conditionalDef)
        "long" -> UnnamedParsedArg(LONG.copy(nullable = conditionalDef != null), bare, conditionalDef)
        "double" -> UnnamedParsedArg(DOUBLE.copy(nullable = conditionalDef != null), bare, conditionalDef)
        "string" -> UnnamedParsedArg(STRING.copy(nullable = conditionalDef != null), bare, conditionalDef)
        "bytes" -> UnnamedParsedArg(BYTE_ARRAY.copy(nullable = conditionalDef != null), bare, conditionalDef)
        "vector" -> UnnamedParsedArg(LIST.copy(nullable = conditionalDef != null), bare, conditionalDef)
        "true" -> {
            assert(conditionalDef != null)
            UnnamedParsedArg(BOOLEAN, bare, conditionalDef)
        }
        else -> UnnamedParsedArg(ClassName(OUTPUT_PACKAGE_NAME, transformUserFacingCombinatorName(ident, CONSTRUCTOR)), bare, conditionalDef)
    }
}

@ExperimentalUnsignedTypes
fun Term.ignoreBare(): Term = if (this is Term.PercentTerm) term.ignoreBare() else this

const val CONSTRUCTOR = "Object"
const val FUNCTION = "Request"

const val OUTPUT_PACKAGE_NAME = "dev.hack5.telekram.core.tl" // TODO make dynamic-ish
val BUFFER = ClassName("dev.hack5.telekram.core.tl", "Buffer")
val UINT = ClassName("kotlin", "UInt")