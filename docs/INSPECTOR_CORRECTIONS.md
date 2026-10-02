# Variable Inspector correction report

Implemented on the last delivered `SordlandTreeViewer-updated.zip`, as requested. All 166 bundled JSON files are byte-identical to that base. Existing main-viewer and graph code is unchanged.

## Default view

Source mode starts OFF. For a numeric selection, the entire center is the spreadsheet: no selected-variable heading, canonical-name subtitle, statistics, Derived mechanic, explanatory text, related variables, source evidence, source references, or internal grid controls. Only the fixed toolbar and search row remain above the table. One outer scroll area fills the remaining height; there is no nested fixed-height grid viewport. Blocks touch with thin borders.

The toolbar owns Back, Forward, Cancel, Source mode, Clear focus, and status. Source mode restores detailed analysis and click-for-source facilities. Toggling reuses the same grid and cached semantic analysis.

The picker now uses an anchored popup. It filters with the existing matching/ranking semantics and closes on selection, Escape, or outside click without resizing the result. All variables starts OFF: the cheap shared classifier exposes 65 quantitative variables; enabling it restores all 4,793 catalogue variables.

## Route resolution and identity

Numeric routes traverse ordinary speech, menu and supported effect nodes. Exact source predicates, edges, ordering and choice identities are retained. Reconverging alternatives use a proof graph to avoid exponential path enumeration. Predicates preceding a mutation are explicitly marked “Earlier:” rather than treated as current-state facts. A route with no additional condition is valid.

Real Conversation 93 writes 275 and 285 remain separate +1 blocks with distinct ancestry. Both resolve to complete same-conversation source ancestry. Tests verify every retained predicate and edge against the JSON and prove that 285 requires Highway and OnTime before the Agnolia trade predicate.

Exact duplicate occurrences are deduplicated; equal displayed guards never merge independent writes. Proven straight-line deltas in the same complete source field can show a combined +2 total in Source mode while retaining all write references and separate grid blocks. Cross-entry co-execution is not presumed.

Boolean factoring and geometry remain intact. AND stacks vertically; OR is adjacent within a formula; separate alternative route groups have labeled bands. Broad formulas scroll horizontally with readable minimum cell widths. Factoring stays inside each bonus block.

Unsupported control scripts, connector/cross-conversation boundaries, cycles, and proof-budget limits remain explicit partial/unknown results. Source mode gives the reason and retained evidence; the resolver does not invent missing conditions or execute game scripts. Economy still contains 131 partial/unresolved incoming proofs outside the verified complete C93 cases.

## Validation

- `scripts/test.sh`: PASS — 12,136,035 checks, 32.23 seconds, including existing Boolean/layout/runtime tests, all quantitative models, new route/classifier/duplicate/aggregation regressions, and real C93 requirements.
- `scripts/test-inspector-shell-ui.sh`: PASS — actual inspector, native popup selection/Escape/outside click, layout stability, both catalogue modes, grid-only default, cached detailed toggle, full-height growth, toolbar focus reset and thin separators.
- `scripts/test-variable-ui.sh`: PASS — filtering/exact selection and existing Boolean EPA family regression.
- `scripts/test-numeric-ui.sh`: PASS — real numeric data, focus, compatibility dimming, source evidence and reference retention.
- `scripts/test-runtime-ui.sh`: PASS — Budget canvas, fit, zoom, member/edge hits and snapshots.

Logs and current screenshots are in `docs/correction-validation/`. Older reports and screenshots are retained as historical records; this report describes the current correction pass.

## Running

Open the included project in IntelliJ with Liberica JDK 25 FULL, or run `./scripts/run.sh` (`scripts\run.cmd` on Windows). No downloaded dependencies are needed. The archive includes the complete source, data, IDE configuration, scripts, tests and documentation; generated build outputs are excluded.
