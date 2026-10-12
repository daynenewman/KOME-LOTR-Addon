package kome.common.data;

import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

/**
 * Cleans physical encounter state before canonical progression state is invalidated.
 * Unloaded marked entities remain safe because their existing load reconcilers still
 * compare themselves against canonical state when they naturally return.
 */
public final class KOMEProgressionEncounterCleanup {
    private KOMEProgressionEncounterCleanup() {
    }
    /** No actor/item became playable: release partial issuance and retain the same trial for retry. */
    static void activationFailed(KOMEWorldData world,World liveWorld,KOMESerfKnightProgression state){
        KOMESerfKnightTrialAssignment trial=state.getTrialAssignment();if(world==null||trial==null)return;
        for(java.util.Map.Entry<UUID,KOMEPlayerProgression> row:world.progressions.entrySet())if(row.getValue().getSerfKnightProgression()==state){
            KOMESerfKnightEscortService.cleanup(liveWorld,row.getKey(),trial);
            KOMESerfKnightRecoveryService.cleanup(liveWorld,row.getKey(),trial);
            KOMESerfKnightDefenseService.cleanup(liveWorld,row.getKey(),trial);
        }
        state.retryUnfinishedTrial();
        net.minecraft.nbt.NBTTagCompound data=(net.minecraft.nbt.NBTTagCompound)state.getTrialAssignment().data.copy();
        long previous=trial.data.hasKey("PreviousTrialDay",99)?trial.data.getLong("PreviousTrialDay"):-1L;
        data.setLong("PreviousTrialDay",previous);data.setBoolean("ActivationPending",true);
        data.setLong("ActivationRetryAt",liveWorld==null?0:liveWorld.getTotalWorldTime()+100);
        state.updateTrialAssignment(trial.withStage(KOMESerfKnightTrialAssignment.Stage.ASSIGNED,data));
        state.setLastTrialAssignmentEpochDay(previous);
        for(java.util.Map.Entry<UUID,KOMEPlayerProgression> row:world.progressions.entrySet())if(row.getValue().getSerfKnightProgression()==state)KOMEProgressionNpcRoles.syncPlayer(world,row.getKey());
        world.markDirty();
    }
    static void activationSucceeded(KOMESerfKnightProgression state){
        KOMESerfKnightTrialAssignment trial=state.getTrialAssignment();
        if(trial==null||trial.stage!=KOMESerfKnightTrialAssignment.Stage.ACTIVE)return;
        state.setLastTrialAssignmentEpochDay(KOMESerfKnightService.calendarDayNow());
        net.minecraft.nbt.NBTTagCompound data=(net.minecraft.nbt.NBTTagCompound)trial.data.copy();
        data.removeTag("ActivationPending");data.removeTag("ActivationRetryAt");
        state.updateTrialAssignment(trial.withStage(trial.stage,data));
    }

    /** Failed trials retain their retry/leave UI, but no temporary physical roles. */
    static void failTrial(KOMEWorldData world,World liveWorld,KOMESerfKnightProgression state) {
        KOMESerfKnightTrialAssignment trial=state.getTrialAssignment();
        if(world==null||trial==null)return;
        state.updateTrialAssignment(trial.withStage(KOMESerfKnightTrialAssignment.Stage.FAILED,null));
        UUID failedOwner=null;
        for(java.util.Map.Entry<UUID,KOMEPlayerProgression> row:world.progressions.entrySet()) {
            if(row.getValue().getSerfKnightProgression()!=state)continue;
            if(liveWorld!=null)cleanup(liveWorld,row.getKey(),row.getValue());
            failedOwner=row.getKey();
            if(liveWorld!=null){net.minecraft.entity.Entity liege=KOMEKnightCommissionService.findLoaded(liveWorld,trial.liege.entityUuid);net.minecraft.entity.player.EntityPlayer player=liveWorld.func_152378_a(row.getKey());if(liege instanceof lotr.common.entity.npc.LOTREntityNPC&&player instanceof EntityPlayerMP&&player.getDistanceSqToEntity(liege)<=64)KOMEProgressionNpcSpeech.say((EntityPlayerMP)player,(lotr.common.entity.npc.LOTREntityNPC)liege,"Your trial has failed. I release you from this service. Seek another lord, and reflect on what went wrong.");}
            state.releaseFailedLiege(row.getKey(),trial,KOMESerfKnightProgression.failureClock.getAsLong());
            KOMEProgressionNpcRoles.syncPlayer(world,row.getKey());
            if(liveWorld!=null&&kome.common.network.KOMEPacketHandler.network!=null){net.minecraft.entity.player.EntityPlayer live=liveWorld.func_152378_a(row.getKey());if(live instanceof EntityPlayerMP)KOMEProgressionAutoCompleter.syncPlayer((EntityPlayerMP)live,row.getValue());}
        }
        if(failedOwner==null)state.releaseFailedLiege(null,trial,KOMESerfKnightProgression.failureClock.getAsLong());
        world.markDirty();
    }

    public static void cleanup(EntityPlayerMP player,KOMEPlayerProgression progression) {
        if(player==null||progression==null)return;
        cleanup(player.worldObj,player.getUniqueID(),progression);
    }

    public static void cleanup(World world,UUID owner,KOMEPlayerProgression progression) {
        if(world==null||owner==null||progression==null)return;

        KOMELordshipTrialService.relationshipLost(world,owner,progression);
        KOMEKnightCommissionService.cancel(world,owner,progression);
        KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        KOMESerfKnightTrialAssignment trial=state.getTrialAssignment();

        KOMESerfKnightEscortService.cleanup(world,owner,trial);
        KOMESerfKnightRecoveryService.cleanup(world,owner,trial);
        KOMESerfKnightDefenseService.cleanup(world,owner,trial);

        KOMESerfCourierAssignment courier=null;
        if("courier".equals(state.getActiveAssignmentKind())) {
            courier=KOMESerfCourierAssignment.readFromNBT(
                state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
            if(courier!=null)KOMECourierService.cleanup(
                world,
                courier,
                owner,
                state.getSerfdomMaster());
        }

        EntityPlayer live=world.func_152378_a(owner);
        if(live instanceof EntityPlayerMP) {
            EntityPlayerMP player=(EntityPlayerMP)live;
            if(KOMESerfKnightRecoveryService.cleanupInventory(
                    player.inventory.mainInventory,trial,owner)>0) {
                player.inventoryContainer.detectAndSendChanges();
            }
            if(courier!=null) {
                KOMECourierService.cleanupInventory(player,courier,state.getSerfdomMaster());
            }
        }
    }
}
