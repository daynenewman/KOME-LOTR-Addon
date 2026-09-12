package com.enovak.lotrmoremobs.trade;

import com.enovak.lotrmoremobs.Main;
import lotr.common.entity.npc.LOTRTradeEntries;
import lotr.common.entity.npc.LOTRTradeEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * MUMAKIL_MATERIAL_TRADES_V2
 *
 * Adds Mumakil materials to southern LOTR trader pools which naturally occur
 * in or around Mumakil country.
 *
 * Important LOTR 36.15 naming convention:
 *   *_BUY  = NPC sells the item to the player.
 *   *_SELL = NPC buys the item from the player.
 *
 * Prices below are LOTR trade-pool base prices, so normal LOTR per-trader
 * price variation is still applied when an NPC generates its trades.
 */
public final class MumakilItemTradeInjector {
    // NPC -> player prices.
    public static final int MUMAK_RAW_SHANK_HUNTER_SELL_PRICE = 12;
    public static final int MUMAK_RAW_SHANK_BUTCHER_SELL_PRICE = 8;
    public static final int MUMAK_COOKED_SHANK_BUTCHER_SELL_PRICE = 12;
    public static final int MUMAK_TUSK_HUNTER_SELL_PRICE = 45;
    public static final int MUMAK_RAW_SHANK_MOREDAIN_SELL_PRICE = 8;

    // Player -> NPC payouts.
    public static final int MUMAK_TUSK_HARNEDOR_HUNTER_BUY_PRICE = 20;
    public static final int MUMAK_TUSK_GOLDSMITH_BUY_PRICE = 25;
    public static final int MUMAK_TUSK_BLACKSMITH_BUY_PRICE = 20;
    public static final int MUMAK_TUSK_MOREDAIN_BUY_PRICE = 18;
    public static final int MUMAK_TUSK_HALF_TROLL_BUY_PRICE = 12;

    private MumakilItemTradeInjector() {
    }

    public static void inject() {
        if (Main.mumakilShank == null
                || Main.mumakilCookedShank == null
                || Main.mumakilTusk == null) {
            System.out.println(
                    "[LOTRMoreMobs] Mumakil item trades were requested"
                            + " before the items were registered."
            );
            return;
        }

        /*
         * Shared native LOTR pools:
         * - HARAD_BUTCHER_* is used by Harnedor, Southron, Umbar, and Gulf
         *   butchers.
         * - HARAD_GOLDSMITH_* is used by Southron, Umbar, and Gulf goldsmiths.
         *
         * Harnedor Hunter and Gulf Hunter use separate hunter pools.
         */
        LOTRTradeEntries harnedorHunterNpcSells =
                LOTRTradeEntries.HARAD_HUNTER_BUY;
        LOTRTradeEntries harnedorHunterNpcBuys =
                LOTRTradeEntries.HARAD_HUNTER_SELL;
        LOTRTradeEntries haradButchersNpcSell =
                LOTRTradeEntries.HARAD_BUTCHER_BUY;
        LOTRTradeEntries haradGoldsmithsNpcBuy =
                LOTRTradeEntries.HARAD_GOLDSMITH_SELL;
        LOTRTradeEntries harnedorBlacksmithNpcBuys =
                LOTRTradeEntries.HARNEDOR_BLACKSMITH_SELL;
        LOTRTradeEntries nearHaradBlacksmithNpcBuys =
                LOTRTradeEntries.NEAR_HARAD_BLACKSMITH_SELL;
        LOTRTradeEntries umbarBlacksmithNpcBuys =
                LOTRTradeEntries.UMBAR_BLACKSMITH_SELL;
        LOTRTradeEntries gulfHunterNpcSells =
                LOTRTradeEntries.GULF_HUNTER_BUY;
        LOTRTradeEntries gulfBlacksmithNpcBuys =
                LOTRTradeEntries.GULF_BLACKSMITH_SELL;
        LOTRTradeEntries moredainHuntsmanNpcSells =
                LOTRTradeEntries.MOREDAIN_HUNTSMAN_BUY;
        LOTRTradeEntries moredainHuntsmanNpcBuys =
                LOTRTradeEntries.MOREDAIN_HUNTSMAN_SELL;
        LOTRTradeEntries halfTrollScavengerNpcBuys =
                LOTRTradeEntries.HALF_TROLL_SCAVENGER_SELL;

        if (!isReady(harnedorHunterNpcSells)
                || !isReady(harnedorHunterNpcBuys)
                || !isReady(haradButchersNpcSell)
                || !isReady(haradGoldsmithsNpcBuy)
                || !isReady(harnedorBlacksmithNpcBuys)
                || !isReady(nearHaradBlacksmithNpcBuys)
                || !isReady(umbarBlacksmithNpcBuys)
                || !isReady(gulfHunterNpcSells)
                || !isReady(gulfBlacksmithNpcBuys)
                || !isReady(moredainHuntsmanNpcSells)
                || !isReady(moredainHuntsmanNpcBuys)
                || !isReady(halfTrollScavengerNpcBuys)) {
            System.out.println(
                    "[LOTRMoreMobs] Mumakil material trade pools were not ready"
                            + " for item injection."
            );
            return;
        }

        // Harnedor Hunter: sells raw shank/tusk; buys tusks from hunters.
        ensureTrade(
                harnedorHunterNpcSells,
                Main.mumakilShank,
                MUMAK_RAW_SHANK_HUNTER_SELL_PRICE
        );
        ensureTrade(
                harnedorHunterNpcSells,
                Main.mumakilTusk,
                MUMAK_TUSK_HUNTER_SELL_PRICE
        );
        ensureTrade(
                harnedorHunterNpcBuys,
                Main.mumakilTusk,
                MUMAK_TUSK_HARNEDOR_HUNTER_BUY_PRICE
        );

        // Harnedor, Southron, Umbar, and Gulf butchers share this pool.
        ensureTrade(
                haradButchersNpcSell,
                Main.mumakilShank,
                MUMAK_RAW_SHANK_BUTCHER_SELL_PRICE
        );
        ensureTrade(
                haradButchersNpcSell,
                Main.mumakilCookedShank,
                MUMAK_COOKED_SHANK_BUTCHER_SELL_PRICE
        );

        // Southron, Umbar, and Gulf goldsmiths share this pool.
        ensureTrade(
                haradGoldsmithsNpcBuy,
                Main.mumakilTusk,
                MUMAK_TUSK_GOLDSMITH_BUY_PRICE
        );

        // Regional southern blacksmiths buy tusks as a crafting material.
        ensureTrade(
                harnedorBlacksmithNpcBuys,
                Main.mumakilTusk,
                MUMAK_TUSK_BLACKSMITH_BUY_PRICE
        );
        ensureTrade(
                nearHaradBlacksmithNpcBuys,
                Main.mumakilTusk,
                MUMAK_TUSK_BLACKSMITH_BUY_PRICE
        );
        ensureTrade(
                umbarBlacksmithNpcBuys,
                Main.mumakilTusk,
                MUMAK_TUSK_BLACKSMITH_BUY_PRICE
        );
        ensureTrade(
                gulfBlacksmithNpcBuys,
                Main.mumakilTusk,
                MUMAK_TUSK_BLACKSMITH_BUY_PRICE
        );

        // Gulf Hunter mirrors the Harnedor Hunter's local game-animal sales.
        ensureTrade(
                gulfHunterNpcSells,
                Main.mumakilShank,
                MUMAK_RAW_SHANK_HUNTER_SELL_PRICE
        );
        ensureTrade(
                gulfHunterNpcSells,
                Main.mumakilTusk,
                MUMAK_TUSK_HUNTER_SELL_PRICE
        );

        // Moredain Huntsman: cheap local meat seller and lower-paying tusk buyer.
        ensureTrade(
                moredainHuntsmanNpcSells,
                Main.mumakilShank,
                MUMAK_RAW_SHANK_MOREDAIN_SELL_PRICE
        );
        ensureTrade(
                moredainHuntsmanNpcBuys,
                Main.mumakilTusk,
                MUMAK_TUSK_MOREDAIN_BUY_PRICE
        );

        /*
         * Half-Troll Scavenger buys tusks only. Intentionally no raw Mumakil
         * shank trade here.
         */
        ensureTrade(
                halfTrollScavengerNpcBuys,
                Main.mumakilTusk,
                MUMAK_TUSK_HALF_TROLL_BUY_PRICE
        );

        System.out.println(
                "[LOTRMoreMobs] Ensured southern Mumakil material trades:"
                        + " hunters sell raw shank="
                        + MUMAK_RAW_SHANK_HUNTER_SELL_PRICE
                        + " and tusk="
                        + MUMAK_TUSK_HUNTER_SELL_PRICE
                        + "; butchers sell raw/cooked shank="
                        + MUMAK_RAW_SHANK_BUTCHER_SELL_PRICE
                        + "/"
                        + MUMAK_COOKED_SHANK_BUTCHER_SELL_PRICE
                        + "; goldsmiths buy tusk="
                        + MUMAK_TUSK_GOLDSMITH_BUY_PRICE
                        + "; blacksmiths buy tusk="
                        + MUMAK_TUSK_BLACKSMITH_BUY_PRICE
                        + "; Moredain sells raw shank="
                        + MUMAK_RAW_SHANK_MOREDAIN_SELL_PRICE
                        + " and buys tusk="
                        + MUMAK_TUSK_MOREDAIN_BUY_PRICE
                        + "; Half-Troll Scavenger buys tusk="
                        + MUMAK_TUSK_HALF_TROLL_BUY_PRICE
                        + " only."
        );
    }

    private static boolean isReady(LOTRTradeEntries tradePool) {
        return tradePool != null && tradePool.tradeEntries != null;
    }

    private static void ensureTrade(
            LOTRTradeEntries tradePool,
            Item item,
            int basePrice
    ) {
        LOTRTradeEntry existing = findItemTrade(tradePool, item);
        if (existing != null) {
            existing.setCost(basePrice);
            return;
        }

        LOTRTradeEntry[] oldEntries = tradePool.tradeEntries;
        LOTRTradeEntry[] newEntries =
                new LOTRTradeEntry[oldEntries.length + 1];
        System.arraycopy(
                oldEntries,
                0,
                newEntries,
                0,
                oldEntries.length
        );
        newEntries[oldEntries.length] =
                new LOTRTradeEntry(new ItemStack(item), basePrice);
        tradePool.tradeEntries = newEntries;
    }

    private static LOTRTradeEntry findItemTrade(
            LOTRTradeEntries tradePool,
            Item item
    ) {
        for (int i = 0; i < tradePool.tradeEntries.length; ++i) {
            LOTRTradeEntry entry = tradePool.tradeEntries[i];

            if (entry == null) {
                continue;
            }

            ItemStack tradeStack = entry.createTradeItem();

            if (tradeStack != null && tradeStack.getItem() == item) {
                return entry;
            }
        }

        return null;
    }
}
