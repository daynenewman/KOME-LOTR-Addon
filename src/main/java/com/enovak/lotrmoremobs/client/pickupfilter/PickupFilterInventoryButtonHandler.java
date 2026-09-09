package com.enovak.lotrmoremobs.client.pickupfilter;

import com.enovak.lotrmoremobs.client.gui.GuiPickupFilterInventoryButton;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import lotr.common.LOTRMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.gui.inventory.GuiScreenHorseInventory;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraftforge.client.event.GuiScreenEvent;
import java.util.Iterator;
import java.util.List;

/** Adds and maintains the pickup-filter entry button in both inventories. */
public class PickupFilterInventoryButtonHandler {

    private static final int PICKUP_FILTER_BUTTON_ID = 42001;
    private static final int BUTTON_GAP = 2;
    private static final int MOUNT_BUTTON_GAP = 4;

    private static final String NPC_MOUNT_GUI_CLASS =
            "lotr.client.gui.LOTRGuiNPCMountInventory";

    private static final int SURVIVAL_GUI_WIDTH = 176;
    private static final int SURVIVAL_GUI_HEIGHT = 166;
    private static final int SURVIVAL_POUCH_X = 159;
    private static final int SURVIVAL_POUCH_Y = 128;

    private static final int CREATIVE_GUI_WIDTH = 195;
    private static final int CREATIVE_GUI_HEIGHT = 136;
    private static final int CREATIVE_POUCH_X = 160;
    private static final int CREATIVE_POUCH_Y = 40;

    private static final String POUCH_BUTTON_CLASS =
            "lotr.client.gui.LOTRGuiButtonRestockPouch";

    private final PickupFilterTooltipRenderer tooltipRenderer =
            new PickupFilterTooltipRenderer();

    private GuiPickupFilterInventoryButton pickupFilterButton;

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!isSupportedInventory(event.gui)
                || event.gui != Minecraft.getMinecraft().currentScreen) {
            return;
        }

        // Forge or another mod can initialize a GUI more than once. Remove
        // stale instances so this handler always owns exactly one button.
        Iterator iterator = event.buttonList.iterator();
        while (iterator.hasNext()) {
            Object object = iterator.next();
            if (object instanceof GuiPickupFilterInventoryButton
                    || object instanceof GuiButton
                    && ((GuiButton) object).id
                    == PICKUP_FILTER_BUTTON_ID) {
                iterator.remove();
            }
        }

        pickupFilterButton = new GuiPickupFilterInventoryButton(
                PICKUP_FILTER_BUTTON_ID,
                0,
                0
        );
        event.buttonList.add(pickupFilterButton);
        updateButtonPosition(event.gui, event.buttonList);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrawScreenPre(
            GuiScreenEvent.DrawScreenEvent.Pre event
    ) {
        if (isSupportedInventory(event.gui)) {
            List buttonList = getCurrentButtonList(event.gui);
            refreshCurrentInventory(buttonList);
            if (pickupFilterButton != null && buttonList != null) {
                updateButtonPosition(event.gui, buttonList);
            }
        }
    }

    @SubscribeEvent
    public void onDrawScreenPost(
            GuiScreenEvent.DrawScreenEvent.Post event
    ) {
        if (!isSupportedInventory(event.gui)) {
            return;
        }

        List buttonList = getCurrentButtonList(event.gui);
        refreshCurrentInventory(buttonList);
        if (pickupFilterButton == null || buttonList == null) {
            return;
        }
        updateButtonPosition(event.gui, buttonList);

        if (!pickupFilterButton.visible) {
            return;
        }

        boolean hovering =
                event.mouseX >= pickupFilterButton.xPosition
                        && event.mouseY >= pickupFilterButton.yPosition
                        && event.mouseX
                        < pickupFilterButton.xPosition
                        + pickupFilterButton.width
                        && event.mouseY
                        < pickupFilterButton.yPosition
                        + pickupFilterButton.height;

        if (hovering) {
            tooltipRenderer.drawTooltip(
                    event.gui,
                    "Pickup Filter",
                    event.mouseX,
                    event.mouseY
            );
        }
    }

    @SubscribeEvent
    public void onButtonPressed(
            GuiScreenEvent.ActionPerformedEvent.Post event
    ) {
        if (!isSupportedInventory(event.gui)
                || !isPickupFilterTabAllowed(event.gui)
                || event.button == null
                || event.button.id != PICKUP_FILTER_BUTTON_ID
                || !event.button.visible
                || !event.button.enabled) {
            return;
        }

        PickupFilterGuiOpenHandler.requestOpen(
                event.gui instanceof GuiContainerCreative
        );
    }

    private void updateButtonPosition(
            GuiScreen gui,
            List buttonList
    ) {
        pickupFilterButton.visible = isPickupFilterTabAllowed(gui);
        pickupFilterButton.enabled = pickupFilterButton.visible;
        if (!pickupFilterButton.visible) {
            return;
        }

        GuiButton pouchButton = findPouchButton(buttonList);

        if (pouchButton != null) {
            if (gui instanceof GuiContainerCreative) {
                pouchButton.xPosition =
                        (gui.width - CREATIVE_GUI_WIDTH) / 2
                                + CREATIVE_POUCH_X;
                pouchButton.yPosition =
                        (gui.height - CREATIVE_GUI_HEIGHT) / 2
                                + CREATIVE_POUCH_Y;
            }
            pickupFilterButton.xPosition = pouchButton.xPosition;
            pickupFilterButton.yPosition = pouchButton.yPosition;

            if (shouldShowPouchButton(gui)) {
                pickupFilterButton.xPosition -=
                        pickupFilterButton.width + BUTTON_GAP;
            }
            return;
        }

        if (gui instanceof GuiContainerCreative) {
            int guiLeft = (gui.width - CREATIVE_GUI_WIDTH) / 2;
            int guiTop = (gui.height - CREATIVE_GUI_HEIGHT) / 2;
            pickupFilterButton.xPosition = guiLeft + CREATIVE_POUCH_X;
            pickupFilterButton.yPosition = guiTop + CREATIVE_POUCH_Y;
        } else if (isMountedInventory(gui)) {
            int guiLeft = (gui.width - SURVIVAL_GUI_WIDTH) / 2;
            int guiTop = (gui.height - SURVIVAL_GUI_HEIGHT) / 2;
            pickupFilterButton.xPosition =
                    guiLeft + SURVIVAL_GUI_WIDTH + MOUNT_BUTTON_GAP;
            pickupFilterButton.yPosition = guiTop + 6;
        } else {
            int guiLeft = (gui.width - SURVIVAL_GUI_WIDTH) / 2;
            int guiTop = (gui.height - SURVIVAL_GUI_HEIGHT) / 2;
            pickupFilterButton.xPosition = guiLeft + SURVIVAL_POUCH_X;
            pickupFilterButton.yPosition = guiTop + SURVIVAL_POUCH_Y;
        }
    }

    private boolean isPickupFilterTabAllowed(GuiScreen gui) {
        if (!(gui instanceof GuiContainerCreative)) {
            return true;
        }

        int selectedTab =
                ((GuiContainerCreative) gui).func_147056_g();
        return selectedTab == CreativeTabs.tabInventory.getTabIndex();
    }

    private void refreshCurrentInventory(List buttonList) {
        pickupFilterButton = buttonList == null
                ? null
                : findPickupFilterButton(buttonList);
    }

    @SuppressWarnings("unchecked")
    private List getCurrentButtonList(GuiScreen gui) {
        return ReflectionHelper.getPrivateValue(
                GuiScreen.class,
                gui,
                "buttonList",
                "field_146292_n"
        );
    }

    private GuiPickupFilterInventoryButton findPickupFilterButton(
            List buttonList
    ) {
        for (Object object : buttonList) {
            if (object instanceof GuiPickupFilterInventoryButton
                    && ((GuiButton) object).id
                    == PICKUP_FILTER_BUTTON_ID) {
                return (GuiPickupFilterInventoryButton) object;
            }
        }

        return null;
    }

    private GuiButton findPouchButton(List buttonList) {
        for (Object object : buttonList) {
            if (object instanceof GuiButton
                    && POUCH_BUTTON_CLASS.equals(
                    object.getClass().getName()
            )) {
                return (GuiButton) object;
            }
        }

        return null;
    }

    private boolean shouldShowPouchButton(GuiScreen gui) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null
                || !mc.thePlayer.inventory.hasItem(LOTRMod.pouch)) {
            return false;
        }

        return !(gui instanceof GuiContainerCreative)
                || ((GuiContainerCreative) gui).func_147056_g()
                == CreativeTabs.tabInventory.getTabIndex();
    }

    private static boolean isSupportedInventory(GuiScreen gui) {
        return gui instanceof GuiInventory
                || gui instanceof GuiContainerCreative
                || isMountedInventory(gui);
    }

    private static boolean isMountedInventory(GuiScreen gui) {
        return gui instanceof GuiScreenHorseInventory
                || gui != null
                && NPC_MOUNT_GUI_CLASS.equals(gui.getClass().getName());
    }

    /** Exposes GuiScreen's normal one-line Minecraft tooltip renderer. */
    private static class PickupFilterTooltipRenderer extends GuiScreen {

        private void drawTooltip(
                GuiScreen parent,
                String text,
                int mouseX,
                int mouseY
        ) {
            mc = Minecraft.getMinecraft();
            fontRendererObj = mc.fontRenderer;
            width = parent.width;
            height = parent.height;
            drawCreativeTabHoveringText(text, mouseX, mouseY);
        }
    }
}
