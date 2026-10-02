package sordland.data.runtime;

import sordland.data.Domain.*;
import sordland.data.Json;
import java.util.*;

public final class RuntimeDatabase {
    public static final Map<String, String> TYPES = Map.ofEntries(Map.entry("policiesdata", "Policy"), Map.entry("situationsdata",
        "Situation"), Map.entry("reportsdata", "Report"), Map.entry("decreesdata", "Decree"), Map.entry("journalentriesdata",
        "Journal"), Map.entry("tokenstatuseffectsdata", "Token status"), Map.entry("pageddecisionpanelsdata", "Decision panel"));
    public record Entity(String collection, String dataset, String file, int index, Map<String, Object> raw) {
        public Entity {
            raw = Collections.unmodifiableMap(new LinkedHashMap<>(raw));
        }
        public String name() {
            return Json.string(raw, "NameInDatabase");
        }
        public String id() {
            return Json.string(raw, "Id");
        }
        public String path() {
            return Json.string(raw, "Path");
        }
        public List<Object> storyPacks() {
            return values(Json.object(raw.get("AppBundleProperties")).get("StoryPacks"));
        }
        public Map<String, Object> properties() {
            String key = switch (collection) {
                case "pageddecisionpanelsdata" -> "PagedDecisionPanelProperties";
                case "policiesdata" -> "PolicyProperties";
                case "situationsdata" -> "SituationProperties";
                case "reportsdata" -> "ReportProperties";
                case "decreesdata" -> "DecreeProperties";
                case "journalentriesdata" -> "JournalEntryProperties";
                case "tokenstatuseffectsdata" -> "TokenStatusEffectProperties";
                case "conditionalinstructiondata" -> "ConditionalInstructionProperties";
                case "carouselchoicepagedata" -> "CarouselChoicePageProperties";
                case "carouselchoiceoptiondata" -> "CarouselChoiceOptionProperties";
                case "multiplechoicepagedata" -> "MultipleChoicePageProperties";
                case "multiplechoiceoptiondata" -> "MultipleChoiceOptionProperties";
                case "onetimedecreespaneldata" -> "OneTimeDecreesPanelProperties";
                default -> "";
            };
            return Json.object(raw.get(key));
        }
        public String value(String key) {
            return Json.string(properties(), key);
        }
        public boolean graphEligible() {
            return !path().startsWith("Rizia/") && !value("IsEnabledVariable").startsWith("RiziaDLC");
        }
        public String title() {
            String t = value("Title");
            return t.isBlank() ? name(): t;
        }
        public String location() {
            return file + "#$.data.$items[" + index + "]";
        }
        public String metadata() {
            return "Runtime dataset: " + dataset + "\nLogical collection: " + collection + "\nSource: " + location() + "\nId: " + id() + "\nNameInDatabase: " + name() + "\nPath: " + path() + "\nStoryPacks: " + storyPacks() + "\nOriginal runtime object:\n" + Json.pretty(raw);
        }
        public Item item() {
            var source = new LinkedHashMap<>(raw);
            source.put("RuntimeSource", location());
            source.put("RuntimeDataset", dataset);
            return new Item("runtime:" + collection + ":" + index, TYPES.getOrDefault(collection, "Runtime source"),
                title(), name(), path(), null, null, value("Condition"), value("Instruction"), "", value("Description"),
                List.of(), source);
        }
    }
    public final Map<String, List<Entity>>collections;
    public final Map<String, List<Entity>>byName, byId, byEnabledVariable;
    public final Map<String, Object> catalog;
    public final Map<String, Object> sourceFiles;
    public final List<String> diagnostics;
    public RuntimeDatabase(Map<String, List<Entity>>collections, Map<String, Object> catalog, Map<String, Object> sourceFiles,
        List<String> diagnostics) {
        var copy = new LinkedHashMap<String, List<Entity>>();
        collections.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        this.collections = Collections.unmodifiableMap(copy);
        this.catalog = Collections.unmodifiableMap(new LinkedHashMap<>(catalog));
        this.sourceFiles = Collections.unmodifiableMap(new LinkedHashMap<>(sourceFiles));
        this.diagnostics = List.copyOf(diagnostics);
        byName = index(0);
        byId = index(1);
        byEnabledVariable = index(2);
    }
    private Map<String, List<Entity>>index(int kind) {
        var out = new LinkedHashMap<String, List<Entity>>();
        for (var list: collections.values()) for (var e: list) {
            String key = kind == 0 ? e.name(): kind == 1 ? e.id(): TYPES.containsKey(e.collection()) && e.graphEligible() ? e.value("IsEnabledVariable"): "";
            if (!key.isBlank()) out.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        out.replaceAll((k, v) -> List.copyOf(v));
        return Collections.unmodifiableMap(out);
    }
    public static RuntimeDatabase empty() {
        return new RuntimeDatabase(Map.of(), Map.of(), Map.of(), List.of());
    }
    public List<Entity> collection(String name) {
        return collections.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
    }
    public Entity resolve(String name, String...collections) {
        Set<String> allowed = Set.of(collections);
        var matches = byName.getOrDefault(name, List.of()).stream().filter(e -> allowed.contains(e.collection())).toList();
        return matches.size() == 1 ? matches.getFirst(): null;
    }
    public List<Item> items() {
        return collections.entrySet().stream().filter(e -> TYPES.containsKey(e.getKey())).flatMap(e -> e.getValue().stream()).filter(Entity::graphEligible).map(Entity::item).toList();
    }
    public List<IgnoredData> sourceInspection() {
        var records = new ArrayList<IgnoredData>();
        records.add(new IgnoredData("runtime/entity_catalog.json", "Runtime catalog", "", "$", "Runtime catalog",
            "Preferred-member manifest; all member identities preserved.", catalog));
        sourceFiles.forEach((file, raw) -> records.add(new IgnoredData("runtime/" + file, "Runtime source", "", "$",
            file, "Complete preferred source, including unsupported fields and out-of-scope records. Only StoryPack_Main entities enter the runtime graph index.",
            raw)));
        return records;
    }
    public static List<Object> values(Object source) {
        return source instanceof List<?> ? Json.list(source): Json.list(Json.object(source).get("$items"));
    }
}
