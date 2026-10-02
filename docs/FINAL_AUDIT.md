# Final completion audit

Authoritative project: the current repository checkout.

Continued the current implementation; did not restore an older ZIP or rewrite the overhaul. Reviewed the complete Run 1 (A–Q), Run 2 (A–S), final completion card (0–16) and zero-assumption audit (17 A–F). A pre-edit source/test/docs snapshot is preserved in the workspace audit directory. No reference-screenshot archive was supplied with these inputs; theme acceptance is based on the specified dark/contrast/semantic criteria and actual rendered output.

## Requirement matrix

PASS means code inspected plus the named behavior checked. Initial UNVERIFIED denotes requirements whose runtime/behavioral evidence had not yet been established in this completion pass. No row is passed merely because a class compiled. Repeated requirements across the cards are consolidated below with their complete behavioral subcriteria.

Evidence: **T** = complete principal test suite; **G** = successful JavaFX smoke with reviewed actual rendered PNGs; **S** = independent source inventory/control-flow inspection; **D** = final document/source discrepancy review.

| Requirement (Run 1 / Run 2) | Initial | Final | Evidence |
| --- | --- | --- | --- |
| Continue current project, preserve prior work; targeted changes (M–N / A) | PASS | PASS | Exact path and pre-edit snapshot; file-level diff |
| Preserve source truth; never infer causality from adjacency, chronology or similar names (principles / P) | UNVERIFIED | PASS | S; NewsChecks, RootedCampaignChecks and dialogue fixtures |
| Checklist-style independent Types selections comparable to Actors (A / B) | UNVERIFIED | PASS | G: actual popup screenshot, observable selection callbacks |
| Types X / Y caption updates; multiple selections (A / B,Q) | UNVERIFIED | PASS | G: default, arbitrary selections, only-News screenshots |
| All applicable types selected by default except News (A / B) | UNVERIFIED | PASS | T TypeProjectionChecks; G default startup |
| Condition independent/on; News does not preserve hidden Conditions (A / B) | UNVERIFIED | PASS | T; G only-News and Condition-off paths |
| No ROOTED campaign Dialogue fragment category (A / B) | UNVERIFIED | PASS | T and G; fragments remain accessible in PLAIN/dialogue |
| Same primary filter bar in ROOTED/PLAIN; actors only in dialogue (J / B,K) | UNVERIFIED | PASS | G mode/dialogue transitions |
| Hidden type cards disappear, not opacity filtering (A,D / B,C) | UNVERIFIED | PASS | T projected node sets; G rendered views |
| Single and multi-node hidden chains preserve connectivity (A,D / C) | UNVERIFIED | PASS | T TypeProjectionChecks chain/provenance fixtures |
| Hidden Condition and hidden alternatives preserve distinct TRUE/FALSE paths (D / C,E) | UNVERIFIED | PASS | T branching fixtures and 64 combinations; G |
| Projection does not mutate canonical/source graph (A,D / C,P) | UNVERIFIED | PASS | T identity/provenance checks; G canonical identity assertion |
| Neutral hidden branch/merge/loop anchors retained when needed (D / C) | UNVERIFIED | PASS | T TypeProjectionChecks and ActorFilterChecks |
| Only Conversation, only News, News+Condition combinations (O / B) | UNVERIFIED | PASS | T all 64 combinations and named cases; G only-News |
| Everything except Condition; except Conversation; default News-off; arbitrary combinations (O / B) | UNVERIFIED | PASS | T all 64; G Condition-off / defaults |
| Type preferences survive compatible mode/turn changes and Back (A / M) | UNVERIFIED | PASS | T TypeSelection; G mode/turn/details/ignored/dialogue Back |
| ROOTED News exists and can be enabled; off by default (B / D) | UNVERIFIED | PASS | T NewsChecks; G |
| Exact enable-variable/EnableNews proof only; no fuzzy links (B / D,P) | UNVERIFIED | PASS | Source logic review; adversarial NewsChecks fixtures |
| Unsupported/control-flow/ambiguous/call-containing blocks cannot fabricate News evidence (B / D,P) | UNVERIFIED | PASS | T proof rejection and later-write invalidation fixtures |
| News placed locally with its exact enabler, not publication timing (B / D,N) | UNVERIFIED | PASS | T attachment/turn geometry; G News screenshot |
| Optional dialogue/choice News labeled possible, not guaranteed completion (B / D,P) | UNVERIFIED | PASS | T evidence provenance; G POSSIBLE NEWS EFFECT cards |
| News graph cards compact title/database identity, no article body (B / O) | PARTIAL | PASS | ROOTED inspected; added PLAIN identity/type label + tests; G |
| Full article, newspaper, raw enable/source metadata accessible (B / D,O) | UNVERIFIED | PASS | T item/raw retention; G News detail and expanded metadata |
| Unproven News retained PLAIN plus truthful diagnostics (B,I / D,J) | UNVERIFIED | PASS | T all 1,436 articles; source proof diagnostics |
| TRUE/FALSE branch labels and same-step activation skip route (C / E) | UNVERIFIED | PASS | T source semantics fixtures; G Gasom branches |
| Immediate reconvergence remains visible after projection (C,D / E,N) | UNVERIFIED | PASS | T distinct route point lists and projection fixtures |
| Unknown turn false destination is explicitly unresolved, never invented (C / E,P) | UNVERIFIED | PASS | T synthetic turn-condition fixture; builder inspection (supplied turn conditions are empty) |
| Same condition color family, practical height, readable predicate (C / E) | UNVERIFIED | PASS | ThemeTypographyChecks; G |
| No misleading shared TRUE/FALSE routing across adjacent cards (C / E,N) | PARTIAL | PASS | Nearest outer skip lanes; T all combinations; G reviewed Gasom image |
| Full-width alternating ROOTED turn bands, continuous graph (E / F) | UNVERIFIED | PASS | T sectors/order; G distinct Turn 1/2 backgrounds |
| Left vertical TransitionTitle labels; TURN N fallback (E / F) | UNVERIFIED | PASS | T metadata/title fixture; G |
| Larger bold high-contrast labels, consistent size (E / F,G) | UNVERIFIED | PASS | Exact font tests and contrast ratios; G |
| One-line labels, measured ellipsis, full text tooltip/metadata (E / F,O) | UNVERIFIED | PASS | T Unicode/font/ellipsis checks; Canvas tooltip code; G |
| Legacy duplicate horizontal ROOTED label removed (E / F) | UNVERIFIED | PASS | Rendering source inspection, G, D |
| Dark coherent canvas, toolbar/status, bands and controls (F / G) | UNVERIFIED | PASS | G reviewed startup, both modes, popup and details |
| Semantic node fills remain distinct, including Condition/News (F / G) | UNVERIFIED | PASS | T palette checks; G |
| Selected/search states, node borders, edges and boolean labels visible (F / G) | PARTIAL | PASS | Tree selection CSS repaired; G selected/search/edge screenshots |
| Main/muted metadata text contrast on semantic backgrounds (F / G) | UNVERIFIED | PASS | T contrast >=4.5; turn label >=7; G |
| Details, source inspector and dialogs use shared dark styling (F / G) | PARTIAL | PASS | Shared theme applied to dialogs; G details/ignored/data-notes |
| No horizontal scaling/constrained fillText compression (G / H) | UNVERIFIED | PASS | Rendering source search; exact font metrics; G |
| Actual family/weight/size used for measurement and painting (G / H) | UNVERIFIED | PASS | ThemeTypographyChecks + TextChecks and source review |
| Card titles wrap within bounded width; glyphs fit, no major overflow (G / H,O) | UNVERIFIED | PASS | T measured lines; G startup/dialogue/News/details |
| Ignored Data button replaces old checkbox (H / I,K) | UNVERIFIED | PASS | UI/source review; G |
| Ignored records actually grouped by file, usable at scale (H / I) | FAIL | PASS | Tree groups/counts/search; G collapsed groups and selected record |
| File, collection, index/path, identity, reason, raw data inspectable (H / I) | UNVERIFIED | PASS | T retained fields; G source inspector |
| Supported News/conditional instructions not dumped as ignored (H,I / I,J) | UNVERIFIED | PASS | T classification + PLAIN graph |
| Every supplied source file/category accounted (I / J) | FAIL | PASS | S; actor-name loading/retention added; SourceAccountingChecks |
| Unsupported/incomplete readable content retained with reasons (I / J) | UNVERIFIED | PASS | S control-flow audit; SourceClassificationChecks adversarial fixtures |
| No destructive dialogue field/link whitelist; complete raw entries retained (I / J) | UNVERIFIED | PASS | Independent equality checks of 83,454 entries/868,521 fields/104,553 links |
| Corrupt basic structure/contradictory identity explicit errors (I / J) | UNVERIFIED | PASS | LoaderChecks, JsonChecks, GameFlowLoaderChecks |
| Unresolved GameFlow fragments graph-visible with exact identity (I / J,P) | UNVERIFIED | PASS | T missing/ambiguous/incomplete reference fixtures and layout |
| Unknown dialogue commands/links remain inspectable/unresolved (I / J,P) | UNVERIFIED | PASS | T semantics/dialogue/source fixtures; source inspectors |
| PLAIN catalogue loads, bands/News/type filtering operate (K / K) | UNVERIFIED | PASS | T graph/layout; G PLAIN/only-News |
| PLAIN search/turn filter and item/Bill details (K / K,L) | UNVERIFIED | PASS | G Turn 1, hidden search and Bill detail |
| Dialogue opening, exact pointers, reconvergence and loops preserved (K / K) | UNVERIFIED | PASS | T all 286 complete graphs and pointer fixtures; G dialogue |
| Actor filtering, actor colors and mechanics preservation (K / C,K,M) | UNVERIFIED | PASS | ActorFilterChecks; G speech-off and colors |
| Search cannot resurrect hidden News/Condition/other types (L / L) | UNVERIFIED | PASS | T projection; G ROOTED and PLAIN hidden-match searches |
| Empty search restores catalogue/highlights; visible match navigation (L / L) | UNVERIFIED | PASS | G clear search and Gasom selection; search code inspected |
| Back restores query, types, mode, turn, same view/viewport (A,K / M) | UNVERIFIED | PASS | G same-object Back assertions; ViewportChecks |
| Metadata expansion/details, source and edge inspectors work (K / Q) | UNVERIFIED | PASS | G detail/expansion callbacks plus actual Canvas hit-testing screenshot |
| Zoom, Fit, Readable and selection remain functional (K / Q) | UNVERIFIED | PASS | ViewportChecks; G scale changes and 100% assertion |
| Card/group collisions and orthogonal route clearance (O / N) | UNVERIFIED | PASS | CampaignLayoutChecks: expanded/all turns/64 type combinations; G |
| Group containers enclose children tightly without clipping (O / N) | UNVERIFIED | PASS | T 14-unit padding/hit targets; G |
| Turn labels do not collide with cards during panning (E,O / N) | UNVERIFIED | PASS | Reserved clipped side rail; G |
| News annotation geometry/turn containment, bounded card widths (B,O / N,O) | UNVERIFIED | PASS | T all attached effects/articles and selection combinations; G |
| Clean latest-source build and complete test entry point (O,Q / Q) | PARTIAL | PASS | Clean classes removal, scripts; ThemeTypographyChecks and every Checks class integrated |
| App starts; GUI smoke actually succeeds (O,Q / Q) | UNVERIFIED | PASS | Mac graphical execution, exit 0; reviewed actual PNGs |
| Documentation current; final stale-behavior search (Q / R,S; completion 12,17E) | FAIL | PASS | README/CHANGES/DATA_DISCOVERY/VALIDATION corrected; D |
| Independent final matrix, accounting, test inventory, discrepancy review (Q / R,S; completion 17) | PARTIAL | PASS | This document; SOURCE_ACCOUNTING and VALIDATION |
| Final runnable output from current work; preserve source copy (Q / S; completion 16) | UNVERIFIED | PASS | Copied current project, ZIP content/hash validation and fresh-extraction checks |

## Final test inventory

Every `tests/sordland/*Checks.java` is called by `AllTests.java`: ActorFilterChecks, CampaignLayoutChecks, DataChecks, DialogueChecks, EdgeChecks, GameFlowLoaderChecks, JsonChecks, LayoutChecks, LoaderChecks, NewsChecks, RootedCampaignChecks, SemanticChecks, SourceAccountingChecks, SourceClassificationChecks, TextChecks, ThemeTypographyChecks, TypeProjectionChecks and ViewportChecks. DialogueChecks/SourceClassificationChecks/TypeProjectionChecks also run their real-data entry points. No Checks file is accidentally unused. TestSupport is assertion infrastructure, not a missing suite.

## Remaining source constraints

- Actor-name list positions do not prove dialogue ActorIDs. The 103 source names remain visible in Ignored Data; source dialogue labels provide speaker names.
- No game-state interpreter is supplied. Conditional/optional effects describe source possibilities, not feasible playthrough outcomes. GameFlow normally establishes progression, not unique event causality.
- Unproven News stays accessible in PLAIN. Unknown turn-condition false destinations stay unresolved. Neither is filled with guessed topology.
- No reference screenshot archive was supplied; no visual-copy claim is made.

No known requested implementation omission remains. Graphical verification uses the existing smoke automation on a real Mac JavaFX display, not an assertion of exhaustive manual clicking of every source entry. The native UI connector could not bind to the unbundled Java executable, so reviewed JavaFX scene snapshots and the smoke handlers supplied the GUI evidence. This did not prevent successful GUI verification.

## Exact files, defects and commands

[CHANGED_FILES.md](CHANGED_FILES.md) lists every changed source/test/document file for this completion pass and for the complete overhaul versus the supplied ZIP. [CHANGES.md](CHANGES.md) maps discovered defects to fixes. [VALIDATION.md](VALIDATION.md) records commands, counts, build/runtime results and evidence.
