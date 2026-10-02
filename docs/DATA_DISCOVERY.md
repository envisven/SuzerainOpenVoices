# Runtime data discovery — 2026-09-21

`entity_catalog.json` lists 162 reflected members in 88 case-insensitive logical groups. The loader selects exactly one `preferredForCoverage` member per logicalKey and follows its `outputFile`; numbered filenames are never hardcoded. Missing or ambiguous preferred members produce diagnostics. The complete catalog and preferred envelopes preserve unsupported records/fields and serializer reference metadata.

| StoryPack_Main logical collection | Records |
|---|---:|
| PagedDecisionPanelsData | 8 |
| CarouselChoicePageData | 17 |
| CarouselChoiceOptionData | 55 |
| MultipleChoicePageData | 24 |
| MultipleChoiceOptionData | 64 |
| PoliciesData | 164 |
| SituationsData | 228 |
| ReportsData | 862 |
| DecreesData | 19 |
| JournalEntriesData | 561 |
| TokenStatusEffectsData | 106 |

Supporting collections include OneTimeDecreesPanelData, ConditionalInstructionData (four Main-tagged records), AllConversationsData for exact completion references, and all other preferred collections as source/index data. Old AllConversationsData, Decisions, Bills, News and GameFlow remain authoritative for the mature renderer.

Budget's source page order is Healthcare, Security, Education, Military. Each page source orders Maintain before Increase, so the display explicitly reorders these known choices to Increase / Maintain / Decrease while retaining the raw list. The Security page's choice variables use LawEnforcement. GovernmentBudget deltas come from PanelCounterVariable plus option PanelCounterIncrement, independently of Instruction.

Data conflict: JournalEntriesData includes `Turn05_Security_Iza_ReduceSOAuthority` tagged only StoryPack_Main, but its path is `Rizia/Journal Entries/Turn05` and IsEnabledVariable is `RiziaDLCSupport.Journal_Turn05_Security_Iza_ReduceSOAuthority`. It remains in the 561 source count and raw index, with an explicit diagnostic; gameplay rendering excludes it. Runtime News also includes shared articles under Rizia paths; runtime News is not used to replace the established News pipeline.

`PagedDecisionPanel_PromisesPanel.ConversationOnFinish` names `Turn01_Start_Inauguration`. Completion fields produce exact reference cards (or explicit unresolved notices), never substituted or fabricated dialogue pointers. Other supplied Main panels have empty completion fields. Budget's dialogue source supplies exactly 27:1.

---

## Preserved baseline documentation (historical)

# Supplied data: findings and limits

This report describes the supplied newest entity, Sordland conversation and actor-name JSON files, packaged under canonical names in `data/`. The application reads them without modification. Only `StoryPack_Main` is used for campaign GameFlow; no Rizia view or execution model is implemented.

## JSON representation

All three roots are objects. Most logical lists are serialized as dictionaries with numeric string keys (`"0"`, `"1"`, …) plus a `_type` marker. The loader orders those entries by their numeric keys and ignores the type marker as a list element. Conventional JSON arrays are also accepted. IDs are read from their explicit fields, not reconstructed from list position.

The parser reads UTF-8 through a buffered character reader, avoiding a second 123 MB input string. JSON structures are immutable. It rejects malformed JSON, duplicate object keys, trailing data, invalid collection shape, invalid integer IDs, duplicate dialogue identities, and links whose declared origin disagrees with their containing entry.

## Campaign catalogue and campaign filtering

| Collection | Sordland | Excluded Rizia | Role |
| --- | ---: | ---: | --- |
| `AllConversationsData` | 151 | 113 | Dialogue-bearing campaign entities |
| `AllBillsData` | 22 | 4 | SIGN/VETO interactions |
| `AllDecisionsData` | 56 | 254 | Source-defined option interactions |
| `conditionalInstructionData` | 4 | 364 | Supported conditional calculations (PLAIN) |
| `NewsData` | 1,436 | 40 | Supported News (PLAIN and proven ROOTED annotations) |

There are 229 Sordland interactive catalogue entities and 1,440 additional supported records (the internal storage field is still named `ancillary`). No separate decree collection is supplied. A decree can appear as a conversation or a source command such as `ShowOneTimeDecreesPanel`; that is not evidence for an absent decree option schema.

All 229 interactive items have `Path` under `Sordland/` and `AppBundleProperties.StoryPacks` containing `StoryPack_Main`. Explicit Rizia paths or dialogue references are excluded. For records without a campaign path, the loader can use unambiguous `StoryPack_Main` membership while rejecting `StoryPack_Rizia`. All 775 excluded catalogue records in these files are Rizia records and are retained in Ignored data with a scope reason. The Rizia GameFlow is also retained. Conversation titles must begin with `Sordland/`.

Catalogue metadata includes `NameInDatabase`, `Path`, `AssignedTokenProperties`, and `StoryFragmentProperties`. The latter contains `StoryFragmentCondition`, `OnStoryFragmentBeginInstruction`, and `OnStoryFragmentEndInstruction`.

## Turn membership and endings

Interactive catalogue items have **no numeric Turn field**. Their `NameInDatabase` normally contains `TurnNN`. The loader records that as the turn's provenance. Dialogue references and conversation titles also encode turns as `Sordland/TurnNN/...`.

All nine catalogue names beginning `Turn_Endings_` resolve to explicit **Turn 11** conversation paths. They must therefore appear in Turn 11, not an invented end-turn band. Their graphs remain accessible:

| Entity suffix | Conversation ID |
| --- | ---: |
| `A_ElectionSpeech` and `A_ElectionSpeech_MaroonSquare` | 244 |
| `A_ImpeachmentTrial` | 246 |
| `Personal_Exile` | 255 |
| `Personal_Retirement` | 254 |
| `SnO_CoupTrial` | 243 |
| `SnO_RumburgWarLost` | 242 |
| `SnO_RumburgWarWin` | 245 |
| `SnO_WorldWar` | 256 |

An actual inconsistency is retained and reported: `WIP_Turn04_EnT_RuralDevelopment` references `Sordland/Turn01/EnT_EconomicOverview` (conversation 1). The catalogue item stays in its named Turn 4, with the conflicting dialogue turn visible in diagnostics. It is not silently conflated with the ordinary Turn 1 economic overview.

Conversation-only paths with no numeric turn, including `Sordland/Prologue` and numerous `Sordland/Epilogue...` graphs, remain explicitly unplaced. Their name does not prove a numeric turn. `NewsProperties.TurnNo` is a real numeric turn field and is used for news. Conditional instructions have schedule/check metadata rather than ordinary event turn membership.

## GameFlow progression

`GameFlowData` contains a `StoryPack_Main` entry with **11 turns, 132 steps and 227 fragment occurrences**. The numbered dictionaries preserve source collection indices; each turn’s displayed number is its source index plus one. No catalogue sort, title similarity or dialogue link supplies campaign ordering.

Each fragment resolves by exact `NameInDatabase` against loaded Sordland entities. Conversation entities in turn resolve through exact `ConversationProperties.Dialogue` → `Conversation.Title` identity. Absent or ambiguous names remain explicit unresolved occurrences with diagnostics. The supplied 227 fragment references all resolve. Two catalogue entities outside this GameFlow remain available in PLAIN rather than being appended as invented campaign steps.

The first source step contains only `Turn01_Start_Inauguration`, so the synthetic START connects immediately to it. Turn 3 Step 4 contains `Turn03_Decision_Extraction`; Step 5 contains `Turn03_A_AddressTheProtestors` then `Turn03_Personal_HelicopterEscape`. Its decision options set the corresponding exact Boolean flags tested by those next-step activation conditions. This supports the specific local decision-to-condition branches. They reconverge before Step 6 (Late Briefing and Stability Order).

GameFlow establishes progression between steps, but generally does not establish that a particular event causes a particular event in the following step. Neutral junctions are the safe default. Direct branches require strictly parsed choice writes and conditions at adjacent steps; distant shared flags never become edges. Independently conditional candidates do not become an unconditional cross-product. Only mechanically complementary Boolean predicates share a true/false condition block; unsupported expressions remain separate.

Several unconditional fragments in one step occupy the same progression level without an asserted relative completion order. Their gray container is purely visual. Conditional fragments at that step keep separate condition branches. Empty steps, turn conditions, `OnTurnStartInstruction` and `OnStepStartInstruction` retain their indices and raw metadata; no scripts are executed.

Turn titles label alternating background bands, not new roots. Selecting one turn creates a synthetic TURN START. Search highlights/focuses visible cards. Independent Types checkboxes remove cards and project original connectivity through hidden nodes; the canonical graph remains unchanged. PLAIN retains the full flat catalogue and the same Types checklist. Exact source-proven News appears as local ROOTED annotations.

The 249 links between different conversation IDs remain exact dialogue links, including subdialogue calls and returns. They are not promoted to campaign edges. No game-state simulation, inferred remote causality or feasibility solver is introduced.

## Conversation identity, text, and source order

The conversation root contains `conversations`: **286 conversations and 83,454 dialogue entries**. A conversation has `id`, `Title`, and `dialogueEntries`. Every entry has `conversationID` and `id`; its identity is the pair `(conversationID, id)`.

`ConversationProperties.Dialogue` exactly matches `Conversation.Title`. All 151 catalogue references resolve. They reference 149 unique conversations: the two election-speech entities share conversation 244, and the WIP rural-development item shares conversation 1. These distinct catalogue entities retain distinct identities, metadata, and conditions.

The remaining 137 conversation graphs are retained as dialogue-fragment/ending items, giving **366 accessible primary items**. They are not asserted to be independent campaign events.

Each `outgoingLinks` element supplies `destinationConversationID`, `destinationDialogueID`, `originConversationID`, `originDialogueID`, `priority`, and `isConnector`. The loader preserves numeric source order, exact destination identity, priority, and connector status. There are no unresolved link destinations in the supplied dump.

Entry `fields` is another numbered list of `{title, typeString, value}` objects. English spoken/narrated text is normally in the field titled `en`; `Dialogue Text` is the fallback. Player menu text is normally `Menu Text en`, with `Menu Text` as fallback. Base `Sequence` and localized `Sequence en` are both preserved. `Articy Id`, `InputId`, and `OutputId` remain available in entry metadata. The complete original entry, including field wrappers, type descriptors and raw outgoing links, is retained in immutable source metadata. A separate interpretation map supplies display text; ambiguous duplicate titles are not arbitrarily interpreted.

### Canonical visual representation

The dialogue view represents the JSON pointer graph. A source identity `(conversationID, dialogueID)` is materialized once, as its ordered sequence of condition, speech/control and instruction/terminal boxes. Multiple boxes belonging to one entry are parts of that single representation, not duplicate source occurrences.

Each outgoing source link connects the entry's last box to the exact destination entry's first box. Different effects or conditions on incoming paths do not produce copies of the destination. Conditions and effects remain visible, but the viewer does not evaluate game state or claim that every structural path is feasible for a particular playthrough.

Source loops are ordinary back-references to an existing entry. Complete graphs replace the previous semantic-history expansion and continuation pages. There is no calculated route history to show in the inspector. Verification of complete-graph sizes and layout is recorded in [VALIDATION.md](VALIDATION.md).

## Speakers and choices

The supplied `actor_names.json` contains a numbered name list, not an explicit ActorID mapping. For example its list index 4 is Player, whereas dialogue ActorID 5 is Player. All 103 records are loaded and retained individually in Ignored data with the exact index, value and an explicit unsupported-mapping reason. No offset is guessed and working dialogue naming is preserved. No portrait assets are supplied. Speaker names are corroborated across actual dialogue titles of the form `Speaker: "text"`, indexed by `ActorID`. Control labels such as `Jump to:` and script lines containing colons must not become actor names.

The source repeatedly identifies actor 5 as `Player`, actor 6 as `Player_Italic`, and actor 10 as `Narrator`. Both player actors represent responses/actions; narrator is a distinct role. Other examples are 4 `Petr Vectern`, 38 `Lucian Galade`, and 51 `Symon Holl`. Actor 1 sometimes has the literal source label `...`; those entry-specific labels are preserved, while `Ovid Grecer` is the corroborated default for its control entries. Speaker names alone are used when no local portrait can be resolved.

Player choice order comes from the incoming source node's ordered outgoing links. It must not be derived from dialogue IDs, alphabetical text, or screen position.

Choice numbers are properties of those incoming edges. A shared player response keeps one **YOU** box even if different sources reach it under different choice numbers.

The dialogue actor filter uses the complete graph's source speaker set. Actors 5 and 6 normalize to **You**; narration normalizes to **Narrator**; other actors use their source speaker names. Names are deduplicated, with You and Narrator first when present, followed by the remaining names alphabetically. It does not invent missing actors.

Each checkbox controls only that actor's CHARACTER, NARRATOR or CHOICE boxes. Conditions, effects, unresolved commands, control and terminal boxes stay visible regardless of the entry's actor. Bypassing hidden speech is a visual projection; compact reference junctions may preserve a hidden branch or loop. The underlying source entries and links are unchanged.

## Effects, controls, conditions, and non-dialogue options

Entry `conditionsString` and `userScript` carry logical predicates and scripts. Sequence fields additionally carry presentation commands and occasional other commands; both are retained. Literal `BaseGame.` may be removed for display only; `BaseGameSupport.`, `BaseGameIsolated.`, `GameCondition.`, and every other namespace remain intact. Source expressions are not reordered or simplified.

Variable assignments and compound assignments are present directly in scripts. There are also calls such as `EnableNews`, `AddTokenStatus`, `UpdateCountryRelationship`, and `UpdateProgress` whose meaning cannot safely be equated with plain cosmetic audio/UI commands. Unknown commands must remain available for inspection. The viewer never executes source scripts.

Control titles include `START`, `input`, `output`, `Jump to: ...`, and explicit `End();`. `End()` is a dialogue/conversation control concept, not a proof of campaign completion. For example, entry `1:348` has script `End();` and an actual outgoing link to output `1:2`.

Bills use `BillProperties.Title`, `Description`, `SignVariables`, `VetoVariables`, and `IsVetoDisabledCondition`. The final field is a **disable** predicate, not a VETO availability predicate; the detail view labels it `Disabled when:` instead of silently inverting or misrepresenting it. SIGN and VETO effects remain separate.

Decisions use `DecisionProperties.Options`, an ordered list of objects with `Text`, `Condition`, and `Instruction`. For example, `Turn01_Decision_InfrastructureProject` offers investment first (sets the investment flag and decreases government budget by 1), then rejection (sets rejection and `GameCondition` flags).

Supported conditional-instruction records use `ConditionalInstructionProperties.ConditionalInstructions` with `Condition` and `Instruction`, together with scheduling fields including `CheckOnTurnNo`, `CheckOnStepNo`, `CheckPerTurn`, `CheckPerStep`, `CheckPerStoryFragment`, `Priority`, and `IsOneTime`. News uses `NewsProperties.Title`, `Description`, `TurnNo`, `Newspaper`, and `IsEnabledVariable`. Neither should be turned into fake decision options in the primary campaign tree.

## Representative verification

- Conversation 6: `Sordland/Turn01/Start_Inauguration`, 134 entries.
- Conversation 9: `Sordland/Turn01/Personal_Ball`, 241 entries.
- Conversation 1: `Sordland/Turn01/EnT_EconomicOverview`, 352 entries.
- Entry `1:3`: Narrator, English text beginning “Symon Holl, Gus Manger and Lileas Graf...”, exact outgoing destination `1:246`.
- Entry `1:348`: explicit `End();`, exact outgoing destination `1:2`.
- Current build, test and graphical smoke results are recorded in [VALIDATION.md](VALIDATION.md).

## Complete source accounting

See [SOURCE_ACCOUNTING.md](SOURCE_ACCOUNTING.md) for the independent inventory, field retention checks and control-flow audit. No supplied readable record is unaccounted for. Ignored Data contains 881 entries: 775 out-of-scope catalogue records, one out-of-scope GameFlow, two unsupported Main GameFlow metadata fields (`Description`, `Path`) and 103 unmapped actor names. Conversation records need no ignored entries in the supplied complete dump; their original fields are retained in entry metadata.

## News proof boundaries

`NewsGraphBuilder` accepts whole supported statements and exact variable/name equality. It rejects unsupported control flow, ambiguous named calls and assignment expressions with potential calls; later false/computed writes invalidate a final enabled-state claim. No names, source adjacency or publication turn produce causal links. Exact reachable dialogue links identify possible inner source effects; conditions are not evaluated, so these remain labeled possibilities. Missing proof leaves articles in PLAIN with a diagnostic. ROOTED band placement uses the enabler’s turn; the publication/source turn remains in article metadata.
