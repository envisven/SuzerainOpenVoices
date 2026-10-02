package sordland;

import sordland.analysis.*;
import sordland.data.Domain.*;
import java.util.*;
import static sordland.TestSupport.*;

final class DialogueGuardChecks {
    private static Entry node(int id, String condition, String script, int...successors) {
        var links = new ArrayList<Link>();
        for (int successor: successors) links.add(new Link(new EntryKey(1, successor), links.size(), "Normal", false));
        return new Entry(new EntryKey(1, id), 0, "", "Node " + id, "", "", condition, script, "", links, Map.of());
    }
    private static Dataset dataset(Entry...entries) {
        var map = new LinkedHashMap<Integer, Entry>();
        for (Entry entry: entries) map.put(entry.key().dialogueId(), entry);
        return new Dataset(List.of(), Map.of(1, new Conversation(1, "Test", map)), List.of(), List.of());
    }
    private static DialogueGuardResolver.Resolved resolve(Dataset dataset, int target) {
        return new DialogueGuardResolver(dataset).resolve(new EntryKey(1, target));
    }
    static void run(Dataset real) {
        Entry a = node(1, "BaseGame.A == true", "", 3);
        Entry write = node(3, "", "BaseGame.X = true");
        var directData = dataset(a, write);
        var direct = resolve(directData, 3);
        equal(DialogueGuardResolver.Kind.DIRECT_CONDITION_EDGE, direct.kind(), "Direct encoded condition edge");
        equal(Set.of("BaseGame.A"), direct.expression().variables(), "Direct guard variables");
        equal(1, direct.paths().getFirst().edges().size(), "Exact edge recorded");
        var chain = resolve(dataset(node(1, "BaseGame.A", "", 2), node(2, "BaseGame.B", "", 3), write), 3);
        equal("and", chain.expression().op(), "Nested guards AND");
        equal(2, chain.paths().getFirst().predicates().size(), "Nested original predicates");
        equal(DialogueGuardResolver.Kind.PROVEN_CHAIN, chain.kind(), "Chain proof category");
        var alternativesData = dataset(a, node(2, "BaseGame.B", "", 3), write);
        var alternatives = resolve(alternativesData, 3);
        equal("or", alternatives.expression().op(), "Alternative guards OR");
        equal(2, alternatives.paths().size(), "Two independent proof paths retained");
        var indexed = new VariableIndex(alternativesData);
        equal(1, indexed.occurrences("BaseGame.X").size(), "Alternative paths do not duplicate write");
        equal(VariableIndex.Proof.DIALOGUE_GUARD, indexed.occurrences("BaseGame.X").getFirst().proof(), "Propagated proof distinguishable");
        equal(Map.of("BaseGame.A", 1, "BaseGame.B", 1), new VariableAnalyzer(indexed).analyze("BaseGame.X").dependencies(),
            "Derived dependencies");
        var ambiguous = resolve(dataset(node(1, "BaseGame.A", "", 2), node(4, "", "", 2), node(2, "", "", 3), write),
            3);
        check(!ambiguous.propagated(), "Unconditional merge bypass rejects upstream A");
        var surviving = resolve(dataset(node(1, "BaseGame.A", "", 2), node(4, "", "", 2), node(2, "BaseGame.B", "",
            3), write), 3);
        equal(Set.of("BaseGame.B"), surviving.expression().variables(), "Downstream mandatory B survives ambiguous upstream A");
        Entry foreign = new Entry(new EntryKey(2, 1), 0, "", "Foreign", "", "", "BaseGame.B", "", "", List.of(new Link(write.key(),
            0, "Normal", false)), Map.of());
        var cross = new Dataset(List.of(), Map.of(1, new Conversation(1, "One", Map.of(1, a, 3, write)), 2, new Conversation(2,
            "Two", Map.of(1, foreign))), List.of(), List.of());
        check(!new DialogueGuardResolver(cross).resolve(write.key()).propagated(), "Cross-conversation incoming path blocks local dominance");
        var unrelated = resolve(dataset(a, write, node(4, "", "BaseGame.Y = true")), 4);
        check(!unrelated.propagated() && unrelated.expression().variables().isEmpty(), "No manufactured NOT A branch");
        var loop = resolve(dataset(node(1, "BaseGame.A", "", 2), node(2, "", "", 1, 3), write), 3);
        check(!loop.propagated() && loop.boundary().contains("Cycle"), "Loops stop bounded reconstruction");
        var effect = resolve(dataset(node(1, "BaseGame.A", "", 2), node(2, "", "BaseGame.A = false", 3), write), 3);
        check(!effect.propagated(), "Variable mutation blocks stale predicate propagation");
        var unknown = resolve(dataset(node(1, "BaseGame.A", "Mystery()", 3), write), 3);
        check(!unknown.propagated(), "Unknown calls block propagation");
        var transparent = resolve(dataset(node(1, "BaseGame.A", "", 2), node(2, "", "", 3), write), 3);
        check(transparent.propagated() && transparent.paths().getFirst().edges().size() == 2, "Empty continuation allowed");
        Entry speech = new Entry(new EntryKey(1, 2), 5, "Player", "Choice", "Choose", "", "", "", "", List.of(new Link(write.key(),
            0, "Normal", false)), Map.of());
        check(!resolve(dataset(node(1, "BaseGame.A", "", 2), speech, write), 3).propagated(), "Speech is not transparent");
        Entry connector = new Entry(a.key(), 0, "", "Connector", "", "", a.condition(), "", "", List.of(new Link(write.key(),
            0, "Normal", true)), Map.of());
        check(!resolve(dataset(connector, write), 3).propagated(), "Connector boundary");
        Entry priority = new Entry(a.key(), 0, "", "Priority", "", "", a.condition(), "", "", List.of(new Link(write.key(),
            0, "High", false)), Map.of());
        check(!resolve(dataset(priority, write), 3).propagated(), "Priority boundary");
        check(!resolve(dataset(node(1, "BaseGame.A", "", 3, 4), write, node(4, "", "")), 3).propagated(), "Ambiguous condition continuation rejected");
        check(!resolve(dataset(node(1, "BaseGame.A == (", "", 3), write), 3).propagated(), "Malformed predicate rejected");
        var local = resolve(dataset(node(1, "", "Mystery()", 3), node(3, "BaseGame.Local", "BaseGame.X = true")),
            3);
        equal(DialogueGuardResolver.Kind.LOCAL, local.kind(), "Valid local guard survives upstream boundary");
        var resolver = new DialogueGuardResolver(directData);
        check(resolver.resolve(write.key()) == resolver.resolve(write.key()), "Per-entry proof cache");
        var longChain = new ArrayList<Entry>();
        longChain.add(node(1, "BaseGame.A", "", 2));
        for (int i = 2; i<70; i++) longChain.add(node(i, "", "", i + 1));
        longChain.add(node(70, "", "BaseGame.X = true"));
        check(!resolve(dataset(longChain.toArray(Entry[]::new)), 70).propagated(), "Depth bound fails closed");
        realData(real);
    }
    private static void realData(Dataset data) {
        var index = new VariableIndex(data);
        var analyzer = new VariableAnalyzer(index);
        for (String suffix: List.of("Surge", "Balanced", "Fluctuation")) {
            String name = "BaseGame.Situation_Diplomacy_Energy_Price" + suffix;
            if (!index.variables().contains(name)) continue;
            var result = analyzer.analyze(name);
            var propagated = result.rules().stream().filter(o -> o.guardProof() != null && o.guardProof().propagated()).toList();
            check(!propagated.isEmpty(), "Real energy guards reconstructed " + suffix);
            check(result.family() != null, "One-hot family preserved with OR/AND guards " + suffix);
            equal(result, analyzer.analyze(name), "Deterministic derived analysis");
            for (var occurrence: propagated) for (var path: occurrence.guardProof().paths()) {
                for (var predicate: path.predicates()) equal(data.entry(predicate.entry()).condition(), predicate.original(),
                    "Exact real predicate evidence");
                for (var edge: path.edges()) check(data.entry(edge.from()).links().stream().anyMatch(link -> link.target().equals(edge.to()) &&
                    link.order() == edge.order() && link.priority().equals(edge.priority()) && link.connector() == edge.connector()),
                    "Proof edge exists in current source");
            }
            System.out.println("GUARDS " + name + " propagated=" + propagated.size() + "/" + result.rules().size());
        }
        var surge = analyzer.analyze("BaseGame.Situation_Diplomacy_Energy_PriceSurge");
        var rule = surge.rules().stream().filter(o -> o.source().identity().equals("Conversation 44 / Dialogue 450")).findFirst().orElseThrow();
        check(rule.source().guard().isBlank(), "Real regression previously had no local guard");
        check(rule.guardProof().propagated(), "Real 453 to 450 propagation");
        check(rule.guardProof().paths().stream().flatMap(p -> p.edges().stream()).anyMatch(e -> e.from().equals(new EntryKey(44,
            453)) && e.to().equals(new EntryKey(44, 450))), "Real exact EPA proof edge");
        equal(VariableSyntax.parse(data.entry(new EntryKey(44, 453)).condition()), rule.condition(), "Guard matches current JSON, no hardcoded production rule");
        System.out.println("REAL EPA RULE " + rule.effect().display() + " WHEN " + rule.condition().display(index::label));
        System.out.println(rule.guardProof().evidence());
    }
}
