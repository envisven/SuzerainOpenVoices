package sordland;

import sordland.data.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static sordland.TestSupport.*;

final class LoaderChecks {
    private LoaderChecks() {
    }
    static void run() throws Exception {
        Path directory = Paths.get("build", "test-fixtures");
        Files.createDirectories(directory);
        Path entity = directory.resolve("arbitrary-entity-name.txt"), dialogue = directory.resolve("arbitrary-dialogue-name.txt");
        var sourceEntry = new LinkedHashMap<String, Object>();
        sourceEntry.put("id", 0);
        sourceEntry.put("conversationID", 77);
        sourceEntry.put("ActorID", 10);
        sourceEntry.put("Title", "Narrator: \"A source sentence\"");
        sourceEntry.put("fields", List.of(Map.of("title", "en", "value", "A source sentence")));
        sourceEntry.put("outgoingLinks", List.of());
        var sourceConversation = Map.of("id", 77, "Title", "Sordland/Turn03/Example", "dialogueEntries", List.of(sourceEntry));
        var sourceItem = Map.of("NameInDatabase", "Turn03_Example", "Path", "Sordland/Examples/Example", "ConversationProperties",
            Map.of("Title", "Human title", "Dialogue", "Sordland/Turn03/Example"));
        var riziaItem = Map.of("NameInDatabase", "Turn03_Rizia", "Path", "Rizia/Example", "ConversationProperties",
            Map.of("Title", "Rizia title", "Dialogue", "Rizia/Turn03/Example"));
        var catalogue = Map.of("AllConversationsData", List.of(sourceItem, riziaItem), "AllBillsData", List.of(),
            "AllDecisionsData", List.of());
        try {
            Files.writeString(entity, Json.pretty(catalogue));
            Files.writeString(dialogue, Json.pretty(Map.of("conversations", List.of(sourceConversation))));
            Path actorNames = directory.resolve(Loader.ACTOR_NAMES_FILE);
            try {
                Files.writeString(actorNames, Json.pretty(Map.of("actorNames", Map.of("17", "A source name", "29",
                    Map.of("unsupported", "value")), "extra", "retained")));
                var accounted = Loader.load(entity, dialogue);
                equal(3L, accounted.ignoredData().stream().filter(r -> r.sourceFile().equals(Loader.ACTOR_NAMES_FILE)).count(),
                    "Every actor-name record and unknown root field is retained");
                Files.writeString(actorNames, "{ broken");
                expectRejected(entity, dialogue, "Malformed optional actor source produces explicit load error");
            } finally {
                Files.deleteIfExists(actorNames);
            }
            var data = Loader.load(entity, dialogue);
            equal(1, data.items().size(), "Structure validation accepts valid dumps with arbitrary filenames and excludes Rizia");
            equal(3, data.items().getFirst().turn(), "Synthetic item turn resolved from actual source name");
            equal("A source sentence", data.conversations().get(77).entries().get(0).text(), "Localized English field loaded from actual field structure");
            var sparse = new LinkedHashMap<String, Object>(catalogue);
            sparse.put("AllConversationsData", Map.of("8", sourceItem, "19", riziaItem));
            Files.writeString(entity, Json.pretty(sparse));
            equal("AllConversationsData:8", Loader.load(entity, dialogue).items().getFirst().id(), "Sparse source dictionary key is retained as catalogue identity");
            Files.writeString(entity, Json.pretty(catalogue));
            var secondEntry = new LinkedHashMap<>(sourceEntry);
            secondEntry.put("conversationID", 78);
            var sameTitle = Map.of("id", 78, "Title", "Sordland/Turn03/Example", "dialogueEntries", List.of(secondEntry));
            Files.writeString(dialogue, Json.pretty(Map.of("conversations", List.of(sourceConversation, sameTitle))));
            var ambiguous = Loader.load(entity, dialogue);
            check(ambiguous.items().stream().filter(i -> i.id().startsWith("AllConversationsData")).allMatch(i -> i.conversationId() == null),
                "Ambiguous title reference never selects an arbitrary numeric conversation");
            equal(2, ambiguous.conversations().size(), "Ambiguous titles retain both distinct source conversations");
            Files.writeString(dialogue, Json.pretty(Map.of("conversations", List.of(Map.of("id", 99, "Title", "Rizia/Example",
                "dialogueEntries", List.of())))));
            expectRejected(entity, dialogue, "A Rizia-only conversation file is rejected as the Sordland input");
            Files.writeString(dialogue, Json.pretty(Map.of("conversations", List.of(sourceConversation))));
            Files.writeString(entity, "{\"not_an_entity_dump\": []}");
            expectRejected(entity, dialogue, "Invalid entity schema rejected");
            Files.writeString(entity, Json.pretty(catalogue));
            Files.writeString(dialogue, "{\"not_a_conversation_dump\": []}");
            expectRejected(entity, dialogue, "Invalid conversation schema rejected");
            sourceEntry.put("conversationID", 99);
            Files.writeString(dialogue, Json.pretty(Map.of("conversations", List.of(sourceConversation))));
            expectRejected(entity, dialogue, "Dialogue owner disagreement rejected");
        } finally {
            Files.deleteIfExists(entity);
            Files.deleteIfExists(dialogue);
        }
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
