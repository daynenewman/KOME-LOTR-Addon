package com.enovak.lotrmoremobs.hiring;

import lotr.common.entity.npc.LOTRUnitTradeEntries;
import lotr.common.entity.npc.LOTRUnitTradeEntry;
import lotr.common.fac.LOTRFaction;

/** Adds one faction-matched Battle Ram to each normal faction hiring roster. */
public final class BattleRamUnitTradeInjector {

    private static boolean injected;

    private BattleRamUnitTradeInjector() {
    }

    public static void inject() {
        if (injected) {
            return;
        }

        inject(LOTRUnitTradeEntries.HOBBIT_SHIRRIFF, LOTRFaction.HOBBIT);
        inject(LOTRUnitTradeEntries.BREE_CAPTAIN, LOTRFaction.BREE);
        inject(
                LOTRUnitTradeEntries.RANGER_NORTH_CAPTAIN,
                LOTRFaction.RANGER_NORTH
        );
        inject(
                LOTRUnitTradeEntries.BLUE_DWARF_COMMANDER,
                LOTRFaction.BLUE_MOUNTAINS
        );
        inject(LOTRUnitTradeEntries.HIGH_ELF_LORD, LOTRFaction.HIGH_ELF);
        inject(
                LOTRUnitTradeEntries.GUNDABAD_ORC_MERCENARY_CAPTAIN,
                LOTRFaction.GUNDABAD
        );
        inject(
                LOTRUnitTradeEntries.ANGMAR_ORC_MERCENARY_CAPTAIN,
                LOTRFaction.ANGMAR
        );
        inject(
                LOTRUnitTradeEntries.WOOD_ELF_CAPTAIN,
                LOTRFaction.WOOD_ELF
        );
        inject(
                LOTRUnitTradeEntries.DOL_GULDUR_CAPTAIN,
                LOTRFaction.DOL_GULDUR
        );
        inject(LOTRUnitTradeEntries.DALE_CAPTAIN, LOTRFaction.DALE);
        inject(
                LOTRUnitTradeEntries.DWARF_COMMANDER,
                LOTRFaction.DURINS_FOLK
        );
        inject(LOTRUnitTradeEntries.ELF_LORD, LOTRFaction.LOTHLORIEN);
        inject(
                LOTRUnitTradeEntries.DUNLENDING_WARLORD,
                LOTRFaction.DUNLAND
        );
        inject(
                LOTRUnitTradeEntries.URUK_HAI_MERCENARY_CAPTAIN,
                LOTRFaction.ISENGARD
        );
        inject(
                LOTRUnitTradeEntries.ROHIRRIM_MARSHAL,
                LOTRFaction.ROHAN
        );
        inject(
                LOTRUnitTradeEntries.GONDORIAN_CAPTAIN,
                LOTRFaction.GONDOR
        );
        inject(
                LOTRUnitTradeEntries.DORWINION_ELF_CAPTAIN,
                LOTRFaction.DORWINION
        );
        inject(
                LOTRUnitTradeEntries.EASTERLING_WARLORD,
                LOTRFaction.RHUDEL
        );
        inject(
                LOTRUnitTradeEntries.NEAR_HARADRIM_WARLORD,
                LOTRFaction.NEAR_HARAD
        );
        inject(
                LOTRUnitTradeEntries.MOREDAIN_CHIEFTAIN,
                LOTRFaction.MORWAITH
        );
        inject(
                LOTRUnitTradeEntries.TAUREDAIN_CHIEFTAIN,
                LOTRFaction.TAURETHRIM
        );
        inject(
                LOTRUnitTradeEntries.HALF_TROLL_WARLORD,
                LOTRFaction.HALF_TROLL
        );
        inject(
                LOTRUnitTradeEntries.MORDOR_ORC_MERCENARY_CAPTAIN,
                LOTRFaction.MORDOR
        );

        /*
         * RUFFIAN and UTUMNO remain command/debug-only because the base LOTR
         * hiring system has no corresponding normal captain/lord roster for
         * those two hidden factions.
         */
        injected = true;
        System.out.println(
                "[LOTRMoreMobs] Added faction Battle Rams to unit hiring rosters."
        );
    }

    private static void inject(
            LOTRUnitTradeEntries trades,
            LOTRFaction faction
    ) {
        if (trades == null
                || trades.tradeEntries == null
                || faction == null) {
            return;
        }

        for (LOTRUnitTradeEntry existing : trades.tradeEntries) {
            if (existing instanceof LOTRUnitTradeEntryBattleRam
                    && ((LOTRUnitTradeEntryBattleRam)existing)
                    .getRamFaction() == faction) {
                return;
            }
        }

        LOTRUnitTradeEntry[] oldEntries = trades.tradeEntries;
        LOTRUnitTradeEntry[] newEntries =
                new LOTRUnitTradeEntry[oldEntries.length + 1];
        System.arraycopy(
                oldEntries,
                0,
                newEntries,
                0,
                oldEntries.length
        );
        newEntries[oldEntries.length] =
                new LOTRUnitTradeEntryBattleRam(faction);
        trades.tradeEntries = newEntries;
    }
}
