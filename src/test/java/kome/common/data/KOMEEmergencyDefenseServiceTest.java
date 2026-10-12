package kome.common.data;

import kome.common.KOMEAccessFixture;
import lotr.common.entity.npc.LOTREntityGondorMan;
import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.EntryOrigin;
import static org.junit.Assert.*;

public class KOMEEmergencyDefenseServiceTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private static final long DAY = KOMEEmergencyDefenseService.REAL_DAY_MILLIS;

    @Before @After public void resetRelations() {
        relation("gondor", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
        relation("rohan", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
    }

    @Test public void kinglessRequiresAuthoritativeHostileDefensiveConflict() {
        KOMEWorldData data = world();
        assertFalse(KOMEEmergencyDefenseService.INSTANCE
            .assess(data, "mordor", 20L).eligible);

        createAttack(data, "T100", "mordor", "gondor", "C1", 10L);
        relation("gondor", "mordor", LOTRFactionRelations.Relation.ENEMY);
        KOMEEmergencyDefenseService.Assessment eligible =
            KOMEEmergencyDefenseService.INSTANCE.assess(data, "mordor", 20L);
        assertTrue(eligible.eligible);
        assertFalse(eligible.mobilized);
        assertFalse(eligible.active);
        assertFalse(eligible.recognizedKing);
        assertEquals(Collections.singletonList("CF1"), eligible.qualifyingConflictIds);

        relation("gondor", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
        assertFalse(KOMEEmergencyDefenseService.INSTANCE
            .assess(data, "mordor", 21L).eligible);
    }

    @Test public void ruledFactionUsesRecruitmentActivityAndConservativeUnknownAnchor() {
        KOMEWorldData data = world();
        data.writeFactionKingRecord("mordor", UUID.randomUUID(), "King");
        KOMEEmergencyDefenseService.INSTANCE.anchorUnknownHistory(data, "mordor", 100L);
        createAttack(data, "T100", "mordor", "gondor", "C1", 110L);
        relation("gondor", "mordor", LOTRFactionRelations.Relation.ENEMY);
        long threshold = KOMEEmergencyDefenseService.INSTANCE
            .configuredInactivityThresholdMillis();

        KOMEEmergencyDefenseService.Assessment fresh =
            KOMEEmergencyDefenseService.INSTANCE.assess(data, "mordor", 100L + threshold - 1L);
        assertFalse(fresh.knownQualifyingHire);
        assertFalse(fresh.eligible);
        assertTrue(fresh.reason.contains("observation window"));
        assertTrue(KOMEEmergencyDefenseService.INSTANCE
            .assess(data, "mordor", 100L + threshold).eligible);

        KOMEEmergencyDefenseService.INSTANCE.recordRecruitmentActivity(data, "mordor", 2,
            true, KOMEEmergencyDefenseService.RecruitmentSource.PLAYER_COMBAT_HIRE,
            100L + threshold);
        assertFalse(KOMEEmergencyDefenseService.INSTANCE
            .assess(data, "mordor", 100L + threshold + DAY).eligible);
    }

    @Test public void onlySuccessfulPositivePlayerCombatDebitResetsActivity() {
        KOMEWorldData data = world();
        data.grantFactionPopulationCenti("mordor", 1000L);
        long first = 500L;
        KOMEPopulationService.CombatHireDebit committed =
            KOMEPopulationService.beginCombatHireDebit(data, "mordor", 2,
                KOMEEmergencyDefenseService.RecruitmentSource.PLAYER_COMBAT_HIRE, first);
        assertNotNull(committed);
        committed.commit();
        assertEquals(first, data.emergencyDefenseActivities.get("mordor")
            .lastQualifyingHireAtMillis);

        KOMEPopulationService.CombatHireDebit rolledBack =
            KOMEPopulationService.beginCombatHireDebit(data, "mordor", 1,
                KOMEEmergencyDefenseService.RecruitmentSource.PLAYER_COMBAT_HIRE, 600L);
        assertNotNull(rolledBack);
        rolledBack.rollback();
        assertEquals(first, data.emergencyDefenseActivities.get("mordor")
            .lastQualifyingHireAtMillis);
        assertNull(KOMEPopulationService.beginCombatHireDebit(data, "mordor", 100,
            KOMEEmergencyDefenseService.RecruitmentSource.PLAYER_COMBAT_HIRE, 700L));

        KOMEPopulationService.CombatHireDebit zero =
            KOMEPopulationService.beginCombatHireDebit(data, "mordor", 0,
                KOMEEmergencyDefenseService.RecruitmentSource.PLAYER_COMBAT_HIRE, 800L);
        assertNotNull(zero); zero.commit();
        assertFalse(KOMEEmergencyDefenseService.INSTANCE.recordRecruitmentActivity(data,
            "mordor", 3, false,
            KOMEEmergencyDefenseService.RecruitmentSource.PLAYER_COMBAT_HIRE, 900L));
        assertFalse(KOMEEmergencyDefenseService.INSTANCE.recordRecruitmentActivity(data,
            "mordor", 3, true,
            KOMEEmergencyDefenseService.RecruitmentSource.SYSTEM_EMERGENCY_RESERVE, 1000L));
        assertEquals(first, data.emergencyDefenseActivities.get("mordor")
            .lastQualifyingHireAtMillis);
    }

    @Test public void fixedWarSidesCannotActivateEmergencyDefense() {
        KOMEWorldData data = world();
        KOMEWar war = KOMEWarService.createWar(data, "mordor", "gondor", "", "test", 1L);
        assertNotNull(war);
        relation("gondor", "mordor", LOTRFactionRelations.Relation.ENEMY);
        assertFalse(KOMEEmergencyDefenseService.INSTANCE
            .assess(data, "mordor", 100L).eligible);
    }

    @Test public void activityRoundTripsAndMalformedSectionFailsAtomically() {
        KOMEWorldData data = world();
        data.writeFactionKingRecord("mordor", UUID.randomUUID(), "King");
        KOMEEmergencyDefenseService.INSTANCE.anchorUnknownHistory(data, "mordor", 100L);
        KOMEEmergencyDefenseService.INSTANCE.recordRecruitmentActivity(data, "mordor", 1,
            true, KOMEEmergencyDefenseService.RecruitmentSource.PLAYER_COMBAT_HIRE, 200L);
        NBTTagCompound saved = new NBTTagCompound(); data.writeToNBT(saved);
        KOMEWorldData restored = new KOMEWorldData("restored"); restored.readFromNBT(saved);
        assertEquals(200L, restored.emergencyDefenseActivities.get("mordor")
            .lastQualifyingHireAtMillis);

        KOMEWorldData live = world();
        live.emergencyDefenseActivities.put("gondor",
            KOMEEmergencyDefenseActivity.unknownHistory("gondor", 50L));
        NBTTagCompound malformed = (NBTTagCompound) saved.copy();
        malformed.setString(KOMEEmergencyDefensePersistence.RECORDS_KEY, "wrong type");
        try {
            live.readFromNBT(malformed);
            fail("Malformed emergency-defense section loaded.");
        } catch (IllegalStateException expected) {
            assertTrue(live.isWriteBlocked());
            assertTrue(live.emergencyDefenseActivities.containsKey("gondor"));
            assertFalse(live.emergencyDefenseActivities.containsKey("mordor"));
        }
    }

    @Test public void schemaEightMigrationPreservesActivityAndInventsNoCommitment() {
        KOMEWorldData source = world();
        source.writeFactionKingRecord("mordor", UUID.randomUUID(), "King");
        KOMEEmergencyDefenseService.INSTANCE.anchorUnknownHistory(source, "mordor", 100L);
        NBTTagCompound schemaEight = new NBTTagCompound(); source.writeToNBT(schemaEight);
        schemaEight.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 8);
        schemaEight.removeTag("MovementBoundary");
        NBTTagList historicalCompanies = schemaEight.getTagList("ArmyCompanies", 10);
        for (int i = 0; i < historicalCompanies.tagCount(); i++)
            historicalCompanies.getCompoundTagAt(i).removeTag("MovementAllowance");
        schemaEight.removeTag(KOMEWorldData.TACTICAL_CONFIGURATION_REQUIRED_KEY);
        schemaEight.removeTag("TacticalConfiguration");
        schemaEight.setInteger(KOMEEmergencyDefensePersistence.SCHEMA_KEY, 1);
        schemaEight.removeTag(KOMEEmergencyDefensePersistence.COMMITMENTS_KEY);
        schemaEight.removeTag(KOMEEmergencyDefensePersistence.OBSERVATIONS_KEY);

        KOMEWorldData migrated = new KOMEWorldData("migrated");
        migrated.readFromNBT(schemaEight);
        KOMEEmergencyDefenseActivity activity =
            migrated.emergencyDefenseActivities.get("mordor");
        assertNotNull(activity);
        assertFalse(activity.hasKnownQualifyingHire());
        assertEquals(KOMEEmergencyDefenseActivity.UNKNOWN_HISTORY_ANCHOR,
            activity.updateSource);
        assertTrue(migrated.emergencyDefenseCommitments.isEmpty());
        assertTrue(migrated.emergencyDefenseObservations.isEmpty());
        assertTrue(migrated.isDirty());
    }

    @Test public void inspectionIsReadOnly() {
        KOMEWorldData data = world();
        createAttack(data, "T100", "mordor", "gondor", "C1", 10L);
        relation("gondor", "mordor", LOTRFactionRelations.Relation.ENEMY);
        NBTTagCompound before = new NBTTagCompound(); data.writeToNBT(before);
        int auditSize = data.centralAudit.size();
        assertFalse(KOMEEmergencyDefenseService.INSTANCE
            .inspectionLines(data, "mordor", 20L).isEmpty());
        NBTTagCompound after = new NBTTagCompound(); data.writeToNBT(after);
        assertEquals(before, after);
        assertEquals(auditSize, data.centralAudit.size());
    }

    @Test public void inspectionDistinguishesLoadedAndUnloadedActiveDefendersWithoutInference()
            throws Exception {
        KOMEAccessFixture fixture = new KOMEAccessFixture();
        UUID loadedId = UUID.randomUUID(), unloadedId = UUID.randomUUID();
        java.util.Map<String, KOMEEmergencyDefenseCommitment.Defender> defenders =
            new java.util.LinkedHashMap<String, KOMEEmergencyDefenseCommitment.Defender>();
        defenders.put("ED-1-1", new KOMEEmergencyDefenseCommitment.Defender(
            "ED-1-1", loadedId, 20, 10L,
            KOMEEmergencyDefenseCommitment.Disposition.ACTIVE, false, "deployed"));
        defenders.put("ED-1-2", new KOMEEmergencyDefenseCommitment.Defender(
            "ED-1-2", unloadedId, 20, 10L,
            KOMEEmergencyDefenseCommitment.Disposition.ACTIVE, false, "deployed"));
        fixture.data.emergencyDefenseCommitments.put("CF1",
            new KOMEEmergencyDefenseCommitment("CF1", "T100", "gondor", 1L, 1L,
                4000L, "EDT-GONDOR", 20, 2, 4000L, 10L,
                KOMEEmergencyDefenseCommitment.State.ACTIVE, defenders, "test"));
        fixture.world.isRemote = true;
        LOTREntityGondorMan loaded = new LOTREntityGondorMan(fixture.world);
        fixture.world.isRemote = false;
        loaded.setUniqueID(loadedId);
        loaded.setLocationAndAngles(11.5D, 65D, -4.5D, 0F, 0F);
        loaded.dimension = 100;
        KOMEEmergencyDefenseMobilizationService.establishBattlefieldHome(loaded,
            "ED-1-1", "CF1", 10, 65, -5);
        fixture.world.loadedEntityList.add(loaded);

        java.util.List<String> lines = KOMEEmergencyDefenseService.INSTANCE
            .inspectionLines(fixture.data, "gondor", 20L, fixture.world);
        String loadedLine = lineContaining(lines, loadedId.toString());
        String unloadedLine = lineContaining(lines, unloadedId.toString());
        assertTrue(loadedLine, loadedLine.contains("disposition=ACTIVE"));
        assertTrue(loadedLine, loadedLine.contains("loaded=true"));
        assertTrue(loadedLine, loadedLine.contains("dimension=100"));
        assertTrue(loadedLine, loadedLine.contains("position=11.5,65.0,-4.5"));
        assertTrue(loadedLine, loadedLine.contains("home=10,65,-5 radius=24"));
        assertTrue(unloadedLine, unloadedLine.contains("disposition=ACTIVE"));
        assertTrue(unloadedLine, unloadedLine.contains("loaded=false"));
        assertFalse(unloadedLine, unloadedLine.contains("dimension="));
    }

    private static String lineContaining(java.util.List<String> lines, String value) {
        for (String line : lines) if (line.contains(value)) return line;
        throw new AssertionError("Missing inspection line containing " + value + ": " + lines);
    }

    private static KOMEWorldData world() {
        KOMEWorldData data = new KOMEWorldData("test");
        data.initializeIntegratedWorld();
        return data;
    }

    private static KOMEConflictRecord createAttack(KOMEWorldData data, String tileId,
            String defender, String attacker, String detachmentId, long now) {
        KOMEConquestTile tile = data.getConquestTile(tileId);
        tile.claim(defender, now);
        ValidatedCommitmentRequest request = new ValidatedCommitmentRequest(tileId,
            detachmentId, attacker, ValidatedConflictAuthority.defender(defender), now,
            EntryOrigin.LEGAL_ARRIVAL, "M1", false,
            Collections.<GarrisonParticipantSeed>emptyList(), ExpectedConflict.absent());
        KOMEConflictContracts.DetachmentResolver resolver =
            new KOMEConflictContracts.DetachmentResolver() {
                @Override public DetachmentResolution resolve(String ignored) {
                    return new DetachmentResolution(detachmentId, ReferenceStatus.RESOLVED,
                        KOMEHiredUnitClass.CAMPAIGN, attacker, tileId, "test");
                }
            };
        KOMEConflictContracts.HostilityResolver hostile =
            new KOMEConflictContracts.HostilityResolver() {
                @Override public Hostility resolve(String first, String second) {
                    return Hostility.HOSTILE;
                }
            };
        KOMEConflictService.Result result = data.getConflictService()
            .acceptValidatedCommitment(request, hostile, resolver,
                new Context(now, "test", "hostile arrival"));
        assertEquals(Code.SUCCESS, result.code);
        return result.record;
    }

    private static void relation(String first, String second,
            LOTRFactionRelations.Relation relation) {
        LOTRFactionRelations.overrideRelations(KOMEAlliance.findLotrFaction(first),
            KOMEAlliance.findLotrFaction(second), relation);
    }
}
