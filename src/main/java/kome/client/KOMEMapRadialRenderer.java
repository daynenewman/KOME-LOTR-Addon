package kome.client;

import kome.client.gui.KOMEGuiTheme;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/** Stateless stepped-cell rendering through the existing conquest-map overlay. */
final class KOMEMapRadialRenderer {
    private KOMEMapRadialRenderer() { }
    static void draw(KOMEMapRadialMenu menu, KOMEMapViewport view, double x, double y, int hover,
            FontRenderer font, double scale) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_POLYGON_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_SCISSOR_BIT);
        GL11.glPushMatrix();
        try {
            // Scissor stays in native GUI space; only the radial presentation is scaled about its map anchor.
            KOMEGuiTheme.enableScissor(KOMEMinecraftClient.get(), (int)view.left, (int)view.top, (int)(view.right-view.left), (int)(view.bottom-view.top));
            GL11.glTranslated(x, y, 0);
            GL11.glScaled(scale, scale, 1);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            for (int i = 0; i < menu.segments().size(); i++) {
                KOMEMapRadialMenu.Segment segment = menu.segments().get(i);
                KOMEMapRadialMenu.Action action = segment.action;
                int border=borderColor(menu,action,hover==i);
                int fill=fillColor(menu,action,hover==i);
                cells(segment, fill, border,borderStroke(action));
            }
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            for (KOMEMapRadialMenu.Segment segment : menu.segments()) {
                String[] lines=labelLines(menu,segment.action);
                for(int line=0;line<lines.length;line++)centered(font,lines[line],segment.labelX(),
                    segment.labelY()-4-(lines.length-1)*4+line*8,
                    menu.enabled(segment.action)?KOMEGuiTheme.COLOR_TEXT_LIGHT:KOMEGuiTheme.COLOR_TEXT_DISABLED);
            }
            centered(font, menu.tileId(), 0, 12, KOMEGuiTheme.COLOR_GOLD);
            if(!menu.status().isEmpty())centered(font,font.trimStringToWidth(menu.status(),300),0,
                KOMEMapRadialMenu.OUTER_RADIUS+24,KOMEGuiTheme.COLOR_WARN);
            if (hover >= 0) centered(font, menu.segments().get(hover).action.description, 0,
                KOMEMapRadialMenu.OUTER_RADIUS + 8, KOMEGuiTheme.COLOR_TEXT_LIGHT);
        } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
    }
    private static void centered(FontRenderer font, String text, double x, double y, int color) {
        GL11.glPushMatrix();
        try {
            double scale = KOMEMapRadialMenu.FONT_SCALE;
            GL11.glScaled(scale, scale, 1);
            font.drawStringWithShadow(text, (int)Math.round(x / scale) - font.getStringWidth(text) / 2,
                (int)Math.round(y / scale), color);
        } finally { GL11.glPopMatrix(); }
    }
    static String[] labelLines(KOMEMapRadialMenu menu,KOMEMapRadialMenu.Action action) {
        return action==KOMEMapRadialMenu.Action.JOIN_BATTLE||action==KOMEMapRadialMenu.Action.RALLY_HERE?menu.label(action).split(" "):new String[]{menu.label(action)};
    }
    static int fillColor(KOMEMapRadialMenu menu,KOMEMapRadialMenu.Action action,boolean hover) {
        if(!menu.enabled(action))return KOMEGuiTheme.COLOR_PANEL_DARK;
        if(action==KOMEMapRadialMenu.Action.JOIN_BATTLE)return hover?0xFF945242:0xFF572C24;
        return hover?KOMEGuiTheme.COLOR_PARCHMENT_LIGHT:KOMEGuiTheme.COLOR_PARCHMENT_DARK;
    }
    static int borderColor(KOMEMapRadialMenu menu,KOMEMapRadialMenu.Action action,boolean hover) {
        if(action==KOMEMapRadialMenu.Action.JOIN_BATTLE)return KOMEGuiTheme.COLOR_WARN;
        return !menu.enabled(action)?KOMEGuiTheme.COLOR_TEXT_DISABLED:
            hover?KOMEGuiTheme.COLOR_GOLD:KOMEGuiTheme.COLOR_GOLD_DARK;
    }
    static int borderStroke(KOMEMapRadialMenu.Action action){return action==KOMEMapRadialMenu.Action.JOIN_BATTLE?2:1;}
    private static void cells(KOMEMapRadialMenu.Segment segment, int fill, int border,int stroke) {
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setColorRGBA_I(fill & 0xFFFFFF, fill >>> 24);
        int grid = KOMEMapRadialMenu.PIXEL_GRID;
        for (KOMEMapRadialMenu.Cell cell : segment.cells) quad(tess, cell.x, cell.y, cell.x + grid, cell.y + grid);
        tess.setColorRGBA_I(border & 0xFFFFFF, border >>> 24);
        for (KOMEMapRadialMenu.Cell cell : segment.cells) {
            int x = cell.x, y = cell.y, edges = cell.edges;
            if ((edges & KOMEMapRadialMenu.Cell.LEFT) != 0) quad(tess, x, y, x + stroke, y + grid);
            if ((edges & KOMEMapRadialMenu.Cell.RIGHT) != 0) quad(tess, x + grid - stroke, y, x + grid, y + grid);
            if ((edges & KOMEMapRadialMenu.Cell.TOP) != 0) quad(tess, x, y, x + grid, y + stroke);
            if ((edges & KOMEMapRadialMenu.Cell.BOTTOM) != 0) quad(tess, x, y + grid - stroke, x + grid, y + grid);
        }
        tess.draw();
    }
    private static void quad(Tessellator tess, double left, double top, double right, double bottom) {
        tess.addVertex(left, top, 0); tess.addVertex(right, top, 0);
        tess.addVertex(right, bottom, 0); tess.addVertex(left, bottom, 0);
    }
}
