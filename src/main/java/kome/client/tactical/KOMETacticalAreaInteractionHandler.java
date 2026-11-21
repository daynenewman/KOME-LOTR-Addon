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
    public interface BlockTargeter { MovingObjectPosition target(Minecraft mc, double ox, double oy, double oz,
        double dx, double dy, double dz, double reach); }
    private final BlockTargeter blockTargeter;
    private boolean tabHeld;
    public KOMETacticalAreaInteractionHandler(KOMEClientProxy proxy) {
        this(proxy, (mc, ox, oy, oz, dx, dy, dz, reach) ->
            KOMETacticalBlockTarget.trace(mc.theWorld, ox, oy, oz, dx, dy, dz, reach));
    }
    public KOMETacticalAreaInteractionHandler(KOMEClientProxy proxy, BlockTargeter blockTargeter) {
        this.proxy = proxy; this.blockTargeter = blockTargeter;
    }
    private KOMETacticalAreaEditor editor() { return proxy.getTacticalAreaEditor(); }
    private boolean selecting() {
        Minecraft mc = Minecraft.getMinecraft(); KOMETacticalAreaEditor e = editor();
        return e != null && mc.thePlayer != null && mc.currentScreen == null
            && e.consumesClicks(mc.thePlayer.getUniqueID(), mc.thePlayer.dimension);
    }
    private boolean viewing() {
        Minecraft mc = Minecraft.getMinecraft(); KOMETacticalAreaEditor e = editor();
        return e != null && mc.thePlayer != null && mc.currentScreen == null
            && e.worldViewMatches(mc.thePlayer.getUniqueID(), mc.thePlayer.dimension);
    }
    @SubscribeEvent public void mouse(MouseEvent event) {
        if (viewing() && (event.button == 0 || event.button == 1)) {
            event.setCanceled(true);
            if (event.buttonstate) handleWorldClick(event.button,
                Keyboard.isCreated() && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)));
            return;
        }
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
    /** One click owns one action; Forge samples Shift before vanilla dispatch. */
    public void handleWorldClick(int button, boolean shift) {
        Minecraft mc = Minecraft.getMinecraft(); KOMETacticalAreaEditor e = editor();
        if (e == null || mc.thePlayer == null || mc.currentScreen != null || !e.isViewingGeometry() || e.isBusy()) return;
        if (!e.canEditWorldGeometry(mc.thePlayer.getUniqueID(), mc.thePlayer.dimension)) {
            if (button == 1 && !selectByRay(mc, e)) e.error("Aim at an authored polygon to select it.");
            return;
        }
        if (button == 0) {
            if (e.getHeldVertex() < 0 && e.getWorldHeightMode() == KOMETacticalAreaEditor.Selection.NONE)
                try { e.pickUpVertex(worldHoveredVertex(mc, e, null)); }
                catch (RuntimeException invalid) { e.error(invalid.getMessage()); }
            return;
        }
        try {
            if (e.getHeldVertex() >= 0) {
                MovingObjectPosition hit = placementTarget(mc);
                if (hit == null) e.error("Aim at a physical block surface within 128 blocks to place the vertex.");
                else e.placeHeldVertex(hit.blockX, hit.blockZ);
                return;
            }
            if (e.getWorldHeightMode() != KOMETacticalAreaEditor.Selection.NONE) {
                MovingObjectPosition hit = placementTarget(mc);
                if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)
                    e.error("Aim at a physical block surface within 128 blocks to pick a height.");
                else e.applyWorldHeight(hit.blockY);
                return;
            }
            if (shift) {
                if (!selectByRay(mc, e, true)) e.error("Aim at another authored polygon to switch selection.");
                return;
            }
            if (!e.isWorldVertexPlacementActive()) return;
            MovingObjectPosition hit = placementTarget(mc);
            if (hit == null) e.error("Aim at a physical block surface within 128 blocks to add a corner.");
            else e.addWorldVertex(hit.blockX, hit.blockZ);
        } catch (RuntimeException invalid) { e.error(invalid.getMessage()); }
    }
    private MovingObjectPosition placementTarget(Minecraft mc) {
        WorldPick ray = worldPick(mc);
        return ray == null ? null : blockTargeter.target(mc, ray.ox, ray.oy, ray.oz,
            ray.dx, ray.dy, ray.dz, ray.reach);
    }
    private boolean selectByRay(Minecraft mc, KOMETacticalAreaEditor e) {
        return selectByRay(mc, e, false);
    }
    private boolean selectByRay(Minecraft mc, KOMETacticalAreaEditor e, boolean otherOnly) {
        WorldPick ray = worldPick(mc);
        if (ray == null) return false;
        return otherOnly
            ? e.selectOtherWorldPolygonRay(mc.thePlayer.getUniqueID(), mc.thePlayer.dimension,
                ray.ox, ray.oy, ray.oz, ray.dx, ray.dy, ray.dz, ray.reach)
            : e.selectWorldPolygonRay(mc.thePlayer.getUniqueID(), mc.thePlayer.dimension,
                ray.ox, ray.oy, ray.oz, ray.dx, ray.dy, ray.dz, ray.reach);
    }
    private int worldHoveredVertex(Minecraft mc, KOMETacticalAreaEditor e, MovingObjectPosition hit) {
        WorldPick ray = worldPick(mc);
        return ray == null ? -1 : e.hoveredVertexRay(ray.ox, ray.oy, ray.oz, ray.dx, ray.dy, ray.dz, ray.reach);
    }
    private static final class WorldPick {
        final double ox, oy, oz, dx, dy, dz, reach;
        WorldPick(double ox, double oy, double oz, double dx, double dy, double dz, double reach) {
            this.ox = ox; this.oy = oy; this.oz = oz; this.dx = dx; this.dy = dy; this.dz = dz; this.reach = reach;
        }
    }
    /** Editor reach is independent of vanilla interaction reach; block collisions are checked separately. */
    private WorldPick worldPick(Minecraft mc) {
        if (mc.renderViewEntity == null || mc.thePlayer == null) return null;
        Entity camera = mc.renderViewEntity;
        net.minecraft.util.Vec3 look = camera.getLookVec();
        if (look == null) return null;
        double ox = camera.posX, oy = camera.posY + camera.getEyeHeight(), oz = camera.posZ;
        return new WorldPick(ox, oy, oz, look.xCoord, look.yCoord, look.zCoord, 128D);
    }
    @SubscribeEvent public void interact(PlayerInteractEvent event) {
        if (event.world != null && event.world.isRemote && (selecting() || viewing())
                && event.entityPlayer.getUniqueID().equals(Minecraft.getMinecraft().thePlayer.getUniqueID())) event.setCanceled(true);
    }
    @SubscribeEvent public void keys(InputEvent.KeyInputEvent event) {
        try {
            handleKey(Keyboard.getEventKey(), Keyboard.getEventKeyState(), Keyboard.isRepeatEvent(),
                Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
        } catch (RuntimeException invalid) { editor().error(invalid.getMessage()); }
    }
    private boolean selectingVertices() { return selecting() && editor().getSelection() == KOMETacticalAreaEditor.Selection.VERTICES; }
    private boolean viewingTopology() {
        Minecraft mc = Minecraft.getMinecraft(); KOMETacticalAreaEditor e = editor();
        return e != null && mc.thePlayer != null && mc.currentScreen == null
            && e.hasSession(mc.thePlayer.getUniqueID(), mc.thePlayer.dimension) && e.getSelection() == KOMETacticalAreaEditor.Selection.TOPOLOGY;
    }
    /** The real FML callback delegates here; one physical Tab press changes only the corner. */
    public boolean handleKey(int key, boolean pressed, boolean repeat) {
        return handleKey(key, pressed, repeat, false);
    }
    public boolean handleKey(int key, boolean pressed, boolean repeat, boolean shift) {
        if (viewing()) {
            if (!pressed || repeat || !editor().isWorldPolygonSelected() || editor().isBusy()) return false;
            try {
                if (key == Keyboard.KEY_TAB) { suppressPlayerListBinding(); editor().cycleWorldCorner(); return true; }
                if (shift) {
                    if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) {
                        editor().toggleWorldHeight(key == Keyboard.KEY_UP ? KOMETacticalAreaEditor.Selection.UPPER_Y : KOMETacticalAreaEditor.Selection.LOWER_Y);
                        return true;
                    }
                }
                if (key == Keyboard.KEY_BACK) { editor().undoWorldGeometry(); return true; }
                if (key == Keyboard.KEY_DELETE) { editor().deleteSelectedVertex(); return true; }
                if (key == Keyboard.KEY_RETURN) { returnToWorldViewScreen(); return true; }
            } catch (RuntimeException invalid) { editor().error(invalid.getMessage()); return true; }
            return false;
        }
        if (viewingTopology() && key == Keyboard.KEY_RETURN && pressed && !repeat) { returnToScreen(); return true; }
        if (key == Keyboard.KEY_TAB) {
            if (!selectingVertices()) { tabHeld = false; return false; }
            suppressPlayerListBinding();
            if (!pressed) tabHeld = false;
            else if (!repeat && !tabHeld) { tabHeld = true; editor().cycleCorner(); }
            return true;
        }
        if (!selecting() || !pressed || repeat) return false;
        if (key == Keyboard.KEY_BACK) editor().removeLastVertex();
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
        if (event.type == RenderGameOverlayEvent.ElementType.PLAYER_LIST && (selectingVertices() || viewing() && editor().isWorldPolygonSelected())) event.setCanceled(true);
    }
    private void returnToScreen() {
        editor().select(KOMETacticalAreaEditor.Selection.NONE);
        Minecraft.getMinecraft().displayGuiScreen(new KOMEGuiTacticalAreaEditor(editor()));
    }
    private void returnToWorldViewScreen() {
        boolean focused = editor().isWorldPolygonSelected();
        net.minecraft.client.gui.GuiScreen origin = editor().endWorldView();
        if (origin instanceof KOMEGuiTacticalAreaEditor && focused) ((KOMEGuiTacticalAreaEditor) origin).focusWorldSelection();
        Minecraft.getMinecraft().displayGuiScreen(origin == null ? new KOMEGuiTacticalAreaEditor(editor()) : origin);
    }
    @SubscribeEvent public void gui(GuiOpenEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.gui instanceof GuiIngameMenu && editor() != null && mc.thePlayer != null
                && editor().worldViewMatches(mc.thePlayer.getUniqueID(), mc.thePlayer.dimension)) {
            boolean focused = editor().isWorldPolygonSelected();
            editor().cancelHeldVertex();
            net.minecraft.client.gui.GuiScreen origin = editor().endWorldView();
            if (origin instanceof KOMEGuiTacticalAreaEditor && focused) ((KOMEGuiTacticalAreaEditor) origin).focusWorldSelection();
            event.gui = origin == null ? new KOMEGuiTacticalAreaEditor(editor()) : origin;
            return;
        }
        if (editor() != null && editor().isEditing() && editor().getSelection() != KOMETacticalAreaEditor.Selection.NONE
                && event.gui instanceof GuiIngameMenu) {
            editor().select(KOMETacticalAreaEditor.Selection.NONE);
            event.gui = new KOMEGuiTacticalAreaEditor(editor());
        }
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (selectingVertices() || viewing() && editor().isWorldPolygonSelected()) suppressPlayerListBinding(); else tabHeld = false;
        if (event.phase == TickEvent.Phase.START && (selecting() || viewing())) {
            Minecraft mc = Minecraft.getMinecraft();
            // MouseEvent cancels fresh clicks; clear held/buffered attack/use bindings as well.
            if (mc.gameSettings != null) {
                net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                while (mc.gameSettings.keyBindAttack.isPressed()) { }
                while (mc.gameSettings.keyBindUseItem.isPressed()) { }
            }
        }
        if (event.phase != TickEvent.Phase.END || editor() == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (editor().hasWorldViewContext() && (mc.thePlayer == null || mc.thePlayer.isDead
                || !editor().worldViewMatches(mc.thePlayer.getUniqueID(), mc.thePlayer.dimension))) { editor().reset(); return; }
        if (editor().hasRetainedDraft()) {
            if (mc.thePlayer != null) editor().tickSession(mc.thePlayer.getUniqueID(), mc.thePlayer.dimension);
            return;
        }
        if (!editor().isEditing()) return;
        if (mc.thePlayer == null || mc.thePlayer.isDead || !editor().hasSession(mc.thePlayer.getUniqueID(), mc.thePlayer.dimension)) editor().reset();
        else if (editor().getSelection() == KOMETacticalAreaEditor.Selection.NONE && !editor().hasWorldViewContext()
                && !(mc.currentScreen instanceof KOMEGuiTacticalAreaEditor)) editor().cancel();
        else editor().tickSession(mc.thePlayer.getUniqueID(), mc.thePlayer.dimension);
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
            List<KOMEXZPoint> bottomFace = KOMETacticalPolygonPreview.bottomFaceForPlayer(overlay.prism,
                mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
            if (!bottomFace.isEmpty()) {
                Tessellator fill = Tessellator.instance; fill.startDrawing(GL11.GL_TRIANGLES);
                fill.setColorRGBA_F(overlay.invalid ? 1F : 0.2F, overlay.invalid ? 0.15F : 1F, overlay.invalid ? 0.15F : 0.85F, 0.22F);
                for (KOMEXZPoint p : bottomFace) fill.addVertex(p.getX(), low + 0.02, p.getZ());
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
            MovingObjectPosition hit = selecting() ? mc.objectMouseOver
                : overlay.selected && e.isWorldPolygonSelected()
                    ? e.getWorldHeightMode() != KOMETacticalAreaEditor.Selection.NONE
                        || e.isWorldVertexPlacementActive() || e.getHeldVertex() >= 0 ? placementTarget(mc) : null
                    : null;
            int hoverIndex = overlay.selected && e.isWorldPolygonSelected() ? worldHoveredVertex(mc, e, hit) : -1;
            if (overlay.selected) for (int i = 0; i < points.size(); i++) {
                KOMEXZPoint p = points.get(i);
                if (e.isWorldPolygonSelected() && (hoverIndex == i || e.getSelectedVertex() == i)) t.setColorOpaque_F(1F, 1F, 0.1F);
                else if (i == 0) t.setColorOpaque_F(0.2F, 1F, 0.2F);
                else if (i == points.size() - 1) t.setColorOpaque_F(1F, 0.8F, 0.2F);
                else t.setColorOpaque_F(0.2F, 0.85F, 1F);
                line(t, p.getX() - 0.3, low, p.getZ(), p.getX() + 0.3, low, p.getZ());
                line(t, p.getX(), low, p.getZ() - 0.3, p.getX(), low, p.getZ() + 0.3);
            }
            if (overlay.selected && (selecting() || e.isWorldPolygonSelected()) && hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                t.setColorOpaque_F(1F, 0.2F, 1F);
                blockOutline(t, hit.blockX, hit.blockZ, hit.blockY, (double)hit.blockY+1);
                if (e.getSelection() == KOMETacticalAreaEditor.Selection.VERTICES || e.isWorldPolygonSelected() && e.getWorldHeightMode() == KOMETacticalAreaEditor.Selection.NONE) {
                    KOMEXZPoint p = e.aimedCorner(hit.blockX,hit.blockZ);
                    line(t,p.getX()-0.3,low,p.getZ(),p.getX()+0.3,low,p.getZ());
                    line(t,p.getX(),low,p.getZ()-0.3,p.getX(),low,p.getZ()+0.3);
                    line(t,p.getX(),low,p.getZ(),p.getX(),high,p.getZ());
                    double cursorY=(double)hit.blockY+1;
                    line(t,p.getX()-0.3,cursorY,p.getZ(),p.getX()+0.3,cursorY,p.getZ());
                    line(t,p.getX(),cursorY,p.getZ()-0.3,p.getX(),cursorY,p.getZ()+0.3);
                    if (!points.isEmpty()) {
                        KOMEXZPoint last = e.getHeldVertex() >= 0 ? points.get((e.getHeldVertex() + points.size() - 1) % points.size()) : points.get(points.size()-1);
                        line(t,last.getX(),low,last.getZ(),p.getX(),low,p.getZ());
                        line(t,last.getX(),high,last.getZ(),p.getX(),high,p.getZ());
                        if (e.getHeldVertex() >= 0) {
                            KOMEXZPoint next = points.get((e.getHeldVertex() + 1) % points.size());
                            line(t,p.getX(),low,p.getZ(),next.getX(),low,next.getZ());
                            line(t,p.getX(),high,p.getZ(),next.getX(),high,next.getZ());
                        }
                    }
                } else {
                    double guideY = heightGuideY(e.getWorldHeightMode(), hit.blockY);
                    t.setColorOpaque_F(1F, 1F, 0.2F);
                    blockOutline(t, hit.blockX, hit.blockZ, guideY, guideY);
                    for (int i = 0; i < points.size(); i++) {
                        KOMEXZPoint a = points.get(i), b = points.get((i + 1) % points.size());
                        line(t, a.getX(), guideY, a.getZ(), b.getX(), guideY, b.getZ());
                    }
                }
            }
            t.draw();
            if (e.isViewingGeometry() && overlay.prism.getPolygon().hasBounds()) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                String kind = overlay.type == null ? "Area" : overlay.type == KOMETacticalComplexDraft.ZoneType.NORMAL ? "Normal"
                    : overlay.type == KOMETacticalComplexDraft.ZoneType.WALL ? "Wall" : "Transition";
                String authored = overlay.label == null || overlay.label.trim().isEmpty() ? "" : " - " + overlay.label.trim();
                String title = mc.fontRenderer.trimStringToWidth(kind + " " + overlay.id + authored
                    + (overlay.unsaved ? " [unsaved]" : "") + (e.isWorldPolygonSelected() && overlay.selected ? " [editing]" : ""), 180);
                label(mc, camera, centerX(overlay.prism), high + 0.6, centerZ(overlay.prism),
                    title, overlay.invalid ? 0xFF5555 : overlay.selected ? 0x55FFDD : 0xFFFFFF);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
            if (overlay.selected) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                for (int i = 0; i < points.size(); i++) {
                    KOMEXZPoint p = points.get(i);
                    label(mc, camera, p.getX(), low + 0.3, p.getZ(), Integer.toString(i+1), overlay.invalid ? 0xFF5555 : 0xFFFFFF);
                }
                if ((selectingVertices() || e.isWorldPolygonSelected() && (e.isWorldVertexPlacementActive() || e.getHeldVertex() >= 0)
                        && e.getWorldHeightMode() == KOMETacticalAreaEditor.Selection.NONE) && hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    KOMEXZPoint p = e.aimedCorner(hit.blockX,hit.blockZ);
                    label(mc,camera,p.getX(),(double)hit.blockY+1.3,p.getZ(),(e.getHeldVertex() >= 0 ? "Move " : "Next ") + e.getCorner(),0xFF55FF);
                } else if (e.isWorldPolygonSelected() && e.getWorldHeightMode() != KOMETacticalAreaEditor.Selection.NONE
                        && hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    label(mc, camera, hit.blockX + 0.5, hit.blockY + 1.3, hit.blockZ + 0.5,
                        heightGuideLabel(e.getWorldHeightMode(), hit.blockY), 0xFFFF55);
                }
                GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
            }
            for (KOMETacticalAreaEditor.ConnectionOverlay overlay : e.connectionOverlays(mc.thePlayer.getUniqueID(), mc.thePlayer.dimension)) {
                if (overlay.transition == null || !overlay.transition.getPolygon().hasBounds()) continue;
                double x = centerX(overlay.transition), z = centerZ(overlay.transition), y = overlay.transition.getMaxYExclusive() + 0.4;
                GL11.glLineWidth(overlay.selected ? 4F : 2F);
                Tessellator t = Tessellator.instance; t.startDrawing(GL11.GL_LINES);
                if (overlay.invalid) t.setColorOpaque_F(1F, 0.15F, 0.15F);
                else if (overlay.selected) t.setColorOpaque_F(1F, 0.9F, 0.2F);
                else if (overlay.connection.isGated()) t.setColorOpaque_F(0.2F, 0.9F, 1F);
                else t.setColorOpaque_F(0.9F, 0.9F, 0.9F);
                for (KOMEPolygonPrism endpoint : new KOMEPolygonPrism[] {overlay.normalA, overlay.normalB})
                    if (endpoint != null && endpoint.getPolygon().hasBounds()) line(t, x, y, z, centerX(endpoint), endpoint.getMaxYExclusive() + 0.4, centerZ(endpoint));
                line(t, x, y - 0.3, z, x, y + 0.3, z); t.draw();
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                String gate = overlay.connection.isGated() ? overlay.connection.getGateRef().get().getBuildId() + " / " + overlay.connection.getGateRef().get().getGateRecordId() : "Gateless";
                label(mc, camera, x, y + 0.5, z, overlay.connection.getId() + " | " + gate, overlay.invalid ? 0xFF5555 : overlay.selected ? 0xFFFF55 : 0xFFFFFF);
                if (overlay.selected) label(mc, camera, x, y + 1, z, overlay.connection.getEndpointA() + " <-> " + overlay.connection.getEndpointB(), 0xFFFFFF);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
        } finally { GL11.glPopAttrib(); GL11.glPopMatrix(); }
    }
    private static double centerX(KOMEPolygonPrism prism) { return ((double) prism.getMinX() + prism.getMaxX()) / 2D; }
    static double heightGuideY(KOMETacticalAreaEditor.Selection mode, int blockY) {
        return mode == KOMETacticalAreaEditor.Selection.UPPER_Y ? (double) blockY + 1D : blockY;
    }
    static String heightGuideLabel(KOMETacticalAreaEditor.Selection mode, int blockY) {
        return mode == KOMETacticalAreaEditor.Selection.UPPER_Y
            ? "Top Y " + blockY + " (upper face " + ((long) blockY + 1L) + ")"
            : "Bottom Y " + blockY;
    }
    private static double centerZ(KOMEPolygonPrism prism) { return ((double) prism.getMinZ() + prism.getMaxZ()) / 2D; }
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
        if (viewing()) {
            KOMETacticalAreaEditor e = editor();
            event.left.add("Tactical Geometry | Esc: return to editor");
            event.left.add((e.isComplexEditing() ? "Siege Complex " : "Deployment Area ") + e.getTargetId());
            if (e.isViewingGeometry()) {
                if (!e.isWorldPolygonSelected()) event.left.add("Shift+right-click a polygon to edit vertices.");
                else if (e.getWorldHeightMode() != KOMETacticalAreaEditor.Selection.NONE) {
                    event.left.add((e.getWorldHeightMode() == KOMETacticalAreaEditor.Selection.UPPER_Y ? "Pick Top Y" : "Pick Bottom Y")
                        + " | Aim at block; right-click applies Y | same Shift+Arrow exits");
                } else if (e.getHeldVertex() >= 0) event.left.add("Moving vertex " + (e.getHeldVertex() + 1) + " | Tab: corner | right-click: place | Esc: cancel");
                else if (e.isWorldVertexPlacementActive()) event.left.add("Placement ready | Left-click vertex: pick up | Right-click: add corner | Shift+Right-click: switch | Tab: corner");
                else event.left.add("Passive Geometry View | Left-click vertex: move | Shift+Right-click: edit vertices | Esc: GUI");
                if (e.isWorldPolygonSelected()) {
                    event.left.add("Backspace: Undo | Delete: held or last vertex | Shift+Up/Down: pick Y");
                    event.left.addAll(e.geometryFeedback().messages);
                }
                if (e.isComplexEditing()) event.left.add("Normal blue | Wall orange | Transition purple | Selected cyan | Invalid red");
            } else event.left.add("Session expired. Return and reopen the geometry before editing.");
            if (!e.getMessage().isEmpty()) event.left.add(e.getMessage());
            return;
        }
        if (viewingTopology()) {
            event.left.add("Tactical Connections | Enter/Esc editor | Yellow selected | Cyan gated | White gateless | Red invalid");
            if (editor().getConnection() != null) { event.left.add(editor().getConnection().getEndpointA() + " <-> " + editor().getConnection().getEndpointB()); event.left.add(editor().connectionStatus()); }
            event.left.addAll(editor().connectionDiagnostics()); return;
        }
        if (!selecting()) return;
        KOMEPolygonPrism prism = editor().getGeometry();
        if (prism == null) return;
        event.left.add("Tactical geometry " + editor().getGeometryId() + " | " + (editor().getSelection() == KOMETacticalAreaEditor.Selection.VERTICES
            ? "Polygon vertices | Corner " + editor().getCorner()
            : editor().getSelection() == KOMETacticalAreaEditor.Selection.LOWER_Y ? "Bottom Y" : "Top block Y"));
        event.left.add("Aim at block | Tab: NW / NE / SE / SW | Right-click confirms corner/Y");
        event.left.add("Backspace remove last | Delete clear | Enter/Esc editor | N = -Z, E = +X");
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
