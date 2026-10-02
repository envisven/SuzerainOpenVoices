package sordland.analysis;

import sordland.data.Domain.*;
import sordland.graph.Semantics;
import java.util.*;
import static sordland.analysis.VariableSyntax.Expr;

public final class CausalProvenance {
    public enum BranchKind {
        OPTIONAL_EVENT, EXCLUSIVE_CHOICE_MEMBER, MANDATORY_CONSEQUENCE, COMPLEMENTARY_PAIR, INDEPENDENT_CANDIDATE, UNKNOWN
    }
    public enum TriggerKind {
        STATE_GUARDED, CHOICE_TRIGGERED, DECISION_TRIGGERED, EVENT_TRIGGERED, STORY_TRIGGERED, PARTIAL
    }
    public record Outcome(String label, Map<String, Boolean> writes, List<EntryKey> entries, String instruction) {
    }
    public record Selection(String id, String title, Item source, List<Outcome> options, boolean exhaustive) {
    }
    public record Branch(BranchKind kind, Selection selection, List<Outcome> enabling, List<Outcome> other, String evidence) {
    }
    private final Dataset data;
    private final Map<EntryKey, String> choiceTokens = new HashMap<>();
    private final Map<String, EntryKey> choiceOrigins = Collections.synchronizedMap(new IdentityHashMap<>());
    public EntryKey choiceOrigin(String token) {
        return choiceOrigins.get(token);
    }
    private TargetReachability targetReachability;
    public synchronized TargetReachability targetReachability() {
        if (targetReachability == null) targetReachability = new TargetReachability(data.conversations().values().stream().flatMap(c -> c.entries().values().stream()).toList(),
            this::choice);
        return targetReachability;
    }
    private final Map<Integer, Item> events = new HashMap<>();
    private final Map<EntryKey, List<EntryKey>>incoming = new HashMap<>();
    private final Map<String, Item> entities = new HashMap<>();
    private final List<Selection> selections = new ArrayList<>();
    private final Map<String, Integer> flowOrder = new HashMap<>();
    private final List<Item> flowItems = new ArrayList<>();
    private final Map<String, List<Selection>>byVariable = new HashMap<>();
    public CausalProvenance(Dataset data) {
        this.data = data;
        for (var turn: data.gameFlow().turns()) for (var step: turn.steps()) for (var f: step.fragments()) if (f.item() != null) {
            flowOrder.putIfAbsent(f.item().id(), flowItems.size());
            flowItems.add(f.item());
        }
        for (Item item: data.items()) {
            entities.put("Entity " + item.id() + " · " + item.internalName(), item);
            if (item.conversationId() != null) events.putIfAbsent(item.conversationId(), item);
            if (Set.of("Decision", "Bill").contains(item.type()) && item.options().size()>1) {
                var options = new ArrayList<Outcome>();
                boolean complete = true;
                for (Option option: item.options()) {
                    var writes = booleanWrites(option.instruction());
                    if (writes == null || !option.condition().isBlank()) {
                        complete = false;
                        break;
                    }
                    options.add(new Outcome(option.title(), writes, List.of(), option.instruction()));
                }
                if (complete) add(new Selection(item.id(), item.title(), item, List.copyOf(options), true));
            }
        }
        for (var c: data.conversations().values()) for (var e: c.entries().values()) for (var l: e.links()) if (!l.connector() &&
            l.target().conversationId() == e.key().conversationId()) incoming.computeIfAbsent(l.target(), k -> new ArrayList<>()).add(e.key());
        for (var c: data.conversations().values()) for (var fork: c.entries().values()) {
            if (fork.links().size()<2 || fork.links().stream().anyMatch(l -> l.connector() || !normal(l) || data.entry(l.target()) == null ||
                !data.entry(l.target()).isPlayer())) continue;
            var traces = new ArrayList<List<Entry>>();
            boolean valid = true;
            for (var link: fork.links()) {
                var trace = new ArrayList<Entry>();
                var seen = new HashSet<EntryKey>();
                Entry e = data.entry(link.target());
                while (e != null && seen.add(e.key())) {
                    if (!e.condition().isBlank()) break;
                    trace.add(e);
                    if (Semantics.analyze(e.script(), e.sequence()).commands().stream().anyMatch(command -> command.kind() != Semantics.CommandKind.COSMETIC)) break;
                    if (e.links().size() != 1 || !normal(e.links().getFirst()) || e.links().getFirst().connector()) break;
                    e = data.entry(e.links().getFirst().target());
                }
                if (trace.isEmpty()) {
                    valid = false;
                    break;
                }
                traces.add(trace);
            }
            if (!valid) continue;
            var common = new HashSet<EntryKey>();
            traces.getFirst().forEach(e -> common.add(e.key()));
            for (var trace: traces) common.retainAll(trace.stream().map(Entry::key).toList());
            var options = new ArrayList<Outcome>();
            for (var trace: traces) {
                var writes = new LinkedHashMap<String, Boolean>();
                var keys = new ArrayList<EntryKey>();
                var script = new StringBuilder();
                for (Entry e: trace) {
                    if (common.contains(e.key())) break;
                    var parsed = booleanWrites(e.script());
                    if (parsed == null || Semantics.analyze("", e.sequence()).commands().stream().anyMatch(command -> command.kind() != Semantics.CommandKind.COSMETIC)) {
                        valid = false;
                        break;
                    }
                    writes.putAll(parsed);
                    keys.add(e.key());
                    script.append(e.key()).append(": ").append(e.script()).append('\n');
                }
                if (!valid) break;
                options.add(new Outcome(playerText(trace.getFirst()), Map.copyOf(writes), List.copyOf(keys), script.toString()));
            }
            if (valid && options.stream().anyMatch(o -> !o.writes().isEmpty())) add(new Selection("Dialogue " + fork.key(),
                c.title(), events.get(c.id()), List.copyOf(options), true));
        }
        byVariable.values().forEach(list -> {
            var frequency = new HashMap<String, Integer>();
            list.forEach(s -> s.options().forEach(o -> frequency.merge(o.label(), 1, Integer::sum)));
            list.sort(Comparator.comparingInt((Selection s) -> s.options().stream().mapToInt(o -> frequency.get(o.label())).sum()).reversed());
        });
    }
    private void add(Selection s) {
        selections.add(s);
        var names = new HashSet<String>();
        s.options().forEach(o -> names.addAll(o.writes().keySet()));
        names.forEach(v -> byVariable.computeIfAbsent(v, k -> new ArrayList<>()).add(s));
    }
    private static boolean normal(Link l) {
        return l.priority().isBlank() || l.priority().equalsIgnoreCase("Normal");
    }
    private static Map<String, Boolean> booleanWrites(String script) {
        var out = new LinkedHashMap<String, Boolean>();
        for (var command: Semantics.analyze(script, "").commands()) {
            if (command.kind() == Semantics.CommandKind.COSMETIC) continue;
            var effect = VariableSyntax.effect(command.raw());
            if (effect != null && effect.value().op().equals("literal")) {
                if (effect.operator().equals("=") && Set.of("true", "false").contains(effect.value().value())) out.put(effect.variable(),
                    Boolean.valueOf(effect.value().value()));
                else out.remove(effect.variable());
                continue;
            }
            var literal = java.util.regex.Pattern.compile("^(?:Variable\\[\"([^\"]+)\"\\]|([A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)+))\\s*(=|[+*/-]=)\\s*(true|false|[-+]?\\d+(?:\\.\\d+)?)$").matcher(command.raw().trim());
            if (!literal.matches()) return null;
            if (literal.group(3).equals("=") && Set.of("true", "false").contains(literal.group(4))) out.put(literal.group(1) == null ? literal.group(2): literal.group(1),
                Boolean.valueOf(literal.group(4)));
        }
        return Map.copyOf(out);
    }
    public List<Selection> selections() {
        return List.copyOf(selections);
    }
    public Selection mandatoryOutcomes(Item item) {
        if (item.conversationId() == null) return null;
        for (Selection s: selections) {
            if (s.source() != item || !s.id().startsWith("Dialogue ")) continue;
            String[] parts = s.id().substring(9).split(":");
            var fork = new EntryKey(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            var targets = new HashSet<String>();
            boolean valid = true;
            for (Outcome option: s.options()) {
                var matches = data.items().stream().filter(i -> i != item && i.turn() != null && item.turn() != null &&
                    i.turn() >= item.turn()).filter(i -> {
                    Expr test = ConditionLogic.normalize(VariableSyntax.parse(i.condition()));
                    return test.op().equals("==") && test.children().getFirst().op().equals("var") && Set.of("true",
                        "false").contains(test.children().getLast().value()) && Objects.equals(option.writes().get(test.children().getFirst().value()),
                        Boolean.valueOf(test.children().getLast().value()));
                }).toList();
                if (matches.size() != 1 || !targets.add(matches.getFirst().internalName())) {
                    valid = false;
                    break;
                }
            }
            if (!valid) continue;
            if (valid && allCompletingRoutesCross(item.conversationId(), fork)) return s;
        }
        return null;
    }
    private boolean allCompletingRoutesCross(int conversation, EntryKey fork) {
        var c = data.conversations().get(conversation);
        if (c == null) return false;
        var variables = new TreeSet<String>();
        c.entries().values().forEach(e -> variables.addAll(VariableSyntax.parse(e.condition()).variables()));
        if (variables.size()>12) return false;
        var names = List.copyOf(variables);
        var positions = new HashMap<String, Integer>();
        for (int i = 0; i<names.size(); i++) positions.put(names.get(i), i);
        record State(EntryKey entry, int values) {
        }
        var queue = new ArrayDeque<State>();
        var seen = new HashSet<State>();
        for (int mask = 0; mask<(1<<names.size()); mask++) queue.add(new State(new EntryKey(conversation, 0), mask));
        boolean reached = false;
        while (!queue.isEmpty()) {
            State state = queue.removeFirst();
            if (!seen.add(state)) continue;
            if (seen.size()>100000) return false;
            Entry e = data.entry(state.entry());
            if (e == null) return false;
            Boolean enabled = evaluate(VariableSyntax.parse(e.condition()), positions, state.values());
            if (enabled == null) return false;
            if (!enabled) continue;
            if (state.entry().equals(fork)) {
                reached = true;
                continue;
            }
            if (e.links().isEmpty()) return false;
            int values = state.values();
            for (var command: Semantics.analyze(e.script(), e.sequence()).commands()) {
                if (command.kind() == Semantics.CommandKind.COSMETIC) continue;
                var effect = VariableSyntax.effect(command.raw());
                if (effect == null) return false;
                Integer bit = positions.get(effect.variable());
                if (bit == null) continue;
                if (!effect.operator().equals("=") || !Set.of("true", "false").contains(effect.value().value())) return false;
                values = effect.value().value().equals("true") ? values |(1<<bit): values & ~(1<<bit);
            }
            for (Link link: e.links()) {
                if (link.connector() || !normal(link) || link.target().conversationId() != conversation) return false;
                queue.addLast(new State(link.target(), values));
            }
        }
        return reached;
    }
    private static Boolean evaluate(Expr e, Map<String, Integer> positions, int state) {
        if (e.op().equals("literal") && Set.of("true", "false").contains(e.value())) return Boolean.valueOf(e.value());
        if (e.op().equals("var")) return(state &(1<<positions.get(e.value()))) != 0;
        if (e.op().equals("not")) {
            Boolean a = evaluate(e.children().getFirst(), positions, state);
            return a == null ? null: !a;
        }
        if (Set.of("and", "or", "==", "!=").contains(e.op())) {
            var values = e.children().stream().map(x -> evaluate(x, positions, state)).toList();
            if (values.contains(null)) return null;
            return switch (e.op()) {
                case "and" -> values.stream().allMatch(Boolean::booleanValue);
                case "or" -> values.stream().anyMatch(Boolean::booleanValue);
                case "==" -> values.getFirst().equals(values.getLast());
                default -> !values.getFirst().equals(values.getLast());
            };
        }
        return null;
    }
    public Branch branch(Item target) {
        Expr test = ConditionLogic.normalize(VariableSyntax.parse(target.condition()));
        if (!test.op().equals("==") || !test.children().getFirst().op().equals("var") || !Set.of("true", "false").contains(test.children().getLast().value())) return new Branch(BranchKind.UNKNOWN,
            null, List.of(), List.of(), "Activation is not a proven source-choice Boolean test");
        String variable = test.children().getFirst().value();
        boolean value = Boolean.parseBoolean(test.children().getLast().value());
        Branch fallback = null;
        for (Selection s: byVariable.getOrDefault(variable, List.of())) {
            if (s.source() == null || s.source().turn() == null || target.turn() == null || s.source().turn()> target.turn() ||
                s.source() == target) continue;
            var yes = s.options().stream().filter(o -> Objects.equals(o.writes().get(variable), value)).toList();
            var no = s.options().stream().filter(o -> !yes.contains(o)).toList();
            if (yes.isEmpty() || no.isEmpty() || !persistsUntil(s, target, variable, value)) continue;
            boolean siblings = no.stream().allMatch(o -> o.writes().entrySet().stream().anyMatch(w -> data.items().stream().anyMatch(i -> !i.equals(target) &&
                i.turn() != null && i.turn().equals(target.turn()) && ConditionLogic.normalize(VariableSyntax.parse(i.condition())).equals(new Expr("==",
                "", List.of(new Expr("var", w.getKey(), List.of()), ConditionLogic.literal(w.getValue().toString())))))));
            boolean explicitSkip = no.stream().allMatch(o -> Objects.equals(o.writes().get(variable), !value) ||
                Boolean.TRUE.equals(o.writes().get("GameCondition." + target.internalName())));
            Branch found = new Branch(siblings ? BranchKind.EXCLUSIVE_CHOICE_MEMBER: explicitSkip ? BranchKind.OPTIONAL_EVENT: BranchKind.MANDATORY_CONSEQUENCE,
                s, yes, no, "Source selection: " + s.id() + " / " + s.title() + "\nExhaustive source options: " + s.options().size() + "\n" + s.options() + "\nActivation: " + target.condition() + "\nAn enabling option requires the event; other options are upstream alternatives, never an additional choice after activation.");
            if (siblings || explicitSkip) return found;
            fallback = found;
        }
        if (fallback != null) return fallback;
        return new Branch(BranchKind.UNKNOWN, null, List.of(), List.of(), "No exhaustive source choice proven; no false destination inferred");
    }
    private boolean persistsUntil(Selection selection, Item target, String variable, boolean value) {
        Integer a = flowOrder.get(selection.source().id()), b = flowOrder.get(target.id());
        if (a == null || b == null || a >= b) return false;
        for (int i = a + 1; i<b; i++) {
            Item item = flowItems.get(i);
            if (overwrites(item.beginInstruction(), variable, value) || overwrites(item.endInstruction(), variable,
                value)) return false;
            for (Option option: item.options()) if (overwrites(option.instruction(), variable, value)) return false;
            if (item.conversationId() != null) {
                var c = data.conversations().get(item.conversationId());
                if (c != null) for (Entry entry: c.entries().values()) if (overwrites(entry.script(), variable, value)) return false;
            }
        }
        int order = 0;
        for (var turn: data.gameFlow().turns()) {
            if (order> a && order <= b && overwrites(turn.onTurnStartInstruction(), variable, value)) return false;
            for (var step: turn.steps()) {
                if (order> a && order <= b && overwrites(step.onStepStartInstruction(), variable, value)) return false;
                order +=(int) step.fragments().stream().filter(f -> f.item() != null).count();
            }
        }
        return true;
    }
    private static boolean overwrites(String script, String variable, boolean value) {
        for (var command: Semantics.analyze(script, "").commands()) {
            var effect = VariableSyntax.effect(command.raw());
            if (effect != null && effect.variable().equals(variable) && !(effect.operator().equals("=") && effect.value().equals(ConditionLogic.literal(Boolean.toString(value))))) return true;
            if (effect == null && command.raw().contains(variable) && command.kind() != Semantics.CommandKind.COSMETIC) return true;
        }
        return false;
    }
    public static String playerText(Entry e) {
        return !e.text().isBlank() ? e.text(): !e.menuText().isBlank() ? e.menuText(): e.title();
    }
    public synchronized String choice(EntryKey key) {
        String cached = choiceTokens.get(key);
        if (cached != null) return cached;
        Entry e = data.entry(key);
        if (e == null || key.dialogueId() == 0 || !e.isPlayer() || playerText(e).isBlank()) return "";
        var c = data.conversations().get(key.conversationId());
        Item event = events.get(key.conversationId());
        String name = event != null ? event.title(): c == null ? "": c.title().substring(c.title().lastIndexOf('/') + 1);
        String token = "[CHOICE] " + playerText(e) + " - [" + name + "]";
        choiceOrigins.put(token, key);
        choiceTokens.put(key, token);
        return token;
    }
    public Item event(EntryKey key) {
        return events.get(key.conversationId());
    }
    public boolean exhaustiveChoices(Set<EntryKey> choices) {
        if (choices.size()<2) return false;
        for (EntryKey choice: choices) for (EntryKey parent: incoming.getOrDefault(choice, List.of())) {
            Entry fork = data.entry(parent);
            if (fork != null && fork.links().size() == choices.size() && fork.links().stream().allMatch(l -> normal(l) &&
                !l.connector() && choices.contains(l.target()))) return true;
        }
        return false;
    }
    public String activation(VariableIndex.Source s) {
        Item e = sourceItem(s);
        return e == null ? "": e.condition();
    }
    private Item sourceItem(VariableIndex.Source s) {
        if (entities.containsKey(s.identity())) return entities.get(s.identity());
        var match = java.util.regex.Pattern.compile("Conversation (\\d+) / Dialogue (\\d+)").matcher(s.identity());
        return match.matches() ? events.get(Integer.parseInt(match.group(1))): null;
    }
    public String trigger(VariableIndex.Source s) {
        Item item = sourceItem(s);
        if (s.field().startsWith("Options[")) return "[" +(item == null ? "OPTION": item.type().toUpperCase(Locale.ROOT)) + "] " + s.title();
        if (item != null) return "[EVENT] " + item.title();
        if (s.identity().startsWith("StoryPack_Main")) return "[STORY] " + s.title() + " - " + s.identity();
        return "[SOURCE] " + s.title();
    }
    public String entryEvidence(EntryKey key) {
        Entry e = data.entry(key);
        return e == null ? "Missing source " + key: key + " " + choice(key) + "\ncondition: " + e.condition() + "\nscript: " + e.script() + "\nsequence: " + e.sequence() + "\nlinks: " + e.links();
    }
    public Set<TriggerKind> categories(NumericVariableLayout.Reference reference) {
        var result = EnumSet.noneOf(TriggerKind.class);
        var path = reference.path();
        var source = reference.occurrence().source();
        if (path != null && !path.complete()) result.add(TriggerKind.PARTIAL);
        if (path != null && !ConditionLogic.truth(path.condition())) result.add(TriggerKind.STATE_GUARDED);
        if (path != null && !path.choices().isEmpty()) result.add(TriggerKind.CHOICE_TRIGGERED);
        if (source.field().startsWith("Options[")) result.add(TriggerKind.DECISION_TRIGGERED);
        if (sourceItem(source) != null) result.add(TriggerKind.EVENT_TRIGGERED);
        if (source.identity().startsWith("StoryPack_Main")) result.add(TriggerKind.STORY_TRIGGERED);
        if (result.isEmpty()) result.add(TriggerKind.PARTIAL);
        return Collections.unmodifiableSet(result);
    }
}
