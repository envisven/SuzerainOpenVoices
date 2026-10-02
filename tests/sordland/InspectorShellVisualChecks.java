package sordland;

import javafx.application.*;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.image.*;
import javafx.stage.*;
import sordland.data.Loader;
import sordland.ui.*;
import java.nio.file.*;
import static sordland.TestSupport.*;

public final class InspectorShellVisualChecks extends Application {
    private static int exit = 2;
    private int phase, ticks;
    private Timeline timer;
    private VariableInspectorWindow inspector;
    private NumericVariableGrid grid;
    private double heightBefore, popupHeightBefore;
    private VariablePicker picker;
    public static void main(String[] args) {
        launch(args);
        System.exit(exit);
    }
    @Override
    public void start(Stage owner) {
        owner.setScene(new Scene(new StackPane(), 300, 200));
        owner.show();
        Thread worker = new Thread(() -> {
            try {
                var data = Loader.load(Path.of("data", Loader.ENTITY_FILE), Path.of("data", Loader.CONVERSATIONS_FILE));
                Platform.runLater(() -> {
                    inspector = new VariableInspectorWindow(owner, data,() -> {
                    });
                    timer = new Timeline(new KeyFrame(Duration.millis(250), e -> step(owner)));
                    timer.setCycleCount(Timeline.INDEFINITE);
                    timer.play();
                });
            } catch (Throwable e) {
                e.printStackTrace();
                Platform.exit();
            }
        });
        worker.setDaemon(true);
        worker.start();
    }
    private void step(Stage owner) {
        try {
            if (++ticks>360) throw new AssertionError("Shell UI timed out at phase " + phase);
            Stage window =(Stage) Window.getWindows().stream().filter(w -> w instanceof Stage s && "Variable Inspector".equals(s.getTitle())).findFirst().orElseThrow();
            Scene scene = window.getScene();
            var root =(BorderPane) scene.getRoot();
            if (phase == 0 && scene.lookup("#variable-query") instanceof TextField query) {
                root.applyCss();
                root.layout();
                picker =(VariablePicker) query.getParent();
                check(!picker.suggestionsShowing(), "Popup closed by default");
                check(!((CheckBox) scene.lookup("#all-variables")).isSelected(), "Quantitative default");
                check(!((CheckBox) scene.lookup("#numeric-source-mode")).isSelected(), "Source mode off by default");
                query.setText("BaseGame.Economy");
                root.applyCss();
                root.layout();
                popupHeightBefore = root.getCenter().getBoundsInParent().getHeight();
                click(query);
                phase++;
            } else if (phase == 1) {
                check(picker.suggestionsShowing(), "Click opens popup");
                equal(popupHeightBefore, root.getCenter().getBoundsInParent().getHeight(), "Popup does not change center dimensions");
                check(scene.lookup("#variable-choices") == null, "Popup list is not embedded in shell scene");
                ((TextField) scene.lookup("#variable-query")).setText("BaseGame.Situation_Diplomacy_Energy_PriceSurge");
                check(picker.choices().getItems().isEmpty(), "Boolean excluded from quantitative catalogue");
                ((CheckBox) scene.lookup("#all-variables")).fire();
                check(picker.choices().getItems().contains("BaseGame.Situation_Diplomacy_Energy_PriceSurge"), "All variables reveals Boolean variable");
                ((CheckBox) scene.lookup("#all-variables")).fire();
                ((TextField) scene.lookup("#variable-query")).setText("BaseGame.Economy");
                phase++;
            } else if (phase == 2) {
                for (Node n: picker.choices().lookupAll(".list-cell")) if (n instanceof ListCell<?> c && "BaseGame.Economy".equals(c.getItem())) {
                    click(c);
                    window.setHeight(Math.min(650, Screen.getPrimary().getVisualBounds().getHeight() - 180));
                    check(!picker.suggestionsShowing(), "Selection closes popup");
                    phase++;
                    break;
                }
            } else if (phase == 3 && scene.lookup("#numeric-variable-grid") instanceof NumericVariableGrid numeric) {
                grid = numeric;
                var scroll =(ScrollPane) root.getCenter();
                check(scroll.getContent() == grid, "Default center is only spreadsheet");
                check(scene.lookup("#variable-analysis") == null && scene.lookup("#numeric-source-references") == null,
                    "No descriptions/evidence in default view");
                check(grid.lookupAll(".scroll-pane").isEmpty(), "No nested grid scrolling");
                var sheets =(VBox) scene.lookup("#numeric-sheets");
                equal(0d, sheets.getSpacing(), "No 12px block gaps");
                double gap = sheets.getChildren().get(1).getBoundsInParent().getMinY() - sheets.getChildren().getFirst().getBoundsInParent().getMaxY();
                check(gap <= 1, "Thin block boundary");
                heightBefore = scroll.getViewportBounds().getHeight();
                window.setHeight(window.getHeight() + 140);
                phase++;
            } else if (phase == 4) {
                if (((ScrollPane) root.getCenter()).getViewportBounds().getHeight() <= heightBefore + 100) return;
                check(((ScrollPane) root.getCenter()).getViewportBounds().getHeight()> heightBefore + 100, "Grid viewport grows with window: before=" + heightBefore + ", after=" +((ScrollPane) root.getCenter()).getViewportBounds().getHeight() + ", window=" + window.getHeight());
                save(scene, "economy-grid-only.png");
                ((CheckBox) scene.lookup("#numeric-source-mode")).fire();
                phase++;
            } else if (phase == 5) {
                root.applyCss();
                root.layout();
                check(scene.lookup("#variable-analysis") != null, "Source mode restores detailed analysis");
                check(scene.lookup("#numeric-variable-grid") == grid, "Mode toggle reuses same cached grid");
                check(scene.lookup("#numeric-source-references") != null, "Source references available only in detailed mode");
                Label header =(Label) grid.lookupAll(".numeric-cell").stream().filter(n -> n instanceof Label l &&
                    l.getText().contains("block 1")).findFirst().orElseThrow();
                click(header);
                phase++;
            } else if (phase == 6) {
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                if (scene.lookup("#numeric-source-text") == null) return;
                check(scene.lookup("#numeric-source-text") != null, "Source-mode cell click opens evidence");
                save(scene, "economy-source-mode.png");
                ((CheckBox) scene.lookup("#numeric-source-mode")).fire();
                phase++;
            } else if (phase == 7) {
                check(((ScrollPane) root.getCenter()).getContent() == grid, "OFF immediately restores cached full-area grid");
                Label header =(Label) grid.lookupAll(".numeric-cell").stream().filter(n -> n instanceof Label l &&
                    l.getText().contains("block 1")).findFirst().orElseThrow();
                click(header);
                check(header.isUnderline(), "Grid focus set");
                ((Button) scene.lookup("#numeric-clear-focus")).fire();
                check(!header.isUnderline(), "Toolbar clears grid focus");
                root.applyCss();
                root.layout();
                popupHeightBefore = root.getCenter().getBoundsInParent().getHeight();
                click(scene.lookup("#variable-query"));
                phase++;
            } else if (phase == 8) {
                equal(popupHeightBefore, root.getCenter().getBoundsInParent().getHeight(), "Popup overlays rendered grid without resizing");
                save(scene, "economy-picker-overlay.png");
                scene.lookup("#variable-query").fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                    false, false, false, false));
                check(!picker.suggestionsShowing(), "Escape closes popup");
                click(scene.lookup("#variable-query"));
                window.toFront();
                window.requestFocus();
                phase++;
            } else if (phase == 9) {
                var point = scene.lookup("#numeric-clear-focus").localToScreen(5, 5);
                var robot = new javafx.scene.robot.Robot();
                robot.mouseMove(point);
                robot.mouseClick(MouseButton.PRIMARY);
                phase++;
            } else if (phase == 10) {
                if (picker.suggestionsShowing()) {
                    window.toFront();
                    window.requestFocus();
                    var point = scene.lookup("#numeric-clear-focus").localToScreen(5, 5);
                    var robot = new javafx.scene.robot.Robot();
                    robot.mouseMove(point);
                    robot.mouseClick(MouseButton.PRIMARY);
                    return;
                }
                check(!picker.suggestionsShowing(), "Real outside click closes popup");
                check(scene.lookup("#variable-query").isVisible(), "Search stays visible after selection");
                exit = 0;
                System.out.println("PASS: popup layout/selection/Escape/outside click, quantitative toggle, grid-only/source modes, cached toggle, full-height growth, toolbar focus and thin separators");
                timer.stop();
                inspector.close();
                owner.close();
                Platform.exit();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            timer.stop();
            if (inspector != null) inspector.close();
            owner.close();
            Platform.exit();
        }
    }
    private static void click(Node n) {
        n.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 5, 5, 5, 5, MouseButton.PRIMARY, 1, false, false, false,
            false, false, false, false, false, false, true, null));
    }
    private static void save(Scene scene, String file) throws Exception {
        WritableImage image = scene.snapshot(null);
        int w =(int) image.getWidth(), h =(int) image.getHeight();
        int[] pixels = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        var bitmap = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        bitmap.setRGB(0, 0, w, h, pixels, 0, w);
        Files.createDirectories(Path.of("docs/correction-validation"));
        javax.imageio.ImageIO.write(bitmap, "png", Path.of("docs/correction-validation", file).toFile());
    }
}
