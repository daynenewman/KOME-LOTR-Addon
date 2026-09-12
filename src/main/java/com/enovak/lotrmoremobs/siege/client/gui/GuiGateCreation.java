package com.enovak.lotrmoremobs.siege.client.gui;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.client.ClientGateCreationState;
import com.enovak.lotrmoremobs.siege.creation.GateBlockPosition;
import com.enovak.lotrmoremobs.siege.network.GateCreationActionPacket;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

public class GuiGateCreation extends GuiScreen {

    private static final int ROW_WIDTH =
            292;

    private static final int SIDE_BUTTON_WIDTH =
            88;

    private static final int CENTER_BUTTON_WIDTH =
            104;

    private static final int BUTTON_GAP =
            6;

    private static final int HINGE_BUTTON_WIDTH =
            136;

    private static final int DIRECTION_BUTTON_WIDTH =
            194;

    private static final int BORDER_BUTTON_WIDTH =
            92;

    private static final int ACTION_BUTTON_WIDTH =
            118;

    private static final int ACTION_GAP =
            8;

    private static final int BUILD_TIP_WIDGET_ID =
            -2000;

    private GuiGateBuildTipWidget buildTipWidget;

    @Override
    public void initGui() {
        buttonList.clear();
        buildTipWidget = null;

        int centerX =
                width / 2;

        int top =
                height / 2 - 58;

        /*
         * Compact three-button leaf row.
         */
        int leafLeftX =
                centerX
                        - ROW_WIDTH / 2;

        int centerSplitX =
                leafLeftX
                        + SIDE_BUTTON_WIDTH
                        + BUTTON_GAP;

        int rightX =
                centerSplitX
                        + CENTER_BUTTON_WIDTH
                        + BUTTON_GAP;

        buttonList.add(
                new GuiButton(
                        GateCreationActionPacket.SELECT_LEFT,
                        leafLeftX,
                        top,
                        SIDE_BUTTON_WIDTH,
                        20,
                        "Select LEFT"
                )
        );

        buttonList.add(
                new GuiButton(
                        GateCreationActionPacket.SELECT_CENTER_SPLIT,
                        centerSplitX,
                        top,
                        CENTER_BUTTON_WIDTH,
                        20,
                        "Center Split"
                )
        );

        buttonList.add(
                new GuiButton(
                        GateCreationActionPacket.SELECT_RIGHT,
                        rightX,
                        top,
                        SIDE_BUTTON_WIDTH,
                        20,
                        "Select RIGHT"
                )
        );

        /*
         * Narrower hinge row.
         */
        int hingeRowWidth =
                HINGE_BUTTON_WIDTH
                        * 2
                        + BUTTON_GAP;

        int hingeLeftX =
                centerX
                        - hingeRowWidth / 2;

        buttonList.add(
                new GuiButton(
                        GateCreationActionPacket.SET_LEFT_HINGE,
                        hingeLeftX,
                        top + 30,
                        HINGE_BUTTON_WIDTH,
                        20,
                        "Set LEFT Hinge"
                )
        );

        buttonList.add(
                new GuiButton(
                        GateCreationActionPacket.SET_RIGHT_HINGE,
                        hingeLeftX
                                + HINGE_BUTTON_WIDTH
                                + BUTTON_GAP,
                        top + 30,
                        HINGE_BUTTON_WIDTH,
                        20,
                        "Set RIGHT Hinge"
                )
        );

        /*
         * Direction and optional border styling share one compact row.
         */
        buttonList.add(
                new GuiButton(
                        GateCreationActionPacket.TOGGLE_DIRECTION,
                        leafLeftX,
                        top + 60,
                        DIRECTION_BUTTON_WIDTH,
                        20,
                        getDirectionButtonText()
                )
        );

        buttonList.add(
                new GuiButton(
                        GateCreationActionPacket.TOGGLE_BORDER_TEXTURE,
                        leafLeftX
                                + DIRECTION_BUTTON_WIDTH
                                + BUTTON_GAP,
                        top + 60,
                        BORDER_BUTTON_WIDTH,
                        20,
                        getBorderButtonText()
                )
        );

        /*
         * Compact action row.
         */
        int actionRowWidth =
                ACTION_BUTTON_WIDTH
                        * 2
                        + ACTION_GAP;

        int actionLeftX =
                centerX
                        - actionRowWidth / 2;

        buildTipWidget =
                new GuiGateBuildTipWidget(
                        BUILD_TIP_WIDGET_ID,
                        actionLeftX
                                - ACTION_GAP
                                - 20,
                        top + 90
                );

        buttonList.add(
                buildTipWidget
        );

        buttonList.add(
                new GuiButton(
                        GateCreationActionPacket.FINALIZE,
                        actionLeftX,
                        top + 90,
                        ACTION_BUTTON_WIDTH,
                        20,
                        "Build"
                )
        );

        buttonList.add(
                new GuiButton(
                        GateCreationActionPacket.CANCEL,
                        actionLeftX
                                + ACTION_BUTTON_WIDTH
                                + ACTION_GAP,
                        top + 90,
                        ACTION_BUTTON_WIDTH,
                        20,
                        "Cancel"
                )
        );

        updateFinalizeButton();
    }

    @Override
    protected void actionPerformed(
            GuiButton button
    ) {
        if (!button.enabled
                || !ClientGateCreationState.isActive()) {
            return;
        }

        sendAction(
                button.id
        );

        if (button.id
                != GateCreationActionPacket.TOGGLE_DIRECTION
                && button.id
                != GateCreationActionPacket.TOGGLE_BORDER_TEXTURE) {

            mc.displayGuiScreen(
                    null
            );
        }
    }

    @Override
    protected void keyTyped(
            char typedChar,
            int keyCode
    ) {
        if (keyCode
                == Keyboard.KEY_ESCAPE) {

            sendAction(
                    GateCreationActionPacket.STOP_SELECTING
            );

            mc.displayGuiScreen(
                    null
            );

            return;
        }

        super.keyTyped(
                typedChar,
                keyCode
        );
    }

    @Override
    public void drawScreen(
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        drawDefaultBackground();

        updateDirectionButton();
        updateBorderButton();
        updateFinalizeButton();

        int centerX =
                width / 2;

        int top =
                height / 2 - 58;

        drawCenteredString(
                fontRendererObj,
                "Siege Gate Creation",
                centerX,
                top - 40,
                0xFFFFFF
        );

        /*
         * Once all four mandatory requirements are satisfied, the orbs
         * disappear and one tiny positive cue remains.
         */
        if (hasCreationRequirements()) {
            drawCenteredString(
                    fontRendererObj,
                    "Ready",
                    centerX,
                    top - 24,
                    0x77DD77
            );
        }

        super.drawScreen(
                mouseX,
                mouseY,
                partialTicks
        );

        drawCreationRequirementOrbs(
                top
        );

        drawBuildTipTooltip(
                mouseX,
                mouseY
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

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void drawCreationRequirementOrbs(
            int top
    ) {
        int centerX =
                width / 2;

        int leafLeftX =
                centerX
                        - ROW_WIDTH / 2;

        int centerSplitX =
                leafLeftX
                        + SIDE_BUTTON_WIDTH
                        + BUTTON_GAP;

        int rightX =
                centerSplitX
                        + CENTER_BUTTON_WIDTH
                        + BUTTON_GAP;

        int hingeRowWidth =
                HINGE_BUTTON_WIDTH
                        * 2
                        + BUTTON_GAP;

        int hingeLeftX =
                centerX
                        - hingeRowWidth / 2;

        boolean needsLeftLeaf =
                ClientGateCreationState
                        .getSelectionCount(
                                GateLeaf.LEFT
                        )
                        <= 0;

        boolean needsRightLeaf =
                ClientGateCreationState
                        .getSelectionCount(
                                GateLeaf.RIGHT
                        )
                        <= 0;

        boolean needsLeftHinge =
                ClientGateCreationState
                        .getLeftHingePosition()
                        == null;

        boolean needsRightHinge =
                ClientGateCreationState
                        .getRightHingePosition()
                        == null;

        drawRequirementOrb(
                leafLeftX - 10,
                top + 10,
                needsLeftLeaf
        );

        drawRequirementOrb(
                rightX
                        + SIDE_BUTTON_WIDTH
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

    private boolean hasCreationRequirements() {
        return ClientGateCreationState.isActive()
                && ClientGateCreationState
                .getSelectionCount(
                        GateLeaf.LEFT
                ) > 0
                && ClientGateCreationState
                .getSelectionCount(
                        GateLeaf.RIGHT
                ) > 0
                && ClientGateCreationState
                .getLeftHingePosition()
                != null
                && ClientGateCreationState
                .getRightHingePosition()
                != null;
    }

    private void updateFinalizeButton() {
        boolean enabled =
                hasCreationRequirements();

        for (Object object
                : buttonList) {

            if (!(object
                    instanceof GuiButton)) {
                continue;
            }

            GuiButton button =
                    (GuiButton)object;

            if (button.id
                    == GateCreationActionPacket.FINALIZE) {

                button.enabled =
                        enabled;

                return;
            }
        }
    }

    private void drawRequirementOrb(
            int centerX,
            int centerY,
            boolean show
    ) {
        if (!show) {
            return;
        }

        /*
         * Gentle approximately two-second breathing pulse.
         */
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

        /*
         * Tiny rounded-looking halo.
         */
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

        /*
         * Bright pale center.
         */
        drawRect(
                centerX - 1,
                centerY - 2,
                centerX + 2,
                centerY + 3,
                innerColor
        );
    }

    private void sendAction(
            int action
    ) {
        if (!ClientGateCreationState.isActive()) {
            return;
        }

        GateBlockPosition controller =
                ClientGateCreationState
                        .getControllerPosition();

        if (controller != null) {
            Main.network.sendToServer(
                    new GateCreationActionPacket(
                            action,
                            ClientGateCreationState
                                    .getDimensionId(),
                            controller
                    )
            );
        }
    }

    private void updateDirectionButton() {
        for (Object object
                : buttonList) {

            if (object
                    instanceof GuiButton
                    && ((GuiButton)object).id
                    == GateCreationActionPacket.TOGGLE_DIRECTION) {

                ((GuiButton)object).displayString =
                        getDirectionButtonText();
            }
        }
    }

    private String getDirectionButtonText() {
        return "Opening Direction: "
                + ClientGateCreationState
                .getOpeningDirection()
                .name();
    }

    private void updateBorderButton() {
        for (Object object
                : buttonList) {

            if (object
                    instanceof GuiButton
                    && ((GuiButton)object).id
                    == GateCreationActionPacket.TOGGLE_BORDER_TEXTURE) {

                ((GuiButton)object).displayString =
                        getBorderButtonText();
            }
        }
    }

    private String getBorderButtonText() {
        return "Border: "
                + (ClientGateCreationState
                .isBorderTextureEnabled()
                ? "ON"
                : "OFF");
    }
}