package kome.common.data;

import kome.common.config.KOMEConfigRegistry;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sole server-authoritative recruitment-tile classifier and deterministic selector. */
public final class KOMERecruitmentLocationService {
    private KOMERecruitmentLocationService() { }

    public static Decision evaluate(KOMEWorldData data, String factionId, String tileId) {
        return evaluate(data, factionId, tileId, KOMEConfigRegistry.population());
    }

    public static Decision evaluate(KOMEWorldData data, String factionId, String tileId,
            KOMEConfigRegistry.PopulationSettings settings) {
        String faction = KOMEAlliance.normalizeFactionKey(factionId);
        String tileKey = KOMEConquestTile.normalizeId(tileId);
        BigInteger threshold = BigInteger.valueOf(
            settings.getRecruitmentTileActiveRateThresholdUnits());
        if (data == null || faction.length() == 0)
            return Decision.deny(faction, tileKey, "", "", false,
                BigInteger.ZERO, threshold, "A supported recruiting faction is required.");
        KOMEConquestTile tile = data.conquestTiles.get(tileKey);
        if (tile == null || KOMEConquestTileDefaults.isRetiredTile(tileKey))
            return Decision.deny(faction, tileKey, "", "", false,
                BigInteger.ZERO, threshold, "The conquest tile is unknown or retired.");
        String defaultFaction = KOMEAlliance.normalizeFactionKey(
            tile.defaultRulingFaction);
        String controller = KOMEAlliance.normalizeFactionKey(
            tile.projectRulingFaction());
        boolean capital = KOMEFactionCapitalService.isCapitalTile(
            data, faction, tileKey);
        BigInteger rate = tileRate(data, settings, tileKey, faction);
        if (!faction.equals(defaultFaction))
            return Decision.deny(faction, tileKey, defaultFaction, controller,
                capital, rate, threshold,
                "Only canonical default territory may be a recruitment tile.");
        if (!faction.equals(controller))
            return Decision.deny(faction, tileKey, defaultFaction, controller,
                capital, rate, threshold,
                "The faction does not currently control this default tile.");
        if (capital)
            return Decision.allow(faction, tileKey, defaultFaction, controller,
                true, rate, threshold, "Controlled canonical capital.");
        if (rate.compareTo(threshold) < 0)
            return Decision.deny(faction, tileKey, defaultFaction, controller,
                false, rate, threshold,
                "Developed tile rate is below the recruitment threshold.");
        return Decision.allow(faction, tileKey, defaultFaction, controller,
            false, rate, threshold, "Developed tile rate meets the recruitment threshold.");
    }

    public static List<String> legalTiles(KOMEWorldData data, String factionId) {
        List<String> result = new ArrayList<String>();
        if (data == null) return result;
        for (String tile : data.conquestTiles.keySet())
            if (evaluate(data, factionId, tile).legal)
                result.add(KOMEConquestTile.normalizeId(tile));
        Collections.sort(result);
        return Collections.unmodifiableList(result);
    }

    public static Selection resolveSelectedOrDefault(KOMEWorldData data,
            UUID playerId, String factionId) {
        String faction = KOMEAlliance.normalizeFactionKey(factionId);
        String selected = playerId == null || data == null ? ""
            : KOMEConquestTile.normalizeId(data.activeRecruitmentTiles.get(
                KOMEWorldData.recruitmentTileKey(faction, playerId)));
        if (selected.length() > 0) {
            Decision explicit = evaluate(data, faction, selected);
            if (explicit.legal) return new Selection(selected, true, explicit);
        }
        List<String> legal = legalTiles(data, faction);
        if (legal.isEmpty()) return new Selection("", false,
            Decision.deny(faction, "", "", "", false, BigInteger.ZERO,
                BigInteger.valueOf(KOMEConfigRegistry.population()
                    .getRecruitmentTileActiveRateThresholdUnits()),
                "No legal recruitment tile is currently available."));
        String fallback = legal.get(0);
        return new Selection(fallback, false, evaluate(data, faction, fallback));
    }

    private static BigInteger tileRate(KOMEWorldData data,
            KOMEConfigRegistry.PopulationSettings settings, String tile,
            String faction) {
        Map<String, Map<String, BigInteger>> rates =
            KOMEPopulationRateService.getExactTilePopulationRates(data, settings);
        Map<String, BigInteger> byFaction = rates.get(tile);
        BigInteger rate = byFaction == null ? null : byFaction.get(faction);
        return rate == null ? BigInteger.ZERO : rate;
    }

    public static final class Selection {
        public final String tileId;
        public final boolean explicit;
        public final Decision decision;
        Selection(String tile, boolean explicit, Decision decision) {
            tileId = tile; this.explicit = explicit; this.decision = decision;
        }
        public boolean available() { return tileId.length() > 0 && decision.legal; }
    }

    public static final class Decision {
        public final boolean legal, capital;
        public final String factionId, tileId, defaultFaction, currentController, reason;
        public final BigInteger effectiveRateUnits, thresholdUnits;
        private Decision(boolean legal, String faction, String tile,
                String defaultFaction, String controller, boolean capital,
                BigInteger rate, BigInteger threshold, String reason) {
            this.legal = legal; factionId = faction; tileId = tile;
            this.defaultFaction = defaultFaction; currentController = controller;
            this.capital = capital; effectiveRateUnits = rate;
            thresholdUnits = threshold; this.reason = reason;
        }
        static Decision allow(String faction, String tile, String defaultFaction,
                String controller, boolean capital, BigInteger rate,
                BigInteger threshold, String reason) {
            return new Decision(true, faction, tile, defaultFaction, controller,
                capital, rate, threshold, reason);
        }
        static Decision deny(String faction, String tile, String defaultFaction,
                String controller, boolean capital, BigInteger rate,
                BigInteger threshold, String reason) {
            return new Decision(false, faction, tile, defaultFaction, controller,
                capital, rate, threshold, reason);
        }
    }
}
