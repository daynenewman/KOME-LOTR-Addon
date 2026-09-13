package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class KOMEAuditServiceTest {
    @Test public void boundedStructuredAuditPersistsAndSummarizesDeterministically() {
        KOMEWorldData data = new KOMEWorldData("test");
        for (int i = 0; i < KOMEAuditService.MAX_ENTRIES + 3; i++)
            KOMEAuditService.record(data, i, "TEST", i % 2 == 0 ? "EVEN" : "ODD", "actor", "subject-" + i, "reason", "detail");
        assertEquals(KOMEAuditService.MAX_ENTRIES, KOMEAuditService.entries(data).size());
        assertEquals(3L, KOMEAuditService.entries(data).get(0).timestamp);
        NBTTagCompound nbt = new NBTTagCompound(); data.writeToNBT(nbt);
        KOMEWorldData restored = new KOMEWorldData("test"); restored.readFromNBT(nbt);
        assertEquals(KOMEAuditService.MAX_ENTRIES, KOMEAuditService.entries(restored).size());
        assertEquals("reason", KOMEAuditService.entries(restored).get(0).reason);
        List<String> summary = KOMEAuditService.summary(restored);
        assertEquals(2, summary.size()); assertEquals("TEST/ODD=250", summary.get(0)); assertEquals("TEST/EVEN=250", summary.get(1));
    }

    @Test public void emptyReasonIsRejectedAndNotificationFailuresDoNotEscape() {
        KOMEWorldData data = new KOMEWorldData("test");
        assertNull(KOMEAuditService.record(data, 1L, "TEST", "ACTION", "", "", "", ""));
        final int[] calls = new int[] {0};
        KOMENotificationService.setExternalSink(new KOMENotificationService.Sink() {
            public void send(String message) { calls[0]++; throw new RuntimeException("offline"); }
        });
        KOMENotificationService.timeSensitive("event");
        assertEquals(1, calls[0]);
        KOMENotificationService.setExternalSink(null);
    }

    @Test public void warRepairIsIdempotentAndAmbiguityFailsClosed() {
        KOMEWorldData data = new KOMEWorldData("test"); KOMEWar war = new KOMEWar(); war.id = "w";
        war.sideOneFactions.add(" Rohan "); war.sideOneFactions.add("rohan"); data.wars.put(war.id, war);
        int before = data.centralAudit.size();
        KOMEWarService.RepairResult first = KOMEWarService.repairConsistency(data, war, 10L);
        assertTrue(first.allowed); assertTrue(first.changed); assertEquals(before + 1, data.centralAudit.size());
        KOMEWarService.RepairResult second = KOMEWarService.repairConsistency(data, war, 11L);
        assertTrue(second.allowed); assertFalse(second.changed); assertEquals(before + 1, data.centralAudit.size());
        war.sideTwoFactions.add("rohan");
        KOMEWarService.RepairResult ambiguous = KOMEWarService.repairConsistency(data, war, 12L);
        assertFalse(ambiguous.allowed); assertFalse(ambiguous.reason.isEmpty());
    }

    @Test public void warRepairPreservesExplicitActorIdentity() {
        KOMEWorldData data = new KOMEWorldData("test"); KOMEWar war = new KOMEWar(); war.id = "actor-war";
        war.sideOneFactions.add(" Rohan "); data.wars.put(war.id, war);
        KOMEWarService.RepairResult result = KOMEWarService.repairConsistency(data, war, 10L, "Alice");
        assertTrue(result.changed);
        assertEquals("Alice", data.centralAudit.get(data.centralAudit.size() - 1).actor);
    }

    @Test public void movementAndBuildMutationAuditsOnlyRecordTransitions() {
        KOMEWorldData data = new KOMEWorldData("test"); KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
        order.id = "move"; order.companyId = "company"; order.owner = java.util.UUID.randomUUID();
        data.recordMovementStarted(order); int afterStart = data.centralAudit.size();
        data.recordMovementStarted(order); assertEquals(afterStart, data.centralAudit.size());
        data.updateMovementHistory(order, KOMEArmyMovementOrder.ARRIVED); assertTrue(data.centralAudit.size() > afterStart);
        int beforeStopped = data.centralAudit.size();
        data.markMovementHistoryStopped(order, order.owner, "Manager", 22L);
        assertEquals(beforeStopped + 1, data.centralAudit.size());
        data.markMovementHistoryStopped(order, order.owner, "Manager", 23L);
        assertEquals(beforeStopped + 1, data.centralAudit.size());
        KOMEPlayerBuild build = new KOMEPlayerBuild(); build.id = "build"; build.active = true; build.managerUuid = order.owner; build.type = KOMEBuildType.NORMAL;
        KOMEBuildContribution contribution = KOMEBuildService.addSubmission(data, build, java.util.UUID.randomUUID(), "Builder", "rohan", 2, false, 20L);
        data.builds.put(build.id, build); int beforeApprove = data.centralAudit.size();
        assertTrue(KOMEBuildService.decideSubmission(data, build, contribution.id, order.owner, "Manager", true, true, "approved", 21L).allowed);
        assertTrue(data.centralAudit.size() > beforeApprove);
        assertFalse(KOMEBuildService.decideSubmission(data, build, "missing", order.owner, "Manager", true, true, "", 22L).allowed);
    }

    @Test public void readOnlyMovementSynchronizationDoesNotAuditMissingCompletedRows() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder(); order.id = "history"; order.status = KOMEArmyMovementOrder.ARRIVED;
        data.syncMovementHistory(order, KOMEMovementHistoryRecord.ARRIVED);
        assertEquals(0, data.centralAudit.size());
    }

    @Test public void failedMovementAuditIncludesCanonicalFailureReason() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder(); order.id = "failed"; order.companyId = "company";
        order.pendingSpawnReason = "pending reason"; order.lastSpawnFailureDetails = "fallback reason";
        data.updateMovementHistory(order, KOMEMovementHistoryRecord.FAILED);
        assertEquals("pending reason", data.centralAudit.get(data.centralAudit.size() - 1).details.split(";failure=", 2)[1]);
    }

    @Test public void buildEmptyReasonsUseStableAuditFallbacks() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = new KOMEPlayerBuild(); build.id = "build-empty"; build.active = true; build.type = KOMEBuildType.NORMAL;
        build.managerUuid = java.util.UUID.randomUUID(); data.builds.put(build.id, build);
        KOMEBuildContribution contribution = KOMEBuildService.addSubmission(data, build, java.util.UUID.randomUUID(), "Builder", "rohan", 2, false, 1L);
        assertTrue(KOMEBuildService.decideSubmission(data, build, contribution.id, build.managerUuid, "Manager", true, true, "", 2L).allowed);
        KOMEAuditEntry approval = data.centralAudit.get(data.centralAudit.size() - 1);
        assertFalse(approval.reason.isEmpty());
        assertTrue(KOMEBuildService.removeApprovedContribution(data, build, contribution.id, build.managerUuid, "Manager", "", 3L).allowed);
        KOMEAuditEntry removal = data.centralAudit.get(data.centralAudit.size() - 1);
        assertFalse(removal.reason.isEmpty());
    }

    @Test public void stewardshipRevalidationFingerprintIgnoresReasonButTracksState() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEArmyCompany company = new KOMEArmyCompany(); company.id = "company"; company.nativeFaction = "rohan";
        java.util.UUID controller = java.util.UUID.randomUUID(); company.temporaryController = controller;
        company.controllerAuthority = KOMEArmyCompany.AUTHORITY_STEWARDSHIP;
        data.recordCompanyDelegationAudit(1L, "STEWARDSHIP_REVALIDATED", company, controller, "Ruler", controller, "Ruler", "first");
        int afterFirst = data.centralAudit.size();
        data.recordCompanyDelegationAudit(2L, "STEWARDSHIP_REVALIDATED", company, controller, "Ruler", controller, "Ruler", "different reason");
        assertEquals(afterFirst, data.centralAudit.size());
        company.authorizedWarIds.add("war-2");
        data.recordCompanyDelegationAudit(3L, "STEWARDSHIP_REVALIDATED", company, controller, "Ruler", controller, "Ruler", "changed state");
        assertEquals(afterFirst + 1, data.centralAudit.size());
    }

    @Test public void touchedDenialsHaveReasons() {
        KOMEWorldData data = new KOMEWorldData("test");
        assertFalse(KOMEWarService.repairConsistency(data, null, 1L).reason.isEmpty());
        assertFalse(KOMEWartimeStewardshipService.reconcileDefensiveUnits(data, "", 1L).reason.isEmpty());
        assertFalse(KOMEBuildService.canPlace(data, "rohan", "missing", "rohan").reason.isEmpty());
        assertFalse(KOMEDiplomacyService.requestIncrease(data, "rohan", "gondor", KOMEDiplomacyRelation.FRIENDS, null, 1L).reason.isEmpty());
    }

    @Test public void minecraftBroadcastIsIndependentFromExternalSink() {
        final int[] minecraft = new int[] {0};
        KOMENotificationService.setMinecraftBroadcaster(new KOMENotificationService.MinecraftBroadcaster() {
            public void broadcast(String message) { minecraft[0]++; }
        });
        KOMENotificationService.setExternalSink(new KOMENotificationService.Sink() { public void send(String message) { throw new RuntimeException("offline"); } });
        KOMENotificationService.global("notice");
        assertEquals(1, minecraft[0]);
        KOMENotificationService.setExternalSink(null); KOMENotificationService.resetMinecraftBroadcaster();
    }
}
