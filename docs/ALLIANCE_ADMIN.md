# Alliance and War Administration

All state changes are server-authoritative. Take a complete stopped-server world backup before deploying schema 5.

## Visibility and diplomacy

Operator View is an explicit session-only Server Records/Alliance visibility toggle. It defaults off and resets on reconnect/restart. It never expands request candidates or bypasses pledge, king, relation, ledger, movement, conquest, or benefit rules.

```text
/alliance request <civil|trade|military> <factionA> <factionB>
/alliance accept <civil|trade|military> <factionA> <factionB>
/alliance break <civil|trade|military> <factionA> <factionB>
/alliance roll <civil|trade|military> <factionA> <factionB>
/alliance reroll <civil|trade|military> <contributingFaction> <receivingFaction>
/alliance goods <contributingFaction> <receivingFaction>
/alliance claimGoods <contributingFaction> <receivingFaction>
/alliance get|list|benefits ...
/alliance set|clear ...
```

`reroll`, `set`, and `clear` are operator corrections. `reroll` selects a valid replacement before mutation and moves recoverable delivered goods to the faction ledger. The first faction passed to `goods`/`claimGoods` is the contributing ledger; the opposite faction claims incoming goods.

## War commands

All `/war` commands require permission level 2.

```text
/war create <factionA> <factionB> [name]
/war rename <warId> <name>
/war side rename <warId> <1|2> <name>
/war side add <warId> <1|2> <faction>
/war side remove <warId> <faction>
/war side move <warId> <faction> <1|2>
/war status <warId>
/war list [active|ending|ended|all]
/war end <warId> [reason]
/war finalize <warId> [reason]
/war cancel <warId> <reason>
```

Wars always have neutral Side One and Side Two sets. `create` refuses duplicate active opposition. `side add` refuses a faction already on the opposite side; use `side move` explicitly. Active edits preserve at least one faction on each side and revalidate every temporary controller/company/movement. `/war list ending` isolates withdrawal-stage records. Review every `WARNING` about contradictory memberships.

When an active war contains a kingless faction, effective Military T3 partners are enrolled automatically on that faction's side and recorded with automatic-support provenance. Do not manually add them first. A partner already on the opposing side is never moved automatically; resolve the contradiction explicitly with `side move`, `side remove`, alliance correction, or war lifecycle commands. Removing an automatically enrolled faction marks the enrollment as operator-removed so reconciliation does not immediately add it back.

`end` enters `ENDING` and starts withdrawal. Do not use `finalize` until stewardship-created forces are demobilized or unresolved cases show explicit `PENDING_ADMIN_RESOLUTION`. `cancel` is an audited correction: it retains capture, admin, unit, population, and cleanup records.

Hostile player conquest creates/updates wars automatically. Accepted `/conquest` transfer/correction paths do not. Operators must explicitly use `/war create` when a correction should also create a war.

## Trade and future Produce metadata

There are no trade-post commands. Any old `/alliance post ...` workflow is removed.

There are also no `/alliance production ...` commands and no `tradeProduceSlots` configuration. Trade T1 goods still use the faction ledger. Trade T2 needs 250 cumulative legitimate allied trades, has no population/structure requirement, completes and persists normally, and currently grants only the future-facing `Additional Produce Farmer Slot` display text. No slot, timer, product, pending item, permission, or claim backend exists.

## Permanent transfer, delegation, and pledge release

```text
/troops company <id> transfer <onlinePlayer>
/troops company <id> acceptTransfer
/troops company <id> rejectTransfer
/troops company <id> cancelTransfer

/troops company <id> delegate <onlinePlayer>
/troops company <id> reclaim
/troops movement continue|halt|stay|retreat|resume <orderId>

/troops pledgeRelease preview <player>
/troops pledgeRelease status <player>
/troops pledgeRelease retry <player>
/troops pledgeRelease resolve <unitUuid> <removed|quarantine>
```

Permanent transfer requires the actual owner and explicit same-native-faction acceptance. It rejects moving, stewardship, missing, mixed-invalid, underfunded, or unproven-source companies as one atomic operation. Temporary controllers cannot transfer.

Voluntary Military T3 delegation is king-to-king: the recognized pledged native king may delegate a personally owned company only to the recognized pledged king of an effective Military T3 partner that is not directly opposed. Wartime Stewardship is likewise supporting-king-only. Ordinary members and operators playing normally receive no T3 authority. Temporary authority is limited to View, Dispatch, Continue, Halt, Stay, Retreat, and Resume; structural actions remain denied server-side.

Supporting-king loss revokes that player's control and halts movement without changing native ownership, funding, population, or coalition membership. A valid replacement king is authorized automatically. Native-king return ends kingless stewardship and sends stewardship-created forces through safe withdrawal/demobilization; it does not delete legitimate native companies or remove supporters from the war.

Players may preview/status only themselves. `retry` and `resolve` are operator-only. `resolve removed` confirms/removes a late entity; `resolve quarantine` retains the record as an explicit unresolved exception. Both are audited. Never delete tombstones or quarantine NBT manually while the server is running.

## Requirement, quota, waypoint, and grace configuration

```text
/alliance config difficulty <easy|standard|hard>
/alliance config requirement <civil|trade|military> <tier> items <stack-equivalents>
/alliance config requirement <civil|trade|military> <tier> activity <count>
/alliance config requirement military <1|2|3> population <capacity>
/alliance config quota item <registry[:meta]> weight <1|2|4|8|16|32|64>
/alliance config quota item <registry[:meta]> max <positive quantity>
/alliance config quota item <registry[:meta]> enabled <on|off>
/alliance config quota item <registry[:meta]> show
/alliance config waypointRestriction <on|off>
/alliance waypoint bypass <player> <on|off>
/alliance waypoint check <player> [current|waypointCode]
/alliance config grace succession|contribution <duration>
/alliance grace status <A> <B> [affectedFaction]
/alliance grace set <A> <B> <affectedFaction> <succession|contribution> <duration>
/alliance grace expire <A> <B> <affectedFaction> <succession|contribution>
```

Durations accept seconds, minutes, hours, and days. Requirement changes never revoke completed tiers. Invalid open quota rolls remain blocked until a lossless operator reroll.

## Operational checks

After deployment:

1. Confirm the schema-5 migration summary and legacy post recovery totals in the server log.
2. Open Alliance Benefits and verify Trade T2 shows the future Produce message with no action; confirm there is no Production tab or Produce command.
3. Run `/war list all` and confirm old worlds begin with no inferred historical wars.
4. Check `/troops pledgeRelease status <player>` for pending/quarantined entities.
5. Inspect Server Records > Wars and all four status filters, including `/war list ending`.
6. Cold-restart twice and compare unit UUIDs, population usage, recovery goods, war memberships/enrollments, authorizations, and tombstones. Confirm retired Produce tags do not return.

The existing LOTR v36.15 jar must remain byte-for-byte unchanged. KOME's transformer registration and final waypoint-gate startup lines remain release gates.
