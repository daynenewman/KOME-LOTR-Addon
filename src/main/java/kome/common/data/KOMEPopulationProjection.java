package kome.common.data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import kome.common.config.KOMEConfigRegistry;

/** Immutable, uncached server projection. No bank, cost, rate or liveness authority lives here. */
public final class KOMEPopulationProjection {
    public final String faction;
    public final long availablePopulationCenti;
    public final BigInteger activePopulationCenti;
    public final BigInteger representedPopulationCenti;
    public final BigInteger dailyRateUnits;
    public final boolean capEnabled;
    public final long capCenti;

    public KOMEPopulationProjection(String faction, long available, BigInteger active, BigInteger rate,
            boolean capEnabled, long capCenti) {
        if (available < 0L || capCenti < 0L || active == null || rate == null
                || active.signum() < 0 || rate.signum() < 0 || capEnabled && capCenti == 0L)
            throw new IllegalArgumentException("Invalid population projection");
        this.faction = KOMEAlliance.normalizeFactionKey(faction);
        this.availablePopulationCenti = available;
        this.activePopulationCenti = active;
        this.representedPopulationCenti = active.add(BigInteger.valueOf(available));
        this.dailyRateUnits = rate;
        this.capEnabled = capEnabled;
        this.capCenti = capCenti;
    }

    public static KOMEPopulationProjection of(KOMEWorldData data, String faction) {
        KOMEConfigRegistry.PopulationSettings settings = KOMEConfigRegistry.population();
        String key = KOMEAlliance.normalizeFactionKey(faction);
        BigInteger rate = KOMEPopulationRateService.getExactDailyPopulationRates(data, settings).get(key);
        return project(data, key, rate, settings, KOMEPopulationService.livingRecords(data));
    }

    public static Map<String, KOMEPopulationProjection> all(KOMEWorldData data) {
        KOMEConfigRegistry.PopulationSettings settings = KOMEConfigRegistry.population();
        Map<String, BigInteger> rates = KOMEPopulationRateService.getExactDailyPopulationRates(data, settings);
        TreeSet<String> factions = new TreeSet<String>(rates.keySet());
        factions.addAll(data.factionPopulations.keySet());
        for (KOMEConquestTile tile : data.conquestTiles.values()) {
            if (tile != null) factions.add(tile.projectRulingFaction());
        }
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (record != null) factions.add(KOMEPopulationService.populationFaction(record));
        }
        factions.remove("");
        Map<String, KOMEPopulationProjection> result = new TreeMap<String, KOMEPopulationProjection>();
        java.util.List<KOMEHiredUnitRecord> living = KOMEPopulationService.livingRecords(data);
        for (String faction : factions) result.put(faction, project(data, faction, rates.get(faction), settings, living));
        return Collections.unmodifiableMap(result);
    }

    private static KOMEPopulationProjection project(KOMEWorldData data, String faction, BigInteger rate,
            KOMEConfigRegistry.PopulationSettings settings, java.util.Collection<KOMEHiredUnitRecord> living) {
        return new KOMEPopulationProjection(faction,
                faction.isEmpty() ? 0L : KOMEPopulationService.getAvailablePopulationCenti(data, faction),
                KOMEPopulationService.getExactActivePopulationCenti(faction, living),
                rate == null ? BigInteger.ZERO : rate, settings.isPopulationCapEnabled(), settings.getPopulationCapCenti().orElse(0L));
    }

    public static String formatCenti(long centi) { return formatCenti(BigInteger.valueOf(centi)); }

    public static String formatCenti(BigInteger centi) {
        if (centi == null || centi.signum() < 0) throw new IllegalArgumentException("Population must be nonnegative");
        return new BigDecimal(centi, 2).toPlainString();
    }

    public static String formatRate(BigInteger units) {
        if (units == null || units.signum() < 0) throw new IllegalArgumentException("Rate must be nonnegative");
        return new BigDecimal(units).divide(BigDecimal.valueOf(KOMEPopulationRate.SCALE))
                .stripTrailingZeros().toPlainString();
    }

    public String summary() {
        return "Faction " + faction + ", Available Population " + formatCenti(availablePopulationCenti)
                + ", Active Population " + formatCenti(activePopulationCenti)
                + ", Daily Population Rate " + formatRate(dailyRateUnits)
                + ", Population cap " + (capEnabled ? formatCenti(capCenti) : "disabled (uncapped)");
    }
}
