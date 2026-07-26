# KOME redesign completion report

Status: implementation complete; final artifact verification values are recorded after the clean release build.

## Delivered scope

- Build schema 1 and split population schema 2.
- Alliance schema 7 unified directional ladder.
- Builds-first Tile Command and map markers.
- Persistent owner/source-tile company assignment and rename.
- Server-authoritative placement, contribution, migration, progression, delegation, war, and destructive-action validation.
- Current documentation set with pre-schema-7 material isolated under `archive`.

## Schema and migration summary

Alliance 6 and earlier migrate per direction to the highest clearly supported benefit, with ambiguous cases lowered and persistent merchant entitlement preserved separately. Build collections initialize empty. Legacy tile population becomes explicit native source-faction pools; it is never fabricated into Builds. Obsolete grace and alliance-waypoint fields do not participate in runtime behavior.

## New primary classes/services

- `KOMEPlayerBuild`, `KOMEBuildContribution`
- `KOMEBuildService`, `KOMEBuildPopulationService`
- `KOMEAllianceStageProgress`, `KOMEAllianceProgressionService`
- `KOMEGuiAllianceUnified`
- `KOMEPacketBuildAction`

The existing `KOMEWorldData`, population/funding records, company/war services, packet layer, Tile Command, map overlay, ledger container, and Server Records were extended rather than duplicated.

## Verification

Final automated count, clean Gradle output, jar filename/size/SHA-256, deployed hash, commits, and tree state will be filled from the final clean run before handoff.

## Known limitations

- A hire for a company currently away still spawns through LOTR's normal safe player-side path, then joins the persistent company. The addon does not force-load a remote chunk or teleport the new entity.
- Produce merchant slots are persistent entitlements and query APIs only; no Produce merchant/economy is implemented.
- Deterministic ledger screenshots use a visual twin; the real container slots/hitboxes require live manual testing.
- Minecraft 1.7.10 has no modern accessibility/layout framework; KOME uses its scaled scissor, wrapping, tooltip, and confirmation utilities.

See `KOME_DECISION_LOG.md` for all judgments and `KOME_TEST_PLAN.md` for remaining multiplayer/manual checks.
