package sordland;

import sordland.data.Domain.*;
import sordland.graph.*;
import java.util.*;
import static sordland.TestSupport.*;

final class ActorFilterChecks {
    private ActorFilterChecks() {
    }
    static void run() {
        membership();
        linearBypass();
        mechanics();
        branches();
        hiddenRootsAndCycles();
        largeReconvergentGraph();
    }
    public static void main(String[] ignored) {
        run();
        System.out.println("Actor projection checks passed: " + TestSupport.count());
    }
    private static void membership() {
        var nodes = List.of(speech("p", Graph.Kind.CHOICE, "Player"), speech("pi", Graph.Kind.CHOICE, "Player_Italic"),
            speech("n", Graph.Kind.NARRATOR, "Narrator"), speech("z", Graph.Kind.CHARACTER, "Zeta"), speech("a", Graph.Kind.CHARACTER,
            "Alpha"), speech("a2", Graph.Kind.CHARACTER, "Alpha"), mechanic("fx", Graph.Kind.EFFECT, "Lucian Galade"));
        Graph graph = graph(nodes, List.of());
        equal(List.of("You", "Narrator", "Alpha", "Zeta"), ActorProjection.actors(graph), "Actor options merge both player roles, deduplicate names and sort source actors");
        check(ActorProjection.project(graph, new HashSet<>(ActorProjection.actors(graph))) == graph, "All selected returns the canonical graph unchanged");
        equal(List.of("Alpha"), ActorProjection.actors(graph(List.of(speech("a", Graph.Kind.CHARACTER, "Alpha")),
            List.of())), "Unusual graphs do not force absent You or Narrator options");
        equal(List.of(), ActorProjection.actors(graph(List.of(mechanic("e", Graph.Kind.EFFECT, "Petr Vectern")), List.of())),
            "An effect's source speaker is not actor-filter membership");
        Graph filtered = ActorProjection.project(graph, Set.of("Alpha"));
        equal(ActorProjection.actors(graph), List.of("You", "Narrator", "Alpha", "Zeta"), "Canonical actor membership survives visual filtering");
        check(filtered.nodes.stream().noneMatch(n -> n.kind == Graph.Kind.CHOICE || n.kind == Graph.Kind.NARRATOR),
            "Both player speech styles and narrator speech can be hidden");
    }
    private static void linearBypass() {
        NodeList list = new NodeList();
        var a = list.add(speech("a", Graph.Kind.CHARACTER, "Petr Vectern"));
        var h = list.add(speech("h", Graph.Kind.CHARACTER, "Lucian Galade"));
        var h2 = list.add(speech("h2", Graph.Kind.CHARACTER, "Lucian Galade"));
        var b = list.add(mechanic("b", Graph.Kind.EFFECT, "Lucian Galade"));
        var edges = List.of(edge(a, h, "Choice 1", 0), edge(h, h2, "priority High", 0), edge(h2, b, "source link after End()",
            0));
        Graph canonical = graph(list.nodes, edges);
        Graph p = ActorProjection.project(canonical, Set.of("Petr Vectern"));
        equal(List.of("a", "b"), p.nodes.stream().map(n -> n.id).toList(), "Hidden linear speech disappears completely");
        equal(1, p.edges.size(), "A hidden speech chain is replaced by one connector");
        Graph.Edge e = p.edges.getFirst();
        equal("a", e.from, "Projection starts at original visible node");
        equal("b", e.to, "Projection ends at original visible node");
        equal(edges, e.projectionPath, "Projected connector retains every original source edge in path order");
        check(e.sourceFrom == null && e.sourceTo == null && e.sourceLink == null, "A bypass is not misrepresented as an exact JSON link");
        check(e.label.indexOf("Choice 1")<e.label.indexOf("priority High") && e.label.indexOf("priority High")<e.label.indexOf("source link after End()"),
            "Source path labels retain order on a bypass");
        check(e.label.contains("hidden speech (Lucian Galade)"), "Bypass is explicitly labeled as hidden speech");
        check(p.nodes.getFirst() == a && p.nodes.getLast() == b, "Visible source boxes are retained by identity");
        equal(4, canonical.nodes.size(), "Projection never mutates canonical nodes");
        equal(3, canonical.edges.size(), "Projection never mutates canonical links");
        assertEndpoints(p);
    }
    private static void mechanics() {
        List<Graph.Node> nodes = List.of(mechanic("c", Graph.Kind.CONDITION, "Lucian Galade"), speech("h", Graph.Kind.CHARACTER,
            "Lucian Galade"), mechanic("e", Graph.Kind.EFFECT, "Lucian Galade"), mechanic("n", Graph.Kind.NOTICE,
            "Lucian Galade"), mechanic("control", Graph.Kind.CONTROL, "Lucian Galade"), mechanic("terminal", Graph.Kind.TERMINAL,
            "Lucian Galade"), mechanic("reference", Graph.Kind.REFERENCE, "Lucian Galade"));
        var edges = new ArrayList<Graph.Edge>();
        for (int i = 0; i<nodes.size() - 1; i++) edges.add(edge(nodes.get(i), nodes.get(i + 1), "", 0));
        Graph p = ActorProjection.project(graph(nodes, edges), Set.of());
        equal(nodes.stream().filter(n -> !n.id.equals("h")).toList(), p.nodes, "Hiding an actor keeps every condition/effect/notice/control/terminal/reference box");
        check(p.edges.stream().anyMatch(e -> e.from.equals("c") && e.to.equals("e") && e.projectionPath.size() == 2),
            "Condition connects directly to effect after only speech is hidden");
        check(p.edges.stream().anyMatch(e -> e == edges.getLast()), "Unaffected exact-source edges preserve identity and metadata");
        assertEndpoints(p);
    }
    private static void branches() {
        var start = mechanic("start", Graph.Kind.CONTROL, "");
        var split = speech("split", Graph.Kind.CHARACTER, "Lucian Galade");
        var one = speech("one", Graph.Kind.CHOICE, "Player");
        var two = speech("two", Graph.Kind.CHOICE, "Player_Italic");
        var a = mechanic("a", Graph.Kind.EFFECT, "");
        var b = mechanic("b", Graph.Kind.EFFECT, "");
        var edges = List.of(edge(start, split, "", 0), edge(split, one, "Choice 1", 0), edge(split, two, "Choice 2",
            1), edge(one, a, "", 0), edge(two, b, "", 0));
        Graph p = ActorProjection.project(graph(List.of(start, split, one, two, a, b), edges), Set.of());
        Graph.Node junction = p.nodes.stream().filter(n -> n.kind == Graph.Kind.REFERENCE).findFirst().orElseThrow();
        equal("HIDDEN SPEECH JUNCTION", junction.title, "Hidden branching speech becomes an explicit compact reference");
        check(junction.text.contains("Lucian Galade") && !junction.text.contains("spoken contents"), "Hidden junction shows actor and role but no original speech");
        check(junction.metadata.contains("NOT AN ADDITIONAL SOURCE MECHANIC"), "Inspector explains synthetic projection junction");
        var choices = p.edges.stream().filter(e -> e.from.equals(junction.id)).toList();
        equal(List.of("a", "b"), choices.stream().map(e -> e.to).toList(), "Projected hidden choices preserve left-to-right source branch order");
        check(choices.get(0).label.startsWith("Choice 1") && choices.get(1).label.startsWith("Choice 2"), "Hidden choice numbers remain on their ordered edges");
        equal(List.of(0, 1), choices.stream().map(e -> e.projectionPath.getFirst().sourceLink.order()).toList(), "Projected choices retain source link ordinal evidence");
        check(p.nodes.stream().noneMatch(n -> n.kind == Graph.Kind.CHARACTER || n.kind == Graph.Kind.CHOICE), "No unchecked actor speech box survives as speech");
        assertEndpoints(p);
    }
    private static void hiddenRootsAndCycles() {
        var root = speech("root", Graph.Kind.CHARACTER, "Lucian Galade");
        var end = mechanic("end", Graph.Kind.TERMINAL, "");
        Graph p = ActorProjection.project(graph(List.of(root, end), List.of(edge(root, end, "Choice 1", 0))), Set.of());
        check(p.nodes.stream().anyMatch(n -> n.kind == Graph.Kind.REFERENCE && n.text.contains("source root")), "A hidden initial speech root keeps an inspectable source boundary");
        check(p.edges.getFirst().label.contains("Choice 1"), "Hidden source root does not discard its outgoing source label");
        assertEndpoints(p);
        var a = speech("a", Graph.Kind.CHARACTER, "Lucian Galade");
        var b = speech("b", Graph.Kind.CHARACTER, "Petr Vectern");
        var originals = List.of(edge(a, b, "next", 0), edge(b, a, "return", 0));
        Graph cycle = ActorProjection.project(graph(List.of(a, b), originals), Set.of());
        equal(1, cycle.nodes.size(), "A closed all-hidden cycle keeps exactly one compact anchor");
        equal(1, cycle.edges.size(), "A closed hidden cycle does not expand recursively");
        Graph.Edge loop = cycle.edges.getFirst();
        check(loop.back && loop.from.equals(loop.to), "All-hidden cycle remains a classified inspectable loop");
        equal(originals, loop.projectionPath, "Hidden loop preserves the complete exact-source edge cycle");
        check(cycle.nodes.getFirst().text.contains("closed source loop"), "Hidden cycle is visibly identified rather than silently lost");
        assertEndpoints(cycle);
        var visible = mechanic("visible", Graph.Kind.CONTROL, "");
        var hidden = speech("hidden", Graph.Kind.CHARACTER, "Lucian Galade");
        Graph returned = ActorProjection.project(graph(List.of(visible, hidden), List.of(edge(visible, hidden, "out",
            0), edge(hidden, visible, "back", 0))), Set.of());
        equal(List.of(visible), returned.nodes, "Visible-hidden loop needs no redundant junction");
        check(returned.edges.getFirst().back, "Projection reclassifies newly formed self-loop before layout");
    }
    private static void largeReconvergentGraph() {
        var nodes = new ArrayList<Graph.Node>();
        var edges = new ArrayList<Graph.Edge>();
        var previous = speech("r0", Graph.Kind.CHARACTER, "Lucian Galade");
        nodes.add(previous);
        for (int i = 0; i<40; i++) {
            var a = speech("a" + i, Graph.Kind.CHOICE, "Player");
            var b = speech("b" + i, Graph.Kind.CHOICE, "Player_Italic");
            var join = speech("r" +(i + 1), Graph.Kind.CHARACTER, "Lucian Galade");
            nodes.add(a);
            nodes.add(b);
            nodes.add(join);
            edges.add(edge(previous, a, "Choice 1", 0));
            edges.add(edge(previous, b, "Choice 2", 1));
            edges.add(edge(a, join, "", 0));
            edges.add(edge(b, join, "", 0));
            previous = join;
        }
        Graph p = ActorProjection.project(graph(nodes, edges), Set.of());
        check(p.nodes.size() <= nodes.size() && p.edges.size() <= edges.size(), "Forty reconverging diamonds remain bounded instead of enumerating 2^40 hidden routes");
        check(p.nodes.stream().allMatch(n -> n.kind == Graph.Kind.REFERENCE), "Fully hidden branching graph contains only explicit topology references");
        check(p.edges.stream().allMatch(e -> !e.projectionPath.isEmpty()), "Every projected branch retains original edge evidence");
        assertEndpoints(p);
    }
    private static Graph.Node speech(String id, Graph.Kind kind, String name) {
        return new Graph.Node(id, kind, kind == Graph.Kind.CHOICE ? "YOU": name, "spoken contents for " + id, "original metadata " + id,
            "Dialogue", name, null, null, key(id));
    }
    private static Graph.Node mechanic(String id, Graph.Kind kind, String sourceSpeaker) {
        return new Graph.Node(id, kind, kind.name(), "source mechanics " + id, "original metadata " + id, "Dialogue",
            sourceSpeaker, null, null, key(id));
    }
    private static EntryKey key(String id) {
        return new EntryKey(100, id.hashCode() & 0x7fffffff);
    }
    private static Graph.Edge edge(Graph.Node a, Graph.Node b, String label, int order) {
        return new Graph.Edge(a.id, b.id, label, false, a.source, new Link(b.source, order, "Normal", false));
    }
    private static Graph graph(List<Graph.Node> nodes, List<Graph.Edge> edges) {
        return new Graph("Synthetic source", nodes, edges, List.of());
    }
    private static void assertEndpoints(Graph graph) {
        Set<String> ids = new HashSet<>();
        graph.nodes.forEach(n -> ids.add(n.id));
        for (Graph.Edge e: graph.edges) check(ids.contains(e.from) && ids.contains(e.to), "Every projected connector endpoint is rendered");
    }
    private static final class NodeList {
        final List<Graph.Node> nodes = new ArrayList<>();
        Graph.Node add(Graph.Node node) {
            nodes.add(node);
            return node;
        }
    }
}
