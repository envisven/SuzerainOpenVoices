package sordland.graph;

import sordland.data.Domain.*;
import sordland.data.Json;
import sordland.graph.Graph.*;
import java.util.*;
import java.util.regex.Pattern;

public final class NewsGraphBuilder {
    private static final String IDENT = "[A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)*";
    private static final Pattern DIRECT_WRITE = Pattern.compile("^(" + IDENT + ")\\s*([+*/%\\-]?=)(?!=)\\s*(.+)$",
        Pattern.DOTALL);
    private static final Pattern VARIABLE_WRITE = Pattern.compile("^Variable\\s*\\[\\s*([\"'])(" + IDENT + ")\\1\\s*\\]\\s*([+*/%\\-]?=)(?!=)\\s*(.+)$",
        Pattern.DOTALL);
    private static final Pattern ENABLE = Pattern.compile("^EnableNews\\s*\\(\\s*([\"'])([^\"'\\\\]+)\\1\\s*\\)$");
    private static final Pattern RHS_CALL = Pattern.compile("\\b[A-Za-z_]\\w*\\s*\\(");
    public record Evidence(Item news, String statement, String proof) {
    }
    public static List<Evidence> evidence(String source, List<Item> news) {
        Map<String, List<Item>>variables = new LinkedHashMap<>(), names = new LinkedHashMap<>();
        for (Item item: news) {
            String variable = Json.string(Json.object(item.raw().get("NewsProperties")), "IsEnabledVariable");
            if (!variable.isBlank()) variables.computeIfAbsent(variable, k -> new ArrayList<>()).add(item);
            names.computeIfAbsent(item.internalName(), k -> new ArrayList<>()).add(item);
        }
        return evidence(source, variables, names);
    }
    private static List<Evidence> evidence(String source, Map<String, List<Item>>variables, Map<String, List<Item>>names) {
        var analysis = Semantics.analyze(instructionNewlines(source), "");
        if (!analysis.unknown().isEmpty()) return List.of();
        for (var command: analysis.commands()) {
            var direct = DIRECT_WRITE.matcher(command.raw());
            var bracket = VARIABLE_WRITE.matcher(command.raw());
            String rhs = bracket.matches() ? bracket.group(4): direct.matches() ? direct.group(3): "";
            if (RHS_CALL.matcher(rhs).find()) return List.of();
        }
        var proven = new LinkedHashMap<String, Evidence>();
        for (var command: analysis.commands()) {
            if (command.kind() == Semantics.CommandKind.TERMINAL) break;
            if (command.kind() != Semantics.CommandKind.EFFECT) continue;
            String raw = command.raw().trim();
            var direct = DIRECT_WRITE.matcher(raw);
            var bracket = VARIABLE_WRITE.matcher(raw);
            var call = ENABLE.matcher(raw);
            String variable = null, value = null, operator = null;
            if (bracket.matches()) {
                variable = bracket.group(2);
                operator = bracket.group(3);
                value = bracket.group(4).trim();
            } else if (direct.matches()) {
                variable = direct.group(1);
                operator = direct.group(2);
                value = direct.group(3).trim();
            }
            if (variable != null) for (Item item: variables.getOrDefault(variable, List.of())) {
                proven.remove(item.id());
                if (operator.equals("=") && value.equals("true")) proven.put(item.id(), new Evidence(item, raw, "Exact write to NewsProperties.IsEnabledVariable: " + variable));
            }
            if (call.matches()) {
                var matches = names.getOrDefault(call.group(2), List.of());
                if (matches.size() == 1) {
                    Item item = matches.getFirst();
                    proven.put(item.id(), new Evidence(item, raw, "Exact EnableNews argument equals NewsData.NameInDatabase: " + call.group(2)));
                }
            }
        }
        return List.copyOf(proven.values());
    }
    public Graph attach(Dataset data, Graph graph) {
        if (graph.campaign == null) throw new IllegalArgumentException("News annotations require campaign metadata");
        List<Item> news = data.news();
        if (news.isEmpty()) return graph;
        var variables = new LinkedHashMap<String, List<Item>>();
        var names = new LinkedHashMap<String, List<Item>>();
        for (Item item: news) {
            String variable = Json.string(Json.object(item.raw().get("NewsProperties")), "IsEnabledVariable");
            if (!variable.isBlank()) variables.computeIfAbsent(variable, k -> new ArrayList<>()).add(item);
            names.computeIfAbsent(item.internalName(), k -> new ArrayList<>()).add(item);
        }
        var nodes = new ArrayList<>(graph.nodes);
        var edges = new ArrayList<>(graph.edges);
        var diagnostics = new ArrayList<>(graph.diagnostics);
        var attachments = new ArrayList<>(graph.campaign.news());
        var represented = new LinkedHashSet<String>();
        Map<String, Node> byId = new LinkedHashMap<>();
        graph.nodes.forEach(n -> byId.put(n.id, n));
        var context = new Context(nodes, edges, attachments, represented, variables, names);
        for (Node event: graph.nodes) if (event.item != null && event.kind == Kind.EVENT && !event.type.equals("News")) {
            Item item = event.item;
            context.add(event, "begin", item.beginInstruction(), "StoryFragmentProperties.OnStoryFragmentBeginInstruction",
                false, null, item.condition());
            context.add(event, "end", item.endInstruction(), "StoryFragmentProperties.OnStoryFragmentEndInstruction",
                false, null, item.condition());
            for (int i = 0; i<item.options().size(); i++) {
                Option option = item.options().get(i);
                context.add(event, "option-" + i, option.instruction(), "Option " + i + ": " + option.title(), true,
                    null, option.condition());
            }
            if (item.conversationId() != null) {
                Conversation conversation = data.conversations().get(item.conversationId());
                if (conversation != null) for (Entry entry: reachableEntries(data, conversation)) {
                    String provenance = "Conversation " + entry.key().conversationId() + ", dialogue entry " + entry.key().dialogueId() + " (" + entry.title() + ")";
                    context.add(event, "entry-" + entry.key().conversationId() + "-" + entry.key().dialogueId() + "-script",
                        entry.script(), provenance + " · userScript", true, entry.key(), entry.condition());
                    context.add(event, "entry-" + entry.key().conversationId() + "-" + entry.key().dialogueId() + "-sequence",
                        entry.sequence(), provenance + " · Sequence", true, entry.key(), entry.condition());
                }
            }
        }
        for (var visualTurn: graph.campaign.turns()) {
            Turn turn = data.gameFlow().turns().stream().filter(t -> t.sourceIndex() == visualTurn.sourceIndex()).findFirst().orElse(null);
            if (turn == null) continue;
            Node anchor = inTurn(afterTurnGate(graph, byId.get(visualTurn.entryId())), turn.turnNumber());
            if (anchor != null) context.add(anchor, "turn-" + turn.sourceIndex(), turn.onTurnStartInstruction(), "GameFlow Turn[" + turn.sourceIndex() + "].OnTurnStartInstruction",
                false, null, turn.condition());
            for (var level: graph.campaign.levels()) if (level.turnSourceIndex() == turn.sourceIndex()) {
                Step step = turn.steps().stream().filter(s -> s.sourceIndex() == level.stepIndex()).findFirst().orElse(null);
                if (step != null) {
                    Node stepAnchor = inTurn(afterTurnGate(graph, byId.get(level.entryId())), turn.turnNumber());
                    if (stepAnchor != null) context.add(stepAnchor, "turn-" + turn.sourceIndex() + "-step-" + step.sourceIndex(),
                        step.onStepStartInstruction(), "GameFlow Turn[" + turn.sourceIndex() + "].Step[" + step.sourceIndex() + "].OnStepStartInstruction",
                        false, null, turn.condition());
                }
            }
        }
        diagnostics.add("News source evidence: " + represented.size() + " distinct articles represented by " + attachments.size() + " exact effect annotations. Optional dialogue/choice annotations describe source possibilities, never guaranteed event completion or newspaper publication time.");
        List<String> unattached = news.stream().filter(n -> !represented.contains(n.id())).map(Item::internalName).toList();
        if (!unattached.isEmpty()) diagnostics.add("News without a proven enabler in this rooted selection remains available in PLAIN: " + String.join(", ",
            unattached));
        return new Graph(graph.title, nodes, edges, diagnostics, new CampaignMetadata(graph.campaign.turns(), graph.campaign.levels(),
            graph.campaign.groups(), attachments));
    }
    private static Node inTurn(Node node, int turn) {
        return node == null ? null: new Node(node.id, node.kind, node.title, node.text, node.metadata, node.type,
            node.speaker, turn, node.item, node.source, node.actor);
    }
    private static Node afterTurnGate(Graph graph, Node anchor) {
        if (anchor == null) return null;
        return graph.edges.stream().filter(e -> e.from.equals(anchor.id)).map(e -> graph.nodes.stream().filter(n -> n.id.equals(e.to) &&
            n.type.equals("Turn condition")).findFirst().orElse(null)).filter(Objects::nonNull).findFirst().orElse(anchor);
    }
    private static List<Entry> reachableEntries(Dataset data, Conversation conversation) {
        var pending = new ArrayDeque<EntryKey>();
        conversation.entries().values().stream().filter(e -> e.title().trim().equalsIgnoreCase("START")).forEach(e -> pending.add(e.key()));
        var seen = new LinkedHashSet<EntryKey>();
        var result = new ArrayList<Entry>();
        while (!pending.isEmpty()) {
            EntryKey key = pending.removeFirst();
            if (!seen.add(key)) continue;
            Entry entry = data.entry(key);
            if (entry == null) continue;
            result.add(entry);
            entry.links().stream().sorted(Comparator.comparingInt(Link::order)).forEach(link -> pending.addLast(link.target()));
        }
        return result;
    }
    private static final class Context {
        final List<Node> nodes;
        final List<Edge> edges;
        final List<NewsAttachment> attachments;
        final Set<String> represented;
        final Map<String, List<Item>>variables, names;
        Context(List<Node> nodes, List<Edge> edges, List<NewsAttachment> attachments, Set<String> represented, Map<String,
            List<Item>>variables, Map<String, List<Item>>names) {
            this.nodes = nodes;
            this.edges = edges;
            this.attachments = attachments;
            this.represented = represented;
            this.variables = variables;
            this.names = names;
        }
        void add(Node anchor, String suffix, String script, String source, boolean optional, EntryKey key, String condition) {
            var evidence = evidence(script, variables, names);
            var statements = new LinkedHashMap<String, List<Evidence>>();
            evidence.forEach(e -> statements.computeIfAbsent(e.statement(), k -> new ArrayList<>()).add(e));
            int index = 0;
            for (var statement: statements.entrySet()) {
                String effectId = anchor.id + ":news:" + suffix + ":" + index++;
                String metadata = "Source event/mechanic: " + anchor.id + "\nSource location: " + source + "\nSource entry: " + Objects.toString(key,
                    "not a dialogue entry") + "\nOriginal source condition: " + condition + "\nExact enabling statement: " + statement.getKey() + "\nOriginal instruction block:\n" + script +(optional ? "\nPOSSIBLE SOURCE EFFECT: this particular dialogue entry or option must execute. Opening or completing the enclosing event does not guarantee this outcome.": "\nEffect belongs to this exact source instruction phase; this viewer does not evaluate activation or execute it.") + "\nNews is an annotation, not a campaign prerequisite; display turn is the enabler's source turn.";
                nodes.add(new Node(effectId, Kind.EFFECT, optional ? "POSSIBLE NEWS EFFECT": "NEWS EFFECT", statement.getKey(),
                    metadata, "News", "", anchor.turn, null, key));
                edges.add(new Edge(anchor.id, effectId, optional ? "possible source effect" +(key == null ? "": " · " + key): "source instruction",
                    false));
                var newsIds = new ArrayList<String>();
                int articleIndex = 0;
                for (Evidence proof: statement.getValue()) {
                    Item item = proof.news();
                    String id = effectId + ":article:" + articleIndex++;
                    newsIds.add(id);
                    represented.add(item.id());
                    var properties = Json.object(item.raw().get("NewsProperties"));
                    String details = metadata + "\n\nNEWS SOURCE\n" + proof.proof() + "\nNews source identity: " + item.id() + "\nNameInDatabase: " + item.internalName() + "\nNewspaper: " + Json.string(properties,
                        "Newspaper") + "\nRaw enable variable: " + Json.string(properties, "IsEnabledVariable") + "\nSource newspaper turn: " + item.turn() + "\nDescription / article:\n" + item.description();
                    nodes.add(new Node(id, Kind.EVENT, "NEWS · " + item.title(), item.internalName(), details, "News",
                        "", anchor.turn, item, key));
                    edges.add(new Edge(effectId, id, "enables", false));
                }
                attachments.add(new NewsAttachment(anchor.id, effectId, newsIds));
            }
        }
    }
    private static String instructionNewlines(String source) {
        if (source == null) return "";
        var out = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        boolean comment = false;
        for (int i = 0; i<source.length(); i++) {
            char c = source.charAt(i);
            if (comment) {
                out.append(c);
                if (c == '\n' || c == '\r') comment = false;
            } else if (quote != 0) {
                out.append(c);
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
            } else if (i + 1<source.length() &&((c == '-' && source.charAt(i + 1) == '-') ||(c == '/' && source.charAt(i + 1) == '/'))) {
                comment = true;
                out.append(c);
            } else if (c == '\'' || c == '\"') {
                quote = c;
                out.append(c);
            } else if (c == '\\' && i + 1<source.length() &&(source.charAt(i + 1) == 'n' || source.charAt(i + 1) == 'r')) {
                out.append('\n');
                i++;
            } else out.append(c);
        }
        return out.toString();
    }
}
