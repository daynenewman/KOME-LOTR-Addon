package kome.common.data;

import kome.common.config.KOMEConfigRegistry;
import java.math.BigInteger;
import java.math.BigDecimal;
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
        KOMEConfigRegistry.PopulationSettings settings = KOMEConfigRegistry.population();
        BigInteger denominator = rateDenominator(settings.getHoursPerPopulationPointCentiHours());
        Map<String, BigInteger> sourceUnits = new TreeMap<String, BigInteger>();
        for (KOMEPopulationRateContribution row : getPopulationRateContributions(data, settings)) {
            if (row.receivingFaction.length() == 0) continue;
            // The row's control policy chooses the weight; never sum already-rounded row rates.
            long multiplier = "NATIVE".equals(row.status) ? KOMEConfigRegistry.CAPTURED_MULTIPLIER_SCALE
                    : settings.getCapturedBuildMultiplierBasisPoints();
            BigInteger source = rateNumerator(row.approvedCentiHours, multiplier);
            BigInteger old = sourceUnits.get(row.receivingFaction);
            sourceUnits.put(row.receivingFaction, (old == null ? BigInteger.ZERO : old).add(source));
        }
        Map<String, KOMEPopulationRate> result = new LinkedHashMap<String, KOMEPopulationRate>();
        for (Map.Entry<String, BigInteger> entry : sourceUnits.entrySet()) result.put(entry.getKey(), fixedRate(entry.getValue(), denominator));
        return Collections.unmodifiableMap(result);
    }

    /** Sorted Build audit rows and the single control/recipient policy used by aggregation. */
    public static List<KOMEPopulationRateContribution> getPopulationRateContributions(KOMEWorldData data) {
        return getPopulationRateContributions(data, KOMEConfigRegistry.population());
    }

    private static List<KOMEPopulationRateContribution> getPopulationRateContributions(KOMEWorldData data,
            KOMEConfigRegistry.PopulationSettings settings) {
        BigInteger denominator = rateDenominator(settings.getHoursPerPopulationPointCentiHours());
        List<KOMEPopulationRateContribution> result = new ArrayList<KOMEPopulationRateContribution>();
        for (KOMEPlayerBuild build : KOMEBuildService.activeNormalBuilds(data)) {
            String faction = KOMEAlliance.normalizeFactionKey(build.populationFaction);
            KOMEConquestTile tile = data == null ? null : data.conquestTiles.get(KOMEConquestTile.normalizeId(build.tileId));
            String controller = tile == null ? "" : KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());
            long approved = build.approvedCentiHours();
            KOMEPopulationRate original = fixedRate(rateNumerator(approved, KOMEConfigRegistry.CAPTURED_MULTIPLIER_SCALE), denominator);
            String status = "UNCONTROLLED", receiving = "", multiplier = "0";
            long multiplierBasisPoints = 0L;
            if (controller.length() > 0 && faction.length() > 0 && faction.equals(controller)) {
                status = "NATIVE"; receiving = faction; multiplier = "1";
                multiplierBasisPoints = KOMEConfigRegistry.CAPTURED_MULTIPLIER_SCALE;
            } else if (controller.length() > 0 && faction.length() > 0) {
                status = "CAPTURED"; receiving = controller;
                multiplierBasisPoints = settings.getCapturedBuildMultiplierBasisPoints();
                multiplier = BigDecimal.valueOf(multiplierBasisPoints)
                        .divide(BigDecimal.valueOf(KOMEConfigRegistry.CAPTURED_MULTIPLIER_SCALE)).stripTrailingZeros().toPlainString();
            }
            result.add(new KOMEPopulationRateContribution(build.id, build.displayName, build.tileId, faction, controller,
                receiving, approved, original, fixedRate(rateNumerator(approved, multiplierBasisPoints), denominator), status, multiplier));
        }
        return Collections.unmodifiableList(result);
    }

    /** Exact Build/config centi-hour rate, also used by focused conversion tests. */
    static KOMEPopulationRate rate(long approvedCentiHours, long centiHoursPerPoint) {
        return fixedRate(rateNumerator(approvedCentiHours, KOMEConfigRegistry.CAPTURED_MULTIPLIER_SCALE),
                rateDenominator(centiHoursPerPoint));
    }

    /** Fixed units = approved centi-hours * SCALE * basis points / (configured centi-hours * 10,000). */
    private static BigInteger rateNumerator(long approvedCentiHours, long basisPoints) {
        return BigInteger.valueOf(KOMEBuildTime.requireNonnegative(approvedCentiHours))
                .multiply(BigInteger.valueOf(KOMEPopulationRate.SCALE)).multiply(BigInteger.valueOf(basisPoints));
    }

    private static BigInteger rateDenominator(long centiHours) {
        if (centiHours <= 0L) throw new IllegalArgumentException("Population centi-hours must be positive");
        return BigInteger.valueOf(centiHours)
                .multiply(BigInteger.valueOf(KOMEConfigRegistry.CAPTURED_MULTIPLIER_SCALE));
    }

    /** Positive half-up at the final conversion only; preserve existing derived-rate saturation. */
    private static KOMEPopulationRate fixedRate(BigInteger numerator, BigInteger denominator) {
        BigInteger rounded = numerator.add(denominator.shiftRight(1)).divide(denominator);
        return new KOMEPopulationRate(rounded.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : rounded.longValue());
    }
}
