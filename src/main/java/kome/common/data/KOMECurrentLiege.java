package kome.common.data;

import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayer;

/** Shared loaded-NPC authority. An unloaded reference is reconciled separately, never presumed dead. */
final class KOMECurrentLiege {
    private KOMECurrentLiege() {}
    static boolean validNpc(KOMEWorldData data,LOTREntityNPC npc,KOMEProgressionNpcRef reference) {
        return npc!=null&&reference!=null&&npc.isEntityAlive()&&!npc.isChild()
            &&npc.hiredNPCInfo!=null&&!npc.hiredNPCInfo.isActive
            &&KOMEProgressionLords.isStandingTrialLiegeCandidate(npc)
            &&KOMEProgressionNpcRankService.effectiveRank(data,npc)==KOMEProgressionNpcRank.LORD
            &&KOMEProgressionFactionResolver.matches(reference.factionKey,npc.getFaction())
            &&reference.hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(npc));
    }
    static boolean valid(EntityPlayer player,LOTREntityNPC npc) {
        if(player==null||npc==null||player.worldObj==null||player.worldObj.isRemote||npc.worldObj!=player.worldObj)return false;
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=data.getProgression(player.getUniqueID());
        LOTRFaction faction=LOTRLevelData.getData(player).getPledgeFaction();
        return faction!=null&&faction.isPlayableAlignmentFaction()&&npc.getFaction()==faction
            &&validNpc(data,npc,p.getSerfKnightProgression().getLiege());
    }
}
