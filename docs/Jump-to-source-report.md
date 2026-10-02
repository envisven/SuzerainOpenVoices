# Shared Jump to source implementation

Continued from the current target-corrected project. Added one application-scoped **Jump to source** checkbox state to both existing toolbars. It starts OFF each launch; either checkbox immediately updates the other. OFF retains the existing click handlers.

With the toggle ON, single clicks use exact dialogue keys, entity IDs, runtime locations, or retained generated-choice token identities. No visible-text search or approximate matching is used. Dialogue navigation opens the containing conversation, selects and focuses its exact entry, and foregrounds the main viewer. Entity sources use the existing source/detail views. Missing identities leave the current view unchanged. Multiple references retain their existing order; the first supported exact source is used.

The inspector remains open without rebuilding its analysis, grid, selection, query, controls or scroll position. It is an independent desktop window so macOS permits the main viewer to come forward; closing the main window still closes the inspector. Source mode OFF continues to show only the controls/search and spreadsheet.

Only interaction wiring and a bounded choice-token provenance cache were added. Numeric route semantics, Compact reduction, factoring, target reachability, source reconstruction and game data are unchanged. All 166 files under data/ are byte-identical to the base ZIP.

## Validation

- Full headless suite: **13,948,753 checks passed**.
- New shared-navigation desktop suite: **26 checks passed**, including default OFF, synchronization both ways, normal OFF clicks, exact Agnolia entry 77:159, Koronti entry 13:136, containing event, selected/readable node, foreground window, inspector retention, immediate OFF restoration, bills, runtime locations and invalid-reference handling. A copied equal-text string cannot impersonate the retained choice provenance.
- Existing desktop suites passed: variable picker, numeric grid, runtime Budget, route rendering, final regressions (4,510 checks), Compact (6,541 checks), and target reachability (2,149 checks).
- Inspected captured screenshots for the highlighted Koronti quote and retained inspector spreadsheet.
- **Validation limitation:** the existing inspector-shell suite timed out at phase 10, its native Robot outside-click popup-dismissal check. The unchanged base project reproduces the same timeout. Diagnostic pointer movement reached the button but delivered no observed mouse-press event to the scene. All earlier assertions in that suite passed; native outside-click dismissal could not be confirmed in this desktop session. The test remains unchanged, with no skipped assertion or weakened expectation.

Logs and screenshots are included in docs/jump-validation/. Run scripts/test.sh and scripts/test-jump-ui.sh using the project's existing JavaFX-enabled JDK setup. The ZIP includes complete source, data, IDE setup, scripts, tests and documentation; generated build output is omitted.
