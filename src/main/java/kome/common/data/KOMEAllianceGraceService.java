package kome.common.data;

/** Side-specific administrative lifecycle changes for a mutual alliance. */
public final class KOMEAllianceGraceService {
    public static final String SUCCESSION = "succession";
    public static final String CONTRIBUTION = "contribution";

    private KOMEAllianceGraceService() {
    }

    public static Change set(KOMEAlliance alliance, String affectedFaction, String type,
            long nowMillis, long durationMillis) {
        KOMEAllianceFactionLedger ledger = requireLedger(alliance, affectedFaction);
        String normalizedType = requireType(type);
        long formerDeadline = deadline(ledger, normalizedType);
        if (SUCCESSION.equals(normalizedType)) {
            ledger.beginSuccession(nowMillis, durationMillis);
        } else {
            ledger.beginContributionGrace("Administrative grace", nowMillis, durationMillis,
                alliance.getFactionTier(ledger.faction, KOMEAlliance.CIVIL),
                alliance.getFactionTier(ledger.faction, KOMEAlliance.TRADE),
                alliance.getFactionTier(ledger.faction, KOMEAlliance.MILITARY));
        }
        return new Change(ledger.faction, normalizedType, formerDeadline, deadline(ledger, normalizedType),
            "administrative set");
    }

    public static Change expire(KOMEWorldData data, KOMEAlliance alliance, String affectedFaction,
            String type, long nowMillis, long worldTime) {
        if (data == null) {
            throw new IllegalArgumentException("World data is required.");
        }
        KOMEAllianceFactionLedger ledger = requireLedger(alliance, affectedFaction);
        String normalizedType = requireType(type);
        long formerDeadline = deadline(ledger, normalizedType);
        if (SUCCESSION.equals(normalizedType)) {
            ledger.successionEndMillis = nowMillis;
            data.expireSuccessionSide(alliance, ledger.faction, worldTime);
        } else {
            ledger.graceEndMillis = nowMillis;
            data.expireContributionSide(alliance, ledger.faction, worldTime);
        }
        return new Change(ledger.faction, normalizedType, formerDeadline, 0L,
            "administrative force-expire");
    }

    public static KOMEAllianceFactionLedger requireLedger(KOMEAlliance alliance, String affectedFaction) {
        String faction = KOMEAlliance.normalizeFactionKey(affectedFaction);
        if (alliance == null || faction.length() == 0 || !alliance.involves(faction)) {
            throw new IllegalArgumentException("Affected faction must be one side of the canonical alliance pair.");
        }
        KOMEAllianceFactionLedger ledger = alliance.getFactionLedger(faction);
        if (ledger == null) {
            throw new IllegalArgumentException("The affected faction ledger is unavailable.");
        }
        return ledger;
    }

    private static String requireType(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(java.util.Locale.ROOT);
        if (!SUCCESSION.equals(normalized) && !CONTRIBUTION.equals(normalized)) {
            throw new IllegalArgumentException("Grace type must be succession or contribution.");
        }
        return normalized;
    }

    private static long deadline(KOMEAllianceFactionLedger ledger, String type) {
        return SUCCESSION.equals(type) ? ledger.successionEndMillis : ledger.graceEndMillis;
    }

    public static final class Change {
        public final String affectedFaction;
        public final String type;
        public final long formerDeadline;
        public final long newDeadline;
        public final String reason;

        private Change(String affectedFaction, String type, long formerDeadline, long newDeadline, String reason) {
            this.affectedFaction = affectedFaction;
            this.type = type;
            this.formerDeadline = formerDeadline;
            this.newDeadline = newDeadline;
            this.reason = reason;
        }
    }
}
