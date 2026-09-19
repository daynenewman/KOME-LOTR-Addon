package kome.common.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Read-only deterministic presentation of the effective typed KOME configuration. */
public final class KOMEConfigInspection {
    private static final String[] CATEGORIES = {
            "dailyBatch", "population", "movement", "battle", "muster", "siege",
            "battleSupport", "encirclement", "season", "gear"
    };

    private KOMEConfigInspection() {
    }

    public static List<EffectiveValue> getAllEffectiveValues() {
        List<EffectiveValue> values = new ArrayList<EffectiveValue>();
        KOMEConfigRegistry.ValidatedConfig snapshot = KOMEConfigRegistry.currentValidated();
        for (String category : CATEGORIES) {
            values.addAll(valuesFor(category, snapshot));
        }
        Collections.sort(values, ORDER);
        return Collections.unmodifiableList(values);
    }

    public static List<EffectiveValue> getEffectiveValues(String category) {
        if (category == null) {
            throw new IllegalArgumentException("Unknown KOME config category: null");
        }
        for (String supported : CATEGORIES) {
            if (supported.equalsIgnoreCase(category)) {
                List<EffectiveValue> values = valuesFor(supported, KOMEConfigRegistry.currentValidated());
                Collections.sort(values, ORDER);
                return Collections.unmodifiableList(values);
            }
        }
        throw new IllegalArgumentException("Unknown KOME config category: " + category);
    }

    public static List<String> getSupportedCategories() {
        List<String> result = new ArrayList<String>();
        Collections.addAll(result, CATEGORIES);
        return Collections.unmodifiableList(result);
    }

    private static List<EffectiveValue> valuesFor(String category, KOMEConfigRegistry.ValidatedConfig snapshot) {
        List<EffectiveValue> values = new ArrayList<EffectiveValue>();
        if ("dailyBatch".equals(category)) {
            KOMEConfigRegistry.DailyBatchSettings settings = snapshot.getDailyBatch();
            add(values, category, "localTime", settings.getLocalTime());
            add(values, category, "timezone", settings.getTimezone());
        } else if ("population".equals(category)) {
            KOMEConfigRegistry.PopulationSettings s = snapshot.getPopulation();
            add(values, category, "hoursPerPopulationPoint", s.formatHoursPerPopulationPoint());
            add(values, category, "capturedBuildMultiplier", s.formatCapturedBuildMultiplier());
            add(values, category, "offlinePopulationCatchUp", s.isOfflinePopulationCatchUp());
            add(values, category, "populationCapEnabled", s.isPopulationCapEnabled());
            add(values, category, "populationCapValue", s.formatPopulationCap());
            add(values, category, "populationCapCenti", s.getPopulationCapCenti().isPresent()
                    ? Long.toString(s.getPopulationCapCenti().getAsLong()) : "TBD");
            add(values, category, "registryReady", KOMEConfigRegistry.isReady());
            add(values, category, "worldConfigurationLocked", KOMEConfigRegistry.isWorldConfigurationLocked());
            add(values, category, "lastApplyStatus", KOMEConfigRegistry.getLastApplyStatus());
            add(values, category, "encirclementPopulationSuppressionEnabled", s.isEncirclementPopulationSuppressionEnabled());
            add(values, category, "unitPopulationCostOverrides", s.getUnitPopulationCostOverrides());
        } else if ("movement".equals(category)) {
            KOMEConfigRegistry.MovementSettings s = snapshot.getMovement();
            add(values, category, "footOrMixedTilesPerDay", s.getFootOrMixedTilesPerDay());
            add(values, category, "fullyMountedTilesPerDay", s.getFullyMountedTilesPerDay());
        } else if ("battle".equals(category)) {
            KOMEConfigRegistry.BattleSettings s = snapshot.getBattle();
            add(values, category, "responseLevel1Minutes", s.getResponseLevel1Minutes());
            add(values, category, "responseLevel2Minutes", s.getResponseLevel2Minutes());
            add(values, category, "responseLevel3Minutes", s.getResponseLevel3Minutes());
        } else if ("muster".equals(category)) {
            KOMEConfigRegistry.MusterSettings s = snapshot.getMuster();
            add(values, category, "threatDistanceTiles", s.getThreatDistanceTiles());
            add(values, category, "budgetDailyPopulationMultiplier", s.getBudgetDailyPopulationMultiplier());
            add(values, category, "arrivalDelayHours", s.getArrivalDelayHours());
            add(values, category, "encircledCapitalArrivalPolicy", s.getEncircledCapitalArrivalPolicy());
        } else if ("siege".equals(category)) {
            KOMEConfigRegistry.SiegeSettings s = snapshot.getSiege();
            add(values, category, "gateHpPerApprovedHour", s.getGateHpPerApprovedHour());
            add(values, category, "gateBaselineWidth", s.getGateBaselineWidth());
            add(values, category, "gateBaselineHeight", s.getGateBaselineHeight());
            add(values, category, "gateFullBonusWidth", s.getGateFullBonusWidth());
            add(values, category, "gateFullBonusHeight", s.getGateFullBonusHeight());
            add(values, category, "gateMaxSizeMultiplier", s.getGateMaxSizeMultiplier());
            add(values, category, "gateSizeCurveExponent", s.getGateSizeCurveExponent());
            add(values, category, "normalSegmentSupportMinimumTroops", s.getNormalSegmentSupportMinimumTroops());
            add(values, category, "supportFallbackGraceSeconds", s.getSupportFallbackGraceSeconds());
            add(values, category, "preBreachRepair", s.getPreBreachRepair());
            add(values, category, "postBreachRepairEnabled", s.isPostBreachRepairEnabled());
            add(values, category, "exteriorMarginBlocks", s.getExteriorMarginBlocks());
            add(values, category, "activeSiegeCheckInWindowMinutes", s.getActiveSiegeCheckInWindowMinutes());
        } else if ("battleSupport".equals(category)) {
            KOMEConfigRegistry.BattleSupportSettings s = snapshot.getBattleSupport();
            add(values, category, "mode", s.getMode());
            add(values, category, "fullDamageDistanceBlocks", s.getFullDamageDistanceBlocks());
            add(values, category, "halfDamageDistanceBlocks", s.getHalfDamageDistanceBlocks());
            add(values, category, "lowDamageDistanceBlocks", s.getLowDamageDistanceBlocks());
            add(values, category, "minimumDamageDistanceBlocks", s.getMinimumDamageDistanceBlocks());
            add(values, category, "halfDamageMultiplier", s.getHalfDamageMultiplier());
            add(values, category, "lowDamageMultiplier", s.getLowDamageMultiplier());
            add(values, category, "minimumDamageMultiplier", s.getMinimumDamageMultiplier());
            add(values, category, "hardFallbackDistanceBlocks", s.getHardFallbackDistanceBlocks());
            add(values, category, "openBattleRadiusBlocks", s.getOpenBattleRadiusBlocks());
        } else if ("encirclement".equals(category)) {
            KOMEConfigRegistry.EncirclementSettings s = snapshot.getEncirclement();
            add(values, category, "starvationGraceDays", s.getStarvationGraceDays());
            add(values, category, "announcedAssaultNoticeHours", s.getAnnouncedAssaultNoticeHours());
            add(values, category, "offlineStarvationCatchUp", s.isOfflineStarvationCatchUp());
        } else if ("season".equals(category)) {
            KOMEConfigRegistry.SeasonSettings s = snapshot.getSeason();
            add(values, category, "minimumWarSeasonLengthDays", s.getMinimumWarSeasonLengthDays());
            add(values, category, "automaticFinaleEnabled", s.isAutomaticFinaleEnabled());
            add(values,category,"warInactivityDurationMillis",s.getWarInactivityDurationMillis()); add(values,category,"warBondsEnabled",s.isWarBondsEnabled()); add(values,category,"attackerWarBond",s.getAttackerWarBond()); add(values,category,"participationWarBond",s.getParticipationWarBond());
        } else if ("gear".equals(category)) {
            add(values, category, "restrictionRules", snapshot.getGear().getRulesByItemId());
        }
        return values;
    }

    private static void add(List<EffectiveValue> values, String category, String key,
            Object value) {
        values.add(new EffectiveValue(category, key, format(value)));
    }

    private static String format(Object value) {
        if (value instanceof OptionalInt) return ((OptionalInt) value).isPresent()
                ? Integer.toString(((OptionalInt) value).getAsInt()) : "TBD";
        if (value instanceof OptionalDouble) return ((OptionalDouble) value).isPresent()
                ? Double.toString(((OptionalDouble) value).getAsDouble()) : "TBD";
        if (value instanceof Double) return Double.toString(((Double) value).doubleValue());
        if (value instanceof Enum) return ((Enum) value).name();
        return String.valueOf(value);
    }

    private static final Comparator<EffectiveValue> ORDER = new Comparator<EffectiveValue>() {
        @Override public int compare(EffectiveValue a, EffectiveValue b) {
            int result = a.category.compareTo(b.category);
            return result == 0 ? a.key.compareTo(b.key) : result;
        }
    };

    public static final class EffectiveValue {
        private final String category;
        private final String key;
        private final String value;
        private EffectiveValue(String category, String key, String value) {
            this.category = category; this.key = key; this.value = value;
        }
        public String getCategory() { return category; }
        public String getKey() { return key; }
        public String getValue() { return value; }
        public String format() { return category + "." + key + "=" + value; }
    }
}
