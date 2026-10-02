package sordland;

import java.nio.file.*;
import java.util.*;
import sordland.data.*;
import sordland.data.Domain.*;
import static sordland.TestSupport.*;

final class SourceAccountingChecks {
    static void run(Path directory, Dataset data) throws Exception {
        var entities = Json.object(Json.parse(directory.resolve(Loader.ENTITY_FILE)));
        var supported = new HashMap<String, Item>();
        data.items().forEach(i -> supported.put(i.id(), i));
        data.ancillary().forEach(i -> supported.put(i.id(), i));
        long total = 0, consumed = 0, ignored = 0;
        for (var collection: entities.entrySet()) {
            if (collection.getKey().equals("_type")) {
                check(collection.getValue() instanceof String, "Entity serialization descriptor accounted");
                continue;
            }
            long ct = 0, cc = 0, ci = 0;
            for (var record: Json.object(collection.getValue()).entrySet()) {
                if (record.getKey().equals("_type")) continue;
                total++;
                ct++;
                String location = collection.getKey() + "[" + record.getKey() + "]";
                Item item = supported.get(collection.getKey() + ":" + record.getKey());
                Object retained = item == null ? null: item.raw();
                if (collection.getKey().equals("GameFlowData") && Integer.toString(data.gameFlow().sourceIndex()).equals(record.getKey())) retained = data.gameFlow().raw();
                if (retained != null) {
                    var raw = Json.object(record.getValue());
                    var actual = Json.object(retained);
                    for (var field: raw.entrySet()) equal(field.getValue(), actual.get(field.getKey()), "Every supported entity field retained at " + location + "." + field.getKey());
                    consumed++;
                    cc++;
                } else {
                    var match = data.ignoredData().stream().filter(r -> r.sourceFile().equals(Loader.ENTITY_FILE) &&
                        r.location().equals(location) && Objects.equals(r.raw(), record.getValue())).toList();
                    equal(1, match.size(), "Exactly one complete ignored entity at " + location);
                    ignored++;
                    ci++;
                }
            }
            System.out.printf("ACCOUNT %s/%s: total=%d consumed=%d retained=%d unaccounted=0%n", Loader.ENTITY_FILE,
                collection.getKey(), ct, cc, ci);
        }
        equal(total, consumed + ignored, "All entity records accounted independently");
        var conversations = Json.object(Json.object(Json.parse(directory.resolve(Loader.CONVERSATIONS_FILE))).get("conversations"));
        long entries = 0, fields = 0, links = 0, count = 0;
        for (var record: conversations.entrySet()) {
            if (record.getKey().equals("_type")) continue;
            count++;
            var raw = Json.object(record.getValue());
            var c = data.conversations().get(((Number) raw.get("id")).intValue());
            check(c != null, "Every supplied Sordland conversation consumed");
            equal(raw, c.raw(), "Whole conversation retained including every unsupported field");
            for (var e: Json.list(raw.get("dialogueEntries"))) {
                var eraw = Json.object(e);
                entries++;
                var entry = c.entries().get(((Number) eraw.get("id")).intValue());
                check(entry != null, "Every current source entry is represented");
                equal(eraw, entry.raw(), "Every original entry field and link retained losslessly");
                fields += Json.list(eraw.get("fields")).size();
                links += Json.list(eraw.get("outgoingLinks")).size();
            }
        }
        System.out.printf("ACCOUNT %s: conversations=%d entries=%d fields=%d links=%d; all consumed/raw-retained; unaccounted=0%n",
            Loader.CONVERSATIONS_FILE, count, entries, fields, links);
        var names = Json.object(Json.object(Json.parse(directory.resolve(Loader.ACTOR_NAMES_FILE))).get("actorNames"));
        int actors = 0;
        for (var record: names.entrySet()) {
            if (record.getKey().equals("_type")) continue;
            actors++;
            String location = "actorNames[" + record.getKey() + "]";
            var matches = data.ignoredData().stream().filter(r -> r.sourceFile().equals(Loader.ACTOR_NAMES_FILE) &&
                r.location().equals(location)).toList();
            equal(1, matches.size(), "Each actor source record retained once without guessed numbering");
            equal(record.getValue(), matches.getFirst().raw(), "Actor source value exact");
        }
        System.out.printf("ACCOUNT %s: total=%d consumed=0 retained=%d rejected=0 unaccounted=0%n", Loader.ACTOR_NAMES_FILE,
            actors, actors);
    }
}
