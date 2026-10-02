# Sordland Tree Viewer runtime integration

The supplied working project is the baseline. The complete project includes original JSON inputs, all runtime entity member files and catalog, source changes, tests, documentation, Unix/Windows scripts, and IntelliJ configuration. No dumper code was touched.

## Implementation

- **Data layer:** RuntimeDatabaseLoader follows entity_catalog logicalKey/preferredForCoverage/outputFile selection; RuntimeDatabase provides exact name, Id and IsEnabledVariable indexes plus raw source envelopes/provenance. Runtime absence is graceful.
- **Consumed collections:** all 88 preferred logical collections load for indexing/source inspection. Gameplay support uses PagedDecisionPanelsData; both CarouselChoice and MultipleChoice page/option collections; PoliciesData; SituationsData; ReportsData; DecreesData; JournalEntriesData; TokenStatusEffectsData. Supporting OneTimeDecreesPanelData, AllConversationsData and ConditionalInstructionData provide exact references/details. The complete 162-member input set is packaged.
- **New campaign types:** Policy, Situation, Report, Decree, Journal, Token status, Decision panel. All default off. Original News and core behavior remain intact.
- **Resolved structured command:** `ShowPagedDecisionsPanel("exact NameInDatabase")`, including Panel_Budget and all other supplied Main panels. It has a dedicated semantic kind. Missing references become notices with raw source details. Both page families and their availability conditions are supported.
- **Budget:** one neutral PanelGroup; four aligned Health / Law Enforcement / Education / Military headers; three branches in Increase / Maintain / Decrease order; exactly twelve bundled effect cards. Each combines its instruction with counter/bar consequences without losing their distinct provenance. One panel-completion port retains the original single outgoing link from 26:209 to 27:1. No twelve-way exclusive choice, ministry sequence, or 3^4 expansion is invented.
- **ROOTED:** Policy, Situation, Report, Journal and Token status attach through exact supported literal command arguments or authoritative IsEnabledVariable writes. Decrees appear through exact ShowOneTimeDecreesPanel/AssignedDecreePanel membership, labeled possible panel content rather than guaranteed enactment. Item begin/end, options, reachable dialogue entries, and turn/step instructions supply evidence. Runtime attachments use local occurrences and retain canonical source identities.
- **PLAIN/index-only:** eligible records of all new gameplay types remain accessible even without proven ROOTED relationships. Decision panels open their structured mechanics from PLAIN. Unattached individual records stay PLAIN; configuration/support collections stay indexed/source-inspectable. The richer conditional-instruction source is available alongside original details, without fabricating additional scheduling edges.
- **Changed files:** see CHANGED_FILES.md for the full inventory. Production additions are RuntimeDatabase, RuntimeDatabaseLoader, PanelGraphBuilder, RuntimeGraphBuilder and PanelLayout; integration changes cover Domain/Loader, Graph/Semantics, dialogue/campaign builders, projections/filter defaults, layout dispatch and Main UI.

## Validation

Baseline: 6,671,942 checks passed. Final: **11,869,167 headless checks passed**. Existing desktop smoke suite and new actual-JavaFX Budget canvas checks both passed. Original assertions were preserved; legacy linear-chain tests and runtime-aware branch tests are separate. Both paths audit all 286 conversation graphs. Logs and inspected screenshots are in runtime-validation/.

Runtime count targets all match: panels 8; carousel pages/options 17/55; multiple-choice pages/options 24/64; policies 164; situations 228; reports 862; decrees 19; journals 561; token effects 106. The three old JSONs, existing scripts and IntelliJ configuration are unchanged.

## Source limitations retained explicitly

- One Main-tagged journal record (`Turn05_Security_Iza_ReduceSOAuthority`) has a Rizia path and RiziaDLCSupport enable variable. It remains in the source count/index but is quarantined from gameplay, leaving 560 eligible journal records.
- Non-literal or ambiguous AddReport/AddJournalEntry/status references, unsupported Lua control constructs and unknown commands are not guessed. Their original source survives; unresolved references produce diagnostics. Commands without supported argument semantics retain the existing unresolved presentation.
- The viewer displays source possibilities, not an evaluated game state. A decree panel membership does not prove enactment. Shared variables alone do not create campaign arrows.
- Serializer references are preserved, not treated as game identities. Unsupported configuration and unproven schedule relationships remain in source inspection.

Run with the supplied IntelliJ configuration or `./scripts/run.sh` (`scripts\run.cmd` on Windows), using the existing Liberica FULL JDK 25 setup. No Maven or Gradle is required.
