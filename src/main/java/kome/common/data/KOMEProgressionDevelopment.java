package kome.common.data;

import java.util.*;
import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/** Staff-only callers use existing completion/cleanup boundaries, with explicit test provenance. */
public final class KOMEProgressionDevelopment {
    private KOMEProgressionDevelopment() {}
    public static String finish(EntityPlayerMP player){
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=data.getProgression(player.getUniqueID());KOMESerfKnightProgression s=p.getSerfKnightProgression();
        LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
        if(pledge==null)return "Pledge to the assignment's faction first.";
        String active=s.getActiveAssignmentKind();KOMESerfKnightDutyType duty=KOMESerfKnightDutyType.forKey(active);
        if(p.getCanonicalRank()==KOMEProgressionRank.SERF&&duty!=null){
            if(!KOMEProgressionFactionResolver.matches(s.getSerfdomMaster().factionKey,pledge))return "Your pledge does not match your Master.";
            s.getDuty(duty).adminForced=true;s.getDuty(duty).rewardReserved=true;
            if(duty==KOMESerfKnightDutyType.COURIER){
                KOMESerfCourierAssignment dispatch=KOMESerfCourierAssignment.readFromNBT(s.getDuty(duty).getAssignmentData());if(dispatch==null)return "No valid dispatch to finish.";
                KOMEProgressionEncounterCleanup.cleanup(player,p);dispatch.stage=KOMESerfCourierAssignment.Stage.DELIVERED;s.setDutyAssignmentData(duty,dispatch.writeToNBT());
                changed(player,data,p,"finishDuty");return "Test dispatch ready to report to your Master. No normal delivery evidence or payment recorded.";
            }
            KOMESerfKnightService.Result result=KOMESerfKnightService.completeDuty(p,duty);if(!result.success)return result.reason;
            changed(player,data,p,"finishDuty");return "Test duty completed. No goods, payment or normal delivery evidence recorded.";
        }
        if(p.getCanonicalRank()==KOMEProgressionRank.SERF&&s.getTrialAssignment()!=null&&!s.isTrialCompleted()){
            KOMESerfKnightTrialAssignment a=s.getTrialAssignment();if(!KOMEProgressionFactionResolver.matches(a.factionKey,pledge)||!s.getLiege().hasSameIdentity(a.liege))return "Your trial relationship or pledge is invalid.";
            if(a.stage==KOMESerfKnightTrialAssignment.Stage.FAILED)return "Reset the failed trial first.";
            KOMEProgressionEncounterCleanup.cleanup(player,p);NBTTagCompound n=(NBTTagCompound)a.data.copy();n.setBoolean("AdminForced",true);
            s.updateTrialAssignment(a.withStage(KOMESerfKnightTrialAssignment.Stage.OBJECTIVE_COMPLETE,n));s.trialRewardReserved=true;
            KOMESerfKnightService.Result result=KOMESerfKnightService.completeTrial(s);if(!result.success)return result.reason;
            changed(player,data,p,"finishDuty");return "Test trial ready. Report personally to your Liege; rank is unchanged.";
        }
        KOMELordshipTrial trial=p.getLordship().assignment();KOMEKnightCommission a=trial==null?p.getKnightService().assignment():trial.objective;
        if(p.getCanonicalRank()!=KOMEProgressionRank.KNIGHT||a==null)return "No eligible active progression service.";
        if(!KOMEProgressionFactionResolver.matches(a.faction,pledge)||!a.liege.hasSameIdentity(s.getLiege()))return "Your commission relationship or pledge is invalid.";
        if(a.stage==KOMEKnightCommission.Stage.OFFERED||a.stage==KOMEKnightCommission.Stage.FAILED||a.stage==KOMEKnightCommission.Stage.REPORTED)return "Receive your orders before finishing an active test service.";
        if(trial!=null){KOMELordshipTrialService.cleanup(player.worldObj,player.getUniqueID(),trial);trial.forceReleased=true;}
        else KOMEKnightCommissionService.cleanup(player.worldObj,player.getUniqueID(),a);
        a.adminForced=true;a.rewardReserved=true;a.stage=KOMEKnightCommission.Stage.READY_TO_REPORT;
        changed(player,data,p,"finishDuty");return "Test service ready to report personally. No combat, delivery, payment or earned service credit recorded.";
    }
    public static String reset(EntityPlayerMP player){
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=data.getProgression(player.getUniqueID());KOMESerfKnightProgression s=p.getSerfKnightProgression();
        KOMEProgressionEncounterCleanup.cleanup(player,p);KOMELordshipTrialService.cancel(player.worldObj,player.getUniqueID(),p);p.getKnightService().discardTestAssignment();
        KOMESerfKnightDutyType duty=KOMESerfKnightDutyType.forKey(s.getActiveAssignmentKind());if(duty!=null)s.cancelTestDuty(duty);
        if(!s.isPromoted()&&s.getTrialAssignment()!=null){s.cancelTrial();s.leaveLiege();}
        changed(player,data,p,"resetDuty");return "Test assignment cleaned up. Earned history, rank, daily limits and failed-Liege restrictions remain.";
    }
    private static void changed(EntityPlayerMP player,KOMEWorldData data,KOMEPlayerProgression p,String action){KOMEAuditService.record(data,System.currentTimeMillis(),"progression",action,player.getCommandSenderName(),player.getUniqueID().toString(),"administrator testing","No earned gameplay evidence or reward");data.markDirty();KOMEProgressionNpcRoles.syncPlayer(data,player.getUniqueID());KOMEProgressionAutoCompleter.syncPlayer(player,p);}
    public static List<String> status(EntityPlayerMP player){
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=data.getProgression(player.getUniqueID());KOMESerfKnightProgression s=p.getSerfKnightProgression();LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
        List<String> lines=new ArrayList<String>();lines.add("Pledge: "+(pledge==null?"none":pledge.codeName())+" | canonical rank: "+p.getCanonicalRank().key);lines.add("Master: "+reference(s.getSerfdomMaster()));lines.add("Liege: "+reference(s.getLiege()));lines.add("Active duty: "+s.getActiveAssignmentKind()+" | phase: "+s.getPhase());
        KOMESerfKnightDutyType duty=KOMESerfKnightDutyType.forKey(s.getActiveAssignmentKind());if(duty!=null){KOMESerfKnightProgression.Duty d=s.getDuty(duty);lines.add("Duty data: "+d.getAssignmentData());lines.add("Compensation reserved: "+d.materialPaid+" | forced: "+d.adminForced);}
        KOMESerfKnightTrialAssignment standing=s.getTrialAssignment();if(standing!=null)lines.add("Standing trial: "+standing.trialId+" | "+standing.stage+" | token: "+standing.assignmentToken+" | data: "+standing.data);
        KOMELordshipTrial t=p.getLordship().assignment();KOMEKnightCommission a=t==null?p.getKnightService().assignment():t.objective;
        if(a!=null){lines.add((t==null?"Commission":"Lordship trial")+": "+a.type+" | "+a.stage+" | forced: "+a.adminForced);lines.add("Destination: dim "+a.dimension+" ("+a.destinationX+", "+a.destinationZ+") | token/revision: "+a.token+"/"+a.revision);for(KOMEKnightCommission.Actor actor:a.actors)lines.add("Actor: "+actor.role+" "+actor.id+" | dead: "+actor.dead);for(KOMEKnightCommission.Goods g:a.goods)lines.add("Item: "+g.itemKey+":"+g.damage+" "+g.delivered+"/"+g.required);}
        lines.add("Daily duty/trial: "+s.getLastAssignmentEpochDay()+" / "+s.getLastTrialAssignmentEpochDay()+" | today: "+KOMESerfKnightService.calendarDayNow());for(NBTTagCompound n:s.failures())lines.add("Failed Liege: "+n.getString("Npc")+" | faction/trial: "+n.getString("Faction")+"/"+n.getString("Trial")+" | expiry: "+n.getLong("Expiry"));lines.add("Next action: "+why(player));return lines;
    }
    private static String reference(KOMEProgressionNpcRef n){return n.isSet()?n.displayName+" ["+n.entityUuid+"] dim "+n.dimension+" ("+n.x+", "+n.z+")":"none";}
    public static String why(EntityPlayerMP player){
        KOMEPlayerProgression p=KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID());KOMESerfKnightProgression s=p.getSerfKnightProgression();
        String id=s.hasLiege()?s.getLiege().entityUuid:s.getSerfdomMaster().entityUuid;Entity current=KOMEKnightCommissionService.findLoaded(player.worldObj,id);
        if(current instanceof LOTREntityNPC&&s.hasLiege()){KOMEProgressionEligibility e=KOMEProgressionEligibility.evaluate(player,(LOTREntityNPC)current);return e.enabled?"Quest is available from your Liege.":e.reason;}
        LOTREntityNPC nearest=null;double distance=64*64;for(Object o:player.worldObj.loadedEntityList)if(o instanceof LOTREntityNPC){LOTREntityNPC npc=(LOTREntityNPC)o;if(KOMEProgressionNpcRankService.effectiveRank(KOMEWorldData.get(player.worldObj),npc)!=KOMEProgressionNpcRank.UNRANKED&&player.getDistanceSqToEntity(npc)<distance){nearest=npc;distance=player.getDistanceSqToEntity(npc);}}
        if(nearest!=null){KOMEProgressionEligibility e=KOMEProgressionEligibility.evaluate(player,nearest);return nearest.getNPCName()+": "+(e.enabled?"Quest available":e.reason);}
        LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();return KOMEProgressionSummary.text(p,pledge==null?"":pledge.factionName(),pledge==null?"":pledge.codeName(),pledge==null?0:LOTRLevelData.getData(player).getAlignment(pledge));
    }
}
