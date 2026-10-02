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
import java.util.*;
import static sordland.TestSupport.*;

public final class RouteDagVisualChecks extends Application {
    private static int exit = 2;
    private int phase, ticks;
    private Timeline timer;
    private VariableInspectorWindow inspector;
    private Stage window;
    private NumericVariableGrid original;
    private long began;
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
                    timer = new Timeline(new KeyFrame(Duration.millis(350), e -> step(owner)));
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
            if (++ticks>1000) throw new AssertionError("Route UI timed out at " + phase);
            window =(Stage) Window.getWindows().stream().filter(w -> w instanceof Stage s && "Variable Inspector".equals(s.getTitle())).findFirst().orElseThrow();
            Scene scene = window.getScene();
            var root =(BorderPane) scene.getRoot();
            if (phase == 0 && scene.lookup("#variable-query") instanceof TextField query) {
                select(query, "BaseGame.AMorgnaWesCore");
                began = System.nanoTime();
                phase++;
            } else if (phase == 1) {
                if (choose(scene, "BaseGame.AMorgnaWesCore")) phase++;
            } else if (phase == 2 && scene.lookup("#numeric-variable-grid") instanceof NumericVariableGrid grid) {
                original = grid;
                ((CheckBox) scene.lookup("#numeric-compact")).fire();
                root.applyCss();
                root.layout();
                normal(scene);
                System.out.println("Actual AMorgna inspector load seconds=" +(System.nanoTime() - began) / 1e9);
                scrollTo(scene, "Conversation 226 / Dialogue 1007/userScript:0", null);
                phase++;
            } else if (phase == 3) {
                save(scene, "amorgna-226-1007-block.png");
                scrollTo(scene, "Conversation 244 / Dialogue 1082/userScript:0", "Turn09_UnrestStopped");
                phase = 12;
            } else if (phase == 12) {
                save(scene, "amorgna-244-1082-unrest-video-area.png");
                scrollTo(scene, "Conversation 244 / Dialogue 1082/userScript:0", "Bill_Turn07_LanguageBill_Signed");
                phase = 13;
            } else if (phase == 13) {
                save(scene, "amorgna-244-1082-language-video-area.png");
                scrollTo(scene, "Conversation 226 / Dialogue 1007/userScript:0", "RumburgIncident_DeclareWar");
                phase = 4;
            } else if (phase == 4) {
                save(scene, "amorgna-226-1007-video-area.png");
                scrollTo(scene, "Conversation 240 / Dialogue 201/userScript:0", null);
                phase++;
            } else if (phase == 5) {
                save(scene, "amorgna-240-201-video-area.png");
                ((CheckBox) scene.lookup("#numeric-source-mode")).fire();
                phase++;
            } else if (phase == 6) {
                check(scene.lookup("#numeric-variable-grid") == original, "Source mode retains cached graph/grid");
                Node videoSheet =((VBox) scene.lookup("#numeric-sheets")).getChildren().stream().filter(n -> "Conversation 226 / Dialogue 1007/userScript:0".equals(n.getProperties().get("source-block"))).findFirst().orElseThrow();
                Label warning = videoSheet.lookupAll(".numeric-cell").stream().filter(n -> n instanceof Label l &&
                    l.getText().contains("⚠")).map(n ->(Label) n).findFirst().orElseThrow();
                click(warning);
                root.applyCss();
                root.layout();
                phase++;
            } else if (phase == 7 && scene.lookup("#numeric-source-text") instanceof TextArea text) {
                check(text.getText().contains("Source route DAG") && text.getText().contains("stopped: DEPTH_BUDGET at 226:960"),
                    "Detailed DAG source includes boundary reasons");
                var scroll =(ScrollPane) root.getCenter();
                double y = scroll.getContent().sceneToLocal(text.localToScene(0, 0)).getY();
                scroll.setVvalue(y / Math.max(1, scroll.getContent().getLayoutBounds().getHeight() - scroll.getViewportBounds().getHeight()));
                int boundary = text.getText().indexOf("stopped:");
                text.positionCaret(Math.max(0, boundary));
                phase++;
            } else if (phase == 8) {
                save(scene, "route-boundary-source-mode.png");
                ((CheckBox) scene.lookup("#numeric-source-mode")).fire();
                select((TextField) scene.lookup("#variable-query"), "BaseGame.Election_Ending_Vote");
                phase++;
            } else if (phase == 9) {
                if (choose(scene, "BaseGame.Election_Ending_Vote")) {
                    began = System.nanoTime();
                    phase++;
                }
            } else if (phase == 10 && scene.lookup("#numeric-variable-grid") instanceof NumericVariableGrid grid &&
                grid != original) {
                root.applyCss();
                root.layout();
                normal(scene);
                check(((VBox) scene.lookup("#numeric-sheets")).getChildren().size() == 89, "All 89 independent Election write blocks mounted");
                System.out.println("Actual Election inspector load seconds=" +(System.nanoTime() - began) / 1e9);
                scrollTo(scene, "Conversation 244 / Dialogue 987/userScript:0", null);
                phase++;
            } else if (phase == 11) {
                save(scene, "election-244-987.png");
                var labels = scene.lookup("#numeric-variable-grid").lookupAll(".numeric-cell");
                Label cell = labels.stream().filter(n -> n instanceof Label l && l.getText().contains(" = true")).map(n ->(Label) n).findFirst().orElseThrow();
                click(cell);
                check(cell.isUnderline(), "Huge variable remains interactive");
                ((Button) scene.lookup("#numeric-clear-focus")).fire();
                check(!cell.isUnderline(), "Huge variable clears focus");
                exit = 0;
                System.out.println("PASS: exact MOV blocks, no unresolved condition cells, source DAG evidence, full Election aggregate and focus");
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
    private static void normal(Scene scene) {
        check(!((CheckBox) scene.lookup("#numeric-source-mode")).isSelected(), "Default grid-only mode");
        check(((ScrollPane)((BorderPane) scene.getRoot()).getCenter()).getContent() == scene.lookup("#numeric-variable-grid"),
            "Only spreadsheet in center");
        for (Node n: scene.lookup("#numeric-variable-grid").lookupAll(".numeric-cell")) if (n instanceof Label l) {
            check(!l.getText().contains("Incoming route unresolved"), "Proof boundary never appears as condition text");
            check(!l.getText().matches("Route \\d+(?: ⚠)?"), "No artificial section-number route label");
            check(l.getHeight()<900, "No pathological vertically stretched condition/route cell: " + l.getText());
        }
    }
    private static void select(TextField field, String value) {
        field.setText(value);
        click(field);
    }
    private static boolean choose(Scene scene, String value) {
        var picker =(VariablePicker) scene.lookup("#variable-query").getParent();
        for (Node n: picker.choices().lookupAll(".list-cell")) if (n instanceof ListCell<?> cell && value.equals(cell.getItem())) {
            click(cell);
            return true;
        }
        return false;
    }
    private static void scrollTo(Scene scene, String id, String condition) {
        Node sheet =((VBox) scene.lookup("#numeric-sheets")).getChildren().stream().filter(n -> id.equals(n.getProperties().get("source-block"))).findFirst().orElseThrow();
        Node target = sheet;
        if (condition != null) target = sheet.lookupAll(".numeric-cell").stream().filter(n -> n instanceof Label l &&
            l.getText().contains(condition) && !l.getText().startsWith("Route")).findFirst().orElseThrow();
        ScrollPane scroll =(ScrollPane)((BorderPane) scene.getRoot()).getCenter();
        double y = scroll.getContent().sceneToLocal(target.localToScene(0, 0)).getY();
        scroll.setHvalue(0);
        scroll.setVvalue(y / Math.max(1, scroll.getContent().getLayoutBounds().getHeight() - scroll.getViewportBounds().getHeight()));
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
        Files.createDirectories(Path.of("docs/route-validation"));
        javax.imageio.ImageIO.write(bitmap, "png", Path.of("docs/route-validation", file).toFile());
    }
}
