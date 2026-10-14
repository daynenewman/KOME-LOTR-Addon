package kome.common.data;


import java.util.List;
import java.util.UUID;

/** The only player-claim entry point. Administrative transfers continue to use claimTile directly. */
public final class KOMEConquestClaimService {
    public static final String METHOD_PLAYER_CONQUEST = "PLAYER_CONQUEST";

    private KOMEConquestClaimService() {
    }

    /** Only restores the compatibility alias; never chooses between conflicting owners. */
    static boolean repairMissingOwnerAlias(KOMEWorldData data, KOMEConquestTile tile) {
        data.ensureWritable();
        if (tile == null || data.conquestTiles.get(tile.id) != tile
                || !KOMEAlliance.allFactionKeys().contains(tile.currentRulingFaction)
                || tile.ownerFaction == null || !tile.ownerFaction.isEmpty()) return false;
        tile.setCurrentRulingFaction(tile.currentRulingFaction);
        data.markDirty();
        return true;
    }

    /**
     * Manual rank/checkbox claims are not a battle result. KOM-18 owns conquest;
     * sovereign peaceful offer/accept and explicit staff correction remain separate.
     * This checkout has no verified player resolution receipt to consume here.
     */
    public static String manualClaimDenial(KOMEWorldData data, KOMEConquestTile tile,
            String claimantFaction) {
        if (data == null || tile == null || data.conquestTiles.get(tile.id) != tile)
            return "Unknown or unavailable conquest tile.";
        String faction = KOMEAlliance.normalizeFactionKey(claimantFaction);
        if (faction.isEmpty()) return "You must be pledged to a faction.";
        if (faction.equals(tile.projectRulingFaction()))
            return "This tile is already controlled by your faction.";
        return "Manual claims cannot certify a battle victory or declare war. Conquest must resolve through the canonical battle system; peaceful transfers use the owning and receiving rulers' offer/accept actions.";
    }

    public static Result claim(KOMEWorldData data, KOMEConquestTile tile, String claimantFaction,
            UUID claimant, String claimantName, long worldTime, long nowMillis) {
        Result result = new Result();
        result.message = claimant == null ? "Invalid conquest claim."
            : manualClaimDenial(data, tile, claimantFaction);
        // No ownership, timer, confirmation, war or diplomatic mutation on a denied intent.
        return result;
    }
    public static class Result {
        public boolean success;
        public boolean confirmationRequired;
        public boolean staleConfirmation;
        public boolean allianceBroken;
        public String message = "";
        public KOMEWar war;
        public List<KOMEWar> sameSideContradictions = java.util.Collections.emptyList();
    }
}
