package sordland;

import sordland.analysis.*;
import sordland.data.Domain.*;
import java.util.*;
import static sordland.TestSupport.*;
import static sordland.analysis.VariableIndex.*;

final class RouteCorrectionChecks {
    private static Entry node(int id, String condition, String script, String text, int...next) {
        var links = new ArrayList<Link>();
        for (int to: next) links.add(new Link(new EntryKey(1, to), links.size(), "Normal", false));
        return new Entry(new EntryKey(1, id), 0, "", id == 0 ? "START": "Node " + id, text, "", condition, script,
            "", links, Map.of());
    }
    private static Dataset data(Entry...entries) {
        var map = new LinkedHashMap<Integer, Entry>();
        for (var e: entries) map.put(e.key().dialogueId(), e);
        return new Dataset(List.of(), Map.of(1, new Conversation(1, "Fixture", map)), List.of(), List.of());
    }
    private static DialogueGuardResolver.Resolved routes(Dataset data, int id) {
        return new DialogueGuardResolver(data).resolveNumericRoutes(new EntryKey(1, id));
    }
    static void run(Dataset real) {
        var speech = routes(data(node(0, "BaseGame.A", "", "", 1), node(1, "", "", "Ordinary speech", 2), node(2,
            "", "BaseGame.Other += 1", "", 3), node(3, "BaseGame.B", "BaseGame.X += 1", "")), 3);
        equal(Set.of("BaseGame.A", "BaseGame.B"), speech.expression().variables(), "Speech and disjoint effects retain full guard ancestry");
        check(speech.paths().stream().allMatch(DialogueGuardResolver.Path::complete), "Ordinary route complete");
        equal(3, speech.paths().getFirst().edges().size(), "Exact speech/effect path edges retained");
        var unguarded = routes(data(node(0, "", "", "Speech", 1), node(1, "", "BaseGame.X += 1", "")), 1);
        equal(DialogueGuardResolver.Kind.NO_GUARD, unguarded.kind(), "No additional guard is a valid route");
        var mixed = routes(data(node(0, "", "", "", 1, 2), node(1, "BaseGame.A", "", "", 3), node(2, "", "", "", 3),
            node(3, "", "BaseGame.X += 1", "")), 3);
        check(mixed.paths().stream().allMatch(DialogueGuardResolver.Path::complete), "Unguarded alternative does not erase route proof");
        check(mixed.graph().nodes().values().stream().mapToInt(n -> n.incoming().size()).sum() == 4, "All alternative edges preserved in route DAG");
        var mutable = routes(data(node(0, "BaseGame.A", "", "", 1), node(1, "", "BaseGame.A = false", "", 2), node(2,
            "!BaseGame.A", "BaseGame.X += 1", "")), 2);
        check(mutable.expression().toString().contains("history"), "Historical predicate distinguished after mutation");
        check(!CompatibilityAnalyzer.incompatible(mutable.expression(), VariableSyntax.parse("true")), "Historical and current predicates not falsely contradictory");
        var unknown = routes(data(node(0, "BaseGame.A", "Mystery()", "", 1), node(1, "BaseGame.B", "BaseGame.X += 1",
            "")), 1);
        equal(DialogueGuardResolver.Kind.PARTIAL, unknown.kind(), "Unsupported script has explicit partial proof");
        check(unknown.boundary().contains("UNSUPPORTED") && unknown.expression().variables().contains("BaseGame.B"),
            "Partial proof retains downstream condition and reason");
        var cycle = routes(data(node(1, "BaseGame.A", "", "", 2), node(2, "", "", "", 1, 3), node(3, "", "BaseGame.X += 1",
            "")), 3);
        equal(DialogueGuardResolver.Kind.PARTIAL, cycle.kind(), "Cycle bounded and reported");
        check(cycle.boundary().contains("CYCLE"), "Exact unresolved reason");
        var option1 = new Entry(new EntryKey(1, 1), 5, "Player", "Choice", "First", "First", "", "", "", List.of(new Link(new EntryKey(1,
            3), 0, "Normal", false)), Map.of());
        var option2 = new Entry(new EntryKey(1, 2), 5, "Player", "Choice", "Second", "Second", "", "", "", List.of(new Link(new EntryKey(1,
            4), 0, "Normal", false)), Map.of());
        var choices = data(node(0, "", "", "", 1, 2), option1, option2, node(3, "", "BaseGame.X += 1", ""), node(4,
            "", "BaseGame.X += 2", ""));
        equal(new EntryKey(1, 1), routes(choices, 3).graph().nodes().values().stream().flatMap(n -> n.incoming().stream()).filter(a -> a.choice() != null).findFirst().orElseThrow().choice().option(),
            "Actual choice identity retained");
        var index = new VariableIndex(real);
        var catalog = new VariableCatalog(index);
        check(catalog.filter("").size()<catalog.filter("", true).size(), "Quantitative-only default");
        for (String v: catalog.filter("")) equal(VariableAnalyzer.Layout.NUMERIC, VariableClassification.classify(index.occurrences(v)),
            "Shared cheap numeric criterion");
        check(!catalog.filter("Situation_Diplomacy_Energy_PriceSurge").contains("BaseGame.Situation_Diplomacy_Energy_PriceSurge"),
            "Boolean excluded by default");
        check(catalog.filter("Situation_Diplomacy_Energy_PriceSurge", true).contains("BaseGame.Situation_Diplomacy_Energy_PriceSurge"),
            "All variables restores Boolean catalogue");
        var economy = new VariableAnalyzer(index).analyze("BaseGame.Economy");
        var first = economy.rules().stream().filter(o -> o.source().identity().equals("Conversation 93 / Dialogue 275")).findFirst().orElseThrow();
        var second = economy.rules().stream().filter(o -> o.source().identity().equals("Conversation 93 / Dialogue 285")).findFirst().orElseThrow();
        for (var o: List.of(first, second)) {
            check(o.guardProof().paths().stream().allMatch(DialogueGuardResolver.Path::complete), "Real C93 complete ancestry " + o.source().identity());
            for (var path: o.guardProof().paths()) {
                for (var predicate: path.predicates()) equal(real.entry(predicate.entry()).condition(), predicate.original(),
                    "Real ancestry predicate exact");
                for (var edge: path.edges()) check(real.entry(edge.from()).links().stream().anyMatch(l -> l.target().equals(edge.to()) &&
                    l.order() == edge.order() && l.connector() == edge.connector() && l.priority().equals(edge.priority())),
                    "Real ancestry edge exact");
            }
        }
        check(second.condition().variables().containsAll(Set.of("BaseGame.Turn01_InT_Investment_Highway", "BaseGame.Turn06_InT_OnTime",
            "BaseGame.Agnolia_TradeDeal")), "285 retains Highway/OnTime/Agnolia ancestry");
        check(CompatibilityAnalyzer.incompatible(second.condition(), VariableSyntax.parse("!BaseGame.Turn01_InT_Investment_Highway")),
            "Highway is a requirement, not merely a referenced variable");
        check(CompatibilityAnalyzer.incompatible(second.condition(), VariableSyntax.parse("!BaseGame.Turn06_InT_OnTime")),
            "OnTime is a requirement");
        check(!first.condition().equals(second.condition()), "275 and 285 have distinct route conditions");
        equal(2, NumericVariableLayout.build(NumericLayoutChecks.analysis(List.of(first, second))).size(), "Independent writes never collapsed");
        equal(1, NumericVariableLayout.build(NumericLayoutChecks.analysis(List.of(first, first))).size(), "True duplicate source occurrences deduplicated");
        String script = "BaseGame.X += 1; BaseGame.X += 1";
        var source = new Source("same", "Same field", 1, "Instruction", script, "BaseGame.A", List.of());
        var writes = new ArrayList<Occurrence>();
        for (int n = 0; n<2; n++) writes.add(new Occurrence("BaseGame.X", Access.READ_WRITE, Proof.EXACT, source,
            n, "BaseGame.X += 1", VariableSyntax.parse("BaseGame.A"), VariableSyntax.effect("BaseGame.X += 1")));
        var totals = WriteAggregation.proven(writes);
        equal(1, totals.size(), "Co-executed complete straight-line field can aggregate");
        equal(new java.math.BigDecimal("2"), totals.getFirst().delta(), "Proven +2 total");
        equal(2, totals.getFirst().writes().size(), "Aggregated references retained");
        equal(0, WriteAggregation.proven(List.of(first, second)).size(), "Same guard does not prove aggregation");
        System.out.println("C93: distinct 275/285 full ancestry verified; quantitative catalogue=" + catalog.filter("").size() + " / " + catalog.filter("",
            true).size());
    }
}
