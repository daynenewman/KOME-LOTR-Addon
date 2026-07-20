# Produce Farmer Future-Design Note

KOME does not currently implement a Produce Farmer runtime.

Trade T2 still completes and persists after 250 cumulative legitimate allied trades, its rolled item requirement, and both faction-side completions/waivers. It has no population, Trade Post, or structure requirement. Shared common metadata and the Alliance Benefits tab display:

```text
Additional Produce Farmer Slot

This Trade T2 alliance has unlocked an additional Produce Farmer slot. Produce Farmer integration will be added in a future update.
```

This is display text only. There is no slot or entitlement record, player/pair record, product pool, product selection, duplicate-product rule, cooldown, timer, generator, pending product, claim action, permission flag, packet, Production tab, command, configurable maximum, or runtime NBT. The canonical alliance pair already preserves both partner factions for future integration.

The following withdrawn commands/configuration do not exist:

```text
/alliance production list
/alliance production products
/alliance production select
/alliance production claim
/alliance config tradeProduceSlots
```

Development saves from the withdrawn provisional implementation are cleaned idempotently. A genuine unclaimed pending stack is moved once to the contributing faction's existing alliance recovery ledger; an invalid pair/contributor is quarantined. `AllianceProduceSlots` and `TradeProduceSlotsMaximum` are never written again, so two cold restarts cannot duplicate recovery.

A future implementation must integrate the real Produce Farmer system rather than recreate an item generator inside KOME. It should continue using server-authoritative identity, the canonical alliance pair, shared benefit metadata, lossless migration, and explicit manual multiplayer verification.
