# Final variable renderer validation

Validated 1 October 2026. This report supersedes the earlier validation reports retained in this project.

## Deliverable and base

The latest user submission, `sordland.zip` (SHA-256 `f23b857fe362749d59acc400ee5f8b94cac520e8fc637a370d8f696dc8751b63`), contains the Java source package only. Every submitted source file was installed into a separate copy of the latest complete workspace project before this finishing pass. No older implementation replaced the submitted sources.

The complete deliverable includes `src/`, `tests/`, `scripts/`, `docs/`, `data/`, IntelliJ configuration, Unix/Windows build and launch scripts, source audits, screenshots, and this report. All 166 existing JSON data files are byte-for-byte unchanged. They are the existing application's input resources, required for the default automatic load; no additional generated game dumps were restored. Generated classes, build directories, temporary investigation programs and local scratch files are excluded from the ZIP.

Run with an installed JDK 25 FULL containing JavaFX:

```sh
./scripts/run.sh
```

Set `JAVA_HOME` if necessary. Windows: `scripts\run.cmd`. The project does not download a runtime.

## Architecture retained

`VariableIndex` indexes exact source operations and their canonical identities. `NumericRouteResolver` and `RouteProof` retain the source DAG, original predicates, writes, edges, choices and local boundaries. `CausalProvenance` provides player-facing choice/event causes and conservative source-choice proofs. `NumericVariableLayout` produces separate write blocks, structural Boolean regions, references and transformation history. `NumericVariableGrid` renders those regions as cells. The existing popup picker and background/cached analysis flow remain in `VariableInspectorWindow`.

`RootedCampaignGraphBuilder` continues to follow the authoritative GameFlow order. Source-choice cards represent previously made choices. Each enabling card has one continuation to its event, rather than another independent TRUE/FALSE selection. The graph is an inspection view, not a full playthrough simulator.

## Corrections in this finishing pass

- Normal numeric mode shows only the toolbar/search controls and spreadsheet. Status explanations, technical route labels, source tooltips, evidence and related variables are absent until Source mode is enabled.
- Source mode and All variables remain OFF by default. The popup picker, selection behavior, Escape, outside-click dismissal and cached Source toggle were preserved and tested.
- Player-choice provenance remains visible even when all choices at a source fork lead onward. Choice/event labels use human-facing event titles; original identities remain in Source mode.
- Boolean alternatives remain side by side. A width cap must not turn alternatives into apparently required stacked conditions. Long genuine alternatives can require horizontal scrolling.
- Partial source branches are not merged with complete siblings as if they established an exhaustive Boolean alternative. Known predicates and all source references are retained.
- Serial composition is bounded to prevent unnecessarily tall shared regions. Actual local source predicates retain their full structure. The recorded MOV problem blocks remain within the existing geometry regression bounds.
- AND/OR markers are unmanaged overlays, with no change to any visible cell rectangle or overall table dimensions. The toggle starts OFF.
- Gain/loss/SET amounts remain prominent; their header has enough minimum height for the larger font.
- Infrastructure completion is checked by bounded Boolean-state traversal over every initial valuation of the relevant flags, rather than treating guard-false dialogue leaves as feasible exits. The real infrastructure conversation requires H-3 or L-1 on every feasible completing route.
- The contract alternatives are scoped to the earlier infrastructure event's activation. Rejecting investment remains outside the H-3/L-1 choice. Each selected project continues to its own contract.
- Long campaign choice labels are measured cards instead of overlapping text on connectors. Circas Attend/Do not attend and Koronti Arrange/reject retain their separate legitimate outcomes.
- Choice proofs reject future source producers and explicit intervening overwrites instead of relying only on turn-number proximity. Unproven activation retains its source condition and an explicit unresolved-opposite diagnostic; an activation predicate alone never creates a skip destination.

Production classes changed relative to the submitted source package: `CausalProvenance`, `NumericVariableLayout`, `NumericVariableGrid`, `VariableInspectorWindow`, and `RootedCampaignGraphBuilder`. New acceptance checks: `FinalValidationChecks` and `FinalVisualChecks`, plus `scripts/test-final-ui.sh`.

## Tests

Runtime: existing Liberica JDK 25 FULL, JavaFX, local macOS desktop.

| Command | Result |
| --- | --- |
| `./scripts/test.sh` | **PASS — 12,075,020 assertions**, 28.24 seconds |
| `./scripts/test-variable-ui.sh` | PASS — picker/filter separation, reconstructed guards and live analysis |
| `./scripts/test-numeric-ui.sh` | PASS — real numeric grid, compatibility, source-reference union, headers and clipping |
| `./scripts/test-runtime-ui.sh` | PASS — actual runtime-panel Canvas, fit/zoom, measured hits and edge hits |
| `./scripts/test-route-ui.sh` | PASS — exact MOV areas, boundary evidence, focus and all 89 election blocks |
| `./scripts/test-inspector-shell-ui.sh` | PASS — native popup selection/Escape/outside click, resizing, cached toggles and grid-only view |
| `./scripts/test-final-ui.sh` | **PASS — 24,031 assertions**, including cell geometry, no normal-mode source tooltips/status, and real screenshots |

All existing headless suites run through `AllTests`; the new real-data acceptance suite is included there. Full logs are in `docs/final-validation/logs/`.

Obsolete test expectations were changed only where the new specification/source justified it: Gasom's two exhaustive options now share one proven split; twenty alternatives at one actual fork remain horizontal; the gain amount is in its own header cell; hidden technical route labels are not normal-mode focus targets; source-choice cards are allowed between a proven gate and its event; a synthetic standalone activation without a negative/skip proof no longer asserts a skip (real Circas/Koronti tests verify legitimate skips). No suite was removed. The native outside-click test now allows a desktop pulse after requesting window focus before sending the physical mouse click.

The final-election inspector loaded all 89 blocks in 4.02 seconds in the route desktop run; AMorgna loaded in 1.05 seconds. These are observed local timings, not performance guarantees.

## Real-data audit

| Metric | Agnolia negotiation | Economy |
| --- | ---: | ---: |
| Indexed writes | 9 | 219 |
| Rendered independent write blocks | 9 | 219 |
| Source routes / DAG segments | 634 | 9,201 |
| Retained distinct source references | 634 | 9,201 |

The detailed audit files include trigger-cell counts, partial routes, tautology transformations and factoring transformations:

- [Agnolia audit](final-validation/AgnoliaTradeDeal_Negotiation-audit.md)
- [Economy audit](final-validation/Economy-audit.md)

Economy has **81 player-choice trigger cells**, **7,286 state-condition cells**, **131 event/story/option trigger cells**, **373 partial source routes**, **1,185 Boolean-tautology transformations**, and **2 common-prefix/suffix factoring transformations**. Trigger counts count rendered cells; transformation counts are distinct per independent write. A source route here is a DAG segment, not an enumerated complete game history. Compression preserves the exact union of source references for each write; tests check that no reference moves across write blocks.

Agnolia's real `77:176` and `77:177` writes display the specified “Not if you keep acting…” and “Of course, Mr. Van Hoorten…” choices with −1 and +1. Immigration, H-3 and Agnland state contributors remain visible. The real rooted graph verifies infrastructure selection/contracts, Circas attendance/skipping, and the arranged Koronti meeting's direct continuation.

## Screenshots of actual application output

The numeric images use the actual inspector and popup selection. Campaign images use the actual `GraphCanvas`, rooted graph builder, type projection and layout engine; they are focused views of the full real campaign, not mockups or hand-drawn graphs.

1. [Agnolia choices and effects](final-validation/agnolia-choices.png)
2. [Economy complex section](final-validation/economy-complex.png)
3. [Logic labels ON](final-validation/logic-labels-on.png)
4. [Source evidence for an Agnolia choice cell](final-validation/source-choice-evidence.png)
5. [Infrastructure mandatory H-3/L-1 selection](final-validation/infrastructure-selection.png)
6. [Infrastructure contract continuations](final-validation/infrastructure-contracts.png)
7. [Circas Attend / Do not attend](final-validation/circas-funeral.png)
8. [Koronti Arrange / reject](final-validation/koronti-meeting.png)

Refreshed reproductions of the original screen-recording areas are in `docs/route-validation/`.

## Remaining genuine limits

- Economy's 373 partial segments remain partial. Unsupported commands/predicates, cross-conversation/priority boundaries, cycles and node/depth budgets are disclosed in Source mode. Known information is retained; missing ancestry is not replaced with invented conditions.
- Safe tautology removal applies to supported Boolean expressions and proven equivalent local alternatives. Different downstream outcomes, source choices, historical states and incomplete branches are not erased merely because two visible atoms look complementary.
- Source-choice causality is a static proof over supported assignments and source links, not execution of the game engine. The viewer does not predict arbitrary engine calls, scheduling behavior or an entire accumulated playthrough state. Source evidence remains available for those limits.
- Campaign steps can contain independent candidates. Their neutral scheduler junctions do not mean that selecting one candidate excludes every other event in the step.
- Genuine wide alternatives can require horizontal scrolling. Large variables retain many cells because the source contains many distinct writes/routes; compactness is not used as a reason to discard provenance.

No game data was changed, no game scripts were executed, and no source conditions, option text or effects were invented. The finite Boolean proof refuses unsupported constructs or budget overflow rather than claiming they are proven.
