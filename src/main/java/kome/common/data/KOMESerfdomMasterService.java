package kome.common.data;

import java.util.UUID;
import kome.common.KOMEReflection;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketLordHighlight;
import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

/** Runtime validation boundary for canonical Serfdom Master interaction. */
public final class KOMESerfdomMasterService {
    public static final class Result { public final boolean success, enteredSerfdom; public final String reason; private Result(boolean success,String reason,boolean enteredSerfdom){this.success=success;this.reason=reason;this.enteredSerfdom=enteredSerfdom;} }
    private KOMESerfdomMasterService() { }
    private static Result ok(){return new Result(true,"",false);} private static Result entered(){return new Result(true,"",true);} private static Result reject(String value){return new Result(false,value,false);}
    public static Result validateMasterSelection(KOMEProgressionRank playerRank, String pledgeKey, String npcFactionKey, KOMEProgressionNpcRank npcRank, boolean validNpc) {
        if(playerRank != KOMEProgressionRank.WANDERER && playerRank != KOMEProgressionRank.SERF) return reject("Only a Wanderer or Serf may choose a Serfdom Master.");
        if(!validNpc || npcRank != KOMEProgressionNpcRank.UNRANKED) return reject("That NPC is not an eligible Serfdom Master.");
        if(pledgeKey == null || pledgeKey.trim().length()==0) return reject("You must pledge to a playable faction before serving a Serfdom Master.");
        if(npcFactionKey == null || !pledgeKey.trim().equalsIgnoreCase(npcFactionKey.trim())) return reject("A Serfdom Master must belong to your pledged faction.");
        return ok();
    }
    public static Result validateCurrentMasterInteraction(KOMEProgressionRank playerRank, String pledgeKey, String npcFactionKey, KOMEProgressionNpcRank npcRank, boolean validNpc) {
        if(playerRank != KOMEProgressionRank.SERF) return reject("Canonical Serf rank is required.");
        return validateMasterSelection(playerRank,pledgeKey,npcFactionKey,npcRank,validNpc);
    }
    public static Result validateMasterSelection(EntityPlayerMP player, KOMEWorldData data, LOTREntityNPC npc, boolean requireRange) {
        return validateRuntime(player,data,npc,requireRange,false);
    }
    public static Result validateCurrentMasterInteraction(EntityPlayerMP player, KOMEWorldData data, LOTREntityNPC npc, boolean requireRange) {
        return validateRuntime(player,data,npc,requireRange,true);
    }
    private static Result validateRuntime(EntityPlayerMP player, KOMEWorldData data, LOTREntityNPC npc, boolean requireRange, boolean currentInteraction) {
        if(player == null || data == null || npc == null || !npc.isEntityAlive() || npc.isChild()) return reject("A valid adult Serfdom Master is required.");
        if(requireRange && player.getDistanceSqToEntity(npc)>64.0D) return reject("That Serfdom Master is no longer close enough.");
        LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
        LOTRFaction faction=npc.getFaction();
        String pledgeKey=pledge==null || !pledge.isPlayableAlignmentFaction()?"":pledge.codeName();
        String factionKey=faction==null?"":faction.codeName();
        KOMEPlayerProgression progression=data.getProgression(KOMEReflection.getEntityUUID(player));
        return currentInteraction?validateCurrentMasterInteraction(progression.getCanonicalRank(),pledgeKey,factionKey,KOMEProgressionNpcRankService.effectiveRank(data,npc),KOMEProgressionNpcRankService.isValidFactionNpc(npc)):validateMasterSelection(progression.getCanonicalRank(),pledgeKey,factionKey,KOMEProgressionNpcRankService.effectiveRank(data,npc),KOMEProgressionNpcRankService.isValidFactionNpc(npc));
    }
    public static Result serve(EntityPlayerMP player, KOMEWorldData data, LOTREntityNPC npc) {
        Result validation=validateMasterSelection(player,data,npc,true); if(!validation.success)return validation;
        UUID playerId=KOMEReflection.getEntityUUID(player);KOMEPlayerProgression progression=data.getProgression(playerId);KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        if(!KOMESerfKnightService.canSelectReplacement(state,KOMESerfKnightService.calendarDayNow())) return reject("Progression assignments are locked after betrayal.");
        if(state.getSerfdomMaster().hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(npc))) return KOMECanonicalRankService.enterSerfdom(data,playerId)?entered():ok();
        KOMESerfKnightService.Result result=KOMESerfKnightService.selectSerfdomMaster(state,data,npc);
        if(!result.success)return reject(result.reason);
        boolean promoted=KOMECanonicalRankService.enterSerfdom(data,playerId);data.markDirty(); return promoted?entered():ok();
    }
    public static Result requestDuty(EntityPlayerMP player,KOMEWorldData data,LOTREntityNPC npc) {
        Result validation=validateCurrentMasterInteraction(player,data,npc,true); if(!validation.success)return validation;
        KOMESerfKnightProgression state=data.getProgression(KOMEReflection.getEntityUUID(player)).getSerfKnightProgression();
        KOMESerfKnightDutyType next=KOMESerfKnightService.nextDuty(state);java.util.Random random=new java.util.Random();net.minecraft.nbt.NBTTagCompound assignmentData=null;
        if(next==KOMESerfKnightDutyType.PROVISIONING)assignmentData=KOMESerfProvisioningAssignment.generate(npc.getFaction().codeName(),random).writeToNBT();
        else if(next==KOMESerfKnightDutyType.PROFESSION)assignmentData=KOMESerfProfessionAssignment.generate(KOMESerfProfessionClassifier.classify(npc),npc.getFaction().codeName(),random).writeToNBT();
        Result request=requestDuty(state,KOMEProgressionNpcRankService.referenceOf(npc),KOMESerfKnightService.calendarDayNow(),assignmentData,random);
        if(!request.success)return request;
        data.markDirty(); return ok();
    }
    /** Identity/cadence orchestration only; assignment mutation remains in KOMESerfKnightService. */
    static Result requestDuty(KOMESerfKnightProgression state,KOMEProgressionNpcRef clickedMaster,long calendarDay) {return requestDuty(state,clickedMaster,calendarDay,new java.util.Random());}
    static Result requestDuty(KOMEPlayerProgression progression,KOMEProgressionNpcRef clickedMaster,long calendarDay,net.minecraft.nbt.NBTTagCompound suppliedAssignmentData,java.util.Random random) {if(progression==null||progression.getCanonicalRank()!=KOMEProgressionRank.SERF)return reject("Canonical Serf rank is required.");return requestDuty(progression.getSerfKnightProgression(),clickedMaster,calendarDay,suppliedAssignmentData,random);}
    static Result requestDuty(KOMESerfKnightProgression state,KOMEProgressionNpcRef clickedMaster,long calendarDay,java.util.Random random) {return requestDuty(state,clickedMaster,calendarDay,null,random);}
    static Result requestDuty(KOMESerfKnightProgression state,KOMEProgressionNpcRef clickedMaster,long calendarDay,net.minecraft.nbt.NBTTagCompound suppliedAssignmentData,java.util.Random random) {
        if(state==null || clickedMaster==null || !clickedMaster.isSet()) return reject("A valid Serfdom Master is required.");
        if(!state.getSerfdomMaster().hasSameIdentity(clickedMaster)) return reject("You may only request duties from your current Serfdom Master.");
        KOMESerfKnightDutyType next=KOMESerfKnightService.nextDuty(state); if(next==null)return reject("All Serfdom duties are already complete.");
        if(!KOMESerfKnightService.mayIssueAssignment(state,calendarDay))return reject("You have already received a progression task today. Return later for another assignment.");
        net.minecraft.nbt.NBTTagCompound data=null;if(next==KOMESerfKnightDutyType.PROVISIONING)data=suppliedAssignmentData==null?KOMESerfProvisioningAssignment.generate(clickedMaster.factionKey,random).writeToNBT():(net.minecraft.nbt.NBTTagCompound)suppliedAssignmentData.copy();else if(next==KOMESerfKnightDutyType.PROFESSION&&suppliedAssignmentData!=null)data=(net.minecraft.nbt.NBTTagCompound)suppliedAssignmentData.copy();
        KOMESerfKnightService.Result result=KOMESerfKnightService.assignDuty(state,next,data,calendarDay);
        if(!result.success)return reject(result.reason);
        return ok();
    }
    public static void highlightMaster(EntityPlayerMP player,KOMEWorldData data) {
        KOMESerfKnightProgression state=data.getProgression(KOMEReflection.getEntityUUID(player)).getSerfKnightProgression();
        KOMEProgressionNpcRef master=state.getSerfdomMaster();
        if(!master.isSet()){player.addChatMessage(new ChatComponentText("You do not currently serve a Serfdom Master."));return;}
        Entity loaded=findLoaded(player,master.entityUuid);
        if(loaded!=null) KOMEPacketHandler.network.sendTo(new KOMEPacketLordHighlight(loaded.getEntityId(),master.displayName,loaded.posX,loaded.posY,loaded.posZ),player);
        else if(master.dimension==KOMEReflection.getWorld(player).provider.dimensionId) KOMEPacketHandler.network.sendTo(new KOMEPacketLordHighlight(-1,master.displayName,master.x,master.y,master.z),player);
        else {player.addChatMessage(new ChatComponentText("Your Serfdom Master is recorded in another dimension."));return;}
        player.addChatMessage(new ChatComponentText("Highlighted your Serfdom Master."));
    }
    private static Entity findLoaded(EntityPlayerMP player,String uuid){try{UUID id=UUID.fromString(uuid);World world=KOMEReflection.getWorld(player);for(Object value:world.loadedEntityList)if(value instanceof Entity&&id.equals(KOMEReflection.getEntityUUID((Entity)value)))return (Entity)value;}catch(Exception ignored){}return null;}
}
