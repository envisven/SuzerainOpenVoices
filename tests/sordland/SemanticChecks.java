package sordland;

import sordland.graph.Semantics;
import java.util.*;
import static sordland.TestSupport.*;

final class SemanticChecks {
    private SemanticChecks() {
    }
    static void run() {
        String expression = "BaseGame.GovernmentBudget >= 2 and BaseGameSupport.Flag and BaseGameIsolated.Other and GameCondition.X or BaseGame.Flag";
        equal("GovernmentBudget >= 2 and BaseGameSupport.Flag and BaseGameIsolated.Other and GameCondition.X or Flag",
            Semantics.conditionDisplay(expression), "Only literal BaseGame namespace stripped; expression order and other namespaces retained");
        equal("SomeBaseGame.Flag + Outer.BaseGame.Flag", Semantics.conditionDisplay("SomeBaseGame.Flag + Outer.BaseGame.Flag"),
            "Unrelated namespace suffixes remain intact");
        var analysis = Semantics.analyze("BaseGame.GovernmentBudget -= 1; BaseGame.Relations_Monica_Opinion += 1; GameCondition.Flag = true;",
            "PlaySceneMusic(\"song;name\"); Continue();");
        equal(List.of("BaseGame.GovernmentBudget -= 1", "BaseGame.Relations_Monica_Opinion += 1", "GameCondition.Flag = true"),
            analysis.effects(), "Persistent assignments identified in source order");
        equal(2, analysis.cosmetic().size(), "Music/continue commands recognized without splitting quoted semicolon");
        equal(3L, analysis.commands().stream().filter(c -> c.kind() == Semantics.CommandKind.EFFECT).count(), "Cosmetic commands do not become gameplay effect boxes");
        check(!analysis.terminal(), "Assignments do not terminate dialogue");
        check(Semantics.analyze("End();", "").terminal(), "End() is recognized as conversation terminal plumbing");
        equal(0, Semantics.analyze("End();", "").effects().size(), "End() is not a state mutation");
        var unknown = Semantics.analyze("MaybeSetPolicy(42);", "");
        equal(List.of("MaybeSetPolicy(42)"), unknown.unknown(), "Unrecognized commands are retained as unknown");
        equal(Semantics.CommandKind.UNKNOWN, unknown.commands().getFirst().kind(), "Unknown commands retain their explicit source classification");
        var guarded = Semantics.analyze("if BaseGame.Ready then BaseGame.Flag = true; end", "");
        equal(0, guarded.effects().size(), "Guarded assignment is not misrepresented as an unconditional effect");
        equal(1, guarded.unknown().size(), "Unsupported control construct retained whole");
        equal(0, Semantics.analyze("-- BaseGame.Flag = true\n// another comment", "").effects().size(), "Comments do not execute as effects");
        equal(List.of("BaseGame.Text = \"a;--b\""), Semantics.analyze("BaseGame.Text = \"a;--b\";", "").effects(),
            "Comment markers inside strings retained");
        var malformed = Semantics.analyze("BaseGame.Flag = \"unterminated", "");
        equal(0, malformed.effects().size(), "Malformed command cannot be proven a state mutation");
        equal(1, malformed.unknown().size(), "Malformed source remains visible as unknown");
    }
}
