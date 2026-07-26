# KOME Alliance, War, and Pledge System

## Server-authoritative alliance model

An alliance is one canonical unordered LOTR faction pair. Civil, Trade, and Military retain mutual relationship statuses plus faction-side ledgers and unlocked tiers. `ACTIVE/T0` means Established and grants no benefit. Civil and Trade end at T2; Military ends at T3. Trade depends on Civil, and Military depends on both lower tracks.

Every faction side has its own persisted unlocked Civil/Trade/Military tiers, quota assignment, deposits, claims, recovery storage, cumulative activity, completed requirements, waiver, and contribution/succession grace. Completing a target advances only that faction's tier; its partner may remain at a different tier. Every permission and benefit reads the acting faction's tier. Weighted quota selection, side-specific recovery, and fourteen-day grace remain unchanged.

Normal requests remain limited by the current server-derived request options. Two kings may negotiate across any default relation. A king requesting toward a kingless faction is limited to Civil at Neutral+, Trade at Friend+, and Military at Ally; eligible requests auto-accept with a kingless-side waiver. The GUI cycles only through server-approved candidates. Operator all-record visibility is an explicit session toggle and never changes request authority.

The actual LOTR pledge is authoritative for live faction membership. KOME progression-lord data is not a substitute after a player's real pledge has been observed.

## Standard requirements

| Track | Tier | Rolled item effort | Cumulative activity | Population |
|---|---:|---:|---:|---:|
| Civil | T1 | 4 stack-equivalents | None | None |
| Civil | T2 | 6 stack-equivalents | 100 allied trades | None |
| Trade | T1 | 8 stack-equivalents | 50 allied trades | None |
| Trade | T2 | 12 stack-equivalents | 250 allied trades | None |
| Military | T1 | 6 stack-equivalents | 50 eligible kills | 50 effective offensive |
| Military | T2 | 12 stack-equivalents | 500 eligible kills | 150 effective offensive |
| Military | T3 | 20 stack-equivalents | 1,000 eligible kills | 300 effective offensive |

Trade activity never resets between tiers. There is no Trade T2 structure or population requirement. Easy, Standard, and Hard continue to scale item/activity requirements by 0.65, 1.00, and 1.50 with ceiling rounding. Completed tiers remain complete after configuration or migration.

## Benefits

- Civil T1: allied public-waypoint access, still subject to native LOTR progression and the final server gate.
- Civil T2: eligible allied farmhand hiring with existing funding provenance.
- Trade T1: faction-side goods exchange through the alliance ledger. A faction with the unlock contributes its own roll; the opposite side claims those goods.
- Trade T2: display-only future benefit, `Additional Produce Farmer Slot`. It currently exposes no action or runtime state.
- Military T1: one eligible allied combat recruit per player/pair under existing funding rules.
- Military T2: army passage for the faction that unlocked it. It grants no ownership, population, hiring, or conquest rights.
- Military T3: voluntary delegation when the native faction has a king, or Wartime Stewardship when it is kingless and both factions share an active war side.

Trade posts no longer exist. There are no charters, locations, approvals, inputs, outputs, timers, catch-up settings, post limits, or post permissions. The migration-only reader still recovers legacy inventories exactly once.

## Future Produce Farmer integration

No Produce Farmer runtime currently exists. There is no slot registry, player entitlement, product pool, selection, cooldown, timer, generator, pending-item workflow, claim action, permission flag, packet, command, configuration field, Production tab, or live Produce NBT.

The canonical alliance pair already preserves both partner factions for a future real integration. Shared metadata and the Benefits tab show `Additional Produce Farmer Slot` with this exact description: `This Trade T2 alliance has unlocked an additional Produce Farmer slot. Produce Farmer integration will be added in a future update.` This is text only.

Development saves containing records from the withdrawn provisional implementation are read once. A real unclaimed pending stack is moved to the contributing faction's existing alliance recovery ledger, or quarantined when the pair/contributor is invalid. The retired tags are never written again, making the cleanup idempotent across cold restarts.

## Coalition war model

`KOMEWar` is a persisted, server-authoritative record with a stable ID, optional name, status, neutral Side One/Side Two names, multi-faction sets, creation/ending/ended timestamps, tile-capture history, admin history, stewardship authorizations, ending reason, and last-update time. Status is `ACTIVE`, `ENDING`, or `ENDED`; gameplay never depends on aggressor/defender labels.

A faction may join multiple active wars. Records and `/war status` report contradictory memberships. If two factions already oppose each other in an active war, a hostile capture appends to that war rather than creating a duplicate.

A successful player conquest from one controlled faction to another creates or updates a war. Truly unclaimed, same-faction, accepted transfer/sale, and administrative correction paths do not. The server reads the current tile owner at execution time.

## Allied and same-side conquest confirmation

Capturing a direct ally or a faction sharing an active coalition side requires two server-backed clicks. The confirmation stores player UUID, tile, expected owner, alliance/war fingerprint, and a 30-second expiration. The second click rechecks every value. Expired, owner-mutated, or diplomacy-mutated confirmations fail closed, including when the new owner would not otherwise require confirmation. All player packets use the same claim service.

Confirmed hostile capture ends every direct Civil/Trade/Military track, revokes direct delegation, applies movement access-loss safety, and creates/updates opposition. Existing same-side coalition membership is not rewritten automatically; the contradiction remains visible for operator correction, while direct active opposition takes priority in permission checks.

## Military T3 authority

Voluntary Delegation is available only while the native faction has a king. The king may delegate personally owned companies and reclaim immediately. Ownership, unit faction, funding, and provenance never transfer. Temporary commands are closed to exactly View, Dispatch, Continue, Halt, Stay, Retreat, and Resume.

Wartime Stewardship is dormant during peace. When a kingless native faction joins an `ACTIVE` war, every effectively active mutual Military T3 supporting faction is automatically enrolled on the native side. Enrollment provenance is persisted as `AUTOMATIC_MILITARY_T3_SUPPORT`. Same-side members are not duplicated. An already-opposing supporter is never moved; it receives a prominent contradiction record for operator resolution. Later authority loss never silently removes the recorded coalition member.

Only the currently recognized, actually pledged supporting king may exercise stewardship. Operator status and Operator View do not supply this gameplay authority. Authorization requires:

- the native faction has no king;
- Military T3 is effectively active between native and controller factions; and
- both factions occupy the same side of at least one `ACTIVE` KOME war;
- the actor is the supporting faction's recognized king and is still pledged there; and
- no direct active war places the supporting and native factions on opposing sides.

Legal offensive destinations are the native faction's land, current Military T2 passage, and the union of factions on opposing sides of every authorizing active war. Controller faction identity is never substituted for native identity. Side changes, war ending, tier loss, controller invalidation, pledge change, and king creation revalidate authority at GUI construction, hiring, commands/packets, route preview, dispatch, departure, every physical boundary, arrival retry, pending spawn, and restart. Ending one overlapping war preserves every remaining valid authorization.

Supporting-king loss immediately clears that controller and safely halts movement while keeping native ownership, funding, population reservations, and war membership. A replacement recognized pledged king is assigned automatically while the war and Military T3 remain valid. Native-king creation ends kingless stewardship, revokes temporary controllers, and keeps legitimate native companies and coalition membership; stewardship-created forces follow the existing safe withdrawal/demobilization path.

When the native faction has a king, only that recognized pledged king may voluntarily delegate a personally owned company, and only to the recognized pledged king of an effectively active Military T3 partner that is not directly opposed. Ordinary members, operators acting as players, unrelated kings, and temporary controllers are rejected.

Stewardship uses 100% of globally eligible, unallocated native offensive population. All controllers share the same pool. Defensive/farmer population, player allocations, already funded population, captured/foreign-source pools, and valid native-player assets are excluded. Each company has one controller and persists native/source/controller/war/reservation/cleanup metadata.

## War ending and safe demobilization

`/war end` changes an active war to `ENDING`, disables new authority under it, and revalidates companies. A stewardship company stranded in former opponent territory becomes `WAR_ENDED_HALTED`; only Retreat is accepted. Retreat prefers native territory on the actual traveled route, then valid Military T2 staging. If no safe route exists, the company and order are retained as `PENDING_ADMIN_RESOLUTION` rather than teleported or deleted.

At a safe stationary tile, stewardship-created units are removed without drops, population returns once to the exact native source, and records/reservations clear. Unloaded entities receive persistent tombstones before refund; a highest-priority KOME entity-join handler removes any later ghost without a second refund. Pre-existing native/orphan companies lose temporary control and become dormant rather than being deleted.

A war finalizes only when cleanup is complete or every remaining exception is explicitly preserved in admin-resolution state.

## Pledge departure and permanent transfer

KOME observes the actual LOTR pledge on login and server ticks. Unpledge, direct switch, or administrative pledge loss triggers cleanup immediately after the base mutation without patching LOTR.

The company screen's `Departure` action opens a typed, server-authored preview showing current/former faction, unit/company counts, population types, funding totals, movements, transfer offers, and pending tombstones/quarantine. Cleanup cancels movement before entity mutation, releases still-owned old-faction units without drops, refunds exact reserve/tile-pool/allocation provenance once, closes stale allocations, removes empty companies, starts succession for a departing king, revokes temporary authority, writes bounded audit, and notifies the player/operators. Farmhands release their slot without a population credit. Ambiguous legacy provenance is quarantined and never guessed.

Permanent whole-company transfer is separate from temporary delegation. It requires owner offer, online same-native-faction recipient, explicit acceptance, a five-minute persisted offer, stationary records, and complete funding preflight. Reserve/allocation debits and refunds are atomic; direct tile-pool sources remain exact; mixed-source failure changes nothing.

## GUI and records

- Alliance: Overview, Requirements, Benefits, Military. There is no Production tab.
- Requirements: authoritative per-side rolled/delivered quota, activity, Military population, completion/waiver/grace/invalid state, and concise remainder.
- Military: server-authored voluntary/delegated/stewardship state, actual owner/controller and king state, dormant/revocation reason or active wars/opponents, one global native population pool, tendency, movement, withdrawals, and viewer-authorized actions.
- Company: native faction, owner, controller/authority, war IDs, legal targets, source, movement and cleanup state.
- Conquest: hostile warning, armed confirmation, and whether the capture creates or updates a war.
- Server Records: Players/Wars pages plus All/Active/Ending/Ended filtering and full war detail/history.
- Pledge: typed, scrollable departure preview from Companies and one-time result chat after transition.

All GUI records are explicit, viewer-scoped server payloads. Display text never grants authority.

## Addon-only waypoint enforcement

KOME still uses its version-checked final-travel transformer for LOTR v36.15. The original LOTR jar is neither edited nor redistributed. The transformer fails closed on an incompatible target, while optional map-overlay reflection fails open. `/alliance config waypointRestriction off` or a player bypass disables only KOME territory policy; native LOTR travel restrictions remain.
