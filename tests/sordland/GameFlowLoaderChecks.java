package sordland;

import sordland.data.*;
import sordland.data.Domain.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static sordland.TestSupport.*;

final class GameFlowLoaderChecks {
    private GameFlowLoaderChecks() {
    }
    static void run(Path dataDirectory, Dataset data) throws Exception {
        GameFlow flow = data.gameFlow();
        equal("StoryPack_Main", flow.storyPack(), "Sordland scheduler selected by exact StoryPack");
        equal(0, flow.sourceIndex(), "Scheduler source index retained");
        equal(11, flow.turns().size(), "All eleven supplied Main turns retained");
        equal(132, flow.turns().stream().mapToInt(t -> t.steps().size()).sum(), "All 132 source steps retained");
        equal(227, flow.turns().stream().flatMap(t -> t.steps().stream()).mapToInt(s -> s.fragments().size()).sum(),
            "All 227 source fragment occurrences retained");
        Map<String, Object> root = Json.object(Json.parse(dataDirectory.resolve(Loader.ENTITY_FILE)));
        Map<String, Object> sourceFlow = Json.list(root.get("GameFlowData")).stream().map(Json::object).filter(f -> "StoryPack_Main".equals(f.get("StoryPack"))).findFirst().orElseThrow();
        equal(sourceFlow, flow.raw(), "Complete scheduler source metadata retained");
        List<Object> sourceTurns = Json.list(sourceFlow.get("Turns"));
        for (int ti = 0; ti<sourceTurns.size(); ti++) {
            Turn turn = flow.turns().get(ti);
            Map<String, Object> rawTurn = Json.object(sourceTurns.get(ti));
            equal(ti, turn.sourceIndex(), "Turn source index retained");
            equal(ti + 1, turn.turnNumber(), "Turn display numbering derived from zero-based source index");
            equal(rawTurn, turn.raw(), "Complete turn metadata retained");
            equal(Json.string(rawTurn, "Condition"), turn.condition(), "Raw turn condition retained");
            equal(Json.string(rawTurn, "TransitionTitle"), turn.transitionTitle(), "Raw transition title retained");
            equal(Json.string(rawTurn, "OnTurnStartInstruction"), turn.onTurnStartInstruction(), "Raw turn instruction retained");
            List<Object> sourceSteps = Json.list(rawTurn.get("Steps"));
            equal(sourceSteps.size(), turn.steps().size(), "No source step disappears");
            for (int si = 0; si<sourceSteps.size(); si++) {
                Step step = turn.steps().get(si);
                Map<String, Object> rawStep = Json.object(sourceSteps.get(si));
                equal(si, step.sourceIndex(), "Step source index retained");
                equal(rawStep, step.raw(), "Complete step metadata retained");
                equal(Json.string(rawStep, "OnStepStartInstruction"), step.onStepStartInstruction(), "Raw step instruction retained");
                List<Object> sourceFragments = Json.list(rawStep.get("Fragments"));
                equal(sourceFragments.size(), step.fragments().size(), "No fragment occurrence disappears");
                for (int fi = 0; fi<sourceFragments.size(); fi++) {
                    Fragment fragment = step.fragments().get(fi);
                    equal(fi, fragment.sourceIndex(), "Fragment source position retained");
                    equal(sourceFragments.get(fi), fragment.name(), "Exact fragment identifier and source order retained");
                    check(fragment.resolved(), "Every supplied Main fragment resolves: " + fragment.name());
                    equal(fragment.name(), fragment.item().internalName(), "Resolution is exact NameInDatabase equality");
                    check(data.items().contains(fragment.item()), "Actual Main fragments resolve to existing campaign Item objects");
                    equal("", fragment.diagnostic(), "Resolved fragment has no unresolved diagnostic");
                    if (fragment.item().type().equals("Conversation")) check(fragment.item().conversationId() != null,
                        "Scheduled conversation still maps to its dialogue graph");
                }
            }
        }
        equal("Turn01_Start_Inauguration", flow.turns().getFirst().steps().getFirst().fragments().getFirst().name(),
            "Inauguration occupies immediate first source level");
        Turn third = flow.turns().get(2);
        equal("Trials of Democracy", third.transitionTitle(), "Source Turn 3 chapter title retained");
        equal(List.of("Turn03_Decision_Extraction"), third.steps().get(4).fragments().stream().map(Fragment::name).toList(),
            "Extraction decision precedes the branch step");
        equal(List.of("Turn03_A_AddressTheProtestors", "Turn03_Personal_HelicopterEscape"), third.steps().get(5).fragments().stream().map(Fragment::name).toList(),
            "Both Gasom alternatives retain exact source step order");
        check(data.ancillary().stream().noneMatch(i -> i.id().startsWith("GameFlowData:")), "GameFlow schedule never becomes ancillary catalogue records");
        Path actorPath = dataDirectory.resolve(Loader.ACTOR_NAMES_FILE);
        check(Files.isRegularFile(actorPath), "Actor-name source is packaged under its canonical name");
        byte[] actorBytes = Files.readAllBytes(actorPath);
        Map<String, Object> actorRoot = Json.object(Json.parse(actorPath));
        check(actorRoot.get("actorNames") instanceof Map<?,?>, "Packaged actorNames keeps the source numbered-list structure");
        Map<String, Object> actorNames = Json.object(actorRoot.get("actorNames"));
        List<Object> names = Json.list(actorNames);
        equal(103, names.size(), "All supplied actor-name list entries are available for future use");
        for (int i = 0; i<names.size(); i++) {
            check(actorNames.containsKey(Integer.toString(i)), "Exact actor-name list index retained: " + i);
            check(names.get(i) instanceof String name && !name.isBlank(), "Actor-name list entry is a nonempty source string: " + i);
            equal(actorNames.get(Integer.toString(i)), names.get(i), "Actor-name list follows exact numeric source order: " + i);
        }
        equal("Ovid Grecer", names.getFirst(), "First actor-name source value retained verbatim");
        equal("Player", names.get(4), "Actor-name list position is preserved without treating it as dialogue ActorID");
        equal("Narrator", names.get(9), "Actor-name source label retained verbatim");
        equal("Marcel Koronti (R)", names.getLast(), "Packaged source remains complete without rewriting out-of-scope entries");
        check(Arrays.equals(actorBytes, Files.readAllBytes(actorPath)), "Actor-name source bytes remain unchanged after parse validation");
        fixtures();
    }
    private static void fixtures() throws Exception {
        Path directory = Path.of("build", "test-fixtures");
        Files.createDirectories(directory);
        Path entity = directory.resolve("gameflow-entity.json"), dialogue = directory.resolve("gameflow-dialogue.json");
        Map<String, Object> entry = Map.of("id", 0, "conversationID", 77, "ActorID", 10, "Title", "Narrator: \"Source\"",
            "fields", List.of(), "outgoingLinks", List.of());
        Map<String, Object> conversation = Map.of("id", 77, "Title", "Sordland/Turn01/Exact", "dialogueEntries", List.of(entry));
        Map<String, Object> a = item("Exact_A", "Sordland/ExactA"), b = item("Exact_B", "Sordland/ExactB");
        Map<String, Object> duplicate1 = item("Duplicate", "Sordland/One"), duplicate2 = item("Duplicate", "Sordland/Two");
        Map<String, Object> excluded = item("RiziaOnly", "Rizia/Only");
        Map<String, Object> fragments = new LinkedHashMap<>();
        fragments.put("10", "Exact_B");
        fragments.put("0", "Exact_A");
        fragments.put("3", "Missing_Exact_ID");
        fragments.put("4", "Duplicate");
        fragments.put("5", "exact_a");
        fragments.put("6", "RiziaOnly");
        fragments.put("7", "Missing_Exact_ID");
        fragments.put("_type", "List<String>");
        Map<String, Object> step = Map.of("Fragments", fragments, "OnStepStartInstruction", "BaseGame.Step = true;",
            "UnfamiliarSourceField", "preserved");
        Map<String, Object> steps = new LinkedHashMap<>();
        steps.put("12", Map.of("Fragments", List.of("Exact_B")));
        steps.put("2", step);
        Map<String, Object> turn = Map.of("Condition", "BaseGame.Turn == true", "TransitionTitle", "Raw<br>Chapter",
            "OnTurnStartInstruction", "BaseGame.TurnStarted = true;", "Steps", steps);
        Map<String, Object> turns = new LinkedHashMap<>();
        turns.put("9", Map.of("Steps", List.of(Map.of("Fragments", List.of("Exact_A")))));
        turns.put("2", turn);
        Map<String, Object> main = Map.of("StoryPack", "StoryPack_Main", "Turns", turns);
        Map<String, Object> flows = new LinkedHashMap<>();
        flows.put("8", Map.of("StoryPack", "StoryPack_Rizia", "Turns", "ignored non-Main structure"));
        flows.put("5", main);
        Map<String, Object> catalogue = new LinkedHashMap<>();
        catalogue.put("AllConversationsData", List.of(a, b, duplicate1, duplicate2, excluded));
        catalogue.put("AllBillsData", List.of());
        catalogue.put("AllDecisionsData", List.of());
        catalogue.put("GameFlowData", flows);
        try {
            Files.writeString(dialogue, Json.pretty(Map.of("conversations", List.of(conversation))));
            Files.writeString(entity, Json.pretty(catalogue));
            Dataset data = Loader.load(entity, dialogue);
            GameFlow flow = data.gameFlow();
            equal(5, flow.sourceIndex(), "Sparse scheduler source index retained");
            equal(List.of(2, 9), flow.turns().stream().map(Turn::sourceIndex).toList(), "Sparse turns follow numeric source position, independent of dictionary insertion order");
            Turn first = flow.turns().getFirst();
            equal(3, first.turnNumber(), "Sparse source turn index is not renumbered from list length");
            equal(List.of(2, 12), first.steps().stream().map(Step::sourceIndex).toList(), "Sparse step positions retain numeric source order");
            equal("BaseGame.Turn == true", first.condition(), "Conditional turn source text preserved");
            equal("Raw<br>Chapter", first.transitionTitle(), "Chapter text remains raw in domain");
            equal("BaseGame.TurnStarted = true;", first.onTurnStartInstruction(), "Turn instruction preserved without execution");
            Step firstStep = first.steps().getFirst();
            equal("BaseGame.Step = true;", firstStep.onStepStartInstruction(), "Step instruction preserved without execution");
            equal("preserved", firstStep.raw().get("UnfamiliarSourceField"), "Unknown metadata survives loading");
            equal(List.of(0, 3, 4, 5, 6, 7, 10), firstStep.fragments().stream().map(Fragment::sourceIndex).toList(),
                "Sparse fragment indices remain exact");
            equal(List.of("Exact_A", "Missing_Exact_ID", "Duplicate", "exact_a", "RiziaOnly", "Missing_Exact_ID",
                "Exact_B"), firstStep.fragments().stream().map(Fragment::name).toList(), "No unresolved or repeated source occurrence is dropped");
            check(firstStep.fragments().getFirst().item() == data.items().getFirst(), "Resolution reuses original Item rather than duplicating it");
            equal(5L, firstStep.fragments().stream().filter(f -> !f.resolved()).count(), "Missing, ambiguous, case-mismatched and excluded fragments all remain unresolved");
            for (Fragment fragment: firstStep.fragments()) if (!fragment.resolved()) {
                check(fragment.diagnostic().contains(fragment.name()), "Every unresolved diagnostic identifies the exact fragment");
                check(fragment.diagnostic().contains("Fragments[" + fragment.sourceIndex() + "]"), "Every unresolved occurrence identifies its own source location");
                check(data.diagnostics().contains(fragment.diagnostic()), "Every unresolved occurrence has a dataset diagnostic");
            }
            check(firstStep.fragments().get(2).diagnostic().contains("ambiguous"), "Duplicate NameInDatabase never selects an arbitrary entity");
            equal(0, data.ancillary().size(), "GameFlowData is skipped by ancillary entity parser");
            equal(0, flow.turns().get(1).steps().getFirst().sourceIndex(), "Array step indices preserved");
            equal(0, flow.turns().get(1).steps().getFirst().fragments().getFirst().sourceIndex(), "Array fragment indices preserved");
            catalogue.put("GameFlowData", List.of(main, main));
            Files.writeString(entity, Json.pretty(catalogue));
            expectRejected(entity, dialogue, "Multiple exact Main schedules are rejected instead of guessed");
            catalogue.put("GameFlowData", List.of(Map.of("StoryPack", "StoryPack_Main", "Turns", List.of(Map.of("Steps",
                List.of(Map.of("Fragments", List.of(123))))))));
            Files.writeString(entity, Json.pretty(catalogue));
            expectRejected(entity, dialogue, "Non-string source fragment cannot silently disappear");
            catalogue.put("GameFlowData", List.of(Map.of("StoryPack", "StoryPack_Rizia", "Turns", List.of())));
            Files.writeString(entity, Json.pretty(catalogue));
            Dataset other = Loader.load(entity, dialogue);
            check(other.gameFlow().turns().isEmpty(), "No Rizia schedule is substituted for missing Main");
            check(other.diagnostics().stream().anyMatch(s -> s.contains("no StoryPack_Main")), "Missing Main diagnostic is explicit");
            catalogue.remove("GameFlowData");
            Files.writeString(entity, Json.pretty(catalogue));
            Dataset legacy = Loader.load(entity, dialogue);
            check(legacy.gameFlow().turns().isEmpty() && !legacy.items().isEmpty(), "Legacy catalogue inputs remain usable without fabricating progression");
        } finally {
            Files.deleteIfExists(entity);
            Files.deleteIfExists(dialogue);
        }
    }
    private static Map<String, Object> item(String name, String path) {
        return Map.of("NameInDatabase", name, "Path", path, "ConversationProperties", Map.of("Title", name, "Dialogue",
            ""));
    }
    private static void expectRejected(Path entity, Path dialogue, String message) throws Exception {
        boolean rejected = false;
        try {
            Loader.load(entity, dialogue);
        } catch (IOException expected) {
            rejected = true;
        }
        check(rejected, message);
    }
}
