package kome.common.data;

import java.util.UUID;

/** Canonical player-to-company command authority without operator impersonation. */
public final class KOMECompanyCommandAuthority {
    private KOMECompanyCommandAuthority() { }

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
