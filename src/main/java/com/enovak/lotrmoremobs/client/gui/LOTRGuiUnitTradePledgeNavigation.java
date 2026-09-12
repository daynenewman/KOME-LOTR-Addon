package com.enovak.lotrmoremobs.client.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lotr.client.LOTRClientProxy;
import lotr.client.gui.LOTRGuiFactions;
import lotr.client.gui.LOTRGuiUnitTrade;
import lotr.common.LOTRDimension;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRMod;
import lotr.common.LOTRPlayerData;
import lotr.common.entity.npc.LOTRUnitTradeEntries;
import lotr.common.entity.npc.LOTRUnitTradeEntry;
import lotr.common.entity.npc.LOTRUnitTradeEntry.PledgeType;
import lotr.common.entity.npc.LOTRUnitTradeable;
import lotr.common.fac.LOTRAlignmentValues;
import lotr.common.fac.LOTRFaction;
import lotr.common.inventory.LOTRContainerUnitTrade;
import lotr.common.inventory.LOTRSlotAlignmentReward;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Native LOTR unit-trade GUI with one real, fixed-label pledge button. The
 * inherited preview/background/button lifecycle is unchanged.
 */
public final class LOTRGuiUnitTradePledgeNavigation
        extends LOTRGuiUnitTrade {
    private static final ResourceLocation UNIT_TRADE_TEXTURE =
            new ResourceLocation("lotr:gui/npc/unit_trade.png");
    private static final int PLEDGE_NAVIGATION_BUTTON_ID = 0x4D554D;
    private static final int PLEDGE_X = 64;
    private static final int PLEDGE_TEXT_X = 83;
    private static final int PLEDGE_Y = 101;
    private static final int PLEDGE_HEIGHT = 16;
    private static final int EXTRA_INFO_X = 49;
    private static final int EXTRA_INFO_Y = 106;
    private static final int EXTRA_INFO_WIDTH = 9;
    private static final int EXTRA_INFO_HEIGHT = 7;
    /*
     * Vanilla's hovering-text background uses z=300. Adding 400 keeps the
     * whole tooltip above NEI's z=200 overlay while remaining safely inside
     * Minecraft's GUI projection near plane.
     */
    private static final float LATE_TOOLTIP_Z_OFFSET = 400.0F;
    private static final int TOOLTIP_GL_ATTRIB_MASK =
            GL11.GL_ENABLE_BIT
                    | GL11.GL_COLOR_BUFFER_BIT
                    | GL11.GL_DEPTH_BUFFER_BIT
                    | GL11.GL_LIGHTING_BIT
                    | GL11.GL_TEXTURE_BIT
                    | GL11.GL_TRANSFORM_BIT
                    | GL11.GL_CURRENT_BIT;

    private final LOTRUnitTradeable unitTrader;
    private final LOTRUnitTradeEntries trades;
    private final LOTRFaction traderFaction;
    private int currentTradeEntryIndex;
    private PledgeNavigationButton pledgeButton;

    public LOTRGuiUnitTradePledgeNavigation(
            EntityPlayer player,
            LOTRUnitTradeable trader,
            World world
    ) {
        super(player, trader, world);
        this.unitTrader = trader;
        this.trades = trader.getUnits();
        this.traderFaction = trader.getFaction();
    }

    @Override
    public void initGui() {
        super.initGui();
        this.pledgeButton = new PledgeNavigationButton(
                PLEDGE_NAVIGATION_BUTTON_ID,
                this.guiLeft + PLEDGE_X,
                this.guiTop + PLEDGE_Y
        );
        this.buttonList.add(this.pledgeButton);
        this.updatePledgeButton();
    }

    @Override
    public void drawScreen(
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        this.updatePledgeButton();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == this.pledgeButton
                && button.enabled
                && button.visible) {
            this.openFactionPage(this.traderFaction);
            return;
        }

        if (button.enabled && button.id == 0
                && this.currentTradeEntryIndex > 0) {
            --this.currentTradeEntryIndex;
        } else if (button.enabled && button.id == 2
                && this.currentTradeEntryIndex
                < this.trades.tradeEntries.length - 1) {
            ++this.currentTradeEntryIndex;
        }
        super.actionPerformed(button);
    }

    /**
     * Matches LOTR's native foreground except that every pledge requirement
     * row is owned by the real GuiButton drawn in the normal button pass.
     * Cost/alignment, reward-slot, and extra-info behavior remain native.
     */
    @Override
    protected void drawGuiContainerForegroundLayer(
            int mouseX,
            int mouseY
    ) {
        LOTRUnitTradeEntry trade = this.currentTrade();
        this.drawCenteredString(
                this.unitTrader.getNPCName(),
                110,
                11,
                4210752
        );
        this.fontRendererObj.drawString(
                StatCollector.translateToLocal(
                        "container.inventory"
                ),
                30,
                162,
                4210752
        );
        this.drawCenteredString(
                trade.getUnitTradeName(),
                138,
                50,
                4210752
        );

        int requirementX = 64;
        int requirementTextX = requirementX + 19;
        int requirementY = 65;
        int requirementTextOffsetY = 4;
        int requirementGap = 18;

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_CULL_FACE);
        RenderItem.getInstance().renderItemAndEffectIntoGUI(
                this.fontRendererObj,
                this.mc.getTextureManager(),
                new ItemStack(LOTRMod.silverCoin),
                requirementX,
                requirementY
        );
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        int cost = trade.getCost(
                this.mc.thePlayer,
                this.unitTrader
        );
        this.fontRendererObj.drawString(
                String.valueOf(cost),
                requirementTextX,
                requirementY + requirementTextOffsetY,
                4210752
        );

        int nextRequirementY =
                requirementY + requirementGap;
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(
                LOTRClientProxy.alignmentTexture
        );
        this.drawTexturedModalRect(
                requirementX,
                nextRequirementY,
                0,
                36,
                16,
                16
        );
        String alignment = LOTRAlignmentValues
                .formatAlignForDisplay(
                        trade.alignmentRequired
                );
        this.fontRendererObj.drawString(
                alignment,
                requirementTextX,
                nextRequirementY + requirementTextOffsetY,
                4210752
        );

        if (trade.getPledgeType() != PledgeType.NONE) {
            /*
             * The real navigation button owns this row for every pledge type
             * (FACTION, ANY_ELF, ANY_DWARF, etc.). Keeping the row reserved
             * preserves the native spacing without drawing the old "More..."
             * implementation underneath it.
             */
            nextRequirementY += requirementGap;
        }

        LOTRContainerUnitTrade container =
                (LOTRContainerUnitTrade)this.inventorySlots;
        if (container.alignmentRewardSlots > 0) {
            Slot rewardSlot = this.inventorySlots.getSlot(0);
            if (rewardSlot.getHasStack()) {
                GL11.glEnable(GL11.GL_LIGHTING);
                GL11.glEnable(GL11.GL_CULL_FACE);
                RenderItem.getInstance()
                        .renderItemAndEffectIntoGUI(
                                this.fontRendererObj,
                                this.mc.getTextureManager(),
                                new ItemStack(LOTRMod.silverCoin),
                                160,
                                100
                        );
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glColor4f(
                        1.0F,
                        1.0F,
                        1.0F,
                        1.0F
                );
                this.fontRendererObj.drawString(
                        String.valueOf(
                                LOTRSlotAlignmentReward
                                        .REWARD_COST
                        ),
                        179,
                        104,
                        4210752
                );
            } else if (LOTRLevelData.getData(
                    this.mc.thePlayer
            ).getAlignment(this.traderFaction) < 1500.0F
                    && this.func_146978_c(
                    rewardSlot.xDisplayPosition,
                    rewardSlot.yDisplayPosition,
                    16,
                    16,
                    mouseX,
                    mouseY
            )) {
                this.drawCreativeTabHoveringText(
                        StatCollector.translateToLocalFormatted(
                                "container.lotr.unitTrade"
                                        + ".requiresAlignment",
                                new Object[] {
                                        Float.valueOf(1500.0F)
                                }
                        ),
                        mouseX - this.guiLeft,
                        mouseY - this.guiTop
                );
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glColor4f(
                        1.0F,
                        1.0F,
                        1.0F,
                        1.0F
                );
            }
        }

        if (trade.hasExtraInfo()) {
            this.drawNativeExtraInfo(
                    trade,
                    mouseX,
                    mouseY
            );
        }
    }

    private void drawNativeNonFactionPledge(
            LOTRUnitTradeEntry trade,
            int requirementX,
            int requirementTextX,
            int requirementY,
            int requirementTextOffsetY,
            int mouseX,
            int mouseY
    ) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(
                LOTRClientProxy.alignmentTexture
        );
        this.drawTexturedModalRect(
                requirementX,
                requirementY,
                0,
                212,
                16,
                16
        );
        this.fontRendererObj.drawString(
                StatCollector.translateToLocal(
                        "container.lotr.unitTrade.pledge"
                ),
                requirementTextX,
                requirementY + requirementTextOffsetY,
                4210752
        );

        int relativeMouseX =
                mouseX - this.guiLeft - requirementX;
        int relativeMouseY =
                mouseY - this.guiTop - requirementY;
        if (relativeMouseX >= 0
                && relativeMouseX < 16
                && relativeMouseY >= 0
                && relativeMouseY < 16) {
            this.drawCreativeTabHoveringText(
                    trade.getPledgeType()
                            .getCommandReqText(
                                    this.traderFaction
                            ),
                    mouseX - this.guiLeft,
                    mouseY - this.guiTop
            );
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glColor4f(
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F
            );
        }
    }

    private void drawNativeExtraInfo(
            LOTRUnitTradeEntry trade,
            int mouseX,
            int mouseY
    ) {
        boolean hovered = this.isExtraInfoHovered(
                mouseX,
                mouseY
        );
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(
                UNIT_TRADE_TEXTURE
        );
        this.drawTexturedModalRect(
                EXTRA_INFO_X,
                EXTRA_INFO_Y,
                220,
                38 + (hovered ? 1 : 0)
                        * EXTRA_INFO_HEIGHT,
                EXTRA_INFO_WIDTH,
                EXTRA_INFO_HEIGHT
        );
    }

    /**
     * Draws only the unit entry's large requirement tooltip. The registered
     * LOWEST-priority Post handler calls this after NEI's container overlay.
     */
    public void drawLateRequirementTooltip(
            int mouseX,
            int mouseY
    ) {
        LOTRUnitTradeEntry trade = this.currentTrade();
        String tooltipText = null;

        if (this.pledgeButton != null
                && this.pledgeButton.visible
                && this.pledgeButton.isMouseOver(
                mouseX,
                mouseY
        )
                && trade.getPledgeType() != PledgeType.NONE
                && !this.isPledgedToTraderFaction()) {

            tooltipText =
                    "Pledge to " + this.traderFaction.factionName();
        } else if (trade.hasExtraInfo()
                && this.isExtraInfoHovered(mouseX, mouseY)) {

            tooltipText = trade.getFormattedExtraInfo();
        }

        if (tooltipText == null) {
            return;
        }

        List description = this.fontRendererObj
                .listFormattedStringToWidth(
                        tooltipText,
                        200
                );
        RenderItem renderItem = RenderItem.getInstance();
        float previousGuiZ = this.zLevel;
        float previousItemZ = renderItem.zLevel;
        int previousMatrixMode =
                GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousActiveTexture =
                GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean attributesPushed = false;
        boolean matrixPushed = false;

        try {
            GL11.glPushAttrib(TOOLTIP_GL_ATTRIB_MASK);
            attributesPushed = true;
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            matrixPushed = true;

            GL11.glTranslatef(
                    0.0F,
                    0.0F,
                    LATE_TOOLTIP_Z_OFFSET
            );
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            RenderHelper.disableStandardItemLighting();
            this.zLevel = LATE_TOOLTIP_Z_OFFSET;
            renderItem.zLevel = LATE_TOOLTIP_Z_OFFSET;
            this.func_146283_a(
                    description,
                    mouseX,
                    mouseY
            );
        } finally {
            this.zLevel = previousGuiZ;
            renderItem.zLevel = previousItemZ;
            if (matrixPushed) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            if (attributesPushed) {
                GL11.glPopAttrib();
            }
            OpenGlHelper.setActiveTexture(
                    previousActiveTexture
            );
            GL11.glMatrixMode(previousMatrixMode);
        }
    }

    private boolean isExtraInfoHovered(
            int mouseX,
            int mouseY
    ) {
        return mouseX >= this.guiLeft + EXTRA_INFO_X
                && mouseX < this.guiLeft
                + EXTRA_INFO_X
                + EXTRA_INFO_WIDTH
                && mouseY >= this.guiTop
                + EXTRA_INFO_Y
                && mouseY < this.guiTop
                + EXTRA_INFO_Y
                + EXTRA_INFO_HEIGHT;
    }

    private void updatePledgeButton() {
        if (this.pledgeButton == null) {
            return;
        }

        LOTRUnitTradeEntry trade = this.currentTrade();
        boolean pledgeRequired =
                trade.getPledgeType() != PledgeType.NONE;
        this.pledgeButton.visible = pledgeRequired;
        this.pledgeButton.enabled = pledgeRequired;
        if (!pledgeRequired) {
            return;
        }

        this.pledgeButton.xPosition =
                this.guiLeft + PLEDGE_X;
        this.pledgeButton.yPosition =
                this.guiTop + PLEDGE_Y;
        this.pledgeButton.displayString =
                this.getPledgeLabel();
        this.pledgeButton.width = Math.min(
                this.xSize - PLEDGE_X,
                19 + this.fontRendererObj.getStringWidth(
                        this.pledgeButton.displayString
                ) + 2
        );
    }

    private LOTRUnitTradeEntry currentTrade() {
        return this.trades.tradeEntries[
                this.currentTradeEntryIndex
                ];
    }

    private String getPledgeLabel() {
        LOTRUnitTradeEntry trade = this.currentTrade();
        if (trade.getPledgeType() != PledgeType.NONE) {
            return this.isPledgedToTraderFaction()
                    ? StatCollector.translateToLocal(
                    "gui.lotrmoremobs.unitTrade.pledged"
            )
                    : "Pledge";
        }

        return "";
    }

    private boolean isPledgedToTraderFaction() {
        LOTRPlayerData playerData = LOTRLevelData.getData(
                this.mc.thePlayer
        );
        return playerData.isPledgedTo(this.traderFaction);
    }

    private void openFactionPage(LOTRFaction faction) {
        LOTRPlayerData playerData = LOTRLevelData.getData(
                this.mc.thePlayer
        );
        playerData.setViewingFaction(faction);
        playerData.setRegionLastViewedFaction(
                faction.factionRegion,
                faction
        );

        Map<LOTRDimension.DimensionRegion, LOTRFaction>
                viewedRegions =
                new HashMap<LOTRDimension.DimensionRegion, LOTRFaction>();
        viewedRegions.put(
                faction.factionRegion,
                faction
        );
        LOTRClientProxy.sendClientInfoPacket(
                faction,
                viewedRegions
        );
        this.mc.displayGuiScreen(
                new HiringReturnFactionGui(this, this.unitTrader)
        );
    }

    /** Faction screen variant used only by the pledge link in this trade GUI. */
    private static final class HiringReturnFactionGui extends LOTRGuiFactions {
        private final GuiScreen returnGui;
        private final LOTRUnitTradeable trader;

        private HiringReturnFactionGui(
                GuiScreen returnGui,
                LOTRUnitTradeable trader
        ) {
            this.returnGui = returnGui;
            this.trader = trader;
        }

        @Override
        protected void keyTyped(char typedChar, int keyCode) {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft != null
                    && minecraft.gameSettings != null
                    && minecraft.gameSettings.keyBindInventory != null
                    && keyCode == minecraft.gameSettings.keyBindInventory.getKeyCode()
                    && this.returnGui != null
                    && minecraft.thePlayer != null
                    && minecraft.theWorld != null
                    && this.trader != null
                    && this.trader.canTradeWith(minecraft.thePlayer)) {
                minecraft.displayGuiScreen(this.returnGui);
                return;
            }
            super.keyTyped(typedChar, keyCode);
        }
    }

    private void drawCenteredString(
            String text,
            int centerX,
            int y,
            int color
    ) {
        this.fontRendererObj.drawString(
                text,
                centerX
                        - this.fontRendererObj
                        .getStringWidth(text) / 2,
                y,
                color
        );
    }

    private static final class PledgeNavigationButton
            extends GuiButton {
        private PledgeNavigationButton(int id, int x, int y) {
            super(id, x, y, 16, PLEDGE_HEIGHT, "");
        }

        @Override
        public void drawButton(
                Minecraft minecraft,
                int mouseX,
                int mouseY
        ) {
            if (!this.visible) {
                return;
            }

            this.field_146123_n = this.isMouseOver(
                    mouseX,
                    mouseY
            );
            minecraft.getTextureManager().bindTexture(
                    LOTRClientProxy.alignmentTexture
            );
            this.drawTexturedModalRect(
                    this.xPosition,
                    this.yPosition,
                    0,
                    212,
                    16,
                    16
            );
            minecraft.fontRenderer.drawString(
                    this.displayString,
                    this.xPosition + PLEDGE_TEXT_X
                            - PLEDGE_X,
                    this.yPosition + 4,
                    this.field_146123_n
                            ? 0x7F5A20
                            : 4210752
            );
            this.mouseDragged(
                    minecraft,
                    mouseX,
                    mouseY
            );
        }

        private boolean isMouseOver(int mouseX, int mouseY) {
            return mouseX >= this.xPosition
                    && mouseX < this.xPosition + this.width
                    && mouseY >= this.yPosition
                    && mouseY < this.yPosition + this.height;
        }
    }
}
