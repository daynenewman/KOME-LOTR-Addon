package kome.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import kome.common.data.KOMEProgressionVisualItems;
import kome.common.data.KOMEVisualMarker;
import lotr.common.LOTRLevelData;
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
    static final int GL_QUERY_FLOATS=16; // LWJGL 2 requires 16 even for GL_CURRENT_COLOR's four values.
    private static Method transformCoords, drawFancyRect;
    private static Field hasOverlay, mapXMin, mapXMax, mapYMin, mapYMax;
    private static boolean reflectionAttempted;
    private static Method nativeRelationshipRenderer, nativeSpeechDisplacement;
    private static boolean nativeRendererAttempted;
    private static boolean nativeRendererFailureReported;
    private static final ThreadLocal<ItemStack> RELATIONSHIP_ICON = new ThreadLocal<ItemStack>();

    private KOMEVisualRenderBridge() { }

    public static void renderRelationshipMarker(LOTREntityNPC npc, double x, double y, double z) {
        if(KOMEProgressionEnemyOutline.isRendering())return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (npc == null || minecraft.thePlayer == null || minecraft.renderViewEntity == null
                || !npc.isEntityAlive()) return;
        KOMEVisualMarker marker = overheadFor(npc.getUniqueID().toString(), minecraft.thePlayer.dimension);
        if(marker==null&&KOMELiegeQuestButtonOverlay.isStandingTrialAvailableFor(minecraft.thePlayer,npc))
            marker=new KOMEVisualMarker(KOMEVisualMarker.Role.COMMISSION,npc.getUniqueID().toString(),"","",minecraft.thePlayer.dimension,npc.posX,npc.posY,npc.posZ);
        if (marker == null) return;
        if (LOTRSpeechClient.hasSpeech(npc)) y += speechDisplacement(npc);
        invokeNativeRelationshipRenderer(npc, icon(marker), x, y, z);
    }

    static KOMEVisualMarker overheadFor(String entityUuid,int dimension) {
        KOMEVisualMarker relationship=null, courier=null, ruler=null,gift=null;
        for(KOMEVisualMarker marker:KOMEVisualMarkerClientState.markers())
            if((marker.actionable||marker.isRelationship())&&marker.dimension==dimension&&marker.entityUuid.equals(entityUuid)) {
                if(marker.role==KOMEVisualMarker.Role.MASTER_GIFT){gift=marker;continue;}
                if(marker.role==KOMEVisualMarker.Role.RULER)ruler=marker;
                if(marker.isRelationship()&&relationship==null)relationship=marker;
                if(marker.role==KOMEVisualMarker.Role.COURIER)courier=marker;
                if(marker.role==KOMEVisualMarker.Role.ESCORT||marker.role==KOMEVisualMarker.Role.DEFENSE||marker.role==KOMEVisualMarker.Role.COMMISSION)courier=marker;
            }
        return ruler!=null?ruler:relationship!=null?relationship:gift!=null?gift:courier;
    }

    /** Presentation only: never changes native quest data or another player's indicators. */
    public static boolean suppressNativeIndicator(LOTREntityNPC npc) {
        Minecraft mc=Minecraft.getMinecraft();
        if(KOMEProgressionEnemyOutline.isRendering())return true;
        if(npc==null||mc.thePlayer==null)return false;
        if(KOMEVisualMarkerClientState.relevant(npc.getUniqueID().toString(),mc.thePlayer.dimension))return true;
        return KOMELiegeQuestButtonOverlay.isStandingTrialAvailableFor(mc.thePlayer,npc);
    }

    static KOMEVisualMarker relationshipFor(String entityUuid, int dimension) {
        KOMEVisualMarker relationship=null;
        for (KOMEVisualMarker marker : KOMEVisualMarkerClientState.markers())
            if (marker.isRelationship() && marker.dimension == dimension
                    && marker.entityUuid.equals(entityUuid)) {
                if(marker.role==KOMEVisualMarker.Role.RULER)return marker;
                if(relationship==null)relationship=marker;
            }
        return relationship;
    }

    static float speechDisplacement(LOTREntityNPC npc) {
        if(!prepareNativeRenderer()||nativeSpeechDisplacement==null)return 0F;
        try{return ((Float)nativeSpeechDisplacement.invoke(null,npc)).floatValue();}catch(Throwable ignored){return 0F;}
    }

    public static ItemStack relationshipIconForNative() { return RELATIONSHIP_ICON.get(); }
    public static boolean nearRelationshipIndicator(boolean nativeNear){
        ItemStack stack=RELATIONSHIP_ICON.get();
        // A static pouch uses LOTR's native billboard path; its map silhouette stays readable nearby.
        return nativeNear&&(stack==null||!(stack.getItem() instanceof lotr.common.item.LOTRItemPouch));
    }
    public static net.minecraft.util.IIcon itemIcon(ItemStack stack) {
        return stack==null?null:stack.getItem().getIcon(stack,0);
    }
    /** Native overhead geometry/fading with the pouch's normal two texture/color passes. */
    public static void renderRelationshipIcon(RenderItem renderer,int x,int y,net.minecraft.util.IIcon icon,int width,int height){
        ItemStack stack=RELATIONSHIP_ICON.get();
        if(stack==null||!(stack.getItem() instanceof lotr.common.item.LOTRItemPouch)){renderer.renderIcon(x,y,icon,width,height);return;}
        java.nio.FloatBuffer color=org.lwjgl.BufferUtils.createFloatBuffer(GL_QUERY_FLOATS);GL11.glGetFloat(GL11.GL_CURRENT_COLOR,color);float alpha=color.get(3);
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT);
        try{for(int pass=0;pass<2;pass++){
            int rgb=stack.getItem().getColorFromItemStack(stack,pass);
            GL11.glColor4f(((rgb>>16)&255)/255F,((rgb>>8)&255)/255F,(rgb&255)/255F,alpha);
            renderer.renderIcon(x,y,stack.getItem().getIcon(stack,pass),width,height);
        }}finally{GL11.glPopAttrib();}
    }
    /** Close-up native indicators spin as extruded items, using the same two pouch passes. */
    public static void renderRelationshipItemIn2D(net.minecraft.client.renderer.Tessellator t,float maxU,float minV,float minU,float maxV,int width,int height,float thickness){
        ItemStack stack=RELATIONSHIP_ICON.get();
        if(stack==null||!(stack.getItem() instanceof lotr.common.item.LOTRItemPouch)){
            net.minecraft.client.renderer.ItemRenderer.renderItemIn2D(t,maxU,minV,minU,maxV,width,height,thickness);return;
        }
        java.nio.FloatBuffer color=org.lwjgl.BufferUtils.createFloatBuffer(GL_QUERY_FLOATS);GL11.glGetFloat(GL11.GL_CURRENT_COLOR,color);float alpha=color.get(3);
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT);
        try{for(int pass=0;pass<2;pass++){
            int rgb=stack.getItem().getColorFromItemStack(stack,pass);net.minecraft.util.IIcon icon=stack.getItem().getIcon(stack,pass);
            GL11.glColor4f(((rgb>>16)&255)/255F,((rgb>>8)&255)/255F,(rgb&255)/255F,alpha);
            net.minecraft.client.renderer.ItemRenderer.renderItemIn2D(t,icon.getMaxU(),icon.getMinV(),icon.getMinU(),icon.getMaxV(),icon.getIconWidth(),icon.getIconHeight(),thickness);
        }}finally{GL11.glPopAttrib();}
    }

    private static void invokeNativeRelationshipRenderer(LOTREntityNPC npc, ItemStack stack,
            double x, double y, double z) {
        if (stack == null || !prepareNativeRenderer()) return;
        int matrixMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        int matrixDepth=GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_TEXTURE_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        RELATIONSHIP_ICON.set(stack);
        try {
            nativeRelationshipRenderer.invoke(null, npc, x, y, z);
        } catch (Throwable failure) {
            if(!nativeRendererFailureReported){nativeRendererFailureReported=true;cpw.mods.fml.common.FMLLog.log(org.apache.logging.log4j.Level.ERROR,failure,"KOME native relationship indicator failed");}
        } finally {
            RELATIONSHIP_ICON.remove();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            while(GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH)>matrixDepth)GL11.glPopMatrix();
            GL11.glPopAttrib();GL11.glMatrixMode(matrixMode);
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
                if (marker.dimension != player.dimension||(marker.role==KOMEVisualMarker.Role.ENCOUNTER_ENEMY||marker.role==KOMEVisualMarker.Role.PARTICIPANT)) continue;
                float[] point = (float[])transformCoords.invoke(map, (float)marker.x, (float)marker.z);
                if(marker.role==KOMEVisualMarker.Role.RULER&&(point[0]<mapXMin.getInt(null)||point[0]>mapXMax.getInt(null)||point[1]<mapYMin.getInt(null)||point[1]>mapYMax.getInt(null)))continue;
                int x = Math.round(point[0]), y = Math.round(point[1]);
                int half = 5;
                x = Math.max(mapXMin.getInt(null) + half + 1,
                    Math.min(mapXMax.getInt(null) - half - 2, x));
                y = Math.max(mapYMin.getInt(null) + half + 1,
                    Math.min(mapYMax.getInt(null) - half - 2, y));
                if(isDestination(marker))drawDestinationShield(x,y);else drawMapItem(icon(marker), x, y);
                if(KOMEProgressionMapFocus.isFocused(marker)){
                    GuiScreen.drawRect(x-7,y-7,x+7,y-6,0xFFFFD15A);GuiScreen.drawRect(x-7,y+6,x+7,y+7,0xFFFFD15A);
                    GuiScreen.drawRect(x-7,y-7,x-6,y+7,0xFFFFD15A);GuiScreen.drawRect(x+6,y-7,x+7,y+7,0xFFFFD15A);
                }
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
        if(KOMEProgressionEnemyOutline.isRendering())return;
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

    static ItemStack icon(KOMEVisualMarker marker) {
        return icon(marker.role);
    }
    static boolean isDestination(KOMEVisualMarker marker){return marker.role==KOMEVisualMarker.Role.ESCORT&&marker.entityUuid.isEmpty();}
    static net.minecraft.util.ResourceLocation destinationShieldTexture(){return lotr.common.LOTRShields.ACHIEVEMENT_BRONZE.getTexture();}
    private static void drawDestinationShield(int x,int y){
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT|GL11.GL_ENABLE_BIT|GL11.GL_TEXTURE_BIT|GL11.GL_COLOR_BUFFER_BIT);
        try{
            GL11.glColor4f(1,1,1,1);GL11.glEnable(GL11.GL_BLEND);GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            Minecraft.getMinecraft().getTextureManager().bindTexture(destinationShieldTexture());
            net.minecraft.client.renderer.Tessellator t=net.minecraft.client.renderer.Tessellator.instance;
            // v36.15 packs front/back faces side by side. Sample the native front shield.
            t.startDrawingQuads();t.addVertexWithUV(x-5,y+5,0,0,1);t.addVertexWithUV(x+5,y+5,0,0.5,1);
            t.addVertexWithUV(x+5,y-5,0,0.5,0);t.addVertexWithUV(x-5,y-5,0,0,0);t.draw();
        }finally{GL11.glPopAttrib();}
    }
    static ItemStack icon(KOMEVisualMarker.Role role) {
        if(role==KOMEVisualMarker.Role.MASTER_GIFT&&lotr.common.LOTRMod.pouch!=null)return new ItemStack(lotr.common.LOTRMod.pouch,1,1);
        if (role == KOMEVisualMarker.Role.RULER)
            return new ItemStack(KOMEProgressionVisualItems.RULER);
        if (role == KOMEVisualMarker.Role.SERFDOM_MASTER
                || role == KOMEVisualMarker.Role.KNIGHT_LIEGE
                || role == KOMEVisualMarker.Role.LORD_LIEGE)
            return new ItemStack(KOMEProgressionVisualItems.RELATIONSHIP);
        if (role == KOMEVisualMarker.Role.COURIER) return new ItemStack(Items.paper);
        if (role == KOMEVisualMarker.Role.COMMISSION||role==KOMEVisualMarker.Role.DEFENSE) return new ItemStack(Items.iron_sword);
        if (role == KOMEVisualMarker.Role.ESCORT) return new ItemStack(Items.lead);
        return role == KOMEVisualMarker.Role.RECOVERY_SEARCH ? new ItemStack(Items.gold_ingot) : null;
    }

    private static RenderItem itemRenderer() { return ItemRendererHolder.INSTANCE; }

    private static void drawNativeMapHover(Object map, KOMEVisualMarker marker, int mouseX, int mouseY)
            throws Exception {
        if(KOMEProgressionEnemyOutline.isRendering())return;
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft.fontRenderer;
        int width = Math.max(font.getStringWidth(marker.title), font.getStringWidth(marker.subtitle)) + 6;
        int lineHeight = font.FONT_HEIGHT;
        int height = lineHeight * (marker.subtitle.isEmpty()?1:2) + 6;
        int x = Math.max(mapXMin.getInt(null) + 2,
            Math.min(mapXMax.getInt(null) - width - 2, mouseX - width / 2));
        int y = Math.max(mapYMin.getInt(null) + 2,
            Math.min(mapYMax.getInt(null) - height - 2, mouseY + 7));
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0F, 0.0F, 300.0F);
            drawFancyRect.invoke(map, x, y, x + width, y + height);
            font.drawString(marker.title, x + 3, y + 3, 0xFFFFFF);
            if(!marker.subtitle.isEmpty())font.drawString(marker.subtitle, x + 3, y + 3 + lineHeight, 0xFFFFFF);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static boolean prepareReflection(Class<?> mapClass) {
        if (reflectionAttempted) return transformCoords != null;
        reflectionAttempted = true;
        mapClass=lotr.client.gui.LOTRGuiMap.class;
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
