package kome.client.gui;

import kome.client.KOMEMinecraftClient;
import kome.common.network.KOMEPacketConquestClaim;
import kome.common.network.KOMEPacketConquestTransfer;
import kome.common.network.KOMEPacketHandler;

import lotr.client.gui.LOTRGuiMap;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiConquestCapture extends GuiScreen {
    private final String tileId;
    private final String ownerFaction;
    private final String pendingFromFaction;
    private final String pendingToFaction;
    private final List transferFactions = new ArrayList();
    private int transferIndex;
    private boolean transferMode;

    public KOMEGuiConquestCapture(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction) {
        this.tileId = tileId;
        this.ownerFaction = ownerFaction == null ? "" : ownerFaction;
        this.pendingFromFaction = pendingFromFaction == null ? "" : pendingFromFaction;
        this.pendingToFaction = pendingToFaction == null ? "" : pendingToFaction;
    }

    @Override
    public void initGui() {
        buildTransferFactions();
        int x = width / 2 - 90;
        int y = height / 2 + 28;
        buttonList.clear();
        if (transferMode) {
            buttonList.add(new GuiButton(2, x, y - 2, 28, 20, "<"));
            buttonList.add(new GuiButton(3, x + 152, y - 2, 28, 20, ">"));
            buttonList.add(new GuiButton(4, x, y + 24, 85, 20, "Transfer"));
            buttonList.add(new GuiButton(1, x + 95, y + 24, 85, 20, "Cancel"));
        } else {
            GuiButton claim = new GuiButton(0, x, y, 85, 20, "Claim");
            claim.enabled = getPledgeFaction() != null && !isOwnedByPledge();
            buttonList.add(claim);
            GuiButton transfer = new GuiButton(5, x + 95, y, 85, 20, "Sell/Trade");
            transfer.enabled = isOwnedByPledge() && !transferFactions.isEmpty();
            buttonList.add(transfer);
            if (isPendingToPledge()) {
                buttonList.add(new GuiButton(6, x, y + 24, 85, 20, "Accept"));
                buttonList.add(new GuiButton(1, x + 95, y + 24, 85, 20, "Cancel"));
            } else if (isOwnedByPledge() && hasPendingTransfer()) {
                buttonList.add(new GuiButton(7, x, y + 24, 85, 20, "Cancel Offer"));
                buttonList.add(new GuiButton(1, x + 95, y + 24, 85, 20, "Back"));
            } else {
                buttonList.add(new GuiButton(1, x + 47, y + 24, 85, 20, "Cancel"));
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            LOTRFaction pledge = getPledgeFaction();
            if (pledge != null) {
                KOMEPacketHandler.network.sendToServer(new KOMEPacketConquestClaim(tileId));
            }
            mc.displayGuiScreen(new LOTRGuiMap());
        } else if (button.id == 1) {
            mc.displayGuiScreen(new LOTRGuiMap());
        } else if (button.id == 2) {
            transferIndex = wrap(transferIndex - 1, transferFactions.size());
        } else if (button.id == 3) {
            transferIndex = wrap(transferIndex + 1, transferFactions.size());
        } else if (button.id == 4) {
            LOTRFaction target = getTransferFaction();
            if (target != null) {
                KOMEPacketHandler.network.sendToServer(new KOMEPacketConquestTransfer(tileId, target.codeName(), KOMEPacketConquestTransfer.OFFER));
            }
            mc.displayGuiScreen(new LOTRGuiMap());
        } else if (button.id == 5) {
            transferMode = true;
            initGui();
        } else if (button.id == 6) {
            KOMEPacketHandler.network.sendToServer(new KOMEPacketConquestTransfer(tileId, pendingToFaction, KOMEPacketConquestTransfer.ACCEPT));
            mc.displayGuiScreen(new LOTRGuiMap());
        } else if (button.id == 7) {
            KOMEPacketHandler.network.sendToServer(new KOMEPacketConquestTransfer(tileId, pendingToFaction, KOMEPacketConquestTransfer.CANCEL));
            mc.displayGuiScreen(new LOTRGuiMap());
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int x = width / 2 - 120;
        int y = height / 2 - 70;
        drawCenteredString(fontRendererObj, "Conquest Tile " + tileId, width / 2, y, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "Owning faction: " + valueOrUnclaimed(ownerFaction), width / 2, y + 28, 0xD8D8D8);
        LOTRFaction pledge = getPledgeFaction();
        drawCenteredString(fontRendererObj, "Your faction: " + (pledge == null ? "none" : pledge.factionName()), width / 2, y + 52, pledge == null ? 0xFF7777 : 0xAAFFAA);
        if (transferMode) {
            LOTRFaction target = getTransferFaction();
            drawCenteredString(fontRendererObj, "Offer transfer to:", width / 2, y + 76, 0xFFFFFF);
            drawCenteredString(fontRendererObj, target == null ? "No valid factions" : target.factionName(), width / 2, y + 91, 0xFFE8C46A);
        } else if (isPendingToPledge()) {
            drawCenteredString(fontRendererObj, "Offered by " + factionName(pendingFromFaction), width / 2, y + 76, 0xFFE8C46A);
            drawCenteredString(fontRendererObj, "Your king may accept this tile.", width / 2, y + 91, 0xFFFFFF);
        } else if (pledge == null) {
            drawCenteredString(fontRendererObj, "You must pledge to a faction before claiming.", width / 2, y + 76, 0xFF7777);
        } else if (hasPendingTransfer()) {
            drawCenteredString(fontRendererObj, "Pending offer to " + factionName(pendingToFaction), width / 2, y + 76, 0xFFE8C46A);
        } else if (isOwnedByPledge()) {
            drawCenteredString(fontRendererObj, "Only king-to-king tile trades are allowed.", width / 2, y + 76, 0xFFE8C46A);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private LOTRFaction getPledgeFaction() {
        return KOMEMinecraftClient.player() == null ? null : LOTRLevelData.getData(KOMEMinecraftClient.player()).getPledgeFaction();
    }

    private boolean isOwnedByPledge() {
        LOTRFaction pledge = getPledgeFaction();
        return pledge != null && ownerFaction != null && pledge.codeName().equals(ownerFaction);
    }

    private boolean isPendingToPledge() {
        LOTRFaction pledge = getPledgeFaction();
        return pledge != null && pendingToFaction.length() > 0 && pledge.codeName().equals(pendingToFaction);
    }

    private boolean hasPendingTransfer() {
        return pendingFromFaction.length() > 0 && pendingToFaction.length() > 0;
    }

    private void buildTransferFactions() {
        transferFactions.clear();
        LOTRFaction pledge = getPledgeFaction();
        for (LOTRFaction faction : LOTRFaction.values()) {
            if (faction != null && faction.isPlayableAlignmentFaction() && (pledge == null || !pledge.codeName().equals(faction.codeName()))) {
                transferFactions.add(faction);
            }
        }
        if (transferIndex >= transferFactions.size()) {
            transferIndex = 0;
        }
    }

    private LOTRFaction getTransferFaction() {
        return transferFactions.isEmpty() ? null : (LOTRFaction) transferFactions.get(transferIndex);
    }

    private static int wrap(int value, int size) {
        return size <= 0 ? 0 : (value % size + size) % size;
    }

    private static String factionName(String factionKey) {
        LOTRFaction faction = LOTRFaction.forName(factionKey);
        return faction == null ? factionKey : faction.factionName();
    }

    private static String valueOrUnclaimed(String value) {
        return value == null || value.trim().isEmpty() ? "unclaimed" : value;
    }

}
