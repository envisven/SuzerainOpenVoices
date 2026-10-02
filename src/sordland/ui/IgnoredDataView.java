package sordland.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import sordland.data.Domain.IgnoredData;
import sordland.data.Json;
import java.util.*;

public final class IgnoredDataView extends BorderPane {
    public IgnoredDataView(List<IgnoredData> records) {
        setPadding(new Insets(20));
        setStyle(Theme.PANEL_STYLE);
        Label description = new Label("Readable source records that have no safe graphical representation. News and supported conditional instructions remain graphical types.");
        description.setWrapText(true);
        TextField find = new TextField();
        find.setPromptText("Find source file, identity, location or reason…");
        TreeView<Object> list = new TreeView<>();
        list.setShowRoot(false);
        list.setPrefWidth(440);
        var ordered = records.stream().sorted(Comparator.comparing(IgnoredData::sourceFile).thenComparing(IgnoredData::collection).thenComparing(r -> r.sourceIndex().matches("\\d+") ? new java.math.BigInteger(r.sourceIndex()): java.math.BigInteger.valueOf(-1)).thenComparing(IgnoredData::location)).toList();
        list.setCellFactory(v -> new TreeCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null: item instanceof IgnoredData r ? r.location() +(r.identity().isBlank() ? "": " · " + r.identity()): item.toString());
                setStyle(item instanceof String ? "-fx-font-weight: bold;": "");
            }
        });
        TextArea raw = new TextArea(records.isEmpty() ? "No ignored source records. All loaded source content is represented or explicitly diagnosed.": "");
        raw.setEditable(false);
        raw.setWrapText(false);
        raw.setStyle(Theme.TEXT_AREA_STYLE + "-fx-font-family: 'Monospaced';");
        list.getSelectionModel().selectedItemProperty().addListener((p, old, item) -> {
            if (item != null) raw.setText(item.getValue() instanceof IgnoredData r ? format(r): item.getValue() + "\n" + "=".repeat(60) + "\n\nSelect a record in this source file to inspect its reason, identity and exact raw content.");
        });
        java.util.function.Consumer<String> filter = value -> {
            String q = value.toLowerCase(Locale.ROOT).trim();
            TreeItem<Object> tree = new TreeItem<>("Sources");
            Map<String, TreeItem<Object>>groups = new LinkedHashMap<>();
            for (var r: ordered) if ((r.sourceFile() + " " + r.collection() + " " + r.location() + " " + r.identity() + " " + r.reason()).toLowerCase(Locale.ROOT).contains(q)) {
                TreeItem<Object> group = groups.computeIfAbsent(r.sourceFile(), file -> {
                    TreeItem<Object> g = new TreeItem<>(file);
                    g.setExpanded(!q.isEmpty());
                    tree.getChildren().add(g);
                    return g;
                });
                group.getChildren().add(new TreeItem<>(r));
            }
            for (var group: groups.values()) group.setValue(group.getValue() + " (" + group.getChildren().size() + " records)");
            list.setRoot(tree);
            if (!tree.getChildren().isEmpty()) {
                var first = tree.getChildren().getFirst();
                first.setExpanded(true);
                list.getSelectionModel().select(first.getChildren().getFirst());
            } else raw.setText("No ignored records match this search.");
        };
        find.textProperty().addListener((p, old, value) -> filter.accept(value));
        filter.accept("");
        VBox heading = new VBox(12, description, find);
        heading.setPadding(new Insets(0, 0, 16, 0));
        setTop(heading);
        SplitPane split = new SplitPane(list, raw);
        split.setDividerPositions(.36);
        setCenter(split);
    }
    public static String format(IgnoredData item) {
        return item.sourceFile() + "\n" + "=".repeat(Math.min(80, item.sourceFile().length())) + "\n\n" + item.collection() + "[" + item.sourceIndex() + "]\n\nReason:\n" + item.reason() + "\n\nSource identity / JSON location:\n" + item.location() + "\n" + item.identity() + "\n\nRaw data:\n" + Json.pretty(item.raw());
    }
}
