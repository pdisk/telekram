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
fun parseSubexpr(subExpr: SubExpr, context: Context, nameAllocator: NameAllocator, conditionalDef: ConditionalDef?, bare: Boolean): UnnamedParsedArg {
    return when (subExpr) {
        is SubExpr.AdditionExpr -> error("Unexpected $subExpr")
        is SubExpr.TermExpr -> parseTerm(subExpr.term, context, nameAllocator, conditionalDef, bare)
    }
}

@ExperimentalUnsignedTypes
fun parseExpr(expr: Expr, context: Context, nameAllocator: NameAllocator, conditionalDef: ConditionalDef?, bare: Boolean): UnnamedParsedArg {
    return expr.subexprs.map { parseSubexpr(it, context, nameAllocator, conditionalDef, bare) }.let {
        println(it)
        it.singleOrNull() ?: it.first().parameterizedBy(*it.drop(1).toTypedArray())
    }
}

@ExperimentalUnsignedTypes
fun getGenericExpr(expr: Expr, context: Context, nameAllocator: NameAllocator, conditionalDef: ConditionalDef?): UnnamedParsedArg {
    return getNativeType(expr.subexprs.asSequence().filterIsInstance<SubExpr.TermExpr>().map { it.term.ignoreBare() }.filterIsInstance<Term.TypeIdentTerm>().map { it.type }.single(), context, conditionalDef, false)
}

@ExperimentalUnsignedTypes
fun parseTerm(term: Term, context: Context, nameAllocator: NameAllocator, conditionalDef: ConditionalDef?, bare: Boolean): UnnamedParsedArg {
    return when (term) {
        is Term.ExprTerm -> parseExpr(term.expr, context, nameAllocator, conditionalDef, bare)
        is Term.TypeIdentTerm -> getNativeType(term.type, context, conditionalDef, bare)
        is Term.VarIdentTerm -> error("Unexpected $term")
        is Term.NatConstTerm -> error("Unexpected $term")
        is Term.PercentTerm -> parseTerm(term.term, context, nameAllocator, conditionalDef, true)
        is Term.GenericTerm -> getNativeType(term.type, context, conditionalDef, bare).parameterizedBy(*term.generics.map { getGenericExpr(it, context, nameAllocator, conditionalDef) }.toTypedArray())
    }
}

@ExperimentalUnsignedTypes
fun getUserFacingType(parameter: TypeTerm, context: Context, nameAllocator: NameAllocator, conditionalDef: ConditionalDef?): UnnamedParsedArg {
    val term = parseTerm(parameter.term, context, nameAllocator, conditionalDef, false)
    if (parameter.excl) {
        return term.copy(type = TL_FUNCTION.parameterizedBy(term.type))
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
                getUserFacingType(single, Context(context), nameAllocator)
            }
            UnnamedParsedArg(LIST, true).parameterizedBy(auxType)
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
            BOOLEAN -> if (it.bare) 0 else 4
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
    BOOLEAN -> (if (parameter.bare) "true" else "$buffer.readBoolean(false)") to arrayOf()
    is ParameterizedTypeName -> {
        if ((parameter.type as ParameterizedTypeName).rawType.copy(nullable = false) == LIST) {
            val reader = getReader(parameter.parameters.single(), buffer)
            "(0U until $buffer.readInt().toUInt()).map { ${reader.first} }" to reader.second
        } else {
            TODO("non-vector generic parameters")
        }
    }
    else -> "%T.fromTlRepr($buffer)" to arrayOf(parameter.type)
}

@ExperimentalUnsignedTypes
fun getToTlRepr(buffer: String, bare: String, id: Int, parameters: Map<String, ParsedArg<OptArgOrArg.Arg>>) = FunSpec.builder("toTlRepr")
    .addModifiers(KModifier.OVERRIDE)
    .addParameter(buffer, BUFFER)
    .addParameter(bare, BOOLEAN)
    .beginControlFlow("if (!$bare)")
    .addStatement("$buffer.write($id, true)")
    .endControlFlow()
    .apply {
        if (parameters.values.any { it.conditionalDef != null }) {
            val initialized = mutableSetOf<ConditionalDef>()
            for (parameter in parameters.values) {
                parameter.conditionalDef?.let {
                    if (initialized.add(parameter.conditionalDef)) {
                        addStatement("var ${parameter.conditionalDef.name} = 0U")
                    }
                    beginFlagsBlock(parameter)
                    val fieldIndex = parameter.conditionalDef.index?.toInt() ?: 0
                    assert(fieldIndex < 32)
                    addStatement("${parameter.conditionalDef.name} = ${parameter.conditionalDef.name} or ${1 shl fieldIndex}U", )
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
            val toInt = if (parameter.type.copy(nullable = false) == UINT) "?.toInt()" else ""
            val innerBare = if (parameter.type.copy(nullable = false).removeParameters() == LIST) ", " + parameter.parameters.single().bare else ""
            addStatement("$buffer.write(%N$toInt, ${parameter.bare}$innerBare)", parameter.name)
            parameter.conditionalDef?.let {
                endControlFlow()
            }
        }
    }
    .build()

@ExperimentalUnsignedTypes
fun getFromTlRepr(buffer: String, bare: String, id: Int, parameters: Map<String, ParsedArg<OptArgOrArg.Arg>>, userParameters: Map<String, ParsedArg<OptArgOrArg.Arg>>, typeName: TypeName) = FunSpec.builder("fromTlRepr")
    .addModifiers(KModifier.OVERRIDE)
    .addParameter(buffer, BUFFER)
    .returns(typeName)
    .apply {
        for (parameter in parameters.values) {
            if (parameter.type == BOOLEAN) {
                val conditionalDef = parameters[parameter.conditionalDef!!.name]
                assert(conditionalDef!!.type == UINT)
                addStatement("val ${parameter.name} = %N.shr(${parameter.conditionalDef.index}).and(1U) != 0U", conditionalDef.name)
                continue
            }
            addCode("val ${parameter.name} = ")
            parameter.conditionalDef?.let {
                val conditionalDef = parameters[it.name]
                assert(conditionalDef!!.type == UINT)
                beginControlFlow("if (%N.shr(${it.index}).and(1U) == 1U)", conditionalDef.name)
            }
            val reader = getReader(UnnamedParsedArg(parameter), buffer)
            addStatement(reader.first, *reader.second)
            parameter.conditionalDef?.let {
                nextControlFlow("else")
                val default = if (parameter.type == BOOLEAN) false else null
                addStatement("$default")
                endControlFlow()
            }
        }
    }
    .addStatement("return %T(${userParameters.values.joinToString(", ") { it.name }})", typeName)
    .build()

@ExperimentalUnsignedTypes
fun getFields(userParameters: Map<String, ParsedArg<OptArgOrArg.Arg>>): CodeBlock {
    return buildCodeBlock {
        beginControlFlow("lazy")
        val conversions = userParameters.map {
            when (it.value.type.copy(nullable = false).removeParameters()) {
                INT -> TL_INT
                LONG -> TL_LONG
                DOUBLE -> TL_DOUBLE
                STRING -> TL_STRING
                BYTE_ARRAY -> TL_BYTES
                LIST -> TL_LIST
                BOOLEAN -> TL_BOOL
                else -> null
            }
        }
        val format = conversions.joinToString { if (it == null) "%S to %N" else "%S to %T(%N)" }
        val parameters = userParameters.values.zip(conversions).flatMap {
            if (it.second == null)
                listOf(it.first.name, it.first.name)
            else
                listOf(it.first.name, it.second, it.first.name)
        }
        addStatement("mapOf($format)", *parameters.toTypedArray())
        endControlFlow()
    }
}

@ExperimentalUnsignedTypes
fun parseArgs(constructor: Combinator, nameAllocator: NameAllocator, packageName: String): Triple<Map<String, ParsedArg<OptArgOrArg.Arg>>, Map<String, ParsedArg<OptArgOrArg.Arg>>, Context> {
    val context = Context(null, packageName)
    constructor.optArgs.forEach {
        context += ParsedArg(it, it.name, UnnamedParsedArg(TypeVariableName(it.name)))
    }
    var index = 0
    @Suppress("UNCHECKED_CAST") val parameters = flattenParameters(constructor.args).associate {
        val arg = getUserFacingParameter(it, index++, context, nameAllocator)
        (it.name.ident ?: arg.name) to arg
    }
    return Triple(parameters, stripImplicitParameters(parameters), context)
}

@ExperimentalUnsignedTypes
fun parseArgs(constructors: List<Combinator>, nameAllocator: NameAllocator, packageName: String): Map<Combinator, Triple<Map<String, ParsedArg<OptArgOrArg.Arg>>, Map<String, ParsedArg<OptArgOrArg.Arg>>, Context>> {
    val args = constructors.associateWith { parseArgs(it, nameAllocator.copy(), packageName) }

    val commonParams = args.values.map { it.second.mapValues { arg -> arg.value.copy(bare = false) } }.reduce { acc, arg ->
        println("acc=$acc")
        val tmpAcc = acc.toMutableMap()
        for (entry in arg) {
            if (acc[entry.key]?.copy(bare = false)?.equals(entry.value.copy(bare = false)) == false)
                tmpAcc.remove(entry.key)
        }
        tmpAcc
    }.values.toSet()

    return args.mapValues { comb ->
        Triple(
            comb.value.first.mapValues { it.value.copy(common = it.value.copy(bare = false) in commonParams) },
            comb.value.second.mapValues { it.value.copy(common = it.value.copy(bare = false) in commonParams) },
            comb.value.third
        )
    }
}

@ExperimentalUnsignedTypes
fun getEquals(type: TypeName, userParameters: Map<String, ParsedArg<OptArgOrArg.Arg>>) = FunSpec.builder("equals").apply {
    returns(BOOLEAN)
    addModifiers(KModifier.OVERRIDE)
    addParameter("other", ANY.copy(nullable = true))
    addStatement("if (this === other) return true")
    addStatement("if (other !is %T) return false", type)
    for (param in userParameters) {
        addStatement("if (other.${param.value.name} != ${param.value.name}) return false")
    }
    addStatement("return true")
}.build()

@ExperimentalUnsignedTypes
fun getHashCode(type: TypeName, userParameters: Map<String, ParsedArg<OptArgOrArg.Arg>>) = FunSpec.builder("hashCode").apply {
    returns(INT)
    addModifiers(KModifier.OVERRIDE)
    var i = 1
    addStatement("return " + userParameters.values.joinToString(" + ") {
        i *= 31
        it.name + ".hashCode() * " + (i / 31)
    })
}.build()

@ExperimentalUnsignedTypes
fun getBaseFromTlRepr(type: TypeName, constructors: Set<Combinator>, packageName: String) = FunSpec.builder("fromTlRepr").apply {
    returns(type)
    addModifiers(KModifier.OVERRIDE)
    addParameter("buffer", BUFFER)
    beginControlFlow("return when (val id = buffer.readInt()) {")
    for (constructor in constructors) {
        addStatement("${constructor.crc.toInt()} -> %T", getNativeType(constructor, Context(null, packageName)))
    }
    addStatement("else -> throw %T(id, buffer)", TYPE_NOT_FOUND_ERROR)
    endControlFlow()
    addCode(".fromTlRepr(buffer)")
}.build()

@ExperimentalUnsignedTypes
fun generateRequest(
    function: Combinator,
    parameters: Map<String, ParsedArg<OptArgOrArg.Arg>>,
    userParameters: Map<String, ParsedArg<OptArgOrArg.Arg>>,
    context: Context,
    nameAllocator: NameAllocator
): TypeSpec {
    val functionName = transformUserFacingCombinatorName(function.id, FUNCTION)
    val primaryConstructor = FunSpec.constructorBuilder().also {
        it.parameters.addAll(userParameters.values.map { param -> ParameterSpec.builder(param.name, param.type).build() })
    }.build()
    val superclass = TL_FUNCTION.parameterizedBy(getNativeType(function.resultType, context))
    val generics = function.optArgs.map { TypeVariableName(it.name, TL_OBJECT) }
    val buffer = nameAllocator.newName("buffer", BufferName)
    val bare = nameAllocator.newName("bare", BareName)
    val toTlRepr = getToTlRepr(buffer, bare, function.crc.toInt(), parameters)
    val className = getNativeType(function, Context(null, context.packageName))
    val fields = getFields(userParameters)
    val equals = getEquals(className, userParameters)
    val hashCode = getHashCode(className, userParameters)

    return TypeSpec.classBuilder(functionName)
        .primaryConstructor(primaryConstructor)
        .addProperties(userParameters.values.map { PropertySpec.builder(it.name, it.type).initializer(it.name).build() })
        .addProperty(
            PropertySpec.builder("tlSize", INT)
                .delegate(buildCodeBlock {
                    beginControlFlow("lazy(LazyThreadSafetyMode.PUBLICATION)")
                    writeSum(parameters.values)
                    endControlFlow()
                })
                .addModifiers(KModifier.OVERRIDE)
                .build()
        )
        .addProperty(
            PropertySpec.builder("fields", MAP.parameterizedBy(STRING, TL_BASE))
                .delegate(fields)
                .addModifiers(KModifier.OVERRIDE)
                .build()
        )
        .superclass(superclass)
        .addTypeVariables(generics)
        .addFunction(toTlRepr)
        .addFunction(equals)
        .addFunction(hashCode)
        .addModifiers(KModifier.DATA)
        .build()
}

@ExperimentalUnsignedTypes
fun generateType(
    type: Combinator,
    parameters: Map<String, ParsedArg<OptArgOrArg.Arg>>,
    userParameters: Map<String, ParsedArg<OptArgOrArg.Arg>>,
    context: Context,
    nameAllocator: NameAllocator
): TypeSpec {
    println(parameters)
    val typeName = transformUserFacingCombinatorName(type.id, CONSTRUCTOR)
    type.optArgs.forEach {
        context += ParsedArg(it, it.name, UnnamedParsedArg(TypeVariableName(it.name)))
    }
    val primaryConstructor = FunSpec.constructorBuilder().also {
        it.parameters.addAll(userParameters.values.map { param -> ParameterSpec.builder(param.name, param.type).build() })
    }.build()
    val superclass = getNativeType(type.resultType, context)
    val generics = type.optArgs.map { TypeVariableName(it.name, TL_OBJECT) }
    val buffer = nameAllocator.newName("buffer", BufferName)
    val bare = nameAllocator.newName("bare", BareName)
    val toTlRepr = getToTlRepr(buffer, bare, type.crc.toInt(), parameters)
    val className = getNativeType(type, Context(null, context.packageName))
    val fromTlRepr = getFromTlRepr(buffer, bare, type.crc.toInt(), parameters, userParameters, className)
    val fields = getFields(userParameters)
    val equals = getEquals(className, userParameters)
    val hashCode = getHashCode(className, userParameters)

    val companion = TypeSpec.companionObjectBuilder()
        .addSuperinterface(TL_DESERIALIZER.parameterizedBy(className))
        .addFunction(fromTlRepr)
        .build()

    return TypeSpec.classBuilder(typeName)
        .primaryConstructor(primaryConstructor)
        .addProperties(userParameters.values.map {
            PropertySpec.builder(it.name, it.type)
                .initializer(it.name)
                .apply {
                    if (it.common)
                        addModifiers(KModifier.OVERRIDE)
                }
                .build()
        })
        .addProperty(
            PropertySpec.builder("tlSize", INT)
                .delegate(buildCodeBlock {
                    beginControlFlow("lazy(LazyThreadSafetyMode.PUBLICATION)")
                    writeSum(parameters.values)
                    endControlFlow()
                })
                .addModifiers(KModifier.OVERRIDE)
                .build()
        )
        .addProperty(
            PropertySpec.builder("fields", MAP.parameterizedBy(STRING, TL_BASE))
                .delegate(fields)
                .addModifiers(KModifier.OVERRIDE)
                .build()
        )
        .superclass(superclass)
        .addTypeVariables(generics)
        .addFunction(toTlRepr)
        .addFunction(equals)
        .addFunction(hashCode)
        .addModifiers(KModifier.DATA)
        .addType(companion)
        .build()
}

@ExperimentalUnsignedTypes
fun generateBaseType(type: String, userParameters: Map<Combinator, Triple<Map<String, ParsedArg<OptArgOrArg.Arg>>, Map<String, ParsedArg<OptArgOrArg.Arg>>, Context>>, genericsCount: Int, nameAllocator: NameAllocator, packageName: String): TypeSpec {
    val typeName = transformUserFacingCombinatorName(type, TYPE)
    val superClass = TL_OBJECT
    val className = ClassName(packageName, typeName).let {
        if (genericsCount > 0) {
            it.parameterizedBy(List(genericsCount) { STAR })
        } else {
            it
        }
    }

    val fromTlRepr = getBaseFromTlRepr(className, userParameters.keys, packageName)

    val companion = TypeSpec.companionObjectBuilder()
        .addSuperinterface(TL_DESERIALIZER.parameterizedBy(className))
        .addFunction(fromTlRepr)
        .build()

    return TypeSpec.classBuilder(typeName)
        .addProperties(
            userParameters
                .values
                .map { it.second.values.filter { arg -> arg.common } }
                .flatten()
                .map { it.name to it.type }
                .toSet()
                .map {
                    PropertySpec.builder(it.first, it.second)
                        .addModifiers(KModifier.ABSTRACT)
                        .build()
                }
        )
        .addType(companion)
        .addSuperinterface(superClass)
        .addModifiers(KModifier.SEALED)
        .build()
}

@ExperimentalUnsignedTypes
data class UnnamedParsedArg(val type: TypeName, val bare: Boolean = false, val conditionalDef: ConditionalDef? = null, val parameters: List<UnnamedParsedArg> = emptyList()) {
    fun parameterizedBy(vararg args: UnnamedParsedArg): UnnamedParsedArg {
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
data class ParsedArg<A : OptArgOrArg> (val arg: A, val name: String, val type: TypeName, val bare: Boolean, val conditionalDef: ConditionalDef? = null, val parameters: List<UnnamedParsedArg> = emptyList(), val common: Boolean = false) {
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
class Context(private val parent: Context?, val packageName: String = parent!!.packageName) {
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

fun TypeName.removeParameters() = when (this) {
    is ParameterizedTypeName -> this.rawType
    else -> this
}
