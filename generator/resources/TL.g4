grammar TL;

/* +++ Tokens +++ */
/* === Character classes === */
/*
HEX_LETTER_ : [a-f] ;
NON_HEX_LETTER_ : [g-z] ;
UC_LETTER : [A-Z] ;
DIGIT : [0-9] ;
hex_digit : DIGIT | HEX_LETTER_ ;
letter : lc_letter | UC_LETTER ;
ident_char : letter | DIGIT | UNDERSCORE ;
lc_letter : HEX_LETTER_ | NON_HEX_LETTER_ ;
*/


/* === Character classes and Simple identifiers and keywords === */
/*
lc_ident : lc_letter ident_char* ;
uc_ident : UC_LETTER ident_char* ;
*/
NUMBER_ : [0-9]+ ;
HEX_CONST_ : ([a-f0-9] | NUMBER_)+ ;
LC_IDENT_ : [a-z] ([_a-zA-Z] | HEX_CONST_)* ;
UC_IDENT_ : [A-Z] ([_a-zA-Z] | NUMBER_)* ;
namespace_ident : LC_IDENT_ ;
lc_ident_ns : (namespace_ident DOT)* LC_IDENT_ ;
uc_ident_ns : (namespace_ident DOT)* UC_IDENT_ ;
lc_ident_full : lc_ident_ns (HASH (HEX_CONST_ | NUMBER_))? ;
var_ident : LC_IDENT_ | UC_IDENT_ ;

/*
namespace_ident : lc_ident ;
lc_ident_ns : (namespace_ident DOT)* lc_ident ;
uc_ident_ns : (namespace_ident DOT)* uc_ident ;
lc_ident_full : lc_ident_ns (HASH hex_digit (hex_digit (hex_digit (hex_digit (hex_digit (hex_digit (hex_digit hex_digit?)?)?)?)?)?)?)? ;
*/

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
tl_program : constr_declarations ((WS? TYPES WS? fun_declarations) | (WS? FUNCTIONS WS? constr_declarations))* ;
constr_declarations : declaration* ;
fun_declarations : declaration* ;
// Note this is modified from the original to add `builtin_combinator_decl` - see https://t.me/c/1147847827/36700
declaration : (combinator_decl | builtin_combinator_decl | partial_app_decl | final_decl) WS? SEMICOLON WS? ;

/* === Syntactical categories and constructions === */
type_expr : expr ;
nat_expr : expr ;
expr : subexpr (WS subexpr)* ;
subexpr : term | nat_const WS? PLUS WS? subexpr | subexpr WS? PLUS WS? nat_const ;
term : OPEN_PAR WS? expr WS? CLOSE_PAR | type_ident | var_ident | nat_const | PERCENT term | type_ident WS? LANGLE WS? expr (WS? COMMA WS? expr)* WS? RANGLE ;
type_ident : boxed_type_ident | lc_ident_ns | HASH ;
boxed_type_ident : uc_ident_ns ;
type_term : term;
nat_term : term;

/* === Combinator declarations === */
combinator_decl : full_combinator_id WS opt_args* args* WS? EQUALS WS? result_type ;
full_combinator_id : lc_ident_full | UNDERSCORE ;
combinator_id : lc_ident_ns | UNDERSCORE ;
opt_args : OPEN_BRACE var_ident (WS var_ident)* WS? COLON EXCL_MARK? type_expr CLOSE_BRACE WS ;
args
    : var_ident_opt COLON conditional_def? EXCL_MARK? type_term WS
    | (var_ident_opt WS? COLON WS?)? (multiplicity WS? ASTERISK WS?)? OPEN_BRACKET WS? args* WS? CLOSE_BRACKET
    | OPEN_PAR var_ident_opt (WS var_ident_opt)* WS? COLON WS? EXCL_MARK? type_term WS
    | EXCL_MARK? type_term WS
    ;
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
