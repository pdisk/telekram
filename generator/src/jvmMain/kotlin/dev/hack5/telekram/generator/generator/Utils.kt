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
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
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
fun getNativeType(ident: String, context: Context, conditionalDef: ConditionalDef?, bare: Boolean, suffix: String? = null, rawType: Boolean = false): UnnamedParsedArg {
    if (context[ident]?.arg is OptArgOrArg.OptArg) {
        return UnnamedParsedArg(TypeVariableName(ident))
    }
    val calculatedBare = ident.first() == ident.first().toLowerCase() || bare
    val calculatedSuffix = suffix ?: if (bare) CONSTRUCTOR else TYPE
    if (rawType) {
        return UnnamedParsedArg(ClassName(context.packageName, transformUserFacingCombinatorName(ident, calculatedSuffix)), calculatedBare, conditionalDef)
    }
    return when (ident.toLowerCase()) {
        "#" -> return UnnamedParsedArg(UINT.copy(nullable = conditionalDef != null), true, conditionalDef)
        "int" -> UnnamedParsedArg(INT.copy(nullable = conditionalDef != null), calculatedBare, conditionalDef)
        "long" -> UnnamedParsedArg(LONG.copy(nullable = conditionalDef != null), calculatedBare, conditionalDef)
        "double" -> UnnamedParsedArg(DOUBLE.copy(nullable = conditionalDef != null), calculatedBare, conditionalDef)
        "string" -> UnnamedParsedArg(STRING.copy(nullable = conditionalDef != null), calculatedBare, conditionalDef)
        "bytes" -> UnnamedParsedArg(BYTE_ARRAY.copy(nullable = conditionalDef != null), calculatedBare, conditionalDef)
        "vector" -> UnnamedParsedArg(LIST.copy(nullable = conditionalDef != null), calculatedBare, conditionalDef)
        "bool" -> {
            assert(!bare)
            UnnamedParsedArg(BOOLEAN.copy(nullable = conditionalDef != null), calculatedBare, conditionalDef)
        }
        "true" -> {
            assert(conditionalDef != null)
            assert(bare)
            UnnamedParsedArg(BOOLEAN, calculatedBare, conditionalDef)
        }
        else -> UnnamedParsedArg(ClassName(context.packageName, transformUserFacingCombinatorName(ident, calculatedSuffix)), calculatedBare, conditionalDef)
    }
}

@ExperimentalUnsignedTypes
fun getNativeType(comb: Combinator, context: Context, suffix: String? = null, rawType: Boolean = false): TypeName {
    return getNativeType(comb.id.name!!, context, null, true, suffix, rawType).type
        .let {
            if (comb.optArgs.isNotEmpty())
                (it as ClassName).parameterizedBy(comb.optArgs.map { STAR })
            else
                it
        }
}

@ExperimentalUnsignedTypes
fun getNativeType(type: ResultType, context: Context, rawType: Boolean = false): TypeName {
    return getNativeType(type.name, context, null, false, rawType = rawType).type
        .let {
            if (type.generics.isNotEmpty())
                (it as ClassName).parameterizedBy(type.generics.map { STAR })
            else
                it
        }
}

@ExperimentalUnsignedTypes
fun Term.ignoreBare(): Term = if (this is Term.PercentTerm) term.ignoreBare() else this

fun FileSpec.Builder.suppressWarnings(vararg warnings: String): FileSpec.Builder = apply {
    addAnnotation(
        AnnotationSpec.builder(ClassName("kotlin", "Suppress"))
            .addMember(List(warnings.size) { "%S" }.joinToString(), *warnings)
            .build()
    )
}

const val CONSTRUCTOR = "Object"
const val FUNCTION = "Request"
const val TYPE = "Type"

val UINT = ClassName("kotlin", "UInt")

val BUFFER = ClassName("dev.hack5.telekram.core.tl", "Buffer")
val TL_OBJECT = ClassName("dev.hack5.telekram.core.tl", "TLObject")
val TL_FUNCTION = ClassName("dev.hack5.telekram.core.tl", "TLFunction")
val TL_DESERIALIZER = ClassName("dev.hack5.telekram.core.tl", "TLDeserializer")
val TL_INT = ClassName("dev.hack5.telekram.core.tl", "IntType")
val TL_LONG = ClassName("dev.hack5.telekram.core.tl", "LongType")
val TL_DOUBLE = ClassName("dev.hack5.telekram.core.tl", "DoubleType")
val TL_STRING = ClassName("dev.hack5.telekram.core.tl", "StringType")
val TL_BYTE_ARRAY = ClassName("dev.hack5.telekram.core.tl", "BytesType")
val TL_LIST = ClassName("dev.hack5.telekram.core.tl", "VectorType")
val TL_BOOL = ClassName("dev.hack5.telekram.core.tl", "BoolType")
val TL_INT_BARE = ClassName("dev.hack5.telekram.core.tl", "IntObject")
val TL_LONG_BARE = ClassName("dev.hack5.telekram.core.tl", "LongObject")
val TL_DOUBLE_BARE = ClassName("dev.hack5.telekram.core.tl", "DoubleObject")
val TL_STRING_BARE = ClassName("dev.hack5.telekram.core.tl", "StringObject")
val TL_BYTE_ARRAY_BARE = ClassName("dev.hack5.telekram.core.tl", "BytesObject")
val TL_LIST_BARE = ClassName("dev.hack5.telekram.core.tl", "VectorObject")
val TL_BOOL_BARE = ClassName("dev.hack5.telekram.core.tl", "BoolObject")
val TL_BASE = ClassName("dev.hack5.telekram.core.tl", "TLBase")

val TYPE_NOT_FOUND_ERROR = ClassName("dev.hack5.telekram.core.tl", "TypeNotFoundError")