package sordland;

import sordland.analysis.*;
import sordland.data.Domain.*;
import java.util.*;
import static sordland.TestSupport.*;
import static sordland.analysis.NumericVariableLayout.*;

final class RouteDagChecks {
    private static Entry node(int id, String guard, String script, int...targets) {
        var links = new ArrayList<Link>();
        for (int t: targets) links.add(new Link(new EntryKey(1, t), links.size(), "Normal", false));
        return new Entry(new EntryKey(1, id), 0, "", "Node " + id, "Ordinary dialogue", "", guard, script, "", links,
            Map.of());
    }
    private static Dataset data(List<Entry> nodes) {
        var entries = new LinkedHashMap<Integer, Entry>();
        nodes.forEach(n -> entries.put(n.key().dialogueId(), n));
        return new Dataset(List.of(), Map.of(1, new Conversation(1, "Fixture", entries)), List.of(), List.of());
    }
    private static BonusBlock block(Dataset data) {
        return build(new VariableAnalyzer(new VariableIndex(data)).analyze("BaseGame.X")).getFirst();
    }
    static void run(Dataset real) {
        var diamond = data(List.of(node(0, "BaseGame.P", "", 1, 2), node(1, "BaseGame.A", "", 3), node(2, "BaseGame.B",
            "", 3), node(3, "BaseGame.C", "BaseGame.X += 1")));
        var resolved = new NumericRouteResolver(diamond).resolve(new EntryKey(1, 3));
        var graph = resolved.graph();
        equal(2, graph.nodes().get(graph.target()).incoming().size(), "Diamond keeps two incoming arcs");
        check(resolved.paths().size()>1, "Diamond never collapses to a coalesced Path");
        equal(4, graph.nodes().size(), "Diamond shared prefix stored once");
        for (int mask = 0; mask<16; mask++) {
            var state = Map.of("BaseGame.P",(mask & 1) != 0, "BaseGame.A",(mask & 2) != 0, "BaseGame.B",(mask & 4) != 0,
                "BaseGame.C",(mask & 8) != 0);
            equal(state.get("BaseGame.P") &&(state.get("BaseGame.A") || state.get("BaseGame.B")) && state.get("BaseGame.C"),
                evaluate(graph, graph.target(), state, new HashMap<>()), "Retained graph truth matches original diamond topology");
        }
        check(resolved.paths().stream().noneMatch(p -> p.condition().op().equals("or")), "No whole-graph synthetic OR");
        var roots = new ArrayList<Entry>();
        var targets = new int[20];
        for (int i = 0; i<20; i++) targets[i] = i + 1;
        roots.add(node(0, "BaseGame.P", "", targets));
        for (int i = 1; i <= 20; i++) roots.add(node(i, "BaseGame.A" + i, "", 21));
        roots.add(node(21, "BaseGame.C", "BaseGame.X += 1"));
        var many = data(roots);
        var proof = new NumericRouteResolver(many).resolve(new EntryKey(1, 21));
        equal(20, proof.graph().nodes().get("1:21").incoming().size(), "More than twelve real alternatives retained");
        check(proof.graph().complete(), "Twenty branches do not become unknown");
        check(proof.graph().nodes().values().stream().allMatch(n -> n.knownCondition().known()), "Boundaries not encoded as unknown expressions");
        var manyBlock = block(many);
        equal(20d, manyBlock.sections().stream().mapToDouble(s -> s.root().columns()).max().orElseThrow(), "Twenty alternatives at one real fork stay horizontal; no false AND stacking");
        var partial = data(List.of(node(0, "BaseGame.A", "Mystery()", 2), node(1, "BaseGame.B", "", 2), node(2, "BaseGame.C",
            "BaseGame.X += 1")));
        var b = block(partial);
        var g = b.occurrence().guardProof().graph();
        check(!g.complete(), "Partial metadata separate");
        check(g.nodes().get("1:1").boundaries().isEmpty(), "Complete sibling unchanged");
        check(b.cells().stream().anyMatch(c -> c.region().expression().variables().contains("BaseGame.A")), "Known condition on partial branch visible");
        check(b.cells().stream().anyMatch(c -> c.region().expression().variables().contains("BaseGame.B")), "Complete branch condition visible");
        check(b.cells().stream().allMatch(c -> c.region().expression().known()), "No unknown boundary cell");
        var emptyPartial = block(data(List.of(node(0, "", "Mystery()", 1), node(1, "", "BaseGame.X += 1"))));
        check(emptyPartial.sections().stream().anyMatch(s -> s.cells().isEmpty()), "Condition-free partial route is metadata only");
        var budget = new NumericRouteResolver(many, 5).resolve(new EntryKey(1, 21)).graph();
        check(budget.boundaries().stream().anyMatch(x -> x.reason() == RouteProof.Reason.NODE_BUDGET), "Exact node budget boundary");
        check(budget.nodes().values().stream().anyMatch(n -> n.knownCondition().variables().contains("BaseGame.C")),
            "Budget preserves known suffix");
        check(budget.nodes().values().stream().allMatch(n -> n.knownCondition().known()), "Budget never replaces graph with unknown formula");
        var diamonds = new ArrayList<Entry>();
        int last = 0;
        for (int i = 0; i<24; i++) {
            int a = 3 * i, bn = a + 1, c = a + 2, next = a + 3;
            diamonds.add(node(a, "", "", bn, c));
            diamonds.add(node(bn, "BaseGame.A" + i, "", next));
            diamonds.add(node(c, "!BaseGame.A" + i, "", next));
            last = next;
        }
        diamonds.add(node(last, "BaseGame.C", "BaseGame.X += 1"));
        var stress = block(data(diamonds));
        equal(73, stress.occurrence().guardProof().graph().nodes().size(), "24 diamonds retain 73 nodes, not 2^24 walks");
        check(stress.cells().size() <= 100, "Linear rendered condition count for repeated diamonds");
        var siblings = block(data(List.of(node(0, "BaseGame.P", "", 1, 2), node(1, "BaseGame.A && BaseGame.B && BaseGame.C",
            "", 3), node(2, "BaseGame.A && BaseGame.B && BaseGame.D", "", 3), node(3, "", "BaseGame.X += 1"))));
        check(siblings.cells().stream().anyMatch(c -> c.region().references().size() == 2 && c.region().expression().variables().equals(Set.of("BaseGame.A"))),
            "Real sibling common condition factored with both references");
        for (var route: siblings.routes()) check(siblings.sections().stream().anyMatch(s -> s.root().references().contains(route.reference())),
            "Every original route represented after factoring");
        real(real);
        System.out.println("PASS: source DAG diamonds, 20 alternatives, local boundaries, linear stress, real sibling factoring, video and election geometry");
    }
    private static boolean evaluate(RouteProof.Graph graph, String id, Map<String, Boolean> state, Map<String, Boolean> memo) {
        if (memo.containsKey(id)) return memo.get(id);
        var node = graph.nodes().get(id);
        boolean result =(node.incoming().isEmpty() || node.incoming().stream().anyMatch(a -> evaluate(graph, a.upstream(),
            state, memo))) && eval(node.knownCondition(), state);
        memo.put(id, result);
        return result;
    }
    private static boolean eval(VariableSyntax.Expr e, Map<String, Boolean> state) {
        return switch (e.op()) {
            case "literal" -> Boolean.parseBoolean(e.value());
            case "var" -> state.get(e.value());
            case "==" -> eval(e.children().getFirst(), state) == eval(e.children().getLast(), state);
            case "and" -> e.children().stream().allMatch(c -> eval(c, state));
            case "or" -> e.children().stream().anyMatch(c -> eval(c, state));
            default -> throw new AssertionError(e);
        };
    }
    private static void real(Dataset data) {
        var index = new VariableIndex(data);
        var analyzer = new VariableAnalyzer(index);
        for (String name: List.of("BaseGame.AMorgnaWesCore", "BaseGame.Election_Ending_Vote")) {
            var analysis = analyzer.analyze(name);
            var blocks = build(analysis);
            equal(new LinkedHashSet<>(analysis.rules()).size(), blocks.size(), "Every independent real write remains a block");
            if (name.endsWith("Election_Ending_Vote")) {
                check(blocks.stream().anyMatch(b -> b.effect().text().equals("+1")) && blocks.stream().anyMatch(b -> b.effect().text().equals("+2")) &&
                    blocks.stream().anyMatch(b -> b.effect().text().equals("+3")), "Election retains independent +1/+2/+3 writes");
            }
            for (var b: blocks) {
                var proof = b.occurrence().guardProof();
                if (proof == null || proof.graph() == null) continue;
                var g = proof.graph();
                equal(g.segments().size(), b.routes().size(), "Labels backed by actual graph segments, not expression sections");
                for (int i = 0; i<b.routes().size(); i++) check(b.routes().get(i).label().startsWith(g.label(g.segments().get(i))),
                    "Real source route identity labels");
                for (var n: g.nodes().values()) {
                    if (data.entry(n.entry()) != null) equal(data.entry(n.entry()).condition(), n.originalCondition(),
                        "Exact original predicate retained");
                    for (var arc: n.incoming()) {
                        var edge = arc.edge();
                        check(data.entry(edge.from()).links().stream().anyMatch(l -> l.target().equals(edge.to()) &&
                            l.order() == edge.order() && l.priority().equals(edge.priority()) && l.connector() == edge.connector()),
                            "Every proof arc exists in JSON");
                    }
                }
                for (var c: b.cells()) check(c.region().expression().known(), "No unknown condition cells in huge real variables");
                check(b.sections().stream().allMatch(s -> s.root().columns() <= 12 && s.root().rows() <= 16), "Real geometry bounded by local predicates, not whole-route products");
            }
            System.out.println("DAG REAL " + name + " blocks=" + blocks.size() + " cells=" + blocks.stream().mapToInt(b -> b.cells().size()).sum() + " maxColumns=" + blocks.stream().flatMap(b -> b.sections().stream()).mapToDouble(s -> s.root().columns()).max().orElse(0));
            if (name.equals("BaseGame.AMorgnaWesCore")) {
                var video = blocks.stream().filter(b -> b.occurrence().source().identity().equals("Conversation 226 / Dialogue 1007")).findFirst().orElseThrow();
                equal("Gain block 11 (+1)", video.title(), "Exact MOV artificial-route area");
                check(video.occurrence().guardProof().graph().variables().containsAll(Set.of("BaseGame.RumburgIncident_DeclareWar",
                    "BaseGame.Wehlen_JointOperation", "BaseGame.Unrest_BFF_Happened")), "MOV visible condition fingerprint matches source");
                var huge = blocks.stream().filter(b -> b.title().equals("Gain block 16 (+1)")).findFirst().orElseThrow();
                equal("Conversation 244 / Dialogue 1082", huge.occurrence().source().identity(), "Exact MOV huge-white-cell area");
                check(huge.occurrence().guardProof().graph().variables().containsAll(Set.of("BaseGame.Turn09_UnrestStopped",
                    "BaseGame.Bill_Turn07_LanguageBill_Signed")), "MOV Unrest/Language Bill fingerprint retained");
                check(huge.sections().stream().allMatch(s -> s.root().columns() <= 6 && s.root().rows() <= 4), "MOV huge-cell block now only bounded local alternatives");
                equal("Conversation 240 / Dialogue 201", blocks.stream().filter(b -> b.title().equals("Gain block 14 (+1)")).findFirst().orElseThrow().occurrence().source().identity(),
                    "MOV Gain block 14 fingerprint");
            }
        }
    }
}
