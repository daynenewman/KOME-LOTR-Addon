package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.siege.network.GateCreationSyncPacket;

public final class GateCreationClientPacketHandler {

    private GateCreationClientPacketHandler() {
    }

    public static void apply(GateCreationSyncPacket packet) {
        if (packet.getOperation() == GateCreationSyncPacket.START) {
            ClientGateCreationState.start(
                    packet.getDimensionId(),
                    packet.getControllerPosition(),
                    packet.getActiveLeaf(),
                    packet.getPositions(),
                    packet.getLeaves(),
                    packet.getSelectionMode(),
                    packet.getOpeningDirection(),
                    packet.isBorderTextureEnabled(),
                    packet.getLeftHingePosition(),
                    packet.getRightHingePosition()
            );
            if (packet.shouldOpenControls()) {
                GateCreationGuiOpenHandler.requestOpen();
            }
        } else if (packet.getOperation()
                == GateCreationSyncPacket.CONFIGURATION) {
            ClientGateCreationState.applyConfiguration(
                    packet.getActiveLeaf(),
                    packet.getSelectionMode(),
                    packet.getOpeningDirection(),
                    packet.isBorderTextureEnabled(),
                    packet.getLeftHingePosition(),
                    packet.getRightHingePosition()
            );
        } else if (packet.getOperation()
                == GateCreationSyncPacket.PART_UPDATE
                && packet.getChangedPosition() != null) {
            ClientGateCreationState.updateSelection(
                    packet.getChangedPosition(),
                    packet.getChangedLeaf()
            );
        } else if (packet.getOperation() == GateCreationSyncPacket.END) {
            ClientGateCreationState.clear();
            GateCreationGuiOpenHandler.requestClose();
        }
    }
}
