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
import sordland.data.*;
import sordland.data.Domain.*;
import sordland.analysis.*;
import sordland.graph.*;
import sordland.layout.*;
import sordland.ui.*;
import java.nio.file.*;
import java.util.*;
import static sordland.TestSupport.*;

public final class TargetBeforeVisualChecks extends Application {
    private static int exit = 2;
    private int phase, ticks;
    private Timeline timer;
    private VariableInspectorWindow inspector;
    private NumericVariableGrid grid;
    private Dataset data;
    private Map<Node, javafx.geometry.Bounds> geometry;
    private double width, height;
    public static void main(String[] args) {
        launch(args);
        System.exit(exit);
    }
    public void start(Stage owner) {
        owner.setScene(new Scene(new StackPane(), 300, 200));
        owner.show();
        new Thread(() -> {
            try {
                data = Loader.load(Path.of("data", Loader.ENTITY_FILE), Path.of("data", Loader.CONVERSATIONS_FILE));
                Platform.runLater(() -> {
                    inspector = new VariableInspectorWindow(owner, data,() -> {
                    });
                    timer = new Timeline(new KeyFrame(Duration.millis(450), e -> step(owner)));
                    timer.setCycleCount(Timeline.INDEFINITE);
                    timer.play();
                });
            } catch (Throwable e) {
                e.printStackTrace();
                Platform.exit();
            }
        }).start();
    }
    private void step(Stage owner) {
        try {
            if (++ticks>600) throw new AssertionError("timeout " + phase);
            Stage window =(Stage) Window.getWindows().stream().filter(w -> w instanceof Stage s && "Variable Inspector".equals(s.getTitle())).findFirst().orElseThrow();
            Scene scene = window.getScene();
            var root =(BorderPane) scene.getRoot();
            var compact =(CheckBox) scene.lookup("#numeric-compact");
            var source =(CheckBox) scene.lookup("#numeric-source-mode");
            var logic =(CheckBox) scene.lookup("#numeric-logic-labels");
            if (phase == 0 && scene.lookup("#variable-query") instanceof TextField q) {
                check(compact.isSelected(), "Compact default ON");
                check(!source.isSelected() && !logic.isSelected(), "Source and logic default OFF");
                q.setText("BaseGame.AMorgnaWesCore");
                click(q);
                phase++;
            } else if (phase == 1) {
                if (choose(scene, "BaseGame.AMorgnaWesCore")) phase++;
            } else if (phase == 2 && scene.lookup("#numeric-variable-grid") instanceof NumericVariableGrid g) {
                grid = g;
                root.applyCss();
                root.layout();
                scroll(scene, "Conversation 226 / Dialogue 1007/userScript:0");
                phase++;
            } else if (phase == 3) {
                save(scene, "gain11-before.png");
                exit = 0;
                timer.stop();
                inspector.close();
                owner.close();
                Platform.exit();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            timer.stop();
            inspector.close();
            owner.close();
            Platform.exit();
        }
    }
    private Label summary(Scene scene) {
        Node block =((VBox) scene.lookup("#numeric-sheets")).getChildren().stream().filter(n -> "Conversation 226 / Dialogue 1007/userScript:0".equals(n.getProperties().get("source-block"))).findFirst().orElseThrow();
        return labels(block).stream().filter(l -> l.getText().startsWith("[EXHAUSTIVE] Resigned:")).findFirst().orElseThrow();
    }
    private void graphShots(Stage owner) throws Exception {
        var graph = new RootedCampaignGraphBuilder().build(data, null);
        var types = TypeProjection.types(graph);
        check(types.contains("Choice"), "Choice category exposed");
        var defaults = TypeProjection.defaults(types);
        check(defaults.contains("Choice"), "Choice default ON");
        TypeFilterControl filter = new TypeFilterControl();
        GraphCanvas canvas = new GraphCanvas();
        BorderPane root = new BorderPane(canvas);
        root.setTop(new ToolBar(new Label("Event view: ROOTED"), filter));
        Scene scene = new Scene(root, 1500, 900);
        Theme.apply(scene);
        owner.setScene(scene);
        owner.show();
        filter.configure(types, defaults, selected -> {
        });
        for (boolean enabled: List.of(true, false)) {
            filter.setSelected("Choice", enabled);
            var projected = TypeProjection.project(graph, filter.selected());
            check(projected.nodes.stream().anyMatch(n -> n.kind == Graph.Kind.CHOICE) == enabled, "Choice hide/restore projection");
            var layout = new LayoutEngine().campaign(projected, Set.of(), new TextMeasurer());
            root.applyCss();
            root.layout();
            canvas.setResult(layout, true);
            var event = projected.nodes.stream().filter(n -> n.item != null && n.item.internalName().equals("Turn02_Personal_Funeral")).findFirst().orElseThrow();
            String focus = projected.edges.stream().filter(e -> e.to.equals(event.id)).findFirst().orElseThrow().from;
            canvas.focus(focus);
            canvas.redraw();
            save(scene, enabled ? "choice-on.png": "choice-off.png");
        }
        filter.setSelected("Choice", true);
        check(filter.selected().contains("Choice"), "Choice restored independently");
    }
    private static List<Label> labels(Node node) {
        return node.lookupAll(".numeric-cell").stream().map(n ->(Label) n).toList();
    }
    private static boolean visible(Node n) {
        for (Node p = n; p != null; p = p.getParent()) if (!p.isVisible()) return false;
        return true;
    }
    private static void normal(Scene scene) {
        var grid = scene.lookup("#numeric-variable-grid");
        check(((ScrollPane)((BorderPane) scene.getRoot()).getCenter()).getContent() == grid, "Only spreadsheet below toolbar/search");
        check(scene.lookup("#numeric-source-references") == null, "No source evidence outside Source mode");
        check(((BorderPane) scene.getRoot()).getTop().lookupAll(".label").stream().noneMatch(TargetBeforeVisualChecks::visible),
            "No status or explanatory text above normal table");
        for (Label l: labels(grid)) if (visible(l)) {
            check(l.getTooltip() == null, "Source tooltips are off in normal mode");
            check(!l.getText().startsWith("Route "), "Technical route IDs hidden");
            check(!l.getText().contains("No additional condition") && !l.getText().contains("Unguarded segments"),
                "No generic filler");
        }
    }
    private static boolean choose(Scene scene, String name) {
        var picker =(VariablePicker) scene.lookup("#variable-query").getParent();
        for (Node n: picker.choices().lookupAll(".list-cell")) if (n instanceof ListCell<?> c && name.equals(c.getItem())) {
            click(c);
            return true;
        }
        return false;
    }
    private static void scroll(Scene scene, String id) {
        Node n =((VBox) scene.lookup("#numeric-sheets")).getChildren().stream().filter(x -> id.equals(x.getProperties().get("source-block"))).findFirst().orElseThrow();
        scrollNode(scene, n);
    }
    private static void scrollNode(Scene scene, Node n) {
        var scroll =(ScrollPane)((BorderPane) scene.getRoot()).getCenter();
        double y = scroll.getContent().sceneToLocal(n.localToScene(0, 0)).getY();
        scroll.setHvalue(0);
        scroll.setVvalue(y / Math.max(1, scroll.getContent().getLayoutBounds().getHeight() - scroll.getViewportBounds().getHeight()));
    }
    private static void click(Node n) {
        n.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 5, 5, 5, 5, MouseButton.PRIMARY, 1, false, false, false,
            false, false, false, false, false, false, true, null));
    }
    private static void save(Scene scene, String name) throws Exception {
        WritableImage image = scene.snapshot(null);
        int w =(int) image.getWidth(), h =(int) image.getHeight();
        int[] px = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), px, 0, w);
        var bitmap = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        bitmap.setRGB(0, 0, w, h, px, 0, w);
        Files.createDirectories(Path.of("docs/target-validation"));
        javax.imageio.ImageIO.write(bitmap, "png", Path.of("docs/target-validation", name).toFile());
    }
}
