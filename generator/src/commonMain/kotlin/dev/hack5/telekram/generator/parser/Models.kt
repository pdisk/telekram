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

package dev.hack5.telekram.generator.parser

import com.strumenta.kotlinmultiplatform.assert
import kotlinx.ast.common.ast.AstNode
import kotlinx.ast.common.ast.AstTerminal
import kotlinx.ast.common.filter
import kotlinx.ast.common.flatten


@ExperimentalUnsignedTypes
data class TLFile(val constructors: List<Declaration>, val functions: List<Declaration>) {
    companion object {
        operator fun invoke(ast: AstNode): TLFile {
            val constructors = ast.flatten("constr_declarations").flatten("declaration").map(Declaration::invoke)
            val functions = ast.flatten("fun_declarations").flatten("declaration").map(Declaration::invoke)
            return TLFile(constructors, functions)
        }
    }
}


@ExperimentalUnsignedTypes
sealed class Declaration {
    companion object {
        operator fun invoke(ast: AstNode): Declaration {
            assert(ast.description == "declaration")
            val child = ast.children.filterIsInstance<AstNode>().single()
            return when (child.description) {
                "combinator_decl" -> {
                    Combinator(child)
                }
                "builtin_combinator_decl" -> {
                    BuiltinCombinator(child)
                }
                "partial_app_decl" -> {
                    TODO("partial_app")
                }
                "final_decl" -> {
                    TODO("final")
                }
                else -> {
                    error("Unknown $child")
                }
            }
        }
    }

    fun calculateId() = crcOf(toString(false)).toUInt()

    override fun toString() = toString(false)
    abstract fun toString(includeHash: Boolean): String
    abstract val crc: UInt
}

data class VarIdentOpt(private val rawIdent: String?) {
    val ident: String? get() = rawIdent?.let { if (it == "_") null else it }

    companion object {
        operator fun invoke(ast: AstNode?) = VarIdentOpt(ast?.let {
            assert(ast.description == "var_ident_opt")
            ast.flattenString()
        })
    }

    override fun toString() = rawIdent ?: ""
}
@ExperimentalUnsignedTypes
data class FullCombinatorId(val name: String?, val givenId: UInt?) {
    companion object {
        operator fun invoke(ast: AstNode): FullCombinatorId {
            assert(ast.description == "full_combinator_id")
            if (ast.children.single() is AstTerminal)
                return FullCombinatorId(null, null)
            val name = (ast.flatten("lc_ident_ns").single()).flattenString()
            val id = ast.flatten("hex_const").singleOrNull()?.flattenString()?.toUInt(16)
            return FullCombinatorId(name, id)
        }
    }

    override fun toString() = name ?: "_"
}
@ExperimentalUnsignedTypes
data class Combinator(val id: FullCombinatorId, val optArgs: List<OptArg>, val args: List<Arg>, val resultType: ResultType) : Declaration() {
    companion object {
        operator fun invoke(ast: AstNode): Combinator {
            assert(ast.description == "combinator_decl")
            val fullCombinatorId = FullCombinatorId(ast.children.filter("full_combinator_id").single() as AstNode)
            val optArgs = OptArgs(ast.children.filter("opt_args").filterIsInstance<AstNode>())
            val args = Args(ast.children.filter("args").filterIsInstance<AstNode>())
            val resultType = ResultType(ast.children.filter("result_type").single() as AstNode)
            return Combinator(fullCombinatorId, optArgs, args, resultType)
        }
    }

    override fun toString(includeHash: Boolean): String {
        val fullId = id.name?.let { if (includeHash) "$it#" + crc.toString(16).padStart(8, '0') else it } ?: ""
        return "$fullId ${optArgs.joinToString("") { "$it " }}${args.joinToString("") { "$it " }}= $resultType"
    }

    override val crc by lazy { id.givenId ?: calculateId() }
}
@ExperimentalUnsignedTypes
data class BuiltinCombinator(val id: FullCombinatorId, val resultType: ResultType) : Declaration() {
    companion object {
        operator fun invoke(ast: AstNode): BuiltinCombinator {
            assert(ast.description == "builtin_combinator_decl")
            val id = FullCombinatorId(ast.children.filter("full_combinator_id").single() as AstNode)
            val resultType = ResultType(ast.children.filter("boxed_type_ident").single().flattenString(), listOf())
            return BuiltinCombinator(id, resultType)
        }
    }

    override fun toString(includeHash: Boolean): String {
        val fullId = id.name?.let { if (includeHash) "$it#" + crc.toString(16).padStart(8, '0') else it } ?: ""
        return "$fullId ? = $resultType"
    }

    override val crc by lazy { id.givenId ?: calculateId() }
}
@ExperimentalUnsignedTypes
fun OptArgs(ast: List<AstNode>): List<OptArg> = ast.flatMap(OptArg::invoke)
@ExperimentalUnsignedTypes
data class OptArg(val name: String, val type: TypeExpr) {
    companion object {
        operator fun invoke(ast: AstNode): List<OptArg> {
            assert(ast.description == "opt_args")
            val names = ast.children.filter("var_ident").map { it.flattenString() }
            val type = TypeExpr(ast.children.filter("type_expr").single() as AstNode)
            return names.map { OptArg(it, type) }
        }
    }

    override fun toString() = "$name:$type"
}
@ExperimentalUnsignedTypes
fun Args(ast: List<AstNode>): List<Arg> = ast.flatMap(Arg::invoke)
@ExperimentalUnsignedTypes
sealed class Arg {
    data class SimpleArg(val name: VarIdentOpt, val conditionalDef: ConditionalDef?, val type: TypeTerm) : Arg() {
        companion object {
            operator fun invoke(ast: AstNode): SimpleArg {
                val name = VarIdentOpt(ast.children.filter("var_ident_opt").single() as AstNode)
                val conditionalDef = ast.children.filter("conditional_def").singleOrNull()?.let {
                    ConditionalDef(it as AstNode)
                }
                val type = TypeTerm(ast.children.filter("type_term").single() as AstNode)
                return SimpleArg(name, conditionalDef, type)
            }
        }

        override fun toString() = name.toString().let { if (it.isEmpty()) "" else "$it:" } + "${conditionalDef ?: ""}$type"
    }

    data class BracketArg(val name: VarIdentOpt, val multiplicity: Term?, val innerArgs: List<Arg>) : Arg() {
        companion object {
            operator fun invoke(ast: AstNode): BracketArg {
                val name = VarIdentOpt(ast.children.filter("var_ident_opt").singleOrNull() as AstNode?)
                val multiplicity = ast.children.filter("multiplicity").singleOrNull()?.let {
                    Term(it.flatten("term").single())
                }
                val innerArgs = Args(ast.children.filter("args").filterIsInstance<AstNode>())
                return BracketArg(name, multiplicity, innerArgs)
            }
        }

        override fun toString() = name.toString().let { if (it.isEmpty()) "" else "$it:" } + (multiplicity?.let { "$it*" } ?: "") + "[ " + innerArgs.joinToString(" ") + " ]"
    }

    data class TypeArg(val type: TypeTerm) : Arg() {
        companion object {
            operator fun invoke(ast: AstNode): TypeArg {
                return TypeArg(TypeTerm(ast.children.first() as AstNode))
            }
        }

        override fun toString() = type.toString()
    }

    companion object {
        operator fun invoke(ast: AstNode): List<Arg> {
            assert(ast.description == "args")
            val child = ast.children.filterIsInstance<AstNode>().single()
            return when (child.description) {
                "simple_arg" -> listOf(SimpleArg(child))
                "bracket_arg" -> listOf(BracketArg(child))
                "par_arg" -> {
                    val names = child.children.filter("var_ident_opt").filterIsInstance<AstNode>().map(VarIdentOpt::invoke)
                    val type = TypeTerm(child.children.filter("type_term").single() as AstNode)
                    return names.map { SimpleArg(it, null, type) }
                }
                "type_arg" -> listOf(TypeArg(child))
                else -> error("Unknown $child")
            }
        }
    }
}
@ExperimentalUnsignedTypes
data class ConditionalDef(val name: String, val index: UInt?) {
    companion object {
        operator fun invoke(ast: AstNode): ConditionalDef {
            assert(ast.description == "conditional_def")
            val name = ast.children.first().flattenString()
            val index = ast.children.filter("nat_const").singleOrNull()?.flattenString()?.toUInt()
            return ConditionalDef(name, index)
        }
    }

    override fun toString() = if (index == null) name else "$name.$index?"
}
@ExperimentalUnsignedTypes
data class TypeExpr(val exclMark: Boolean, val expr: Expr) {
    companion object {
        operator fun invoke(ast: AstNode): TypeExpr {
            assert(ast.description == "type_expr")
            val exclMark = ast.children.any { it is AstTerminal }
            val expr = Expr(ast.children.last() as AstNode)
            return TypeExpr(exclMark, expr)
        }
    }

    override fun toString() = expr.toString()
}
@ExperimentalUnsignedTypes
data class ResultType(val name: String, val generics: List<SubExpr>) {
    companion object {
        operator fun invoke(ast: AstNode): ResultType {
            assert(ast.description == "result_type")
            val ident = ast.children.filter("boxed_type_ident").flattenString()
            val generics = ast.children.filter("subexpr").filterIsInstance<AstNode>().map(SubExpr::invoke)
            return ResultType(ident, generics)
        }
    }

    override fun toString() = if (generics.isEmpty()) name else "$name ${generics.joinToString(" ")}"
}
@ExperimentalUnsignedTypes
data class Expr(val subexprs: List<SubExpr>) {
    companion object {
        operator fun invoke(ast: AstNode): Expr {
            assert(ast.description == "expr")
            val subexprs = ast.children.filter("subexpr").filterIsInstance<AstNode>().map { SubExpr(it) }
            return Expr(subexprs)
        }
    }

    override fun toString() = subexprs.joinToString(" ")
}
@ExperimentalUnsignedTypes
sealed class SubExpr {
    data class AdditionExpr(val number: Int, val subexpr: SubExpr, private val reverse: Boolean) : SubExpr() {
        override fun toString() = if (reverse) "$subexpr + $number" else "$number + $subexpr"
    }
    data class TermExpr(val term: Term) : SubExpr() {
        override fun toString() = term.toString()
    }

    companion object {
        operator fun invoke(ast: AstNode): SubExpr {
            assert(ast.description == "subexpr")
            val number = ast.children.filter("nat_const").singleOrNull()?.flattenString()?.toInt()
            return if (number == null) {
                val term = Term(ast.children.filter("term").single() as AstNode)
                TermExpr(term)
            } else {
                val subexpr = SubExpr(ast.children.filter("subexpr").single() as AstNode)
                AdditionExpr(number, subexpr, ast.children.first().description == "subexpr")
            }
        }
    }
}
@ExperimentalUnsignedTypes
sealed class Term {
    data class ExprTerm(val expr: Expr) : Term() {
        override fun toString() = expr.toString()
    }
    data class TypeIdentTerm(val type: String) : Term() {
        override fun toString() = type
    }
    data class VarIdentTerm(val name: String) : Term() {
        override fun toString() = name
    }
    data class NatConstTerm(val natConst: UInt) : Term() {
        override fun toString() = natConst.toString()
    }
    data class PercentTerm(val term: Term) : Term() {
        override fun toString() = "%$term"
    }
    data class GenericTerm(val type: String, val generics: List<Expr>) : Term() {
        override fun toString() = if (generics.isEmpty()) type else "$type ${generics.joinToString(" ")}"
    }

    companion object {
        operator fun invoke(ast: AstNode): Term {
            assert(ast.description == "term")
            val single = ast.children.filterIsInstance<AstNode>().singleOrNull()
            return when (single?.description) {
                "expr" -> ExprTerm(Expr(single))
                "type_ident" -> TypeIdentTerm(single.flattenString())
                "var_ident" -> VarIdentTerm(single.flattenString())
                "nat_const" -> NatConstTerm(single.flattenString().toUInt())
                "term" -> PercentTerm(Term(single))
                null -> {
                    val type = ast.children.filter("type_ident").flattenString()
                    val generics = ast.children.filter("expr").filterIsInstance<AstNode>().map(Expr::invoke)
                    GenericTerm(type, generics)
                }
                else -> error("Unknown $single")
            }
        }
    }
}
@ExperimentalUnsignedTypes
data class TypeTerm(val excl: Boolean, val term: Term) {
    companion object {
        operator fun invoke(ast: AstNode): TypeTerm {
            assert(ast.description == "type_term")
            val excl = ast.children.first() is AstTerminal
            val term = Term(ast.children.last() as AstNode)
            return TypeTerm(excl, term)
        }
    }

    override fun toString() = term.toString()
}
