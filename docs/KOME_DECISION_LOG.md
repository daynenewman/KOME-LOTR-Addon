# KOME redesign decision log

This log records every material interpretation made during the Build/population/company/alliance redesign.

## D1 — Legacy alliance migration

Decision: migrate each faction direction to the highest stage backed by a clearly earned corresponding benefit, not the maximum old tier. Ambiguous evidence chooses the lower stage; a clear merchant entitlement survives separately.

Why: unrelated legacy tracks had different meaning and numeric ranges. Taking a maximum would grant unearned movement or command.

Alternative rejected: max numeric tier or one shared pair stage.

## D2 — Population-owner selector

Decision: the player's own faction is eligible whenever placement is legal; a foreign owner must be Friendly/Allied with both player faction and current controller.

Why: it implements the two-party permission intent and closes a player–third-faction–controller exploit.

Alternative rejected: checking only the player's relation to the selected owner.

## D3 — Distant-company spawning

Decision: retain LOTR's normal safe spawn beside the hiring player, then associate the unit with the persistent source-tile company even if that company is away.

Why: Minecraft 1.7.10 provides no reliable, duplication-safe way in this addon boundary to materialize a newly hired LOTR entity in an unloaded remote chunk. Association is deterministic; temporary spatial separation is already tolerated by company/unit records.

Alternative rejected: forcing chunk loads and teleport/spawn at a saved company position, which risks ghost/duplicate entities and unsafe chunk lifetime.

Limitation: the unit does not visually appear at a remote company. It remains in the same company and can converge through normal play/movement.

## D4 — One company identity

Decision: auto-company ID is deterministic from owner UUID plus immutable source tile. Rename changes only the display name.

Why: prevents duplicate companies and survives arbitrary player names.

Alternative rejected: editable name as identity or creating another company when the existing one is away.

## D5 — Build manager succession

Decision: an ineligible manager transfers to the current recognized king of the population owner. With no eligible king, manager is cleared and only operator repair is allowed.

Why: preserves data and prevents an unrelated player from gaining ownership.

Alternative rejected: delete the Build, transfer to tile controller, or retain unauthorized management.

## D6 — Active-hour reversal

Decision: approved contribution removal and Build deletion reverse all active generated population and unclaimed progression. Audit records may remain but never count.

Why: totals must describe current reality.

Alternative rejected: lifetime contribution accumulation.

## D7 — Claimed Stage 3

Decision: a claimed stage is not downgraded after later hour removal/deletion, but deleted/removed hours cannot satisfy an unclaimed stage or a newly formed post-break relationship.

Why: the prompt says claimed Stage 3 remains unlocked yet deleted hours cannot count again. Relationship timestamps provide the boundary.

Alternative rejected: retroactive stage downgrade or permanent reuse of deleted credit.

## D8 — Foreign 50% access

Decision: calculate each offensive/defensive faction pool separately and floor integer halves.

Why: population is integral and source identity must remain intact.

Alternative rejected: pool aggregation before halving, which changes rounding and hides ownership.

## D9 — Build deletion safety

Decision: validate Build commitment, total source-pool commitment, and controller-effective allocation/usage before mutation.

Why: checking only the Build could still invalidate native/other-Build allocations; checking only the tile could lose source provenance.

Alternative rejected: automatic disbanding or negative/orphaned capacity.

## D10 — Stage requirements

Decision: keep the authoritative goods ledger for every stage, add the Build-hour milestone to Stage 3 and qualifying deployment to Stage 4, and require an explicit king claim.

Why: preserves the requested ledger while simplifying presentation and prevents event-driven surprise unlocks.

Alternative rejected: automatic claims or a second progression inventory.

## D11 — Stage 4 timing and unit validity

Decision: the progressing faction's active war membership must be strictly later than its Stage 3 claim, and the company must contain a live tracked offensive non-farmhand unit owned by a still-pledged player.

Why: excludes old wars, empty/stale companies, and post-pledge ownership exploits.

Alternative rejected: `>=` timestamps, historical membership, or company metadata alone.

## D12 — King loss

Decision: preserve stage and benefits indefinitely; remove all grace tags/commands/UI.

Why: this is explicit source-of-truth behavior.

Alternative rejected: downgrade, suspension, or hidden grace compatibility.

## D13 — Merchant placeholder

Decision: Stage 2 unlocks a persistent unique entitlement query but creates no merchant/economy. Break does not revoke it.

Why: matches the temporary exception and supplies a clean future integration point without inventing Produce behavior.

Alternative rejected: spawning merchant entities or deleting earned slots on break.

## D14 — Legacy identifiers

Decision: retain several old string/NBT tokens only as read/write compatibility mirrors; remove them from active records, commands, GUI, and permission decisions.

Why: renaming persisted tokens would risk existing inventories, wars, and hired-unit provenance.

Alternative rejected: destructive rewrite with no rollback evidence.

## D15 — GUI capture ledger

Decision: deterministic screenshots use a visual ledger twin; live manual testing remains mandatory.

Why: the base LOTR pouch hook dereferences a live player for `GuiContainer` menu capture.

Alternative rejected: weakening production container behavior to satisfy screenshot automation.

## D16 — Typed Build hours

Decision: expose offensive and defensive hours as typed decimal fields with adjacent 0.5 decrement/increment buttons, normalize valid input to canonical integer half-hours, and validate those bounds again in the server-authoritative mutation service.

Why: direct entry is efficient for large contributions, while fixed 0.5 controls make the supported granularity obvious. Duplicate validation preserves server authority.

Alternative rejected: client-only validation, quarter-hour rounding, or spinner-only entry.

## D17 — One Build destruction action

Decision: present one **Destroy Build** action. The server preflight selects manager/operator deletion or existing controller-king hostile homeland destruction and supplies the precise disabled reason.

Why: both actions remove the same persistent object and require confirmation; two adjacent destructive buttons obscured which one applied without changing authority.

Alternative rejected: retaining separate Delete/Destroy Enemy controls or broadening either permission path.

## D18 — Base Population presentation

Decision: keep persisted/backend `native` fields unchanged, but call the concept **Base Population** in player-facing GUI and command output. Visualize each faction pool as used, available, and inaccessible segments with exact numeric values.

Why: "Base" explains the source without implying faction ethnicity, and segmented graphs make conquest access loss visible while preserving source ownership.

Alternative rejected: schema/NBT renames or a blended tile-wide graph that hides faction provenance.

## Future questions intentionally not implemented

- Broader foreign-owner placement rules or explicit tile-owner approval.
- Build categories and category-specific mechanics.
- Revocation of merchant slots on break.
- Non-default Stage 3 thresholds beyond the existing server configuration.
- Safe remote entity placement if a future API provides loaded-company coordinates and an atomic entity-transfer primitive.
