package sordland;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import sordland.data.*;
import sordland.analysis.SearchMode;
import sordland.data.Domain.*;
import sordland.graph.*;
import sordland.layout.LayoutEngine;
import sordland.ui.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public final class Main extends Application {
    private Stage stage;
    private BorderPane root;
    private StackPane center;
    private VBox top;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sordland-model");
        t.setDaemon(true);
        return t;
    });
    private VariableInspectorWindow variableWindow;
    private final SourceNavigation sourceNavigation = new SourceNavigation(this::jumpToSource);
    private final MenuButton searchModeControl = new MenuButton(SearchMode.GRAPH_SEARCH.toString());
    private SearchMode searchMode = SearchMode.GRAPH_SEARCH;
    private Dataset data;
    private View current, campaign;
    private final Deque<View> history = new ArrayDeque<>();
    private final Label status = new Label("Loading source data…"), notice = new Label(), title = new Label("Sordland campaign");
    private final TextField search = new TextField();
    private final ComboBox<String> turn = new ComboBox<>();
    private final TypeFilterControl typeFilter = new TypeFilterControl();
    private final TypeSelection typePreferences = new TypeSelection();
    private final CheckBox speakerColors = new CheckBox("Speaker Colors");
    private final Button ignoredData = new Button("Ignored data");
    private final Button runtimeSources = new Button("Runtime sources");
    private final Button back = new Button("← Back"), itemDetails = new Button("Item details"), eventView = new Button("Event view: ROOTED");
    private HBox filters;
    private boolean rootedCampaign = true;
    private int smokeStage;
    private View smokeReturnView;
    private Graph smokeCanonical;
    private final List<Button> zoomButtons = new ArrayList<>();
    private final ActorFilterControl actorFilter = new ActorFilterControl();
    private Task<?> activeTask;
    private final Map<String, javafx.scene.paint.Color> speakerPalette = new HashMap<>();
    private final Set<String> campaignExpanded = new HashSet<>();
    private VBox inspector;
    private Path dataDir;
    private String smokeDir;
    private static boolean smokeMode;
    private static volatile int smokeExitCode = 2;
    private long revision;
    private boolean initializing;
    private static final String STYLE = Theme.ROOT_STYLE;
    private static final class View {
        final GraphCanvas canvas;
        final Graph graph;
        final boolean campaign;
        final String name;
        final Item item;
        final javafx.scene.Node detail;
        final Set<String> expanded;
        final List<String> actors, types;
        final Set<String> selectedActors = new LinkedHashSet<>(), selectedTypes = new LinkedHashSet<>();
        String query = "", lastSearch = "", campaignTurn = "All turns";
        int matchIndex = -1;
        View(GraphCanvas canvas, Graph graph, boolean campaign, String name, Item item, javafx.scene.Node detail,
            Set<String> expanded) {
            this.canvas = canvas;
            this.graph = graph;
            this.campaign = campaign;
            this.name = name;
            this.item = item;
            this.detail = detail;
            this.expanded = expanded;
            actors = graph != null && !campaign ? ActorProjection.actors(graph): List.of();
            selectedActors.addAll(actors);
            types = graph != null && campaign ? TypeProjection.types(graph): List.of();
            selectedTypes.addAll(TypeProjection.defaults(types));
        }
    }
    @Override
    public void start(Stage stage) {
        stage.setOnHidden(e -> {
            if (variableWindow != null) variableWindow.close();
        });
        this.stage = stage;
        dataDir = Path.of(System.getProperty("suzerain.data", "data")).toAbsolutePath();
        for (String arg: getParameters() == null ? List.<String> of(): getParameters().getRaw()) {
            if (arg.startsWith("--data=")) dataDir = Path.of(arg.substring(7)).toAbsolutePath();
            if (arg.startsWith("--smoke=")) smokeDir = arg.substring(8);
        }
        root = new BorderPane();
        root.setStyle(STYLE);
        center = new StackPane();
        root.setCenter(center);
        top = new VBox();
        top.setStyle(Theme.TOOLBAR_STYLE);
        Label brand = new Label("SUZERAIN  /  SORDLAND");
        brand.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #c5d3e1;");
        Label offline = new Label("OFFLINE SOURCE EXPLORER");
        offline.setStyle("-fx-font-size: 10px; -fx-text-fill: #bac9d8;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button diagnostics = new Button("Data notes");
        diagnostics.setOnAction(e -> diagnostics());
        Button files = new Button("Data files…");
        files.setOnAction(e -> load(true));
        HBox mast = new HBox(12, brand, spacer, offline, files, diagnostics);
        mast.setAlignment(Pos.CENTER_LEFT);
        mast.setPadding(new Insets(12, 20, 9, 20));
        back.setOnAction(e -> goBack());
        back.setVisible(false);
        back.setManaged(false);
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #eef3f9;");
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);
        Button minus = new Button("−"), plus = new Button("+"), fit = new Button("Fit"), readable = new Button("Readable");
        minus.setOnAction(e -> {
            if (current != null && current.canvas != null) current.canvas.zoom(1 / 1.25);
        });
        plus.setOnAction(e -> {
            if (current != null && current.canvas != null) current.canvas.zoom(1.25);
        });
        fit.setOnAction(e -> {
            if (current != null && current.canvas != null) current.canvas.fit();
        });
        readable.setOnAction(e -> {
            if (current != null && current.canvas != null) current.canvas.actualSize();
        });
        speakerColors.setOnAction(e -> {
            if (current != null && current.canvas != null) current.canvas.setSpeakerColors(speakerColors.isSelected());
        });
        speakerColors.setVisible(false);
        speakerColors.setManaged(false);
        zoomButtons.addAll(List.of(minus, plus, fit, readable));
        itemDetails.setOnAction(e -> {
            if (current != null && current.item != null) {
                history.push(current);
                show(new View(null, null, false, current.name, current.item, detail(current.item), Set.of()));
            }
        });
        eventView.setOnAction(e -> {
            if (current != null && current.campaign) {
                rootedCampaign = !isRooted(current);
                updateTurnChoices();
                rebuildCampaign();
            }
        });
        HBox toolbar = new HBox(9, back, title, eventView, itemDetails, minus, plus, fit, readable, speakerColors,
            sourceNavigation.toggle("viewer-jump-source"));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 20, 12, 20));
        searchModeControl.setId("viewer-search-mode");
        search.setPromptText("Find title or database name…");
        search.setPrefWidth(340);
        search.setOnAction(e -> search());
        Button find = new Button("Find");
        find.setOnAction(e -> search());
        for (SearchMode mode: SearchMode.values()) {
            MenuItem choice = new MenuItem(mode.toString());
            choice.setOnAction(e -> {
                if (mode == SearchMode.GRAPH_SEARCH) {
                    if (variableWindow != null) variableWindow.close();
                    return;
                }
                if (data == null) return;
                searchMode = mode;
                searchModeControl.setText(mode.toString());
                if (variableWindow == null) variableWindow = new VariableInspectorWindow(stage, data,() -> {
                    variableWindow = null;
                    searchMode = SearchMode.GRAPH_SEARCH;
                    searchModeControl.setText(searchMode.toString());
                }, sourceNavigation);
                else variableWindow.show();
            });
            searchModeControl.getItems().add(choice);
        }
        typeFilter.setPrefWidth(170);
        turn.setPrefWidth(150);
        turn.setOnAction(e -> {
            if (!initializing) rebuildCampaign();
        });
        ignoredData.setOnAction(e -> openIgnoredData());
        runtimeSources.setOnAction(e -> {
            if (data != null) {
                history.push(current);
                show(new View(null, null, false, "Runtime sources", null, new IgnoredDataView(data.runtime().sourceInspection()),
                    Set.of()));
            }
        });
        filters = new HBox(10, search, find, searchModeControl, typeFilter, turn, ignoredData, runtimeSources, actorFilter);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.setPadding(new Insets(0, 20, 12, 20));
        notice.setWrapText(true);
        notice.setStyle("-fx-font-size: 11px; -fx-text-fill: #bac9d8;");
        notice.setPadding(new Insets(0, 20, 11, 20));
        top.getChildren().addAll(mast, toolbar, filters, notice);
        root.setTop(top);
        status.setPadding(new Insets(8, 20, 8, 20));
        status.setStyle("-fx-background-color: #17212d; -fx-text-fill: #bac9d8;");
        status.setMaxWidth(Double.MAX_VALUE);
        root.setBottom(status);
        Scene scene = new Scene(root, 1440, 920);
        Theme.apply(scene);
        stage.setTitle("Sordland Tree Viewer");
        stage.setScene(scene);
        stage.setMinWidth(1050);
        stage.setMinHeight(620);
        stage.show();
        load(false);
    }
    private void load(boolean chooseFiles) {
        Path entity = dataDir.resolve(Loader.ENTITY_FILE), conversations = dataDir.resolve(Loader.CONVERSATIONS_FILE);
        if (chooseFiles || !Files.isRegularFile(entity)) entity = choose("Select the entity catalogue", Loader.ENTITY_FILE);
        if (entity == null) {
            if (data == null) loadCancelled();
            return;
        }
        if (chooseFiles || !Files.isRegularFile(conversations)) conversations = choose("Select Sordland conversations",
            Loader.CONVERSATIONS_FILE);
        if (conversations == null) {
            if (data == null) loadCancelled();
            return;
        }
        Path ep = entity, cp = conversations;
        runWork("Reading and validating Sordland source files…",() -> Loader.load(ep, cp), loaded -> {
            if (variableWindow != null) variableWindow.close();
            data = loaded;
            speakerPalette.clear();
            var speakers = new TreeSet<String>();
            data.conversations().values().forEach(c -> c.entries().values().stream().filter(e -> !e.isPlayer() &&
                !e.isNarrator()).forEach(e -> speakers.add(e.speaker())));
            int speakerIndex = 0;
            for (String name: speakers) speakerPalette.put(name, javafx.scene.paint.Color.hsb((speakerIndex++ * 137.50776405003785) % 360,
            .35,.30));
            initializing = true;
            turn.setValue("All turns");
            initializing = false;
            updateTurnChoices();
            rebuildCampaign();
        });
    }
    private Path choose(String caption, String name) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(caption + " (" + name + ")");
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("JSON files", "*.json"), new FileChooser.ExtensionFilter("All files",
            "*"));
        if (Files.isDirectory(dataDir)) chooser.setInitialDirectory(dataDir.toFile());
        var file = chooser.showOpenDialog(stage);
        return file == null ? null: file.toPath();
    }
    private void loadCancelled() {
        Label text = new Label("Both source JSON files are needed to open the viewer.");
        Button retry = new Button("Choose files again");
        retry.setOnAction(e -> load(true));
        VBox panel = new VBox(16, text, retry);
        panel.setAlignment(Pos.CENTER);
        center.getChildren().setAll(panel);
        status.setText("Data loading cancelled");
    }
    private static boolean isRooted(View view) {
        return view != null && view.campaign && view.graph.campaign != null;
    }
    private void updateTurnChoices() {
        if (data == null) return;
        String selected = turn.getValue();
        initializing = true;
        var turns = new TreeSet<Integer>();
        if (rootedCampaign) data.gameFlow().turns().forEach(t -> turns.add(t.turnNumber()));
        else {
            data.items().forEach(i -> {
                if (i.turn() != null) turns.add(i.turn());
            });
            data.ancillary().forEach(i -> {
                if (i.turn() != null) turns.add(i.turn());
            });
        }
        var options = new ArrayList<String>();
        options.add("All turns");
        turns.forEach(t -> options.add("Turn " + t));
        if (!rootedCampaign) options.add("Unspecified");
        if (!turn.getItems().equals(options)) turn.getItems().setAll(options);
        turn.setValue(turn.getItems().contains(selected) ? selected: "All turns");
        initializing = false;
    }
    private record CampaignBuild(Graph canonical, LayoutEngine.Result layout, Set<String> selectedTypes) {
    }
    private void rebuildCampaign() {
        if (data == null) return;
        String q = search.getText(), selectedTurn = turn.getValue();
        boolean rooted = rootedCampaign;
        TypeSelection choices = new TypeSelection(typePreferences.snapshot());
        Integer t = selectedTurn != null && selectedTurn.startsWith("Turn ") ? Integer.valueOf(selectedTurn.substring(5)): null;
        runWork(rooted ? "Laying out GameFlow progression…": "Laying out source catalogue…",() -> {
            Graph graph = rooted ? new RootedCampaignGraphBuilder().build(data, t): new CampaignGraphBuilder().build(data,
                q, "All types", t, true);
            if (!rooted && "Unspecified".equals(selectedTurn)) {
                var nodes = graph.nodes.stream().filter(n -> n.turn == null).toList();
                var ids = new HashSet<String>();
                nodes.forEach(n -> ids.add(n.id));
                graph = new Graph(graph.title, nodes, graph.edges.stream().filter(e -> ids.contains(e.from) && ids.contains(e.to)).toList(),
                    graph.diagnostics);
            }
            var selected = choices.selectedFor(TypeProjection.types(graph));
            return new CampaignBuild(graph, new LayoutEngine().campaign(TypeProjection.project(graph, selected), campaignExpanded,
                new TextMeasurer()), selected);
        }, built -> {
            var layout = built.layout();
            var canvas = new GraphCanvas();
            View view = new View(canvas, built.canonical(), true, "Sordland campaign", null, null, campaignExpanded);
            view.selectedTypes.clear();
            view.selectedTypes.addAll(built.selectedTypes());
            configure(view);
            canvas.setResult(layout, true);
            view.query = q;
            view.campaignTurn = selectedTurn;
            campaign = view;
            history.clear();
            show(view);
            canvas.setCampaignSearch(q);
            Platform.runLater(() -> {
                root.applyCss();
                root.layout();
                if (!layout.boxes.isEmpty()) canvas.focus(initialNode(layout));
                if (smokeDir != null) smokeCampaign();
            });
        });
    }
    private void filterTypes(View view, Set<String> selected) {
        Set<String> chosen = Set.copyOf(selected);
        String query = search.getText();
        runWork("Projecting visible types…",() -> new LayoutEngine().campaign(TypeProjection.project(view.graph, chosen),
            view.expanded, new TextMeasurer()), layout -> {
            view.selectedTypes.clear();
            view.selectedTypes.addAll(chosen);
            typePreferences.choose(view.types, chosen);
            view.query = query;
            view.lastSearch = "";
            view.matchIndex = -1;
            view.canvas.setResult(layout, false);
            view.canvas.setCampaignSearch(query);
            show(view);
            if (smokeDir != null) Platform.runLater(this::smokeCampaign);
        }, false);
    }
    private void openIgnoredData() {
        if (data == null) return;
        history.push(current);
        show(new View(null, null, false, "Ignored data", null, new IgnoredDataView(data.ignoredData()), Set.of()));
    }
    private String initialNode(LayoutEngine.Result layout) {
        if (layout.graph.campaign != null) return layout.graph.nodes.getFirst().id;
        return layout.graph.nodes.stream().filter(n -> n.item != null && Boolean.TRUE.equals(Json.object(n.item.raw().get("ConversationProperties")).get("IsOnStart"))).map(n -> n.id).findFirst().orElseGet(() -> layout.graph.nodes.getFirst().id);
    }
    private void search() {
        if (current == null) {
            rebuildCampaign();
            return;
        }
        if (current.campaign) {
            if (!isRooted(current)) {
                rebuildCampaign();
                return;
            }
            String q = search.getText().toLowerCase(Locale.ROOT).trim();
            current.query = search.getText();
            current.canvas.setCampaignSearch(q);
            if (q.isBlank()) {
                current.lastSearch = "";
                current.matchIndex = -1;
                status.setText("GameFlow structure preserved. Enter a title, fragment ID or condition to highlight events.");
                return;
            }
            var matches = current.canvas.result().graph.nodes.stream().filter(n -> GraphCanvas.campaignMatch(n, q)).toList();
            if (matches.isEmpty()) {
                status.setText("No matching visible campaign cards. Hidden types stay hidden.");
                return;
            }
            current.matchIndex = q.equals(current.lastSearch) ?(current.matchIndex + 1) % matches.size(): 0;
            current.lastSearch = q;
            current.canvas.focus(matches.get(current.matchIndex).id);
            status.setText("Match " +(current.matchIndex + 1) + " of " + matches.size() + " · Find again for the next match · progression preserved");
            return;
        }
        if (current.canvas != null) {
            String q = search.getText().toLowerCase(Locale.ROOT).trim();
            var matches = current.canvas.result().graph.nodes.stream().filter(n ->(n.title + " " + n.text + " " + n.source).toLowerCase(Locale.ROOT).contains(q)).toList();
            if (q.isBlank()) {
                status.setText("Enter dialogue text or a source ID, such as 6:12.");
                return;
            }
            if (matches.isEmpty()) {
                status.setText("No matching visible source box. Check the actor filter or try another search.");
                return;
            }
            current.matchIndex = q.equals(current.lastSearch) ?(current.matchIndex + 1) % matches.size(): 0;
            current.lastSearch = q;
            current.canvas.focus(matches.get(current.matchIndex).id);
            status.setText("Match " +(current.matchIndex + 1) + " of " + matches.size() + " · Find again for the next match");
        }
    }
    private void configure(View view) {
        view.canvas.setSpeakerPalette(speakerPalette);
        view.canvas.onStatus = status::setText;
        view.canvas.onNode =(node, expand) -> {
            if (sourceNavigation.enabled()) {
                sourceNavigation.jump(SourceNavigation.node(node));
                return;
            }
            if (expand) {
                if (!view.expanded.add(node.id)) view.expanded.remove(node.id);
                relayout(view);
            } else if (node.item != null) openItem(node.item);
            else inspect(node);
        };
        view.canvas.onEdge = this::inspectEdge;
    }
    private void relayout(View view) {
        runWork("Updating measured layout…",() -> view.campaign ? new LayoutEngine().campaign(TypeProjection.project(view.graph,
            view.selectedTypes), view.expanded, new TextMeasurer()): new LayoutEngine().dialogue(ActorProjection.project(view.graph,
            view.selectedActors), view.expanded, new TextMeasurer()), layout -> {
            view.canvas.setResult(layout, false);
            show(view);
            if (smokeDir != null && smokeStage == 9) Platform.runLater(this::smokeFinish);
        });
    }
    private void jumpToSource(SourceNavigation.Target target) {
        if (data == null || target == null) {
            status.setText("No navigable source available.");
            return;
        }
        if (target.entry() != null) {
            EntryKey key = target.entry();
            Conversation conversation = data.conversations().get(key.conversationId());
            if (conversation == null || data.entry(key) == null) {
                status.setText("No navigable source available.");
                return;
            }
            if (current != null && !current.campaign && current.canvas != null) {
                var node = current.canvas.result().graph.nodes.stream().filter(n -> key.equals(n.source)).findFirst();
                if (node.isPresent()) {
                    current.canvas.focus(node.get().id);
                    stage.toFront();
                    stage.requestFocus();
                    return;
                }
            }
            Item item = data.items().stream().filter(i -> Objects.equals(i.conversationId(), key.conversationId())).findFirst().orElse(null);
            stage.toFront();
            stage.requestFocus();
            buildDialogue(item == null ? conversation.title(): item.title(), item,() -> new DialogueGraphBuilder().build(data,
                conversation), key);
            return;
        }
        Item item = java.util.stream.Stream.concat(data.items().stream(), data.ancillary().stream()).filter(i -> i.id().equals(target.itemId())).findFirst().orElse(null);
        if (item == null) item = data.runtime().collections.values().stream().flatMap(java.util.Collection::stream).filter(e -> e.item().id().equals(target.itemId()) ||
            e.location().equals(target.location()) ||(target.location() != null && target.location().startsWith(e.location() + " / "))).map(sordland.data.runtime.RuntimeDatabase.Entity::item).findFirst().orElse(null);
        if (item == null) {
            status.setText("No navigable source available.");
            return;
        }
        stage.toFront();
        stage.requestFocus();
        openItem(item);
    }
    private void openItem(Item item) {
        if (item.type().equals("Decision panel")) {
            buildDialogue(item.title(), item,() -> PanelGraphBuilder.build(data.runtime(), item.internalName()));
            return;
        }
        if (item.conversationId() == null) {
            history.push(current);
            show(new View(null, null, false, item.title(), item, detail(item), Set.of()));
            return;
        }
        Conversation conversation = data.conversations().get(item.conversationId());
        if (conversation == null) {
            error(new IllegalArgumentException("Unresolved conversation ID: " + item.conversationId()));
            return;
        }
        buildDialogue(item.title(), item,() -> new DialogueGraphBuilder().build(data, conversation));
    }
    private void buildDialogue(String name, Item item, Supplier<Graph> make) {
        buildDialogue(name, item, make, null);
    }
    private void buildDialogue(String name, Item item, Supplier<Graph> make, EntryKey focus) {
        View previous = current;
        runWork("Building the complete source dialogue graph…",() -> {
            Graph graph = make.get();
            return new LayoutEngine().dialogue(graph, Set.of(), new TextMeasurer());
        }, layout -> {
            var canvas = new GraphCanvas();
            View view = new View(canvas, layout.graph, false, name, item, null, new HashSet<>());
            configure(view);
            canvas.setSpeakerColors(speakerColors.isSelected());
            canvas.setResult(layout, true);
            if (previous != null) history.push(previous);
            show(view);
            Platform.runLater(() -> {
                root.applyCss();
                root.layout();
                if (focus != null) {
                    layout.graph.nodes.stream().filter(n -> focus.equals(n.source)).findFirst().ifPresent(n -> canvas.focus(n.id));
                    stage.toFront();
                    stage.requestFocus();
                } else if (!layout.boxes.isEmpty()) canvas.focus(initialNode(layout));
                if (smokeDir != null && smokeStage == 7) smokeDialogue();
            });
        });
    }
    private void show(View view) {
        if (current != null && current != view) current.query = search.getText();
        current = view;
        if (view.campaign) {
            rootedCampaign = isRooted(view);
            updateTurnChoices();
            initializing = true;
            typePreferences.choose(view.types, view.selectedTypes);
            typeFilter.configure(view.types, view.selectedTypes, chosen -> {
                if (current == view) filterTypes(view, chosen);
            });
            turn.setValue(turn.getItems().contains(view.campaignTurn) ? view.campaignTurn: "All turns");
            initializing = false;
        }
        search.setText(view.query);
        root.setRight(null);
        center.getChildren().setAll(view.canvas == null ? view.detail: view.canvas);
        title.setText(view.name);
        title.setTooltip(new Tooltip(view.name));
        back.setVisible(!history.isEmpty());
        back.setManaged(!history.isEmpty());
        speakerColors.setVisible(!view.campaign && view.canvas != null);
        speakerColors.setManaged(!view.campaign && view.canvas != null);
        eventView.setVisible(view.campaign);
        eventView.setManaged(view.campaign);
        eventView.setText(isRooted(view) ? "Event view: ROOTED": "Event view: PLAIN");
        typeFilter.setVisible(view.campaign);
        typeFilter.setManaged(view.campaign);
        turn.setVisible(view.campaign);
        turn.setManaged(view.campaign);
        ignoredData.setVisible(view.campaign);
        ignoredData.setManaged(view.campaign);
        runtimeSources.setVisible(view.campaign);
        runtimeSources.setManaged(view.campaign);
        search.setPromptText(view.campaign ? "Find title or database name…": "Find text or source ID…");
        search.setVisible(view.canvas != null);
        search.setManaged(view.canvas != null);
        filters.getChildren().get(1).setVisible(view.canvas != null);
        filters.getChildren().get(1).setManaged(view.canvas != null);
        boolean dialogue = !view.campaign && view.canvas != null;
        actorFilter.setVisible(dialogue);
        actorFilter.setManaged(dialogue);
        if (dialogue) actorFilter.configure(view.actors, view.selectedActors, chosen -> {
            if (current == view) filterActors(view, chosen);
        });
        itemDetails.setVisible(view.item != null && view.canvas != null);
        itemDetails.setManaged(view.item != null && view.canvas != null);
        zoomButtons.forEach(b -> b.setDisable(view.canvas == null));
        notice.setText(view.campaign ?(isRooted(view) ? "GAMEFLOW PROGRESSION  ·  Turn → Step → Fragment. Conditions control activation; junctions show progression without assuming event causality. Unchecked types disappear; source alternatives remain connected. News attaches only by exact source proof.  ·  Click a card to explore; › expands metadata.": "SOURCE CATALOGUE  ·  Flat source items. Types are independent checkboxes; News starts hidden. Use ROOTED for GameFlow progression.  ·  Click a title to explore; › expands metadata."): view.canvas == null ? "SOURCE OPTIONS  ·  Conditions and instructions are displayed exactly; this viewer does not execute them.": "SOURCE ROUTES · Each source entry appears once; conditions and effects are not evaluated. Dashed arrows mark loops. Crossing gaps are not junctions. Click an arrow to trace it or a node for metadata.");
        if (view.canvas != null) {
            view.canvas.setSpeakerColors(speakerColors.isSelected());
            view.canvas.redraw();
        } else status.setText(view.item == null ? data.ignoredData().size() + " retained source records": view.item.type() + "  ·  " + view.item.internalName());
    }
    private void filterActors(View view, Set<String> selected) {
        Set<String> chosen = Set.copyOf(selected);
        runWork("Updating actor visibility…",() -> new LayoutEngine().dialogue(ActorProjection.project(view.graph,
            chosen), view.expanded, new TextMeasurer()), layout -> {
            view.selectedActors.clear();
            view.selectedActors.addAll(chosen);
            view.matchIndex = -1;
            view.lastSearch = "";
            view.canvas.setResult(layout, false);
            root.setRight(null);
            if (smokeDir != null && smokeStage == 8) Platform.runLater(this::smokeDialogue);
        }, false);
    }
    private void inspectEdge(Graph.Edge edge) {
        var layout = current.canvas.result();
        var from = layout.byId.get(edge.from).node();
        var to = layout.byId.get(edge.to).node();
        StringBuilder text = new StringBuilder("FROM: " + edgeEndpoint(from) + "\nTO: " + edgeEndpoint(to) + "\n" + edge.label);
        if (edge.back) text.append("\nSource back-reference / loop");
        if (edge.sourceLink != null) text.append("\n\nEXACT JSON POINTER\n").append(edge.sourceFrom).append(" → ").append(edge.sourceTo).append("\nOrder: ").append(edge.sourceLink.order() + 1).append("\nPriority: ").append(edge.sourceLink.priority()).append("\nConnector: ").append(edge.sourceLink.connector());
        if (!edge.projectionPath.isEmpty()) {
            text.append("\n\nVISUAL PROJECTION THROUGH HIDDEN CARDS\nThis arrow abbreviates the following original connectors; it is not a new JSON link.\n");
            for (var original: edge.projectionPath) text.append(original.sourceLink == null ? original.from + " → " + original.to: original.sourceFrom + " → " + original.sourceTo).append("  ").append(original.label).append('\n');
        } else if (edge.sourceLink == null) text.append(isRooted(current) ? "\n\nGameFlow campaign connector. Neutral junctions and group ports are layout mechanisms; only condition/choice evidence stated above establishes a specific event branch.": "\n\nSource mechanic connection: panel page membership, independent option branch, panel completion, or internal source-entry sequence. This is not an additional outgoing dialogue pointer.");
        if (!from.metadata.isBlank()) text.append("\n\nFROM SOURCE METADATA\n").append(from.metadata);
        if (!to.metadata.isBlank()) text.append("\n\nTO SOURCE METADATA\n").append(to.metadata);
        Label heading = new Label("Selected arrow");
        heading.setStyle("-fx-font-weight: bold;");
        Button close = new Button("×");
        close.setOnAction(e -> root.setRight(null));
        HBox header = new HBox(16, heading, close);
        TextArea area = new TextArea(text.toString());
        area.setEditable(false);
        area.setWrapText(true);
        VBox.setVgrow(area, Priority.ALWAYS);
        inspector = new VBox(12, header, area);
        inspector.setPadding(new Insets(16));
        inspector.setPrefWidth(365);
        root.setRight(inspector);
    }
    private static String edgeEndpoint(Graph.Node node) {
        return(node.title.isBlank() ? "Progression junction": node.title) + "\nVisual identity: " + node.id +(node.source == null ? "": "\nSource entry: " + node.source);
    }
    private void goBack() {
        if (!history.isEmpty()) {
            revision++;
            show(history.pop());
        }
    }
    private void inspect(Graph.Node node) {
        Label heading = new Label(node.source == null ? node.title: "Source " + node.source);
        heading.setWrapText(true);
        heading.setStyle("-fx-font-weight: bold; -fx-text-fill: #eef3f9;");
        Button close = new Button("×");
        close.setOnAction(e -> root.setRight(null));
        HBox head = new HBox(12, heading, close);
        HBox.setHgrow(heading, Priority.ALWAYS);
        String raw = node.metadata;
        if (node.source != null) {
            Entry entry = data.entry(node.source);
            if (entry != null) raw += "\n\nRAW ENTRY\n" + Json.pretty(entry.raw());
        }
        TextArea area = new TextArea(raw);
        area.setEditable(false);
        area.setWrapText(true);
        area.setStyle("-fx-font-family: 'monospaced'; -fx-font-size: 11px;");
        VBox.setVgrow(area, Priority.ALWAYS);
        inspector = new VBox(12, head, area);
        inspector.setPadding(new Insets(16));
        inspector.setPrefWidth(365);
        inspector.setStyle("-fx-background-color: #17212d;");
        root.setRight(inspector);
    }
    private javafx.scene.Node detail(Item item) {
        VBox page = new VBox(20);
        page.setPadding(new Insets(30));
        page.setMaxWidth(1050);
        Label subtitle = new Label(item.type() + "  ·  " +(item.turn() == null ? "Turn unspecified": "Turn " + item.turn()) + "  ·  " + item.internalName());
        subtitle.setStyle("-fx-text-fill: #bac9d8;");
        page.getChildren().add(subtitle);
        if (!item.description().isBlank()) page.getChildren().add(copyBlock(item.description(), false));
        if (!item.condition().isBlank()) page.getChildren().add(card("ACTIVATION", CampaignGraphBuilder.display(item.condition()),
            "#443047"));
        if (!item.beginInstruction().isBlank()) page.getChildren().add(card("ON BEGIN", CampaignGraphBuilder.display(item.beginInstruction()),
            "#244237"));
        int i = 0;
        for (Option option: item.options()) {
            VBox content = new VBox(12);
            content.setPadding(new Insets(18));
            content.setStyle("-fx-background-color: #202d3b; -fx-background-radius: 10; -fx-border-color: #405064; -fx-border-radius: 10;");
            Label name = new Label((++i) + ". " + option.title());
            name.setWrapText(true);
            name.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #eef3f9;");
            content.getChildren().add(name);
            if (!option.condition().isBlank()) content.getChildren().add(card("CONDITION", CampaignGraphBuilder.display(option.condition()),
                "#443047"));
            if (!option.instruction().isBlank()) content.getChildren().add(card("SOURCE INSTRUCTIONS / EFFECTS", CampaignGraphBuilder.display(option.instruction()),
                "#244237"));
            page.getChildren().add(content);
        }
        if (!item.endInstruction().isBlank()) page.getChildren().add(card("ON END", CampaignGraphBuilder.display(item.endInstruction()),
            "#244237"));
        if (item.type().equals("Conditional instruction")) {
            var runtime = data.runtime().resolve(item.internalName(), "conditionalinstructiondata");
            if (runtime != null) {
                TitledPane richer = new TitledPane("Additional runtime conditional instruction source", copyBlock(runtime.metadata(),
                    true));
                richer.setExpanded(false);
                page.getChildren().add(richer);
            }
        }
        TitledPane source = new TitledPane("Complete source metadata", copyBlock(Json.pretty(item.raw()), true));
        source.setExpanded(false);
        page.getChildren().add(source);
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #101722; -fx-background-color: #101722;");
        return scroll;
    }
    private VBox card(String title, String body, String color) {
        Label h = new Label(title);
        h.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");
        VBox box = new VBox(9, h, copyBlock(body, true));
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 8;");
        return box;
    }
    private TextArea copyBlock(String value, boolean mono) {
        TextArea text = new TextArea(value);
        text.setEditable(false);
        text.setWrapText(true);
        text.setFocusTraversable(false);
        int rows = Math.min(26, Math.max(4, value.split("\\R").length + value.length() / 110));
        text.setPrefRowCount(rows);
        text.setStyle("-fx-font-size: 13px;" + "-fx-text-fill: #eef3f9;" + "-fx-highlight-fill: #34586c;" + "-fx-highlight-text-fill: #eef3f9;" + "-fx-control-inner-background: #15202c;" + "-fx-background-color: #15202c;" + "-fx-background-insets: 0;" + "-fx-background-radius: 6;" + "-fx-border-color: #405064;" + "-fx-border-radius: 6;" +(mono ? "-fx-font-family: 'Consolas', 'Courier New', monospace;": ""));
        return text;
    }
    private void diagnostics() {
        if (data == null) return;
        String message = "DATA DISCOVERY\n" + String.join("\n\n", data.diagnostics());
        if (current != null && current.canvas != null) message += "\n\nCURRENT GRAPH\n" + String.join("\n\n", current.canvas.result().graph.diagnostics);
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Source coverage and unresolved relationships");
        TextArea area = new TextArea(message);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefSize(850, 570);
        dialog.getDialogPane().setContent(area);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.show();
        Theme.apply(dialog.getDialogPane().getScene());
    }
    private<T> void runWork(String label, Callable<T> job, Consumer<T> success) {
        runWork(label, job, success, true);
    }
    private<T> void runWork(String label, Callable<T> job, Consumer<T> success, boolean freezeToolbar) {
        if (activeTask != null) activeTask.cancel(true);
        long token =++revision;
        top.setDisable(freezeToolbar);
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(30, 30);
        Label message = new Label(label);
        VBox busy = new VBox(16, progress, message);
        busy.setAlignment(Pos.CENTER);
        busy.setStyle("-fx-background-color: rgba(16,23,34,0.96);");
        center.getChildren().add(busy);
        status.setText(label);
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return job.call();
            }
        };
        task.setOnSucceeded(e -> {
            center.getChildren().remove(busy);
            if (token == revision) {
                top.setDisable(false);
                activeTask = null;
                success.accept(task.getValue());
            }
        });
        task.setOnFailed(e -> {
            center.getChildren().remove(busy);
            if (token == revision) {
                top.setDisable(false);
                activeTask = null;
                if (current != null) show(current);
                error(task.getException());
            }
        });
        task.setOnCancelled(e -> center.getChildren().remove(busy));
        Button cancel = new Button("Cancel");
        cancel.setOnAction(e -> {
            revision++;
            task.cancel(true);
            activeTask = null;
            top.setDisable(false);
            if (current != null) show(current);
            else loadCancelled();
        });
        busy.getChildren().add(cancel);
        activeTask = task;
        worker.submit(task);
    }
    private void error(Throwable failure) {
        failure.printStackTrace();
        if (data == null) loadCancelled();
        status.setText("Could not complete operation: " + failure.getMessage());
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle("Sordland viewer");
        alert.setHeaderText("The source files or graph could not be loaded");
        alert.setContentText(failure.getMessage() + "\n\nChoose structurally valid entity and Sordland conversation JSON files.");
        alert.show();
        Theme.apply(alert.getDialogPane().getScene());
        if (smokeDir != null) {
            System.err.println("SMOKE FAILED");
            Platform.exit();
        }
    }
    private void snapshot(String name) throws Exception {
        snapshotNode(name, root);
    }
    private void snapshotNode(String name, javafx.scene.Parent target) throws Exception {
        target.applyCss();
        target.layout();
        WritableImage image = target.snapshot(null, null);
        int w =(int) image.getWidth(), h =(int) image.getHeight();
        var bitmap = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels,
            0, w);
        bitmap.setRGB(0, 0, w, h, pixels, 0, w);
        Path dir = Path.of(smokeDir);
        Files.createDirectories(dir);
        javax.imageio.ImageIO.write(bitmap, "png", dir.resolve(name + ".png").toFile());
    }
    private static void smokeCheck(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("SMOKE: " + message);
    }
    private void smokeDetails(Graph.Node node, String filename) throws Exception {
        View previous = current;
        current.canvas.onNode.accept(node, false);
        smokeCheck(current.canvas == null && current.item == node.item && current.detail != null, "event click opens existing details: " + filename);
        snapshot(filename);
        goBack();
        smokeCheck(current == previous, "Back restores the same campaign view");
    }
    private void smokeCampaign() {
        root.applyCss();
        root.layout();
        try {
            Graph visible = current.canvas.result().graph;
            if (smokeStage == 0) {
                smokeCheck(isRooted(current) && eventView.getText().equals("Event view: ROOTED"), "ROOTED default");
                smokeCheck(current.graph.nodes.getFirst().title.equals("START"), "continuous START root");
                smokeCheck(current.selectedTypes.contains("Condition") && !current.selectedTypes.contains("News"),
                    "Condition ON / News OFF defaults");
                smokeCheck(!current.types.contains("Dialogue fragment"), "ROOTED has no Dialogue fragment category");
                smokeCheck(current.graph.nodes.stream().anyMatch(n -> n.type.equals("News")) && visible.nodes.stream().noneMatch(n -> n.type.equals("News")),
                    "News model exists but unchecked cards disappear");
                snapshot("campaign-rooted-default");
                diagnostics();
                var notesWindow = javafx.stage.Window.getWindows().stream().filter(w -> w != stage && w.isShowing() &&
                    w.getScene() != null).findFirst().orElseThrow();
                snapshotNode("data-notes", notesWindow.getScene().getRoot());
                notesWindow.hide();
                var secondTurn = visible.nodes.stream().filter(n -> n.item != null && Objects.equals(n.turn, 2)).findFirst().orElseThrow();
                current.canvas.focus(secondTurn.id);
                snapshot("turn-band-boundary");
                current.canvas.focus(initialNode(current.canvas.result()));
                typeFilter.show();
                smokeCheck(typeFilter.isShowing(), "Types popup opens");
                smokeCanonical = current.graph;
                var pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
                pause.setOnFinished(e -> {
                    try {
                        var popup = javafx.stage.Window.getWindows().stream().filter(w -> w != stage && w.isShowing() &&
                            w.getScene() != null).findFirst().orElseThrow();
                        snapshotNode("types-checklist", popup.getScene().getRoot());
                        typeFilter.hide();
                        smokeStage = 1;
                        typeFilter.setSelected("News", true);
                        typeFilter.setSelected("Condition", false);
                    } catch (Exception failure) {
                        error(failure);
                    }
                });
                pause.play();
                return;
            }
            if (smokeStage == 1) {
                smokeCheck(current.graph == smokeCanonical, "type projection preserves canonical graph");
                smokeCheck(visible.nodes.stream().noneMatch(n -> n.kind == Graph.Kind.CONDITION), "Condition unchecked disappears");
                Graph.Node news = visible.nodes.stream().filter(n -> n.item != null && n.type.equals("News")).findFirst().orElseThrow();
                current.canvas.focus(news.id);
                snapshot("rooted-news-enabled");
                smokeDetails(news, "news-details");
                Set<String> chosen = Set.copyOf(current.selectedTypes);
                ignoredData.fire();
                smokeCheck(current.detail instanceof IgnoredDataView, "Ignored data opens dedicated inspector");
                snapshot("ignored-data");
                var tree =(TreeView<?>) current.detail.lookup(".tree-view");
                smokeCheck(tree.getRoot().getChildren().size() == 2, "Ignored data groups both retained source files");
                tree.getRoot().getChildren().forEach(g -> g.setExpanded(false));
                snapshot("ignored-file-groups");
                var ignoredFind =(TextField) current.detail.lookup(".text-field");
                ignoredFind.setText("GameFlowData");
                snapshot("ignored-source-search");
                smokeCheck(!tree.getRoot().getChildren().isEmpty(), "Ignored source search remains usable");
                goBack();
                smokeCheck(chosen.equals(current.selectedTypes), "Ignored data Back retains types");
                smokeStage = 2;
                eventView.fire();
                return;
            }
            if (smokeStage == 2) {
                smokeCheck(!isRooted(current) && current.selectedTypes.contains("News") && !current.selectedTypes.contains("Condition"),
                    "PLAIN toggle retains compatible choices");
                snapshot("campaign-plain");
                smokeStage = 21;
                turn.setValue("Turn 1");
                return;
            }
            if (smokeStage == 21) {
                smokeCheck(visible.nodes.stream().allMatch(n -> Objects.equals(n.turn, 1)), "PLAIN turn selection limits the catalogue");
                smokeDetails(visible.nodes.stream().filter(n -> n.item != null && n.item.type().equals("Bill")).findFirst().orElseThrow(),
                    "bill-plain");
                smokeStage = 3;
                for (String name: current.types) typeFilter.setSelected(name, name.equals("News"));
                return;
            }
            if (smokeStage == 3) {
                smokeCheck(current.selectedTypes.equals(Set.of("News")), "checklist supports independent arbitrary selections");
                smokeCheck(visible.nodes.stream().noneMatch(n -> n.kind == Graph.Kind.CONDITION || n.kind == Graph.Kind.EVENT &&
                    !n.type.equals("News")), "only News hides unrelated Conditions and event types");
                snapshot("plain-only-news");
                smokeStage = 31;
                search.setText("Turn03_Decision_Extraction");
                search();
                return;
            }
            if (smokeStage == 31) {
                smokeCheck(visible.nodes.isEmpty(), "PLAIN search cannot resurrect a hidden matching Decision or Condition");
                snapshot("plain-hidden-search");
                smokeStage = 32;
                search.clear();
                search();
                return;
            }
            if (smokeStage == 32) {
                smokeStage = 33;
                turn.setValue("All turns");
                return;
            }
            if (smokeStage == 33) {
                if (!current.selectedTypes.equals(Set.of("News"))) {
                    smokeStage = 34;
                    for (String name: current.types) typeFilter.setSelected(name, name.equals("News"));
                    return;
                }
                smokeStage = 4;
                eventView.fire();
                return;
            }
            if (smokeStage == 34) {
                smokeStage = 4;
                eventView.fire();
                return;
            }
            if (smokeStage == 4) {
                smokeCheck(isRooted(current) && current.selectedTypes.equals(Set.of("News")), "ROOTED only-News choices persist");
                smokeCheck(visible.nodes.stream().noneMatch(n -> n.kind == Graph.Kind.CONDITION), "only News does not preserve unrelated Conditions");
                search.setText("Turn03_Decision_Extraction");
                search();
                smokeCheck(current.canvas.result().graph.nodes.stream().noneMatch(n -> n.item != null && n.item.internalName().equals("Turn03_Decision_Extraction")),
                    "search does not resurrect hidden events");
                search.clear();
                search();
                snapshot("rooted-only-news");
                smokeStage = 5;
                for (String name: current.types) typeFilter.setSelected(name, !name.equals("News"));
                return;
            }
            if (smokeStage == 5) {
                smokeStage = 6;
                turn.setValue("Turn 3");
                return;
            }
            if (smokeStage == 6) {
                smokeCheck(current.graph.nodes.getFirst().title.equals("TURN START"), "isolated turn root");
                var layout = current.canvas.result();
                search.setText("Turn03_Decision_Extraction");
                search();
                smokeCheck(current.canvas.result() == layout, "search preserves projected topology");
                var extraction = layout.byId.get(current.canvas.selected()).node();
                smokeCheck(extraction.item.internalName().equals("Turn03_Decision_Extraction"), "search finds visible event");
                current.canvas.zoom(.8);
                snapshot("gasom-branches");
                current.canvas.fit();
                current.canvas.actualSize();
                smokeCheck(current.canvas.zoom() == 1, "zoom fit and readable function");
                smokeClickEdge();
                snapshot("edge-inspector");
                root.setRight(null);
                smokeDetails(extraction, "decision-rooted");
                smokeCheck(current.query.equals(search.getText()), "Back retains search");
                smokeReturnView = current;
                smokeStage = 7;
                var conversation = visible.nodes.stream().filter(n -> n.item != null && n.item.conversationId() != null).findFirst().orElseThrow();
                current.canvas.onNode.accept(conversation, false);
                return;
            }
        } catch (Exception e) {
            error(e);
        }
    }
    private void smokeDialogue() {
        try {
            if (smokeStage == 7) {
                smokeCheck(!current.campaign && current.canvas != null && current.graph.campaign == null, "existing dialogue opens");
                smokeCheck(actorFilter.isVisible() && !typeFilter.isVisible() && !eventView.isVisible(), "dialogue controls remain appropriate");
                snapshot("dialogue");
                speakerColors.fire();
                snapshot("dialogue-colors");
                smokeStage = 8;
                filterActors(current, Set.of());
                return;
            }
            smokeCheck(current.canvas.result().graph.nodes.stream().noneMatch(n -> n.kind == Graph.Kind.CHARACTER ||
                n.kind == Graph.Kind.NARRATOR || n.kind == Graph.Kind.CHOICE), "actor filtering still hides speech while retaining mechanics");
            snapshot("dialogue-actors-hidden");
            goBack();
            smokeCheck(current == smokeReturnView && isRooted(current), "dialogue Back restores same campaign and state");
            smokeStage = 9;
            Graph.Node event = current.canvas.result().graph.nodes.stream().filter(n -> n.item != null).findFirst().orElseThrow();
            current.canvas.onNode.accept(event, true);
        } catch (Exception e) {
            error(e);
        }
    }
    private void smokeClickEdge() {
        var canvas = current.canvas;
        var layout = canvas.result();
        for (var line: layout.lines) for (int i = 1; i<line.points().size(); i++) {
            var a = line.points().get(i - 1);
            var b = line.points().get(i);
            double wx =(a.x() + b.x()) / 2, wy =(a.y() + b.y()) / 2;
            double x = wx * canvas.zoom() + canvas.panX(), y = wy * canvas.zoom() + canvas.panY();
            if (x<70 || x> canvas.getWidth() - 10 || y<10 || y> canvas.getHeight() - 10 || layout.hit(wx, wy) != null) continue;
            if (sordland.layout.EdgeHitTest.candidates(layout.lines, wx, wy, canvas.zoom(), 6).size() != 1) continue;
            var surface = canvas.getChildrenUnmodifiable().getFirst();
            surface.fireEvent(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_RELEASED, x, y,
                x, y, javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false, false, false, false,
                false, false, true, new javafx.scene.input.PickResult(surface, new javafx.geometry.Point3D(x, y, 0),
                0)));
            smokeCheck(root.getRight() != null && root.getRight().lookupAll(".label").stream().anyMatch(n -> n instanceof Label l &&
                l.getText().equals("Selected arrow")), "Canvas edge hit testing opens the actual arrow inspector");
            return;
        }
        throw new IllegalStateException("SMOKE: no visible unique connector target");
    }
    private void smokeFinish() {
        try {
            smokeCheck(!current.expanded.isEmpty(), "metadata expansion persists");
            snapshot("campaign-metadata");
            smokeCheck(searchMode == SearchMode.GRAPH_SEARCH && searchModeControl.getItems().size() == 2, "two search modes; Current view default");
            View beforeInspector = current;
            searchModeControl.show();
            searchModeControl.getItems().get(1).fire();
            searchModeControl.hide();
            smokeCheck(variableWindow != null && searchMode == SearchMode.VARIABLE_INSPECTOR, "Game variable opens owned inspector");
            variableWindow.close();
            smokeCheck(variableWindow == null && searchMode == SearchMode.GRAPH_SEARCH && current == beforeInspector,
                "closing inspector restores mode and preserves graph view");
            smokeExitCode = 0;
            System.out.println("SMOKE PASSED: ROOTED/PLAIN; multi-select Types; News defaults/evidence/details; Condition projection; turn/search state; ignored data; event/dialogue navigation; actors; metadata; zoom/fit/readable; edge trace. Snapshots at " + smokeDir);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Platform.exit();
        }
    }
    @Override
    public void stop() {
        if (variableWindow != null) variableWindow.close();
        worker.shutdownNow();
        if (smokeMode) System.exit(smokeExitCode);
    }
    public static void main(String[] args) {
        smokeMode = Arrays.stream(args).anyMatch(a -> a.startsWith("--smoke="));
        if (smokeMode) {
            Thread watchdog = new Thread(() -> {
                try {
                    Thread.sleep(60_000);
                    System.err.println("SMOKE FAILED: no completed JavaFX snapshot within 60 seconds. A graphical display is required.");
                    Runtime.getRuntime().halt(2);
                } catch (InterruptedException ignored) {
                }
            }, "smoke-timeout");
            watchdog.setDaemon(true);
            watchdog.start();
        }
        launch(args);
    }
}
