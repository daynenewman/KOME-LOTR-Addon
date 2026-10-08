package com.lotrcharactercreation.client.gui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.faction.StartingFaction;
import com.lotrcharactercreation.race.PlayerRace;
import cpw.mods.fml.common.gameevent.TickEvent;
import kome.common.KOMEAccessFixture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CharacterCreationPauseTest {
    public static class TestMinecraft extends Minecraft {
        private TestMinecraft(){super(null,1,1,false,false,null,null,null,null,"test",null,null);}
        @Override public void displayGuiScreen(GuiScreen next){currentScreen=next;}
    }
    @Test public void escapeOpensVanillaPauseAtEveryMandatoryStageIncludingPendingSelection()throws Exception {
        Field global=Minecraft.class.getDeclaredField("theMinecraft");global.setAccessible(true);Object previous=global.get(null);
        TestMinecraft mc=KOMEAccessFixture.allocate(TestMinecraft.class);global.set(null,mc);
        try{
            GuiScreen[] screens={new GuiRaceSelection(),new GuiSexSelection(null,PlayerRace.MAN,PlayerSex.MALE,true),
                new GuiStartingFactionSelection(null,PlayerRace.MAN,StartingFaction.ROHAN,true),
                new GuiAppearanceSelection(null,PlayerRace.MAN,PlayerSex.MALE,StartingFaction.ROHAN,"",true),
                new GuiCharacterConfirmation(null,PlayerRace.MAN,PlayerSex.MALE,StartingFaction.ROHAN,"","",true)};
            for(GuiScreen screen:screens){
                mc.currentScreen=screen;Method key=screen.getClass().getDeclaredMethod("keyTyped",char.class,int.class);key.setAccessible(true);
                key.invoke(screen,(char)27,1);assertTrue(screen.getClass().getName(),mc.currentScreen instanceof GuiIngameMenu);
            }
        }finally{ClientCreationContinuation.INSTANCE.complete();global.set(null,previous);}
    }
    @Test public void serverStageUpdatesWaitForPauseOptionsAndResumeOnlyWhenReturningToWorld()throws Exception {
        Field global=Minecraft.class.getDeclaredField("theMinecraft");global.setAccessible(true);Object previous=global.get(null);
        TestMinecraft mc=KOMEAccessFixture.allocate(TestMinecraft.class);global.set(null,mc);
        mc.thePlayer=KOMEAccessFixture.allocate(net.minecraft.client.entity.EntityClientPlayerMP.class);
        mc.theWorld=KOMEAccessFixture.allocate(net.minecraft.client.multiplayer.WorldClient.class);
        try{
            GuiScreen options=new GuiOptions(new GuiIngameMenu(),null);mc.currentScreen=options;
            GuiScreen next=new GuiRaceSelection();ClientCreationContinuation.INSTANCE.require(next);
            ClientCreationContinuation.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));assertSame(options,mc.currentScreen);
            mc.currentScreen=null;ClientCreationContinuation.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));assertSame(next,mc.currentScreen);
            ClientCreationContinuation.INSTANCE.disconnect(KOMEAccessFixture.clientDisconnected());mc.currentScreen=null;
            ClientCreationContinuation.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));assertNull(mc.currentScreen);
        }finally{ClientCreationContinuation.INSTANCE.complete();global.set(null,previous);}
    }
}
