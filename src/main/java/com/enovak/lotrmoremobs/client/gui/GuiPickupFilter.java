package com.enovak.lotrmoremobs.client.gui;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.client.pickupfilter.ClientPickupFilterState;
import com.enovak.lotrmoremobs.network.PickupFilterClearPacket;
import com.enovak.lotrmoremobs.network.PickupFilterTogglePacket;

import lotr.common.item.LOTRItemPouch;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Client-only pickup-filter editor. The upper panel switches between the
 * server-synchronized excluded list and a searchable item catalog; the lower
 * panel always represents the player's real inventory and hotbar.
 */
public class GuiPickupFilter extends GuiScreen {

    private static final ResourceLocation BACKGROUND =
            new ResourceLocation(
                    "lotrmoremobs",
                    "textures/gui/pickup_filter.png"
            );

    private static final int GUI_WIDTH = 195;
    private static final int GUI_HEIGHT = 167;

    private static final int CLOSE_BUTTON_ID = 0;
    private static final int MODE_BUTTON_ID = 1;
    private static final int CLEAR_BUTTON_ID = 2;

    private static final int COLUMN_COUNT = 9;
    private static final int UPPER_ROW_COUNT = 3;
    private static final int UPPER_VISIBLE_COUNT =
            COLUMN_COUNT * UPPER_ROW_COUNT;
    private static final int SLOT_SIZE = 18;

    private static final int SLOT_X = 8;
    private static final int UPPER_SLOT_Y = 17;
    private static final int INVENTORY_SLOT_Y = 85;
    private static final int HOTBAR_SLOT_Y = 143;

    private static final int SCROLL_TRACK_X = 175;
    private static final int SCROLL_TRACK_Y = 18;
    private static final int SCROLL_TRACK_WIDTH = 12;
    private static final int SCROLL_TRACK_HEIGHT = 52;
    private static final int SCROLL_THUMB_HEIGHT = 15;
    private static final int SCROLL_THUMB_U = 195;
    private static final int SCROLL_THUMB_ENABLED_V = 56;
    private static final int SCROLL_THUMB_DISABLED_V = 71;

    private static final int GOLD_FRAME_U = 195;
    private static final int GOLD_FRAME_V = 0;
    private static final int GOLD_FRAME_SIZE = 22;

    private final RenderItem itemRenderer = new RenderItem();
    private final List<ItemStack> searchItems =
            new ArrayList<ItemStack>();
    private final List<ItemStack> filteredSearchItems =
            new ArrayList<ItemStack>();
    private final List<ItemStack> filteredExcludedItems =
            new ArrayList<ItemStack>();
    private final List<ItemStack> lastExcludedItems =
            new ArrayList<ItemStack>();
    private final boolean returnToCreativeInventory;

    private int guiLeft;
    private int guiTop;
    private int excludedScrollRow;
    private int searchScrollRow;
    private boolean searchMode;
    private boolean draggingScrollbar;
    private GuiTextField searchField;

    public GuiPickupFilter() {
        this(false);
    }

    public GuiPickupFilter(boolean returnToCreativeInventory) {
        this.returnToCreativeInventory = returnToCreativeInventory;
    }

    @Override
    public void initGui() {
        super.initGui();

        String previousSearch = searchField == null
                ? ""
                : searchField.getText();

        guiLeft = (width - GUI_WIDTH) / 2;
        guiTop = (height - GUI_HEIGHT) / 2;
        draggingScrollbar = false;

        buttonList.clear();
        buttonList.add(new GuiButton(
                CLOSE_BUTTON_ID,
                guiLeft + GUI_WIDTH + 5,
                guiTop,
                50,
                20,
                "Close"
        ));
        buttonList.add(new GuiButton(
                MODE_BUTTON_ID,
                guiLeft + GUI_WIDTH + 5,
                guiTop + 24,
                Math.max(
                        50,
                        fontRendererObj.getStringWidth("Browse Items") + 8
                ),
                20,
                searchMode ? "Back" : "Browse Items"
        ));
        buttonList.add(new GuiButton(
                CLEAR_BUTTON_ID,
                guiLeft + GUI_WIDTH + 5,
                guiTop + 48,
                50,
                20,
                "Clear All"
        ));

        // This aligns with the inset already painted into the GUI header.
        searchField = new GuiTextField(
                fontRendererObj,
                guiLeft + 90,
                guiTop + 5,
                86,
                10
        );
        searchField.setEnableBackgroundDrawing(false);
        searchField.setMaxStringLength(40);
        searchField.setText(previousSearch);
        searchField.setVisible(true);
        searchField.setFocused(searchMode);

        // Reinitialization can happen on a resolution change. Do not rebuild
        // the complete registry catalog unless this GUI instance needs it.
        if (searchItems.isEmpty()) {
            rebuildSearchItems();
        }

        updateAllSearchResults();
        clampActiveScroll();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        if (searchField != null) {
            searchField.updateCursorCounter();
        }
    }

    @Override
    public void drawScreen(
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        List<ItemStack> upperItems = getUpperItems();
        clampActiveScroll(upperItems.size());

        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY, upperItems.size());
        }

        drawDefaultBackground();
        bindBackgroundTexture();
        drawTexturedModalRect(
                guiLeft,
                guiTop,
                0,
                0,
                GUI_WIDTH,
                GUI_HEIGHT
        );

        drawGuiTitle();
        drawPanelLabels();
        drawUpperScrollbar(upperItems.size());

        if (searchMode) {
            drawSearchSelectionFrames(upperItems);
        }
        drawPlayerInventorySelectionFrames();

        drawUpperItems(upperItems);
        drawPlayerInventory();
        drawHoveredSlot(mouseX, mouseY);

        if (searchField != null) {
            searchField.drawTextBox();
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        ItemStack hoveredStack = getHoveredStack(mouseX, mouseY, upperItems);
        if (hoveredStack != null) {
            if (hoveredStack.getItem() instanceof LOTRItemPouch) {
                drawCreativeTabHoveringText(
                        hoveredStack.getDisplayName(),
                        mouseX,
                        mouseY
                );
            } else {
                renderToolTip(hoveredStack, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();

        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }

        int size = getUpperItems().size();
        int scrollRow = getActiveScrollRow();
        scrollRow += wheel < 0 ? 1 : -1;
        setActiveScrollRow(clamp(
                scrollRow,
                0,
                getMaxScrollRow(size)
        ));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == CLOSE_BUTTON_ID) {
            openPlayerInventory();
            return;
        }

        if (button.id == MODE_BUTTON_ID) {
            searchMode = !searchMode;
            button.displayString = searchMode
                    ? "Back"
                    : "Browse Items";

            if (searchField != null) {
                searchField.setVisible(true);
                searchField.setFocused(searchMode);
            }

            updateAllSearchResults();
            clampActiveScroll();
            return;
        }

        if (button.id == CLEAR_BUTTON_ID) {
            // The client deliberately does not clear its display cache here.
            // The server applies the change and sends the authoritative list.
            Main.network.sendToServer(new PickupFilterClearPacket());
            excludedScrollRow = 0;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (searchField != null
                && searchField.isFocused()) {
            String previousSearch = searchField.getText();

            if (searchField.textboxKeyTyped(typedChar, keyCode)) {
                if (!previousSearch.equals(searchField.getText())) {
                    excludedScrollRow = 0;
                    searchScrollRow = 0;
                    updateAllSearchResults();
                }
                return;
            }
        }

        if (keyCode == Keyboard.KEY_E
                || keyCode == mc.gameSettings
                .keyBindInventory.getKeyCode()) {
            openPlayerInventory();
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton
    ) {
        // Give an already-visible field the click before a mode button can
        // change visibility/focus through super.mouseClicked().
        if (searchField != null) {
            searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton != 0 || mc.thePlayer == null) {
            return;
        }

        List<ItemStack> upperItems = getUpperItems();
        clampActiveScroll(upperItems.size());

        if (isInsideScrollbar(mouseX, mouseY)) {
            draggingScrollbar = getMaxScrollRow(upperItems.size()) > 0;
            updateScrollFromMouse(mouseY, upperItems.size());
            return;
        }

        int visibleUpperIndex = getGridIndex(
                mouseX,
                mouseY,
                UPPER_SLOT_Y,
                UPPER_ROW_COUNT
        );
        if (visibleUpperIndex >= 0) {
            int itemIndex = getActiveScrollRow() * COLUMN_COUNT
                    + visibleUpperIndex;

            if (itemIndex < upperItems.size()) {
                toggleStack(upperItems.get(itemIndex));
            }
            return;
        }

        int inventoryGridIndex = getGridIndex(
                mouseX,
                mouseY,
                INVENTORY_SLOT_Y,
                3
        );
        if (inventoryGridIndex >= 0) {
            toggleInventoryStack(9 + inventoryGridIndex);
            return;
        }

        int hotbarColumn = getHotbarColumn(mouseX, mouseY);
        if (hotbarColumn >= 0) {
            toggleInventoryStack(hotbarColumn);
        }
    }

    @Override
    protected void mouseMovedOrUp(
            int mouseX,
            int mouseY,
            int mouseButton
    ) {
        super.mouseMovedOrUp(mouseX, mouseY, mouseButton);

        if (mouseButton == 0) {
            draggingScrollbar = false;
        }
    }

    private void drawGuiTitle() {
        // The texture's header is already occupied by its section label and
        // search inset. Align the permanent title to that same left inset,
        // immediately above the 167-pixel panel so none of those elements
        // are covered.
        fontRendererObj.drawStringWithShadow(
                "Pickup Filter",
                guiLeft + SLOT_X,
                guiTop - fontRendererObj.FONT_HEIGHT - 2,
                0xFFFFFF
        );
    }

    private void drawPanelLabels() {
        // 0x404040 is the label color used by the vanilla 1.7.10 inventory
        // screens. Direct font rendering keeps the normal, unscaled weight.
        fontRendererObj.drawString(
                searchMode ? "Browse Items" : "Excluded Items",
                guiLeft + 8,
                guiTop + 6,
                0x404040
        );
        fontRendererObj.drawString(
                "Inventory",
                guiLeft + 8,
                guiTop + 74,
                0x404040
        );
    }

    private void drawUpperScrollbar(int itemCount) {
        int maxScrollRow = getMaxScrollRow(itemCount);
        int thumbY = guiTop + SCROLL_TRACK_Y;
        int textureV = SCROLL_THUMB_DISABLED_V;

        if (maxScrollRow > 0) {
            int travel = SCROLL_TRACK_HEIGHT - SCROLL_THUMB_HEIGHT;
            thumbY += getActiveScrollRow() * travel / maxScrollRow;
            textureV = SCROLL_THUMB_ENABLED_V;
        }

        bindBackgroundTexture();
        drawTexturedModalRect(
                guiLeft + SCROLL_TRACK_X,
                thumbY,
                SCROLL_THUMB_U,
                textureV,
                SCROLL_TRACK_WIDTH,
                SCROLL_THUMB_HEIGHT
        );
    }

    private void drawSearchSelectionFrames(List<ItemStack> upperItems) {
        int startIndex = getActiveScrollRow() * COLUMN_COUNT;
        int visibleCount = Math.min(
                UPPER_VISIBLE_COUNT,
                Math.max(0, upperItems.size() - startIndex)
        );
        List<ItemStack> excludedItems =
                ClientPickupFilterState.getExcludedItems();

        bindBackgroundTexture();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        for (int i = 0; i < visibleCount; ++i) {
            ItemStack stack = upperItems.get(startIndex + i);
            if (!isStackInList(stack, excludedItems)) {
                continue;
            }

            int column = i % COLUMN_COUNT;
            int row = i / COLUMN_COUNT;
            int x = guiLeft + SLOT_X + column * SLOT_SIZE;
            int y = guiTop + UPPER_SLOT_Y + row * SLOT_SIZE;

            drawGoldSelectionFrame(x, y);
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private void drawPlayerInventorySelectionFrames() {
        if (mc.thePlayer == null) {
            return;
        }

        List<ItemStack> excludedItems =
                ClientPickupFilterState.getExcludedItems();

        bindBackgroundTexture();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        for (int row = 0; row < 3; ++row) {
            for (int column = 0; column < COLUMN_COUNT; ++column) {
                ItemStack stack = mc.thePlayer.inventory.mainInventory[
                        9 + row * COLUMN_COUNT + column
                ];
                if (stack != null && isStackInList(stack, excludedItems)) {
                    drawGoldSelectionFrame(
                            guiLeft + SLOT_X + column * SLOT_SIZE,
                            guiTop + INVENTORY_SLOT_Y + row * SLOT_SIZE
                    );
                }
            }
        }

        for (int column = 0; column < COLUMN_COUNT; ++column) {
            ItemStack stack =
                    mc.thePlayer.inventory.mainInventory[column];
            if (stack != null && isStackInList(stack, excludedItems)) {
                drawGoldSelectionFrame(
                        guiLeft + SLOT_X + column * SLOT_SIZE,
                        guiTop + HOTBAR_SLOT_Y
                );
            }
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private void drawGoldSelectionFrame(int x, int y) {
        // Scale the existing 22x22 frame into the 18x18 slot. Its transparent
        // center leaves the item sprite unobscured when the item is drawn.
        func_152125_a(
                x,
                y,
                GOLD_FRAME_U,
                GOLD_FRAME_V,
                GOLD_FRAME_SIZE,
                GOLD_FRAME_SIZE,
                SLOT_SIZE,
                SLOT_SIZE,
                256.0F,
                256.0F
        );
    }

    private void drawUpperItems(List<ItemStack> upperItems) {
        int startIndex = getActiveScrollRow() * COLUMN_COUNT;
        int visibleCount = Math.min(
                UPPER_VISIBLE_COUNT,
                Math.max(0, upperItems.size() - startIndex)
        );

        beginItemRendering();

        for (int i = 0; i < visibleCount; ++i) {
            int column = i % COLUMN_COUNT;
            int row = i / COLUMN_COUNT;
            drawItemStack(
                    upperItems.get(startIndex + i),
                    guiLeft + SLOT_X + 1 + column * SLOT_SIZE,
                    guiTop + UPPER_SLOT_Y + 1 + row * SLOT_SIZE
            );
        }

        endItemRendering();
    }

    private void drawPlayerInventory() {
        if (mc.thePlayer == null) {
            return;
        }

        beginItemRendering();

        for (int row = 0; row < 3; ++row) {
            for (int column = 0; column < COLUMN_COUNT; ++column) {
                int inventoryIndex = 9 + row * COLUMN_COUNT + column;
                drawItemStack(
                        mc.thePlayer.inventory
                                .mainInventory[inventoryIndex],
                        guiLeft + SLOT_X + 1 + column * SLOT_SIZE,
                        guiTop + INVENTORY_SLOT_Y + 1
                                + row * SLOT_SIZE
                );
            }
        }

        for (int column = 0; column < COLUMN_COUNT; ++column) {
            drawItemStack(
                    mc.thePlayer.inventory.mainInventory[column],
                    guiLeft + SLOT_X + 1 + column * SLOT_SIZE,
                    guiTop + HOTBAR_SLOT_Y + 1
            );
        }

        endItemRendering();
    }

    private void beginItemRendering() {
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private void endItemRendering() {
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
    }

    private void drawItemStack(ItemStack stack, int x, int y) {
        if (stack == null) {
            return;
        }

        itemRenderer.renderItemAndEffectIntoGUI(
                fontRendererObj,
                mc.getTextureManager(),
                stack,
                x,
                y
        );
        itemRenderer.renderItemOverlayIntoGUI(
                fontRendererObj,
                mc.getTextureManager(),
                stack,
                x,
                y
        );
    }

    private void drawHoveredSlot(int mouseX, int mouseY) {
        int upperIndex = getGridIndex(
                mouseX,
                mouseY,
                UPPER_SLOT_Y,
                UPPER_ROW_COUNT
        );
        if (upperIndex >= 0) {
            drawSlotHighlight(
                    SLOT_X + upperIndex % COLUMN_COUNT * SLOT_SIZE,
                    UPPER_SLOT_Y
                            + upperIndex / COLUMN_COUNT * SLOT_SIZE
            );
            return;
        }

        int inventoryIndex = getGridIndex(
                mouseX,
                mouseY,
                INVENTORY_SLOT_Y,
                3
        );
        if (inventoryIndex >= 0) {
            drawSlotHighlight(
                    SLOT_X + inventoryIndex % COLUMN_COUNT * SLOT_SIZE,
                    INVENTORY_SLOT_Y
                            + inventoryIndex / COLUMN_COUNT * SLOT_SIZE
            );
            return;
        }

        int hotbarColumn = getHotbarColumn(mouseX, mouseY);
        if (hotbarColumn >= 0) {
            drawSlotHighlight(
                    SLOT_X + hotbarColumn * SLOT_SIZE,
                    HOTBAR_SLOT_Y
            );
        }
    }

    private void drawSlotHighlight(int relativeX, int relativeY) {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        drawGradientRect(
                guiLeft + relativeX + 1,
                guiTop + relativeY + 1,
                guiLeft + relativeX + 17,
                guiTop + relativeY + 17,
                0x80FFFFFF,
                0x80FFFFFF
        );
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private ItemStack getHoveredStack(
            int mouseX,
            int mouseY,
            List<ItemStack> upperItems
    ) {
        int upperIndex = getGridIndex(
                mouseX,
                mouseY,
                UPPER_SLOT_Y,
                UPPER_ROW_COUNT
        );
        if (upperIndex >= 0) {
            int itemIndex = getActiveScrollRow() * COLUMN_COUNT
                    + upperIndex;
            return itemIndex < upperItems.size()
                    ? upperItems.get(itemIndex)
                    : null;
        }

        if (mc.thePlayer == null) {
            return null;
        }

        int inventoryIndex = getGridIndex(
                mouseX,
                mouseY,
                INVENTORY_SLOT_Y,
                3
        );
        if (inventoryIndex >= 0) {
            return mc.thePlayer.inventory
                    .mainInventory[9 + inventoryIndex];
        }

        int hotbarColumn = getHotbarColumn(mouseX, mouseY);
        return hotbarColumn >= 0
                ? mc.thePlayer.inventory.mainInventory[hotbarColumn]
                : null;
    }

    private int getGridIndex(
            int mouseX,
            int mouseY,
            int relativeY,
            int rowCount
    ) {
        int localX = mouseX - (guiLeft + SLOT_X);
        int localY = mouseY - (guiTop + relativeY);

        if (localX < 0
                || localY < 0
                || localX >= COLUMN_COUNT * SLOT_SIZE
                || localY >= rowCount * SLOT_SIZE) {
            return -1;
        }

        return localY / SLOT_SIZE * COLUMN_COUNT
                + localX / SLOT_SIZE;
    }

    private int getHotbarColumn(int mouseX, int mouseY) {
        int localX = mouseX - (guiLeft + SLOT_X);
        int localY = mouseY - (guiTop + HOTBAR_SLOT_Y);

        if (localX < 0
                || localX >= COLUMN_COUNT * SLOT_SIZE
                || localY < 0
                || localY >= SLOT_SIZE) {
            return -1;
        }

        return localX / SLOT_SIZE;
    }

    private boolean isInsideScrollbar(int mouseX, int mouseY) {
        return mouseX >= guiLeft + SCROLL_TRACK_X - 1
                && mouseX < guiLeft + SCROLL_TRACK_X
                + SCROLL_TRACK_WIDTH + 1
                && mouseY >= guiTop + UPPER_SLOT_Y
                && mouseY < guiTop + UPPER_SLOT_Y
                + UPPER_ROW_COUNT * SLOT_SIZE;
    }

    private void updateScrollFromMouse(int mouseY, int itemCount) {
        int maxScrollRow = getMaxScrollRow(itemCount);
        if (maxScrollRow <= 0) {
            setActiveScrollRow(0);
            return;
        }

        int travel = SCROLL_TRACK_HEIGHT - SCROLL_THUMB_HEIGHT;
        int relativeY = mouseY
                - (guiTop + SCROLL_TRACK_Y + SCROLL_THUMB_HEIGHT / 2);
        int row = Math.round((float) relativeY * maxScrollRow / travel);
        setActiveScrollRow(clamp(row, 0, maxScrollRow));
    }

    private List<ItemStack> getUpperItems() {
        refreshExcludedSearchResultsIfNeeded();
        return searchMode ? filteredSearchItems : filteredExcludedItems;
    }

    private int getActiveScrollRow() {
        return searchMode ? searchScrollRow : excludedScrollRow;
    }

    private void setActiveScrollRow(int row) {
        if (searchMode) {
            searchScrollRow = row;
        } else {
            excludedScrollRow = row;
        }
    }

    private int getMaxScrollRow(int itemCount) {
        int rowCount = (itemCount + COLUMN_COUNT - 1) / COLUMN_COUNT;
        return Math.max(0, rowCount - UPPER_ROW_COUNT);
    }

    private void clampActiveScroll() {
        clampActiveScroll(getUpperItems().size());
    }

    private void clampActiveScroll(int itemCount) {
        setActiveScrollRow(clamp(
                getActiveScrollRow(),
                0,
                getMaxScrollRow(itemCount)
        ));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean isStackInList(
            ItemStack target,
            List<ItemStack> stacks
    ) {
        if (target == null) {
            return false;
        }

        for (ItemStack stack : stacks) {
            if (stack != null && stack.isItemEqual(target)) {
                return true;
            }
        }

        return false;
    }

    private void toggleInventoryStack(int inventoryIndex) {
        if (inventoryIndex < 0
                || inventoryIndex >= mc.thePlayer.inventory
                .mainInventory.length) {
            return;
        }

        toggleStack(mc.thePlayer.inventory
                .mainInventory[inventoryIndex]);
    }

    private void toggleStack(ItemStack stack) {
        if (stack != null) {
            Main.network.sendToServer(
                    new PickupFilterTogglePacket(stack)
            );
        }
    }

    @SuppressWarnings("unchecked")
    private void rebuildSearchItems() {
        searchItems.clear();

        for (Object object : Item.itemRegistry) {
            if (!(object instanceof Item)) {
                continue;
            }

            Item item = (Item) object;
            if (item.getCreativeTab() == null) {
                continue;
            }

            List<ItemStack> subItems =
                    new ArrayList<ItemStack>();

            try {
                item.getSubItems(
                        item,
                        null,
                        subItems
                );
            } catch (Exception ignored) {
                continue;
            }

            for (ItemStack stack : subItems) {
                if (stack != null) {
                    ItemStack copy = stack.copy();
                    copy.stackSize = 1;
                    searchItems.add(copy);
                }
            }
        }

        // Match the extra enchanted-book population performed by vanilla's
        // 1.7.10 Creative Search tab.
        for (Enchantment enchantment : Enchantment.enchantmentsList) {
            if (enchantment == null || enchantment.type == null) {
                continue;
            }

            List<ItemStack> enchantedBooks =
                    new ArrayList<ItemStack>();
            Items.enchanted_book.func_92113_a(
                    enchantment,
                    enchantedBooks
            );

            for (ItemStack stack : enchantedBooks) {
                if (stack != null) {
                    ItemStack copy = stack.copy();
                    copy.stackSize = 1;
                    searchItems.add(copy);
                }
            }
        }
    }

    private void updateSearchResults() {
        filteredSearchItems.clear();

        String query = searchField == null
                ? ""
                : searchField.getText()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (query.isEmpty()) {
            filteredSearchItems.addAll(searchItems);
        } else {
            for (ItemStack stack : searchItems) {
                try {
                    String displayName = stack.getDisplayName();
                    if (displayName != null
                            && displayName.toLowerCase(Locale.ROOT)
                            .contains(query)) {
                        filteredSearchItems.add(stack);
                    }
                } catch (Exception ignored) {
                    // Keep searching even if a modded stack has a broken name.
                }
            }
        }

        searchScrollRow = clamp(
                searchScrollRow,
                0,
                getMaxScrollRow(filteredSearchItems.size())
        );
    }

    private void updateAllSearchResults() {
        updateSearchResults();
        updateExcludedSearchResults(
                ClientPickupFilterState.getExcludedItems()
        );
    }

    private void refreshExcludedSearchResultsIfNeeded() {
        List<ItemStack> excludedItems =
                ClientPickupFilterState.getExcludedItems();

        if (!areStackListsEqual(excludedItems, lastExcludedItems)) {
            updateExcludedSearchResults(excludedItems);
        }
    }

    private void updateExcludedSearchResults(
            List<ItemStack> excludedItems
    ) {
        filteredExcludedItems.clear();
        lastExcludedItems.clear();

        String query = getSearchQuery();

        for (ItemStack stack : excludedItems) {
            if (stack == null) {
                continue;
            }

            ItemStack copy = stack.copy();
            lastExcludedItems.add(copy);

            if (query.isEmpty() || stackDisplayNameContains(stack, query)) {
                filteredExcludedItems.add(stack);
            }
        }

        excludedScrollRow = clamp(
                excludedScrollRow,
                0,
                getMaxScrollRow(filteredExcludedItems.size())
        );
    }

    private String getSearchQuery() {
        return searchField == null
                ? ""
                : searchField.getText()
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static boolean stackDisplayNameContains(
            ItemStack stack,
            String query
    ) {
        try {
            String displayName = stack.getDisplayName();
            return displayName != null
                    && displayName.toLowerCase(Locale.ROOT)
                    .contains(query);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean areStackListsEqual(
            List<ItemStack> first,
            List<ItemStack> second
    ) {
        if (first.size() != second.size()) {
            return false;
        }

        for (int i = 0; i < first.size(); ++i) {
            ItemStack left = first.get(i);
            ItemStack right = second.get(i);

            if (left == null || right == null) {
                if (left != right) {
                    return false;
                }
            } else if (!left.isItemEqual(right)
                    || !ItemStack.areItemStackTagsEqual(left, right)) {
                return false;
            }
        }

        return true;
    }

    private void openPlayerInventory() {
        if (mc.thePlayer != null) {
            if (returnToCreativeInventory
                    && mc.playerController.isInCreativeMode()) {
                // GuiContainerCreative retains its selected tab in the
                // vanilla static selectedTabIndex field in 1.7.10.
                mc.displayGuiScreen(
                        new GuiContainerCreative(mc.thePlayer)
                );
            } else {
                mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
            }
        }
    }

    private void bindBackgroundTexture() {
        mc.getTextureManager().bindTexture(BACKGROUND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
