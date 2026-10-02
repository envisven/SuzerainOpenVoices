package sordland.ui;

import javafx.geometry.Bounds;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import sordland.analysis.*;
import java.util.function.Consumer;

public final class VariablePicker extends HBox {
    private final TextField query = new TextField();
    private final ListView<String> choices = new ListView<>();
    private final CheckBox allVariables = new CheckBox("All variables");
    private final Popup popup = new Popup();
    private final VariableCatalog catalog;
    public VariablePicker(VariableIndex index, Consumer<String> selected) {
        super(10);
        catalog = new VariableCatalog(index);
        query.setPromptText("Filter quantitative variables… then click an exact option");
        query.setId("variable-query");
        choices.setId("variable-choices");
        allVariables.setId("all-variables");
        HBox.setHgrow(query, Priority.ALWAYS);
        choices.setPlaceholder(new Label("No matching variables"));
        choices.setCellFactory(list -> {
            var cell = new ListCell<String>() {
                @Override
                protected void updateItem(String name, boolean empty) {
                    super.updateItem(name, empty);
                    setText(empty || name == null ? null: index.label(name).equals(name) ? name: index.label(name) + "\n" + name);
                }
            };
            cell.setOnMouseClicked(e -> {
                if (!cell.isEmpty() && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    popup.hide();
                    selected.accept(cell.getItem());
                    e.consume();
                }
            });
            return cell;
        });
        popup.getContent().add(choices);
        popup.getScene().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                popup.hide();
                e.consume();
            }
        });
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setConsumeAutoHidingEvents(false);
        query.setOnMouseClicked(e -> showSuggestions());
        query.setOnKeyTyped(e -> {
            if (!e.isControlDown() && !e.isMetaDown()) showSuggestions();
        });
        query.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                popup.hide();
                e.consume();
            } else if (e.getCode() == KeyCode.DOWN) {
                showSuggestions();
                e.consume();
            }
        });
        query.textProperty().addListener((p, old, value) -> refresh());
        allVariables.setOnAction(e -> {
            refresh();
            query.setPromptText(allVariables.isSelected() ? "Filter all game variables… then click an exact option": "Filter quantitative variables… then click an exact option");
        });
        query.localToSceneTransformProperty().addListener((p, a, b) -> reposition());
        query.widthProperty().addListener((p, a, b) -> reposition());
        sceneProperty().addListener((p, a, b) -> {
            if (b == null) popup.hide();
        });
        getChildren().addAll(query, allVariables);
        refresh();
    }
    private void refresh() {
        choices.getItems().setAll(catalog.filter(query.getText(), allVariables.isSelected()));
        choices.getSelectionModel().clearSelection();
        choices.setPrefHeight(Math.max(68, Math.min(260, 24 + choices.getItems().size() * 44)));
        reposition();
    }
    private void reposition() {
        Bounds bounds = query.localToScreen(query.getBoundsInLocal());
        if (bounds != null) {
            choices.setPrefWidth(bounds.getWidth());
            if (popup.isShowing()) {
                popup.setX(bounds.getMinX());
                popup.setY(bounds.getMaxY());
            }
        }
    }
    public void showSuggestions() {
        Bounds bounds = query.localToScreen(query.getBoundsInLocal());
        if (bounds == null) return;
        if (!popup.isShowing()) {
            popup.show(query, bounds.getMinX(), bounds.getMaxY());
            Theme.apply(popup.getScene());
        }
        reposition();
    }
    public void hideSuggestions() {
        popup.hide();
    }
    public ListView<String> choices() {
        return choices;
    }
    public boolean suggestionsShowing() {
        return popup.isShowing();
    }
}
