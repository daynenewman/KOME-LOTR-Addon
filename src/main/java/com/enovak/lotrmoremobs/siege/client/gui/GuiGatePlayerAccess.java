package com.enovak.lotrmoremobs.siege.client.gui;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.client.GateManagementClientContext;
import com.enovak.lotrmoremobs.siege.network.GateManagementActionPacket;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.input.Keyboard;

public class GuiGatePlayerAccess
        extends GuiScreen {

    private static final int VISIBLE_ROWS =
            10;

    private static final int ROW_HEIGHT =
            18;

    private static final int ROW_SPACING =
            22;

    private static final int ROWS_TOP_OFFSET =
            103;

    private static final int BACK_BUTTON =
            1;

    private static final int SCROLL_UP_BUTTON =
            2;

    private static final int SCROLL_DOWN_BUTTON =
            3;

    private static final int FACTION_ACCESS_BUTTON =
            4;

    private static final int ROW_ACTION_BASE =
            100;

    private static final int ROW_REMOVE_BASE =
            200;

    private final List<GuiTextField> fields =
            new ArrayList<GuiTextField>();

    private GuiTextField alignmentField;

    private int visibleRowCount =
            2;

    private final UUID[] rowUuids =
            new UUID[VISIBLE_ROWS];

    private final boolean[] rowEditors =
            new boolean[VISIBLE_ROWS];

    private final boolean[] rowOwners =
            new boolean[VISIBLE_ROWS];

    private int scrollOffset;

    private long observedAccessGeneration =
            Long.MIN_VALUE;

    @Override
    public void initGui() {
        buttonList.clear();
        fields.clear();
        alignmentField = null;

        for (int i = 0;
             i < VISIBLE_ROWS;
             ++i) {

            rowUuids[i] = null;
            rowEditors[i] = false;
            rowOwners[i] = false;
        }

        observedAccessGeneration =
                GateManagementClientContext
                        .getAccessGeneration();

        TileEntitySiegeGate gate =
                getGate();

        int centerX =
                width / 2;

        if (gate == null
                || !GateManagementClientContext
                .canManagePlayerAccess()) {

            visibleRowCount = 1;

            int top =
                    getTop();

            buttonList.add(
                    new GuiButton(
                            BACK_BUTTON,
                            centerX - 55,
                            top + 80,
                            110,
                            20,
                            "Back"
                    )
            );

            return;
        }

        List<AccessEntry> entries =
                getEntries(
                        gate
                );

        /*
         * The list grows with the whitelist instead of reserving a wall of
         * empty rows. Row 1 is the owner, and exactly one blank row remains
         * after the last visible access entry for the next player.
         */
        visibleRowCount =
                Math.min(
                        VISIBLE_ROWS,
                        Math.max(
                                2,
                                entries.size() + 1
                        )
                );

        int top =
                getTop();

        int panelWidth =
                294;

        int left =
                centerX
                        - panelWidth / 2;

        GuiButton factionAccessButton =
                new GuiButton(
                        FACTION_ACCESS_BUTTON,
                        centerX - 90,
                        top + 29,
                        180,
                        20,
                        getFactionAccessButtonText(
                                gate
                        )
                );

        factionAccessButton.enabled =
                GateManagementClientContext
                        .canManage()
                        && gate.getGateFaction() != null;

        buttonList.add(
                factionAccessButton
        );

        String alignmentLabel =
                "Required Alignment";

        int alignmentFieldWidth =
                60;

        int alignmentGap =
                6;

        int alignmentLabelWidth =
                fontRendererObj
                        .getStringWidth(
                                alignmentLabel
                        );

        int alignmentGroupWidth =
                alignmentLabelWidth
                        + alignmentGap
                        + alignmentFieldWidth;

        int alignmentLabelX =
                centerX
                        - alignmentGroupWidth / 2;

        int alignmentFieldX =
                alignmentLabelX
                        + alignmentLabelWidth
                        + alignmentGap;

        alignmentField =
                new GuiTextField(
                        fontRendererObj,
                        alignmentFieldX,
                        top + 55,
                        alignmentFieldWidth,
                        20
                );

        alignmentField.setMaxStringLength(
                7
        );

        alignmentField.setText(
                Integer.toString(
                        gate.getRequiredAlignment()
                )
        );

        alignmentField.setEnabled(
                canEditAlignment(
                        gate
                )
        );

        int maximumOffset =
                Math.max(
                        0,
                        entries.size()
                                + 1
                                - visibleRowCount
                );

        scrollOffset =
                Math.max(
                        0,
                        Math.min(
                                scrollOffset,
                                maximumOffset
                        )
                );

        int fieldX =
                left + 20;

        int fieldWidth =
                174;

        int actionX =
                fieldX
                        + fieldWidth
                        + 4;

        int actionWidth =
                66;

        int removeX =
                actionX
                        + actionWidth
                        + 4;

        boolean ownerPinned =
                !entries.isEmpty()
                        && entries.get(0).owner;

        for (int row = 0;
             row < visibleRowCount;
             ++row) {

            int y =
                    top
                            + ROWS_TOP_OFFSET
                            + row * ROW_SPACING;

            GuiTextField field =
                    new GuiTextField(
                            fontRendererObj,
                            fieldX,
                            y,
                            fieldWidth,
                            ROW_HEIGHT
                    );

            field.setMaxStringLength(
                    64
            );

            int entryIndex;
            if (ownerPinned
                    && row == 0) {
                entryIndex = 0;
            } else if (ownerPinned) {
                entryIndex =
                        1
                                + scrollOffset
                                + row
                                - 1;
            } else {
                entryIndex =
                        scrollOffset
                                + row;
            }

            if (entryIndex < entries.size()) {
                AccessEntry entry =
                        entries.get(
                                entryIndex
                        );

                rowUuids[row] =
                        entry.uuid;
                rowEditors[row] =
                        entry.editor;
                rowOwners[row] =
                        entry.owner;

                field.setText(
                        entry.displayName
                );

                /* Existing names are identity labels, never rename fields. */
                field.setEnabled(
                        false
                );

                boolean canEditRoles =
                        canManageEditors(
                                gate
                        );

                if (!entry.owner
                        && canEditRoles) {
                    buttonList.add(
                            new GuiButton(
                                    ROW_ACTION_BASE
                                            + row,
                                    actionX,
                                    y,
                                    actionWidth,
                                    ROW_HEIGHT,
                                    entry.editor
                                            ? "Manager"
                                            : "Access"
                            )
                    );
                }

                /*
                 * Managers may remove ordinary Access entries, but only the
                 * Owner/Admin may remove or change Manager entries.
                 */
                if (!entry.owner
                        && (canEditRoles
                        || !entry.editor)) {
                    buttonList.add(
                            new GuiButton(
                                    ROW_REMOVE_BASE
                                            + row,
                                    removeX,
                                    y,
                                    20,
                                    ROW_HEIGHT,
                                    "X"
                            )
                    );
                }

            } else {
                field.setText(
                        ""
                );

                GuiButton addButton =
                        new GuiButton(
                                ROW_ACTION_BASE
                                        + row,
                                actionX,
                                y,
                                actionWidth,
                                ROW_HEIGHT,
                                "Add"
                        );

                /* Blank rows stay clean until the player starts typing. */
                addButton.visible =
                        false;

                buttonList.add(
                        addButton
                );
            }

            fields.add(
                    field
            );
        }

        if (maximumOffset > 0) {
            buttonList.add(
                    new GuiButton(
                            SCROLL_UP_BUTTON,
                            left
                                    + panelWidth
                                    + 4,
                            top + ROWS_TOP_OFFSET,
                            20,
                            ROW_HEIGHT,
                            "^"
                    )
            );

            buttonList.add(
                    new GuiButton(
                            SCROLL_DOWN_BUTTON,
                            left
                                    + panelWidth
                                    + 4,
                            top
                                    + ROWS_TOP_OFFSET
                                    + (visibleRowCount - 1)
                                    * ROW_SPACING,
                            20,
                            ROW_HEIGHT,
                            "v"
                    )
            );

            setButtonEnabled(
                    SCROLL_UP_BUTTON,
                    scrollOffset > 0
            );

            setButtonEnabled(
                    SCROLL_DOWN_BUTTON,
                    scrollOffset
                            < maximumOffset
            );
        }

        buttonList.add(
                new GuiButton(
                        BACK_BUTTON,
                        centerX - 55,
                        top
                                + ROWS_TOP_OFFSET
                                + visibleRowCount * ROW_SPACING
                                + 8,
                        110,
                        20,
                        "Back"
                )
        );
    }

    @Override
    public void drawScreen(
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        drawDefaultBackground();

        int centerX =
                width / 2;

        int top =
                getTop();

        drawCenteredString(
                fontRendererObj,
                "Access & Roles",
                centerX,
                top,
                0xFFFFFF
        );

        TileEntitySiegeGate gate =
                getGate();

        if (gate == null) {
            drawCenteredString(
                    fontRendererObj,
                    "Gate unavailable",
                    centerX,
                    top + 58,
                    0xFF7777
            );
        } else if (alignmentField != null) {
            String alignmentLabel =
                    "Required Alignment";

            int alignmentFieldWidth =
                    60;

            int alignmentGap =
                    6;

            int alignmentLabelWidth =
                    fontRendererObj
                            .getStringWidth(
                                    alignmentLabel
                            );

            int alignmentGroupWidth =
                    alignmentLabelWidth
                            + alignmentGap
                            + alignmentFieldWidth;

            int alignmentLabelX =
                    centerX
                            - alignmentGroupWidth / 2;

            drawString(
                    fontRendererObj,
                    alignmentLabel,
                    alignmentLabelX,
                    top + 61,
                    canEditAlignment(gate)
                            ? 0xBBBBBB
                            : 0x666666
            );

            int panelLeft =
                    centerX - 147;

            drawRect(
                    panelLeft + 20,
                    top + 82,
                    panelLeft + 274,
                    top + 83,
                    0xFF555555
            );

            drawString(
                    fontRendererObj,
                    "Player",
                    panelLeft + 22,
                    top + 89,
                    0x888888
            );

            drawCenteredString(
                    fontRendererObj,
                    "Role",
                    panelLeft + 231,
                    top + 89,
                    0x888888
            );
        }

        for (int row = 0;
             row < fields.size();
             ++row) {

            int y =
                    top
                            + ROWS_TOP_OFFSET
                            + 5
                            + row * ROW_SPACING;

            boolean ownerPinned =
                    rowOwners.length > 0
                            && rowOwners[0];

            int displayIndex;
            if (ownerPinned
                    && row == 0) {
                displayIndex = 1;
            } else if (ownerPinned) {
                displayIndex =
                        scrollOffset
                                + row
                                + 1;
            } else {
                displayIndex =
                        scrollOffset
                                + row
                                + 1;
            }

            drawString(
                    fontRendererObj,
                    Integer.toString(
                            displayIndex
                    )
                            + ".",
                    centerX
                            - 147,
                    y,
                    0xBBBBBB
            );
        }

        updateAddButtonVisibility();

        int left =
                centerX - 147;

        int actionX =
                left + 20 + 174 + 4;

        boolean canEditRoles =
                canManageEditors(
                        gate
                );

        for (int row = 0;
             row < fields.size();
             ++row) {
            if (rowUuids[row] == null) {
                continue;
            }
            if (rowOwners[row]
                    || !canEditRoles) {
                String role =
                        rowOwners[row]
                                ? "Owner"
                                : rowEditors[row]
                                ? "Manager"
                                : "Access";
                int y =
                        top
                                + ROWS_TOP_OFFSET
                                + 5
                                + row * ROW_SPACING;
                drawCenteredString(
                        fontRendererObj,
                        role,
                        actionX + 33,
                        y,
                        0xBBBBBB
                );
            }
        }

        super.drawScreen(
                mouseX,
                mouseY,
                partialTicks
        );

        if (alignmentField != null) {
            alignmentField.drawTextBox();
        }

        for (GuiTextField field
                : fields) {

            field.drawTextBox();
        }

        drawRoleTooltip(
                mouseX,
                mouseY
        );
    }

    private void drawRoleTooltip(
            int mouseX,
            int mouseY
    ) {
        int top =
                getTop();

        int left =
                width / 2 - 147;

        int actionX =
                left + 20 + 174 + 4;

        for (int row = 0;
             row < fields.size();
             ++row) {

            if (rowUuids[row] == null) {
                continue;
            }

            int rowY =
                    top
                            + ROWS_TOP_OFFSET
                            + row * ROW_SPACING;

            if (mouseX < actionX
                    || mouseX >= actionX + 66
                    || mouseY < rowY
                    || mouseY >= rowY + ROW_HEIGHT) {

                continue;
            }

            List<String> tooltip =
                    new ArrayList<String>();

            if (rowOwners[row]) {
                tooltip.add(
                        "Owner"
                );

                tooltip.add(
                        "Full gate control"
                );

            } else if (rowEditors[row]) {
                tooltip.add(
                        "Manager"
                );

                tooltip.add(
                        "Can operate, repair, and configure the gate"
                );

            } else {
                tooltip.add(
                        "Access"
                );

                tooltip.add(
                        "Can operate and repair the gate"
                );
            }

            drawHoveringText(
                    tooltip,
                    mouseX,
                    mouseY,
                    fontRendererObj
            );

            return;
        }
    }

    @Override
    protected void actionPerformed(
            GuiButton button
    ) {
        if (!button.enabled) {
            return;
        }

        if (button.id
                == BACK_BUTTON) {

            returnToManagement();

            return;
        }

        if (button.id
                == FACTION_ACCESS_BUTTON) {

            TileEntitySiegeGate gate =
                    getGate();

            if (gate != null
                    && gate.getGateFaction() != null
                    && GateManagementClientContext
                    .canManage()) {

                sendAction(
                        GateManagementActionPacket
                                .SET_FACTION_ACCESS,
                        gate.isFactionAccessEnabled()
                                ? 0
                                : 1,
                        ""
                );
            }

            return;
        }

        if (button.id
                == SCROLL_UP_BUTTON) {

            if (scrollOffset > 0) {
                --scrollOffset;

                initGui();
            }

            return;
        }

        if (button.id
                == SCROLL_DOWN_BUTTON) {

            ++scrollOffset;

            initGui();

            return;
        }

        if (button.id >= ROW_ACTION_BASE
                && button.id
                < ROW_ACTION_BASE
                + VISIBLE_ROWS) {

            int row =
                    button.id
                            - ROW_ACTION_BASE;

            UUID uuid =
                    rowUuids[row];

            if (uuid == null) {
                String target =
                        fields.get(row)
                                .getText()
                                .trim();

                if (!target.isEmpty()) {
                    sendAction(
                            GateManagementActionPacket
                                    .SET_PLAYER_ACCESS_LEVEL,
                            GateManagementActionPacket
                                    .ACCESS_LEVEL_ACCESS,
                            target
                    );
                }

            } else {
                int newLevel =
                        rowEditors[row]
                                ? GateManagementActionPacket
                                .ACCESS_LEVEL_ACCESS
                                : GateManagementActionPacket
                                .ACCESS_LEVEL_EDITOR;

                sendAction(
                        GateManagementActionPacket
                                .SET_PLAYER_ACCESS_LEVEL,
                        newLevel,
                        uuid.toString()
                );
            }

            return;
        }

        if (button.id >= ROW_REMOVE_BASE
                && button.id
                < ROW_REMOVE_BASE
                + VISIBLE_ROWS) {

            int row =
                    button.id
                            - ROW_REMOVE_BASE;

            UUID uuid =
                    rowUuids[row];

            if (uuid != null) {
                sendAction(
                        GateManagementActionPacket
                                .REMOVE_PLAYER_ACCESS,
                        0,
                        uuid.toString()
                );
            }
        }
    }

    @Override
    protected void mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton
    ) {
        boolean alignmentWasFocused =
                alignmentField != null
                        && alignmentField.isFocused();

        if (alignmentField != null) {
            TileEntitySiegeGate gate =
                    getGate();

            if (canEditAlignment(gate)) {
                alignmentField.mouseClicked(
                        mouseX,
                        mouseY,
                        mouseButton
                );
            } else {
                alignmentField.setFocused(false);
            }
        }

        for (GuiTextField field
                : fields) {

            field.mouseClicked(
                    mouseX,
                    mouseY,
                    mouseButton
            );
        }

        if (alignmentWasFocused
                && alignmentField != null
                && !alignmentField.isFocused()) {
            commitAlignmentField();
        }

        super.mouseClicked(
                mouseX,
                mouseY,
                mouseButton
        );
    }

    @Override
    protected void keyTyped(
            char typedChar,
            int keyCode
    ) {
        if (keyCode
                == Keyboard.KEY_ESCAPE) {

            returnToManagement();

            return;
        }

        boolean enterPressed =
                keyCode
                        == Keyboard.KEY_RETURN
                        || keyCode
                        == Keyboard.KEY_NUMPADENTER;

        if (enterPressed) {
            if (alignmentField != null
                    && alignmentField.isFocused()) {
                commitAlignmentField();
                alignmentField.setFocused(false);
                return;
            }

            for (int row = 0;
                 row < fields.size();
                 ++row) {

                GuiTextField field =
                        fields.get(
                                row
                        );

                if (field.isFocused()
                        && rowUuids[row]
                        == null) {

                    String value =
                            field.getText()
                                    .trim();

                    if (!value.isEmpty()) {
                        sendAction(
                                GateManagementActionPacket
                                        .SET_PLAYER_ACCESS_LEVEL,
                                GateManagementActionPacket
                                        .ACCESS_LEVEL_ACCESS,
                                value
                        );

                        field.setFocused(
                                false
                        );

                        return;
                    }
                }
            }
        }

        if (alignmentField != null
                && alignmentField.textboxKeyTyped(
                typedChar,
                keyCode
        )) {
            return;
        }

        for (GuiTextField field
                : fields) {

            if (field.textboxKeyTyped(
                    typedChar,
                    keyCode
            )) {
                return;
            }
        }

        /*
         * Inventory key returns to Management only when the user was not
         * typing into a name field.
         */
        if (!isAnyFieldFocused()
                && keyCode
                == mc
                .gameSettings
                .keyBindInventory
                .getKeyCode()) {

            returnToManagement();

            return;
        }

        super.keyTyped(
                typedChar,
                keyCode
        );
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        if (alignmentField != null) {
            alignmentField.updateCursorCounter();
        }

        for (GuiTextField field
                : fields) {

            field.updateCursorCounter();
        }

        updateAddButtonVisibility();

        long generation =
                GateManagementClientContext
                        .getAccessGeneration();

        if (generation
                != observedAccessGeneration
                && !isAnyFieldFocused()) {

            observedAccessGeneration =
                    generation;

            initGui();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void returnToManagement() {
        commitAlignmentField();

        mc.displayGuiScreen(
                new GuiGateManagement(
                        false
                )
        );
    }

    private boolean isAnyFieldFocused() {
        if (alignmentField != null
                && alignmentField.isFocused()) {
            return true;
        }

        for (GuiTextField field
                : fields) {

            if (field.isFocused()) {
                return true;
            }
        }

        return false;
    }

    private boolean canEditAlignment(
            TileEntitySiegeGate gate
    ) {
        return GateManagementClientContext
                .canManage()
                && gate != null
                && gate.getGateFaction() != null
                && gate.isFactionAccessEnabled();
    }

    private int getTop() {
        int contentHeight =
                ROWS_TOP_OFFSET
                        + visibleRowCount * ROW_SPACING
                        + 28;

        return Math.max(
                8,
                height / 2
                        - contentHeight / 2
        );
    }

    private void commitAlignmentField() {
        if (alignmentField == null
                || !GateManagementClientContext
                .canManage()) {
            return;
        }

        TileEntitySiegeGate gate =
                getGate();

        if (!canEditAlignment(gate)) {
            return;
        }

        int value =
                parseInteger(
                        alignmentField.getText()
                );

        if (value == gate.getRequiredAlignment()) {
            return;
        }

        sendAction(
                GateManagementActionPacket.SET_ALIGNMENT,
                value,
                ""
        );
    }

    private String getFactionAccessButtonText(
            TileEntitySiegeGate gate
    ) {
        if (gate == null
                || gate.getGateFaction() == null) {

            return "Faction Access: N/A";
        }

        return "Faction Access: "
                + (gate.isFactionAccessEnabled()
                ? "ON"
                : "OFF");
    }

    private List<AccessEntry> getEntries(
            TileEntitySiegeGate gate
    ) {
        List<AccessEntry> result =
                new ArrayList<AccessEntry>();

        List<AccessEntry> nonOwners =
                new ArrayList<AccessEntry>();

        Set<UUID> seen =
                new HashSet<UUID>();

        UUID ownerUuid =
                gate.getOwnerUuid();

        if (ownerUuid != null
                && seen.add(ownerUuid)) {
            result.add(
                    new AccessEntry(
                            ownerUuid,
                            false,
                            true,
                            GateManagementClientContext
                                    .getAccessDisplayName(
                                            ownerUuid
                                    )
                    )
            );
        }

        for (UUID uuid
                : gate.getEditorUuids()) {

            if (uuid != null
                    && seen.add(
                    uuid
            )) {

                nonOwners.add(
                        new AccessEntry(
                                uuid,
                                true,
                                false,
                                GateManagementClientContext
                                        .getAccessDisplayName(
                                                uuid
                                        )
                        )
                );
            }
        }

        for (UUID uuid
                : gate.getOperatorUuids()) {

            if (uuid != null
                    && seen.add(
                    uuid
            )) {

                nonOwners.add(
                        new AccessEntry(
                                uuid,
                                false,
                                false,
                                GateManagementClientContext
                                        .getAccessDisplayName(
                                                uuid
                                        )
                        )
                );
            }
        }

        for (UUID uuid
                : gate
                .getAccessWhitelistUuids()) {

            if (uuid != null
                    && seen.add(
                    uuid
            )) {

                nonOwners.add(
                        new AccessEntry(
                                uuid,
                                false,
                                false,
                                GateManagementClientContext
                                        .getAccessDisplayName(
                                                uuid
                                        )
                        )
                );
            }
        }

        Collections.sort(
                nonOwners,
                new Comparator<AccessEntry>() {

                    @Override
                    public int compare(
                            AccessEntry first,
                            AccessEntry second
                    ) {
                        int name =
                                first
                                        .displayName
                                        .compareToIgnoreCase(
                                                second
                                                        .displayName
                                        );

                        if (name != 0) {
                            return name;
                        }

                        return first
                                .uuid
                                .toString()
                                .compareTo(
                                        second
                                                .uuid
                                                .toString()
                                );
                    }
                }
        );

        result.addAll(
                nonOwners
        );

        return result;
    }

    private TileEntitySiegeGate getGate() {
        if (!GateManagementClientContext
                .isActive()
                || mc.theWorld == null
                || mc
                .theWorld
                .provider.dimensionId
                != GateManagementClientContext
                .getDimensionId()) {

            return null;
        }

        TileEntity tileEntity =
                mc
                        .theWorld
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
                ? (TileEntitySiegeGate)
                tileEntity
                : null;
    }

    private void sendAction(
            int action,
            int value,
            String text
    ) {
        if (!GateManagementClientContext
                .isActive()) {

            return;
        }

        Main.network
                .sendToServer(
                        new GateManagementActionPacket(
                                action,
                                GateManagementClientContext
                                        .getDimensionId(),
                                GateManagementClientContext
                                        .getControllerX(),
                                GateManagementClientContext
                                        .getControllerY(),
                                GateManagementClientContext
                                        .getControllerZ(),
                                value,
                                text
                        )
                );
    }

    private boolean canManageEditors(
            TileEntitySiegeGate gate
    ) {
        if (GateManagementClientContext
                .canAdminister()) {
            return true;
        }
        return gate != null
                && mc.thePlayer != null
                && gate.getOwnerUuid() != null
                && gate.getOwnerUuid().equals(
                mc.thePlayer.getUniqueID()
        );
    }

    private void updateAddButtonVisibility() {
        for (int row = 0;
             row < fields.size();
             ++row) {
            if (rowUuids[row] != null) {
                continue;
            }
            setButtonVisible(
                    ROW_ACTION_BASE + row,
                    !fields.get(row)
                            .getText()
                            .trim()
                            .isEmpty()
            );
        }
    }

    private void setButtonVisible(
            int id,
            boolean visible
    ) {
        for (Object object
                : buttonList) {
            if (object instanceof GuiButton
                    && ((GuiButton)object).id == id) {
                ((GuiButton)object).visible =
                        visible;
                return;
            }
        }
    }

    private void setButtonEnabled(
            int id,
            boolean enabled
    ) {
        for (Object object
                : buttonList) {

            if (object instanceof GuiButton
                    && ((GuiButton)object).id
                    == id) {

                ((GuiButton)object).enabled =
                        enabled;

                return;
            }
        }
    }

    private static int parseInteger(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static final class AccessEntry {

        private final UUID uuid;
        private final boolean editor;
        private final boolean owner;
        private final String displayName;

        private AccessEntry(
                UUID uuid,
                boolean editor,
                boolean owner,
                String displayName
        ) {
            this.uuid =
                    uuid;

            this.editor =
                    editor;

            this.owner =
                    owner;

            this.displayName =
                    displayName == null
                            || displayName.isEmpty()
                            ? uuid.toString()
                            : displayName;
        }
    }
}
