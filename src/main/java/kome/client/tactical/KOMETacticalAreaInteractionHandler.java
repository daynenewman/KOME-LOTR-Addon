package kome.client.tactical;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import kome.client.KOMEClientProxy;
import kome.client.gui.KOMEGuiTacticalAreaEditor;
import kome.common.siege.geometry.KOMEXZPoint;
import kome.common.tactical.KOMEForceDeploymentArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import java.util.List;

/** Client-only input suppression and transient prism lines. No marker blocks, gate state or world mutations. */
public final class KOMETacticalAreaInteractionHandler {
    private final KOMEClientProxy proxy;
    public KOMETacticalAreaInteractionHandler(KOMEClientProxy proxy) { this.proxy = proxy; }
    private KOMETacticalAreaEditor editor() { return proxy.getTacticalAreaEditor(); }
    private boolean selecting() {
        Minecraft mc = Minecraft.getMinecraft(); KOMETacticalAreaEditor e = editor();
        return e != null && mc.thePlayer != null && mc.currentScreen == null
            && e.consumesClicks(mc.thePlayer.getUniqueID(), mc.thePlayer.dimension);
    }
    @SubscribeEvent public void mouse(MouseEvent event) {
        if (!selecting() || (event.button != 0 && event.button != 1)) return;
        event.setCanceled(true); // Cancel before vanilla break/place/attack dispatch.
        if (event.button == 1 && event.buttonstate) {
            MovingObjectPosition hit = Minecraft.getMinecraft().objectMouseOver;
            if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                try { editor().worldPoint(hit.blockX, hit.blockY, hit.blockZ); }
                catch (RuntimeException invalid) { editor().error(invalid.getMessage()); }
            } else editor().error("Aim at a block; right-click its X/Z corner or Y level.");
        }
    }
    @SubscribeEvent public void interact(PlayerInteractEvent event) {
        if (event.world != null && event.world.isRemote && selecting()
                && event.entityPlayer.getUniqueID().equals(Minecraft.getMinecraft().thePlayer.getUniqueID())) event.setCanceled(true);
    }
    @SubscribeEvent public void keys(InputEvent.KeyInputEvent event) {
        if (!selecting() || !Keyboard.getEventKeyState()) return;
        try {
            int key = Keyboard.getEventKey();
            if (key == Keyboard.KEY_BACK) editor().undo();
            else if (key == Keyboard.KEY_DELETE) editor().clearVertices();
            else if (key == Keyboard.KEY_RETURN) returnToScreen();
        } catch (RuntimeException invalid) { editor().error(invalid.getMessage()); }
    }
    private void returnToScreen() {
        editor().select(KOMETacticalAreaEditor.Selection.NONE);
        Minecraft.getMinecraft().displayGuiScreen(new KOMEGuiTacticalAreaEditor(editor()));
    }
    @SubscribeEvent public void gui(GuiOpenEvent event) {
        if (editor() != null && editor().isEditing() && editor().getSelection() != KOMETacticalAreaEditor.Selection.NONE
                && event.gui instanceof GuiIngameMenu) {
            editor().select(KOMETacticalAreaEditor.Selection.NONE);
            event.gui = new KOMEGuiTacticalAreaEditor(editor());
        }
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START && selecting()) {
            Minecraft mc = Minecraft.getMinecraft();
            // MouseEvent cancels fresh clicks; clear held/buffered attack/use bindings as well.
            if (mc.gameSettings != null) {
                net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                while (mc.gameSettings.keyBindAttack.isPressed()) { }
                while (mc.gameSettings.keyBindUseItem.isPressed()) { }
            }
        }
        if (event.phase != TickEvent.Phase.END || editor() == null || !editor().isEditing()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.isDead || editor().overlay(mc.thePlayer.getUniqueID(), mc.thePlayer.dimension) == null) editor().reset();
        else if (editor().getSelection() == KOMETacticalAreaEditor.Selection.NONE && !(mc.currentScreen instanceof KOMEGuiTacticalAreaEditor)) editor().cancel();
    }
    @SubscribeEvent public void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft(); KOMETacticalAreaEditor e = editor();
        if (e == null || mc.thePlayer == null || mc.renderViewEntity == null) return;
        KOMEForceDeploymentArea area = e.overlay(mc.thePlayer.getUniqueID(), mc.thePlayer.dimension);
        if (area == null) return;
        Entity camera = mc.renderViewEntity;
        double cx = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * event.partialTicks;
        double cy = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * event.partialTicks;
        double cz = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * event.partialTicks;
        List<KOMEXZPoint> points = area.getPrism().getPolygon().getVertices();
        double low = area.getPrism().getMinYInclusive(), high = area.getPrism().getMaxYExclusive();
        GL11.glPushMatrix(); GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glTranslated(-cx, -cy, -cz); GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(false); GL11.glLineWidth(2F);
            Tessellator t = Tessellator.instance; t.startDrawing(GL11.GL_LINES); t.setColorOpaque_F(0.2F, 0.85F, 1F);
            for (int i = 0; i < points.size(); i++) {
                KOMEXZPoint p = points.get(i), next = points.get((i + 1) % points.size());
                line(t, p.getX(), low, p.getZ(), next.getX(), low, next.getZ());
                line(t, p.getX(), high, p.getZ(), next.getX(), high, next.getZ());
                line(t, p.getX(), low, p.getZ(), p.getX(), high, p.getZ());
            }
            for (int i = 0; i < points.size(); i++) {
                KOMEXZPoint p = points.get(i);
                if (i == 0) t.setColorOpaque_F(0.2F, 1F, 0.2F);
                else if (i == points.size() - 1) t.setColorOpaque_F(1F, 0.8F, 0.2F);
                else t.setColorOpaque_F(0.2F, 0.85F, 1F);
                line(t, p.getX() - 0.3, low, p.getZ(), p.getX() + 0.3, low, p.getZ());
                line(t, p.getX(), low, p.getZ() - 0.3, p.getX(), low, p.getZ() + 0.3);
            }
            MovingObjectPosition hit = mc.objectMouseOver;
            if (selecting() && hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                t.setColorOpaque_F(1F, 0.2F, 1F);
                line(t, hit.blockX, low, hit.blockZ, hit.blockX, high, hit.blockZ);
                line(t, hit.blockX - 0.4, hit.blockY + 1, hit.blockZ, hit.blockX + 0.4, hit.blockY + 1, hit.blockZ);
            }
            t.draw();
        } finally { GL11.glPopAttrib(); GL11.glPopMatrix(); }
    }
    private static void line(Tessellator t, double x, double y, double z, double xx, double yy, double zz) {
        t.addVertex(x, y, z); t.addVertex(xx, yy, zz);
    }
    @SubscribeEvent public void hud(RenderGameOverlayEvent.Text event) {
        if (!selecting()) return;
        KOMEForceDeploymentArea area = editor().getDraft();
        event.left.add("Tactical area " + area.getAreaId() + " | " + (editor().getSelection() == KOMETacticalAreaEditor.Selection.VERTICES
            ? "Polygon corners" : editor().getSelection() == KOMETacticalAreaEditor.Selection.LOWER_Y ? "Bottom Y" : "Top block Y"));
        event.left.add("Right-click block corner/Y | Backspace undo | Delete clear | Enter/Esc editor");
        event.left.add("Next vertex " + (area.getPrism().getPolygon().getVertices().size() + 1)
            + " | Y [" + area.getPrism().getMinYInclusive() + ", " + area.getPrism().getMaxYExclusive() + ")");
        if (!editor().getMessage().isEmpty()) event.left.add(editor().getMessage());
    }
}
