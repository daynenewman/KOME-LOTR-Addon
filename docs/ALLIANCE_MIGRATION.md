# Alliance Schema-6 Migration and Rollback

## Mandatory backup

Before the first schema-6 start, stop the server and make a verified, recoverable backup of the entire world, including the dimension containing `KOME_ServerRules`. Do not edit live NBT. Older addon builds must not be started against a schema-6 world.

## Schema change

`AllianceDataSchemaVersion` is now 6. The migration is deterministic and idempotent. It does not infer historical wars from existing tile ownership.

Schema 6 adds an `UnlockedTiers` map to each faction-side alliance ledger. A schema-5 shared active tier is copied to both participating sides as the safe initial value, while existing per-side completed requirements, quotas, deposits, activity, recovery, waiver, and grace remain unchanged. Subsequent completion advances only the acting faction. The legacy pair-level tier fields remain a conservative lower-side compatibility projection and are not used to grant directional benefits.

Preserved:

- canonical alliance pairs and both faction ledgers;
- completed Civil/Trade/Military tiers, quotas, deposits, claims, recovery, cumulative activity, waiver, and side-specific grace;
- companies, movement snapshots/history, hired-unit provenance, population pools/allocations, and existing safe delegation;
- completed Trade T2 under the new no-structure rule;
- external/non-KOME farmer data (none is rewritten by this addon revision);
- waypoint policy and addon-only LOTR v36.15 transformer behavior.

Initialized:

- empty war registry and sequence for worlds with no schema-5 wars;
- player actual-pledge baseline;
- war membership provenance, Military T3 support enrollment/authorization state, and revocation history;
- pledge-release tombstones, quarantine, last-result, and bounded audit stores.

## Peacetime stewardship migration

Every loaded stewardship company is revalidated after companies, wars, and units load. Kingless Military T3 without an active same-side war loses temporary control. Movement is halted before another step. Stewardship-created forces enter withdrawal/demobilization; pre-existing native/orphan companies become dormant. Population returns only at a safe demobilization point or through a persisted tombstone. Existing valid reservations are preserved and future eligibility uses the global 100% native, unallocated offensive pool.

Ambiguous legacy provenance is never guessed or deleted at load. It remains quarantined for `/troops pledgeRelease status|resolve` diagnostics.

## Trade-post removal and recovery

The old runtime system is gone. Schema 6 retains the migration-only `AllianceTradePosts` reader. For every valid record:

1. read all input and output slots;
2. identify the operating and host factions;
3. copy each valid stack to the operating faction's alliance-ledger recovery storage;
4. persist the recovered post ID;
5. omit `AllianceTradePosts` and `FarmerReserved` from the new save.

Malformed records are retained in `TradePostMigrationQuarantine`; they are not silently discarded. `RecoveredLegacyTradePostIds` makes repeated loads idempotent. The server log reports affected posts, recovered stacks, recovered item total, quarantined records, and cleared obsolete farmer flags. A real-world total is known only after that world is loaded; automated coverage verifies a representative 1-stack/17-item recovery plus marker idempotence.

No post location, approval, production, product, interval, storage, catch-up, or post-limit state becomes live schema-6 gameplay.

## Trade and retired provisional Produce records

Trade T1 remains 50 cumulative legitimate trades. Incomplete Trade T2 now checks 250 cumulative trades and no structural/population requirement. Cumulative progress is retained. Completed Trade T2 is not revoked.

The provisional KOME Produce runtime was withdrawn before release. Schema 6 does not persist a slot registry or maximum. `AllianceProduceSlots` and `TradeProduceSlotsMaximum` are removed on every save and never emitted.

For a development-world `AllianceProduceSlots` record, only a real, unclaimed `CurrentPendingProduct` is considered. A valid canonical pair plus contributing faction moves that stack once into the existing faction-side recovery ledger. An invalid pair/contributor is copied to migration quarantine. Because the retired source tags are absent from the next save, cold restarts cannot replay the recovery. All other provisional selection, cooldown, lock, timer, and entitlement fields are discarded rather than becoming a replacement economy.

## Pledge baseline and old-faction units

The schema-5 pledge baseline behavior remains in schema 6: KOME compares the actual LOTR pledge with the previous KOME faction baseline. A mismatch runs the same transactional release used for later unpledge/switch events. Thereafter the observed actual pledge, including an empty unpledged value, is authoritative.

Loaded units are removed without drops. Moving snapshots are cancelled/removed before refund. Unloaded stationary units receive tombstones before exact-source refund and are killed by the KOME join interceptor if they later load. Farmhands free only their slot. `PLAYER_RESERVE`, `TILE_POOL`, and `TILE_ALLOCATION` use exact recorded provenance; stewardship reservation and malformed legacy sources are quarantined rather than guessed.

## First-load verification

1. Save the pre-start backup and record its path/date.
2. Capture the complete schema-6 migration log, especially the `5 -> 6` alliance summary and any post `affected/recoveredStacks/recoveredItems/quarantined` totals.
3. Verify no `AllianceTradePosts` key is written on the next save.
4. Claim all recovered ledger goods and reconcile totals with old post inputs/outputs.
5. Confirm `/war list all` is empty unless new captures/operator commands occurred.
6. Inspect every migrated stewardship company and movement status.
7. Open Benefits and verify the Trade T2 future message; confirm no Production tab, command, or live Produce NBT remains.
8. Run pledge-release status for representative players and resolve no record without evidence.
9. Cold-restart twice; compare wars/membership provenance, unit UUIDs, populations, allocations, recovery goods, and tombstones; confirm retired Produce tags do not return or recover twice.
10. Run the complete manual matrix.

## Rollback

Rollback only by stopping the server, restoring the complete pre-schema-6 backup, restoring the earlier addon, and restarting. Do not downgrade against the migrated world, delete tombstones/quarantine/recovery IDs, or hand-edit population and alliance records.

Migration never edits or redistributes the LOTR jar.
