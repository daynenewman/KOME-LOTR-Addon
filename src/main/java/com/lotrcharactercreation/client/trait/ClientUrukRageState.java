package com.lotrcharactercreation.client.trait;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundEventAccessorComposite;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;

import org.lwjgl.opengl.GL11;

import com.lotrcharactercreation.trait.UrukHaiTraitService;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

public final class ClientUrukRageState {

    static final ResourceLocation EMPOWERED_HIT_SOUND = new ResourceLocation(
        "lotrcharactercreation", "uruk.empowered_hit");
    private static final ResourceLocation VESSELS = new ResourceLocation(
        "lotrcharactercreation", "textures/gui/uruk_rage_vessels.png");
    private static final ResourceLocation VESSEL_SWELL = new ResourceLocation(
        "lotrcharactercreation", "textures/gui/uruk_rage_vessels_swell.png");
    private static final ClientUrukRageState INSTANCE = new ClientUrukRageState();

    private final UrukHeavyBlowFeedback feedback = new UrukHeavyBlowFeedback();
    private final UrukEmpoweredHitAudio audio = new UrukEmpoweredHitAudio();
    private boolean active;
    private float rage;
    private int visualTicks;

    private ClientUrukRageState() {}

    public static ClientUrukRageState getInstance() {
        return INSTANCE;
    }

    public void update(boolean newActive, float newRage) {
        active = newActive;
        rage = active ? MathHelper.clamp_float(newRage, 0.0F, UrukHaiTraitService.MAX_RAGE) : 0.0F;
        if (!active) {
            visualTicks = 0;
            feedback.clearTransient();
            stopActiveAudio(Minecraft.getMinecraft());
        }
    }

    public void confirmHeavyBlow(long activationId) {
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean alive = minecraft.theWorld != null && minecraft.thePlayer != null
            && minecraft.thePlayer.isEntityAlive();
        if (!feedback.accept(activationId, active, alive)) {
            return;
        }
        try {
            if (isSoundAvailable(minecraft, EMPOWERED_HIT_SOUND)) {
                PositionedSoundRecord cue = PositionedSoundRecord.func_147673_a(EMPOWERED_HIT_SOUND);
                minecraft.getSoundHandler().playSound(cue);
                audio.track(cue);
            }
        } catch (RuntimeException ignored) {
            // A malformed local sound replacement must not affect the confirmed visual cue.
        }
    }

    private static boolean isSoundAvailable(Minecraft minecraft, ResourceLocation event) {
        try {
            SoundEventAccessorComposite sound = minecraft.getSoundHandler().getSound(event);
            return sound != null && sound.func_148721_a() > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean alive = minecraft.theWorld != null && minecraft.thePlayer != null
            && minecraft.thePlayer.isEntityAlive();
        if (!alive) {
            visualTicks = 0;
            feedback.clearTransient();
            stopActiveAudio(minecraft);
            return;
        }
        if (audio.hasActive()) {
            audio.forgetFinished(minecraft.getSoundHandler()::isSoundPlaying);
        }
        if (active && !minecraft.isGamePaused()) {
            ++visualTicks;
            feedback.tick(true);
        }
    }

    @SubscribeEvent
    public void renderOverlay(RenderGameOverlayEvent.Post event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean alive = minecraft.thePlayer != null && minecraft.thePlayer.isEntityAlive();
        int flashTicks = feedback.getFlashTicksRemaining();
        if (event.type != RenderGameOverlayEvent.ElementType.ALL
            || !UrukRageHudModel.shouldRender(active, minecraft.theWorld != null, alive,
                minecraft.gameSettings.hideGUI, rage, flashTicks)) {
            return;
        }

        float visualTime = visualTicks + event.partialTicks;
        float intensity = UrukRageHudModel.intensity(rage, visualTime, flashTicks);
        float flash = UrukRageHudModel.flashBlend(flashTicks);
        float red = UrukRageHudModel.colorChannel(UrukRageHudModel.normalRed(rage), 1.0F, flash);
        float green = UrukRageHudModel.colorChannel(0.045F, 0.87F, flash);
        float blue = UrukRageHudModel.colorChannel(0.035F, 0.85F, flash);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT
            | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            int width = event.resolution.getScaledWidth();
            int height = event.resolution.getScaledHeight();
            GL11.glColor4f(red, green, blue, intensity);
            int frame = UrukRageHudModel.heartbeatFrame(rage, visualTime, flashTicks);
            if (frame == 0) {
                minecraft.getTextureManager().bindTexture(VESSELS);
                drawFullscreen(width, height, 0.0D, 0.0D, 1.0D, 1.0D);
            } else {
                minecraft.getTextureManager().bindTexture(VESSEL_SWELL);
                double tile = 1.0D / UrukRageHudModel.HEARTBEAT_ATLAS_COLUMNS;
                double u = (frame % UrukRageHudModel.HEARTBEAT_ATLAS_COLUMNS) * tile;
                double v = (frame / UrukRageHudModel.HEARTBEAT_ATLAS_COLUMNS) * tile;
                drawFullscreen(width, height, u, v, u + tile, v + tile);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    @SubscribeEvent
    public void worldUnloaded(WorldEvent.Unload event) {
        if (event.world.isRemote) {
            clearConnection();
        }
    }

    @SubscribeEvent
    public void connected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        clearConnection();
    }

    @SubscribeEvent
    public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        clearConnection();
    }

    private static void drawFullscreen(int width, int height, double u0, double v0, double u1, double v1) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(0, height, 0, u0, v1);
        tessellator.addVertexWithUV(width, height, 0, u1, v1);
        tessellator.addVertexWithUV(width, 0, 0, u1, v0);
        tessellator.addVertexWithUV(0, 0, 0, u0, v0);
        tessellator.draw();
    }

    private void clearConnection() {
        stopActiveAudio(Minecraft.getMinecraft());
        active = false;
        rage = 0.0F;
        visualTicks = 0;
        feedback.resetConnection();
    }

    private void stopActiveAudio(Minecraft minecraft) {
        if (audio.hasActive()) {
            audio.stopAll(minecraft.getSoundHandler()::stopSound);
        }
    }
}
