package kome.common.data;

public final class KOMEAllianceProgressionService {
    public static final String ALLIED_TRADES = "allied.trades";
    public static final String ELIGIBLE_KILLS = "military.kills";
    public static final String OFFENSIVE_CAPACITY_MAX = "military.population.max";

    private KOMEAllianceProgressionService() {
    }

    public static boolean refreshFactionCompletion(KOMEWorldData data, KOMEAlliance alliance, String faction,
            String type, long worldTime) {
        if (data == null || alliance == null) {
            return false;
        }
        String normalizedFaction = KOMEAlliance.normalizeFactionKey(faction);
        String normalizedType = KOMEAlliance.normalizeType(type);
        KOMEAllianceFactionLedger ledger = alliance.getFactionLedger(normalizedFaction);
        int target = alliance.getTier(normalizedType) + 1;
        if (ledger == null || target < 1 || target > KOMEAlliance.maxTier(normalizedType)
                || ledger.getCompletedTier(normalizedType) >= target) {
            return false;
        }

        int currentCapacity = data.getFactionEffectiveOffensiveCapacity(normalizedFaction);
        if (KOMEAlliance.MILITARY.equals(normalizedType)
                && currentCapacity > alliance.getDelivered(normalizedFaction, OFFENSIVE_CAPACITY_MAX)) {
            alliance.setDelivered(normalizedFaction, OFFENSIVE_CAPACITY_MAX, currentCapacity);
        }

        String id = KOMEAllianceQuotaPool.assignmentId(normalizedType, target);
        KOMEAllianceQuotaPool.Requirement requirement = KOMEAllianceQuotaPool.resolve(data, alliance,
            normalizedFaction, normalizedType, target);
        if (requirement == null || !requirement.isValid()
                || alliance.getDelivered(normalizedFaction, id) < requirement.requiredUnits) {
            return false;
        }
        if (!activityComplete(data, alliance, normalizedFaction, normalizedType, target)) {
            return false;
        }
        if (KOMEAlliance.MILITARY.equals(normalizedType)) {
            int populationRequired = data.getAlliancePopulationRequirement(normalizedType, target);
            if (alliance.getDelivered(normalizedFaction, OFFENSIVE_CAPACITY_MAX) < populationRequired) {
                return false;
            }
        }
        ledger.setCompletedTier(normalizedType, target);
        boolean advanced = recomputeSharedTier(alliance, normalizedType, worldTime);
        if (advanced && KOMEAlliance.MILITARY.equals(normalizedType) && alliance.getTier(normalizedType) >= 3) {
            KOMEWarService.reconcileAutomaticMilitarySupport(data, System.currentTimeMillis(), "Military T3 became effective");
            KOMEWartimeStewardshipService.revalidateAll(data, System.currentTimeMillis(), "Military T3 became effective");
        }
        return advanced;
    }

    public static boolean activityComplete(KOMEWorldData data, KOMEAlliance alliance, String faction,
            String type, int tier) {
        int required = data.getAllianceActivityRequirement(type, tier);
        if (required <= 0) {
            return true;
        }
        if (KOMEAlliance.CIVIL.equals(type) || KOMEAlliance.TRADE.equals(type)) {
            return alliance.getDelivered(faction, ALLIED_TRADES) >= required;
        }
        return KOMEAlliance.MILITARY.equals(type)
            && alliance.getDelivered(faction, ELIGIBLE_KILLS) >= required;
    }

    public static boolean recomputeSharedTier(KOMEAlliance alliance, String type, long worldTime) {
        if (alliance == null || !alliance.hasAccepted(type)) {
            return false;
        }
        int current = alliance.getTier(type);
        int target = current + 1;
        if (target < 1 || target > KOMEAlliance.maxTier(type)) {
            return false;
        }
        KOMEAllianceFactionLedger first = alliance.getFactionLedger(alliance.factionA);
        KOMEAllianceFactionLedger second = alliance.getFactionLedger(alliance.factionB);
        boolean firstComplete = first != null && (first.kinglessWaived || first.getCompletedTier(type) >= target);
        boolean secondComplete = second != null && (second.kinglessWaived || second.getCompletedTier(type) >= target);
        if (!firstComplete || !secondComplete) {
            return false;
        }
        alliance.setTier(type, target, "Shared faction requirements", worldTime);
        return true;
    }
}
