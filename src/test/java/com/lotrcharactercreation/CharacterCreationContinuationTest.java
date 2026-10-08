package com.lotrcharactercreation;

import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.creation.*;
import com.lotrcharactercreation.faction.StartingFaction;
import com.lotrcharactercreation.race.*;
import kome.common.KOMEAccessFixture;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class CharacterCreationContinuationTest {
    @Test public void confirmedSelectionsDetermineExactPersistedStageAndCannotFinalizeEarly()throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();assertEquals(CharacterCreationStage.RACE,CharacterCreationFlowService.getNextRequiredStage(f.player));
        assertTrue(CharacterCreationFlowService.selectRace(f.player,PlayerRace.MAN));assertEquals(CharacterCreationStage.SEX,CharacterCreationFlowService.getNextRequiredStage(f.player));
        assertTrue(CharacterCreationFlowService.selectSex(f.player,PlayerSex.FEMALE));assertEquals(CharacterCreationStage.FACTION,CharacterCreationFlowService.getNextRequiredStage(f.player));
        assertTrue(CharacterCreationFlowService.selectStartingFaction(f.player,StartingFaction.ROHAN));assertEquals(CharacterCreationStage.APPEARANCE,CharacterCreationFlowService.getNextRequiredStage(f.player));
        KOMEAccessFixture reconnect=new KOMEAccessFixture();NBTTagCompound saved=(NBTTagCompound)f.player.getEntityData().copy();
        for(Object key:saved.func_150296_c())reconnect.player.getEntityData().setTag((String)key,saved.getTag((String)key).copy());
        assertEquals(CharacterCreationStage.APPEARANCE,CharacterCreationFlowService.getNextRequiredStage(reconnect.player));
        assertEquals(PlayerRace.MAN,PlayerRaceData.getRace(reconnect.player));assertEquals(PlayerSex.FEMALE,PlayerRaceData.getSex(reconnect.player));assertEquals(StartingFaction.ROHAN,PlayerRaceData.getStartingFaction(reconnect.player));
        assertFalse(PlayerRaceData.isCharacterCreationComplete(reconnect.player));assertFalse(CharacterCreationFlowService.isReadyForFinalization(reconnect.player));
        assertFalse(CharacterCreationFlowService.selectRace(reconnect.player,PlayerRace.ELF));
    }
}
