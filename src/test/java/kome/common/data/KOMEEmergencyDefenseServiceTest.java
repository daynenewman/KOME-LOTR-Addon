package kome.common.data;

import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.nbt.NBTTagCompound;
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

    @Test public void schemaSevenMigrationAnchorsRuledFactionWithoutInventingHire() {
        KOMEWorldData source = world();
        source.writeFactionKingRecord("mordor", UUID.randomUUID(), "King");
        KOMEEmergencyDefenseService.INSTANCE.anchorUnknownHistory(source, "mordor", 100L);
        NBTTagCompound schemaSeven = new NBTTagCompound(); source.writeToNBT(schemaSeven);
        schemaSeven.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 7);
        schemaSeven.removeTag(KOMEEmergencyDefensePersistence.SCHEMA_KEY);
        schemaSeven.removeTag(KOMEEmergencyDefensePersistence.RECORDS_KEY);

        KOMEWorldData migrated = new KOMEWorldData("migrated");
        migrated.readFromNBT(schemaSeven);
        KOMEEmergencyDefenseActivity activity =
            migrated.emergencyDefenseActivities.get("mordor");
        assertNotNull(activity);
        assertFalse(activity.hasKnownQualifyingHire());
        assertEquals(KOMEEmergencyDefenseActivity.UNKNOWN_HISTORY_ANCHOR,
            activity.updateSource);
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
