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
fun parseSubexpr(subExpr: SubExpr, context: Context, nameAllocator: NameAllocator, conditionalDef: ConditionalDef?): UnnamedParsedArg {
    return when (subExpr) {
        is SubExpr.AdditionExpr -> error("Unexpected $subExpr")
        is SubExpr.TermExpr -> parseTerm(subExpr.term, context, nameAllocator, conditionalDef)
    }
}

@ExperimentalUnsignedTypes
fun parseExpr(expr: Expr, context: Context, nameAllocator: NameAllocator, conditionalDef: ConditionalDef?): UnnamedParsedArg {
    return expr.subexprs.map { parseSubexpr(it, context, nameAllocator, conditionalDef) }.let {
        println(it)
        it.singleOrNull() ?: it.first().parameterizedBy(*it.drop(1).toTypedArray())
    }
}

@ExperimentalUnsignedTypes
fun getGenericExpr(expr: Expr, context: Context, nameAllocator: NameAllocator, conditionalDef: ConditionalDef?): UnnamedParsedArg {
    return getNativeType(expr.subexprs.asSequence().filterIsInstance<SubExpr.TermExpr>().map { it.term.ignoreBare() }.filterIsInstance<Term.TypeIdentTerm>().map { it.type }.single(), context, conditionalDef)
}

@ExperimentalUnsignedTypes
fun parseTerm(term: Term, context: Context, nameAllocator: NameAllocator, conditionalDef: ConditionalDef?): UnnamedParsedArg {
    return when (term) {
        is Term.ExprTerm -> parseExpr(term.expr, context, nameAllocator, conditionalDef)
        is Term.TypeIdentTerm -> getNativeType(term.type, context, conditionalDef)
        is Term.VarIdentTerm -> error("Unexpected $term")
        is Term.NatConstTerm -> error("Unexpected $term")
        is Term.PercentTerm -> parseTerm(term.term, context, nameAllocator, conditionalDef).copy(bare = true)
        is Term.GenericTerm -> getNativeType(term.type, context, conditionalDef).parameterizedBy(*term.generics.map { getGenericExpr(it, context, nameAllocator, conditionalDef) }.toTypedArray())
    }
}

@ExperimentalUnsignedTypes
fun getUserFacingType(parameter: TypeTerm, context: Context, nameAllocator: NameAllocator, conditionalDef: ConditionalDef?): UnnamedParsedArg {
    val term = parseTerm(parameter.term, context, nameAllocator, conditionalDef)
    if (parameter.excl) {
        return term.copy(type = ClassName(OUTPUT_PACKAGE_NAME, "TLFunction").parameterizedBy(term.type))
    }
    return term
}

@ExperimentalUnsignedTypes
fun getUserFacingType(parameter: Arg, context: Context, nameAllocator: NameAllocator): UnnamedParsedArg {
    return when (parameter) {
        is Arg.SimpleArg -> getUserFacingType(parameter.type, context, nameAllocator, parameter.conditionalDef)
        is Arg.BracketArg -> {
            val single = parameter.innerArgs.singleOrNull()
            val auxType = if (single == null) {
                TODO()
            } else {
                getUserFacingType(single, Context(context), nameAllocator).type
            }
            UnnamedParsedArg(LIST.parameterizedBy(auxType), true)
        }
        is Arg.TypeArg -> getUserFacingType(parameter.type, context, nameAllocator, null)
    }
}

@ExperimentalUnsignedTypes
fun getUserFacingParameter(parameter: Arg, index: Int, context: Context, nameAllocator: NameAllocator): ParsedArg<OptArgOrArg.Arg> {
    val (tag, suggestion) = transformUserFacingParameterName(parameter.name, index)
    val name = nameAllocator.newName(suggestion, tag)
    val type = getUserFacingType(parameter, context, nameAllocator)
    val ret = ParsedArg(parameter, name, type)
    context += ret
    return ret
}

object BufferName
object BareName
data class ParameterName(val name: String)
data class UnnamedParameter(val index: Int)

@ExperimentalUnsignedTypes
fun FunSpec.Builder.beginFlagsBlock(parameter: ParsedArg<OptArgOrArg.Arg>) {
    beginControlFlow(
        when (parameter.type) {
            BOOLEAN -> "if (${parameter.name})"
            else -> "if (${parameter.name} != null)"
        },
        parameter.name
    )
}

@ExperimentalUnsignedTypes
fun CodeBlock.Builder.writeSum(parameters: Iterable<ParsedArg<OptArgOrArg.Arg>>) {
    val others = mutableListOf<String>()
    val sum = parameters.sumBy {
        when (it.type) {
            UINT -> 4
            INT -> 4
            LONG -> 8
            DOUBLE -> 8
            else -> {
                others.add("${it.name}.tlSize")
                0
            }
        } + if (it.bare) 0 else 4
    }
    val sizes = listOf(sum.toString()) + others
    addStatement(sizes.joinToString(" + "))
}

@ExperimentalUnsignedTypes
fun getReader(parameter: UnnamedParsedArg, buffer: String): Pair<String, Array<TypeName>> = when (parameter.type.copy(nullable = false)) {
    UINT -> "$buffer.readInt().toUInt()" to arrayOf()
    INT -> "$buffer.readInt()" to arrayOf()
    LONG -> "$buffer.readLong()" to arrayOf()
    DOUBLE -> "$buffer.readDouble()" to arrayOf()
    STRING -> "$buffer.readBytes().asString()" to arrayOf()
    BYTE_ARRAY -> "$buffer.readBytes()" to arrayOf()
    is ParameterizedTypeName -> {
        if ((parameter.type as ParameterizedTypeName).rawType.copy(nullable = false) == LIST) {
            val reader = getReader(parameter.parameters.single(), buffer)
            "(0U until $buffer.readInt().toUInt()).map { ${reader.first} }" to reader.second
        } else {
            TODO("non-vector generic parameters")
        }
    }
    else -> if (parameter.bare) {
        "%T.fromTlRepr($buffer)" to arrayOf(parameter.type)
    } else {
        "%T[$buffer.readInt()].fromTlRepr($buffer)" to arrayOf(parameter.type)
    }
}

@ExperimentalUnsignedTypes
fun getToTlRepr(buffer: String, bare: String, id: Int, parameters: Map<String, ParsedArg<OptArgOrArg.Arg>>) = FunSpec.builder("toTlRepr")
    .addModifiers(KModifier.OVERRIDE)
    .addParameter(buffer, BUFFER)
    .addParameter(bare, BOOLEAN)
    .beginControlFlow("if (!$bare)")
    .addStatement("$buffer.writeInt($id)")
    .endControlFlow()
    .apply {
        if (parameters.values.any { it.conditionalDef != null }) {
            val initialized = mutableSetOf<ConditionalDef>()
            for (parameter in parameters.values) {
                parameter.conditionalDef?.let {
                    if (initialized.add(parameter.conditionalDef)) {
                        addStatement("val ${parameter.conditionalDef.name} = 0U")
                    }
                    beginFlagsBlock(parameter)
                    val fieldIndex = parameter.conditionalDef.index?.toInt() ?: 0
                    assert(fieldIndex < 32)
                    addStatement("${parameter.conditionalDef.name} = ${parameter.conditionalDef.name} or ${1U shl fieldIndex}")
                    endControlFlow()
                }
            }
        }
        for (parameter in parameters.values) {
            parameter.conditionalDef?.let {
                val conditionalDef = parameters[it.name]
                assert(conditionalDef!!.type == UINT)
                beginFlagsBlock(parameter)
            }
            addStatement("%N.toTlRepr($buffer, ${parameter.bare})", parameter.name)
            parameter.conditionalDef?.let {
                endControlFlow()
            }
        }
    }
    .build()

@ExperimentalUnsignedTypes
fun getFromTlRepr(buffer: String, parameters: Map<String, ParsedArg<OptArgOrArg.Arg>>, typeName: String) = FunSpec.builder("fromTlRepr")
    .addModifiers(KModifier.OVERRIDE)
    .addParameter(buffer, BUFFER)
    .apply {
        for (parameter in parameters.values) {
            parameter.conditionalDef?.let {
                val conditionalDef = parameters[it.name]
                assert(conditionalDef!!.type == UINT)
                beginControlFlow("if (%N.shr(${it.index}).and(1U) == 1U)", conditionalDef.name)
            }
            val reader = getReader(UnnamedParsedArg(parameter), buffer)
            addStatement("val ${parameter.name} = ${reader.first}", *reader.second)
            parameter.conditionalDef?.let {
                endControlFlow()
            }
        }
    }
    .addStatement("return $typeName(${parameters.values.joinToString(", ") { it.name }})")
    .build()

@ExperimentalUnsignedTypes
fun generateRequest(function: Combinator, nameAllocator: NameAllocator): TypeSpec {
    val context = Context(null)
    val functionName = transformUserFacingCombinatorName(function.id, FUNCTION)
    function.optArgs.forEach {
        context += ParsedArg(it, it.name, UnnamedParsedArg(TypeVariableName(it.name)))
    }
    var index = 0
    @Suppress("UNCHECKED_CAST") val parameters = flattenParameters(function.args).associate {
        val arg = getUserFacingParameter(it, index++, context, nameAllocator)
        arg.name to arg
    }
    val userParameters = stripImplicitParameters(parameters)
    val primaryConstructor = FunSpec.constructorBuilder().also {
        it.parameters.addAll(userParameters.values.map { param -> ParameterSpec.builder(param.name, param.type).build() })
    }.build()
    val superclass = ClassName("dev.hack5.telekram.core.tl", "TLFunction").parameterizedBy(getNativeType(function.resultType.name, context, null).parameterizedBy(*function.resultType.generics.map { getGenericExpr(Expr(listOf(it)), context, nameAllocator, null) }.toTypedArray()).type)
    val generics = function.optArgs.map { TypeVariableName(it.name, ClassName(OUTPUT_PACKAGE_NAME, "TLObject")) }
    val buffer = nameAllocator.newName("buffer", BufferName)
    val bare = nameAllocator.newName("bare", BareName)
    val toTlRepr = getToTlRepr(buffer, bare, function.crc.toInt(), parameters)

    return TypeSpec.classBuilder(functionName)
        .primaryConstructor(primaryConstructor)
        .addProperties(userParameters.values.map { PropertySpec.builder(it.name, it.type).initializer(it.name).build() })
        .addProperty(PropertySpec.builder("tlSize", INT).delegate(buildCodeBlock {
            beginControlFlow("lazy(LazyThreadSafetyMode.PUBLICATION)")
            writeSum(parameters.values)
            endControlFlow()
        }).build())
        .addSuperinterface(superclass)
        .addTypeVariables(generics)
        .addFunction(toTlRepr)
        .addModifiers(KModifier.DATA)
        .build()
}

@ExperimentalUnsignedTypes
fun generateType(type: Combinator, nameAllocator: NameAllocator): TypeSpec {
    val context = Context(null)
    val typeName = transformUserFacingCombinatorName(type.id, CONSTRUCTOR)
    type.optArgs.forEach {
        context += ParsedArg(it, it.name, UnnamedParsedArg(TypeVariableName(it.name)))
    }
    var index = 0
    @Suppress("UNCHECKED_CAST") val parameters = flattenParameters(type.args).associate {
        val arg = getUserFacingParameter(it, index++, context, nameAllocator)
        arg.name to arg
    }
    val userParameters = stripImplicitParameters(parameters)
    val primaryConstructor = FunSpec.constructorBuilder().also {
        it.parameters.addAll(userParameters.values.map { param -> ParameterSpec.builder(param.name, param.type).build() })
    }.build()
    val superclass = ClassName("dev.hack5.telekram.core.tl", "TLObject").parameterizedBy(getNativeType(type.resultType.name, context, null).parameterizedBy(*type.resultType.generics.map { getGenericExpr(Expr(listOf(it)), context, nameAllocator, null) }.toTypedArray()).type)
    val generics = type.optArgs.map { TypeVariableName(it.name, ClassName(OUTPUT_PACKAGE_NAME, "TLObject")) }
    val buffer = nameAllocator.newName("buffer", BufferName)
    val bare = nameAllocator.newName("bare", BareName)
    val toTlRepr = getToTlRepr(buffer, bare, type.crc.toInt(), parameters)
    val fromTlRepr = getFromTlRepr(buffer, parameters, typeName)

    val companion = TypeSpec.companionObjectBuilder()
        .addFunction(fromTlRepr)
        .build()

    return TypeSpec.classBuilder(typeName)
        .primaryConstructor(primaryConstructor)
        .addProperties(userParameters.values.map { PropertySpec.builder(it.name, it.type).initializer(it.name).build() })
        .addProperty(PropertySpec.builder("tlSize", INT).delegate(buildCodeBlock {
            beginControlFlow("lazy(LazyThreadSafetyMode.PUBLICATION)")
            writeSum(parameters.values)
            endControlFlow()
        }).build())
        .addSuperinterface(superclass)
        .addTypeVariables(generics)
        .addFunction(toTlRepr)
        .addModifiers(KModifier.DATA)
        .addType(companion)
        .build()}

@ExperimentalUnsignedTypes
data class UnnamedParsedArg(val type: TypeName, val bare: Boolean = false, val conditionalDef: ConditionalDef? = null, val parameters: List<UnnamedParsedArg> = emptyList()) {
    fun parameterizedBy(args: Array<UnnamedParsedArg>): UnnamedParsedArg {
        if (args.isEmpty()) {
            return this
        }
        type as ClassName
        val type = type.parameterizedBy(*args.map { it.type }.toTypedArray())
        return copy(type = type, parameters = parameters + args)
    }

    companion object {
        operator fun invoke(arg: ParsedArg<OptArgOrArg.Arg>): UnnamedParsedArg {
            return UnnamedParsedArg(arg.type, arg.bare, arg.conditionalDef, arg.parameters)
        }
    }
}

@ExperimentalUnsignedTypes
data class ParsedArg<A : OptArgOrArg> (val arg: A, val name: String, val type: TypeName, val bare: Boolean, val conditionalDef: ConditionalDef? = null, val parameters: List<UnnamedParsedArg> = emptyList()) {
    companion object {
        operator fun invoke(arg: Arg, name: String, parsedArg: UnnamedParsedArg): ParsedArg<OptArgOrArg.Arg> {
            return ParsedArg(OptArgOrArg.Arg(arg), name, parsedArg.type, parsedArg.bare, parsedArg.conditionalDef, parsedArg.parameters)
        }
        operator fun invoke(arg: OptArg, name: String, parsedArg: UnnamedParsedArg): ParsedArg<OptArgOrArg.OptArg> {
            return ParsedArg(OptArgOrArg.OptArg(arg), name, parsedArg.type, parsedArg.bare, parsedArg.conditionalDef, parsedArg.parameters)
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