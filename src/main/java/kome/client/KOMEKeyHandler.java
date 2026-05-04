package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.world.map.LOTRAbstractWaypoint;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;

public class KOMEKeyHandler {
    private static Field selectedWaypointField;
    private boolean territoryWasDown;

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (KOMEClientProxy.populationGuiKey.getIsKeyPressed() && mc.thePlayer != null && mc.currentScreen == null) {
            mc.thePlayer.sendChatMessage("/population gui " + mc.thePlayer.getCommandSenderName());
        }
        if (KOMEClientProxy.territoryGuiKey.getIsKeyPressed()) {
            openTerritoryGui(mc);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        boolean territoryDown = Keyboard.isKeyDown(KOMEClientProxy.territoryGuiKey.getKeyCode());
        if (territoryDown && !territoryWasDown) {
            openTerritoryGui(mc);
        }
        territoryWasDown = territoryDown;
    }

    private void openTerritoryGui(Minecraft mc) {
        if (mc.thePlayer == null || !(mc.currentScreen instanceof LOTRGuiMap)) {
            return;
        }
        LOTRAbstractWaypoint selected = getSelectedWaypoint((LOTRGuiMap) mc.currentScreen);
        if (selected instanceof LOTRWaypoint) {
            mc.thePlayer.sendChatMessage("/territory gui " + ((LOTRWaypoint) selected).getCodeName());
        }
    }

    public static LOTRAbstractWaypoint getSelectedWaypoint(LOTRGuiMap map) {
        try {
            if (selectedWaypointField == null) {
                selectedWaypointField = LOTRGuiMap.class.getDeclaredField("selectedWaypoint");
                selectedWaypointField.setAccessible(true);
            }
            Object selected = selectedWaypointField.get(map);
            return selected instanceof LOTRAbstractWaypoint ? (LOTRAbstractWaypoint) selected : null;
        } catch (Exception e) {
            return null;
        }
    }
}
