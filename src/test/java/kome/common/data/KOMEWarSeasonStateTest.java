package kome.common.data;

import java.time.Instant;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

/** Deterministic campaign state coverage: every time is supplied by the test. */
public class KOMEWarSeasonStateTest {
    @Test public void initialAndLegalLifecycleAreExplicit() {
        KOMEWarSeasonState s = new KOMEWarSeasonState();
        assertEquals(KOMEWarSeasonState.Phase.MAINTENANCE, s.phase); assertFalse(s.isPopulationPayoutEnabled());
        assertTrue(s.beginPreWar(10L).allowed); assertEquals(KOMEWarSeasonState.Phase.PRE_WAR, s.phase);
        assertTrue(s.recordLegalConflict(20L, 100L).allowed); assertEquals(120L, s.minimumWarEndMillis); assertTrue(s.isPopulationPayoutEnabled());
        assertTrue(s.triggerFinale(UUID.randomUUID(), "king", true, 120L).allowed); assertEquals(KOMEWarSeasonState.Phase.FINALE, s.phase);
        assertTrue(s.beginReset(130L).allowed); assertFalse(s.isPopulationPayoutEnabled()); assertTrue(s.completeReset(140L).allowed);
        assertEquals(KOMEWarSeasonState.Phase.MAINTENANCE, s.phase); assertEquals(2L, s.seasonId); assertEquals("COMPLETE", s.resetStatus);
    }
    @Test public void illegalTransitionsAndEarlyOrIneligibleFinaleExplainWhy() {
        KOMEWarSeasonState s = new KOMEWarSeasonState();
        assertFalse(s.beginReset(0L).allowed); assertFalse(s.triggerFinale(UUID.randomUUID(), "x", true, 0L).allowed);
        s.recordLegalConflict(100L, 50L);
        assertFalse(s.triggerFinale(UUID.randomUUID(), "x", true, 149L).allowed);
        assertFalse(s.triggerFinale(UUID.randomUUID(), "x", false, 150L).allowed);
        assertEquals(KOMEWarSeasonState.Phase.WAR, s.phase); // no automatic transition at the deadline
    }
    @Test public void firstLegalConflictStartsWarOnlyOnce() {
        KOMEWorldData data = new KOMEWorldData("season");
        assertTrue(KOMEWarService.recordFirstLegalConflict(data, 500L).allowed);
        assertEquals(KOMEWarSeasonState.Phase.WAR, data.warSeason.phase);
        assertFalse(KOMEWarService.recordFirstLegalConflict(data, 600L).allowed);
    }
    @Test public void finaleActorAndCampaignStatePersistWithoutTouchingPopulationOrUnits() {
        KOMEWorldData data = new KOMEWorldData("season"); data.grantFactionPopulation("gondor", 17);
        UUID unitId = UUID.randomUUID(); KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord(); unit.entity = unitId; unit.owner = UUID.randomUUID(); data.hiredUnits.put(unitId, unit);
        UUID actor = UUID.randomUUID(); data.warSeason.recordLegalConflict(100L, 0L); assertTrue(data.warSeason.triggerFinale(actor, "Aragorn", true, 100L).allowed);
        NBTTagCompound tag = new NBTTagCompound(); data.writeToNBT(tag); KOMEWorldData restored = new KOMEWorldData("restored"); restored.readFromNBT(tag);
        assertEquals(KOMEWarSeasonState.Phase.FINALE, restored.warSeason.phase); assertEquals(actor, restored.warSeason.finaleTriggerActor); assertEquals("Aragorn", restored.warSeason.finaleTriggerActorName);
        assertEquals(100L, restored.warSeason.finaleTriggerTimeMillis); assertEquals(17, KOMEPopulationService.getAvailablePopulation(restored, "gondor")); assertTrue(restored.hiredUnits.containsKey(unitId));
    }
    @Test public void payoutIsFrozenOutsideWarAndFinale() {
        KOMEWorldData data = new KOMEWorldData("season"); data.populationPayoutInitialized = true; data.lastPopulationPayoutBoundaryMillis = 0L;
        KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data, Instant.ofEpochMilli(1000L)); assertEquals(0, data.factionPopulations.size());
        data.warSeason.recordLegalConflict(1000L, -1L); assertTrue(data.warSeason.isPopulationPayoutEnabled());
        assertTrue(data.warSeason.triggerFinale(UUID.randomUUID(), "king", true, 1000L).allowed == false); // TBD duration blocks Finale, but War payout remains enabled
        assertTrue(data.warSeason.beginPreWar(1000L).allowed == false);
    }
    @Test public void repairProvidesAdministrativeRecovery() {
        KOMEWarSeasonState s = new KOMEWarSeasonState(); assertTrue(s.repair(KOMEWarSeasonState.Phase.RESET, 5L).allowed);
        assertEquals("PENDING", s.resetStatus); assertTrue(s.repair(KOMEWarSeasonState.Phase.MAINTENANCE, 6L).allowed); assertEquals("COMPLETE", s.resetStatus);
    }
}
