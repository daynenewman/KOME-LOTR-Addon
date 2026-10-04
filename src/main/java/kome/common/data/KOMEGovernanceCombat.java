package kome.common.data;

import java.util.UUID;
import kome.common.KOMEReflection;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/** Server damage boundary, including projectiles and ordinary/native hired followers. */
public final class KOMEGovernanceCombat {
    private KOMEGovernanceCombat() { }

    public static String denial(KOMEWorldData data, Entity attacker, Entity target) {
        if (attacker == null || target == null) return "";
        String targetFaction = faction(data, target);
        if (targetFaction.isEmpty()) return "";
        if (attacker instanceof EntityPlayer)
            return playerDenial(data, KOMEReflection.getEntityUUID(attacker), targetFaction);
        if (!(attacker instanceof LOTREntityNPC)) return "";
        LOTREntityNPC npc = (LOTREntityNPC) attacker;
        KOMEHiredUnitRecord record = data.hiredUnits.get(KOMEReflection.getEntityUUID(attacker));
        UUID owner = record == null ? npc.hiredNPCInfo.getHiringPlayerUUID() : record.owner;
        String denied = owner == null ? "" : playerDenial(data, owner, targetFaction);
        if (!denied.isEmpty()) return denied;
        UUID controller = record == null ? null : record.controller;
        return controller == null || controller.equals(owner) ? "" : playerDenial(data, controller, targetFaction);
    }

    private static String playerDenial(KOMEWorldData data, UUID player, String targetFaction) {
        for (KOMEWar war : data.wars.values()) {
            if (war.isEnded() || war.sideOf(targetFaction) == 0) continue;
            KOMEPlayerGovernance record = KOMEGovernanceService.record(data, player, war.id);
            String acting = record != null && record.state == KOMEPlayerGovernance.State.EXILED
                ? record.host : data.getPlayerFactionKey(player);
            KOMEGovernanceService.Decision decision = KOMEGovernanceService.participation(data, player, war, acting);
            if (!decision.allowed) return decision.reason;
        }
        return "";
    }

    public static String warActionDenial(EntityPlayer player, String affectedFaction) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return "";
        return playerDenial(KOMEWorldData.get(player.worldObj), KOMEReflection.getEntityUUID(player), affectedFaction);
    }

    private static String faction(KOMEWorldData data, Entity entity) {
        if (entity instanceof EntityPlayer) return data.getPlayerFactionKey(KOMEReflection.getEntityUUID(entity));
        if (entity instanceof LOTREntityNPC) {
            LOTREntityNPC npc = (LOTREntityNPC) entity;
            return npc.getFaction() == null ? "" : KOMEAlliance.normalizeFactionKey(npc.getFaction().codeName());
        }
        return "";
    }
}
