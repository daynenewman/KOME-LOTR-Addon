package com.enovak.lotrmoremobs.siege.client.gui;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.SiegeRegistry;
import com.enovak.lotrmoremobs.siege.client.GateManagementClientContext;
import com.enovak.lotrmoremobs.siege.network.GateManagementActionPacket;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class GuiGateBlockPicker
        extends GuiScreen {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(
                    "lotrmoremobs",
                    "textures/gui/block_picker_gui.png"
            );

    private static final int GUI_WIDTH = 194;
    private static final int GUI_HEIGHT = 113;

    private static final int GRID_X = 8;
    private static final int GRID_Y = 17;

    private static final int SLOT_SIZE = 18;

    private static final int COLUMNS = 9;
    private static final int ROWS = 5;

    private static final int VISIBLE_SLOTS =
            COLUMNS * ROWS;

    private static final int SCROLL_X = 175;
    private static final int SCROLL_Y = 18;
    private static final int SCROLL_HEIGHT = 88;

    private static final int THUMB_WIDTH = 12;
    private static final int THUMB_HEIGHT = 15;

    private static final int SCROLL_THUMB_U = 195;
    private static final int SCROLL_THUMB_ENABLED_V = 56;
    private static final int SCROLL_THUMB_DISABLED_V = 71;

    private static final int APPLY_BUTTON = 1;
    private static final int CANCEL_BUTTON = 2;

    private final RenderItem renderItem =
            new RenderItem();

    private final List<ItemStack> allBlocks =
            new ArrayList<ItemStack>();

    private final List<ItemStack> filteredBlocks =
            new ArrayList<ItemStack>();

    private GuiTextField searchField;

    private ItemStack selectedStack;

    private int guiLeft;
    private int guiTop;

    private int scrollRow;

    private boolean draggingScroll;

    private static final ResourceLocation PICKUP_FILTER_TEXTURE =
            new ResourceLocation(
                    "lotrmoremobs",
                    "textures/gui/pickup_filter.png"
            );

    private static final int SCROLL_THUMB_V = 56;

    private static final int GOLD_FRAME_U = 195;
    private static final int GOLD_FRAME_V = 0;
    private static final int GOLD_FRAME_SIZE = 22;

    @Override
    public void updateScreen() {
        super.updateScreen();

        if (searchField != null) {
            searchField.updateCursorCounter();
        }
    }

    @Override
    public void initGui() {
        buttonList.clear();

        guiLeft =
                (width - GUI_WIDTH) / 2;

        guiTop =
                (height - GUI_HEIGHT) / 2 - 10;

        searchField =
                new GuiTextField(
                        fontRendererObj,
                        guiLeft + 90,
                        guiTop + 5,
                        78,
                        10
                );

        searchField.setEnableBackgroundDrawing(
                false
        );

        searchField.setMaxStringLength(
                40
        );

        buildCreativeBlockList();

        TileEntitySiegeGate gate =
                getGate();

        if (gate != null) {
            selectExistingAppearance(
                    gate
            );
        }

        rebuildFilteredList();

        buttonList.add(
                new GuiButton(
                        APPLY_BUTTON,
                        width / 2 - 74,
                        guiTop + GUI_HEIGHT + 7,
                        70,
                        20,
                        "Apply"
                )
        );

        buttonList.add(
                new GuiButton(
                        CANCEL_BUTTON,
                        width / 2 + 4,
                        guiTop + GUI_HEIGHT + 7,
                        70,
                        20,
                        "Cancel"
                )
        );

        updateApplyButton();
    }

    private void buildCreativeBlockList() {
        allBlocks.clear();

        Map<String, ItemStack> unique =
                new LinkedHashMap<String, ItemStack>();

        /*
         * Always expose the Gate Controller as the first texture choice.
         *
         * It is intentionally special:
         * selecting it means "use the native controller texture", not
         * "skin the controller with itself".
         *
         * Do not depend on CreativeTabs enumeration to discover this entry.
         */
        if (SiegeRegistry.gateController != null) {
            String controllerRegistryName =
                    Block.blockRegistry
                            .getNameForObject(
                                    SiegeRegistry.gateController
                            );

            if (controllerRegistryName != null) {
                unique.put(
                        controllerRegistryName + "#0",
                        new ItemStack(
                                SiegeRegistry.gateController,
                                1,
                                0
                        )
                );
            }
        }

        for (CreativeTabs tab
                : CreativeTabs.creativeTabArray) {

            if (tab == null
                    || tab == CreativeTabs.tabAllSearch
                    || tab == CreativeTabs.tabInventory) {

                continue;
            }

            List<ItemStack> stacks =
                    new ArrayList<ItemStack>();

            try {
                tab.displayAllReleventItems(
                        stacks
                );

            } catch (RuntimeException ignored) {
                continue;
            }

            for (ItemStack stack
                    : stacks) {

                if (stack == null
                        || stack.getItem() == null) {

                    continue;
                }

                Block block =
                        Block.getBlockFromItem(
                                stack.getItem()
                        );

                if (block == null
                        || block == Blocks.air) {

                    continue;
                }

                /*
                 * The controller was already inserted explicitly above.
                 * Avoid processing it again through the ordinary cosmetic
                 * block rules.
                 */
                if (block == SiegeRegistry.gateController) {
                    continue;
                }

                /*
                 * Every ordinary picker entry must satisfy the solid/full-cube
                 * controller appearance policy.
                 */
                if (!TileEntitySiegeGate
                        .isValidControllerAppearanceBlock(
                                block
                        )) {

                    continue;
                }

                int metadata =
                        stack.getItemDamage();

                if (metadata < 0
                        || metadata > 15) {

                    continue;
                }

                String registryName =
                        Block.blockRegistry
                                .getNameForObject(
                                        block
                                );

                if (registryName == null) {
                    continue;
                }

                String key =
                        registryName
                                + "#"
                                + metadata;

                if (!unique.containsKey(
                        key
                )) {

                    unique.put(
                            key,
                            stack.copy()
                    );
                }
            }
        }

        allBlocks.addAll(
                unique.values()
        );
    }

    private void rebuildFilteredList() {
        filteredBlocks.clear();

        String search =
                searchField == null
                        ? ""
                        : searchField
                        .getText()
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        for (ItemStack stack
                : allBlocks) {

            if (search.isEmpty()
                    || matchesSearch(
                    stack,
                    search
            )) {

                filteredBlocks.add(
                        stack
                );
            }
        }

        scrollRow =
                Math.min(
                        scrollRow,
                        getMaxScrollRow()
                );
    }

    private boolean matchesSearch(
            ItemStack stack,
            String search
    ) {
        String displayName =
                stack.getDisplayName();

        if (displayName != null
                && displayName
                .toLowerCase(
                        Locale.ROOT
                )
                .contains(
                        search
                )) {

            return true;
        }

        Block block =
                Block.getBlockFromItem(
                        stack.getItem()
                );

        String registryName =
                block == null
                        ? null
                        : Block.blockRegistry
                        .getNameForObject(
                                block
                        );

        return registryName != null
                && registryName
                .toLowerCase(
                        Locale.ROOT
                )
                .contains(
                        search
                );
    }

    @Override
    public void drawScreen(
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        drawDefaultBackground();

        mc.getTextureManager()
                .bindTexture(
                        TEXTURE
                );

        GL11.glColor4f(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        drawTexturedModalRect(
                guiLeft,
                guiTop,
                0,
                0,
                GUI_WIDTH,
                GUI_HEIGHT
        );

        /*
         * Normal Minecraft font.
         *
         * Keep the complete wording outside the search inset so the two
         * elements never overlap.
         */
        fontRendererObj.drawString(
                "Browse texture",
                guiLeft + 8,
                guiTop + 6,
                0x404040
        );

        /*
         * Draw selection frames BEFORE item sprites, matching GuiPickupFilter.
         */
        mc.getTextureManager()
                .bindTexture(
                        PICKUP_FILTER_TEXTURE
                );

        GL11.glDisable(
                GL11.GL_LIGHTING
        );

        GL11.glDisable(
                GL11.GL_DEPTH_TEST
        );

        for (int visibleIndex = 0;
             visibleIndex < VISIBLE_SLOTS;
             ++visibleIndex) {

            int listIndex =
                    scrollRow
                            * COLUMNS
                            + visibleIndex;

            if (listIndex >= filteredBlocks.size()) {
                break;
            }

            ItemStack stack =
                    filteredBlocks.get(
                            listIndex
                    );

            if (!isSelected(
                    stack
            )) {
                continue;
            }

            int column =
                    visibleIndex
                            % COLUMNS;

            int row =
                    visibleIndex
                            / COLUMNS;

            int x =
                    guiLeft
                            + GRID_X
                            + column
                            * SLOT_SIZE;

            int y =
                    guiTop
                            + GRID_Y
                            + row
                            * SLOT_SIZE;

            drawGoldSelectionFrame(
                    x,
                    y
            );
        }

        GL11.glEnable(
                GL11.GL_DEPTH_TEST
        );

        /*
         * Now render the actual block/item sprites.
         */
        RenderHelper.enableGUIStandardItemLighting();

        ItemStack hovered =
                null;

        for (int visibleIndex = 0;
             visibleIndex < VISIBLE_SLOTS;
             ++visibleIndex) {

            int listIndex =
                    scrollRow
                            * COLUMNS
                            + visibleIndex;

            if (listIndex >= filteredBlocks.size()) {
                break;
            }

            int column =
                    visibleIndex
                            % COLUMNS;

            int row =
                    visibleIndex
                            / COLUMNS;

            int x =
                    guiLeft
                            + GRID_X
                            + column
                            * SLOT_SIZE;

            int y =
                    guiTop
                            + GRID_Y
                            + row
                            * SLOT_SIZE;

            ItemStack stack =
                    filteredBlocks.get(
                            listIndex
                    );

            renderItem.renderItemAndEffectIntoGUI(
                    fontRendererObj,
                    mc.getTextureManager(),
                    stack,
                    x + 1,
                    y + 1
            );

            if (mouseX >= x
                    && mouseX < x + SLOT_SIZE
                    && mouseY >= y
                    && mouseY < y + SLOT_SIZE) {

                hovered =
                        stack;
            }
        }

        RenderHelper.disableStandardItemLighting();

        drawScrollThumb();

        super.drawScreen(
                mouseX,
                mouseY,
                partialTicks
        );

        searchField.drawTextBox();

        if (hovered != null) {
            List<String> tooltip =
                    new ArrayList<String>();

            tooltip.add(
                    hovered.getDisplayName()
            );

            Block block =
                    Block.getBlockFromItem(
                            hovered.getItem()
                    );

            String registryName =
                    block == null
                            ? null
                            : Block.blockRegistry
                            .getNameForObject(
                                    block
                            );

            if (registryName != null) {
                tooltip.add(
                        registryName
                                + ":"
                                + hovered.getItemDamage()
                );
            }

            drawHoveringText(
                    tooltip,
                    mouseX,
                    mouseY,
                    fontRendererObj
            );
        }
    }


    private void drawScrollThumb() {
        int maximum =
                getMaxScrollRow();

        int y =
                guiTop
                        + SCROLL_Y;

        int textureV =
                SCROLL_THUMB_DISABLED_V;

        /*
         * Light slider while scrolling is possible.
         * Dark slider while all matching blocks fit on one page.
         */
        if (maximum > 0) {
            int travel =
                    SCROLL_HEIGHT
                            - THUMB_HEIGHT;

            y += scrollRow
                    * travel
                    / maximum;

            textureV =
                    SCROLL_THUMB_ENABLED_V;
        }

        mc.getTextureManager()
                .bindTexture(
                        TEXTURE
                );

        GL11.glColor4f(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        drawTexturedModalRect(
                guiLeft + SCROLL_X,
                y,
                SCROLL_THUMB_U,
                textureV,
                THUMB_WIDTH,
                THUMB_HEIGHT
        );
    }

    @Override
    protected void mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton
    ) {
        searchField.mouseClicked(
                mouseX,
                mouseY,
                mouseButton
        );

        if (getMaxScrollRow() > 0
                && mouseX >= guiLeft + SCROLL_X
                && mouseX < guiLeft
                + SCROLL_X
                + THUMB_WIDTH
                && mouseY >= guiTop + SCROLL_Y
                && mouseY < guiTop
                + SCROLL_Y
                + SCROLL_HEIGHT) {

            draggingScroll =
                    true;

            updateScrollFromMouse(
                    mouseY
            );

            return;
        }

        if (mouseButton == 0) {
            ItemStack clicked =
                    getStackAt(
                            mouseX,
                            mouseY
                    );

            if (clicked != null) {
                selectedStack =
                        clicked.copy();

                updateApplyButton();

                return;
            }

            if (mouseX
                    >= guiLeft + SCROLL_X
                    && mouseX
                    < guiLeft
                    + SCROLL_X
                    + THUMB_WIDTH
                    && mouseY
                    >= guiTop + SCROLL_Y
                    && mouseY
                    < guiTop
                    + SCROLL_Y
                    + SCROLL_HEIGHT) {

                draggingScroll =
                        true;

                updateScrollFromMouse(
                        mouseY
                );

                return;
            }
        }

        super.mouseClicked(
                mouseX,
                mouseY,
                mouseButton
        );
    }

    @Override
    protected void mouseClickMove(
            int mouseX,
            int mouseY,
            int clickedMouseButton,
            long timeSinceLastClick
    ) {
        if (draggingScroll
                && clickedMouseButton == 0) {

            updateScrollFromMouse(
                    mouseY
            );

            return;
        }

        super.mouseClickMove(
                mouseX,
                mouseY,
                clickedMouseButton,
                timeSinceLastClick
        );
    }

    @Override
    protected void mouseMovedOrUp(
            int mouseX,
            int mouseY,
            int state
    ) {
        if (state == 0) {
            draggingScroll =
                    false;
        }

        super.mouseMovedOrUp(
                mouseX,
                mouseY,
                state
        );
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();

        int wheel =
                Mouse.getEventDWheel();

        if (wheel == 0) {
            return;
        }

        if (wheel > 0) {
            --scrollRow;

        } else {
            ++scrollRow;
        }

        clampScroll();
    }

    @Override
    protected void keyTyped(
            char typedChar,
            int keyCode
    ) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            returnToManagement();

            return;
        }

        if (keyCode
                == mc.gameSettings
                .keyBindInventory
                .getKeyCode()
                && !searchField.isFocused()) {

            GateManagementClientContext.clear();

            mc.displayGuiScreen(
                    null
            );

            return;
        }

        String before =
                searchField.getText();

        if (searchField.textboxKeyTyped(
                typedChar,
                keyCode
        )) {
            if (!before.equals(
                    searchField.getText()
            )) {
                scrollRow =
                        0;

                rebuildFilteredList();
            }

            return;
        }

        super.keyTyped(
                typedChar,
                keyCode
        );
    }



    @Override
    protected void actionPerformed(
            GuiButton button
    )

    {
        if (!button.enabled) {
            return;
        }

        if (button.id == CANCEL_BUTTON) {
            returnToManagement();

            return;
        }

        if (button.id == APPLY_BUTTON
                && selectedStack != null) {

            Block block =
                    Block.getBlockFromItem(
                            selectedStack
                                    .getItem()
                    );

            String registryName =
                    block == null
                            ? null
                            : Block.blockRegistry
                            .getNameForObject(
                                    block
                            );

            if (registryName != null) {
                Main.network.sendToServer(
                        new GateManagementActionPacket(
                                GateManagementActionPacket
                                        .SET_CONTROLLER_APPEARANCE,
                                GateManagementClientContext
                                        .getDimensionId(),
                                GateManagementClientContext
                                        .getControllerX(),
                                GateManagementClientContext
                                        .getControllerY(),
                                GateManagementClientContext
                                        .getControllerZ(),
                                selectedStack
                                        .getItemDamage(),
                                registryName
                        )
                );
            }

            returnToManagement();
        }
    }

    private void returnToManagement() {
        mc.displayGuiScreen(
                new GuiGateManagement(
                        false
                )
        );
    }

    private ItemStack getStackAt(
            int mouseX,
            int mouseY
    ) {
        int relativeX =
                mouseX
                        - guiLeft
                        - GRID_X;

        int relativeY =
                mouseY
                        - guiTop
                        - GRID_Y;

        if (relativeX < 0
                || relativeY < 0
                || relativeX
                >= COLUMNS
                * SLOT_SIZE
                || relativeY
                >= ROWS
                * SLOT_SIZE) {

            return null;
        }

        int column =
                relativeX
                        / SLOT_SIZE;

        int row =
                relativeY
                        / SLOT_SIZE;

        int index =
                scrollRow
                        * COLUMNS
                        + row
                        * COLUMNS
                        + column;

        if (index < 0
                || index
                >= filteredBlocks.size()) {

            return null;
        }

        return filteredBlocks
                .get(
                        index
                );
    }

    private void updateScrollFromMouse(
            int mouseY
    ) {
        int maximum =
                getMaxScrollRow();

        if (maximum <= 0) {
            scrollRow =
                    0;

            return;
        }

        int travel =
                SCROLL_HEIGHT
                        - THUMB_HEIGHT;

        float normalized =
                (mouseY
                        - guiTop
                        - SCROLL_Y
                        - THUMB_HEIGHT
                        / 2.0F)
                        / (float)travel;

        normalized =
                Math.max(
                        0.0F,
                        Math.min(
                                1.0F,
                                normalized
                        )
                );

        scrollRow =
                Math.round(
                        normalized
                                * maximum
                );

        clampScroll();
    }

    private int getMaxScrollRow() {
        int totalRows =
                (filteredBlocks.size()
                        + COLUMNS
                        - 1)
                        / COLUMNS;

        return Math.max(
                0,
                totalRows
                        - ROWS
        );
    }

    private void clampScroll() {
        scrollRow =
                Math.max(
                        0,
                        Math.min(
                                scrollRow,
                                getMaxScrollRow()
                        )
                );
    }

    private void drawGoldSelectionFrame(
            int x,
            int y
    ) {
        mc.getTextureManager()
                .bindTexture(
                        PICKUP_FILTER_TEXTURE
                );

        GL11.glColor4f(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        /*
         * Exact Pickup Filter selection graphic:
         * 22x22 source sprite scaled into an 18x18 slot.
         */
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

    private void selectExistingAppearance(
            TileEntitySiegeGate gate
    ) {
        String targetName =
                gate.getControllerAppearanceBlockName();

        int targetMetadata =
                gate.getControllerAppearanceMetadata();

        /*
         * Empty appearance name means the native controller skin.
         * Represent that in this GUI by selecting the Gate Controller item.
         */
        if (targetName == null
                || targetName.isEmpty()) {

            targetName =
                    Block.blockRegistry
                            .getNameForObject(
                                    SiegeRegistry.gateController
                            );

            targetMetadata =
                    0;
        }

        for (ItemStack stack
                : allBlocks) {

            Block block =
                    Block.getBlockFromItem(
                            stack.getItem()
                    );

            String registryName =
                    block == null
                            ? null
                            : Block.blockRegistry
                            .getNameForObject(
                                    block
                            );

            if (targetName != null
                    && targetName.equals(
                    registryName
            )
                    && targetMetadata
                    == stack.getItemDamage()) {

                selectedStack =
                        stack.copy();

                return;
            }
        }
    }

    private boolean isSelected(
            ItemStack stack
    ) {
        if (selectedStack == null
                || stack == null) {

            return false;
        }

        Block first =
                Block.getBlockFromItem(
                        selectedStack
                                .getItem()
                );

        Block second =
                Block.getBlockFromItem(
                        stack.getItem()
                );

        return first == second
                && selectedStack
                .getItemDamage()
                == stack.getItemDamage();
    }

    private TileEntitySiegeGate getGate() {
        if (!GateManagementClientContext
                .isActive()
                || mc.theWorld == null
                || mc.theWorld
                .provider.dimensionId
                != GateManagementClientContext
                .getDimensionId()) {

            return null;
        }

        TileEntity tileEntity =
                mc.theWorld
                        .getTileEntity(
                                GateManagementClientContext
                                        .getControllerX(),
                                GateManagementClientContext
                                        .getControllerY(),
                                GateManagementClientContext
                                        .getControllerZ()
                        );

        return tileEntity
                instanceof TileEntitySiegeGate
                ? (TileEntitySiegeGate)tileEntity
                : null;
    }

    private void updateApplyButton() {
        for (Object object
                : buttonList) {

            if (object instanceof GuiButton
                    && ((GuiButton)object).id
                    == APPLY_BUTTON) {

                ((GuiButton)object).enabled =
                        selectedStack != null;

                return;
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}