package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraftforge.client.event.GuiScreenEvent;
import kome.common.KOMEReflection;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketUnitCapRequest;
import kome.common.network.KOMEPacketUnitCapUpdate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KOMEUnitOverviewCapOverlay {
    private static final String OVERVIEW_CLASS = "metweaks.client.gui.unitoverview.GuiUnitOverview";
    private static final int BUTTON_BASE = 9700;
    private static final int BUTTONS_PER_ROW = 3;
    private static final int MAX_BUTTON_ROWS = 18;
    private final Set<Integer> requestedSyncs = new HashSet<>();
    private GuiScreen lastGui;

    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!isUnitOverview(event.gui)) {
            return;
        }
        for (int row = 0; row < MAX_BUTTON_ROWS; row++) {
            event.buttonList.add(new GuiButton(buttonId(row, -1), 0, 0, 13, 12, "-"));
            event.buttonList.add(new GuiButton(buttonId(row, 1), 0, 0, 13, 12, "+"));
            event.buttonList.add(new GuiButton(buttonId(row, 0), 0, 0, 13, 12, "x"));
        }
        updateButtons(event.gui);
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!isUnitOverview(event.gui) || event.button.id < BUTTON_BASE || event.button.id >= BUTTON_BASE + MAX_BUTTON_ROWS * BUTTONS_PER_ROW) {
            return;
        }
        GuiSlot list = getUnitList(event.gui);
        List entries = getEntries(list);
        if (list == null || entries == null) {
            return;
        }
        int row = (event.button.id - BUTTON_BASE) / BUTTONS_PER_ROW;
        int entryIndex = firstVisibleRow(list) + row;
        if (entryIndex < 0 || entryIndex >= entries.size()) {
            return;
        }
        LOTREntityNPC npc = getNpc(entries.get(entryIndex));
        if (npc == null) {
            return;
        }
        sendCapUpdate(npc, actionFromButton(event.button.id));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!isUnitOverview(event.gui)) {
            return;
        }
        if (lastGui != event.gui) {
            requestedSyncs.clear();
            lastGui = event.gui;
        }
        GuiSlot list = getUnitList(event.gui);
        List entries = getEntries(list);
        if (list == null || entries == null || entries.isEmpty()) {
            return;
        }
        updateButtons(event.gui);
        int x = getCapColumnX(event.gui, list);
        Minecraft.getMinecraft().fontRenderer.drawString("Cap", x, Math.max(0, list.top - 16), 0xFFFFFF);
        int rowStart = list.top + 4 - list.getAmountScrolled();
        for (int i = 0; i < entries.size(); i++) {
            int rowY = rowStart + i * list.slotHeight;
            if (rowY > list.bottom || rowY + list.slotHeight < list.top) {
                continue;
            }
            LOTREntityNPC npc = getNpc(entries.get(i));
            if (npc == null) {
                continue;
            }
            int entityId = KOMEReflection.getEntityId(npc);
            requestSync(entityId);
            drawCapText(entityId, x, rowY + Math.max(2, (list.slotHeight - 12) / 2));
        }
    }

    private void updateButtons(GuiScreen gui) {
        GuiSlot list = getUnitList(gui);
        List entries = getEntries(list);
        List buttons = getButtonList(gui);
        if (buttons == null) {
            return;
        }
        int x = list == null ? 0 : getCapColumnX(gui, list);
        int first = list == null ? 0 : firstVisibleRow(list);
        for (Object object : buttons) {
            if (!(object instanceof GuiButton)) {
                continue;
            }
            GuiButton button = (GuiButton) object;
            if (button.id < BUTTON_BASE || button.id >= BUTTON_BASE + MAX_BUTTON_ROWS * BUTTONS_PER_ROW) {
                continue;
            }
            int row = (button.id - BUTTON_BASE) / BUTTONS_PER_ROW;
            int action = actionFromButton(button.id);
            int entryIndex = first + row;
            int rowY = list == null ? 0 : list.top + 4 - list.getAmountScrolled() + entryIndex * list.slotHeight;
            boolean visible = list != null && entries != null && entryIndex >= 0 && entryIndex < entries.size() && rowY <= list.bottom && rowY + list.slotHeight >= list.top;
            button.visible = visible;
            button.enabled = visible;
            button.xPosition = x + (action == -1 ? 24 : action == 1 ? 40 : 56);
            button.yPosition = rowY + Math.max(2, (list == null ? 16 : list.slotHeight - 12) / 2);
            button.displayString = action == -1 ? "-" : action == 1 ? "+" : "x";
        }
    }

    private void drawCapText(int entityId, int x, int y) {
        int cap = KOMEUnitCapClientState.getCap(entityId);
        Minecraft.getMinecraft().fontRenderer.drawString(cap < 0 ? "..." : cap > 0 ? String.valueOf(cap) : "-", x, y + 2, cap > 0 ? 0x55FF55 : 0xFFAA55);
    }

    private void sendCapUpdate(LOTREntityNPC npc, int action) {
        int entityId = KOMEReflection.getEntityId(npc);
        int cap = KOMEUnitCapClientState.getCap(entityId);
        int level = action == 0 ? 0 : Math.max(1, (cap > 0 ? cap : Math.max(1, npc.hiredNPCInfo.xpLevel)) + action);
        KOMEUnitCapClientState.setCap(entityId, level);
        KOMEPacketHandler.network.sendToServer(new KOMEPacketUnitCapUpdate(entityId, level));
    }

    private void requestSync(int entityId) {
        if (requestedSyncs.add(Integer.valueOf(entityId))) {
            KOMEPacketHandler.network.sendToServer(new KOMEPacketUnitCapRequest(entityId));
        }
    }

    private int getCapColumnX(GuiScreen gui, GuiSlot list) {
        int listLeft = invokeInt(list, new String[] {"getListLeft"}, list.width / 2 - invokeInt(list, new String[] {"func_148139_c", "getListWidth"}, 220) / 2);
        int listWidth = invokeInt(list, new String[] {"func_148139_c", "getListWidth"}, 220);
        return Math.min(gui.width - 86, listLeft + listWidth + 10);
    }

    private int firstVisibleRow(GuiSlot list) {
        return Math.max(0, list.getAmountScrolled() / list.slotHeight);
    }

    private int buttonId(int row, int action) {
        return BUTTON_BASE + row * BUTTONS_PER_ROW + (action == -1 ? 0 : action == 1 ? 1 : 2);
    }

    private int actionFromButton(int buttonId) {
        int index = (buttonId - BUTTON_BASE) % BUTTONS_PER_ROW;
        return index == 0 ? -1 : index == 1 ? 1 : 0;
    }

    private boolean isUnitOverview(GuiScreen gui) {
        return gui != null && OVERVIEW_CLASS.equals(gui.getClass().getName());
    }

    private GuiSlot getUnitList(GuiScreen gui) {
        Object list = getField(gui, "unitsList");
        return list instanceof GuiSlot ? (GuiSlot) list : null;
    }

    private List getEntries(GuiSlot list) {
        Object entries = getField(list, "entrys");
        return entries instanceof List ? (List) entries : null;
    }

    private List getButtonList(GuiScreen gui) {
        Object buttons = getField(gui, "buttonList", "field_146292_n");
        return buttons instanceof List ? (List) buttons : null;
    }

    private LOTREntityNPC getNpc(Object entry) {
        Object npc = getField(entry, "npc");
        return npc instanceof LOTREntityNPC ? (LOTREntityNPC) npc : null;
    }

    private Object getField(Object target, String name) {
        return getField(target, name, null);
    }

    private Object getField(Object target, String name, String fallbackName) {
        if (target == null) {
            return null;
        }
        for (Class clazz = target.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Exception ignored) {
            }
            if (fallbackName != null) {
                try {
                    Field field = clazz.getDeclaredField(fallbackName);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private int invokeInt(Object target, String[] names, int fallback) {
        for (Class clazz = target.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            for (String name : names) {
                try {
                    Method method = clazz.getDeclaredMethod(name);
                    method.setAccessible(true);
                    return ((Integer) method.invoke(target)).intValue();
                } catch (Exception ignored) {
                }
            }
        }
        return fallback;
    }
}
