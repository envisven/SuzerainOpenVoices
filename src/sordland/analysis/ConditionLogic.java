package sordland.analysis;

import java.math.BigDecimal;
import java.util.*;
import static sordland.analysis.VariableSyntax.Expr;

public final class ConditionLogic {
    private ConditionLogic() {
    }
    public static Expr literal(String value) {
        return new Expr("literal", value, List.of());
    }
    public static boolean truth(Expr e) {
        return e.equals(literal("true"));
    }
    public static Expr normalize(Expr e) {
        return normalize(e, false);
    }
    private static Expr normalize(Expr e, boolean negative) {
        if (e.op().equals("history")) {
            Expr child = normalize(e.children().getFirst(), negative);
            if (Set.of("and", "or").contains(child.op())) return new Expr(child.op(), "", child.children().stream().map(c -> normalize(new Expr("history",
                e.value(), List.of(c)))).toList());
            return new Expr("history", e.value(), List.of(child));
        }
        if (e.op().equals("not")) return normalize(e.children().getFirst(), !negative);
        if (e.op().equals("and") || e.op().equals("or")) {
            String op = negative ? e.op().equals("and") ? "or": "and": e.op();
            return junction(op, e.children().stream().map(c -> normalize(c, negative)).toList());
        }
        if (e.op().equals("var")) return new Expr("==", "", List.of(e, literal(negative ? "false": "true")));
        if (e.op().equals("literal") && Set.of("true", "false").contains(e.value())) return literal(Boolean.toString(e.value().equals("true") != negative));
        if (Set.of("==", "!=").contains(e.op())) {
            Expr a = e.children().getFirst(), b = e.children().getLast();
            if (a.op().equals("literal") &&(b.op().equals("var") || booleanExpression(b))) {
                Expr t = a;
                a = b;
                b = t;
            }
            String op = negative ? e.op().equals("==") ? "!=": "==": e.op();
            if (booleanExpression(a) && b.op().equals("literal") && Set.of("true", "false").contains(b.value())) return normalize(a,
                b.value().equals("true") != op.equals("=="));
            if (a.op().equals("var") && b.op().equals("literal") && Set.of("true", "false").contains(b.value())) {
                return new Expr("==", "", List.of(a, literal(Boolean.toString(b.value().equals("true") == op.equals("==")))));
            }
            return new Expr(op, "", List.of(a, b));
        }
        return negative ? new Expr("not", "", List.of(e)): e;
    }
    private static boolean booleanExpression(Expr e) {
        return Set.of("and", "or", "not", "==", "!=", ">", ">=", "<", "<=").contains(e.op());
    }
    public static Expr junction(String op, List<Expr> expressions) {
        var terms = new LinkedHashSet<Expr>();
        for (Expr e: expressions) {
            if (e.op().equals(op)) terms.addAll(e.children());
            else terms.add(e);
        }
        if (op.equals("and")) {
            if (terms.contains(literal("false"))) return literal("false");
            terms.remove(literal("true"));
        } else {
            if (terms.contains(literal("true"))) return literal("true");
            terms.remove(literal("false"));
        }
        if (terms.isEmpty()) return literal(op.equals("and") ? "true": "false");
        for (Expr term: terms) if (terms.contains(complement(term))) return literal(op.equals("or") ? "true": "false");
        if (terms.size() == 1) return terms.iterator().next();
        return new Expr(op, "", List.copyOf(terms));
    }
    public static Expr complement(Expr e) {
        if (e.op().equals("not")) return e.children().getFirst();
        if (e.op().equals("==") && e.children().getFirst().op().equals("var") && Set.of("true", "false").contains(e.children().getLast().value())) return new Expr("==",
            "", List.of(e.children().getFirst(), literal(Boolean.toString(!Boolean.parseBoolean(e.children().getLast().value())))));
        return new Expr("not", "", List.of(e));
    }
    public static String basename(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }
    public static String display(Expr e) {
        if (!Set.of("and", "or").contains(e.op()) && e.children().stream().anyMatch(ConditionLogic::containsGroup)) return "Compound predicate — inspect source";
        return switch (e.op()) {
            case "history" -> "Earlier: " + display(e.children().getFirst());
            case "trigger", "exhaustive" -> e.value();
            case "var" -> basename(e.value());
            case "literal" -> e.value();
            case "unknown" -> "Unresolved predicate — inspect source";
            case "not" -> "NOT (" + display(e.children().getFirst()) + ")";
            case "and", "or" -> throw new IllegalArgumentException("Boolean groups must be rendered geometrically");
            default -> "" + operand(e.children().getFirst()) + " " +(e.op().equals("==") ? "=": e.op()) + " " + operand(e.children().getLast());
        };
    }
    private static boolean containsGroup(Expr e) {
        return Set.of("and", "or").contains(e.op()) || e.children().stream().anyMatch(ConditionLogic::containsGroup);
    }
    private static String operand(Expr e) {
        return e.children().isEmpty() ? display(e): "(" + display(e) + ")";
    }
    public static BigDecimal number(Expr e) {
        try {
            if (e.op().equals("literal")) return new BigDecimal(e.value());
            if (!Set.of("+", "-", "*", "/").contains(e.op())) return null;
            BigDecimal a = number(e.children().getFirst()), b = number(e.children().getLast());
            if (a == null || b == null) return null;
            return switch (e.op()) {
                case "+" -> a.add(b);
                case "-" -> a.subtract(b);
                case "*" -> a.multiply(b);
                default -> a.divide(b);
            };
        } catch (ArithmeticException | NumberFormatException ex) {
            return null;
        }
    }
}
