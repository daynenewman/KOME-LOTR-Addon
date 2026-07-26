# Legacy KOME 1.0.7 Release Notes (Pre-Schema 7)

> Historical release notes retained for traceability; current schema-7 behavior supersedes them.

Schema 5 alliance/war/economy/pledge revision.

- Added persisted two-side multi-faction wars, conquest-created opposition, allied double confirmation, operator `/war` lifecycle, and Wars Server Records.
- Replaced peacetime kingless control with active-war-only Military T3 stewardship, automatic generic supporting-faction war enrollment, recognized-pledged-supporting-king-only authority, replacement/native-king lifecycle handling, overlapping-war target unions, and one global 100% native eligible offensive pool.
- Added war-ending retreat/demobilization and unloaded entity tombstones.
- Removed the Trade Post runtime; legacy inventories migrate to faction-ledger recovery.
- Revised Trade to 50/250 cumulative trades. Trade T2 has no structure/population requirement and shows future Produce Farmer metadata only.
- Removed the withdrawn provisional Produce runtime: no service/slot/command/config/packet/permission/timer/generator/claim/NBT/Production tab remains. Genuine development-world pending stacks migrate once to existing recovery storage.
- Added permanent atomic whole-company transfer.
- Added actual-LOTR-pledge cleanup with exact-source population refunds, movement snapshot cancellation, quarantine diagnostics, and a typed scrollable Departure preview.
- Added typed alliance actions with server-recomputed authority, in-place refresh, four-tab Alliance detail backed by per-side track/Military/company records, whitelisted typed company/movement/recruitment/history GUI intents, ledger direction/remainder hardening, automatic-support war records, and `/war list ending`.
- Hardened fresh-world conquest initialization so the canonical tile-ID catalog is available even before LOTR's biome-map dimensions finish loading.
- Completed the dedicated KOME visual pass across Alliance list/request/detail/ledger, War records, allied-tile confirmation, and pledge departure with shared responsive components and destructive confirmation.
- Expanded automated and visual coverage. See the exact clean test/build/hash results in the [KOME 1.0.7 completion report](KOME_1.0.7_COMPLETION_REPORT.md) and the [55-screenshot scale matrix](gui-scale-verification/README.md); these are not claims of live multiplayer proof.

Back up the complete world before first schema-5 startup. Capture the migration log's real legacy-post recovery totals before allowing play.
