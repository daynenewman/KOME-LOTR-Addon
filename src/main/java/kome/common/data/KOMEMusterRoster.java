package kome.common.data;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Exact, deterministic weighted selection. Costs and native identity come from server probes. */
public final class KOMEMusterRoster {
    private KOMEMusterRoster() { }
    public static final class Unit {
        public final String key, faction, entityId, mountId;
        public final int cost, weight;
        public Unit(String key, String faction, String entityId, String mountId, int cost, int weight) {
            if (key == null || key.isEmpty() || !KOMEAlliance.allFactionKeys().contains(faction)
                    || entityId == null || entityId.isEmpty() || mountId == null || cost <= 0 || weight < 0)
                throw new IllegalArgumentException("Invalid muster roster unit.");
            this.key = key; this.faction = faction; this.entityId = entityId; this.mountId = mountId;
            this.cost = cost; this.weight = weight;
        }
        public boolean mounted() { return !mountId.isEmpty(); }
    }
    public static final class Selection {
        public final Map<Unit, BigInteger> counts;
        public final BigInteger spentUnits;
        Selection(Map<Unit, BigInteger> counts, BigInteger spent) {
            this.counts = Collections.unmodifiableMap(counts); spentUnits = spent;
        }
    }
    public static Selection select(String faction, BigInteger budgetUnits, long seed, List<Unit> source) {
        if (budgetUnits == null || budgetUnits.signum() < 0 || source == null)
            throw new IllegalArgumentException("Muster budget and roster are required.");
        List<Unit> roster = new ArrayList<Unit>();
        java.util.Set<String> keys = new java.util.HashSet<String>();
        for (Unit unit : source) {
            if (unit == null || !keys.add(unit.key)) throw new IllegalArgumentException("Duplicate/null roster unit.");
            if (faction.equals(unit.faction) && unit.weight > 0
                    && (!"rohan".equals(faction) || unit.mounted())) roster.add(unit);
        }
        Collections.sort(roster, new Comparator<Unit>() {
            public int compare(Unit a, Unit b) { return a.key.compareTo(b.key); }
        });
        Random random = new Random(seed);
        Map<Unit, BigInteger> counts = new LinkedHashMap<Unit, BigInteger>();
        BigInteger remaining = budgetUnits;
        while (true) {
            List<Unit> eligible = new ArrayList<Unit>();
            long totalWeight = 0L;
            int cheapest = Integer.MAX_VALUE;
            for (Unit unit : roster) if (costUnits(unit).compareTo(remaining) <= 0) {
                eligible.add(unit); totalWeight = Math.addExact(totalWeight, unit.weight);
                cheapest = Math.min(cheapest, unit.cost);
            }
            if (eligible.isEmpty()) break;
            long draw = nextLong(random, totalWeight);
            Unit chosen = eligible.get(0);
            for (Unit unit : eligible) {
                if (draw < unit.weight) { chosen = unit; break; }
                draw -= unit.weight;
            }
            // Large exact budgets use weighted batches, keeping work bounded without capping population.
            BigInteger batch = eligible.size() == 1 ? remaining.divide(costUnits(chosen)) : remaining.divide(BigInteger.valueOf((long) cheapest * KOMEPopulationRate.SCALE))
                .divide(BigInteger.valueOf(4096L)).max(BigInteger.ONE)
                .min(remaining.divide(costUnits(chosen)));
            BigInteger old = counts.get(chosen);
            counts.put(chosen, (old == null ? BigInteger.ZERO : old).add(batch));
            remaining = remaining.subtract(costUnits(chosen).multiply(batch));
        }
        return new Selection(counts, budgetUnits.subtract(remaining));
    }
    static BigInteger costUnits(Unit unit) {
        return BigInteger.valueOf(unit.cost).multiply(BigInteger.valueOf(KOMEPopulationRate.SCALE));
    }
    private static long nextLong(Random random, long bound) {
        long bits, value;
        do { bits = random.nextLong() >>> 1; value = bits % bound; }
        while (bits - value + bound - 1L < 0L);
        return value;
    }
}
