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

    /** Unsaturated fixed-rate units; the sole faction aggregation used by payouts and displays. */
    public static Map<String, BigInteger> getExactDailyPopulationRates(KOMEWorldData data,
            KOMEConfigRegistry.PopulationSettings settings) {
        if (data != null)
            KOMEPopulationDevelopmentService.requireCompatibleSettings(data, settings);
        BigInteger denominator = rateDenominator(settings.getHoursPerPopulationPointCentiHours());
        Map<String, BigInteger> sourceUnits = new TreeMap<String, BigInteger>();
        for (KOMEPopulationRateContribution row : getPopulationRateContributions(data, settings)) {
            if (row.receivingFaction.length() == 0) continue;
            // Control already selected the exact weight; status/text are presentation only.
            BigInteger source = rateNumerator(row.developedNativeCentiHours,
                    row.multiplierBasisPoints);
            BigInteger old = sourceUnits.get(row.receivingFaction);
            sourceUnits.put(row.receivingFaction, (old == null ? BigInteger.ZERO : old).add(source));
        }
        Map<String, BigInteger> result = new LinkedHashMap<String, BigInteger>();
        for (Map.Entry<String, BigInteger> entry : sourceUnits.entrySet()) result.put(entry.getKey(), roundedUnits(entry.getValue(), denominator));
        return Collections.unmodifiableMap(result);
    }

    /** Sorted Build audit rows and the single control/recipient policy used by aggregation. */
    public static List<KOMEPopulationRateContribution> getPopulationRateContributions(KOMEWorldData data) {
        return getPopulationRateContributions(data, KOMEConfigRegistry.population());
    }

    public static List<KOMEPopulationRateContribution> getPopulationRateContributions(KOMEWorldData data,
            KOMEConfigRegistry.PopulationSettings settings) {
        if (data != null)
            KOMEPopulationDevelopmentService.requireCompatibleSettings(data, settings);
        BigInteger denominator = rateDenominator(settings.getHoursPerPopulationPointCentiHours());
        List<KOMEPopulationRateContribution> result = new ArrayList<KOMEPopulationRateContribution>();
        for (KOMEPlayerBuild build : KOMEBuildService.activeNormalBuilds(data)) {
            String faction = KOMEAlliance.normalizeFactionKey(build.populationFaction);
            KOMEConquestTile tile = data == null ? null : data.conquestTiles.get(KOMEConquestTile.normalizeId(build.tileId));
            String controller = tile == null ? "" : tile.projectRulingFaction();
            long approved = build.approvedCentiHours();
            long developed = build.developedNativeCentiHours();
            long pending = build.pendingNativeCentiHours();
            BigInteger original = roundedUnits(rateNumerator(developed,
                KOMEConfigRegistry.CAPTURED_MULTIPLIER_SCALE), denominator);
            String status = "UNCONTROLLED", receiving = "";
            long multiplierBasisPoints = 0L;
            if (controller.length() > 0 && faction.length() > 0 && faction.equals(controller)) {
                status = "NATIVE"; receiving = faction;
                multiplierBasisPoints = KOMEConfigRegistry.CAPTURED_MULTIPLIER_SCALE;
            } else if (controller.length() > 0 && faction.length() > 0) {
                status = "CAPTURED"; receiving = controller;
                multiplierBasisPoints = settings.getCapturedBuildMultiplierBasisPoints();
            }
            result.add(new KOMEPopulationRateContribution(build.id, build.displayName, build.tileId, faction, controller,
                receiving, approved, developed, pending, original,
                roundedUnits(rateNumerator(developed, multiplierBasisPoints), denominator),
                status, multiplierBasisPoints));
        }
        return Collections.unmodifiableList(result);
    }

    /** Exact rounded native rate for a developed centi-hour allocation. */
    public static BigInteger rateUnitsForDevelopedCentiHours(long developedCentiHours,
            KOMEConfigRegistry.PopulationSettings settings) {
        BigInteger denominator = rateDenominator(
            settings.getHoursPerPopulationPointCentiHours());
        return roundedUnits(rateNumerator(developedCentiHours,
            KOMEConfigRegistry.CAPTURED_MULTIPLIER_SCALE), denominator);
    }

    /** Current native-only rates; captured contributions deliberately do not affect ceiling comparison. */
    public static Map<String, BigInteger> getExactNativeDevelopedRates(KOMEWorldData data,
            KOMEConfigRegistry.PopulationSettings settings) {
        Map<String, BigInteger> numerators = new TreeMap<String, BigInteger>();
        for (KOMEPopulationRateContribution row : getPopulationRateContributions(data, settings)) {
            if (!"NATIVE".equals(row.status) || row.receivingFaction.length() == 0) continue;
            BigInteger old = numerators.get(row.receivingFaction);
            BigInteger value = rateNumerator(row.developedNativeCentiHours,
                KOMEConfigRegistry.CAPTURED_MULTIPLIER_SCALE);
            numerators.put(row.receivingFaction,
                (old == null ? BigInteger.ZERO : old).add(value));
        }
        BigInteger denominator = rateDenominator(
            settings.getHoursPerPopulationPointCentiHours());
        Map<String, BigInteger> result = new LinkedHashMap<String, BigInteger>();
        for (Map.Entry<String, BigInteger> entry : numerators.entrySet())
            result.put(entry.getKey(), roundedUnits(entry.getValue(), denominator));
        return Collections.unmodifiableMap(result);
    }

    /** Exact current production grouped by strategic tile and receiving faction. */
    public static Map<String, Map<String, BigInteger>> getExactTilePopulationRates(
            KOMEWorldData data, KOMEConfigRegistry.PopulationSettings settings) {
        Map<String, Map<String, BigInteger>> numerators =
            new TreeMap<String, Map<String, BigInteger>>();
        for (KOMEPopulationRateContribution row : getPopulationRateContributions(data, settings)) {
            if (row.receivingFaction.length() == 0) continue;
            String tile = KOMEConquestTile.normalizeId(row.tileId);
            Map<String, BigInteger> byFaction = numerators.get(tile);
            if (byFaction == null) {
                byFaction = new TreeMap<String, BigInteger>();
                numerators.put(tile, byFaction);
            }
            BigInteger old = byFaction.get(row.receivingFaction);
            BigInteger value = rateNumerator(row.developedNativeCentiHours,
                row.multiplierBasisPoints);
            byFaction.put(row.receivingFaction,
                (old == null ? BigInteger.ZERO : old).add(value));
        }
        BigInteger denominator = rateDenominator(
            settings.getHoursPerPopulationPointCentiHours());
        Map<String, Map<String, BigInteger>> result =
            new LinkedHashMap<String, Map<String, BigInteger>>();
        for (Map.Entry<String, Map<String, BigInteger>> tile : numerators.entrySet()) {
            Map<String, BigInteger> rates = new LinkedHashMap<String, BigInteger>();
            for (Map.Entry<String, BigInteger> faction : tile.getValue().entrySet())
                rates.put(faction.getKey(), roundedUnits(faction.getValue(), denominator));
            result.put(tile.getKey(), Collections.unmodifiableMap(rates));
        }
        return Collections.unmodifiableMap(result);
    }

    /** Fixed units = developed centi-hours * SCALE * basis points / (configured centi-hours * 10,000). */
    private static BigInteger rateNumerator(long approvedCentiHours, long basisPoints) {
        return BigInteger.valueOf(KOMEBuildTime.requireNonnegative(approvedCentiHours))
                .multiply(BigInteger.valueOf(KOMEPopulationRate.SCALE)).multiply(BigInteger.valueOf(basisPoints));
    }

    private static BigInteger rateDenominator(long centiHours) {
        if (centiHours <= 0L) throw new IllegalArgumentException("Population centi-hours must be positive");
        return BigInteger.valueOf(centiHours)
                .multiply(BigInteger.valueOf(KOMEConfigRegistry.CAPTURED_MULTIPLIER_SCALE));
    }

    private static BigInteger roundedUnits(BigInteger numerator, BigInteger denominator) {
        return numerator.add(denominator.shiftRight(1)).divide(denominator);
    }

}
