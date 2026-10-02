package sordland;

import sordland.analysis.*;
import sordland.data.Domain.*;
import java.util.*;
import static sordland.TestSupport.*;
import static sordland.analysis.NumericVariableLayout.*;
import static sordland.analysis.VariableIndex.*;
import static sordland.analysis.VariableSyntax.Expr;

final class NumericLayoutChecks {
    static Expr expr(String text) {
        return VariableSyntax.parse(text.replaceAll("\\b([ABCDX])\\b", "BaseGame.$1"));
    }
    static Occurrence occurrence(int operation, String condition, String effect) {
        var source = new Source("fixture", "Fixture", 1, "Instruction", effect, condition, List.of("runtime alias"));
        return new Occurrence("BaseGame.X", Access.READ_WRITE, Proof.EXACT, source, operation, effect, expr(condition),
            VariableSyntax.effect(effect));
    }
    static VariableAnalyzer.Analysis analysis(List<Occurrence> rules) {
        return new VariableAnalyzer.Analysis("BaseGame.X", VariableAnalyzer.Layout.NUMERIC, rules, rules, Map.of(),
            null);
    }
    static BonusBlock block(String guard) {
        return build(analysis(List.of(occurrence(0, guard, "BaseGame.X += 1")))).getFirst();
    }
    static void run(Dataset data) {
        BonusBlock vertical = block("A && B && C");
        equal(List.of("A = true", "B = true", "C = true"), vertical.cells().stream().map(c -> ConditionLogic.display(c.region().expression())).toList(),
            "Vertical cells");
        equal(List.of(0d, 1d, 2d), vertical.cells().stream().map(Cell::row).toList(), "Vertical row spans");
        BonusBlock split = block("(A || B) && C");
        equal(3, split.cells().size(), "Nested alternatives cells");
        equal(.5, split.cells().getFirst().columnSpan(), "Split width");
        equal(1d, split.cells().getLast().columnSpan(), "Shared full width");
        equal(1d, split.cells().getLast().row(), "Shared suffix below split");
        equal(block("A && B && (C || D)").root().expression(), block("(A && B && C) || (A && B && D)").root().expression(),
            "Common prefix factoring");
        equal(block("(A || B) && C").root().expression(), block("(A && C) || (B && C)").root().expression(), "Common suffix factoring");
        var separate = build(analysis(List.of(occurrence(0, "A", "BaseGame.X += 1"), occurrence(1, "A", "BaseGame.X += 1"))));
        equal(2, separate.size(), "Distinct identical source operations remain distinct");
        check(!separate.getFirst().id().equals(separate.getLast().id()), "Operation ordinal boundary");
        equal("X = false", ConditionLogic.display(ConditionLogic.normalize(expr("!X"))), "Negated boolean display");
        for (String text: List.of("X", "X == true", "X != false", "!(X == false)")) equal("X = true", ConditionLogic.display(ConditionLogic.normalize(expr(text))),
            "Positive boolean normalization");
        for (String text: List.of("X == false", "X != true", "!(X == true)")) equal("X = false", ConditionLogic.display(ConditionLogic.normalize(expr(text))),
            "False normalization");
        equal(block("!A || !B").root().expression(), block("(A && B) == false").root().expression(), "Compound false comparison decomposes structurally");
        equal(block("!A").root().expression(), block("(A == true) == false").root().expression(), "Nested boolean comparison normalizes");
        equal("NOT (X > 5)", ConditionLogic.display(ConditionLogic.normalize(expr("!(X > 5)"))), "No fabricated boolean assignment for numeric negation");
        for (String[] pair: List.of(new String[] {
            "X", "!X"
        }, new String[] {
            "X == 1", "X == 2"
        }, new String[] {
            "X > 7", "X < 5"
        }, new String[] {
            "X >= 7", "X < 7"
        }, new String[] {
            "X == 1.0", "X != 1"
        }, new String[] {
            "7 < X", "X <= 7"
        }, new String[] {
            "(X == 1 || X == 2)", "X == 3"
        })) check(CompatibilityAnalyzer.incompatible(expr(pair[0]), expr(pair[1])), "Proven contradiction " + Arrays.toString(pair));
        for (String[] pair: List.of(new String[] {
            "A", "B"
        }, new String[] {
            "X >= 7", "X <= 7"
        }, new String[] {
            "A || B", "!A"
        }, new String[] {
            "X == 1", "X == 1.00"
        }, new String[] {
            "mystery(X)", "!X"
        })) check(!CompatibilityAnalyzer.incompatible(expr(pair[0]), expr(pair[1])), "Unknown/overlap remains normal " + Arrays.toString(pair));
        for (String[] effect: List.of(new String[] {
            "+= 1", "GAIN", "+1"
        }, new String[] {
            "+= -2", "LOSS", "-2"
        }, new String[] {
            "-= 2", "LOSS", "-2"
        }, new String[] {
            "-= -2", "GAIN", "+2"
        }, new String[] {
            "+= 0", "ZERO", "+0"
        }, new String[] {
            "= 0", "SET", "SET = 0"
        }, new String[] {
            "+= (2 * 3)", "GAIN", "+6"
        }, new String[] {
            "+= BaseGame.A", "OPERATION", "+= BaseGame.A"
        })) {
            Effect e = NumericVariableLayout.effect(VariableSyntax.effect("BaseGame.X " + effect[0]));
            equal(effect[1], e.kind().name(), "Numeric effect classification " + effect[0]);
            if (!e.kind().equals(EffectKind.OPERATION)) equal(effect[2], e.text(), "Correct effect sign");
        }
        equal(EffectKind.OPERATION, NumericVariableLayout.effect(VariableSyntax.effect("BaseGame.X += 1 / 3")).kind(),
            "Nonterminating constant stays neutral");
        equal(EffectKind.OPERATION, NumericVariableLayout.effect(VariableSyntax.effect("BaseGame.X /= 0")).kind(),
            "Division by zero stays neutral");
        var computedItem = new Item("computed", "Decision", "Computed", "Computed", "Sordland/Computed", 1, null,
            "", "BaseGame.X += 2 * 3", "", "", List.of(), Map.of());
        var computedIndex = new VariableIndex(new Dataset(List.of(computedItem), Map.of(), List.of(), List.of()));
        equal(VariableAnalyzer.Layout.NUMERIC, new VariableAnalyzer(computedIndex).analyze("BaseGame.X").layout(),
            "Constant computed writes use numeric grid");
        var many = new ArrayList<Expr>();
        for (int i = 0; i<9; i++) many.add(VariableSyntax.parse("BaseGame.A" + i + " || BaseGame.B" + i));
        check(!CompatibilityAnalyzer.incompatible(ConditionLogic.junction("and", many), expr("X")), "Proof expansion budget returns UNKNOWN");
        provenance();
        choiceProof();
        equivalence();
        real(data);
        System.out.println("PASS: numeric layout, equivalence, provenance, compatibility and real-data checks");
    }
    private static void provenance() {
        var a = new DialogueGuardResolver.Path(expr("A && C"), List.of(new DialogueGuardResolver.Predicate(new EntryKey(1,
            1), "A && C")), List.of());
        var b = new DialogueGuardResolver.Path(expr("B && C"), List.of(new DialogueGuardResolver.Predicate(new EntryKey(1,
            2), "B && C")), List.of());
        var base = occurrence(0, "", "BaseGame.X += 1");
        var proof = new DialogueGuardResolver.Resolved(DialogueGuardResolver.Kind.ALTERNATIVE_PATHS, List.of(a, b),
            "fixture");
        var o = new Occurrence(base.variable(), base.access(), base.proof(), base.source(), 0, base.expression(),
            proof.expression(), base.effect(), proof);
        var block = build(analysis(List.of(o))).getFirst();
        equal(2, block.routes().size(), "Original proof paths retained");
        var common = block.cells().stream().filter(c -> ConditionLogic.display(c.region().expression()).equals("C = true")).findFirst().orElseThrow();
        equal(2, common.region().references().size(), "Merged cell union of proven sources");
        var engine = new CompatibilityAnalyzer(List.of());
        var incompatible = block("!C");
        equal(CompatibilityAnalyzer.Verdict.INCOMPATIBLE, engine.compare(CompatibilityAnalyzer.selection(block, common.region()),
            CompatibilityAnalyzer.selection(incompatible, incompatible.root())).verdict(), "Every merged route contradicts !C");
        var partial = block("!A");
        equal(CompatibilityAnalyzer.Verdict.UNKNOWN, engine.compare(CompatibilityAnalyzer.selection(block, common.region()),
            CompatibilityAnalyzer.selection(partial, partial.root())).verdict(), "One unproven route pair prevents dimming merged cell");
        var rawUnknown = occurrence(0, "", "BaseGame.X += 1");
        var unknownOccurrence = new Occurrence(rawUnknown.variable(), rawUnknown.access(), rawUnknown.proof(), rawUnknown.source(),
            0, rawUnknown.expression(), rawUnknown.condition(), rawUnknown.effect(), new DialogueGuardResolver.Resolved(DialogueGuardResolver.Kind.UNRESOLVED,
            List.of(), "unsupported fixture"));
        var unknown = build(analysis(List.of(unknownOccurrence))).getFirst();
        check(!unknown.routes().getFirst().proven(), "Absent incoming path not manufactured");
        equal("Partial ⚠", unknown.routes().getFirst().label(), "Unknown route honestly labeled as metadata");
        equal(CompatibilityAnalyzer.Verdict.UNKNOWN, engine.compare(CompatibilityAnalyzer.selection(unknown, unknown.root()),
            CompatibilityAnalyzer.selection(partial, partial.root())).verdict(), "Unknown route stays normal");
    }
    private static void choiceProof() {
        var options = List.of(new Option("First", "", "BaseGame.X += 1"), new Option("Second", "", "BaseGame.X += 2"));
        for (String type: List.of("Decision", "Conditional instruction")) {
            var item = new Item("test", type, "Fixture", "Fixture", "Sordland/Fixture", 1, null, "", "", "", "", options,
                Map.of());
            var index = new VariableIndex(new Dataset(List.of(item), Map.of(), List.of(), List.of()));
            var blocks = build(new VariableAnalyzer(index).analyze("BaseGame.X"));
            var verdict = new CompatibilityAnalyzer(index).compare(CompatibilityAnalyzer.selection(blocks.getFirst(),
                blocks.getFirst().root()), CompatibilityAnalyzer.selection(blocks.getLast(), blocks.getLast().root())).verdict();
            equal(type.equals("Decision") ? CompatibilityAnalyzer.Verdict.INCOMPATIBLE: CompatibilityAnalyzer.Verdict.UNKNOWN,
                verdict, "Only actual choice structure proves exclusivity " + type);
        }
    }
    private static void equivalence() {
        var random = new Random(37);
        for (int test = 0; test<300; test++) {
            Expr original = randomExpr(random, 4);
            Region result = factor(region(ConditionLogic.normalize(original), Set.of()));
            for (int mask = 0; mask<16; mask++) equal(eval(original, mask), eval(result.expression(), mask), "Factoring truth-table equivalence");
        }
        var span = block("A || (B && C && D)");
        equal(3d, span.cells().getFirst().rowSpan(), "Short alternative spans adjacent stacked rows");
        for (String guard: List.of("A || (B && C)", "(A || B) && (C || D)", "(A && B) || (A && C) || D")) {
            var block = block(guard);
            double area = 0;
            for (RouteLayout section: block.sections()) {
                double sectionArea = section.cells().stream().mapToDouble(c -> c.columnSpan() * c.rowSpan()).sum();
                check(section.cells().isEmpty() ? section.root().references().stream().allMatch(r -> r.path() != null &&
                    !r.path().complete()): Math.abs(sectionArea - section.root().rows())<1e-8, "Condition cells tile; metadata-only partial routes are compact");
            }
            for (Cell c: block.cells()) {
                check(c.columnSpan()>0 && c.rowSpan()>0, "Positive cell spans");
                area += c.columnSpan() * c.rowSpan();
            }
            check(Math.abs(area - block.root().rows())<1e-8, "Cells tile their rectangle");
        }
    }
    private static Expr randomExpr(Random random, int depth) {
        if (depth == 0 || random.nextInt(4) == 0) return expr(String.valueOf((char)('A' + random.nextInt(4))));
        String op = List.of("and", "or", "not", "==").get(random.nextInt(4));
        if (op.equals("==")) return new Expr("==", "", List.of(randomExpr(random, depth - 1), ConditionLogic.literal(random.nextBoolean() ? "true": "false")));
        return new Expr(op, "", op.equals("not") ? List.of(randomExpr(random, depth - 1)): List.of(randomExpr(random,
            depth - 1), randomExpr(random, depth - 1)));
    }
    private static boolean eval(Expr e, int mask) {
        return switch (e.op()) {
            case "var" ->(mask &(1<<(e.value().charAt(e.value().length() - 1) - 'A'))) != 0;
            case "literal" -> Boolean.parseBoolean(e.value());
            case "not" -> !eval(e.children().getFirst(), mask);
            case "==" -> eval(e.children().getFirst(), mask) == eval(e.children().getLast(), mask);
            case "and" -> e.children().stream().allMatch(c -> eval(c, mask));
            case "or" -> e.children().stream().anyMatch(c -> eval(c, mask));
            default -> throw new AssertionError(e);
        };
    }
    private static void real(Dataset data) {
        var index = new VariableIndex(data);
        var analyzer = new VariableAnalyzer(index);
        String name = "BaseGameIsolated.Ending_Vote_Liberals";
        var analysis = analyzer.analyze(name);
        var blocks = build(analysis);
        equal(VariableAnalyzer.Layout.NUMERIC, analysis.layout(), "Real numeric variable");
        equal(analysis.rules().size(), blocks.size(), "Every real source write remains independent");
        equal(blocks.size(), new HashSet<>(blocks.stream().map(BonusBlock::id).toList()).size(), "Distinct real operation IDs");
        int paths = 0;
        for (BonusBlock block: blocks) {
            check(block.root().references().stream().allMatch(r -> r.occurrence().equals(block.occurrence())), "No cross-block factoring");
            if (block.occurrence().guardProof() != null && !block.occurrence().guardProof().paths().isEmpty()) equal(block.occurrence().guardProof().paths().size(),
                block.routes().size(), "Individual resolver paths retained");
            for (Route route: block.routes()) if (route.reference().path() != null) {
                paths++;
                for (var predicate: route.reference().path().predicates()) equal(data.entry(predicate.entry()).condition(),
                    predicate.original(), "Actual source predicate");
                for (var edge: route.reference().path().edges()) check(data.entry(edge.from()).links().stream().anyMatch(l -> l.target().equals(edge.to()) &&
                    l.order() == edge.order()), "Actual source edge");
            }
            for (RouteLayout section: block.sections()) {
                double sectionArea = section.cells().stream().mapToDouble(c -> c.columnSpan() * c.rowSpan()).sum();
                check(section.cells().isEmpty() ? section.root().references().stream().allMatch(r -> r.path() != null &&
                    !r.path().complete()): Math.abs(sectionArea - section.root().rows())<1e-8, "Condition cells tile; metadata-only partial routes are compact");
            }
            for (Cell c: block.cells()) {
                check(!c.region().references().isEmpty(), "Every real cell traceable");
                String text = ConditionLogic.display(c.region().expression());
                check(!text.contains("BaseGame"), "Basenames in main grid");
                check(!text.matches(".*\\b(AND|OR)\\b.*"), "No Boolean operator words in cell");
            }
            System.out.println("NUMERIC " + block.title() + " source=" + block.id() + " sourceSegments=" + block.routes().size() + " cells=" + block.cells().size() + " maxBandColumns=" + block.sections().stream().mapToDouble(s -> s.root().columns()).max().orElse(0) + " maxBandRows=" + block.sections().stream().mapToDouble(s -> s.root().rows()).max().orElse(0));
        }
        check(paths>0, "Real proven dialogue paths used");
        System.out.println("REAL NUMERIC " + name + " blocks=" + blocks.size() + " gains=" + blocks.stream().filter(b -> b.effect().kind() == EffectKind.GAIN).count() + " sourceSegments=" + paths);
        for (String variable: index.variables()) {
            var result = analyzer.analyze(variable);
            if (result.layout() != VariableAnalyzer.Layout.NUMERIC) continue;
            for (BonusBlock b: build(result)) for (Cell c: b.cells()) check(!ConditionLogic.display(c.region().expression()).isBlank(),
                "All numeric conditions display safely");
        }
    }
}
