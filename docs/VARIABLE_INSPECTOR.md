# Variable Inspector — first-pass implementation and validation

Historical first-pass report. The current, targeted second pass is documented in
[DIALOGUE_GUARDS.md](DIALOGUE_GUARDS.md). It supersedes the local-only guard limitation
and the state-table restrictions described below; architecture and discovery remain unchanged.

Implemented against the submitted SordlandTreeViewer.zip only. Existing Loader,
Dataset, RuntimeDatabase, Semantics, graph builders, layouts, and graph search are
unchanged. No dependencies or script engines were added.

## Changed files

Existing code/documentation changed:
- `src/sordland/Main.java`: two-choice enum-backed menu after Find; owned inspector
  lifecycle, data-reload invalidation, shutdown, and menu smoke assertions.
- `tests/sordland/AllTests.java`: register the headless variable checks.
- `README.md`: usage and conservative interpretation contract.

Added:
- `src/sordland/analysis/SearchMode.java`
- `src/sordland/analysis/VariableSyntax.java`
- `src/sordland/analysis/VariableIndex.java`
- `src/sordland/analysis/VariableCatalog.java`
- `src/sordland/analysis/VariableAnalyzer.java`
- `src/sordland/ui/VariablePicker.java`
- `src/sordland/ui/VariableAnalysisView.java`
- `src/sordland/ui/VariableInspectorWindow.java`
- `tests/sordland/VariableChecks.java`
- `tests/sordland/VariableVisualChecks.java`
- `scripts/test-variable-ui.sh`
- This document and artifacts in `docs/variable-validation/`.

## Discovery and analysis

The immutable loaded dataset is the input. One worker builds an occurrence index
from Entry condition/script/sequence, Item activation/begin/end, each Option
condition/instruction, GameFlow Turn condition/start and Step start. Dialogue
source identities retain conversation/dialogue IDs; item identities retain
collection/index and database name; scheduler identities retain source indices.
Known turns are retained, not inferred through arbitrary dialogue links.

Runtime discovery uses the existing preferred-member, StoryPack_Main loader and
its graphEligible scope check. Known Entity property records supply condition and
instruction fields, nested ConditionalInstructions, IsEnabledVariable, and panel
variable metadata. Exact panel -> page -> option references resolve explicit
PanelCounter/PanelBar increments separately from Instruction. Runtime titles are
used for labels; otherwise the full canonical variable name is retained.

The lexer recognizes actual BaseGame-family qualified references and both quoted
Variable[...] forms. A quoted content ID passed to a function is not a variable.
There is no recursive raw-map text scan, no additional JSON reading during
filtering, and no inferred relationships from names alone.

VariableCatalog has no reference to VariableAnalyzer. Filtering is case-insensitive
substring matching, ranked exact/prefix/piece/substring. A primary mouse click on
a nonempty catalog cell is the only picker commit; text changes never select or
analyse a variable. The window uses its own daemon worker, cancellable tasks and
revision checks, with a per-variable analysis cache. Closing disposes that worker;
reopening builds a fresh dataset-scoped index. Reloading data closes the old window.

## Access, proof and deduplication

Each executable command contributes at most one occurrence per variable:
- READ: a supported condition or assignment RHS referring to another variable.
- WRITE: the target of an ordinary assignment not reading that target.
- READ_WRITE: a compound assignment, or assignment that also reads its target.
- REFERENCE: an explicit metadata variable binding.
- UNRESOLVED: an unsupported operation or a write whose guard cannot be parsed.

X = X + N is normalized to one modification. Absolute assignment remains SET.
The parser is a closed precedence-based AST for boolean operators, comparisons,
parentheses, literals, references, and simple arithmetic. No source code executes.
Semantics supplies its existing conservative command boundaries/control-flow
classification. Unsupported control blocks are retained whole and do not yield
unconditional writes. A separate exact parser must also accept an effect before
it becomes a rule. Guard dependencies are extracted only from that local AST.

The canonical typed fields take precedence over runtime records with the same
logical collection/database name, and alternate runtime locations are retained.
Runtime entities use collection plus stable ID/name deduplication; preferred
serializer members were already selected by RuntimeDatabaseLoader. Display text
and raw mirrors are not scanned as executable fields. Source identity, field and
operation ordinal preserve two legitimately repeated commands as two operations.
Distinct dialogue entries remain distinct even if their statements are identical.

EXACT means a supported local source operation. EXPLICIT_STRUCTURE means a local
condition/effect pair or the resolved panel/option increment relationship.
Neither proves global reachability, necessary causation, frequency or final state.
No false branch or backwards dialogue-path condition is invented.

## Adaptive rendering

Boolean assignments get SET TRUE/FALSE groups. Identical guard ASTs share a card,
with all concrete source operations available beneath it. Predominantly numeric
operations get a modifier table, including SET for absolute or symbolic writes.
Other assignments are grouped by assigned expression/value. References and
unsupported appearances remain available even if there are no proven rules.

State-family candidates arise from boolean co-writes. Every indexed write to every
candidate member must be supported, assign all members once in the same source
field, and set exactly one true. Partial writes, repeated writes, unresolved uses,
or nonboolean writes disqualify the family. At least two source groups and outcomes
are required. Tables are limited to 2–6 members, at most six guard drivers and 24
transitions, and no OR/unknown guards. Uncovered combinations remain UNKNOWN /
NOT PROVEN. This is a sparse table of actual source transitions, not a prediction
for unobserved combinations or an inferred game-wide execution order.

Related variables link to another analysis with independent Back/Forward history.
Exact source identity is shown instead of introducing graph-navigation side effects.
Evidence rows are virtualized and expandable, with exact full source fields,
operation text, normalized effect, guard, proof and alternate source locations.

## Real supplied-data results

| Canonical variable | Read-only operations | Proven write operations | Unresolved | Layout |
|---|---:|---:|---:|---|
| BaseGame.Malenyevist | 92 | 103 | 0 | Numeric |
| BaseGame.Economy | 240 | 219 | 0 | Numeric |
| BaseGame.Situation_Diplomacy_Energy_PriceBalanced | 14 | 24 | 0 | Boolean + state transitions |
| BaseGame.Situation_Diplomacy_Energy_PriceFluctuation | 30 | 24 | 0 | Boolean + state transitions |
| BaseGame.Situation_Diplomacy_Energy_PriceSurge | 32 | 24 | 0 | Boolean + state transitions |

The UI's Reads chip additionally includes READ_WRITE occurrences.

Energy Prices produces a useful generic source-transition rendering: the three
states form a proven one-hot family across 24 source fields. Surge has five true
assignments and nineteen false assignments. Runtime metadata supplies labels such
as “Surging Energy Prices”. No Energy/EPA/Gasom names occur in production logic.

These dialogue writes have no guard on the same entry. The inspector therefore
explicitly says no additional local guard is encoded. It does NOT reconstruct an
EPA/Gasom causal decision table from earlier dialogue predicates. Such a table
would exceed the proof available from the implemented local analysis. This limit
is intentional, and the exact conversation/dialogue evidence remains accessible.

## Validation

- `./scripts/test.sh`: PASS, 12,041,423 checks, including all existing regressions.
- `./scripts/build.sh`: PASS on the existing Java 25 / JavaFX toolchain.
- `./scripts/test-variable-ui.sh`: PASS with real data; typing narrows suggestions
  without analysis or automatic selection, clicking an exact option renders the
  analysis. Reviewed `variable-validation/energy-inspector.png`.
- `./scripts/test-runtime-ui.sh`: PASS; existing Budget canvas, sizing, zoom,
  member and edge hit checks.
- `./scripts/run.sh --smoke=docs/variable-validation/graph-regression`: PASS;
  existing ROOTED/PLAIN, search, Types, turns, actors, navigation, metadata, zoom,
  and edge checks, plus the new menu opening/closing and default reset checks.

Desktop tests require screen access; the initial sandbox-only graphics attempt
had no screen. The tests passed with desktop access. No headless test depends on
a display. The source JSON files are checked unchanged against the input ZIP.
