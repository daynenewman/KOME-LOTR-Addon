package kome.common.command;

import java.util.UUID;
import kome.common.KOMEReflection;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMECompanyCommandAuthority;
import kome.common.data.KOMERulerAuthorization;
import kome.common.data.KOMEWorldData;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;

/** Live player authority for company mutations; historical owner IDs are insufficient. */
public final class KOMECompanyMutationAuthority {
    private KOMECompanyMutationAuthority() { }

    public static boolean currentOwner(KOMEWorldData data, EntityPlayerMP player, KOMEArmyCompany company) {
        return current(data, player, company)
            && KOMEReflection.getEntityUUID(player).equals(company.owner)
            && pledgedTo(player, company.faction);
    }

    static boolean currentRuler(KOMEWorldData data, EntityPlayerMP player, KOMEArmyCompany company,
            String faction) {
        return current(data, player, company) && pledgedTo(player, faction)
            && KOMERulerAuthorization.canActAsRuler(data, faction, KOMEReflection.getEntityUUID(player));
    }

    static boolean temporaryController(KOMEWorldData data, EntityPlayerMP player, KOMEArmyCompany company) {
        if (!current(data, player, company)) return false;
        UUID actor=KOMEReflection.getEntityUUID(player);
        return actor.equals(company.temporaryController)
            && (KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE.equals(company.controllerAuthority)
                || KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority))
            && KOMECompanyCommandAuthority.canPersonallyCommand(data, company, actor);
    }

    static boolean controller(KOMEWorldData data, EntityPlayerMP player, KOMEArmyCompany company) {
        if (!current(data, player, company)) return false;
        if (player.canCommandSenderUseCommand(2, "troops")) return true;
        return (currentOwner(data, player, company) || temporaryController(data, player, company))
            && KOMECompanyMovementService.INSTANCE.canPlayerControlCompany(data, player, company);
    }

    static boolean operator(EntityPlayerMP player) {
        return player != null && player.canCommandSenderUseCommand(2, "troops");
    }

    private static boolean current(KOMEWorldData data, EntityPlayerMP player, KOMEArmyCompany company) {
        return data != null && player != null && company != null && company.id != null
            && data.armyCompanies.get(company.id) == company && KOMEReflection.getEntityUUID(player) != null;
    }

    static boolean pledgedTo(EntityPlayerMP player, String faction) {
        LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
        String expected=KOMEAlliance.normalizeFactionKey(faction);
        return pledge != null && !expected.isEmpty()
            && expected.equals(KOMEAlliance.normalizeFactionKey(pledge.codeName()));
    }
}
