package sordland.analysis;

import sordland.data.Domain.*;
import sordland.data.Json;
import sordland.data.runtime.RuntimeDatabase;
import sordland.graph.Semantics;
import java.util.*;

public final class VariableIndex {
    public enum Access {
        READ, WRITE, READ_WRITE, REFERENCE, UNRESOLVED
    }
    public enum Proof {
        EXACT, EXPLICIT_STRUCTURE, DIALOGUE_GUARD, UNRESOLVED
    }
    public record Source(String identity, String title, Integer turn, String field, String original, String guard,
        List<String> aliases, CausalProvenance causes) {
        public Source {
            aliases = List.copyOf(aliases);
        }
        public Source(String identity, String title, Integer turn, String field, String original, String guard, List<String> aliases) {
            this(identity, title, turn, field, original, guard, aliases, null);
        }
        public String key() {
            return identity + "/" + field;
        }
    }
    public record Occurrence(String variable, Access access, Proof proof, Source source, int operation, String expression,
        VariableSyntax.Expr condition, VariableSyntax.Effect effect, DialogueGuardResolver.Resolved guardProof) {
        public Occurrence(String variable, Access access, Proof proof, Source source, int operation, String expression,
            VariableSyntax.Expr condition, VariableSyntax.Effect effect) {
            this(variable, access, proof, source, operation, expression, condition, effect, null);
        }
    }
    public record ChoiceProof(String decision, int option, String evidence) {
    }
    private final Map<String, ChoiceProof> choices = new HashMap<>();
    public ChoiceProof choice(Occurrence occurrence) {
        return choices.get(occurrence.source().key());
    }
    private DialogueGuardResolver dialogueGuards;
    private final Map<String, sordland.data.Domain.EntryKey> dialogueSources = new HashMap<>();
    public DialogueGuardResolver.Resolved numericRoutes(Occurrence o) {
        var key = dialogueSources.get(o.source().identity());
        return key == null ? o.guardProof(): dialogueGuards.resolveNumericRoutes(key);
    }
    private final Map<String, List<Occurrence>>occurrences = new TreeMap<>();
    private final Map<String, String> labels = new TreeMap<>();
    private final CausalProvenance provenance;
    public VariableIndex(Dataset data) {
        provenance = new CausalProvenance(data);
        var items = new ArrayList<>(data.items());
        items.addAll(data.ancillary());
        var names = new HashSet<String>();
        var conversations = new HashMap<Integer, Item>();
        for (Item item: items) {
            checkCancelled();
            names.add(item.id().split(":", 2)[0].toLowerCase(Locale.ROOT) + ":" + item.internalName());
            if (item.conversationId() != null) conversations.putIfAbsent(item.conversationId(), item);
            var aliases = data.runtime().byName.getOrDefault(item.internalName(), List.of()).stream().filter(e -> e.graphEligible() &&
                e.collection().equals(item.id().split(":", 2)[0].toLowerCase(Locale.ROOT))).map(RuntimeDatabase.Entity::location).toList();
            String id = "Entity " + item.id() + " · " + item.internalName();
            add(id, item.title(), item.turn(), "StoryFragmentCondition", item.condition(), "", true, aliases);
            add(id, item.title(), item.turn(), "OnStoryFragmentBeginInstruction", item.beginInstruction(), item.condition(),
                false, aliases);
            add(id, item.title(), item.turn(), "OnStoryFragmentEndInstruction", item.endInstruction(), item.condition(),
                false, aliases);
            for (int i = 0; i<item.options().size(); i++) {
                Option option = item.options().get(i);
                String guard = option.condition();
                if (guard.startsWith("Disabled when: ")) guard = "!(" + guard.substring(15) + ")";
                String title = item.title() + " / " + option.title();
                add(id, title, item.turn(), "Options[" + i + "].Condition", option.condition(), "", true, aliases);
                if (Set.of("Decision", "Bill").contains(item.type())) choices.put(id + "/Options[" + i + "].Instruction",
                    new ChoiceProof(id, i, item.type() + " " + item.id() + " / Options[" + i + "] " + option.title() + ": distinct options of one source selection"));
                String combined = combine(item.condition(), guard);
                add(id, title, item.turn(), "Options[" + i + "].Instruction", option.instruction(), combined, false,
                    aliases);
            }
        }
        dialogueGuards = new DialogueGuardResolver(data);
        for (Conversation conversation: data.conversations().values()) {
            Item item = conversations.get(conversation.id());
            for (Entry entry: conversation.entries().values()) {
                checkCancelled();
                String id = "Conversation " + conversation.id() + " / Dialogue " + entry.key().dialogueId();
                dialogueSources.put(id, entry.key());
                String title = conversation.title() + " / Dialogue " + entry.key().dialogueId();
                Integer turn = item == null ? null: item.turn();
                add(id, title, turn, "conditionsString", entry.condition(), "", true, List.of());
                if (!entry.script().isBlank() || !entry.sequence().isBlank()) {
                    var resolved = dialogueGuards.resolve(entry.key());
                    add(id, title, turn, "userScript", entry.script(), entry.condition(), false, List.of(), resolved);
                    add(id, title, turn, "Sequence", entry.sequence(), entry.condition(), false, List.of(), resolved);
                }
            }
        }
        for (Turn turn: data.gameFlow().turns()) {
            String id = "StoryPack_Main / Turns[" + turn.sourceIndex() + "]";
            add(id, turn.transitionTitle(), turn.turnNumber(), "Condition", turn.condition(), "", true, List.of());
            add(id, turn.transitionTitle(), turn.turnNumber(), "OnTurnStartInstruction", turn.onTurnStartInstruction(),
                turn.condition(), false, List.of());
            for (Step step: turn.steps()) add(id + "/Steps[" + step.sourceIndex() + "]", "Step " + step.sourceIndex(),
                turn.turnNumber(), "OnStepStartInstruction", step.onStepStartInstruction(), "", false, List.of());
        }
        var seenRuntime = new HashSet<String>();
        for (var collection: data.runtime().collections.values()) for (var entity: collection) {
            checkCancelled();
            if (!entity.graphEligible()) continue;
            String enabled = entity.value("IsEnabledVariable");
            if (VariableSyntax.variable(enabled)) {
                labels.putIfAbsent(enabled, entity.title());
                add(entity.location(), entity.title(), null, "IsEnabledVariable", enabled, "", null, List.of());
            }
            if (names.contains(entity.collection() + ":" + entity.name())) continue;
            String identity = entity.collection() + ":" +(entity.id().isBlank() ? entity.name(): entity.id());
            if (!seenRuntime.add(identity)) continue;
            runtimeProperties(entity, entity.properties(), "", "");
        }
        panelIncrements(data.runtime());
        occurrences.replaceAll((key, value) -> value.stream().sorted(Comparator.comparing((Occurrence o) -> o.source().turn(),
            Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(o -> o.source().key()).thenComparingInt(Occurrence::operation)).toList());
    }
    private void panelIncrements(RuntimeDatabase runtime) {
        var seen = new HashSet<String>();
        for (var panel: runtime.collection("pageddecisionpanelsdata")) {
            if (!panel.graphEligible()) continue;
            for (Object pageName: RuntimeDatabase.values(panel.properties().get("Pages"))) {
                var page = runtime.resolve(Json.string(pageName), "carouselchoicepagedata", "multiplechoicepagedata");
                if (page == null || !page.graphEligible()) continue;
                for (Object optionName: RuntimeDatabase.values(page.properties().get("Options"))) {
                    var option = runtime.resolve(Json.string(optionName), "carouselchoiceoptiondata", "multiplechoiceoptiondata");
                    if (option == null || !option.graphEligible()) continue;
                    for (String dimension: List.of("Counter", "Bar")) {
                        String variableField = "Panel" + dimension + "Variable";
                        String incrementField = "Panel" + dimension + "Increment";
                        String variable = panel.value(variableField);
                        Object rawIncrement = option.properties().get(incrementField);
                        if (!VariableSyntax.variable(variable) || !(rawIncrement instanceof Number number) || number.doubleValue() == 0) continue;
                        String identity = panel.location() + " / " + option.location();
                        if (!seen.add(identity + incrementField)) continue;
                        String guard = option.value("Condition");
                        var condition = VariableSyntax.parse(guard);
                        var value = VariableSyntax.parse(number.toString());
                        var effect = new VariableSyntax.Effect(variable, "+", value, true);
                        Source source = new Source(identity, panel.title() + " / " + page.title() + " / " + option.title(),
                            null, incrementField, variableField + " = " + variable + "\n" + incrementField + " = " + number + "\nPage Options = " + Json.pretty(page.properties().get("Options")),
                            guard, List.of(page.location()));
                        put(new Occurrence(variable, condition.known() ? Access.READ_WRITE: Access.UNRESOLVED, condition.known() ? Proof.EXPLICIT_STRUCTURE: Proof.UNRESOLVED,
                            source, 0, "Derived from explicit panel/option fields: " + variable + " += " + number,
                            condition, effect));
                    }
                }
            }
        }
    }
    private void runtimeProperties(RuntimeDatabase.Entity entity, Map<String, Object> properties, String path, String parentGuard) {
        String condition = Json.string(properties, "Condition");
        String guard = combine(parentGuard, condition);
        for (var field: properties.entrySet()) {
            String key = field.getKey();
            if (field.getValue() instanceof String value &&(key.endsWith("Condition") || key.endsWith("Instruction"))) {
                add(entity.location() + " · " + entity.name(), entity.title(), null, path + key, value, key.endsWith("Condition") ? "": guard,
                    key.endsWith("Condition"), List.of());
            }
        }
        int i = 0;
        for (Object child: RuntimeDatabase.values(properties.get("ConditionalInstructions"))) {
            runtimeProperties(entity, Json.object(child), path + "ConditionalInstructions[" + i++ + "].", guard);
        }
        for (String field: List.of("PanelCounterVariable", "PanelBarVariable")) {
            String value = Json.string(properties, field);
            if (VariableSyntax.variable(value)) add(entity.location(), entity.title(), null, path + field, value,
                "", null, List.of());
        }
    }
    private static String combine(String a, String b) {
        return a.isBlank() ? b: b.isBlank() ? a: "(" + a + ") && (" + b + ")";
    }
    private void add(String id, String title, Integer turn, String field, String text, String guard, Boolean condition,
        List<String> aliases) {
        add(id, title, turn, field, text, guard, condition, aliases, null);
    }
    private void add(String id, String title, Integer turn, String field, String text, String guard, Boolean condition,
        List<String> aliases, DialogueGuardResolver.Resolved resolved) {
        if (text.isBlank()) return;
        Source source = new Source(id, title, turn, field, text, guard, aliases, provenance);
        VariableSyntax.Expr predicate = VariableSyntax.parse(guard);
        if (condition == null || condition) {
            VariableSyntax.Expr parsed = VariableSyntax.parse(text);
            for (String name: VariableSyntax.references(text)) put(new Occurrence(name, condition == null ? Access.REFERENCE: parsed.known() ? Access.READ: Access.UNRESOLVED,
                condition == null || parsed.known() ? Proof.EXACT: Proof.UNRESOLVED, source, 0, text, parsed, null));
            return;
        }
        int position = 0;
        for (var command: Semantics.analyze(text, "").commands()) {
            int operation = position++;
            var effect = command.kind() == Semantics.CommandKind.EFFECT ? VariableSyntax.effect(command.raw()): null;
            boolean known = effect != null && predicate.known();
            for (String name: VariableSyntax.references(command.raw())) {
                Access access = !known ? Access.UNRESOLVED: name.equals(effect.variable()) ? effect.readsTarget() ? Access.READ_WRITE: Access.WRITE: Access.READ;
                put(new Occurrence(name, access, !known ? Proof.UNRESOLVED: resolved != null && resolved.propagated() ? Proof.DIALOGUE_GUARD: guard.isBlank() ? Proof.EXACT: Proof.EXPLICIT_STRUCTURE,
                    source, operation, command.raw(), predicate, effect, resolved));
            }
        }
    }
    private void put(Occurrence occurrence) {
        occurrences.computeIfAbsent(occurrence.variable(), key -> new ArrayList<>()).add(occurrence);
    }
    public List<String> variables() {
        return List.copyOf(occurrences.keySet());
    }
    public List<Occurrence> occurrences(String name) {
        return occurrences.getOrDefault(name, List.of());
    }
    public String label(String name) {
        return labels.getOrDefault(name, name);
    }
    public static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
    }
}
