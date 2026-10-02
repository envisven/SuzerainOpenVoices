package sordland.graph;

import sordland.data.Domain.*;
import sordland.data.runtime.RuntimeDatabase;
import sordland.data.runtime.RuntimeDatabase.Entity;
import sordland.graph.Graph.*;
import java.util.*;
import java.util.regex.Pattern;

public final class RuntimeGraphBuilder {
    private static final Pattern WRITE = Pattern.compile("^([A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)+)\\s*=\\s*(true|false)$");
    private static final Pattern CALL = Pattern.compile("^(AddReport|AddJournalEntry|AddTokenStatus|RemoveTokenStatus|ShowOneTimeDecreesPanel)\\s*\\((.*)\\)$");
    private static final Pattern STRING = Pattern.compile("\\s*([\"'])([^\"'\\\\]+)\\1\\s*");
    public record Evidence(Entity entity, String statement, String proof) {
    }
    public static List<Evidence> evidence(RuntimeDatabase db, String script) {
        var out = new ArrayList<Evidence>();
        var analysis = Semantics.analyze(script, "");
        for (var command: analysis.commands()) {
            if (command.kind() == Semantics.CommandKind.TERMINAL) break;
            String raw = command.raw();
            var write = WRITE.matcher(raw);
            if (command.kind() == Semantics.CommandKind.EFFECT && write.matches()) for (Entity e: db.byEnabledVariable.getOrDefault(write.group(1),
                List.of())) out.add(new Evidence(e, raw, "Exact IsEnabledVariable write: " + write.group(1) + " = " + write.group(2)));
            var call = CALL.matcher(raw);
            if (!call.matches()) continue;
            String[] args = call.group(2).split(",", -1);
            var strings = new ArrayList<String>();
            boolean literal = true;
            for (String arg: args) {
                var m = STRING.matcher(arg);
                if (!m.matches()) {
                    literal = false;
                    break;
                }
                strings.add(m.group(2));
            }
            if (!literal) continue;
            String function = call.group(1), collection = switch (function) {
                case "AddReport" -> "reportsdata";
                case "AddJournalEntry" -> "journalentriesdata";
                case "ShowOneTimeDecreesPanel" -> "onetimedecreespaneldata";
                default -> "tokenstatuseffectsdata";
            };
            int index = function.equals("AddTokenStatus") || function.equals("RemoveTokenStatus") ? 1: 0;
            if (strings.size() != index + 1) continue;
            Entity entity = db.resolve(strings.get(index), collection);
            if (entity != null && entity.graphEligible()) {
                if (function.equals("ShowOneTimeDecreesPanel")) {
                    for (Entity decree: db.collection("decreesdata")) if (decree.graphEligible() && decree.value("AssignedDecreePanel").equals(entity.name())) out.add(new Evidence(decree,
                        raw, "Exact DecreeProperties.AssignedDecreePanel = " + entity.name() + "; available panel member, not guaranteed enactment."));
                } else out.add(new Evidence(entity, raw, "Exact " + function + " argument " + index + " = NameInDatabase: " + entity.name()));
            }
        }
        return out;
    }
    public Graph attach(Dataset data, Graph graph) {
        if (data.runtime().collections.isEmpty()) return graph;
        var nodes = new ArrayList<>(graph.nodes);
        var edges = new ArrayList<>(graph.edges);
        var attachments = new ArrayList<NewsAttachment>();
        var notes = new ArrayList<>(graph.diagnostics);
        for (Node node: graph.nodes) if (node.kind == Kind.EVENT && node.item != null && !node.item.id().startsWith("runtime:")) {
            Item item = node.item;
            add(data.runtime(), node, "begin", item.beginInstruction(), item.condition(), null, nodes, edges, attachments,
                notes);
            add(data.runtime(), node, "end", item.endInstruction(), item.condition(), null, nodes, edges, attachments,
                notes);
            for (int i = 0; i<item.options().size(); i++) {
                Option o = item.options().get(i);
                add(data.runtime(), node, "option-" + i, o.instruction(), o.condition(), null, nodes, edges, attachments,
                    notes);
            }
            if (item.conversationId() != null) {
                Conversation c = data.conversations().get(item.conversationId());
                if (c == null) continue;
                var pending = new ArrayDeque<EntryKey>();
                var seen = new HashSet<EntryKey>();
                c.entries().values().stream().filter(e -> e.title().equalsIgnoreCase("START")).forEach(e -> pending.add(e.key()));
                while (!pending.isEmpty()) {
                    EntryKey key = pending.remove();
                    if (!seen.add(key)) continue;
                    Entry e = data.entry(key);
                    if (e == null) continue;
                    add(data.runtime(), node, "entry-" + key + "-script", e.script(), e.condition(), key, nodes, edges,
                        attachments, notes);
                    add(data.runtime(), node, "entry-" + key + "-sequence", e.sequence(), e.condition(), key, nodes,
                        edges, attachments, notes);
                    for (Link link: e.links()) pending.add(link.target());
                }
            }
        }
        var byId = new HashMap<String, Node>();
        graph.nodes.forEach(n -> byId.put(n.id, n));
        for (var visual: graph.campaign.turns()) {
            Turn turn = data.gameFlow().turns().stream().filter(t -> t.sourceIndex() == visual.sourceIndex()).findFirst().orElse(null);
            Node anchor = byId.get(visual.entryId());
            if (turn == null || anchor == null) continue;
            add(data.runtime(), anchor, "runtime-turn-" + turn.sourceIndex(), turn.onTurnStartInstruction(), turn.condition(),
                null, nodes, edges, attachments, notes);
            for (var level: graph.campaign.levels()) if (level.turnSourceIndex() == turn.sourceIndex()) {
                Step step = turn.steps().stream().filter(t -> t.sourceIndex() == level.stepIndex()).findFirst().orElse(null);
                Node start = byId.get(level.entryId());
                if (step != null && start != null) add(data.runtime(), start, "runtime-step-" + turn.sourceIndex() + "-" + step.sourceIndex(),
                    step.onStepStartInstruction(), turn.condition(), null, nodes, edges, attachments, notes);
            }
        }
        notes.add("Runtime exact source evidence: " + attachments.size() + " local annotations. Optional routes, predicates and instruction phases are retained; no campaign reader/writer edges inferred.");
        return new Graph(graph.title, nodes, edges, notes, new CampaignMetadata(graph.campaign.turns(), graph.campaign.levels(),
            graph.campaign.groups(), graph.campaign.news(), attachments));
    }
    private static void add(RuntimeDatabase db, Node anchor, String phase, String script, String condition, EntryKey key,
        List<Node> nodes, List<Edge> edges, List<NewsAttachment> attachments, List<String> notes) {
        var proofs = evidence(db, script);
        int i = 0;
        for (var command: Semantics.analyze(script, "").commands()) if (CALL.matcher(command.raw()).matches() &&
            evidence(db, command.raw()).isEmpty()) notes.add("Unresolved runtime content reference at " + anchor.id + " / " + phase + ": " + command.raw() + ". No unambiguous exact Sordland entity/argument signature; original instruction retained in source details.");
        for (Evidence proof: proofs) {
            Entity e = proof.entity();
            String id = anchor.id + ":runtime:" + phase + ":" + i++, effect = id + ":effect";
            String type = RuntimeDatabase.TYPES.get(e.collection());
            String metadata = "Possible source effect; source path must execute. No guaranteed outcome or publication turn is inferred.\nSource phase: " + phase + "\nSource entry: " + key + "\nCondition (original): " + condition + "\nInstruction (original): " + script + "\nProof: " + proof.proof() + "\n" + e.metadata();
            nodes.add(new Node(effect, Kind.EFFECT, "POSSIBLE " + type.toUpperCase(Locale.ROOT) + " EFFECT", Semantics.conditionDisplay(proof.statement()),
                metadata, type, "", anchor.turn, null, key));
            nodes.add(new Node(id, Kind.EVENT, type.toUpperCase(Locale.ROOT) + " · " + e.title(), e.name(), metadata,
                type, "", anchor.turn, e.item(), key));
            edges.add(new Edge(anchor.id, effect, "possible source effect" +(key == null ? "": " · " + key), false));
            edges.add(new Edge(effect, id, "exact source relationship", false));
            attachments.add(new NewsAttachment(anchor.id, effect, List.of(id)));
        }
    }
}
