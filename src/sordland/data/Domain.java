package sordland.data;

import java.util.*;

public final class Domain {
    private Domain() {
    }
    public record EntryKey(int conversationId, int dialogueId) {
        @Override
        public String toString() {
            return conversationId + ":" + dialogueId;
        }
    }
    public record Link(EntryKey target, int order, String priority, boolean connector) {
    }
    public record Entry(EntryKey key, int actorId, String speaker, String title, String text, String menuText, String condition,
        String script, String sequence, List<Link> links, Map<String, Object> raw) {
        public Entry {
            links = List.copyOf(links);
            raw = immutableMap(raw);
        }
        public boolean isPlayer() {
            return actorId == 5 || actorId == 6 || speaker.equals("Player") || speaker.equals("Player_Italic");
        }
        public boolean isNarrator() {
            return actorId == 10 || speaker.equalsIgnoreCase("Narrator");
        }
    }
    public record Conversation(int id, String title, Map<Integer, Entry> entries, Map<String, Object> raw) {
        public Conversation {
            entries = immutableMap(entries);
            raw = immutableMap(raw);
        }
        public Conversation(int id, String title, Map<Integer, Entry> entries) {
            this(id, title, entries, Map.of());
        }
    }
    public record Option(String title, String condition, String instruction) {
    }
    public record Item(String id, String type, String title, String internalName, String path, Integer turn, Integer conversationId,
        String condition, String beginInstruction, String endInstruction, String description, List<Option> options,
        Map<String, Object> raw) {
        public Item {
            options = List.copyOf(options);
            raw = immutableMap(raw);
        }
    }
    public record GameFlow(String storyPack, int sourceIndex, List<Turn> turns, Map<String, Object> raw) {
        public GameFlow {
            turns = List.copyOf(turns);
            raw = immutableMap(raw);
        }
        public static GameFlow empty() {
            return new GameFlow("", -1, List.of(), Map.of());
        }
    }
    public record Turn(int sourceIndex, String condition, String transitionTitle, String onTurnStartInstruction, List<Step> steps,
        Map<String, Object> raw) {
        public Turn {
            steps = List.copyOf(steps);
            raw = immutableMap(raw);
        }
        public int turnNumber() {
            return sourceIndex + 1;
        }
    }
    public record Step(int sourceIndex, String onStepStartInstruction, List<Fragment> fragments, Map<String, Object> raw) {
        public Step {
            fragments = List.copyOf(fragments);
            raw = immutableMap(raw);
        }
    }
    public record Fragment(int sourceIndex, String name, Item item, String diagnostic) {
        public boolean resolved() {
            return item != null;
        }
        public String id() {
            return name;
        }
    }
    public record IgnoredData(String sourceFile, String collection, String sourceIndex, String location, String identity,
        String reason, Object raw) {
    }
    public record Dataset(List<Item> items, Map<Integer, Conversation> conversations, List<Item> ancillary, List<String> diagnostics,
        GameFlow gameFlow, List<IgnoredData> ignoredData, sordland.data.runtime.RuntimeDatabase runtime) {
        public Dataset {
            runtime = Objects.requireNonNull(runtime);
            items = List.copyOf(items);
            conversations = immutableMap(conversations);
            ancillary = List.copyOf(ancillary);
            diagnostics = List.copyOf(diagnostics);
            gameFlow = Objects.requireNonNull(gameFlow);
            ignoredData = List.copyOf(ignoredData);
        }
        public Dataset(List<Item> items, Map<Integer, Conversation> conversations, List<Item> ancillary, List<String> diagnostics,
            GameFlow gameFlow, List<IgnoredData> ignoredData) {
            this(items, conversations, ancillary, diagnostics, gameFlow, ignoredData, sordland.data.runtime.RuntimeDatabase.empty());
        }
        public Dataset(List<Item> items, Map<Integer, Conversation> conversations, List<Item> ancillary, List<String> diagnostics,
            GameFlow gameFlow) {
            this(items, conversations, ancillary, diagnostics, gameFlow, List.of());
        }
        public Dataset(List<Item> items, Map<Integer, Conversation> conversations, List<Item> ancillary, List<String> diagnostics) {
            this(items, conversations, ancillary, diagnostics, GameFlow.empty());
        }
        public Entry entry(EntryKey key) {
            Conversation c = conversations.get(key.conversationId());
            return c == null ? null: c.entries().get(key.dialogueId());
        }
        public List<Item> news() {
            return ancillary.stream().filter(item -> item.type().equals("News")).toList();
        }
    }
    private static<K, V> Map<K, V> immutableMap(Map<K, V> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }
}
