package kome.common.data;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMECanonicalPlayerRankTest {
    @Test public void canonicalRankDefaultsRoundTripsAndNeverMigratesLegacyAchievements() {
        KOMEPlayerProgression progression = new KOMEPlayerProgression();
        assertEquals(KOMEProgressionRank.WANDERER, progression.getCanonicalRank());
        assertTrue(progression.grant("knight.craftsman"));
        NBTTagCompound before = progression.writeToNBT();
        KOMEPlayerProgression restored = new KOMEPlayerProgression(); restored.readFromNBT(before);
        assertEquals(KOMEProgressionRank.WANDERER, restored.getCanonicalRank());
        assertTrue(restored.isCompleted(KOMEProgressionAchievement.forID("knight.craftsman")));
        KOMEWorldData data = new KOMEWorldData("ranks"); UUID id = UUID.randomUUID();
        assertTrue(KOMECanonicalRankService.setCanonicalRank(data, id, KOMEProgressionRank.SERF));
        NBTTagCompound saved = data.getProgression(id).writeToNBT();
        KOMEPlayerProgression loaded = new KOMEPlayerProgression(); loaded.readFromNBT(saved);
        assertEquals(KOMEProgressionRank.SERF, loaded.getCanonicalRank());
        assertFalse(loaded.isCompleted(KOMEProgressionAchievement.forID("serf.quest_seeker")));
    }

    @Test public void malformedCanonicalRankFailsSafelyAndKingIsNotAPlayerRank() {
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("CanonicalRank", "king");
        KOMEPlayerProgression progression = new KOMEPlayerProgression(); progression.readFromNBT(tag);
        assertEquals(KOMEProgressionRank.WANDERER, progression.getCanonicalRank());
        assertNull(KOMEProgressionRank.forKey("king"));
    }

    @Test public void resetReturnsToWandererAndRankChangesDoNotEraseCanonicalSerfState() {
        KOMEWorldData data = new KOMEWorldData("ranks"); UUID id = UUID.randomUUID();
        KOMEPlayerProgression progression = data.getProgression(id);
        KOMEProgressionNpcRef master = new KOMEProgressionNpcRef(UUID.randomUUID().toString(), "Master", "rohan", 0, 1, 2, 3);
        assertTrue(KOMESerfKnightService.setSerfdomMaster(progression.getSerfKnightProgression(), master).success);
        assertTrue(KOMECanonicalRankService.setCanonicalRank(data, id, KOMEProgressionRank.SERF));
        assertTrue(KOMECanonicalRankService.setCanonicalRank(data, id, KOMEProgressionRank.KNIGHT));
        assertEquals(KOMEProgressionRank.KNIGHT, progression.getCanonicalRank());
        assertEquals(master.entityUuid, progression.getSerfKnightProgression().getSerfdomMaster().entityUuid);
        progression.reset();
        assertEquals(KOMEProgressionRank.WANDERER, progression.getCanonicalRank());
    }

    @Test public void serfdomMasterEligibilityRequiresSerfSameFactionAndExactUnrankedNpc() {
        assertTrue(KOMESerfdomMasterService.validate(KOMEProgressionRank.SERF,"rohan","rohan",KOMEProgressionNpcRank.UNRANKED,true).success);
        for(KOMEProgressionRank rank:new KOMEProgressionRank[]{KOMEProgressionRank.WANDERER,KOMEProgressionRank.KNIGHT,KOMEProgressionRank.LORD,KOMEProgressionRank.PRINCE}) assertFalse(KOMESerfdomMasterService.validate(rank,"rohan","rohan",KOMEProgressionNpcRank.UNRANKED,true).success);
        for(KOMEProgressionNpcRank rank:new KOMEProgressionNpcRank[]{KOMEProgressionNpcRank.LORD,KOMEProgressionNpcRank.PRINCE,KOMEProgressionNpcRank.KING}) assertFalse(KOMESerfdomMasterService.validate(KOMEProgressionRank.SERF,"rohan","rohan",rank,true).success);
        assertFalse(KOMESerfdomMasterService.validate(KOMEProgressionRank.SERF,"rohan","gondor",KOMEProgressionNpcRank.UNRANKED,true).success);
        assertFalse(KOMESerfdomMasterService.validate(KOMEProgressionRank.SERF,"","rohan",KOMEProgressionNpcRank.UNRANKED,true).success);
    }

    @Test public void serfdomRoutingUsesFullEligibilityAndGuiHasNoLocalHighlightAuthority() throws Exception {
        assertTrue(KOMESerfdomMasterService.validate(KOMEProgressionRank.SERF,"rohan","rohan",KOMEProgressionNpcRank.UNRANKED,true).success);
        assertFalse(KOMESerfdomMasterService.validate(KOMEProgressionRank.SERF,"rohan","gondor",KOMEProgressionNpcRank.UNRANKED,true).success);
        assertFalse(KOMESerfdomMasterService.validate(KOMEProgressionRank.SERF,"","rohan",KOMEProgressionNpcRank.UNRANKED,true).success);
        String gui = new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiSerfdomMaster.java")), StandardCharsets.UTF_8);
        assertFalse(gui.contains("KOMEEntityHighlightOverlay"));
        assertFalse(gui.contains("Highlight master"));
    }

    @Test public void dutyOrchestrationRequiresCurrentMasterUuidAndUsesCanonicalCadence() {
        KOMESerfKnightProgression state = new KOMESerfKnightProgression();
        KOMEProgressionNpcRef master = new KOMEProgressionNpcRef(UUID.randomUUID().toString(), "Master", "rohan", 0, 0, 0, 0);
        KOMEProgressionNpcRef other = new KOMEProgressionNpcRef(UUID.randomUUID().toString(), "Other", "rohan", 0, 0, 0, 0);
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state, master).success);
        assertTrue(KOMESerfdomMasterService.requestDuty(state, master, 10L, new net.minecraft.nbt.NBTTagCompound(), new java.util.Random(1L)).success);
        assertTrue(state.getDuty(KOMESerfKnightDutyType.PROVISIONING).isAssigned());
        assertEquals(10L, state.getLastAssignmentEpochDay());
        assertFalse(KOMESerfdomMasterService.requestDuty(state, other, 10L).success);
        assertFalse(KOMESerfdomMasterService.requestDuty(state, master, 10L).success);
        assertTrue(KOMESerfKnightService.completeDuty(state, KOMESerfKnightDutyType.PROVISIONING).success);
        assertFalse(KOMESerfdomMasterService.requestDuty(state, master, 10L).success);
        assertTrue(KOMESerfdomMasterService.requestDuty(state, master, 11L).success);
        assertTrue(state.getDuty(KOMESerfKnightDutyType.PROFESSION).isAssigned());
    }
    @Test public void voluntaryDepartureResetsOnlyRelationshipScopedProgressWithoutCadencePenalty() {
        KOMESerfKnightProgression state=new KOMESerfKnightProgression();KOMEProgressionNpcRef master=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Master","rohan",0,0,0,0);assertTrue(KOMESerfKnightService.setSerfdomMaster(state,master).success);assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.PROVISIONING,null,20L).success);assertEquals(20L,state.getLastAssignmentEpochDay());assertTrue(KOMESerfKnightService.leaveSerfdomMaster(state).success);assertFalse(state.getSerfdomMaster().isSet());assertFalse(state.getDuty(KOMESerfKnightDutyType.PROVISIONING).isAssigned());assertFalse(state.isMasterReplacementRequired());assertEquals(20L,state.getLastAssignmentEpochDay());assertTrue(KOMESerfKnightService.setSerfdomMaster(state,new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"New","rohan",0,0,0,0)).success);assertFalse(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.PROVISIONING,null,20L).success);assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.PROVISIONING,null,21L).success);
    }
}
