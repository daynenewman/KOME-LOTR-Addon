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

    /** Failed trials retain their retry/leave UI, but no temporary physical roles. */
    static void failTrial(KOMEWorldData world,World liveWorld,KOMESerfKnightProgression state) {
        KOMESerfKnightTrialAssignment trial=state.getTrialAssignment();
        if(world==null||trial==null)return;
        state.updateTrialAssignment(trial.withStage(KOMESerfKnightTrialAssignment.Stage.FAILED,null));
        for(java.util.Map.Entry<UUID,KOMEPlayerProgression> row:world.progressions.entrySet()) {
            if(row.getValue().getSerfKnightProgression()!=state)continue;
            if(liveWorld!=null)cleanup(liveWorld,row.getKey(),row.getValue());
            KOMEProgressionNpcRoles.syncPlayer(world,row.getKey());
        }
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
