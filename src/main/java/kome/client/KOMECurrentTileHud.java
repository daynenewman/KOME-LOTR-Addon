package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.Objects;
import kome.client.gui.KOMEGuiTheme;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMETileResolution;
import kome.common.data.KOMETileWaypointLink;
import kome.common.data.KOMETileWorldResolver;
import lotr.common.LOTRDimension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Client-thread presentation of the common resolver; never owns tile geometry or server state. */
public final class KOMECurrentTileHud implements IResourceManagerReloadListener {
    private final Minecraft client;
    private final KOMEClientConfig config;
    final KeyBinding toggle = new KeyBinding("key.kome.currentTileHud", Keyboard.KEY_NONE, "key.categories.kome");
    // Network events close this gate immediately; session reset/publication uses the existing client queue.
    private volatile boolean sessionActive;
    private long sessionGeneration;
    private WorldClient sampledWorld;
    private EntityClientPlayerMP sampledPlayer;
    private KOMETileResolution location;
    private String label = "";
    private String displayedId = "";
    private KOMETileResolution.Status displayedStatus;
    private KOMETileWaypointLink displayedLink;
    private String waypointKey, waypointName, language;
    private boolean refreshText = true;
    private String renderedLabel;
    private String fittedLabel = "";
    private FontRenderer renderedFont;
    private int renderedLimit = -1;

    KOMECurrentTileHud(Minecraft client, KOMEClientConfig config) {
        this.client = client;
        this.config = config;
    }

    /** Safe from the FML connection thread; actual clearing happens on the client thread. */
    synchronized long suspendSession() { sessionActive = false; return ++sessionGeneration; }

    synchronized void startSession(long expectedGeneration) {
        if (sessionGeneration != expectedGeneration) return;
        clear();
        sessionActive = true;
    }

    void clear() {
        sampledWorld = null;
        sampledPlayer = null;
        location = null;
        label = "";
        displayedId = "";
        displayedStatus = null;
        displayedLink = null;
        waypointKey = waypointName = language = null;
        renderedLabel = null;
        refreshText = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (toggle.isPressed()) {
            if (sessionActive && client.theWorld != null && client.thePlayer != null && client.currentScreen == null) {
                config.setShowCurrentTile(!config.showCurrentTile());
            }
        }
        if (!sessionActive || client.theWorld == null || client.thePlayer == null
                || client.thePlayer.worldObj != client.theWorld || client.theWorld.provider == null
                || client.thePlayer.isDead) {
            clear();
            return;
        }
        if (sampledWorld != client.theWorld || sampledPlayer != client.thePlayer) clear();
        sampledWorld = client.theWorld;
        sampledPlayer = client.thePlayer;
        // Resolve every tick: neither movement nor a snapshot replacement can leave a cached identity.
        location = KOMETileWorldResolver.INSTANCE.resolveWorldPosition(
            sampledWorld.provider.dimensionId, sampledPlayer.posX, sampledPlayer.posZ);
        if (sampledWorld.provider.dimensionId != LOTRDimension.MIDDLE_EARTH.dimensionID
                || location.status == KOMETileResolution.Status.UNSUPPORTED_DIMENSION) {
            label = "";
            displayedStatus = null;
            refreshText = true;
            return;
        }
        KOMETileWaypointLink link = location.status == KOMETileResolution.Status.RESOLVED
            ? KOMEClientData.INSTANCE.tileWaypointLinksByTileId.get(location.tileId) : null;
        String key = link == null ? null : link.lotrWaypointKey;
        String name = link == null ? null : link.waypointDisplayName;
        String currentLanguage = client.gameSettings.language;
        if (!refreshText && displayedStatus == location.status && displayedId.equals(location.tileId)
                && displayedLink == link && Objects.equals(waypointKey, key)
                && Objects.equals(waypointName, name) && Objects.equals(language, currentLanguage)) return;
        displayedStatus = location.status;
        displayedId = location.tileId;
        displayedLink = link;
        waypointKey = key;
        waypointName = name;
        language = currentLanguage;
        refreshText = false;
        switch (location.status) {
            case RESOLVED:
                // Use the same existing localized place metadata as the conquest map.
                String place = link == null ? "" : link.displayName();
                place = place == null ? "" : place.trim();
                // A missing name is not permission to invent one from an unknown waypoint key.
                if (link != null && (place.equals(key) || place.equals("lotr.waypoint." + key))) {
                    place = name == null ? "" : name.trim();
                }
                label = place.isEmpty() ? location.tileId : location.tileId + " - " + place;
                break;
            case IN_BOUNDS_GAP: label = I18n.format("kome.hud.tile.gap"); break;
            case OUTSIDE_MASK: label = I18n.format("kome.hud.tile.outside"); break;
            default: label = I18n.format("kome.hud.tile.unavailable"); break;
        }
    }

    boolean visible() {
        return sessionActive && config.showCurrentTile() && !label.isEmpty()
            && client.theWorld != null && client.theWorld == sampledWorld
            && client.thePlayer != null && client.thePlayer == sampledPlayer
            && !client.thePlayer.isDead && client.thePlayer.worldObj == sampledWorld
            && sampledWorld.provider != null
            && sampledWorld.provider.dimensionId == LOTRDimension.MIDDLE_EARTH.dimensionID
            && client.currentScreen == null && !client.gameSettings.hideGUI
            && !client.gameSettings.showDebugInfo && !client.gameSettings.keyBindPlayerList.getIsKeyPressed();
    }

    KOMETileResolution location() { return sessionActive ? location : null; }
    String label() { return sessionActive ? label : ""; }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        refreshText = true;
        renderedLabel = null;
    }

    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !visible()) return;
        FontRenderer font = client.fontRenderer;
        // Below LOTR's default alignment/boss/invasion area; above lower-left racial meters.
        int x = 6, y = 76;
        int limit = Math.min(200, event.resolution.getScaledWidth() / 2 - 16);
        if (limit < 24 || y + font.FONT_HEIGHT + 6 > event.resolution.getScaledHeight() - 90) return;
        if (!label.equals(renderedLabel) || renderedLimit != limit || renderedFont != font) {
            fittedLabel = font.getStringWidth(label) <= limit ? label
                : font.trimStringToWidth(label, Math.max(0, limit - font.getStringWidth("..."))) + "...";
            renderedLabel = label;
            renderedLimit = limit;
            renderedFont = font;
        }
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            Gui.drawRect(x - 3, y - 3, x + font.getStringWidth(fittedLabel) + 3, y + font.FONT_HEIGHT + 3,
                KOMEGuiTheme.COLOR_SHADOW);
            font.drawStringWithShadow(fittedLabel, x, y, KOMEGuiTheme.COLOR_TEXT);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
