package sordland;

import sordland.data.Domain.*;
import sordland.data.Json;
import sordland.graph.*;
import java.util.*;
import static sordland.TestSupport.*;

final class NewsChecks {
    private NewsChecks() {
    }
    static void run(Dataset actual) {
        Item news = news("Article", "BaseGameSupport.News_Exact", 9);
        for (String instruction: List.of("BaseGameSupport.News_Exact = true;", "Variable[\"BaseGameSupport.News_Exact\"] = true;",
            "Variable [ 'BaseGameSupport.News_Exact' ] = true;", "EnableNews(\"Article\");", "-- comment\nBaseGameSupport.News_Exact = true;")) equal(List.of(news),
            NewsGraphBuilder.evidence(instruction, List.of(news)).stream().map(NewsGraphBuilder.Evidence::news).toList(),
            "Exact complete source enable statement resolves an article: " + instruction);
        for (String instruction: List.of("BaseGameSupport.News_Exact = false;", "BaseGameSupport.News_Exact == true;",
            "BaseGameSupport.News_Exact = \"true\";", "BaseGameSupport.News_Exact = 1;", "BaseGameSupport.News_ExactSimilar = true;",
            "Other.News_Exact = true;", "Variable[\"Other.News_Exact\"] = true;", "Variable[\"BaseGameSupport.News_Exact\"] = false;",
            "EnableNews(\"article\");", "EnableNews(\"ArticleSimilar\");", "EnableNews(OtherVariable);", "Print(\"BaseGameSupport.News_Exact = true\");",
            "Other.Message = \"BaseGameSupport.News_Exact = true;\";", "-- BaseGameSupport.News_Exact = true;", "// BaseGameSupport.News_Exact = true;",
            "--[[ BaseGameSupport.News_Exact = true; ]]", "if BaseGame.Ready then BaseGameSupport.News_Exact = true; end",
            "BaseGameSupport.News_Exact = true; if BaseGame.Ready then BaseGameSupport.News_Exact = false; end", "BaseGameSupport.News_Exact = true; BaseGameSupport.News_Exact = false;",
            "BaseGameSupport.News_Exact = true; Variable[\"BaseGameSupport.News_Exact\"] = Compute();", "BaseGameSupport.News_Exact = true; Variable[\"BaseGameSupport.News_Exact\"] += 1;",
            "End(); BaseGameSupport.News_Exact = true;", "UnknownCall(); BaseGameSupport.News_Exact = true;", "BaseGameSupport.News_Exact = true; BaseGame.Unrelated = MutateNews();",
            "-- not executable \\nBaseGameSupport.News_Exact = true;")) equal(0, NewsGraphBuilder.evidence(instruction,
            List.of(news)).size(), "No news relationship inferred from unsupported/negative/textual source: " + instruction);
        equal(1, NewsGraphBuilder.evidence("BaseGame.Unrelated = 1;\\nBaseGameSupport.News_Exact = true;", List.of(news)).size(),
            "Double-serialized instruction newline recognized outside quoted strings");
        equal(0, NewsGraphBuilder.evidence("Other.Message = \"ignored\\nBaseGameSupport.News_Exact = true;\";", List.of(news)).size(),
            "Literal escaped newline inside a string cannot create a fake statement");
        Item duplicate = new Item("duplicate", "News", news.title(), news.internalName(), news.path(), news.turn(),
            null, "", "", "", news.description(), List.of(), news.raw());
        equal(0, NewsGraphBuilder.evidence("EnableNews(\"Article\");", List.of(news, duplicate)).size(), "Ambiguous duplicate database names cannot prove a named news call");
        equal(2, NewsGraphBuilder.evidence("BaseGameSupport.News_Exact = true;", List.of(news, duplicate)).size(),
            "An exact shared enable variable preserves both explicitly activated records");
        graphFixtures(news);
        Graph graph = new RootedCampaignGraphBuilder().build(actual, null);
        var funeral = graph.nodes.stream().filter(n -> n.item != null && n.item.internalName().equals("Turn02_HP_FuneralPoem3")).toList();
        check(!funeral.isEmpty(), "Actual FuneralPoem3 NewsData article is represented in ROOTED");
        for (var article: funeral) {
            equal("NEWS · They Are Afraid of Hope But Our Songs Will Be Sung", article.title, "Real article preserves authoritative title");
            equal("Turn02_HP_FuneralPoem3", article.text, "News body is compact database identity only");
            check(!article.text.contains("Huge crowds") && article.metadata.contains("Huge crowds"), "Full article belongs to source metadata, never graph body");
            check(article.source != null, "Optional dialogue enabling carries its exact EntryKey provenance");
            Entry source = actual.entry(article.source);
            check(source != null && source.script().contains("Variable[\"BaseGameSupport.News_Turn02_HP_FuneralPoem3\"] = true"),
                "Actual source pointer resolves the exact enabling write");
            var attachment = graph.campaign.news().stream().filter(a -> a.newsIds().contains(article.id)).findFirst().orElseThrow();
            var effect = node(graph, attachment.effectId());
            var anchor = node(graph, attachment.eventId());
            equal(Graph.Kind.EFFECT, effect.kind, "News source evidence has effect semantics");
            equal("News", effect.type, "Attachment-only evidence follows News visibility, independently of Condition");
            check(effect.title.contains("POSSIBLE") && effect.metadata.contains("does not guarantee"), "Optional dialogue source never claims guaranteed event completion");
            equal(anchor.turn, article.turn, "News layout uses immediate enabler turn, not newspaper release turn");
            check(graph.edges.stream().noneMatch(e -> e.from.equals(article.id)), "News annotation never becomes a campaign progression prerequisite");
            check(graph.edges.stream().anyMatch(e -> e.from.equals(effect.id) && e.to.equals(article.id)), "Source effect points to compact News card");
        }
        check(graph.campaign.news().size()>0, "Full rooted model includes supported News annotations");
        check(graph.nodes.stream().filter(n -> n.type.equals("News") && n.kind == Graph.Kind.EVENT).allMatch(n -> n.item != null &&
            n.text.equals(n.item.internalName())), "All rooted News cards retain Item for unchanged full details and compact graph identity");
    }
    private static void graphFixtures(Item news) {
        Item event = new Item("event", "Conversation", "Event", "Event", "Sordland/Conversations", 1, 42, "", "BaseGameSupport.News_Exact = true;",
            "", "", List.of(), Map.of());
        EntryKey start = new EntryKey(42, 0), possible = new EntryKey(42, 1), detached = new EntryKey(42, 2);
        var entries = new LinkedHashMap<Integer, Entry>();
        entries.put(0, entry(start, "START", "", List.of(new Link(possible, 0, "Normal", false))));
        entries.put(1, entry(possible, "Option effect", "Variable[\"BaseGameSupport.News_Exact\"] = true;", List.of()));
        entries.put(2, entry(detached, "Unreachable stale effect", "BaseGameSupport.News_Exact = true;", List.of()));
        var step = new Step(0, "BaseGameSupport.News_Exact = true;", List.of(new Fragment(0, "Event", event, "")),
            Map.of());
        var turn = new Turn(0, "", "Chapter", "EnableNews(\"Article\");", List.of(step), Map.of());
        var data = new Dataset(List.of(event), Map.of(42, new Conversation(42, "Sordland/Test", entries)), List.of(news),
            List.of(), new GameFlow("StoryPack_Main", 0, List.of(turn), Map.of()));
        Graph graph = new RootedCampaignGraphBuilder().build(data, null);
        equal(4, graph.campaign.news().size(), "Exact begin, reachable dialogue, turn and step instruction evidence all appear");
        check(graph.nodes.stream().noneMatch(n -> n.type.equals("News") && Objects.equals(detached, n.source)), "Unreachable dialogue component cannot imply an event's enabling effect");
        check(graph.nodes.stream().filter(n -> n.type.equals("News")).allMatch(n -> Objects.equals(1, n.turn)), "All annotation display turns follow the enabler even when NewsData declares turn 9");
        check(graph.nodes.stream().filter(n -> n.type.equals("News") && n.item != null).allMatch(n -> Objects.equals(9,
            n.item.turn())), "Source newspaper turn is preserved on original Item");
        equal(1, graph.campaign.levels().size(), "News leaves do not add invented GameFlow levels");
        equal(1, graph.campaign.levels().getFirst().eventIds().size(), "News never becomes an authoritative step fragment");
        Item unattached = news("Unattached", "BaseGameSupport.Unwritten", 1);
        var noEvidence = new Dataset(List.of(event), Map.of(), List.of(unattached), List.of(), data.gameFlow());
        Graph conservative = new RootedCampaignGraphBuilder().build(noEvidence, null);
        equal(0, conservative.campaign.news().size(), "No matching exact source evidence creates no causal placement");
        check(conservative.diagnostics.stream().anyMatch(s -> s.contains("Unattached") && s.contains("PLAIN")), "Supported unplaced News remains explicitly discoverable in PLAIN");
    }
    private static Graph.Node node(Graph graph, String id) {
        return graph.nodes.stream().filter(n -> n.id.equals(id)).findFirst().orElseThrow();
    }
    private static Entry entry(EntryKey key, String title, String script, List<Link> links) {
        return new Entry(key, 10, "Narrator", title, "", "", "", script, "", links, Map.of());
    }
    private static Item news(String name, String variable, int turn) {
        return new Item("news:" + name, "News", "Article title", name, "Sordland/News", turn, null, "", "", "", "Complete article text that must never be placed in the compact card.",
            List.of(), Map.of("NameInDatabase", name, "NewsProperties", Map.of("IsEnabledVariable", variable, "Newspaper",
            "Test newspaper", "Description", "Complete article text")));
    }
}
