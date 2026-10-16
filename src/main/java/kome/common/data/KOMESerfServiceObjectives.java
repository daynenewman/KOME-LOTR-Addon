package kome.common.data;

import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.player.EntityPlayerMP;

/** Reuses the bound Standing encounter mechanics as optional ordinary Master duties. */
public final class KOMESerfServiceObjectives {
    private KOMESerfServiceObjectives(){}
    public static boolean objective(KOMESerfKnightDutyType t){return t==KOMESerfKnightDutyType.ESCORT||t==KOMESerfKnightDutyType.RECOVERY||t==KOMESerfKnightDutyType.DEFENSE;}
    public static boolean prepare(EntityPlayerMP player,KOMESerfKnightProgression state,LOTREntityNPC master,KOMESerfKnightDutyType type){
        KOMESerfKnightTrialAssignment a=KOMESerfKnightTrialAssignment.create(KOMESerfKnightTrial.forId(type.key),KOMEProgressionNpcRankService.referenceOf(master),KOMESerfKnightService.calendarDayNow(),player.worldObj.rand.nextInt(2));
        a.data.setBoolean("OrdinaryDuty",true);state.setTrial(a);
        if(type==KOMESerfKnightDutyType.ESCORT&&!KOMESerfKnightEscortService.prepareDestination(state,player.worldObj)){state.retireMandatoryTrial(false);return false;}
        return true;
    }
    public static boolean interact(EntityPlayerMP player,LOTREntityNPC master){
        KOMEWorldData world=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=world.getProgression(player.getUniqueID());
        KOMESerfKnightProgression s=p.getSerfKnightProgression();KOMESerfKnightTrialAssignment a=s.getTrialAssignment();
        if(!s.ordinaryObjective()||!KOMESerfdomMasterService.validateCurrentMasterInteraction(player,world,master,true).success)return false;
        KOMESerfKnightDutyType type=KOMESerfKnightDutyType.forKey(a.trialId);
        if(a.stage==KOMESerfKnightTrialAssignment.Stage.FAILED){
            cleanup(player,a);s.cancelTestDuty(type);s.retireMandatoryTrial(false);world.markDirty();
            KOMEProgressionNpcSpeech.say(player,master,"This assignment was lost. Your earlier service remains honoured. Ask me for new work.");return true;
        }
        if(a.stage==KOMESerfKnightTrialAssignment.Stage.ASSIGNED){
            if(a.data.getBoolean("ActivationPending")&&player.worldObj.getTotalWorldTime()<a.data.getLong("ActivationRetryAt"))return true;
            if(a.data.getInteger("ActivationFailures")>=3){
                cleanup(player,a);s.cancelTestDuty(type);s.retireMandatoryTrial(false);world.markDirty();
                KOMEProgressionNpcSpeech.say(player,master,"This work could not be arranged safely. Your earlier service is preserved. Ask me for another assignment.");
                KOMEProgressionAutoCompleter.syncPlayer(player,p);return true;
            }
            boolean activated=type==KOMESerfKnightDutyType.ESCORT?KOMESerfKnightEscortService.activate(player,p,master):
                type==KOMESerfKnightDutyType.RECOVERY?KOMESerfKnightRecoveryService.activate(player,p,master):KOMESerfKnightDefenseService.activate(player,p,master);
            if(!activated){KOMEProgressionEncounterCleanup.activationFailed(world,player.worldObj,s);KOMEProgressionNpcSpeech.say(player,master,"The work cannot be arranged safely yet. Your assignment and earlier service are preserved. Return shortly.");}
            else KOMEProgressionNpcSpeech.say(player,master,KOMESerfKnightService.trialSpeech(s.getTrialAssignment()));
            return true;
        }
        if(type==KOMESerfKnightDutyType.RECOVERY&&a.stage==KOMESerfKnightTrialAssignment.Stage.ACTIVE)KOMESerfKnightRecoveryService.deliver(player,p,master);
        a=s.getTrialAssignment();
        if(a!=null&&a.stage==KOMESerfKnightTrialAssignment.Stage.OBJECTIVE_COMPLETE){
            s.setDutyAssignmentData(type,a.writeToNBT());KOMESerfKnightService.completeDuty(p,type);
            KOMEProgressionServiceRewards.duty(player,s,type);cleanup(player,a);s.retireMandatoryTrial(false);
            world.markDirty();if(!KOMEServicePromotionService.report(player,master))KOMEProgressionNpcSpeech.say(player,master,"Your service is remembered. Speak with me when you are ready for another duty.");
            KOMEProgressionAutoCompleter.syncPlayer(player,p);return true;
        }
        KOMEProgressionNpcSpeech.say(player,master,KOMESerfKnightService.existingTrialSpeech(a));return true;
    }
    private static void cleanup(EntityPlayerMP player,KOMESerfKnightTrialAssignment a){
        KOMESerfKnightEscortService.cleanup(player.worldObj,player.getUniqueID(),a);
        KOMESerfKnightRecoveryService.cleanup(player.worldObj,player.getUniqueID(),a);
        KOMESerfKnightDefenseService.cleanup(player.worldObj,player.getUniqueID(),a);
    }
}
