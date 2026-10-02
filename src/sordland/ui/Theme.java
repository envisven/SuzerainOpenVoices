package sordland.ui;

import javafx.scene.Scene;
import javafx.scene.paint.Color;
import sordland.graph.Graph;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class Theme {
    private Theme() {
    }
    public static final String BACKGROUND = "#111a23", SURFACE = "#1b2733", INPUT = "#14202b", BORDER = "#587084";
    public static final String TEXT = "#f0f4f8", MUTED = "#b9c9d6", ACCENT = "#79ddc8";
    public static final String BAND_DARK = "#14202b", BAND_LIGHT = "#1c2c39", TURN_TEXT = "#d8e7f2";
    public static final String CONDITION = "#422d54", EFFECT = "#213f37", NOTICE = "#4b3825";
    public static final String EDGE = "#a3b6c5", BACK_EDGE = "#c49ada", SEARCH = "#654b1d", SEARCH_BORDER = "#f0c66e";
    public static final String GROUP = "#303b46", GROUP_BORDER = "#77838d";
    public static final String ROOT_STYLE = "-fx-font-family: 'System'; -fx-font-size: 13px; -fx-base: " + SURFACE + "; -fx-background: " + BACKGROUND + "; -fx-control-inner-background: " + INPUT + "; -fx-text-background-color: " + TEXT + "; -fx-text-base-color: " + TEXT + "; -fx-accent: #32685f; -fx-focus-color: " + ACCENT + "; -fx-faint-focus-color: transparent; -fx-background-color: " + BACKGROUND + ";";
    public static final String PANEL_STYLE = "-fx-background-color: " + SURFACE + ";";
    public static final String TOOLBAR_STYLE = PANEL_STYLE + " -fx-border-color: transparent transparent " + BORDER + " transparent;";
    public static final String TEXT_AREA_STYLE = "-fx-control-inner-background: " + INPUT + "; -fx-text-fill: " + TEXT + "; -fx-highlight-fill: #32685f; -fx-highlight-text-fill: " + TEXT + ";";
    public static final String POPUP_STYLE = "-fx-background-color: " + SURFACE + "; -fx-text-fill: " + TEXT + ";";
    private static String stylesheet;
    public static synchronized void apply(Scene scene) {
        scene.getRoot().setStyle(ROOT_STYLE);
        if (stylesheet == null) {
            String css = """
                .label, .check-box, .button, .menu-button, .combo-box, .list-cell, .tree-cell { -fx-text-fill: %s; }
                .text-field, .text-area { -fx-text-fill: %s; -fx-prompt-text-fill: %s; -fx-control-inner-background: %s; }
                .text-area .content, .list-view, .list-cell, .tree-view, .tree-cell { -fx-background-color: %s; }
                .list-cell:selected, .tree-cell:selected, .tree-cell:filled:selected:focused { -fx-background-color: #304c5b; }
                .tree-cell .tree-disclosure-node .arrow { -fx-background-color: #d8e7f2; }
                .menu-item:focused, .menu-item:hover { -fx-background-color: #304c5b; }
                .context-menu, .menu-item { -fx-background-color: %s; }
                .context-menu { -fx-border-color: %s; -fx-background-radius: 7; -fx-border-radius: 7; }
                .scroll-pane, .scroll-pane > .viewport { -fx-background-color: %s; }
                .tooltip { -fx-background-color: %s; -fx-text-fill: %s; -fx-font-size: 13px; }
                .check-box .box { -fx-background-color: #283d4d; -fx-border-color: %s; -fx-border-radius: 3; }
                .check-box:selected .mark { -fx-background-color: %s; }
                """.formatted(TEXT,
                TEXT, MUTED, INPUT, INPUT, SURFACE, BORDER, BACKGROUND, SURFACE, TEXT, BORDER, ACCENT);
            try {
                var path = Files.createTempFile("sordland-theme-", ".css");
                Files.writeString(path, css, StandardCharsets.UTF_8);
                path.toFile().deleteOnExit();
                stylesheet = path.toUri().toString();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to prepare the shared application theme", e);
            }
        }
        if (!scene.getStylesheets().contains(stylesheet)) scene.getStylesheets().add(stylesheet);
    }
    public static Color semanticFill(Graph.Node node) {
        return Color.web(switch (node.kind) {
            case CONDITION -> CONDITION;
            case EFFECT -> EFFECT;
            case NARRATOR -> "#303b47";
            case CHOICE -> "#293f63";
            case CONTROL, TERMINAL -> "#2c3a43";
            case REFERENCE, NOTICE -> NOTICE;
            case CHARACTER -> "#253b47";
            case JUNCTION -> EDGE;
            case EVENT -> switch (node.type) {
                case "Conversation" -> "#24404d";
                case "Decision" -> "#3b3556";
                case "Bill" -> "#244739";
                case "News" -> "#4c382b";
                case "Ending" -> "#503241";
                case "Conditional instruction" -> "#244447";
                default -> "#354153";
            };
        });
    }
    public static Color speakerFill(Color source) {
        return Color.hsb(source.getHue(), Math.min(.5, Math.max(.25, source.getSaturation())),.29);
    }
    public static double contrast(Color a, Color b) {
        double first = luminance(a), second = luminance(b);
        return(Math.max(first, second) +.05) /(Math.min(first, second) +.05);
    }
    private static double luminance(Color c) {
        return.2126 * linear(c.getRed()) +.7152 * linear(c.getGreen()) +.0722 * linear(c.getBlue());
    }
    private static double linear(double v) {
        return v <=.04045 ? v / 12.92: Math.pow((v +.055) / 1.055, 2.4);
    }
}
