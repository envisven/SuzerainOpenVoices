# Compact and Choice correction pass

Implemented directly on the supplied **SordlandTreeViewer-final.zip**. All original game data files are unchanged. The existing picker, rooted campaign reconstruction, optional/mandatory semantics, independent writes, and Source evidence remain in place.

## Changes

- **Choice** is an independent category in the existing Types menu, enabled by default. Hiding it uses the existing topology-preserving projection; showing it restores the original cards.
- **Compact** defaults ON. Compact, Source mode, and Show logic labels are independent. Expanded and compact models are prepared once in the analysis worker; switching Compact rebuilds the grid from cached models without re-indexing or re-analysis.
- Source mode defaults OFF. Normal mode contains only the controls/search and spreadsheet. Separate source sections have a thin source-flow separator; AND/OR markers describe only relationships within Boolean regions.
- Exhaustive reconverged forks are hidden or shown as teal display summaries. No variables are invented. Clicking a summary in Source mode exposes original predicates, entries, route references, topology, and the reduction reason. Compact OFF restores the explicit layout.

## Detection and preservation

Structural complement checks handle arbitrary N-way positive alternatives plus their all-negative case. A memoized reduced Boolean decision diagram handles equivalent Boolean forms, capped at 4,096 nodes and 20,000 operations. No truth table is enumerated. Untyped variable-to-variable comparisons, numeric predicates, historical conditions, unresolved atoms and player-choice triggers are not treated as Boolean coverage proofs.

Reduction is scoped to one write. Parallel alternatives must share source predecessors and successors. A bounded closed-DAG pass handles non-series-parallel forks, requiring a common continuation, no outside entry, complete local paths, and at most 32 interior segments/4,096 expression nodes. Cycles, incomplete evidence and budget exhaustion remain expanded. Existing factoring runs again after removal. Direct player-choice cells are preserved.

## Exact Gain block 7

The Resigned/Petr fingerprint identifies **BaseGame.AMorgnaWesCore**, Conversation **212**, Dialogue **853**, `userScript`, operation **0**. It is **one write, +1**, with **148 source-DAG segments**. Compact reduces **17 sections / 67 cells to 3 sections / 3 cells**, retaining all **148 route references**:

1. `Enable_Congress = true`: the StoryFragment activation requirement.
2. `[CHOICE] "A Morgna wes core!"`: the direct source choice causing the write.
3. `[EXHAUSTIVE] Resigned: GusManger | CiaraWalda | NiaMorgna | PaskalBeniwoll | SymonHoll | None`: a display annotation, not a requirement.

The five-member Resigned positive-or-all-negative forks recur at route ordinals 23/24, 52/53, 66/67 and 81/82. Their eight references are united into one displayed summary. Petr routes 20/21 originate at 212:86, through 212:88 and 212:91, and reconverge at 212:126 through the intervening DAG. Their predicates are complements: `(!DenyEverything && LiviaInvestigated && !Petr_isVP) || Petr_Dead` versus `(DenyEverything || !LiviaInvestigated || Petr_isVP) && !Petr_Dead`. The closed-DAG proof removes this restriction. There are 45 recorded reduction steps in this write, four summarized family occurrences and 41 hidden reductions, including condition-free reconvergences. All source speech/choice paths remain in evidence.

Replacing a proven exhaustive fork with TRUE preserves its downstream reachability condition; source topology is checked before that replacement. The activation and direct choice remain. No internal section becomes an additional gain.

The older submitted recording shows AMorgna's Conversation 244 / Dialogue 1082 area (Gain block 16), a different source block. Its unrest/language predicates were also reproduced by the existing MOV regression suite in Compact OFF. Ending_Vote_Liberals' Gain block 7 is a separate write (244:997), also covered by the audit; these identities are not conflated.

## Real-data audit

| Variable | Write blocks | DAG segments | Condition regions | Exhaustive reductions | Hidden | Summarized | References retained | Factoring |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| BaseGame.AMorgnaWesCore | 20 | 3353 | 253 | 974 | 950 | 24 | 3353 | 3 |
| BaseGame.Economy | 219 | 9201 | 1039 | 2182 | 2101 | 81 | 9201 | 2 |
| BaseGameIsolated.Ending_Vote_Liberals | 8 | 2315 | 458 | 506 | 498 | 8 | 2315 | 0 |
| BaseGame.AgnoliaTradeDeal_Negotiation | 9 | 634 | 7 | 188 | 188 | 0 | 634 | 0 |

Counts are per write, so shared ancestry is counted again for distinct operations. Condition regions are remaining predicate-bearing layout regions, not assertions that all separate regions form one conjunction. Exhaustive counts are reduction steps, including condition-free reconvergence; summarized counts are family occurrences before equal summary labels are combined. The complete Gain 7 route-to-region audit is in `compact-validation/audit.txt`.

## Validation

- headless: PASS: 13.948.256 checks in 44,08 s.
- compact-ui: PASS Compact desktop checks 8377.
- route-ui: PASS: exact MOV blocks, no unresolved condition cells, source DAG evidence, full Election aggregate and focus.
- shell-ui: PASS: popup layout/selection/Escape/outside click, quantitative toggle, grid-only/source modes, cached toggle, full-height growth, toolbar focus and thin separators.
- final-ui: PASS: 15048 final desktop checks; Agnolia, Economy, logic geometry, Source evidence and actual rooted screenshots.
- numeric-ui: PASS: numeric real-data grid, toggle, source evidence, cell/header/route focus, clearing, dimming and snapshots.
- variable-ui: PASS: filtering/selection separation, reconstructed EPA guard in live family table, summary and family snapshots..
- runtime-ui: PASS: actual JavaFX Budget canvas, fit, zoom/focus, measured member hits, edge hits and full-scale snapshots..

Symbolic coverage includes A/!A, two/three-variable and 256-variable covers, non-exhaustive alternatives, common-X factoring, unequal suffixes, rejected untyped numeric equality, and reference preservation across all four real-data variables. Desktop checks exercise the actual picker, grid, Source evidence, independent toggles, stable logic-label geometry, Choice projection, and real source blocks. The existing route screenshot test now explicitly selects Compact OFF to inspect expanded predicates; the shell test waits for layout before measuring the popup viewport.

## Changed classes

Production: `BooleanCoverage` (new), `CompactNumericLayout` (new), `NumericVariableLayout`, `ConditionLogic`, `TypeProjection`, `NumericVariableGrid`, `VariableInspectorWindow`.

Tests: `CompactChecks` and `CompactVisualChecks` (new), `AllTests`, `RouteDagVisualChecks`, `InspectorShellVisualChecks`. New runner: `scripts/test-compact-ui.sh`.

## Screenshots

Actual JavaFX screenshots, inspected after capture:

- [Choice ON](compact-validation/choice-on.png) / [Choice OFF](compact-validation/choice-off.png)
- [Gain 7 expanded](compact-validation/gain7-before.png) / [Gain 7 Compact ON](compact-validation/gain7-compact.png)
- [Resigned summary](compact-validation/resigned-summary.png)
- [Compact with logic labels](compact-validation/compact-logic-on.png)
- [Source evidence behind the summary](compact-validation/summary-source-evidence.png)

## Limits

Compaction deliberately leaves unsupported, historical, incomplete, cyclic or over-budget structures explicit. It is a presentation reduction of the retained source DAG, not a full temporal simulation of every game state. Large remaining non-exhaustive alternatives may still require horizontal scrolling. The project uses its existing Java 25 + JavaFX build/run scripts.
