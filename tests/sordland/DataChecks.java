package sordland;

import sordland.data.*;
import sordland.data.Domain.*;
import sordland.graph.*;
import sordland.layout.LayoutEngine;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static sordland.TestSupport.*;

final class DataChecks {
    private DataChecks() {
    }
    static Dataset run(Path dataDirectory) throws Exception {
        Path entity = dataDirectory.resolve(Loader.ENTITY_FILE), conversations = dataDirectory.resolve(Loader.CONVERSATIONS_FILE);
        String entityHash = sha256(entity), conversationHash = sha256(conversations);
        Dataset data = Loader.load(entity, conversations);
        equal(286, data.conversations().size(), "All supplied Sordland conversation graphs retained");
        equal(83_454L, data.conversations().values().stream().mapToLong(c -> c.entries().size()).sum(), "Every source dialogue entry retained");
        equal(366, data.items().size(), "229 catalogue items and 137 uncatalogued dialogue graphs retained");
        equal(1440, data.ancillary().size(), "1436 news and 4 conditional instruction records retained");
        equal(22L, data.items().stream().filter(i -> i.type().equals("Bill")).count(), "22 Sordland bills");
        equal(56L, data.items().stream().filter(i -> i.type().equals("Decision")).count(), "56 Sordland decisions");
        check(data.conversations().values().stream().allMatch(c -> c.title().startsWith("Sordland/")), "Rizia conversation graphs excluded");
        check(data.items().stream().noneMatch(i -> i.path().startsWith("Rizia/")), "Rizia campaign items excluded");
        equal(134, data.conversations().get(6).entries().size(), "Inauguration graph entry count");
        equal(241, data.conversations().get(9).entries().size(), "Inaugural Ball graph entry count");
        equal(new EntryKey(1, 246), data.entry(new EntryKey(1, 3)).links().getFirst().target(), "Exact dump link 1:3 → 1:246");
        check(data.entry(new EntryKey(1, 348)).script().contains("End()"), "Known End() source marker retained");
        int crossConversation = 0;
        for (var c: data.conversations().values()) for (var e: c.entries().values()) {
            int expectedOrder = 0;
            for (var link: e.links()) {
                equal(expectedOrder++, link.order(), "Outgoing link order preserved");
                check(data.entry(link.target()) != null, "Every actual supplied outgoing link resolves: " + e.key() + " → " + link.target());
                if (e.key().conversationId() != link.target().conversationId()) crossConversation++;
            }
        }
        equal(249, crossConversation, "All cross-conversation links preserved");
        var infrastructure = data.items().stream().filter(i -> i.internalName().equals("Turn01_Decision_InfrastructureProject")).findFirst().orElseThrow();
        equal(1, infrastructure.turn(), "Infrastructure decision source turn");
        equal(2, infrastructure.options().size(), "Infrastructure decision includes both source options");
        check(infrastructure.options().getFirst().instruction().contains("GovernmentBudget"), "First decision option preserves budget mutation");
        check(infrastructure.options().get(1).instruction().contains("GameCondition."), "Second decision option retains other namespaces");
        for (var bill: data.items().stream().filter(i -> i.type().equals("Bill")).toList()) equal(List.of("SIGN",
            "VETO"), bill.options().stream().map(Option::title).toList(), "Bills expose source sign/veto actions");
        var election = data.items().stream().filter(i -> Integer.valueOf(244).equals(i.conversationId()) && !i.id().startsWith("conversation:")).toList();
        equal(2, election.size(), "Separate ElectionSpeech catalogue identities retained despite shared graph");
        check(!election.getFirst().id().equals(election.get(1).id()) && !election.getFirst().condition().equals(election.get(1).condition()),
            "Shared conversation does not erase distinct entity predicates");
        var endings = data.items().stream().filter(i -> i.internalName().startsWith("Turn_Endings_")).toList();
        equal(9, endings.size(), "Nine ending catalogue records retained");
        check(endings.stream().allMatch(i -> Integer.valueOf(11).equals(i.turn()) && i.conversationId() != null),
            "Ending turn resolves from source dialogue path and inner graph remains available");
        var campaign = new CampaignGraphBuilder().build(data, "", "All types", null, false);
        var ids = new HashMap<String, Graph.Node>();
        campaign.nodes.forEach(n -> ids.put(n.id, n));
        equal(data.items().size(),(int) campaign.nodes.stream().filter(n -> n.kind == Graph.Kind.EVENT).count(), "Campaign presents every interactive item/uncatalogued graph");
        check(campaign.edges.stream().noneMatch(e -> ids.get(e.from).kind == Graph.Kind.EVENT && ids.get(e.to).kind == Graph.Kind.EVENT),
            "PLAIN remains a flat catalogue without event progression edges");
        equal("StoryPack_Main", data.gameFlow().storyPack(), "Authoritative Sordland GameFlow is available independently of the PLAIN catalogue");
        var engine = new LayoutEngine();
        var compact = engine.campaign(campaign, Set.of(), new sordland.ui.TextMeasurer());
        LayoutChecks.assertGeometry(compact, "Complete actual campaign");
        LayoutChecks.assertTurns(compact);
        Set<String> expanded = new HashSet<>();
        campaign.nodes.stream().filter(n -> n.kind == Graph.Kind.EVENT).forEach(n -> expanded.add(n.id));
        var detailed = engine.campaign(campaign, expanded, new sordland.ui.TextMeasurer());
        LayoutChecks.assertGeometry(detailed, "Complete campaign with all metadata expanded");
        LayoutChecks.assertTurns(detailed);
        equal(entityHash, sha256(entity), "Entity source bytes unchanged after load/build/layout");
        equal(conversationHash, sha256(conversations), "Conversation source bytes unchanged after load/build/layout");
        return data;
    }
    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65536];
        try (InputStream input = Files.newInputStream(path)) {
            for (int n;(n = input.read(buffer)) >= 0;) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
