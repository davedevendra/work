/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * FormulaParser
 */
public class FormulaParser {
    
    //
    // Lexer
    //
    public static class Token {
        public enum Type {
            AND,    // &&
            COLON,  // :
            COMMA,  // ,
            DIV,    // \
            DOLLAR, // $
            EQ,     // =
            FUNCTION, // IDENT '('
            ATTRIBUTE, // '$(' IDENT ')'
            GT,     // >
            GTE,    // >=
            IDENT,  // [_a-zA-Z]+ [_a-zA-Z0-9\-]*
            LPAREN, // (
            LT,     // <
            LTE,    // <=
            MINUS,  // -
            MOD,    // %
            MUL,    // *
            NEQ,    // !=
            NOT,    // !
            NUMBER, // [0-9]+|[0-9]*"."[0-9]+
            OR,     // ||
            PLUS,   // +
            QUESTION_MARK, // ?
            RPAREN, // )
            STRING, // "
            LIKE, // LIKE String.matches()
            WS;     // whitespace is not significant and is consumed
        }

        private final Type type;
        private final int pos;
        private final int length;

        Token(Type type, int pos, int length) {
            this.type = type;
            this.pos = pos;
            this.length = length;
        }

        public Type getType() {
            return type;
        }

        public int getPos() {
            return pos;
        }

        public int getLength() {
            return length;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            Token other = (Token)obj;

            return  this.type   == other.type   &&
                    this.pos    == other.pos    &&
                    this.length == other.length;
        }
    }

    public static List<Token> tokenize(String formula)
            throws IllegalArgumentException {

        final List<Token> tokens = new ArrayList<Token>();

        int pos = 0;
        Token.Type tokenType = null;
        boolean inString = false;

        for (int i = 0; i < formula.length(); ++i) {
            Token.Type type = tokenType;
            int length = i - pos;
            final char ch = formula.charAt(i);

            if (inString && ch != '"') {
                continue;
            }

            switch (ch) {
                case '"':
                    if (inString) {
                        inString = false;
                        continue;
                    } else {
                        type = Token.Type.STRING;
                        inString = true;
                        break;
                    }
                case '(':
                    type = Token.Type.LPAREN;
                    break;
                case ')':
                    type = Token.Type.RPAREN;
                    break;
                case ',':
                    type = Token.Type.COMMA;
                    break;
                case '?':
                    type = Token.Type.QUESTION_MARK;
                    break;
                case ':':
                    type = Token.Type.COLON;
                    break;
                case '+':
                    type = Token.Type.PLUS;
                    break;
                case '-':
                    if (tokenType != Token.Type.IDENT) {
                        type = Token.Type.MINUS;
                    }
                    break;
                case '*':
                    type = Token.Type.MUL;
                    break;
                case '/':
                    type = Token.Type.DIV;
                    break;
                case '%':
                    type = Token.Type.MOD;
                    break;
                case '=': {
                    type = Token.Type.EQ;
                    char peekChar = peek(formula, i + 1);
                    // be forgiving of '=='
                    if (peekChar == '=') {
                        i += 1;
                    }
                    break;
                }
                case '!': {
                    char peekChar = peek(formula, i + 1);
                    if (peekChar == '=') {
                        type = Token.Type.NEQ;
                        i += 1;
                    } else {
                        type = Token.Type.NOT;
                    }
                    break;
                }
                case '>': {
                    char peekChar = peek(formula, i + 1);
                    if (peekChar == '=') {
                        type = Token.Type.GTE;
                        i += 1;
                    } else {
                        type = Token.Type.GT;
                    }
                    break;
                }
                case '<': {
                    char peekChar = peek(formula, i + 1);
                    if (peekChar == '=') {
                        type = Token.Type.LTE;
                        i += 1;
                    } else {
                        type = Token.Type.LT;
                    }
                    break;
                }
                case '|': {
                    char peekChar = peek(formula, i + 1);
                    if (peekChar == '|') {
                        type = Token.Type.OR;
                        i += 1;
                    }
                    break;
                }
                case '&': {
                    char peekChar = peek(formula, i + 1);
                    if (peekChar == '&') {
                        type = Token.Type.AND;
                        i += 1;
                    }
                    break;
                }
                case '$': {
                    char peekChar = peek(formula, i + 1);
                    if (peekChar == '(') {
                        type = Token.Type.ATTRIBUTE;
                        i += 1;
                    } else {
                        type = Token.Type.DOLLAR;
                    }
                    break;
                }
                default:
                    if (Character.isWhitespace(ch)) {
                        type = Token.Type.WS;
                    } else if (tokenType != Token.Type.IDENT) {
                        if (Character.isDigit(ch)) {
                            type = Token.Type.NUMBER;
                        } else if (ch == '.') {
                            // [0-9]+|[0-9]*"."[0-9]+
                            if (tokenType != Token.Type.NUMBER) {
                                char peekChar = peek(formula, i + 1);
                                if (Character.isDigit(peekChar)) {
                                    type = Token.Type.NUMBER;
                                    i += 1;
                                } else {
                                    throw new IllegalArgumentException(
                                            "Found '" + peekChar + "' @ " + i + 1 + ": expected [0-9]"
                                    );
                                }
                            }
                        } else {
                            type = Token.Type.IDENT;
                        }
                    }
                    break;
            }

            // Add previous token when lexer hits a new token.
            if (tokenType != type
                    // it is possible to have two or more LPARENs or RPARENs in a row.
                    || (tokenType == Token.Type.LPAREN && type == Token.Type.LPAREN)
                    || (tokenType == Token.Type.RPAREN && type == Token.Type.RPAREN)
                    ) {
                if (tokenType == Token.Type.IDENT) {
                    final String token =
                            formula.substring(pos, pos+length);
                    if ("AND".equalsIgnoreCase(token)) {
                        tokenType = Token.Type.AND;
                    } else if ("OR".equalsIgnoreCase(token)) {
                        tokenType = Token.Type.OR;
                    } else if ("NOT".equalsIgnoreCase(token)) {
                        tokenType = Token.Type.NOT;
                    } else if ("LIKE".equalsIgnoreCase(token)) {
                        tokenType = Token.Type.LIKE;
                    } else if (type == Token.Type.LPAREN) {
                        tokenType = type = Token.Type.FUNCTION;
                        continue;
                    }
                }

                // tokenType should only be null the first time through
                if (tokenType != null) {
                    if (tokenType != Token.Type.WS) {
                        tokens.add(new Token(tokenType, pos, length));
                    }
                    pos += length;
                }
                // previous token is now current token
                tokenType = type;
            }
        }

        // add the last token
        if (tokenType != Token.Type.WS) {
            tokens.add(new Token(tokenType, pos, formula.length() - pos));
        }

        return tokens;
    }

    static char peek(String str, int offset) {
        return (offset < str.length()) ? str.charAt(offset) : '\0';
    }

    //
    // AST part
    //
    public static class Node {
        enum Operation {
            UNARY_PLUS(6),
            UNARY_MINUS(6),
            PLUS(3),
            MINUS(3),
            MUL(4),
            DIV(4),
            MOD(4),
            AND(1),
            OR(1),
            EQ(2),
            LIKE(2),
            NEQ(2),
            LT(2),
            LTE(2),
            GT(2),
            GTE(2),
            TERNARY(0), // this is for the logical part of ?:, LHS is the logical, RHS is the alternatives
            ALTERNATIVE(0), // this is for the alternatives part of ?:, RHS is true choice, LHS is false choice
            NOT(6), // ! has only LHS, no RHS. LHS is an equality expression or numeric expression
            LOWER(6), // has only LHS, no RHS. LHS is an string expression
            UPPER(6), // has only LHS, no RHS. LHS is an string expression
            FUNCTION(6), // function LHS is function name. args, if any, chain to rhs
            GROUP(-1), // group LHS is the enclosed arithmetic expression
            TERMINAL(-1); // terminal is a number or attribute, LHS is a Terminal, no RHS

            final int prec;

            Operation(int precedence) {
                this.prec = precedence;
            }

            public int getPrec() {
                return prec;
            }
        }

        private final Operation operation;
        private Node leftHandSide;
        private Node rightHandSide;

        Node(Node.Operation operation, Node leftHandSide) {
            this.operation = operation;
            this.leftHandSide = leftHandSide;
        }

        final Operation getOperation() {
            return operation;
        }

        Node getLeftHandSide() {
            return leftHandSide;
        }

        void setLeftHandSide(Node leftHandSide) {
            this.leftHandSide = leftHandSide;
        }

        Node getRightHandSide() {
            return rightHandSide;
        }

        void setRightHandSide(Node rightHandSide) {
            this.rightHandSide = rightHandSide;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || obj.getClass() != this.getClass())  return false;
            Node other = (Node)obj;

            if (this.leftHandSide != null ? !this.leftHandSide.equals(other.leftHandSide) : other.leftHandSide != null) return false;
            return this.rightHandSide != null ? this.rightHandSide.equals(other.rightHandSide) : other.rightHandSide == null;
        }
    }

    // A terminal is either an attribute, number, or no-arg function.
    // It is a Node, but has no Operation, LHS, or RHS
    public static class Terminal extends Node {
        public enum Type {
            IN_PROCESS_ATTRIBUTE,
            CURRENT_ATTRIBUTE,
            NUMBER,
            IDENT,
            STRING
        }

        public final Type type;
        final String value;

        Terminal(Type type, String value) {
            super(Operation.TERMINAL, null);
            this.type = type;
            this.value = value;
        }

        public final String getValue() {
            return value;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || obj.getClass() != this.getClass())  return false;
            Terminal other = (Terminal)obj;

            if (this.type != other.type) return false;

            if (this.value != null ? !this.value.equals(other.value) : other.value != null) return false;
            return true;
        }

    }

    static int comparePrec(Node left, Node right) {
        return left.getOperation().getPrec() - right.getOperation().getPrec();
    }

    // formula
    //    : numericExpression
    //    | ternaryExpression
    //    ;
    //
    // returns the root of the AST
    public static Node parseFormula(List<Token> tokens, String formula)
            throws IllegalArgumentException {

        final Stack<Node> stack = new Stack<Node>();
        int index = -1;

        try {
            index = parseNumericExpression(stack, tokens, formula, 0);
        } catch (IllegalArgumentException e) {
            // drop through = try as conditional expression
        }

        if (index < tokens.size()) {
            stack.clear();
            try {
                index = parseTernaryExpression(stack, tokens, formula, 0);
            } catch (IllegalArgumentException e) {
                // drop through = try as conditional expression
            } catch (IndexOutOfBoundsException e) {
                // drop through = try as conditional expression
            }
        }

        if (index < tokens.size()) {
            stack.clear();
            index = parseRelationalExpression(stack, tokens, formula, 0);
        }

        if (index < tokens.size()) {
            final Token lastToken = tokens.get(index);
            throw new IllegalArgumentException("formula: parser bailed @ " + lastToken.pos);
        }

        return stack.get(0);
    }

    // ternaryExpression
    //     : conditionalOrExpression QUESTION_MARK additiveExpression COLON additiveExpression
    //     ;
    //
    // returns the index of the next token to be processed.
    static int parseTernaryExpression(Stack<Node> stack, List<Token> tokens, String formula, int index) throws IllegalArgumentException {

        if (index >= tokens.size()) return tokens.size();

        index = parseConditionalOrExpression(stack, tokens, formula, index);
        Token token = tokens.get(index);
        if (token.getType() != Token.Type.QUESTION_MARK) {
            throw new IllegalArgumentException(
                    "parseTernaryExpression: found " + String.valueOf(token) + ", expected QUESTION_MARK"
            );
        }

        Node ternary = new Node(Node.Operation.TERNARY, stack.pop());

        index = parseAdditiveExpression(stack, tokens, formula, index+1);
        token = tokens.get(index);
        if (token.getType() != Token.Type.COLON) {
            throw new IllegalArgumentException(
                    "parseTernaryExpression: found " + String.valueOf(token) + ", expected COLON"
            );
        }

        Node alternatives = new Node(Node.Operation.ALTERNATIVE, stack.pop());
        ternary.setRightHandSide(alternatives);

        index = parseAdditiveExpression(stack, tokens, formula, index+1);
        alternatives.setRightHandSide(stack.pop());

        stack.push(ternary);

        return index;
    }

    // conditionalOrExpression
    //     : conditionalAndExpression ( OR conditionalAndExpression )*
    //     ;
    //
    // returns the index of the next token to be processed.
    public static int parseConditionalOrExpression(Stack<Node> stack, List<Token> tokens, String formula, int index) throws IllegalArgumentException {
        if (index >= tokens.size()) return tokens.size();

        index = parseConditionalAndExpression(stack, tokens, formula, index);
        if (index >= tokens.size()) return tokens.size();

        final Token token = tokens.get(index);
        final Node lhs;
        switch (token.getType()) {
            case OR:
                lhs = new Node(Node.Operation.OR, stack.pop());
                index += 1;
                break;
            default:
                return index;
        }

        index = parseConditionalOrExpression(stack, tokens, formula, index);
        stack.push(prioritize(lhs, stack.pop()));
        return index;
    }

    //
    // conditionalAndExpression
    //     : valueLogical ( AND valueLogical )*
    //     ;
    //
    // returns the index of the next token to be processed.
    public static int parseConditionalAndExpression(Stack<Node> stack, List<Token> tokens, String formula, int index) throws IllegalArgumentException {
        if (index >= tokens.size()) return tokens.size();

        index = parseValueLogical(stack, tokens, formula, index);
        if (index >= tokens.size()) return tokens.size();

        final Token token = tokens.get(index);
        final Node lhs;
        switch (token.getType()) {
            case AND:
                lhs = new Node(Node.Operation.AND, stack.pop());
                index += 1;
                break;
            default:
                return index;
        }

        index = parseConditionalAndExpression(stack, tokens, formula, index);
        stack.push(prioritize(lhs, stack.pop()));
        return index;
    }

    //
    // valueLogical
    //     : relationalExpression
    //     ;
    //
    // returns the index of the next token to be processed.
    static int parseValueLogical(Stack<Node> stack, List<Token> tokens, String formula, int index) throws IllegalArgumentException {
        if (index >= tokens.size()) return tokens.size();
        return parseRelationalExpression(stack, tokens, formula, index);
    }

    //
    // relationalExpression
    //     : numericExpression (EQ numericExpression | NEQ numericExpression | LT numericExpression | GT numericExpression | LTE numericExpression | GTE numericExpression )?
    //     ;
    //
    // returns the index of the next token to be processed.
    static int parseRelationalExpression(Stack<Node> stack, List<Token> tokens, String formula, int index) throws IllegalArgumentException {
        if (index >= tokens.size()) return tokens.size();

        index = parseNumericExpression(stack, tokens, formula, index);
        if (index >= tokens.size()) return tokens.size();

        final Token token = tokens.get(index);
        final Node lhs;
        switch (token.getType()) {
            case LIKE:
                lhs = new Node(Node.Operation.LIKE, stack.pop());
                index += 1;
                break;
            case EQ:
                lhs = new Node(Node.Operation.EQ, stack.pop());
                index += 1;
                break;
            case NEQ:
                lhs = new Node(Node.Operation.NEQ, stack.pop());
                index += 1;
                break;
            case LT:
                lhs = new Node(Node.Operation.LT, stack.pop());
                index += 1;
                break;
            case LTE:
                lhs = new Node(Node.Operation.LTE, stack.pop());
                index += 1;
                break;
            case GT:
                lhs = new Node(Node.Operation.GT, stack.pop());
                index += 1;
                break;
            case GTE:
                lhs = new Node(Node.Operation.GTE, stack.pop());
                index += 1;
                break;
            default:
                return index;
        }

        index = parseRelationalExpression(stack, tokens, formula, index);
        stack.push(prioritize(lhs, stack.pop()));
        return index;
    }

    //
    // numericExpression
    //     : additiveExpression
    //     ;
    //
    // returns the index of the next token to be processed.
    static int parseNumericExpression(Stack<Node> stack, List<Token> tokens, String formula, int index) throws IllegalArgumentException {
        if (index >= tokens.size()) return tokens.size();
        return parseAdditiveExpression(stack, tokens, formula, index);
    }

    //
    // additiveExpression
    //     : multiplicativeExpression (PLUS multiplicativeExpression | MINUS multiplicativeExpression )*
    //     ;
    //
    // returns the index of the next token to be processed.
    static int parseAdditiveExpression(Stack<Node> stack, List<Token> tokens, String formula, int index) throws IllegalArgumentException {

        if (index >= tokens.size()) return tokens.size();

        index = parseMultiplicativeExpression(stack, tokens, formula, index);
        if (index >= tokens.size()) return tokens.size();

        final Token token = tokens.get(index);
        final Node lhs;
        switch (token.getType()) {
            case PLUS:
                lhs = new Node(Node.Operation.PLUS, stack.pop());
                index += 1;
                break;
            case MINUS:
                lhs = new Node(Node.Operation.MINUS, stack.pop());
                index += 1;
                break;
            default:
                return index;
        }

        index = parseAdditiveExpression(stack, tokens, formula, index);
        stack.push(prioritize(lhs, stack.pop()));
        return index;
    }

    //
    // multiplicativeExpression
    //     : exponentiationExpression (MUL exponentiationExpression | DIV exponentiationExpression | MOD exponentiationExpression)*
    //     ;
    //
    // returns the index of the next token to be processed.
    static int parseMultiplicativeExpression(Stack<Node> stack, List<Token> tokens, String formula, int index) throws IllegalArgumentException {

        if (index >= tokens.size()) return tokens.size();

        index = parseUnaryExpression(stack, tokens, formula, index);
        if (index >= tokens.size()) return tokens.size();

        final Token token = tokens.get(index);
        final Node lhs;
        switch (token.getType()) {
            case MUL:
                lhs = new Node(Node.Operation.MUL, stack.pop());
                index += 1;
                break;
            case DIV:
                lhs = new Node(Node.Operation.DIV, stack.pop());
                index += 1;
                break;
            case MOD:
                lhs = new Node(Node.Operation.MOD, stack.pop());
                index += 1;
                break;
            default:
                return index;
        }

        index = parseMultiplicativeExpression(stack, tokens, formula, index);
        stack.push(prioritize(lhs, stack.pop()));
        return index;
    }

    //
    // unaryExpression
    //     : NOT primaryExpression
    //     | PLUS primaryExpression
    //     | MINUS primaryExpression
    //     | LOWER primaryExpression
    //     | UPPER primaryExpression
    //     | primaryExpression
    //     ;
    //
    // returns the index of the next token to be processed.
    static int parseUnaryExpression(Stack<Node> stack, List<Token> tokens, String formula, int index) throws IllegalArgumentException {

        if (index >= tokens.size()) return tokens.size();

        final Token token = tokens.get(index);
        switch (token.getType()) {

            case NOT: {
                index = parsePrimaryExpression(stack, tokens, formula, index + 1);
                stack.push(new Node(Node.Operation.NOT, stack.pop()));
                break;
            }
            case PLUS: {
                index = parsePrimaryExpression(stack, tokens, formula, index + 1);
                stack.push(new Node(Node.Operation.UNARY_PLUS, stack.pop()));
                break;
            }
            case MINUS: {
                index = parsePrimaryExpression(stack, tokens, formula, index + 1);
                stack.push(new Node(Node.Operation.UNARY_MINUS, stack.pop()));
                break;
            }
            default: {
                index = parsePrimaryExpression(stack, tokens, formula, index);
                break;
            }
        }

        return index;
    }

    //
    // primaryExpression
    //     : brackettedExpression
    //     | functionElement
    //     | expressionElement
    //     ;
    //
    // returns the index of the next token to be processed.
    static int parsePrimaryExpression(Stack<Node> stack, List<Token> tokens, String formula, int index) throws IllegalArgumentException {

        if (index >= tokens.size()) return tokens.size();

        int newIndex = parseBrackettedExpression(stack, tokens, formula, index);
        if (newIndex == index) {
            newIndex = parseFunctionElement(stack, tokens, formula, index);
            if (newIndex == index) {
                newIndex = parseExpressionElement(stack, tokens, formula, index);
            }
        }

        if (newIndex == index) {
            throw new IllegalArgumentException(
                    "parsePrimaryExpression: expected [brackettedExpression|functionElement|expressionElement]"
            );
        }
        return newIndex;
    }

    //
    // brackettedExpression
    //     : LPAREN conditionalOrExpression RPAREN
    //     ;
    //
    // returns the index of the next token to be processed.
    static int parseBrackettedExpression(Stack<Node> stack, List<Token> tokens, String formula, int index) throws IllegalArgumentException {

        if (index >= tokens.size()) return tokens.size();

        final Token token = tokens.get(index);
        switch (token.getType()) {

            case LPAREN: {
                index = parseConditionalOrExpression(stack, tokens, formula, index + 1);
                Token current = peek(tokens, index);
                if (current.getType() != Token.Type.RPAREN) {
                    throw new IllegalArgumentException(
                            "term: Found " + current.getType() + " @ " + current.getPos() + " expected RPAREN"
                    );
                }
                stack.push(new Node(Node.Operation.GROUP, stack.pop()));
                index += 1; // consume RPAREN
            }
        }

        return index;
    }

    //
    // functionElement
    //     : FUNCTION (args)? RPAREN
    //     ;
    //
    // returns the index of the next token to be processed.
    static int parseFunctionElement(Stack<Node> stack, List<Token> tokens, String formula, int index) throws IllegalArgumentException {

        if (index >= tokens.size()) return tokens.size();

        final Token token = tokens.get(index);
        switch (token.getType()) {

            case FUNCTION: {
                final Token next = peek(tokens, index + 1);
                // token.getLength()-1 to strip off LPAREN
                final String value = formula.substring(token.getPos(), token.getPos() + token.getLength() - 1);
                // FUNCTION operation has function name on LHS, args chaining from RHS to RHS
                final Node function = new Node(
                        Node.Operation.FUNCTION,
                        new Terminal(Terminal.Type.IDENT, value)
                );

                if (next.getType() == Token.Type.RPAREN) {
                    // no-arg function
                } else {
                    // FUNCTION arg [, arg]* )
                    index = parseArgs(stack, tokens, formula, index + 1);
                    function.setRightHandSide(stack.pop());

                    Token current = peek(tokens, index);
                    if (current.getType() != Token.Type.RPAREN) {
                        throw new IllegalArgumentException(
                                "term: Found " + current.getType() + " @ " + current.getPos() + ". Expected RPAREN"
                        );
                    }
                }
                stack.push(function);
                index += 1; // consume RPAREN
                break;
            }

        }

        return index;
    }

    //
    // expressionElement
    //     : IDENT | NUMBER | propertyRef
    //     ;
    //
    // returns the index of the next token to be processed.
    static int parseExpressionElement(Stack<Node> stack, List<Token> tokens, String formula, int index) throws IllegalArgumentException {

        if (index >= tokens.size()) return tokens.size();

        final Token token = tokens.get(index);
        switch (token.getType()) {

            case STRING: {
                // Do not include quotes in the value
                final String value = formula.substring(token.getPos() + 1,
                    token.getPos() + token.getLength() - 1);
                stack.push(new Terminal(Terminal.Type.STRING, value));
                index += 1; // consume STRING
                break;
            }
            case IDENT: {
                final String value = formula.substring(token.getPos(), token.getPos() + token.getLength());
                stack.push(new Terminal(Terminal.Type.IDENT, value));
                index += 1; // consume IDENT
                break;
            }
            case NUMBER: {
                final String value = formula.substring(token.getPos(), token.getPos() + token.getLength());
                stack.push(new Terminal(Terminal.Type.NUMBER, value));
                index += 1; // consume NUMBER
                break;
            }
            default: {
                index = parsePropertyRef(stack, tokens, formula, index);
                break;
            }

        }

        return index;
    }

    //
    // args
    //     : conditionalOrExpression
    //     | conditionalOrExpression COMMA args
    //     ;
    //
    // returns the index of the next token to be processed.
    static int parseArgs(Stack<Node> stack, List<Token> tokens, String formula, int index) throws IllegalArgumentException {

        if (index >= tokens.size()) return tokens.size();

        Node previous = null;
        while (index < tokens.size()) {
            index = parseConditionalOrExpression(stack, tokens, formula, index);
            Node arg = previous == null ? stack.peek() : stack.pop();
            if (previous != null) {
                previous.setRightHandSide(arg);
            }
            previous = arg;
            final Token current = tokens.get(index);
            switch (current.getType()) {
                case COMMA:
                    index += 1;
                    break;
                default:
                    return index;
            }
        }
        return index;

    }

    //
    // propertyRef
    //     : DOLLAR? ATTRIBUTE IDENT RPAREN
    //     ;
    //
    // returns the index of the next token to be processed.
    static int parsePropertyRef(Stack<Node> stack, List<Token> tokens, String formula, int index) throws IllegalArgumentException {
        if (index >= tokens.size()) return tokens.size();

        final Token token = tokens.get(index);
        switch (token.getType()) {

            case ATTRIBUTE:
            case DOLLAR: {

                Token current = token;

                // Handle attribute, which is $? $( IDENT )
                int dollarCount = 0;
                while (current.getType() == Token.Type.DOLLAR) {
                    dollarCount += 1;

                    if (dollarCount > 1) {
                        throw new IllegalArgumentException(
                                "term: " + current.getType() + " @ " + current.getPos() + " not expected"
                        );
                    }

                    index += 1;
                    current = peek(tokens, index);
                }

                final Terminal.Type attrType = Terminal.Type.values()[dollarCount];

                if (current.getType() != Token.Type.ATTRIBUTE) {
                    throw new IllegalArgumentException(
                            "term: Found " + current.getType() + " @ " + current.getPos() + ". Expected ATTRIBUTE"
                    );
                }

                index += 1;
                current = peek(tokens, index);
                if (current.getType() != Token.Type.IDENT) {
                    throw new IllegalArgumentException(
                            "term: Found " + current.getType() + " @ " + current.getPos() + ". Expected IDENT"
                    );
                }

                final String value = formula.substring(current.getPos(), current.getPos() + current.getLength());

                index += 1;
                current = peek(tokens, index);
                if (current.getType() != Token.Type.RPAREN) {
                    throw new IllegalArgumentException(
                            "term: Found " + current.getType() + " @ " + current.getPos() + ". Expected RPAREN"
                    );
                }

                stack.push(new Terminal(attrType, value));
                index += 1; // consume RPAREN
                break;
            }
        }

        return index;
    }
    
    static Token peek(List<Token> tokens, int offset) {
        int index = 0 <= offset && offset <= tokens.size() - 1 ? offset : tokens.size() - 1;
        return tokens.get(index);
    }

    // left hand side needs to have higher precedence than right hand side
    // so that post-fix traversal does higher precedence operations first.
    // The swap on compare == 0 ensures the remaining operations are left-to-right.
    static Node prioritize(Node lhs, Node rhs) {
        if (rhs.getOperation() != Node.Operation.TERMINAL) {
            int c = comparePrec(lhs, rhs);
            if (c == 0) {
                lhs.setRightHandSide(rhs.getLeftHandSide());
                final Node rightHandSide = rhs.getRightHandSide();
                rhs.setLeftHandSide(lhs);
                rhs.setRightHandSide(rightHandSide);
                return rhs;
            } else if (c > 0) {
                final Node leftHandSide = rhs.getLeftHandSide();
                rhs.setLeftHandSide(lhs);
                lhs.setRightHandSide(leftHandSide);
                return lhs;
            } else {
                lhs.setRightHandSide(rhs);
                return lhs;
            }
        } else {
            lhs.setRightHandSide(rhs);
            return lhs;
        }
    }

    public static String dump(Node node) {

        if (node == null) {
            return null;
        }
        if (node instanceof Terminal) {
            String s = ((Terminal) node).getValue();
            if (((Terminal)node).type == Terminal.Type.IN_PROCESS_ATTRIBUTE) {
                s = "$(".concat(s).concat(")");
            } else if (((Terminal)node).type == Terminal.Type.CURRENT_ATTRIBUTE) {
                s = "$$(".concat(s).concat(")");
            }
            return s;
        }

        String lhs = dump(node.getLeftHandSide());
        String rhs = dump(node.getRightHandSide());

        final Node.Operation operation = node.getOperation();
        return "["+operation + "|" + lhs + "|" + rhs + "]";
    }

}
