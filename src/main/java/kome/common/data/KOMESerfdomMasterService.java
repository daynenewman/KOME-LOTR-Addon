package kome.common.data;

import java.util.UUID;
import kome.common.KOMEReflection;
import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;

/** Runtime validation boundary for canonical Serfdom Master interaction. */
public final class KOMESerfdomMasterService {
    public static final class Result { public final boolean success, enteredSerfdom; public final String reason; private Result(boolean success,String reason,boolean enteredSerfdom){this.success=success;this.reason=reason;this.enteredSerfdom=enteredSerfdom;} }
    private KOMESerfdomMasterService() { }
    private static Result ok(){return new Result(true,"",false);} private static Result entered(){return new Result(true,"",true);} private static Result reject(String value){return new Result(false,value,false);}
    public static Result validateMasterSelection(KOMEProgressionRank playerRank, String pledgeKey, String npcFactionKey, KOMEProgressionNpcRank npcRank, boolean validNpc) {
        if(playerRank != KOMEProgressionRank.WANDERER && playerRank != KOMEProgressionRank.SERF) return reject("Only a Wanderer or a player in their first faction rank may choose a Master.");
        if(!validNpc || npcRank != KOMEProgressionNpcRank.UNRANKED) return reject("That NPC is not an eligible Master.");
        if(pledgeKey == null || KOMEProgressionFactionResolver.resolve(pledgeKey)==null || !KOMEProgressionFactionResolver.resolve(pledgeKey).isPlayableAlignmentFaction()) return reject("You must first pledge yourself to this faction.");
        if(npcFactionKey == null || !KOMEProgressionFactionResolver.matches(npcFactionKey,KOMEProgressionFactionResolver.resolve(pledgeKey))) return reject("You have sworn yourself elsewhere.");
        return ok();
    }
    public static Result validateCurrentMasterInteraction(KOMEProgressionRank playerRank, String pledgeKey, String npcFactionKey, KOMEProgressionNpcRank npcRank, boolean validNpc) {
        if(playerRank != KOMEProgressionRank.SERF) return reject("Your current faction rank is not eligible for this Master interaction.");
        return validateMasterSelection(playerRank,pledgeKey,npcFactionKey,npcRank,validNpc);
    }
    public static Result validateMasterSelection(EntityPlayerMP player, KOMEWorldData data, LOTREntityNPC npc, boolean requireRange) {
        return validateRuntime(player,data,npc,requireRange,false);
    }
    public static Result validateCurrentMasterInteraction(EntityPlayerMP player, KOMEWorldData data, LOTREntityNPC npc, boolean requireRange) {
        return validateRuntime(player,data,npc,requireRange,true);
    }
    private static Result validateRuntime(EntityPlayerMP player, KOMEWorldData data, LOTREntityNPC npc, boolean requireRange, boolean currentInteraction) {
        if(player == null || data == null || npc == null || !npc.isEntityAlive() || npc.isChild()) return reject("A valid adult Master is required.");
        if(requireRange && player.getDistanceSqToEntity(npc)>64.0D) return reject("That Master is no longer close enough.");
        LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
        LOTRFaction faction=npc.getFaction();
        String pledgeKey=pledge==null || !pledge.isPlayableAlignmentFaction()?"":pledge.codeName();
        String factionKey=faction==null?"":faction.codeName();
        KOMEPlayerProgression progression=data.getProgression(KOMEReflection.getEntityUUID(player));
        KOMEProgressionNpcRef master=progression.getSerfKnightProgression().getSerfdomMaster();
        if(master.isSet()&&!master.hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(npc)))return reject("Return to your own Master.");
        return currentInteraction?validateCurrentMasterInteraction(progression.getCanonicalRank(),pledgeKey,factionKey,KOMEProgressionNpcRankService.effectiveRank(data,npc),KOMEProgressionNpcRankService.isValidFactionNpc(npc)):validateMasterSelection(progression.getCanonicalRank(),pledgeKey,factionKey,KOMEProgressionNpcRankService.effectiveRank(data,npc),KOMEProgressionNpcRankService.isValidFactionNpc(npc));
    }
    public static Result serve(EntityPlayerMP player, KOMEWorldData data, LOTREntityNPC npc) {
        Result validation=validateMasterSelection(player,data,npc,true); if(!validation.success)return validation;
        UUID playerId=KOMEReflection.getEntityUUID(player);KOMEPlayerProgression progression=data.getProgression(playerId);KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        if(!KOMESerfKnightService.canSelectReplacement(state,KOMESerfKnightService.calendarDayNow())) return reject("Progression assignments are locked after betrayal.");
        if(state.getSerfdomMaster().hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(npc))){KOMEProgressionNpcRoles.syncPlayer(data,playerId);return KOMECanonicalRankService.enterSerfdom(data,playerId)?entered():ok();}
        KOMESerfKnightService.Result result=KOMESerfKnightService.selectSerfdomMaster(state,data,npc);
        if(!result.success)return reject(result.reason);
        boolean promoted=KOMECanonicalRankService.enterSerfdom(data,playerId);KOMEProgressionNpcRoles.syncPlayer(data,playerId);data.markDirty(); KOMEProgressionOfferBridge.refreshPlayerOffers(player); return promoted?entered():ok();
    }
    /** Reward and rank transition share one locked, validated server transaction. */
    public static Result conferKnighthood(EntityPlayerMP player,KOMEWorldData data,LOTREntityNPC npc) {
        if(player==null||data==null||player.worldObj.isRemote)return reject("Server-side Master interaction required.");
        synchronized(data){
            Result validation=validateCurrentMasterInteraction(player,data,npc,true);if(!validation.success)return validation;
            LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
            String pledgeKey=pledge==null?"":pledge.codeName();double alignment=pledge==null?0D:LOTRLevelData.getData(player).getAlignment(pledge);
            UUID owner=player.getUniqueID();KOMEProgressionNpcRef master=KOMEProgressionNpcRankService.referenceOf(npc);
            validation=validateConfer(data,owner,master,pledgeKey,alignment);if(!validation.success)return validation;
            KOMESerfKnightProgression state=data.getProgression(owner).getSerfKnightProgression();
            KOMEPartingGiftService.pending(state,player.worldObj.rand);
            Result result=conferKnighthood(data,owner,master,pledgeKey,alignment);
            return result;
        }
    }
    /** Server-only transition shared by the live NPC interaction and integrated progression tests. */
    static Result conferKnighthood(KOMEWorldData data,UUID playerId,KOMEProgressionNpcRef clickedMaster,String pledgeKey,double alignment) {
        Result validation=validateConfer(data,playerId,clickedMaster,pledgeKey,alignment);if(!validation.success)return validation;
        KOMEPlayerProgression progression=data.getProgression(playerId);
        KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        KOMESerfKnightService.Result promotion=KOMESerfKnightService.markPromoted(state,alignment);
        if(!promotion.success)throw new IllegalStateException("Validated promotion could not complete knighthood: "+promotion.reason);
        KOMECanonicalRankService.setCanonicalRank(data,playerId,KOMEProgressionRank.KNIGHT);
        state.retireSerfdomMasterAfterPromotion();
        progression.grant("serf.title_knight");
        KOMEProgressionAutoCompleter.applyUnlocks(progression);
        KOMEProgressionNpcRoles.syncPlayer(data,playerId);
        data.markDirty();
        return ok();
    }
    private static Result validateConfer(KOMEWorldData data,UUID playerId,KOMEProgressionNpcRef clickedMaster,String pledgeKey,double alignment) {
        if(data==null||playerId==null||clickedMaster==null)return reject("A valid Master is required.");
        KOMEPlayerProgression progression=data.getProgression(playerId);
        KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        if(progression.getCanonicalRank()!=KOMEProgressionRank.SERF||state.isPromoted())return reject("Your next standing has already been conferred or is unavailable.");
        if(!state.getSerfdomMaster().hasSameIdentity(clickedMaster))return reject("Return to your own Master.");
        LOTRFaction pledgedFaction=KOMEProgressionFactionResolver.resolve(pledgeKey);
        KOMESerfKnightTrialAssignment trial=state.getTrialAssignment();
        if(pledgedFaction==null||!KOMEProgressionFactionResolver.matches(state.getSerfdomMaster().factionKey,pledgedFaction)
            ||trial==null||!KOMEProgressionFactionResolver.matches(trial.factionKey,pledgedFaction))return reject("Your current faction pledge must match your Master and completed Trial.");
        if(!KOMESerfKnightService.allDutiesComplete(state)||!state.isTrialCompleted()
            ||trial.stage==KOMESerfKnightTrialAssignment.Stage.FAILED
            ||!trial.isValidFor(state.getTrialId(),trial.liege))
            return reject("Complete your duties and your Liege's Trial of Standing first.");
        if(alignment<KOMESerfKnightService.REQUIRED_ALIGNMENT)return reject("Requires at least 150 positive faction alignment.");
        return ok();
    }
    public static Result requestDuty(EntityPlayerMP player,KOMEWorldData data,LOTREntityNPC npc) {
        if(player==null||data==null||player.worldObj.isRemote)return reject("Server-side Master interaction required.");
        synchronized(data){return requestDutyLocked(player,data,npc);}
    }
    private static Result requestDutyLocked(EntityPlayerMP player,KOMEWorldData data,LOTREntityNPC npc) {
        Result validation=validateCurrentMasterInteraction(player,data,npc,true); if(!validation.success)return validation;
        KOMESerfKnightProgression state=data.getProgression(KOMEReflection.getEntityUUID(player)).getSerfKnightProgression();
        if(state.hasActiveAssignment())return reject("Finish the work I have already given you.");
        if(!KOMESerfKnightService.mayIssueAssignment(state,KOMESerfKnightService.calendarDayNow(),player.getUniqueID()))
            return reject("You have already received a progression task today. Return tomorrow.");
        java.util.Random random=new java.util.Random();KOMESerfKnightDutyType next=KOMESerfKnightService.chooseAvailableDuty(state,random);net.minecraft.nbt.NBTTagCompound assignmentData=null;
        if(next==KOMESerfKnightDutyType.PROVISIONING)assignmentData=KOMESerfProvisioningAssignment.generate(npc.getFaction().codeName(),random).writeToNBT();
        else if(next==KOMESerfKnightDutyType.PROFESSION)assignmentData=KOMESerfProfessionAssignment.generate(KOMESerfProfessionClassifier.classify(npc),npc.getFaction().codeName(),random).writeToNBT();
        else if(next==KOMESerfKnightDutyType.COURIER) { KOMESerfCourierAssignment courier=KOMESerfCourierAssignment.create(KOMEProgressionNpcRankService.referenceOf(npc),KOMEReflection.getWorld(player));if(courier==null||!KOMECourierRecipientSpawner.prepareCorrespondent(player.worldObj,courier,npc))return reject("No suitable faction correspondent is available for courier work.");assignmentData=courier.writeToNBT(); }
        net.minecraft.nbt.NBTTagCompound before=state.writeToNBT();
        Result request=requestDuty(state,KOMEProgressionNpcRankService.referenceOf(npc),KOMESerfKnightService.calendarDayNow(),assignmentData,random,player.getUniqueID());
        if(!request.success)return request;
        if(next==KOMESerfKnightDutyType.COURIER&&!KOMECourierIssuance.initial(player,data,npc)){state.readFromNBT(before);data.markDirty();return reject("The dispatch could not be issued. Ask again when the way is clear.");}
        data.markDirty(); return ok();
    }
    /** Identity/cadence orchestration only; assignment mutation remains in KOMESerfKnightService. */
    static Result requestDuty(KOMESerfKnightProgression state,KOMEProgressionNpcRef clickedMaster,long calendarDay) {return requestDuty(state,clickedMaster,calendarDay,new java.util.Random());}
    static Result requestDuty(KOMEPlayerProgression progression,KOMEProgressionNpcRef clickedMaster,long calendarDay,net.minecraft.nbt.NBTTagCompound suppliedAssignmentData,java.util.Random random) {if(progression==null||progression.getCanonicalRank()!=KOMEProgressionRank.SERF)return reject("Your current faction rank is not eligible for this Master interaction.");return requestDuty(progression.getSerfKnightProgression(),clickedMaster,calendarDay,suppliedAssignmentData,random);}
    static Result requestDuty(KOMESerfKnightProgression state,KOMEProgressionNpcRef clickedMaster,long calendarDay,java.util.Random random) {return requestDuty(state,clickedMaster,calendarDay,null,random);}
    static Result requestDuty(KOMESerfKnightProgression state,KOMEProgressionNpcRef clickedMaster,long calendarDay,java.util.Random random,java.util.UUID playerId) {return requestDuty(state,clickedMaster,calendarDay,null,random,playerId);}
    static Result requestDuty(KOMESerfKnightProgression state,KOMEProgressionNpcRef clickedMaster,long calendarDay,net.minecraft.nbt.NBTTagCompound suppliedAssignmentData,java.util.Random random) {
        return requestDuty(state,clickedMaster,calendarDay,suppliedAssignmentData,random,null);
    }
    static Result requestDuty(KOMESerfKnightProgression state,KOMEProgressionNpcRef clickedMaster,long calendarDay,net.minecraft.nbt.NBTTagCompound suppliedAssignmentData,java.util.Random random,java.util.UUID playerId) {
        if(state==null || clickedMaster==null || !clickedMaster.isSet()) return reject("A valid Master is required.");
        if(!state.getSerfdomMaster().hasSameIdentity(clickedMaster)) return reject("You may only request duties from your current Master.");
        KOMESerfCourierAssignment courier=suppliedAssignmentData==null?null:KOMESerfCourierAssignment.readFromNBT(suppliedAssignmentData);KOMESerfKnightDutyType next=suppliedAssignmentData!=null&&suppliedAssignmentData.hasKey("TradeKey")?KOMESerfKnightDutyType.PROFESSION:courier!=null?KOMESerfKnightDutyType.COURIER:suppliedAssignmentData!=null&&suppliedAssignmentData.hasKey("Foods",9)?KOMESerfKnightDutyType.PROVISIONING:KOMESerfKnightService.chooseAvailableDuty(state,random); if(next==null)return reject("All duties are already complete.");
        if(!KOMESerfKnightService.mayIssueAssignment(state,calendarDay,playerId))return reject("You have already received a progression task today. Return later for another assignment.");
        net.minecraft.nbt.NBTTagCompound data=null;if(next==KOMESerfKnightDutyType.PROVISIONING)data=suppliedAssignmentData==null?KOMESerfProvisioningAssignment.generate(clickedMaster.factionKey,random).writeToNBT():(net.minecraft.nbt.NBTTagCompound)suppliedAssignmentData.copy();else if((next==KOMESerfKnightDutyType.PROFESSION||next==KOMESerfKnightDutyType.COURIER)&&suppliedAssignmentData!=null)data=(net.minecraft.nbt.NBTTagCompound)suppliedAssignmentData.copy();
        KOMESerfKnightService.Result result=playerId==null?KOMESerfKnightService.assignDuty(state,next,data,calendarDay):KOMESerfKnightService.assignDuty(state,next,data,calendarDay,playerId);
        if(!result.success)return reject(result.reason);
        return ok();
    }
}
