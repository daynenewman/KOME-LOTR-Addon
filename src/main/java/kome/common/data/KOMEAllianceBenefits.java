package kome.common.data;

/**
 * Common benefit metadata shared by the authoritative server rules and the client GUI.
 * Tier zero is deliberately metadata-only base status and is never a benefit row.
 */
public final class KOMEAllianceBenefits {
    private static final Benefit[][] BENEFITS = new Benefit[][] {
        {
            new Benefit("Accepted base", "No gameplay benefit at T0."),
            new Benefit("Use allied public waypoints", "Only standard public waypoints in regions the player has normally unlocked; the server rechecks at travel."),
            new Benefit("Hire allied civilian farmhands", "Civilian farmhands only; population and provenance remain attached to the spawning side and source.")
        },
        {
            new Benefit("Accepted base", "No gameplay benefit at T0."),
            new Benefit("Hire one allied combat unit", "At most one active cross-faction combat recruit per player and alliance; tracked across restarts."),
            new Benefit("Move armies through allied territory", "Passage is revalidated before every physical step and arrival; revoked access halts further entry."),
            new Benefit("Voluntary delegation and Wartime Stewardship", "Delegation remains owner-controlled. Kingless stewardship is dormant during peace and targets only opposing sides of an active shared war.")
        },
        {
            new Benefit("Accepted base", "No gameplay benefit at T0."),
            new Benefit("Mutual Goods Exchange", "Each faction side contributes its rolled goods; the opposite faction may claim them through the shared ledger."),
            new Benefit("Additional Produce Farmer Slot", "This Trade T2 alliance has unlocked an additional Produce Farmer slot. Produce Farmer integration will be added in a future update.")
        }
    };

    private KOMEAllianceBenefits() {
    }

    public static Benefit get(String type, int tier) {
        int track = KOMEAlliance.CIVIL.equals(KOMEAlliance.normalizeType(type)) ? 0
            : KOMEAlliance.MILITARY.equals(KOMEAlliance.normalizeType(type)) ? 1 : 2;
        int safeTier = Math.max(0, Math.min(tier, BENEFITS[track].length - 1));
        return BENEFITS[track][safeTier];
    }

    public static String display(String type, int tier) {
        Benefit benefit = get(type, tier);
        return benefit.title + ": " + benefit.restriction;
    }

    public static Benefit get(int type, int tier) {
        int safeType = Math.max(0, Math.min(type, BENEFITS.length - 1));
        int safeTier = Math.max(0, Math.min(tier, BENEFITS[safeType].length - 1));
        return BENEFITS[safeType][safeTier];
    }

    public static int maxTier(int type) {
        return BENEFITS[Math.max(0, Math.min(type, BENEFITS.length - 1))].length - 1;
    }

    public static final class Benefit {
        public final String title;
        public final String restriction;

        private Benefit(String title, String restriction) {
            this.title = title;
            this.restriction = restriction;
        }
    }
}
