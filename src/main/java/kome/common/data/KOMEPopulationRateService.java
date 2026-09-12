package kome.common.data;

import kome.common.config.KOMEConfigRegistry;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The sole derived Daily Population Rate calculation. No payout or persistence occurs here. */
public final class KOMEPopulationRateService {
    private KOMEPopulationRateService() { }

    public static KOMEPopulationRate getDailyPopulationRate(KOMEWorldData data, String faction) {
        KOMEPopulationRate rate = getAllDailyPopulationRates(data).get(KOMEAlliance.normalizeFactionKey(faction));
        return rate == null ? KOMEPopulationRate.ZERO : rate;
    }

    /** Sorted faction map. Native NORMAL hours are summed exactly before one half-up fixed conversion. */
    public static Map<String, KOMEPopulationRate> getAllDailyPopulationRates(KOMEWorldData data) {
        int hours = Math.max(1, KOMEConfigRegistry.population().getHoursPerPopulationPoint());
        Map<String, Long> halfHours = new TreeMap<String, Long>();
        for (KOMEPopulationRateContribution row : getPopulationRateContributions(data)) {
            if (!"NATIVE".equals(row.status)) continue;
            Long old = halfHours.get(row.populationFaction);
            halfHours.put(row.populationFaction, Long.valueOf(saturatingAdd(old == null ? 0L : old.longValue(), row.approvedHalfHours)));
        }
        Map<String, KOMEPopulationRate> result = new LinkedHashMap<String, KOMEPopulationRate>();
        for (Map.Entry<String, Long> entry : halfHours.entrySet()) result.put(entry.getKey(), rate(entry.getValue(), hours));
        return Collections.unmodifiableMap(result);
    }

    /** Sorted Build audit rows. Captured Builds are deliberately zero until KOM-9 supplies its policy. */
    public static List<KOMEPopulationRateContribution> getPopulationRateContributions(KOMEWorldData data) {
        int hours = Math.max(1, KOMEConfigRegistry.population().getHoursPerPopulationPoint());
        List<KOMEPopulationRateContribution> result = new ArrayList<KOMEPopulationRateContribution>();
        for (KOMEPlayerBuild build : KOMEBuildService.activeNormalBuilds(data)) {
            String faction = KOMEAlliance.normalizeFactionKey(build.populationFaction);
            KOMEConquestTile tile = data == null ? null : data.conquestTiles.get(KOMEConquestTile.normalizeId(build.tileId));
            String controller = tile == null ? "" : KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());
            int approved = build.approvedHalfHours();
            KOMEPopulationRate original = rate(approved, hours);
            boolean nativeControl = faction.length() > 0 && faction.equals(controller);
            result.add(new KOMEPopulationRateContribution(build.id, build.displayName, build.tileId, faction, controller,
                approved, original, nativeControl ? original : KOMEPopulationRate.ZERO,
                nativeControl ? "NATIVE" : "CAPTURED_DEFERRED_KOM9"));
        }
        return Collections.unmodifiableList(result);
    }

    /** Exact rational half-hours/(2*hours) converted once using positive half-up rounding. */
    static KOMEPopulationRate rate(long halfHours, int hoursPerPopulationPoint) {
        if (halfHours <= 0L) return KOMEPopulationRate.ZERO;
        BigInteger numerator = BigInteger.valueOf(halfHours).multiply(BigInteger.valueOf(KOMEPopulationRate.SCALE));
        BigInteger denominator = BigInteger.valueOf(2L).multiply(BigInteger.valueOf(Math.max(1, hoursPerPopulationPoint)));
        BigInteger rounded = numerator.add(denominator.shiftRight(1)).divide(denominator);
        return new KOMEPopulationRate(rounded.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : rounded.longValue());
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - Math.max(0L, right) ? Long.MAX_VALUE : left + Math.max(0L, right);
    }
}
