# KOME alliance migration

Alliance schema 7 replaces schema 6's three visible directional tracks with one directional four-stage ladder.

## Read path

Every old pair is loaded into the same canonical pair key. Duplicate byte-identical tags are ignored, duplicate logical pairs are merged conservatively, and malformed/self/blank pairs are copied to `AllianceMigrationQuarantine` rather than crashing world load.

Each faction direction is migrated independently from its own legacy ledger. The migration does not take the maximum numeric tier across unrelated tracks and does not merge the two sides' goods.

## Exact stage mapping

For each direction, the highest clearly earned benefit is selected:

| Evidence in that direction | New stage |
|---|---:|
| Active accepted relationship only | 0 |
| Legacy Civil farmer access earned | at least 1 |
| Persistent trade/Produce merchant entitlement clearly earned | at least 2 |
| Cross-territory passage clearly earned | at least 3 |
| Valid delegated-command / former Military T3 benefit clearly earned | at least 4 |

Contradictory or incomplete evidence resolves to the lower safe stage. A tier number without the corresponding active/earned benefit is not promoted. Stage claim timestamps use the best saved update time and are clamped to valid values.

Legacy pending state is collapsed to one relationship request using the first valid requested-by/receiver direction. A clearly active track makes the formal relationship active.

## Compatibility data

Schema 7 still writes legacy `Civil*`, `Trade*`, `Military*`, and faction-ledger fields as a compatibility mirror for inventory recovery, quota configuration, and rollback inspection. Runtime requests, records, GUI, permissions, and benefit checks use `RelationshipStatus` plus `StageProgress`.

Internal identifiers such as `MILITARY_T3_STEWARDSHIP`, `AUTOMATIC_MILITARY_T3_SUPPORT`, and quota pool names are stable persisted tokens. They are not evidence that the retired three-track system remains active.

## Merchant entitlement

A clearly earned legacy merchant entitlement becomes the direction's persistent `produceMerchantSlotUnlocked` flag even when ambiguous stage evidence is conservatively lower. Breaking/reforming does not duplicate the flag. The actual Produce merchant feature remains future work.

## Removed data

King-loss/contribution grace tags and waypoint-restriction/bypass tags are ignored during read and explicitly removed from the root tag on the next save. No timer is recreated. Obsolete Military-4 assignment/delivery entries are cleared. Legacy post inventories are recovered to the appropriate directional ledger or quarantined, never silently discarded.

## Population and Build companion migration

The same world load initializes Build schema 1 with an empty collection when no Builds exist. It does not reinterpret old population as construction.

Population schema 2 gives legacy tile totals an explicit source faction, preferring a saved source, then current tile controller, then legacy faction. The saved total becomes the native baseline. Allocations, living units, and funding records are reconciled after all pools and Builds load.

## Backup and validation procedure

1. Stop the server and copy the world directory.
2. Record the pre-upgrade `KOMEWorldData` file/hash.
3. Start once with the schema-7 addon and inspect log lines for alliance loaded/merged/quarantined counts plus Build/population schema messages.
4. For representative asymmetric pairs, compare each direction's old earned benefit with its new stage.
5. Inspect persistent merchant partners, ledger contents/recovery, split tile pools, allocations, hired-unit sources, companies, and active wars.
6. Stop cleanly, restart, and verify the same values.
7. Keep the backup until multiplayer validation is complete.

Rollback to schema 6 is not supported in-place because schema 6 cannot understand Builds, split source pools, or the unified stage model. Restore the pre-upgrade world backup if rollback is required.
