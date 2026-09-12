package com.enovak.lotrmoremobs.siege.client.gui;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.client.RamControlClientContext;
import com.enovak.lotrmoremobs.siege.network.RamControlActionPacket;
import com.enovak.lotrmoremobs.siege.ram.EntityBattleRam;
import com.enovak.lotrmoremobs.siege.ram.RamControlManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;

public class GuiBattleRamControl extends GuiScreen {

    private static final int DISBAND_BUTTON = 0;
    private static final int CLOSE_BUTTON = 1;
    private static final int TARGET_MODE_BUTTON = 2;

    private static final int PANEL_WIDTH = 232;
    private static final int PANEL_HEIGHT = 154;

    private boolean confirmDisband;

    @Override
    public void initGui() {
        buttonList.clear();

        int centerX = width / 2;
        int centerY = height / 2;
        int buttonTop = centerY + 16;

        buttonList.add(new GuiButton(
                TARGET_MODE_BUTTON,
                centerX - 102,
                buttonTop,
                204,
                20,
                "Edit Gate Target Queue"
        ));

        buttonList.add(new GuiButton(
                DISBAND_BUTTON,
                centerX - 102,
                buttonTop + 26,
                100,
                20,
                "Disband Ram"
        ));

        buttonList.add(new GuiButton(
                CLOSE_BUTTON,
                centerX + 2,
                buttonTop + 26,
                100,
                20,
                "Close"
        ));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }

        if (button.id == CLOSE_BUTTON) {
            mc.displayGuiScreen(null);

        } else if (button.id == TARGET_MODE_BUTTON) {
            Main.network.sendToServer(new RamControlActionPacket(
                    RamControlManager.ENTER_TARGET_MODE,
                    RamControlClientContext.getDimensionId(),
                    RamControlClientContext.getEntityId()
            ));

            mc.displayGuiScreen(null);

        } else if (button.id == DISBAND_BUTTON) {
            if (!confirmDisband) {
                confirmDisband = true;
                button.displayString = "Confirm Disband";
                return;
            }

            Main.network.sendToServer(new RamControlActionPacket(
                    RamControlManager.DISBAND,
                    RamControlClientContext.getDimensionId(),
                    RamControlClientContext.getEntityId()
            ));

            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        drawDefaultBackground();

        EntityBattleRam ram =
                getRam();

        int centerX =
                width / 2;

        int centerY =
                height / 2;

        int left =
                centerX - PANEL_WIDTH / 2;

        int top =
                centerY - PANEL_HEIGHT / 2;

        drawRect(
                left,
                top,
                left + PANEL_WIDTH,
                top + PANEL_HEIGHT,
                0x88000000
        );

        drawCenteredString(
                fontRendererObj,
                "Battle Ram Control",
                centerX,
                top + 12,
                0xFFFFFF
        );

        if (ram == null) {
            drawCenteredString(
                    fontRendererObj,
                    "Battle Ram unavailable",
                    centerX,
                    top + 38,
                    0xFF7777
            );

        } else {
            String faction =
                    ram.getRamFaction() == null
                            ? "UNASSIGNED"
                            : ram.getRamFaction().factionName();

            drawCenteredString(
                    fontRendererObj,
                    "State: " + ram.getRamState().name(),
                    centerX,
                    top + 34,
                    0xD0D0D0
            );

            drawCenteredString(
                    fontRendererObj,
                    "Faction: " + faction,
                    centerX,
                    top + 48,
                    0xD0D0D0
            );

            drawCenteredString(
                    fontRendererObj,
                    "Living crew: "
                            + ram.getLivingCrewCount()
                            + " / "
                            + EntityBattleRam.CREW_SLOT_COUNT,
                    centerX,
                    top + 62,
                    0xD0D0D0
            );
        }

        super.drawScreen(
                mouseX,
                mouseY,
                partialTicks
        );
    }

    @Override
    public void onGuiClosed() {
        RamControlClientContext.clear();
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private EntityBattleRam getRam() {
        if (!RamControlClientContext.isActive()
                || mc.theWorld == null
                || mc.theWorld.provider.dimensionId
                != RamControlClientContext.getDimensionId()) {
            return null;
        }

        Entity entity =
                mc.theWorld.getEntityByID(
                        RamControlClientContext.getEntityId()
                );

        return entity instanceof EntityBattleRam
                ? (EntityBattleRam)entity
                : null;
    }
}
