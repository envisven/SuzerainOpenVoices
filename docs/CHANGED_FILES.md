# Budget compound-layout correction

Modified in this repair:

- `src/sordland/layout/PanelLayout.java` — compound panel sizing, per-ministry vertical choice stacks, local edge routing, no synthetic completion side-rail.
- `src/sordland/graph/PanelGraphBuilder.java` — hidden panel entry junction and shorter choice-bundle titles.
- `src/sordland/ui/TextMeasurer.java` — compact width for runtime panel choice bundles only.
- `tests/sordland/RuntimeChecks.java` — compact-width and same-column stacking regressions.

---

# Runtime integration changed files — 2026-09-21

Modified baseline files:

- `README.md`
- `docs/CHANGES.md`
- `docs/DATA_DISCOVERY.md`
- `docs/SOURCE_ACCOUNTING.md`
- `docs/VALIDATION.md`
- `src/sordland/Main.java`
- `src/sordland/data/Domain.java`
- `src/sordland/data/Loader.java`
- `src/sordland/graph/ActorProjection.java`
- `src/sordland/graph/CampaignGraphBuilder.java`
- `src/sordland/graph/DialogueGraphBuilder.java`
- `src/sordland/graph/Graph.java`
- `src/sordland/graph/RootedCampaignGraphBuilder.java`
- `src/sordland/graph/Semantics.java`
- `src/sordland/graph/TypeProjection.java`
- `src/sordland/graph/TypeSelection.java`
- `src/sordland/layout/LayoutEngine.java`
- `src/sordland/layout/RootedCampaignLayout.java`
- `tests/sordland/AllTests.java`
- `docs/CHANGED_FILES.md`

Added files:

- `scripts/test-runtime-ui.sh`
- `src/sordland/data/runtime/RuntimeDatabase.java`
- `src/sordland/data/runtime/RuntimeDatabaseLoader.java`
- `src/sordland/graph/PanelGraphBuilder.java`
- `src/sordland/graph/RuntimeGraphBuilder.java`
- `src/sordland/layout/PanelLayout.java`
- `tests/sordland/RuntimeChecks.java`
- `tests/sordland/RuntimeVisualChecks.java`
- `data/runtime/entity_catalog.json` and all 162 supplied `entity_data_manager/*.json` files.
- `docs/runtime-validation/`: test logs and actual JavaFX Budget screenshots.
- `docs/RUNTIME_INTEGRATION.md`: implementation and limitations report.

No original source data, dumper code, existing run/build scripts, IntelliJ files, or original regression assertions were changed.


---

## Preserved baseline documentation (historical)

# Changed files

## This final completion pass

Compared byte-for-byte with the pre-edit snapshot of the authoritative work/run1 project. Generated classes are excluded.

- `README.md`
- `docs/CHANGED_FILES.md`
- `docs/CHANGES.md`
- `docs/DATA_DISCOVERY.md`
- `docs/FINAL_AUDIT.md`
- `docs/SOURCE_ACCOUNTING.md`
- `docs/VALIDATION.md`
- `src/sordland/Main.java`
- `src/sordland/data/Loader.java`
- `src/sordland/graph/CampaignGraphBuilder.java`
- `src/sordland/layout/RootedCampaignLayout.java`
- `src/sordland/ui/IgnoredDataView.java`
- `src/sordland/ui/Theme.java`
- `tests/sordland/AllTests.java`
- `tests/sordland/CampaignLayoutChecks.java`
- `tests/sordland/LoaderChecks.java`
- `tests/sordland/SourceAccountingChecks.java`
- `tests/sordland/TypeProjectionChecks.java`

## Entire delivered overhaul versus the supplied ZIP

- `README.md`
- `docs/CHANGED_FILES.md`
- `docs/CHANGES.md`
- `docs/DATA_DISCOVERY.md`
- `docs/FINAL_AUDIT.md`
- `docs/SOURCE_ACCOUNTING.md`
- `docs/VALIDATION.md`
- `src/sordland/Main.java`
- `src/sordland/data/Domain.java`
- `src/sordland/data/Loader.java`
- `src/sordland/graph/CampaignGraphBuilder.java`
- `src/sordland/graph/DialogueGraphBuilder.java`
- `src/sordland/graph/Graph.java`
- `src/sordland/graph/NewsGraphBuilder.java`
- `src/sordland/graph/RootedCampaignGraphBuilder.java`
- `src/sordland/graph/TypeProjection.java`
- `src/sordland/graph/TypeSelection.java`
- `src/sordland/layout/RootedCampaignLayout.java`
- `src/sordland/ui/ActorFilterControl.java`
- `src/sordland/ui/GraphCanvas.java`
- `src/sordland/ui/IgnoredDataView.java`
- `src/sordland/ui/TextMeasurer.java`
- `src/sordland/ui/Theme.java`
- `src/sordland/ui/TypeFilterControl.java`
- `tests/sordland/AllTests.java`
- `tests/sordland/CampaignLayoutChecks.java`
- `tests/sordland/LoaderChecks.java`
- `tests/sordland/NewsChecks.java`
- `tests/sordland/RootedCampaignChecks.java`
- `tests/sordland/SourceAccountingChecks.java`
- `tests/sordland/SourceClassificationChecks.java`
- `tests/sordland/ThemeTypographyChecks.java`
- `tests/sordland/TypeProjectionChecks.java`

Validation logs and rendered screenshots under `docs/validation/` are new generated evidence. All three `data/` files are unchanged.
