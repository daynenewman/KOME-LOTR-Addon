package kome.common.data;

import java.util.Random;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMESerfKnightProgressionTest {
    private static KOMEProgressionNpcRef npc(String name, String faction) {
        return new KOMEProgressionNpcRef(UUID.randomUUID().toString(), name, faction, 100, 1.5D, 64.0D, -2.5D);
    }
    private static void assignAndCompleteDuties(KOMESerfKnightProgression state) {
        long day = 10L;
        for (KOMESerfKnightDutyType type : KOMESerfKnightDutyType.values()) {
            assertTrue(KOMESerfKnightService.assignDuty(state, type, null, day++).success);
            assertTrue(KOMESerfKnightService.completeDuty(state, type).success);
        }
    }
    private static void readyForKnight(KOMESerfKnightProgression state) {
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state, npc("Master", "rohan")).success);
        assignAndCompleteDuties(state);
        assertTrue(KOMESerfKnightService.setProspectiveLiege(state, npc("Liege", "rohan")).success);
        assertTrue(KOMESerfKnightService.assignTrial(state, new Random(4L), 20L).success);
        assertTrue(KOMESerfKnightService.completeTrial(state).success);
        assertTrue(KOMESerfKnightService.recordPartingGift(state).success);
    }

    @Test public void emptyAndCompleteStateRoundTripWithSeparateNpcs() {
        KOMEPlayerProgression player = new KOMEPlayerProgression();
        assertEquals(KOMESerfKnightPhase.SERFDOM_DUTIES, player.getSerfKnightProgression().getPhase());
        assertFalse(player.getSerfKnightProgression().getSerfdomMaster().isSet());
        KOMEPlayerProgression emptyRestored = new KOMEPlayerProgression(); emptyRestored.readFromNBT(new NBTTagCompound());
        assertFalse(emptyRestored.getSerfKnightProgression().getSerfdomMaster().isSet());
        assertEquals(KOMESerfKnightPhase.SERFDOM_DUTIES, emptyRestored.getSerfKnightProgression().getPhase());
        KOMEProgressionNpcRef master = npc("Master", "rohan"), liege = npc("Liege", "gondor");
        KOMESerfKnightProgression state = player.getSerfKnightProgression();
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state, master).success);
        assignAndCompleteDuties(state);
        assertTrue(KOMESerfKnightService.setProspectiveLiege(state, liege).success);
        String assigned = KOMESerfKnightService.assignTrial(state, new Random(1L), 20L).trialId;
        assertTrue(KOMESerfKnightService.completeTrial(state).success);
        assertTrue(KOMESerfKnightService.recordPartingGift(state).success);
        KOMEPlayerProgression restored = new KOMEPlayerProgression(); restored.readFromNBT(player.writeToNBT());
        KOMESerfKnightProgression loaded = restored.getSerfKnightProgression();
        assertEquals(master.entityUuid, loaded.getSerfdomMaster().entityUuid);
        assertEquals(liege.entityUuid, loaded.getProspectiveLiege().entityUuid);
        assertNotEquals(loaded.getSerfdomMaster().entityUuid, loaded.getProspectiveLiege().entityUuid);
        assertEquals(assigned, loaded.getTrialId()); assertTrue(loaded.isTrialCompleted()); assertTrue(loaded.hasPartingGift());
        for (KOMESerfKnightDutyType type : KOMESerfKnightDutyType.values()) assertTrue(loaded.getDuty(type).isCompleted());
    }

    @Test public void dutyLifecycleRequiresAllThreeAndIsIdempotent() {
        assertEquals(3, KOMESerfKnightDutyType.values().length);
        KOMESerfKnightProgression state = new KOMESerfKnightProgression();
        assertFalse(KOMESerfKnightService.assignDuty(state, KOMESerfKnightDutyType.PROVISIONING).success);
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state, npc("Master", "rohan")).success);
        assertTrue(KOMESerfKnightService.assignDuty(state, KOMESerfKnightDutyType.PROVISIONING, null, 10L).success);
        assertTrue(KOMESerfKnightService.completeDuty(state, KOMESerfKnightDutyType.PROVISIONING).success);
        assertTrue(KOMESerfKnightService.completeDuty(state, KOMESerfKnightDutyType.PROVISIONING).success);
        assertFalse(KOMESerfKnightService.allDutiesComplete(state));
        assertFalse(KOMESerfKnightService.setProspectiveLiege(state, npc("Liege", "rohan")).success);
        assertFalse(KOMESerfKnightService.assignTrial(state, new Random(0L)).success);
        long day = 11L;
        for (KOMESerfKnightDutyType type : new KOMESerfKnightDutyType[] {KOMESerfKnightDutyType.PROFESSION, KOMESerfKnightDutyType.COURIER}) { assertTrue(KOMESerfKnightService.assignDuty(state, type, null, day++).success); assertTrue(KOMESerfKnightService.completeDuty(state, type).success); }
        assertTrue(KOMESerfKnightService.allDutiesComplete(state));
    }

    @Test public void trialsAreRegisteredDeterministicAndNeverRerolled() {
        assertNotNull(KOMESerfKnightTrial.forId("escort")); assertNotNull(KOMESerfKnightTrial.forId("recovery")); assertNotNull(KOMESerfKnightTrial.forId("defense"));
        KOMESerfKnightProgression first = new KOMESerfKnightProgression();
        assertFalse(KOMESerfKnightService.completeTrial(first).success);
        assertTrue(KOMESerfKnightService.setSerfdomMaster(first, npc("Master", "rohan")).success); assignAndCompleteDuties(first);
        assertFalse(KOMESerfKnightService.assignTrial(first, new Random(2L)).success);
        assertTrue(KOMESerfKnightService.setProspectiveLiege(first, npc("Liege", "rohan")).success);
        String selected = KOMESerfKnightService.assignTrial(first, new Random(2L), 20L).trialId;
        KOMESerfKnightProgression second = new KOMESerfKnightProgression(); assertTrue(KOMESerfKnightService.setSerfdomMaster(second, npc("Master2", "rohan")).success); assignAndCompleteDuties(second); assertTrue(KOMESerfKnightService.setProspectiveLiege(second, npc("Liege2", "rohan")).success);
        assertEquals(selected, KOMESerfKnightService.assignTrial(second, new Random(2L), 20L).trialId);
        assertFalse(KOMESerfKnightService.assignTrial(first, new Random(3L), 21L).success);
    }

    @Test public void promotionUsesLivePositiveAlignmentAndAllRequirements() {
        KOMESerfKnightProgression state = new KOMESerfKnightProgression();
        assertFalse(KOMESerfKnightService.canPromote(state, 150));
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state, npc("Master", "rohan")).success);
        assignAndCompleteDuties(state);
        assertFalse(KOMESerfKnightService.canPromote(state, 150));
        assertTrue(KOMESerfKnightService.setProspectiveLiege(state, npc("Liege", "rohan")).success);
        assertFalse(KOMESerfKnightService.canPromote(state, 150));
        assertTrue(KOMESerfKnightService.assignTrial(state, new Random(4L), 20L).success);
        assertFalse(KOMESerfKnightService.canPromote(state, 150));
        assertTrue(KOMESerfKnightService.completeTrial(state).success);
        assertFalse(KOMESerfKnightService.canPromote(state, 150));
        assertTrue(KOMESerfKnightService.recordPartingGift(state).success);
        assertFalse(KOMESerfKnightService.canPromote(state, 149));
        assertFalse(KOMESerfKnightService.canPromote(state, -150));
        assertTrue(KOMESerfKnightService.canPromote(state, 150));
        assertEquals(KOMESerfKnightPhase.READY_FOR_KNIGHT, state.getPhase());
        assertTrue(KOMESerfKnightService.markPromoted(state, 150).success);
        assertEquals(KOMESerfKnightPhase.COMPLETE, state.getPhase());
    }

    @Test public void resetAndLegacyDataRemainIndependent() {
        KOMEPlayerProgression player = new KOMEPlayerProgression(); player.grant("wanderer.expert_traveler"); player.setPledgedLord("legacy", "Legacy Lord", "gondor"); player.setPledgedLordLocation(2, 8, 9, 10);
        readyForKnight(player.getSerfKnightProgression());
        NBTTagCompound saved = player.writeToNBT(); assertTrue(saved.hasKey("SerfKnightProgression", 10));
        KOMEPlayerProgression restored = new KOMEPlayerProgression(); restored.readFromNBT(saved);
        assertTrue(restored.isCompleted(KOMEProgressionAchievement.forID("wanderer.expert_traveler")));
        assertEquals("legacy", restored.getPledgedLordID()); assertEquals("Legacy Lord", restored.getPledgedLordDisplay().split(" of ")[0]);
        restored.reset(); assertFalse(restored.getSerfKnightProgression().getSerfdomMaster().isSet()); assertEquals(KOMESerfKnightPhase.SERFDOM_DUTIES, restored.getSerfKnightProgression().getPhase());
    }

    @Test public void relationshipPresentationFollowsCanonicalPhaseAndLegacyIsOnlyFallback() {
        KOMEPlayerProgression player = new KOMEPlayerProgression(); player.setCanonicalRank(KOMEProgressionRank.SERF); player.setPledgedLord("legacy", "Legacy", "rohan");
        KOMESerfKnightProgression state = player.getSerfKnightProgression();
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state, npc("Master", "rohan")).success);
        assertEquals("Find Master", KOMEProgressionSummary.findLabel(player)); assertEquals("Leave Master", KOMEProgressionSummary.leaveRelationshipLabel(player));
        assignAndCompleteDuties(state);
        assertEquals("", KOMEProgressionSummary.findLabel(player)); assertEquals("Leave Master", KOMEProgressionSummary.leaveRelationshipLabel(player));
        assertTrue(KOMESerfKnightService.setProspectiveLiege(state, npc("Liege", "rohan")).success);
        assertEquals("Find Liege", KOMEProgressionSummary.findLabel(player)); assertEquals("Leave Liege", KOMEProgressionSummary.leaveRelationshipLabel(player));
        assertTrue(KOMEProgressionSummary.text(player).contains("Next: Speak with your Liege"));
        assertTrue(KOMESerfKnightService.assignTrial(state, new Random(1L), 20L).success);
        assertEquals("Find Liege", KOMEProgressionSummary.findLabel(player)); assertEquals("Leave Liege", KOMEProgressionSummary.leaveRelationshipLabel(player));
        assertTrue(KOMESerfKnightService.completeTrial(state).success);
        assertEquals("Find Master", KOMEProgressionSummary.findLabel(player)); assertEquals("Leave Master", KOMEProgressionSummary.leaveRelationshipLabel(player));
        assertTrue(KOMESerfKnightService.leaveSerfdomMaster(state).success);
        assertEquals("Find Lord", KOMEProgressionSummary.findLabel(player)); assertEquals("", KOMEProgressionSummary.leaveRelationshipLabel(player));
    }

    @Test public void npcIdentityAndAssignmentDataAreValidatedAndDefensive() {
        try { new KOMEProgressionNpcRef("not-a-uuid", "Bad", "ROHAN", 0, 0, 0, 0); fail(); } catch (IllegalArgumentException expected) { }
        NBTTagCompound malformed = new NBTTagCompound(); malformed.setString("EntityUUID", "not-a-uuid"); malformed.setString("Name", "Bad");
        assertFalse(KOMEProgressionNpcRef.readFromNBT(malformed).isSet());
        KOMEProgressionNpcRef normalized = npc("Master", "ROHAN"); assertEquals("rohan", normalized.factionKey);
        KOMESerfKnightProgression state = new KOMESerfKnightProgression(); assertTrue(KOMESerfKnightService.setSerfdomMaster(state, normalized).success);
        NBTTagCompound data = new NBTTagCompound(); data.setString("Future", "original");
        assertTrue(KOMESerfKnightService.assignDuty(state, KOMESerfKnightDutyType.PROVISIONING, data, 10L).success);
        data.setString("Future", "mutated"); assertEquals("original", state.getDuty(KOMESerfKnightDutyType.PROVISIONING).getAssignmentData().getString("Future"));
        NBTTagCompound read = state.getDuty(KOMESerfKnightDutyType.PROVISIONING).getAssignmentData(); read.setString("Future", "mutated again");
        assertEquals("original", state.getDuty(KOMESerfKnightDutyType.PROVISIONING).getAssignmentData().getString("Future"));
    }

    @Test public void corruptNbtCannotCreateReadinessAndPromotedStateRoundTrips() {
        KOMESerfKnightProgression source = new KOMESerfKnightProgression(); readyForKnight(source);
        NBTTagCompound corrupt = source.writeToNBT();
        corrupt.setString("Phase", "READY_FOR_KNIGHT"); // Legacy/foreign phase data is ignored; it is not authoritative.
        NBTTagCompound duties = corrupt.getCompoundTag("Duties");
        for (KOMESerfKnightDutyType type : KOMESerfKnightDutyType.values()) duties.getCompoundTag(type.key).setBoolean("Assigned", false);
        KOMESerfKnightProgression loaded = new KOMESerfKnightProgression(); loaded.readFromNBT(corrupt);
        assertEquals(KOMESerfKnightPhase.SERFDOM_DUTIES, loaded.getPhase()); assertFalse(KOMESerfKnightService.canPromote(loaded, 150));
        KOMESerfKnightProgression promoted = new KOMESerfKnightProgression(); readyForKnight(promoted); assertTrue(KOMESerfKnightService.markPromoted(promoted, 150).success);
        KOMESerfKnightProgression restored = new KOMESerfKnightProgression(); restored.readFromNBT(promoted.writeToNBT());
        assertTrue(restored.isPromoted()); assertEquals(KOMESerfKnightPhase.COMPLETE, restored.getPhase()); assertFalse(KOMESerfKnightService.canPromote(restored, 150));
    }
}
