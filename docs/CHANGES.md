# Budget compound-layout correction — 2026-09-21

- Reworked runtime decision panels as compact compound blocks from the global dialogue layout's point of view. The twelve Budget options no longer form one enormous horizontal row.
- Budget ministries remain aligned horizontally, while Increase / Maintain / Decrease bundles stack locally inside each ministry column. Internal choice routing stays inside the panel container.
- The panel entry is now a hidden top-centre junction and the panel completion is a hidden bottom-centre junction, so the outer group has one clean incoming and one clean continuation route without the previous side-rail.
- Runtime panel choice cards use a narrower measured width without changing ordinary dialogue-effect cards.
- Added compact-layout regression checks; the core runtime suite passes 5,234,664 checks, including all 286 enriched dialogue graphs and exact Budget mechanics/continuation.

---

# Runtime integration — 2026-09-21

- Added a reusable catalog-driven runtime loader and exact name/Id/enable-variable indexes. Preferred logical members load once; only StoryPack_Main records enter the runtime entity index. All raw preferred-source envelopes remain inspectable.
- Kept the original dialogue and GameFlow pipelines authoritative. No dumper changes, data replacement, build framework or JSON dependency was introduced.
- Added structured `ShowPagedDecisionsPanel` semantics, generic carousel/multiple-choice resolution, explicit unresolved references, existing-style condition gates, and bundled choice instructions plus panel counter/bar effects.
- Added independent PanelGroup/PanelCategory/PanelBranch semantics and a measured panel layout. Actor projection retains panel metadata. Budget has one container, aligned headers, twelve effect bundles and a single completion continuation.
- Added exact ROOTED runtime content attachments and PLAIN catalogue types; new types default off. Decrees attach through explicit AssignedDecreePanel membership, not assumed enactment. GameFlow turn/step instructions are also considered exact source evidence.
- Added a separate Runtime sources inspector and richer conditional-instruction source details. The original Ignored data view and its smoke assertions are unchanged.
- Quarantined the Main-tagged Rizia journal anomaly from gameplay while preserving its raw source and catalog count.
- Added headless integration tests, JavaFX Budget visual checks, screenshots and reproducible validation logs. All original test classes/assertions remain unchanged; AllTests runs legacy linear-chain checks with the optional runtime absent, then the runtime-aware suite with it present.

---

## Preserved baseline documentation (historical)

# Completed overhaul and final audit repairs

## Run 1 / Run 2 functionality preserved

- Independent Types checklist in ROOTED and PLAIN; News defaults off and Condition defaults on. Canonical graphs remain immutable while visibility projection removes cards and preserves alternative routes and original-edge provenance.
- Exact ROOTED News evidence, compact article cards, local effect annotations and full source/article details. Optional dialogue effects are labeled possible; unproven articles stay in PLAIN with diagnostics.
- Explicit TRUE/FALSE activation paths, immediate skip reconvergence, and unresolved notices for unknown turn-condition false destinations.
- Alternating turn bands and measured bold vertical TransitionTitle labels; dark semantic palette and undistorted text metrics.
- Source-based Ignored Data retention, complete raw dialogue fields/links and preserved GameFlow/dialogue navigation, actors, search, details and viewport controls.

## Defects discovered and repaired in the final completion pass

1. Actor-name input was only packaged/test-parsed. Loader now accounts for every present record and retains unmapped names with exact source indices/reasons. It does not invent ActorIDs. Malformed optional actor input raises an explicit load error.
2. Ignored Data was merely filename-sorted. It now has collapsible source-file groups with counts, numeric source ordering, searchable provenance and a single selected raw-record panel suitable for large inputs.
3. ThemeTypographyChecks existed but was not executed. AllTests now invokes it and the new independent SourceAccountingChecks; every Checks class is included.
4. Initial inspection could not establish full type-combination geometry. Added all 64 ROOTED combinations to the main suite, verifying visibility, rectangles, orthogonal routes, hit targets and card/container clearance.
5. Runtime screenshots exposed poor selected TreeView row contrast. Shared CSS now themes tree rows, selection and disclosure arrows explicitly.
6. Newly rebuilt canvases could receive focus while their width was still zero, moving searched/isolated content off the left side. CSS/layout now runs before deferred initial focus and smoke interactions.
7. Rebuilding the turn-choice list unnecessarily could leave its displayed value blank. Unchanged choices are now preserved instead of repeatedly clearing/repopulating the ComboBox.
8. Multiple FALSE skip paths always used the right-hand channel, visually sharing a rail under adjacent conditions. Routes now choose the nearest clear side; the Gasom branches use opposite outer channels and reconverge visibly.
9. GUI automation did not inspect the popup itself, ignored-file group/search UI, PLAIN turn filtering or actual Canvas edge hit-testing. Extended the existing harness to exercise these and save reviewed screenshots. A temporary smoke assertion was corrected to respect stored preferences of categories absent from a selected turn; application semantics were preserved.
10. PLAIN News cards omitted their database identity; added the compact NEWS title/identity format and real-data assertions.
11. Arrow endpoints with no dialogue pointer appeared as an empty name with `[null]`. The inspector now shows the progression-junction label, exact visual identity and available endpoint source metadata.
12. Auxiliary Data notes/error dialogs now receive the shared dark theme.
13. README and data/validation/change documents described obsolete filtering, source retention and rendering. Replaced those claims with current behavior and measured verification results.

No broad graph or application rewrite was made. The original ZIP and the authoritative working project remain preserved. See [FINAL_AUDIT.md](FINAL_AUDIT.md) for exact completion-pass files, the requirement matrix, commands, results and remaining source constraints.

## Budget panel vertical layout correction

- `Panel_Budget` is now a true vertical separator in dialogue layout: dialogue before the panel enters the compound block at top-centre and the exact source continuation leaves bottom-centre.
- The four funding categories are stacked downward instead of consuming one horizontal row.
- Within each funding category, the three choices fan downward into a left/centre/right row (`Decrease | Maintain | Increase`).
- Each choice remains one bundled effect card; category sections are independent and are not chained to each other.
- The local panel geometry is excluded from global DAG ranking, so the runtime panel does not create direct Part A -> Part B bypass routes.

## Budget allocation final convergence layout — 2026-09-21

- Replaced the previous vertical-ministry attempt with the intended horizontal ministry enclosure: `Health | Law Enforcement | Education | Military`.
- All incoming Part A routes now gather at the enclosure's top-middle.
- Each ministry branches downward into three distinct option cards (`Decrease | Maintain | Increase`), each followed by its own immediate effect card.
- All twelve effect cards visually reconverge into one visible `Budget chosen` node.
- The exact source continuation to Part B leaves only from `Budget chosen`; no Part A -> Part B bypass is introduced.
- The global dialogue layout still treats the complete Budget micro-tree as one macro block, so internal choices do not contaminate global DAG ranking.
- Actor filtering no longer mistakes runtime panel-choice cards for player speech.
- Core runtime regression: 5,379,450 checks passed.

## Budget gateway topology correction

- Reworked `Panel_Budget` as a true Part A -> Budget -> Part B gateway instead of inserting the panel into one global layered dialogue layout.
- Verified the exact source cut: `26:158 -> 26:209 -> 27:1 -> 27:81`, with `26:209` the only cross-conversation progress exit from conversation 26.
- The complete ministry/choice/effect/merge structure is now enclosed by one rectangle.
- Pre-budget loop nodes are partitioned with full source reachability (including back references), preventing legitimate Part A loop branches from being laid out below the Budget panel.
- The only external panel input is the upper-middle boundary; the only source continuation leaves through the lower-middle boundary after `Budget chosen`.
- Added regression checks for the exact panel predecessor/successor topology and for geometric non-bypass of the Budget gateway.
