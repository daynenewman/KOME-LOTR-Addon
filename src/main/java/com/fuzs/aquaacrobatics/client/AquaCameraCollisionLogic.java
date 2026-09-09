package com.fuzs.aquaacrobatics.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * Third-person camera collision policy for legacy 1.7.10.
 */
public final class AquaCameraCollisionLogic {

    private static final double RAY_EPSILON = 0.01D;
    private static final int MAX_SKIPPED_HITS = 64;

    private AquaCameraCollisionLogic() {}

    /**
     * Vanilla 1.7.10 camera ray tracing treats many selection-only blocks as
     * obstructions. That includes vegetation a player can physically walk through.
     * Keep tracing through those hits and stop only at a block that contributes a
     * real collision box to entity movement.
     */
    /**
     * EntityRenderer's legacy bytecode invokes World#rayTraceBlocks through its
     * WorldClient-typed field, so the ASM bridge descriptor can name WorldClient
     * even though the implementation only needs World. Keep this exact overload
     * so the transformed invokevirtual -> invokestatic call resolves in dev and
     * reobfuscated runtime environments.
     */
    public static MovingObjectPosition rayTraceCameraBlocks(
        net.minecraft.client.multiplayer.WorldClient world, Vec3 start, Vec3 end) {
        return rayTraceCameraBlocks((World) world, start, end);
    }

    public static MovingObjectPosition rayTraceCameraBlocks(World world, Vec3 start, Vec3 end) {
        if (world == null || start == null || end == null) return null;

        final double startX = start.xCoord;
        final double startY = start.yCoord;
        final double startZ = start.zCoord;
        final double dx = end.xCoord - startX;
        final double dy = end.yCoord - startY;
        final double dz = end.zCoord - startZ;
        final double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-7D) return null;

        final double unitX = dx / length;
        final double unitY = dy / length;
        final double unitZ = dz / length;
        double progress = 0.0D;

        for (int skipped = 0; skipped < MAX_SKIPPED_HITS && progress < length; ++skipped) {
            Vec3 currentStart = Vec3.createVectorHelper(
                startX + unitX * progress,
                startY + unitY * progress,
                startZ + unitZ * progress);
            Vec3 currentEnd = Vec3.createVectorHelper(end.xCoord, end.yCoord, end.zCoord);

            // Use vanilla's normal camera-selection ray first. We then distinguish
            // actual movement collision from selection-only vegetation ourselves.
            MovingObjectPosition hit = world.rayTraceBlocks(currentStart, currentEnd);
            if (hit == null || hit.hitVec == null) return null;

            if (isCameraBlockingBlock(world, hit.blockX, hit.blockY, hit.blockZ)) {
                return hit;
            }

            double hitProgress = (hit.hitVec.xCoord - startX) * unitX
                + (hit.hitVec.yCoord - startY) * unitY
                + (hit.hitVec.zCoord - startZ) * unitZ;
            progress = Math.max(progress + RAY_EPSILON, hitProgress + RAY_EPSILON);
        }

        return null;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static boolean isCameraBlockingBlock(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        if (block == null) return false;

        AxisAlignedBB blockMask = AxisAlignedBB.getBoundingBox(
            (double) x, (double) y, (double) z,
            (double) x + 1.0D, (double) y + 1.0D, (double) z + 1.0D);
        List collisionBoxes = new ArrayList();
        block.addCollisionBoxesToList(world, x, y, z, blockMask, collisionBoxes, null);
        return !collisionBoxes.isEmpty();
    }
}
