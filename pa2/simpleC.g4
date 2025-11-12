grammar simpleC;

program
    : (declList)? (funcList)?
    ;

declList
    : (declaration)+
    ;

declaration
    : type identList SEMI
    ;

funcList
    : (function)+
    ;

function
    : type ID LPAREN (paramList)? RPAREN compoundStmt
    ;

identList
    : identifier (',' identifier)*
    ;

identifier
    : ID (ASSGN (INTNUM|FLOATNUM))?
    ;

paramList
    : type identifier (',' type identifier)*
    ;

type
    : 'int'
    | 'float'
    ;

compoundStmt
    : LBRACE (declList)? stmtList RBRACE
    ;

stmtList
    : (stmt)*
    ;

stmt
    : assignStmt
    | callStmt
    | retStmt
    | whileStmt
    | forStmt
    | ifStmt
    | compoundStmt
    | SEMI 
    ;

assignStmt
    : assign SEMI
    ;

assign
    : ID ASSGN expr
    ;

callStmt
    : call SEMI
    ;

call
    : ID LPAREN (argList)? RPAREN
    ;

argList
    : expr (',' expr)*
    ;

retStmt
    : 'return' (expr)? SEMI
    ;

whileStmt
    : 'while' LPAREN expr RPAREN stmt
    ;

forStmt
    : 'for' LPAREN assign SEMI expr SEMI assign RPAREN stmt
    ;

ifStmt
    : 'if' LPAREN expr RPAREN stmt ('else' stmt)?
    ;

expr
    : (PLUS | MINUS)? atom
    | expr (MUL | DIV) expr
    | expr (PLUS | MINUS) expr
    | expr (GT | GTE | LT | LTE) expr
    | expr (EQ |NEQ) expr
    ;

atom
    : ID
    | INTNUM
    | FLOATNUM
    | LPAREN expr RPAREN
    | call
    ;

MUL:   '*' ;
DIV:   '/' ;
PLUS:  '+' ;
MINUS: '-' ;
GT:    '>' ;
GTE:   '>=';
LT:    '<' ;
LTE:   '<=';
EQ:    '==';
NEQ:   '!=';
ASSGN: '=' ;
SEMI:  ';' ;
LBRACE: '{';
RBRACE: '}';
LPAREN: '(';
RPAREN: ')';

ID: [A-Za-z_][A-Za-z0-9_]* ;
INTNUM:   '0' | [1-9][0-9]* ;
FLOATNUM: '0' '.' [0-9]* | [1-9][0-9]* '.' [0-9]* | '.' [0-9]+ ;

WHITESPACE:   [ \t\r\n]+ -> skip;
BLOCKCOMMENT: '/*' .*? '*/' -> skip;
LINECOMMENT:  '//' ~[\r\n]* -> skip;

