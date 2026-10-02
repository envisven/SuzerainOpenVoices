# Proven dialogue guards — targeted second pass

This pass extends the existing VariableIndex / VariableAnalyzer and inspector.
The base is the completed first-pass project in this task; the second-pass
attachment contained the request, not another project archive. Actual source JSON
was inspected before editing. No graph builder, graph search, toolbar, picker,
loader, runtime integration, Semantics, or source data was changed.

## Files

Added:
- `src/sordland/analysis/DialogueGuardResolver.java`
- `tests/sordland/DialogueGuardChecks.java`
- This report, `variable-validation/guard-headless.log`, and the state-family screenshot.

Modified:
- `src/sordland/analysis/VariableIndex.java`: build the control-flow index once;
  attach immutable proof paths to occurrences; retain original local source guards.
- `src/sordland/analysis/VariableAnalyzer.java`: use resolved guards in summaries,
  dependencies and state-family rows; keep alternative indexed paths separate.
- `src/sordland/ui/VariableAnalysisView.java`: display guard categories and proof
  paths; distinguish unresolved incoming guards from unsupported operations;
  prioritize conditions/results in sparse family tables and wrap long cells.
- `tests/sordland/AllTests.java`: register new checks.
- `tests/sordland/VariableVisualChecks.java`: assert the actual EPA propagated
  condition reaches the live state-family table and capture it.
- `README.md` and `docs/VARIABLE_INSPECTOR.md`: current usage and historical-report clarification.
- Updated `variable-validation/energy-inspector.png`; graph smoke artifacts rerun.

## Resolver and allowed paths

The resolver builds EntryKey -> Entry and EntryKey -> incoming edges maps from the
already loaded Dataset, including cross-conversation incoming links so they cannot
be silently overlooked. It caches each requested entry's result. This happens in
the existing inspector worker while constructing VariableIndex, not on keystrokes
or by rebuilding graphs for every selected variable. Dataset replacement disposes
the owning inspector/index/cache as before.

It performs bounded reverse traversal of actual links. Every accepted incoming
path must contain a parsed predicate. A blank, effect-free entry is transparent
only if it has no dialogue text, menu text or script, and its sequence is empty or
exactly Continue(). A predicate entry must additionally have one encoded outgoing
continuation. An empty transparent junction may branch: only the actual edge into
the selected route is retained, and all of its incoming paths must still be checked.
No ordering of sibling branches is guessed.

A local condition is preserved. Mandatory successive predicates combine with AND
in traversal order. Independently proven incoming routes remain separate Path
records; VariableAnalyzer constructs their OR only for the analysis/presentation.
There is still one occurrence per concrete executable write. No factoring or other
new boolean simplification is performed; original AST grouping is preserved.

The guards refer to the state at entry along the recorded path, not to values after
later assignments in the write entry. They are necessary predicates for those
encoded routes, not claims that satisfying a predicate alone guarantees global
reachability, execution, or final game state.

## Stopping rules

The search stops at:
- speech or choices;
- any intervening script/effect (including known assignments that could invalidate
  a prior predicate), and every nontrivial sequence;
- unsupported or malformed predicates;
- conditions with multiple outgoing continuations;
- connectors, cross-conversation links, or non-Normal priorities;
- missing entries, START / dialogue-zero entry roots, or no encoded predecessor;
- merges with even one incoming route that lacks a proven predicate;
- cycles, depth over 48, more than 256 visited traversal nodes, or over 32 paths.

A boundary before a later mandatory predicate prevents earlier predicates from
being claimed; that later predicate may still be retained. For example, ambiguous
paths into B do not invalidate B itself when every route from B to the write is
proven. Cycles/resource bounds fail closed for the entire inherited proof; a valid
local target condition can still be retained. No NOT condition or false edge is
ever synthesized. An explicitly encoded false comparison can be parsed normally.

## Proof evidence

Each immutable result records LOCAL, DIRECT_CONDITION_EDGE, PROVEN_CHAIN,
ALTERNATIVE_PATHS or UNRESOLVED; a search-boundary diagnostic; and separate paths.
Each path contains its AST, exact condition text plus EntryKey for every predicate,
and ordered edges with origin/target EntryKeys, source order, priority and connector
flag. Evidence expands these records alongside the untouched source field and
operation. Propagated occurrences additionally carry DIALOGUE_GUARD as their proof
category. Unsupported operations remain unresolved independently of guard status.

READ / WRITE / READ_WRITE classification and canonical-source deduplication are
unchanged. Derived guard references do not manufacture extra source occurrences.
They contribute to the existing related-variable rule dependencies instead.

## Real source result

Inspected the current JSON entries 44:449–457 and all their incoming source links.
Conversation 44, dialogue 453 contains this exact condition:

```text
Variable["BaseGame.Turn03_EnT_Gasom_OnlyRiziaInvestment"] == true  and  (Variable["BaseGame.Policy_Economy_EnergyProtectionAct_Removed"] == true  or  Variable["BaseGame.Policy_Economy_EnergyProtectionAct_10Percent"] == true)  or (Variable["BaseGame.Turn03_EnT_Gasom_CoInvestmentRizia"] == true  and  Variable["BaseGame.Policy_Economy_EnergyProtectionAct_Removed"] == true)
```

Its sole link is `44:453 -> 44:450`, source order 0, priority Normal, connector false.
Dialogue 450 has no local condition and writes:

```text
Variable["BaseGame.Situation_Diplomacy_Energy_PriceSurge"] = true;
Variable["BaseGame.Situation_Diplomacy_Energy_PriceBalanced"] = false;
Variable["BaseGame.Situation_Diplomacy_Energy_PriceFluctuation"] = false;
```

The generic result is therefore:

```text
(OnlyRiziaInvestment AND (EPA Removed OR EPA 10%))
OR
(CoInvestmentRizia AND EPA Removed)
    -> Surge TRUE, Balanced FALSE, Fluctuation FALSE
```

The short names above are explanatory aliases for the exact identifiers shown
above. Production code contains no Energy, EPA, Gasom or Rizia-investment rules.
The inspector uses runtime titles where available and canonical names otherwise.

Balanced at 44:451 also gets the explicit outer condition from 44:454 AND the local
branch predicate at 44:456, through transparent junction 44:455. Fluctuation at
44:449 likewise uses the actual explicit false-comparison predicate at 44:457;
it is not an invented ELSE branch.

Each of the three energy variables retains 24 proven writes: **9 now have derived
incoming guards; 15 incoming guards remain unresolved**. Their effects remain
known even when preceding control flow cannot be proven. The one-hot family still
has 24 concrete transition rows. No source counts or modifiers were changed.

## State-family scope

The existing complete, supported one-hot co-write detector is retained. The sparse
renderer now accepts known AND/OR predicate trees instead of rejecting OR guards.
It still caps family size at six members and table size at 24 source transitions.
The old six-driver limit is unnecessary for this sparse source-row table and is
not applied: it never enumerates combinations. UNKNOWN / NOT PROVEN remains the
answer for uncovered combinations. No claim of global mutual exclusivity beyond
the detected source transitions is added.

## Validation

- `./scripts/test.sh`: **PASS — 12,041,622 checks**. All previous checks remain.
- New tests cover direct edges, nested AND, alternative OR with two proof paths,
  no duplicate write at merges, unconditional bypass, surviving downstream local
  predicates, no invented false branch, loops, mutation, unknown scripts,
  transparent chains, speech, connector/priority/cross-conversation boundaries,
  malformed predicates, ambiguous condition continuations, caching and limits.
- Real regression tests cover all three energy variables. Every retained proof
  edge is checked against actual Link records, and every predicate text against
  its actual Entry. The 44:453 -> 44:450 guard AST must equal the parsed JSON field.
- `./scripts/build.sh`: PASS using the existing Java 25 / JavaFX toolchain.
- `./scripts/test-variable-ui.sh`: PASS; filter/selection separation preserved,
  actual derived EPA guard present in the live table. Summary and wrapped table
  screenshots inspected manually.
- `./scripts/test-runtime-ui.sh`: PASS; existing Budget canvas and hit tests.
- Full `./scripts/run.sh --smoke=docs/variable-validation/graph-regression`: PASS;
  ROOTED/PLAIN, search, types, turns, navigation, actors, metadata, zoom, edge trace,
  and inspector opening/closing remain intact.

Desktop tests were run with screen access. Headless tests remain display-independent.
All supplied source-data files are verified unchanged. Complete project delivery
includes sources, data, scripts, tests and these validation artifacts.
