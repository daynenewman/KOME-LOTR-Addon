package com.enovak.lotrmoremobs.spawning;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import lotr.common.world.spawning.LOTRInvasions;

/**
 * Shared invasion-member metadata and the exact invasion eligibility list.
 *
 * Formation spawning is owned by the independent per-unit join handler. This
 * class deliberately does not mutate LOTR's weighted invasion mob lists.
 */
public final class MumakilInvasionFormationRegistry {
    public static final String INVASION_MEMBER_WEIGHT_KEY =
            "lotrmoremobs_mumakInvasionWeight";
    private static boolean registered;

    private static final LOTRInvasions[] ELIGIBLE_INVASIONS =
            new LOTRInvasions[] {
                    LOTRInvasions.NEAR_HARAD_CORSAIR,
                    LOTRInvasions.NEAR_HARAD_COAST,
                    LOTRInvasions.NEAR_HARAD_HARNEDOR
            };

    private MumakilInvasionFormationRegistry() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        registered = true;
        System.out.println(
                "[LOTRMoreMobs] Mumak invasion per-unit replacement: "
                        + "eligibleInvasions="
                        + ELIGIBLE_INVASIONS.length
                        + " names="
                        + getEligibleInvasionNames()
                        + " enabled="
                        + MumakilConfig
                        .enableMumakWarFormationsInInvasions
                        + " chance=1/"
                        + MumakilConfig.invasionUnitRollDenominator
                        + " limit=unlimited"
                        + " weightedBootstrap=false"
                        + " budget="
                        + MumakilConfig.INVASION_FORMATION_BUDGET_VALUE
        );
    }

    public static boolean isEligibleInvasion(
            LOTRInvasions invasion
    ) {
        for (int i = 0; i < ELIGIBLE_INVASIONS.length; ++i) {
            if (ELIGIBLE_INVASIONS[i] == invasion) {
                return true;
            }
        }
        return false;
    }

    private static String getEligibleInvasionNames() {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < ELIGIBLE_INVASIONS.length; ++i) {
            if (i > 0) {
                names.append(',');
            }
            names.append(ELIGIBLE_INVASIONS[i].name());
        }
        return names.toString();
    }
}
