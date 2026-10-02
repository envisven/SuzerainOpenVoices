package sordland.ui;

import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import java.util.*;
import java.util.function.Consumer;

public final class TypeFilterControl extends MenuButton {
    public static final class TypeItem {
        private final String name;
        private final BooleanProperty selected = new SimpleBooleanProperty();
        public TypeItem(String name, boolean selected) {
            this.name = name;
            this.selected.set(selected);
        }
        public String name() {
            return name;
        }
        public BooleanProperty selectedProperty() {
            return selected;
        }
    }
    private final ListView<TypeItem> list = new ListView<>();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(140));
    private Consumer<Set<String>>onChange = ignored -> {
    };
    public TypeFilterControl() {
        super("Types");
        list.setPrefWidth(255);
        list.setCellFactory(CheckBoxListCell.forListView(TypeItem::selectedProperty, new StringConverter<>() {
            @Override
            public String toString(TypeItem item) {
                return item == null ? "": item.name();
            }
            @Override
            public TypeItem fromString(String value) {
                throw new UnsupportedOperationException();
            }
        }));
        Label hint = new Label("Hide cards by type.\nSource routes remain connected.");
        hint.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.MUTED + ";");
        VBox content = new VBox(8, hint, list);
        CustomMenuItem menu = new CustomMenuItem(content, false);
        getItems().add(menu);
        debounce.setOnFinished(e -> onChange.accept(selected()));
    }
    public void configure(List<String> types, Set<String> selected, Consumer<Set<String>>callback) {
        debounce.stop();
        onChange = callback;
        list.getItems().clear();
        for (String name: types) {
            var item = new TypeItem(name, selected.contains(name));
            item.selectedProperty().addListener((p, old, value) -> {
                updateCaption();
                debounce.playFromStart();
            });
            list.getItems().add(item);
        }
        list.setPrefHeight(Math.min(330, Math.max(80, types.size() * 29 + 4)));
        updateCaption();
    }
    public void setSelected(String name, boolean selected) {
        var item = list.getItems().stream().filter(i -> i.name().equals(name)).findFirst().orElseThrow();
        item.selectedProperty().set(selected);
    }
    public Set<String> selected() {
        var out = new LinkedHashSet<String>();
        for (var item: list.getItems()) if (item.selectedProperty().get()) out.add(item.name());
        return Collections.unmodifiableSet(out);
    }
    private void updateCaption() {
        setText("Types " + selected().size() + " / " + list.getItems().size());
    }
}
