package sordland.graph;

import sordland.data.Domain.*;
import sordland.graph.Graph.*;
import java.util.*;
import java.util.regex.Pattern;
import sordland.analysis.CausalProvenance;

public final class RootedCampaignGraphBuilder {
    private static final String IDENT = "(?:[A-Za-z_]\\w*)(?:\\.[A-Za-z_]\\w*)*";
    private static final Pattern BOOLEAN_TEST = Pattern.compile("^(" + IDENT + ")\\s*==\\s*(true|false)$");
    private static final Pattern BOOLEAN_WRITE = Pattern.compile("^(" + IDENT + ")\\s*=\\s*(true|false)$");
    private static final Pattern ANY_WRITE = Pattern.compile("^(" + IDENT + ")\\s*(?:[+*/%\\-]=|=(?!=)).+$", Pattern.DOTALL);
    private static final Pattern LITERAL_WRITE = Pattern.compile("^(" + IDENT + ")\\s*(?:[+*/%\\-]=|=(?!=))\\s*(?:true|false|[-+]?\\d+(?:\\.\\d+)?)$");
    private record BoolTest(String variable, boolean value) {
    }
    private record Occurrence(Fragment fragment, String id) {
        String condition() {
            return fragment.item() == null ? "": fragment.item().condition();
        }
    }
    private record DecisionProof(String parentId, Map<String, String> reasons) {
    }
    public Graph build(Dataset data, Integer selectedTurn) {
        Objects.requireNonNull(data);
        var provenance = new CausalProvenance(data);
        var nodes = new ArrayList<Node>();
        var edges = new ArrayList<Edge>();
        var diagnostics = new ArrayList<String>();
        var levels = new ArrayList<CampaignLevel>();
        var turns = new ArrayList<CampaignTurn>();
        var groups = new ArrayList<CampaignGroup>();
        String rootId = "campaign:start";
        nodes.add(new Node(rootId, Kind.CONTROL, selectedTurn == null ? "START": "TURN START", selectedTurn == null ? "Sordland": "Turn " + selectedTurn,
            "Synthetic entry into StoryPack_Main GameFlow" +(selectedTurn == null ? "": "; isolated turn " + selectedTurn),
            "Campaign start", "", selectedTurn, null, null));
        String previousExit = rootId;
        List<Occurrence> previousOccurrences = List.of();
        Turn previousTurn = null;
        int occurrenceCount = 0, unresolvedCount = 0;
        for (Turn turn: data.gameFlow().turns()) {
            if (selectedTurn != null && turn.turnNumber() != selectedTurn) continue;
            boolean firstStep = true;
            String turnEntry = null;
            if (turn.steps().isEmpty()) {
                turnEntry = "campaign:turn:" + turn.sourceIndex() + ":empty";
                nodes.add(junction(turnEntry, turn.turnNumber()));
                edges.add(edge(previousExit, turnEntry));
                previousExit = turnEntry;
                if (!turn.condition().isBlank()) {
                    String gate = turnEntry + ":condition";
                    nodes.add(new Node(gate, Kind.CONDITION, "CONDITION", Semantics.conditionDisplay(turn.condition()),
                        turnMetadata(data, turn), "Turn condition", "", turn.turnNumber(), null, null));
                    edges.add(edge(previousExit, gate));
                    previousExit = gate;
                    unresolvedTurnFalse(nodes, edges, diagnostics, gate, turn);
                }
                String notice = turnEntry + ":notice";
                nodes.add(new Node(notice, Kind.NOTICE, "EMPTY GAMEFLOW TURN", "Turn " + turn.turnNumber(), turnMetadata(data,
                    turn), "GameFlow", "", turn.turnNumber(), null, null));
                edges.add(edge(previousExit, notice));
                previousExit = notice;
            }
            for (Step step: turn.steps()) {
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Campaign construction cancelled");
                String prefix = "campaign:t" + turn.sourceIndex() + ":s" + step.sourceIndex();
                DecisionProof proof = !firstStep && previousTurn == turn ? proveDecision(previousOccurrences, step,
                    prefix): null;
                if (proof != null) {
                    String oldExit = previousExit;
                    nodes.removeIf(n -> n.id.equals(oldExit));
                    edges.removeIf(e -> e.from.equals(oldExit) || e.to.equals(oldExit));
                    CampaignLevel before = levels.removeLast();
                    levels.add(new CampaignLevel(before.turn(), before.turnSourceIndex(), before.stepIndex(), before.entryId(),
                        proof.parentId(), before.eventIds(), before.conditionIds(), before.groupIds()));
                    previousExit = proof.parentId();
                }
                boolean startAlias = previousExit.equals(rootId) && levels.isEmpty();
                String entryId = proof != null ? proof.parentId(): startAlias ? rootId: prefix + ":entry", exitId = prefix + ":exit";
                if (proof == null && !startAlias) {
                    nodes.add(junction(entryId, turn.turnNumber()));
                    edges.add(edge(previousExit, entryId));
                }
                nodes.add(junction(exitId, turn.turnNumber()));
                if (firstStep) turnEntry = entryId;
                var eventIds = new ArrayList<String>();
                var conditionIds = new ArrayList<String>();
                var groupIds = new ArrayList<String>();
                var occurrences = new ArrayList<Occurrence>();
                for (Fragment fragment: step.fragments()) {
                    String id = prefix + ":f" + fragment.sourceIndex();
                    occurrences.add(new Occurrence(fragment, id));
                    eventIds.add(id);
                    occurrenceCount++;
                    String metadata = sourceMetadata(data, turn, step, fragment);
                    Item item = fragment.item();
                    if (item == null) {
                        nodes.add(new Node(id, Kind.NOTICE, "UNRESOLVED FRAGMENT", fragment.name(), metadata, "Unresolved",
                            "", turn.turnNumber(), null, null));
                        diagnostics.add("Unresolved GameFlow fragment " + fragment.name() + " at turn " + turn.turnNumber() + ", step " + step.sourceIndex() + ", fragment " + fragment.sourceIndex() + ". " + fragment.diagnostic());
                        unresolvedCount++;
                    } else nodes.add(new Node(id, Kind.EVENT, item.type() + " · " + item.title(), fragment.name(),
                        metadata, item.type(), "", turn.turnNumber(), item, null));
                }
                String branchEntry = entryId;
                if (firstStep && !turn.condition().isBlank()) {
                    String gate = prefix + ":turn-condition";
                    nodes.add(new Node(gate, Kind.CONDITION, "CONDITION", Semantics.conditionDisplay(turn.condition()),
                        turnMetadata(data, turn), "Turn condition", "", turn.turnNumber(), null, null));
                    conditionIds.add(gate);
                    edges.add(edge(entryId, gate));
                    branchEntry = gate;
                    unresolvedTurnFalse(nodes, edges, diagnostics, gate, turn);
                }
                Set<String> consumed = new HashSet<>();
                var unconditional = occurrences.stream().filter(o -> o.fragment().resolved() && o.condition().isBlank()).toList();
                if (unconditional.size()>1) {
                    String gid = prefix + ":siblings", gin = gid + ":entry", gout = gid + ":exit";
                    List<String> members = unconditional.stream().map(Occurrence::id).toList();
                    groups.add(new CampaignGroup(gid, gin, gout, members));
                    groupIds.add(gid);
                    nodes.add(junction(gin, turn.turnNumber()));
                    nodes.add(junction(gout, turn.turnNumber()));
                    edges.add(edge(branchEntry, gin));
                    edges.add(edge(gout, exitId));
                    for (String id: members) {
                        edges.add(edge(gin, id));
                        edges.add(edge(id, gout));
                        consumed.add(id);
                    }
                }
                for (int i = 0; i<occurrences.size(); i++) {
                    Occurrence occurrence = occurrences.get(i);
                    if (!consumed.add(occurrence.id())) continue;
                    if (occurrence.condition().isBlank()) {
                        edges.add(edge(branchEntry, occurrence.id()));
                        edges.add(edge(occurrence.id(), exitId));
                        continue;
                    }
                    Occurrence opposite = null;
                    var causal = occurrence.fragment().item() == null ? null: provenance.branch(occurrence.fragment().item());
                    for (int j = i + 1; j<occurrences.size(); j++) {
                        Occurrence candidate = occurrences.get(j);
                        if (consumed.contains(candidate.id())) continue;
                        if (complements(occurrence.condition(), candidate.condition())) {
                            opposite = candidate;
                            break;
                        }
                        if (causal != null && causal.selection() != null && candidate.fragment().item() != null) {
                            var other = provenance.branch(candidate.fragment().item());
                            if (other.selection() != null && causal.selection().id().equals(other.selection().id()) &&
                                Collections.disjoint(causal.enabling(), other.enabling()) && causal.enabling().size() + other.enabling().size() == causal.selection().options().size()) {
                                opposite = candidate;
                                break;
                            }
                        }
                    }
                    String cid = occurrence.id() + ":condition";
                    String conditionMetadata = sourceMetadata(data, turn, step, occurrence.fragment()) + "\nRaw activation condition: " + occurrence.condition();
                    String parent = proof == null ? branchEntry: proof.parentId();
                    if (proof != null) conditionMetadata += "\nExact adjacent decision proof: " + proof.reasons().get(occurrence.id());
                    if (opposite != null) {
                        consumed.add(opposite.id());
                        conditionMetadata += "\nComplementary fragment: " + opposite.fragment().name() + "\nRaw complementary condition: " + opposite.condition() + "\nComplement proof: " +(complements(occurrence.condition(),
                            opposite.condition()) ? "identical boolean variable compared with opposite boolean literals.": "distinct outcomes of the same exhaustive source choice.");
                        if (proof != null) conditionMetadata += "\nExact complementary decision proof: " + proof.reasons().get(opposite.id());
                    }
                    if (causal != null) conditionMetadata += "\nBranch classification: " + causal.kind() + "\n" + causal.evidence();
                    if (opposite == null &&(causal == null || causal.selection() == null)) {
                        conditionMetadata += "\nOpposite outcome unresolved: activation alone does not prove a skip destination.";
                        diagnostics.add(occurrence.fragment().name() + ": no proven skip destination; activation retained");
                    }
                    boolean sourceChoice = causal != null && causal.selection() != null;
                    nodes.add(new Node(cid, Kind.CONDITION, sourceChoice ? "EARLIER SOURCE CHOICE": "CONDITION", sourceChoice ? causal.selection().source().title(): Semantics.conditionDisplay(occurrence.condition()),
                        conditionMetadata, "Condition", "", turn.turnNumber(), null, null));
                    conditionIds.add(cid);
                    if (opposite != null && causal != null && causal.selection() != null && !causal.selection().source().condition().isBlank()) {
                        Item source = causal.selection().source();
                        var activation = provenance.branch(source);
                        String scope = cid + ":source-activation";
                        nodes.add(new Node(scope, Kind.CONDITION, "SOURCE EVENT ACTIVATION", Semantics.conditionDisplay(source.condition()),
                            "The following alternatives apply only within " + source.title() + "\n" + activation.evidence(),
                            "Condition", "", turn.turnNumber(), null, null));
                        conditionIds.add(scope);
                        edges.add(edge(parent, scope));
                        String enabled = activation.enabling().isEmpty() ? "TRUE": activation.enabling().stream().map(CausalProvenance.Outcome::label).collect(java.util.stream.Collectors.joining(" / "));
                        String skipped = activation.other().isEmpty() ? "FALSE": activation.other().stream().map(CausalProvenance.Outcome::label).collect(java.util.stream.Collectors.joining(" / "));
                        edges.add(new Edge(scope, cid, enabled, false));
                        edges.add(new Edge(scope, exitId, skipped, false));
                    } else edges.add(edge(parent, cid));
                    String yes = causal != null && causal.selection() != null ? causal.enabling().stream().map(CausalProvenance.Outcome::label).collect(java.util.stream.Collectors.joining(" / ")): "TRUE";
                    causalEdge(nodes, edges, cid, occurrence.id(), yes, causal, turn.turnNumber(), "enabled");
                    completeEvent(provenance, occurrence, exitId, nodes, edges, turn.turnNumber());
                    if (opposite != null) {
                        String no = causal != null && causal.selection() != null ? causal.other().stream().map(CausalProvenance.Outcome::label).collect(java.util.stream.Collectors.joining(" / ")): "FALSE";
                        causalEdge(nodes, edges, cid, opposite.id(), no, causal, turn.turnNumber(), "alternative");
                        completeEvent(provenance, opposite, exitId, nodes, edges, turn.turnNumber());
                    } else if (causal != null && causal.kind() == CausalProvenance.BranchKind.OPTIONAL_EVENT) {
                        int skip = 0;
                        for (var option: causal.other()) causalEdge(nodes, edges, cid, exitId, option.label(), causal,
                            turn.turnNumber(), "skip:" + skip++);
                    }
                }
                if (occurrences.isEmpty()) {
                    String emptyId = prefix + ":empty";
                    nodes.add(new Node(emptyId, Kind.NOTICE, "EMPTY GAMEFLOW STEP", "Step " + step.sourceIndex(),
                        turnMetadata(data, turn) + "\nStep source index: " + step.sourceIndex() + "\nOnStepStartInstruction: " + step.onStepStartInstruction(),
                        "GameFlow", "", turn.turnNumber(), null, null));
                    edges.add(edge(branchEntry, emptyId));
                    edges.add(edge(emptyId, exitId));
                }
                levels.add(new CampaignLevel(turn.turnNumber(), turn.sourceIndex(), step.sourceIndex(), entryId, exitId,
                    eventIds, conditionIds, groupIds));
                previousExit = exitId;
                previousOccurrences = occurrences;
                previousTurn = turn;
                firstStep = false;
            }
            turns.add(new CampaignTurn(turn.turnNumber(), turn.sourceIndex(), cleanTitle(turn.transitionTitle()),
                turnEntry));
            if (turn.steps().isEmpty()) {
                previousOccurrences = List.of();
                previousTurn = null;
            }
        }
        if (levels.isEmpty()) diagnostics.add("No StoryPack_Main GameFlow steps are available" +(selectedTurn == null ? "": " for turn " + selectedTurn) + ".");
        diagnostics.add("ROOTED uses StoryPack_Main GameFlow Turn → Step → Fragment order: " + levels.size() + " levels, " + occurrenceCount + " fragment occurrences, " + unresolvedCount + " unresolved.");
        diagnostics.add("Conditions are displayed, not evaluated. Neutral junctions communicate level progression without invented event causality. Gray sibling containers have no source semantics.");
        Set<String> turnGates = new HashSet<>();
        nodes.stream().filter(n -> n.type.equals("Turn condition")).forEach(n -> turnGates.add(n.id));
        for (int i = 0; i<edges.size(); i++) {
            Edge edge = edges.get(i);
            if (turnGates.contains(edge.from) && edge.label.isBlank()) edges.set(i, new Edge(edge.from, edge.to, "TRUE",
                false));
        }
        Graph result = new Graph(selectedTurn == null ? "Sordland campaign": "Sordland campaign · Turn " + selectedTurn,
            nodes, edges, diagnostics, new CampaignMetadata(turns, levels, groups));
        return new RuntimeGraphBuilder().attach(data, new NewsGraphBuilder().attach(data, result));
    }
    private static void unresolvedTurnFalse(List<Node> nodes, List<Edge> edges, List<String> diagnostics, String gate,
        Turn turn) {
        String id = gate + ":false-unresolved";
        String reason = "Turn[" + turn.sourceIndex() + "].Condition can be false, but GameFlow provides no explicit false destination. No later event or turn is inferred. This notice represents missing route semantics, not a campaign ending.\nRaw condition: " + turn.condition();
        nodes.add(new Node(id, Kind.NOTICE, "FALSE DESTINATION UNRESOLVED", "Turn activation did not pass", reason,
            "Unresolved", "", turn.turnNumber(), null, null));
        edges.add(new Edge(gate, id, "FALSE", false));
        diagnostics.add(reason);
    }
    private static Node junction(String id, int turn) {
        return new Node(id, Kind.JUNCTION, "", "", "", "", "", turn, null, null);
    }
    private static void completeEvent(CausalProvenance provenance, Occurrence occurrence, String exit, List<Node> nodes,
        List<Edge> edges, int turn) {
        var selection = occurrence.fragment().item() == null ? null: provenance.mandatoryOutcomes(occurrence.fragment().item());
        if (selection == null) {
            edges.add(edge(occurrence.id(), exit));
            return;
        }
        int i = 0;
        for (var option: selection.options()) {
            String id = occurrence.id() + ":outcome:" + i++;
            nodes.add(new Node(id, Kind.CHOICE, "YOU", option.label(), "Mandatory source selection: " + selection.id() + "\nEvery feasible completing dialogue route crosses this fork (all initial Boolean valuations checked).\n" + option.instruction() + "\nWrites: " + option.writes(),
                "Dialogue", "Player", turn, null, option.entries().isEmpty() ? null: option.entries().getFirst()));
            edges.add(edge(occurrence.id(), id));
            edges.add(edge(id, exit));
        }
    }
    private static void causalEdge(List<Node> nodes, List<Edge> edges, String from, String to, String label, CausalProvenance.Branch branch,
        int turn, String suffix) {
        if (branch == null || branch.selection() == null) {
            edges.add(new Edge(from, to, label, false));
            return;
        }
        String id = from + ":choice:" + suffix;
        var entries = branch.selection().options().stream().filter(o -> label.contains(o.label())).flatMap(o -> o.entries().stream()).toList();
        nodes.add(new Node(id, Kind.CHOICE, "[CHOICE]", label, branch.evidence(), "Condition", "Player", turn, null,
            entries.isEmpty() ? null: entries.getFirst()));
        edges.add(edge(from, id));
        edges.add(new Edge(id, to, "", false));
    }
    private static Edge edge(String from, String to) {
        return new Edge(from, to, "", false);
    }
    private static String cleanTitle(String title) {
        return title.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("\\s+", " ").trim();
    }
    private static String turnMetadata(Dataset data, Turn turn) {
        return "StoryPack: " + data.gameFlow().storyPack() + "\nGameFlow source index: " + data.gameFlow().sourceIndex() + "\nTurn: " + turn.turnNumber() + "\nTurn source index: " + turn.sourceIndex() + "\nTransitionTitle: " + turn.transitionTitle() + "\nRaw turn Condition: " + turn.condition() + "\nOnTurnStartInstruction: " + turn.onTurnStartInstruction();
    }
    private static String sourceMetadata(Dataset data, Turn turn, Step step, Fragment fragment) {
        Item item = fragment.item();
        return turnMetadata(data, turn) + "\nStep source index: " + step.sourceIndex() + "\nOnStepStartInstruction: " + step.onStepStartInstruction() + "\nFragment source index: " + fragment.sourceIndex() + "\nExact fragment ID: " + fragment.name() +(item == null ? "\nResolution: " + fragment.diagnostic(): "\nType: " + item.type() + "\nSource identity: " + item.id() + "\nDatabase name: " + item.internalName() + "\nPath: " + item.path() +(item.conversationId() == null ? "": "\nConversation ID: " + item.conversationId()) + "\nRaw activation condition: " + item.condition() + "\nOn begin: " + item.beginInstruction() + "\nOn end: " + item.endInstruction());
    }
    public static boolean complements(String first, String second) {
        BoolTest a = booleanTest(first), b = booleanTest(second);
        return a != null && b != null && a.variable().equals(b.variable()) && a.value() != b.value();
    }
    private static BoolTest booleanTest(String condition) {
        var match = BOOLEAN_TEST.matcher(stripOuterParentheses(Objects.requireNonNullElse(condition, "").trim()));
        return match.matches() ? new BoolTest(match.group(1), Boolean.parseBoolean(match.group(2))): null;
    }
    private static String stripOuterParentheses(String expression) {
        while (expression.startsWith("(") && expression.endsWith(")")) {
            int depth = 0;
            boolean wraps = true;
            for (int i = 0; i<expression.length() - 1; i++) {
                char c = expression.charAt(i);
                if (c == '(') depth++;
                else if (c == ')') depth--;
                if (depth <= 0) {
                    wraps = false;
                    break;
                }
            }
            if (!wraps) break;
            expression = expression.substring(1, expression.length() - 1).trim();
        }
        return expression;
    }
    private static DecisionProof proveDecision(List<Occurrence> previous, Step step, String prefix) {
        if (previous.size() != 1 || step.fragments().size()<2 || !step.onStepStartInstruction().isBlank()) return null;
        var next = step.fragments().stream().map(f -> new Occurrence(f, prefix + ":f" + f.sourceIndex())).toList();
        Occurrence parent = previous.getFirst();
        Item decision = parent.fragment().item();
        if (decision == null || !decision.type().equals("Decision") || !decision.condition().isBlank() || !decision.endInstruction().isBlank()) return null;
        var optionWrites = new ArrayList<Map<String, Boolean>>();
        for (Option option: decision.options()) {
            var analysis = Semantics.analyze(option.instruction(), "");
            if (!analysis.unknown().isEmpty() || analysis.terminal()) return null;
            var writes = new LinkedHashMap<String, Boolean>();
            for (var command: analysis.commands()) {
                if (command.kind() == Semantics.CommandKind.COSMETIC) continue;
                var assignment = ANY_WRITE.matcher(command.raw().trim());
                if (!assignment.matches() || !LITERAL_WRITE.matcher(command.raw().trim()).matches()) return null;
                var matcher = BOOLEAN_WRITE.matcher(command.raw().trim());
                if (matcher.matches()) writes.put(matcher.group(1), Boolean.parseBoolean(matcher.group(2)));
                else writes.remove(assignment.group(1));
            }
            optionWrites.add(writes);
        }
        var usedOptions = new HashSet<Integer>();
        var reasons = new LinkedHashMap<String, String>();
        for (Occurrence target: next) {
            BoolTest test = booleanTest(target.condition());
            if (test == null) return null;
            int optionIndex = -1;
            for (int i = 0; i<optionWrites.size(); i++) if (Objects.equals(optionWrites.get(i).get(test.variable()),
                test.value())) {
                if (optionIndex >= 0) return null;
                optionIndex = i;
            }
            if (optionIndex<0 || !usedOptions.add(optionIndex)) return null;
            reasons.put(target.id(), decision.internalName() + ", option " +(optionIndex + 1) + " (" + decision.options().get(optionIndex).title() + ") writes " + test.variable() + " = " + test.value() + "; next-step fragment tests " + target.condition());
        }
        return new DecisionProof(parent.id(), reasons);
    }
}
