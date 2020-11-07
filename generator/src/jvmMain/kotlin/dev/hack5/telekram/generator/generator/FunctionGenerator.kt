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
        is Arg.SimpleArg -> getUserFacingType(parameter.type, context, nameAllocator)
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
fun getUserFacingParameter(parameter: Arg, context: Context, nameAllocator: NameAllocator): ParsedArg {
    val name = transformUserFacingParameterName(parameter.name)
    val type = getUserFacingType(parameter, context, nameAllocator)
    val ret = ParsedArg(parameter, name, type)
    context += ret
    return ret
}

@ExperimentalUnsignedTypes
fun generateRequest(function: Combinator, nameAllocator: NameAllocator): TypeSpec {
    val context = Context(null)
    val functionName = transformUserFacingCombinatorName(function.id, "Request")
    function.optArgs.forEach {
        context += ParsedArg(it, it.name, UnnamedParsedArg(TypeVariableName(it.name)))
    }
    val parameters = stripImplicitParameters(function.args).map {
        getUserFacingParameter(it, context, nameAllocator)
    }
    val primaryConstructor = FunSpec.constructorBuilder().also {
        it.parameters.addAll(parameters.map { param -> ParameterSpec.builder(param.name, param.type).build() })
    }.build()
    val superclass = ClassName("dev.hack5.telekram.core.tl", "TLFunction").parameterizedBy(getNativeType(function.resultType.name, context).parameterizedBy(*function.resultType.generics.map { getGenericExpr(Expr(listOf(it)), context, nameAllocator) }.toTypedArray()).type)
    val generics = function.optArgs.map { TypeVariableName(it.name, ClassName(OUTPUT_PACKAGE_NAME, "TLObject")) }
    val toTlRepr = FunSpec.builder("toTlRepr")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("buffer", BUFFER)
        .addParameter("bare", BOOLEAN)
        .build()


    return TypeSpec.classBuilder(functionName)
        .primaryConstructor(primaryConstructor)
        .addProperties(parameters.map { PropertySpec.builder(it.name, it.type).initializer(it.name).build() })
        .addSuperinterface(superclass)
        .addTypeVariables(generics)
        .addFunction(toTlRepr)
        .addModifiers(KModifier.DATA)
        .build()
}

@ExperimentalUnsignedTypes
fun generateType(type: Combinator, nameAllocator: NameAllocator): TypeSpec { TODO() }

@ExperimentalUnsignedTypes
data class UnnamedParsedArg(val type: TypeName, val bare: Boolean? = null) {
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
data class ParsedArg(val arg: OptArgOrArg, val name: String, val type: TypeName, val bare: Boolean?) {
    constructor(arg: Arg, name: String, parsedArg: UnnamedParsedArg) : this(OptArgOrArg.Arg(arg), name, parsedArg.type, parsedArg.bare)
    constructor(arg: OptArg, name: String, parsedArg: UnnamedParsedArg) : this(OptArgOrArg.OptArg(arg), name, parsedArg.type, parsedArg.bare)
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
    private val argsSoFarLocal = mutableMapOf<String, ParsedArg>()

    val argsSoFar: Map<String, ParsedArg>
        get() = parent?.let { argsSoFarLocal + it.argsSoFar } ?: argsSoFarLocal

    operator fun plusAssign(value: ParsedArg) {
        value.arg.name?.let { argsSoFarLocal[it] = value }
    }

    operator fun get(key: String) = argsSoFar[key]

    fun addAuxType(auxType: TypeSpec) {
        // TODO
    }
}