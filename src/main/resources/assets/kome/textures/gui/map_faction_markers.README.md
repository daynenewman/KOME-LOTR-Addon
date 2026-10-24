# Map company and Build artwork

These are client presentation assets, never faction/Build authority. Native artwork is **not copied into KOME**.

## Editable KOME PNGs

| File | Default source canvas | Logical GUI size at closest native zoom (scale 16) | Use |
|---|---|---|---|
| `defensive_build.png` | Owner-edited 17 x 15 RGBA | 10 x 10 | Defensive Builds, all factions |
| `neutral_shield.png` | 64 x 64 RGBA | 18 x 18 | Unsupported/unresolved company shield |
| `neutral_banner.png` | 64 x 128 RGBA | 10 x 20 | Unsupported/unresolved Normal Build banner |

Edit these standalone files directly in a pixel editor, retaining RGBA transparency. Neutral shield/banner canvases retain their square/portrait aspect ratio. The castle accepts any positive canvas within the image-size limit and samples its entire canvas into the square logical bounds; a square canvas avoids distortion. The current owner-edited PNG is preserved. Do not edit `bridge.png`, `river.png` or `map_button_icons.png` as part of this workflow. Source resolution is independent of displayed size; changing the pixels does not require Java edits. Logical dimensions are centralized in `KOMEMapMarkerArt.Sprite.bounds`.

Native shields use the same 18 x 18 closest-zoom bounds, and native banners 10 x 20. LOTR's eight settled map zoom powers are -3 through 4, corresponding to native `zoomScale` 0.125 through 16. The percentage column names the eight-view progression from widest to closest; it is not the native `zoomScale`. KOME now applies the existing size curve to that progression while leaving alpha and map anchors tied to actual native zoom.

| View progression | Native `zoomScale` | Shield | Banner | Castle |
|---|---|---|---|---|
| 12.5% | 0.125 | 7x7 | 5x9 | 5x5 |
| 25% | 0.25 | 8x8 | 5x11 | 5x5 |
| 37.5% | 0.5 | 9x9 | 6x12 | 6x6 |
| 50% | 1 | 9x9 | 6x12 | 6x6 |
| 62.5% | 2 | 12x12 | 7x14 | 7x7 |
| 75% | 4 | 14x14 | 8x16 | 8x8 |
| 87.5% | 8 | 17x17 | 9x19 | 9x9 |
| 100% | 16 | 18x18 | 10x20 | 10x10 |

The previous plateau came from applying the accepted 0.125–1 size curve directly to native `zoomScale`: it reached 18x18 at the middle native stop (scale 1), then stayed 18x18 through scales 2, 4, 8 and 16. Sprite sizing now maps native `log2(zoomScale)` continuously from powers -3..4 onto 0.125..1 before that same curve. The local sine-squared delay remains smooth and monotonic. There are no zoom-stop size tables in production. Rounded GUI pixels still change one pixel at a time during animation. The 37.5% and 50% stop sizes match, avoiding the former 18-to-9 jump; actual-zoom alpha still rises from 191 to 255 across those stops. Opacity, labels and geography use actual zoom; geography retains exact map-relative scaling. Rendering, clipping, hover and picking consume the same final bounds.

The final shield polish adds a smooth 0.75-GUI-pixel unrounded bump centered on native scale 2, yielding a 12x12 rounded shield there. It reaches zero with zero slope at native scales 1 and 4, so the other seven settled shield sizes stay unchanged. Native and neutral fallback shields share the adjustment. Banner and castle sizing, opacity and asset UVs are unaffected.

To override through a client resource pack, use the same paths beneath `assets/kome/textures/gui/`. Reload resources (normally F3+T) to refresh both validation and Minecraft's texture objects. KOME textures use nearest filtering. Keep critical details large enough to survive the small GUI footprint. Transparent corners are part of the shared rectangular interaction canvas; there is no per-pixel picking.

## Native LOTR resources and UV contract

- Shields: `lotr:shield/alignment_<name>.png`, native 64 x 32. Draw the left/front 32 x 32 face: normalized UV `(0,0)` to `(0.5,1)`. The right half remains native back-face art and is not drawn on the map.
- Banners: `lotr:item/banner/banner_<name>.png`, native 128 x 128. Draw the 32 x 64 cloth area: normalized UV `(0,0)` to `(0.25,0.5)`. The rest of the canvas is transparent. The separate `lotr:textures/items/banner_*.png` inventory icons are not used.
- Shield locations come from `LOTRShields.getTexture()`. Banner locations use the verified native `getBannerTexture` formula with `BannerType.bannerName`; calling the entity renderer itself would initialize a GL-reading frustum. A bytecode test verifies the formula against the bundled native jar without initializing that renderer.

Native textures always resolve and bind their **original namespaced ResourceLocation** through the active resource manager and Minecraft's TextureManager API. Marker registrations are now owned by a **private KOME TextureManager instance**, not the globally shared native registrations. Resource packs override `assets/lotr/shield/...` and `assets/lotr/item/banner/...` normally, with the highest-priority pack winning. Native blur/clamp metadata is passed to the upload; KOME does not mutate shared native texture filters. There are no copied native PNGs, extracted sprite caches, generated native atlases or permanent decoded native pixel arrays.

Decode, validation and upload run once on resource reload through the supplied IResourceManager, not during marker rendering. Temporary images/readers/streams are released; only descriptors, availability and owned GL IDs remain. There are at most 51 fixed resource slots (48 native textures and three KOME textures), never a new slot per company or frame. Each reload deletes prior owned GL IDs and replaces the private manager/objects; failed slots remain KOME objects marked unavailable, never Minecraft's sticky missing-texture substitution. Later repaired resources are decoded/uploaded anew. Ordinary IO, corrupt/null-image and layout failures stay inside this loading boundary. No original shared native registration is replaced, deleted or repaired by KOME. The private manager is not registered in Minecraft's global reload-listener list; the overlay supplies the current resources after reload. Loaded installation-local artwork can remain across world disconnects, like ordinary client textures; no world/company records or decoded images are retained by this cache.

Images above 1,048,576 pixels are rejected before decoding. Compatible integer-resolution replacements are supported. Shield canvases must retain the 2:1 two-face layout with nonempty, partly transparent faces; banner canvases must remain square with nonempty cloth inside the top-left quarter-width/half-height region and transparency elsewhere. A detectable missing, malformed or incompatible native asset selects the corresponding neutral KOME sprite. If even that custom fallback is missing/invalid, omit the marker and its hover/picking rather than showing an invisible interactive target. Invalid castle art is likewise omitted.

Minecraft 1.7.10's shared TextureManager runs before the mod's later reload callback. Its IOException branch can install a missing DynamicTexture whose reload never rereads the original path. A null image in SimpleTexture can instead throw a ReportedException; Minecraft.refreshResources catches RuntimeException and removes selected packs. KOME's owned marker path avoids both failure modes without globally intercepting reloads. **It cannot protect unrelated or independently shared native registrations:** if LOTR equipment or another renderer has registered the same broken asset globally, that engine path can still abort reload/remove packs before KOME runs. A globally sticky missing texture may likewise remain missing in other native renderers even while the KOME marker recovers correctly. No global cache mutation or guarantee of arbitrary broken-pack isolation is claimed.

Not every resource pack preserves these nonstandard layouts. The checks catch dimensions, empty art, missing two-face transparency and banner art moved outside the verified region. They cannot infer the semantic meaning of arbitrary replacement pixels that still satisfy those checks. Packs that rearrange native art need a compatible override or will require live inspection; arbitrary custom UV metadata is not supported.

## Mapping inventory

Among the 24 bundled keys, 22 have shields and 23 have banners. Bree, Angmar, Gundabad, Dale, Dorwinion, Dunland, Rohan, Gondor and Mordor use matching lowercase suffixes in both native families. Exceptional suffixes:

| KOME key | Shield suffix | Banner suffix |
|---|---|---|
| hobbit | neutral | hobbit |
| dunedain | ranger | ranger |
| bluemountains | blue_mountains | blueMountains |
| highelves | high_elf | highElf |
| woodelf | wood_elf | mirkwood |
| dolguldur | dol_guldur | dolGuldur |
| durinsfolk | dwarf | durin |
| lothlorien | galadhrim | lothlorien |
| isengard | uruk_hai | isengard |
| fangorn | neutral | neutral |
| rhudel | rhun | rhun |
| harad | near_harad | nearHarad |
| morwaith | moredain | moredain |
| taurethrim | tauredain | tauredain |
| halftroll | half_troll | halfTroll |

Explicit extra mappings: Umbar uses native `umbar` shield/banner; Nomad has `haradNomad` banner only. Utumno, Far Harad, blank and unknown keys stay neutral. Canonical aliases such as RANGER_NORTH/NEAR_HARAD are normalized; regional native cosmetic variants are not selected by a first-match enum scan.

Company marker IDs join exact client `armyCompanies` keys. A recognized `nativeFaction` takes precedence over `faction`; a known faction lacking a shield stays neutral. An unknown/empty native identity may fall back to the company's faction. Before the first completed conquest snapshot, a physical marker missing its company snapshot may use neutral art. Once a completed baseline exists, a missing company ID invalidates the marker immediately, including late unit packets; it cannot become a stale neutral shield. Matching current snapshots restore valid artwork without an asset reload. Strategic-only markers wait for a matching stationed company snapshot and an existing tile presentation anchor. Company names, viewer allegiance and tile ownership are never substitutes. Exact company IDs define ascending sprite draw order, and the last actually visible overlapping company owns the tooltip. Companies are not merged or clustered. The deleted legacy troop PNG is not restored: aggregate totals retain existing text/zoom eligibility without an emblem, and active movement retains its existing neutral current-position diamond. Normal Builds use `populationFaction`; Defensive Builds use the castle. Null/unrecognized Build types use the neutral banner, without inferring authority.

Build sprites render only in the validated terrain-exit pass, below native waypoint icons/names and region labels. Post handles their tooltips, not another sprite pass. Unsupported/failed hooks omit Build sprites and their picking rather than obscure labels with a Post fallback. `populationFaction` is population allegiance, not independently captured Build control; controller banners need a future campaign/siege authority implementation.

## Independent review and live acceptance

Review original resource identities, verified UVs, bounded reload-only loading, private registration ownership, missing-asset fallback and exception-safe upload/draw GL scopes. Check exact company joins, no tile-faction inference, Build type selection, shared drawn/hover/click bounds, stable topmost Build ordering and unchanged layer/control/destination precedence. The native terrain/territory/geography/waypoint/label hooks and border caches are unchanged. Headless recovery tests execute actual TextureManager/SimpleTexture cache/reload logic with driver operations, Forge progress UI and crash-report environment collection isolated; this is not live driver validation.

In Minecraft, inspect every mapped faction at far/normal/close zoom, overlapping Normal/Defensive Builds, count labels, map edges, controls/panels and waypoint names. First draw/bind a valid marker. Install compatible native shield/banner replacements and custom castle/fallback replacements, change pack priority, then reload with the map open. Introduce a missing/IO-failing or corrupt marker replacement, confirm neutral fallback, repair it and reload without restarting. Repeat valid-to-valid and compatible HD pack switches. Confirm original source paths, preserved UV selection, restored art and no growth of owned texture IDs across repeated reloads. Test incompatible layouts and omitted custom assets for invisible click targets. If the engine removes a broken pack before KOME's callback, record whether the same asset was already used by native equipment/other renderers; that is the documented global-engine limitation. Live GL-driver behavior and resource-pack artwork acceptance remain separate from deterministic headless tests.
