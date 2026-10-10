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
            case INVALID_ACTION_TOKEN: return "This Join Battle view expired. Refresh and try again.";
            case COMPANY_LOCATION_UNKNOWN: return "That Campaign company has no verified physical location yet.";
            case COMPANY_LOCATION_UNAVAILABLE: return "That Campaign company's physical location could not be verified. Try again shortly.";
            case SAFE_POSITION_UNAVAILABLE: return "No safe Join Battle position is currently available beside that company.";
            case UNSUPPORTED_MOUNT: return "That mount is not supported for Join Battle.";
            case INVALID_MOUNT_RELATIONSHIP: return "Your current riding arrangement cannot be moved into battle safely.";
            case MOUNTED_CROSS_DIMENSION_UNSUPPORTED: return "Mounted Join Battle cannot cross dimensions yet.";
            case MOUNT_TRANSFER_ROLLED_BACK: return "Mounted deployment could not complete. You and your mount were restored safely; KOME will retry when safe.";
            case MOUNT_TRANSFER_FAILED: return "Mounted deployment is temporarily blocked. The server is preserving and retrying it safely.";
            case SOURCE_MOUNT_NOT_FOUND_AFTER_REMOVAL: return "Your mount was restored safely at the source. KOME will retry the accepted deployment when safe.";
            case DESTINATION_MOUNT_SPAWN_FAILED: return "The destination mount could not be published. Recovery was attempted.";
            case DESTINATION_UUID_MISMATCH: return "The recreated mount identity did not match. Recovery was attempted.";
            case DESTINATION_PROFILE_MISMATCH: return "The recreated mount type did not match. Recovery was attempted.";
            case PLAYER_TELEPORT_FAILED: return "The player could not be verified at the battle destination. Recovery was attempted.";
            case REMOUNT_FAILED: return "The rider relationship could not be restored at the destination. Recovery was attempted.";
            case SOURCE_ROLLBACK_SPAWN_FAILED: return "The source mount could not be restored yet. Entry recovery remains pending.";
            case SOURCE_ROLLBACK_UUID_MISMATCH: return "The restored source mount identity could not be verified. Entry recovery remains pending.";
            case MULTIPLE_MOUNT_COPIES_FOUND: return "Mount recovery found multiple matching copies and stopped safely.";
            case MOUNT_DUPLICATE_STATE_MISMATCH: return "Mount recovery found copies with different persistent state and stopped safely.";
            case MOUNT_COPY_OUTSIDE_RECOVERY_AREA: return "Mount recovery found a matching copy outside the recorded transfer area and stopped safely.";
            case MOUNT_LOCATION_AMBIGUOUS: return "Mount recovery could not verify a unique source or destination location.";
            case ENTRY_CANCELLED: return "The unfinished Join Battle was cancelled safely.";
            case ENTRY_ALREADY_IN_PROGRESS: return "The server is safely completing an accepted Join Battle.";
            case ALREADY_DEPLOYED: return "You are already deployed through another Join Battle entry.";
            case GOVERNANCE_RESTRICTED: return "Your current war governance status prevents Join Battle participation.";
            case INVALID_REQUEST:
            default: return "The Join Battle request was invalid.";
        }
    }
}
