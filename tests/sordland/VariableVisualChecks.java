package sordland;

import javafx.application.*;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import javafx.scene.image.*;
import javafx.stage.*;
import sordland.data.Loader;
import sordland.analysis.VariableIndex.Occurrence;
import sordland.ui.VariableInspectorWindow;
import java.nio.file.*;

public final class VariableVisualChecks extends Application {
    private static int exit = 2;
    private int phase;
    private int ticks;
    private Timeline timer;
    private VariableInspectorWindow inspector;
    public static void main(String[] args) {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(45_000);
                Runtime.getRuntime().halt(2);
            } catch (InterruptedException ignored) {
            }
        }, "variable-smoke-timeout");
        watchdog.setDaemon(true);
        watchdog.start();
        launch(args);
        System.exit(exit);
    }
    @Override
    public void start(Stage owner) {
        owner.setScene(new Scene(new StackPane(), 400, 200));
        owner.show();
        Thread loader = new Thread(() -> {
            try {
                var data = Loader.load(Path.of("data", Loader.ENTITY_FILE), Path.of("data", Loader.CONVERSATIONS_FILE));
                Platform.runLater(() -> {
                    inspector = new VariableInspectorWindow(owner, data,() -> {
                    });
                    timer = new Timeline(new KeyFrame(Duration.millis(200), event -> check(owner)));
                    timer.setCycleCount(150);
                    timer.play();
                });
            } catch (Throwable error) {
                error.printStackTrace();
                Platform.exit();
            }
        });
        loader.setDaemon(true);
        loader.start();
    }
    private void check(Stage owner) {
        try {
            if (++ticks>145) throw new AssertionError("Inspector timed out");
            Stage window =(Stage) Window.getWindows().stream().filter(w -> w instanceof Stage s && "Variable Inspector".equals(s.getTitle())).findFirst().orElseThrow();
            var scene = window.getScene();
            if (phase == 0 && scene.lookup("#variable-query") instanceof TextField query) {
                ((CheckBox) scene.lookup("#all-variables")).fire();
                ((sordland.ui.VariablePicker) query.getParent()).showSuggestions();
                query.setText("energy");
                phase++;
            } else if (phase == 1) {
                if (scene.lookup("#variable-analysis") != null) throw new AssertionError("Typing triggered analysis");
                @SuppressWarnings("unchecked") ListView<String> choices =((sordland.ui.VariablePicker) scene.lookup("#variable-query").getParent()).choices();
                if (choices.getItems().isEmpty()) throw new AssertionError("No real energy suggestions");
                if (choices.getSelectionModel().getSelectedItem() != null) throw new AssertionError("Filter auto-selected a result");
                ((TextField) scene.lookup("#variable-query")).setText("BaseGame.Situation_Diplomacy_Energy_PriceSurge");
                phase++;
            } else if (phase == 2) {
                for (var node:((sordland.ui.VariablePicker) scene.lookup("#variable-query").getParent()).choices().lookupAll(".list-cell")) {
                    if (node instanceof ListCell<?> cell && "BaseGame.Situation_Diplomacy_Energy_PriceSurge".equals(cell.getItem())) {
                        cell.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 5, 5, 5, 5, MouseButton.PRIMARY, 1,
                            false, false, false, false, false, false, false, false, false, true, null));
                        phase++;
                        break;
                    }
                }
            } else if (phase == 3 && scene.lookup("#variable-analysis") != null) {
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                save(scene, "energy-inspector.png");
                @SuppressWarnings("unchecked") TableView<Occurrence> table =(TableView<Occurrence>) scene.lookup("#state-family-table");
                if (table == null) throw new AssertionError("Proven family lost after guard propagation");
                Occurrence epa = table.getItems().stream().filter(o -> o.source().identity().equals("Conversation 44 / Dialogue 450")).findFirst().orElseThrow();
                if (epa.guardProof() == null || !epa.guardProof().propagated() || epa.condition().variables().isEmpty()) throw new AssertionError("Family table missing actual EPA guard");
                table.scrollTo(epa);
                ScrollPane scroll =(ScrollPane) scene.getRoot().lookupAll(".scroll-pane").stream().filter(n -> n instanceof ScrollPane p &&
                    p.getContent() != null && "variable-analysis".equals(p.getContent().getId())).findFirst().orElseThrow();
                double contentHeight = scroll.getContent().getBoundsInLocal().getHeight();
                scroll.setVvalue(table.getBoundsInParent().getMinY() /(contentHeight - scroll.getViewportBounds().getHeight()));
                phase++;
            } else if (phase == 4) {
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                save(scene, "energy-guard-family.png");
                System.out.println("PASS: filtering/selection separation, reconstructed EPA guard in live family table, summary and family snapshots.");
                exit = 0;
                timer.stop();
                inspector.close();
                owner.close();
                Platform.exit();
            }
        } catch (Throwable error) {
            error.printStackTrace();
            timer.stop();
            if (inspector != null) inspector.close();
            owner.close();
            Platform.exit();
        }
    }
    private static void save(Scene scene, String name) throws Exception {
        WritableImage image = scene.snapshot(null);
        int width =(int) image.getWidth(), height =(int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        var bitmap = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        bitmap.setRGB(0, 0, width, height, pixels, 0, width);
        Files.createDirectories(Path.of("docs/variable-validation"));
        javax.imageio.ImageIO.write(bitmap, "png", Path.of("docs/variable-validation", name).toFile());
    }
}
