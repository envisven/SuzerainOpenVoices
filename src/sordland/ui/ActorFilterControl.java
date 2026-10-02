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

public final class ActorFilterControl extends MenuButton {
    public static final class ActorFilterItem {
        private final String name;
        private final BooleanProperty selected = new SimpleBooleanProperty(true);
        public ActorFilterItem(String name) {
            this.name = Objects.requireNonNull(name);
        }
        public String name() {
            return name;
        }
        public BooleanProperty selectedProperty() {
            return selected;
        }
    }
    private final ListView<ActorFilterItem> list = new ListView<>();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(140));
    private Consumer<Set<String>>onChange = ignored -> {
    };
    public ActorFilterControl() {
        super("Actors");
        list.setPrefWidth(250);
        list.setCellFactory(CheckBoxListCell.forListView(ActorFilterItem::selectedProperty, new StringConverter<>() {
            @Override
            public String toString(ActorFilterItem item) {
                return item == null ? "": item.name();
            }
            @Override
            public ActorFilterItem fromString(String value) {
                throw new UnsupportedOperationException();
            }
        }));
        Label hint = new Label("Show speech by actor.\nConditions and effects stay visible.");
        hint.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.MUTED + ";");
        VBox content = new VBox(8, hint, list);
        CustomMenuItem item = new CustomMenuItem(content, false);
        getItems().add(item);
        debounce.setOnFinished(e -> onChange.accept(selected()));
    }
    public void configure(List<String> names, Set<String> selected, Consumer<Set<String>>onChange) {
        debounce.stop();
        this.onChange = onChange;
        list.getItems().clear();
        for (String name: names) {
            ActorFilterItem item = new ActorFilterItem(name);
            item.selectedProperty().set(selected.contains(name));
            item.selectedProperty().addListener((value, oldState, newState) -> {
                updateCaption();
                debounce.playFromStart();
            });
            list.getItems().add(item);
        }
        list.setPrefHeight(Math.min(350, Math.max(80, names.size() * 29 + 4)));
        updateCaption();
    }
    private Set<String> selected() {
        var names = new LinkedHashSet<String>();
        for (var item: list.getItems()) if (item.selectedProperty().get()) names.add(item.name());
        return Set.copyOf(names);
    }
    private void updateCaption() {
        setText("Actors " + selected().size() + " / " + list.getItems().size());
    }
}
