package sordland;

import sordland.data.Domain.*;
import sordland.graph.*;
import java.util.*;
import static sordland.TestSupport.*;

final class RootedCampaignChecks {
    private RootedCampaignChecks() {
    }
    static void run(Dataset actual) {
        Graph graph = new RootedCampaignGraphBuilder().build(actual, null);
        check(graph.campaign != null, "Rooted campaign declares explicit progression layout metadata");
        equal("START", graph.nodes.getFirst().title, "Continuous campaign starts with synthetic START");
        var expected = new ArrayList<String>();
        actual.gameFlow().turns().forEach(t -> t.steps().forEach(s -> s.fragments().forEach(f -> expected.add(f.name()))));
        equal(expected, graph.nodes.stream().filter(n -> n.kind == Graph.Kind.EVENT && !n.type.equals("News") ||
            n.title.equals("UNRESOLVED FRAGMENT")).map(n -> n.text).toList(), "Every source fragment occurrence appears once in exact turn/step/fragment order");
        Graph.Node inauguration = event(graph, "Turn01_Start_Inauguration");
        check(hasEdge(graph, "campaign:start", inauguration.id), "Inauguration is immediately reachable from START");
        Graph.Node extraction = event(graph, "Turn03_Decision_Extraction");
        var targets = List.of("Turn03_A_AddressTheProtestors", "Turn03_Personal_HelicopterEscape");
        var next = graph.campaign.levels().stream().filter(l -> l.turn() == 3 && l.stepIndex() == 5).findFirst().orElseThrow();
        equal(targets, next.eventIds().stream().map(id -> node(graph, id).item.internalName()).toList(), "Gasom alternatives retain next-step source order");
        for (String target: targets) {
            Graph.Node destination = event(graph, target);
            var condition = graph.nodes.stream().filter(n -> n.kind == Graph.Kind.CONDITION && hasEdge(graph, n.id,
                destination.id)).findFirst().orElseThrow();
            check(hasEdge(graph, extraction.id, condition.id), "Exact adjacent Gasom option proves Extraction → condition parent");
            check(condition.metadata.contains("Exact adjacent decision proof:"), "Specific parent proof retains exact option assignment");
            check(condition.metadata.contains(destination.item.condition()), "Condition metadata preserves original BaseGame expression");
            check(!hasEdge(graph, extraction.id, destination.id), "Gasom alternative never bypasses its condition");
            check(hasEdge(graph, destination.id, next.exitId()), "Gasom alternatives reconverge at the same local exit");
        }
        equal(1, next.conditionIds().size(), "Exhaustive Gasom source choice shares one activation split");
        check(graph.edges.stream().noneMatch(e -> node(graph, e.from).kind == Graph.Kind.EVENT && node(graph, e.to).kind == Graph.Kind.EVENT),
            "GameFlow order never creates guessed event-to-event causality");
        equal(11, graph.campaign.turns().size(), "All source turns remain chapters in one graph");
        check(graph.campaign.turns().stream().anyMatch(t -> t.turn() == 3 && t.title().equals("Trials of Democracy")),
            "TransitionTitle labels source turn separators");
        assertAcyclicAndReachable(graph);
        Graph isolated = new RootedCampaignGraphBuilder().build(actual, 3);
        equal("TURN START", isolated.nodes.getFirst().title, "Isolated turn has its own synthetic root");
        check(isolated.nodes.stream().allMatch(n -> Objects.equals(3, n.turn)), "Turn filter restricts occurrences and mechanics to the selected GameFlow turn");
        assertAcyclicAndReachable(isolated);
        Graph repeat = new RootedCampaignGraphBuilder().build(actual, null);
        equal(graph.nodes.stream().map(n -> n.id).toList(), repeat.nodes.stream().map(n -> n.id).toList(), "Repeated builds preserve occurrence ordering");
        equal(graph.edges.stream().map(e -> e.from + "→" + e.to + ":" + e.label).toList(), repeat.edges.stream().map(e -> e.from + "→" + e.to + ":" + e.label).toList(),
            "Repeated builds preserve connector ordering");
        fixtures();
    }
    private static void fixtures() {
        Item a = item("A", "", List.of()), b = item("B", "", List.of()), c = item("C", "", List.of());
        Graph sibling = build(step(0, a, b, c));
        equal(1, sibling.campaign.groups().size(), "Multiple unconditional same-level events form exactly one visual sibling container");
        var group = sibling.campaign.groups().getFirst();
        equal(3, group.eventIds().size(), "Sibling group retains all independently interactive event IDs");
        equal(1L, sibling.edges.stream().filter(e -> e.to.equals(group.entryId())).count(), "One logical incoming connector reaches sibling group");
        equal(1L, sibling.edges.stream().filter(e -> e.from.equals(group.exitId())).count(), "One logical outgoing connector leaves sibling group");
        assertAcyclicAndReachable(sibling);
        equal(0, build(step(0, a)).campaign.groups().size(), "Singleton event does not create an aesthetic group");
        Item yes = item("Yes", "BaseGame.X == true", List.of()), no = item("No", "( BaseGame.X == false )", List.of());
        Graph complementary = build(step(0, yes, no));
        equal(1, complementary.campaign.levels().getFirst().conditionIds().size(), "Identical boolean variables with opposite literals share one condition");
        String cid = complementary.campaign.levels().getFirst().conditionIds().getFirst();
        equal(List.of("TRUE", "FALSE"), complementary.edges.stream().filter(e -> e.from.equals(cid)).map(e -> e.label).toList(),
            "Mechanical boolean complement split labels both predicate outcomes");
        check(node(complementary, cid).metadata.contains(no.condition()), "Merged condition retains raw complementary source expression");
        Graph optional = build(step(0, yes), step(1, a));
        var optionalLevel = optional.campaign.levels().getFirst();
        String optionalGate = optionalLevel.conditionIds().getFirst();
        check(optional.edges.stream().anyMatch(e -> e.from.equals(optionalGate) && e.to.equals(event(optional, "Yes").id) &&
            e.label.equals("TRUE")), "Standalone activation has explicit TRUE execution route");
        check(optional.edges.stream().noneMatch(e -> e.from.equals(optionalGate) && e.to.equals(optionalLevel.exitId())),
            "A standalone activation predicate does not prove a skip destination");
        check(hasEdge(optional, event(optional, "Yes").id, optionalLevel.exitId()), "Proven event path reaches its level exit");
        check(node(optional, optionalGate).metadata.contains("Opposite outcome unresolved"), "Missing skip proof is disclosed; real optional choices are covered separately");
        assertAcyclicAndReachable(optional);
        check(!RootedCampaignGraphBuilder.complements("BaseGame.X == 1", "BaseGame.X == 2"), "Numeric alternatives are not claimed to be exhaustive");
        check(!RootedCampaignGraphBuilder.complements("BaseGame.X == true", "Other.X == false"), "Same suffix in different namespaces never proves complementarity");
        check(!RootedCampaignGraphBuilder.complements("BaseGame.X == true and BaseGame.Y == true", "BaseGame.X == false and BaseGame.Y == true"),
            "Compound conditions are not rewritten using guessed domains");
        Graph numeric = build(step(0, item("One", "BaseGame.X == 1", List.of()), item("Two", "BaseGame.X == 2", List.of()),
            item("Three", "BaseGame.X == 3", List.of())));
        equal(3, numeric.campaign.levels().getFirst().conditionIds().size(), "Noncomplementary numeric candidates keep independent condition blocks");
        Graph mixed = build(step(0, a, yes, b, no));
        equal(1, mixed.campaign.groups().size(), "Mixed level contains only one unconditional subgroup");
        equal(List.of("A", "B"), mixed.campaign.groups().getFirst().eventIds().stream().map(id -> node(mixed, id).text).toList(),
            "Conditional events remain outside sibling container");
        equal(1, mixed.campaign.levels().getFirst().conditionIds().size(), "Mixed level retains complementary condition branch alongside group");
        assertAcyclicAndReachable(mixed);
        var missing = new Fragment(0, "Exact_Missing_Fragment", null, "No exact entity match");
        Graph unresolved = build(new Step(4, "", List.of(missing, new Fragment(1, "A", a, ""), new Fragment(2, "A",
            a, "")), Map.of()));
        check(unresolved.nodes.stream().anyMatch(n -> n.title.equals("UNRESOLVED FRAGMENT") && n.text.equals(missing.name())),
            "Missing reference is visible at its own occurrence");
        check(unresolved.diagnostics.stream().anyMatch(d -> d.contains(missing.name()) && d.contains("step 4")), "Missing-reference diagnostic identifies source location and exact ID");
        equal(2L, unresolved.nodes.stream().filter(n -> n.item == a).count(), "Repeated source fragment occurrences never deduplicate away");
        assertAcyclicAndReachable(unresolved);
        Turn gated = new Turn(3, "BaseGame.Ready == true", "Chapter<br>Four", "BaseGame.TurnStarted = true;", List.of(new Step(5,
            "BaseGame.StepStarted = true;", List.of(new Fragment(0, "A", a, "")), Map.of())), Map.of());
        Graph turnGate = new RootedCampaignGraphBuilder().build(dataset(List.of(gated)), 4);
        Graph.Node gate = turnGate.nodes.stream().filter(n -> n.type.equals("Turn condition")).findFirst().orElseThrow();
        check(hasEdge(turnGate, "campaign:start", gate.id) && hasEdge(turnGate, gate.id, event(turnGate, "A").id),
            "Turn activation gate applies before the first source fragment");
        check(event(turnGate, "A").metadata.contains(gated.onTurnStartInstruction()) && event(turnGate, "A").metadata.contains("BaseGame.StepStarted = true;"),
            "Turn and step instructions stay intact in source details");
        equal("Chapter Four", turnGate.campaign.turns().getFirst().title(), "Visual chapter title normalizes source HTML line breaks");
        assertAcyclicAndReachable(turnGate);
        Graph.Node unknownFalse = turnGate.nodes.stream().filter(n -> n.title.equals("FALSE DESTINATION UNRESOLVED")).findFirst().orElseThrow();
        check(turnGate.edges.stream().anyMatch(e -> e.from.equals(gate.id) && e.to.equals(unknownFalse.id) && e.label.equals("FALSE")),
            "Turn condition false semantics stay explicitly unresolved without inventing a later-turn destination");
        check(turnGate.edges.stream().noneMatch(e -> e.from.equals(unknownFalse.id)), "Unresolved false notice has no invented onward pointer");
        Item choice = item("Choice", "", List.of(new Option("yes", "", "BaseGame.X = true;"), new Option("no", "",
            "BaseGame.X = false;")));
        Graph direct = build(step(0, choice), step(1, yes, no));
        check(hasEdge(direct, event(direct, "Choice").id, direct.campaign.levels().get(1).conditionIds().getFirst()),
            "Exact adjacent decision assignments prove a specific branch");
        equal(1L, direct.edges.stream().filter(e -> e.from.equals(event(direct, "Choice").id)).count(), "Proven if/else decision has one split and no redundant bypass trunk");
        assertAcyclicAndReachable(direct);
        Graph distant = build(step(0, choice), step(1, a), step(2, yes, no));
        check(distant.edges.stream().noneMatch(e -> e.from.equals(event(distant, "Choice").id) && node(distant, e.to).kind == Graph.Kind.CONDITION),
            "Old shared variable writes never produce long-distance causality");
        Graph ambiguous = build(step(0, choice, b), step(1, yes, no));
        check(ambiguous.edges.stream().noneMatch(e -> e.from.equals(event(ambiguous, "Choice").id) && node(ambiguous,
            e.to).kind == Graph.Kind.CONDITION), "Ambiguous multi-event parent step uses neutral transition");
        Graph intervening = build(step(0, choice), new Step(1, "BaseGame.X = true;", step(1, yes, no).fragments(),
            Map.of()));
        check(intervening.edges.stream().noneMatch(e -> e.from.equals(event(intervening, "Choice").id) && node(intervening,
            e.to).kind == Graph.Kind.CONDITION), "Intervening step instruction invalidates specific decision proof");
        Item complex = item("Complex", "", List.of(new Option("yes", "", "if BaseGame.Ready then BaseGame.X = true; end"),
            new Option("no", "", "BaseGame.X = false;")));
        Graph unsafe = build(step(0, complex), step(1, yes, no));
        check(unsafe.edges.stream().noneMatch(e -> e.from.equals(event(unsafe, "Complex").id) && node(unsafe, e.to).kind == Graph.Kind.CONDITION),
            "Assignments hidden in unsupported option control flow cannot prove a parent");
        Item hiddenCall = item("HiddenCall", "", List.of(new Option("yes", "", "BaseGame.X = true; BaseGame.Y = MutateState();"),
            new Option("no", "", "BaseGame.X = false;")));
        Graph indirect = build(step(0, hiddenCall), step(1, yes, no));
        check(indirect.edges.stream().noneMatch(e -> e.from.equals(event(indirect, "HiddenCall").id) && node(indirect,
            e.to).kind == Graph.Kind.CONDITION), "Calls inside assignment expressions cannot prove a safe parent");
        Turn emptyTurn = new Turn(0, "BaseGame.Ready == true", "Empty chapter", "BaseGame.Started = true;", List.of(),
            Map.of());
        Turn populatedTurn = new Turn(1, "", "Next chapter", "", List.of(step(0, a)), Map.of());
        Graph leadingEmpty = new RootedCampaignGraphBuilder().build(dataset(List.of(emptyTurn, populatedTurn)), null);
        Graph.Node emptyGate = leadingEmpty.nodes.stream().filter(n -> n.type.equals("Turn condition")).findFirst().orElseThrow();
        Graph.Node emptyNotice = leadingEmpty.nodes.stream().filter(n -> n.title.equals("EMPTY GAMEFLOW TURN")).findFirst().orElseThrow();
        check(hasEdge(leadingEmpty, emptyGate.id, emptyNotice.id), "Empty source turn still displays its activation gate");
        check(hasEdge(leadingEmpty, emptyNotice.id, leadingEmpty.campaign.levels().getFirst().entryId()), "A leading empty turn connects continuously into the later populated turn");
        check(!hasEdge(leadingEmpty, "campaign:start", event(leadingEmpty, "A").id), "Later first fragment never jumps around a preceding empty turn");
        assertAcyclicAndReachable(leadingEmpty);
        Graph emptyStep = build(new Step(0, "BaseGame.EmptyStepStarted = true;", List.of(), Map.of()), step(1, a));
        check(emptyStep.nodes.stream().anyMatch(n -> n.title.equals("EMPTY GAMEFLOW STEP") && n.metadata.contains("BaseGame.EmptyStepStarted = true;")),
            "Empty step remains inspectable with its source start instruction");
        assertAcyclicAndReachable(emptyStep);
    }
    private static Graph build(Step...steps) {
        return new RootedCampaignGraphBuilder().build(dataset(List.of(new Turn(0, "", "Test chapter", "", List.of(steps),
            Map.of()))), null);
    }
    private static Dataset dataset(List<Turn> turns) {
        return new Dataset(List.of(), Map.of(), List.of(), List.of(), new GameFlow("StoryPack_Main", 0, turns, Map.of()));
    }
    private static Step step(int index, Item...items) {
        var fragments = new ArrayList<Fragment>();
        for (int i = 0; i<items.length; i++) fragments.add(new Fragment(i, items[i].internalName(), items[i], ""));
        return new Step(index, "", fragments, Map.of());
    }
    private static Item item(String name, String condition, List<Option> options) {
        return new Item(name, options.isEmpty() ? "Conversation": "Decision", name, name, "Sordland/Test", 1, null,
            condition, "", "", "", options, Map.of());
    }
    private static Graph.Node event(Graph graph, String name) {
        return graph.nodes.stream().filter(n -> n.item != null && n.item.internalName().equals(name)).findFirst().orElseThrow();
    }
    private static Graph.Node node(Graph graph, String id) {
        return graph.nodes.stream().filter(n -> n.id.equals(id)).findFirst().orElseThrow();
    }
    private static boolean hasEdge(Graph graph, String from, String to) {
        return graph.edges.stream().anyMatch(e -> e.from.equals(from) &&(e.to.equals(to) || node(graph, e.to).kind == Graph.Kind.CHOICE &&
            graph.edges.stream().anyMatch(next -> next.from.equals(e.to) && next.to.equals(to))));
    }
    private static void assertAcyclicAndReachable(Graph graph) {
        var degrees = new LinkedHashMap<String, Integer>();
        var outgoing = new HashMap<String, List<String>>();
        for (Graph.Node n: graph.nodes) check(degrees.put(n.id, 0) == null, "Campaign occurrence node IDs are unique");
        for (Graph.Edge e: graph.edges) {
            check(degrees.containsKey(e.from) && degrees.containsKey(e.to), "Every campaign connector endpoint resolves");
            check(!e.back, "Rooted GameFlow progression contains no backward link");
            degrees.merge(e.to, 1, Integer::sum);
            outgoing.computeIfAbsent(e.from, k -> new ArrayList<>()).add(e.to);
        }
        var queue = new ArrayDeque<String>();
        degrees.forEach((id, d) -> {
            if (d == 0) queue.add(id);
        });
        equal(List.of("campaign:start"), List.copyOf(queue), "Continuous rooted campaign has exactly one graph root");
        int count = 0;
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            count++;
            for (String target: outgoing.getOrDefault(id, List.of())) if (degrees.merge(target, -1, Integer::sum) == 0) queue.add(target);
        }
        equal(graph.nodes.size(), count, "Every source occurrence and layout mechanic is reachable in an acyclic graph");
    }
}
