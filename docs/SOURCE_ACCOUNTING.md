# Runtime source accounting — 2026-09-21

The existing source-accounting tests still report zero unaccounted legacy records/entries/links. All three old inputs remain byte-for-byte unchanged.

RuntimeDatabase keeps the complete catalog, complete preferred-source envelopes and full raw entity objects. Each indexed entity retains logical collection, dataset, outputFile, original `$items` index, Id, NameInDatabase, Path and StoryPacks. Exact indexes never interpret `$objectId` or `$ref` as gameplay relationships. Non-preferred member files are also shipped unchanged, but never merged into the preferred collection.

Records without StoryPack_Main do not enter the entity index or gameplay. They remain inspectable in the complete source envelopes. Main-tagged records with conflicting Rizia path/variable evidence are additionally excluded from gameplay and diagnosed. No scope is guessed from numeric Ids or filename numbers.

Graph relationships come only from source pointers, panel/page/option names, exact supported command arguments, explicit IsEnabledVariable assignments, or decree AssignedDecreePanel references. Unsupported constructs, malformed references and non-literal arguments are retained and diagnosed; no fuzzy matches or variable reader/writer webs are produced.

Panel choice metadata retains raw Condition, Instruction, full descriptions, source identity, page/panel IDs, raw counter/bar increments and panel variable fields. Item details contain a separate PanelContext with its source location; that context does not overwrite the original option object fields. Presentation shortens only complete literal assignments. Unknown instructions and quoted strings are not rewritten.

Panel completion is distinct from all choice consequences. Original outgoing Link objects, order, priority and connector flags survive exactly once. Both the legacy and runtime-aware tests audit the full set of 286 conversations. The new panel model is separate from CampaignGroup; no common option-exit semantics are introduced.

The Runtime sources inspector exposes all preferred runtime envelopes and manifest. Configuration/support collections remain source/index-only unless an exact gameplay relation is implemented. Conditional-instruction runtime data supplements source details without replacing the established scheduling interpretation.

---

## Preserved baseline documentation (historical)

# Independent source-accounting audit

Audited the current `work/run1/SordlandTreeViewer` implementation before editing and repeated the inventory after the fixes. The three supplied inputs were read independently of Loader’s counters. `SourceAccountingChecks` compares every original supported entity field, whole conversation and whole entry with the resulting raw model, and matches every unconsumed record to an exact Ignored Data record. Nested raw values are compared recursively by collection equality. Source bytes remain unchanged.

## Inventory and outcomes

Counts below are source records, not visual boxes. A record can support multiple boxes or appear as several exact scheduled occurrences. Nested totals are separate and must not be added to their containing record totals.

| Source / top-level collection | Readable records | Consumed / raw retained by supported feature | Ignored records | Rejected | Unaccounted |
| --- | ---: | ---: | ---: | ---: | ---: |
| entity_data / AllBillsData | 26 | 22 | 4 | 0 | **0** |
| entity_data / AllConversationsData | 264 | 151 | 113 | 0 | **0** |
| entity_data / AllDecisionsData | 310 | 56 | 254 | 0 | **0** |
| entity_data / conditionalInstructionData | 368 | 4 | 364 | 0 | **0** |
| entity_data / GameFlowData | 2 | 1 | 1 | 0 | **0** |
| entity_data / NewsData | 1,476 | 1,436 | 40 | 0 | **0** |
| **entity_data total** | **2,446** | **1,670** | **776** | **0** | **0** |
| conversations_Sordland / conversations | 286 | 286 | 0 | 0 | **0** |
| nested dialogueEntries | 83,454 | 83,454 | 0 | 0 | **0** |
| nested dialogue field records | 868,521 | 868,521 | 0 | 0 | **0** |
| nested outgoingLinks | 104,553 | 104,553 | 0 | 0 | **0** |
| actor_names / actorNames | 103 | 0 | 103 | 0 | **0** |

All three roots also contain `_type` serialization descriptors. Numbered collection objects have `_type` descriptors as well. They are recognized container annotations, not list elements, entity IDs or actor IDs. They are excluded from record counts deliberately. Descriptors nested in supported/ignored records remain in raw JSON. Each actual top-level collection is listed above; there are no other top-level categories in the supplied files.

Main GameFlow additionally has two non-topological metadata fields, `Description` and `Path`. They remain in its raw model AND receive individual Ignored Data entries so the UI exposes them. Thus the visible Ignored Data total is **881**, rather than 879: 776 excluded entity/schedule records + 2 unsupported metadata fields + 103 actor names. The conversation file has no ignored group for this input because every record is consumed; the inspector creates that group if unsupported conversation data is supplied.

All 227 GameFlow fragment references and all 104,553 original dialogue links resolve in this input: **0 unresolved source targets**. Rooted News proof is a separate question from record accounting: all 1,436 supported articles are accessible in PLAIN, and articles without an exact enabler in the current ROOTED selection receive an explicit diagnostic. They are not rejected or lost. No article is attached from guessed causality.

The raw data includes fields beyond the display whitelist. Supported items and entries preserve the entire original object; those fields are consumed as source-inspection metadata even if not interpreted as mechanics. “Consumed” does not claim that every field has gameplay semantics implemented.

## Loader control-flow review

| Path reviewed | Outcome and justification |
| --- | --- |
| JSON parse/root/required collection failures | Explicit IOException; no successful partial load conceals invalid basic structure. Duplicate JSON keys fail explicitly. |
| Entity root collection switch | Five supported collections parsed; GameFlow handled separately; unknown collections/root fields go through `retainUnsupported`. `_type` is the recognized serialization descriptor. |
| `sourceValues` / numbered-key filtering | Only `_type` excluded; all numeric entries visited in numeric order, including sparse keys. Unsupported collection shapes are retained where safe; required corrupt schemas fail explicitly. |
| Sordland scope exclusion / `continue` | Whole original record retained with file, collection, index, identity and scope reason. No Rizia gameplay interpretation is guessed. |
| Entity parse catch | Entire readable incomplete entity retained with the exact parse reason. Missing/invalid Bill effect fields cannot silently become no-ops. |
| Entity optional text/default fields | Absent optional values give no claimed instruction/text. Wrong-type interpreted text is rejected into an ignored record. All present original fields stay in raw metadata. Actual supplied source has no incomplete-record fallback. |
| Title-resolution ambiguity / `continue` | Ambiguous title removed from lookup and diagnosed; numeric conversations remain separate. Catalogue item raw references remain intact. |
| Conversation naming prepass skip/catch | Only speaker-name inference is skipped; the main pass still visits and classifies every source conversation/entry. Entry-specific speaker labels are preserved. |
| Out-of-scope/incomplete conversation | Entire record retained with reason. If no usable Sordland conversation remains, loading fails explicitly instead of presenting a false Sordland graph. |
| Unknown conversation root/record fields | Ignored Data with exact location; complete supported conversation raw also retained. |
| Dialogue fields | Full original `fields` list retained in entry metadata. Incomplete fields have individual ignored records; ambiguous duplicate titles are not interpreted. No first/last-value guess creates a false instruction. |
| Dialogue links | Complete original link list retained. Exact valid pointers preserved; missing target identities diagnosed; malformed links retained with locator/reason and diagnostic. Contradictory origins/duplicate identities cause an explicit topology error. |
| Incomplete entry catch | Whole readable entry retained and diagnosed. Incoming valid links to its missing identity remain unresolved. |
| Referenced dialogue fragment skip | Avoids a duplicate catalogue fallback only; the conversation remains consumed and reachable from its catalogue item. Unreferenced conversations receive PLAIN fragment/ending items. |
| Non-Main GameFlow skip | Whole schedule retained in Ignored Data with story-pack scope reason. Missing Main schedule diagnosed; PLAIN remains available. Multiple Main schedules fail rather than choosing arbitrarily. |
| GameFlow exact resolution | Every fragment occurrence remains in the model. Missing/ambiguous/incomplete matches produce graph-visible unresolved nodes and diagnostics. Unknown schedule/turn/step fields retained explicitly. |
| GameFlow structural parse failures | Explicit errors for unsafe structural/index/fragment schema, never repaired with invented order. |
| Actor-name file absence | Explicit diagnostic that optional source was not supplied. When present, file parsed; all 103 supplied records retained with unsupported ActorID-mapping reason. Unknown root fields retained; corrupt structure gives explicit error. |
| Readable value retention versus interpretation | Whole original objects retained for supported metadata; unknown standalone content retained with reason. No whitelist destroys the original fields or wrappers. |

## Evidence beyond existing tests

The audit enumerated the JSON root categories and their counts independently, inspected Loader’s `continue`, `return`, `catch`, scope tests, collection dispatch, optional defaults and field interpretation paths, and inspected the resulting Ignored Data GUI. The new inventory checks then compare the model back to the files, rather than accepting Loader’s summary messages. Fixtures additionally exercise unknown collections/scalars, malformed but readable records, unsupported fields, actor-name files, duplicate fields and exact unresolved topology. This is a source accounting guarantee for the supplied dump, not a claim to execute arbitrary future game schemas.
