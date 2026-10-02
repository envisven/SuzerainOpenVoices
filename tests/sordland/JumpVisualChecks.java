package sordland;

import javafx.application.*;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import sordland.ui.*;
import sordland.graph.*;
import sordland.data.Domain.*;
import java.util.*;
import static sordland.TestSupport.*;

public class JumpVisualChecks extends Application {
    static int exit = 2;
    Main app;
    Stage viewer, inspector;
    Timeline timer;
    int phase, ticks;
    NumericVariableGrid grid;
    Node chosen;
    double scroll;
    String query;
    GraphCanvas canvas;
    Object oldView;
    public static void main(String[] args) {
        launch(args);
        System.exit(exit);
    }
    public void start(Stage stage) {
        viewer = stage;
        app = new Main();
        app.start(stage);
        timer = new Timeline(new KeyFrame(Duration.millis(400), e -> step()));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }
    Object field(Object object, String name) throws Exception {
        var f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(object);
    }
    void step() {
        try {
            if (++ticks>350) throw new AssertionError("Jump test timeout phase " + phase);
            Scene main = viewer.getScene();
            if (phase == 0 && field(app, "current") != null && !main.getRoot().lookup("#viewer-search-mode").isDisabled()) {
                check(!((CheckBox) main.lookup("#viewer-jump-source")).isSelected(), "viewer defaults OFF");
                ((MenuButton) main.lookup("#viewer-search-mode")).getItems().get(1).fire();
                phase++;
            } else if (phase == 1) {
                inspector =(Stage) Window.getWindows().stream().filter(w -> w instanceof Stage s && "Variable Inspector".equals(s.getTitle())).findFirst().orElseThrow();
                Scene scene = inspector.getScene();
                if (scene.lookup("#variable-query") instanceof TextField q) {
                    check(!((CheckBox) scene.lookup("#inspector-jump-source")).isSelected(), "inspector defaults OFF");
                    q.setText("BaseGame.AgnoliaTradeDeal_Negotiation");
                    click(q);
                    phase++;
                }
            } else if (phase == 2) {
                Scene scene = inspector.getScene();
                var picker =(VariablePicker) scene.lookup("#variable-query").getParent();
                for (Node n: picker.choices().lookupAll(".list-cell")) if (n instanceof ListCell<?> c && "BaseGame.AgnoliaTradeDeal_Negotiation".equals(c.getItem())) {
                    click(c);
                    phase++;
                    break;
                }
            } else if (phase == 3 && inspector.getScene().lookup("#numeric-variable-grid") instanceof NumericVariableGrid g) {
                grid = g;
                Scene scene = inspector.getScene();
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                chosen = grid.lookupAll(".numeric-cell").stream().filter(n -> n instanceof Label l && l.getText().contains("Of course, Mr. Van Hoorten") &&
                    l.getText().startsWith("[CHOICE]")).findFirst().orElseThrow();
                var exact =(SourceNavigation.Target) chosen.getProperties().get("jump-source-target");
                equal(new EntryKey(77, 159), exact.entry(), "choice uses exact original entry");
                oldView = field(app, "current");
                click(chosen);
                check(field(app, "current") == oldView, "OFF preserves viewer");
                check(((Label) chosen).isUnderline(), "OFF retains grid compatibility focus");
                ((CheckBox) scene.lookup("#numeric-logic-labels")).fire();
                ((ScrollPane)((BorderPane) scene.getRoot()).getCenter()).setVvalue(.3);
                scroll =((ScrollPane)((BorderPane) scene.getRoot()).getCenter()).getVvalue();
                query =((TextField) scene.lookup("#variable-query")).getText();
                ((CheckBox) scene.lookup("#inspector-jump-source")).fire();
                check(((CheckBox) main.lookup("#viewer-jump-source")).isSelected(), "inspector synchronizes viewer");
                click(chosen);
                phase++;
            } else if (phase == 4) {
                Object current = field(app, "current");
                canvas =(GraphCanvas) field(current, "canvas");
                if (canvas == null || canvas.result() == null) return;
                var selected = canvas.result().byId.get(canvas.selected());
                if (selected == null || !new EntryKey(77, 159).equals(selected.node().source)) return;
                check(viewer.isShowing() && inspector.isShowing(), "both windows stay open");
                check(viewer.isFocused(), "source viewer comes to front");
                check(canvas.zoom()>.5, "source centered at readable zoom");
                Scene scene = inspector.getScene();
                check(scene.lookup("#numeric-variable-grid") == grid, "same cached grid");
                equal(query,((TextField) scene.lookup("#variable-query")).getText(), "query preserved");
                equal(scroll,((ScrollPane)((BorderPane) scene.getRoot()).getCenter()).getVvalue(), "scroll preserved");
                check(((CheckBox) scene.lookup("#numeric-compact")).isSelected() && !((CheckBox) scene.lookup("#numeric-source-mode")).isSelected() &&
                ((CheckBox) scene.lookup("#numeric-logic-labels")).isSelected(), "inspector controls preserved");
                save(main, "dialogue-source.png");
                Object before = field(app, "current");
                canvas.onNode.accept(new Graph.Node("unbacked", Graph.Kind.NOTICE, "Note", "", "", "", "", null, null,
                    null), false);
                check(field(app, "current") == before, "no source safely ignored");
                ((CheckBox) main.lookup("#viewer-jump-source")).fire();
                check(!((CheckBox) scene.lookup("#inspector-jump-source")).isSelected(), "viewer synchronizes inspector OFF");
                inspector.toFront();
                inspector.requestFocus();
                click(chosen);
                check(field(app, "current") == before, "OFF restored immediately");
                phase++;
            } else if (phase == 5) {
                Scene scene = inspector.getScene();
                check(inspector.isShowing(), "inspector returns intact");
                save(scene, "inspector-retained.png");
                ((CheckBox) main.lookup("#viewer-jump-source")).fire();
                var data =(Dataset) field(app, "data");
                var item = data.items().stream().filter(i -> Objects.equals(i.conversationId(), 13)).findFirst().orElseThrow();
                canvas.onNode.accept(new Graph.Node("event-source", Graph.Kind.EVENT, item.title(), "", "", item.type(),
                    "", item.turn(), item, null), false);
                phase++;
            } else if (phase == 6) {
                Object current = field(app, "current");
                canvas =(GraphCanvas) field(current, "canvas");
                if (canvas == null || canvas.result() == null) return;
                var quote = canvas.result().graph.nodes.stream().filter(n -> new EntryKey(13, 136).equals(n.source) &&
                    n.kind == Graph.Kind.CHOICE).findFirst();
                if (quote.isEmpty()) return;
                canvas.onNode.accept(quote.get(), false);
                equal(new EntryKey(13, 136), canvas.result().byId.get(canvas.selected()).node().source, "Koronti exact quote focused");
                check(((Item) field(current, "item")).conversationId() == 13, "Koronti event/conversation opened");
                check(quote.get().text.contains("They were startled"), "real requested quote");
                save(main, "koronti-exact-quote.png");
                var data =(Dataset) field(app, "data");
                Item bill = data.items().stream().filter(i -> i.type().equals("Bill") && i.conversationId() == null).findFirst().orElseThrow();
                canvas.onNode.accept(new Graph.Node("bill-source", Graph.Kind.EVENT, bill.title(), "", "", bill.type(),
                    "", bill.turn(), bill, null), false);
                equal(bill, field(field(app, "current"), "item"), "bill exact source representation");
                check(inspector.isShowing(), "bill navigation leaves inspector open");
                var navigation =(SourceNavigation) field(app, "sourceNavigation");
                var runtime = data.runtime().collection("policiesdata").getFirst();
                navigation.jump(new SourceNavigation.Target(null, null, runtime.location()));
                equal(runtime.item().id(),((Item) field(field(app, "current"), "item")).id(), "exact runtime location opens source");
                Object unchanged = field(app, "current");
                navigation.jump(new SourceNavigation.Target(new EntryKey(-1, -1), null));
                check(field(app, "current") == unchanged, "invalid exact reference is safely ignored");
                var causes = new sordland.analysis.CausalProvenance(data);
                String token = causes.choice(new EntryKey(13, 136));
                equal(new EntryKey(13, 136), causes.choiceOrigin(token), "generated token retains typed origin");
                check(causes.choiceOrigin(new String(token)) == null, "equal visible text cannot impersonate provenance");
                timer.stop();
                app.stop();
                viewer.close();
                exit = 0;
                System.out.println("PASS shared Jump UI checks " + count());
                Platform.exit();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            timer.stop();
            app.stop();
            viewer.close();
            Platform.exit();
        }
    }
    static void click(Node n) {
        n.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 5, 5, 5, 5, MouseButton.PRIMARY, 1, false, false, false,
            false, false, false, false, false, false, true, null));
    }
    static void save(Scene scene, String name) throws Exception {
        var image = scene.snapshot(null);
        int w =(int) image.getWidth(), h =(int) image.getHeight();
        int[] p = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), p, 0, w);
        var b = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        b.setRGB(0, 0, w, h, p, 0, w);
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("docs/jump-validation"));
        javax.imageio.ImageIO.write(b, "png", java.nio.file.Path.of("docs/jump-validation", name).toFile());
    }
}
