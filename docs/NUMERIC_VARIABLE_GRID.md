# Numeric Variable Inspector implementation

Implemented on the supplied latest SordlandTreeViewer ZIP. The complete project includes all original game data, source, IDE configuration, scripts, tests, and documentation. Generated build caches and macOS metadata are omitted; `scripts/run.sh` rebuilds the application using the existing Java 25 FULL toolchain.

## Changes

- Added `ConditionLogic`: structural Boolean normalization, basename display, and exact constant arithmetic.
- Added `NumericVariableLayout`: independent write blocks, original proven routes, factoring, normalized rectangular row/column spans, and source references.
- Added `CompatibilityAnalyzer`: conservative predicate and source-choice proofs, with an extensible `SourceRule` interface.
- Added `NumericVariableGrid`: rectangular spreadsheet cells, effect colors, compatibility focus, and Source mode.
- Updated `VariableAnalysisView` to use the new grid only for numeric results.
- Updated `VariableAnalyzer` to recognize constant computed numeric writes; existing Boolean/value/family analysis remains intact.
- Updated `VariableIndex` with separate decision/bill option metadata. Existing occurrence generation and alias deduplication are unchanged.
- Added `NumericLayoutChecks`, `NumericVisualChecks`, and `scripts/test-numeric-ui.sh`; registered headless checks in `AllTests`.

`VariablePicker`, `VariableCatalog`, `VariableInspectorWindow`, the syntax parser, dialogue resolver, main viewer, and graph/layout implementations are unchanged. All 166 supplied JSON files are byte-identical to the ZIP.

## Identity, factoring, and provenance

Each block is exactly one existing write Occurrence, identified by source identity, field, and operation ordinal. Identical effects or guards never combine separate writes. Existing runtime aliases remain attached to their canonical source operation.

The builder consumes individual `DialogueGuardResolver.Resolved.paths()` directly. It normalizes `VariableSyntax.Expr`, flattens nested groups, removes exact duplicate expressions, and factors common conjuncts within that block. Shared prefixes appear above splits; shared suffixes appear below. Compound Boolean comparisons such as `(A && B) == false` are decomposed structurally. Relational negations retain a readable NOT form instead of assuming numeric type or NaN semantics.

Stacked condition cells require all predicates; adjacent cells represent alternatives. Unfactored incoming paths have separate labeled route bands. Shared cells retain the union of original path references. Every reference retains its source Occurrence and exact resolver predicates/edges. Missing incoming proofs appear as Unknown, not invented unconditional routes.

## Compatibility and Source mode

Click a condition, route label, or block heading to focus; repeat the click, click blank space, or use Clear focus to reset. Proven incompatible regions become slightly darker and transparent. Selected blocks/routes remain fully visible.

Proofs include Boolean opposites, unequal numeric equalities, disjoint numeric intervals, exact predicate negation, and different options in the same typed decision/bill selection. Alternatives are never presumed mutually exclusive merely because they are adjacent. A merged region is dimmed only when every represented route pairing is proven incompatible. Disjunctive proof expansion is bounded at 256 clauses; overflow remains UNKNOWN.

These proofs compare one predicate state or one source selection. They do not establish that two writes cannot occur at different times in a campaign. Dialogue loops, priorities, mutable state over time, unresolved control flow, and unproven runtime-page exclusivity intentionally remain UNKNOWN.

Source mode is off by default. When enabled, clicking a header, route, or cell opens Source references below the grid, including identity, title, turn, field, operation, exact expression, local/resolved guard, represented proof paths, full original field, and runtime aliases. Canonical identifiers remain in evidence/tooltips.

## Validation

- `scripts/test.sh`: **PASS — 12,053,635 checks** (12.81 seconds). Existing VariableChecks and DialogueGuardChecks pass.
- New headless coverage: requested stacking/splitting examples; prefix/suffix factoring; distinct identical writes; Boolean normalization and compound false comparisons; numeric signs/SET/zero/computed/unknown effects; contradictions and UNKNOWN cases; proof-budget fallback; source-choice scope; merged-reference retention; rectangle coverage; and 300 generated expressions checked against all 16 Boolean assignments.
- All numeric variables in the supplied index were built and their condition cells checked for safe rendering.
- `scripts/test-numeric-ui.sh`: **PASS**, including real data, condition/route/header interaction, repeat/blank reset, conservative dimming, Source mode, and both references of a merged cell.
- Existing `scripts/test-variable-ui.sh`: **PASS**, preserving type → filtered results → exact click → analysis and the nonnumeric state-family view.
- Existing `scripts/test-runtime-ui.sh`: **PASS**, including Budget canvas, zoom, hit testing, and snapshots.

Real validation: `BaseGameIsolated.Ending_Vote_Liberals` has **8 independent blocks: 7 gains of +1 and 1 SET = 0**. The gains retain **19 proven paths**. The SET has unresolved incoming reachability. Every retained predicate and edge was checked against the supplied game data; no production code hard-codes this variable.

Validation logs and screenshots are in `docs/numeric-validation/`.
