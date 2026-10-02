package sordland;

import sordland.data.*;
import sordland.data.Domain.*;
import sordland.analysis.*;
import sordland.graph.*;
import java.nio.file.*;
import java.util.*;
import static sordland.TestSupport.*;
import static sordland.analysis.NumericVariableLayout.*;

public final class FinalValidationChecks {
    public static void main(String[] args) throws Exception {
        run(Loader.load(Path.of("data", Loader.ENTITY_FILE), Path.of("data", Loader.CONVERSATIONS_FILE)));
        System.out.println("PASS: " + TestSupport.count() + " final real-data acceptance checks");
    }
    static Item item(Dataset d, String name) {
        return d.items().stream().filter(i -> i.internalName().equals(name)).findFirst().orElseThrow();
    }
    static Graph.Node event(Graph g, String name) {
        return g.nodes.stream().filter(n -> n.item != null && n.item.internalName().equals(name)).findFirst().orElseThrow();
    }
    static void run(Dataset data) throws Exception {
        var index = new VariableIndex(data);
        var analyzer = new VariableAnalyzer(index);
        var agnolia = build(analyzer.analyze("BaseGame.AgnoliaTradeDeal_Negotiation"));
        equal(9, agnolia.size(), "Nine actual Agnolia writes");
        for (var pair: Map.of(176, "Not if you keep acting like", 177, "Of course, Mr. Van Hoorten.").entrySet()) {
            var b = agnolia.stream().filter(x -> x.occurrence().source().identity().equals("Conversation 77 / Dialogue " + pair.getKey())).findFirst().orElseThrow();
            check(b.cells().stream().anyMatch(c -> c.region().expression().op().equals("trigger") && c.region().expression().value().contains("[CHOICE]") &&
                c.region().expression().value().contains(pair.getValue())), "Direct Agnolia choice visible in normal cell");
            equal(pair.getKey() == 176 ? "-1": "+1", b.effect().text(), "Exact Agnolia choice effect");
        }
        for (String variable: List.of("BaseGame.Policy_Diplomacy_RelaxedImmigration", "BaseGame.Turn01_InT_Investment_Highway",
            "BaseGame.Decision_Turn04_RegionalInvestment_Agnland")) check(agnolia.stream().flatMap(b -> b.cells().stream()).anyMatch(c -> c.region().expression().variables().contains(variable)),
            "Agnolia state contributor retained: " + variable);
        for (String variable: List.of("BaseGame.AgnoliaTradeDeal_Negotiation", "BaseGame.Economy")) {
            var analysis = analyzer.analyze(variable);
            var blocks = build(analysis);
            long indexed = index.occurrences(variable).stream().filter(o -> o.effect() != null).count();
            equal(indexed,(long) blocks.size(), "All indexed numeric writes rendered: " + variable);
            equal(blocks.size(), new HashSet<>(blocks.stream().map(BonusBlock::id).toList()).size(), "No independent write merged");
            for (var b: blocks) {
                var retained = new HashSet<Reference>();
                b.sections().forEach(s -> retained.addAll(s.root().references()));
                equal(new HashSet<>(b.routes().stream().map(Route::reference).toList()), retained, "Exact source-reference union preserved");
                for (var ref: retained) equal(b.occurrence(), ref.occurrence(), "Factoring never crosses independent writes");
                for (var c: b.cells()) check(!ConditionLogic.display(c.region().expression()).contains("No additional condition"),
                    "No condition filler");
            }
            writeAudit(variable, indexed, blocks);
        }
        var a = ConditionLogic.normalize(VariableSyntax.parse("BaseGame.A"));
        var notA = ConditionLogic.normalize(VariableSyntax.parse("!BaseGame.A"));
        check(ConditionLogic.truth(ConditionLogic.junction("or", List.of(a, notA))), "A OR !A removed");
        var shared = factor(region(ConditionLogic.normalize(VariableSyntax.parse("(BaseGame.A && BaseGame.X) || (!BaseGame.A && BaseGame.X)")),
            Set.of()));
        equal(ConditionLogic.normalize(VariableSyntax.parse("BaseGame.X")), shared.expression(), "Complementary branches preserve common X");
        var distinct = factor(region(ConditionLogic.normalize(VariableSyntax.parse("(BaseGame.A && BaseGame.X) || (!BaseGame.A && BaseGame.Y)")),
            Set.of()));
        check(distinct.expression().variables().containsAll(Set.of("BaseGame.A", "BaseGame.X", "BaseGame.Y")), "Different outcomes retain the determining predicate");
        var provenance = new CausalProvenance(data);
        var infrastructure = item(data, "Turn01_InT_Infrastructure");
        var mandatory = provenance.mandatoryOutcomes(infrastructure);
        check(mandatory != null, "Real infrastructure completing routes must reach project choice");
        equal(2, mandatory.options().size(), "Exactly two project choices; no neither option");
        equal(Set.of("BaseGame.Turn01_InT_Investment_Highway", "BaseGame.Turn01_InT_Investment_Railway"), mandatory.options().stream().flatMap(o -> o.writes().keySet().stream()).collect(java.util.stream.Collectors.toSet()),
            "Exact project enabling writes");
        var graph = new RootedCampaignGraphBuilder().build(data, null);
        var meeting = event(graph, "Turn01_InT_Infrastructure");
        var out = graph.edges.stream().filter(e -> e.from.equals(meeting.id) && graph.nodes.stream().anyMatch(n -> n.id.equals(e.to) &&
        (n.kind == Graph.Kind.CHOICE || n.kind == Graph.Kind.JUNCTION))).toList();
        equal(2, out.size(), "Invested meeting exits only through H3 or L1");
        check(out.stream().allMatch(e -> graph.nodes.stream().anyMatch(n -> n.id.equals(e.to) && n.kind == Graph.Kind.CHOICE)),
            "No invested bypass of project choice");
        var high = event(graph, "Turn02_InT_HighwayContract");
        var rail = event(graph, "Turn02_InT_RailwayContract");
        String gate = sourceGate(graph, high.id);
        equal(gate, sourceGate(graph, rail.id), "Same exact choice controls both contracts");
        var projectOptions = graph.edges.stream().filter(e -> e.from.equals(gate)).map(e -> node(graph, e.to)).toList();
        equal(2, projectOptions.size(), "Selected project split has no third skip");
        check(projectOptions.stream().allMatch(n -> n.kind == Graph.Kind.CHOICE &&(n.text.contains("H-3") || n.text.contains("L-1"))),
            "Contract follows exact selected project");
        for (var option: projectOptions) equal(1L, graph.edges.stream().filter(e -> e.from.equals(option.id)).count(),
            "Selected project has one mandatory continuation");
        check(graph.edges.stream().anyMatch(e -> e.to.equals(gate) && e.label.contains("Invest")), "Project alternatives scoped to investment route");
        for (String name: List.of("Turn02_Personal_Funeral", "Turn02_A_MediaDeal")) {
            var branch = provenance.branch(item(data, name));
            equal(CausalProvenance.BranchKind.OPTIONAL_EVENT, branch.kind(), "Real optional event proven: " + name);
            var ev = event(graph, name);
            String cid = sourceGate(graph, ev.id);
            var options = graph.edges.stream().filter(e -> e.from.equals(cid)).map(e -> node(graph, e.to)).toList();
            var enabling = options.stream().filter(n -> branch.enabling().stream().anyMatch(o -> n.text.contains(o.label()))).findFirst().orElseThrow();
            var skip = options.stream().filter(n -> branch.other().stream().anyMatch(o -> n.text.equals(o.label()))).findFirst().orElseThrow();
            equal(1L, graph.edges.stream().filter(e -> e.from.equals(enabling.id)).count(), "Enabling choice has no additional skip decision");
            check(graph.edges.stream().anyMatch(e -> e.from.equals(enabling.id) && e.to.equals(ev.id)), "Enabling choice goes directly to event");
            check(graph.edges.stream().anyMatch(e -> e.from.equals(skip.id) && node(graph, e.to).kind == Graph.Kind.JUNCTION),
                "Actual rejection/Do not attend preserved");
        }
        System.out.println("PASS: Agnolia, Economy coverage, complementary simplification, infrastructure H3/L1, Circas and Koronti real-data acceptance");
    }
    static Graph.Node node(Graph g, String id) {
        return g.nodes.stream().filter(n -> n.id.equals(id)).findFirst().orElseThrow();
    }
    static String sourceGate(Graph g, String event) {
        String option = g.edges.stream().filter(e -> e.to.equals(event)).findFirst().orElseThrow().from;
        return g.edges.stream().filter(e -> e.to.equals(option)).findFirst().orElseThrow().from;
    }
    static void writeAudit(String variable, long indexed, List<BonusBlock> blocks) throws Exception {
        long routes = blocks.stream().mapToLong(b -> b.routes().size()).sum();
        long partial = blocks.stream().flatMap(b -> b.routes().stream()).filter(r -> !r.proven()).count();
        long choices = blocks.stream().flatMap(b -> b.cells().stream()).filter(c -> c.region().expression().op().equals("trigger") &&
            c.region().expression().value().startsWith("[CHOICE]")).count();
        long states = blocks.stream().flatMap(b -> b.cells().stream()).filter(c -> !c.region().expression().variables().isEmpty()).count();
        long events = blocks.stream().flatMap(b -> b.cells().stream()).filter(c -> c.region().expression().op().equals("trigger") &&
            !c.region().expression().value().startsWith("[CHOICE]")).count();
        long taut = 0, factored = 0;
        for (var b: blocks) {
            var changes = b.sections().stream().flatMap(s -> s.root().transformations().stream()).distinct().toList();
            taut += changes.stream().filter(c -> c.category().contains("tautology")).count();
            factored += changes.stream().filter(c -> c.category().startsWith("factored")).count();
        }
        long refs = blocks.stream().mapToLong(b -> b.sections().stream().flatMap(s -> s.root().references().stream()).distinct().count()).sum();
        String report = "# " + variable + " source audit\n\n| Metric | Count |\n| --- | ---: |\n| Indexed writes | " + indexed + " |\n| Rendered independent blocks | " + blocks.size() + " |\n| Source routes (DAG segments, not enumerated playthroughs) | " + routes + " |\n| Player-choice trigger cells | " + choices + " |\n| State-condition cells | " + states + " |\n| Event/story/option trigger cells | " + events + " |\n| Partial source routes | " + partial + " |\n| Boolean tautology transformations | " + taut + " |\n| Factoring transformations | " + factored + " |\n| Retained distinct source references | " + refs + " |\n\nTransformations are counted distinctly per write. Partial routes retain their supported predicates and exact stopping boundaries; they are not complete reachability proofs. Source references include condition-free paths and are available in Source mode.\n";
        Files.createDirectories(Path.of("docs/final-validation"));
        Files.writeString(Path.of("docs/final-validation", ConditionLogic.basename(variable) + "-audit.md"), report);
        System.out.println(report);
    }
}
