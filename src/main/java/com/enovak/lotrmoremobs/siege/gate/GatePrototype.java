package com.enovak.lotrmoremobs.siege.gate;

import com.enovak.lotrmoremobs.siege.SiegeRegistry;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.World;

public final class GatePrototype {

    private static final List<GatePartData> PROTOTYPE_PARTS =
            Collections.unmodifiableList(Arrays.asList(
                    new GatePartData(1, 0, 0, GateLeaf.LEFT),
                    new GatePartData(1, 1, 0, GateLeaf.LEFT),
                    new GatePartData(1, 2, 0, GateLeaf.LEFT),
                    new GatePartData(2, 0, 0, GateLeaf.RIGHT),
                    new GatePartData(2, 1, 0, GateLeaf.RIGHT),
                    new GatePartData(2, 2, 0, GateLeaf.RIGHT)
            ));

    private GatePrototype() {
    }

    public static boolean migrateLegacyPrototype(
            TileEntitySiegeGate controller
    ) {
        if (!canUsePrototype(controller)
                || controller.hasGateStructureData()
                || !controller.getGateParts().isEmpty()) {
            return false;
        }

        World world = controller.getWorldObj();
        for (GatePartData part : PROTOTYPE_PARTS) {
            int partX = part.getAbsoluteX(controller.xCoord);
            int partY = part.getAbsoluteY(controller.yCoord);
            int partZ = part.getAbsoluteZ(controller.zCoord);
            TileEntitySiegeGate linkedController =
                    GateRegistry.getController(
                            world,
                            partX,
                            partY,
                            partZ
                    );
            if (!part.hasValidAbsolutePosition(
                    controller.xCoord,
                    controller.yCoord,
                    controller.zCoord
            ) || !world.blockExists(
                    partX,
                    partY,
                    partZ
            ) || world.getBlock(
                    partX,
                    partY,
                    partZ
            ) != SiegeRegistry.gatePart
                    || (linkedController != null
                    && linkedController != controller)) {
                return false;
            }
        }

        return controller.setGateParts(PROTOTYPE_PARTS);
    }

    private static boolean canUsePrototype(
            TileEntitySiegeGate controller
    ) {
        return controller != null
                && controller.getWorldObj() != null
                && !controller.getWorldObj().isRemote
                && SiegeRegistry.gatePart != null;
    }

}
