# Editable KOME map button pictograms

Edit `map_button_icons.png` directly, keeping its **128 x 32 RGBA** canvas and transparent background. No Java changes are needed to replace the symbols. Use a pixel editor with nearest-neighbor scaling and no antialiasing. The adjacent `.png.mcmeta` requests nearest filtering (`blur: false`) and clamping. A resource pack may override both files at `assets/kome/textures/gui/`.

The fixed grid has **8 x 8 pixel cells**, 16 columns and 4 rows. Only columns 0-8 and rows 0-2 are used. Each cell is drawn at its original 8 x 8 size, inset one pixel inside a native 10 x 10 LOTR button. Transparent pixels reveal the native background. The placeholder symbols occupy seven pixels, leaving the last row/column transparent; replacement art may use all eight.

| Column | Control | Row 0 | Row 1 | Row 2 |
|---|---|---|---|---|
| 0 | Palette | Classic LOTR | Current KOME | Input locked (reserved) |
| 1 | Territory mode | Full fill | Borders only | Input locked (reserved) |
| 2 | Fill opacity | Opacity symbol, all percentages | Reserved | Input locked (reserved) |
| 3 | Individual tile borders | On | Off | Input locked (reserved) |
| 4 | Conquest layer | On | Off | Input locked |
| 5 | River markers | On | Off | Input locked |
| 6 | Bridge markers | On | Off | Input locked |
| 7 | Troop markers | On | Off | Input locked |
| 8 | Build markers | On | Off | Input locked |

Cell origin is `(column * 8, row * 8)`. UV coordinates divide by 128 horizontally and 32 vertically. Off-state placeholders are red with a diagonal stroke; locked placeholders are muted brown. Palette cells use different color samples; territory cells use fill versus outline. No runtime tint or code-drawn status overlay changes your supplied colors. Opacity percentage remains in the tooltip.

This sheet contains **pictograms only**. Native tan frames, including locked appearance, continue to come from `lotr:map/mapScreen.png`; no LOTR assets or widget definitions are modified. Reload resources after replacing the sheet in a running client. The owner has accepted the current map-control artwork in Minecraft; live resource-pack switching remains untested.
