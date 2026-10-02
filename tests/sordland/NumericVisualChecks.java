package sordland;

import javafx.application.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.scene.image.*;
import sordland.analysis.*;
import sordland.data.Loader;
import sordland.data.Domain.*;
import sordland.ui.*;
import java.nio.file.*;
import java.util.*;
import static sordland.TestSupport.*;

public final class NumericVisualChecks extends Application {
    private static int exit = 2;
    public static void main(String[] args) {
        launch(args);
        System.exit(exit);
    }
    @Override
    public void start(Stage stage) {
        stage.setScene(new Scene(new StackPane(new Label("Loading real data…")), 1280, 900));
        stage.show();
        var worker = new Thread(() -> {
            try {
                Dataset data = Loader.load(Path.of("data", Loader.ENTITY_FILE), Path.of("data", Loader.CONVERSATIONS_FILE));
                var index = new VariableIndex(data);
                var analysis = new VariableAnalyzer(index).analyze("BaseGameIsolated.Ending_Vote_Liberals");
                var blocks = NumericVariableLayout.build(analysis);
                Platform.runLater(() -> {
                    try {
                        var grid = new NumericVariableGrid(index, blocks);
                        BorderPane root = host(grid);
                        root.setPadding(new javafx.geometry.Insets(16));
                        Scene scene = new Scene(root, 1280, 900);
                        Theme.apply(scene);
                        stage.setScene(scene);
                        root.applyCss();
                        root.layout();
                        check(scene.lookup("#numeric-variable-grid") != null, "Numeric grid mounted");
                        check(!((CheckBox) scene.lookup("#numeric-source-mode")).isSelected(), "Source mode starts off");
                        save(scene, "numeric-liberals.png");
                        var label =(Label) scene.getRoot().lookupAll(".numeric-cell").stream().filter(n -> n instanceof Label l &&
                            l.getText().contains(" = false")).findFirst().orElseThrow();
                        click(label);
                        check(label.getOpacity() == 1, "Selected cell fully visible");
                        check(label.isUnderline(), "Focus visible");
                        click(label);
                        check(!label.isUnderline(), "Repeated click clears focus");
                        click(label);
                        click(scene.lookup("#numeric-sheets"));
                        check(!label.isUnderline(), "Blank space clears focus");
                        ((CheckBox) scene.lookup("#numeric-source-mode")).fire();
                        click(label);
                        root.applyCss();
                        root.layout();
                        var text =(TextArea) scene.lookup("#numeric-source-text");
                        check(text.getText().contains("BaseGame") && text.getText().contains("Original full source field"),
                            "Source mode contains canonical source evidence");
                        check(!label.isUnderline(), "Source mode does not select compatibility focus");
                        save(scene, "numeric-source-mode.png");
                        fixture(stage, index);
                        exit = 0;
                        System.out.println("PASS: numeric real-data grid, toggle, source evidence, cell/header/route focus, clearing, dimming and snapshots");
                    } catch (Throwable e) {
                        e.printStackTrace();
                    } finally {
                        stage.close();
                        Platform.exit();
                    }
                });
            } catch (Throwable e) {
                e.printStackTrace();
                Platform.exit();
            }
        });
        worker.setDaemon(true);
        worker.start();
    }
    private static void fixture(Stage stage, VariableIndex index) throws Exception {
        var rules = List.of(NumericLayoutChecks.occurrence(0, "(A && C) || (B && C)", "BaseGame.X += 1"), NumericLayoutChecks.occurrence(1,
            "!C", "BaseGame.X += 1"), NumericLayoutChecks.occurrence(2, "D", "BaseGame.X -= 2"));
        var grid = new NumericVariableGrid(index, NumericVariableLayout.build(NumericLayoutChecks.analysis(rules)));
        Scene scene = new Scene(host(grid), 1100, 850);
        Theme.apply(scene);
        stage.setScene(scene);
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        List<Label> labels = scene.getRoot().lookupAll(".numeric-cell").stream().map(n ->(Label) n).toList();
        Label positive = labels.stream().filter(l -> l.getText().equals("C = true")).findFirst().orElseThrow();
        Label negative = labels.stream().filter(l -> l.getText().equals("C = false")).findFirst().orElseThrow();
        Label unknown = labels.stream().filter(l -> l.getText().equals("D = true")).findFirst().orElseThrow();
        click(positive);
        check(negative.getOpacity()<1 && negative.getEffect() != null, "Proven incompatible cell subtly dimmed");
        check(unknown.getOpacity() == 1, "Unknown relation not dimmed");
        var header = labels.stream().filter(l -> l.getText().equals("Gain block 1")).findFirst().orElseThrow();
        click(header);
        check(header.isUnderline() && positive.getOpacity() == 1, "Block focus leaves selected block fully visible");
        Label route = labels.stream().filter(l -> l.getText().equals("Local")).findFirst().orElseThrow();
        check(!route.getParent().isVisible(), "Technical route labels hidden in normal mode");
        save(scene, "numeric-compatibility.png");
        ((CheckBox) scene.lookup("#numeric-source-mode")).fire();
        click(header);
        check(((TitledPane) scene.lookup("#numeric-source-references")).isExpanded(), "Source panel opens for header");
        click(route);
        grid.applyCss();
        grid.layout();
        check(scene.lookup("#numeric-source-text") != null, "Route source opens");
        var original = rules.getFirst();
        var pathA = new DialogueGuardResolver.Path(NumericLayoutChecks.expr("A && C"), List.of(), List.of());
        var pathB = new DialogueGuardResolver.Path(NumericLayoutChecks.expr("B && C"), List.of(), List.of());
        var proof = new DialogueGuardResolver.Resolved(DialogueGuardResolver.Kind.ALTERNATIVE_PATHS, List.of(pathA,
            pathB), "fixture");
        var merged = new VariableIndex.Occurrence(original.variable(), original.access(), original.proof(), original.source(),
            0, original.expression(), proof.expression(), original.effect(), proof);
        var mergedGrid = new NumericVariableGrid(index, NumericVariableLayout.build(NumericLayoutChecks.analysis(List.of(merged))));
        Scene mergedScene = new Scene(host(mergedGrid), 1100, 850);
        Theme.apply(mergedScene);
        stage.setScene(mergedScene);
        mergedScene.getRoot().applyCss();
        mergedScene.getRoot().layout();
        ((CheckBox) mergedScene.lookup("#numeric-source-mode")).fire();
        click(mergedScene.getRoot().lookupAll(".numeric-cell").stream().filter(n -> n instanceof Label l && l.getText().equals("C = true")).findFirst().orElseThrow());
        mergedGrid.applyCss();
        mergedGrid.layout();
        String evidence =((TextArea) mergedScene.lookup("#numeric-source-text")).getText();
        check(evidence.contains("/path/0") && evidence.contains("/path/1"), "Merged cell source mode retains both paths");
        for (Label label: labels) {
            check(label.getHeight() + 1 >= label.prefHeight(label.getWidth()), "Wrapped cell not clipped: " + label.getText());
        }
    }
    private static BorderPane host(NumericVariableGrid grid) {
        var mode = new CheckBox("Source mode");
        mode.setId("numeric-source-mode");
        mode.setOnAction(e -> grid.setSourceMode(mode.isSelected()));
        var scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        var root = new BorderPane(scroll);
        root.setTop(mode);
        return root;
    }
    private static void click(Node node) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 4, 4, 4, 4, MouseButton.PRIMARY, 1, false, false,
            false, false, false, false, false, false, false, true, null));
    }
    private static void save(Scene scene, String file) throws Exception {
        WritableImage image = scene.snapshot(null);
        int w =(int) image.getWidth(), h =(int) image.getHeight();
        int[] pixels = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        var bitmap = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        bitmap.setRGB(0, 0, w, h, pixels, 0, w);
        Files.createDirectories(Path.of("docs/numeric-validation"));
        javax.imageio.ImageIO.write(bitmap, "png", Path.of("docs/numeric-validation", file).toFile());
    }
}
