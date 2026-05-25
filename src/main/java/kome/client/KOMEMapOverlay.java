package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMETerritory;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.world.map.LOTRAbstractWaypoint;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.client.event.GuiScreenEvent;

import java.util.ArrayList;
import java.util.List;

public class KOMEMapOverlay {
    @SubscribeEvent
    public void onDrawMap(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof LOTRGuiMap)) {
            return;
        }

        LOTRAbstractWaypoint selected = KOMEKeyHandler.getSelectedWaypoint((LOTRGuiMap) event.gui);
        if (!(selected instanceof LOTRWaypoint)) {
            return;
        }

        KOMETerritory territory = KOMEClientData.INSTANCE.territories.get(((LOTRWaypoint) selected).getCodeName());
        if (territory == null || isBlank(territory.displayName) && isBlank(territory.faction) && isBlank(territory.ruler)) {
            return;
        }

        List<String> lines = new ArrayList<>();
        if (!isBlank(territory.displayName)) {
            lines.add("Display Name: " + territory.displayName);
        }
        if (!isBlank(territory.faction)) {
            lines.add("Ruling Faction: " + territory.faction);
        }
        if (!isBlank(territory.ruler)) {
            lines.add("Ruling Player: " + territory.ruler);
        }
        FontRenderer font = KOMEMinecraftClient.fontRenderer();
        drawPanel(font, lines, event.gui.width - getPanelWidth(font, lines) - 36, 68);
    }

    private static void drawPanel(FontRenderer font, List<String> lines, int x, int y) {
        int width = getPanelWidth(font, lines);
        int height = lines.size() * 10 + 8;
        Gui.drawRect(x, y, x + width, y + height, 0xCC1F1F1F);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, 0xAA2B3322);
        for (int i = 0; i < lines.size(); i++) {
            font.drawStringWithShadow(lines.get(i), x + 6, y + 5 + i * 10, 0xFFFFFF);
        }
    }

    private static int getPanelWidth(FontRenderer font, List<String> lines) {
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.getStringWidth(line));
        }
        return width + 12;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
