package sordland.analysis;

import java.util.*;
import static sordland.analysis.VariableIndex.*;

public final class VariableAnalyzer {
    public enum Layout {
        BOOLEAN, NUMERIC, VALUES, EVIDENCE
    }
    public record Family(List<String> members, List<Occurrence> transitions) {
        public Family {
            members = List.copyOf(members);
            transitions = List.copyOf(transitions);
        }
    }
    public record Analysis(String variable, Layout layout, List<Occurrence> evidence, List<Occurrence> rules, Map<String,
        Integer> dependencies, Family family) {
        public Analysis {
            evidence = List.copyOf(evidence);
            rules = List.copyOf(rules);
            dependencies = Collections.unmodifiableMap(new TreeMap<>(dependencies));
        }
        public long count(Access access) {
            return evidence.stream().filter(o -> o.access() == access).count();
        }
    }
    private final VariableIndex index;
    private final Map<String, Analysis> cache = new HashMap<>();
    public VariableAnalyzer(VariableIndex index) {
        this.index = index;
    }
    public synchronized Analysis analyze(String name) {
        checkCancelled();
        if (!index.variables().contains(name)) throw new IllegalArgumentException("Select a catalog variable");
        return cache.computeIfAbsent(name, this::build);
    }
    private Analysis build(String name) {
        boolean numeric = VariableClassification.classify(index.occurrences(name)) == Layout.NUMERIC;
        var evidence = index.occurrences(name).stream().map(o -> numeric && VariableClassification.write(o) ? withNumericRoutes(o): withSummaryGuard(o)).toList();
        var rules = evidence.stream().filter(o -> o.access() == Access.WRITE || o.access() == Access.READ_WRITE).toList();
        Layout layout = VariableClassification.classify(evidence);
        var dependencies = new TreeMap<String, Integer>();
        rules.forEach(o ->(o.guardProof() != null && o.guardProof().graph() != null ? o.guardProof().graph().variables(): o.condition().variables()).forEach(v -> dependencies.merge(v,
            1, Integer::sum)));
        return new Analysis(name, layout, evidence, rules, dependencies, family(name, rules));
    }
    private Occurrence withNumericRoutes(Occurrence o) {
        var proof = index.numericRoutes(o);
        if (proof == null) return o;
        return new Occurrence(o.variable(), o.access(), o.proof(), o.source(), o.operation(), o.expression(), proof.expression(),
            o.effect(), proof);
    }
    private Occurrence withSummaryGuard(Occurrence occurrence) {
        var proof = occurrence.guardProof();
        if (proof == null || !proof.propagated()) return occurrence;
        return new Occurrence(occurrence.variable(), occurrence.access(), occurrence.proof(), occurrence.source(),
            occurrence.operation(), occurrence.expression(), proof.expression(), occurrence.effect(), proof);
    }
    private static boolean booleanWrite(Occurrence o) {
        return o.effect() != null && o.effect().operator().equals("=") && o.effect().value().op().equals("literal") &&
            Set.of("true", "false").contains(o.effect().value().value());
    }
    private Family family(String name, List<Occurrence> rules) {
        for (Occurrence rule: rules) {
            checkCancelled();
            var candidate = new TreeSet<String>();
            for (var command: sordland.graph.Semantics.analyze(rule.source().original(), "").commands()) {
                var effect = VariableSyntax.effect(command.raw());
                if (effect != null && effect.operator().equals("=") && Set.of("true", "false").contains(effect.value().value())) candidate.add(effect.variable());
            }
            if (candidate.size()<2 || candidate.size()>6 || !candidate.contains(name)) continue;
            var groups = new TreeMap<String, Map<String, Occurrence>>();
            boolean valid = true;
            for (String member: candidate) for (Occurrence raw: index.occurrences(member)) {
                Occurrence occurrence = withSummaryGuard(raw);
                if (occurrence.access() == Access.READ || occurrence.access() == Access.REFERENCE) continue;
                if (occurrence.proof() == Proof.UNRESOLVED || !booleanWrite(occurrence)) {
                    valid = false;
                    break;
                }
                var group = groups.computeIfAbsent(occurrence.source().key(), key -> new TreeMap<>());
                if (group.put(member, occurrence) != null) valid = false;
            }
            if (!valid || groups.size()<2) continue;
            var transitions = new ArrayList<Occurrence>();
            var outcomes = new HashSet<String>();
            for (var group: groups.values()) {
                if (!group.keySet().equals(candidate)) {
                    valid = false;
                    break;
                }
                var active = group.values().stream().filter(o -> o.effect().value().value().equals("true")).toList();
                if (active.size() != 1) {
                    valid = false;
                    break;
                }
                Occurrence transition = active.getFirst();
                if (!simple(transition.condition())) {
                    valid = false;
                    break;
                }
                outcomes.add(transition.variable());
                transitions.add(transition);
            }
            if (valid && outcomes.size() >= 2 && transitions.size() <= 24) {
                transitions.sort(Comparator.comparing((Occurrence o) -> o.source().turn(), Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(o -> o.source().key()).thenComparingInt(Occurrence::operation));
                return new Family(List.copyOf(candidate), transitions);
            }
        }
        return null;
    }
    private static boolean simple(VariableSyntax.Expr expression) {
        if (!expression.known()) return false;
        return expression.children().stream().allMatch(VariableAnalyzer::simple);
    }
}
