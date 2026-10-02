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

public final class FinalVisualChecks extends Application {
    private static int exit = 2;
    private int phase, ticks;
    private Timeline timer;
    private VariableInspectorWindow inspector;
    private Stage window;
    private NumericVariableGrid agnolia;
    private Map<Node, javafx.geometry.Bounds> geometry;
    private double gridWidth, gridHeight;
    private Dataset data;
    private List<NumericVariableLayout.BonusBlock> economy;
    private sordland.layout.LayoutEngine.Result campaign;
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
                data = Loader.load(Path.of("data", Loader.ENTITY_FILE), Path.of("data", Loader.CONVERSATIONS_FILE));
                economy = NumericVariableLayout.build(new VariableAnalyzer(new VariableIndex(data)).analyze("BaseGame.Economy"));
                Platform.runLater(() -> {
                    inspector = new VariableInspectorWindow(owner, data,() -> {
                    });
                    timer = new Timeline(new KeyFrame(Duration.millis(400), e -> step(owner)));
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
            if (++ticks>900) throw new AssertionError("Final UI timed out at phase " + phase);
            window =(Stage) Window.getWindows().stream().filter(w -> w instanceof Stage s && "Variable Inspector".equals(s.getTitle())).findFirst().orElseThrow();
            Scene scene = window.getScene();
            var root =(BorderPane) scene.getRoot();
            if (phase == 0 && scene.lookup("#variable-query") instanceof TextField query) {
                check(!((CheckBox) scene.lookup("#numeric-source-mode")).isSelected(), "Source OFF default");
                check(!((CheckBox) scene.lookup("#numeric-logic-labels")).isSelected(), "Logic OFF default");
                check(!((CheckBox) scene.lookup("#all-variables")).isSelected(), "Numeric-only default");
                query.setText("BaseGame.AgnoliaTradeDeal_Negotiation");
                click(query);
                phase++;
            } else if (phase == 1) {
                if (choose(scene, "BaseGame.AgnoliaTradeDeal_Negotiation")) phase++;
            } else if (phase == 2 && scene.lookup("#numeric-variable-grid") instanceof NumericVariableGrid grid) {
                agnolia = grid;
                root.applyCss();
                root.layout();
                normal(scene);
                check(labels(grid).stream().anyMatch(l -> l.getText().contains("Of course, Mr. Van Hoorten")), "Agnolia positive choice mounted");
                check(labels(grid).stream().anyMatch(l -> l.getText().contains("Not if you keep acting")), "Agnolia negative choice mounted");
                scroll(scene, "Conversation 77 / Dialogue 176/userScript:0");
                phase++;
            } else if (phase == 3) {
                save(scene, "agnolia-choices.png");
                scroll(scene, "Conversation 77 / Dialogue 175/userScript:0");
                geometry = new IdentityHashMap<>();
                for (Label l: labels(agnolia)) if (visible(l)) geometry.put(l, l.getBoundsInParent());
                gridWidth = agnolia.getWidth();
                gridHeight = agnolia.getHeight();
                ((CheckBox) scene.lookup("#numeric-logic-labels")).fire();
                phase++;
            } else if (phase == 4) {
                root.applyCss();
                root.layout();
                equal(gridWidth, agnolia.getWidth(), "Logic toggle preserves table width");
                equal(gridHeight, agnolia.getHeight(), "Logic toggle preserves table height");
                geometry.forEach((n, b) -> equal(b, n.getBoundsInParent(), "Logic toggle preserves every visible cell rectangle"));
                check(!agnolia.lookupAll(".logic-marker").isEmpty(), "AND/OR markers appear");
                check(agnolia.lookupAll(".logic-marker").stream().allMatch(n -> !n.isManaged()), "Logic markers never participate in sizing");
                save(scene, "logic-labels-on.png");
                ((CheckBox) scene.lookup("#numeric-source-mode")).fire();
                phase++;
            } else if (phase == 5) {
                check(scene.lookup("#numeric-variable-grid") == agnolia, "Source reuses cached grid");
                Label choice = labels(agnolia).stream().filter(l -> l.getText().contains("Of course, Mr. Van Hoorten") &&
                    l.getText().startsWith("[CHOICE]")).findFirst().orElseThrow();
                click(choice);
                root.applyCss();
                root.layout();
                phase++;
            } else if (phase == 6 && scene.lookup("#numeric-source-text") instanceof TextArea text) {
                String value = text.getText();
                check(value.contains("Canonical variable: BaseGame.AgnoliaTradeDeal_Negotiation"), "Canonical name retained");
                check(value.contains("77:159") && value.contains("Original full source field") && value.contains("Of course, Mr. Van Hoorten") &&
                    value.contains("Represented reference:"), "Choice, script and exact source IDs retained");
                scrollNode(scene, text);
                phase++;
            } else if (phase == 7) {
                save(scene, "source-choice-evidence.png");
                ((CheckBox) scene.lookup("#numeric-source-mode")).fire();
                ((CheckBox) scene.lookup("#numeric-logic-labels")).fire();
                TextField query =(TextField) scene.lookup("#variable-query");
                query.setText("BaseGame.Economy");
                click(query);
                phase++;
            } else if (phase == 8) {
                if (choose(scene, "BaseGame.Economy")) phase++;
            } else if (phase == 9 && scene.lookup("#numeric-variable-grid") instanceof NumericVariableGrid grid &&
                grid != agnolia) {
                root.applyCss();
                root.layout();
                normal(scene);
                equal(219,((VBox) scene.lookup("#numeric-sheets")).getChildren().size(), "All 219 Economy blocks mounted");
                String id = economy.stream().filter(b -> b.sections().stream().anyMatch(s -> s.root().columns()>1 &&
                    s.root().rows()>1)).findFirst().orElseThrow().id();
                scroll(scene, id);
                phase++;
            } else if (phase == 10) {
                timer.stop();
                phase = 11;
                save(scene, "economy-complex.png");
                var graph = new RootedCampaignGraphBuilder().build(data, null);
                graph = TypeProjection.project(graph, TypeProjection.defaults(TypeProjection.types(graph)));
                campaign = new LayoutEngine().campaign(graph, Set.of(), new TextMeasurer());
                graphShot(owner, "Turn01_InT_Infrastructure", false, "infrastructure-selection.png");
                graphShot(owner, "Turn02_InT_HighwayContract", true, "infrastructure-contracts.png");
                graphShot(owner, "Turn02_Personal_Funeral", true, "circas-funeral.png");
                graphShot(owner, "Turn02_A_MediaDeal", true, "koronti-meeting.png");
                exit = 0;
                System.out.println("PASS: " + TestSupport.count() + " final desktop checks; Agnolia, Economy, logic geometry, Source evidence and actual rooted screenshots");
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
    private void graphShot(Stage owner, String name, boolean gate, String file) throws Exception {
        var graph = campaign.graph;
        var event = graph.nodes.stream().filter(n -> n.item != null && n.item.internalName().equals(name)).findFirst().orElseThrow();
        String focus = event.id;
        if (gate) {
            focus = graph.edges.stream().filter(e -> e.to.equals(event.id)).findFirst().orElseThrow().from;
            String option = focus;
            if (graph.nodes.stream().anyMatch(n -> n.id.equals(option) && n.kind == Graph.Kind.CHOICE)) focus = graph.edges.stream().filter(e -> e.to.equals(option)).findFirst().orElseThrow().from;
        }
        GraphCanvas canvas = new GraphCanvas();
        BorderPane root = new BorderPane(canvas);
        root.setTop(new ToolBar(new Label("Event view: ROOTED"), new Label("All turns"), new Label(event.item.title())));
        Scene scene = new Scene(root, 1500, 900);
        Theme.apply(scene);
        owner.setScene(scene);
        owner.setWidth(1500);
        owner.setHeight(900);
        owner.show();
        root.applyCss();
        root.layout();
        canvas.setResult(campaign, true);
        canvas.focus(focus);
        canvas.redraw();
        check(campaign.byId.containsKey(focus), "Real rooted source node visible");
        save(scene, file);
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
        check(((BorderPane) scene.getRoot()).getTop().lookupAll(".label").stream().noneMatch(FinalVisualChecks::visible),
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
        Files.createDirectories(Path.of("docs/final-validation"));
        javax.imageio.ImageIO.write(bitmap, "png", Path.of("docs/final-validation", name).toFile());
    }
}
