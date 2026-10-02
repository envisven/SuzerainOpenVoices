package sordland.graph;

import sordland.data.Domain.*;
import sordland.data.Json;
import java.util.*;

public final class CampaignGraphBuilder {
    public Graph build(Dataset data, String query, String type, Integer turn, boolean ancillary) {
        var nodes = new ArrayList<Graph.Node>();
        var edges = new ArrayList<Graph.Edge>();
        var items = new ArrayList<>(data.items());
        if (ancillary) {
            items.addAll(data.ancillary());
            items.addAll(data.runtime().items());
        }
        String q = query == null ? "": query.toLowerCase(Locale.ROOT).trim();
        for (Item item: items) {
            if (!q.isEmpty() && !(item.title() + " " + item.internalName() + " " + item.path() + " " + item.condition()).toLowerCase(Locale.ROOT).contains(q)) continue;
            if (type != null && !type.equals("All types") && !item.type().equals(type)) continue;
            if (turn != null && !Objects.equals(turn, item.turn())) continue;
            String id = "item:" + item.id();
            var conversation = Json.object(item.raw().get("ConversationProperties"));
            boolean isStart = Boolean.TRUE.equals(conversation.get("IsOnStart"));
            String metadata = "Type: " + item.type() + "\nTurn: " +(item.turn() == null ? "Unspecified by source": item.turn()) + "\nDatabase name: " + item.internalName() + "\nPath: " + item.path() +(item.conversationId() == null ? "": "\nConversation ID: " + item.conversationId()) + "\nSource identity: " + item.id() + "\nTurn provenance: " + Json.string(item.raw(),
                "TurnSource") +(conversation.isEmpty() ? "": "\nDialogue path: " + Json.string(conversation, "Dialogue") + "\nCategory: " + Json.string(conversation,
                "TypeString") + "\nIsOnStart: " + isStart) +(item.condition().isBlank() ? "": "\nActivation: " + display(item.condition())) +(item.beginInstruction().isBlank() ? "": "\nOn begin: " + display(item.beginInstruction())) +(item.endInstruction().isBlank() ? "": "\nOn end: " + display(item.endInstruction())) + "\nView: PLAIN catalogue. Select ROOTED to follow GameFlow progression.";
            if (!item.condition().isBlank()) {
                String cid = id + ":condition";
                nodes.add(new Graph.Node(cid, Graph.Kind.CONDITION, "ACTIVATION PREDICATE", display(item.condition()),
                    "Source: StoryFragmentProperties.StoryFragmentCondition\nIndependent predicate. No else branch or execution order is implied.",
                    "Condition", "", item.turn(), null, null, null));
                edges.add(new Graph.Edge(cid, id, "requires", false));
            }
            nodes.add(new Graph.Node(id, Graph.Kind.EVENT, item.type().equals("News") ? "NEWS · " + item.title(): item.title(),
                item.type().equals("News") ? item.internalName(): isStart ? "START EVENT · source IsOnStart": "",
                metadata, item.type(), "", item.turn(), item, null, null));
        }
        return new Graph("Sordland campaign", nodes, edges, List.of("PLAIN source catalogue grouped by proven turn. Lines attach activation predicates only; ROOTED uses GameFlow progression.",
            "No event-to-event routes or ordered if/else chains are inferred from names, chronology, predicates, or dialogue calls."));
    }
    public static String display(String s) {
        return Semantics.conditionDisplay(s);
    }
}
