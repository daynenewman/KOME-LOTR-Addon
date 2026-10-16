package kome.common.data;

import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayer;

/** Explanations are evaluated on the server alongside the existing action policies. */
public final class KOMEProgressionEligibility {
    public final boolean relevant,enabled;public final String reason;
    private KOMEProgressionEligibility(boolean relevant,boolean enabled,String reason){this.relevant=relevant;this.enabled=enabled;this.reason=reason;}
    private static KOMEProgressionEligibility no(String reason){return new KOMEProgressionEligibility(true,false,reason);}
    public static KOMEProgressionEligibility evaluate(EntityPlayer player,LOTREntityNPC npc){
        if(player==null||npc==null||player.worldObj==null||player.worldObj.isRemote||npc.worldObj!=player.worldObj)return new KOMEProgressionEligibility(false,false,"");
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=data.getProgression(player.getUniqueID());KOMESerfKnightProgression s=p.getSerfKnightProgression();
        KOMEProgressionNpcRank rank=KOMEProgressionNpcRankService.effectiveRank(data,npc);
        if(rank==KOMEProgressionNpcRank.UNRANKED)return new KOMEProgressionEligibility(false,false,"");
        if(!npc.isEntityAlive()||npc.isChild()||npc.hiredNPCInfo==null||npc.hiredNPCInfo.isActive)return no("This lord cannot receive your service.");
        LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
        if(pledge==null)return no("You must first pledge to a faction.");
        if(pledge!=npc.getFaction())return no("You have sworn yourself to another faction.");
        if(p.getCanonicalRank()==KOMEProgressionRank.WANDERER)return no("Pledge allegiance to enter Serf standing.");
        if(p.getCanonicalRank()==KOMEProgressionRank.SERF)return no("Your Master confers Knight standing after three reported duties. Seek higher service after that promotion.");
        if(!KOMEProgressionLiegePolicy.accepts(data,p.getCanonicalRank(),rank,pledge.codeName(),npc.getFaction().codeName()))return no("This superior cannot receive service at your current standing.");
        if(KOMELiegeProgressionInteraction.ownsQuest(player,npc)){
            if(p.getKnightService().qualifyingTypes(pledge.codeName()).size()>=3&&LOTRLevelData.getData(player).getAlignment(pledge)<2000)return no("Your service is complete. Reach 2000 faction alignment, then report to your Lord.");
            return new KOMEProgressionEligibility(true,true,"");
        }
        if(KOMEProgressionOfferBridge.canRequestLiegeOfferFrom(player,npc))return new KOMEProgressionEligibility(true,true,"");
        if(s.isLockedOut(KOMESerfKnightService.calendarDayNow()))return no("Your betrayal prevents new service today.");
        if(s.hasLiege())return no("You already serve another superior.");
        if(p.getCanonicalRank()==KOMEProgressionRank.LORD)return no("Royal service belongs to the Crown at the capital; its leadership duties await redesign. The legacy Prince path remains in your book.");
        if(p.getCanonicalRank()==KOMEProgressionRank.KNIGHT&&!KOMEProgressionServiceLocation.regional(npc))return no("Seek a Lord or Prince based at a verified native regional seat. Wilderness nobles cannot receive new Knight service.");
        if(npc instanceof lotr.common.entity.npc.LOTRUnitTradeable&&!((lotr.common.entity.npc.LOTRUnitTradeable)npc).canTradeWith(player))return no("Your native faction alignment does not meet this superior's requirement.");
        return no("An earned recommendation grants reliable admission. Recover yours with /progression recommendation.");
    }
}
