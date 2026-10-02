package sordland.analysis;

import java.math.BigDecimal;
import java.util.*;
import sordland.graph.Semantics;
import static sordland.analysis.VariableIndex.*;

public final class WriteAggregation {
    private WriteAggregation() {
    }
    public record Group(List<Occurrence> writes, BigDecimal delta) {
        public Group {
            writes = List.copyOf(writes);
        }
    }
    public static List<Group> proven(List<Occurrence> input) {
        var byField = new LinkedHashMap<String, List<Occurrence>>();
        for (Occurrence o: new LinkedHashSet<>(input)) byField.computeIfAbsent(o.source().key(), k -> new ArrayList<>()).add(o);
        var result = new ArrayList<Group>();
        for (var writes: byField.values()) {
            if (writes.size()<2) continue;
            Occurrence first = writes.getFirst();
            var commands = Semantics.analyze(first.source().original(), "").commands();
            if (commands.size() != writes.size()) continue;
            boolean valid = true;
            BigDecimal total = BigDecimal.ZERO;
            for (int i = 0; i<commands.size(); i++) {
                var command = commands.get(i);
                var effect = VariableSyntax.effect(command.raw());
                if (command.kind() != Semantics.CommandKind.EFFECT || effect == null || !effect.variable().equals(first.variable()) ||
                    !Set.of("+", "-").contains(effect.operator())) {
                    valid = false;
                    break;
                }
                BigDecimal amount = ConditionLogic.number(effect.value());
                if (amount == null) {
                    valid = false;
                    break;
                }
                final int operation = i;
                if (writes.stream().noneMatch(o -> o.operation() == operation && o.expression().equals(command.raw()) &&
                    o.source().equals(first.source()) && o.condition().equals(first.condition()) &&(o.guardProof() == null ||
                    o.guardProof().paths().stream().allMatch(DialogueGuardResolver.Path::complete)))) {
                    valid = false;
                    break;
                }
                total = total.add(effect.operator().equals("-") ? amount.negate(): amount);
            }
            if (valid) result.add(new Group(writes, total));
        }
        return List.copyOf(result);
    }
}
