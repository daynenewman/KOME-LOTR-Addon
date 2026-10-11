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
        if(p.getCanonicalRank()==KOMEProgressionRank.WANDERER)return no("You must first serve a Master.");
        if(!KOMEProgressionLiegePolicy.accepts(data,p.getCanonicalRank(),rank,pledge.codeName(),npc.getFaction().codeName()))return no(rank==KOMEProgressionNpcRank.PRINCE?"This prince cannot accept your service at your current standing.":rank==KOMEProgressionNpcRank.KING?"This king cannot offer a progression quest at your current standing.":"This lord cannot accept your service at your current standing.");
        KOMEProgressionNpcRef ref=KOMEProgressionNpcRankService.referenceOf(npc);
        if(p.getCanonicalRank()==KOMEProgressionRank.SERF&&s.failedLiegeRemaining(ref,KOMESerfKnightProgression.failureClock.getAsLong())>0)return no("You must wait before approaching this lord again.");
        if(KOMELiegeProgressionInteraction.ownsQuest(player,npc)){
            if(p.getCanonicalRank()==KOMEProgressionRank.SERF){if(s.isTrialCompleted()&&LOTRLevelData.getData(player).getAlignment(pledge)<KOMESerfKnightService.REQUIRED_ALIGNMENT)return no("You need more faction alignment before receiving your standing.");return new KOMEProgressionEligibility(true,true,"");}
            if(KOMELiegeProgressionInteraction.action(p,LOTRLevelData.getData(player).getAlignment(pledge),pledge.codeName())!=KOMELiegeProgressionInteraction.Action.UNAVAILABLE)return new KOMEProgressionEligibility(true,true,"");
        }
        if(KOMEProgressionOfferBridge.canRequestLiegeOfferFrom(player,npc))return new KOMEProgressionEligibility(true,true,"");
        if(s.isLockedOut(KOMESerfKnightService.calendarDayNow()))return no("Your betrayal prevents new service today.");
        if(s.hasLiege()&&!s.getLiege().hasSameIdentity(ref))return no("You already serve another Liege.");
        if(p.getCanonicalRank()==KOMEProgressionRank.SERF){
            if(!s.getSerfdomMaster().isSet())return no("You must first find a Master.");
            if(!KOMESerfKnightService.allDutiesComplete(s))return no("Complete your Master's duties first.");
            if(s.hasActiveAssignment())return no("You must first complete your current trial.");
            if(!KOMESerfKnightService.mayIssueTrial(s,KOMESerfKnightService.calendarDayNow(),player.getUniqueID()))return no("You have already attempted a trial today.");
        }
        int needed=p.getCanonicalRank()==KOMEProgressionRank.KNIGHT?KOMELordshipTrialService.REQUIRED_ALIGNMENT:KOMEStandingTrialEligibility.requiredAlignment(pledge.codeName());
        if(LOTRLevelData.getData(player).getAlignment(pledge)<needed)return no("You need more faction alignment.");
        if(p.getKnightService().assignment()!=null)return no("You must first complete your current commission.");
        if(npc instanceof lotr.common.entity.npc.LOTRUnitTradeable&&!((lotr.common.entity.npc.LOTRUnitTradeable)npc).canTradeWith(player))return no("This lord will not hear your petition yet.");
        return no("No progression quest is available at your current standing.");
    }
}
