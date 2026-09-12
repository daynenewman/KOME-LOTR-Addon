package com.enovak.lotrmoremobs.proxy;

import com.enovak.lotrmoremobs.config.MumakilConfig;

import com.enovak.lotrmoremobs.client.MumakilShankFovHandler; // MUMAKIL_SHANK_SYSTEM_V1_1
import com.enovak.lotrmoremobs.client.MumakilHiredDriverGuiHandler;
import com.enovak.lotrmoremobs.client.MumakilInventoryKeyHandler;
import com.enovak.lotrmoremobs.client.UnitTradePledgeNavigationHandler;
import com.enovak.lotrmoremobs.client.config.MumakilConfigChangeHandler;
import com.enovak.lotrmoremobs.client.config.ClientServerGameplayState;
import com.enovak.lotrmoremobs.client.gui.MumakilHiredDriverGuiContext;
import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHirePreviewDriver;
import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHowdahArcher;
import com.enovak.lotrmoremobs.render.entity.LOTRRenderMumakilHowdahArcher;
import com.enovak.lotrmoremobs.render.entity.LOTRRenderMumakilHirePreviewDriver;
import com.enovak.lotrmoremobs.render.entity.LOTRRenderMumakilDriver;
import com.enovak.lotrmoremobs.render.entity.LOTRRenderMumakilGeoInventoryScaled;
import com.enovak.lotrmoremobs.render.tileentity.LOTRRenderMumakilSpawnCageContext;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import lotr.common.tileentity.LOTRTileEntityMobSpawner;
import lotr.common.entity.npc.LOTREntitySouthronChampion;
import net.minecraftforge.common.MinecraftForge;
import software.bernie.geckolib3.GeckoLib;
import com.enovak.lotrmoremobs.client.pickupfilter.ClientPickupFilterState;
import com.enovak.lotrmoremobs.network.PickupFilterSyncPacket;
import com.enovak.lotrmoremobs.network.ServerGameplaySyncPacket;
import com.enovak.lotrmoremobs.client.pickupfilter.PickupFilterGuiOpenHandler;
import com.enovak.lotrmoremobs.client.pickupfilter.PickupFilterInventoryButtonHandler;
import com.enovak.lotrmoremobs.siege.client.GateCreationClientHandler;
import com.enovak.lotrmoremobs.siege.client.GateEditClientInteractionHandler;
import com.enovak.lotrmoremobs.siege.client.GateAccessClientPacketHandler;
import com.enovak.lotrmoremobs.siege.client.GateCreationClientPacketHandler;
import com.enovak.lotrmoremobs.siege.client.GateCreationGuiOpenHandler;
import com.enovak.lotrmoremobs.siege.client.GateHealthClientPacketHandler;
import com.enovak.lotrmoremobs.siege.client.GateHealthHudHandler;
import com.enovak.lotrmoremobs.siege.client.GateManagementClientContext;
import com.enovak.lotrmoremobs.siege.client.GateFinalizedInspectionClientContext;
import com.enovak.lotrmoremobs.siege.client.GateEditClientContext;
import com.enovak.lotrmoremobs.siege.client.GateFinalizedInspectionClientHandler;
import com.enovak.lotrmoremobs.siege.client.GateRepairClientPacketHandler;
import com.enovak.lotrmoremobs.siege.client.RamControlClientContext;
import com.enovak.lotrmoremobs.siege.client.ClientRamTargetState;
import com.enovak.lotrmoremobs.siege.client.RamTargetClientHandler;
import com.enovak.lotrmoremobs.siege.client.RamCrewAttachmentClientHandler;
import com.enovak.lotrmoremobs.siege.client.RamDirectInteractionClientHandler;
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
import com.enovak.lotrmoremobs.siege.client.GateStateClientPacketHandler;
import com.enovak.lotrmoremobs.siege.client.render.RenderSiegeGate;
import com.enovak.lotrmoremobs.siege.client.render.RenderSiegeGatePartFallback;
import com.enovak.lotrmoremobs.siege.client.render.RenderBattleRam;
import com.enovak.lotrmoremobs.siege.block.BlockSiegeGatePart;
import com.enovak.lotrmoremobs.siege.ram.EntityBattleRam;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import net.minecraft.client.Minecraft;
import com.enovak.lotrmoremobs.siege.client.GateGuiHudSuppressor;

public class ClientProxy extends CommonProxy {

    private RenderSiegeGate siegeGateRenderer;

    @Override
    public void registerRenderers() {
        GeckoLib.initialize();

        // Uses the stable Geo renderer path, with a wrapper that only shrinks the inventory preview.
        LOTRRenderMumakilGeoInventoryScaled mumakilInventoryRenderer =
                new LOTRRenderMumakilGeoInventoryScaled();
        RenderingRegistry.registerEntityRenderingHandler(
                LOTREntityMumakil.class,
                mumakilInventoryRenderer
        );
        FMLCommonHandler.instance().bus().register(mumakilInventoryRenderer);
        MinecraftForge.EVENT_BUS.register(mumakilInventoryRenderer);
        ClientRegistry.bindTileEntitySpecialRenderer(
                LOTRTileEntityMobSpawner.class,
                new LOTRRenderMumakilSpawnCageContext()
        );
        siegeGateRenderer = new RenderSiegeGate();
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileEntitySiegeGate.class,
                siegeGateRenderer
        );
        int gatePartFallbackRenderId =
                RenderingRegistry.getNextAvailableRenderId();
        BlockSiegeGatePart.setFallbackRenderType(
                gatePartFallbackRenderId
        );
        RenderingRegistry.registerBlockHandler(
                new RenderSiegeGatePartFallback(
                        gatePartFallbackRenderId
                )
        );
        RenderingRegistry.registerEntityRenderingHandler(
                EntityBattleRam.class,
                new RenderBattleRam()
        );
        RenderingRegistry.registerEntityRenderingHandler(
                LOTREntityMumakilHirePreviewDriver.class,
                new LOTRRenderMumakilHirePreviewDriver()
        );
        RenderingRegistry.registerEntityRenderingHandler(
                LOTREntitySouthronChampion.class,
                new LOTRRenderMumakilDriver()
        );
        this.registerHowdahArcherRenderer();

        if (MumakilConfig.enableMumakil) {
            MumakilInventoryKeyHandler mumakilInventoryKeyHandler =
                    new MumakilInventoryKeyHandler();
            FMLCommonHandler.instance().bus().register(mumakilInventoryKeyHandler);
            MinecraftForge.EVENT_BUS.register(mumakilInventoryKeyHandler);
        }

    }

    @Override
    public void prepareMumakilHiredDriverGui(int driverEntityId, int mumakilEntityId) {
        MumakilHiredDriverGuiContext.begin(driverEntityId, mumakilEntityId);
    }

    @Override
    public void handlePickupFilterSync(final PickupFilterSyncPacket packet) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {
            @Override
            public void run() {
                ClientPickupFilterState.setExcludedItems(
                        packet.getExcludedItems()
                );
            }
        });
    }

    @Override
    public void handleServerGameplaySync(
            final ServerGameplaySyncPacket packet
    ) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {
            @Override
            public void run() {
                ClientServerGameplayState.setModernPlayerAnimations(
                        packet.isModernPlayerAnimations()
                );
            }
        });
    }

    @Override
    public void handleGateCreationSync(
            final GateCreationSyncPacket packet
    ) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {
            @Override
            public void run() {
                GateCreationClientPacketHandler.apply(packet);
            }
        });
    }

    @Override
    public void handleGateStateSync(final GateStateSyncPacket packet) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {
            @Override
            public void run() {
                GateStateClientPacketHandler.apply(packet);
            }
        });
    }

    @Override
    public void handleGateHealthSync(final GateHealthSyncPacket packet) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {
            @Override
            public void run() {
                GateHealthClientPacketHandler.apply(packet);
            }
        });
    }

    @Override
    public void handleGateRepairSync(final GateRepairSyncPacket packet) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {
            @Override
            public void run() {
                GateRepairClientPacketHandler.apply(packet);
            }
        });
    }

    @Override
    public void handleGateManagementOpen(
            final GateManagementOpenPacket packet
    ) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {
            @Override
            public void run() {
                GateManagementClientContext.open(packet);
            }
        });
    }

    @Override
    public void handleGateFinalizedInspectionSnapshot(
            final GateFinalizedInspectionSnapshotPacket packet
    ) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {
            @Override
            public void run() {
                GateFinalizedInspectionClientContext.apply(
                        packet.getSnapshot()
                );
            }
        });
    }
    @Override
    public void handleGateEditSessionStatus(final GateEditSessionStatusPacket packet) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() { @Override public void run() { GateEditClientContext.apply(packet); } });
    }
    @Override
    public void handleGateEditDraftSnapshot(final GateEditDraftSnapshotPacket packet) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() { @Override public void run() { GateEditClientContext.apply(packet); } });
    }
    @Override
    public void handleGateEditPreflightSnapshot(final GateEditPreflightSnapshotPacket packet) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() { @Override public void run() { GateEditClientContext.apply(packet); } });
    }
    @Override
    public void handleGateEditCommitResult(final GateEditCommitResultPacket packet) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() { @Override public void run() {
            GateEditClientContext.apply(packet);
            if (Minecraft.getMinecraft().thePlayer != null) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(editCommitMessage(packet)));
            }
            if (packet.getState() == com.enovak.lotrmoremobs.siege.edit.GateEditSessionManager.EditCommitAdmissionResult.State.PREPARED) {
                Minecraft.getMinecraft().displayGuiScreen(null);
            }
        } });
    }

    private static String editCommitMessage(GateEditCommitResultPacket packet) {
        switch (packet.getState()) {
            case PREPARED: return "Siege Gate edit committed. Applying changes...";
            case INVALID_SESSION: return "Edit session expired. Reopen the gate to edit it.";
            case STALE_DRAFT: return "The draft changed. Refresh and try again.";
            case NOT_READY: return "The gate is not ready to commit.";
            case MUTATION_IN_PROGRESS: return "Another gate mutation is already in progress.";
            case OWNERSHIP_CONFLICT: return "Gate ownership changed. Commit was refused.";
            case RESERVATION_CONFLICT: return "A required gate position is reserved.";
            case REVISION_OVERFLOW: return "The gate cannot create another structure revision.";
            case CAPACITY_REJECTED: return "Gate ownership storage capacity prevented the commit.";
            default: return "The gate edit could not be started.";
        }
    }

    @Override
    public void handleGateAccessSync(final GateAccessSyncPacket packet) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {
            @Override
            public void run() {
                GateAccessClientPacketHandler.apply(packet);
            }
        });
    }

    @Override
    public void handleRamControlOpen(final RamControlOpenPacket packet) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {
            @Override
            public void run() {
                RamControlClientContext.open(packet);
            }
        });
    }

    @Override
    public void handleRamTargetMode(final RamTargetModePacket packet) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {
            @Override
            public void run() {
                ClientRamTargetState.apply(packet);
            }
        });
    }

    @Override
    public void handleRamCrewAttachment(
            final RamCrewAttachmentPacket packet
    ) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {
            @Override
            public void run() {
                RamCrewAttachmentClientHandler.apply(packet);
            }
        });
    }

    @Override
    public void releaseGateRenderCache(TileEntitySiegeGate controller) {
        if (siegeGateRenderer != null) {
            siegeGateRenderer.release(controller);
        }
    }

    @Override
    public void registerEventHandlers() {
        super.registerEventHandlers();

        /* Config changes must always be observable from the Mods config GUI. */
        FMLCommonHandler.instance().bus().register(
                new MumakilConfigChangeHandler()
        );

        if (MumakilConfig.enableMumakil) {
            MinecraftForge.EVENT_BUS.register(new MumakilShankFovHandler());

            MumakilHiredDriverGuiHandler hiredDriverGuiHandler =
                    new MumakilHiredDriverGuiHandler();
            MinecraftForge.EVENT_BUS.register(hiredDriverGuiHandler);
            FMLCommonHandler.instance().bus().register(hiredDriverGuiHandler);
        }

        /*
         * Both custom Mumakil and Battle Ram unit trades can be pledge-only.
         * Keep their pledge-navigation helper independent of either master
         * feature switch.
         */
        if (MumakilConfig.enableMumakil
                || MumakilConfig.enableBattleRams) {
            MinecraftForge.EVENT_BUS.register(
                    new UnitTradePledgeNavigationHandler()
            );
        }

        if (MumakilConfig.enableItemPickupFilter) {
            FMLCommonHandler.instance().bus().register(
                    new PickupFilterGuiOpenHandler()
            );
            MinecraftForge.EVENT_BUS.register(
                    new PickupFilterInventoryButtonHandler()
            );
        }

        if (MumakilConfig.enableSiegeGates) {
            MinecraftForge.EVENT_BUS.register(new GateGuiHudSuppressor());
            MinecraftForge.EVENT_BUS.register(new GateHealthHudHandler());

            GateCreationClientHandler gateCreationHandler =
                    new GateCreationClientHandler();
            MinecraftForge.EVENT_BUS.register(gateCreationHandler);
            FMLCommonHandler.instance().bus().register(gateCreationHandler);

            GateEditClientInteractionHandler gateEditHandler =
                    new GateEditClientInteractionHandler();
            MinecraftForge.EVENT_BUS.register(gateEditHandler);
            FMLCommonHandler.instance().bus().register(
                    new GateCreationGuiOpenHandler()
            );
            MinecraftForge.EVENT_BUS.register(
                    new GateFinalizedInspectionClientHandler()
            );
        }

        if (MumakilConfig.enableBattleRams) {
            RamTargetClientHandler ramTargetHandler =
                    new RamTargetClientHandler();
            MinecraftForge.EVENT_BUS.register(ramTargetHandler);
            FMLCommonHandler.instance().bus().register(ramTargetHandler);

            RamCrewAttachmentClientHandler ramCrewHandler =
                    new RamCrewAttachmentClientHandler();
            MinecraftForge.EVENT_BUS.register(ramCrewHandler);
            FMLCommonHandler.instance().bus().register(ramCrewHandler);
            MinecraftForge.EVENT_BUS.register(
                    new RamDirectInteractionClientHandler()
            );
        }
    }

    private void registerHowdahArcherRenderer() {
        RenderingRegistry.registerEntityRenderingHandler(
                LOTREntityMumakilHowdahArcher.class,
                new LOTRRenderMumakilHowdahArcher()
        );
        System.out.println("[LOTRMoreMobs] Registered Mumak howdah archer renderer.");
    }
}
