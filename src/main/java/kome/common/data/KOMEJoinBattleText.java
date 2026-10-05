package kome.common.data;

/** Stable player-facing copy for server-authoritative Join Battle decisions. */
public final class KOMEJoinBattleText {
    private KOMEJoinBattleText() { }

    public static String forReason(KOMEJoinBattleService.Reason reason) {
        if (reason == null) return "The Join Battle request was invalid.";
        switch (reason) {
            case ALLOWED: return "You may join this battle.";
            case INVALID_TILE: return "That conquest tile is invalid.";
            case NO_ACTIVE_CONFLICT: return "There is no active battle on this tile.";
            case STALE_CONFLICT: return "The battle changed. Refresh and try again.";
            case UNPLEDGED: return "You must be pledged to a faction to join this battle.";
            case FACTION_NOT_PARTICIPATING: return "Your faction is not participating in this conflict.";
            case NO_ELIGIBLE_COMPANY: return "Your faction has no eligible committed Campaign company in this conflict.";
            case WITHDRAWN: return "You have withdrawn from this conflict.";
            case COMPANY_NOT_COMMITTED: return "That Campaign company is not committed to this conflict.";
            case WRONG_FACTION_COMPANY: return "That Campaign company does not belong to your current faction.";
            case COMPANY_INCOHERENT: return "That Campaign company is not currently coherent.";
            case COMPANY_HAS_NO_CAMPAIGN_COMBAT_UNITS: return "That company has no eligible Campaign combat units.";
            case INVALID_REQUEST:
            default: return "The Join Battle request was invalid.";
        }
    }
}
