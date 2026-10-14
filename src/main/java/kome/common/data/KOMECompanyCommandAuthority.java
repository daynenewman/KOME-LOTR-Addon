package kome.common.data;

import java.util.UUID;

/** Canonical player-to-company command authority without operator impersonation. */
public final class KOMECompanyCommandAuthority {
    private KOMECompanyCommandAuthority() { }

    /** New player orders only. Existing movements, retreat and recovery keep their own lifecycle. */
    public static String newMilitaryOperationDenial(KOMEWorldData data, KOMEArmyCompany company,
            UUID actor, String destination, boolean administrative) {
        if(data==null || company==null || actor==null)return "Military command authority is unavailable.";
        if(administrative)return "";
        KOMEConquestTile tile=data.getConquestTileIfPresent(destination);
        String defender=tile==null?"":KOMEAlliance.normalizeFactionKey(tile.projectRulingFaction());
        String faction=KOMEAlliance.normalizeFactionKey(company.faction);
        if(defender.isEmpty() || defender.equals(faction))return "";
        if(data.getConflictService().currentHostility(data,faction,defender)!=KOMEConflictContracts.Hostility.HOSTILE)return "";
        if(KOMEWarService.findActiveOpposition(data,faction,defender)!=null)return "";
        if(KOMERulerAuthorization.canActAsRuler(data,faction,actor))return "";
        return "A new hostile operation outside a declared war requires the recognized faction ruler. Company command does not authorize war declaration.";
    }

    public static boolean canPersonallyCommand(KOMEWorldData data, KOMEArmyCompany company,
            UUID playerId) {
        if (data == null || company == null || playerId == null) return false;
        if (playerId.equals(company.owner)) return true;
        if (!playerId.equals(company.temporaryController)) return false;
        if (KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE.equals(company.controllerAuthority)) {
            return KOMECompanyDiplomacyAuthorization.canContinueDelegation(
                data, KOMEWartimeStewardshipService.nativeFaction(company), playerId).allowed;
        }
        if (KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)) {
            return new KOMEAllianceAuthority(data)
                .canControlTemporaryCompany(company, playerId).allowed;
        }
        return false;
    }
}
