package sordland;

import sordland.data.*;
import sordland.data.Domain.*;
import sordland.data.runtime.*;
import sordland.graph.*;
import sordland.graph.Graph.*;
import sordland.layout.*;
import sordland.layout.LayoutEngine.*;
import java.nio.file.*;
import java.util.*;
import static sordland.TestSupport.*;

final class RuntimeChecks {
    static void run(Path path, Dataset data) throws Exception {
        RuntimeDatabase db = data.runtime();
        Map<String, Integer> counts = Map.ofEntries(Map.entry("pageddecisionpanelsdata", 8), Map.entry("carouselchoicepagedata",
            17), Map.entry("carouselchoiceoptiondata", 55), Map.entry("multiplechoicepagedata", 24), Map.entry("multiplechoiceoptiondata",
            64), Map.entry("policiesdata", 164), Map.entry("situationsdata", 228), Map.entry("reportsdata", 862),
            Map.entry("decreesdata", 19), Map.entry("journalentriesdata", 561), Map.entry("tokenstatuseffectsdata",
            106));
        for (var expected: counts.entrySet()) equal(expected.getValue(), db.collection(expected.getKey()).size(),
            "Sordland runtime count " + expected.getKey());
        equal(162, Json.list(db.catalog.get("members")).size(), "All catalog members preserved");
        for (var collection: db.collections.entrySet()) for (var entity: collection.getValue()) {
            check(entity.storyPacks().contains("StoryPack_Main"), "Only exact main story pack enters runtime index");
            check(!entity.graphEligible() || !entity.path().startsWith("Rizia/"), "Conflicting Rizia paths cannot enter gameplay graphs");
            check(db.byName.get(entity.name()).contains(entity), "Exact name indexed");
            check(entity.id().isBlank() || db.byId.get(entity.id()).contains(entity), "Exact Id indexed");
            var source = Json.object(db.sourceFiles.get(entity.file()));
            equal(RuntimeDatabase.values(source.get("data")).get(entity.index()), entity.raw(), "Complete raw object and original index retained");
            var preferred = Json.list(db.catalog.get("members")).stream().map(Json::object).filter(m -> entity.collection().equals(Json.string(m,
                "logicalKey")) && Boolean.TRUE.equals(m.get("preferredForCoverage"))).toList();
            equal(1, preferred.size(), "One preferred member");
            equal(Json.string(preferred.getFirst(), "outputFile"), entity.file(), "Catalog outputFile selected");
        }
        var panel = db.resolve("Panel_Budget", "pageddecisionpanelsdata");
        check(panel != null, "Exact Budget panel resolves");
        equal("BaseGame.GovernmentBudget", panel.value("PanelCounterVariable"), "Counter provenance");
        var entry = data.entry(new EntryKey(26, 209));
        check(entry != null, "26:209 exists");
        check(Semantics.analyze(entry.script(), entry.sequence()).commands().stream().anyMatch(c -> c.kind() == Semantics.CommandKind.PANEL &&
            "Panel_Budget".equals(PanelGraphBuilder.panelName(c.raw()))), "Structured panel command");
        equal(1, entry.links().size(), "Budget panel source has one outgoing source link");
        equal(new EntryKey(27, 1), entry.links().getFirst().target(), "Budget panel source enters conversation 27 input");
        var intoPanel = data.conversations().get(26).entries().values().stream().filter(e -> e.links().stream().anyMatch(l -> l.target().equals(new EntryKey(26,
            209)))).toList();
        equal(1, intoPanel.size(), "Only one source entry enters Panel_Budget");
        equal(new EntryKey(26, 158), intoPanel.getFirst().key(), "I turned the first page is the unique panel predecessor");
        long exitsFrom26 = data.conversations().get(26).entries().values().stream().flatMap(e -> e.links().stream()).filter(l -> l.target().conversationId() != 26).count();
        equal(1L, exitsFrom26, "Conversation 26 has one cross-conversation progress exit");
        Entry input27 = data.entry(new EntryKey(27, 1));
        equal(1, input27.links().size(), "Post-budget input has one outgoing source link");
        equal(new EntryKey(27, 81), input27.links().getFirst().target(), "Post-budget input reaches the budget-complete narrator line");
        long narratorMatches = data.conversations().get(27).entries().values().stream().filter(e -> e.text().startsWith("I had finished allocating the government budget")).count();
        equal(1L, narratorMatches, "Budget-complete narrator line appears once in conversation 27");
        Map<String, int[]> budgets = Map.of("Education", new int[] {
            2, 0, -1, -3, 0, 2
        }, "Healthcare", new int[] {
            1, 0, -2, -2, 0, 3
        }, "Security", new int[] {
            1, 0, -1, -3, 0, 2
        }, "Military", new int[] {
            -1, 0, -2, -4, 0, 3
        });
        for (var area: budgets.entrySet()) for (int i = 0; i<3; i++) {
            String choice = List.of("Increase", "Maintain", "Decrease").get(i), name = "Option_Budget_" + area.getKey() + "_" + choice;
            var option = db.resolve(name, "carouselchoiceoptiondata");
            check(option != null, "Exact option " + name);
            String flag = "BaseGame.Turn03_EnT_" +(area.getKey().equals("Security") ? "LawEnforcement": area.getKey()) + "_Budget_" + choice + " = true";
            check(option.value("Instruction").contains(flag), "Exact boolean flag " + name);
            int economy = area.getValue()[i];
            String instruction = option.value("Instruction");
            if (economy == 0) check(!instruction.contains("BaseGame.Economy"), "No fake economy on Maintain");
            else check(instruction.contains("BaseGame.Economy " +(economy>0 ? "+= ": "-= ") + Math.abs(economy)),
                "Exact Economy delta " + name);
            equal(area.getValue()[i + 3],((Number) option.properties().get("PanelCounterIncrement")).intValue(), "Counter delta " + name);
        }
        Graph graph = new DialogueGraphBuilder().build(data, data.conversations().get(26));
        PanelGroup group = graph.panels.stream().filter(p -> graph.nodes.stream().anyMatch(n -> n.id.equals(p.entryId()) &&
            n.item != null && n.item.internalName().equals("Panel_Budget"))).findFirst().orElseThrow();
        equal(4, group.categories().size(), "Four funding sets");
        equal(30, group.members().size(), "One entry, four headers, twelve choices, twelve effects, one completion");
        var ids = new HashMap<String, Node>();
        graph.nodes.forEach(n -> ids.put(n.id, n));
        for (var category: group.categories()) {
            equal(3, category.branches().size(), "Three choices per funding set");
            for (var branch: category.branches()) {
                Node choice = ids.get(branch.choiceId()), effect = ids.get(branch.effectId());
                check(Set.of("Increase", "Maintain", "Decrease").stream().anyMatch(x -> choice.title.equalsIgnoreCase(x)),
                    "Exact budget choice title retained");
                check(choice.kind == Kind.CHOICE, "Budget option is a distinct choice card");
                check(effect.kind == Kind.EFFECT, "Immediate consequences are a distinct effect card");
                check(effect.metadata.contains("PanelCounterVariable (panel source): BaseGame.GovernmentBudget"),
                    "Panel source retained");
                check(effect.metadata.contains("PanelCounterIncrement (option source):"), "Counter source retained");
                check(effect.metadata.contains("Instruction") && effect.metadata.contains("Condition"), "Raw instruction and condition retained");
                equal(1L, graph.edges.stream().filter(e -> e.from.equals(choice.id) && e.to.equals(effect.id)).count(),
                    "Choice flows to its immediate effect");
                equal(1L, graph.edges.stream().filter(e -> e.from.equals(effect.id) && e.to.equals(group.completionId())).count(),
                    "Every immediate effect reconverges at Budget chosen");
            }
        }
        var continuation = graph.edges.stream().filter(e -> new EntryKey(26, 209).equals(e.sourceFrom)).toList();
        equal(1, continuation.size(), "One exact Budget continuation");
        equal(new EntryKey(27, 1), continuation.getFirst().sourceTo, "Exact continuation destination");
        equal(group.completionId(), continuation.getFirst().from, "Continuation leaves panel completion");
        for (var area: budgets.entrySet()) for (int i = 0; i<3; i++) {
            String choice = List.of("Increase", "Maintain", "Decrease").get(i), name = "Option_Budget_" + area.getKey() + "_" + choice;
            Node card = graph.nodes.stream().filter(n -> n.kind == Kind.EFFECT && n.item != null && n.item.internalName().equals(name)).findFirst().orElseThrow();
            String flag =(area.getKey().equals("Security") ? "LawEnforcement": area.getKey()) + "Budget" + choice + " = true";
            check(card.text.contains(flag), "Rendered exact choice flag " + name);
            int eco = area.getValue()[i], counter = area.getValue()[i + 3];
            if (eco == 0) check(!card.text.contains("Economy"), "Rendered Maintain has no Economy change");
            else check(card.text.contains("Economy " +(eco>0 ? "+": "") + eco), "Rendered Economy delta " + name);
            if (counter == 0) check(!card.text.contains("GovernmentBudget"), "Zero counter stays in source without visual noise");
            else check(card.text.contains("GovernmentBudget " +(counter>0 ? "+": "") + counter), "Rendered GovernmentBudget delta " + name);
        }
        var layout = new LayoutEngine().dialogue(graph, Set.of(), RuntimeChecks::measure);
        geometry(layout, group);
        var projected = ActorProjection.project(graph, Set.of());
        equal(graph.panels, projected.panels, "Actor filter retains panel mechanics");
        geometry(new LayoutEngine().dialogue(projected, Set.of(), RuntimeChecks::measure), group);
        var expanded = new LayoutEngine().dialogue(graph, Set.of(group.categories().getFirst().branches().getFirst().effectId()),
            RuntimeChecks::measure);
        geometry(expanded, group);
        int graphs = 0;
        for (Conversation c: data.conversations().values()) {
            Graph g = new DialogueGraphBuilder().build(data, c);
            graphs++;
            var map = new HashMap<String, Node>();
            g.nodes.forEach(n -> check(map.put(n.id, n) == null, "Unique enriched node id"));
            var seen = new HashSet<EntryKey>();
            g.nodes.stream().map(n -> n.source).filter(Objects::nonNull).forEach(seen::add);
            for (EntryKey key: seen) {
                Entry e = data.entry(key);
                if (e == null) continue;
                var links = g.edges.stream().filter(edge -> key.equals(edge.sourceFrom)).toList();
                equal(e.links().size(), links.size(), "Runtime exact link cardinality " + key);
                for (Link l: e.links()) equal(1L, links.stream().filter(edge -> edge.sourceLink == l).count(), "Exact Link identity retained once");
            }
            for (Edge edge: g.edges) check(map.containsKey(edge.from) && map.containsKey(edge.to), "Runtime edge endpoints exist");
        }
        equal(286, graphs, "All runtime-enriched Sordland graphs audited");
        var plain = new CampaignGraphBuilder().build(data, "", null, null, true);
        for (String type: RuntimeDatabase.TYPES.values()) {
            check(TypeProjection.types(plain).contains(type), "Runtime PLAIN type " + type);
            check(!TypeProjection.defaults(TypeProjection.types(plain)).contains(type), "Runtime default off " + type);
            check(!new TypeSelection().selectedFor(List.of(type)).contains(type), "Remembered selection default off");
        }
        var rooted = new RootedCampaignGraphBuilder().build(data, null);
        check(!rooted.campaign.runtime().isEmpty(), "Exact runtime evidence enters ROOTED");
        System.out.println("Runtime ROOTED types: " + rooted.nodes.stream().filter(n -> n.item != null && n.item.id().startsWith("runtime:")).map(n -> n.type).distinct().sorted().toList());
        var defaultGraph = TypeProjection.project(rooted, TypeProjection.defaults(TypeProjection.types(rooted)));
        check(defaultGraph.campaign.runtime().isEmpty(), "Runtime annotations hidden by default");
        new LayoutEngine().campaign(defaultGraph, Set.of(), RuntimeChecks::measure);
        new LayoutEngine().campaign(rooted, Set.of(), RuntimeChecks::measure);
        check(RuntimeGraphBuilder.evidence(db, "if BaseGame.X then BaseGame.Policy_Diplomacy_Agnolia_Alliance = true; end").isEmpty(),
            "No speculative evidence inside unsupported control flow");
        check(RuntimeGraphBuilder.evidence(db, "BaseGame.Unrelated = true").isEmpty(), "No variable co-occurrence links");
        check(!RuntimeGraphBuilder.evidence(db, "BaseGame.Policy_Diplomacy_Agnolia_Alliance = true").isEmpty(), "Exact enabled variable resolves");
        check(!RuntimeGraphBuilder.evidence(db, "AddTokenStatus(\"Sordland_City_Lachaven\", \"Progress_Highway\")").isEmpty(),
            "Exact token status argument resolves");
        check(RuntimeGraphBuilder.evidence(db, "AddReport(\"not_a_report\")").isEmpty(), "No fuzzy report match");
        check(plain.nodes.stream().filter(n -> n.item != null && n.item.id().startsWith("runtime:")).noneMatch(n -> n.item.path().startsWith("Rizia/")),
            "No conflicting Rizia record rendered in PLAIN");
        check(rooted.nodes.stream().filter(n -> n.item != null && n.item.id().startsWith("runtime:")).noneMatch(n -> n.item.path().startsWith("Rizia/")),
            "No Rizia record rendered in ROOTED");
        brokenFixtures(db, panel);
        System.out.println("Runtime counts: " + counts + "; 286 enriched dialogue graphs, exact Budget effects/continuation and panel geometry PASS.");
    }
    static Size measure(Node n, boolean expanded) {
        if (n.kind == Kind.JUNCTION) return new Size(2, 2, List.of(), List.of(), List.of());
        double width = n.type.equals("Panel effect") ? 210: n.type.equals("Panel choice") ? 150: n.type.equals("Panel header") ? 240: 300;
        return new Size(expanded ? 480: width, expanded ? 900: 90 + 18 * n.text.lines().count(), List.of(n.title),
            List.of(n.text), List.of());
    }
    private static void geometry(Result layout, PanelGroup panel) {
        Group group = layout.groups.stream().filter(g -> g.id().equals(panel.id())).findFirst().orElseThrow();
        check(group.width()>1000, "Budget outer enclosure remains a wide horizontal ministry block");
        var headers = panel.categories().stream().map(c -> layout.byId.get(c.headerId())).toList();
        equal(1L, headers.stream().map(Box::y).distinct().count(), "All four ministries are aligned on one row");
        for (int i = 1; i<headers.size(); i++) check(headers.get(i - 1).cx()<headers.get(i).cx(), "Ministry order stays left-to-right");
        for (Box header: headers) {
            check(header.x() >= group.x() && header.x() + header.w() <= group.x() + group.width() + 0.01, "Header inside ministry rectangle");
            check(header.y() >= group.y() && header.bottom() <= group.bottom() + 0.01, "Header vertically inside ministry rectangle");
        }
        double effectBottom = group.y();
        for (var c: panel.categories()) {
            Box header = layout.byId.get(c.headerId());
            var branches = new ArrayList<>(c.branches());
            branches.sort(Comparator.comparingDouble(b -> layout.byId.get(b.choiceId()).cx()));
            equal(3, branches.size(), "Three choices below each ministry");
            Box decrease = layout.byId.get(branches.get(0).choiceId());
            Box maintain = layout.byId.get(branches.get(1).choiceId());
            Box increase = layout.byId.get(branches.get(2).choiceId());
            check(decrease.node().title.equalsIgnoreCase("Decrease") && maintain.node().title.equalsIgnoreCase("Maintain") &&
                increase.node().title.equalsIgnoreCase("Increase"), "Visual order is Decrease | Maintain | Increase");
            equal(1L, branches.stream().map(b -> layout.byId.get(b.choiceId()).y()).distinct().count(), "Three choices share one row");
            check(branches.stream().allMatch(b -> layout.byId.get(b.choiceId()).y()> header.bottom()), "Choices branch downward from ministry");
            equal(header.cx(), maintain.cx(), "Maintain stays centered under ministry");
            for (var b: branches) {
                Box choice = layout.byId.get(b.choiceId()), effect = layout.byId.get(b.effectId());
                equal(choice.cx(), effect.cx(), "Immediate effect stays directly below its option");
                check(effect.y()> choice.bottom(), "Immediate effect is below option");
                effectBottom = Math.max(effectBottom, effect.bottom());
            }
        }
        Box completion = layout.byId.get(panel.completionId());
        check(completion.node().title.equals("Budget chosen"), "Visible Budget chosen merge node");
        check(completion.y()> effectBottom, "Budget chosen is below every immediate effect");
        equal(group.cx(), completion.cx(), "Budget chosen is centered under the ministry rectangle");
        check(completion.bottom()<group.bottom(), "Budget chosen remains inside the full Budget rectangle");
        equal(12L, layout.lines.stream().filter(l -> l.edge().to.equals(panel.completionId())).count(), "All twelve effect alternatives merge into Budget chosen");
        for (String id: panel.members()) {
            Box member = layout.byId.get(id);
            check(member != null, "Every Budget member has geometry");
            check(member.x() >= group.x() -.01 && member.x() + member.w() <= group.x() + group.width() +.01, "Every Budget member stays horizontally inside the outer rectangle");
            check(member.y() >= group.y() -.01 && member.bottom() <= group.bottom() +.01, "Every Budget member stays vertically inside the outer rectangle");
        }
        for (int i = 0; i<layout.boxes.size(); i++) for (int j = i + 1; j<layout.boxes.size(); j++) {
            Box a = layout.boxes.get(i), b = layout.boxes.get(j);
            check(!(a.x()<b.x() + b.w() && a.x() + a.w()> b.x() && a.y()<b.bottom() && a.bottom()> b.y()), "No overlapping enriched boxes");
        }
        for (Line line: layout.lines) for (Box box: layout.boxes) {
            if (box.node().id.equals(line.edge().from) || box.node().id.equals(line.edge().to)) continue;
            for (int i = 1; i<line.points().size(); i++) {
                Point a = line.points().get(i - 1), b = line.points().get(i);
                boolean through = a.x() == b.x() ? a.x()> box.x() +.01 && a.x()<box.x() + box.w() -.01 && Math.max(a.y(),
                    b.y())> box.y() +.01 && Math.min(a.y(), b.y())<box.bottom() -.01: a.y() == b.y() && a.y()> box.y() +.01 &&
                    a.y()<box.bottom() -.01 && Math.max(a.x(), b.x())> box.x() +.01 && Math.min(a.x(), b.x())<box.x() + box.w() -.01;
                check(!through, "No routed segment crosses unrelated card: " + line.edge().from + " -> " + line.edge().to + " across " + box.node().id);
            }
        }
        long incoming = layout.lines.stream().filter(line -> line.edge().to.equals(panel.entryId())).count();
        long outgoing = layout.lines.stream().filter(line -> line.edge().from.equals(panel.completionId()) && !panel.members().contains(line.edge().to)).count();
        equal(1L, incoming, "Exactly one real Part A source edge enters the Budget gateway");
        equal(1L, outgoing, "Exactly one real source continuation leaves the Budget gateway");
        for (Line line: layout.lines) if (line.edge().to.equals(panel.entryId())) {
            Point end = line.points().getLast();
            equal(group.y(), end.y(), "Part A enters outer Budget rectangle top");
            equal(group.cx(), end.x(), "Part A gathers at upper middle");
            check(line.from().bottom() <= group.y(), "Every Part A predecessor stays above the Budget rectangle");
        } else if (line.edge().from.equals(panel.completionId()) && !panel.members().contains(line.edge().to)) {
            check(line.points().stream().anyMatch(p -> Math.abs(p.x() - group.cx())<.01 && Math.abs(p.y() - group.bottom())<.01),
                "Part B continuation passes through the lower-middle Budget boundary");
            check(line.to().y() >= group.bottom(), "Part B starts below the full Budget rectangle");
        }
        for (Line line: layout.lines) {
            boolean internal = panel.members().contains(line.edge().from) && panel.members().contains(line.edge().to);
            boolean boundary = line.edge().to.equals(panel.entryId()) || line.edge().from.equals(panel.completionId());
            if (internal || boundary) continue;
            boolean opposite =(line.from().bottom()<group.y() && line.to().y()> group.bottom()) ||(line.to().bottom()<group.y() &&
                line.from().y()> group.bottom());
            check(!opposite, "No source edge geometrically bypasses the Budget gateway");
            for (int i = 1; i<line.points().size(); i++) {
                Point a = line.points().get(i - 1), b = line.points().get(i);
                boolean through = a.x() == b.x() ? a.x()> group.x() +.01 && a.x()<group.x() + group.width() -.01 &&
                    Math.max(a.y(), b.y())> group.y() +.01 && Math.min(a.y(), b.y())<group.bottom() -.01: a.y() == b.y() &&
                    a.y()> group.y() +.01 && a.y()<group.bottom() -.01 && Math.max(a.x(), b.x())> group.x() +.01 &&
                    Math.min(a.x(), b.x())<group.x() + group.width() -.01;
                check(!through, "No unrelated connector enters the Budget rectangle");
            }
        }
        equal(group.y(), layout.byId.get(panel.entryId()).y(), "Hidden input at rectangle top");
        equal(group.cx(), layout.byId.get(panel.entryId()).cx(), "Hidden input at rectangle upper middle");
    }
    private static void brokenFixtures(RuntimeDatabase db, RuntimeDatabase.Entity panel) throws Exception {
        var nodes = new ArrayList<Node>();
        var edges = new ArrayList<Edge>();
        var groups = new ArrayList<PanelGroup>();
        var notes = new ArrayList<String>();
        PanelGraphBuilder.append(RuntimeDatabase.empty(), "ShowPagedDecisionsPanel(\"Panel_Budget\")", "missing",
            new EntryKey(26, 209), nodes, edges, groups, notes);
        check(!notes.isEmpty() && nodes.getFirst().kind == Kind.NOTICE, "Missing runtime explicit diagnostic");
        var broken = new RuntimeDatabase(Map.of("pageddecisionpanelsdata", List.of(panel)), db.catalog, db.sourceFiles,
            List.of());
        nodes.clear();
        notes.clear();
        PanelGraphBuilder.append(broken, "ShowPagedDecisionsPanel(\"Panel_Budget\")", "broken", null, nodes, edges,
            groups, notes);
        equal(4L, nodes.stream().filter(n -> n.kind == Kind.NOTICE).count(), "All missing pages retained as diagnostics");
        var noOptions = new RuntimeDatabase(Map.of("pageddecisionpanelsdata", List.of(panel), "carouselchoicepagedata",
            db.collection("carouselchoicepagedata")), db.catalog, db.sourceFiles, List.of());
        nodes.clear();
        notes.clear();
        edges.clear();
        groups.clear();
        PanelGraphBuilder.append(noOptions, "ShowPagedDecisionsPanel(\"Panel_Budget\")", "brokenOptions", null, nodes,
            edges, groups, notes);
        equal(12L, nodes.stream().filter(n -> n.kind == Kind.NOTICE).count(), "All missing options retained");
        Path absent = Files.createTempDirectory("sordland-runtime-test-");
        try {
            var empty = RuntimeDatabaseLoader.load(absent);
            check(empty.collections.isEmpty() && !empty.diagnostics.isEmpty(), "Runtime absence graceful");
        } finally {
            Files.delete(absent);
        }
        for (var p: db.collection("pageddecisionpanelsdata")) {
            nodes.clear();
            edges.clear();
            groups.clear();
            notes.clear();
            PanelGraphBuilder.append(db, "ShowPagedDecisionsPanel(\"" + p.name() + "\")", "panel", null, nodes, edges,
                groups, notes);
            check(!groups.isEmpty(), "Generic panel resolves " + p.name());
            for (Node n: nodes) if (n.kind == Kind.CONDITION) {
                equal(1L, edges.stream().filter(e -> e.from.equals(n.id) && e.label.equals("TRUE")).count(), "Condition TRUE only");
                check(edges.stream().noneMatch(e -> e.from.equals(n.id) && e.label.equals("FALSE")), "No invented false destination");
            }
        }
    }
}
