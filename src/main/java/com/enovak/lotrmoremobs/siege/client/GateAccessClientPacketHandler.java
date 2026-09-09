package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.siege.network.GateAccessSyncPacket;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;

public final class GateAccessClientPacketHandler {

    private GateAccessClientPacketHandler() {
    }

    public static void apply(
            GateAccessSyncPacket packet
    ) {
        Minecraft minecraft =
                Minecraft.getMinecraft();

        if (minecraft.theWorld == null
                || minecraft.theWorld.provider.dimensionId
                != packet.getDimensionId()) {

            return;
        }

        TileEntity tileEntity =
                minecraft.theWorld.getTileEntity(
                        packet.getX(),
                        packet.getY(),
                        packet.getZ()
                );

        if (tileEntity
                instanceof TileEntitySiegeGate) {

            TileEntitySiegeGate gate =
                    (TileEntitySiegeGate)tileEntity;

            gate.applySynchronizedAccessState(
                    packet.getGateName(),
                    packet.getOwnerUuid(),
                    packet.getFactionName(),
                    packet.getRequiredAlignment(),
                    packet.isFactionAccessEnabled(),
                    packet.getGateControlMode(),
                    packet.getEditors(),
                    packet.getOperators(),
                    packet.getWhitelist()
            );

            gate.applySynchronizedRamReservation(
                    packet.getReservedRamUuid()
            );
        }

        GateManagementClientContext.updateAccessNames(
                packet.getDimensionId(),
                packet.getX(),
                packet.getY(),
                packet.getZ(),
                packet.getAccessNames()
        );
    }
}