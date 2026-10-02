package sordland.analysis;

import java.util.*;
import static sordland.analysis.VariableSyntax.Expr;

public final class BooleanCoverage {
    private BooleanCoverage() {
    }
    public record Proof(boolean exhaustive, String reason) {
    }
    public static Proof prove(Expr expression) {
        if (!supported(expression, 0)) return new Proof(false, "Non-Boolean, historical, choice or unresolved atom retained");
        Expr e = ConditionLogic.normalize(expression);
        if (ConditionLogic.truth(e)) return new Proof(true, "Direct Boolean complement/identity");
        if (e.op().equals("or")) {
            Set<Expr> alternatives = new HashSet<>(e.children());
            for (Expr alternative: e.children()) if (alternative.op().equals("and") && alternative.children().stream().allMatch(x -> alternatives.contains(ConditionLogic.complement(x)))) return new Proof(true,
                "Every conjunct's complement is an alternative; exhaustive N-way cover");
        }
        try {
            return new Proof(new Diagram().build(e) == 1, "Reduced Boolean decision diagram (4096 nodes / 20000 operations maximum)");
        } catch (Limit exhausted) {
            return new Proof(false, "Symbolic budget exhausted; original structure retained");
        }
    }
    private static boolean supported(Expr e, int depth) {
        if (depth>128) return false;
        return switch (e.op()) {
            case "var" -> true;
            case "literal" -> Set.of("true", "false").contains(e.value());
            case "and", "or", "not" -> e.children().stream().allMatch(c -> supported(c, depth + 1));
            case "==", "!=" -> e.children().size() == 2 && e.children().stream().anyMatch(c -> c.op().equals("literal") &&
                Set.of("true", "false").contains(c.value())) && e.children().stream().allMatch(c -> supported(c, depth + 1));
            default -> false;
        };
    }
    private static final class Limit extends RuntimeException {
    }
    private record Node(String variable, int low, int high) {
    }
    private record Apply(String op, int first, int second) {
    }
    private static final class Diagram {
        final List<Node> nodes = new ArrayList<>(Arrays.asList(null, null));
        final Map<Node, Integer> unique = new HashMap<>();
        final Map<Apply, Integer> memo = new HashMap<>();
        final Map<Expr, Integer> built = new HashMap<>();
        int work;
        int node(String variable, int low, int high) {
            if (low == high) return low;
            Node n = new Node(variable, low, high);
            Integer old = unique.get(n);
            if (old != null) return old;
            if (nodes.size() >= 4096) throw new Limit();
            int id = nodes.size();
            nodes.add(n);
            unique.put(n, id);
            return id;
        }
        int build(Expr e) {
            if (++work>20000) throw new Limit();
            Integer cached = built.get(e);
            if (cached != null) return cached;
            int result;
            if (e.op().equals("literal")) result = e.value().equals("true") ? 1: 0;
            else if (e.op().equals("var")) result = node(e.value(), 0, 1);
            else if (e.op().equals("not")) result = apply("xor", build(e.children().getFirst()), 1);
            else if (e.op().equals("==") || e.op().equals("!=")) {
                result = apply("xor", build(e.children().getFirst()), build(e.children().getLast()));
                if (e.op().equals("==")) result = apply("xor", result, 1);
            } else {
                result = e.op().equals("and") ? 1: 0;
                for (Expr c: e.children()) result = apply(e.op(), result, build(c));
            }
            built.put(e, result);
            return result;
        }
        int apply(String op, int a, int b) {
            if (++work>20000) throw new Limit();
            if (a> b) {
                int t = a;
                a = b;
                b = t;
            }
            if (a<2 && b<2) return switch (op) {
                case "and" -> a & b;
                case "or" -> a | b;
                default -> a ^ b;
            };
            if (op.equals("and")) {
                if (a == 0) return 0;
                if (a == 1) return b;
                if (a == b) return a;
            }
            if (op.equals("or")) {
                if (a == 0) return b;
                if (a == 1) return 1;
                if (a == b) return a;
            }
            if (op.equals("xor")) {
                if (a == 0) return b;
                if (a == b) return 0;
            }
            Apply key = new Apply(op, a, b);
            Integer previous = memo.get(key);
            if (previous != null) return previous;
            Node x = a<2 ? null: nodes.get(a), y = b<2 ? null: nodes.get(b);
            String variable = x == null ? y.variable(): y == null ? x.variable(): x.variable().compareTo(y.variable())<0 ? x.variable(): y.variable();
            int al = x != null && x.variable().equals(variable) ? x.low(): a, ah = x != null && x.variable().equals(variable) ? x.high(): a;
            int bl = y != null && y.variable().equals(variable) ? y.low(): b, bh = y != null && y.variable().equals(variable) ? y.high(): b;
            int result = node(variable, apply(op, al, bl), apply(op, ah, bh));
            memo.put(key, result);
            return result;
        }
    }
}
