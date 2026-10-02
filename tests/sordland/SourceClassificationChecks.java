package sordland;

import sordland.data.*;
import sordland.data.Domain.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static sordland.TestSupport.*;

final class SourceClassificationChecks {
    private SourceClassificationChecks() {
    }
    static void run() throws Exception {
        Path directory = Path.of("build", "test-fixtures");
        Files.createDirectories(directory);
        Path entity = directory.resolve("classification entities exact.json"), dialogue = directory.resolve("classification conversations exact.json");
        Map<String, Object> goodEntity = Map.of("NameInDatabase", "Turn01_Exact", "Path", "Sordland/Conversations",
            "ConversationProperties", Map.of("Title", "Recognized event", "Dialogue", "Sordland/Turn01/Example"),
            "FutureMetadata", Map.of("verbatim", "source"));
        Map<String, Object> incomplete = Map.of("NameInDatabase", "Turn01_Incomplete", "Path", "Sordland/Conversations",
            "ConversationProperties", "unexpected but readable value");
        Map<String, Object> unknown = Map.of("NameInDatabase", "UnsupportedExact", "Path", "Sordland/Unknown", "Payload",
            List.of("keep", 42));
        Map<String, Object> news = Map.of("NameInDatabase", "Turn01_News", "Path", "Sordland/News", "NewsProperties",
            Map.of("Title", "Compact headline", "Description", "Complete article body.", "IsEnabledVariable", "BaseGameSupport.News_Exact",
            "Newspaper", "Newspaper_Test", "TurnNo", 1));
        Map<String, Object> instruction = Map.of("NameInDatabase", "ExactInstruction", "Path", "Sordland/Conditional Instructions",
            "ConditionalInstructionProperties", Map.of("ConditionalInstructions", List.of(Map.of("Condition", "BaseGame.X == true",
            "Instruction", "BaseGame.Y = true;"))));
        Map<String, Object> rizia = Map.of("NameInDatabase", "Rizia_Exact", "Path", "Rizia/Conversations", "ConversationProperties",
            Map.of());
        Map<String, Object> catalogue = new LinkedHashMap<>();
        catalogue.put("AllConversationsData", List.of(goodEntity, incomplete, rizia));
        catalogue.put("AllBillsData", List.of());
        catalogue.put("AllDecisionsData", List.of());
        catalogue.put("NewsData", List.of(news));
        catalogue.put("conditionalInstructionData", List.of(instruction));
        catalogue.put("UnsupportedCollection", Map.of("14", unknown, "29", List.of("readable", false), "_type", "UnknownList"));
        catalogue.put("FutureRootMetadata", Map.of("version", 123, "payload", "retain exactly"));
        catalogue.put("ScalarExtension", "readable extension");
        Map<String, Object> mainStep = Map.of("Fragments", List.of("Turn01_Exact", "Turn01_Incomplete", "Missing_Exact"),
            "FutureStepField", List.of("source", "step"));
        Map<String, Object> mainTurn = Map.of("Steps", List.of(mainStep), "FutureTurnField", "unrepresented turn metadata");
        Map<String, Object> mainFlow = Map.of("StoryPack", "StoryPack_Main", "Turns", List.of(mainTurn), "FutureFlowField",
            Map.of("retained", true));
        catalogue.put("GameFlowData", List.of(mainFlow));
        Map<String, Object> field = Map.of("title", "en", "value", "Visible dialogue", "type", "Text", "FutureFieldMetadata",
            "retained");
        Map<String, Object> unknownField = Map.of("title", "FutureDialogueContent", "value", Map.of("opaque", "preserved"));
        Map<String, Object> incompleteField = Map.of("value", "has no field title");
        Map<String, Object> unresolvedLink = Map.of("originConversationID", 77, "originDialogueID", 0, "destinationConversationID",
            77, "destinationDialogueID", 99, "FutureLinkMetadata", "preserved");
        Map<String, Object> incompleteLink = Map.of("originConversationID", 77, "originDialogueID", 0, "priority",
            "Normal");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", 0);
        entry.put("conversationID", 77);
        entry.put("ActorID", 10);
        entry.put("Title", "Narrator: \"Visible dialogue\"");
        entry.put("fields", List.of(field, unknownField, incompleteField));
        entry.put("outgoingLinks", List.of(unresolvedLink, incompleteLink));
        entry.put("FutureEntryMetadata", List.of("a", "b"));
        Map<String, Object> incompleteEntry = Map.of("id", 99, "conversationID", 77, "Title", "Missing required actor and fields");
        Map<String, Object> sourceConversation = Map.of("id", 77, "Title", "Sordland/Turn01/Example", "dialogueEntries",
            List.of(entry, incompleteEntry, "readable unsupported entry"), "FutureConversationMetadata", Map.of("no",
            "loss"));
        Map<String, Object> badConversation = Map.of("Title", "Sordland/Turn01/Incomplete", "dialogueEntries", List.of());
        Map<String, Object> root = Map.of("conversations", List.of(sourceConversation, badConversation), "FutureDialogueRoot",
            List.of("complete", "metadata"));
        try {
            Files.writeString(entity, Json.pretty(catalogue));
            Files.writeString(dialogue, Json.pretty(root));
            byte[] entityBytes = Files.readAllBytes(entity), dialogueBytes = Files.readAllBytes(dialogue);
            Dataset data = Loader.load(entity, dialogue);
            equal(1, data.items().size(), "Only sufficiently complete supported Sordland catalogue item is rendered");
            equal(1, data.news().size(), "News is supported graphical content, not ignored legacy ancillary data");
            equal("Complete article body.", data.news().getFirst().description(), "Full News article body survives compact-card model");
            equal("BaseGameSupport.News_Exact", Json.string(Json.object(data.news().getFirst().raw().get("NewsProperties")),
                "IsEnabledVariable"), "Exact source news enable variable survives");
            equal(1L, data.ancillary().stream().filter(i -> i.type().equals("Conditional instruction")).count(), "Conditional instruction remains supported PLAIN content");
            check(data.ignoredData().stream().noneMatch(i -> i.identity().equals("Turn01_News") || i.identity().equals("ExactInstruction")),
                "Supported News and conditional instructions are not blanket ignored");
            equal(Json.pretty(unknown), Json.pretty(find(data, "UnsupportedCollection[14]").raw()), "Unsupported numbered collection entry retained verbatim");
            equal("14", find(data, "UnsupportedCollection[14]").sourceIndex(), "Exact unsupported source index retained");
            equal("UnsupportedExact", find(data, "UnsupportedCollection[14]").identity(), "Unsupported source database identity retained");
            equal("classification entities exact.json", find(data, "UnsupportedCollection[14]").sourceFile(), "Exact user-supplied entity filename retained");
            equal("UnsupportedCollection", find(data, "UnsupportedCollection[14]").collection(), "Unsupported source collection retained");
            equal(Json.pretty(List.of("readable", false)), Json.pretty(find(data, "UnsupportedCollection[29]").raw()),
                "Non-object readable record retained as its original JSON type");
            equal(Json.pretty(catalogue.get("FutureRootMetadata")), Json.pretty(find(data, "FutureRootMetadata").raw()),
                "Unknown entity root metadata retained in ignored view");
            equal("readable extension", find(data, "ScalarExtension").raw(), "Readable root scalar cannot disappear");
            check(find(data, "AllConversationsData[1]").reason().contains("Incomplete"), "Incomplete recognized entity has an explicit classification reason");
            equal(Json.pretty(incomplete), Json.pretty(find(data, "AllConversationsData[1]").raw()), "Incomplete entity raw retained");
            check(find(data, "AllConversationsData[2]").reason().contains("Sordland scope"), "Out-of-scope Rizia is retained with a scope reason without gameplay support");
            equal(Json.pretty(goodEntity.get("FutureMetadata")), Json.pretty(data.items().getFirst().raw().get("FutureMetadata")),
                "Unknown graphical entity field survives in complete source metadata");
            List<Fragment> fragments = data.gameFlow().turns().getFirst().steps().getFirst().fragments();
            equal(List.of("Turn01_Exact", "Turn01_Incomplete", "Missing_Exact"), fragments.stream().map(Fragment::name).toList(),
                "Unresolved GameFlow topology retains every exact source occurrence");
            check(fragments.getFirst().resolved() && !fragments.get(1).resolved() && !fragments.get(2).resolved(),
                "Incomplete entity becomes explicit unresolved topology instead of disappearing");
            check(fragments.get(1).diagnostic().contains("Ignored data"), "Incomplete GameFlow target points to retained ignored source");
            check(data.ignoredData().stream().noneMatch(i -> i.identity().equals("Missing_Exact")), "A missing GameFlow target is topology, not fabricated ignored source");
            equal(Json.pretty(mainFlow.get("FutureFlowField")), Json.pretty(find(data, "GameFlowData[0].FutureFlowField").raw()),
                "Unknown schedule metadata is inspectable, not just stored in a hidden domain raw map");
            equal(mainTurn.get("FutureTurnField"), find(data, "GameFlowData[0].Turns[0].FutureTurnField").raw(), "Unknown turn metadata is retained with exact source locator");
            equal(mainStep.get("FutureStepField"), find(data, "GameFlowData[0].Turns[0].Steps[0].FutureStepField").raw(),
                "Unknown step metadata remains inspectable beside normal graph metadata");
            Entry loaded = data.entry(new EntryKey(77, 0));
            equal("Visible dialogue", loaded.text(), "Malformed neighbor records do not prevent a valid dialogue entry from rendering");
            equal(Json.pretty(entry), Json.pretty(loaded.raw()), "Complete original entry preserves unknown fields, duplicate schema metadata, and original outgoing-link records");
            equal(Json.pretty(sourceConversation), Json.pretty(data.conversations().get(77).raw()), "Complete original conversation remains available without rebuilding it on entry inspection");
            equal(new EntryKey(77, 99), loaded.links().getFirst().target(), "Existing unresolved link target is retained exactly");
            equal(1, loaded.links().size(), "No guessed link replaces an incomplete target");
            check(data.diagnostics().stream().anyMatch(s -> s.contains("77:0 -> 77:99")), "Unresolved dialogue topology has explicit diagnostic");
            check(find(data, "conversations[0].dialogueEntries[1]").reason().contains("Incomplete dialogue entry"),
                "Readable incomplete dialogue entry retained with reason");
            equal("readable unsupported entry", find(data, "conversations[0].dialogueEntries[2]").raw(), "Unknown dialogue record retained unchanged");
            equal("classification conversations exact.json", find(data, "conversations[0].dialogueEntries[2]").sourceFile(),
                "Exact dialogue input filename retained");
            equal(Json.pretty(incompleteField), Json.pretty(find(data, "conversations[0].dialogueEntries[0].fields[2]").raw()),
                "Incomplete dialogue content field retained in ignored data");
            equal(Json.pretty(incompleteLink), Json.pretty(find(data, "conversations[0].dialogueEntries[0].outgoingLinks[1]").raw()),
                "Incomplete dialogue link retained with exact location");
            equal(Json.pretty(sourceConversation.get("FutureConversationMetadata")), Json.pretty(find(data, "conversations[0].FutureConversationMetadata").raw()),
                "Unknown conversation metadata is directly inspectable in ignored view");
            equal(Json.pretty(root.get("FutureDialogueRoot")), Json.pretty(find(data, "$.FutureDialogueRoot").raw()),
                "Unknown conversation-file root metadata retained");
            check(find(data, "conversations[1]").reason().contains("Incomplete conversation"), "Readable conversation missing identity retained without blocking valid records");
            check(Arrays.equals(entityBytes, Files.readAllBytes(entity)) && Arrays.equals(dialogueBytes, Files.readAllBytes(dialogue)),
                "Classification never rewrites supplied input files");
            check(data.ignoredData().stream().allMatch(i -> !i.sourceFile().isBlank() && !i.location().isBlank() &&
                !i.reason().isBlank()), "Every ignored record has inspectable provenance and reason");
            Map<String, Object> noopBill = Map.of("NameInDatabase", "NoopBill", "Path", "Sordland/Bills", "BillProperties",
                Map.of("SignVariables", "", "VetoVariables", ""));
            Map<String, Object> incompleteBill = Map.of("NameInDatabase", "IncompleteBill", "Path", "Sordland/Bills",
                "BillProperties", Map.of("SignVariables", ""));
            Map<String, Object> wrongBill = Map.of("NameInDatabase", "WrongBill", "Path", "Sordland/Bills", "BillProperties",
                Map.of("SignVariables", 123, "VetoVariables", ""));
            Map<String, Object> wrongNews = Map.of("NameInDatabase", "WrongNews", "Path", "Sordland/News", "NewsProperties",
                Map.of("Title", "Readable article", "Description", "Retained body", "IsEnabledVariable", Map.of("invalid",
                "variable")));
            Map<String, Object> unplacedNews = Map.of("NameInDatabase", "UnplacedNews", "Path", "Sordland/News", "NewsProperties",
                Map.of("Title", "Unplaced article", "Description", "No enable proof is given."));
            catalogue.put("AllBillsData", List.of(noopBill, incompleteBill, wrongBill));
            catalogue.put("NewsData", List.of(news, wrongNews, unplacedNews));
            Files.writeString(entity, Json.pretty(catalogue));
            Dataset audited = Loader.load(entity, dialogue);
            equal(1L, audited.items().stream().filter(i -> i.type().equals("Bill")).count(), "Only complete Bill actions are exposed; explicit empty-effect no-op Bill remains supported");
            check(audited.items().stream().filter(i -> i.type().equals("Bill")).findFirst().orElseThrow().options().stream().allMatch(o -> o.instruction().isEmpty()),
                "Empty source action strings are preserved as known no-ops");
            check(find(audited, "AllBillsData[1]").reason().contains("VetoVariables"), "Missing Bill effect does not silently become a fabricated no-op");
            check(find(audited, "AllBillsData[2]").reason().contains("SignVariables"), "Wrong-type Bill effect is retained as incomplete source");
            equal(2, audited.news().size(), "Supported News without enable proof stays available in PLAIN; malformed enable value is ignored");
            check(find(audited, "NewsData[1]").reason().contains("IsEnabledVariable"), "A malformed News enable value cannot enter exact causal matching");
            entry.put("fields", List.of(field, Map.of("title", "en", "value", "Conflicting text"), Map.of("title",
                "Sequence", "value", "BaseGame.X = true;"), Map.of("title", "Sequence", "value", "BaseGame.X = false;")));
            Files.writeString(dialogue, Json.pretty(root));
            Dataset duplicateFields = Loader.load(entity, dialogue);
            equal("", duplicateFields.entry(new EntryKey(77, 0)).text(), "Duplicate localized text fields do not arbitrarily select first or last value");
            equal("", duplicateFields.entry(new EntryKey(77, 0)).sequence(), "Duplicate execution sequence fields cannot fabricate a chosen instruction");
            check(find(duplicateFields, "conversations[0].dialogueEntries[0].fields[1]").reason().contains("Ambiguous"),
                "Duplicate source field values remain inspectable with an ambiguity reason");
            equal(Json.pretty(entry), Json.pretty(duplicateFields.entry(new EntryKey(77, 0)).raw()), "Ambiguous dialogue fields preserve the entire original raw list");
            var collision = new LinkedHashMap<>(incomplete);
            collision.put("NameInDatabase", "Turn01_Exact");
            catalogue.put("AllConversationsData", List.of(goodEntity, collision));
            Files.writeString(entity, Json.pretty(catalogue));
            check(!Loader.load(entity, dialogue).gameFlow().turns().getFirst().steps().getFirst().fragments().getFirst().resolved(),
                "Incomplete duplicate source name blocks arbitrary exact-name resolution");
            Files.writeString(entity, "[]");
            expectRejected(entity, dialogue, "Fundamentally invalid entity root remains an explicit loading error");
            Files.writeString(entity, Json.pretty(catalogue));
            Files.writeString(dialogue, "{\"conversations\": \"corrupt structure\"}");
            expectRejected(entity, dialogue, "Fundamentally invalid conversation collection remains an explicit loading error");
        } finally {
            Files.deleteIfExists(entity);
            Files.deleteIfExists(dialogue);
        }
    }
    static void realData(Dataset data) {
        equal(1436, data.news().size(), "All supplied supported News records remain available");
        equal(4L, data.ancillary().stream().filter(i -> i.type().equals("Conditional instruction")).count(), "All supplied Sordland conditional instruction records remain supported");
        check(data.ignoredData().stream().noneMatch(i -> i.reason().startsWith("Incomplete")), "Current complete source requires no incomplete-record fallback");
        check(data.ignoredData().stream().anyMatch(i -> i.reason().contains("Sordland scope")), "Out-of-scope readable source is explicitly retained");
        check(data.conversations().values().stream().allMatch(c -> !c.raw().isEmpty()), "Every actual conversation retains complete raw source");
        check(data.conversations().values().stream().flatMap(c -> c.entries().values().stream()).allMatch(e -> e.raw().containsKey("fields") &&
            e.raw().containsKey("outgoingLinks")), "Every graphical dialogue entry retains original field/link structures");
    }
    private static IgnoredData find(Dataset data, String location) {
        return data.ignoredData().stream().filter(i -> i.location().equals(location)).findFirst().orElseThrow(() -> new AssertionError("Missing retained source: " + location));
    }
    private static void expectRejected(Path entity, Path dialogue, String message) throws Exception {
        boolean rejected = false;
        try {
            Loader.load(entity, dialogue);
        } catch (IOException e) {
            rejected = true;
        }
        check(rejected, message);
    }
}
