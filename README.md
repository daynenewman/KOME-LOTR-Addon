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

World-saved data container. Stores player population totals, territory records, and currently tracked hired units in the world save.

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

The addon still tracks offensive and defensive totals separately for server management, but it does not currently classify individual LOTR units as offensive or defensive. A combat unit's population cost is its current max health, rounded up to the nearest whole number.

```text
25 max health = 25 population
30 max health = 30 population
30.5 max health = 31 population
```

Tracked hired units are recalculated while active. If a troop levels up and its max health increases, the addon's tracked army population usage increases with it. If the player is pushed over their combined army limit, available population shows as 0 until enough population is added or units are dismissed/killed.

The population GUI includes a scrollable `Units` view. It groups tracked hired units into `Army Units` and `Farmhands`, then shows the population or farmhand slot each one currently uses.

Farmhands use a separate slot count instead of consuming offensive or defensive population:

```text
farmhand limit = (offensive total + defensive total) / 25
```

All farmer/farmhand/slave/vinehand unit types share that same farmhand limit. If a tracked farmhand dies or is dismissed, the slot is freed.

## Territory Rules

Territory capture rules are not automated. The addon only gives the server an in-game way to record and display territory ownership.

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
```

## Notes

Everyone on the server should use the same addon jar as the server.
