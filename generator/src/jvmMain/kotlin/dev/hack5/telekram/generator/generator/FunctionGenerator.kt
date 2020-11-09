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
import java.nio.Buffer
import java.nio.IntBuffer


// TODO rewrite this file to allow better handling of OptArgs

@ExperimentalUnsignedTypes
fun parseSubexpr(subExpr: SubExpr, context: Context, nameAllocator: NameAllocator): UnnamedParsedArg {
    return when (subExpr) {
        is SubExpr.AdditionExpr -> TODO()
        is SubExpr.TermExpr -> parseTerm(subExpr.term, context, nameAllocator)
    }
}

@ExperimentalUnsignedTypes
fun parseExpr(expr: Expr, context: Context, nameAllocator: NameAllocator): UnnamedParsedArg {
    return expr.subexprs.map { parseSubexpr(it, context, nameAllocator) }.let {
        println(it)
        it.singleOrNull() ?: it.first().parameterizedBy(*it.drop(1).map { it.type }.toTypedArray())
    }
}

@ExperimentalUnsignedTypes
fun getGenericExpr(expr: Expr, context: Context, nameAllocator: NameAllocator): TypeName {
    return getNativeType(expr.subexprs.asSequence().filterIsInstance<SubExpr.TermExpr>().map { it.term }.filterIsInstance<Term.TypeIdentTerm>().map { it.type }.single(), context).type
}

@ExperimentalUnsignedTypes
fun parseTerm(term: Term, context: Context, nameAllocator: NameAllocator): UnnamedParsedArg {
    return when (term) {
        is Term.ExprTerm -> parseExpr(term.expr, context, nameAllocator)
        is Term.TypeIdentTerm -> getNativeType(term.type, context)
        is Term.VarIdentTerm -> error("Unexpected $term")
        is Term.NatConstTerm -> error("Unexpected $term")
        is Term.PercentTerm -> parseTerm(term.term, context, nameAllocator).copy(bare = true)
        is Term.GenericTerm -> getNativeType(term.type, context).parameterizedBy(*term.generics.map { getGenericExpr(it, context, nameAllocator) }.toTypedArray())
    }
}

@ExperimentalUnsignedTypes
fun getUserFacingType(parameter: TypeTerm, context: Context, nameAllocator: NameAllocator): UnnamedParsedArg {
    val term = parseTerm(parameter.term, context, nameAllocator)
    if (parameter.excl) {
        return term.copy(type = ClassName("dev.hack5.telekram.core.tl", "TLFunction").parameterizedBy(term.type))
    }
    return term
}

@ExperimentalUnsignedTypes
fun getUserFacingType(parameter: Arg, context: Context, nameAllocator: NameAllocator): UnnamedParsedArg {
    return when (parameter) {
        is Arg.SimpleArg -> getUserFacingType(parameter.type, context, nameAllocator).copy(conditionalDef = parameter.conditionalDef)
        is Arg.BracketArg -> {
            val single = parameter.innerArgs.singleOrNull()
            val auxType = if (single == null) {
                TODO()
            } else {
                getUserFacingType(single, Context(context), nameAllocator).type
            }
            UnnamedParsedArg(LIST.parameterizedBy(auxType), true)
        }
        is Arg.TypeArg -> getUserFacingType(parameter.type, context, nameAllocator)
    }
}

@ExperimentalUnsignedTypes
fun getUserFacingParameter(parameter: Arg, context: Context, nameAllocator: NameAllocator): ParsedArg<OptArgOrArg.Arg> {
    val idealName = transformUserFacingParameterName(parameter.name)
    val name = nameAllocator.newName(idealName, ParameterName(idealName))
    val type = getUserFacingType(parameter, context, nameAllocator)
    val ret = ParsedArg(parameter, name, type)
    context += ret
    return ret
}

object BufferName
object BareName
object IdName
data class ParameterName(val name: String)

@ExperimentalUnsignedTypes
fun generateRequest(function: Combinator, nameAllocator: NameAllocator): TypeSpec {
    val buffer = nameAllocator.newName("buffer", BufferName)
    val bare = nameAllocator.newName("bare", BareName)
    val id = nameAllocator.newName("id", IdName)
    val context = Context(null)
    val functionName = transformUserFacingCombinatorName(function.id, "Request")
    function.optArgs.forEach {
        context += ParsedArg(it, it.name, UnnamedParsedArg(TypeVariableName(it.name)))
    }
    val parameters = function.args.associate {
        val arg = getUserFacingParameter(it, context, nameAllocator)
        arg.arg.name!! to arg
    }
    val userParameters = stripImplicitParameters(parameters)
    val primaryConstructor = FunSpec.constructorBuilder().also {
        it.parameters.addAll(parameters.values.map { param -> ParameterSpec.builder(param.name, param.type).build() })
    }.build()
    val superclass = ClassName("dev.hack5.telekram.core.tl", "TLFunction").parameterizedBy(getNativeType(function.resultType.name, context).parameterizedBy(*function.resultType.generics.map { getGenericExpr(Expr(listOf(it)), context, nameAllocator) }.toTypedArray()).type)
    val generics = function.optArgs.map { TypeVariableName(it.name, ClassName(OUTPUT_PACKAGE_NAME, "TLObject")) }
    val toTlRepr = FunSpec.builder("toTlRepr")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter(buffer, BUFFER)
        .addParameter(bare, BOOLEAN)
        .beginControlFlow("if (!$bare)")
        .addStatement("$buffer.writeInt($id)")
        .endControlFlow()
        .apply {
            for (parameter in parameters.values) {
                parameter.conditionalDef?.let {
                    val param = parameters[it.name]
                    println(it.name)
                    println(parameters.keys)
                    assert(param!!.type == UINT)
                    beginControlFlow("if (%N.shr(${it.index}).and(1U) == 1U)", it.name)
                }
                val writer = when (parameter.type) {
                    UINT -> "$buffer.writeInt(%N.toInt())"
                    INT -> "$buffer.writeInt(%N)"
                    LONG -> "$buffer.writeLong(%N)"
                    DOUBLE -> "$buffer.writeDouble(%N)"
                    STRING -> "$buffer.writeBytes(%N.asByteArray())"
                    BYTE_ARRAY -> "$buffer.writeBytes(%N)"
                    else -> "%N.toTlRepr($buffer, ${parameter.bare})"
                }
                addStatement(writer, parameter.name)
                parameter.conditionalDef?.let {
                    endControlFlow()
                }
            }
        }
        .build()
    /*

                val reader = when (parameter.type) {
                    UINT -> "readInt().toUInt()"
                    INT -> "readInt()"
                    LONG -> "readLong()"
                    DOUBLE -> "readDouble()"
                    STRING -> "readBytes().asString()"
                    BYTE_ARRAY -> "readBytes()"
                    else -> TODO()
                }
     */


    return TypeSpec.classBuilder(functionName)
        .primaryConstructor(primaryConstructor)
        .addProperties(parameters.values.map { PropertySpec.builder(it.name, it.type).initializer(it.name).build() })
        .addProperty(PropertySpec.builder(id, INT).initializer(function.crc.toInt().toString()).build())
        .addProperty(PropertySpec.builder("tlSize", INT).delegate(buildCodeBlock {
            beginControlFlow("lazy(LazyThreadSafetyMode.PUBLICATION)")
            val sum = parameters.values.sumBy {
                when (it.type) {
                    UINT -> 4
                    INT -> 4
                    LONG -> 8
                    DOUBLE -> 8
                    else -> 0
                }
            }
            val dynamics = parameters.values.joinToString(" + ") { "${it.name}.tlSize" }
            addStatement("$sum + $dynamics")
            endControlFlow()
        }).build())
        .addSuperinterface(superclass)
        .addTypeVariables(generics)
        .addFunction(toTlRepr)
        .addModifiers(KModifier.DATA)
        .build()
}

@ExperimentalUnsignedTypes
fun generateType(type: Combinator, nameAllocator: NameAllocator): TypeSpec { TODO() }

@ExperimentalUnsignedTypes
data class UnnamedParsedArg(val type: TypeName, val bare: Boolean = false, val conditionalDef: ConditionalDef? = null) {
    fun parameterizedBy(vararg parameters: TypeName): UnnamedParsedArg {
        if (parameters.isEmpty()) {
            return this
        }
        type as ClassName
        val type = type.parameterizedBy(*parameters)
        return copy(type = type)
    }
}

@ExperimentalUnsignedTypes
data class ParsedArg<A : OptArgOrArg> (val arg: A, val name: String, val type: TypeName, val bare: Boolean, val conditionalDef: ConditionalDef? = null) {
    companion object {
        operator fun invoke(arg: Arg, name: String, parsedArg: UnnamedParsedArg): ParsedArg<OptArgOrArg.Arg> {
            return ParsedArg(OptArgOrArg.Arg(arg), name, parsedArg.type, parsedArg.bare, parsedArg.conditionalDef)
        }
        operator fun invoke(arg: OptArg, name: String, parsedArg: UnnamedParsedArg): ParsedArg<OptArgOrArg.OptArg> {
            return ParsedArg(OptArgOrArg.OptArg(arg), name, parsedArg.type, parsedArg.bare, parsedArg.conditionalDef)
        }
    }
}

@ExperimentalUnsignedTypes
sealed class OptArgOrArg {
    abstract val name: String?

    data class OptArg(val optArg: dev.hack5.telekram.generator.parser.OptArg) : OptArgOrArg() {
        override val name get() = optArg.name
    }
    data class Arg(val arg: dev.hack5.telekram.generator.parser.Arg) : OptArgOrArg() {
        override val name get() = arg.name.ident
    }
}

@ExperimentalUnsignedTypes
class Context(private val parent: Context?) {
    private val argsSoFarLocal = mutableMapOf<String, ParsedArg<*>>()

    val argsSoFar: Map<String, ParsedArg<*>>
        get() = parent?.let { argsSoFarLocal + it.argsSoFar } ?: argsSoFarLocal

    operator fun plusAssign(value: ParsedArg<*>) {
        value.arg.name?.let { argsSoFarLocal[it] = value }
    }

    operator fun get(key: String) = argsSoFar[key]

    fun addAuxType(auxType: TypeSpec) {
        // TODO
    }
}