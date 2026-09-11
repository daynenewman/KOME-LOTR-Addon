package com.lotrcharactercreation.client.render;

import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;

import org.lwjgl.opengl.GL11;

import com.lotrcharactercreation.appearance.AppearancePreset;
import com.lotrcharactercreation.appearance.AppearancePresetRegistry;
import com.lotrcharactercreation.appearance.AppearanceSourceType;
import com.lotrcharactercreation.client.appearance.ClientAppearanceTextureResolver;
import com.lotrcharactercreation.client.appearance.ClientLocalAppearancePresetCatalog;
import com.lotrcharactercreation.client.appearance.ClientMinecraftAccountSkinResolver;
import com.lotrcharactercreation.client.appearance.ClientPlayerAppearanceCache;
import com.lotrcharactercreation.client.appearance.ClientPlayerAppearanceCache.SynchronizedPlayerAppearance;
import com.lotrcharactercreation.client.render.LOTRMapReflectionAccessor.MapSnapshot;
import com.lotrcharactercreation.client.render.LOTRMapReflectionAccessor.PlayerLocation;
import com.lotrcharactercreation.client.render.LOTRMapReflectionAccessor.ScreenPosition;
import com.lotrcharactercreation.race.PlayerRace;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.gui.LOTRGuiMap;

@SideOnly(Side.CLIENT)
public final class LOTRMapPlayerAppearanceHandler {

    private static final double ICON_HALF_WIDTH = 4.0D;
    private static final LOTRMapReflectionAccessor MAP_ACCESS = LOTRMapReflectionAccessor.getInstance();

    @SubscribeEvent
    public void afterMapDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof LOTRGuiMap) || !MAP_ACCESS.isAvailable()) {
            return;
        }

        LOTRGuiMap mapGui = (LOTRGuiMap) event.gui;
        MapSnapshot snapshot = MAP_ACCESS.capture(mapGui);
        if (snapshot == null) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            prepareGuiRenderState();
            for (PlayerLocation location : snapshot.getPlayerLocations()
                .values()) {
                renderRemoteReplacementIcon(
                    mapGui,
                    snapshot,
                    location.getProfile(),
                    location.getPosX(),
                    location.getPosZ());
            }

            Minecraft minecraft = Minecraft.getMinecraft();
            if (snapshot.isMiddleEarth() && minecraft.thePlayer != null) {
                renderLocalReplacementIcon(
                    mapGui,
                    snapshot,
                    minecraft.thePlayer);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void prepareGuiRenderState() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderRemoteReplacementIcon(LOTRGuiMap mapGui, MapSnapshot snapshot, GameProfile profile,
        double worldX, double worldZ) {
        if (profile == null || profile.getId() == null) {
            return;
        }

        SynchronizedPlayerAppearance appearance = ClientPlayerAppearanceCache.getInstance()
            .get(profile.getId());
        renderReplacementIcon(mapGui, snapshot, profile, appearance, worldX, worldZ);
    }

    private static void renderLocalReplacementIcon(LOTRGuiMap mapGui, MapSnapshot snapshot,
        AbstractClientPlayer player) {
        SynchronizedPlayerAppearance appearance = ClientPlayerAppearanceCache.getInstance()
            .get(player);
        renderReplacementIcon(mapGui, snapshot, player.getGameProfile(), appearance, player.posX, player.posZ);
    }

    private static void renderReplacementIcon(LOTRGuiMap mapGui, MapSnapshot snapshot, GameProfile profile,
        SynchronizedPlayerAppearance appearance, double worldX, double worldZ) {
        if (appearance == null || appearance.getAppearancePresetId() == null) {
            return;
        }

        String presetId = appearance.getAppearancePresetId();
        AppearancePreset preset = ClientLocalAppearancePresetCatalog.get().findById(presetId);
        if (preset == null
            || !AppearancePresetRegistry.isPresetValid(
                ClientLocalAppearancePresetCatalog.get(), appearance.getRace(), appearance.getSex(), presetId)) {
            return;
        }

        ResourceLocation replacement;
        HeadLayout layout;
        if (preset.getSourceType() == AppearanceSourceType.MINECRAFT_ACCOUNT) {
            if (appearance.getRace() != PlayerRace.MAN || isLocalProfile(profile)) {
                return;
            }
            replacement = ClientMinecraftAccountSkinResolver.getInstance()
                .resolve(profile);
            layout = HeadLayout.LEGACY_64_BY_32;
        } else {
            ResourceLocation profileSkin = appearance.getRace() == PlayerRace.MAN ? resolveProfileSkin(profile) : null;
            replacement = ClientAppearanceTextureResolver
                .resolveWithProfileSkinFallback(appearance.getRace(), appearance.getSex(), presetId, profileSkin);
            if (appearance.getRace() == PlayerRace.MAN && replacement != null && replacement.equals(profileSkin)) {
                return;
            }
            layout = HeadLayout.forRace(appearance.getRace());
        }

        ScreenPosition position = MAP_ACCESS.transformPlayerPosition(mapGui, snapshot, worldX, worldZ);
        if (replacement == null || layout == null || position == null) {
            return;
        }

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(replacement);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        drawLayer(
            position.getX(),
            position.getY(),
            ICON_HALF_WIDTH,
            layout.baseMinU,
            layout.baseMinV,
            layout.baseMaxU,
            layout.baseMaxV);
        drawLayer(
            position.getX(),
            position.getY(),
            ICON_HALF_WIDTH + 0.5D,
            layout.overlayMinU,
            layout.overlayMinV,
            layout.overlayMaxU,
            layout.overlayMaxV);
    }

    private static ResourceLocation resolveProfileSkin(GameProfile profile) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer != null && profile.getId()
            .equals(minecraft.thePlayer.getUniqueID())) {
            return minecraft.thePlayer.getLocationSkin();
        }

        ResourceLocation skin = AbstractClientPlayer.locationStevePng;
        Map<Type, MinecraftProfileTexture> profileTextures = minecraft.func_152342_ad()
            .func_152788_a(profile);
        MinecraftProfileTexture profileSkin = profileTextures.get(Type.SKIN);
        if (profileSkin != null) {
            skin = minecraft.func_152342_ad()
                .func_152792_a(profileSkin, Type.SKIN);
        }
        return skin;
    }

    private static boolean isLocalProfile(GameProfile profile) {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft.thePlayer != null && profile.getId()
            .equals(minecraft.thePlayer.getUniqueID());
    }

    private static void drawLayer(double playerX, double playerY, double halfWidth, double minU, double minV,
        double maxU, double maxV) {
        double centerX = playerX + 0.5D;
        double centerY = playerY + 0.5D;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(centerX - halfWidth, centerY + halfWidth, 0.0D, minU, maxV);
        tessellator.addVertexWithUV(centerX + halfWidth, centerY + halfWidth, 0.0D, maxU, maxV);
        tessellator.addVertexWithUV(centerX + halfWidth, centerY - halfWidth, 0.0D, maxU, minV);
        tessellator.addVertexWithUV(centerX - halfWidth, centerY - halfWidth, 0.0D, minU, minV);
        tessellator.draw();
    }

    private enum HeadLayout {

        LEGACY_64_BY_32(0.125D, 0.25D, 0.25D, 0.5D, 0.625D, 0.25D, 0.75D, 0.5D),
        LOTR_NPC_64_BY_64(0.125D, 0.125D, 0.25D, 0.25D, 0.125D, 0.625D, 0.25D, 0.75D);

        private final double baseMinU;
        private final double baseMinV;
        private final double baseMaxU;
        private final double baseMaxV;
        private final double overlayMinU;
        private final double overlayMinV;
        private final double overlayMaxU;
        private final double overlayMaxV;

        HeadLayout(double baseMinU, double baseMinV, double baseMaxU, double baseMaxV, double overlayMinU,
            double overlayMinV, double overlayMaxU, double overlayMaxV) {
            this.baseMinU = baseMinU;
            this.baseMinV = baseMinV;
            this.baseMaxU = baseMaxU;
            this.baseMaxV = baseMaxV;
            this.overlayMinU = overlayMinU;
            this.overlayMinV = overlayMinV;
            this.overlayMaxU = overlayMaxU;
            this.overlayMaxV = overlayMaxV;
        }

        private static HeadLayout forRace(PlayerRace race) {
            if (race == PlayerRace.ORC || race == PlayerRace.URUK_HAI) {
                return LEGACY_64_BY_32;
            }
            if (race == PlayerRace.MAN || race == PlayerRace.ELF
                || race == PlayerRace.DWARF
                || race == PlayerRace.HOBBIT) {
                return LOTR_NPC_64_BY_64;
            }
            return null;
        }
    }
}
