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
            "battleSupport", "encirclement", "season"
    };

    private KOMEConfigInspection() {
    }

    public static List<EffectiveValue> getAllEffectiveValues() {
        List<EffectiveValue> values = new ArrayList<EffectiveValue>();
        for (String category : CATEGORIES) {
            values.addAll(valuesFor(category));
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
                List<EffectiveValue> values = valuesFor(supported);
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

    private static List<EffectiveValue> valuesFor(String category) {
        List<EffectiveValue> values = new ArrayList<EffectiveValue>();
        if ("dailyBatch".equals(category)) {
            KOMEConfigRegistry.DailyBatchSettings settings = KOMEConfigRegistry.dailyBatch();
            add(values, category, "localTime", settings.getLocalTime());
            add(values, category, "timezone", settings.getTimezone());
        } else if ("population".equals(category)) {
            KOMEConfigRegistry.PopulationSettings s = KOMEConfigRegistry.population();
            add(values, category, "hoursPerPopulationPoint", s.getHoursPerPopulationPoint());
            add(values, category, "capturedBuildMultiplier", s.getCapturedBuildMultiplier());
            add(values, category, "offlinePopulationCatchUp", s.isOfflinePopulationCatchUp());
            add(values, category, "populationCapEnabled", s.isPopulationCapEnabled());
            add(values, category, "populationCapValue", s.getPopulationCapValue());
            add(values, category, "encirclementPopulationSuppressionEnabled", s.isEncirclementPopulationSuppressionEnabled());
        } else if ("movement".equals(category)) {
            KOMEConfigRegistry.MovementSettings s = KOMEConfigRegistry.movement();
            add(values, category, "footOrMixedTilesPerDay", s.getFootOrMixedTilesPerDay());
            add(values, category, "fullyMountedTilesPerDay", s.getFullyMountedTilesPerDay());
        } else if ("battle".equals(category)) {
            KOMEConfigRegistry.BattleSettings s = KOMEConfigRegistry.battle();
            add(values, category, "responseLevel1Minutes", s.getResponseLevel1Minutes());
            add(values, category, "responseLevel2Minutes", s.getResponseLevel2Minutes());
            add(values, category, "responseLevel3Minutes", s.getResponseLevel3Minutes());
        } else if ("muster".equals(category)) {
            KOMEConfigRegistry.MusterSettings s = KOMEConfigRegistry.muster();
            add(values, category, "threatDistanceTiles", s.getThreatDistanceTiles());
            add(values, category, "budgetDailyPopulationMultiplier", s.getBudgetDailyPopulationMultiplier());
            add(values, category, "arrivalDelayHours", s.getArrivalDelayHours());
            add(values, category, "encircledCapitalArrivalPolicy", s.getEncircledCapitalArrivalPolicy());
        } else if ("siege".equals(category)) {
            KOMEConfigRegistry.SiegeSettings s = KOMEConfigRegistry.siege();
            add(values, category, "gateHpPerApprovedHour", s.getGateHpPerApprovedHour());
            add(values, category, "normalSegmentSupportMinimumTroops", s.getNormalSegmentSupportMinimumTroops());
            add(values, category, "supportFallbackGraceSeconds", s.getSupportFallbackGraceSeconds());
            add(values, category, "preBreachRepair", s.getPreBreachRepair());
            add(values, category, "postBreachRepairEnabled", s.isPostBreachRepairEnabled());
            add(values, category, "exteriorMarginBlocks", s.getExteriorMarginBlocks());
        } else if ("battleSupport".equals(category)) {
            KOMEConfigRegistry.BattleSupportSettings s = KOMEConfigRegistry.battleSupport();
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
            KOMEConfigRegistry.EncirclementSettings s = KOMEConfigRegistry.encirclement();
            add(values, category, "starvationGraceDays", s.getStarvationGraceDays());
            add(values, category, "announcedAssaultNoticeHours", s.getAnnouncedAssaultNoticeHours());
            add(values, category, "offlineStarvationCatchUp", s.isOfflineStarvationCatchUp());
        } else if ("season".equals(category)) {
            KOMEConfigRegistry.SeasonSettings s = KOMEConfigRegistry.season();
            add(values, category, "minimumWarSeasonLengthDays", s.getMinimumWarSeasonLengthDays());
            add(values, category, "automaticFinaleEnabled", s.isAutomaticFinaleEnabled());
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
