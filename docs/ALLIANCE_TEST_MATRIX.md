# Alliance Verification Matrix

## Automated gates

From `KOME-LOTR-Addon`:

```powershell
.\gradlew cleanTest test --no-daemon
.\gradlew build --no-daemon
```

From the root LOTR workspace:

```powershell
.\gradlew compileJava --no-daemon
```

The final automated test count must be taken from the clean Gradle XML report. Current coverage includes alliance/quota/grace/waypoint/source isolation, retired-Produce source absence and migration cleanup, coalition persistence/provenance, automatic generic Military T3 enrollment, contradiction handling, recognized-king-only authority, supporting-king replacement, native-king revocation, overlapping wars, global 100% population, hostile confirmation, transfer atomicity, pledge cleanup/quarantine/tombstones, and transformer isolation.

Passing this suite is not live multiplayer proof. Every applicable manual row below remains a release gate.

## Fixtures and evidence

- `A/B/E/N`: arbitrary playable LOTR factions selected from the current registry for the relation/king conditions in the row. Any named factions below are examples only and must be repeated with a different valid set.
- `KA`, `KB`, `KE`: recorded kings; `MA`, `MB`: ordinary pledged members; `O`: permission-level-2 operator; `U`: unpledged player.
- `P-A/P-B`: known tiles owned by A/B; `P-U`: truly unclaimed tile; `W-*`: mapped waypoints in those tiles.
- `C-A`: A-owned offensive company; `S-B`: eligible kingless B stewardship company; `W1`: an active coalition war.
- Before persistence tests, retain a stopped-server world backup and exports/screenshots of relevant commands/GUI.
- Every evidence cell is mandatory if the result differs: server/client logs, screenshot/video, exact command/packet, player/faction/tile/unit/company/order/war IDs, configuration, before/after `KOME_ServerRules` NBT, and timestamps.

## A. GUI and backend-state comparison (3)

| ID | Players/roles | Starting state | Exact actions | Expected server result | Expected GUI result | Restart expectation | Failure evidence |
|---|---|---|---|---|---|---|---|
| A01 | KA, KB, O; A/B | Civil T1, Trade T0, no Military; unequal side progress | Run `/alliance get A B`, grace status both sides, quota `show`; open Overview/Requirements/Benefits | Commands return canonical pair and exact side data | T0 says Established; no Military row; sides/items/activity/population/deadlines match server | Same after restart/refresh | Logs, all tabs, command output, pair NBT |
| A02 | KA, KB | All tracks active; one side completed target, other locked | Open pair, ledger each direction, then deliver one valid stack | One directed ledger changes; completed side rejects extra credit | Viewer progress never appears under ally; contributor/receiver headers reverse correctly; completed and action-enabled never conflict | State and direction persist | Both ledger screenshots, inventory delta, TRACK payload/log |
| A03 | O pledged to A | At least two unrelated alliances | Open GUI with Operator View off; toggle on/off; reconnect | Off sends A-only records; on sends all; request authority unchanged; session clears on disconnect | Label accurately shows Off/On; list changes only while on; receiver selector unchanged | Reconnect/server restart returns Off | Packet/log records, three list screenshots, reconnect log |

## B. Shared quota and ledger concurrency (4)

| ID | Players/roles | Starting state | Exact actions | Expected server result | Expected GUI result | Restart expectation | Failure evidence |
|---|---|---|---|---|---|---|---|
| B01 | KA, MA; A/B | Accepted track with no roll | KA and MA click Roll/send command simultaneously | Exactly one persisted faction-shared assignment | Both clients show identical registry item, metadata, weight, quantity | Same assignment after restart | Packet order, assignment NBT, both screenshots |
| B02 | KA, MA | Open quota needs >2 stacks | Deposit simultaneously into same side ledger | Atomic capped delivery; no duplication/loss; overflow remains in source/storage | Both refresh to same delivered/required count | Count and inventories persist | Inventory before/after, server log, ledger NBT |
| B03 | KA, KB, O | Directed A-to-B ledger has stored, claim, recovery goods | A deposits; KB switches to A ledger and claims; repeat full inventory/drop path | Only A contributes; only receiving KB claims; each good leaves ledger once | Header, Can Deposit, Can Claim and empty state match direction | Claimed goods never reappear after two restarts | Video, inventory, claim/recovery NBT, entity drops |
| B04 | O, KA | Invalid open quota with delivered credit | Disable/low-max item; verify INVALID; run reroll; claim recovery | Replacement selected before mutation; Civil/Military credit becomes recovery; Trade claim goods retained once | INVALID blocks deposit; reroll shows new valid item; recovery visible/claimable | Invalid/new assignment and recovery persist | Config/show, old/new assignment, item counts, NBT |

## C. Civil benefits (8)

| ID | Players/roles | Starting state | Exact actions | Expected server result | Expected GUI result | Restart expectation | Failure evidence |
|---|---|---|---|---|---|---|---|
| C01 | MA; A | W-A owned by A; W-U unclaimed; intrinsic progression met | Travel to W-A then W-U | Both allowed; unclaimed still obeys native LOTR checks | Overlay says OWN then UNCLAIMED with owner/reason | Ownership/access same after restart | Waypoint/tile IDs, overlay, travel log |
| C02 | MA; A/B/N/E | W-B/W-N/W-E; Civil T1 only A/B | Attempt each at T1, then break to T0 and retry W-B | W-B allowed only at active Civil T1; friendly-without-T1 and enemy denied | Blue ally at T1; red exact denial at T0/N/E | Break denial persists | `/alliance get`, waypoint check, screenshots/log |
| C03 | MA, O | Map/countdown open on W-B | Transfer P-B to E or revoke Civil before bounce completes | Final original-packet guard re-resolves and denies stale state | Overlay may be stale briefly; rejection appears in GUI/chat; no teleport | New owner/revocation persists | Timed video, transfer/revoke log, target state |
| C04 | MA, O | W-B denied; native progression test waypoint also locked | Toggle restriction off; retry; enable persisted bypass; test cooldown/combat/sleep/region lock | Off/bypass ignores only KOME territory; native restrictions remain | DISABLED/BYPASS state and exact native outcome | Toggle and bypass persist; removal persists | Config, waypoint check, overlay and native denial logs |
| C05 | O | Exact configured LOTR v36.15 jar plus production KOME jar | Start dedicated/client; retain fingerprint test and startup line; open map | One final hook installed; incompatible jar fails closed | Overlay activates without native-map crash; optional reflection failure fails open | Repeat cold start | Jar/class hashes, manifests, full logs, map screenshot |
| C06 | MA; A/B | Civil T2; known eligible farmhand and ineligible civilian/combat classes; A farmer source | Hire each through normal NPC UI | Only eligible farmhand passes; A's real source/allocation/reserve is debited; B never charged | Population/source record matches hire; no abstract milestone | Relog retains one record/debit | NPC class/UUID, source tile/allocation, balances |
| C07 | MA | One tile-funded and one reserve-funded allied farmhand | Kill one, dismiss/remove the other using normal paths | Exact recorded source receives exact refund once | Unit list clears; A balance returns; B unchanged | Two restarts do not duplicate refund | Unit records, death/removal log, before/after pools |
| C08 | MA, O | Existing Civil T2 farmhand, then downgrade to T1/T0 | Attempt another hire; inspect old unit | New hire denied; existing unit remains owned/tracked and funded | Tier loss reason shown; old unit remains | Old unit survives relog/restart; no early release | Tier commands, NPC UUID, hire rejection, NBT |

## D. Trade benefits (7)

| ID | Players/roles | Starting state | Exact actions | Expected server result | Expected GUI result | Restart expectation | Failure evidence |
|---|---|---|---|---|---|---|---|
| D01 | KA, KB, MA | Mutual Trade T1, directed A/B rolls | Both sides deposit; opposite kings claim | Each side's rolled goods become claimable once by the opposite faction | Ledger direction, deposit/claim flags, recovery exact | No reappearing goods | Pair/ledger NBT, inventories, logs |
| D02 | KA, KB | 49 legitimate trades | Complete one real LOTR trade, then continue toward 250 | T1 reaches 50; cumulative count is not reset | T1 progress completes and T2 shows 50/250 | Counter persists | LOTR/KOME counters, TRACK records |
| D03 | KA, KB | Trade T2 with 249 trades and requirements complete | Complete the 250th trade | T2 completes without post/structure/population check and creates no runtime record | Benefits shows exact future Produce text and no action | Completion persists; no Produce tag appears | Counters, requirements, pair/world NBT |
| D04 | MA/O | Completed Trade T2 | Search commands/config, inspect all Alliance tabs and packets | No production command/config/packet/permission/action exists | No Production tab; only four pair tabs | Same after restart | Command help/tab completion, GUI/video, source/NBT scan |
| D05 | O | Development backup containing one genuine unclaimed retired pending item | Start, inspect recovery, save, cold restart twice | Item moves once to the contributing recovery ledger; retired tags disappear | Recovery is claimable through existing ledger only | No second or third recovery | Backup and three NBT/inventory snapshots |
| D06 | KA, KB | Multiple distinct Trade T2 pairs | Open every Benefits tab | Canonical pairs remain distinct but no per-player slot/entitlement is created | Each pair shows the same shared metadata title/description | Pair state persists; no extra records | Pair NBT, GUI screenshots |
| D07 | KA, KB | Completed Trade T2 | Break/recreate track; change pledge; wait real time | No timer/generator/claim/lock process runs | No hidden or delayed Produce action appears | Two restarts remain state-free | Logs, inventories, world NBT |

## E. Military T1/T2 benefits (7)

| ID | Players/roles | Starting state | Exact actions | Expected server result | Expected GUI result | Restart expectation | Failure evidence |
|---|---|---|---|---|---|---|---|
| E01 | MA; A/B | Military T1; eligible/ineligible B combat classes; A offensive sources | Hire each | Correct combat only; A source debited; B population never charged | Unit/source/funding shown under A | Records/debits persist | NPC classes/UUIDs, source pools, hire logs |
| E02 | MA | One active allied Military T1 unit for A/player/pair | Send duplicate GUI packets/commands and relog before second hire | One-active-unit cap remains; duplicates denied | Existing unit and rejection reason displayed | Restart cannot bypass cap | Packet capture, unit NBT/list, rejection |
| E03 | MA | Tile- and reserve-funded allied combat units | Death/remove/dismiss each | Exact source refunded once | Records clear and source count returns | Two restarts no duplicate refund | UUIDs, pools, removal logs |
| E04 | KA, KB, MA | Military T2; C-A enters B; B-owned company present | Dispatch both directions; inspect control; try claim/recruit/population use in ally tile | Mutual passage; owner retains control; tile owner gets none; no extra claim/recruit/pop rights | Route accessible; no owner/control reassignment | Ownership/access persist | Company/order/tile IDs, authority screens/logs |
| E05 | KA | Long route crossing B | Validate dispatch, departure, next step, arrival, pending spawn, restart, and tile-transfer checks by revoking at each checkpoint | Every checkpoint re-evaluates current access; no unchecked transition | Status/reason updates at exact boundary | Pending/restart path remains safe | One run per checkpoint, timestamps, movement NBT |
| E06 | KA | Revoke passage before next step | Observe ACCESS_HALTED; choose Stay; attempt deeper/unrelated tile actions | ACCESS_HALTED created; Stay prevents deeper move and unrelated tile action | Stay/blocked controls and exact reason | Halted state persists | Route/traversed list, command rejections, GUI |
| E07 | KA, KB | ACCESS_HALTED company with actual traversed route | Retreat, then restore access and Resume; repeat relog/restart/duplicate packet | Retreat follows traversed route; Resume only after access; no unit/company/pop duplication | Route/status/control accurate | Stable across restart | Unit count/UUIDs, population, order before/after |

## F. Movement access-loss timing (5)

| ID | Players/roles | Starting state | Exact actions | Expected server result | Expected GUI result | Restart expectation | Failure evidence |
|---|---|---|---|---|---|---|---|
| F01 | KA, O | Valid preview then revoke before dispatch | Preview, revoke, click confirm/dispatch | Dispatch rejected using current state | Confirmation closes/rejects with server reason | No order after restart | Preview/dispatch timestamps, packet/log |
| F02 | KA, O | Order waiting departure | Revoke during wait; trigger Continue | Departure denied and ACCESS_HALTED/held safely | No deeper route progress | Status persists | Order NBT, route index, log |
| F03 | KA, O | Order in transit toward B | Revoke before next-step scheduling and again before arrival in separate runs | Current step resolves safely; next/arrival boundary halts per policy | Accurate step, halt-after-arrival, reason | No duplicated spawn after restart | Step timestamps, entity UUIDs, chunks |
| F04 | KA, O | Pending spawn in B with chunk unavailable | Revoke, restart, load chunk | Pending spawn rechecks access before entity creation | Failed/ACCESS_HALTED reason, no ghost company | Repeated restart remains idempotent | Pending NBT, entity scan, chunk log |
| F05 | KA, O | Company at tile transfer boundary; traversed route recorded | Transfer owner, choose Stay then Retreat, restore and Resume | Transfer check halts; Stay/Retreat/Resume semantics exact | Route uses actually traversed tiles | No duplicate units/population | Transfer/order history, UUID/pool counts |

## G. Military T3 whitelist, delegation, and stewardship (35)

Common delegation fixture: KA personally owns `C-A`, A/B have Military T3, KB/MB are online, and `C-A` has no active order. Common evidence for every row: actor UUID, company/order/unit IDs, command or crafted packet, server rejection/acceptance log, before/after company/unit/population NBT, and GUI screenshot.

| ID | Action | Players/roles and start | Exact action | Expected server result | Expected GUI result | Restart expectation | Failure evidence |
|---|---|---|---|---|---|---|---|
| G01 | View | MB temporary controller | Open Companies/detail | Allowed read-only | Company visible; owner/faction unchanged | Same valid delegation | Common evidence |
| G02 | Dispatch | MB controller; valid route | Dispatch `C-A` through GUI/command | Allowed after current tier/route checks | Movement created under actual owner | Order persists | Common evidence |
| G03 | Continue | MB controller; waiting order | `/troops movement continue <id>` | Allowed alias; next boundary rechecked | Continued/current status | Persists | Common evidence |
| G04 | Halt | MB controller; moving order | `/troops movement halt <id>` | Allowed alias to stop/halt | Halted/status reason | Persists | Common evidence |
| G05 | Stay | MB controller; ACCESS_HALTED | `/troops movement stay <id>` | Allowed; no deeper action | Stay choice shown | Persists | Common evidence |
| G06 | Retreat | MB controller; traversed path | `/troops movement retreat <id>` | Allowed along traversed legal route | Retreating path shown | Persists | Common evidence |
| G07 | Resume | MB controller; access restored | `/troops movement resume <id>` | Allowed only after restored access | Active route resumes | Persists | Common evidence |
| G08 | Transfer | MB controller | Invoke transfer command/packet directly | Rejected; owner/controller/source unchanged | Rejection mirrored | Unchanged | Common evidence |
| G09 | Redelegate | MB controller | `/troops company C-A delegate <player>` and packet equivalent | Rejected | Rejection mirrored | Unchanged | Common evidence |
| G10 | Disband | MB controller | `/troops company C-A disband` direct | Rejected | Rejection mirrored | Company remains | Common evidence |
| G11 | Rename | MB controller | Invoke rename command/packet | Rejected | Name unchanged | Unchanged | Common evidence |
| G12 | Split | MB controller | Invoke split command/packet | Rejected | Membership unchanged | Unchanged | Common evidence |
| G13 | Merge | MB controller | Invoke merge command/packet | Rejected | Both companies unchanged | Unchanged | Common evidence |
| G14 | Add units | MB controller; spare unit | Invoke add-member packet/command | Rejected | Unit/company lists unchanged | Unchanged | Common evidence |
| G15 | Remove units | MB controller | Invoke remove-member packet/command | Rejected | Membership unchanged | Unchanged | Common evidence |
| G16 | Change membership | MB controller | Alter LOTR company assignment/direct packet | Server reconciles/rejects unauthorized structure | Membership/source unchanged | Unchanged | Common evidence |
| G17 | Change unit caps | MB controller | Send cap update for C-A unit | Rejected by owner authority | Cap unchanged | Unchanged | Common evidence |
| G18 | Consume population | MB controller | Invoke hire/reserve as C-A owner | Rejected; no pool debit | Counts unchanged | Unchanged | Common evidence |
| G19 | Return population | MB controller | Invoke refund/release directly | Rejected; no pool credit | Counts unchanged | Unchanged | Common evidence |
| G20 | Change faction | MB controller | Direct faction mutation packet/command | Rejected | A remains native faction | Unchanged | Common evidence |
| G21 | Change funding source | MB controller | Direct source tile/player mutation | Rejected | Provenance unchanged | Unchanged | Common evidence |
| G22 | Engagement tendency | MB controller | `/troops company C-A tendency aggressive` | Rejected owner-only | Tendency unchanged | Unchanged | Common evidence |
| G23 | Owner administration | MB controller | Invoke every remaining owner-only command exposed by UI/packets | Each rejected closed-by-default | No owner controls enabled | Unchanged | Common evidence |
| G24 | Delegator eligibility | KA, MA; one KA-owned and one MA/other-owned company | KA delegates own; MA and KA try non-owned | Only eligible king may delegate personally owned company | Only valid company shows controller | Valid delegation persists | Common evidence |
| G25 | Identity preservation | KA, MB; valid delegation | Move/halt/relog and inspect unit records | Actual owner, company faction, unit owners, funding never change | Controller shown separately | Same after restart | Common evidence |
| G26 | Native reclaim | KA, MB; active delegated movement | KA reclaims immediately | Native reclaim wins; safe halt-after-boundary | Controller returns native | Persists | Common evidence |
| G27 | Revocation | KA, MB; two valid delegations | In separate runs lose T3 and break alliance | Temporary control revoked; movement safely halted | Revocation reason exact | Remains revoked | Common evidence |
| G28 | King unpledge/restart | KA delegated; then KA unpledges; second run restart while valid | Unpledge, tick; restore backup and cold restart | Unpledge safely revokes/reconciles; valid delegation survives restart without broader rights | Correct controller/reason | As stated | Common evidence |
| G29 | Steward authority/cap | A/B share W1 side; B kingless; known native offensive unallocated totals across multiple allied controllers | Mobilize from two allies | Authority exists only during ACTIVE W1; combined reservations exactly <=100% one global eligible pool | Global used/eligible counts exact | Persists | War, authority, pools/reservations, units |
| G30 | Exclusions | Same; defensive/farmer/player allocations/captured pools/existing native-player units present | Mobilize to cap | Excludes every listed source; never seizes a valid native player's assets | Only native unallocated offensive eligible | Persists | Per-category before/after NBT |
| G31 | Native assets | Valid stewardship units/company | Inspect owner/faction/control; issue allowed movement | Units remain native-faction assets; temporary control is not ownership | Native faction/temporary controller separate | Persists | Unit/company records |
| G32 | Route/claim limits | W1 opposes E; stewardship company at native border | Route to E, unrelated Mordor, and attempt claim as B | E route allowed; unrelated target and claim-in-native-name rejected | Authorized wars/targets and exact denial shown | Unchanged | War, route/claim packet/log |
| G33 | Native reclaim | Replacement native KB appears | KB reclaims during allied control | Native king reclaim always wins | Native controller/reason | Persists | King/company/unit records |
| G34 | Demobilization safety | War ENDING; loaded and unloaded stewardship-created units reach safe tile | Tick cleanup, later load chunk | Loaded removed without drops; unloaded tombstoned; exact population returned once | Cleanup/admin state exact | No second refund or ghost | Chunk/entity/tombstone/pool evidence |
| G35 | Steward restart | Valid wartime stewardship plus unloaded chunks | Cold restart twice; load chunks | No reservation/unit duplication; authority/targets revalidate against current war | Counts/company stable | Idempotent | Two snapshots, UUID scan, wars/pools |

## H. Diplomacy creation and breaking (6)

| ID | Players/roles | Starting state | Exact actions | Expected server result | Expected GUI result | Restart expectation | Failure evidence |
|---|---|---|---|---|---|---|---|
| H01 | KA, KE | A/E hostile defaults; both kings recorded | KA sends each track; non-receiver tries accept; KE accepts | Hostility does not block two kings; receiver-only acceptance | E appears in selector; pending then Established | Accepted tracks persist | Relations, king records, request/accept logs |
| H02 | KA; N/E kingless | Exactly one king | Request Civil at Neutral, Trade at Friend, Military at Ally; try each below cap | Eligible auto-accept/waive; below-cap denied | Selector includes only type-eligible factions and says auto-start | State/waiver persists | Default relation output, options, logs |
| H03 | MA, MB | Both factions kingless | Both ordinary members send GUI/direct requests | Every request denied; no pair created | No Send option/clear server reason | No pair after restart | Commands/packets, alliance NBT |
| H04 | KA, KB | No pair | Request/accept Military, then separate Trade | Military establishes Civil+Trade; Trade establishes Civil | Independent statuses/T0 exact | Hierarchy persists | Before/after TRACK/ALLIANCE |
| H05 | KA, KB | All tracks active | Break Military; restore; break Trade; restore; break Civil | Military break retains lower; Trade removes Trade+Military; Civil removes all | Only remaining tracks shown; no phantom Military | Same after restart | Break logs, GUI and pair NBT |
| H06 | MA, KA | Active pair with grace configured | MA tries break direct packet/command; KA breaks | Ordinary denied; king break immediate with no grace | Rejection then immediate removal | No resurrection | Actor UUIDs, grace/track NBT, logs |

## I. Succession and contribution grace (5)

| ID | Players/roles | Starting state | Exact actions | Expected server result | Expected GUI result | Restart expectation | Failure evidence |
|---|---|---|---|---|---|---|---|
| I01 | KA, KB, O | Different A/B contribution/succession deadlines | Grace status pair and each affected side; set/expire B | Only B changes; audit contains operator/faction/former/new/reason | Deadlines under correct side only | Both unequal deadlines persist | Commands, audit, before/after NBT |
| I02 | KB replacement | Active tiers/progress; KA remains | KB unpledges, replacement claims before expiry | Succession starts B only; replacement inherits B progress and clears succession | B provisional only, then normal; A unchanged | Same after restart | King/progress/deadline records |
| I03 | O | B succession near expiry under Ally/Friend/Neutral/Hostile default pairs | Force-expire each B side | Default cap: Ally retain; Friend loses Military; Neutral loses Trade+Military; Hostile loses all; B waiver applied if lower alliance remains | Exact resulting tracks/reason | Results persist and custom relations reapply | Relation map, tracks, audit, restart log |
| I04 | KA/new KB | B kingless waiver with active tiers | Add KB; set short contribution; complete/miss side requirements; expire B | New king gets B-only contribution grace; expiry downgrades only to shared completed/waived minima | B grace/progress exact; A deadline unchanged | Persists | Side ledgers, requirements, audit |
| I05 | O | Production defaults and short test defaults | Verify 14d defaults; set `30s`, `10m`, `12h`, `14d`; let/force expiry without clock edits | Units parsed exactly; normal tick and admin force reconcile equivalently | Remaining duration accurate | Defaults/deadlines persist | Timestamps, config/audit, host clock proof |

## J. Waypoint restriction, toggle, and bypass (5)

| ID | Players/roles | Starting state | Exact actions | Expected server result | Expected GUI result | Restart expectation | Failure evidence |
|---|---|---|---|---|---|---|---|
| J01 | MA | Own/ally/T0/friendly/enemy/unclaimed mapped waypoints | Run `waypoint check` and travel each | Policy matrix matches C01-C02 | Green/blue/red/yellow and exact owner/reason | Same | Checks, tile map, screenshots/logs |
| J02 | MA, O | Countdown to currently allowed ally | Capture/revoke during countdown; also send original LOTR packet directly | Final injected server check denies stale/forged path | Rejection text; no teleport | New state persists | Packet/timing log, position evidence |
| J03 | O, MA | Restriction on | Turn off/on while map open | Off follows native behavior; transformer remains installed/no-op | DISABLED then live policy | Toggle persists | Config, startup line, overlay |
| J04 | O, MA | Denied destination and native restrictions | Enable/remove persisted bypass; test direct original and KOME packets | Only KOME territory ignored; cooldown/combat/sleep/region still enforced | BYPASS and native reasons | Bypass/removal persist | Player UUID, both packet paths, config |
| J05 | MA | Unmapped waypoint and intentionally incompatible optional GUI field environment if available | Select/travel; inspect reflection failure | Unmapped uses native; overlay field failure never crashes map; incompatible transformer target fails startup | Native map remains usable or overlay normal | Repeat cold start | Debug/error logs, map video, fail-closed trace |

## K. Legacy to schema 5 migration (3)

| ID | Players/roles | Starting state | Exact actions | Expected server result | Expected GUI result | Restart expectation | Failure evidence |
|---|---|---|---|---|---|---|---|
| K01 | O | Backup legacy world with reciprocal pairs/T4/captain/trade posts/farmer flag/peacetime stewardship | Start once, inspect cleanup/quarantine | Canonical pair; T4->T3; captain exact; post goods recovered; farmer flag removed; peacetime stewardship revoked safely | No T4/post/farmer rows; withdrawals explicit | Second start changes nothing | Backup, migration totals, before/after NBT |
| K02 | KA, KB | Legacy directional assignments/deliveries/storage/claims/deadlines | Migrate and open both ledger directions | Side identity/goods/deadlines retained with no duplication | Correct contributor/receiver each direction | Stable after restart | Legacy/current pair and inventories |
| K03 | O | Existing valid and over-max/disabled incomplete REQ3 plus completed tier | Load hardened build; inspect; reroll invalid | Valid unchanged; invalid marked; completed untouched; repair recovery lossless | INVALID only on open bad rolls | New assignment/recovery stable | Encodings, item totals, NBT/audit |

## L. Restart and unloaded-chunk scenarios (4)

| ID | Players/roles | Starting state | Exact actions | Expected server result | Expected GUI result | Restart expectation | Failure evidence |
|---|---|---|---|---|---|---|---|
| L01 | KA, KB | Active pair with unequal progress, deadlines, custom LOTR relationship, item overrides | Cold restart twice | All state and relationship reapplication exact | Same viewer records | Idempotent | Three NBT/log snapshots |
| L02 | KA | Hired Civil/Military units from tile/reserve sources; chunks unloaded | Relog/restart/load/kill | No duplicate record, debit, or early refund; exact later release | Unit/source lists accurate | Idempotent | UUID scan, pool balances, chunks |
| L03 | KA | Active/ACCESS_HALTED/pending-spawn orders across unloaded chunks | Restart, tick, load in each state | Boundary checks and spawn idempotence; no forced unrelated chunks | Accurate status/reason | Stable | Tickets, order/entity NBT, logs |
| L04 | KA, KB | Retired Produce pending migration plus valid delegation/stewardship/tombstones in unloaded chunks | Restart twice and load | One recovery only; no reservation/unit duplication, delegation broadening, double refund, or ghost | Counts/authority/cleanup stable | Idempotent | Recovery/company/unit/tombstone/pool snapshots |

## M. Permission and packet bypass attempts (5)

| ID | Players/roles | Starting state | Exact actions | Expected server result | Expected GUI result | Restart expectation | Failure evidence |
|---|---|---|---|---|---|---|---|
| M01 | MA/MB/O | Mixed king/admin status | Send request/accept/break commands and crafted packets as unauthorized actors | Server enforces sender king, pending receiver king, and break king/admin; operator view is no bypass | Exact rejection mirrored | No mutation | Actor/packet/log/NBT |
| M02 | MA/KB | Directed ledgers | Forge side, deposit item, claim, roll/reroll packets/commands | Wrong side/claim/reroll permissions rejected; inventory unchanged | Correct Can Deposit/Claim only | No mutation | Inventories, packet bytes, ledger NBT |
| M03 | MA/KA/KB | Trade T1 ledger and Trade T2 completed | Forge contribution/receiver, claim, roll, fake Produce action, and pair fields | Ledger sender/receiver/king checks reject forgery; no Produce handler exists | Exact rejection and authoritative in-place refresh | No mutation | Ledger/pair NBT, packets/log |
| M04 | MB temporary controller | Valid T3 delegation | Execute G08-G23 through direct commands and packet handlers, not just disabled buttons | Every forbidden mutation rejected server-side | Buttons absent/disabled plus mirrored reason | Structure unchanged | Common G evidence plus raw packet |
| M05 | MA/O | Waypoint denied, movement access lost, unit-cap owner mismatch | Send original LOTR travel, KOME travel, movement, cap, and duplicate packets | Final travel/access/owner/cap checks reject; no duplication or unauthorized mutation | Server reasons shown | No mutation | Raw packet capture, positions, UUID/pool/NBT |

## N. GUI scale verification (3)

| ID | Players/roles | Starting state | Exact actions | Expected server result | Expected GUI result | Restart expectation | Failure evidence |
|---|---|---|---|---|---|---|---|
| N01 | Any participant | Rich pair with long names/reasons/items | At Small GUI scale open/list/scroll Overview, Requirements, Benefits, Military, both ledger directions, pledge departure, and create selector | Requests only on explicit clicks; no accidental mutation | No overlap/clipping; tooltips/reasons/scroll reachable | N/A | Full-resolution screenshots/video, resolution/scale |
| N02 | Same | Same | Repeat at Normal scale | Same records/actions | Stable layout and centered selectors | N/A | Same evidence |
| N03 | Same | Same | Repeat at Large/Auto and smallest supported resolution | Same records/actions | Buttons remain on-screen; tabs/text/amounts/deadlines readable | N/A | Same evidence |

## O. New coalition-war scenarios (7)

| ID | Players/roles | Starting state | Exact actions | Expected server result | Expected GUI result | Restart expectation | Failure evidence |
|---|---|---|---|---|---|---|---|
| O01 | O | No wars | Create Gondor/Rhudel war; rename war/sides; add Dunedain/Rohan to Side One | One two-sided multi-faction record; no aggressor/defender semantics | Wars list/icons/names/status correct | All membership/history persists | `/war` output, WAR record/NBT, screenshots |
| O02 | Gondor/Rohan/Rhudel kings | O01 active; Gondor/Rohan Military T3; Rohan kingless | Gondor hires/routes Rohan stewardship force toward Rhudel | Same-side authority active; native Rohan population/source retained; Rhudel legal | Military/company detail shows war, target, source, 100% pool | Persists and revalidates | Unit/company/war/pool records |
| O03 | Same | O02 plus unrelated Mordor tile | Preview/dispatch Rohan force toward Mordor | Rejected at preview/dispatch and any crafted next-step packet | Mordor absent from legal targets; exact reason | No order after restart | Route packet/log, company/war state |
| O04 | O/controller | Stewardship company physically in Rhudel territory | `/war end <id>`; try Stay/Resume/Continue; Retreat | `WAR_ENDED_HALTED`; only Retreat; native-first then T2-staging priority; demobilize safely | Ending/withdrawal states visible | No ghost/refund duplication | Order route, entities, pools, tombstones |
| O05 | O/controller | Ending war; no native/T2 tile on traveled route | Attempt Retreat, then inspect/finalize | No teleport/deletion; `PENDING_ADMIN_RESOLUTION` retained and finalize permitted only as documented exception | Clear no-safe-route warning | Exception/history persists | Order/company WAR record, logs |
| O06 | O/new Rohan king | Active stewardship and movement | Establish new native king mid-war | Stewardship revokes immediately; native reclaim/dormant/withdrawal path wins | Controller/authority/reason refresh | Reclaimed state persists | King/company/order/unit NBT |
| O07 | Allied/same-side claimant | Ally-controlled tile | Click Claim once, mutate nothing, click again; repeat with owner/alliance mutation | First click only arms; valid second breaks direct tracks and records opposition; stale mutation fails | Warning says create/update destination and contradiction if same-side | Confirmation never survives expiry/restart incorrectly | Packet timestamps, tile/pair/war history |

## P. New economy migration scenarios (2)

| ID | Players/roles | Starting state | Exact actions | Expected server result | Expected GUI result | Restart expectation | Failure evidence |
|---|---|---|---|---|---|---|---|
| P01 | O | Backup with multiple legacy posts containing inputs/outputs for both operating sides | Start schema 5, total every stack, claim recovery | Log totals equal old inventory exactly; each stack goes to correct operating ledger; no post runtime remains | Recovery claimable through Ledger; no Production tab | Second restart recovers zero additional items | Backup/post NBT, migration log, ledgers/inventories |
| P02 | O | Backup containing valid and malformed retired provisional Produce pending records | Start/save/restart twice | Valid pending stacks recover once; malformed records quarantine; all other provisional fields disappear | No runtime UI/action is created | No duplicate recovery or retired tag | Backup/current NBT, recovery/quarantine totals |

## Q. New pledge-lifecycle scenarios (3)

| ID | Players/roles | Starting state | Exact actions | Expected server result | Expected GUI result | Restart expectation | Failure evidence |
|---|---|---|---|---|---|---|---|
| Q01 | Pledged player/O | Loaded offensive/defensive/farmhand units funded by reserve/tile pool/allocation plus one completed transfer and one pending offer | Open Departure preview, unpledge | Untransferred old-faction units removed without drops; exact sources returned once; allocation closed; completed transfer preserved; pending offer fails/clears | Preview/result totals and funding summary match records | No entities or credits reappear | Entity drops/UUIDs, pools/allocations, transfer and audit records |
| Q02 | Pledged player/O | One moving snapshot, one unloaded stationary unit, one malformed legacy source | Switch directly to another faction; later load chunk; inspect/resolve quarantine | Movement cancelled before next step; snapshot removed; tombstone kills late entity; known sources refunded once; malformed source quarantined | Pending/quarantine/result counts exact | Two restarts after return remain idempotent | Orders/snapshots/tombstones/quarantine/pools |
| Q03 | King and temporary controller | Delegated native company plus foreign stewardship control | King unpledges; separately controller unpledges | Succession begins/pair progress persists; issued authority revoked; controller departure never deletes/refunds native assets it did not own | Correct king/delegation/stewardship result | Stable after two restarts | King ledgers, companies, units, war/pool state |

## R. Automatic Military T3 and dedicated GUI correction scenarios

| ID | Players/roles | Starting state | Exact actions | Expected server result | Expected GUI result | Restart expectation | Failure evidence |
|---|---|---|---|---|---|---|---|
| R01 | Kingless native, two supporting kings, O | Native enters active war; two effective T3 pairs | Create war by hostile capture, then separately `/war create` | Both supporters auto-enroll on native side once with automatic provenance | War detail marks both automatic and names authorized kings | Cold restart does not duplicate membership/enrollment | War NBT/records before/after |
| R02 | Ordinary supporting member and supporting king | R01 active | Attempt hire, route, dispatch, movement controls, and crafted packets as each actor | Member rejected everywhere; recognized pledged king succeeds only for legal actions | Member receives exact unavailable reason and no action flags | Authority remains actor-specific | Packet/log/company evidence |
| R03 | O playing normally | R01 active; Operator View off/on | Attempt normal stewardship actions in both view modes | Operator status/view grants no gameplay authority; explicit `/war` corrections still work | Visibility changes only; action authority does not | View resets off after reconnect | GUI, packets, command logs |
| R04 | Supporting king/replacement | Active stewardship movement | Old king unpledges/loses status; then crown replacement | Old authority clears and movement halts safely; replacement is assigned when still valid; coalition membership unchanged | Dormant reason then new king UUID/name | Same after two cold restarts | King/company/order/enrollment records |
| R05 | New native king | Active stewardship, legitimate native and stewardship-created companies | Crown native king | All kingless control revokes; native companies remain; created forces withdraw/demobilize safely; supporters stay in war | Native-priority/revocation reasons visible | No company/population duplication | Units/companies/pools/war evidence |
| R06 | O | Supporter already native-side | Reconcile repeatedly, reload, move unrelated side member | No duplicate supporter membership or enrollment | One automatic marker | Stable | Membership history count |
| R07 | O | Effective T3 supporter already opposing native | Create/reconcile | Supporter is not moved; contradiction warning blocks authority | Prominent warning and opposing placement | Warning/state persists | War event/enrollment records |
| R08 | Two supporting kings | One native global eligible pool and multiple wars/companies | Hire concurrently to exhaustion | All controllers/alliances/wars share one 100% eligible pool | Global eligible/used/available totals agree everywhere | Reservations remain exact | Pool/unit/source records |
| R09 | Supporting king | Two active authorizing wars with different opponents | Route to each opponent; end first war | Opponent union initially legal; first disappears; second stays authorized without demobilization | War IDs/targets refresh in place | Remaining authority persists | Routes/company/war state |
| R10 | Native and supporting kings/member | Native has king and T3 partner | Delegate owned/non-owned companies to king/member/unrelated king | Only native king's personally owned company to recognized pledged T3 king succeeds | Only authorized server-supplied action appears | Valid delegation persists/revalidates | Company owner/controller/pair records |
| R11 | Allied/same-side claimant | Armed confirmation | Wait beyond 30 seconds; mutate owner/alliance/war/player between clicks | Every stale case rejects; valid second click alone mutates | Explicit hostile warning and 30-second expiration | Confirmation not persisted | Confirmation/tile/war logs |
| R12 | Participant | Small, Normal, Large, Auto, smallest supported resolution | Exercise all four Alliance tabs, ledger switch/claim, Wars filters/detail, conquest confirm, departure refresh/back/scroll | One authoritative mutation per action; forged state rejected | No clipping/overlap; selected pair/tab/filter and reasonable scroll preserved on refresh | N/A | Scale/resolution screenshots and packet log |

## Release interpretation

- Automated green proves model, serialization, policy helpers, exact configured class fingerprint, and compilation behavior.
- Dedicated/client startup logs prove loading and transformer/overlay activation only.
- The manual matrix proves live Forge/LOTR entity, packet, multiplayer, chunk, inventory, and visual behavior only after evidence is captured. Do not mark gameplay verified until each applicable row has pass evidence or a linked defect.
