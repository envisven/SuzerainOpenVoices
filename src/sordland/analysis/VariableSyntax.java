package sordland.analysis;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class VariableSyntax {
    private VariableSyntax() {
    }
    private static final Pattern TOKEN = Pattern.compile("Variable\\s*\\[\\s*(['\"])([^'\"\\\\]+)\\1\\s*\\]|(?:BaseGame\\w*)\\.[A-Za-z_]\\w*|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\d+(?:\\.\\d+)?|[A-Za-z_]\\w*|&&|\\|\\||==|!=|~=|>=|<=|[+*/-]=|[^\\s]");
    public record Expr(String op, String value, List<Expr> children) {
        public Expr {
            children = List.copyOf(children);
        }
        public boolean known() {
            return !op.equals("unknown") && children.stream().allMatch(Expr::known);
        }
        public Set<String> variables() {
            var result = new TreeSet<String>();
            if (op.equals("var")) result.add(value);
            children.forEach(e -> result.addAll(e.variables()));
            return Collections.unmodifiableSet(result);
        }
        public String display(Function<String, String> label) {
            if (op.equals("var")) return label.apply(value);
            if (op.equals("literal") || op.equals("unknown") || op.equals("trigger")) return value;
            if (op.equals("not")) return "NOT (" + children.getFirst().display(label) + ")";
            Expr a = children.getFirst(), b = children.getLast();
            if ((op.equals("==") || op.equals("!=")) && b.op.equals("literal") && Set.of("true", "false").contains(b.value)) {
                boolean positive = b.value.equals("true") == op.equals("==");
                return positive ? a.display(label): "NOT (" + a.display(label) + ")";
            }
            return "(" + a.display(label) + " " + op.toUpperCase(Locale.ROOT) + " " + b.display(label) + ")";
        }
    }
    public static Expr unknown(String text) {
        return new Expr("unknown", text, List.of());
    }
    public static Expr and(Expr a, Expr b) {
        return new Expr("and", "", List.of(a, b));
    }
    public static List<String> tokens(String source) {
        var result = new ArrayList<String>();
        var matcher = TOKEN.matcher(source);
        while (matcher.find()) result.add(matcher.group(2) == null ? matcher.group(): matcher.group(2));
        return result;
    }
    public static boolean variable(String token) {
        return token.matches("BaseGame\\w*\\.[A-Za-z_]\\w*");
    }
    public static Set<String> references(String source) {
        var result = new TreeSet<String>();
        tokens(source).stream().filter(VariableSyntax::variable).forEach(result::add);
        return result;
    }
    public static Expr parse(String source) {
        if (source.isBlank()) return new Expr("literal", "true", List.of());
        try {
            var parser = new Parser(tokens(source));
            Expr value = parser.expression(0);
            return parser.position == parser.tokens.size() ? value: unknown(source);
        } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
            return unknown(source);
        }
    }
    private static final class Parser {
        private final List<String> tokens;
        private int position;
        Parser(List<String> tokens) {
            this.tokens = tokens;
        }
        Expr expression(int minimum) {
            String token = tokens.get(position++);
            Expr left;
            if (token.equals("(")) {
                left = expression(0);
                if (!tokens.get(position++).equals(")")) throw new IllegalArgumentException();
            } else if (Set.of("!", "not").contains(token)) {
                left = new Expr("not", "", List.of(expression(6)));
            } else if (token.equals("-") || token.equals("+")) {
                String number = tokens.get(position++);
                if (!number.matches("\\d+(\\.\\d+)?")) throw new IllegalArgumentException();
                left = new Expr("literal", token.equals("-") ? "-" + number: number, List.of());
            } else if (variable(token)) left = new Expr("var", token, List.of());
            else if (token.matches("true|false|\\d+(\\.\\d+)?|\".*\"|'.*'")) left = new Expr("literal", token, List.of());
            else throw new IllegalArgumentException();
            while (position<tokens.size()) {
                String op = tokens.get(position);
                int priority = switch (op) {
                    case "||", "or" -> 1;
                    case "&&", "and" -> 2;
                    case "==", "!=", "~=", ">", ">=", "<", "<=" -> 3;
                    case "+", "-" -> 4;
                    case "*", "/" -> 5;
                    default -> -1;
                };
                if (priority<minimum) break;
                position++;
                op = switch (op) {
                    case "&&" -> "and";
                    case "||" -> "or";
                    case "~=" -> "!=";
                    default -> op;
                };
                left = new Expr(op, "", List.of(left, expression(priority + 1)));
            }
            return left;
        }
    }
    public record Effect(String variable, String operator, Expr value, boolean readsTarget) {
        public String display() {
            if (operator.equals("=")) return "SET = " + value.display(Function.identity());
            if (Set.of("+", "-").contains(operator) && value.op().equals("literal") && value.value().matches("-?\\d+(\\.\\d+)?")) {
                var number = new java.math.BigDecimal(value.value());
                if (operator.equals("-")) number = number.negate();
                return(number.signum() >= 0 ? "+": "") + number.toPlainString();
            }
            return operator + " " + value.display(Function.identity());
        }
    }
    public static Effect effect(String source) {
        var tokens = tokens(source);
        if (tokens.size()<3 || !variable(tokens.getFirst()) || !Set.of("=", "+=", "-=", "*=", "/=").contains(tokens.get(1))) return null;
        String target = tokens.getFirst(), op = tokens.get(1);
        Expr value = parse(String.join(" ", tokens.subList(2, tokens.size())));
        if (!value.known()) return null;
        if (op.equals("=") && Set.of("+", "-", "*", "/").contains(value.op()) && value.children().getFirst().equals(new Expr("var",
            target, List.of()))) {
            op = value.op() + "=";
            value = value.children().getLast();
        }
        return new Effect(target, op.equals("=") ? "=": op.substring(0, 1), value, !op.equals("=") || value.variables().contains(target));
    }
}
