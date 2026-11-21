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
        if(rank==KOMEProgressionNpcRank.UNRANKED){
            if(!(player instanceof net.minecraft.entity.player.EntityPlayerMP)||!KOMESerfdomMasterService.ordinaryMaster(npc)
                    ||p.getCanonicalRank()!=KOMEProgressionRank.SERF||s.getSerfdomMaster().isSet())return new KOMEProgressionEligibility(false,false,"");
            KOMESerfdomMasterService.Result selection=KOMESerfdomMasterService.validateMasterSelection(
                (net.minecraft.entity.player.EntityPlayerMP)player,data,npc,false);
            if(!selection.success)return no(selection.reason);
            boolean offered=npc.questInfo!=null&&npc.questInfo.getOfferFor(player) instanceof KOMESerfdomOfferQuest
                &&KOMEProgressionOfferBridge.canOffer(npc.questInfo,player);
            return offered?new KOMEProgressionEligibility(true,true,""):no("Finish or decline this NPC's current quest before seeking service.");
        }
        if(!npc.isEntityAlive()||npc.isChild()||npc.hiredNPCInfo==null||npc.hiredNPCInfo.isActive)return no("This Liege cannot accept your service.");
        LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
        if(pledge==null)return no("Pledge to a faction first.");
        if(pledge!=npc.getFaction())return no("Your pledge belongs to another faction.");
        if(p.getCanonicalRank()==KOMEProgressionRank.SERF)return no("Report three duties to your Master to earn Knight standing first.");
        if(!KOMEProgressionLiegePolicy.accepts(data,p.getCanonicalRank(),rank,pledge.codeName(),npc.getFaction().codeName()))return no("Your standing does not qualify for this Liege.");
        if(KOMELiegeProgressionInteraction.ownsQuest(player,npc)){
            if(p.getKnightService().qualifyingTypes(pledge.codeName()).size()>=3&&LOTRLevelData.getData(player).getAlignment(pledge)<2000)return no("Reach 2,000 alignment, then report to your Liege.");
            return new KOMEProgressionEligibility(true,true,"");
        }
        if(KOMEProgressionOfferBridge.canRequestLiegeOfferFrom(player,npc))return new KOMEProgressionEligibility(true,true,"");
        if(s.isLockedOut(KOMESerfKnightService.calendarDayNow()))return no("Betrayal prevents new service today.");
        if(s.hasLiege())return no("You already serve a Liege.");
        if(p.getCanonicalRank()==KOMEProgressionRank.LORD)return no("Royal service is not yet available. The legacy Prince requirements remain in your book.");
        if(p.getCanonicalRank()==KOMEProgressionRank.KNIGHT&&!KOMEProgressionServiceLocation.regional(npc))return no("Find a Lord or Prince at a recognized regional seat.");
        if(npc instanceof lotr.common.entity.npc.LOTRUnitTradeable&&!((lotr.common.entity.npc.LOTRUnitTradeable)npc).canTradeWith(player))return no("Your alignment is too low for this Liege.");
        return no("A valid recommendation secures admission. Recover yours with /progression recommendation.");
    }
}
