package sordland;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import sordland.data.runtime.RuntimeDatabaseLoader;
import sordland.graph.PanelGraphBuilder;
import sordland.layout.LayoutEngine;
import sordland.layout.EdgeHitTest;
import sordland.ui.*;
import java.nio.file.*;
import java.util.*;

public final class RuntimeVisualChecks extends Application {
    private static int exit = 2;
    public static void main(String[] args) {
        launch(args);
        System.exit(exit);
    }
    @Override
    public void start(Stage stage) {
        try {
            var db = RuntimeDatabaseLoader.load(Path.of("data/runtime"));
            var graph = PanelGraphBuilder.build(db, "Panel_Budget");
            var result = new LayoutEngine().dialogue(graph, Set.of(), new TextMeasurer());
            var canvas = new GraphCanvas();
            var root = new StackPane(canvas);
            stage.setScene(new Scene(root, 1500, 800));
            stage.setTitle("Budget runtime visual verification");
            stage.show();
            Platform.runLater(() -> {
                try {
                    root.applyCss();
                    root.layout();
                    canvas.setResult(result, true);
                    canvas.fit();
                    save(root, "budget-fit.png");
                    canvas.zoom(2);
                    canvas.focus(graph.panels.getFirst().categories().get(2).headerId());
                    save(root, "budget-education.png");
                    root.resize(result.width, result.height);
                    root.layout();
                    canvas.setResult(result, true);
                    canvas.fit();
                    save(root, "budget-full.png");
                    for (var category: graph.panels.getFirst().categories()) for (var branch: category.branches()) {
                        var b = result.byId.get(branch.effectId());
                        if (result.hit(b.cx(), b.y() + 8) != b) throw new AssertionError("Actual measured choice hit");
                    }
                    boolean hit = false;
                    for (var line: result.lines) for (int i = 1; i<line.points().size(); i++) {
                        var a = line.points().get(i - 1);
                        var b = line.points().get(i);
                        if (Math.hypot(a.x() - b.x(), a.y() - b.y())<20) continue;
                        if (EdgeHitTest.candidates(result.lines,(a.x() + b.x()) / 2,(a.y() + b.y()) / 2, 1, 4).stream().anyMatch(c -> c == line.edge())) hit = true;
                    }
                    if (!hit) throw new AssertionError("Panel edge hit testing");
                    System.out.println("PASS: actual JavaFX Budget canvas, fit, zoom/focus, measured member hits, edge hits and full-scale snapshots.");
                    exit = 0;
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
    }
    private static void save(javafx.scene.Parent node, String filename) throws Exception {
        WritableImage image = node.snapshot(null, null);
        int w =(int) image.getWidth(), h =(int) image.getHeight();
        var bitmap = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        bitmap.setRGB(0, 0, w, h, pixels, 0, w);
        Path dir = Path.of("docs/runtime-validation");
        Files.createDirectories(dir);
        javax.imageio.ImageIO.write(bitmap, "png", dir.resolve(filename).toFile());
    }
}
