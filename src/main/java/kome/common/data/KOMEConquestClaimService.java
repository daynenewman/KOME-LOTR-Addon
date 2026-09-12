package kome.common.data;


import java.util.List;
import java.util.UUID;

/** The only player-claim entry point. Administrative transfers continue to use claimTile directly. */
public final class KOMEConquestClaimService {
    public static final String METHOD_PLAYER_CONQUEST = "PLAYER_CONQUEST";

    private KOMEConquestClaimService() {
    }

    public static Result claim(KOMEWorldData data, KOMEConquestTile tile, String claimantFaction,
            UUID claimant, String claimantName, long worldTime, long nowMillis) {
        Result result = new Result();
        if (data == null || tile == null || claimant == null) {
            result.message = "Invalid conquest claim.";
            return result;
        }
        String nextOwner = KOMEAlliance.normalizeFactionKey(claimantFaction);
        String previousOwner = KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());
        if (nextOwner.length() == 0) {
            result.message = "You must be pledged to a faction to claim conquest tiles.";
            return result;
        }
        if (nextOwner.equals(previousOwner)) {
            data.conquestClaimConfirmations.remove(claimant);
            result.message = "This tile is already controlled by your faction.";
            return result;
        }

        boolean confirmationRequired = previousOwner.length() > 0
            && KOMEWarService.requiresHostileConfirmation(data, nextOwner, previousOwner);
        String fingerprint = KOMEWarService.allianceFingerprint(data, nextOwner, previousOwner);
        KOMEClaimConfirmation pending = data.conquestClaimConfirmations.get(claimant);
        // A confirmation is a promise about one exact tile, owner, and diplomacy state. If that
        // tile changes underneath the player, fail closed even when its new owner would not itself
        // require confirmation; otherwise the stale second click could become an unintended attack.
        if (pending != null && KOMEConquestTile.normalizeId(tile.id).equals(pending.tileId)
                && (nowMillis > pending.expiresAtMillis
                    || !pending.matches(claimant, tile.id, previousOwner, fingerprint, nowMillis))) {
            data.conquestClaimConfirmations.remove(claimant);
            data.markDirty();
            result.confirmationRequired = confirmationRequired;
            result.staleConfirmation = true;
            result.message = nowMillis > pending.expiresAtMillis
                ? "The hostile-claim confirmation expired. Review the tile and click Claim again."
                : "The tile owner or alliance/war state changed. No claim was made; review and confirm again.";
            return result;
        }
        if (confirmationRequired) {
            if (pending == null) {
                arm(data, tile, previousOwner, claimant, fingerprint, nowMillis);
                result.confirmationRequired = true;
                result.message = warning(data, nextOwner, previousOwner);
                return result;
            }
            if (nowMillis > pending.expiresAtMillis) {
                data.conquestClaimConfirmations.remove(claimant);
                data.markDirty();
                result.confirmationRequired = true;
                result.staleConfirmation = true;
                result.message = "The hostile-claim confirmation expired. Review the tile and click Claim again.";
                return result;
            }
            if (!pending.matches(claimant, tile.id, previousOwner, fingerprint, nowMillis)) {
                data.conquestClaimConfirmations.remove(claimant);
                data.markDirty();
                result.confirmationRequired = true;
                result.staleConfirmation = true;
                result.message = "The tile owner or alliance/war state changed. No claim was made; review and confirm again.";
                return result;
            }
        }

        data.conquestClaimConfirmations.remove(claimant);
        data.claimTile(tile, nextOwner, worldTime, claimant, claimantName);

        KOMEWar war = previousOwner.length() == 0
            ? null
            : KOMEWarService.recordHostileCapture(
                data,
                tile.id,
                previousOwner,
                nextOwner,
                claimant,
                claimantName,
                nowMillis,
                METHOD_PLAYER_CONQUEST);

        result.success = true;
        result.war = war;
        result.allianceBroken = false;
        result.sameSideContradictions =
            KOMEWarService.findActiveSameSide(data, nextOwner, previousOwner);
        result.message = "Claimed conquest tile " + tile.id + " for " + KOMEAlliance.displayFactionName(nextOwner)
            + (war == null ? "." : "; recorded in " + displayWar(war) + ".");
        data.markDirty();
        return result;
    }

    private static void arm(KOMEWorldData data, KOMEConquestTile tile, String owner, UUID claimant,
            String fingerprint, long nowMillis) {
        KOMEClaimConfirmation confirmation = new KOMEClaimConfirmation();
        confirmation.player = claimant;
        confirmation.tileId = KOMEConquestTile.normalizeId(tile.id);
        confirmation.expectedOwner = KOMEAlliance.normalizeFactionKey(owner);
        confirmation.expectedAllianceState = fingerprint;
        confirmation.expiresAtMillis = nowMillis + KOMEWarService.CLAIM_CONFIRMATION_MILLIS;
        data.conquestClaimConfirmations.put(claimant, confirmation);
        data.markDirty();
    }

    private static String warning(KOMEWorldData data, String claimantFaction, String owner) {
        String first =
            "This tile is controlled by "
                + KOMEAlliance.displayFactionName(owner)
                + ". Capturing it is a hostile act.";

        KOMEDiplomacyRelation relation =
            KOMEDiplomacyService.getRelation(data, claimantFaction, owner);

        if (relation.rank() >= KOMEDiplomacyRelation.FRIENDS.rank()) {
            return first
                + " These factions are currently "
                + relation.displayName
                + ". The conquest will begin or record a war, but it will not automatically "
                + "change the accepted diplomacy relation. Click Claim again within 30 seconds "
                + "to continue.";
        }

        return first
            + " These factions share a coalition side. The claim will create direct hostility "
            + "without silently changing that coalition. Click Claim again within 30 seconds "
            + "to continue.";
    }
    private static String displayWar(KOMEWar war) {
        return war.displayName == null || war.displayName.trim().length() == 0 ? war.id : war.displayName + " (" + war.id + ")";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
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
