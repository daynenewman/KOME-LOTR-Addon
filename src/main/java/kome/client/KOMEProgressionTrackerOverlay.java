package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import kome.common.KOMEAddon;
import kome.common.data.KOMEProgressionTrackerSnapshot;
import kome.common.data.KOMEProgressionVisualItems;
import lotr.client.LOTRTickHandlerClient;
import lotr.common.LOTRConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Properties;

/**
 * A second LOTR-style quest tracker for the player's current KOME progression
 * obligation. The server owns the objective; this class owns presentation and
 * the player's local HUD position only.
 */
public final class KOMEProgressionTrackerOverlay extends Gui {
    private static final ResourceLocation TRACKER_TEXTURE =
        new ResourceLocation("lotr:gui/quest/tracker.png");

    private static final RenderItem RENDER_ITEM =
        new RenderItem();

    private static final int ICON_WIDTH=20;
    private static final int ICON_HEIGHT=20;
    private static final int BAR_WIDTH=90;
    private static final int BAR_HEIGHT=15;
    private static final int BAR_EDGE=2;
    private static final int GAP=4;
    private static final int TOTAL_WIDTH=
        ICON_WIDTH+GAP+BAR_WIDTH;

    private static final int DEFAULT_MARGIN_X=16;
    private static final int DEFAULT_Y=10;

    // Muted Middle-earth gold rather than bright yellow.
    private static final float GOLD_R=0.84F;
    private static final float GOLD_G=0.66F;
    private static final float GOLD_B=0.20F;

    private static final int DRAG_BORDER=0xCCD6AE46;

    private KOMEProgressionTrackerSnapshot snapshot=
        KOMEProgressionTrackerSnapshot.EMPTY;

    private boolean dragging;
    private boolean mouseWasDown;
    private int dragOffsetX;
    private int dragOffsetY;

    private boolean settingsLoaded;
    private boolean hasSavedPosition;
    private float normalizedX;
    private float normalizedY;
    private File settingsFile;

    public void update(
            KOMEProgressionTrackerSnapshot next) {
        snapshot=
            next==null
                ?KOMEProgressionTrackerSnapshot.EMPTY
                :next;
    }

    /** Clears per-server presentation without deleting the user's HUD position. */
    public void resetSession() {
        snapshot=KOMEProgressionTrackerSnapshot.EMPTY;
        dragging=false;
        mouseWasDown=false;
    }

    @SubscribeEvent
    public void onHud(RenderGameOverlayEvent.Post event) {
        if(event.type!=
                RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }

        Minecraft minecraft=Minecraft.getMinecraft();

        if(minecraft.currentScreen!=null) {
            return;
        }

        drawTracker(minecraft,false);
    }

    /**
     * Normal HUD rendering happens before GuiChat. Redraw during chat so the
     * tracker remains visible above the chat screen while it is being moved.
     */
    @SubscribeEvent
    public void onChatDraw(
            GuiScreenEvent.DrawScreenEvent.Post event) {
        if(!(event.gui instanceof GuiChat)) {
            return;
        }

        drawTracker(Minecraft.getMinecraft(),true);
    }

    /**
     * Chat already releases the mouse cursor, so T naturally doubles as the
     * HUD-edit gesture without replacing or intercepting Minecraft's chat key.
     */
    @SubscribeEvent
    public void onClientTick(
            TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft=Minecraft.getMinecraft();

        boolean chatOpen=
            minecraft.currentScreen instanceof GuiChat;

        boolean mouseDown=
            chatOpen&&Mouse.isButtonDown(0);

        if(!chatOpen||!canRender(minecraft)) {
            if(dragging) {
                finishDrag();
            }

            dragging=false;
            mouseWasDown=mouseDown;
            return;
        }

        ensureSettingsLoaded();

        ScaledResolution resolution=
            new ScaledResolution(
                minecraft,
                minecraft.displayWidth,
                minecraft.displayHeight);

        Bounds bounds=bounds(minecraft,resolution);

        int mouseX=
            Mouse.getX()
                *resolution.getScaledWidth()
                /Math.max(1,minecraft.displayWidth);

        int mouseY=
            resolution.getScaledHeight()
                -Mouse.getY()
                    *resolution.getScaledHeight()
                    /Math.max(1,minecraft.displayHeight)
                -1;

        if(mouseDown&&!mouseWasDown
                &&bounds.contains(mouseX,mouseY)) {
            dragging=true;
            dragOffsetX=mouseX-bounds.x;
            dragOffsetY=mouseY-bounds.y;
        }

        if(mouseDown&&dragging) {
            setPositionPixels(
                mouseX-dragOffsetX,
                mouseY-dragOffsetY,
                resolution,
                bounds.height);
        }

        if(!mouseDown&&mouseWasDown&&dragging) {
            finishDrag();
        }

        mouseWasDown=mouseDown;
    }

    private void drawTracker(
            Minecraft minecraft,
            boolean editMode) {
        if(!canRender(minecraft)) {
            return;
        }

        ensureSettingsLoaded();

        ScaledResolution resolution=
            new ScaledResolution(
                minecraft,
                minecraft.displayWidth,
                minecraft.displayHeight);

        Bounds bounds=bounds(minecraft,resolution);

        FontRenderer font=minecraft.fontRenderer;

        boolean rightSide=
            bounds.x+bounds.width/2
                >resolution.getScaledWidth()/2;

        int iconX=
            rightSide
                ?bounds.x+bounds.width-ICON_WIDTH
                :bounds.x;

        int barX=
            rightSide
                ?bounds.x
                :bounds.x+ICON_WIDTH+GAP;

        int y=bounds.y;

        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(
            GL11.GL_SRC_ALPHA,
            GL11.GL_ONE_MINUS_SRC_ALPHA);

        minecraft.getTextureManager().bindTexture(
            TRACKER_TEXTURE);

        GL11.glColor4f(1F,1F,1F,1F);

        drawTexturedModalRect(
            iconX,
            y,
            0,
            0,
            ICON_WIDTH,
            ICON_HEIGHT);

        int iconDrawX=
            iconX+(ICON_WIDTH-16)/2;

        int iconDrawY=
            y+(ICON_HEIGHT-16)/2;

        int meterWidth=
            BAR_WIDTH-BAR_EDGE*2;

        meterWidth=
            Math.round(
                meterWidth*snapshot.completion);

        minecraft.getTextureManager().bindTexture(
            TRACKER_TEXTURE);

        GL11.glColor4f(
            GOLD_R,
            GOLD_G,
            GOLD_B,
            1F);

        drawTexturedModalRect(
            barX+BAR_EDGE,
            y,
            ICON_WIDTH+BAR_EDGE,
            BAR_HEIGHT,
            meterWidth,
            BAR_HEIGHT);

        GL11.glColor4f(1F,1F,1F,1F);

        drawTexturedModalRect(
            barX,
            y,
            ICON_WIDTH,
            0,
            BAR_WIDTH,
            BAR_HEIGHT);

        LOTRTickHandlerClient.drawAlignmentText(
            font,
            barX+BAR_WIDTH/2
                -font.getStringWidth(snapshot.progress)/2,
            y+BAR_HEIGHT
                -BAR_HEIGHT/2
                -font.FONT_HEIGHT/2,
            snapshot.progress,
            1F);

        font.drawSplitString(
            snapshot.objective,
            barX,
            y+BAR_HEIGHT+GAP,
            BAR_WIDTH,
            0xFFFFFF);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);

        ItemStack icon=icon(snapshot.iconKey);

        if(icon!=null) {
            RenderHelper.enableGUIStandardItemLighting();

            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(32826);
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1F,1F,1F,1F);

            RENDER_ITEM
                .renderItemAndEffectIntoGUI(
                    font,
                    minecraft.getTextureManager(),
                    icon,
                    iconDrawX,
                    iconDrawY);

            GL11.glDisable(GL11.GL_LIGHTING);
        }

        GL11.glColor4f(1F,1F,1F,1F);

        if(editMode) {
            int mouseX=
                Mouse.getX()
                    *resolution.getScaledWidth()
                    /Math.max(1,minecraft.displayWidth);

            int mouseY=
                resolution.getScaledHeight()
                    -Mouse.getY()
                        *resolution.getScaledHeight()
                        /Math.max(1,minecraft.displayHeight)
                    -1;

            if(dragging||bounds.contains(mouseX,mouseY)) {
                drawDragOutline(bounds);
            }
        }
    }

    private boolean canRender(Minecraft minecraft) {
        return snapshot!=null
            &&snapshot.visible
            &&minecraft!=null
            &&minecraft.thePlayer!=null
            &&minecraft.theWorld!=null
            &&Minecraft.isGuiEnabled()
            &&!minecraft.gameSettings.showDebugInfo;
    }

    private Bounds bounds(
            Minecraft minecraft,
            ScaledResolution resolution) {
        FontRenderer font=minecraft.fontRenderer;

        List lines=
            font.listFormattedStringToWidth(
                snapshot.objective,
                BAR_WIDTH);

        int lineCount=
            Math.max(1,lines==null?0:lines.size());

        int textHeight=
            lineCount*font.FONT_HEIGHT;

        int height=
            Math.max(
                ICON_HEIGHT,
                BAR_HEIGHT+GAP+textHeight);

        int maxX=
            Math.max(
                0,
                resolution.getScaledWidth()-TOTAL_WIDTH);

        int maxY=
            Math.max(
                0,
                resolution.getScaledHeight()-height);

        int x;
        int y;

        if(hasSavedPosition) {
            x=Math.round(normalizedX*maxX);
            y=Math.round(normalizedY*maxY);
        } else {
            // Native LOTR tracker on left -> KOME defaults right, and vice versa.
            x=LOTRConfig.trackingQuestRight
                ?Math.min(DEFAULT_MARGIN_X,maxX)
                :Math.max(0,maxX-DEFAULT_MARGIN_X);

            y=Math.min(DEFAULT_Y,maxY);
        }

        x=clamp(x,0,maxX);
        y=clamp(y,0,maxY);

        return new Bounds(
            x,
            y,
            TOTAL_WIDTH,
            height);
    }

    private void setPositionPixels(
            int x,
            int y,
            ScaledResolution resolution,
            int trackerHeight) {
        int maxX=
            Math.max(
                0,
                resolution.getScaledWidth()-TOTAL_WIDTH);

        int maxY=
            Math.max(
                0,
                resolution.getScaledHeight()-trackerHeight);

        x=clamp(x,0,maxX);
        y=clamp(y,0,maxY);

        normalizedX=
            maxX<=0?0F:x/(float)maxX;

        normalizedY=
            maxY<=0?0F:y/(float)maxY;

        normalizedX=clamp01(normalizedX);
        normalizedY=clamp01(normalizedY);

        hasSavedPosition=true;
    }

    private void finishDrag() {
        if(dragging&&hasSavedPosition) {
            saveSettings();
        }

        dragging=false;
    }

    private void ensureSettingsLoaded() {
        if(settingsLoaded) {
            return;
        }

        settingsLoaded=true;

        Minecraft minecraft=Minecraft.getMinecraft();

        File configDirectory=
            new File(
                minecraft.mcDataDir,
                "config");

        settingsFile=
            new File(
                configDirectory,
                "kome-client.properties");

        if(!settingsFile.isFile()) {
            return;
        }

        Properties properties=new Properties();

        try {
            FileInputStream input=
                new FileInputStream(settingsFile);

            try {
                properties.load(input);
            } finally {
                input.close();
            }

            float x=
                Float.parseFloat(
                    properties.getProperty(
                        "progressionTracker.x",
                        "-1"));

            float y=
                Float.parseFloat(
                    properties.getProperty(
                        "progressionTracker.y",
                        "-1"));

            if(validNormalized(x)
                    &&validNormalized(y)) {
                normalizedX=x;
                normalizedY=y;
                hasSavedPosition=true;
            }
        } catch(Exception ignored) {
            // Invalid or old client preferences simply fall back to the native default.
            hasSavedPosition=false;
        }
    }

    private void saveSettings() {
        ensureSettingsLoaded();

        if(settingsFile==null) {
            return;
        }

        Properties properties=new Properties();

        try {
            if(settingsFile.isFile()) {
                FileInputStream input=
                    new FileInputStream(settingsFile);

                try {
                    properties.load(input);
                } finally {
                    input.close();
                }
            }

            File parent=settingsFile.getParentFile();

            if(parent!=null&&!parent.isDirectory()) {
                parent.mkdirs();
            }

            properties.setProperty(
                "progressionTracker.x",
                Float.toString(normalizedX));

            properties.setProperty(
                "progressionTracker.y",
                Float.toString(normalizedY));

            FileOutputStream output=
                new FileOutputStream(settingsFile);

            try {
                properties.store(
                    output,
                    "KOME client-only HUD preferences");
            } finally {
                output.close();
            }
        } catch(Exception ignored) {
            // HUD positioning is convenience-only; never affect gameplay if disk I/O fails.
        }
    }

    private static ItemStack icon(String key) {
        if("provisioning".equals(key)) {
            return new ItemStack(Items.bread);
        }

        if("profession".equals(key)) {
            return new ItemStack(Items.iron_ingot);
        }

        if("courier".equals(key)) {
            return new ItemStack(
                KOMEAddon.sealedMessage!=null
                    ?KOMEAddon.sealedMessage
                    :Items.paper);
        }

        if("escort".equals(key)) {
            return new ItemStack(Items.saddle);
        }

        if("recovery".equals(key)) {
            return new ItemStack(Items.gold_ingot);
        }

        if("defense".equals(key)) {
            return new ItemStack(Items.iron_sword);
        }

        return new ItemStack(
            KOMEProgressionVisualItems.RELATIONSHIP);
    }

    private void drawDragOutline(Bounds bounds) {
        int x0=bounds.x-2;
        int y0=bounds.y-2;
        int x1=bounds.x+bounds.width+2;
        int y1=bounds.y+bounds.height+2;

        drawRect(
            x0,
            y0,
            x1,
            y0+1,
            DRAG_BORDER);

        drawRect(
            x0,
            y1-1,
            x1,
            y1,
            DRAG_BORDER);

        drawRect(
            x0,
            y0,
            x0+1,
            y1,
            DRAG_BORDER);

        drawRect(
            x1-1,
            y0,
            x1,
            y1,
            DRAG_BORDER);
    }

    private static boolean validNormalized(float value) {
        return !Float.isNaN(value)
            &&!Float.isInfinite(value)
            &&value>=0F
            &&value<=1F;
    }

    private static float clamp01(float value) {
        return Math.max(
            0F,
            Math.min(1F,value));
    }

    private static int clamp(
            int value,
            int minimum,
            int maximum) {
        return Math.max(
            minimum,
            Math.min(maximum,value));
    }

    private static final class Bounds {
        final int x,y,width,height;

        Bounds(
                int x,
                int y,
                int width,
                int height) {
            this.x=x;
            this.y=y;
            this.width=width;
            this.height=height;
        }

        boolean contains(
                int mouseX,
                int mouseY) {
            return mouseX>=x
                &&mouseX<x+width
                &&mouseY>=y
                &&mouseY<y+height;
        }
    }
}
