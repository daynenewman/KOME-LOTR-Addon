package com.lotrcharactercreation.client.body;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;

import com.lotrcharactercreation.body.PlayerRaceEyeService;
import com.lotrcharactercreation.client.appearance.ClientPlayerAppearanceCache;
import com.lotrcharactercreation.client.appearance.ClientPlayerAppearanceCache.SynchronizedPlayerAppearance;
import com.lotrcharactercreation.race.PlayerRace;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ClientPlayerEyeCameraService {

    private static final ClientPlayerEyeCameraService INSTANCE = new ClientPlayerEyeCameraService();

    private EntityPlayerSP renderAdjustedPlayer;
    private float savedRenderYOffset;
    private boolean renderYOffsetAdjusted;

    private ClientPlayerEyeCameraService() {}

    public static ClientPlayerEyeCameraService getInstance() {
        return INSTANCE;
    }

    @SubscribeEvent
    public void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            restoreRenderYOffset();
            applyCurrentLocalEyeHeight();
            applyRenderCameraYOffset();
        } else {
            restoreRenderYOffset();
        }
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        restoreRenderYOffset();
        applyCurrentLocalEyeHeight();
    }

    @SubscribeEvent
    public void connected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        resetLocalState();
    }

    @SubscribeEvent
    public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        resetLocalState();
    }

    public double getLocalPlayerRenderYOffsetCompensation(EntityPlayer player) {
        if (!renderYOffsetAdjusted || player != renderAdjustedPlayer) {
            return 0.0D;
        }

        return (double) (player.yOffset - savedRenderYOffset);
    }

    private void applyCurrentLocalEyeHeight() {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || !player.isEntityAlive()) {
            return;
        }

        SynchronizedPlayerAppearance appearance = ClientPlayerAppearanceCache.getInstance()
            .get(player);
        PlayerRace race = appearance == null ? null : appearance.getRace();
        PlayerRaceEyeService.applyLocalEyeHeight(player, race);
    }

    private void applyRenderCameraYOffset() {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || !player.isEntityAlive() || minecraft.renderViewEntity != player) {
            return;
        }

        SynchronizedPlayerAppearance appearance = ClientPlayerAppearanceCache.getInstance()
            .get(player);
        PlayerRace race = appearance == null ? null : appearance.getRace();
        if (!PlayerRaceEyeService.hasPrototypeEyeHeight(race)) {
            return;
        }

        renderAdjustedPlayer = player;
        savedRenderYOffset = player.yOffset;
        renderYOffsetAdjusted = true;
        player.yOffset = PlayerRaceEyeService.getLocalRenderCameraYOffset(race);
    }

    private void restoreRenderYOffset() {
        if (!renderYOffsetAdjusted) {
            return;
        }

        renderAdjustedPlayer.yOffset = savedRenderYOffset;
        renderAdjustedPlayer = null;
        renderYOffsetAdjusted = false;
    }

    private void resetLocalState() {
        restoreRenderYOffset();

        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null && player.isEntityAlive()) {
            player.eyeHeight = player.getDefaultEyeHeight();
        }
    }
}
