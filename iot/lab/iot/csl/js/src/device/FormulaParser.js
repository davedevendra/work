/**
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

class FormulaParser {
    // Instance "variables"/properties...see constructor.
    /**
     *
     * @param {FormulaParserNode} left
     * @param {FormulaParserNode} right
     * @return {number}
     */
    static comparePrecedence(left, right) {
        return FormulaParserOperation.getPrecedence(left.getOperation()) -
            FormulaParserOperation.getPrecedence(right.getOperation());
    }

    static dump(node) {
        if (!node) {
            return null;
        }

        if (node instanceof FormulaParserTerminal) {
            let s = node.getValue();

            if (node.type === FormulaParserTerminal.Type.IN_PROCESS_ATTRIBUTE) {
                s = "$(".concat(s).concat(")");
            } else if (node.type === FormulaParserTerminal.Type.CURRENT_ATTRIBUTE) {
                s = "$$(".concat(s).concat(")");
            }

            return s;
        }

        const lhs = FormulaParser.dump(node.getLeftHandSide());
        const rhs = FormulaParser.dump(node.getRightHandSide());

        const operation = node.getOperation();
        return "["+operation + "|" + lhs + "|" + rhs + "]";
    }

    //
    // additiveExpression
    //     : multiplicativeExpression (PLUS multiplicativeExpression | MINUS multiplicativeExpression )*
    //     ;
    //
    // returns the index of the next token to be processed.
    /**
     * @param stack (Stack<Node>)
     * @param tokens (Set<Token>)
     * @param formula (string)
     * @param index (int)
     */
    static parseAdditiveExpression(stack, tokens, formula, index) {
        if (index >= tokens.size) {
            return tokens.size();
        }

        index = FormulaParser.parseMultiplicativeExpression(stack, tokens, formula, index);

        if (index >= tokens.size) {
            return tokens.size;
        }

        const tokensAry = Array.from(tokens);
        const token = tokensAry[index];
        let lhs;

        switch (token.getType()) {
            case FormulaParserToken.Type.PLUS:
                lhs = new FormulaParserNode(FormulaParserOperation.Op.PLUS, stack.pop());
                index += 1;
                break;
            case FormulaParserToken.Type.MINUS:
                lhs = new FormulaParserNode(FormulaParserOperation.Op.MINUS, stack.pop());
                index += 1;
                break;
            default:
                return index;
        }

        index = FormulaParser.parseAdditiveExpression(stack, tokens, formula, index);
        stack.push(FormulaParser.prioritize(lhs, stack.pop()));

        return index;
    }

    //
    // args
    //     : conditionalOrExpression
    //     | conditionalOrExpression COMMA args
    //     ;
    //
    // returns the index of the next token to be processed.
    /**
     * @param stack (Stack<Node>)
     * @param tokens (Set<FormulaParserToken>)
     * @param formula (string)
     * @param index (int)
     */
    static parseArgs(stack, tokens, formula, index)  {
        if (index >= tokens.size) {
            return tokens.size;
        }

        let previous = null;

        while (index < tokens.size) {
            index = FormulaParser.parseConditionalOrExpression(stack, tokens, formula, index);
            let arg = previous === null ? stack.peek() : stack.pop();

            if (previous !== null) {
                previous.setRightHandSide(arg);
            }

            previous = arg;
            const tokensAry = Array.from(tokens);
            const current = tokensAry[index];

            switch (current.getType()) {
                case FormulaParserToken.Type.COMMA:
                    index += 1;
                    break;
                default:
                    return index;
            }
        }

        return index;
    }


//
    // brackettedExpression
    //     : LPAREN conditionalOrExpression RPAREN
    //     ;
    //
    // returns the index of the next token to be processed.
    /**
     * @param stack (Stack<Node>)
     * @param tokens (Set<FormulaParserToken>)
     * @param formula (string)
     * @param index (int)
     */
    static parseBrackettedExpression(stack, tokens, formula, index)  {
        if (index >= tokens.size) {
            return tokens.size;
        }

        const tokensAry = Array.from(tokens);
        const token = tokensAry[index];

        switch (token.getType()) {
            case FormulaParserToken.Type.LPAREN: {
                index = FormulaParser.parseConditionalOrExpression(stack, tokens, formula,
                    index + 1);

                let current = FormulaParser.peekSet(tokens, index);

                if (current.getType() !== FormulaParserToken.Type.RPAREN) {
                    throw new TypeError("term: Found " + current.getType() + " @ " +
                        current.getPos() + " expected RPAREN");
                }

                stack.push(new FormulaParserNode(FormulaParserOperation.Op.GROUP, stack.pop()));
                index += 1; // consume RPAREN
            }
        }

        return index;
    }

    //
    // conditionalAndExpression
    //     : valueLogical ( AND valueLogical )*
    //     ;
    //
    // returns the index of the next token to be processed.
    /**
     * Takes a formula as a string along with the tokens present in the formula
     *
     * @param stack (Stack<Node>)
     * @param tokens (Set<FormulaParserToken>)
     * @param formula (string)
     * @param index (int)
     */
    static parseConditionalAndExpression(stack, tokens, formula, index)  {
        if (index >= tokens.size) {
            return tokens.size;
        }

        index = FormulaParser.parseValueLogical(stack, tokens, formula, index);

        if (index >= tokens.size) {
            return tokens.size;
        }

        const tokensAry = Array.from(tokens);
        const token = tokensAry[index];

        let lhs;

        switch (token.getType()) {
            case FormulaParserToken.Type.AND:
                lhs = new FormulaParserNode(FormulaParserOperation.Op.AND, stack.pop());
                index += 1;
                break;
            default:
                return index;
        }

        index = FormulaParser.parseConditionalAndExpression(stack, tokens, formula, index);
        stack.push(FormulaParser.prioritize(lhs, stack.pop()));

        return index;
    }


    // conditionalOrExpression
    //     : conditionalAndExpression ( OR conditionalAndExpression )*
    //     ;
    //
    // returns the index of the next token to be processed.
    /**
     * @param stack (Stack<Node>)
     * @param tokens (Set<FormulaParserToken>)
     * @param formula (string)
     * @param index (int)
     */
    static parseConditionalOrExpression(stack, tokens, formula, index)  {
        if (index >= tokens.size) {
            return tokens.size;
        }

        index = FormulaParser.parseConditionalAndExpression(stack, tokens, formula, index);

        if (index >= tokens.size) {
            return tokens.size;
        }

        const tokensAry = Array.from(tokens);
        const token = tokensAry[index];
        let lhs;

        switch (token.getType()) {
            case FormulaParserToken.Type.OR:
                lhs = new FormulaParserNode(FormulaParserOperation.Op.OR, stack.pop());
                index += 1;
                break;
            default:
                return index;
        }

        index = FormulaParser.parseConditionalOrExpression(stack, tokens, formula, index);
        stack.push(FormulaParser.prioritize(lhs, stack.pop()));

        return index;
    }

    //
    // expressionElement
    //     : IDENT | NUMBER | propertyRef
    //     ;
    //
    // returns the index of the next token to be processed.
    /**
     * @param stack (Stack<Node>)
     * @param tokens (Set<FormulaParserToken>)
     * @param formula (string)
     * @param index (int)
     */
    static parseExpressionElement(stack, tokens, formula, index)  {
        if (index >= tokens.size) {
            return tokens.size;
        }

        const tokensAry = Array.from(tokens);
        const token = tokensAry[index];

        switch (token.getType()) {
            case FormulaParserTerminal.Type.IDENT: {
                const value = formula.substring(token.getPos(), token.getPos() + token.getLength());
                stack.push(new FormulaParserTerminal(FormulaParserTerminal.Type.IDENT, value));
                index += 1; // consume IDENT
                break;
            }
            case FormulaParserTerminal.Type.NUMBER: {
                const value = formula.substring(token.getPos(), token.getPos() + token.getLength());
                stack.push(new FormulaParserTerminal(FormulaParserTerminal.Type.NUMBER, value));
                index += 1; // consume NUMBER
                break;
            }
            default: {
                index = FormulaParser.parsePropertyRef(stack, tokens, formula, index);
                break;
            }
        }

        return index;
    }

    // formula
    //    : numericExpression
    //    | ternaryExpression
    //    ;
    //
    // returns the root of the AST
    /**
     * @param {Set<FormulaParserToken>} tokens
     * @param {string} formula
     * @return {FormulaParserNode}
     */
    static parseFormula(tokens, formula) {
        // @type {Stack<Node>}
        const stack = new Stack();
        let index = -1;

        try {
            index = FormulaParser.parseNumericExpression(stack, tokens, formula, 0);
        } catch (error) {
            // drop through = try as conditional expression
        }

        if (index < tokens.size) {
            stack.clear();
            index = FormulaParser.parseTernaryExpression(stack, tokens, formula, 0);
        }

        let tokensAry = Array.from(tokens);

        if (index < tokens.size) {
            // @type {FormulaParserToken}
            const lastToken = tokensAry[index];
            throw new Error('Formula: parser bailed @ ' + lastToken.pos);
        }

        return stack.get(0);
    }

    //
    // functionElement
    //     : FUNCTION (args)? RPAREN
    //     ;
    //
    // returns the index of the next token to be processed.
    /**
     * @param stack (Stack<Node>)
     * @param tokens (Set<FormulaParserToken>)
     * @param formula (string)
     * @param index (int)
     */
    static parseFunctionElement(stack, tokens, formula, index)  {
        if (index >= tokens.size) {
            return tokens.size;
        }

        const tokensAry = Array.from(tokens);
        const token = tokensAry[index];

        switch (token.getType()) {
            case FormulaParserToken.Type.FUNCTION: {
                const next = FormulaParser.peekSet(tokens, index + 1);
                // token.getLength()-1 to strip off LPAREN
                const value = formula.substring(token.getPos(), token.getPos() +
                    token.getLength() - 1);

                // FUNCTION operation has function name on LHS, args chaining from RHS to RHS
                const func = new FormulaParserNode(FormulaParserOperation.Op.FUNCTION,
                    new FormulaParserTerminal(FormulaParserTerminal.Type.IDENT, value));

                if (next.getType() === FormulaParserToken.Type.RPAREN) {
                    // no-arg function
                } else {
                    // FUNCTION arg [, arg]* )
                    index = FormulaParser.parseArgs(stack, tokens, formula, index + 1);
                    func.setRightHandSide(stack.pop());
                    let current = FormulaParser.peekSet(tokens, index);

                    if (current.getType() !== FormulaParserToken.Type.RPAREN) {
                        throw new TypeError("term: Found " + current.getType() + " @ " +
                            current.getPos() + ". Expected RPAREN");
                    }

                    index += 1;
                }

                stack.push(func);
                index += 1; // consume RPAREN
                break;
            }
        }

        return index;
    }


    //
    // multiplicativeExpression
    //     : exponentiationExpression (MUL exponentiationExpression | DIV exponentiationExpression |
    // MOD exponentiationExpression)*
    //     ;
    //
    // returns the index of the next token to be processed.
    /**
     * @param stack (Stack<Node>)
     * @param tokens (Set<FormulaParserToken>)
     * @param formula (string)
     * @param index (int)
     */
    static parseMultiplicativeExpression(stack, tokens, formula, index) {
        if (index >= tokens.size) {
            return tokens.size;
        }

        index = FormulaParser.parseUnaryExpression(stack, tokens, formula, index);

        if (index >= tokens.size) {
            return tokens.size;
        }

        const tokensAry = Array.from(tokens);
        const token = tokensAry[index];
        let lhs;

        switch (token.getType()) {
            case FormulaParserToken.Type.MUL:
                lhs = new FormulaParserNode(FormulaParserOperation.Op.MUL, stack.pop());
                index += 1;
                break;
            case FormulaParserToken.Type.DIV:
                lhs = new FormulaParserNode(FormulaParserOperation.Op.DIV, stack.pop());
                index += 1;
                break;
            case FormulaParserToken.Type.MOD:
                lhs = new FormulaParserNode(FormulaParserOperation.Op.MOD, stack.pop());
                index += 1;
                break;
            default:
                return index;
        }

        index = FormulaParser.parseMultiplicativeExpression(stack, tokens, formula, index);
        stack.push(FormulaParser.prioritize(lhs, stack.pop()));

        return index;
    }

    //
    // numericExpression
    //     : additiveExpression
    //     ;
    //
    // returns the index of the next token to be processed.
    /**
     *
     * @param stack (Stack<Node>)
     * @param tokens (Set<FormulaParserToken>)
     * @param formula (string)
     * @param index (int)
     */
    static parseNumericExpression(stack, tokens, formula, index)  {
        if (index >= tokens.size) {
            return tokens.size;
        }

        return FormulaParser.parseAdditiveExpression(stack, tokens, formula, index);
    }

    //
    // primaryExpression
    //     : brackettedExpression
    //     | functionElement
    //     | expressionElement
    //     ;
    //
    // returns the index of the next token to be processed.
    /**
     *
     * @param stack (Stack<Node>)
     * @param tokens (Set<FormulaParserToken>)
     * @param formula (string)
     * @param index (int)
     */
    static parsePrimaryExpression(stack, tokens, formula, index)  {
        if (index >= tokens.size) {
            return tokens.size;
        }

        let newIndex = FormulaParser.parseBrackettedExpression(stack, tokens, formula, index);

        if (newIndex === index) {
            newIndex = FormulaParser.parseFunctionElement(stack, tokens, formula, index);
            if (newIndex === index) {
                newIndex = FormulaParser.parseExpressionElement(stack, tokens, formula, index);
            }
        }

        if (newIndex === index) {
            throw new TypeError(
                "parsePrimaryExpression: expected [brackettedExpression|functionElement|expressionElement]"
            );
        }

        return newIndex;
    }

    //
    // propertyRef
    //     : DOLLAR? ATTRIBUTE IDENT RPAREN
    //     ;
    //
    // returns the index of the next token to be processed.
    /**
     *
     * @param stack (Stack<Node>)
     * @param tokens (Set<FormulaParserToken>)
     * @param formula (string)
     * @param index (int)
     */
    static parsePropertyRef(stack, tokens, formula, index)  {
        if (index >= tokens.size) {
            return tokens.size;
        }

        const tokensAry = Array.from(tokens);
        const token = tokensAry[index];

        switch (token.getType()) {
            case FormulaParserToken.Type.ATTRIBUTE:
            case FormulaParserToken.Type.DOLLAR: {
                let current = token;

                // Handle attribute, which is $? $( IDENT )
                let dollarCount = 0;

                while (current.getType() === FormulaParserToken.Type.DOLLAR) {
                    dollarCount += 1;

                    if (dollarCount > 1) {
                        throw new TypeError("term: " + current.getType() + " @ " +
                            current.getPos() + " not expected");
                    }

                    index += 1;
                    current = FormulaParser.peekSet(tokens, index);
                }

                const attrType = FormulaParserTerminal.getTypeValue(dollarCount);

                if (current.getType() !== FormulaParserToken.Type.ATTRIBUTE) {
                    throw new TypeError("term: Found " + current.getType() + " @ " +
                        current.getPos() + ". Expected ATTRIBUTE");
                }

                index += 1;
                current = FormulaParser.peekSet(tokens, index);

                if (current.getType() !== FormulaParserToken.Type.IDENT) {
                    throw new TypeError("term: Found " + current.getType() + " @ " +
                        current.getPos() + ". Expected IDENT");}

                const value = formula.substring(current.getPos(), current.getPos() +
                    current.getLength());

                index += 1;
                current = FormulaParser.peekSet(tokens, index);

                if (current.getType() !== FormulaParserToken.Type.RPAREN) {
                    throw new TypeError("term: Found " + current.getType() + " @ " +
                        current.getPos() + ". Expected RPAREN");
                }

                stack.push(new FormulaParserTerminal(attrType, value));
                index += 1; // consume RPAREN
                break;
            }
        }

        return index;
    }


    //
    // relationalExpression
    //     : numericExpression (EQ numericExpression | NEQ numericExpression | LT numericExpression | GT numericExpression | LTE numericExpression | GTE numericExpression )?
    //     ;
    //
    // returns the index of the next token to be processed.
    /**
     *
     * @param stack (Stack<Node>)
     * @param tokens (Set<FormulaParserToken>)
     * @param formula (string)
     * @param index (int)
     */
    static parseRelationalExpression(stack, tokens, formula, index)  {
        if (index >= tokens.size) {
            return tokens.size;
        }

        index = FormulaParser.parseNumericExpression(stack, tokens, formula, index);

        if (index >= tokens.size) {
            return tokens.size;
        }

        const tokensAry = Array.from(tokens);
        const token = tokensAry[index];
        let lhs;

        switch (token.getType()) {
            case FormulaParserToken.Type.EQ:
                lhs = new FormulaParserNode(FormulaParserOperation.Op.EQ, stack.pop());
                index += 1;
                break;
            case FormulaParserToken.Type.NEQ:
                lhs = new FormulaParserNode(FormulaParserOperation.Op.NEQ, stack.pop());
                index += 1;
                break;
            case FormulaParserToken.Type.LT:
                lhs = new FormulaParserNode(FormulaParserOperation.Op.LT, stack.pop());
                index += 1;
                break;
            case FormulaParserToken.Type.LTE:
                lhs = new FormulaParserNode(FormulaParserOperation.Op.LTE, stack.pop());
                index += 1;
                break;
            case FormulaParserToken.Type.GT:
                lhs = new FormulaParserNode(FormulaParserOperation.Op.GT, stack.pop());
                index += 1;
                break;
            case FormulaParserToken.Type.GTE:
                lhs = new FormulaParserNode(FormulaParserOperation.Op.GTE, stack.pop());
                index += 1;
                break;
            default:
                return index;
        }

        index = FormulaParser.parseRelationalExpression(stack, tokens, formula, index);
        stack.push(FormulaParser.prioritize(lhs, stack.pop()));

        return index;
    }

    // ternaryExpression
    //     : conditionalOrExpression QUESTION_MARK additiveExpression COLON additiveExpression
    //     ;
    //
    // returns the index of the next token to be processed.
    /**
     *
     * @param stack (Stack<Node>)
     * @param tokens (Set<FormulaParserToken>)
     * @param formula (string)
     * @param index (int)
     */
    static parseTernaryExpression(stack, tokens, formula, index)  {
        if (index >= tokens.size) {
            return tokens.size;
        }

        index = FormulaParser.parseConditionalOrExpression(stack, tokens, formula, index);
        let tokensAry = Array.from(tokens);
        let token = tokensAry[index];

        if (token.getType() !== FormulaParserToken.Type.QUESTION_MARK) {
            throw new TypeError("parseTernaryExpression: found " + token +
                ", expected QUESTION_MARK");
        }

        let ternary = new FormulaParserNode(FormulaParserOperation.Op.TERNARY, stack.pop());
        index = FormulaParser.parseAdditiveExpression(stack, tokens, formula, index + 1);
        tokensAry = Array.from(tokens);
        token = tokensAry[index];

        if (token.getType() !== FormulaParserToken.Type.COLON) {
            throw new TypeError("parseTernaryExpression: found " + token + ", expected COLON");
        }

        let alternatives = new FormulaParserNode(FormulaParserOperation.Op.ALTERNATIVE, stack.pop());
        ternary.setRightHandSide(alternatives);
        index = FormulaParser.parseAdditiveExpression(stack, tokens, formula, index+1);
        alternatives.setRightHandSide(stack.pop());
        stack.push(ternary);

        return index;
    }

    //
    // unaryExpression
    //     : NOT primaryExpression
    //     | PLUS primaryExpression
    //     | MINUS primaryExpression
    //     | primaryExpression
    //     ;
    //
    // returns the index of the next token to be processed.
    /**
     *
     * @param stack (Stack<Node>)
     * @param tokens (Set<FormulaParserToken>)
     * @param formula (string)
     * @param index (int)
     */
    static parseUnaryExpression(stack, tokens, formula, index)  {
        if (index >= tokens.size) {
            return tokens.size;
        }

        const tokensAry = Array.from(tokens);
        const token = tokensAry[index];

        switch (token.getType()) {
            case FormulaParserToken.Type.NOT: {
                index = FormulaParser.parsePrimaryExpression(stack, tokens, formula, index + 1);
                stack.push(new FormulaParserNode(FormulaParserOperation.Op.NOT, stack.pop()));
                break;
            }
            case FormulaParserToken.Type.PLUS: {
                index = FormulaParser.parsePrimaryExpression(stack, tokens, formula, index + 1);
                stack.push(new FormulaParserNode(FormulaParserOperation.Op.UNARY_PLUS, stack.pop()));
                break;
            }
            case FormulaParserToken.Type.MINUS: {
                index = FormulaParser.parsePrimaryExpression(stack, tokens, formula, index + 1);
                stack.push(new FormulaParserNode(FormulaParserOperation.Op.UNARY_MINUS, stack.pop()));
                break;
            }
            default: {
                index = FormulaParser.parsePrimaryExpression(stack, tokens, formula, index);
                break;
            }
        }

        return index;
    }

    //
    // valueLogical
    //     : relationalExpression
    //     ;
    //
    // returns the index of the next token to be processed.
    /**
     *
     * @param stack (Stack<Node>)
     * @param tokens (Set<FormulaParserToken>)
     * @param formula (string)
     * @param index (int)
     */
    static parseValueLogical(stack, tokens, formula, index)  {
        if (index >= tokens.size) {
            return tokens.size;
        }

        return FormulaParser.parseRelationalExpression(stack, tokens, formula, index);
    }

    /**
     *
     * @param tokens Set<FormulaParserToken>
     * @param offset int
     */
    static peekSet(tokens, offset) {
        let index = 0 <= offset && offset <= tokens.size - 1 ? offset : tokens.size - 1;
        const tokensAry = Array.from(tokens);
        return tokensAry[index];
    }

    /**
     *
     * @param {string} str
     * @param {number} offset
     * @return {string}
     */
    static peekString(str, offset) {
        return (offset < str.length) ? str.charAt(offset) : '\0';
    }

    // left hand side needs to have higher precedence than right hand side
    // so that post-fix traversal does higher precedence operations first.
    // The swap on compare == 0 ensures the remaining operations are left-to-right.
    /**
     * @param lhs (Node)
     * @param rhs (Node)
     */
    static prioritize(lhs, rhs) {
        if (rhs.getOperation() !== FormulaParserOperation.Op.TERMINAL) {
            let c = FormulaParser.comparePrecedence(lhs, rhs);

            if (c === 0) {
                lhs.setRightHandSide(rhs.getLeftHandSide());
                const rightHandSide = rhs.getRightHandSide();
                rhs.setLeftHandSide(lhs);
                rhs.setRightHandSide(rightHandSide);
                return rhs;
            } else if (c > 0) {
                const leftHandSide = rhs.getLeftHandSide();
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

    /**
     * Takes a formula as a string and returns the Set of tokens in the formula.
     *
     * @param {string} formula
     * @return {Set<FormulaParserToken>}
     */
    static tokenize(formula) {
        const tokens = new Set();
        let pos = 0;
        let tokenType = null;

        for (let i = 0; i < formula.length; ++i) {
            let type = tokenType;
            let length = i - pos;
            const ch = formula.charAt(i);

            switch (ch) {
                case '(':
                    type = FormulaParserToken.Type.LPAREN;
                    break;
                case ')':
                    type = FormulaParserToken.Type.RPAREN;
                    break;
                case ',':
                    type = FormulaParserToken.Type.COMMA;
                    break;
                case '?':
                    type = FormulaParserToken.Type.QUESTION_MARK;
                    break;
                case ':':
                    type = FormulaParserToken.Type.COLON;
                    break;
                case '+':
                    type = FormulaParserToken.Type.PLUS;
                    break;
                case '-':
                    if (tokenType !== FormulaParserToken.Type.IDENT) {
                        type = FormulaParserToken.Type.MINUS;
                    }

                    break;
                case '*':
                    type = FormulaParserToken.Type.MUL;
                    break;
                case '/':
                    type = FormulaParserToken.Type.DIV;
                    break;
                case '%':
                    type = FormulaParserToken.Type.MOD;
                    break;
                case '=': {
                    type = FormulaParserToken.Type.EQ;
                    let peekChar = FormulaParser.peekString(formula, i + 1);

                    // Be forgiving of '=='.
                    if (peekChar === '=') {
                        i += 1;
                    }

                    break;
                }
                case '!': {
                    let peekChar = FormulaParser.peekString(formula, i + 1);

                    if (peekChar === '=') {
                        type = FormulaParserToken.Type.NEQ;
                        i += 1;
                    } else {
                        type = FormulaParserToken.Type.NOT;
                    }

                    break;
                }
                case '>': {
                    let peekChar = FormulaParser.peekString(formula, i + 1);

                    if (peekChar === '=') {
                        type = FormulaParserToken.Type.GTE;
                        i += 1;
                    } else {
                        type = FormulaParserToken.Type.GT;
                    }

                    break;
                }
                case '<': {
                    let peekChar = FormulaParser.peekString(formula, i + 1);

                    if (peekChar === '=') {
                        type = FormulaParserToken.Type.LTE;
                        i += 1;
                    } else {
                        type = FormulaParserToken.Type.LT;
                    }

                    break;
                }
                case '|': {
                    let peekChar = FormulaParser.peekString(formula, i + 1);

                    if (peekChar === '|') {
                        type = FormulaParserToken.Type.OR;
                        i += 1;
                    }

                    break;
                }
                case '&': {
                    let peekChar = FormulaParser.peekString(formula, i + 1);

                    if (peekChar === '&') {
                        type = FormulaParserToken.Type.AND;
                        i += 1;
                    }

                    break;
                }
                // The $ case needs to be in double quotes otherwise the build will fail.
                case "$": {
                    let peekChar = FormulaParser.peekString(formula, i + 1);

                    if (peekChar === '(') {
                        type = FormulaParserToken.Type.ATTRIBUTE;
                        i += 1;
                    } else {
                        type = FormulaParserToken.Type.DOLLAR;
                    }

                    break;
                }
               default:
                    if (ch === ' ') {
                        type = FormulaParserToken.Type.WS;
                    } else if (tokenType !== FormulaParserToken.Type.IDENT) {
                        if (Number.isInteger(parseInt(ch))) {
                            type = FormulaParserToken.Type.NUMBER;
                        } else if (ch === '.') {
                            // [0-9]+|[0-9]*"."[0-9]+
                            if (tokenType !== FormulaParserToken.Type.NUMBER) {
                                let peekChar = FormulaParser.peekString(formula, i + 1);

                                if (Number.isInteger(parseInt(peekChar))) {
                                    type = FormulaParserToken.Type.NUMBER;
                                    i += 1;
                                } else {
                                    throw new TypeError("Found '" + peekChar + "' @ " + i + 1 +
                                        ": expected [0-9]");
                                }
                            }
                        } else {
                            type = FormulaParserToken.Type.IDENT;
                        }
                    }

                   break;
            }

            // Add previous token when lexer hits a new token.
            if (tokenType !== type) {
                if (tokenType === FormulaParserToken.Type.IDENT) {
                    const token = formula.substring(pos, pos+length);

                    if ("AND" === token.toUpperCase()) {
                        tokenType = FormulaParserToken.Type.AND;
                    } else if ("OR" === token.toUpperCase()) {
                        tokenType = FormulaParserToken.Type.OR;
                    } else if ("NOT" === token.toUpperCase()) {
                        tokenType = FormulaParserToken.Type.NOT;
                    } else if (type === FormulaParserToken.Type.LPAREN) {
                        tokenType = type = FormulaParserToken.Type.FUNCTION;
                        continue;
                    }
                }

                // tokenType should only be null the first time through
                if (tokenType) {
                    if (tokenType !== FormulaParserToken.Type.WS) {
                        tokens.add(new FormulaParserToken(tokenType, pos, length));
                    }

                    pos += length;
                }

                // Previous token is now current token.
                tokenType = type;
            }
        }

        // Add the last token.
        if (tokenType !== FormulaParserToken.Type.WS) {
            tokens.add(new FormulaParserToken(tokenType, pos, formula.length - pos));
        }

        return tokens;
    }

    constructor(height, width) {
        // Instance "variables"/properties.
        this.height = height;
        this.width = width;
        // Instance "variables"/properties.
    }
}

class FormulaParserNode {
    // Instance "variables" & properties...see constructor.

    /**
     *
     * @param {number} operation
     * @param {FormulaParserNode} leftHandSide
     */
    constructor(operation, leftHandSide) {
        // Instance "variables" & properties.
        this.operation = operation;
        this.leftHandSide = leftHandSide;
        this.rightHandSide = null;
        this.type = 'node';
        Object.freeze(this.type);
        // Instance "variables" & properties.
    }

    /**
     * @param {object} obj
     * @return {boolean} {@code true} if they are equal.
     */
    equals(obj) {
        if (this === obj) {
            return true;
        }

        if (obj === null || typeof obj !== typeof this)  {
            return false;
        }

        let lhsEquals = this.leftHandSide === obj.leftHandSide;

        if (this.leftHandSide !== null ? !lhsEquals : obj.leftHandSide !== null)
        {
            return false;
        }

        return this.rightHandSide !== null ? this.rightHandSide === obj.rightHandSide :
            obj.rightHandSide === null;
    }

    /**
     * @return {FormulaParserNode}
     */
    getLeftHandSide() {
        return this.leftHandSide;
    }

    /**
     *
     * @return {FormulaParserOperation}
     */
    getOperation() {
        return this.operation;
    }

    /**
     *
     * @return {FormulaParserNode}
     */
    getRightHandSide() {
        return this.rightHandSide;
    }

    /**
     *
     * @param {FormulaParserNode} leftHandSide
     */
    setLeftHandSide(leftHandSide) {
        this.leftHandSide = leftHandSide;
    }

    /**
     *
     * @param {FormulaParserNode} rightHandSide
     */
    setRightHandSide(rightHandSide) {
        this.rightHandSide = rightHandSide;
    }
}

class FormulaParserOperation {
    // Instance "variables" & properties...see constructor.

    /**
     *
     * @param {string} operation
     * @return {number} the precedence of this operation.
     */
    static getPrecedence(operation) {
        switch(operation) {
            case FormulaParserOperation.Op.GROUP:
            case FormulaParserOperation.Op.TERMINAL:
                return -1;
            case FormulaParserOperation.Op.ALTERNATIVE:
            case FormulaParserOperation.Op.TERNARY:
                return 0;
            case FormulaParserOperation.Op.AND:
            case FormulaParserOperation.Op.OR:
                return 1;
            case FormulaParserOperation.Op.EQ:
            case FormulaParserOperation.Op.GT:
            case FormulaParserOperation.Op.GTE:
            case FormulaParserOperation.Op.LT:
            case FormulaParserOperation.Op.LTE:
            case FormulaParserOperation.Op.NEQ:
                return 2;
            case FormulaParserOperation.Op.MINUS:
            case FormulaParserOperation.Op.PLUS:
                return 3;
            case FormulaParserOperation.Op.DIV:
            case FormulaParserOperation.Op.MOD:
            case FormulaParserOperation.Op.MUL:
                return 4;
            case FormulaParserOperation.Op.FUNCTION:
            case FormulaParserOperation.Op.NOT:
            case FormulaParserOperation.Op.UNARY_MINUS:
            case FormulaParserOperation.Op.UNARY_PLUS:
                return 6;
        }
    }
}

FormulaParserOperation.Op = {
    // This is for the alternatives part of ?:, RHS is true choice, LHS is false choice.
    ALTERNATIVE: 'ALTERNATIVE',
    AND: 'AND',
    DIV: 'DIV',
    EQ: 'EQ',
    FUNCTION: 'FUNCTION', // function LHS is function name. args, if any, chain to rhs
    GROUP: 'GROUP', // group LHS is the enclosed arithmetic expression
    GT: 'GT',
    GTE: 'GTE',
    LT: 'LT',
    LTE: 'LTE',
    MINUS: 'MINUS',
    MOD: 'MOD',
    MUL: 'MUL',
    NEQ: 'NEQ',
    NOT: 'NOT', // ! has only LHS, no RHS. LHS is an equality expression or numeric expression
    OR: 'OR',
    PLUS: 'PLUS',
    TERMINAL: 'TERMINAL', // terminal is a number or attribute, LHS is a Terminal, no RHS
    TERNARY: 'TERNARY', // this is for the logical part of ?:, LHS is the logical, RHS is the alternatives
    UNARY_MINUS: 'UNARY_MINUS',
    UNARY_PLUS: 'UNARY_PLUS'
};

class FormulaParserTerminal extends FormulaParserNode {
    // Instance "variables" & properties...see constructor.

    /**
     *
     * @param {number} num
     * @return {string} the FormulaParserTerminal.Type
     */
    static getTypeValue(num) {
        switch(num) {
            case 0:
                return FormulaParserTerminal.Type.IN_PROCESS_ATTRIBUTE;
            case 1:
                return FormulaParserTerminal.Type.CURRENT_ATTRIBUTE;
            case 2:
                return FormulaParserTerminal.Type.NUMBER;
            case 3:
                return FormulaParserTerminal.Type.IDENT;
            default:
        }
    }

    /**
     *
     * @param {string} type
     * @param {string} value
     */
    constructor(type, value) {
        super(FormulaParserOperation.Op.TERMINAL, null);
        // Instance "variables" & properties.
        this.type = type;
        Object.freeze(this.type);
        this.value = value;
        // Instance "variables" & properties.
    }

    /**
     * @param {object} obj
     * @return {boolean} {@code true} if the objects are equal.
     */
    equals(obj) {
        if (this === obj) {
            return true;
        }

        if (!obj || typeof obj !== typeof this) {
            return false;
        }

        if (this.type !== obj.type) {
            return false;
        }

        return !(!this.value ? this.value !== obj.value : obj.value);
    }

    /**
     * @return {string}
     */
    getValue() {
        return this.value;
    }
}

FormulaParserTerminal.Type = {
    TYPE: 'TERMINAL',
    IN_PROCESS_ATTRIBUTE: 'IN_PROCESS_ATTRIBUTE',
    CURRENT_ATTRIBUTE: 'CURRENT_ATTRIBUTE',
    NUMBER: 'NUMBER',
    IDENT: 'IDENT',
};

class FormulaParserToken {
    // Instance "variables" & properties...see constructor.

    /**
     *
     * @param {FormulaParserToken.Type} type
     * @param {number} pos
     * @param {number} length
     */
    constructor(type, pos, length) {
        // Instance "variables" & properties.
        this.type = type;
        Object.freeze(this.type);
        this.pos = pos;
        this.length = length;
        // Instance "variables" & properties.
    }

    /**
     * @return {FormulaParserToken.Type}
     */
    getType() {
        return this.type;
    }

    /**
     * @return {number}
     */
    getPos() {
        return this.pos;
    }

    /**
     * @return {number}
     */
    getLength() {
        return this.length;
    }

    /**
     * @param {object} obj
     * @return {boolean}
     */
    equals(obj) {
        if (this === obj) {
            return true;
        }

        if (!obj || typeof obj !== typeof this) {
            return false;
        }

        return this.type === obj.type && this.pos === obj.pos && this.length === obj.length;
    }
}

// Token types
FormulaParserToken.Type = {
    AND: 'AND',    // &&
    COLON: 'COLON',  // :
    COMMA: 'COMMA',  // ,
    DIV: 'DIV',    // \
    DOLLAR: 'DOLLAR', // $
    EQ: 'EQ',     // =
    FUNCTION: 'FUNCTION', // IDENT '('
    ATTRIBUTE: 'ATTRIBUTE', // '$(' IDENT ')'
    GT: 'GT',     // >
    GTE: 'GTE',    // >=
    IDENT: 'IDENT',  // [_a-zA-Z]+ [_a-zA-Z0-9\-]*
    LPAREN: 'LPARN', // (
    LT: 'LT',     // <
    LTE: 'LTE',    // <=
    MINUS: 'MINUS',  // -
    MOD: 'MOD',    // %
    MUL: 'MUL',    // *
    NEQ: 'NEQ',    // !=
    NOT: 'NOT',    // !
    NUMBER: 'NUMBER', // [0-9]+|[0-9]*"."[0-9]+
    OR: 'OR',     // ||
    PLUS: 'PLUS',   // +
    QUESTION_MARK: 'QUESTION_MARK',
    RPAREN: 'RPAREN', // )
    WS: 'WS'     // whitespace is not significant and is consumed
};
