package sordland;

import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import sordland.graph.Graph;
import sordland.ui.Theme;
import sordland.ui.TextMeasurer;
import java.util.*;
import static sordland.TestSupport.*;

final class ThemeTypographyChecks {
    static void run() {
        Color text = Color.web(Theme.TEXT), muted = Color.web(Theme.MUTED);
        var backgrounds = new LinkedHashSet<Color>();
        for (var kind: Graph.Kind.values()) if (kind != Graph.Kind.JUNCTION) for (String type: List.of("Conversation",
            "Decision", "Bill", "News", "Ending", "Conditional instruction", "Unknown")) backgrounds.add(Theme.semanticFill(new Graph.Node("n",
            kind, "", "", "", type, "", 1, null, null)));
        for (String hex: List.of(Theme.BACKGROUND, Theme.SURFACE, Theme.INPUT, Theme.BAND_DARK, Theme.BAND_LIGHT,
            Theme.SEARCH)) backgrounds.add(Color.web(hex));
        for (Color background: backgrounds) {
            check(Theme.contrast(text, background) >= 4.5, "Main text retains readable contrast on every dark semantic fill");
            check(Theme.contrast(muted, background) >= 4.5, "Metadata text retains readable contrast on every dark semantic fill");
        }
        check(!Theme.BAND_DARK.equals(Theme.BAND_LIGHT), "Alternating turn bands have distinct fills");
        for (String band: List.of(Theme.BAND_DARK, Theme.BAND_LIGHT)) {
            check(Theme.contrast(Color.web(Theme.TURN_TEXT), Color.web(band)) >= 7, "Turn labels have strong contrast on both bands");
            check(Theme.contrast(Color.web(Theme.EDGE), Color.web(band)) >= 4.5, "Progression connectors remain visible against both bands");
        }
        for (int hue = 0; hue<360; hue += 15) check(Theme.contrast(text, Theme.speakerFill(Color.hsb(hue,.4,.98))) >= 4.5,
            "Speaker hue keeps readable dialogue contrast");
        var metrics = new TextMeasurer();
        String sample = "TURN 2 · A Few Days Later… WWW iii — é 😀 " + "ExtremelyLongSourceTitle".repeat(8);
        for (Font font: List.of(TextMeasurer.TITLE, TextMeasurer.BODY, TextMeasurer.META, TextMeasurer.TURN_LABEL,
            TextMeasurer.EDGE_LABEL)) {
            Text glyphs = new Text(sample);
            glyphs.setFont(font);
            equal(glyphs.getLayoutBounds().getWidth(), metrics.width(sample, font), "Width uses the exact rendering font and glyph metrics");
            for (double limit: new double[] {
                0, 5, 20, 100, 318, 640
            }) {
                String shortened = metrics.ellipsize(sample + "\nsecond line", limit, font);
                check(!shortened.contains("\n"), "Turn/edge truncation always uses a single line");
                check(metrics.width(shortened, font) <= limit +.001, "Ellipsis fits through glyph measurement without horizontal compression");
                check(!shortened.chars().anyMatch(c -> Character.isSurrogate((char) c)) || shortened.codePoints().anyMatch(c -> c>0xffff),
                    "Ellipsis retains complete Unicode code points");
            }
            for (String line: metrics.wrap(sample, 318, font)) check(metrics.width(line, font) <= 318.001, "Wrapped bold/body glyphs fit at their normal measured proportions");
        }
        var first = condition("x == true");
        var second = condition("someOtherVariable == false");
        equal(metrics.measure(first, false).height(), metrics.measure(second, false).height(), "Typical condition cards share a consistent minimum height");
        check(TextMeasurer.TURN_LABEL.getSize() >= 22, "Turn headings are visibly larger than card headings");
    }
    private static Graph.Node condition(String body) {
        return new Graph.Node(body, Graph.Kind.CONDITION, "CONDITION", body, "source raw", "Condition", "", 1, null,
            null);
    }
}
