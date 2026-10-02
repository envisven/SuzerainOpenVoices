package sordland.ui;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import sordland.analysis.*;
import sordland.data.Domain.Dataset;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public final class VariableInspectorWindow {
    private final Stage stage = new Stage();
    private final BorderPane root = new BorderPane();
    private final VBox top = new VBox(8);
    private final Label status = new Label();
    private final Button back = new Button("← Back"), forward = new Button("Forward →"), cancel = new Button("Cancel");
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "variable-inspector");
        thread.setDaemon(true);
        return thread;
    });
    private final List<String> history = new ArrayList<>();
    private int cursor = -1;
    private long revision;
    private Task<?> active;
    private VariableIndex index;
    private VariableAnalyzer analyzer;
    private final SourceNavigation navigation;
    private final CheckBox sourceMode = new CheckBox("Source mode");
    private final CheckBox compact = new CheckBox("Compact");
    private final CheckBox logicLabels = new CheckBox("Show logic labels");
    private final Button clearFocus = new Button("Clear focus");
    private VariablePicker picker;
    private VariableAnalyzer.Analysis selectedAnalysis;
    private NumericVariableGrid numericGrid;
    private record Result(VariableAnalyzer.Analysis analysis, List<NumericVariableLayout.BonusBlock> blocks, List<NumericVariableLayout.BonusBlock> compactBlocks) {
    }
    public VariableInspectorWindow(Stage owner, Dataset data, Runnable closed) {
        this(owner, data, closed, new SourceNavigation());
    }
    public VariableInspectorWindow(Stage owner, Dataset data, Runnable closed, SourceNavigation navigation) {
        this.navigation = navigation;
        stage.setTitle("Variable Inspector");
        stage.setMinWidth(660);
        stage.setMinHeight(480);
        top.setPadding(new Insets(16));
        sourceMode.setId("numeric-source-mode");
        clearFocus.setId("numeric-clear-focus");
        logicLabels.setId("numeric-logic-labels");
        compact.setId("numeric-compact");
        compact.setSelected(true);
        compact.setOnAction(e -> {
            if (numericGrid != null) numericGrid.setCompact(compact.isSelected());
        });
        FlowPane toolbar = new FlowPane(10, 8, back, forward, cancel, sourceMode, compact, logicLabels, navigation.toggle("inspector-jump-source"),
            clearFocus, status);
        status.visibleProperty().bind(sourceMode.selectedProperty());
        status.managedProperty().bind(sourceMode.selectedProperty());
        toolbar.setId("variable-toolbar");
        status.setMinWidth(0);
        status.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(status, Priority.ALWAYS);
        top.getChildren().add(toolbar);
        sourceMode.setOnAction(e -> renderSelection());
        logicLabels.setOnAction(e -> {
            if (numericGrid != null) numericGrid.setLogicLabels(logicLabels.isSelected());
        });
        clearFocus.setOnAction(e -> {
            if (numericGrid != null) numericGrid.clearFocus();
        });
        root.setTop(top);
        root.setCenter(new Label("Preparing the variable catalog…"));
        back.setOnAction(event -> {
            if (cursor>0) select(history.get(--cursor), false);
        });
        forward.setOnAction(event -> {
            if (cursor + 1<history.size()) select(history.get(++cursor), false);
        });
        cancel.setOnAction(event -> {
            revision++;
            if (active != null) active.cancel(true);
            status.setText(index == null ? "Catalog cancelled. Close and reopen to retry.": "Analysis cancelled. Select a variable to continue.");
            cancel.setDisable(true);
        });
        stage.setOnHidden(event -> {
            revision++;
            if (active != null) active.cancel(true);
            if (picker != null) picker.hideSuggestions();
            worker.shutdownNow();
            closed.run();
        });
        Scene scene = new Scene(root, 1180, 780);
        Theme.apply(scene);
        stage.setScene(scene);
        updateHistory();
        stage.show();
        work("Indexing canonical source fields…",() -> new VariableIndex(data), result -> {
            index = result;
            analyzer = new VariableAnalyzer(index);
            picker = new VariablePicker(index, name -> select(name, true));
            top.getChildren().add(picker);
            root.setCenter(new Label("Type to filter, then click a game variable. Scripts are never executed."));
            status.setText(index.variables().size() + " variables available");
        });
    }
    public void show() {
        stage.show();
        stage.toFront();
    }
    public void close() {
        stage.close();
    }
    private void select(String name, boolean remember) {
        if (remember) {
            while (history.size()> cursor + 1) history.removeLast();
            history.add(name);
            cursor = history.size() - 1;
        }
        updateHistory();
        work("Analysing " + name + "…",() -> {
            var analysis = analyzer.analyze(name);
            var blocks = analysis.layout() == VariableAnalyzer.Layout.NUMERIC ? NumericVariableLayout.build(analysis): List.<NumericVariableLayout.BonusBlock> of();
            return new Result(analysis, blocks, CompactNumericLayout.build(blocks));
        }, result -> {
            selectedAnalysis = result.analysis();
            numericGrid = result.analysis().layout() == VariableAnalyzer.Layout.NUMERIC ? new NumericVariableGrid(index,
                result.blocks(), result.compactBlocks()): null;
            if (numericGrid != null) {
                numericGrid.setStatus(status::setText);
                numericGrid.setSourceNavigation(navigation);
            }
            renderSelection();
            status.setText(ConditionLogic.basename(name));
        });
    }
    private void renderSelection() {
        if (selectedAnalysis == null) return;
        if (numericGrid != null) {
            var parent = numericGrid.getParent();
            if (parent instanceof VBox box) box.getChildren().remove(numericGrid);
            if (root.getCenter() instanceof ScrollPane old && old.getContent() == numericGrid) old.setContent(null);
            numericGrid.setSourceMode(sourceMode.isSelected());
            numericGrid.setLogicLabels(logicLabels.isSelected());
            numericGrid.setCompact(compact.isSelected());
        }
        javafx.scene.Node content = numericGrid != null && !sourceMode.isSelected() ? numericGrid: new VariableAnalysisView(index,
            selectedAnalysis, related -> select(related, true), numericGrid, navigation);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setId("variable-result-scroll");
        scroll.setFitToWidth(true);
        if (numericGrid != null) scroll.viewportBoundsProperty().addListener((p, a, b) -> numericGrid.setViewportWidth(b.getWidth() -(sourceMode.isSelected() ? 40: 0)));
        root.setCenter(scroll);
        clearFocus.setDisable(numericGrid == null);
    }
    private void updateHistory() {
        back.setDisable(cursor <= 0);
        forward.setDisable(cursor + 1 >= history.size());
    }
    private<T> void work(String message, Supplier<T> operation, java.util.function.Consumer<T> done) {
        long request =++revision;
        if (active != null) active.cancel(true);
        status.setText(message);
        cancel.setDisable(false);
        Task<T> task = new Task<>() {
            @Override
            protected T call() {
                return operation.get();
            }
        };
        active = task;
        task.setOnSucceeded(event -> {
            if (request != revision) return;
            cancel.setDisable(true);
            done.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            if (request != revision) return;
            cancel.setDisable(true);
            status.setText("Unable to analyse: " + task.getException().getMessage());
        });
        worker.execute(task);
    }
}
