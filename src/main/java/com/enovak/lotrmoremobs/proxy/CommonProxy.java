package com.enovak.lotrmoremobs.proxy;

import com.enovak.lotrmoremobs.config.MumakilConfig;

import com.enovak.lotrmoremobs.handler.MumakilAchievementEventHandler;
import com.enovak.lotrmoremobs.handler.MumakilConquestUnitRollEventHandler;
import com.enovak.lotrmoremobs.handler.MumakilDriverControlEventHandler;
import com.enovak.lotrmoremobs.handler.MumakilEnemySightEventHandler;
import com.enovak.lotrmoremobs.handler.MumakilEquipmentEventHandler;
import com.enovak.lotrmoremobs.handler.MumakilFearEventHandler;
import com.enovak.lotrmoremobs.handler.MumakilFormationCreditEventHandler;
import com.enovak.lotrmoremobs.handler.MumakilHiredMountEventHandler;
import com.enovak.lotrmoremobs.handler.MumakilHomeUnitRollEventHandler;
import com.enovak.lotrmoremobs.handler.MumakilHowdahArcherEventHandler;
import com.enovak.lotrmoremobs.handler.MumakilInvasionProgressEventHandler;
import com.enovak.lotrmoremobs.handler.MumakilInvasionUnitRollEventHandler;
import com.enovak.lotrmoremobs.handler.MumakilMakeWayEventHandler;
import com.enovak.lotrmoremobs.handler.MumakilMeleeHitboxEventHandler;
import com.enovak.lotrmoremobs.handler.MumakilPlayerArrowOriginEventHandler;
import com.enovak.lotrmoremobs.handler.MumakilShankHeldEffectHandler;
import com.enovak.lotrmoremobs.spawning.MumakilFormationReplacementService;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraftforge.common.MinecraftForge;
import com.enovak.lotrmoremobs.handler.PickupFilterEventHandler;
import com.enovak.lotrmoremobs.handler.PickupFilterConnectionHandler;
import com.enovak.lotrmoremobs.handler.NpcBomberBlockDamageHandler;
import com.enovak.lotrmoremobs.handler.ServerGameplaySyncEventHandler;
import com.enovak.lotrmoremobs.siege.creation.GateCreationEventHandler;
import com.enovak.lotrmoremobs.siege.gate.SiegeGateLifecycleHandler;
import com.enovak.lotrmoremobs.network.PickupFilterSyncPacket;
import com.enovak.lotrmoremobs.network.ServerGameplaySyncPacket;
import com.enovak.lotrmoremobs.siege.network.GateCreationSyncPacket;
import com.enovak.lotrmoremobs.siege.network.GateAccessSyncPacket;
import com.enovak.lotrmoremobs.siege.network.GateHealthSyncPacket;
import com.enovak.lotrmoremobs.siege.network.GateManagementOpenPacket;
import com.enovak.lotrmoremobs.siege.network.GateFinalizedInspectionSnapshotPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditSessionStatusPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditDraftSnapshotPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditPreflightSnapshotPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditCommitResultPacket;
import com.enovak.lotrmoremobs.siege.network.GateRepairSyncPacket;
import com.enovak.lotrmoremobs.siege.network.GateStateSyncPacket;
import com.enovak.lotrmoremobs.siege.network.RamControlOpenPacket;
import com.enovak.lotrmoremobs.siege.network.RamCrewAttachmentPacket;
import com.enovak.lotrmoremobs.siege.network.RamTargetModePacket;
import com.enovak.lotrmoremobs.siege.repair.GateManagementEventHandler;
import com.enovak.lotrmoremobs.siege.management.GateInspectionSessionEventHandler;
import com.enovak.lotrmoremobs.siege.edit.GateEditSessionEventHandler;
import com.enovak.lotrmoremobs.siege.ram.RamControlEventHandler;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;

public class CommonProxy {
    public void registerRenderers() {
    }

    public void prepareMumakilHiredDriverGui(
            int driverEntityId,
            int mumakilEntityId
    ) {
    }

    public void handlePickupFilterSync(PickupFilterSyncPacket packet) {
    }

    public void handleServerGameplaySync(ServerGameplaySyncPacket packet) {
    }

    public void handleGateCreationSync(GateCreationSyncPacket packet) {
    }

    public void handleGateStateSync(GateStateSyncPacket packet) {
    }

    public void handleGateHealthSync(GateHealthSyncPacket packet) {
    }

    public void handleGateRepairSync(GateRepairSyncPacket packet) {
    }

    public void handleGateManagementOpen(GateManagementOpenPacket packet) {
    }

    public void handleGateFinalizedInspectionSnapshot(
            GateFinalizedInspectionSnapshotPacket packet
    ) {
    }
    public void handleGateEditSessionStatus(GateEditSessionStatusPacket packet) {
    }
    public void handleGateEditDraftSnapshot(GateEditDraftSnapshotPacket packet) {
    }
    public void handleGateEditPreflightSnapshot(GateEditPreflightSnapshotPacket packet) {
    }
    public void handleGateEditCommitResult(GateEditCommitResultPacket packet) {
    }

    public void handleGateAccessSync(GateAccessSyncPacket packet) {
    }

    public void handleRamControlOpen(RamControlOpenPacket packet) {
    }

    public void handleRamTargetMode(RamTargetModePacket packet) {
    }

    public void handleRamCrewAttachment(RamCrewAttachmentPacket packet) {
    }

    public void releaseGateRenderCache(TileEntitySiegeGate controller) {
    }

    public void registerEventHandlers() {
        FMLCommonHandler.instance().bus().register(
                new ServerGameplaySyncEventHandler()
        );
        MinecraftForge.EVENT_BUS.register(
                new NpcBomberBlockDamageHandler()
        );

        if (MumakilConfig.enableMumakil) {
            MinecraftForge.EVENT_BUS.register(new MumakilFearEventHandler());
            MinecraftForge.EVENT_BUS.register(new MumakilEquipmentEventHandler());
            MinecraftForge.EVENT_BUS.register(new MumakilHiredMountEventHandler());
            MinecraftForge.EVENT_BUS.register(new MumakilPlayerArrowOriginEventHandler());
            MinecraftForge.EVENT_BUS.register(new MumakilMeleeHitboxEventHandler());
            MinecraftForge.EVENT_BUS.register(new MumakilEnemySightEventHandler());
            MinecraftForge.EVENT_BUS.register(new MumakilDriverControlEventHandler());
            MinecraftForge.EVENT_BUS.register(new MumakilMakeWayEventHandler());
            MinecraftForge.EVENT_BUS.register(new MumakilFormationCreditEventHandler());
            MinecraftForge.EVENT_BUS.register(new MumakilInvasionProgressEventHandler());

            MumakilFormationReplacementService replacementService =
                    new MumakilFormationReplacementService();
            MinecraftForge.EVENT_BUS.register(replacementService);
            FMLCommonHandler.instance().bus().register(replacementService);
            MinecraftForge.EVENT_BUS.register(
                    new MumakilInvasionUnitRollEventHandler(replacementService)
            );
            MinecraftForge.EVENT_BUS.register(
                    new MumakilConquestUnitRollEventHandler(replacementService)
            );
            MinecraftForge.EVENT_BUS.register(
                    new MumakilHomeUnitRollEventHandler(replacementService)
            );

            MumakilAchievementEventHandler achievementHandler =
                    new MumakilAchievementEventHandler();
            MinecraftForge.EVENT_BUS.register(achievementHandler);
            FMLCommonHandler.instance().bus().register(achievementHandler);

            MumakilHowdahArcherEventHandler archerHandler =
                    new MumakilHowdahArcherEventHandler();
            MinecraftForge.EVENT_BUS.register(archerHandler);
            FMLCommonHandler.instance().bus().register(archerHandler);

            MumakilShankHeldEffectHandler shankHandler =
                    new MumakilShankHeldEffectHandler();
            FMLCommonHandler.instance().bus().register(shankHandler);
        }

        if (MumakilConfig.enableItemPickupFilter) {
            MinecraftForge.EVENT_BUS.register(new PickupFilterEventHandler());
            FMLCommonHandler.instance().bus().register(
                    new PickupFilterConnectionHandler()
            );
        }

        /*
         * Keep gate lifecycle bookkeeping active even when gameplay is disabled
         * so existing saved gates can still load/unload cleanly.
         */
        SiegeGateLifecycleHandler gateLifecycleHandler =
                new SiegeGateLifecycleHandler();
        MinecraftForge.EVENT_BUS.register(gateLifecycleHandler);
        FMLCommonHandler.instance().bus().register(gateLifecycleHandler);

        if (MumakilConfig.enableSiegeGates) {
            GateCreationEventHandler gateCreationHandler =
                    new GateCreationEventHandler();
            MinecraftForge.EVENT_BUS.register(gateCreationHandler);
            FMLCommonHandler.instance().bus().register(gateCreationHandler);
            FMLCommonHandler.instance().bus().register(
                    new GateManagementEventHandler()
            );
            FMLCommonHandler.instance().bus().register(
                    new GateInspectionSessionEventHandler()
            );
            FMLCommonHandler.instance().bus().register(
                    new GateEditSessionEventHandler()
            );
        }

        if (MumakilConfig.enableBattleRams) {
            RamControlEventHandler ramControlHandler =
                    new RamControlEventHandler();
            FMLCommonHandler.instance().bus().register(ramControlHandler);
            MinecraftForge.EVENT_BUS.register(ramControlHandler);
        }
    }

}
