package sordland.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.effect.ColorAdjust;
import sordland.analysis.*;
import java.util.*;
import static sordland.analysis.NumericVariableLayout.*;

public final class NumericVariableGrid extends VBox {
    private final CompatibilityAnalyzer compatibility;
    private SourceNavigation navigation = new SourceNavigation();
    public void setSourceNavigation(SourceNavigation navigation) {
        this.navigation = navigation;
    }
    private boolean sourceMode;
    private boolean compact = true;
    private final List<BonusBlock> expandedBlocks, compactBlocks;
    public boolean compact() {
        return compact;
    }
    public void setCompact(boolean enabled) {
        if (compact == enabled) return;
        compact = enabled;
        rebuild();
    }
    private void rebuild() {
        clearFocus();
        sourceOnly.clear();
        sheetsWithLogic.clear();
        targets.clear();
        getChildren().clear();
        VBox sheets = new VBox(0);
        sheets.setFillWidth(false);
        sheets.setId("numeric-sheets");
        for (BonusBlock block: compact ? compactBlocks: expandedBlocks) sheets.getChildren().add(sheet(block));
        sheets.setOnMouseClicked(e -> clearFocus());
        getChildren().add(sheets);
        setSourceMode(sourceMode);
    }
    private final List<Node> sourceOnly = new ArrayList<>();
    private final List<SheetCells> sheetsWithLogic = new ArrayList<>();
    private boolean logicLabels;
    public void setLogicLabels(boolean enabled) {
        logicLabels = enabled;
        sheetsWithLogic.forEach(SheetCells::requestLayout);
    }
    private java.util.function.Consumer<String> status = ignored -> {
    };
    private final VBox references = new VBox(4);
    private final TitledPane sourcePanel = new TitledPane("Source references", references);
    private final List<Target> targets = new ArrayList<>();
    private final javafx.beans.property.DoubleProperty viewportWidth = new javafx.beans.property.SimpleDoubleProperty(1000);
    public void setViewportWidth(double width) {
        viewportWidth.set(Math.max(400, width));
    }
    private Target focused;
    private final List<WriteAggregation.Group> totals;
    private record Target(Label node, BonusBlock block, CompatibilityAnalyzer.Selection selection, Set<Reference> references,
        boolean wholeBlock, boolean route) {
    }
    public NumericVariableGrid(VariableIndex index, List<BonusBlock> blocks) {
        this(index, blocks, CompactNumericLayout.build(blocks));
    }
    public NumericVariableGrid(VariableIndex index, List<BonusBlock> blocks, List<BonusBlock> compressed) {
        super(0);
        expandedBlocks = blocks;
        compactBlocks = compressed;
        setId("numeric-variable-grid");
        compatibility = new CompatibilityAnalyzer(index);
        totals = WriteAggregation.proven(blocks.stream().map(BonusBlock::occurrence).toList());
        VBox sheets = new VBox(0);
        sheets.setFillWidth(false);
        sheets.setId("numeric-sheets");
        for (BonusBlock block: compactBlocks) sheets.getChildren().add(sheet(block));
        sheets.setOnMouseClicked(e -> clearFocus());
        sourcePanel.setId("numeric-source-references");
        sourcePanel.setAnimated(false);
        sourcePanel.setExpanded(false);
        references.getChildren().add(new Label("Select a block, route, or cell to inspect its source."));
        getChildren().add(sheets);
        setOnMouseClicked(e -> clearFocus());
    }
    public void setStatus(java.util.function.Consumer<String> listener) {
        status = listener;
    }
    public void setSourceMode(boolean enabled) {
        sourceMode = enabled;
        clearFocus();
        for (Target target: targets) target.node().setTooltip(enabled ? sourceTooltip(target.references()): null);
        sourceOnly.forEach(n -> {
            n.setVisible(enabled);
            n.setManaged(enabled);
        });
        if (enabled && !getChildren().contains(sourcePanel)) getChildren().add(sourcePanel);
        if (!enabled) getChildren().remove(sourcePanel);
    }
    public boolean sourceMode() {
        return sourceMode;
    }
    private Node sheet(BonusBlock block) {
        VBox sheet = new VBox(0);
        double minimum = block.sections().stream().mapToDouble(s -> s.root().columns() * 180).max().orElse(300);
        sheet.setMinWidth(Math.max(400, minimum));
        sheet.prefWidthProperty().bind(javafx.beans.binding.Bindings.max(viewportWidth, sheet.minWidthProperty()));
        sheet.setMaxWidth(USE_PREF_SIZE);
        sheet.setId("bonus-" + targets.size());
        sheet.getProperties().put("source-block", block.id());
        sheet.setStyle("-fx-border-color: #87979c; -fx-border-width: 0 0 0 1;");
        Label heading = cell(block.title().substring(0, block.title().lastIndexOf(" (")), fill(block.effect().kind()));
        heading.setStyle(heading.getStyle() + "-fx-font-weight: bold;");
        heading.setMaxHeight(Double.MAX_VALUE);
        attach(heading, block, CompatibilityAnalyzer.selection(block, block.root()), block.root().references(), true);
        Label amount = cell(block.effect().text(), fill(block.effect().kind()));
        amount.setStyle(amount.getStyle() + "-fx-font-size: 18px; -fx-font-weight: bold;");
        amount.setMinWidth(110);
        amount.setMinHeight(USE_PREF_SIZE);
        amount.setMaxHeight(Double.MAX_VALUE);
        amount.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        attach(amount, block, CompatibilityAnalyzer.selection(block, block.root()), block.root().references(), true);
        HBox header = new HBox(heading, amount);
        HBox.setHgrow(heading, Priority.ALWAYS);
        sheet.getChildren().add(header);
        boolean firstSection = true;
        for (RouteLayout section: block.sections()) {
            if (!section.cells().isEmpty()) {
                if (!compact && !firstSection) {
                    Label divider = cell("↳ Separate source segment", "#d9e3e7");
                    divider.setMinHeight(18);
                    divider.setPadding(new Insets(2, 9, 2, 9));
                    divider.getStyleClass().add("source-flow-separator");
                    sheet.getChildren().add(divider);
                }
                firstSection = false;
            }
            HBox content = new HBox(0);
            VBox routes = new VBox(0);
            routes.setMinWidth(170);
            routes.setPrefWidth(170);
            routes.setMaxWidth(170);
            sourceOnly.add(routes);
            routes.setVisible(sourceMode);
            routes.setManaged(sourceMode);
            if (!section.label().isBlank()) {
                Label summary = cell(section.label(), "#e3e9ed");
                summary.setMinHeight(32);
                attach(summary, block, CompatibilityAnalyzer.selection(block, section.root()), section.root().references(),
                    false, true);
                routes.getChildren().add(summary);
            } else for (Reference ref: section.root().references()) {
                Route route = block.routes().get(Math.max(0, ref.pathIndex()));
                Label label = cell(route.label(), "#e3e9ed");
                label.setMinHeight(32);
                label.setMaxHeight(Double.MAX_VALUE);
                VBox.setVgrow(label, Priority.ALWAYS);
                attach(label, block, CompatibilityAnalyzer.selection(block, section.root()), Set.of(route.reference()),
                    false, true);
                routes.getChildren().add(label);
            }
            Pane cells = new SheetCells(block, section);
            HBox.setHgrow(cells, Priority.ALWAYS);
            content.getChildren().addAll(routes, cells);
            if (section.cells().isEmpty() || section.label().equals("Source-only unresolved topology")) {
                sourceOnly.add(content);
                content.setVisible(sourceMode);
                content.setManaged(sourceMode);
            }
            sheet.getChildren().add(content);
        }
        return sheet;
    }
    private final class SheetCells extends Pane {
        private final BonusBlock block;
        private final RouteLayout section;
        private final List<Label> labels = new ArrayList<>();
        private final List<Label> markers = new ArrayList<>();
        SheetCells(BonusBlock block, RouteLayout section) {
            this.block = block;
            this.section = section;
            for (NumericVariableLayout.Cell cell: section.cells()) {
                String text = ConditionLogic.truth(cell.region().expression()) ? block.occurrence().source().title(): ConditionLogic.display(cell.region().expression());
                Label label = cell(text, cell.region().expression().op().equals("exhaustive") ? "#d8ecef": "#f4f6f4");
                attach(label, block, CompatibilityAnalyzer.selection(block, cell.region()), cell.region().references(),
                    false);
                label.getProperties().put("jump-source-target", SourceNavigation.region(block, cell.region()));
                if (cell.region().expression().op().equals("exhaustive")) label.setOnMouseClicked(e -> {
                    if (navigation.enabled()) navigation.jump((SourceNavigation.Target) label.getProperties().get("jump-source-target"));
                    else if (sourceMode) showSource(targets.stream().filter(t -> t.node() == label).findFirst().orElseThrow());
                    else clearFocus();
                    e.consume();
                });
                labels.add(label);
            }
            getChildren().addAll(labels);
            sheetsWithLogic.add(this);
            setMinWidth(Math.max(300, section.root().columns() * 180));
            setPrefWidth(800);
        }
        private double height(double width) {
            double unit = 34;
            for (int i = 0; i<labels.size(); i++) {
                NumericVariableLayout.Cell c = section.cells().get(i);
                unit = Math.max(unit, labels.get(i).prefHeight(Math.max(20, width * c.columnSpan())) / c.rowSpan());
            }
            return Math.max(32, unit * section.root().rows());
        }
        @Override
        public javafx.geometry.Orientation getContentBias() {
            return javafx.geometry.Orientation.HORIZONTAL;
        }
        @Override
        protected double computePrefHeight(double width) {
            return height(width<0 ? 800: width);
        }
        @Override
        protected double computeMinHeight(double width) {
            return computePrefHeight(width);
        }
        @Override
        protected void layoutChildren() {
            double scale = getHeight() / section.root().rows();
            for (int i = 0; i<labels.size(); i++) {
                NumericVariableLayout.Cell c = section.cells().get(i);
                double left = Math.round(c.column() * getWidth()), right = Math.round((c.column() + c.columnSpan()) * getWidth());
                double top = Math.round(c.row() * scale), bottom = Math.round((c.row() + c.rowSpan()) * scale);
                labels.get(i).resizeRelocate(left, top, right - left, bottom - top);
            }
            getChildren().removeAll(markers);
            markers.clear();
            if (logicLabels) mark(section.root(), 0, 0, getWidth(), getHeight());
        }
        private void mark(NumericVariableLayout.Region r, double x, double y, double w, double h) {
            double cursor = 0;
            int ordinal = 0;
            for (NumericVariableLayout.Region child: r.children()) {
                double size = r.horizontal() ? w * child.columns() / r.columns(): h * child.rows() / r.rows();
                if (ordinal++>0) {
                    Label marker = new Label(r.horizontal() ? "OR": "AND");
                    marker.setMouseTransparent(true);
                    marker.setManaged(false);
                    marker.getStyleClass().add("logic-marker");
                    marker.setStyle("-fx-background-color: #ffffff; -fx-text-fill: #394951; -fx-font-size: 10px; -fx-padding: 0 2;");
                    marker.resizeRelocate(r.horizontal() ? x + cursor - 10: x + w / 2 - 14, r.horizontal() ? y + h / 2 - 7: y + cursor - 7,
                        r.horizontal() ? 20: 28, 14);
                    markers.add(marker);
                }
                mark(child, r.horizontal() ? x + cursor: x, r.horizontal() ? y: y + cursor, r.horizontal() ? size: w,
                    r.horizontal() ? h: size);
                cursor += size;
            }
            if (r == section.root()) getChildren().addAll(markers);
        }
    }
    private static Label cell(String text, String fill) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(32);
        label.setPadding(new Insets(7, 9, 7, 9));
        label.setStyle("-fx-text-fill: #223038; -fx-background-color: " + fill + "; -fx-border-color: #87979c; -fx-border-width: 0 1 1 0; -fx-background-radius: 0; -fx-border-radius: 0;");
        label.getStyleClass().add("numeric-cell");
        return label;
    }
    private static String fill(EffectKind kind) {
        return switch (kind) {
            case GAIN -> "#d8eddb";
            case LOSS -> "#f1d8d8";
            case ZERO -> "#f4dfba";
            case SET -> "#dbe5ef";
            case OPERATION -> "#e3e6e9";
        };
    }
    private void attach(Label label, BonusBlock block, CompatibilityAnalyzer.Selection selection, Set<Reference> refs,
        boolean whole) {
        attach(label, block, selection, refs, whole, false);
    }
    private void attach(Label label, BonusBlock block, CompatibilityAnalyzer.Selection selection, Set<Reference> refs,
        boolean whole, boolean route) {
        Target target = new Target(label, block, selection, refs, whole, route);
        targets.add(target);
        label.getProperties().put("jump-source-target", SourceNavigation.source(block.occurrence().source()));
        label.setUserData(target);
        label.setFocusTraversable(true);
        label.setCursor(javafx.scene.Cursor.HAND);
        label.setAccessibleText(label.getText() + ". Select for compatibility; enable Source mode for evidence.");
        if (sourceMode) label.setTooltip(sourceTooltip(refs));
        label.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                activate(target);
                e.consume();
            }
        });
        label.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                activate(target);
                e.consume();
            }
        });
    }
    private static Tooltip sourceTooltip(Set<Reference> refs) {
        return new Tooltip(refs.stream().map(r -> r.occurrence().source().identity() + " / " + r.occurrence().source().field() + "\n" +(r.path() == null ? r.occurrence().source().guard(): r.path().condition().display(x -> x))).distinct().collect(java.util.stream.Collectors.joining("\n")));
    }
    private void activate(Target target) {
        if (navigation.enabled()) {
            var source =(SourceNavigation.Target) target.node().getProperties().get("jump-source-target");
            if (source == null) status.accept("No navigable source available.");
            else navigation.jump(source);
            return;
        }
        if (sourceMode) {
            showSource(target);
            return;
        }
        if (target.node().getText().startsWith("[EXHAUSTIVE]")) {
            clearFocus();
            return;
        }
        if (focused == target) {
            clearFocus();
            return;
        }
        focused = target;
        int dimmed = 0;
        for (Target other: targets) {
            boolean keep = other == target || other.block() == target.block() &&(target.wholeBlock() || target.route() &&
                !Collections.disjoint(target.references(), other.references()));
            var result = keep ? null: compatibility.compare(target.selection(), other.selection());
            boolean dim = result != null && result.verdict() == CompatibilityAnalyzer.Verdict.INCOMPATIBLE;
            other.node().setOpacity(dim ?.86: 1);
            other.node().setEffect(dim ? new ColorAdjust(0, 0, -.05, 0): null);
            other.node().setUnderline(other == target);
            if (dim) dimmed++;
        }
        status.accept(target.node().getText() + " · " + dimmed + " incompatible regions dimmed. Click again or blank space to clear.");
    }
    public void clearFocus() {
        focused = null;
        for (Target target: targets) {
            target.node().setOpacity(1);
            target.node().setEffect(null);
            target.node().setUnderline(false);
        }
        status.accept(sourceMode ? "Source mode: select a cell, route, or block.": "Click a cell, route label, or block heading to focus.");
    }
    private void showSource(Target target) {
        references.getChildren().clear();
        references.getChildren().add(new Label(target.node().getText()));
        var refs = target.references();
        var o = target.block().occurrence();
        var s = o.source();
        StringBuilder text = new StringBuilder("Canonical variable: " + o.variable() + "\nSource: " + s.identity() + "\nTitle: " + s.title() + "\nTurn: " + Objects.toString(s.turn(),
            "unspecified") + "\nField: " + s.field() + "\nOperation: " + o.operation() + "\nProof: " + o.proof() + "\nExact operation expression:\n" + o.expression() + "\nLocal guard:\n" + s.guard() + "\nNecessary guard summary (route DAG retained separately):\n" + o.condition().display(x -> x));
        for (var group: totals) if (group.writes().contains(o)) {
            text.append("\nProven co-executed source-field total: ").append(group.delta().signum() >= 0 ? "+": "").append(group.delta().toPlainString());
            for (var write: group.writes()) text.append("\n  ").append(write.source().key()).append(":").append(write.operation()).append(" ").append(write.expression());
        }
        text.append("\n\n").append(target.block().targetEvidence());
        if (o.guardProof() != null && o.guardProof().graph() != null) text.append("\n\n").append(o.guardProof().graph().evidence());
        text.append("\n\n").append(NumericVariableLayout.audit(target.block()));
        if (s.causes() != null && o.guardProof() != null && o.guardProof().graph() != null) {
            text.append("\nActivation: ").append(s.causes().activation(s));
            for (var node: o.guardProof().graph().nodes().values()) text.append('\n').append(s.causes().entryEvidence(node.entry()));
        }
        for (Reference ref: refs) {
            text.append("\n\nRepresented reference: ").append(ref.id());
            if (ref.path() != null && o.guardProof() != null && o.guardProof().graph() != null) {
                text.append("\nSource segment; local proof complete: ").append(ref.path().complete()).append("; ").append(ref.path().boundary());
                text.append("\nPredicates: ").append(ref.path().predicates()).append("\nEdges: ").append(ref.path().edges()).append("\nSource choices: ").append(ref.path().choices());
            } else if (ref.path() != null) text.append("\n").append(new DialogueGuardResolver.Resolved(o.guardProof().kind(),
                List.of(ref.path()), o.guardProof().boundary()).evidence());
            else if (o.guardProof() != null) text.append("\n").append(o.guardProof().evidence());
        }
        text.append("\n\nOriginal full source field:\n").append(s.original()).append("\nRuntime aliases:\n").append(String.join("\n",
            s.aliases()));
        TextArea exact = new TextArea(text.toString());
        exact.setEditable(false);
        exact.setWrapText(true);
        exact.setPrefRowCount(12);
        exact.setId("numeric-source-text");
        exact.setStyle(Theme.TEXT_AREA_STYLE);
        TitledPane details = new TitledPane(s.identity() + " · " + refs.size() + " source references", exact);
        details.setAnimated(false);
        details.setExpanded(true);
        references.getChildren().add(details);
        sourcePanel.setExpanded(true);
        status.accept("Source references updated below the grid: " + refs.size() + " represented references.");
    }
}
