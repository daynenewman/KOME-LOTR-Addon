package kome.common.data;

import java.util.UUID;
import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;

/** Final service report is the promotion transaction. Rank never depends on the next superior. */
public final class KOMEServicePromotionService {
    private KOMEServicePromotionService() {}
    public static int duties(KOMESerfKnightProgression state){
        int count=0;for(KOMESerfKnightDutyType t:KOMESerfKnightDutyType.values())
            if(state.getDuty(t).isCompleted()&&!state.getDuty(t).adminForced)count++;
        return count;
    }
    public static String reason(KOMEPlayerProgression p,KOMEProgressionNpcRef superior,String faction,double alignment){
        if(p==null||superior==null||!superior.isSet())return "Report to your current superior.";
        KOMEProgressionRank rank=p.getCanonicalRank();KOMESerfKnightProgression s=p.getSerfKnightProgression();
        if(rank==KOMEProgressionRank.KNIGHT&&p.getKnightService().testing())return "A staff field exercise is active; earned recognition is suspended until the exercise is ended.";
        if(rank!=KOMEProgressionRank.SERF&&rank!=KOMEProgressionRank.KNIGHT)return "Standing already granted.";
        for(KOMEServiceRecognition.Record r:p.getServiceRecognition().records())if(r.from==rank)
            return "This service has already earned its recognition. A rank correction cannot reuse it.";
        KOMEProgressionNpcRef current=rank==KOMEProgressionRank.SERF?s.getSerfdomMaster():s.getLiege();
        if(!current.hasSameIdentity(superior)||!KOMEProgressionFactionResolver.matches(superior.factionKey,KOMEProgressionFactionResolver.resolve(faction)))return "Report to your superior in your pledged faction.";
        int credits=rank==KOMEProgressionRank.SERF?duties(s):p.getKnightService().qualifyingTypes(faction).size();
        if(credits<3)return "Report three duties or commissions first.";
        int needed=rank==KOMEProgressionRank.SERF?150:2000;
        if(!Double.isFinite(alignment)||alignment<needed)return "Your service is complete. Reach "+needed+" faction alignment, then report here; your work remains honoured.";
        return "";
    }
    static boolean commit(KOMEWorldData data,UUID owner,KOMEProgressionNpcRef superior,String faction,double alignment,long time){
        KOMEPlayerProgression p=data.getProgression(owner);
        if(!reason(p,superior,faction,alignment).isEmpty())return false;
        KOMEProgressionRank from=p.getCanonicalRank(),to=from==KOMEProgressionRank.SERF?KOMEProgressionRank.KNIGHT:KOMEProgressionRank.LORD;
        StringBuilder evidence=new StringBuilder();
        if(from==KOMEProgressionRank.SERF){
            for(KOMESerfKnightDutyType t:KOMESerfKnightDutyType.values())if(p.getSerfKnightProgression().getDuty(t).isCompleted()){
                if(evidence.length()>0)evidence.append(", ");evidence.append(t.displayName);
            }
        }else for(KOMEKnightCommission.Type type:p.getKnightService().qualifyingTypes(faction)){
            if(evidence.length()>0)evidence.append(", ");evidence.append(KOMEKnightCommissionPresentation.title(type));
        }
        KOMEServiceRecognition.Record recognition=p.getServiceRecognition().earn(owner,superior,from,to,evidence.toString(),time);
        recognition.priorSuperior=from==KOMEProgressionRank.SERF?p.getSerfKnightProgression().getDeceasedMaster():p.getSerfKnightProgression().getDeceasedLiege();
        KOMECanonicalRankService.setCanonicalRank(data,owner,to);
        if(from==KOMEProgressionRank.SERF){p.getSerfKnightProgression().setPromoted();p.getSerfKnightProgression().retireSerfdomMasterAfterPromotion();}
        p.getSerfKnightProgression().releaseLiegeAfterPromotion();
        p.grant(from==KOMEProgressionRank.SERF?"serf.title_knight":"knight.title_lord");
        KOMEProgressionAutoCompleter.applyUnlocks(p);KOMEProgressionNpcRoles.syncPlayer(data,owner);data.markDirty();return true;
    }
    public static boolean report(EntityPlayerMP player,LOTREntityNPC npc){
        if(player==null||npc==null||player.worldObj.isRemote||npc.worldObj!=player.worldObj||!npc.isEntityAlive()||npc.isChild()||player.getDistanceSqToEntity(npc)>64D)return false;
        migrate(player);
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);
        synchronized(data){
            KOMEPlayerProgression p=data.getProgression(player.getUniqueID());KOMEProgressionRank from=p.getCanonicalRank();
            if(p.getKnightService().testing())return false;
            if(from==KOMEProgressionRank.SERF){if(!KOMESerfdomMasterService.validateCurrentMasterInteraction(player,data,npc,true).success)return false;}
            else if(from!=KOMEProgressionRank.KNIGHT||!KOMECurrentLiege.valid(player,npc))return false;
            LOTRFaction f=LOTRLevelData.getData(player).getPledgeFaction();if(f==null)return false;
            String reason=reason(p,KOMEProgressionNpcRankService.referenceOf(npc),f.codeName(),LOTRLevelData.getData(player).getAlignment(f));
            if(!reason.isEmpty()){
                int credit=from==KOMEProgressionRank.SERF?duties(p.getSerfKnightProgression()):p.getKnightService().qualifyingTypes(f.codeName()).size();
                if(credit>=3){KOMEProgressionNpcSpeech.say(player,npc,reason);return true;}return false;
            }
            KOMESerfKnightTrialAssignment ordinary=p.getSerfKnightProgression().getTrialAssignment();
            if(p.getSerfKnightProgression().ordinaryObjective()){
                KOMESerfKnightEscortService.cleanup(player.worldObj,player.getUniqueID(),ordinary);
                KOMESerfKnightRecoveryService.cleanup(player.worldObj,player.getUniqueID(),ordinary);
                KOMESerfKnightDefenseService.cleanup(player.worldObj,player.getUniqueID(),ordinary);
                p.getSerfKnightProgression().retireMandatoryTrial(false);
            }
            if(!commit(data,player.getUniqueID(),KOMEProgressionNpcRankService.referenceOf(npc),f.codeName(),LOTRLevelData.getData(player).getAlignment(f),player.worldObj.getTotalWorldTime()))return false;
            try {
            KOMEProgressionNpcSpeech.say(player,npc,KOMERecommendationService.recognition(f,from,KOMEProgressionNpcRankService.effectiveRank(data,npc)));
            if(from==KOMEProgressionRank.SERF){KOMEPartingGiftService.pending(p.getSerfKnightProgression(),player.worldObj.rand);KOMEPartingGiftService.drop(player,data,npc);}
            KOMERecommendationService.deliver(player);KOMEProgressionAnnouncements.earned(player,from,false);
            } catch(RuntimeException failure) { org.apache.logging.log4j.LogManager.getLogger("KOME").warn("Service promotion presentation failed after standing and recommendation were saved",failure); }
            KOMEProgressionTitles.updatePlayerTitle(player);KOMEProgressionAutoCompleter.syncPlayer(player,p);
            KOMEProgressionOfferBridge.refreshPlayerOffers(player);return true;
        }
    }
    /** Retire obsolete objectives once, retaining evidence and all reported credits. No rank/reward effects. */
    public static boolean migrate(EntityPlayerMP player){
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=data.getProgression(player.getUniqueID());
        if(p.getServiceRecognition().migrationVersion>=1)return false;
        KOMESerfKnightProgression s=p.getSerfKnightProgression();KOMESerfKnightTrialAssignment old=s.getTrialAssignment();
        // Reconstruct letters only from real saved service, never from titles or staff-forced duties.
        if(p.getCanonicalRank().order>=KOMEProgressionRank.KNIGHT.order&&duties(s)>=3){
            KOMEProgressionNpcRef mentor=s.getFormerMaster().isSet()?s.getFormerMaster():s.getSerfdomMaster().isSet()?s.getSerfdomMaster():s.getDeceasedMaster();
            if(mentor.isSet())p.getServiceRecognition().earn(player.getUniqueID(),mentor,KOMEProgressionRank.SERF,KOMEProgressionRank.KNIGHT,"Faithful reported Master service",player.worldObj.getTotalWorldTime());
        }
        if(p.getCanonicalRank().order>=KOMEProgressionRank.LORD.order){
            for(KOMEKnightCommission deed:p.getKnightService().history())if(deed.stage==KOMEKnightCommission.Stage.REPORTED&&!deed.adminForced&&p.getKnightService().qualifyingTypes(deed.faction).size()>=3){
                p.getServiceRecognition().earn(player.getUniqueID(),deed.liege,KOMEProgressionRank.KNIGHT,KOMEProgressionRank.LORD,"Three reported commissions to our people",player.worldObj.getTotalWorldTime());break;
            }
        }
        for(KOMEServiceRecognition.Record record:p.getServiceRecognition().records())
            if(record.conferred==p.getCanonicalRank()&&s.hasLiege()&&KOMEProgressionFactionResolver.matches(record.faction,KOMEProgressionFactionResolver.resolve(s.getLiege().factionKey)))record.redeemed=true;
        if(old!=null&&!s.ordinaryObjective()){
            p.getServiceRecognition().retireStanding(old.writeToNBT());
            KOMESerfKnightEscortService.cleanup(player.worldObj,player.getUniqueID(),old);
            KOMESerfKnightRecoveryService.cleanup(player.worldObj,player.getUniqueID(),old);
            KOMESerfKnightDefenseService.cleanup(player.worldObj,player.getUniqueID(),old);
            s.retireMandatoryTrial(p.getCanonicalRank()==KOMEProgressionRank.SERF);
        }
        KOMELordshipTrial lordship=p.getLordship().assignment();
        if(lordship!=null&&!lordship.ordinaryService){KOMELordshipTrialService.cleanup(player.worldObj,player.getUniqueID(),lordship);lordship.retired=true;p.getLordship().archive();}
        if((old!=null&&!s.ordinaryObjective())||(lordship!=null&&!lordship.ordinaryService))
            player.addChatMessage(new net.minecraft.util.ChatComponentText("Promotion trials are retired. Your service is preserved. Report three assignments to your current superior."));
        p.getServiceRecognition().migrationVersion=1;KOMEProgressionNpcRoles.syncPlayer(data,player.getUniqueID());data.markDirty();return true;
    }
}
