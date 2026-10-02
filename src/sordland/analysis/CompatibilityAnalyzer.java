package sordland.analysis;

import java.math.BigDecimal;
import java.util.*;
import static sordland.analysis.VariableSyntax.Expr;
import static sordland.analysis.NumericVariableLayout.*;

public final class CompatibilityAnalyzer {
    public enum Verdict {
        INCOMPATIBLE, UNKNOWN
    }
    public record Witness(Expr condition, Reference source) {
    }
    public record Selection(List<Witness> witnesses) {
        public Selection {
            witnesses = List.copyOf(witnesses);
        }
    }
    public record Result(Verdict verdict, String reason) {
    }
    @FunctionalInterface
    public interface SourceRule {
        String conflict(Reference a, Reference b);
    }
    private final List<SourceRule> sourceRules;
    public CompatibilityAnalyzer(VariableIndex index) {
        this(List.of((a, b) -> {
            if (a.path() != null && b.path() != null && a.path().complete() && b.path().complete() && !a.path().branched() &&
                !b.path().branched()) for (var x: a.path().choices()) for (var y: b.path().choices()) if (x.decision().equals(y.decision()) &&
                !x.option().equals(y.option())) return "Different actual dialogue choices at " + x.decision();
            return null;
        },(a, b) -> {
            var x = index.choice(a.occurrence());
            var y = index.choice(b.occurrence());
            return x != null && y != null && x.decision().equals(y.decision()) && x.option() != y.option() ? "Different options in one source selection: " + x.evidence() + " / " + y.evidence(): null;
        }));
    }
    public CompatibilityAnalyzer(List<SourceRule> rules) {
        sourceRules = List.copyOf(rules);
    }
    public static Selection selection(BonusBlock block, Region region) {
        var witnesses = new ArrayList<Witness>();
        for (Reference ref: region.references()) {
            int ordinal = Math.max(0, ref.pathIndex());
            Route route = block.routes().get(ordinal);
            witnesses.add(new Witness(ConditionLogic.junction("and", List.of(route.condition(), region.expression())),
                ref));
        }
        return new Selection(witnesses);
    }
    public static Selection selection(Route route) {
        return new Selection(List.of(new Witness(route.condition(), route.reference())));
    }
    public Result compare(Selection a, Selection b) {
        if (a.witnesses().isEmpty() || b.witnesses().isEmpty()) return unknown();
        var reasons = new LinkedHashSet<String>();
        for (Witness x: a.witnesses()) for (Witness y: b.witnesses()) {
            String reason = null;
            for (SourceRule rule: sourceRules) {
                reason = rule.conflict(x.source(), y.source());
                if (reason != null) break;
            }
            if (reason == null && incompatible(x.condition(), y.condition())) reason = "Contradictory predicates at the same state";
            if (reason == null) return unknown();
            reasons.add(reason);
        }
        return new Result(Verdict.INCOMPATIBLE, String.join("\n", reasons));
    }
    private static Result unknown() {
        return new Result(Verdict.UNKNOWN, "No incompatibility proven");
    }
    public static boolean incompatible(Expr first, Expr second) {
        List<List<Expr>>clauses = clauses(ConditionLogic.junction("and", List.of(ConditionLogic.normalize(first),
            ConditionLogic.normalize(second))));
        return clauses != null && !clauses.isEmpty() && clauses.stream().allMatch(CompatibilityAnalyzer::contradiction);
    }
    private static List<List<Expr>>clauses(Expr e) {
        if (!Set.of("and", "or").contains(e.op())) return List.of(List.of(e));
        var result = new ArrayList<List<Expr>>();
        if (e.op().equals("and")) result.add(List.of());
        for (Expr child: e.children()) {
            var part = clauses(child);
            if (part == null) return null;
            if (e.op().equals("or")) {
                if (result.size() + part.size()>256) return null;
                result.addAll(part);
            } else {
                if ((long) result.size() * part.size()>256) return null;
                var next = new ArrayList<List<Expr>>();
                for (var a: result) for (var b: part) {
                    var both = new ArrayList<>(a);
                    both.addAll(b);
                    next.add(both);
                }
                result = next;
            }
        }
        return result;
    }
    private static boolean contradiction(List<Expr> terms) {
        var booleans = new HashMap<String, String>();
        var numbers = new HashMap<String, Bounds>();
        var known = new HashSet<>(terms.stream().filter(Expr::known).toList());
        for (Expr e: terms) {
            if (e.equals(ConditionLogic.literal("false"))) return true;
            if (e.op().equals("not") && e.known() && known.contains(e.children().getFirst())) return true;
            if (!Set.of("==", "!=", ">", ">=", "<", "<=").contains(e.op())) continue;
            Expr a = e.children().getFirst(), b = e.children().getLast();
            String op = e.op();
            if (a.op().equals("literal") && b.op().equals("var")) {
                Expr tmp = a;
                a = b;
                b = tmp;
                op = switch (op) {
                    case ">" -> "<";
                    case ">=" -> "<=";
                    case "<" -> ">";
                    case "<=" -> ">=";
                    default -> op;
                };
            }
            if (!a.op().equals("var") || !b.op().equals("literal")) continue;
            if (Set.of("true", "false").contains(b.value()) && Set.of("==", "!=").contains(op)) {
                String value = Boolean.toString(b.value().equals("true") == op.equals("=="));
                String previous = booleans.putIfAbsent(a.value(), value);
                if (previous != null && !previous.equals(value)) return true;
            } else {
                var value = ConditionLogic.number(b);
                if (value == null) continue;
                Bounds bound = numbers.computeIfAbsent(a.value(), key -> new Bounds());
                if (bound.add(op, value)) return true;
            }
        }
        return false;
    }
    private static final class Bounds {
        BigDecimal low, high;
        boolean lowOpen, highOpen;
        final Set<BigDecimal> excluded = new HashSet<>();
        boolean add(String op, BigDecimal n) {
            n = n.stripTrailingZeros();
            if (op.equals("!=")) excluded.add(n);
            if (Set.of("==", ">", ">=").contains(op)) {
                boolean open = op.equals(">");
                int cmp = low == null ? 1: n.compareTo(low);
                if (cmp>0) {
                    low = n;
                    lowOpen = open;
                } else if (cmp == 0) lowOpen |= open;
            }
            if (Set.of("==", "<", "<=").contains(op)) {
                boolean open = op.equals("<");
                int cmp = high == null ? -1: n.compareTo(high);
                if (cmp<0) {
                    high = n;
                    highOpen = open;
                } else if (cmp == 0) highOpen |= open;
            }
            if (low == null || high == null) return false;
            int cmp = low.compareTo(high);
            return cmp>0 || cmp == 0 &&(lowOpen || highOpen || excluded.contains(low.stripTrailingZeros()));
        }
    }
}
