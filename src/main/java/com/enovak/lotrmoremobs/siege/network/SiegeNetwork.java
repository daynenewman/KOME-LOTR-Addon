package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.Main;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.relauncher.Side;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import com.enovak.lotrmoremobs.siege.ram.EntityBattleRam;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.player.EntityPlayerMP;

public final class SiegeNetwork {

    private SiegeNetwork() {
    }

    public static void register() {
        Main.network.registerMessage(
                GateCreationActionPacket.Handler.class,
                GateCreationActionPacket.class,
                4,
                Side.SERVER
        );
        Main.network.registerMessage(
                GateCreationSelectPacket.Handler.class,
                GateCreationSelectPacket.class,
                5,
                Side.SERVER
        );
        Main.network.registerMessage(
                GateCreationSyncPacket.Handler.class,
                GateCreationSyncPacket.class,
                6,
                Side.CLIENT
        );
        Main.network.registerMessage(
                GateStateSyncPacket.Handler.class,
                GateStateSyncPacket.class,
                7,
                Side.CLIENT
        );
        Main.network.registerMessage(
                GateHealthSyncPacket.Handler.class,
                GateHealthSyncPacket.class,
                8,
                Side.CLIENT
        );
        Main.network.registerMessage(
                GateRepairSyncPacket.Handler.class,
                GateRepairSyncPacket.class,
                9,
                Side.CLIENT
        );
        Main.network.registerMessage(
                GateManagementActionPacket.Handler.class,
                GateManagementActionPacket.class,
                10,
                Side.SERVER
        );
        Main.network.registerMessage(
                GateManagementOpenPacket.Handler.class,
                GateManagementOpenPacket.class,
                11,
                Side.CLIENT
        );
        Main.network.registerMessage(
                GateAccessSyncPacket.Handler.class,
                GateAccessSyncPacket.class,
                12,
                Side.CLIENT
        );
        Main.network.registerMessage(
                RamControlOpenPacket.Handler.class,
                RamControlOpenPacket.class,
                13,
                Side.CLIENT
        );
        Main.network.registerMessage(
                RamControlActionPacket.Handler.class,
                RamControlActionPacket.class,
                14,
                Side.SERVER
        );
        Main.network.registerMessage(
                RamTargetModePacket.Handler.class,
                RamTargetModePacket.class,
                15,
                Side.CLIENT
        );
        Main.network.registerMessage(
                RamTargetSelectPacket.Handler.class,
                RamTargetSelectPacket.class,
                16,
                Side.SERVER
        );
        Main.network.registerMessage(
                RamCrewAttachmentPacket.Handler.class,
                RamCrewAttachmentPacket.class,
                17,
                Side.CLIENT
        );
        Main.network.registerMessage(
                GateFinalizedInspectionSnapshotPacket.Handler.class,
                GateFinalizedInspectionSnapshotPacket.class,
                18,
                Side.CLIENT
        );
        Main.network.registerMessage(GateEditStartPacket.Handler.class, GateEditStartPacket.class, 19, Side.SERVER);
        Main.network.registerMessage(GateEditCancelPacket.Handler.class, GateEditCancelPacket.class, 20, Side.SERVER);
        Main.network.registerMessage(GateEditSessionStatusPacket.Handler.class, GateEditSessionStatusPacket.class, 21, Side.CLIENT);
        Main.network.registerMessage(GateEditDraftActionPacket.Handler.class, GateEditDraftActionPacket.class, 22, Side.SERVER);
        Main.network.registerMessage(GateEditDraftSnapshotPacket.Handler.class, GateEditDraftSnapshotPacket.class, 23, Side.CLIENT);
        Main.network.registerMessage(GateEditPreflightRequestPacket.Handler.class, GateEditPreflightRequestPacket.class, 24, Side.SERVER);
        Main.network.registerMessage(GateEditPreflightSnapshotPacket.Handler.class, GateEditPreflightSnapshotPacket.class, 25, Side.CLIENT);
        Main.network.registerMessage(GateEditCommitRequestPacket.Handler.class, GateEditCommitRequestPacket.class, 26, Side.SERVER);
        Main.network.registerMessage(GateEditCommitResultPacket.Handler.class, GateEditCommitResultPacket.class, 27, Side.CLIENT);
    }

    public static void syncRamCrewAttachment(
            EntityBattleRam ram,
            LOTREntityNPC crew,
            int slot,
            boolean attached
    ) {
        if (ram == null
                || crew == null
                || ram.worldObj == null
                || ram.worldObj.isRemote
                || crew.worldObj != ram.worldObj) {
            return;
        }
        Main.network.sendToAllAround(
                new RamCrewAttachmentPacket(ram, crew, slot, attached),
                new NetworkRegistry.TargetPoint(
                        ram.worldObj.provider.dimensionId,
                        ram.posX,
                        ram.posY,
                        ram.posZ,
                        128.0D
                )
        );
    }

    public static void syncRamCrewAttachmentTo(
            EntityPlayerMP player,
            EntityBattleRam ram,
            LOTREntityNPC crew,
            int slot,
            boolean attached
    ) {
        if (player == null
                || ram == null
                || crew == null
                || player.worldObj != ram.worldObj
                || crew.worldObj != ram.worldObj) {
            return;
        }
        Main.network.sendTo(
                new RamCrewAttachmentPacket(ram, crew, slot, attached),
                player
        );
    }

    public static void syncRamCrewDetachment(LOTREntityNPC crew) {
        if (crew == null
                || crew.worldObj == null
                || crew.worldObj.isRemote
                || !EntityBattleRam.hasRamCrewTag(crew)) {
            return;
        }
        Main.network.sendToAllAround(
                RamCrewAttachmentPacket.detached(crew),
                new NetworkRegistry.TargetPoint(
                        crew.worldObj.provider.dimensionId,
                        crew.posX,
                        crew.posY,
                        crew.posZ,
                        128.0D
                )
        );
    }

    public static void syncRamCrewDetachmentTo(
            EntityPlayerMP player,
            LOTREntityNPC crew
    ) {
        if (player == null
                || crew == null
                || player.worldObj != crew.worldObj
                || !EntityBattleRam.hasRamCrewTag(crew)) {
            return;
        }
        Main.network.sendTo(RamCrewAttachmentPacket.detached(crew), player);
    }

    public static void syncGateState(TileEntitySiegeGate controller) {
        if (controller == null
                || controller.getWorldObj() == null
                || controller.getWorldObj().isRemote) {
            return;
        }
        Main.network.sendToAllAround(
                new GateStateSyncPacket(
                        controller.getWorldObj().provider.dimensionId,
                        controller.xCoord,
                        controller.yCoord,
                        controller.zCoord,
                        controller.getGateState(),
                        controller.getGateStateStartTick()
                ),
                new NetworkRegistry.TargetPoint(
                        controller.getWorldObj().provider.dimensionId,
                        controller.xCoord + 0.5D,
                        controller.yCoord + 0.5D,
                        controller.zCoord + 0.5D,
                        256.0D
                )
        );
    }

    public static void syncGateHealth(TileEntitySiegeGate controller) {
        if (controller == null
                || controller.getWorldObj() == null
                || controller.getWorldObj().isRemote) {
            return;
        }
        Main.network.sendToAllAround(
                new GateHealthSyncPacket(
                        controller.getWorldObj().provider.dimensionId,
                        controller.xCoord,
                        controller.yCoord,
                        controller.zCoord,
                        controller.getCurrentHealth(),
                        controller.getMaxHealth()
                ),
                new NetworkRegistry.TargetPoint(
                        controller.getWorldObj().provider.dimensionId,
                        controller.xCoord + 0.5D,
                        controller.yCoord + 0.5D,
                        controller.zCoord + 0.5D,
                        256.0D
                )
        );
    }

    public static void syncGateRepair(TileEntitySiegeGate controller) {
        if (controller == null
                || controller.getWorldObj() == null
                || controller.getWorldObj().isRemote) {
            return;
        }
        Main.network.sendToAllAround(
                new GateRepairSyncPacket(
                        controller.getWorldObj().provider.dimensionId,
                        controller.xCoord,
                        controller.yCoord,
                        controller.zCoord,
                        controller.isRepairActive(),
                        controller.getRepairPurchasedHealth(),
                        controller.getRepairAppliedHealth(),
                        controller.getRepairActiveTicks(),
                        controller.getRepairPauseUntilTick(),
                        controller.getRepairPurchasedCoinValue()
                ),
                new NetworkRegistry.TargetPoint(
                        controller.getWorldObj().provider.dimensionId,
                        controller.xCoord + 0.5D,
                        controller.yCoord + 0.5D,
                        controller.zCoord + 0.5D,
                        256.0D
                )
        );
    }

    public static void syncGateAccess(TileEntitySiegeGate controller) {
        if (controller == null
                || controller.getWorldObj() == null
                || controller.getWorldObj().isRemote) {
            return;
        }
        Main.network.sendToAllAround(
                new GateAccessSyncPacket(controller),
                new NetworkRegistry.TargetPoint(
                        controller.getWorldObj().provider.dimensionId,
                        controller.xCoord + 0.5D,
                        controller.yCoord + 0.5D,
                        controller.zCoord + 0.5D,
                        256.0D
                )
        );
    }
}
