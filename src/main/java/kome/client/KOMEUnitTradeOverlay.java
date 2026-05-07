package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEPopulationType;
import lotr.client.gui.LOTRGuiHireBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraftforge.client.event.GuiScreenEvent;

public class KOMEUnitTradeOverlay {
    private static final int HIRE_TYPE_BUTTON = 904201;

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof LOTRGuiHireBase)) {
            return;
        }
        int guiLeft = (event.gui.width - 220) / 2;
        int guiTop = (event.gui.height - 256) / 2;
        event.buttonList.add(new GuiButton(HIRE_TYPE_BUTTON, Math.max(4, guiLeft - 154), guiTop + 38, 150, 20, buttonLabel()));
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.gui instanceof LOTRGuiHireBase) || event.button.id != HIRE_TYPE_BUTTON) {
            return;
        }
        KOMEPopulationType nextType = KOMEClientData.INSTANCE.hireType == KOMEPopulationType.DEFENSIVE ? KOMEPopulationType.OFFENSIVE : KOMEPopulationType.DEFENSIVE;
        KOMEClientData.INSTANCE.hireType = nextType;
        event.button.displayString = buttonLabel();
        Minecraft.getMinecraft().thePlayer.sendChatMessage("/population hiretype " + nextType.key);
        event.setCanceled(true);
    }

    private String buttonLabel() {
        return KOMEClientData.INSTANCE.hireType == KOMEPopulationType.DEFENSIVE ? "Hiring defensive units" : "Hiring offensive units";
    }
}
