package sordland.data.runtime;

import sordland.data.Json;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class RuntimeDatabaseLoader {
    private RuntimeDatabaseLoader() {
    }
    public static RuntimeDatabase load(Path directory) {
        var collections = new LinkedHashMap<String, List<RuntimeDatabase.Entity>>();
        var files = new LinkedHashMap<String, Object>();
        var notes = new ArrayList<String>();
        var catalog = new LinkedHashMap<String, Object>();
        if (!Files.isRegularFile(directory.resolve("entity_catalog.json"))) {
            notes.add("Optional runtime database absent: " + directory + ". Legacy sources remain available.");
            return new RuntimeDatabase(collections, catalog, files, notes);
        }
        try {
            catalog.putAll(Json.object(Json.parse(directory.resolve("entity_catalog.json"))));
            var groups = new LinkedHashMap<String, List<Map<String, Object>>>();
            for (Object value: Json.list(catalog.get("members"))) {
                var member = Json.object(value);
                String key = Json.string(member, "logicalKey").toLowerCase(Locale.ROOT);
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(member);
            }
            for (var group: groups.entrySet()) {
                var preferred = group.getValue().stream().filter(m -> Boolean.TRUE.equals(m.get("preferredForCoverage"))).toList();
                if (preferred.size() != 1) {
                    notes.add("Runtime " + group.getKey() + ": expected one preferredForCoverage member; found " + preferred.size() + ". Not guessed.");
                    continue;
                }
                var member = preferred.getFirst();
                String file = Json.string(member, "outputFile");
                Path path = directory.resolve(file).normalize();
                if (!path.startsWith(directory.normalize())) {
                    notes.add("Invalid runtime catalog path: " + file);
                    continue;
                }
                try {
                    var envelope = Json.object(Json.parse(path));
                    files.put(file, envelope);
                    var records = new ArrayList<RuntimeDatabase.Entity>();
                    var values = RuntimeDatabase.values(envelope.get("data"));
                    int excluded = 0;
                    for (int i = 0; i<values.size(); i++) {
                        var raw = Json.object(values.get(i));
                        var packs = RuntimeDatabase.values(Json.object(raw.get("AppBundleProperties")).get("StoryPacks"));
                        if (!packs.contains("StoryPack_Main")) {
                            excluded++;
                            continue;
                        }
                        if (Json.string(raw, "NameInDatabase").isBlank()) {
                            notes.add("Unnamed runtime record retained at " + file + "[" + i + "]");
                            continue;
                        }
                        var entity = new RuntimeDatabase.Entity(group.getKey(), Json.string(envelope, "dataset"),
                            file, i, raw);
                        records.add(entity);
                        if (RuntimeDatabase.TYPES.containsKey(group.getKey()) && !entity.graphEligible()) notes.add("Conflicting runtime scope: " + entity.name() + " is tagged StoryPack_Main but has Rizia path/variable. Indexed/source-retained, excluded from gameplay rendering.");
                    }
                    collections.put(group.getKey(), records);
                    if (RuntimeDatabase.TYPES.containsKey(group.getKey()) || group.getKey().contains("choice")) notes.add("Runtime " + group.getKey() + ": " + records.size() + " StoryPack_Main records; " + excluded + " outside scope.");
                } catch (IOException | RuntimeException ex) {
                    notes.add("Runtime collection unavailable: " + file + ": " + ex.getMessage());
                }
            }
        } catch (IOException | RuntimeException ex) {
            notes.add("Runtime catalog unavailable: " + ex.getMessage());
        }
        return new RuntimeDatabase(collections, catalog, files, notes);
    }
}
