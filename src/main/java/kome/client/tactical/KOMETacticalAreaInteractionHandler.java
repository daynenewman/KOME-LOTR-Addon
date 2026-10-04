package kome.client.tactical;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import kome.client.KOMEClientProxy;
import kome.client.gui.KOMEGuiTacticalAreaEditor;
import kome.common.siege.geometry.KOMEXZPoint;
import kome.common.siege.geometry.KOMEPolygonPrism;
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
    private boolean tabHeld;
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
            } else editor().error("Aim at a block; Tab chooses its corner, right-click confirms the vertex or Y level.");
        }
    }
    @SubscribeEvent public void interact(PlayerInteractEvent event) {
        if (event.world != null && event.world.isRemote && selecting()
                && event.entityPlayer.getUniqueID().equals(Minecraft.getMinecraft().thePlayer.getUniqueID())) event.setCanceled(true);
    }
    @SubscribeEvent public void keys(InputEvent.KeyInputEvent event) {
        try {
            handleKey(Keyboard.getEventKey(), Keyboard.getEventKeyState(), Keyboard.isRepeatEvent());
        } catch (RuntimeException invalid) { editor().error(invalid.getMessage()); }
    }
    private boolean selectingVertices() { return selecting() && editor().getSelection() == KOMETacticalAreaEditor.Selection.VERTICES; }
    /** The real FML callback delegates here; one physical Tab press changes only the corner. */
    public boolean handleKey(int key, boolean pressed, boolean repeat) {
        if (key == Keyboard.KEY_TAB) {
            if (!selectingVertices()) { tabHeld = false; return false; }
            suppressPlayerListBinding();
            if (!pressed) tabHeld = false;
            else if (!repeat && !tabHeld) { tabHeld = true; editor().cycleCorner(); }
            return true;
        }
        if (!selecting() || !pressed || repeat) return false;
        if (key == Keyboard.KEY_BACK) editor().undo();
        else if (key == Keyboard.KEY_DELETE) editor().clearVertices();
        else if (key == Keyboard.KEY_RETURN) returnToScreen();
        else return false;
        return true;
    }
    private void suppressPlayerListBinding() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings != null && mc.gameSettings.keyBindPlayerList != null
                && mc.gameSettings.keyBindPlayerList.getKeyCode() == Keyboard.KEY_TAB) {
            // Vanilla sets this binding before firing KeyInputEvent. Consume held/buffered Tab locally.
            net.minecraft.client.settings.KeyBinding.setKeyBindState(Keyboard.KEY_TAB, false);
            while (mc.gameSettings.keyBindPlayerList.isPressed()) { }
        }
    }
    @SubscribeEvent public void playerList(RenderGameOverlayEvent.Pre event) {
        if (event.type == RenderGameOverlayEvent.ElementType.PLAYER_LIST && selectingVertices()) event.setCanceled(true);
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
        if (selectingVertices()) suppressPlayerListBinding(); else tabHeld = false;
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
        if (mc.thePlayer == null || mc.thePlayer.isDead || !editor().hasSession(mc.thePlayer.getUniqueID(), mc.thePlayer.dimension)) editor().reset();
        else if (editor().getSelection() == KOMETacticalAreaEditor.Selection.NONE && !(mc.currentScreen instanceof KOMEGuiTacticalAreaEditor)) editor().cancel();
    }
    @SubscribeEvent public void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft(); KOMETacticalAreaEditor e = editor();
        if (e == null || mc.thePlayer == null || mc.renderViewEntity == null) return;
        List<KOMETacticalAreaEditor.Overlay> overlays = e.overlays(mc.thePlayer.getUniqueID(), mc.thePlayer.dimension);
        if (overlays.isEmpty()) return;
        Entity camera = mc.renderViewEntity;
        double cx = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * event.partialTicks;
        double cy = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * event.partialTicks;
        double cz = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * event.partialTicks;
        GL11.glPushMatrix(); GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glTranslated(-cx, -cy, -cz); GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_CULL_FACE);
            for (KOMETacticalAreaEditor.Overlay overlay : overlays) {
            List<KOMEXZPoint> points = overlay.prism.getPolygon().getVertices();
            double low = overlay.prism.getMinYInclusive(), high = overlay.prism.getMaxYExclusive();
            if (overlay.selected && e.getGeometry() != null) {
                Tessellator fill = Tessellator.instance; fill.startDrawing(GL11.GL_TRIANGLES);
                fill.setColorRGBA_F(overlay.invalid ? 1F : 0.2F, overlay.invalid ? 0.15F : 1F, overlay.invalid ? 0.15F : 0.85F, 0.22F);
                for (KOMEXZPoint p : e.getFillTriangles()) fill.addVertex(p.getX(), low + 0.02, p.getZ());
                fill.draw();
            }
            GL11.glLineWidth(overlay.selected ? 3F : 1F);
            Tessellator t = Tessellator.instance; t.startDrawing(GL11.GL_LINES);
            if (overlay.invalid) t.setColorOpaque_F(1F, 0.15F, 0.15F);
            else if (overlay.selected) t.setColorOpaque_F(0.2F, 1F, 0.85F);
            else if (overlay.type == KOMETacticalComplexDraft.ZoneType.WALL) t.setColorOpaque_F(1F, 0.6F, 0.15F);
            else if (overlay.type == KOMETacticalComplexDraft.ZoneType.TRANSITION) t.setColorOpaque_F(0.8F, 0.3F, 1F);
            else t.setColorOpaque_F(0.2F, 0.55F, 1F);
            for (int i = 0; i < points.size(); i++) {
                KOMEXZPoint p = points.get(i), next = points.get((i + 1) % points.size());
                line(t, p.getX(), low, p.getZ(), next.getX(), low, next.getZ());
                line(t, p.getX(), high, p.getZ(), next.getX(), high, next.getZ());
                line(t, p.getX(), low, p.getZ(), p.getX(), high, p.getZ());
            }
            if (overlay.selected) for (int i = 0; i < points.size(); i++) {
                KOMEXZPoint p = points.get(i);
                if (i == 0) t.setColorOpaque_F(0.2F, 1F, 0.2F);
                else if (i == points.size() - 1) t.setColorOpaque_F(1F, 0.8F, 0.2F);
                else t.setColorOpaque_F(0.2F, 0.85F, 1F);
                line(t, p.getX() - 0.3, low, p.getZ(), p.getX() + 0.3, low, p.getZ());
                line(t, p.getX(), low, p.getZ() - 0.3, p.getX(), low, p.getZ() + 0.3);
            }
            MovingObjectPosition hit = mc.objectMouseOver;
            if (overlay.selected && selecting() && hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                t.setColorOpaque_F(1F, 0.2F, 1F);
                blockOutline(t, hit.blockX, hit.blockZ, hit.blockY, (double)hit.blockY+1);
                if (e.getSelection() == KOMETacticalAreaEditor.Selection.VERTICES) {
                    KOMEXZPoint p = e.aimedCorner(hit.blockX,hit.blockZ);
                    line(t,p.getX()-0.3,low,p.getZ(),p.getX()+0.3,low,p.getZ());
                    line(t,p.getX(),low,p.getZ()-0.3,p.getX(),low,p.getZ()+0.3);
                    line(t,p.getX(),low,p.getZ(),p.getX(),high,p.getZ());
                    double cursorY=(double)hit.blockY+1;
                    line(t,p.getX()-0.3,cursorY,p.getZ(),p.getX()+0.3,cursorY,p.getZ());
                    line(t,p.getX(),cursorY,p.getZ()-0.3,p.getX(),cursorY,p.getZ()+0.3);
                    if (!points.isEmpty()) {
                        KOMEXZPoint last = points.get(points.size()-1);
                        line(t,last.getX(),low,last.getZ(),p.getX(),low,p.getZ());
                        line(t,last.getX(),high,last.getZ(),p.getX(),high,p.getZ());
                    }
                } else {
                    line(t, hit.blockX + 0.5, low, hit.blockZ + 0.5, hit.blockX + 0.5, high, hit.blockZ + 0.5);
                }
            }
            t.draw();
            if (overlay.selected) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                for (int i = 0; i < points.size(); i++) {
                    KOMEXZPoint p = points.get(i);
                    label(mc, camera, p.getX(), low + 0.3, p.getZ(), Integer.toString(i+1), overlay.invalid ? 0xFF5555 : 0xFFFFFF);
                }
                if (selectingVertices() && hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    KOMEXZPoint p = e.aimedCorner(hit.blockX,hit.blockZ);
                    label(mc,camera,p.getX(),(double)hit.blockY+1.3,p.getZ(),"Next " + e.getCorner(),0xFF55FF);
                }
                GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
            }
        } finally { GL11.glPopAttrib(); GL11.glPopMatrix(); }
    }
    private static void line(Tessellator t, double x, double y, double z, double xx, double yy, double zz) {
        t.addVertex(x, y, z); t.addVertex(xx, yy, zz);
    }
    private static void label(Minecraft mc,Entity camera,double x,double y,double z,String text,int color) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x,y,z); GL11.glRotatef(-camera.rotationYaw,0,1,0); GL11.glRotatef(camera.rotationPitch,1,0,0);
            GL11.glScalef(-0.025F,-0.025F,0.025F);
            mc.fontRenderer.drawString(text,-mc.fontRenderer.getStringWidth(text)/2,0,color);
        } finally { GL11.glPopMatrix(); }
    }
    private static void blockOutline(Tessellator t, int x, int z, double low, double high) {
        double xx = (double) x + 1, zz = (double) z + 1;
        for (double y : low == high ? new double[] {low} : new double[] {low, high}) {
            line(t, x, y, z, xx, y, z); line(t, xx, y, z, xx, y, zz);
            line(t, xx, y, zz, x, y, zz); line(t, x, y, zz, x, y, z);
        }
        if(low!=high) {
            line(t,x,low,z,x,high,z);line(t,xx,low,z,xx,high,z);
            line(t,xx,low,zz,xx,high,zz);line(t,x,low,zz,x,high,zz);
        }
    }
    @SubscribeEvent public void hud(RenderGameOverlayEvent.Text event) {
        if (!selecting()) return;
        KOMEPolygonPrism prism = editor().getGeometry();
        if (prism == null) return;
        event.left.add("Tactical geometry " + editor().getGeometryId() + " | " + (editor().getSelection() == KOMETacticalAreaEditor.Selection.VERTICES
            ? "Polygon vertices | Corner " + editor().getCorner()
            : editor().getSelection() == KOMETacticalAreaEditor.Selection.LOWER_Y ? "Bottom Y" : "Top block Y"));
        event.left.add("Aim at block | Tab: NW / NE / SE / SW | Right-click confirms corner/Y");
        event.left.add("Backspace undo vertex | Delete clear | Enter/Esc editor | N = -Z, E = +X");
        event.left.add(editor().getConfirmedVertexCount() + " confirmed vertices / " + editor().getDistinctVertexCount() + " distinct"
            + " | Y [" + prism.getMinYInclusive() + ", " + prism.getMaxYExclusive() + ")");
        List<KOMEXZPoint> vertices = editor().getConfirmedVertices();
        int start = Math.max(0,vertices.size()-8);
        if (start > 0) event.left.add("Latest 8 vertices (all vertices numbered in world):");
        for (int i = start; i < vertices.size(); i++) event.left.add((i+1) + ": X " + vertices.get(i).getX() + ", Z " + vertices.get(i).getZ());
        MovingObjectPosition hit = Minecraft.getMinecraft().objectMouseOver;
        if (selectingVertices() && hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            KOMEXZPoint p = editor().aimedCorner(hit.blockX,hit.blockZ);
            event.left.add("Next " + editor().getCorner() + ": X " + p.getX() + ", Z " + p.getZ());
        }
        if (editor().isComplexEditing()) event.left.add("Normal blue | Wall orange | Transition purple | Selected green | Conflict red");
        event.left.addAll(editor().geometryFeedback().messages);
        if (!editor().getMessage().isEmpty()) event.left.add(editor().getMessage());
    }
}
