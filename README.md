# KOME LOTR Addon

Kings of Middle-earth server addon for Minecraft 1.7.10 and LOTR Mod v36.15. The repository and release jar are addon-only: install it beside an untouched LOTR jar.

## Current systems

- Persistent player Builds at exact conquest-map coordinates, with half-hour contributions, manager approval, faction-owned population, succession, audit-safe deletion, and homeland-only enemy destruction.
- Explicit tile/faction population pools: native and Build sources remain separate; controllers receive 100% of their own capacity and 50% of foreign capacity.
- Builds-first Tile Command with Population and Allocations tabs plus clickable map markers.
- One persistent auto-created combat company per player/source tile; editable name never changes source identity.
- One directional four-stage alliance ladder: Agricultural Access, Trade Entitlement, Territorial Passage, and Military Partnership.
- Canonical authoritative goods ledger, coalition wars, voluntary company delegation, restricted kingless wartime stewardship, pledge cleanup, and movement revalidation.
- Unified dark-parchment GUI components and typed server records.

There is no active three-track alliance model, king-loss grace timer, or alliance waypoint restriction. A few old NBT/string identifiers remain only for safe schema migration.

## Documentation

- [Build system](docs/KOME_BUILD_SYSTEM.md)
- [Population system](docs/KOME_POPULATION_SYSTEM.md)
- [Alliance system](docs/KOME_ALLIANCE_SYSTEM.md)
- [Alliance migration](docs/KOME_ALLIANCE_MIGRATION.md)
- [GUI handoff](docs/KOME_GUI_HANDOFF.md)
- [Server Records](docs/KOME_SERVER_RECORDS.md)
- [Decision log](docs/KOME_DECISION_LOG.md)
- [Test plan](docs/KOME_TEST_PLAN.md)
- [Redesign completion report](docs/KOME_REDESIGN_COMPLETION_REPORT.md)
- [Historical/archive documents](docs/archive/README.md)

## Schemas

- Alliance: 7
- Build: 1
- Population: 2

Old saves load conservatively. Back up a world before upgrading; see the migration document.

## Build

The addon compiles against the LOTR development classes in the parent workspace:

```powershell
.\gradlew clean test build --no-daemon
```

Output is under `build/libs`. Everyone on a server should use the same KOME jar. Do not replace or patch `LOTRMod v36.15.jar`.

## Normal UI

Use the KOME Lord Menu and conquest map. Tile Command provides Build creation/contribution/review, split population, and allocations. Combat hires automatically join the source-tile company. Alliance list/request/detail/ledger provide the complete normal diplomacy flow; chat commands are not required.

## Operator repair commands

```text
/build list [tile]
/build inspect <id>
/build pools <tile>
/build reassign <id> <onlinePlayer>
/build remove <id>
/build sethours <id> <offensive|defensive> <hours>
/build config populationPerHalfHour <value>

/alliance list|status ...
/alliance request|accept|break <factionA> <factionB>
/alliance roll|goods|claimGoods ...
/alliance stage <actingFaction> <partnerFaction> <0-4>
/alliance clear <factionA> <factionB>
/alliance config difficulty <easy|standard|hard>
/alliance config stagequota <1-4> <stack-equivalents>
/alliance config stage3hours <hours>
/alliance config quota item ...

/war create|rename|side|status|list|end|finalize|cancel ...
/troops companies [tile]
/troops company <id> tendency|rename|delegate|reclaim|transfer|acceptTransfer|rejectTransfer|cancelTransfer ...
/troops movement stay|retreat|resume <orderId>
/troops pledgeRelease preview|status|retry|resolve ...
```

These commands are for repair/debug/administration; normal gameplay remains server-authoritative through GUI packets.
