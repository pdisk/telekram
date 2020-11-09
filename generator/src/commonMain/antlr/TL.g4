grammar TL;

/* +++ Tokens +++ */

/* === Character classes and Simple identifiers and keywords === */
NUMBER_ : [0-9]+ ;
HEX_CONST_ : ([a-f0-9] | NUMBER_)+ ;
LC_IDENT_ : [a-z] ([_a-zA-Z] | HEX_CONST_)* ;
UC_IDENT_ : [A-Z] ([_a-zA-Z] | HEX_CONST_)* ;
namespace_ident : LC_IDENT_ ;
lc_ident_ns : (namespace_ident DOT)* LC_IDENT_ ;
uc_ident_ns : (namespace_ident DOT)* UC_IDENT_ ;
hex_const : HEX_CONST_ | NUMBER_ ;
lc_ident_full : lc_ident_ns (HASH hex_const)? ;
var_ident : LC_IDENT_ | UC_IDENT_ ;

/* === Tokens === */
UNDERSCORE : '_' ;
COLON : ':' ;
SEMICOLON : ';' ;
OPEN_PAR : '(' ;
CLOSE_PAR : ')' ;
OPEN_BRACKET : '[' ;
CLOSE_BRACKET : ']' ;
OPEN_BRACE : '{' ;
CLOSE_BRACE : '}' ;
TYPES : '---' WS? 'TYPES' WS? '---' ;
FUNCTIONS : '---' WS? 'functions' WS? '---' ;
nat_const : NUMBER_ ;
EQUALS : '=' ;
HASH : '#' ;
QUESTION_MARK : '?' ;
PERCENT : '%' ;
PLUS : '+' ;
LANGLE : '<' ;
RANGLE : '>' ;
COMMA : ',' ;
DOT : '.' ;
ASTERISK : '*' ;
EXCL_MARK : '!' ;
FINAL_KW : 'Final' WS ;
NEW_KW : 'New' WS ;
EMPTY_KW : 'Empty' WS ;
/* +++ Syntax +++ */
/* === General syntax of a TL program === */
tl_program : constr_declarations ((TYPES WS? constr_declarations) | (FUNCTIONS WS? fun_declarations))* ;
constr_declarations : declaration* ;
fun_declarations : declaration* ;
// Note this is modified from the original to add `builtin_combinator_decl` - see https://t.me/c/1147847827/36700
declaration : (combinator_decl | builtin_combinator_decl | partial_app_decl | final_decl) WS? SEMICOLON WS? ;

/* === Syntactical categories and constructions === */
type_expr : EXCL_MARK? expr ;
nat_expr : expr ;
expr : subexpr (WS subexpr)* ;
subexpr : term | nat_const WS? PLUS WS? subexpr | subexpr WS? PLUS WS? nat_const ;
term
    : OPEN_PAR WS? expr WS? CLOSE_PAR
    | type_ident
    | var_ident
    | nat_const
    | PERCENT term
    | type_ident WS? LANGLE WS? expr (WS? COMMA WS? expr)* WS? RANGLE
    ;
type_ident : boxed_type_ident | lc_ident_ns | HASH ;
boxed_type_ident : uc_ident_ns ;
type_term : EXCL_MARK? term;
nat_term : term;

/* === Combinator declarations === */
combinator_decl : full_combinator_id WS opt_args* args* WS? EQUALS WS? result_type ;
full_combinator_id : lc_ident_full | UNDERSCORE ;
combinator_id : lc_ident_ns | UNDERSCORE ;
opt_args : OPEN_BRACE var_ident (WS var_ident)* WS? COLON type_expr CLOSE_BRACE WS ;
args
    : simple_arg
    | bracket_arg
    | par_arg
    | type_arg
    ;
simple_arg : var_ident_opt COLON conditional_def? type_term WS ;
bracket_arg : (var_ident_opt WS? COLON WS?)? (multiplicity WS? ASTERISK WS?)? OPEN_BRACKET WS? args* WS? CLOSE_BRACKET ;
par_arg : OPEN_PAR var_ident_opt (WS var_ident_opt)* WS? COLON WS? type_term WS ;
type_arg : type_term WS ;
multiplicity : nat_term ;
var_ident_opt : var_ident | UNDERSCORE ;
conditional_def : var_ident (DOT nat_const)? QUESTION_MARK ;
result_type
    : boxed_type_ident (WS subexpr (WS subexpr)*)?
    | boxed_type_ident WS? LANGLE WS? subexpr (WS? COMMA WS? subexpr)* WS? RANGLE
    ;
builtin_combinator_decl : full_combinator_id WS QUESTION_MARK WS? EQUALS WS? boxed_type_ident ;

/* === Partial applications (patterns) === */
partial_app_decl : partial_type_app_decl | partial_comb_app_decl ;
partial_type_app_decl
    : boxed_type_ident WS subexpr (WS subexpr)*
    | boxed_type_ident WS? LANGLE WS? expr (WS? COMMA WS? expr)* WS? RANGLE
    ;
partial_comb_app_decl : combinator_id WS subexpr (WS subexpr)* ;

/* === Type finalization === */
final_decl
    : NEW_KW boxed_type_ident
    | FINAL_KW boxed_type_ident
    | EMPTY_KW boxed_type_ident
    ;

/* +++ Other +++ */
/* === Root program definition === */
tl_file : WS? tl_program WS? EOF ;

/* === Whitespace handling === */
WS : [ \r\n\t]+ ;

/* === Comments === */
LINE_COMMENT : '//' .+? ('\n'|EOF) -> skip ;
MULTILINE_COMMENT : '/*' .+? '*/' -> skip ;
