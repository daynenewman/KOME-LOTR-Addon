# KOME LOTR Addon

Kings of Middle-earth server addon for the Minecraft 1.7.10 Lord of the Rings Mod.

This repo is intentionally addon-only. It does not include the full LOTR mod source or the original LOTR mod jar.

## Features

- Player population tracking with offensive and defensive pools.
- Population GUI and keybind.
- Hired-unit population enforcement with coin refunds on failed hires.
- Farmhand slot tracking based on total population divided by 25.
- Unit price categories for farmhands, Huorns, mounted units, Warg Bombardiers, Trolls, Olog-hai, and standard units.
- Hired-unit level cap.
- Territory manager commands and GUI.
- LOTR map overlay for territory display name, ruling faction, and ruling player.

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
