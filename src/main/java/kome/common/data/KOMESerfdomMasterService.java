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
        if(playerRank != KOMEProgressionRank.SERF) return reject("Pledge allegiance to attain Serf standing before choosing a Master.");
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
    public static boolean ordinaryMaster(LOTREntityNPC npc) {
        return npc!=null&&npc.getFaction()!=null&&(npc.isCivilianNPC()||KOMESerfProfessionClassifier.farmerOnly(npc))
            &&(npc.hiredNPCInfo==null||!npc.hiredNPCInfo.isActive)
            &&!npc.isTraderEscort&&KOMEProgressionEncounterMarker.read(npc)==null;
    }
    private static Result validateRuntime(EntityPlayerMP player, KOMEWorldData data, LOTREntityNPC npc, boolean requireRange, boolean currentInteraction) {
        if(player!=null&&npc!=null&&(player.worldObj.isRemote||npc.worldObj!=player.worldObj
                ||data!=KOMEWorldData.get(player.worldObj)||!ordinaryMaster(npc)))return reject("An ordinary native faction civilian Master is required.");
        if(player == null || data == null || npc == null || !npc.isEntityAlive() || npc.isChild()) return reject("A valid adult Master is required.");
        if(requireRange && player.getDistanceSqToEntity(npc)>64.0D) return reject("That Master is no longer close enough.");
        if(!npc.isFriendlyAndAligned(player))return reject("This Master cannot receive your service while hostile or unaligned.");
        if(npc instanceof lotr.common.entity.npc.LOTRTradeable&&!((lotr.common.entity.npc.LOTRTradeable)npc).canTradeWith(player))return reject("Meet this civilian's native interaction requirements first.");
        LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
        LOTRFaction faction=npc.getFaction();
        String pledgeKey=pledge==null || !pledge.isPlayableAlignmentFaction()?"":pledge.codeName();
        String factionKey=faction==null?"":faction.codeName();
        KOMEPlayerProgression progression=data.getProgression(KOMEReflection.getEntityUUID(player));
        KOMEProgressionNpcRef master=progression.getSerfKnightProgression().getSerfdomMaster();
        if(master.isSet()&&!master.hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(npc)))return reject("Return to your own Master.");
        if(!currentInteraction&&!master.hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(npc))
                &&!KOMEProgressionServiceLocation.factionTerritory(npc))return reject("Seek an ordinary Master in your faction's native territory.");
        return currentInteraction?validateCurrentMasterInteraction(progression.getCanonicalRank(),pledgeKey,factionKey,KOMEProgressionNpcRankService.effectiveRank(data,npc),KOMEProgressionNpcRankService.isValidFactionNpc(npc)):validateMasterSelection(progression.getCanonicalRank(),pledgeKey,factionKey,KOMEProgressionNpcRankService.effectiveRank(data,npc),KOMEProgressionNpcRankService.isValidFactionNpc(npc));
    }
    public static Result serve(EntityPlayerMP player, KOMEWorldData data, LOTREntityNPC npc) {
        Result validation=validateMasterSelection(player,data,npc,true); if(!validation.success)return validation;
        UUID playerId=KOMEReflection.getEntityUUID(player);KOMEPlayerProgression progression=data.getProgression(playerId);KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        if(!KOMESerfKnightService.canSelectReplacement(state,KOMESerfKnightService.calendarDayNow())) return reject("Progression assignments are locked after betrayal.");
        if(state.getSerfdomMaster().hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(npc))){KOMEProgressionNpcRoles.syncPlayer(data,playerId);return ok();}
        KOMESerfKnightService.Result result=KOMESerfKnightService.selectSerfdomMaster(state,data,npc);
        if(!result.success)return reject(result.reason);
        boolean promoted=false;KOMEProgressionNpcRoles.syncPlayer(data,playerId);data.markDirty(); KOMEProgressionOfferBridge.refreshPlayerOffers(player); if(promoted)KOMEProgressionAnnouncements.earned(player,KOMEProgressionRank.WANDERER,false);return promoted?entered():ok();
    }
    /** Compatibility facade; only the current Master may conclude service. */
    public static Result conferKnighthood(EntityPlayerMP player,KOMEWorldData data,LOTREntityNPC npc) {
        if(player==null||data!=KOMEWorldData.get(player.worldObj)||data.getProgression(player.getUniqueID()).getCanonicalRank()!=KOMEProgressionRank.SERF)return reject("Return to your Master after three qualifying duties.");
        KOMEServicePromotionService.report(player,npc);
        return data.getProgression(player.getUniqueID()).getCanonicalRank()==KOMEProgressionRank.KNIGHT?ok():reject("Complete three qualifying duties and reach 150 faction alignment, then report to your current Master.");
    }
    static Result conferKnighthood(KOMEWorldData data,UUID owner,KOMEProgressionNpcRef master,String faction,double alignment) {
        String reason=KOMEServicePromotionService.reason(data.getProgression(owner),master,faction,alignment);
        if(!reason.isEmpty())return reject(reason);
        return KOMEServicePromotionService.commit(data,owner,master,faction,alignment,0L)?ok():reject("Promotion unavailable.");
    }
    public static Result requestDuty(EntityPlayerMP player,KOMEWorldData data,LOTREntityNPC npc) {
        if(player==null||data==null||player.worldObj.isRemote)return reject("Server-side Master interaction required.");
        synchronized(data){return requestDutyLocked(player,data,npc);}
    }
    private static Result requestDutyLocked(EntityPlayerMP player,KOMEWorldData data,LOTREntityNPC npc) {
        Result validation=validateCurrentMasterInteraction(player,data,npc,true); if(!validation.success)return validation;
        KOMESerfKnightProgression state=data.getProgression(KOMEReflection.getEntityUUID(player)).getSerfKnightProgression();
        if(KOMEServicePromotionService.duties(state)>=3)return reject("Your service is complete. Report to me for Knight standing; 150 faction alignment is required.");
        if(state.hasActiveAssignment())return reject("Finish the work I have already given you.");
        if(!KOMESerfKnightService.mayIssueAssignment(state,KOMESerfKnightService.calendarDayNow(),player.getUniqueID()))
            return reject("Finish your active assignment before requesting another.");
        java.util.Random random=player.worldObj.rand;
        java.util.List<KOMESerfKnightDutyType> candidates=new java.util.ArrayList<KOMESerfKnightDutyType>();
        for(KOMESerfKnightDutyType type:KOMESerfKnightDutyType.values())if(!state.getDuty(type).isCompleted()){
            if(type==KOMESerfKnightDutyType.ESCORT&&KOMESerfKnightEscortService.findCharge(player,state,npc)==null)continue;
            if(type==KOMESerfKnightDutyType.DEFENSE&&(KOMESerfKnightDefenseService.findObjective(player,state,npc)==null||KOMEProgressionRegionalEnemies.choices(player.worldObj,npc.getFaction(),npc.posX,npc.posZ).isEmpty()))continue;
            // Avoid two successive delivery experiences when a distinct local activity is feasible.
            if(type==KOMESerfKnightDutyType.PROVISIONING&&state.getDuty(KOMESerfKnightDutyType.PROFESSION).isCompleted())continue;
            if(type==KOMESerfKnightDutyType.PROFESSION&&state.getDuty(KOMESerfKnightDutyType.PROVISIONING).isCompleted())continue;
            candidates.add(type);
        }
        java.util.Collections.shuffle(candidates,random);
        if(candidates.isEmpty())return reject("No suitable local work can be arranged here. Your completed service is preserved.");
        KOMESerfKnightDutyType next=candidates.get(0);net.minecraft.nbt.NBTTagCompound assignmentData=null;
        if(KOMESerfServiceObjectives.objective(next)){
            if(!KOMESerfServiceObjectives.prepare(player,state,npc,next))return reject("No safe destination is available here. Your service is preserved.");
            state.assignDuty(next,state.getTrialAssignment().writeToNBT());data.markDirty();KOMESerfServiceObjectives.interact(player,npc);return ok();
        }
        if(next==KOMESerfKnightDutyType.PROVISIONING)assignmentData=KOMESerfProvisioningAssignment.generate(player,npc.getFaction().codeName(),random).writeToNBT();
        else if(next==KOMESerfKnightDutyType.PROFESSION)assignmentData=KOMESerfProfessionAssignment.generate(KOMESerfProfessionClassifier.classify(npc),npc.getFaction().codeName(),random).writeToNBT();
        else if(next==KOMESerfKnightDutyType.COURIER) { KOMESerfCourierAssignment courier=KOMESerfCourierAssignment.create(KOMEProgressionNpcRankService.referenceOf(npc),KOMEReflection.getWorld(player),state.variety);if(courier==null||!KOMECourierRecipientSpawner.prepareCorrespondent(player.worldObj,courier,npc))return reject("No suitable faction correspondent is available for courier work.");assignmentData=courier.writeToNBT(); }
        net.minecraft.nbt.NBTTagCompound before=state.writeToNBT();
        Result request=requestDuty(state,KOMEProgressionNpcRankService.referenceOf(npc),KOMESerfKnightService.calendarDayNow(),assignmentData,random,player.getUniqueID());
        if(!request.success)return request;
        if(next==KOMESerfKnightDutyType.COURIER&&!KOMECourierIssuance.initial(player,data,npc)){state.readFromNBT(before);data.markDirty();return reject("The dispatch could not be issued. Ask again when the way is clear.");}
        if(next==KOMESerfKnightDutyType.COURIER){KOMESerfCourierAssignment courier=KOMESerfCourierAssignment.readFromNBT(assignmentData);if(courier!=null)state.variety.remember(KOMEProgressionNpcRankService.referenceOf(npc),"courier",courier.destinationX,courier.destinationZ);}data.markDirty(); return ok();
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
        if(!KOMESerfKnightService.mayIssueAssignment(state,calendarDay,playerId))return reject("Finish your active assignment before requesting another.");
        net.minecraft.nbt.NBTTagCompound data=null;if(next==KOMESerfKnightDutyType.PROVISIONING)data=suppliedAssignmentData==null?KOMESerfProvisioningAssignment.generate(clickedMaster.factionKey,random).writeToNBT():(net.minecraft.nbt.NBTTagCompound)suppliedAssignmentData.copy();else if((next==KOMESerfKnightDutyType.PROFESSION||next==KOMESerfKnightDutyType.COURIER)&&suppliedAssignmentData!=null)data=(net.minecraft.nbt.NBTTagCompound)suppliedAssignmentData.copy();
        KOMESerfKnightService.Result result=playerId==null?KOMESerfKnightService.assignDuty(state,next,data,calendarDay):KOMESerfKnightService.assignDuty(state,next,data,calendarDay,playerId);
        if(!result.success)return reject(result.reason);
        return ok();
    }
}
