package sordland.data;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.*;
import static sordland.data.Domain.*;

public final class Loader {
    public static final String ENTITY_FILE = "SuzerainDataDumper.entity_data.json";
    public static final String CONVERSATIONS_FILE = "SuzerainDataDumper.conversations_Sordland.json";
    public static final String ACTOR_NAMES_FILE = "SuzerainDataDumper.actor_names.json";
    private static final Pattern TURN = Pattern.compile("(?:^|[/_ -])Turn(\\d+)(?=[/_ -]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPEAKER = Pattern.compile("^([^:\\n]{1,80}):\\s*\"");
    private static final Set<String> ENTITY_COLLECTIONS = Set.of("AllConversationsData", "AllBillsData", "AllDecisionsData",
        "NewsData", "conditionalInstructionData");
    private Loader() {
    }
    public static Dataset load(Path entityPath, Path conversationsPath) throws IOException {
        List<String> diagnostics = new ArrayList<>();
        List<IgnoredData> ignored = new ArrayList<>();
        String entityFile = entityPath.getFileName().toString();
        Map<String, Object> catalogue = requireObject(Json.parse(entityPath), "Entity root");
        for (String key: List.of("AllConversationsData", "AllBillsData", "AllDecisionsData")) requireCollection(catalogue.get(key),
            "Entity root." + key);
        readActorNames(entityPath.resolveSibling(ACTOR_NAMES_FILE), diagnostics, ignored);
        Map<Integer, Conversation> conversations = readConversations(conversationsPath, diagnostics, ignored);
        Map<String, Conversation> byTitle = new LinkedHashMap<>();
        Set<String> ambiguousTitles = new HashSet<>();
        for (Conversation c: conversations.values()) {
            if (ambiguousTitles.contains(c.title())) continue;
            if (byTitle.containsKey(c.title())) {
                byTitle.remove(c.title());
                ambiguousTitles.add(c.title());
                diagnostics.add("Ambiguous conversation title: " + c.title() + ". Catalogue references to it remain unresolved; numeric source identities stay separate.");
            } else byTitle.put(c.title(), c);
        }
        List<Item> items = new ArrayList<>(), ancillary = new ArrayList<>();
        Set<Integer> referenced = new HashSet<>();
        int excluded = 0;
        for (var collection: catalogue.entrySet()) {
            String key = collection.getKey();
            Object content = collection.getValue();
            if (key.equals("_type") || key.equals("GameFlowData")) continue;
            if (!ENTITY_COLLECTIONS.contains(key)) {
                retainUnsupported(ignored, entityFile, key, content, "Unsupported source entity collection or root metadata; no graphical interpretation is assumed.");
                continue;
            }
            List<SourceValue> values;
            try {
                values = sourceValues(content, key);
            } catch (IOException e) {
                retain(ignored, entityFile, key, "", key, "", e.getMessage(), content);
                continue;
            }
            for (SourceValue value: values) {
                String location = key + "[" + value.key() + "]", id = key + ":" + value.key();
                try {
                    Map<String, Object> raw = requireObject(value.value(), location);
                    if (!isSordland(raw)) {
                        excluded++;
                        retain(ignored, entityFile, key, value.key(), location, identity(raw), "Outside Sordland scope, or no unambiguous Sordland path/story-pack evidence; no gameplay interpretation added.",
                            raw);
                        continue;
                    }
                    Item item = readItem(id, key, raw, byTitle, diagnostics);
                    if (item.conversationId() != null) referenced.add(item.conversationId());
                    if (key.equals("AllConversationsData") || key.equals("AllDecisionsData") || key.equals("AllBillsData")) items.add(item);
                    else ancillary.add(item);
                } catch (IOException e) {
                    retain(ignored, entityFile, key, value.key(), location, identity(Json.object(value.value())),
                        "Incomplete or unsupported entity: " + e.getMessage(), value.value());
                }
            }
        }
        GameFlow gameFlow = readGameFlow(catalogue.get("GameFlowData"), items, ancillary, diagnostics, ignored, entityFile);
        int fragments = 0;
        for (Conversation c: conversations.values()) {
            if (referenced.contains(c.id())) continue;
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("id", c.id());
            raw.put("Title", c.title());
            raw.put("Source", "Conversation database only; no campaign entity was supplied for this graph.");
            raw.put("TurnSource", turn(c.title()) == null ? "Unspecified in source": "Conversation.Title path");
            raw.put("Progression", "Not a GameFlow entity reference; dialogue links do not establish campaign event sequencing.");
            String type = isEnding(c.title()) ? "Ending": "Dialogue fragment";
            items.add(new Item("conversation:" + c.id(), type, lastPart(c.title()), c.title(), c.title(), turn(c.title()),
                c.id(), "", "", "", "No catalogue entity; retained for complete access to supplied Sordland dialogue.",
                List.of(), raw));
            fragments++;
        }
        long entryCount = conversations.values().stream().mapToLong(c -> c.entries().size()).sum();
        diagnostics.add("Loaded " + conversations.size() + " Sordland conversations with " + entryCount + " dialogue entries.");
        diagnostics.add("Loaded " +(items.size() - fragments) + " campaign catalogue items and " + fragments + " additional dialogue graphs; " + ancillary.size() + " additional supported News/conditional-instruction records.");
        diagnostics.add("Excluded " + excluded + " catalogue records outside Sordland scope or without unambiguous Sordland evidence; exact source records remain in Ignored data.");
        diagnostics.add("Retained " + ignored.size() + " readable unsupported, incomplete, or out-of-scope source records in Ignored data. News and conditional instructions remain supported graphical types.");
        diagnostics.add("Speaker names are corroborated from source dialogue titles. Actor-name source indices have no proven dialogue ActorID mapping; every supplied record is retained in Ignored data.");
        var runtime = sordland.data.runtime.RuntimeDatabaseLoader.load(entityPath.resolveSibling("runtime"));
        diagnostics.addAll(runtime.diagnostics);
        return new Dataset(items, conversations, ancillary, diagnostics, gameFlow, ignored, runtime);
    }
    private static void readActorNames(Path path, List<String> diagnostics, List<IgnoredData> ignored) throws IOException {
        if (!Files.exists(path)) {
            diagnostics.add("Optional actor-name source was not supplied: " + path);
            return;
        }
        String file = path.getFileName().toString();
        Map<String, Object> root = requireObject(Json.parse(path), "Actor-name root");
        retainUnknownFields(ignored, file, "Actor-name root", "", "$", root, Set.of("actorNames", "_type"));
        List<SourceValue> values = sourceValues(root.get("actorNames"), "Actor-name root.actorNames");
        for (SourceValue value: values) retain(ignored, file, "actorNames", value.key(), "actorNames[" + value.key() + "]",
            value.value() instanceof String name ? name: "", "No source evidence maps this list position to a dialogue ActorID. Retained without assigning a potentially incorrect speaker identity.",
            value.value());
        diagnostics.add("Accounted for " + values.size() + " actor-name records in Ignored data; serialization _type fields describe source containers, not actor identities.");
    }
    private static GameFlow readGameFlow(Object source, List<Item> items, List<Item> ancillary, List<String> diagnostics,
        List<IgnoredData> ignored, String file) throws IOException {
        if (source == null) {
            diagnostics.add("No GameFlowData was supplied; ROOTED campaign progression is unavailable for this input. PLAIN catalogue and dialogue views remain available.");
            return GameFlow.empty();
        }
        List<IndexedValue> flows = indexedValues(source, "GameFlowData");
        IndexedValue selected = null;
        for (IndexedValue flow: flows) {
            Map<String, Object> raw = requireObject(flow.value(), "GameFlowData[" + flow.index() + "]");
            if (!"StoryPack_Main".equals(Json.string(raw, "StoryPack"))) {
                retain(ignored, file, "GameFlowData", Integer.toString(flow.index()), "GameFlowData[" + flow.index() + "]",
                    Json.string(raw, "StoryPack"), "Out-of-scope or unsupported story-pack schedule; only StoryPack_Main is interpreted.",
                    raw);
                continue;
            }
            if (selected != null) throw new IOException("GameFlowData has more than one StoryPack_Main entry; refusing to choose an ambiguous schedule.");
            selected = flow;
        }
        if (selected == null) {
            diagnostics.add("GameFlowData contains no StoryPack_Main entry; no other story pack is used for Sordland progression.");
            return GameFlow.empty();
        }
        Map<String, List<Item>>byName = new LinkedHashMap<>();
        for (List<Item> collection: List.of(items, ancillary)) for (Item item: collection) byName.computeIfAbsent(item.internalName(),
            key -> new ArrayList<>()).add(item);
        Set<String> incompleteNames = new HashSet<>();
        for (IgnoredData record: ignored) if (ENTITY_COLLECTIONS.contains(record.collection())) {
            Map<String, Object> entity = Json.object(record.raw());
            if (isSordland(entity) && entity.get("NameInDatabase") instanceof String name) incompleteNames.add(name);
        }
        Map<String, Object> raw = requireObject(selected.value(), "StoryPack_Main");
        String flowLocation = "GameFlowData[" + selected.index() + "]";
        retainUnknownFields(ignored, file, "GameFlowData", Integer.toString(selected.index()), flowLocation, raw,
            Set.of("StoryPack", "Turns", "_type"));
        List<Turn> turns = new ArrayList<>();
        int stepCount = 0, fragmentCount = 0, unresolvedCount = 0;
        for (IndexedValue turnValue: indexedValues(raw.get("Turns"), "StoryPack_Main.Turns")) {
            String turnContext = "GameFlowData[" + selected.index() + "].Turns[" + turnValue.index() + "]";
            Map<String, Object> turn = requireObject(turnValue.value(), turnContext);
            retainUnknownFields(ignored, file, "GameFlowData.Turns", Integer.toString(turnValue.index()), turnContext,
                turn, Set.of("Condition", "TransitionTitle", "OnTurnStartInstruction", "Steps", "_type"));
            List<Step> steps = new ArrayList<>();
            for (IndexedValue stepValue: indexedValues(turn.get("Steps"), turnContext + ".Steps")) {
                String stepContext = turnContext + ".Steps[" + stepValue.index() + "]";
                Map<String, Object> step = requireObject(stepValue.value(), stepContext);
                retainUnknownFields(ignored, file, "GameFlowData.Turns.Steps", Integer.toString(stepValue.index()),
                    stepContext, step, Set.of("Fragments", "OnStepStartInstruction", "_type"));
                List<Fragment> fragments = new ArrayList<>();
                for (IndexedValue fragmentValue: indexedValues(step.get("Fragments"), stepContext + ".Fragments")) {
                    if (!(fragmentValue.value() instanceof String name)) throw new IOException(stepContext + ".Fragments[" + fragmentValue.index() + "] must be a fragment ID string.");
                    List<Item> candidates = byName.getOrDefault(name, List.of());
                    Item resolved = candidates.size() == 1 && !incompleteNames.contains(name) ? candidates.getFirst(): null;
                    String diagnostic = "";
                    if (resolved == null) {
                        diagnostic = "Unresolved GameFlow fragment " + name + " at " + stepContext + ".Fragments[" + fragmentValue.index() + "]: " +(incompleteNames.contains(name) ? "an exact Sordland entity is incomplete or unsupported; inspect Ignored data. No partial or ambiguous entity was guessed.": candidates.isEmpty() ? "no loaded Sordland entity has this exact NameInDatabase.": "ambiguous exact NameInDatabase matches " + candidates.stream().map(Item::id).toList() + "; no entity was guessed.");
                        diagnostics.add(diagnostic);
                        unresolvedCount++;
                    }
                    fragments.add(new Fragment(fragmentValue.index(), name, resolved, diagnostic));
                    fragmentCount++;
                }
                steps.add(new Step(stepValue.index(), sourceText(step, "OnStepStartInstruction", stepContext), fragments,
                    step));
                stepCount++;
            }
            turns.add(new Turn(turnValue.index(), sourceText(turn, "Condition", turnContext), sourceText(turn, "TransitionTitle",
                turnContext), sourceText(turn, "OnTurnStartInstruction", turnContext), steps, turn));
        }
        diagnostics.add("Loaded StoryPack_Main GameFlow: " + turns.size() + " turns, " + stepCount + " steps, " + fragmentCount + " fragment references; " + unresolvedCount + " unresolved. Source Turn → Step → Fragment order is authoritative; it does not by itself prove a specific event parent.");
        return new GameFlow("StoryPack_Main", selected.index(), turns, raw);
    }
    private record IndexedValue(int index, Object value) {
    }
    private static List<IndexedValue> indexedValues(Object value, String context) throws IOException {
        requireCollection(value, context);
        List<IndexedValue> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (int i = 0; i<list.size(); i++) result.add(new IndexedValue(i, list.get(i)));
        } else {
            Set<Integer> seen = new HashSet<>();
            for (var entry: Json.object(value).entrySet()) {
                if (entry.getKey().equals("_type")) continue;
                int index;
                try {
                    index = Integer.parseInt(entry.getKey());
                } catch (NumberFormatException e) {
                    throw new IOException(context + " source index is outside the supported integer range: " + entry.getKey(),
                        e);
                }
                if (!seen.add(index)) throw new IOException(context + " contains ambiguous numeric source index " + index);
                result.add(new IndexedValue(index, entry.getValue()));
            }
            result.sort(Comparator.comparingInt(IndexedValue::index));
        }
        return result;
    }
    private static String sourceText(Map<String, Object> raw, String key, String context) throws IOException {
        return raw.containsKey(key) ? requiredString(raw, key, context): "";
    }
    private static Map<Integer, Conversation> readConversations(Path path, List<String> diagnostics, List<IgnoredData> ignored) throws IOException {
        String file = path.getFileName().toString();
        Map<String, Object> root = requireObject(Json.parse(path), "Conversation root");
        List<SourceValue> values = sourceValues(root.get("conversations"), "Conversation root.conversations");
        if (values.isEmpty()) throw new IOException("Conversation database has no conversations.");
        retainUnknownFields(ignored, file, "Conversation root", "", "$", root, Set.of("conversations", "_type"));
        Map<Integer, Map<String, Integer>>names = new LinkedHashMap<>();
        for (SourceValue value: values) {
            Map<String, Object> c = Json.object(value.value());
            if (!Json.string(c, "Title").startsWith("Sordland/")) continue;
            List<SourceValue> entryValues;
            try {
                entryValues = sourceValues(c.get("dialogueEntries"), "Conversation.dialogueEntries");
            } catch (IOException e) {
                continue;
            }
            for (SourceValue ev: entryValues) {
                Map<String, Object> e = Json.object(ev.value());
                Matcher matcher = SPEAKER.matcher(Json.string(e, "Title"));
                Object actor = e.get("ActorID");
                if (actor instanceof Number n && n.doubleValue() == n.intValue() && matcher.find() && !matcher.group(1).equalsIgnoreCase("Jump to")) names.computeIfAbsent(n.intValue(),
                    x -> new LinkedHashMap<>()).merge(matcher.group(1), 1, Integer::sum);
            }
        }
        Map<Integer, String> actors = new HashMap<>();
        for (var e: names.entrySet()) {
            String best = e.getValue().entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
            actors.put(e.getKey(), best);
            if (e.getValue().size()>1) diagnostics.add("Actor " + e.getKey() + " has multiple source speaker names: " + e.getValue() + "; each spoken entry preserves its own label.");
        }
        Map<Integer, Conversation> result = new LinkedHashMap<>();
        for (SourceValue value: values) {
            String location = "conversations[" + value.key() + "]";
            Map<String, Object> raw;
            String title;
            int cid;
            List<SourceValue> entryValues;
            try {
                raw = requireObject(value.value(), location);
                title = requiredString(raw, "Title", location);
                if (!title.startsWith("Sordland/")) {
                    retain(ignored, file, "conversations", value.key(), location, identity(raw), "Outside Sordland conversation scope; no dialogue interpretation added.",
                        raw);
                    diagnostics.add("Excluded non-Sordland conversation " + title + "; retained in Ignored data.");
                    continue;
                }
                cid = requiredInt(raw, "id", location);
                entryValues = sourceValues(raw.get("dialogueEntries"), location + ".dialogueEntries");
            } catch (IOException e) {
                retain(ignored, file, "conversations", value.key(), location, identity(Json.object(value.value())),
                    "Incomplete conversation: " + e.getMessage(), value.value());
                continue;
            }
            retainUnknownFields(ignored, file, "conversations", value.key(), location, raw, Set.of("id", "Title",
                "dialogueEntries", "_type"));
            Map<Integer, Entry> entries = new LinkedHashMap<>();
            for (SourceValue ev: entryValues) {
                String entryLocation = location + ".dialogueEntries[" + ev.key() + "]";
                try {
                    Map<String, Object> e = requireObject(ev.value(), entryLocation);
                    int did = requiredInt(e, "id", entryLocation);
                    int owner = requiredInt(e, "conversationID", entryLocation);
                    if (owner != cid) throw new CorruptTopology("Entry " + cid + ":" + did + " declares conversationID " + owner);
                    List<SourceValue> fieldValues = sourceValues(e.get("fields"), entryLocation + ".fields");
                    List<SourceValue> linkValues = sourceValues(e.get("outgoingLinks"), entryLocation + ".outgoingLinks");
                    Map<String, Object> fields = new LinkedHashMap<>();
                    Set<String> ambiguousFields = new HashSet<>();
                    for (SourceValue fv: fieldValues) {
                        String fieldLocation = entryLocation + ".fields[" + fv.key() + "]";
                        try {
                            Map<String, Object> f = requireObject(fv.value(), fieldLocation);
                            String field = requiredString(f, "title", fieldLocation);
                            if (fields.containsKey(field) || ambiguousFields.contains(field)) {
                                fields.remove(field);
                                ambiguousFields.add(field);
                                retain(ignored, file, "dialogueEntries.fields", fv.key(), fieldLocation, field, "Ambiguous duplicate field title; none of its values is interpreted. Complete field list remains in entry source metadata.",
                                    fv.value());
                                diagnostics.add("Ambiguous dialogue field " + field + " at " + file + ":" + entryLocation + "; no value was guessed.");
                                continue;
                            }
                            if (!f.containsKey("value")) throw new IOException("Field value is missing.");
                            if (Set.of("en", "Dialogue Text", "Menu Text en", "Menu Text", "Sequence", "Sequence en").contains(field) &&
                                !(f.get("value") instanceof String)) throw new IOException("Rendered text/sequence field " + field + " must have a string value.");
                            fields.put(field, f.get("value"));
                        } catch (IOException ex) {
                            retain(ignored, file, "dialogueEntries.fields", fv.key(), fieldLocation, cid + ":" + did,
                                "Incomplete dialogue field: " + ex.getMessage(), fv.value());
                        }
                    }
                    List<Link> links = new ArrayList<>();
                    int order = 0;
                    for (SourceValue lv: linkValues) {
                        String linkLocation = entryLocation + ".outgoingLinks[" + lv.key() + "]";
                        int sourceOrder = order++;
                        try {
                            Map<String, Object> link = requireObject(lv.value(), linkLocation);
                            if (requiredInt(link, "originConversationID", linkLocation) != cid || requiredInt(link,
                                "originDialogueID", linkLocation) != did) throw new CorruptTopology("Outgoing link origin disagrees with entry " + cid + ":" + did);
                            links.add(new Link(new EntryKey(requiredInt(link, "destinationConversationID", linkLocation),
                                requiredInt(link, "destinationDialogueID", linkLocation)), sourceOrder, Json.string(link,
                                "priority"), Boolean.TRUE.equals(link.get("isConnector"))));
                        } catch (CorruptTopology ex) {
                            throw ex;
                        } catch (IOException ex) {
                            retain(ignored, file, "dialogueEntries.outgoingLinks", lv.key(), linkLocation, cid + ":" + did,
                                "Incomplete dialogue link: " + ex.getMessage(), lv.value());
                            diagnostics.add("Unresolved unreadable link target at " + file + ":" + linkLocation + "; retained in Ignored data.");
                        }
                    }
                    int actor = requiredInt(e, "ActorID", entryLocation);
                    String etitle = sourceText(e, "Title", entryLocation);
                    Matcher label = SPEAKER.matcher(etitle);
                    String speaker = label.find() && !label.group(1).equalsIgnoreCase("Jump to") ? label.group(1): actors.getOrDefault(actor,
                        "Actor " + actor);
                    String text = prefer(Json.string(fields, "en"), Json.string(fields, "Dialogue Text"));
                    String menu = prefer(Json.string(fields, "Menu Text en"), Json.string(fields, "Menu Text"));
                    String sequence = join(Json.string(fields, "Sequence"), Json.string(fields, "Sequence en"));
                    Entry entry = new Entry(new EntryKey(cid, did), actor, speaker, etitle, text, menu, sourceText(e,
                        "conditionsString", entryLocation), sourceText(e, "userScript", entryLocation), sequence,
                        links, e);
                    if (entries.putIfAbsent(did, entry) != null) throw new CorruptTopology("Duplicate dialogue identity " + cid + ":" + did);
                } catch (CorruptTopology ex) {
                    throw ex;
                } catch (IOException ex) {
                    retain(ignored, file, "dialogueEntries", ev.key(), entryLocation, identity(Json.object(ev.value())),
                        "Incomplete dialogue entry: " + ex.getMessage(), ev.value());
                    diagnostics.add("Dialogue entry could not be rendered at " + file + ":" + entryLocation + "; retained in Ignored data.");
                }
            }
            if (result.putIfAbsent(cid, new Conversation(cid, title, entries, raw)) != null) throw new IOException("Duplicate conversation id " + cid);
        }
        if (result.isEmpty()) throw new IOException("Conversation database contains no usable Sordland conversations. Readable out-of-scope/incomplete records cannot provide a Sordland dialogue graph.");
        int missing = 0, cross = 0;
        for (Conversation c: result.values()) for (Entry e: c.entries().values()) for (Link l: e.links()) {
            Conversation dest = result.get(l.target().conversationId());
            if (dest == null || !dest.entries().containsKey(l.target().dialogueId())) {
                missing++;
                diagnostics.add("Unresolved source dialogue link " + e.key() + " -> " + l.target());
            }
            if (l.target().conversationId() != e.key().conversationId()) cross++;
        }
        if (missing>0) diagnostics.add("Unresolved dialogue links: " + missing + " (all retained with exact targets).");
        diagnostics.add("Source cross-conversation dialogue links: " + cross + ". These include subdialogue calls/returns; they do not establish campaign order.");
        return result;
    }
    private static final class CorruptTopology extends IOException {
        CorruptTopology(String message) {
            super(message);
        }
    }
    private record SourceValue(String key, Object value) {
    }
    private static List<SourceValue> sourceValues(Object value, String context) throws IOException {
        requireCollection(value, context);
        List<SourceValue> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (int i = 0; i<list.size(); i++) result.add(new SourceValue(Integer.toString(i), list.get(i)));
        } else {
            Json.object(value).entrySet().stream().filter(e -> !e.getKey().equals("_type")).sorted(Comparator.comparing(e -> new java.math.BigInteger(e.getKey()))).forEach(e -> result.add(new SourceValue(e.getKey(),
                e.getValue())));
        }
        return result;
    }
    private static void retain(List<IgnoredData> ignored, String file, String collection, String index, String location,
        String identity, String reason, Object raw) {
        ignored.add(new IgnoredData(file, collection, index, location, identity, reason, raw));
    }
    private static String identity(Map<String, Object> raw) {
        String name = Json.string(raw, "NameInDatabase");
        if (!name.isBlank()) return name;
        String title = Json.string(raw, "Title");
        String id = Json.string(raw, "id");
        return title +(id.isBlank() ? "": " [id=" + id + "]");
    }
    private static void retainUnknownFields(List<IgnoredData> ignored, String file, String collection, String index,
        String location, Map<String, Object> raw, Set<String> known) {
        for (var field: raw.entrySet()) if (!known.contains(field.getKey())) retain(ignored, file, collection, index,
            location + "." + field.getKey(), identity(raw), "Unsupported source metadata field; retained without a graphical interpretation.",
            field.getValue());
    }
    private static void retainUnsupported(List<IgnoredData> ignored, String file, String collection, Object raw, String reason) {
        try {
            List<SourceValue> values = sourceValues(raw, collection);
            if (values.isEmpty()) {
                retain(ignored, file, collection, "", collection, "", reason, raw);
                return;
            }
            for (SourceValue value: values) retain(ignored, file, collection, value.key(), collection + "[" + value.key() + "]",
                identity(Json.object(value.value())), reason, value.value());
        } catch (IOException e) {
            retain(ignored, file, collection, "", collection, "", reason, raw);
        }
    }
    private static Item readItem(String id, String collection, Map<String, Object> raw, Map<String, Conversation> conversations,
        List<String> diagnostics) throws IOException {
        String name = requiredString(raw, "NameInDatabase", collection), path = Json.string(raw, "Path");
        if (name.isBlank()) throw new IOException(collection + ".NameInDatabase must not be blank.");
        Map<String, Object> story = raw.containsKey("StoryFragmentProperties") ? requireObject(raw.get("StoryFragmentProperties"),
            collection + ".StoryFragmentProperties"): Map.of();
        for (String key: List.of("StoryFragmentCondition", "OnStoryFragmentBeginInstruction", "OnStoryFragmentEndInstruction")) sourceText(story,
            key, collection + ".StoryFragmentProperties");
        String type, propertiesName;
        switch (collection) {
            case "AllConversationsData" -> {
                type = isEnding(name) ? "Ending": "Conversation";
                propertiesName = "ConversationProperties";
            }
            case "AllBillsData" -> {
                type = "Bill";
                propertiesName = "BillProperties";
            }
            case "AllDecisionsData" -> {
                type = "Decision";
                propertiesName = "DecisionProperties";
            }
            case "NewsData" -> {
                type = "News";
                propertiesName = "NewsProperties";
            }
            case "conditionalInstructionData" -> {
                type = "Conditional instruction";
                propertiesName = "ConditionalInstructionProperties";
            }
            default -> {
                type = Json.string(raw, "_type");
                propertiesName = "";
            }
        }
        Map<String, Object> p = propertiesName.isEmpty() ? Map.of(): requireObject(raw.get(propertiesName), collection + "." + propertiesName);
        for (String key: List.of("Title", "HubTitle", "Description", "Dialogue")) sourceText(p, key, collection + "." + propertiesName);
        if (type.equals("News")) for (String key: List.of("IsEnabledVariable", "Newspaper")) sourceText(p, key, collection + "." + propertiesName);
        String title = prefer(Json.string(p, "Title"), Json.string(p, "HubTitle"));
        if (title.isBlank() || title.equals("-")) title = name;
        String dialogue = Json.string(p, "Dialogue");
        Integer cid = null;
        if (!dialogue.isBlank()) {
            Conversation c = conversations.get(dialogue);
            if (c == null) diagnostics.add("Unresolved catalogue dialogue reference: " + name + " -> " + dialogue);
            else cid = c.id();
        }
        Integer turn = turn(name);
        String turnSource = turn == null ? "Unspecified in source": "NameInDatabase";
        Integer dialogueTurn = turn(dialogue);
        if (turn == null && dialogueTurn != null) {
            turn = dialogueTurn;
            turnSource = "ConversationProperties.Dialogue path";
        }
        if (turn != null && dialogueTurn != null && !turn.equals(dialogueTurn)) diagnostics.add("Conflicting turn labels for " + name + ": name=" + turn + ", dialogue=" + dialogueTurn);
        if (p.get("TurnNo") instanceof Number n) {
            turn = n.intValue();
            turnSource = propertiesName + ".TurnNo";
        }
        if (turn == null && turn(path) != null) {
            turn = turn(path);
            turnSource = "Path";
        }
        List<Option> options = new ArrayList<>();
        if (type.equals("Bill")) {
            options.add(new Option("SIGN", "", requiredString(p, "SignVariables", name + ".BillProperties")));
            String disabled = sourceText(p, "IsVetoDisabledCondition", name + ".BillProperties");
            options.add(new Option("VETO", disabled.isBlank() ? "": "Disabled when: " + disabled, requiredString(p,
                "VetoVariables", name + ".BillProperties")));
        } else if (type.equals("Decision")) {
            requireCollection(p.get("Options"), name + ".DecisionProperties.Options");
            for (Object ov: Json.list(p.get("Options"))) {
                Map<String, Object> o = requireObject(ov, "DecisionOption");
                options.add(new Option(requiredString(o, "Text", "DecisionOption"), sourceText(o, "Condition", "DecisionOption"),
                    requiredString(o, "Instruction", "DecisionOption")));
            }
        } else if (type.equals("Conditional instruction")) {
            requireCollection(p.get("ConditionalInstructions"), name + ".ConditionalInstructionProperties.ConditionalInstructions");
            for (Object ov: Json.list(p.get("ConditionalInstructions"))) {
                Map<String, Object> o = requireObject(ov, "ConditionalInstruction");
                options.add(new Option("Instruction " +(options.size() + 1), requiredString(o, "Condition", "ConditionalInstruction"),
                    requiredString(o, "Instruction", "ConditionalInstruction")));
            }
        }
        Map<String, Object> metadata = new LinkedHashMap<>(raw);
        metadata.putIfAbsent("TurnSource", turnSource);
        return new Item(id, type, title, name, path, turn, cid, Json.string(story, "StoryFragmentCondition"), Json.string(story,
            "OnStoryFragmentBeginInstruction"), Json.string(story, "OnStoryFragmentEndInstruction"), Json.string(p,
            "Description"), options, metadata);
    }
    private static boolean isSordland(Map<String, Object> raw) {
        String path = Json.string(raw, "Path"), dialogue = Json.string(Json.object(raw.get("ConversationProperties")),
            "Dialogue");
        List<Object> packs = Json.list(Json.object(raw.get("AppBundleProperties")).get("StoryPacks"));
        if (path.startsWith("Rizia/") || dialogue.startsWith("Rizia/")) return false;
        if (path.startsWith("Sordland/") || dialogue.startsWith("Sordland/")) return true;
        return packs.contains("StoryPack_Main") && !packs.contains("StoryPack_Rizia");
    }
    public static Integer turn(String value) {
        Matcher m = TURN.matcher(value);
        return m.find() ? Integer.valueOf(m.group(1)): null;
    }
    private static boolean isEnding(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        return l.contains("ending") || l.contains("epilogue");
    }
    private static String lastPart(String s) {
        return s.substring(s.lastIndexOf('/') + 1);
    }
    private static String prefer(String first, String second) {
        return first.isBlank() ? second: first;
    }
    private static String join(String first, String second) {
        if (first.isBlank()) return second;
        if (second.isBlank() || first.equals(second)) return first;
        return first + "\n" + second;
    }
    private static Map<String, Object> requireObject(Object value, String context) throws IOException {
        if (!(value instanceof Map<?,?>)) throw new IOException(context + " must be a JSON object.");
        return Json.object(value);
    }
    private static void requireCollection(Object value, String context) throws IOException {
        if (!(value instanceof Map<?,?> || value instanceof List<?>)) throw new IOException(context + " must be a numbered object or JSON array.");
        if (value instanceof Map<?,?> map) for (Object key: map.keySet()) if (!key.equals("_type") && !String.valueOf(key).matches("\\d+")) throw new IOException(context + " has a non-numeric entry key: " + key);
    }
    private static String requiredString(Map<String, Object> map, String key, String context) throws IOException {
        if (!(map.get(key) instanceof String s)) throw new IOException(context + "." + key + " must be a string.");
        return s;
    }
    private static int requiredInt(Map<String, Object> map, String key, String context) throws IOException {
        if (!(map.get(key) instanceof Number n) || n.doubleValue() != n.intValue()) throw new IOException(context + "." + key + " must be an integer.");
        return n.intValue();
    }
}
