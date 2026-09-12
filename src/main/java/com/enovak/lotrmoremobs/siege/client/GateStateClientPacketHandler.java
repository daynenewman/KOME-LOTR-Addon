package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.siege.network.GateStateSyncPacket;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;

public final class GateStateClientPacketHandler {

    private GateStateClientPacketHandler() {
    }

    public static void apply(GateStateSyncPacket packet) {
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
            ((TileEntitySiegeGate)tileEntity).applySynchronizedGateState(
                    packet.getGateState(),
                    packet.getGateStateStartTick()
            );
        }
    }
}
