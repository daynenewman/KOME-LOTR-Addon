package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

public class KOMEEntityHighlightOverlay {
    private static final long DURATION_MS = 30000L;
    private static int entityId = -1;
    private static String entityName = "";
    private static long expiresAt;

    public static void highlight(int id, String name) {
        entityId = id;
        entityName = name == null ? "Pledged lord" : name;
        expiresAt = System.currentTimeMillis() + DURATION_MS;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (entityId < 0 || System.currentTimeMillis() > expiresAt) {
            clear();
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            clear();
            return;
        }
        Entity entity = mc.theWorld.getEntityByID(entityId);
        if (entity == null || entity.isDead) {
            clear();
            return;
        }
        drawHighlight(entity);
    }

    private static void clear() {
        entityId = -1;
        entityName = "";
        expiresAt = 0L;
    }

    private void drawHighlight(Entity entity) {
        AxisAlignedBB box = entity.boundingBox.expand(0.35D, 0.35D, 0.35D).offset(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(3.0F);
        GL11.glColor4f(0.35F, 1.0F, 0.45F, 0.85F);
        drawBox(box);
        GL11.glLineWidth(1.0F);
        GL11.glColor4f(1.0F, 0.86F, 0.25F, 0.85F);
        drawBeacon(entity, box);
        GL11.glPopAttrib();
        RenderHelper.enableStandardItemLighting();
    }

    private void drawBox(AxisAlignedBB box) {
        GL11.glBegin(GL11.GL_LINES);
        vertex(box.minX, box.minY, box.minZ);
        vertex(box.maxX, box.minY, box.minZ);
        vertex(box.maxX, box.minY, box.minZ);
        vertex(box.maxX, box.minY, box.maxZ);
        vertex(box.maxX, box.minY, box.maxZ);
        vertex(box.minX, box.minY, box.maxZ);
        vertex(box.minX, box.minY, box.maxZ);
        vertex(box.minX, box.minY, box.minZ);

        vertex(box.minX, box.maxY, box.minZ);
        vertex(box.maxX, box.maxY, box.minZ);
        vertex(box.maxX, box.maxY, box.minZ);
        vertex(box.maxX, box.maxY, box.maxZ);
        vertex(box.maxX, box.maxY, box.maxZ);
        vertex(box.minX, box.maxY, box.maxZ);
        vertex(box.minX, box.maxY, box.maxZ);
        vertex(box.minX, box.maxY, box.minZ);

        vertex(box.minX, box.minY, box.minZ);
        vertex(box.minX, box.maxY, box.minZ);
        vertex(box.maxX, box.minY, box.minZ);
        vertex(box.maxX, box.maxY, box.minZ);
        vertex(box.maxX, box.minY, box.maxZ);
        vertex(box.maxX, box.maxY, box.maxZ);
        vertex(box.minX, box.minY, box.maxZ);
        vertex(box.minX, box.maxY, box.maxZ);
        GL11.glEnd();
    }

    private void drawBeacon(Entity entity, AxisAlignedBB box) {
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        double top = box.maxY + 0.2D;
        double height = Math.max(3.0D, entity.height + 2.5D);
        GL11.glBegin(GL11.GL_LINES);
        vertex(centerX, top, centerZ);
        vertex(centerX, top + height, centerZ);
        vertex(centerX - 0.35D, top + height - 0.35D, centerZ);
        vertex(centerX, top + height, centerZ);
        vertex(centerX + 0.35D, top + height - 0.35D, centerZ);
        vertex(centerX, top + height, centerZ);
        vertex(centerX, top + height - 0.35D, centerZ - 0.35D);
        vertex(centerX, top + height, centerZ);
        vertex(centerX, top + height - 0.35D, centerZ + 0.35D);
        vertex(centerX, top + height, centerZ);
        GL11.glEnd();
    }

    private static void vertex(double x, double y, double z) {
        GL11.glVertex3d(x, y, z);
    }
}
