# Runtime integration validation — 2026-09-21

Environment: existing BellSoft Liberica FULL JDK 25.0.4.1 with JavaFX on macOS. No dependencies or runtime were downloaded.

- Unmodified supplied baseline: **6,671,942 checks PASS**.
- Final `./scripts/test.sh`: **11,869,167 checks PASS**, exit 0 (10.97 seconds).
- Original legacy regression classes and assertions are unchanged. AllTests preserves their legacy linear-entry contract by running them with optional runtime absent. RuntimeChecks separately verifies all 286 enriched graphs, every exact Link object and cardinality, runtime counts/preferred selection/scope, raw source provenance, all Budget booleans/Economy/counter effects, twelve bundled cards, one continuation, unresolved fixtures, generic panels, conditions, actor projection, defaults, PLAIN/ROOTED content and geometry.
- Deterministic Budget geometry checks cover containment, aligned headers, no overlapping cards, no segments through unrelated cards, incoming top-middle connection, correct branch exits, expanded cards, actor filtering and node hit testing.
- Existing desktop smoke suite: **PASS**, including ROOTED/PLAIN, independent type selection, News evidence/details, conditions, search/turn state, Ignored data, navigation, actor filters, metadata, fit/zoom/readable and edge trace. No old smoke assertions were weakened.
- `./scripts/test-runtime-ui.sh`: **PASS** with actual GraphCanvas and TextMeasurer, fit, zoom/focus, measured member hit tests, edge hit tests and screenshots. Budget full and education-detail images were visually inspected.
- Initial sandbox GUI startup failed because JavaFX had no exposed screen. The final desktop runs succeeded with screen access; this was an execution-environment limitation, not a skipped UI test.
- Original JSONs, existing scripts, IntelliJ configuration, and original test classes (except the additive AllTests orchestration) were byte-compared against the supplied ZIP. Full runtime catalog and all 162 member JSON files are shipped unchanged.

Logs and Budget screenshots: `docs/runtime-validation/`. The distributed archive excludes generated build classes; scripts rebuild from source. This preserves the supplied project structure, `.idea`, `.run`, `.iml`, and all original documentation/assets.

---

## Preserved baseline documentation (historical)

# Validation — final current-source completion

Validated on the local Mac using the installed BellSoft Liberica FULL JDK 25.0.4.1, including JavaFX. No runtime or dependency downloads were required.

## Clean build and principal suite

From the authoritative project directory:

```sh
rm -rf build/classes build/test-classes
./scripts/test.sh
```

Only generated class directories were removed. The test script recompiles all current production and test sources. Result: **6,671,942 checks passed in 9.26 seconds; exit 0**. Build succeeded. See `validation/tests-final.log` for the exact output (the JVM prints grouped numbers with the local locale).

All 18 `*Checks.java` suites are called by AllTests, including ThemeTypographyChecks and the new SourceAccountingChecks. No orphan test suite remains. The final runnable archive is also extracted into a fresh verification directory and tested independently before delivery.

Coverage includes:

- Independent accounting of all three source files, exact original raw-field retention and byte integrity.
- 11 turns, 132 steps, 227 exact GameFlow references; missing/ambiguous/incomplete/unresolved fixtures and strict branch proof rejection.
- Exact News variable/call evidence; no approximate matching; optional-route provenance; conservative unplaced articles; compact ROOTED and PLAIN News card text.
- Type defaults, independence, hidden chains/branches, original-edge provenance, immutable canonical graphs and state snapshots.
- All **64** applicable ROOTED type combinations, all turns, expanded metadata and named combinations from the task cards. Geometry checks cover node/group collisions, orthogonal connector clearance, separate parallel alternatives, turn containment, tight containers and independent hit targets.
- All 286 complete dialogue graphs: 159,924 boxes and 187,600 exact source links across those graphs, maximum 3,187 boxes. Counts across graphs include cross-conversation reachable subgraphs; the original input contains 104,553 link records.
- Actor projection, loops/reconvergence, unknown instructions, exact entry identity, link ordering, edge hit testing/selection, viewport transforms and text metrics.
- Exact font family/weight/size measurement, Unicode-safe truncation and wrapping, dark-theme text/metadata contrast, strong turn-label/connector contrast and practical condition heights.

## Real JavaFX runtime verification

Command, from the same working project:

```sh
./scripts/run.sh --smoke=../completion-audit/gui
```

The script recompiles the current sources before launching. It ran with permission to use the local Mac display and returned **SMOKE PASSED, exit 0**. This replaces the earlier display-limited result; no stale classes or headless proxy render was used.

The harness exercises real JavaFX scenes, controls, observable checkbox properties, asynchronous layout, navigation handlers and Canvas hit testing. It saves rendered scene PNGs, including the popup’s own scene. It verifies:

- ROOTED default and PLAIN toggle, News-off/Condition-on defaults and no ROOTED Dialogue fragment category.
- Actual Types popup appearance, multi-selection, canonical identity, hidden Condition and only-News projections.
- ROOTED News appearance, compact cards and full article detail/Back.
- Ignored Data file groups, counts, searchable provenance and raw-record inspection/Back.
- PLAIN turn filtering, Bill details, hidden matching type staying absent under search, clearing search and compatible mode transitions.
- ROOTED turn selection, visible Gasom search, clear TRUE/FALSE skip routes, query/turn/type persistence.
- Dialogue opening, speaker colors, actor-hidden speech with mechanics retained, Back to the same campaign.
- Metadata expansion, zoom, Fit and Readable at 100%.
- A synthetic mouse release through the real Canvas handler at a unique connector location, with an assertion that **Selected arrow** opens (not merely any inspector).
- Shared dark Data notes dialog.

Reviewed actual screenshots include startup, popup, Turn 2 band, Gasom branches, ROOTED/PLAIN News, both types of details, ignored groups/raw/search, dialogue/colors/actor filtering, metadata and selected-arrow inspector. Files are in `validation/screenshots/`; the run log is `validation/gui-final.log`.

The native desktop connector could not bind to the unbundled Java executable. Verification therefore used the explicitly requested existing JavaFX smoke automation and image inspection. It is not described as manual physical clicking. The model/geometry suite supplies exhaustive type-combination coverage; screenshots supply representative rendered acceptance, not a claim to inspect every one of 83,454 entries manually. Windows launch scripts are included but this validation was performed on macOS.

## Discrepancy search

Reviewed source, docs and tests for obsolete behavior. Remaining `ancillary` references are the compatible internal Dataset storage/accessor and PLAIN builder argument; the UI always includes supported records in its canonical catalogue and uses Types for visibility. `All types` is the retained builder sentinel used by tests/Main, not a type-selection widget. The only Main ComboBox is Turn. No old type ComboBox, Ancillary checkbox, opacity-based type filtering or duplicate horizontal ROOTED turn labels remain. Historical defect descriptions in CHANGES are intentional.

## Source integrity

| Input | SHA-256 |
| --- | --- |
| SuzerainDataDumper.actor_names.json | `336d59165011e37425c5c8e1322df820c9fecf8696abd591ddb08c142ae5bd5c` |
| SuzerainDataDumper.conversations_Sordland.json | `e74761760ae80f14b8445c01450f8a03d638b4c1713b2e46816ab3c7f0440531` |
| SuzerainDataDumper.entity_data.json | `884143c99773afd420d0f6fc716a8086efdd431fd543f4adb7fb8043cbf7e6ec` |

The three files are unchanged from the supplied ZIP. The current working project is preserved; final outputs are made from that project. See [SOURCE_ACCOUNTING.md](SOURCE_ACCOUNTING.md) and [FINAL_AUDIT.md](FINAL_AUDIT.md) for outcome counts, complete requirement coverage, fixes and source constraints.
