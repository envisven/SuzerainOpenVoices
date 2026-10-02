package sordland.graph;

import java.util.*;

public final class TypeSelection {
    private final Map<String, Boolean> preferences = new LinkedHashMap<>();
    public TypeSelection() {
    }
    public TypeSelection(Map<String, Boolean> values) {
        preferences.putAll(values);
    }
    public Set<String> selectedFor(Collection<String> types) {
        var selected = new LinkedHashSet<String>();
        for (String type: types) if (preferences.getOrDefault(type, !type.equals("News") && !sordland.data.runtime.RuntimeDatabase.TYPES.containsValue(type))) selected.add(type);
        return Collections.unmodifiableSet(selected);
    }
    public void choose(Collection<String> applicable, Set<String> selected) {
        for (String type: applicable) preferences.put(type, selected.contains(type));
    }
    public Map<String, Boolean> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(preferences));
    }
}
