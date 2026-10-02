package sordland.graph;

import sordland.data.Domain.EntryKey;
import sordland.data.Json;
import sordland.data.runtime.RuntimeDatabase;
import sordland.data.runtime.RuntimeDatabase.Entity;
import sordland.graph.Graph.*;
import java.util.*;
import java.util.regex.Pattern;

public final class PanelGraphBuilder {
    private static final Pattern SHOW = Pattern.compile("^ShowPagedDecisionsPanel\\s*\\(\\s*([\"'])([^\"'\\\\]+)\\1\\s*\\)$");
    public static String panelName(String command) {
        var m = SHOW.matcher(command.trim());
        return m.matches() ? m.group(2): null;
    }
    public static String append(RuntimeDatabase db, String command, String id, EntryKey source, List<Node> nodes,
        List<Edge> edges, List<PanelGroup> groups, List<String> notes) {
        String name = panelName(command);
        Entity panel = name == null ? null: db.resolve(name, "pageddecisionpanelsdata");
        if (panel == null) {
            String reason = "Unresolved exact runtime panel reference: " + command;
            notes.add(reason);
            nodes.add(new Node(id, Kind.NOTICE, "UNRESOLVED PANEL", command, reason, "Dialogue", "", null, null, source));
            return id;
        }
        boolean budget = "Panel_Budget".equals(name);
        String details = panel.metadata() + "\nInvoking command: " + command + "\nSource dialogue: " + source;
        nodes.add(new Node(id, Kind.JUNCTION, panel.title(), "", details, "Dialogue", "", null, panel.item(), source));
        var categories = new ArrayList<PanelCategory>();
        var members = new ArrayList<String>();
        members.add(id);
        int pi = 0;
        for (Object pageRef: RuntimeDatabase.values(panel.properties().get("Pages"))) {
            String pageName = Json.string(pageRef), pid = id + ":page:" + pi++;
            members.add(pid);
            Entity page = db.resolve(pageName, "carouselchoicepagedata", "multiplechoicepagedata");
            var branches = new ArrayList<PanelBranch>();
            if (page == null) {
                unresolved(pid, pageName, panel, source, nodes, notes);
                edges.add(new Edge(id, pid, "page", false));
                categories.add(new PanelCategory(pid, branches));
                continue;
            }
            String header = page.title().replaceFirst("^[IVX]+\\.\\s*", "");
            if (budget) header = switch (page.name()) {
                case "Page_Budget_Healthcare" -> "Health";
                case "Page_Budget_Security" -> "Law Enforcement";
                case "Page_Budget_Education" -> "Education";
                case "Page_Budget_Military" -> "Military";
                default -> header;
            };
            nodes.add(new Node(pid, Kind.CONTROL, header, "", page.metadata() + "\nPanel: " + panel.name(), budget ? "Panel header": "Dialogue",
                "", null, page.item(), source));
            edges.add(new Edge(id, pid, "", false));
            var refs = new ArrayList<>(RuntimeDatabase.values(page.properties().get("Options")));
            int oi = 0;
            for (Object ref: refs) {
                String baseId = pid + ":option:" + oi++, optionName = Json.string(ref);
                Entity option = db.resolve(optionName, page.collection().equals("carouselchoicepagedata") ? "carouselchoiceoptiondata": "multiplechoiceoptiondata");
                if (option == null) {
                    members.add(baseId);
                    unresolved(baseId, optionName, page, source, nodes, notes);
                    edges.add(new Edge(pid, baseId, "unresolved option", false));
                    branches.add(new PanelBranch("", "", baseId));
                    continue;
                }
                String metadata = option.metadata() + "\nPanel: " + panel.name() + "\nPage: " + page.name() + "\nPanelCounterVariable (panel source): " + panel.value("PanelCounterVariable") + "\nPanelCounterIncrement (option source): " + option.value("PanelCounterIncrement") + "\nPanelBarVariable (panel source): " + panel.value("PanelBarVariable") + "\nPanelBarIncrement (option source): " + option.value("PanelBarIncrement") + "\nThese increments are separate from the raw Instruction, bundled for display only.";
                String effect = compact(option.value("Instruction"));
                effect = increment(effect, panel.value("PanelCounterVariable"), option.properties().get("PanelCounterIncrement"));
                effect = increment(effect, panel.value("PanelBarVariable"), option.properties().get("PanelBarIncrement"));
                var analysis = Semantics.analyze(option.value("Instruction"), "");
                if (!analysis.unknown().isEmpty()) notes.add("Unsupported instruction retained in runtime choice " + option.name() + ": " + analysis.unknown());
                String condition = option.value("Condition"), cid = "";
                if (!condition.isBlank()) {
                    cid = baseId + ":condition";
                    members.add(cid);
                    nodes.add(new Node(cid, Kind.CONDITION, "CONDITION", Semantics.conditionDisplay(condition), metadata,
                        "Dialogue", "", null, null, source));
                }
                if (budget) {
                    String choiceId = baseId + ":choice";
                    String effectId = baseId + ":effect";
                    members.add(choiceId);
                    members.add(effectId);
                    nodes.add(new Node(choiceId, Kind.CHOICE, option.title(), "", metadata, "Panel choice", "", null,
                        null, source));
                    nodes.add(new Node(effectId, Kind.EFFECT, "EFFECT", effect, metadata, "Panel effect", "", null,
                        contextItem(option, panel, page), source));
                    if (!cid.isBlank()) {
                        edges.add(new Edge(pid, cid, option.title(), false));
                        edges.add(new Edge(cid, choiceId, "TRUE", false));
                    } else {
                        edges.add(new Edge(pid, choiceId, option.title(), false));
                    }
                    edges.add(new Edge(choiceId, effectId, "", false));
                    branches.add(new PanelBranch(cid, choiceId, effectId));
                } else {
                    String effectId = baseId;
                    members.add(effectId);
                    nodes.add(new Node(effectId, Kind.EFFECT, option.title().toUpperCase(Locale.ROOT) + " " + header.toUpperCase(Locale.ROOT),
                        effect, metadata, "Dialogue", "", null, contextItem(option, panel, page), source));
                    if (!cid.isBlank()) {
                        edges.add(new Edge(pid, cid, option.title(), false));
                        edges.add(new Edge(cid, effectId, "TRUE", false));
                    } else {
                        edges.add(new Edge(pid, effectId, option.title(), false));
                    }
                    branches.add(new PanelBranch(cid, "", effectId));
                }
            }
            categories.add(new PanelCategory(pid, branches));
        }
        String completion = id + ":complete";
        members.add(completion);
        if (budget) {
            nodes.add(new Node(completion, Kind.CONTROL, "Budget chosen", "", details + "\nVisual merge after all budget alternatives; the exact source continuation remains singular.",
                "Panel completion", "", null, null, source));
            for (PanelCategory category: categories) for (PanelBranch branch: category.branches()) edges.add(new Edge(branch.effectId(),
                completion, "", false));
        } else {
            nodes.add(new Node(completion, Kind.JUNCTION, "", "", details + "\nPanel completion only; no individual choice implies completion.",
                "Panel completion", "", null, null, source));
            edges.add(new Edge(id, completion, "panel completion", false));
        }
        groups.add(new PanelGroup(id + ":group", id, completion, categories, members));
        for (String field: List.of("ConversationOnFinish", "PagedDecisionPanelOnFinish")) if (!panel.value(field).isBlank()) {
            String target = panel.value(field), callback = completion + ":" + field;
            Entity resolved = field.equals("ConversationOnFinish") ? db.resolve(target, "allconversationsdata"): db.resolve(target,
                "pageddecisionpanelsdata");
            if (resolved == null) unresolved(callback, target, panel, source, nodes, notes);
            else nodes.add(new Node(callback, Kind.REFERENCE, "PANEL COMPLETION REFERENCE", resolved.title(), "Exact " + field + " = " + target + "\n" + resolved.metadata(),
                "Dialogue", "", null, null, source));
            edges.add(new Edge(completion, callback, field, false));
        }
        return completion;
    }
    private static sordland.data.Domain.Item contextItem(Entity option, Entity panel, Entity page) {
        var item = option.item();
        var raw = new LinkedHashMap<>(item.raw());
        raw.put("PanelContext", Map.of("Panel", panel.name(), "PanelSource", panel.location(), "Page", page.name(),
            "PageSource", page.location(), "PanelCounterVariable", panel.value("PanelCounterVariable"), "PanelBarVariable",
            panel.value("PanelBarVariable")));
        return new sordland.data.Domain.Item(item.id(), item.type(), item.title(), item.internalName(), item.path(),
            item.turn(), item.conversationId(), item.condition(), item.beginInstruction(), item.endInstruction(),
            item.description(), item.options(), raw);
    }
    private static void unresolved(String id, String name, Entity owner, EntryKey source, List<Node> nodes, List<String> notes) {
        String text = "Unresolved exact reference " + name + " in " + owner.location();
        notes.add(text);
        nodes.add(new Node(id, Kind.NOTICE, "UNRESOLVED REFERENCE", name, text + "\n" + owner.metadata(), "Dialogue",
            "", null, null, source));
    }
    public static Graph build(RuntimeDatabase db, String name) {
        var nodes = new ArrayList<Node>();
        var edges = new ArrayList<Edge>();
        var groups = new ArrayList<PanelGroup>();
        var notes = new ArrayList<String>();
        append(db, "ShowPagedDecisionsPanel(\"" + name + "\")", "runtime-panel", null, nodes, edges, groups, notes);
        return new Graph(name, nodes, edges, notes, null, groups);
    }
    public static String compact(String instruction) {
        var lines = new ArrayList<String>();
        for (var command: Semantics.analyze(instruction, "").commands()) {
            String raw = command.raw();
            var m = Pattern.compile("^(BaseGame\\.[A-Za-z_]\\w*)\\s*(=|\\+=|-=)\\s*(true|false|[-+]?\\d+(?:\\.\\d+)?)$").matcher(raw);
            if (m.matches()) {
                String variable = Semantics.conditionDisplay(m.group(1)).replaceFirst("^Turn\\d+_EnT_", "").replace("_",
                    "");
                lines.add(variable + " " +(m.group(2).equals("=") ? "= ": m.group(2).replace("+=", "+").replace("-=",
                    "-")) + m.group(3));
            } else lines.add(raw);
        }
        return String.join("\n", lines);
    }
    private static String increment(String text, String variable, Object amount) {
        if (variable.isBlank() || !(amount instanceof Number n) || n.doubleValue() == 0) return text;
        String value = n.doubleValue() == n.longValue() ? Long.toString(n.longValue()): n.toString();
        return text +(text.isBlank() ? "": "\n") + Semantics.conditionDisplay(variable) + " " +(n.doubleValue()>0 ? "+": "") + value;
    }
}
