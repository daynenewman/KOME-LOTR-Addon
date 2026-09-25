# Tile-world milestone checkpoint - 2026-09-20

This checkpoint builds on foundation commit `04726458229057706e83b059e98e009f9aa4ac7e`.
The foundation already contains exact common resolution, atomic Build validation,
owner-choice population, deliberate Build confirmation and cancellation coverage.

## Completed scope

- Current-tile HUD: the common resolver samples the actual player each client tick;
  session/dimension/death/resource transitions clear stale display. Persisted
  `config/kome-client.cfg` option `hud.showCurrentTile`, enabled by default;
  Controls > KOME > Toggle current tile HUD is unbound by default.
- Explicit validated gameplay defaults preserve connections/types, routing references,
  arrivals, waypoint choices and route markers independently of raster geometry.
  WorldData overrides remain authoritative. No migration or rule changes.
- Approved 95-cell pilot, 93,103-cell zero-extra-buffer V2 additions, then the exact
  final 4,915 edits (4,003 V2 corrections + 912 gap fills). Final geometry retains
  621 IDs and 622 components, protected water/gaps and pre-V2 territory.
- Borders derive shared edges and gap edges from immutable raster snapshots;
  hover, fill, borders and selection share the map transform. Bounded texture slots,
  GL state restoration and bounded Tessellator batches are included. These map
  presentation fixes do not address the separate native window-maximize issue.
- Associated Java 8 tests, all fixture dependencies, accepted manifests and pinned
  gameplay-export provenance are included. No superseded candidate images, world
  snapshots, runtime agents, binaries, private records or backups are included.

## Validation reused, not rerun

The latest successful `gradlew.bat clean test build --no-daemon --console=plain`
reported **814 discovered, 812 passed, two existing Windows symlink skips, zero
failed/errored**; the focused suites passed 138 tests. All **6,055 production
routing/initialization/reload expectations** passed. The current production Java
files equal the verified pre-install recovery; only the approved mask changed
then. All 27 KOME resources equal the validated JAR bytes, no source file is newer
than that build, and its JAR hash is unchanged. This checkpoint changes no Java or
production resource. Only documentation and offline reproduction packaging change.

- Installed mask SHA-256: `ab792277f61882d415963bf5af1b8d2705458c68de80f5b3cbb9102e30d1b4a7`.
- Validated JAR SHA-256: `a4a46b6e5f588f8266faf8b512f527afc5485a780f3c43b5cac100989a52485e`.
- Combined manifest SHA-256: `72a21e60160994b9054e37eb3db4ce7d30ecb55152c7c0c61d9a365779dfa6f4`.

Previously recorded runtime evidence: dedicated startup plus ten final-geometry
console checks passed; final stopped-save comparison found no membership changes
among 1,515 saved coordinates, including B1-B4. These are dated evidence, not a
new live-world inspection. Runtime was not refreshed for this checkpoint.

## User acceptance and remaining checks

The user's latest **"it works"** is recorded only as acceptance of the tested
**Rhun T401 -> T442** boundary at X=237248.5, across Z=87296, in Middle-earth.
The preceding console teleport and settled position were verified at
(237248.5,70,87295.5), with an empty operator list. This accepts that tested
boundary only: **not the entire map, all border rendering, or the remaining checklist**.
Earlier reports of other crossings, public access and Build form behavior remain
limited to their separately documented observations.

Still pending: Nindalf/other corrected boundaries in-game; river corridor width;
hover/selection alignment at multiple zoom and odd GUI sizes; F1/HUD toggling,
disconnect/reconnect and F3+T behavior; representative performance on target hardware.
The separate native window-maximize bug remains outside this checkpoint.

Excluded geography remains excluded: the separate Weathertop (974,727) transfer,
blocked 55-cell proposal, fragment-producing transfers, optional shape changes,
and uncertain geography. (974,727) remains T149. R1 (1083,735) and (2291,58)
remain gaps; (2292,58) remains T001. Preserve the 19 legacy destination exceptions;
new destination changes need explicit review, not automatic relocation.

## Reproduction from committed files

Run from the repository root with the already used Python/Pillow/NumPy tooling:

```powershell
python docs/tile-milestone-checkpoint-20260920/verify-geometry.py
python docs/gameplay-separation-implementation-20260919/export-baseline.py --output build/reports/checkpoint-gameplay-export
```

The first command replays only accepted manifest rows in memory and compares all
pixels with the installed mask; it writes no resources. It reuses the existing
scanline component helper, not the old audit's baseline-specific main program.
The second command exports from pinned original gameplay captures, never from
current geometry. Normal Gradle builds do not regenerate these tables.
Production-loader/fractional/Build/HUD/rendering/gameplay verification remains in
the included Java tests. Neither tool edits worlds or requires a running server.

Accepted manifests: pilot `docs/weathertop-seam-candidate-20260919/edit-manifest.csv`;
V2 `docs/land-seam-candidate-v2-20260919/edit-manifest-buffer0.csv`; final
`docs/final-combined-geography-20260920/edit-manifest.csv`. The original 4,003 and
912 input manifests are retained for provenance. All original proposal files
remain unchanged locally. Historical reports may reference deliberately uncommitted
preview images, machine-specific runtime logs or intermediate candidates; they are
not dependencies of the reproduction commands or the build. Use this checkpoint
record and `docs/KOME_TILE_WORLD_RESOLUTION.md` for current acceptance/scope.

## Checkpoint-specific checks

The existing Git setting is `core.autocrlf=true`. Narrow `.gitattributes` entries
now preserve checksum-pinned gameplay tables, archived capture inputs, approved
manifests and test fixtures byte-for-byte on checkout. No dataset content changed.
The standalone accepted-manifest replay passed (95 + 93,103 + 4,915 rows), with
621 IDs / 622 components and a component bijection. The pinned gameplay exporter
reproduced all four packaged CSV hashes. These checks address reproducibility of
the selected checkpoint; the completed full Java suite was not repeated.
