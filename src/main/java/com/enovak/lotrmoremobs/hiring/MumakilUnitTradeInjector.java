package com.enovak.lotrmoremobs.hiring;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import lotr.common.entity.npc.LOTRUnitTradeEntries;
import lotr.common.entity.npc.LOTRUnitTradeEntry;

public class MumakilUnitTradeInjector {
    private static boolean injected;

    public static void inject() {
        if (injected) {
            return;
        }

        LOTRUnitTradeEntries trades = LOTRUnitTradeEntries.NEAR_HARADRIM_WARLORD;
        if (trades == null || trades.tradeEntries == null) {
            System.out.println("[LOTRMoreMobs] Near Harad Warlord unit trades were not ready for Mumakil injection.");
            return;
        }

        for (int i = 0; i < trades.tradeEntries.length; ++i) {
            LOTRUnitTradeEntry existing = trades.tradeEntries[i];
            if (existing != null && existing.mountClass == LOTREntityMumakil.class) {
                injected = true;
                return;
            }
        }

        LOTRUnitTradeEntry[] oldEntries = trades.tradeEntries;
        LOTRUnitTradeEntry[] newEntries = new LOTRUnitTradeEntry[oldEntries.length + 1];
        System.arraycopy(oldEntries, 0, newEntries, 0, oldEntries.length);
        newEntries[oldEntries.length] = new LOTRUnitTradeEntryMumakil();
        trades.tradeEntries = newEntries;

        injected = true;
        System.out.println("[LOTRMoreMobs] Added Mumak with Howdah to Near Harad Warlord unit trades.");
    }
}
