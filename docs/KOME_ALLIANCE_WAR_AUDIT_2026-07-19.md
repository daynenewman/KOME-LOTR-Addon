# KOME Alliance/War Revision Post-Implementation Audit

Date: 2026-07-19
World schema: 5
Addon release target: 1.0.7

## Implemented invariants

- Wars are stable two-coalition records with multi-faction sides and `ACTIVE/ENDING/ENDED` lifecycle. No gameplay field is named or interpreted as aggressor/defender.
- Player conquest of a faction-controlled tile passes through one server service. Existing opposition receives a capture event; otherwise one war is created. Unclaimed, same-owner, accepted transfer, and admin correction paths do not create wars.
- Direct allies and same-side coalition factions require a 30-second, UUID/tile/owner/diplomacy-bound second confirmation. Stale state fails closed.
- Direct active opposition wins permission/relation checks even when a contradictory coalition still places the factions together.
- Kingless Military T3 grants no peacetime authority. Active wars automatically enroll every effective T3 supporting faction on the native side with persisted provenance. Existing same-side members are not duplicated; opposing-side supporters are not moved and receive a contradiction warning.
- Only the recognized supporting king who is actually pledged to that faction may hire or control stewardship forces. Ordinary members and operators playing normally are denied at record/action construction, hire, command/packet, route/dispatch, each movement boundary, pending spawn, and restart reconciliation.
- Supporting-king loss clears that controller without rewriting coalition history or native funding; a replacement king is adopted automatically. Native-king creation ends kingless authority while keeping the coalition and legitimate native companies.
- Overlapping active wars contribute a union of legal opponents. Ending one authorization retains another valid authorization and does not prematurely demobilize the company.
- Stewardship capacity is one global 100% pool of unallocated native-source offensive population. Defensive, farmer, allocation, funded, captured-source, and valid native-player assets are excluded.
- War ending permits retreat only for stranded stewardship forces. Safe demobilization removes without drops and refunds once. No-safe-route cases are retained for admin resolution.
- Trade posts have no live class, command, GUI, timer, inventory, permission, or world record. Only a read-only legacy migration shape remains.
- Trade T1 is mutual ledger exchange at 50 cumulative trades; Trade T2 is 250 cumulative trades with no structure/population requirement and only the shared future Produce metadata. No Produce runtime exists.
- Actual LOTR pledge transitions release untransferred old-faction assets, preserve completed transfers, revoke temporary authority, reconcile succession/allocations, and audit results through a typed departure-preview GUI.
- Unloaded release uses persisted tombstones and a highest-priority entity-join interceptor; refund and removal flags are independent and idempotent.
- Whole-company transfer validates all funding before mutation and cannot be performed by temporary controllers.

## Verification performed

- Main and test sources compile under the configured Forge/LOTR 1.7.10 toolchain.
- Automated coverage includes pledge/movement/allocation/succession/migration, automatic generic T3 enrollment, king-only and king-replacement lifecycle, contradictions, overlapping wars, retired-Produce removal/recovery, ledger direction, and transfer safety. The exact clean count/result is recorded in the release completion report.
- Final clean test/build is the release gate recorded in the completion report.
- No LOTR base source or jar was changed.

## Open live-runtime verification

The manual matrix remains required for real Forge networking, multiplayer race timing, supporting-king replacement, multiple supporters, overlapping wars, crafted packets, chunk unload/reload, entity drops, GUI scaling, map overlay, and two-restart population/recovery proof. In particular, capture logs from a backed-up real world are required to report its actual legacy trade-post and retired-pending recovery totals.

## Technical limitation

No reusable Produce Farmer implementation or documented selling API was present. KOME therefore deliberately implements no substitute economy. The only current contract is the exact future-facing Trade T2 benefit text plus migration-only, one-time recovery of genuine pending items from withdrawn development records. Any future feature must integrate the real system and receive its own server-authoritative design and live verification.
