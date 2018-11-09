/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.shared;

import static com.oracle.iot.shared.FormulaParser.tokenize;

import com.oracle.iot.shared.FormulaParser.Node.Operation;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Formula {
    FormulaParser.Node tree;

    public Formula(String formula) {
        List<FormulaParser.Token> tokens = tokenize(formula);
        tree = FormulaParser.parseFormula(tokens, formula);
    }
    
    public Object compute(ValueProvider vp) {
        return compute(tree, vp);
    }
    
    public String dump() {
        return FormulaParser.dump(tree);
    }

    public static Object compute(FormulaParser.Node node, ValueProvider vp) {

        if (node == null) {
            return Double.NaN;
        }

        if (node instanceof FormulaParser.Terminal) {
            FormulaParser.Terminal terminal = (FormulaParser.Terminal) node;
            final String attr = ((FormulaParser.Terminal) node).getValue();
            switch (terminal.type) {
                case CURRENT_ATTRIBUTE:
                    try {
                        final Object value = vp.getCurrentValue(attr);
                        if (value instanceof Number) {
                            return ((Number)value).doubleValue();
                        } else if (value instanceof Boolean) {
                            return ((Boolean)value).booleanValue() ? 1d : 0d;
                        } else if (value instanceof String) {
                            return value;
                        }
                    } catch (ClassCastException e) {
                        getLogger().log(Level.WARNING, e.getMessage());
                    }
                    break;
                case IN_PROCESS_ATTRIBUTE:
                    try {
                        Object value = vp.getInProcessValue(attr);
                        if (value != null ||
                                (value = vp.getCurrentValue(attr)) != null) {
                            if (value instanceof Number) {
                                return ((Number)value).doubleValue();
                            } else if (value instanceof Boolean) {
                                return ((Boolean)value).booleanValue() ? 1d : 0d;
                            } else if (value instanceof String) {
                                return value;
                            }
                        }
                    } catch (ClassCastException e) {
                        getLogger().log(Level.WARNING, e.getMessage());
                    }
                    break;
                case NUMBER:
                    try {
                        final Double value = Double.valueOf(attr);
                        return value;
                    } catch (NumberFormatException e) {
                        getLogger().log(Level.WARNING, e.getMessage());
                    }
                case STRING:
                case IDENT:
                    return attr;
            }
            return Double.NaN;
        }

        if (node.getOperation() == FormulaParser.Node.Operation.TERNARY) {
            Double cond = (Double)compute(node.getLeftHandSide(), vp);
            if (cond.compareTo(1.0) == 0) {
                return compute(node.getRightHandSide().getLeftHandSide(), vp);
            } else {
                return compute(node.getRightHandSide().getRightHandSide(), vp);
            }
        } else if (node.getOperation() == FormulaParser.Node.Operation.GROUP) {
            return compute(node.getLeftHandSide(), vp);
        }

        final FormulaParser.Node.Operation operation = node.getOperation();

        Comparable lhsComp =
            (Comparable)compute(node.getLeftHandSide(), vp);
        Comparable rhsComp =
            (Comparable)compute(node.getRightHandSide(), vp);

        switch (operation) {
            case FUNCTION: {
                final String fn = ((FormulaParser.Terminal)node.getLeftHandSide()).getValue();
                if ("LOWER".equalsIgnoreCase(fn)) {
                    final String str = String.class.cast(rhsComp);
                    return str != null ? str.toLowerCase(Locale.ROOT) : "";
                } else if ("UPPER".equalsIgnoreCase(fn)) {
                    final String str = String.class.cast(rhsComp);
                    return str != null ? str.toUpperCase(Locale.ROOT) : "";
                } else {
                    getLogger().log(Level.WARNING, "unknown function '" + fn + "'");
                }
            }
            case LOWER:
                return ((String)lhsComp).toLowerCase(Locale.ROOT);
            case UPPER:
                return ((String)lhsComp).toUpperCase(Locale.ROOT);
            case EQ:
                if (lhsComp.getClass() == rhsComp.getClass()) {
                    return lhsComp.compareTo(rhsComp) == 0 ? 1.0 : 0.0;
                } else {
                    return 0.0;
                }
            case NEQ:
                if (lhsComp.getClass() == rhsComp.getClass()) {
                    return lhsComp.compareTo(rhsComp) == 0 ? 0.0 : 1.0;
                } else {
                    return 1.0;
                }
            case PLUS:
                if (lhsComp instanceof String && rhsComp instanceof String) {
                    String lhsString = (String)lhsComp;
                    String rhsString = (String)rhsComp;
                    return lhsString.concat(rhsString);
                }
                break;
            case LIKE:
                if (lhsComp instanceof String && rhsComp instanceof String) {
                    String lhsString = (String)lhsComp;
                    String rhsString = (String)rhsComp;
                    return sqlMatches(lhsString, rhsString) ? 1.0 : 0.0;
                } else {
                    return 0.0;
                }
        }

        Double lhs = (Double)lhsComp;
        Double rhs = (Double)rhsComp;

        switch (operation) {
            case UNARY_MINUS:
                return -lhs;
            case UNARY_PLUS:
                return +lhs;
            case DIV:
                return lhs / rhs;
            case MUL:
                return lhs * rhs;
            case PLUS:
                return lhs+ rhs;
            case MINUS:
                return lhs - rhs;
            case MOD:
                return lhs % rhs;
            case OR:
                // Let NaN or NaN be false
                if (lhs.isNaN()) return rhs.isNaN() ? 0.0 : 1.0;
                return lhs.compareTo(0.0) != 0 || rhs.compareTo(0.0) != 0 ? 1.0 : 0.0;
            case AND:
                // If lhs or rhs is NaN, return false
                if (lhs.isNaN() || rhs.isNaN()) return 0.0;
                return lhs.compareTo(0.0) != 0 && rhs.compareTo(0.0) != 0 ? 1.0 : 0.0;
//            case EQ:
//                // NaN.compareTo(42) == 1, 42.compareTo(NaN) == -1
//                return lhs.compareTo(rhs) == 0 ? 1.0 : 0.0;
//            case NEQ:
//                return lhs.compareTo(rhs) == 0 ? 0.0 : 1.0;
            case GT:
                // NaN.compareTo(42) == 1, 42.compareTo(NaN) == -1
                // Let NaN > 42 return false, and 42 > NaN return true
                if (lhs.isNaN()) return 0.0;
                if (rhs.isNaN()) return 1.0;
                return lhs.compareTo(rhs) > 0 ? 1.0 : 0.0;
            case GTE:
                // NaN.compareTo(42) == 1, 42.compareTo(NaN) == -1
                // Let NaN >= 42 return false, and 42 >= NaN return true
                if (lhs.isNaN()) return rhs.isNaN() ? 1.0 : 0.0;
                if (rhs.isNaN()) return 1.0;
                return lhs.compareTo(rhs) >= 0 ? 1.0 : 0.0;
            case LT:
                // NaN.compareTo(42) == 1, 42.compareTo(NaN) == -1
                // Let NaN < 42 return false, and 42 < NaN return true
                if (lhs.isNaN()) return 0.0;
                if (rhs.isNaN()) return 1.0;
                return lhs.compareTo(rhs) < 0 ? 1.0 : 0.0;
            case LTE:
                // NaN.compareTo(42) == 1, 42.compareTo(NaN) == -1
                // Let NaN <= 42 return false, and 42 <= NaN return true
                if (lhs.isNaN()) return rhs.isNaN() ? 1.0 : 0.0;
                if (rhs.isNaN()) return 1.0;
                return lhs.compareTo(rhs) <= 0 ? 1.0 : 0.0;
            case TERNARY:
                break;
            case ALTERNATIVE:
                break;
            case NOT:
                return lhs.compareTo(1.0) == 0 ? 0.0 : 1.0;
            case FUNCTION:
                break;
            case GROUP:
                break;
            case TERMINAL:
                break;
        }
        return Double.NaN;
    }

    private static boolean sqlMatches(String s, String regEx) {
        StringBuilder sb = new StringBuilder();
        char prev = 0;
        for (int i = 0, len = regEx.length(); i < len; i++) {
            char next = regEx.charAt(i);
            if (next == '_') {
                if (prev == '\\') {
                    sb.deleteCharAt(i - 1);
                } else {
                    // Covert _ to .
                    next = '.';
                }
            } else if (next == '%') {
                if (prev == '\\') {
                    sb.deleteCharAt(i - 1);
                } else {
                    // Convert % to .*
                    sb.append('.');
                    next = '*';                    
                }
            }

            sb.append(next);
            prev = next;
        }

        return s.matches(sb.toString());
    }
    
    private static final Logger LOGGER = Logger.getLogger("com.oracle.iot.shared");

    private static Logger getLogger() {
        return LOGGER;
    }
}
