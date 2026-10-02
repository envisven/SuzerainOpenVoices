package sordland;

import sordland.analysis.*;
import sordland.data.*;
import java.nio.file.*;
import java.util.*;
import static sordland.TestSupport.*;

public class CompactChecks {
    static VariableSyntax.Expr expr(String text) {
        return VariableSyntax.parse(text.replace(" or ", " || ").replace(" and ", " && ").replace("not ", "!").replaceAll("\\b([ABCXY])\\b",
            "BaseGame.$1"));
    }
    public static void main(String[] args) throws Exception {
        for (String expression: List.of("A or not A", "A or B or (not A and not B)", "A or B or C or (not A and not B and not C)")) check(BooleanCoverage.prove(expr(expression)).exhaustive(),
            expression);
        check(!BooleanCoverage.prove(expr("A or B or (not A and C)")).exhaustive(), "nonexhaustive retained");
        var r = NumericVariableLayout.factor(NumericVariableLayout.region(ConditionLogic.normalize(expr("(A and X) or (not A and X)")),
            Set.of()));
        check(ConditionLogic.display(r.expression()).equals("X = true"), "common X");
        check(!BooleanCoverage.prove(expr("(A and X) or (not A and Y)")).exhaustive(), "different suffix retained");
        var positives = new ArrayList<VariableSyntax.Expr>();
        var negatives = new ArrayList<VariableSyntax.Expr>();
        for (int n = 0; n<256; n++) {
            var atom = VariableSyntax.parse("BaseGame.Family_" + n);
            positives.add(atom);
            negatives.add(new VariableSyntax.Expr("not", "", List.of(atom)));
        }
        positives.add(new VariableSyntax.Expr("and", "", negatives));
        check(BooleanCoverage.prove(new VariableSyntax.Expr("or", "", positives)).exhaustive(), "256-way structural proof");
        check(!BooleanCoverage.prove(VariableSyntax.parse("BaseGame.A == BaseGame.B || BaseGame.A == BaseGame.C || BaseGame.B == BaseGame.C")).exhaustive(),
            "Untyped numeric equality is not Boolean pigeonhole");
        check(!BooleanCoverage.prove(new VariableSyntax.Expr("trigger", "choice", List.of())).exhaustive(), "Player choices are not Boolean atoms");
        var data = Loader.load(Path.of("data", Loader.ENTITY_FILE), Path.of("data", Loader.CONVERSATIONS_FILE));
        var ix = new VariableIndex(data);
        var report = new StringBuilder();
        for (String name: List.of("BaseGame.AMorgnaWesCore", "BaseGame.Economy", "BaseGameIsolated.Ending_Vote_Liberals",
            "BaseGame.AgnoliaTradeDeal_Negotiation")) {
            var original = NumericVariableLayout.build(new VariableAnalyzer(ix).analyze(name));
            var compact = CompactNumericLayout.build(original);
            equal(original.size(), compact.size(), "write count");
            int detected = 0, hidden = 0, segments = 0, refs = 0, required = 0, factors = 0;
            for (int i = 0; i<original.size(); i++) {
                var a = original.get(i);
                var b = compact.get(i);
                equal(a.id(), b.id(), "same write");
                equal(a.routes(), b.routes(), "unchanged original routes");
                var retained = new HashSet<NumericVariableLayout.Reference>();
                b.sections().forEach(s -> retained.addAll(s.root().references()));
                check(retained.containsAll(a.root().references()), "every source reference retained");
                detected += b.compactions().size();
                hidden += b.compactions().stream().filter(c -> c.summary().isBlank()).count();
                segments += b.routes().size();
                refs += retained.size();
                required += b.sections().stream().filter(s -> !ConditionLogic.truth(s.root().expression()) && !Set.of("trigger",
                    "exhaustive").contains(s.root().expression().op())).count();
                factors += b.sections().stream().flatMap(s -> s.root().transformations().stream()).filter(t -> t.category().contains("factored")).count();
                if (name.equals("BaseGame.AMorgnaWesCore") && b.title().startsWith("Gain block 7 ")) {
                    check(b.compactions().stream().anyMatch(c -> c.summary().contains("Resigned")), "real Resigned summary");
                    check(b.compactions().stream().anyMatch(c -> c.original().variables().stream().anyMatch(v -> v.contains("Petr_"))),
                        "real Petr exhaustive");
                    report.append("GAIN7 " + b.id() + " routes=" + b.routes().size() + " beforeSections=" + a.sections().size() + " afterSections=" + b.sections().size() + " beforeCells=" + a.cells().size() + " afterCells=" + b.cells().size() + "\nSections: " + b.sections().stream().map(s -> s.root().expression() + " [references " + s.root().references().stream().map(NumericVariableLayout.Reference::pathIndex).toList() + "]").toList() + "\n" + NumericVariableLayout.audit(b) + "\n");
                }
            }
            report.append(name + ": writes=" + compact.size() + ", DAG segments=" + segments + ", condition regions=" + required + ", exhaustive=" + detected + ", hidden=" + hidden + ", summarized=" +(detected - hidden) + ", refs=" + refs + ", factoring=" + factors + "\n");
        }
        Files.createDirectories(Path.of("docs/compact-validation"));
        Files.writeString(Path.of("docs/compact-validation/audit.txt"), report);
        System.out.println("PASS compact checks " + count());
    }
}
