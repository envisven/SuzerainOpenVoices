package sordland;

import sordland.graph.Graph;
import sordland.ui.TextMeasurer;
import static sordland.TestSupport.*;

final class TextChecks {
    static void run() {
        var measurer = new TextMeasurer();
        String body = "A long unbroken identifier: " + "SomeVariable".repeat(50) + "\n\nDialogue with é and 😀.";
        var node = new Graph.Node("text", Graph.Kind.CHARACTER, "SPEAKER WITH A LONG TITLE ".repeat(4), body, "Metadata\n".repeat(30),
            "Dialogue", "Speaker", null, null, null, null);
        var size = measurer.measure(node, false);
        var expanded = measurer.measure(node, true);
        check(size.title().size()>1 && size.body().size()>4, "Actual JavaFX font measurements wrap titles and unbroken identifiers");
        check(expanded.height()> size.height(), "Expanded metadata contributes measured height");
        for (String line: size.body()) {
            var text = new javafx.scene.text.Text(line);
            text.setFont(TextMeasurer.BODY);
            check(text.getLayoutBounds().getWidth() <= size.width() - 32 +.01, "Measured body lines fit inside node padding");
        }
        equal(size, measurer.measure(node, false), "Repeated source content keeps stable measured dimensions");
    }
}
