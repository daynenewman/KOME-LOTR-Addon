# KOME GUI handoff

Current source-of-truth handoff for addon 1.0.7 and alliance schema 5, updated 2026-07-19 after the dedicated visual pass.

Historical descriptions and superseded recommendations are isolated in `archive/KOME_GUI_HANDOFF_LEGACY_PRE_VISUAL_PASS.md`. Do not use the archive to infer current behavior.

## Visual language

KOME screens now share a muted dark-parchment and stone presentation:

- near-black stone outer panels and dark brown parchment cards;
- warm gold headings, borders, selected tabs, progress, and focus;
- restrained faction colors on badges and narrow row accents;
- green for active and completed state;
- amber for pending, provisional, succession/grace, and warnings;
- red for denied, contradictory, and destructive state;
- pale parchment text with muted secondary copy and visibly dimmed disabled actions.

The style is implemented by source primitives rather than copied screen-local color blocks.

## Shared components

| Component | Source | Current role |
|---|---|---|
| Panels, cards, headers, dividers | `KOMEGuiTheme` | Main/sub panels, cards, hierarchy, slots |
| Wrapped text and tooltips | `KOMEGuiTheme` | Width-aware copy and disabled-action explanations |
| Faction badges | `KOMEGuiTheme` | Darkened faction-color badges with readable labels |
| Status chips | `KOMEGuiTheme` | Active, complete, planned, warning, denied, locked, neutral |
| Progress bars | `KOMEGuiTheme` | Quota, population, and contribution progress |
| Warning banners | `KOMEGuiTheme` | Grace, kingless, war, capture, departure, and denial notices |
| Styled buttons | `KOMEGuiButton` | Default, dark, tab, destructive, hover, selected, disabled |
| Scaled scroll viewport | `KOMEGuiScrollPanel` | Clamped wheel state, scaled scissor, proportional scrollbar |
| Confirmation dialog | `KOMEGuiConfirmationDialog` | Wrapped destructive/capture confirmation with cancel path |

`KOMEGuiTheme.enableScissor` clamps the logical viewport to the current `ScaledResolution` before converting it to raw OpenGL coordinates. `KOMEGuiScrollPanel` is the preferred screen-level wrapper around that implementation.

## Alliance list and request flow

Source: `KOMEGuiAlliance`

The default page is a mutual-pair record list. Each row has a faction accent, pair name, strongest agreement, Civil/Military/Trade status chips, pending state, king/kingless state, and succession/grace footer. Rows use a scaled scissor viewport and proportional scrollbar.

The header separates the viewer faction, record/config summary, and authority state into three non-overlapping columns. Operator visibility is an explicit session toggle and defaults off; being an operator does not automatically force all-record view.

The request page cycles only through server-provided `REQUEST_OPTION` candidates. Sender, type, receiver, eligibility reason, and disabled-state explanation are presented in the form. Short screens use a compact one-line card layout so selectors and Send remain reachable.

## Alliance detail

Source: `KOMEGuiAllianceDetail`

The selected pair and tab survive authoritative refresh. All four tabs use a single scroll viewport with a fixed action bar:

- **Overview** — type selector, grace/kingless/pending/war warning, current tier, next objective, per-side progress, rolled quota, and unlocked benefits.
- **Requirements** — three authoritative per-track cards with contribution, partner progress, milestones, remainder, validity, completion/waiver/grace, and server-authorized roll action.
- **Benefits** — the shared benefit catalog with Active, Locked, Suspended, and Planned status.
- **Military** — native owner/controller context, recognized kings, authorizing wars, opponents, global offensive population, companies, cleanup/movement state, reason, and allowed actions.

Short screens use a compact relationship header; lifecycle detail remains in the scrollable warning banner. The content viewport therefore remains usable at Large scale and 854x480.

Pending accept and request-cancel state are explicit. Break and Cancel Request are destructive buttons and open `KOMEGuiConfirmationDialog`; Escape and Cancel dismiss without a packet.

## Authoritative Alliance ledger

Sources: `KOMEGuiAllianceLedger`, `KOMEContainerAllianceLedger`, `KOMEQuotaLedgerOverlay`

The visual pass did not create a second inventory, quota model, or progression path. `KOMEContainerAllianceLedger` remains the authoritative storage and slot implementation.

The presentation adds directional faction badges, deposit/claim status chips, track cards, progress bars, quota summary, read-only/deposit explanation, authoritative slot row, player inventory, direction switch, claim reason, and disabled-state feedback.

The 620x460 logical container is responsively scaled when the current GUI coordinate space is smaller. Drawing, hover, click, drag, and release coordinates are mapped through the same scale, so the original authoritative slots remain the interaction targets.

## War records

Source: `KOMEGuiServerRecords`

Players/Wars mode and All/Active/Ending/Ended filter survive refresh. The War list uses coalition faction marks, status chips, warning state, and a scaled list viewport. Detail uses a separate scaled viewport with coalition badges, wrapped provenance/support/capture/stewardship/admin records, a dedicated warning banner, status/date state, and end reason.

Refresh no longer clears the current mode, filter, list selection, or reasonable scroll before replacement records arrive.

## Allied-tile confirmation

Source: `KOMEGuiConquestCapture`

Tile status remains visible behind the modal. When the server returns an armed allied/hostile confirmation, the dialog opens automatically with the authoritative warning and war destination. Confirm sends the existing claim packet; Cancel or Escape performs no mutation. The armed claim action is visually destructive.

## Pledge-departure preview

Source: `KOMEGuiPledgeDeparture`

The server-authored preview now uses the shared warning banner, dark cards, wrapped text, scaled scroll viewport, proportional scrollbar, and fixed Back/Refresh actions. Units/companies, population/movement, offers/transfers, unloaded/quarantined records, funding sources, and the preservation warning remain preview-only.

## Responsive and visual verification

The property-gated `KOMEGuiVisualCaptureController` is registered only when `-Dkome.guiCapture=true`. Normal clients never instantiate it. It renders deterministic long-data fixtures and exits after capture.

The suite covers 11 states at each requested setting: list, create, Overview, Requirements, Benefits, Military, break confirmation, ledger, War records, allied-tile confirmation, and pledge departure.

| Setting | Display | Captures | Result |
|---|---:|---:|---|
| Small | 1280x720 | 11 | Pass |
| Normal | 1280x720 | 11 | Pass |
| Large | 1280x720 | 11 | Pass with compact layouts and scrolling |
| Auto | 1600x900 | 11 | Pass |
| Smallest supported | 854x480, Auto | 11 | Pass with compact layouts and scrolling |

Evidence and a complete linked matrix are in `gui-scale-verification/README.md`.

The ledger capture uses a container-free visual twin because LOTR's global pouch GUI hook dereferences a live player for every `GuiContainer` during menu-only capture. The production `KOMEGuiAllianceLedger` uses the same layout and responsive transform; its authoritative container/storage code is unchanged.

## Classes changed or added by the visual pass

| Class | Change |
|---|---|
| `KOMEGuiTheme` | Reworked palette; reusable faction/status/progress/warning/wrapping/scissor primitives |
| `KOMEGuiButton` | Reusable tab/destructive/dark styles and complete state rendering |
| `KOMEGuiScrollPanel` | Added reusable scaled scrolling component |
| `KOMEGuiConfirmationDialog` | Added reusable wrapped modal confirmation |
| `KOMEGuiAlliance` | Redesigned pair list, header, compact request form, shared scrolling |
| `KOMEGuiAllianceDetail` | Redesigned four-tab detail, warnings, compact mode, scrolling, break modal |
| `KOMEGuiAllianceLedger` | Direction/status/progress presentation and responsive authoritative container transform |
| `KOMEGuiServerRecords` | Completed visual War list/detail and retained refresh state |
| `KOMEGuiConquestCapture` | Completed armed allied/hostile confirmation presentation |
| `KOMEGuiPledgeDeparture` | Completed preview cards, warning, wrapping, and shared scrolling |
| `KOMEGuiVisualCaptureController` | Added property-gated deterministic scale regression capture |

`KOMEClientProxy` only registers the capture controller under the explicit system property. `build.gradle.kts` points `runClient` at the existing deobfuscated LOTR development jar so its packaged resources are available to the capture task; this does not alter the addon jar's runtime dependency declaration or the LOTR production jar.

## Before and after

| Area | Before | After |
|---|---|---|
| Shared style | Partial theme helper and inconsistent local rendering | One dark KOME language and reusable state primitives |
| Buttons | No complete shared state model | Hover, selected tab, disabled, dark, and destructive styles |
| List/request | Sparse pair list and fixed request geometry | Status-rich mutual rows, eligible-only flow, compact responsive form |
| Detail | Fixed header and locally managed scissor | Four coherent tabs, warnings, shared scroll, compact short-screen header |
| Ledger | Light inventory presentation with text-only status | Directional/status/progress hierarchy around the same authoritative slots |
| Wars | Data records in generic cards | Coalition/status/warning visual browser with retained filter/selection |
| Capture | Armed state shown as button text | Dedicated wrapped destructive modal using authoritative consequence text |
| Departure | Plain preview cards and local scissor | Warning-led shared visual presentation and scroll component |
| Destructive actions | Multi-click text convention | Modal confirmation with explicit consequences and cancel path |

## Remaining work

No required Alliance/War/ledger/capture/departure visual component from the schema-5 visual-pass request remains unimplemented.

Non-blocking future work is limited to user-acceptance testing against a populated multiplayer server and optional authored faction icon textures in place of generated faction badges. Neither changes the completed GUI wiring.
