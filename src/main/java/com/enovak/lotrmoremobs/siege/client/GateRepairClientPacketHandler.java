package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.siege.network.GateRepairSyncPacket;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;

public final class GateRepairClientPacketHandler {

    private GateRepairClientPacketHandler() {
    }

    public static void apply(GateRepairSyncPacket packet) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null
                || minecraft.theWorld.provider.dimensionId
                != packet.getDimensionId()) {
            return;
        }
        TileEntity tileEntity = minecraft.theWorld.getTileEntity(
                packet.getX(),
                packet.getY(),
                packet.getZ()
        );
        if (tileEntity instanceof TileEntitySiegeGate) {
            ((TileEntitySiegeGate)tileEntity).applySynchronizedGateRepair(
                    packet.isActive(),
                    packet.getPurchasedHealth(),
                    packet.getAppliedHealth(),
                    packet.getActiveTicks(),
                    packet.getPauseUntilTick(),
                    packet.getPurchasedCoinValue()
            );
        }
    }
}
