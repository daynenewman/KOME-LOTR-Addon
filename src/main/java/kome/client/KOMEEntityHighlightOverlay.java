package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import kome.common.KOMEReflection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

public class KOMEEntityHighlightOverlay {
    private static String highlightedEntityID = "";
    private static String highlightedName = "";
    private static long highlightUntil;

    public static void highlight(String entityID, String name) {
        highlightedEntityID = entityID == null ? "" : entityID;
        highlightedName = name == null ? "" : name;
        highlightUntil = System.currentTimeMillis() + 120000L;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (highlightedEntityID.length() == 0 || System.currentTimeMillis() > highlightUntil) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            return;
        }
        Entity target = null;
        for (Object object : mc.theWorld.loadedEntityList) {
            if (object instanceof Entity && highlightedEntityID.equals(String.valueOf(KOMEReflection.getEntityUUID((Entity) object)))) {
                target = (Entity) object;
                break;
            }
        }
        if (target == null) {
            return;
        }
        double px = mc.renderViewEntity.lastTickPosX + (mc.renderViewEntity.posX - mc.renderViewEntity.lastTickPosX) * event.partialTicks;
        double py = mc.renderViewEntity.lastTickPosY + (mc.renderViewEntity.posY - mc.renderViewEntity.lastTickPosY) * event.partialTicks;
        double pz = mc.renderViewEntity.lastTickPosZ + (mc.renderViewEntity.posZ - mc.renderViewEntity.lastTickPosZ) * event.partialTicks;
        AxisAlignedBB box = target.boundingBox.expand(0.25D, 0.5D, 0.25D).getOffsetBoundingBox(-px, -py, -pz);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(3.0F);
        RenderGlobal.drawOutlinedBoundingBox(box, 0xFFFFD36A);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }
}
