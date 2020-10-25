grammar Mzi;

program : stmt* EOF;

// statements
stmt : decl           # stmtDecl
     | cmd            # stmtCmd
     ;

cmd : cmdName moduleName using? hiding?;

cmdName : '\\open'         # cmdOpen
        | '\\import'       # cmdImport
        ;

using : '\\using'? '(' id_list ')';
hiding : '\\hiding' '(' id_list ')';

moduleName : ID ('.' ID)*;

// declarations

decl : fnDecl
     | structDecl
     | dataDecl
     ;

assoc : '\\infix'               # nonAssocInfix
      | '\\infixl'              # leftAssocInfix
      | '\\infixr'              # rightAssocInfix
      | '\\fix'                 # nonAssoc
      | '\\fixl'                # leftAssoc
      | '\\fixr'                # rightAssoc
      ;

where : '\\where' ('{' stmt* '}' | stmt);

fnDecl : '\\def' fnModifiers* assoc? ID tele* type? fnBody where?;

fnBody : rightEqArrow expr;

fnModifiers : '\\erased'                # fnErased
            ;

structDecl : '\\structure' ID fieldTele* ('\\extends' id_list)? ('|' structFieldOrImpl)* where?;

fieldTele : '(' '\\coerce'? ID+ type')'        # explicitFieldTele
          | '{' '\\coerce'? ID+ type '}'        # implicitFieldTele
          ;

structFieldOrImpl : '\\coerce'? ID tele* type        # classField
                  | ID tele* rightEqArrow expr       # classImpl
                  ;

dataDecl : '\\data' ID tele* type? dataBody where?;

dataBody : ('|' ctor)*                               # dataCtors
         | elim ctorClause*                          # dataClauses
         ;

// TODO[imkiva]: some code commented in Arend.g4
ctor : '\\coerce'? ID tele* (elim? '{' clause? ('|' clause)* '}')?;

elim : '\\elim' ID (',' ID)*;

ctorClause : '|' pattern rightEqArrow ctor;

// expressions

sigmaKw : '\\Sigma'
        | 'Σ'
        ;

lambdaKw : '\\lam'
         | 'λ'
         ;

piKw : '\\Pi'
     | 'Π'
     ;

matchKw : '\\matchy'
        | '\\match'
        ;

expr : appExpr                                                # app
     | <assoc=right> expr rightArrow expr                     # arr
     | <assoc=right> expr '.' NUMBER                          # proj
     | piKw tele+ rightArrow expr                             # pi
     | sigmaKw tele*                                          # sigma
     | lambdaKw tele+ (rightEqArrow expr?)?                   # lam
     | matchKw matchArg (',' matchArg)* ( '|' clause)*        # match
     ;

matchArg : elim          # matchElim
         | expr          # matchExpr
         ;

appExpr : atom argument*      # appArg
        | UNIVERSE            # appUniverse
        | SET                 # appSetUniverse
        ;

tupleExpr : expr type? ;

atom : literal                                     # atomLiteral
     | '(' (tupleExpr (',' tupleExpr)* ','?)? ')'  # tuple
     | NUMBER                                      # atomNumber
     | STRING                                      # atomString
     ;

argument : expr                                     # argumentExplicit
         | universeAtom                             # argumentUniverse
         | '{' tupleExpr (',' tupleExpr)* ','? '}'  # argumentImplicit
         ;

clause : pattern (',' pattern)* rightEqArrow expr;

pattern : atomPattern ('\\as' ID type?)?          # patAtom
        | ID atomPatternOrID* ('\\as' ID)? type?  # patCtor
        ;

atomPattern : '(' (pattern (',' pattern)*)? ')'   # atomPatExplicit
            | '{' pattern '}'                     # atomPatImplicit
            | NUMBER                              # atomPatNumbers
            | '_'                                 # atomPatWildcard
            ;

atomPatternOrID : atomPattern     # patternOrIDAtom
                | ID              # patternID
                ;

literal : ID ('.' (INFIX | POSTFIX))?       # name
        | '\\Prop'                          # prop
        | '_'                               # unknown
        | INFIX                             # infix
        | POSTFIX                           # postfix
        | '{?' ID? ('(' expr? ')')? '}'     # goal
        ;

universeAtom : TRUNCATED_UNIVERSE       # uniTruncatedUniverse
             | UNIVERSE                 # uniUniverse
             | SET                      # uniSetUniverse
             ;

tele : literal                          # teleLiteral
     | universeAtom                     # teleUniverse
     | '(' typedExpr ')'                # explicit
     | '{' typedExpr '}'                # implicit
     ;

typedExpr : expr type? ;

// utilities
id_list : (ID ',')* ID?;
rightArrow : '->' | '→';
rightEqArrow : '=>' | '⇒';
type : ':' expr;

// operators
INFIX : '`' ID '`';
POSTFIX : '`' ID;

// universe
UNIVERSE : '\\Type' [0-9]*;
TRUNCATED_UNIVERSE : '\\' (NUMBER '-' | 'oo-' | 'h') 'Type' [0-9]*;
SET : '\\Set' [0-9]*;

// numbers
NUMBER : [0-9]+;

// string
STRING : INCOMPLETE_STRING '"';
INCOMPLETE_STRING : '"' (~["\\\r\n] | ESCAPE_SEQ | EOF)*;
fragment ESCAPE_SEQ : '\\' [btnfr"'\\] | OCT_ESCAPE | UNICODE_ESCAPE;
fragment OCT_ESCAPE : '\\' OCT_DIGIT OCT_DIGIT? | '\\' [0-3] OCT_DIGIT OCT_DIGIT;
fragment UNICODE_ESCAPE : '\\' 'u'+ HEX_DIGIT HEX_DIGIT HEX_DIGIT HEX_DIGIT;
fragment HEX_DIGIT : [0-9a-fA-F];
fragment OCT_DIGIT : [0-8];

// identifier
fragment START_CHAR : [~!@#$%^&*\-+=<>?/|:[\u005Da-zA-Z_\u2200-\u22FF];
ID : START_CHAR (START_CHAR | [0-9'])*;

// whitespaces
WS : [ \t\r\n]+ -> channel(HIDDEN);
LINE_COMMENT : '--' '-'* (~[~!@#$%^&*\-+=<>?/|:[\u005Da-zA-Z_0-9'\u2200-\u22FF\r\n] ~[\r\n]* | ) -> skip;
COMMENT : '{-' (COMMENT|.)*? '-}' -> channel(HIDDEN);
