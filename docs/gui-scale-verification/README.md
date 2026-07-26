# KOME GUI scale verification screenshots

Captured from the schema-7/Build visual source on 2026-07-26 with deterministic long-data fixtures. Each image is the full display resolution.

| Screen | Small 1280x720 | Normal 1280x720 | Large 1280x720 | Auto 1600x900 | Minimum 854x480 Auto |
|---|---|---|---|---|---|
| Alliance list | [image](small-alliance-list.png) | [image](normal-alliance-list.png) | [image](large-alliance-list.png) | [image](auto-alliance-list.png) | [image](min-854x480-alliance-list.png) |
| Alliance create | [image](small-alliance-create.png) | [image](normal-alliance-create.png) | [image](large-alliance-create.png) | [image](auto-alliance-create.png) | [image](min-854x480-alliance-create.png) |
| Overview | [image](small-alliance-overview.png) | [image](normal-alliance-overview.png) | [image](large-alliance-overview.png) | [image](auto-alliance-overview.png) | [image](min-854x480-alliance-overview.png) |
| Requirements | [image](small-alliance-requirements.png) | [image](normal-alliance-requirements.png) | [image](large-alliance-requirements.png) | [image](auto-alliance-requirements.png) | [image](min-854x480-alliance-requirements.png) |
| Benefits | [image](small-alliance-benefits.png) | [image](normal-alliance-benefits.png) | [image](large-alliance-benefits.png) | [image](auto-alliance-benefits.png) | [image](min-854x480-alliance-benefits.png) |
| Military | [image](small-alliance-military.png) | [image](normal-alliance-military.png) | [image](large-alliance-military.png) | [image](auto-alliance-military.png) | [image](min-854x480-alliance-military.png) |
| Break confirmation | [image](small-alliance-break-confirmation.png) | [image](normal-alliance-break-confirmation.png) | [image](large-alliance-break-confirmation.png) | [image](auto-alliance-break-confirmation.png) | [image](min-854x480-alliance-break-confirmation.png) |
| Alliance ledger | [image](small-alliance-ledger.png) | [image](normal-alliance-ledger.png) | [image](large-alliance-ledger.png) | [image](auto-alliance-ledger.png) | [image](min-854x480-alliance-ledger.png) |
| War records | [image](small-war-records.png) | [image](normal-war-records.png) | [image](large-war-records.png) | [image](auto-war-records.png) | [image](min-854x480-war-records.png) |
| Allied tile confirmation | [image](small-allied-tile-confirmation.png) | [image](normal-allied-tile-confirmation.png) | [image](large-allied-tile-confirmation.png) | [image](auto-allied-tile-confirmation.png) | [image](min-854x480-allied-tile-confirmation.png) |
| Pledge departure | [image](small-pledge-departure-preview.png) | [image](normal-pledge-departure-preview.png) | [image](large-pledge-departure-preview.png) | [image](auto-pledge-departure-preview.png) | [image](min-854x480-pledge-departure-preview.png) |
| Tile Command — Build creation | [image](small-tile-build-create.png) | [image](normal-tile-build-create.png) | [image](large-tile-build-create.png) | [image](auto-tile-build-create.png) | [image](min-854x480-tile-build-create.png) |
| Tile Command — Build detail | [image](small-tile-build-detail.png) | [image](normal-tile-build-detail.png) | [image](large-tile-build-detail.png) | [image](auto-tile-build-detail.png) | [image](min-854x480-tile-build-detail.png) |
| Tile Command — Population | [image](small-tile-population.png) | [image](normal-tile-population.png) | [image](large-tile-population.png) | [image](auto-tile-population.png) | [image](min-854x480-tile-population.png) |

The matrix contains 70 PNGs. The alliance images use the unified directional ladder. The Tile Command captures exercise the responsive typed-hour form, grouped Build detail with one context-aware destructive action, and faction-owned segmented population graphs. The allied-tile capture also exercises the Builds-first Tile Command fixture behind the confirmation. Scroll viewports intentionally clip content below their boundaries; visible proportional scrollbars and fixed actions show that clipped content remains reachable.

The profile controller explicitly applies Small (scale 1), Normal (scale 2), Large (scale 3), Auto at 1600x900, and Auto at the minimum supported 854x480 resolution before capture. Tile Command's constrained logical transform is shared by drawing, hitboxes, fields, tooltips/dialogs, and scissoring.

These deterministic captures are visual regression evidence, not a substitute for the multiplayer/manual matrix in `../KOME_TEST_PLAN.md`. In particular, the captures cannot validate real typing/focus behavior or destructive packet outcomes, and the ledger twin cannot validate the real container's slots, dragging, shift-click, or hitboxes.
