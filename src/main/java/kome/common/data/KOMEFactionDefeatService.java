package kome.common.data;

import kome.common.config.KOMEConfigRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Live canonical destruction predicate; campaign outcomes belong to the existing season state. */
public final class KOMEFactionDefeatService {
    private KOMEFactionDefeatService() { }

    public static Evaluation evaluate(KOMEWorldData data, String faction) {
        if (data == null || data.isWriteBlocked()) return evaluate(data, faction,
            Collections.<String, List<String>>emptyMap());
        return evaluate(data, faction, KOMEPopulationRateService.getTilesProducingAtLeastOnePerDay(
            data, KOMEConfigRegistry.population()));
    }

    private static Evaluation evaluate(KOMEWorldData data, String faction,
            Map<String, List<String>> productiveTiles) {
        String key = KOMEAlliance.normalizeFactionKey(faction);
        if (data == null || data.isWriteBlocked() || !KOMEAlliance.allFactionKeys().contains(key))
            return new Evaluation(key, "", "", false, false,
                Collections.<String>emptyList(), "Authoritative campaign state is unavailable.");
        KOMEFactionCapitalRecord capital = KOMEFactionCapitalService.getCapital(data, key);
        KOMEConquestTile tile = capital == null ? null : data.conquestTiles.get(capital.getCapitalTileId());
        List<String> remaining = productiveTiles.get(key);
        if (remaining == null) remaining = Collections.emptyList();
        if (tile == null) return new Evaluation(key, capital == null ? "" : capital.getCapitalTileId(),
            "", false, false, remaining, "Authoritative capital tile is unavailable.");
        String controller = tile.projectRulingFaction();
        boolean captured = controller.length() > 0 && !key.equals(controller);
        return new Evaluation(key, capital.getCapitalTileId(), controller, true, captured,
            remaining, "");
    }

    /** Server START tick, after live population development. No war-start snapshots or siege suppression. */
    public static int reconcile(KOMEWorldData data, long now) {
        if (data == null || data.isWriteBlocked()
                || data.warSeason.phase != KOMEWarSeasonState.Phase.WAR
                    && data.warSeason.phase != KOMEWarSeasonState.Phase.FINALE) return 0;
        List<String> candidates = new ArrayList<String>();
        for (String faction : KOMEAlliance.allFactionKeys()) {
            if (data.warSeason.isFactionDefeated(faction)) continue;
            Evaluation capital = evaluate(data, faction, Collections.<String, List<String>>emptyMap());
            if (capital.ready && capital.capitalCaptured) candidates.add(faction);
        }
        if (candidates.isEmpty()) return 0;
        Map<String, List<String>> productive = KOMEPopulationRateService.getTilesProducingAtLeastOnePerDay(
            data, KOMEConfigRegistry.population());
        int transitions = 0;
        for (String faction : candidates) {
            Evaluation result = evaluate(data, faction, productive);
            if (!result.defeated) continue;
            long timestamp = Math.max(0L, now);
            KOMEAuditEntry audit = new KOMEAuditEntry(timestamp, "CAMPAIGN", "FACTION_DEFEAT",
                "system", faction, "Capital captured AND no live tile produces >= 1 population/day",
                "season=" + data.warSeason.seasonId + ";threshold=1/day;capital=" + result.capitalTile
                    + ";controller=" + result.capitalController + ";capitalCaptured=true"
                    + ";remainingProductiveTiles=" + result.remainingProductiveTiles + ";predicate=true");
            if (data.publishFactionDefeat(faction, timestamp, audit)) transitions++;
        }
        return transitions;
    }

    /** Public strategic facts, shared by operator inspection and the existing player war UI. */
    public static String inspect(KOMEWorldData data, String faction) {
        return inspect(data, evaluate(data, faction));
    }

    private static String inspect(KOMEWorldData data, Evaluation result) {
        return result.faction + ": "
            + (data != null && data.warSeason.isFactionDefeated(result.faction) ? "DEFEATED @ "
                + data.warSeason.factionDefeatedAt(result.faction) : "not defeated")
            + "; capital " + result.capitalTile + " [" + result.capitalController + "] "
            + (result.capitalCaptured ? "captured" : "not captured")
            + "; remaining objectives=" + result.remainingObjectives
            + "; live predicate=" + result.defeated
            + (result.ready ? "" : "; NOT_READY: " + result.reason);
    }

    public static String inspectWar(KOMEWorldData data, KOMEWar war) {
        Map<String, List<String>> productive = data == null || data.isWriteBlocked()
            ? Collections.<String, List<String>>emptyMap()
            : KOMEPopulationRateService.getTilesProducingAtLeastOnePerDay(data, KOMEConfigRegistry.population());
        Set<String> factions = new TreeSet<String>(war.sideOneFactions);
        factions.addAll(war.sideTwoFactions);
        StringBuilder result = new StringBuilder();
        for (String faction : factions) {
            if (result.length() > 0) result.append(" / ");
            result.append(inspect(data, evaluate(data, faction, productive)));
        }
        return result.toString();
    }

    public static final class Evaluation {
        public final String faction, capitalTile, capitalController, reason;
        public final boolean ready, capitalCaptured, defeated;
        public final List<String> remainingProductiveTiles, remainingObjectives;
        private Evaluation(String faction, String capitalTile, String controller, boolean ready,
                boolean captured, List<String> productive, String reason) {
            this.faction = faction; this.capitalTile = capitalTile; this.capitalController = controller;
            this.ready = ready; this.capitalCaptured = captured; this.reason = reason;
            this.remainingProductiveTiles = Collections.unmodifiableList(new ArrayList<String>(productive));
            Set<String> objectives = new TreeSet<String>(productive);
            if (!captured && capitalTile.length() > 0) objectives.add(capitalTile);
            this.remainingObjectives = Collections.unmodifiableList(new ArrayList<String>(objectives));
            this.defeated = ready && captured && productive.isEmpty();
        }
    }
}
