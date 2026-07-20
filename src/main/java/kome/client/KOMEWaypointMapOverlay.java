package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEWaypointAccessService;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketWaypointTravelRequest;
import lotr.client.LOTRKeyHandler;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.world.map.LOTRAbstractWaypoint;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.GuiScreenEvent;

import java.lang.reflect.Field;

/** Honest client presentation and input bridge; the server independently repeats every check. */
public final class KOMEWaypointMapOverlay {
    private static Field selectedWaypointField;
    private static boolean selectedWaypointLookupAttempted;
    private static boolean selectedWaypointFailureLogged;
    private boolean fastTravelKeyWasDown;

    @SubscribeEvent
    public void onDrawMap(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof LOTRGuiMap)) {
            return;
        }
        LOTRGuiMap map = (LOTRGuiMap) event.gui;
        LOTRAbstractWaypoint waypoint = getSelectedWaypoint(map);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (waypoint == null || minecraft.thePlayer == null) {
            return;
        }
        boolean progression = KOMEWaypointAccessService.hasNativeProgression(minecraft.thePlayer, waypoint);
        KOMEWaypointAccessService.Decision decision = KOMEWaypointAccessService.evaluatePlayer(minecraft.thePlayer, waypoint, progression);
        drawDecision(event.gui.width, decision);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean keyDown = LOTRKeyHandler.keyBindingFastTravel.getIsKeyPressed();
        boolean newlyPressed = keyDown && !fastTravelKeyWasDown;
        fastTravelKeyWasDown = keyDown;
        if (!newlyPressed || !(minecraft.currentScreen instanceof LOTRGuiMap)) {
            return;
        }
        LOTRGuiMap map = (LOTRGuiMap) minecraft.currentScreen;
        LOTRAbstractWaypoint selectedWaypoint = getSelectedWaypoint(map);
        if (!(selectedWaypoint instanceof LOTRWaypoint) || minecraft.thePlayer == null) {
            return;
        }
        LOTRWaypoint waypoint = (LOTRWaypoint) selectedWaypoint;
        boolean nativeUnlocked = waypoint.hasPlayerUnlocked(minecraft.thePlayer);
        boolean progression = KOMEWaypointAccessService.hasNativeProgression(minecraft.thePlayer, waypoint);
        KOMEWaypointAccessService.Decision decision = KOMEWaypointAccessService.evaluatePlayer(minecraft.thePlayer, waypoint, progression);
        if (decision.state == KOMEWaypointAccessService.State.DISABLED
                || decision.state == KOMEWaypointAccessService.State.UNMAPPED) {
            return;
        }
        if (!decision.territoryAllowed) {
            minecraft.thePlayer.addChatMessage(new ChatComponentText("Fast travel denied: " + decision.reason));
            return;
        }
        if (!progression || nativeUnlocked) {
            return;
        }
        LOTRPlayerData playerData = LOTRLevelData.getData(minecraft.thePlayer);
        if (playerData.getTimeSinceFT() < playerData.getWaypointFTTime(waypoint, minecraft.thePlayer)) {
            return;
        }
        KOMEPacketHandler.network.sendToServer(new KOMEPacketWaypointTravelRequest(waypoint));
        minecraft.thePlayer.closeScreen();
    }

    private static LOTRAbstractWaypoint getSelectedWaypoint(LOTRGuiMap map) {
        if (map == null) {
            return null;
        }
        if (!selectedWaypointLookupAttempted) {
            selectedWaypointLookupAttempted = true;
            try {
                selectedWaypointField = LOTRGuiMap.class.getDeclaredField("selectedWaypoint");
                selectedWaypointField.setAccessible(true);
            } catch (Throwable failure) {
                logSelectedWaypointFailure(failure);
            }
        }
        if (selectedWaypointField == null) {
            return null;
        }
        try {
            Object value = selectedWaypointField.get(map);
            return value instanceof LOTRAbstractWaypoint ? (LOTRAbstractWaypoint) value : null;
        } catch (Throwable failure) {
            selectedWaypointField = null;
            logSelectedWaypointFailure(failure);
            return null;
        }
    }

    private static void logSelectedWaypointFailure(Throwable failure) {
        if (!selectedWaypointFailureLogged) {
            selectedWaypointFailureLogged = true;
            System.err.println("[KOME] Could not inspect the selected LOTR map waypoint; native map rendering will continue without the KOME waypoint overlay. "
                + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private void drawDecision(int screenWidth, KOMEWaypointAccessService.Decision decision) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String owner = decision.tileOwner.length() == 0 ? "Unclaimed" : KOMEAlliance.displayFactionName(decision.tileOwner);
        String state;
        int color;
        switch (decision.state) {
            case OWN:
                state = "OWN TERRITORY";
                color = 0xFF55FF55;
                break;
            case ALLY:
                state = "CIVIL T1 ACCESS";
                color = 0xFF55AAFF;
                break;
            case UNCLAIMED:
                state = "UNCLAIMED";
                color = 0xFFFFFF55;
                break;
            case DENIED:
                state = "ACCESS DENIED";
                color = 0xFFFF5555;
                break;
            case BYPASS:
                state = "ADMIN BYPASS";
                color = 0xFFFFAA00;
                break;
            default:
                state = "NATIVE LOTR RULES";
                color = 0xFFAAAAAA;
                break;
        }
        String line1 = "KOME destination: " + state + " | Owner: " + owner;
        String line2 = decision.reason;
        int width = Math.min(screenWidth - 16, Math.max(font.getStringWidth(line1), font.getStringWidth(line2)) + 12);
        int x = (screenWidth - width) / 2;
        Gui.drawRect(x, 8, x + width, 34, 0xCC101820);
        font.drawStringWithShadow(trim(font, line1, width - 8), x + 4, 12, color);
        font.drawStringWithShadow(trim(font, line2, width - 8), x + 4, 22, 0xFFE0E0E0);
    }

    private String trim(FontRenderer font, String value, int width) {
        if (font.getStringWidth(value) <= width) {
            return value;
        }
        return font.trimStringToWidth(value, Math.max(0, width - font.getStringWidth("..."))) + "...";
    }
}
