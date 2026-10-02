package sordland.analysis;

public enum SearchMode {
    GRAPH_SEARCH("Current view"), VARIABLE_INSPECTOR("Game variable");
    private final String label;
    SearchMode(String label) {
        this.label = label;
    }
    @Override
    public String toString() {
        return label;
    }
}
