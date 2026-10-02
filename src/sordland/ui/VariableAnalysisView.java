package sordland.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import sordland.analysis.*;
import java.util.*;
import java.util.function.*;
import static sordland.analysis.VariableIndex.*;

public final class VariableAnalysisView extends VBox {
    private final VariableIndex index;
    private final SourceNavigation navigation;
    public VariableAnalysisView(VariableIndex index, VariableAnalyzer.Analysis analysis, Consumer<String> navigate) {
        this(index, analysis, navigate, null);
    }
    public VariableAnalysisView(VariableIndex index, VariableAnalyzer.Analysis analysis, Consumer<String> navigate,
        NumericVariableGrid numeric) {
        this(index, analysis, navigate, numeric, new SourceNavigation());
    }
    public VariableAnalysisView(VariableIndex index, VariableAnalyzer.Analysis analysis, Consumer<String> navigate,
        NumericVariableGrid numeric, SourceNavigation navigation) {
        super(14);
        this.navigation = navigation;
        this.index = index;
        setPadding(new Insets(20));
        setId("variable-analysis");
        getChildren().addAll(heading(analysis.layout() == VariableAnalyzer.Layout.NUMERIC ? "Selected variable: " + ConditionLogic.basename(analysis.variable()): index.label(analysis.variable())),
            mono(analysis.variable()));
        long readWrite = analysis.count(Access.READ_WRITE);
        getChildren().add(new Label((analysis.count(Access.READ) + readWrite) + " reads   ·   " +(analysis.count(Access.WRITE) + readWrite) + " writes   ·   " + analysis.rules().size() + " proven operations   ·   " + analysis.count(Access.UNRESOLVED) + " unresolved operations   ·   " + analysis.rules().stream().filter(o -> o.guardProof() != null &&
        (o.guardProof().kind() == DialogueGuardResolver.Kind.UNRESOLVED || o.guardProof().kind() == DialogueGuardResolver.Kind.PARTIAL)).count() + " unresolved incoming guards"));
        getChildren().add(heading("Derived mechanic"));
        if (analysis.layout() == VariableAnalyzer.Layout.NUMERIC) getChildren().add(text("Route identifiers denote contiguous source segments; from labels connect shared branches. Necessary guard summaries support conservative compatibility; the retained source DAG is the complete proof structure."));
        getChildren().add(text("Guards describe predicates at their original source entries. Earlier: marks a predicate before an intervening write; it is not a current-state claim. Operations apply when their source is reached or selected. They do not prove reachability, execution frequency, or a final game state."));
        if (analysis.layout() == VariableAnalyzer.Layout.NUMERIC) {
            getChildren().add(numeric == null ? new NumericVariableGrid(index, NumericVariableLayout.build(analysis)): numeric);
        } else if (!analysis.rules().isEmpty()) {
            var groups = new TreeMap<String, List<Occurrence>>(Comparator.reverseOrder());
            for (Occurrence rule: analysis.rules()) groups.computeIfAbsent(rule.effect().display(), key -> new ArrayList<>()).add(rule);
            for (var group: groups.entrySet()) {
                getChildren().add(heading(group.getKey().equals("SET = true") ? "SET TRUE WHEN": group.getKey().equals("SET = false") ? "SET FALSE WHEN": group.getKey() + " WHEN"));
                var guards = new LinkedHashMap<VariableSyntax.Expr, List<Occurrence>>();
                group.getValue().stream().sorted(Comparator.comparing((Occurrence rule) -> rule.condition().variables().isEmpty())).forEach(rule -> guards.computeIfAbsent(rule.condition(),
                    key -> new ArrayList<>()).add(rule));
                boolean first = true;
                for (var guarded: guards.values()) {
                    if (!first) getChildren().add(new Label("OR — another source operation"));
                    first = false;
                    Occurrence rule = guarded.getFirst();
                    VBox sources = new VBox(6);
                    for (Occurrence occurrence: guarded) sources.getChildren().addAll(text(context(occurrence)), evidence(occurrence));
                    TitledPane disclosure = new TitledPane(guarded.size() + " source operations · exact expressions and locations",
                        sources);
                    disclosure.setExpanded(false);
                    VBox card = new VBox(8, text(pretty(rule)), text(guarded.stream().map(this::guardKind).distinct().collect(java.util.stream.Collectors.joining(" · "))),
                        disclosure);
                    card.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
                        if (navigation.enabled() && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                            navigation.jump(SourceNavigation.source(rule.source()));
                            e.consume();
                        }
                    });
                    card.setPadding(new Insets(12));
                    card.setStyle(Theme.PANEL_STYLE + " -fx-border-color: " + Theme.BORDER + "; -fx-background-radius: 6;");
                    getChildren().add(card);
                }
            }
        } else getChildren().add(text("No proven assignments. Read, reference, and unresolved evidence is available below."));
        if (analysis.family() != null) {
            getChildren().addAll(heading("Proven state transitions"), text(String.join(" · ", analysis.family().members())),
                table(analysis.family().transitions(), true), text("Uncovered combinations: UNKNOWN / NOT PROVEN. Source order and reachability are not inferred."));
        }
        getChildren().add(heading("Related variables · proven guard dependencies"));
        if (analysis.dependencies().isEmpty()) getChildren().add(text("No proven guard dependencies."));
        analysis.dependencies().forEach((name, count) -> {
            Hyperlink link = new Hyperlink(index.label(name) + " · " + count + " rule reads");
            link.setWrapText(true);
            link.setTooltip(new Tooltip(name));
            link.setOnAction(event -> navigate.accept(name));
            getChildren().add(link);
        });
        getChildren().add(heading("Source evidence · " + analysis.evidence().size() + " operations"));
        ListView<Occurrence> evidenceList = new ListView<>();
        evidenceList.getItems().setAll(analysis.evidence());
        evidenceList.setPrefHeight(320);
        evidenceList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Occurrence value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                setGraphic(empty || value == null ? null: evidence(value));
            }
        });
        getChildren().add(evidenceList);
    }
    private String pretty(Occurrence rule) {
        if (rule.guardProof() != null && rule.guardProof().propagated()) return rule.condition().display(index::label);
        return rule.source().guard().isBlank() ? "No proven incoming guard · source reachability remains unresolved": rule.condition().display(index::label);
    }
    private String guardKind(Occurrence rule) {
        return rule.guardProof() == null ? "Local source · " + rule.proof(): "Guard proof: " + rule.guardProof().kind() + " · " + rule.guardProof().paths().size() + " retained paths";
    }
    private String context(Occurrence rule) {
        return(rule.source().turn() == null ? "Turn unspecified": "Turn " + rule.source().turn()) + " · " + rule.source().title();
    }
    private TitledPane evidence(Occurrence occurrence) {
        Source source = occurrence.source();
        var pane = new TitledPane(occurrence.access() + " · " + source.identity() + " · " + source.field(), null);
        pane.setExpanded(false);
        pane.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
            if (navigation.enabled() && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                navigation.jump(SourceNavigation.source(source));
                e.consume();
            }
        });
        pane.expandedProperty().addListener((observable, previous, expanded) -> {
            if (expanded && pane.getContent() == null) {
                TextArea exact = new TextArea(context(occurrence) + "\nProof: " + occurrence.proof() + "\nSource: " + source.identity() + "\nField: " + source.field() + "\nOperation: " + occurrence.operation() + "\nOperation expression:\n" + occurrence.expression() + "\nNormalized effect:\n" +(occurrence.effect() == null ? "Not a proven assignment": occurrence.effect().display()) + "\nLocal guard (exact):\n" + source.guard() + "\nResolved guard:\n" + occurrence.condition().display(Function.identity()) + "\n" +(occurrence.guardProof() == null ? "Local source construct": occurrence.guardProof().evidence()) + "\nExact full field:\n" + source.original() + "\nAlternate runtime locations:\n" + String.join("\n",
                    source.aliases()));
                exact.setEditable(false);
                exact.setWrapText(true);
                exact.setPrefRowCount(12);
                exact.setStyle(Theme.TEXT_AREA_STYLE);
                pane.setContent(exact);
            }
        });
        return pane;
    }
    private TableView<Occurrence> table(List<Occurrence> rules, boolean family) {
        TableView<Occurrence> table = new TableView<>();
        if (family) table.setId("state-family-table");
        if (family) {
            column(table, "Condition / inputs", this::pretty);
            column(table, "Result", o -> index.label(o.variable()));
            column(table, "Source / proof", o -> o.source().identity() + " · " + guardKind(o));
        } else {
            column(table, "Turn", o -> Objects.toString(o.source().turn(), "—"));
            column(table, "Source / decision", o -> o.source().title());
            column(table, "Condition / inputs", this::pretty);
            column(table, "Effect", o -> o.effect().display());
        }
        if (family) {
            table.getColumns().get(0).setPrefWidth(620);
            table.getColumns().get(1).setPrefWidth(180);
            table.getColumns().get(2).setPrefWidth(260);
        }
        table.setRowFactory(t -> {
            var row = new TableRow<Occurrence>();
            row.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
                if (navigation.enabled() && !row.isEmpty() && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    navigation.jump(SourceNavigation.source(row.getItem().source()));
                    e.consume();
                }
            });
            return row;
        });
        table.getItems().setAll(rules);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(Math.min(440, 70 + rules.size() * 30));
        return table;
    }
    private static void column(TableView<Occurrence> table, String title, Function<Occurrence, String> value) {
        TableColumn<Occurrence, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setCellFactory(unused -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                setText(null);
                if (empty) setGraphic(null);
                else {
                    var wrapped = new javafx.scene.text.Text(text);
                    wrapped.setFill(javafx.scene.paint.Color.web(Theme.TEXT));
                    wrapped.wrappingWidthProperty().bind(column.widthProperty().subtract(18));
                    setGraphic(wrapped);
                    setPrefHeight(USE_COMPUTED_SIZE);
                }
                setTooltip(empty ? null: new Tooltip(text));
            }
        });
        table.getColumns().add(column);
    }
    private static Label text(String value) {
        Label label = new Label(value);
        label.setWrapText(true);
        return label;
    }
    private static Label mono(String value) {
        Label label = text(value);
        label.setStyle("-fx-font-family: monospace; -fx-text-fill: " + Theme.MUTED + ";");
        return label;
    }
    private static Label heading(String value) {
        Label label = text(value);
        label.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        return label;
    }
}
