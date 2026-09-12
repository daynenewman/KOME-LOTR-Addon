package com.enovak.lotrmoremobs.client.gui;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.util.MumakilHiredDriverGuiAccess;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.gui.LOTRGuiHiredDismiss;
import lotr.common.entity.npc.LOTREntityNPC;

@SideOnly(Side.CLIENT)
public class MumakilAnchoredHiredDismissGui extends LOTRGuiHiredDismiss {
    private final LOTREntityNPC driver;
    private final LOTREntityMumakil mumakil;

    public MumakilAnchoredHiredDismissGui(LOTREntityNPC driver, LOTREntityMumakil mumakil) {
        super(driver);
        this.driver = driver;
        this.mumakil = mumakil;
    }

    @Override
    public void updateScreen() {
        if (!MumakilHiredDriverGuiContext.canAnchor(
                this.driver,
                this.mumakil,
                MumakilHiredDriverGuiAccess.HIRED_INTERACT_DISTANCE_SQ)) {
            MumakilHiredDriverGuiContext.closeAndClear();
            return;
        }

        MumakilHiredDriverGuiContext.SavedDriverPosition saved =
                MumakilHiredDriverGuiContext.pushDriverToMumakil(this.driver, this.mumakil);

        try {
            super.updateScreen();
        } finally {
            saved.restore(this.driver);
        }
    }
}
