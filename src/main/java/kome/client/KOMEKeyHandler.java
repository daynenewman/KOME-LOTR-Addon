package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.world.map.LOTRAbstractWaypoint;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;

public class KOMEKeyHandler {
    private static Field selectedWaypointField;
    private boolean territoryWasDown;

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (KOMEClientProxy.populationGuiKey.getIsKeyPressed() && KOMEMinecraftClient.player() != null && KOMEMinecraftClient.currentScreen() == null) {
            KOMEMinecraftClient.sendChat("/population gui " + KOMEMinecraftClient.playerName());
        }
        if (KOMEClientProxy.territoryGuiKey.getIsKeyPressed()) {
            openTerritoryGui();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        boolean territoryDown = Keyboard.isKeyDown(KOMEClientProxy.TERRITORY_GUI_KEY_CODE);
        if (territoryDown && !territoryWasDown) {
            openTerritoryGui();
        }
        territoryWasDown = territoryDown;
    }

    private void openTerritoryGui() {
        GuiScreen screen = KOMEMinecraftClient.currentScreen();
        if (KOMEMinecraftClient.player() == null || !(screen instanceof LOTRGuiMap)) {
            return;
        }
        LOTRAbstractWaypoint selected = getSelectedWaypoint((LOTRGuiMap) screen);
        if (selected instanceof LOTRWaypoint) {
            KOMEMinecraftClient.sendChat("/territory gui " + ((LOTRWaypoint) selected).getCodeName());
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
