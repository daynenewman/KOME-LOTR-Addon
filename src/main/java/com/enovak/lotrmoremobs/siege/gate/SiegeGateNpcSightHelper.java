package com.enovak.lotrmoremobs.siege.gate;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.siege.SiegeRegistry;
import com.enovak.lotrmoremobs.siege.ram.EntityBattleRam;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * Preserves vanilla sight ray behavior while marking only standard server-side
 * AI visibility rays for the GatePart collisionRayTrace hook.
 */
public final class SiegeGateNpcSightHelper {

    private SiegeGateNpcSightHelper() {
    }

    public static boolean canEntityBeSeen(
            Object observerObject,
            Object targetObject
    ) {
        if (!(observerObject instanceof EntityLiving)
                || !(targetObject instanceof Entity)) {
            return false;
        }

        EntityLiving observer = (EntityLiving)observerObject;
        Entity target = (Entity)targetObject;
        World world = observer.worldObj;
        if (!MumakilConfig.enableSiegeGates) {
            return world != null && rayTraceCanSee(observer, target);
        }
        if (world == null) {
            return false;
        }

        if (world.isRemote || observer instanceof EntityBattleRam) {
            return rayTraceCanSee(observer, target);
        }

        boolean entered = SiegeGateNpcSightContext.enter(observer);
        try {
            return rayTraceCanSee(observer, target);
        } finally {
            if (entered) {
                SiegeGateNpcSightContext.exit();
            }
        }
    }

    public static boolean shouldPassThroughGatePart(
            World world,
            int x,
            int y,
            int z
    ) {
        if (!MumakilConfig.enableSiegeGates) {
            return false;
        }
        return SiegeGateNpcSightContext.isActive()
                && isGatePartOpenToAi(world, x, y, z);
    }

    public static boolean isGatePartOpenToAi(
            World world,
            int x,
            int y,
            int z
    ) {
        if (!MumakilConfig.enableSiegeGates) {
            return false;
        }
        if (world == null
                || world.isRemote
                || !world.blockExists(x, y, z)
                || world.getBlock(x, y, z) != SiegeRegistry.gatePart) {
            return false;
        }

        SiegeGateOwnershipData.DurablePartOwner owner =
                GateRegistry.getDurablePartOwner(world, x, y, z);
        if (!isActiveOwner(owner)) {
            return false;
        }

        TileEntitySiegeGate controller =
                GateRegistry.getController(world, x, y, z);
        if (controller == null
                && world.blockExists(
                owner.getControllerX(),
                owner.getControllerY(),
                owner.getControllerZ()
        )) {
            TileEntity tileEntity = world.getTileEntity(
                    owner.getControllerX(),
                    owner.getControllerY(),
                    owner.getControllerZ()
            );
            if (tileEntity instanceof TileEntitySiegeGate) {
                controller = (TileEntitySiegeGate)tileEntity;
            }
        }
        if (controller == null) {
            return isNonclosed(owner.getLastGateState());
        }

        if (!controller.isFinalized()
                || controller.isGateStructureQuarantined()
                || !matchesController(owner, controller)) {
            return false;
        }
        return isNonclosed(controller.getGateState());
    }

    private static boolean rayTraceCanSee(
            EntityLiving observer,
            Entity target
    ) {
        Vec3 start = Vec3.createVectorHelper(
                observer.posX,
                observer.posY + observer.getEyeHeight(),
                observer.posZ
        );
        Vec3 end = Vec3.createVectorHelper(
                target.posX,
                target.posY + target.getEyeHeight(),
                target.posZ
        );
        return observer.worldObj.rayTraceBlocks(start, end) == null;
    }

    private static boolean isActiveOwner(
            SiegeGateOwnershipData.DurablePartOwner owner
    ) {
        return owner != null
                && owner.getGateUuid() != null
                && owner.getStatus()
                == SiegeGateOwnershipData.ControllerStatus.ACTIVE;
    }

    private static boolean matchesController(
            SiegeGateOwnershipData.DurablePartOwner owner,
            TileEntitySiegeGate controller
    ) {
        UUID gateUuid = controller.getGateUuid();
        return gateUuid != null
                && gateUuid.equals(owner.getGateUuid())
                && owner.getStructureRevision()
                == controller.getStructureRevision();
    }

    private static boolean isNonclosed(GateState state) {
        return state == GateState.OPENING
                || state == GateState.OPEN
                || state == GateState.CLOSING
                || state == GateState.BREACHED;
    }
}
