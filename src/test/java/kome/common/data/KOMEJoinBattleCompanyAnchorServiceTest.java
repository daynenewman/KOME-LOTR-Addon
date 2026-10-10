package kome.common.data;

import kome.common.KOMEAccessFixture;
import lotr.common.fac.LOTRFaction;
import lotr.common.entity.npc.LOTREntityRohirrimWarrior;
import net.minecraft.entity.Entity;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;
import static org.junit.Assert.*;

public class KOMEJoinBattleCompanyAnchorServiceTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    @After public void clearTokenState() {
        KOMEJoinBattleActionTokenService.INSTANCE.clearForTests();
    }

    @Test public void loadedExactMemberWinsWithoutAnyTargetedLoad() throws Exception {
        Fixture f = new Fixture(); Member member = f.addMember(id(1), point(0));
        FakeEnvironment environment = new FakeEnvironment();
        environment.loaded.put(member.id, member.entity);
        KOMEJoinBattleCompanyAnchorService service = service(environment);

        KOMEJoinBattleCompanyAnchorService.Resolution result = service.resolve(f.data,
            f.access.player, f.company.id, "T100", f.record.getConflictId(), f.record.getRevision());
        try {
            assertEquals(KOMEJoinBattleService.Reason.ALLOWED, result.reason);
            assertSame(member.entity, result.anchor);
            assertEquals(0, environment.loads.size());
        } finally { result.close(); }
    }

    @Test public void targetedLoadUsesExactLiveEntityCoordinatesAndReleasesTicket() throws Exception {
        Fixture f = new Fixture(); double[] locatorPoint = point(0);
        Member member = f.addMember(id(2), locatorPoint);
        double liveX = locatorPoint[0] + 0.375D, liveZ = locatorPoint[1] + 0.25D;
        place(member.entity, liveX, 71D, liveZ);
        FakeEnvironment environment = new FakeEnvironment();
        environment.publish(member, locatorPoint);
        KOMEJoinBattleCompanyAnchorService.Resolution result = service(environment).resolve(f.data,
            f.access.player, f.company.id, "T100", f.record.getConflictId(), f.record.getRevision());
        assertEquals(KOMEJoinBattleService.Reason.ALLOWED, result.reason);
        assertSame(member.entity, result.anchor);
        assertEquals(liveX, result.anchor.posX, 0D);
        assertNotEquals("Persisted diagnostic X cannot become the anchor",
            member.locator.getX(), result.anchor.posX, 0D);
        assertEquals(1, environment.loads.size());
        assertEquals(0, environment.closedLeases);
        result.close();
        assertEquals(1, environment.closedLeases);
    }

    @Test public void unknownAndUnverifiableLocationsHaveDistinctResults() throws Exception {
        Fixture unknown = new Fixture(); unknown.addMemberWithoutLocator(id(3));
        FakeEnvironment noLocations = new FakeEnvironment();
        KOMEJoinBattleCompanyAnchorService.Resolution absent = service(noLocations).resolve(
            unknown.data, unknown.access.player, unknown.company.id, "T100",
            unknown.record.getConflictId(), unknown.record.getRevision());
        assertEquals(KOMEJoinBattleService.Reason.COMPANY_LOCATION_UNKNOWN, absent.reason);
        assertEquals(0, noLocations.loads.size());

        Fixture stale = new Fixture(); stale.addMember(id(4), point(0));
        FakeEnvironment exactUuidAbsent = new FakeEnvironment();
        KOMEJoinBattleCompanyAnchorService.Resolution unavailable = service(exactUuidAbsent).resolve(
            stale.data, stale.access.player, stale.company.id, "T100",
            stale.record.getConflictId(), stale.record.getRevision());
        assertEquals(KOMEJoinBattleService.Reason.COMPANY_LOCATION_UNAVAILABLE, unavailable.reason);
        assertEquals(1, exactUuidAbsent.loads.size());
        assertEquals(1, exactUuidAbsent.closedLeases);
    }

    @Test public void postLoadVerificationRejectsWrongUuidDeathAndWrongPhysicalTile() throws Exception {
        Fixture wrongUuid = new Fixture(); Member expected = wrongUuid.addMember(id(5), point(0));
        FakeEnvironment wrongEnvironment = new FakeEnvironment();
        AnchorNpc impostor = npc(wrongUuid.access, UUID.randomUUID(), point(0));
        wrongEnvironment.publishAt(impostor, expected.locator);
        assertUnavailable(wrongUuid, wrongEnvironment);

        Fixture dead = new Fixture(); Member deadMember = dead.addMember(id(6), point(0));
        deadMember.entity.isDead = true;
        FakeEnvironment deadEnvironment = new FakeEnvironment(); deadEnvironment.publish(deadMember, point(0));
        assertUnavailable(dead, deadEnvironment);

        Fixture wrongTile = new Fixture(); Member displaced = wrongTile.addMember(id(7), point(0));
        double[] outside = differentTilePoint(); place(displaced.entity, outside[0], 70D, outside[1]);
        FakeEnvironment displacedEnvironment = new FakeEnvironment();
        displacedEnvironment.publish(displaced, point(0));
        assertUnavailable(wrongTile, displacedEnvironment);
    }

    @Test public void postLoadStrategicMutationReturnsAuthoritativeDenial() throws Exception {
        Fixture relinked = new Fixture(); Member member = relinked.addMember(id(8), point(0));
        FakeEnvironment environment = new FakeEnvironment(); environment.publish(member, point(0));
        environment.beforePublish = new Runnable() {
            @Override public void run() { member.record.companyId = "C-other"; }
        };
        KOMEJoinBattleCompanyAnchorService.Resolution result = service(environment).resolve(
            relinked.data, relinked.access.player, relinked.company.id, "T100",
            relinked.record.getConflictId(), relinked.record.getRevision());
        assertTrue(result.reason == KOMEJoinBattleService.Reason.COMPANY_INCOHERENT
            || result.reason == KOMEJoinBattleService.Reason.COMPANY_HAS_NO_CAMPAIGN_COMBAT_UNITS);
        assertNull(result.anchor);
        assertEquals(1, environment.closedLeases);

        Fixture ordinary = new Fixture(); Member ordinaryMember = ordinary.addMember(id(11), point(0));
        FakeEnvironment ordinaryEnvironment = new FakeEnvironment();
        ordinaryEnvironment.publish(ordinaryMember, point(0));
        ordinaryEnvironment.beforePublish = new Runnable() {
            @Override public void run() {
                ordinaryMember.record.assignPersistedUnitClass(KOMEHiredUnitClass.ORDINARY);
            }
        };
        KOMEJoinBattleCompanyAnchorService.Resolution ordinaryResult = service(ordinaryEnvironment).resolve(
            ordinary.data, ordinary.access.player, ordinary.company.id, "T100",
            ordinary.record.getConflictId(), ordinary.record.getRevision());
        assertEquals(KOMEJoinBattleService.Reason.COMPANY_HAS_NO_CAMPAIGN_COMBAT_UNITS,
            ordinaryResult.reason);
        assertEquals(1, ordinaryEnvironment.closedLeases);

        Fixture terminal = new Fixture(); Member terminalMember = terminal.addMember(id(12), point(0));
        FakeEnvironment terminalEnvironment = new FakeEnvironment();
        terminalEnvironment.publish(terminalMember, point(0));
        terminalEnvironment.beforePublish = new Runnable() {
            @Override public void run() { terminalMember.record.populationReturned = true; }
        };
        KOMEJoinBattleCompanyAnchorService.Resolution terminalResult = service(terminalEnvironment).resolve(
            terminal.data, terminal.access.player, terminal.company.id, "T100",
            terminal.record.getConflictId(), terminal.record.getRevision());
        assertTrue(terminalResult.reason == KOMEJoinBattleService.Reason.COMPANY_INCOHERENT
            || terminalResult.reason == KOMEJoinBattleService.Reason.COMPANY_HAS_NO_CAMPAIGN_COMBAT_UNITS);
        assertEquals(1, terminalEnvironment.closedLeases);

        Fixture rekeyed = new Fixture(); Member old = rekeyed.addMember(id(9), point(0));
        UUID replacement = id(10);
        rekeyed.data.hiredUnits.remove(old.id); old.record.entity = replacement;
        rekeyed.data.hiredUnits.put(replacement, old.record);
        rekeyed.company.units.set(0, replacement);
        FakeEnvironment rekeyEnvironment = new FakeEnvironment();
        KOMEJoinBattleCompanyAnchorService.Resolution unknown = service(rekeyEnvironment).resolve(
            rekeyed.data, rekeyed.access.player, rekeyed.company.id, "T100",
            rekeyed.record.getConflictId(), rekeyed.record.getRevision());
        assertEquals("Old UUID locator must not be trusted after a rekey",
            KOMEJoinBattleService.Reason.COMPANY_LOCATION_UNKNOWN, unknown.reason);
    }

    @Test public void candidatesAreUuidOrderedGroupedAndBoundedToFourUniqueChunks() throws Exception {
        Fixture grouped = new Fixture(); double[] shared = point(0);
        grouped.addMember(id(20), shared); grouped.addMember(id(19), shared);
        FakeEnvironment groupedEnvironment = new FakeEnvironment();
        KOMEJoinBattleCompanyAnchorService.Resolution groupedResult = service(groupedEnvironment).resolve(
            grouped.data, grouped.access.player, grouped.company.id, "T100",
            grouped.record.getConflictId(), grouped.record.getRevision());
        assertEquals(KOMEJoinBattleService.Reason.COMPANY_LOCATION_UNAVAILABLE, groupedResult.reason);
        assertEquals("Two locators in one chunk are one attempt", 1, groupedEnvironment.loads.size());

        Fixture bounded = new Fixture(); List<double[]> points = distinctChunkPoints(5);
        // Deliberately insert reverse UUID order; resolution must use lexical UUID order.
        for (int i = 0; i < 5; i++) bounded.addMember(id(40 - i), points.get(i));
        FakeEnvironment boundedEnvironment = new FakeEnvironment();
        KOMEJoinBattleCompanyAnchorService.Resolution boundedResult = service(boundedEnvironment).resolve(
            bounded.data, bounded.access.player, bounded.company.id, "T100",
            bounded.record.getConflictId(), bounded.record.getRevision());
        assertEquals(KOMEJoinBattleService.Reason.COMPANY_LOCATION_UNAVAILABLE, boundedResult.reason);
        assertEquals(4, boundedEnvironment.loads.size());
        List<String> expected = new ArrayList<String>();
        for (int index : new int[] {4, 3, 2, 1}) expected.add(address(points.get(index)));
        assertEquals(expected, boundedEnvironment.loads);
        assertEquals(4, boundedEnvironment.closedLeases);
    }

    @Test public void everyFailureOrExceptionReleasesTicket() throws Exception {
        Fixture f = new Fixture(); f.addMember(id(50), point(0)); f.addMember(id(51), point(1));
        FakeEnvironment environment = new FakeEnvironment(); environment.throwAfterLease = true;
        KOMEJoinBattleCompanyAnchorService.Resolution result = service(environment).resolve(
            f.data, f.access.player, f.company.id, "T100",
            f.record.getConflictId(), f.record.getRevision());
        assertEquals(KOMEJoinBattleService.Reason.COMPANY_LOCATION_UNAVAILABLE, result.reason);
        assertEquals(environment.loads.size(), environment.closedLeases);
    }

    @Test public void gateSerializesAndRateLimitsPerPlayerWithoutPersistence() {
        KOMEJoinBattleCompanyAnchorService.AttemptGate gate =
            new KOMEJoinBattleCompanyAnchorService.AttemptGate();
        UUID player = UUID.randomUUID();
        KOMEJoinBattleCompanyAnchorService.AttemptGate.Lease first = gate.acquire(player, 1000L);
        assertNotNull(first);
        assertNull("Concurrent attempt must be denied", gate.acquire(player, 1000L));
        first.close();
        assertNull("Five-second transient cooldown must prevent spam", gate.acquire(player, 5999L));
        KOMEJoinBattleCompanyAnchorService.AttemptGate.Lease later = gate.acquire(player, 6000L);
        assertNotNull(later); later.close();
        gate.clear();
        assertNotNull("Transient protection can be cleared/restarted without durable state",
            gate.acquire(player, 6000L));
    }

    @Test public void schemaVersionsRemainOwnedByTheirExistingAuthorities() {
        assertEquals(12, KOMEWorldData.KOME_DATA_SCHEMA_VERSION);
        assertEquals(2, KOMEConflictPersistence.DATA_SCHEMA_VERSION);
        assertEquals(1, KOMEHiredUnitPhysicalLocator.DATA_SCHEMA_VERSION);
    }

    private static KOMEJoinBattleCompanyAnchorService service(FakeEnvironment environment) {
        return new KOMEJoinBattleCompanyAnchorService(environment);
    }

    private static void assertUnavailable(Fixture fixture, FakeEnvironment environment) {
        KOMEJoinBattleCompanyAnchorService.Resolution result = service(environment).resolve(
            fixture.data, fixture.access.player, fixture.company.id, "T100",
            fixture.record.getConflictId(), fixture.record.getRevision());
        assertEquals(KOMEJoinBattleService.Reason.COMPANY_LOCATION_UNAVAILABLE, result.reason);
        assertNull(result.anchor); assertEquals(environment.loads.size(), environment.closedLeases);
    }

    private static final class FakeEnvironment
            implements KOMEJoinBattleCompanyAnchorService.Environment {
        final Map<UUID, Entity> loaded = new HashMap<UUID, Entity>();
        final Map<String, List<Entity>> publish = new HashMap<String, List<Entity>>();
        final List<String> loads = new ArrayList<String>();
        int closedLeases; long now = 1000L; boolean throwAfterLease; Runnable beforePublish;
        @Override public long now() { return now; }
        @Override public Entity findLoaded(UUID entityId) { return loaded.get(entityId); }
        @Override public KOMEJoinBattleCompanyAnchorService.ChunkLease load(
                int dimensionId, int chunkX, int chunkZ) {
            String address = address(dimensionId, chunkX, chunkZ); loads.add(address);
            final boolean[] closed = {false};
            KOMEJoinBattleCompanyAnchorService.ChunkLease lease =
                new KOMEJoinBattleCompanyAnchorService.ChunkLease() {
                    @Override public void close() { if (!closed[0]) { closed[0] = true; closedLeases++; } }
                };
            if (beforePublish != null) { Runnable callback = beforePublish; beforePublish = null; callback.run(); }
            List<Entity> entities = publish.get(address);
            if (entities != null) for (Entity entity : entities)
                loaded.put(entity.getUniqueID(), entity);
            if (throwAfterLease) {
                lease.close();
                throw new IllegalStateException("simulated chunk hook failure");
            }
            return lease;
        }
        void publish(Member member, double[] locatorPoint) {
            publishAt(member.entity, member.locator);
        }
        void publishAt(Entity entity, KOMEHiredUnitPhysicalLocator locator) {
            String key = address(locator.getDimensionId(), locator.getChunkX(), locator.getChunkZ());
            List<Entity> entities = publish.get(key);
            if (entities == null) { entities = new ArrayList<Entity>(); publish.put(key, entities); }
            entities.add(entity);
        }
    }

    private static final class Fixture {
        final KOMEAccessFixture access; final KOMEWorldData data; final KOMEArmyCompany company;
        KOMEConflictRecord record; long time = 10L;
        Fixture() throws Exception {
            access = new KOMEAccessFixture(); data = access.data;
            access.world.provider.dimensionId = KOMETileTestResources.dimension();
            access.pledge(LOTRFaction.GONDOR);
            company = new KOMEArmyCompany(); company.id = "C1"; company.name = "Company";
            company.owner = UUID.randomUUID(); company.ownerName = "Owner";
            company.faction = "gondor"; company.currentTile = "T100";
            data.lastKnownPlayerFactions.put(company.owner, "gondor");
            data.armyCompanies.put(company.id, company);
            record = ok(data.getConflictService().start("T100", KOMEConflictRecord.State.ORDINARY,
                ExpectedConflict.absent(), Collections.<GarrisonSeed>emptyList(), context()));
            record = ok(data.getConflictService().beginFactionParticipation("T100", expected(),
                "gondor", context()));
            record = ok(data.getConflictService().commit("T100", expected(),
                new CommitmentInput(company.id, KOMEHiredUnitClass.CAMPAIGN,
                    EntryOrigin.LEGAL_ARRIVAL, "M-C1"), context()));
        }
        Member addMember(UUID id, double[] coordinates) throws Exception {
            Member member = addMemberWithoutLocator(id);
            place(member.entity, coordinates[0], 70D, coordinates[1]);
            KOMEHiredUnitPhysicalLocator locator = KOMEHiredUnitPhysicalLocator.verified(id,
                KOMETileTestResources.dimension(), coordinates[0], 70D, coordinates[1],
                "T100", 100L, KOMEHiredUnitPhysicalLocator.CaptureKind.CHUNK_UNLOAD);
            member.record.replacePhysicalLocator(locator);
            return new Member(id, member.record, member.entity, locator);
        }
        Member addMemberWithoutLocator(UUID id) throws Exception {
            KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord(); unit.entity = id;
            unit.owner = company.owner; unit.sourcePlayer = company.owner;
            unit.companyId = company.id; unit.companyName = company.name;
            unit.currentTile = "T100"; unit.sourceTileId = "T100";
            unit.unitFaction = "gondor"; unit.populationOwningFaction = "gondor";
            unit.type = KOMEPopulationType.OFFENSIVE; unit.cost = unit.baseCost = unit.populationSpent = 20;
            unit.assignPersistedUnitClass(KOMEHiredUnitClass.CAMPAIGN);
            AnchorNpc entity = npc(access, id, point(0));
            company.units.add(id); company.totalPopulation += 20; company.groundPopulation += 20;
            data.hiredUnits.put(id, unit);
            return new Member(id, unit, entity, null);
        }
        ExpectedConflict expected() { return ExpectedConflict.at(record.getConflictId(), record.getRevision()); }
        Context context() { return new Context(time++, "test", "targeted Join Battle fixture"); }
    }

    private static final class Member {
        final UUID id; final KOMEHiredUnitRecord record; final AnchorNpc entity;
        final KOMEHiredUnitPhysicalLocator locator;
        Member(UUID id, KOMEHiredUnitRecord record, AnchorNpc entity,
                KOMEHiredUnitPhysicalLocator locator) {
            this.id = id; this.record = record; this.entity = entity; this.locator = locator;
        }
    }

    public static final class AnchorNpc extends LOTREntityRohirrimWarrior {
        UUID id; boolean alive;
        private AnchorNpc() { super(null); }
        @Override public UUID getUniqueID() { return id; }
        @Override public boolean isEntityAlive() { return alive && !isDead; }
        @Override public LOTRFaction getFaction() { return LOTRFaction.GONDOR; }
    }

    private static AnchorNpc npc(KOMEAccessFixture fixture, UUID id, double[] position)
            throws Exception {
        AnchorNpc npc = KOMEAccessFixture.allocate(AnchorNpc.class); npc.id = id; npc.alive = true;
        npc.setUniqueID(id); npc.worldObj = fixture.world; place(npc, position[0], 70D, position[1]);
        return npc;
    }

    private static void place(Entity entity, double x, double y, double z) {
        entity.posX = x; entity.posY = y; entity.posZ = z;
    }

    private static KOMEConflictRecord ok(KOMEConflictService.Result result) {
        assertTrue(result.code + ": " + result.reason, result.isSuccess()); return result.record;
    }

    private static UUID id(int value) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", value));
    }

    private static double[] point(int index) {
        return distinctChunkPoints(index + 1).get(index);
    }

    private static List<double[]> distinctChunkPoints(int count) {
        List<double[]> result = new ArrayList<double[]>();
        int baseX = KOMETileTestResources.x(), baseZ = KOMETileTestResources.z();
        for (int radius = 0; radius <= 1024 && result.size() < count; radius += 16) {
            for (int sign : new int[] {1, -1}) {
                double x = baseX + sign * radius + 0.5D, z = baseZ + 0.5D;
                KOMETileResolution resolved = KOMETileWorldResolver.INSTANCE.resolveWorldPosition(
                    KOMETileTestResources.dimension(), x, z);
                if (resolved.status != KOMETileResolution.Status.RESOLVED
                        || !"T100".equals(resolved.tileId)) continue;
                String address = address(KOMETileTestResources.dimension(), floor(x) >> 4, floor(z) >> 4);
                boolean duplicate = false;
                for (double[] existing : result) if (address(existing).equals(address)) duplicate = true;
                if (!duplicate) result.add(new double[] {x, z});
                if (result.size() == count) break;
            }
        }
        if (result.size() != count) throw new AssertionError("T100 lacks " + count + " fixture chunks");
        return result;
    }

    private static double[] differentTilePoint() {
        KOMETileRasterSnapshot map = KOMETileTestResources.real();
        int[] pixels = map.copyArgbPixels();
        for (int pixel = 0; pixel < pixels.length; pixel++) {
            double x = KOMETileTestResources.worldX(pixel % map.width) + 0.5D;
            double z = KOMETileTestResources.worldZ(pixel / map.width) + 0.5D;
            KOMETileResolution resolved = KOMETileWorldResolver.INSTANCE.resolveWorldPosition(
                KOMETileTestResources.dimension(), x, z);
            if (resolved.status == KOMETileResolution.Status.RESOLVED
                    && !"T100".equals(resolved.tileId)) return new double[] {x, z};
        }
        throw new AssertionError("No alternate tile point");
    }

    private static String address(double[] point) {
        return address(KOMETileTestResources.dimension(), floor(point[0]) >> 4, floor(point[1]) >> 4);
    }
    private static String address(int dimension, int chunkX, int chunkZ) {
        return dimension + ":" + chunkX + ":" + chunkZ;
    }
    private static int floor(double value) { return (int) Math.floor(value); }
}
