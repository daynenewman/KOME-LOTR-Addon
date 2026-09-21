# Final combined geography installed - 2026-09-20

## Exact scope and verification

Installed only the approved **4,915 edits: 4,003 V2 corrections + 912 gap fills**.
The proposal package is unchanged. The separate Weathertop transfer, blocked 55-cell
proposal, fragment-producing transfers, shape edits and uncertain geography remain excluded.

- Original production mask SHA-256: `46af1f42854c4c12b7f9ce69b3aad7ad4513340b000631f62514937debf0dbed`.
- Installed mask SHA-256: `ab792277f61882d415963bf5af1b8d2705458c68de80f5b3cbb9102e30d1b4a7`.
- Exact manifest SHA-256: `72a21e60160994b9054e37eb3db4ce7d30ecb55152c7c0c61d9a365779dfa6f4`.
- Production JAR: `build/libs/KOME-LOTR-Addon-1.0.8.jar`.
- JAR SHA-256: `a4a46b6e5f588f8266faf8b512f527afc5485a780f3c43b5cac100989a52485e`.

All 12.8 million source/candidate cells were compared against the exact manifest.
**621 tile IDs and 622 components** remain, with a one-to-one component mapping:
no new fragments or component merges. Protected water/gaps, pre-V2 territory and
all pilot assignments are unchanged. Explicit gameplay-default files retain their
original hashes; packaged resource hashes match. Runtime lookup code is unchanged.

## Tests

Focused geometry/resolver/HUD/map/Build/public-access suites: **138 passed**, no skips,
failures or errors. One `gradlew.bat clean test build --no-daemon --console=plain`:
**814 discovered, 812 passed, two skipped, zero failed/errored**. The two existing
Windows symlink assumptions are listed in `build-results.json`.
The full suite exercises all **6,055 original production gameplay expectations**,
including routing/init/reload, and they remain identical. The approved combined
manifest is now a regression fixture. Full-image pre-V2 territory and protected-gap
checks remain, with exact and adjacent fractional boundary tests for Rhun, Nindalf
and the new ordinary land seam. No production Java changes were needed.

## Recovery and stopped-world gate

Source recovery: `C:/Users/dayne/Documents/KOME-Recovery/before-combined-geography-20260920T165913Z-33551acc`.
468 modified/untracked files were byte/hash verified, with tracked binary diff,
index/status, all-existing-file hashes and a verified history bundle.

Stopped runtime backup:
`C:/Users/dayne/Documents/KOME-Validation/tile-resolution-20260918-200409-d095d7/backups/before-final-combined-20260920T170309Z-36c64fdc`.
All **103 files / 39,065,428 bytes** were SHA-256 verified, including the final
saved world, configurations, profile settings, operator list and old artifacts.
The isolated server was checked twice for connected players (zero), saved and
stopped gracefully. No game client process was running.

Before replacement, the final stopped save was compared under the original and
combined masks through the production Java resolver: **1,515 coordinates, zero
membership changes**. Builds B1-B4 all remain T132. Inventory: four Builds, 621
saved tile destinations, 269 native waypoint links, 621 active routing anchors,
one LOTR player file with no custom/shared waypoint locations. No stored movement
orders/history, hired units or companies existed in this save. This is verified
absence in this save, not proof about other worlds or live unsaved entity state.
`affected-records.tsv` has no records. No save repair, relocation or migration occurred.

## Isolated refresh and runtime evidence

Both isolated client/server production JARs have the JAR hash above; other mods
were unchanged. Server PID 50480 listens only on **127.0.0.1:57858**, online-mode=true,
Middle-earth dimension **100**. Startup reached Done. Ten console resolver checks
passed (`console-checks.json`). No candidate resource-validation, gameplay-default,
linkage or client-only classloading failure was found. Existing FML signature,
LOTR transformer/waypoint-name and legacy Forge version-check JSON warnings remain.
`ops.json` remains empty: `_Danye_` remains non-operator.

Matching Prism profile `tile-resolution-20260918-200409-d095d7` is installed and its
launch was requested. Prism is at **Low free memory**; no game JVM has started.
No memory setting was changed, warning accepted, or account selected. Close unused
applications and retry/continue the prepared profile yourself; stop for account
selection if requested. No current-candidate visual acceptance is claimed.

## Exact manual checks

First: finish the prepared Prism launch and connect to `127.0.0.1:57858`.
Remain non-operator. For the first crossing, arrange staff positioning in **Middle-earth**
at **237248.5, 70, 87295.5**. Ground is actual sand at Y=69 with two clear air blocks,
verified twice after neighboring chunk population. Walk south one block to
**237248.5, 70, 87296.5** and back: **T401 -> T442 -> T401**, switching at Z=87296,
with no No tile interval. Compare the map boundary/hover at two zoom levels.
A console `tp _Danye_ 237248.5 70 87295.5` is suitable only after staff verifies the
connected player is already in dimension 100; vanilla tp does not change dimension.
**No teleport was executed.** Surface readings are point-in-time terrain/headroom
checks, not a guarantee against later world changes or mobs.

Additional verified X/Z controls (console `conquest resolve 100 X Z`):

| Area | X | Z before -> after | Expected |
|---|---:|---:|---|
| Rhun | 237248 | 87295 -> 87296 | T401 -> T442 |
| Nindalf | 73024 | 52223 -> 52224 | T340 -> T351 |
| Newly filled land seam | -20032 | -31489 -> -31488 | T033 -> T035 |
| Weathertop pilot | 21119 -> 21120 | -320 | T149 -> T132 |
| Protected river control | 34944 | 640 | IN_BOUNDS_GAP / No tile |

Dimension 0 at (34944,640) remains UNSUPPORTED_DIMENSION. Nindalf/land/pilot surface
probes encountered water, vegetation or canopy: these X/Z controls are **not**
teleport destinations with an assumed Y. Terrain must be checked locally before
positioning there. Surface probing may load/populate new test chunks through normal
world generation; no blocks, records, progression or permissions were edited.
River-gap width, other borders, hover alignment and manual crossings remain visual
checks for the user. No shared-server changes, staging, commits or pushes occurred.
