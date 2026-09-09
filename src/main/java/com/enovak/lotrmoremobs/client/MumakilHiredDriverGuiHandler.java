package com.enovak.lotrmoremobs.client;

import com.enovak.lotrmoremobs.client.gui.MumakilAnchoredHiredDismissGui;
import com.enovak.lotrmoremobs.client.gui.MumakilAnchoredHiredInteractGui;
import com.enovak.lotrmoremobs.client.gui.MumakilAnchoredHiredWarriorGui;
import com.enovak.lotrmoremobs.client.gui.MumakilHiredDriverGuiContext;
import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.gui.LOTRGuiHiredDismiss;
import lotr.client.gui.LOTRGuiHiredInteract;
import lotr.client.gui.LOTRGuiHiredWarrior;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;

@SideOnly(Side.CLIENT)
public class MumakilHiredDriverGuiHandler {
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!MumakilHiredDriverGuiContext.hasContext()) {
            return;
        }

        if (event.gui == null) {
            MumakilHiredDriverGuiContext.clear();
            return;
        }

        GuiScreen gui = event.gui;
        if (this.isMumakilAnchoredGui(gui)) {
            MumakilHiredDriverGuiContext.markActive();
            return;
        }

        LOTREntityNPC driver = MumakilHiredDriverGuiContext.getGuiDriver(gui);
        if (!MumakilHiredDriverGuiContext.isForDriver(driver)) {
            MumakilHiredDriverGuiContext.clear();
            return;
        }

        LOTREntityMumakil mumakil = MumakilHiredDriverGuiContext.getMumakilForDriver(driver);
        if (mumakil == null) {
            MumakilHiredDriverGuiContext.clear();
            return;
        }

        GuiScreen replacement = this.createAnchoredReplacement(gui, driver, mumakil);
        if (replacement != null) {
            event.gui = replacement;
        }

        MumakilHiredDriverGuiContext.markActive();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MumakilHiredDriverGuiContext.tick();
        }
    }

    private GuiScreen createAnchoredReplacement(GuiScreen gui, LOTREntityNPC driver, LOTREntityMumakil mumakil) {
        if (gui.getClass() == LOTRGuiHiredInteract.class) {
            return new MumakilAnchoredHiredInteractGui(driver, mumakil);
        }

        if (gui.getClass() == LOTRGuiHiredDismiss.class) {
            return new MumakilAnchoredHiredDismissGui(driver, mumakil);
        }

        if (gui.getClass() == LOTRGuiHiredWarrior.class) {
            return new MumakilAnchoredHiredWarriorGui(driver, mumakil);
        }

        return null;
    }

    private boolean isMumakilAnchoredGui(GuiScreen gui) {
        return gui instanceof MumakilAnchoredHiredInteractGui
                || gui instanceof MumakilAnchoredHiredDismissGui
                || gui instanceof MumakilAnchoredHiredWarriorGui;
    }
}
