# KOME LOTR Addon

Kings of Middle-earth server addon for the Minecraft 1.7.10 Lord of the Rings Mod.

This repo is intentionally addon-only. It does not include the full LOTR mod source or the original LOTR mod jar.

## Features

- Player population tracking with offensive and defensive pools.
- Population GUI and keybind.
- Population unit breakdown showing tracked hired units and population cost.
- Hired-unit population enforcement with coin refunds on failed hires.
- Farmhand slot tracking based on total population divided by 25.
- Combat-unit population cost based on max health, including level-up health increases.
- Territory manager commands and GUI.
- LOTR map overlay for territory display name, ruling faction, and ruling player.
- Canonical mutual Civil, Trade, and Military alliances with shared faction-side progression.
- Two-king/kingless diplomacy, faction-side succession/contribution grace, and relation synchronization.
- Addon-only, server-enforced conquest-owner waypoints plus civilian/combat hiring and Military T2 passage.
- Persisted two-side coalition wars created/updated by hostile conquest claims, with operator lifecycle and Server Records GUI.
- Military T3 king-to-king voluntary delegation plus automatic-war-enrolled, supporting-king-only kingless stewardship using one global 100% native eligible pool.
- Trade T1 mutual ledger exchange and Trade T2 future Produce Farmer metadata only; legacy post and retired-development pending inventory migrate losslessly to recovery.
- Permanent whole-company transfer and actual-pledge departure cleanup with exact-source refunds and unloaded-unit tombstones.
- Per-step movement access revalidation with safe war-ending withdrawal/demobilization.

## Alliance Documentation

- [`docs/ALLIANCE_SYSTEM.md`](docs/ALLIANCE_SYSTEM.md) — architecture, tiers, lifecycle, benefits, and persistence.
- [`docs/ALLIANCE_MIGRATION.md`](docs/ALLIANCE_MIGRATION.md) — schema 5 cleanup/recovery, backup, and rollback guidance.
- [`docs/ALLIANCE_ADMIN.md`](docs/ALLIANCE_ADMIN.md) — commands, configuration, and operational notes.
- [`docs/ALLIANCE_TEST_MATRIX.md`](docs/ALLIANCE_TEST_MATRIX.md) — automated gates and multiplayer scenarios.

- [`docs/KOME_SERVER_RECORDS.md`](docs/KOME_SERVER_RECORDS.md) - Players/Wars records and filters.
- [`docs/KOME_PRODUCE_FARMER.md`](docs/KOME_PRODUCE_FARMER.md) - future design note; no Produce Farmer runtime currently exists.
- [`docs/KOME_ALLIANCE_WAR_AUDIT_2026-07-19.md`](docs/KOME_ALLIANCE_WAR_AUDIT_2026-07-19.md) - post-implementation audit.

Schema 5 migration and rollback details are in `docs/ALLIANCE_MIGRATION.md`.

## Project Layout

```text
src/main/java/kome/common
```

Shared/server-side addon code. This is where commands, saved data, event handling, and packets live.

```text
src/main/java/kome/client
```

Client-only code. This contains keybind handling, map overlay rendering, and GUI screens.

```text
src/main/resources/mcmod.info
```

Forge metadata for the addon. The addon mod id is `kome`, and it depends on the LOTR mod id `lotr`.

## Main Classes

`kome.common.KOMEAddon`

Forge entry point. Registers the network packet handler, proxy/event handlers, and server commands.

`kome.common.data.KOMEWorldData`

World-saved data container. Stores population, territory, canonical alliances/ledgers, wars, Produce slots, companies/movement, pledge tombstones, and hired-unit provenance.

`kome.common.data.KOMEEvents`

Main server rule enforcement. Watches hired NPCs, charges population when units are hired, updates population cost as max health changes, refunds coins when a hire is denied, and releases population/farmhand slots when tracked NPCs die or are dismissed.

`kome.common.command.KOMECommandPopulation`

Implements `/population`. This is used by both chat commands and the population GUI.

`kome.common.command.KOMECommandTerritory`

Implements `/territory`. This is used by both chat commands and the territory GUI.

`kome.client.KOMEKeyHandler`

Registers and handles the population and territory GUI keybinds. The territory keybind reads the currently selected LOTR map waypoint.

`kome.client.KOMEMapOverlay`

Draws the territory information panel on top of the LOTR map when a selected waypoint has saved KOME territory data.

`kome.common.KOMEReflection`

Small compatibility helper for Minecraft 1.7.10/Forge runtime naming differences. Some Minecraft fields and methods are obfuscated at runtime, so direct calls can crash in a normal client even when they work in the dev environment.

## Population Rules

Population has two normal pools:

- Offensive
- Defensive

Normal hired combat units consume from the combined army population limit:

```text
army limit = offensive total + defensive total
```

The addon still tracks offensive and defensive totals separately for server management, but it does not currently classify individual LOTR units as offensive or defensive. A combat unit's population cost starts from its unit category and can increase above that if its current max health is higher.

```text
Huorns start at 75 population
Mounted units and Warg Bombardiers start at 50 population
Trolls and Olog-hai start at 125 population
All other units start at 25 population

25 max health standard unit = 25 population
30 max health standard unit = 30 population
30.5 max health standard unit = 31 population
25 max health mounted unit = 50 population
```

Tracked hired units are recalculated while active. If a troop levels up and its max health increases, the addon's tracked army population usage increases with it. If the player is pushed over their combined army limit, available population shows as 0 until enough population is added or units are dismissed/killed.

The population GUI includes a scrollable `Units` view. It groups tracked hired units into `Army Units` and `Farmhands`, then shows the population or farmhand slot each one currently uses.

Farmhands use a separate slot count instead of consuming offensive or defensive population:

```text
farmhand limit = (offensive total + defensive total) / 25
```

All farmer/farmhand/slave/vinehand unit types share that same farmhand limit. If a tracked farmhand dies or is dismissed, the slot is freed.

## Territory Rules

KOME does not simulate battles or victory. Player hostile claims are server-authoritative and create/update coalition-war history; accepted transfers and operator corrections remain non-war administrative paths.

Each territory record stores:

- LOTR waypoint code name
- Ruling faction
- Ruling player
- Optional display name

The territory data is synced from the server to clients so the map overlay can display it.

## Build

This addon compiles against the LOTR dev classes from the main LOTR source workspace.

The default setup expects this folder to live at:

```text
The-Lord-of-the-Rings-main/KOME-LOTR-Addon
```

Build from this folder with:

```powershell
powershell -ExecutionPolicy Bypass -File .\gradle-local.ps1 reobfJar
```

The jar will be created in:

```text
build/libs/KOME-LOTR-Addon-dev-local.jar
```

If the LOTR workspace is somewhere else, update these values in `gradle.properties`:

```properties
kome.lotrClassesDir=C:/path/to/The-Lord-of-the-Rings-main/build/classes/java/main
kome.lotrResourcesDir=C:/path/to/The-Lord-of-the-Rings-main/build/resources/main
```

## Install

Use the original LOTR mod jar plus this addon jar.

Do not replace the LOTR mod jar with this addon. Put both jars in the `mods` folder:

```text
LOTRMod v36.15.jar
KOME-LOTR-Addon-dev-local.jar
```

## Commands

```text
/population get [player]
/population gui [player]
/population units [player]
/population set <player> <offensive|defensive> <amount>
/population add <player> <offensive|defensive> <amount>
/population remove <player> <offensive|defensive> <amount>

/territory get <waypoint>
/territory gui <waypoint>
/territory set <waypoint> <faction|none> <ruler|none> [display name...]
/territory clear <waypoint>

/alliance request|accept|break <civil|trade|military> <factionA> <factionB>
/alliance roll|goods|claimGoods ...
/alliance config difficulty|requirement|quota item|waypointRestriction|grace ...
/alliance waypoint bypass|check ...
/alliance grace status <A> <B> [affected] | set|expire <A> <B> <affected> ...

/war create|rename|side|status|list|end|finalize|cancel ...

/troops companies [tile]
/troops company <id> tendency|delegate|reclaim|transfer|acceptTransfer|rejectTransfer|cancelTransfer ...
/troops movement stay|retreat|resume <orderId>
/troops pledgeRelease preview|status|retry|resolve ...
```

See the admin guide for complete syntax and operator-only configuration.

## Notes

Everyone on the server should use the same addon jar as the server.
