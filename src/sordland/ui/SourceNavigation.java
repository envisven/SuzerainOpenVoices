package sordland.ui;

import javafx.beans.property.*;
import javafx.scene.control.CheckBox;
import sordland.data.Domain.*;
import sordland.analysis.*;
import sordland.graph.Graph;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import static sordland.analysis.NumericVariableLayout.*;

public final class SourceNavigation {
    public record Target(EntryKey entry, String itemId, String location) {
        public Target(EntryKey entry, String itemId) {
            this(entry, itemId, null);
        }
    }
    private final BooleanProperty enabled = new SimpleBooleanProperty(false);
    private final Consumer<Target> navigate;
    public SourceNavigation() {
        this(t -> {
        });
    }
    public SourceNavigation(Consumer<Target> navigate) {
        this.navigate = navigate;
    }
    public boolean enabled() {
        return enabled.get();
    }
    public CheckBox toggle(String id) {
        CheckBox box = new CheckBox("Jump to source");
        box.setId(id);
        box.selectedProperty().bindBidirectional(enabled);
        return box;
    }
    public void jump(Target target) {
        if (target != null) navigate.accept(target);
    }
    public static Target node(Graph.Node node) {
        return node.source != null ? new Target(node.source, null): node.item != null ? new Target(null, node.item.id()): null;
    }
    private static final Pattern DIALOGUE = Pattern.compile("Conversation (\\d+) / Dialogue (\\d+)");
    public static Target source(VariableIndex.Source source) {
        var m = DIALOGUE.matcher(source.identity());
        if (m.matches()) return new Target(new EntryKey(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))),
            null);
        String identity = source.identity();
        if (identity.startsWith("Entity ") && identity.contains(" · ")) return new Target(null, identity.substring(7,
            identity.indexOf(" · ")));
        return new Target(null, null, identity);
    }
    private static boolean contains(VariableSyntax.Expr source, VariableSyntax.Expr atom) {
        return source.equals(atom) || source.children().stream().anyMatch(c -> contains(c, atom));
    }
    public static Target region(BonusBlock block, Region region) {
        boolean choice = region.expression().op().equals("trigger");
        for (Reference ref: region.references()) {
            if (choice && ref.occurrence().source().causes() != null) {
                EntryKey origin = ref.occurrence().source().causes().choiceOrigin(region.expression().value());
                if (origin != null) return new Target(origin, null);
            }
            if (choice && region.expression().value().startsWith("[CHOICE]")) return null;
            if (!choice && ref.path() != null) for (var predicate: ref.path().predicates()) if (contains(ConditionLogic.normalize(VariableSyntax.parse(predicate.original())),
                region.expression())) return new Target(predicate.entry(), null);
            Target source = source(ref.occurrence().source());
            if (source != null) return source;
        }
        return source(block.occurrence().source());
    }
}
