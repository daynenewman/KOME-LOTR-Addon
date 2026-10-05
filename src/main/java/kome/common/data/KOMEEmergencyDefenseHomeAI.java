package kome.common.data;

import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Vec3;

/** Returns an autonomous Emergency Defense NPC to its conflict-local battlefield. */
final class KOMEEmergencyDefenseHomeAI extends EntityAIBase {
    private static final double RETURN_SPEED = 1.15D;
    private final LOTREntityNPC npc;
    private double returnX;
    private double returnY;
    private double returnZ;

    private KOMEEmergencyDefenseHomeAI(LOTREntityNPC npc) {
        this.npc = npc;
        setMutexBits(3);
    }

    static void install(LOTREntityNPC npc) {
        if (npc == null || npc.tasks == null) return;
        for (Object raw : npc.tasks.taskEntries) {
            EntityAITasks.EntityAITaskEntry entry =
                (EntityAITasks.EntityAITaskEntry) raw;
            if (entry.action instanceof KOMEEmergencyDefenseHomeAI) return;
        }
        // Native attack AI normally owns priority 2. Priority 1 takes movement control only
        // after the defender crosses its home boundary, without making it stand still inside.
        npc.tasks.addTask(1, new KOMEEmergencyDefenseHomeAI(npc));
    }

    static boolean requiresReturn(LOTREntityNPC npc) {
        return npc != null && !npc.isWithinHomeDistanceCurrentPosition();
    }

    @Override public boolean shouldExecute() {
        if (!requiresReturn(npc)) return false;
        ChunkCoordinates home = npc.getHomePosition();
        Vec3 toward = RandomPositionGenerator.findRandomTargetBlockTowards(npc, 16, 7,
            Vec3.createVectorHelper(home.posX + 0.5D, home.posY, home.posZ + 0.5D));
        if (toward == null) {
            returnX = home.posX + 0.5D;
            returnY = home.posY;
            returnZ = home.posZ + 0.5D;
        } else {
            returnX = toward.xCoord;
            returnY = toward.yCoord;
            returnZ = toward.zCoord;
        }
        return true;
    }

    @Override public boolean continueExecuting() {
        return !npc.isWithinHomeDistanceCurrentPosition()
            && !npc.getNavigator().noPath();
    }

    @Override public void startExecuting() {
        // EntityAITarget limits new targets to the home area, but does not re-check an already
        // acquired target. Drop that target once it pulls the defender over the boundary.
        npc.setAttackTarget(null);
        npc.getNavigator().clearPathEntity();
        npc.getNavigator().tryMoveToXYZ(returnX, returnY, returnZ, RETURN_SPEED);
    }
}
