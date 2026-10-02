package sordland;

import sordland.analysis.*;
import sordland.data.*;
import sordland.data.Domain.*;
import java.util.*;
import java.nio.file.*;
import static sordland.TestSupport.*;

public class TargetReachabilityChecks {
    static Entry node(int id, String guard, String script, int...targets) {
        var links = new ArrayList<Link>();
        for (int t: targets) links.add(new Link(new EntryKey(1, t), links.size(), "Normal", false));
        return new Entry(new EntryKey(1, id), 0, "", "Node " + id, "Ordinary dialogue", "", guard, script, "", links,
            Map.of());
    }
    static TargetReachability.Result reduce(int target, Entry...entries) {
        return new TargetReachability(List.of(entries)).reduce(new EntryKey(1, target), Map.of(), Map.of());
    }
    public static void main(String[] args) throws Exception {
        var diamond = reduce(4, node(0, "", "", 1, 2), node(1, "BaseGame.A", "", 3), node(2, "!BaseGame.A", "", 3),
            node(3, "", "", 4), node(4, "", "BaseGame.X += 1"));
        check(ConditionLogic.truth(diamond.region().expression()), "diamond exhaustive");
        check(diamond.forks().getFirst().removed(), "fork audited as removed");
        var one = reduce(3, node(0, "", "", 1, 2), node(1, "BaseGame.A", "", 3), node(2, "!BaseGame.A", ""), node(3,
            "", "BaseGame.X += 1"));
        equal(ConditionLogic.normalize(VariableSyntax.parse("BaseGame.A")), one.region().expression(), "one surviving A");
        check(!one.slice().contains(new EntryKey(1, 2)), "dead branch outside target slice");
        var shared = reduce(3, node(0, "BaseGame.A", "", 2), node(1, "BaseGame.B", "", 2), node(2, "BaseGame.C", "",
            3), node(3, "", "BaseGame.X += 1"));
        equal(Set.of("BaseGame.A", "BaseGame.B", "BaseGame.C"), shared.region().expression().variables(), "OR then shared C");
        check(shared.region().expression().op().equals("and"), "common downstream C factored");
        var mutation = reduce(4, node(0, "", "", 1, 2), node(1, "BaseGame.A", "BaseGame.B = true", 3), node(2, "!BaseGame.A",
            "BaseGame.B = false", 3), node(3, "BaseGame.B", "", 4), node(4, "", "BaseGame.X += 1"));
        equal(ConditionLogic.normalize(VariableSyntax.parse("BaseGame.A")), mutation.region().expression(), "branch-local assignment preserves A requirement");
        check(!mutation.forks().getFirst().removed(), "state-changing fork not falsely removed");
        var crossed = reduce(5, node(0, "", "", 1, 2), node(1, "BaseGame.A", "", 3, 4), node(2, "!BaseGame.A", "",
            4), node(3, "", "", 5), node(4, "", "", 5), node(5, "", "BaseGame.X += 1"));
        check(ConditionLogic.truth(crossed.region().expression()), "non-series-parallel DAG reduced");
        var data = Loader.load(Path.of("data", Loader.ENTITY_FILE), Path.of("data", Loader.CONVERSATIONS_FILE));
        var ix = new VariableIndex(data);
        var report = new StringBuilder();
        for (String name: List.of("BaseGame.AMorgnaWesCore", "BaseGame.Economy")) {
            var original = NumericVariableLayout.build(new VariableAnalyzer(ix).analyze(name));
            var after = CompactNumericLayout.build(original);
            equal(original.size(), after.size(), "independent write count");
            int conditions = 0, choices = 0, refs = 0, unresolved = 0, nodes = 0, segments = 0, removed = 0, oneSided = 0;
            for (int i = 0; i<after.size(); i++) {
                var a = original.get(i);
                var b = after.get(i);
                equal(a.routes(), b.routes(), "all original routes unchanged");
                Set<NumericVariableLayout.Reference> retained = new HashSet<>();
                b.sections().forEach(s -> retained.addAll(s.root().references()));
                check(retained.containsAll(a.root().references()), "all provenance retained");
                refs += retained.size();
                segments += a.routes().size();
                if (a.occurrence().guardProof() != null && a.occurrence().guardProof().graph() != null) nodes += a.occurrence().guardProof().graph().nodes().size();
                conditions += b.cells().stream().filter(c -> !Set.of("trigger", "exhaustive").contains(c.region().expression().op())).count();
                choices += b.cells().stream().filter(c -> c.region().expression().op().equals("trigger")).count();
                if (b.targetEvidence().startsWith("Unresolved")) {
                    unresolved++;
                    report.append("BOUNDARY " + name + " / " + b.id() + ": " + b.targetEvidence() + "\n");
                }
                removed += b.targetEvidence().split("EXHAUSTIVE -> removed", -1).length - 1;
                oneSided += b.targetEvidence().split("ONE-SIDED -> retained", -1).length - 1;
                if (b.id().equals("Conversation 226 / Dialogue 1007/userScript:0")) {
                    check(!b.targetEvidence().startsWith("Unresolved"), "Gain11 fully reduced");
                    for (int fork: List.of(152, 170, 506, 509)) {
                        String evidence = b.targetEvidence().split("Source fork 226:" + fork + "\\n")[1].split("Source fork")[0];
                        check(evidence.contains("EXHAUSTIVE -> removed"), "Wehlen fork " + fork + " removed");
                    }
                    check(b.targetEvidence().contains("226:615=true, 226:616=false"), "614 target-conditioned counterexample");
                    check(b.cells().stream().anyMatch(c -> c.region().expression().variables().contains("BaseGame.Wehlen_JointOperation")),
                        "one-sided Wehlen stays visible");
                    check(b.cells().stream().anyMatch(c -> c.region().expression().op().equals("trigger") && c.region().expression().value().contains("A Morgna wes core!")),
                        "direct player choice remains");
                    report.append("GAIN11 " + a.title() + " original segments=" + a.routes().size() + " cells=" + b.cells().size() + " sections=" + b.sections().size() + "\n" + b.targetEvidence() + "\n" + NumericVariableLayout.audit(b));
                }
            }
            report.append("AUDIT " + name + ": writes=" + after.size() + ", original graph nodes=" + nodes + ", segments=" + segments + ", required cells=" + conditions + ", choice cells=" + choices + ", exhaustive removed=" + removed + ", one-sided=" + oneSided + ", unresolved writes=" + unresolved + ", retained refs=" + refs + "\n");
        }
        Files.createDirectories(Path.of("docs/target-validation"));
        Files.writeString(Path.of("docs/target-validation/audit.txt"), report);
        System.out.println("PASS target reachability checks " + count());
    }
}
