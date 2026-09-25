package kome.common.data;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMESerfKnightRelationshipServiceTest {
    private static KOMEProgressionNpcRef npc(String name) {
        return new KOMEProgressionNpcRef(UUID.randomUUID().toString(), name, "rohan", 0, 12, 64, 12);
    }

    @Test public void forceSerfWritesCanonicalMasterAndRankWithoutRewards() {
        KOMEWorldData data = new KOMEWorldData("force-serf"); UUID player = UUID.randomUUID(); KOMEProgressionNpcRef target = npc("Master");
        assertTrue(KOMESerfKnightRelationshipService.force(data, player, target, KOMESerfKnightRelationshipService.ForceLevel.SERF).success);
        KOMEPlayerProgression progression = data.getProgression(player);
        assertEquals(KOMEProgressionRank.SERF, progression.getCanonicalRank());
        assertTrue(progression.getSerfKnightProgression().getSerfdomMaster().hasSameIdentity(target));
        assertFalse(progression.getSerfKnightProgression().getProspectiveLiege().isSet());
        assertFalse(progression.getSerfKnightProgression().getDuty(KOMESerfKnightDutyType.PROVISIONING).isCompleted());
        assertNull(progression.getSerfKnightProgression().getTrialAssignment());
    }

    @Test public void forceKnightAndLordUsePersistedRelationshipReferencesWithoutCompletingWork() {
        for (KOMESerfKnightRelationshipService.ForceLevel level : new KOMESerfKnightRelationshipService.ForceLevel[] {
            KOMESerfKnightRelationshipService.ForceLevel.KNIGHT, KOMESerfKnightRelationshipService.ForceLevel.LORD}) {
            KOMEWorldData data = new KOMEWorldData("force-" + level); UUID player = UUID.randomUUID(); KOMEProgressionNpcRef target = npc(level.name());
            assertTrue(KOMESerfKnightRelationshipService.force(data, player, target, level).success);
            KOMEPlayerProgression progression = data.getProgression(player);
            assertEquals(level.rank, progression.getCanonicalRank());
            assertTrue(progression.getSerfKnightProgression().getSerfdomMaster().hasSameIdentity(target));
            assertTrue(progression.getSerfKnightProgression().getProspectiveLiege().hasSameIdentity(target));
            // These structural records are required for a valid persisted liege;
            // no achievement, trial, or reward has been granted.
            assertTrue(progression.getSerfKnightProgression().getDuty(KOMESerfKnightDutyType.COURIER).isCompleted());
            assertFalse(progression.isCompleted(KOMEProgressionAchievement.forID("knight.craftsman")));
            assertNull(progression.getSerfKnightProgression().getTrialAssignment());
            KOMEPlayerProgression restored = new KOMEPlayerProgression();
            restored.readFromNBT(progression.writeToNBT());
            assertEquals(level.rank, restored.getCanonicalRank());
            assertTrue(restored.getSerfKnightProgression().getProspectiveLiege().hasSameIdentity(target));
        }
    }

    @Test public void forceReplacesOnlyThisPlayersRelationshipAndClearUsesNormalLeaveState() {
        KOMEWorldData data = new KOMEWorldData("force-clear"); UUID first = UUID.randomUUID(), second = UUID.randomUUID(); KOMEProgressionNpcRef one = npc("One"), two = npc("Two");
        assertTrue(KOMESerfKnightRelationshipService.force(data, first, one, KOMESerfKnightRelationshipService.ForceLevel.KNIGHT).success);
        assertTrue(KOMESerfKnightRelationshipService.force(data, second, two, KOMESerfKnightRelationshipService.ForceLevel.SERF).success);
        KOMESerfKnightProgression firstState = data.getProgression(first).getSerfKnightProgression();
        firstState.setTrial("escort");
        assertTrue(KOMESerfKnightRelationshipService.clear(data, first, one.entityUuid).success);
        assertFalse(firstState.getSerfdomMaster().isSet());
        assertFalse(firstState.getProspectiveLiege().isSet());
        assertNull(firstState.getTrialAssignment());
        assertTrue(data.getProgression(second).getSerfKnightProgression().getSerfdomMaster().hasSameIdentity(two));
        assertFalse(KOMESerfKnightRelationshipService.clear(data, second, one.entityUuid).success);
    }

    @Test public void forceRejectsMissingOrMalformedCanonicalTargets() {
        KOMEWorldData data = new KOMEWorldData("force-invalid"); UUID player = UUID.randomUUID();
        assertFalse(KOMESerfKnightRelationshipService.force(data, player, KOMEProgressionNpcRef.EMPTY, KOMESerfKnightRelationshipService.ForceLevel.SERF).success);
        assertFalse(KOMESerfKnightRelationshipService.force(data, player, npc("Valid"), null).success);
    }
}
