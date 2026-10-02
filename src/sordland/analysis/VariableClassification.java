package sordland.analysis;

import java.util.*;
import static sordland.analysis.VariableIndex.*;
import static sordland.analysis.VariableAnalyzer.Layout;

public final class VariableClassification {
    private VariableClassification() {
    }
    public static boolean write(Occurrence o) {
        return o.access() == Access.WRITE || o.access() == Access.READ_WRITE;
    }
    public static Layout classify(List<Occurrence> occurrences) {
        int writes = 0, numeric = 0, booleans = 0;
        for (Occurrence o: occurrences) if (write(o)) {
            writes++;
            var e = o.effect();
            if (e == null) continue;
            if (ConditionLogic.number(e.value()) != null) numeric++;
            if (e.operator().equals("=") && e.value().op().equals("literal") && Set.of("true", "false").contains(e.value().value())) booleans++;
        }
        return writes == 0 ? Layout.EVIDENCE: booleans == writes ? Layout.BOOLEAN: numeric> writes / 2 ? Layout.NUMERIC: Layout.VALUES;
    }
}
