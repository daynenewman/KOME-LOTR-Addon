package com.enovak.lotrmoremobs.siege.client.gui;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.client.GateManagementClientContext;
import com.enovak.lotrmoremobs.siege.client.GateFinalizedInspectionClientContext;
import com.enovak.lotrmoremobs.siege.client.GateEditClientContext;
import com.enovak.lotrmoremobs.siege.edit.GateEditSelectionMode;
import com.enovak.lotrmoremobs.siege.edit.GateEditDraftAction;
import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateControlMode;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.management.FinalizedGateSnapshot;
import com.enovak.lotrmoremobs.siege.network.GateManagementActionPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditStartPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditCancelPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditDraftActionPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditPreflightRequestPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditCommitRequestPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditPreflightSnapshotPacket;
import com.enovak.lotrmoremobs.siege.edit.GateEditPreflightIssueCode;
import com.enovak.lotrmoremobs.siege.edit.GateEditPreflightState;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lotr.common.fac.LOTRFaction;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.tileentity.TileEntity;
import lotr.common.LOTRLevelData;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class GuiGateManagement extends GuiScreen {

    private static final int BEGIN_REPAIR_BUTTON = 0;
    private static final int APPLY_NAME_BUTTON = 1;
    private static final int CYCLE_FACTION_BUTTON = 2;
    private static final int APPLY_ALIGNMENT_BUTTON = 3;
    private static final int TOGGLE_EDITOR_BUTTON = 4;
    private static final int TOGGLE_OPERATOR_BUTTON = 5;
    private static final int TOGGLE_WHITELIST_BUTTON = 6;
    private static final int CLAIM_BUTTON = 7;
    private static final int APPLY_MAX_HEALTH_BUTTON = 8;
    private static final int CLOSE_BUTTON = 9;
    private static final int STRUCTURE_BUTTON = 20;
    private static final int STRUCTURE_BACK_BUTTON = 21;
    private static final int PREVIOUS_LAYER_BUTTON = 22;
    private static final int NEXT_LAYER_BUTTON = 23;
    private static final int BEGIN_EDIT_BUTTON = 24;
    private static final int SELECT_LEFT_BUTTON = 26, SELECT_RIGHT_BUTTON = 27, SELECT_CENTER_BUTTON = 28;
    private static final int LEFT_HINGE_BUTTON = 33, RIGHT_HINGE_BUTTON = 34;
    private static final int DIRECTION_BUTTON = 35, STOP_SELECTION_BUTTON = 36;
    private static final int REFRESH_PREFLIGHT_BUTTON = 37;
    private static final int COMMIT_EDIT_BUTTON = 38;
    private static final int BORDER_TEXTURE_BUTTON = 39;
    private static final int FACTION_PREVIOUS_BUTTON = 40;
    private static final int FACTION_NEXT_BUTTON = 41;
    private static final int PLAYER_ACCESS_BUTTON = 42;
    private static final int CONTROLLER_APPEARANCE_BUTTON = 43;
    private static final int GATE_CONTROL_MODE_BUTTON = 44;
    private static final int BUILD_TIP_WIDGET_ID = -2000;

    private static final ResourceLocation COIN_TEXTURE =
            new ResourceLocation(
                    "lotrmoremobs",
                    "textures/gui/coin.png"
            );

    private final List<LOTRFaction> factions =
            new ArrayList<LOTRFaction>();
    private GuiTextField nameField;
    private GuiTextField maxHealthField;
    private String selectedFactionName = "";
    private boolean structurePage;
    private FinalizedGateSnapshot structureSnapshot;
    private GateStructureInspectionViewModel structureViewModel;
    private int selectedStructureLayer;
    private long observedEditGeneration = Long.MIN_VALUE;
    private boolean leavingForWorldSelection;
    private boolean waitingForEditStart;
    private boolean leavingForPlayerAccess;
    private boolean leavingForAppearancePicker;
    private GuiGateBuildTipWidget buildTipWidget;

    public GuiGateManagement() {
    }

    public GuiGateManagement(boolean openStructurePage) {
        structurePage = openStructurePage;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buildTipWidget = null;

        if (structurePage) {
            initStructurePage();
            return;
        }

        TileEntitySiegeGate gate =
                getGate();

        int centerX =
                width / 2;

        int top =
                Math.max(
                        8,
                        height / 2 - 125
                );

        /*
         * Gate identity. Keep the editable name prominent without spending a
         * full section of vertical space on it.
         */
        nameField =
                createField(
                        centerX - 105,
                        top + 14,
                        210,
                        TileEntitySiegeGate
                                .MAX_GATE_NAME_LENGTH,
                        gate == null
                                ? ""
                                : gate.getGateName()
                );

        /*
         * Keep the secondary controls visually lighter than Gate Name.
         * Max HP stays compact under Health, while Mode mirrors it under State.
         */
        maxHealthField =
                createField(
                        centerX - 70,
                        top + 61,
                        58,
                        7,
                        gate == null
                                ? Integer.toString(
                                TileEntitySiegeGate
                                        .getConfiguredDefaultMaxHealth()
                        )
                                : Integer.toString(
                                gate.getMaxHealth()
                        )
                );

        buttonList.add(
                new GuiButton(
                        GATE_CONTROL_MODE_BUTTON,
                        centerX + 42,
                        top + 61,
                        84,
                        20,
                        gate == null
                                ? GateControlMode.AUTOMATIC.getDisplayName()
                                : gate.getGateControlMode().getDisplayName()
                )
        );

        selectedFactionName =
                gate == null
                        || gate.getGateFaction() == null
                        ? ""
                        : gate
                        .getGateFaction()
                        .codeName();

        int factionWidth =
                132;

        int factionX =
                centerX
                        - factionWidth / 2;

        buttonList.add(
                new GuiGateFactionArrowButton(
                        FACTION_PREVIOUS_BUTTON,
                        factionX - 22,
                        top + 103,
                        true
                )
        );

        buttonList.add(
                new GuiGateFactionArrowButton(
                        FACTION_NEXT_BUTTON,
                        factionX
                                + factionWidth
                                + 2,
                        top + 103,
                        false
                )
        );

        /*
         * Compact navigation row:
         * [ appearance icon ] [ Access & Roles ] [ Edit Gate ]
         */
        int actionRowX =
                centerX - 123;

        int actionRowY =
                top + 142;

        buttonList.add(
                new GuiGateAppearanceButton(
                        CONTROLLER_APPEARANCE_BUTTON,
                        actionRowX,
                        actionRowY
                )
        );

        buttonList.add(
                new GuiButton(
                        PLAYER_ACCESS_BUTTON,
                        actionRowX + 26,
                        actionRowY,
                        128,
                        20,
                        "Access & Roles"
                )
        );

        buttonList.add(
                new GuiButton(
                        STRUCTURE_BUTTON,
                        actionRowX + 160,
                        actionRowY,
                        86,
                        20,
                        "Edit Gate"
                )
        );

        /*
         * Repair is one full-width stateful control instead of a button plus
         * a second competing status label.
         */
        buttonList.add(
                new GuiGateRepairButton(
                        BEGIN_REPAIR_BUTTON,
                        centerX - 90,
                        top + 176,
                        180,
                        20,
                        "Repair Gate"
                )
        );

        buttonList.add(
                new GuiButton(
                        CLOSE_BUTTON,
                        centerX - 45,
                        top + 216,
                        90,
                        20,
                        "Close"
                )
        );
    }

    @Override
    protected void actionPerformed(
            GuiButton button
    ) {
        if (!button.enabled) {
            return;
        }

        if (button.id
                == STRUCTURE_BUTTON) {

            waitingForEditStart =
                    true;

            observedEditGeneration =
                    GateEditClientContext
                            .getGeneration();

            Main.network.sendToServer(
                    new GateEditStartPacket()
            );

        } else if (button.id
                == BEGIN_EDIT_BUTTON) {

            Main.network.sendToServer(
                    new GateEditStartPacket()
            );

        } else if (button.id
                == CONTROLLER_APPEARANCE_BUTTON) {

            leavingForAppearancePicker =
                    true;

            mc.displayGuiScreen(
                    new GuiGateBlockPicker()
            );

        } else if (button.id
                == SELECT_LEFT_BUTTON) {

            beginWorldSelection(
                    GateEditSelectionMode
                            .SELECT_LEFT
            );

        } else if (button.id
                == SELECT_RIGHT_BUTTON) {

            beginWorldSelection(
                    GateEditSelectionMode
                            .SELECT_RIGHT
            );

        } else if (button.id
                == SELECT_CENTER_BUTTON) {

            beginWorldSelection(
                    GateEditSelectionMode
                            .SELECT_SPLIT_CENTER
            );

        } else if (button.id
                == LEFT_HINGE_BUTTON) {

            beginWorldSelection(
                    GateEditSelectionMode
                            .SET_LEFT_HINGE
            );

        } else if (button.id
                == RIGHT_HINGE_BUTTON) {

            beginWorldSelection(
                    GateEditSelectionMode
                            .SET_RIGHT_HINGE
            );

        } else if (button.id
                == DIRECTION_BUTTON) {

            if (GateEditClientContext
                    .isActive()) {

                Main.network.sendToServer(
                        new GateEditDraftActionPacket(
                                GateEditClientContext
                                        .getToken(),
                                GateEditClientContext
                                        .getDirection()
                                        == com.enovak.lotrmoremobs
                                        .siege.gate
                                        .GateOpeningDirection
                                        .FORWARD
                                        ? GateEditDraftAction
                                        .SET_DIRECTION_BACKWARD
                                        : GateEditDraftAction
                                        .SET_DIRECTION_FORWARD,
                                0,
                                0,
                                0
                        )
                );

            } else if (button.id
                    == CONTROLLER_APPEARANCE_BUTTON) {

                leavingForAppearancePicker =
                        true;

                mc.displayGuiScreen(
                        new GuiGateBlockPicker()
                );
            }

        } else if (button.id
                == BORDER_TEXTURE_BUTTON) {

            if (GateEditClientContext
                    .isActive()) {

                Main.network.sendToServer(
                        new GateEditDraftActionPacket(
                                GateEditClientContext
                                        .getToken(),
                                GateEditClientContext
                                        .isBorderTextureEnabled()
                                        ? GateEditDraftAction
                                        .SET_BORDER_TEXTURE_DISABLED
                                        : GateEditDraftAction
                                        .SET_BORDER_TEXTURE_ENABLED,
                                0,
                                0,
                                0
                        )
                );
            }

        } else if (button.id
                == STOP_SELECTION_BUTTON) {

            GateEditClientContext
                    .setSelectionMode(
                            GateEditSelectionMode.NONE
                    );

        } else if (button.id
                == REFRESH_PREFLIGHT_BUTTON) {

            if (GateEditClientContext
                    .getToken()
                    != null) {

                Main.network.sendToServer(
                        new GateEditPreflightRequestPacket(
                                GateEditClientContext
                                        .getToken()
                        )
                );
            }

        } else if (button.id
                == COMMIT_EDIT_BUTTON) {

            if (GateEditClientContext
                    .beginCommitRequest()) {

                button.enabled =
                        false;

                Main.network.sendToServer(
                        new GateEditCommitRequestPacket(
                                GateEditClientContext
                                        .getToken(),
                                GateEditClientContext
                                        .getSequence()
                        )
                );
            }

        } else if (button.id
                == STRUCTURE_BACK_BUTTON) {

            /*
             * While an edit session is active, Back means:
             *
             * discard the draft
             * stop the server edit session
             * return to normal Gate Management
             */
            if (GateEditClientContext.isActive()
                    && GateEditClientContext
                    .getToken()
                    != null) {

                Main.network.sendToServer(
                        new GateEditCancelPacket(
                                GateEditClientContext
                                        .getToken()
                        )
                );
            }

            structurePage =
                    false;

            initGui();

        } else if (button.id
                == PREVIOUS_LAYER_BUTTON) {

            if (selectedStructureLayer > 0) {
                --selectedStructureLayer;
            }

        } else if (button.id
                == NEXT_LAYER_BUTTON) {

            if (structureViewModel != null
                    && selectedStructureLayer + 1
                    < structureViewModel
                    .getLayerCount()) {

                ++selectedStructureLayer;
            }

        } else if (button.id
                == GATE_CONTROL_MODE_BUTTON) {

            TileEntitySiegeGate gate =
                    getGate();

            if (gate != null) {
                GateControlMode nextMode =
                        gate.getGateControlMode().next();

                sendAction(
                        GateManagementActionPacket
                                .SET_GATE_CONTROL_MODE,
                        nextMode.getNetworkId(),
                        ""
                );
            }

        } else if (button.id
                == PLAYER_ACCESS_BUTTON) {

            leavingForPlayerAccess =
                    true;

            mc.displayGuiScreen(
                    new GuiGatePlayerAccess()
            );

        } else if (button.id
                == FACTION_PREVIOUS_BUTTON) {

            cycleFaction(
                    -1
            );

            sendAction(
                    GateManagementActionPacket
                            .SET_FACTION,
                    0,
                    selectedFactionName
            );

        } else if (button.id
                == FACTION_NEXT_BUTTON) {

            cycleFaction(
                    1
            );

            sendAction(
                    GateManagementActionPacket
                            .SET_FACTION,
                    0,
                    selectedFactionName
            );

        } else if (button.id
                == CLOSE_BUTTON) {

            mc.displayGuiScreen(
                    null
            );

        } else if (button.id
                == BEGIN_REPAIR_BUTTON) {

            sendAction(
                    GateManagementActionPacket
                            .BEGIN_REPAIR,
                    0,
                    ""
            );
        }
    }

    @Override
    public void drawScreen(
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        drawDefaultBackground();

        if (structurePage) {
            drawStructurePage(
                    mouseX,
                    mouseY
            );

            super.drawScreen(
                    mouseX,
                    mouseY,
                    partialTicks
            );

            drawBuildTipTooltip(
                    mouseX,
                    mouseY
            );

            return;
        }

        TileEntitySiegeGate gate =
                getGate();

        int centerX =
                width / 2;

        int top =
                Math.max(
                        8,
                        height / 2 - 125
                );

        if (gate == null
                || !gate.isFinalized()) {

            drawCenteredString(
                    fontRendererObj,
                    "Siege Gate unavailable",
                    centerX,
                    top + 100,
                    0xFF7777
            );

            super.drawScreen(
                    mouseX,
                    mouseY,
                    partialTicks
            );

            return;
        }

        boolean canManage =
                GateManagementClientContext
                        .canManage();

        boolean creative =
                mc.thePlayer != null
                        && mc.thePlayer
                        .capabilities
                        .isCreativeMode;

        /*
         * Keep the overview dense but readable: identity, condition, control,
         * faction, deep-management actions, maintenance, exit.
         */
        drawCenteredString(
                fontRendererObj,
                "Gate Name",
                centerX,
                top,
                0xBBBBBB
        );

        int leftColumnX =
                centerX - 116;

        int rightColumnX =
                centerX + 14;

        drawString(
                fontRendererObj,
                "Health:",
                leftColumnX,
                top + 43,
                0xBBBBBB
        );

        drawString(
                fontRendererObj,
                gate.getCurrentHealth()
                        + " / "
                        + gate.getMaxHealth(),
                centerX - 74,
                top + 43,
                0xCCCCCC
        );

        drawString(
                fontRendererObj,
                "State:",
                rightColumnX,
                top + 43,
                0xBBBBBB
        );

        drawString(
                fontRendererObj,
                gate.getGateState().name(),
                centerX + 53,
                top + 43,
                0xCCCCCC
        );

        drawString(
                fontRendererObj,
                "Max HP:",
                leftColumnX,
                top + 67,
                creative
                        ? 0xBBBBBB
                        : 0x666666
        );

        drawString(
                fontRendererObj,
                "Mode:",
                rightColumnX,
                top + 67,
                canManage
                        ? 0xBBBBBB
                        : 0x666666
        );

        drawCenteredString(
                fontRendererObj,
                "Faction",
                centerX,
                top + 91,
                0xBBBBBB
        );

        drawFactionBox(
                centerX,
                top + 103
        );

        drawSectionDivider(
                centerX,
                top + 132
        );

        List<String> selectableFactions =
                getSelectableFactionNames();

        boolean canCycleFaction =
                canManage
                        && (selectableFactions.size() > 1
                        || !selectableFactions.contains(
                        selectedFactionName
                ));

        setButtonEnabled(
                FACTION_PREVIOUS_BUTTON,
                canCycleFaction
        );

        setButtonEnabled(
                FACTION_NEXT_BUTTON,
                canCycleFaction
        );

        setButtonEnabled(
                PLAYER_ACCESS_BUTTON,
                GateManagementClientContext
                        .canManagePlayerAccess()
        );

        GuiButton gateModeButton =
                getButton(
                        GATE_CONTROL_MODE_BUTTON
                );

        if (gateModeButton != null) {
            gateModeButton.displayString =
                    gate.getGateControlMode()
                            .getDisplayName();
            gateModeButton.enabled =
                    canManage;
        }

        setButtonEnabled(
                STRUCTURE_BUTTON,
                canManage
        );

        setButtonEnabled(
                CONTROLLER_APPEARANCE_BUTTON,
                canManage
        );

        nameField.setEnabled(
                canManage
        );

        maxHealthField.setEnabled(
                creative
        );

        updateRepairButton(
                gate
        );

        super.drawScreen(
                mouseX,
                mouseY,
                partialTicks
        );

        nameField.drawTextBox();

        maxHealthField.drawTextBox();

        /* Tooltip only over the black faction box itself. */
        drawFactionRequirementTooltip(
                mouseX,
                mouseY,
                centerX,
                top + 103
        );

        if (!creative) {
            drawCreativeHealthTooltip(
                    mouseX,
                    mouseY,
                    centerX - 40,
                    top + 61
            );
        }

        drawControllerAppearanceTooltip(
                mouseX,
                mouseY
        );

        drawGateControlModeTooltip(
                mouseX,
                mouseY,
                gate
        );
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        if (waitingForEditStart
                && GateEditClientContext
                .isActive()) {

            waitingForEditStart =
                    false;

            structurePage =
                    true;

            observedEditGeneration =
                    GateEditClientContext
                            .getGeneration();

            initGui();

            return;
        }

        if (structurePage) {
            long generation =
                    GateEditClientContext
                            .getGeneration();

            if (generation
                    != observedEditGeneration) {

                observedEditGeneration =
                        generation;

                initGui();
            }

            return;
        }

        if (nameField != null) {
            nameField
                    .updateCursorCounter();
        }


        if (maxHealthField != null) {
            maxHealthField
                    .updateCursorCounter();
        }
    }

    @Override
    protected void keyTyped(
            char typedChar,
            int keyCode
    ) {
        /*
         * ESC while EDIT_EXISTING is open means "discard changes".
         *
         * Do not let GuiScreen's default ESC handling merely close the screen
         * while leaving the authoritative transient edit lease alive.
         */
        if (structurePage
                && keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE
                && GateEditClientContext.isActive()
                && GateEditClientContext.getToken() != null) {

            UUID token =
                    GateEditClientContext.getToken();

            /*
             * Clear the local mirror immediately so overlays/selection state
             * disappear in the same input event. The server remains
             * authoritative and receives the token-bound cancellation below.
             */
            GateEditClientContext.clear();

            Main.network.sendToServer(
                    new GateEditCancelPacket(
                            token
                    )
            );

            mc.displayGuiScreen(
                    null
            );

            return;
        }

        /*
         * Use the player's configured Inventory key to exit the gate GUI.
         *
         * On the normal management page, do not intercept it while the player
         * is actively typing in a text box.
         *
         * The structural edit page has no text boxes, so the Inventory key
         * always exits there.
         */
        if (keyCode
                == mc.gameSettings
                .keyBindInventory
                .getKeyCode()) {

            if (structurePage) {
                /*
                 * Leaving during EDIT_EXISTING means discard the current draft,
                 * exactly like the Discard Changes button should.
                 */
                if (GateEditClientContext.isActive()
                        && GateEditClientContext
                        .getToken()
                        != null) {

                    UUID token =
                            GateEditClientContext
                                    .getToken();

                    GateEditClientContext.clear();

                    Main.network.sendToServer(
                            new GateEditCancelPacket(
                                    token
                            )
                    );
                }

                mc.displayGuiScreen(
                        null
                );

                return;
            }

            if (!isManagementTextFieldFocused()) {
                /*
                 * onGuiClosed() will commit any already-unfocused management
                 * values and clean up the normal management context.
                 */
                mc.displayGuiScreen(
                        null
                );

                return;
            }
        }

        if (structurePage) {
            super.keyTyped(
                    typedChar,
                    keyCode
            );

            return;
        }

        boolean enterPressed =
                keyCode
                        == org.lwjgl.input.Keyboard.KEY_RETURN
                        || keyCode
                        == org.lwjgl.input.Keyboard.KEY_NUMPADENTER;

        if (enterPressed) {
            if (nameField != null
                    && nameField.isFocused()) {

                commitNameField();

                nameField.setFocused(
                        false
                );

                return;
            }


            if (maxHealthField != null
                    && maxHealthField.isFocused()) {

                commitMaxHealthField();

                maxHealthField.setFocused(
                        false
                );

                return;
            }
        }

        if ((nameField != null
                && nameField.textboxKeyTyped(
                typedChar,
                keyCode
        ))
                || (maxHealthField != null
                && maxHealthField.textboxKeyTyped(
                typedChar,
                keyCode
        ))) {

            return;
        }

        super.keyTyped(
                typedChar,
                keyCode
        );
    }

    @Override
    protected void mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton
    ) {
        if (structurePage) {
            super.mouseClicked(
                    mouseX,
                    mouseY,
                    mouseButton
            );

            return;
        }

        boolean nameWasFocused =
                nameField.isFocused();
        boolean maxHealthWasFocused =
                maxHealthField
                        .isFocused();

        nameField.mouseClicked(
                mouseX,
                mouseY,
                mouseButton
        );
        maxHealthField.mouseClicked(
                mouseX,
                mouseY,
                mouseButton
        );

        if (nameWasFocused
                && !nameField
                .isFocused()) {

            commitNameField();
        }
        if (maxHealthWasFocused
                && !maxHealthField
                .isFocused()) {

            commitMaxHealthField();
        }

        super.mouseClicked(
                mouseX,
                mouseY,
                mouseButton
        );
    }

    @Override
    public void onGuiClosed() {
        if (!structurePage) {
            commitNameField();

            commitMaxHealthField();
        }

        /*
         * Opening the Player Access submenu must not destroy the management
         * context it needs to return here.
         */
        if (leavingForPlayerAccess
                || leavingForAppearancePicker) {

            leavingForPlayerAccess =
                    false;

            leavingForAppearancePicker =
                    false;

            super.onGuiClosed();

            return;
        }

        if (GateEditClientContext
                .isActive()) {

            if (!leavingForWorldSelection) {
                GateEditClientContext
                        .setSelectionMode(
                                GateEditSelectionMode.NONE
                        );
            }

            GateManagementClientContext
                    .clear();

            super.onGuiClosed();

            return;
        }

        GateFinalizedInspectionClientContext
                .clear();

        GateManagementClientContext
                .clear();

        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void initStructurePage() {
        int centerX =
                width / 2;

        observedEditGeneration =
                GateEditClientContext
                        .getGeneration();

        int rowWidth =
                292;

        int sideWidth =
                88;

        int centerWidth =
                104;

        int gap =
                6;

        int hingeWidth =
                136;

        int directionWidth =
                194;

        int borderWidth =
                92;

        int actionWidth =
                118;

        int actionGap =
                8;

        int backWidth =
                220;

        int leafLeftX =
                centerX
                        - rowWidth / 2;

        int centerSplitX =
                leafLeftX
                        + sideWidth
                        + gap;

        int rightX =
                centerSplitX
                        + centerWidth
                        + gap;

        int top =
                height / 2 - 58;

        if (!GateEditClientContext.isActive()) {
            buttonList.add(
                    new GuiButton(
                            STRUCTURE_BACK_BUTTON,
                            centerX
                                    - backWidth / 2,
                            top + 60,
                            backWidth,
                            20,
                            "Back"
                    )
            );

            return;
        }

        buttonList.add(
                new GuiButton(
                        SELECT_LEFT_BUTTON,
                        leafLeftX,
                        top,
                        sideWidth,
                        20,
                        "Select LEFT"
                )
        );

        buttonList.add(
                new GuiButton(
                        SELECT_CENTER_BUTTON,
                        centerSplitX,
                        top,
                        centerWidth,
                        20,
                        "Center Split"
                )
        );

        buttonList.add(
                new GuiButton(
                        SELECT_RIGHT_BUTTON,
                        rightX,
                        top,
                        sideWidth,
                        20,
                        "Select RIGHT"
                )
        );

        /*
         * Compact hinge row.
         */
        int hingeRowWidth =
                hingeWidth
                        * 2
                        + gap;

        int hingeLeftX =
                centerX
                        - hingeRowWidth / 2;

        buttonList.add(
                new GuiButton(
                        LEFT_HINGE_BUTTON,
                        hingeLeftX,
                        top + 30,
                        hingeWidth,
                        20,
                        "Set LEFT Hinge"
                )
        );

        buttonList.add(
                new GuiButton(
                        RIGHT_HINGE_BUTTON,
                        hingeLeftX
                                + hingeWidth
                                + gap,
                        top + 30,
                        hingeWidth,
                        20,
                        "Set RIGHT Hinge"
                )
        );

        buttonList.add(
                new GuiButton(
                        DIRECTION_BUTTON,
                        leafLeftX,
                        top + 60,
                        directionWidth,
                        20,
                        getEditDirectionButtonText()
                )
        );

        buttonList.add(
                new GuiButton(
                        BORDER_TEXTURE_BUTTON,
                        leafLeftX
                                + directionWidth
                                + gap,
                        top + 60,
                        borderWidth,
                        20,
                        getEditBorderButtonText()
                )
        );

        /*
         * Smaller edit actions.
         */
        int actionRowWidth =
                actionWidth
                        * 2
                        + actionGap;

        int actionLeftX =
                centerX
                        - actionRowWidth / 2;

        buildTipWidget =
                new GuiGateBuildTipWidget(
                        BUILD_TIP_WIDGET_ID,
                        actionLeftX
                                - actionGap
                                - 20,
                        top + 90
                );

        buttonList.add(
                buildTipWidget
        );

        buttonList.add(
                new GuiButton(
                        STRUCTURE_BACK_BUTTON,
                        actionLeftX,
                        top + 90,
                        actionWidth,
                        20,
                        "Discard Changes"
                )
        );

        buttonList.add(
                new GuiButton(
                        COMMIT_EDIT_BUTTON,
                        actionLeftX
                                + actionWidth
                                + actionGap,
                        top + 90,
                        actionWidth,
                        20,
                        "Commit Edit"
                )
        );
    }

    private void drawBuildTipTooltip(
            int mouseX,
            int mouseY
    ) {
        if (buildTipWidget == null
                || !buildTipWidget.isMouseOver(
                mouseX,
                mouseY
        )) {
            return;
        }

        List<String> tooltip =
                new ArrayList<String>();

        tooltip.add(
                "Build tip:"
        );

        tooltip.add(
                "Shift-right-click inside an enclosed area"
        );

        tooltip.add(
                "of selected blocks to fill it and save time."
        );

        drawHoveringText(
                tooltip,
                mouseX,
                mouseY,
                fontRendererObj
        );
    }

    private void drawControllerAppearanceTooltip(
            int mouseX,
            int mouseY
    ) {
        GuiButton button =
                getButton(
                        CONTROLLER_APPEARANCE_BUTTON
                );

        if (button == null
                || !button.visible
                || mouseX < button.xPosition
                || mouseX >= button.xPosition + button.width
                || mouseY < button.yPosition
                || mouseY >= button.yPosition + button.height) {

            return;
        }

        List<String> tooltip =
                new ArrayList<String>();

        tooltip.add(
                "Gate controller block texture"
        );

        drawHoveringText(
                tooltip,
                mouseX,
                mouseY,
                fontRendererObj
        );
    }

    private boolean isManagementTextFieldFocused() {
        return nameField != null
                && nameField.isFocused()
                || maxHealthField != null
                && maxHealthField.isFocused();
    }

    private void drawSectionDivider(
            int centerX,
            int y
    ) {
        int halfWidth =
                118;

        drawRect(
                centerX - halfWidth,
                y,
                centerX + halfWidth,
                y + 1,
                0x55777777
        );
    }

    private void drawFactionRequirementTooltip(
            int mouseX,
            int mouseY,
            int centerX,
            int factionY
    ) {
        int boxWidth =
                132;

        int left =
                centerX
                        - boxWidth / 2;

        int right =
                left + boxWidth;

        /*
         * Deliberately excludes both arrow buttons.
         */
        if (mouseX < left
                || mouseX >= right
                || mouseY < factionY
                || mouseY >= factionY + 20) {

            return;
        }

        List<String> tooltip =
                new ArrayList<String>();

        tooltip.add(
                "You need +100 alignment with a faction"
        );

        tooltip.add(
                "to build this gate in their name."
        );

        drawHoveringText(
                tooltip,
                mouseX,
                mouseY,
                fontRendererObj
        );
    }

    private void drawCreativeHealthTooltip(
            int mouseX,
            int mouseY,
            int centerX,
            int fieldY
    ) {
        if (mouseX < centerX - 41
                || mouseX > centerX + 41
                || mouseY < fieldY
                || mouseY > fieldY + 20) {

            return;
        }

        List<String> tooltip =
                new ArrayList<String>();

        tooltip.add(
                "Creative mode only"
        );

        drawHoveringText(
                tooltip,
                mouseX,
                mouseY,
                fontRendererObj
        );
    }

    private void drawStructurePage(
            int mouseX,
            int mouseY
    ) {
        int centerX =
                width / 2;

        if (GateEditClientContext.isActive()) {
            drawActiveEditPage(
                    centerX
            );

            return;
        }

        drawCenteredString(
                fontRendererObj,
                "Siege Gate Edit",
                centerX,
                height / 2 - 42,
                0xFFFFFF
        );

        drawCenteredString(
                fontRendererObj,
                "Opening...",
                centerX,
                height / 2 - 24,
                0xAAAAAA
        );
    }

    private void drawActiveEditPage(
            int centerX
    ) {
        updateEditDirectionButton();
        updateEditBorderButton();

        setButtonEnabled(
                COMMIT_EDIT_BUTTON,
                GateEditClientContext
                        .isCommitReady()
        );

        int rowWidth =
                292;

        int sideWidth =
                88;

        int centerWidth =
                104;

        int gap =
                6;

        int hingeWidth =
                136;

        int leafLeftX =
                centerX
                        - rowWidth / 2;

        int centerSplitX =
                leafLeftX
                        + sideWidth
                        + gap;

        int rightX =
                centerSplitX
                        + centerWidth
                        + gap;

        int hingeRowWidth =
                hingeWidth
                        * 2
                        + gap;

        int hingeLeftX =
                centerX
                        - hingeRowWidth / 2;

        int top =
                height / 2 - 58;

        drawCenteredString(
                fontRendererObj,
                "Siege Gate Edit",
                centerX,
                top - 40,
                0xFFFFFF
        );

        GateEditPreflightSnapshotPacket status =
                GateEditClientContext
                        .getPreflight();

        String statusText;
        int statusColor;

        if (status == null) {
            statusText =
                    "Checking...";

            statusColor =
                    0xAAAAAA;

        } else if (status.isCommitReady()) {
            statusText =
                    "Ready"
                            + (status.getChangeCount() > 0
                            ? "   |   "
                            + status.getChangeCount()
                            + " changes"
                            : "");

            statusColor =
                    0x77DD77;

        } else if (status.getState()
                == GateEditPreflightState.NO_CHANGES) {

            statusText =
                    "No changes";

            statusColor =
                    0xAAAAAA;

        } else {
            List<String> reasons =
                    statusLines(
                            status
                    );

            if (!reasons.isEmpty()) {
                statusText =
                        reasons.get(0);

            } else {
                statusText =
                        readinessText(
                                status.getState()
                        );
            }

            if (status.getChangeCount() > 0) {
                statusText +=
                        "   |   "
                                + status.getChangeCount()
                                + " changes";
            }

            statusColor =
                    status.isStructurallyValid()
                            ? 0xFFCC55
                            : 0xFF7777;
        }

        drawCenteredString(
                fontRendererObj,
                statusText,
                centerX,
                top - 24,
                statusColor
        );

        boolean needsLeftLeaf =
                !editHasActualLeaf(
                        GateLeaf.LEFT
                );

        boolean needsRightLeaf =
                !editHasActualLeaf(
                        GateLeaf.RIGHT
                );

        boolean needsLeftHinge =
                GateEditClientContext
                        .getLeftHinge()
                        == null;

        boolean needsRightHinge =
                GateEditClientContext
                        .getRightHinge()
                        == null;

        drawRequirementOrb(
                leafLeftX - 10,
                top + 10,
                needsLeftLeaf
        );

        drawRequirementOrb(
                rightX
                        + sideWidth
                        + 9,
                top + 10,
                needsRightLeaf
        );

        drawRequirementOrb(
                hingeLeftX - 10,
                top + 40,
                needsLeftHinge
        );

        drawRequirementOrb(
                hingeLeftX
                        + hingeRowWidth
                        + 9,
                top + 40,
                needsRightHinge
        );
    }

    private boolean editHasActualLeaf(
            GateLeaf leaf
    ) {
        for (GateEditClientContext.DraftPart part
                : GateEditClientContext
                .getParts()) {

            if (part == null
                    || part.leaf == null) {
                continue;
            }

            if (leaf == GateLeaf.LEFT
                    && part.leaf.isActualLeft()) {
                return true;
            }

            if (leaf == GateLeaf.RIGHT
                    && part.leaf.isActualRight()) {
                return true;
            }
        }

        return false;
    }

    private void drawRequirementOrb(
            int centerX,
            int centerY,
            boolean show
    ) {
        if (!show) {
            return;
        }

        double phase =
                (System.currentTimeMillis()
                        % 2000L)
                        / 2000.0D
                        * Math.PI
                        * 2.0D;

        double pulse =
                0.5D
                        + 0.5D
                        * Math.sin(
                        phase
                );

        int outerAlpha =
                45
                        + (int)(
                        pulse
                                * 55.0D
                );

        int innerAlpha =
                135
                        + (int)(
                        pulse
                                * 100.0D
                );

        int outerColor =
                (outerAlpha << 24)
                        | 0xCFE3FF;

        int innerColor =
                (innerAlpha << 24)
                        | 0xEDF6FF;

        drawRect(
                centerX - 2,
                centerY - 4,
                centerX + 3,
                centerY - 3,
                outerColor
        );

        drawRect(
                centerX - 3,
                centerY - 3,
                centerX + 4,
                centerY + 4,
                outerColor
        );

        drawRect(
                centerX - 2,
                centerY + 4,
                centerX + 3,
                centerY + 5,
                outerColor
        );

        drawRect(
                centerX - 1,
                centerY - 2,
                centerX + 2,
                centerY + 3,
                innerColor
        );
    }

    private static String readinessText(GateEditPreflightState state) {
        if (state == GateEditPreflightState.READY) return "Ready";
        if (state == GateEditPreflightState.NO_CHANGES) return "No changes";
        if (state == GateEditPreflightState.INVALID_DRAFT) return "Invalid draft";
        if (state == GateEditPreflightState.STALE_SESSION) return "Restart editing";
        return "Not ready";
    }

    private static List<String> statusLines(GateEditPreflightSnapshotPacket status) {
        List<String> result = new ArrayList<String>(); int visibleIssues = 0;
        for (GateEditPreflightSnapshotPacket.Issue issue : status.getIssues()) {
            if (issue.code == GateEditPreflightIssueCode.NO_CHANGES || issue.code == GateEditPreflightIssueCode.NONE) continue;
            ++visibleIssues;
            if (result.size() == 3) continue;
            String text = issueText(issue.code);
            if (issue.count > 1) text += " (" + issue.count + ")";
            result.add(text);
        }
        int hidden = visibleIssues - result.size();
        if (hidden > 0 && result.size() < 3) result.add("+" + hidden + " more");
        return result;
    }

    private static String issueText(GateEditPreflightIssueCode code) {
        switch (code) {
            case LEFT_LEAF_DISCONNECTED: return "Left leaf is disconnected";
            case RIGHT_LEAF_DISCONNECTED: return "Right leaf is disconnected";
            case INVALID_HINGE: return "Hinge configuration is invalid";
            case INVALID_SPLIT_CENTER: return "Center split is invalid";
            case EMPTY_LEAF: return "Both leaves need parts";
            case GATE_NOT_CLOSED: return "Gate must be CLOSED";
            case REPAIR_ACTIVE: return "Repair is active";
            case RAM_RESERVED: return "Battle Ram has reserved this gate";
            case CHUNK_UNLOADED: return "An affected chunk is unloaded";
            case SOURCE_CHANGED: return "A drafted source block changed";
            case REMOVE_TARGET_CHANGED: return "A removal target changed";
            case FOREIGN_OWNER: return "A target is owned by another gate";
            case STALE_REVISION: return "Gate changed since editing began";
            case UUID_MISMATCH: return "Gate identity changed";
            case QUARANTINED: return "Gate structure is quarantined";
            case MUTATION_IN_PROGRESS: return "Another gate mutation is active";
            case NO_PERMISSION: return "You no longer can manage this gate";
            case ORIGINAL_MISMATCH: return "Live gate no longer matches the edit origin";
            case OWNERSHIP_MISMATCH: return "Durable ownership no longer matches";
            case BANNER_ATTACHMENT_CONFLICT: return "A LOTR banner conflicts with this edit";
            default: return code.name().replace('_', ' ').toLowerCase();
        }
    }

    private void drawStructureSummary(
            FinalizedGateSnapshot snapshot,
            int centerX
    ) {
        if (structureViewModel.hasOrientation()) {
            drawCenteredString(
                    fontRendererObj,
                    "Size: " + structureViewModel.getWidth() + " x "
                            + structureViewModel.getHeight() + " x "
                            + structureViewModel.getThickness()
                            + "   Revision: "
                            + snapshot.getBaseStructureRevision(),
                    centerX,
                    36,
                    0xD0D0D0
            );
        } else {
            drawCenteredString(
                    fontRendererObj,
                    "Size: Unavailable   Revision: "
                            + snapshot.getBaseStructureRevision(),
                    centerX,
                    36,
                    0xD0D0D0
            );
        }
        drawCenteredString(
                fontRendererObj,
                "Orientation: " + displayEnum(snapshot.getOrientation())
                        + "   Opening: "
                        + displayEnum(snapshot.getOpeningDirection()),
                centerX,
                48,
                0xD0D0D0
        );
        drawCenteredString(
                fontRendererObj,
                "Left hinge: " + formatHinge(snapshot.getLeftHinge())
                        + "   Right hinge: "
                        + formatHinge(snapshot.getRightHinge()),
                centerX,
                60,
                0xD0D0D0
        );
        drawCenteredString(
                fontRendererObj,
                "Captured state: " + snapshot.getGateState().name()
                        + "   HP: " + snapshot.getCurrentHealth() + "/"
                        + snapshot.getMaxHealth(),
                centerX,
                72,
                0xAAAAAA
        );
    }

    private void drawStructureSchematic(FinalizedGateSnapshot snapshot) {
        int panelLeft = 20;
        int panelTop = 98;
        int panelRight = width - 20;
        int panelBottom = height - 42;
        int panelWidth = Math.max(1, panelRight - panelLeft);
        int panelHeight = Math.max(1, panelBottom - panelTop);
        int cellsWide = structureViewModel.getWidth();
        int cellsHigh = structureViewModel.getHeight();
        int cellSize = Math.max(
                1,
                Math.min(panelWidth / cellsWide, panelHeight / cellsHigh)
        );
        int schematicWidth = cellsWide * cellSize;
        int schematicHeight = cellsHigh * cellSize;
        int originX = panelLeft + (panelWidth - schematicWidth) / 2;
        int originY = panelTop + (panelHeight - schematicHeight) / 2;
        GateHinge leftHinge = snapshot.getLeftHinge();
        GateHinge rightHinge = snapshot.getRightHinge();

        drawRect(
                originX - 1,
                originY - 1,
                originX + schematicWidth + 1,
                originY + schematicHeight + 1,
                0xFF555555
        );
        drawRect(
                originX,
                originY,
                originX + schematicWidth,
                originY + schematicHeight,
                0x66000000
        );
        for (FinalizedGateSnapshot.PartEntry part
                : structureViewModel.getPartsForLayer(selectedStructureLayer)) {
            int x = originX + structureViewModel.getScreenColumn(part) * cellSize;
            int y = originY + structureViewModel.getScreenRow(part) * cellSize;
            drawRect(x, y, x + cellSize, y + cellSize, getLeafColor(part.getLeaf()));
            if (structureViewModel.isAtHinge(part, leftHinge)) {
                drawHingeBorder(x, y, cellSize, 0xFF66CCFF);
            }
            if (structureViewModel.isAtHinge(part, rightHinge)) {
                drawHingeBorder(x + 1, y + 1, Math.max(1, cellSize - 2), 0xFFFF99CC);
            }
        }
    }

    private static int getLeafColor(GateLeaf leaf) {
        if (leaf == GateLeaf.LEFT) {
            return 0xFF3F78C5;
        }
        if (leaf == GateLeaf.RIGHT) {
            return 0xFFC45454;
        }
        return 0xFFB88A30;
    }

    private void drawHingeBorder(int x, int y, int size, int color) {
        if (size <= 0) {
            return;
        }
        drawRect(x, y, x + size, y + 1, color);
        drawRect(x, y + size - 1, x + size, y + size, color);
        drawRect(x, y, x + 1, y + size, color);
        drawRect(x + size - 1, y, x + size, y + size, color);
    }

    private FinalizedGateSnapshot getMatchingStructureSnapshot() {
        FinalizedGateSnapshot snapshot =
                GateFinalizedInspectionClientContext.getSnapshot();
        if (!GateManagementClientContext.isActive() || snapshot == null
                || snapshot.getDimensionId()
                != GateManagementClientContext.getDimensionId()
                || snapshot.getControllerX()
                != GateManagementClientContext.getControllerX()
                || snapshot.getControllerY()
                != GateManagementClientContext.getControllerY()
                || snapshot.getControllerZ()
                != GateManagementClientContext.getControllerZ()) {
            return null;
        }
        return snapshot;
    }

    private void ensureStructureViewModel(FinalizedGateSnapshot snapshot) {
        if (structureSnapshot != snapshot || structureViewModel == null) {
            structureSnapshot = snapshot;
            structureViewModel = new GateStructureInspectionViewModel(snapshot);
            selectedStructureLayer = 0;
        }
    }

    private static String displayName(String value) {
        return value == null || value.isEmpty() ? "Unnamed Gate" : value;
    }

    private static String displayEnum(Enum<?> value) {
        return value == null ? "Unknown" : value.name();
    }

    private static String formatHinge(GateHinge hinge) {
        return hinge == null
                ? "None"
                : "(" + hinge.getRelativeX() + ","
                        + hinge.getRelativeZ() + ")"
                        + (hinge.getSide() == null
                                ? ""
                                : " " + hinge.getSide().name());
    }

    private GuiTextField createField(
            int x,
            int y,
            int fieldWidth,
            int maxLength,
            String value
    ) {
        GuiTextField field =
                new GuiTextField(
                        fontRendererObj,
                        x,
                        y,
                        fieldWidth,
                        20
                );

        field.setMaxStringLength(
                maxLength
        );

        field.setText(
                value
        );

        return field;
    }

    private TileEntitySiegeGate getGate() {
        if (!GateManagementClientContext.isActive()
                || mc.theWorld == null
                || mc.theWorld.provider.dimensionId
                != GateManagementClientContext.getDimensionId()) {
            return null;
        }
        TileEntity tileEntity = mc.theWorld.getTileEntity(
                GateManagementClientContext.getControllerX(),
                GateManagementClientContext.getControllerY(),
                GateManagementClientContext.getControllerZ()
        );
        return tileEntity instanceof TileEntitySiegeGate
                ? (TileEntitySiegeGate)tileEntity
                : null;
    }

    private void beginWorldSelection(GateEditSelectionMode mode) {
        if (!GateEditClientContext.isActive() || mode == null) {
            return;
        }
        GateEditClientContext.setSelectionMode(mode);
        leavingForWorldSelection = true;
        mc.displayGuiScreen(null);
    }

    private String getEditDirectionButtonText() {
        return "Opening Direction: " + (GateEditClientContext.getDirection() == null ? "FORWARD" : GateEditClientContext.getDirection().name());
    }

    private void updateEditDirectionButton() {
        for (Object object : buttonList) {
            if (object instanceof GuiButton && ((GuiButton)object).id == DIRECTION_BUTTON) {
                ((GuiButton)object).displayString = getEditDirectionButtonText();
            }
        }
    }

    private String getEditBorderButtonText() {
        return "Border: "
                + (GateEditClientContext.isBorderTextureEnabled()
                ? "ON"
                : "OFF");
    }

    private void updateEditBorderButton() {
        for (Object object : buttonList) {
            if (object instanceof GuiButton
                    && ((GuiButton)object).id == BORDER_TEXTURE_BUTTON) {
                ((GuiButton)object).displayString =
                        getEditBorderButtonText();
            }
        }
    }

    private void sendAction(int action, int value, String text) {
        if (!GateManagementClientContext.isActive()) {
            return;
        }
        Main.network.sendToServer(new GateManagementActionPacket(
                action,
                GateManagementClientContext.getDimensionId(),
                GateManagementClientContext.getControllerX(),
                GateManagementClientContext.getControllerY(),
                GateManagementClientContext.getControllerZ(),
                value,
                text
        ));
    }

    private void commitNameField() {
        if (nameField == null
                || !GateManagementClientContext
                .canManage()) {
            return;
        }

        TileEntitySiegeGate gate =
                getGate();

        String value =
                nameField.getText();

        if (gate != null
                && value.equals(
                gate.getGateName()
        )) {
            return;
        }

        sendAction(
                GateManagementActionPacket.SET_NAME,
                0,
                value
        );
    }

    private void commitMaxHealthField() {
        if (maxHealthField == null
                || mc.thePlayer == null
                || !mc.thePlayer
                .capabilities
                .isCreativeMode) {

            return;
        }

        int value =
                parseInteger(
                        maxHealthField.getText()
                );

        TileEntitySiegeGate gate =
                getGate();

        if (gate != null
                && value
                == gate.getMaxHealth()) {

            return;
        }

        sendAction(
                GateManagementActionPacket.SET_MAX_HEALTH,
                value,
                ""
        );
    }

    private void cycleFaction(
            int direction
    ) {
        List<String> selectable =
                getSelectableFactionNames();

        if (selectable.isEmpty()) {
            selectedFactionName =
                    "";

            return;
        }

        int currentIndex =
                selectable.indexOf(
                        selectedFactionName
                );

        if (currentIndex < 0) {
            /*
             * A previously-selected faction may no longer meet the +100 rule.
             *
             * Keep showing it until an arrow is clicked, then move into the
             * currently-legal list.
             */
            selectedFactionName =
                    direction >= 0
                            ? selectable.get(0)
                            : selectable.get(
                            selectable.size() - 1
                    );

            return;
        }

        int next =
                currentIndex
                        + (direction >= 0
                        ? 1
                        : -1);

        if (next < 0) {
            next =
                    selectable.size() - 1;

        } else if (next
                >= selectable.size()) {

            next =
                    0;
        }

        selectedFactionName =
                selectable.get(
                        next
                );
    }

    private List<String> getSelectableFactionNames() {
        List<String> result =
                new ArrayList<String>();

        /*
         * Neutral / Unassigned is always available.
         */
        result.add(
                ""
        );

        if (mc.thePlayer == null) {
            return result;
        }

        for (LOTRFaction faction
                : LOTRFaction
                .getPlayableAlignmentFactions()) {

            if (faction == null) {
                continue;
            }

            /*
             * No Creative/admin bypass.
             *
             * Factions below +100 simply do not exist in the selector.
             */
            if (LOTRLevelData
                    .getData(
                            mc.thePlayer
                    )
                    .getAlignment(
                            faction
                    )
                    >= 100.0F) {

                result.add(
                        faction.codeName()
                );
            }
        }

        return result;
    }

    private String getFactionDisplayText() {
        if (selectedFactionName == null
                || selectedFactionName
                .isEmpty()) {

            return "Neutral";
        }

        LOTRFaction faction =
                LOTRFaction.forName(
                        selectedFactionName
                );

        return faction == null
                ? "Neutral"
                : faction.factionName();
    }

    private void drawFactionBox(
            int centerX,
            int y
    ) {
        int boxWidth =
                132;

        int left =
                centerX
                        - boxWidth / 2;

        drawRect(
                left,
                y,
                left + boxWidth,
                y + 20,
                0xFFA0A0A0
        );

        drawRect(
                left + 1,
                y + 1,
                left + boxWidth - 1,
                y + 19,
                0xFF000000
        );

        drawCenteredString(
                fontRendererObj,
                getFactionDisplayText(),
                centerX,
                y + 6,
                0xFFFFFF
        );
    }

    private void drawRepairSummary(
            TileEntitySiegeGate gate,
            int centerX,
            int y
    ) {
        if (gate.getMissingHealth()
                <= 0) {

            drawCenteredString(
                    fontRendererObj,
                    "Fully repaired",
                    centerX,
                    y,
                    0xAAAAAA
            );

            return;
        }

        if (gate.isRepairActive()) {
            int purchased =
                    Math.max(
                            1,
                            gate.getRepairPurchasedHealth()
                    );

            int percent =
                    Math.max(
                            0,
                            Math.min(
                                    100,
                                    gate.getRepairAppliedHealth()
                                            * 100
                                            / purchased
                            )
                    );

            /*
             * Keep this deliberately tiny and clean.
             *
             * The button already says Repairing / Paused, while the main
             * gate health display represents actual gate HP.
             */
            drawCenteredString(
                    fontRendererObj,
                    percent + "%",
                    centerX,
                    y,
                    gate.isRepairPaused()
                            ? 0xFFCC55
                            : 0x88FF88
            );

            return;
        }

        String missing =
                "Missing "
                        + gate.getMissingHealth()
                        + " HP";

        String cost =
                Integer.toString(
                        gate.getRepairCostToFull()
                );

        int gap =
                10;

        int missingWidth =
                fontRendererObj
                        .getStringWidth(
                                missing
                        );

        int costWidth =
                fontRendererObj
                        .getStringWidth(
                                cost
                        );

        int totalWidth =
                missingWidth
                        + gap
                        + 16
                        + 3
                        + costWidth;

        int x =
                centerX
                        - totalWidth / 2;

        drawString(
                fontRendererObj,
                missing,
                x,
                y,
                0xDDDDDD
        );

        int coinX =
                x
                        + missingWidth
                        + gap;

        mc.getTextureManager()
                .bindTexture(
                        COIN_TEXTURE
                );

        GL11.glColor4f(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        func_152125_a(
                coinX,
                y - 4,
                0,
                0,
                16,
                16,
                16,
                16,
                16.0F,
                16.0F
        );

        drawString(
                fontRendererObj,
                cost,
                coinX + 19,
                y,
                0xFFFFFF
        );
    }

    private void drawGateControlModeTooltip(
            int mouseX,
            int mouseY,
            TileEntitySiegeGate gate
    ) {
        GuiButton button =
                getButton(
                        GATE_CONTROL_MODE_BUTTON
                );

        if (button == null
                || gate == null
                || mouseX < button.xPosition
                || mouseX >= button.xPosition + button.width
                || mouseY < button.yPosition
                || mouseY >= button.yPosition + button.height) {

            return;
        }

        List<String> tooltip =
                new ArrayList<String>();

        if (gate.getGateControlMode()
                == GateControlMode.AUTOMATIC) {
            tooltip.add(
                    "Automatic: opens normally and closes after the normal delay."
            );
        } else if (gate.getGateControlMode()
                == GateControlMode.LOCKED_CLOSED) {
            tooltip.add(
                    "Locked Closed: stays shut until this mode is changed."
            );
        } else {
            tooltip.add(
                    "Held Open: opens normally and remains open indefinitely."
            );
        }

        tooltip.add(
                "A breached gate ignores this mode until it is repaired."
        );

        drawHoveringText(
                tooltip,
                mouseX,
                mouseY,
                fontRendererObj
        );
    }

    private void updateRepairButton(
            TileEntitySiegeGate gate
    ) {
        GuiButton button =
                getButton(
                        BEGIN_REPAIR_BUTTON
                );

        if (!(button instanceof GuiGateRepairButton)) {
            return;
        }

        GuiGateRepairButton repairButton =
                (GuiGateRepairButton)button;

        if (gate.getMissingHealth()
                <= 0) {

            repairButton.displayString =
                    "Fully Repaired";

            repairButton.setShowCoin(false);

            repairButton.enabled =
                    false;

            return;
        }

        if (gate.isRepairActive()) {
            int purchased =
                    Math.max(
                            1,
                            gate.getRepairPurchasedHealth()
                    );

            int percent =
                    Math.max(
                            0,
                            Math.min(
                                    100,
                                    gate.getRepairAppliedHealth()
                                            * 100
                                            / purchased
                            )
                    );

            repairButton.displayString =
                    (gate.isRepairPaused()
                            ? "Repair Paused - "
                            : "Repairing - ")
                            + percent
                            + "%";

            repairButton.setShowCoin(false);

            repairButton.enabled =
                    false;

            return;
        }

        repairButton.displayString =
                "Repair Gate - "
                        + gate.getRepairCostToFull();

        repairButton.setShowCoin(true);

        repairButton.enabled =
                true;
    }

    private GuiButton getButton(
            int id
    ) {
        for (Object object
                : buttonList) {

            if (object instanceof GuiButton
                    && ((GuiButton)object).id
                    == id) {

                return (GuiButton)object;
            }
        }

        return null;
    }

    private void setButtonEnabled(int buttonId, boolean enabled) {
        for (Object object : buttonList) {
            if (object instanceof GuiButton
                    && ((GuiButton)object).id == buttonId) {
                ((GuiButton)object).enabled = enabled;
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

    private static String formatTicks(long ticks) {
        long totalSeconds = (ticks + 19L) / 20L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes + ":" + (seconds < 10L ? "0" : "") + seconds;
    }

    private static class GuiGateRepairButton
            extends GuiButton {

        private boolean showCoin;

        private GuiGateRepairButton(
                int id,
                int x,
                int y,
                int width,
                int height,
                String text
        ) {
            super(
                    id,
                    x,
                    y,
                    width,
                    height,
                    text
            );
        }

        private void setShowCoin(
                boolean showCoin
        ) {
            this.showCoin =
                    showCoin;
        }

        @Override
        public void drawButton(
                net.minecraft.client.Minecraft mc,
                int mouseX,
                int mouseY
        ) {
            if (!showCoin) {
                super.drawButton(
                        mc,
                        mouseX,
                        mouseY
                );

                return;
            }

            /*
             * Let vanilla draw the normal button background and hover state,
             * but suppress its normal centered text because we need to center
             * the text + coin icon together as one unit.
             */
            String text =
                    displayString;

            displayString =
                    "";

            super.drawButton(
                    mc,
                    mouseX,
                    mouseY
            );

            displayString =
                    text;

            int iconSize =
                    12;

            int gap =
                    3;

            int textWidth =
                    mc.fontRenderer
                            .getStringWidth(
                                    text
                            );

            int totalWidth =
                    textWidth
                            + gap
                            + iconSize;

            int startX =
                    xPosition
                            + (width - totalWidth) / 2;

            int textY =
                    yPosition
                            + (height - 8) / 2;

            boolean hovered =
                    mouseX >= xPosition
                            && mouseY >= yPosition
                            && mouseX < xPosition + width
                            && mouseY < yPosition + height;

            int textColor =
                    hovered
                            ? 0xFFFFA0
                            : 0xE0E0E0;

            mc.fontRenderer
                    .drawStringWithShadow(
                            text,
                            startX,
                            textY,
                            textColor
                    );

            int coinX =
                    startX
                            + textWidth
                            + gap;

            int coinY =
                    yPosition
                            + (height - iconSize) / 2;

            mc.getTextureManager()
                    .bindTexture(
                            COIN_TEXTURE
                    );

            GL11.glColor4f(
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F
            );

            func_152125_a(
                    coinX,
                    coinY,
                    0,
                    0,
                    16,
                    16,
                    iconSize,
                    iconSize,
                    16.0F,
                    16.0F
            );
        }
    }
}
