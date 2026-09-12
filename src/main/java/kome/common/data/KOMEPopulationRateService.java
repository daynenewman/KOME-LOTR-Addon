package kome.common.data;

import kome.common.config.KOMEConfigRegistry;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /** Sorted faction map. Exact native/captured source units are summed before one conversion. */
    public static Map<String, KOMEPopulationRate> getAllDailyPopulationRates(KOMEWorldData data) {
        int hours = Math.max(1, KOMEConfigRegistry.population().getHoursPerPopulationPoint());
        Map<String, BigDecimal> sourceUnits = new TreeMap<String, BigDecimal>();
        for (KOMEPopulationRateContribution row : getPopulationRateContributions(data)) {
            if (row.receivingFaction.length() == 0) continue;
            BigDecimal source = exactUnits(row.approvedHalfHours, hours, row.multiplier);
            BigDecimal old = sourceUnits.get(row.receivingFaction);
            sourceUnits.put(row.receivingFaction, (old == null ? BigDecimal.ZERO : old).add(source));
        }
        Map<String, KOMEPopulationRate> result = new LinkedHashMap<String, KOMEPopulationRate>();
        for (Map.Entry<String, BigDecimal> entry : sourceUnits.entrySet()) result.put(entry.getKey(), fixedRate(entry.getValue()));
        return Collections.unmodifiableMap(result);
    }

    /** Sorted Build audit rows and the single control/recipient policy used by aggregation. */
    public static List<KOMEPopulationRateContribution> getPopulationRateContributions(KOMEWorldData data) {
        int hours = Math.max(1, KOMEConfigRegistry.population().getHoursPerPopulationPoint());
        List<KOMEPopulationRateContribution> result = new ArrayList<KOMEPopulationRateContribution>();
        for (KOMEPlayerBuild build : KOMEBuildService.activeNormalBuilds(data)) {
            String faction = KOMEAlliance.normalizeFactionKey(build.populationFaction);
            KOMEConquestTile tile = data == null ? null : data.conquestTiles.get(KOMEConquestTile.normalizeId(build.tileId));
            String controller = tile == null ? "" : KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());
            int approved = build.approvedHalfHours();
            KOMEPopulationRate original = rate(approved, hours);
            String status = "UNCONTROLLED", receiving = "", multiplier = "0";
            BigDecimal exactCurrent = BigDecimal.ZERO;
            if (controller.length() > 0 && faction.length() > 0 && faction.equals(controller)) {
                status = "NATIVE"; receiving = faction; multiplier = "1"; exactCurrent = exactUnits(approved, hours, multiplier);
            } else if (controller.length() > 0 && faction.length() > 0) {
                status = "CAPTURED"; receiving = controller;
                multiplier = BigDecimal.valueOf(KOMEConfigRegistry.population().getCapturedBuildMultiplier()).stripTrailingZeros().toPlainString();
                exactCurrent = exactUnits(approved, hours, multiplier);
            }
            result.add(new KOMEPopulationRateContribution(build.id, build.displayName, build.tileId, faction, controller,
                receiving, approved, original, fixedRate(exactCurrent), status, multiplier));
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

    private static BigDecimal exactUnits(long halfHours, int hours, String multiplier) {
        return BigDecimal.valueOf(halfHours).multiply(BigDecimal.valueOf(KOMEPopulationRate.SCALE))
            .multiply(new BigDecimal(multiplier)).divide(BigDecimal.valueOf(2L * Math.max(1, hours)), 18, RoundingMode.HALF_UP);
    }
    private static KOMEPopulationRate fixedRate(BigDecimal units) {
        BigInteger rounded = units.setScale(0, RoundingMode.HALF_UP).toBigInteger();
        return new KOMEPopulationRate(rounded.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : rounded.longValue());
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - Math.max(0L, right) ? Long.MAX_VALUE : left + Math.max(0L, right);
    }
}
