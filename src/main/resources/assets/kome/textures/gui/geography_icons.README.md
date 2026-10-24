# Editable local geography icons

Edit these standalone files directly in a pixel editor:

| File | Source canvas | Rendered canvas at map zoom 1 |
|---|---:|---:|
| `bridge.png` | 64 x 32 RGBA pixels | 6 x 3 GUI pixels |
| `river.png` | 64 x 32 RGBA pixels | 4 x 2 GUI pixels |

The bridge template depicts a wooden crossing with stone ends; the quieter river
uses two muted water strokes. They are starting artwork for manual editing.
These PNGs are the assets; no Java drawing or build-time image generator replaces
them. The map-button sprite sheet and troop artwork are separate.

## Editing contract

1. Keep the 64 x 32 canvas, filenames and paths. Use RGBA PNG with a transparent
   background. Keep the artwork within the canvas; transparent padding is allowed.
2. Use a pixel editor's nearest-neighbor tools. Avoid flattening onto white or
   checkerboard backgrounds. White painted pixels and partial alpha are supported.
3. Save/export over the corresponding PNG. Build the client resources normally,
   or use a matching resource-pack path and native resource reload to preview
   edits. No Java changes are needed for artwork edits.

Both icons use the full canvas through the native resource texture manager.
There is no crop-to-content, near-white removal, alpha rewriting or dynamic bridge
texture. Transparent margins never change artwork scale or alignment. Source
resolution is independent of logical display size. Nearest filtering is applied
by the existing scoped sprite renderer.

## Display and interaction

Rendered dimensions equal logical dimensions times actual native animated map
zoom, with no minimum, maximum or integer-size rounding. At zoom 0.125, bridge
canvas size is 0.75 x 0.375 GUI pixels; river is 0.5 x 0.25. The existing opacity
curve remains. Subpixel icons can disappear or occupy one raster pixel at far zoom.

Projected anchors remain unchanged. Logical origins are (-3, -1) for bridges and
(-2, -1) for rivers relative to their anchors at zoom 1; both offsets scale exactly.
Clipping and tooltip bounds use the same canvas, including transparent spaces,
without enlarged hit padding.

Automatic, manual and route bridges use the same PNG without a diamond badge.
Geography sprites draw at the validated native terrain exit before native waypoint
names and region labels; tooltips remain in the later UI pass. If that integration
fingerprint is rejected, geography sprites are omitted rather than painted above
native names. The territory fallback remains unchanged. Native roads may cross
over geography icons at this early seam.
