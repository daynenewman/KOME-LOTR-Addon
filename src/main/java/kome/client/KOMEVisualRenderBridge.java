package kome.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import kome.common.data.KOMEVisualMarker;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRMod;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.client.LOTRSpeechClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/** Rendering adapter invoked from LOTR's native overhead and main-map passes. */
public final class KOMEVisualRenderBridge {
    private static Method transformCoords, drawFancyRect;
    private static Field hasOverlay, mapXMin, mapXMax, mapYMin, mapYMax;
    private static boolean reflectionAttempted;
    private static Method nativeRelationshipRenderer, nativeSpeechDisplacement;
    private static boolean nativeRendererAttempted;
    private static final ThreadLocal<ItemStack> RELATIONSHIP_ICON = new ThreadLocal<ItemStack>();

    private KOMEVisualRenderBridge() { }

    public static void renderRelationshipMarker(LOTREntityNPC npc, double x, double y, double z) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (npc == null || minecraft.thePlayer == null || minecraft.renderViewEntity == null
                || !npc.isEntityAlive()) return;
        KOMEVisualMarker marker = overheadFor(npc.getUniqueID().toString(), minecraft.thePlayer.dimension);
        if (marker == null) return;
        boolean nativeIndicator = npc.questInfo != null && (npc.questInfo.clientIsOffering
            || !LOTRLevelData.getData(minecraft.thePlayer).getMiniQuestsForEntity(npc, true).isEmpty());
        if (nativeIndicator) {
            double angle = Math.toRadians(RenderManager.instance.playerViewY);
            x += Math.cos(angle) * 0.62D;
            z += Math.sin(angle) * 0.62D;
        }
        if (LOTRSpeechClient.hasSpeech(npc)) y += speechDisplacement(npc);
        invokeNativeRelationshipRenderer(npc, icon(marker.role), x, y, z);
    }

    static KOMEVisualMarker overheadFor(String entityUuid,int dimension) {
        KOMEVisualMarker courier=null;
        for(KOMEVisualMarker marker:KOMEVisualMarkerClientState.markers())
            if(marker.dimension==dimension&&marker.entityUuid.equals(entityUuid)) {
                if(marker.isRelationship())return marker;
                if(marker.role==KOMEVisualMarker.Role.COURIER)courier=marker;
            }
        return courier;
    }

    static KOMEVisualMarker relationshipFor(String entityUuid, int dimension) {
        for (KOMEVisualMarker marker : KOMEVisualMarkerClientState.markers())
            if (marker.isRelationship() && marker.dimension == dimension
                    && marker.entityUuid.equals(entityUuid)) return marker;
        return null;
    }

    static float speechDisplacement(LOTREntityNPC npc) {
        if(!prepareNativeRenderer()||nativeSpeechDisplacement==null)return 0F;
        try{return ((Float)nativeSpeechDisplacement.invoke(null,npc)).floatValue();}catch(Throwable ignored){return 0F;}
    }

    public static ItemStack relationshipIconForNative() { return RELATIONSHIP_ICON.get(); }

    private static void invokeNativeRelationshipRenderer(LOTREntityNPC npc, ItemStack stack,
            double x, double y, double z) {
        if (stack == null || !prepareNativeRenderer()) return;
        RELATIONSHIP_ICON.set(stack);
        try {
            nativeRelationshipRenderer.invoke(null, npc, x, y, z);
        } catch (Throwable ignored) {
            // The unmodified native quest indicator remains safe if another coremod changes this class.
        } finally {
            RELATIONSHIP_ICON.remove();
        }
    }

    private static boolean prepareNativeRenderer() {
        if (nativeRendererAttempted) return nativeRelationshipRenderer != null;
        nativeRendererAttempted = true;
        try {
            nativeRelationshipRenderer = Class.forName("lotr.client.render.entity.LOTRNPCRendering")
                .getDeclaredMethod("kome$renderRelationshipIcon", LOTREntityNPC.class,
                    double.class, double.class, double.class);
            nativeRelationshipRenderer.setAccessible(true);
            nativeSpeechDisplacement = Class.forName("lotr.client.render.entity.LOTRNPCRendering")
                .getDeclaredMethod("calcSpeechDisplacement", LOTREntityNPC.class);
            nativeSpeechDisplacement.setAccessible(true);
        } catch (Throwable ignored) { nativeRelationshipRenderer = null; }
        return nativeRelationshipRenderer != null;
    }

    public static void renderMapMarkers(Object map, EntityPlayer player, int mouseX, int mouseY) {
        if (!(map instanceof GuiScreen) || player == null || !prepareReflection(map.getClass())) return;
        try {
            if (hasOverlay.getBoolean(map)) return;
            KOMEVisualMarker hovered = null;
            double hoveredDistance = Double.MAX_VALUE;
            for (KOMEVisualMarker marker : KOMEVisualMarkerClientState.markers()) {
                if (marker.dimension != player.dimension) continue;
                float[] point = (float[])transformCoords.invoke(map, (float)marker.x, (float)marker.z);
                int x = Math.round(point[0]), y = Math.round(point[1]);
                int half = 5;
                x = Math.max(mapXMin.getInt(null) + half + 1,
                    Math.min(mapXMax.getInt(null) - half - 2, x));
                y = Math.max(mapYMin.getInt(null) + half + 1,
                    Math.min(mapYMax.getInt(null) - half - 2, y));
                drawMapItem(icon(marker.role), x, y);
                double dx = x - mouseX, dy = y - mouseY, distance = Math.sqrt(dx * dx + dy * dy);
                if (distance <= 7.0D && distance < hoveredDistance) {
                    hovered = marker;
                    hoveredDistance = distance;
                }
            }
            if (hovered != null) {
                drawNativeMapHover(map, hovered, mouseX, mouseY);
            }
        } catch (Throwable failure) {
            // Native map rendering remains available if another coremod changes its private layout.
        }
    }

    private static void drawMapItem(ItemStack stack, int x, int y) {
        if (stack == null) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        GL11.glPushMatrix();
        GL11.glScalef(0.5F, 0.5F, 0.5F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_CULL_FACE);
        itemRenderer().renderItemAndEffectIntoGUI(minecraft.fontRenderer, minecraft.getTextureManager(),
            stack, x * 2 - 8, y * 2 - 8);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glPopMatrix();
    }

    static ItemStack icon(KOMEVisualMarker.Role role) {
        if (role == KOMEVisualMarker.Role.SERFDOM_MASTER) return new ItemStack(Items.iron_hoe);
        if (role == KOMEVisualMarker.Role.KNIGHT_LIEGE) return new ItemStack(Items.iron_sword);
        if (role == KOMEVisualMarker.Role.LORD_LIEGE) return new ItemStack(LOTRMod.commandHorn);
        return role == KOMEVisualMarker.Role.COURIER ? new ItemStack(Items.paper) : null;
    }

    private static RenderItem itemRenderer() { return ItemRendererHolder.INSTANCE; }

    private static void drawNativeMapHover(Object map, KOMEVisualMarker marker, int mouseX, int mouseY)
            throws Exception {
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft.fontRenderer;
        int width = Math.max(font.getStringWidth(marker.title), font.getStringWidth(marker.subtitle)) + 6;
        int lineHeight = font.FONT_HEIGHT;
        int height = lineHeight * 2 + 6;
        int x = Math.max(mapXMin.getInt(null) + 2,
            Math.min(mapXMax.getInt(null) - width - 2, mouseX - width / 2));
        int y = Math.max(mapYMin.getInt(null) + 2,
            Math.min(mapYMax.getInt(null) - height - 2, mouseY + 7));
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0F, 0.0F, 300.0F);
            drawFancyRect.invoke(map, x, y, x + width, y + height);
            font.drawString(marker.title, x + 3, y + 3, 0xFFFFFF);
            font.drawString(marker.subtitle, x + 3, y + 3 + lineHeight, 0xFFFFFF);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static boolean prepareReflection(Class<?> mapClass) {
        if (reflectionAttempted) return transformCoords != null;
        reflectionAttempted = true;
        try {
            transformCoords = mapClass.getDeclaredMethod("transformCoords", float.class, float.class);
            transformCoords.setAccessible(true);
            hasOverlay = mapClass.getDeclaredField("hasOverlay"); hasOverlay.setAccessible(true);
            mapXMin = mapClass.getDeclaredField("mapXMin"); mapXMin.setAccessible(true);
            mapXMax = mapClass.getDeclaredField("mapXMax"); mapXMax.setAccessible(true);
            mapYMin = mapClass.getDeclaredField("mapYMin"); mapYMin.setAccessible(true);
            mapYMax = mapClass.getDeclaredField("mapYMax"); mapYMax.setAccessible(true);
            drawFancyRect = mapClass.getDeclaredMethod("drawFancyRect",
                int.class, int.class, int.class, int.class);
            drawFancyRect.setAccessible(true);
            return true;
        } catch (Throwable failure) {
            transformCoords = null;
            return false;
        }
    }

    private static final class ItemRendererHolder {
        private static final RenderItem INSTANCE = new RenderItem();
    }
}
