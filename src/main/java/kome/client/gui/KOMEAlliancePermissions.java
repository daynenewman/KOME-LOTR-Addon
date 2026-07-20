package kome.client.gui;

import kome.common.data.KOMEAllianceBenefits;

final class KOMEAlliancePermissions {
    static final String[] TYPES = new String[] {"Civil", "Military", "Trade"};
    static final String[][] UNLOCKS = buildUnlocks();

    private static String[][] buildUnlocks() {
        String[][] unlocks = new String[TYPES.length][];
        for (int type = 0; type < TYPES.length; type++) {
            unlocks[type] = new String[KOMEAllianceBenefits.maxTier(type) + 1];
            unlocks[type][0] = "";
            for (int tier = 1; tier < unlocks[type].length; tier++) {
                unlocks[type][tier] = KOMEAllianceBenefits.get(type, tier).title;
            }
        }
        return unlocks;
    }

    private KOMEAlliancePermissions() {
    }
}
