# KOME Server Records

Server Records has two top-level pages: `Players` and `Wars`. Visibility remains viewer-scoped. Operator all-record access is an explicit session-only toggle, defaults off after reconnect/restart, and does not grant alliance, conquest, movement, or Military T3 gameplay authority.

The Wars list supports `All`, `Active`, `Ending`, and `Ended` filters. Each row shows war name/ID, both side names with faction-style icons, status, creation time, latest tile event, active stewardship count, and warning state.

War detail is built from explicit server `WAR` records and includes:

- Side One and Side Two faction sets;
- membership history distinguishing `CAPTURE`, `MANUAL`, `AUTOMATIC_MILITARY_T3_SUPPORT`, and migrated legacy provenance;
- automatic support enrollment with native/supporting faction, side, current state, authorized king UUID/name, and exact dormant/contradiction/operator-removal reason;
- full tile-capture history with former/new owner, claimant, method, and time;
- stewardship company authorization records and bounded revocation history;
- active reservations and company IDs;
- pending withdrawal, demobilization, entity-removal, or admin-resolution state;
- administrative history;
- ending reason and lifecycle timestamps;
- contradictory-membership warnings.

Back returns to the previous Server Records page/filter. Refresh requests a new authoritative payload; the client never derives permission or war state from rendered strings.

Player detail keeps Controlled Tiles as a compact badge preview. Clicking the card, an individual badge, or the `+N more` badge opens an in-place, scrollable list containing every tile. Waypoint-linked entries display `Waypoint Name (TXXX)` and unlinked entries display `TXXX`; the tile ID remains a separate presentation field. Back returns without resetting the selected player or detail scroll.

The operator command view matches these lifecycles through `/war list active`, `/war list ending`, `/war list ended`, `/war list all`, and `/war status <id>`. Direct active opposition is always a warning and overrides a contradictory alliance/same-side permission until an operator explicitly resolves the record.

## Alliance detail records

Alliance detail is also server-authored. `TRACK` records carry each faction side's own unlocked tier, next target, rolled item, delivered amount, activity and Military population progress, completion/waiver/grace state, effective benefit, mutation flag, and exact rejection reason. The client does not reverse contributor and receiver to infer these values. Player records summarize the selected player's faction tiers, not a shared pair tier.

For Military T3, `MILITARY_CONTEXT` records carry the native/supporting direction, recognized kings, Voluntary Delegation/Wartime Stewardship/Dormant/Active/Contradiction state, authorizing wars, legal opponent union, one global eligible/used/available population pool, and exact unavailable reason. `MILITARY_COMPANY` records carry actual owner, temporary controller and current king status, war IDs, tendency, movement/cleanup state, reservation population, revocation reason, and the limited actions authorized for that viewer. Operators who merely enable Operator View receive visibility, not temporary-company action flags.

The Alliance screen uses typed requests for company navigation and movement history. The server rebuilds the company list and movement-history scope from the packet sender; client-provided pair/faction text cannot grant access.
