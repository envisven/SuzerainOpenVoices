package sordland;

import sordland.analysis.*;
import sordland.data.Domain.*;
import java.util.*;
import static sordland.TestSupport.*;
import static sordland.analysis.VariableIndex.*;

final class VariableChecks {
    static void run(Dataset data) {
        equal(Set.of("BaseGame.Economy"), VariableSyntax.references("BaseGame.Economy + Variable[\"BaseGame.Economy\"] + Variable['BaseGame.Economy']"),
            "Canonical references");
        equal(Set.of(), VariableSyntax.references("EnableNews(\"BaseGame.ContentID\")"), "Quoted content IDs are not variables");
        for (String operator: List.of("=", "+=", "-=", "*=", "/=")) {
            var effect = VariableSyntax.effect("BaseGame.X " + operator + " 2");
            check(effect != null, "Assignment " + operator);
            equal(!operator.equals("="), effect.readsTarget(), "Read/write " + operator);
        }
        var increment = VariableSyntax.effect("Variable['BaseGame.X'] = Variable[\"BaseGame.X\"] + 1");
        equal("+", increment.operator(), "Normalize self increment");
        var condition = VariableSyntax.parse("BaseGame.A == true && (BaseGame.B >= 8 || not BaseGame.C)");
        equal("and", condition.op(), "AND precedence");
        equal("or", condition.children().getLast().op(), "Nested OR retained");
        check(condition.known(), "Condition AST known");
        check(!VariableSyntax.parse("SomeFunction(BaseGame.A)").known(), "Unknown calls");
        check(!VariableSyntax.parse("BaseGame.A ==").known(), "Malformed predicate");
        equal("NOT (BaseGame.A)", VariableSyntax.parse("BaseGame.A == false").display(x -> x), "Boolean display");
        var fixture = new Dataset(List.of(new Item("test", "Decision", "Test", "Test", "Sordland/Test", 1, null, "BaseGame.Guard == true",
            "BaseGame.X = BaseGame.X + 1;", "if BaseGame.A then\nBaseGame.X = 10\nend", "", List.of(), Map.of())),
            Map.of(), List.of(), List.of());
        var small = new VariableIndex(fixture);
        equal(2, small.occurrences("BaseGame.X").size(), "One occurrence per operation");
        equal(Access.READ_WRITE, small.occurrences("BaseGame.X").getFirst().access(), "Self assignment is read/write");
        equal(Access.UNRESOLVED, small.occurrences("BaseGame.X").getLast().access(), "Unsupported block unresolved");
        var smallAnalysis = new VariableAnalyzer(small).analyze("BaseGame.X");
        equal(1, smallAnalysis.rules().size(), "Unknown block excluded from rules");
        equal(Map.of("BaseGame.Guard", 1), smallAnalysis.dependencies(), "Only own guard dependencies");
        var index = new VariableIndex(data);
        var catalog = new VariableCatalog(index);
        check(catalog.filter("malenyevist").contains("BaseGame.Malenyevist"), "Real variable discovery");
        equal(catalog.filter("energy", true), catalog.filter("ENERGY", true), "Case insensitive filtering");
        check(index.variables().stream().noneMatch(v -> v.startsWith("Rizia")), "Sordland scope");
        var analyzer = new VariableAnalyzer(index);
        var targets = new ArrayList<>(List.of("BaseGame.Malenyevist", "BaseGame.Economy"));
        targets.addAll(index.variables().stream().filter(v -> v.startsWith("BaseGame.Situation_Diplomacy_Energy_Price")).toList());
        check(targets.size()>2, "Actual energy variables discovered");
        for (String name: targets) {
            var result = analyzer.analyze(name);
            equal(result, analyzer.analyze(name), "Deterministic cached results");
            check(result.evidence().size()>1, "Real evidence retained " + name);
            check(result.evidence().stream().allMatch(o -> !o.source().identity().isBlank() && !o.source().field().isBlank() &&
                !o.source().original().isBlank()), "Real provenance " + name);
            var identities = new HashSet<String>();
            for (Occurrence occurrence: result.evidence()) check(identities.add(occurrence.source().key() + ":" + occurrence.operation()),
                "No duplicate source operations " + name);
            System.out.println("VARIABLE " + name + " layout=" + result.layout() + " reads=" + result.count(Access.READ) + " writes=" + result.rules().size() + " unresolved=" + result.count(Access.UNRESOLVED) + " family=" +(result.family() != null));
            for (var rule: result.rules().stream().limit(2).toList()) System.out.println("  " + rule.effect().display() + " WHEN " + rule.condition().display(index::label) + " @ " + rule.source().identity() + "/" + rule.source().field());
        }
        var economy = analyzer.analyze("BaseGame.Economy");
        check(economy.count(Access.READ)>0 && !economy.rules().isEmpty(), "Real reads and writes");
        check(index.label("BaseGame.Situation_Diplomacy_Energy_PriceSurge").contains("Energy"), "Runtime friendly label");
        check(index.variables().stream().flatMap(v -> index.occurrences(v).stream()).anyMatch(o -> o.source().field().equals("PanelCounterIncrement") &&
            o.proof() == Proof.EXPLICIT_STRUCTURE), "Real structured runtime panel increments");
        equal(VariableAnalyzer.Layout.NUMERIC, analyzer.analyze("BaseGame.Malenyevist").layout(), "Predominantly numeric real stat");
        equal(VariableAnalyzer.Layout.NUMERIC, economy.layout(), "Economy modifier table");
        check(index.occurrences("BaseGame.Economy").stream().anyMatch(o -> o.source().field().equals("Instruction") &&
            o.source().identity().contains("Option_Budget")), "Real runtime choice instruction");
        equal("=", VariableSyntax.effect("BaseGame.X = 8").operator(), "Absolute assignment stays SET");
        check(VariableSyntax.effect("BaseGame.X = mystery(BaseGame.Y)") == null, "Unknown RHS unresolved");
        equal("\"hello\"", VariableSyntax.effect("BaseGame.X = \"hello\"").value().value(), "String assignment");
        check(VariableSyntax.parse("BaseGame.A and not (BaseGame.B or BaseGame.C)").known(), "Textual boolean operators");
        var values = new Item("values", "Decision", "Values", "Values", "Sordland/Values", null, null, "", "BaseGame.Color = 'red'",
            "", "", List.of(), Map.of("display", "BaseGame.Color = 'red'"));
        var valueIndex = new VariableIndex(new Dataset(List.of(values), Map.of(), List.of(), List.of()));
        equal(1, valueIndex.occurrences("BaseGame.Color").size(), "Raw display counterpart not scanned twice");
        equal(VariableAnalyzer.Layout.VALUES, new VariableAnalyzer(valueIndex).analyze("BaseGame.Color").layout(),
            "String layout");
        familyChecks();
    }
    private static void familyChecks() {
        var options = List.of(new Option("A", "BaseGame.Driver == true", "BaseGame.StateA = true; BaseGame.StateB = false;"),
            new Option("B", "BaseGame.Driver == false", "BaseGame.StateA = false; BaseGame.StateB = true;"));
        var item = new Item("states", "Decision", "States", "States", "Sordland/States", null, null, "", "", "", "",
            options, Map.of());
        var index = new VariableIndex(new Dataset(List.of(item), Map.of(), List.of(), List.of()));
        check(new VariableAnalyzer(index).analyze("BaseGame.StateA").family() != null, "Co-assignment proven family");
        var partial = new Item("partial", "Decision", "Partial", "Partial", "Sordland/Partial", null, null, "", "BaseGame.StateA = true",
            "", "", List.of(), Map.of());
        var incomplete = new VariableIndex(new Dataset(List.of(item, partial), Map.of(), List.of(), List.of()));
        check(new VariableAnalyzer(incomplete).analyze("BaseGame.StateA").family() == null, "Partial state writes disallow global family");
    }
}
