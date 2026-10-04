package kome.common.data;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Hashtable;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.common.DimensionManager;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEConflictContracts.*;

/** Real canonical conflict/persistence services; entity delivery is explicitly unavailable. */
public class KOMEMusterArrivalAuthorityTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private KOMEWorldData world() {
        KOMEWorldData data = new KOMEWorldData("muster-arrival"); data.initializeIntegratedWorld();
        data.warSeason.phase = KOMEWarSeasonState.Phase.WAR;
        return data;
    }
    private KOMEMusterRecord reserve(KOMEWorldData data) {
        BigInteger rate = BigInteger.valueOf(20L * KOMEPopulationRate.SCALE);
        KOMEMusterRoster.Unit unit = new KOMEMusterRoster.Unit("fixture", "gondor", "fixture-native", "", 20, 1);
        KOMEMusterRecord record = new KOMEMusterRecord("gondor", 1, UUID.randomUUID(), 0, 3600000, 42,
            KOMEFactionCapitalService.getCapital(data, "gondor"), rate, 1, 1,
            KOMEMusterRoster.select("gondor", rate, 42, Collections.singletonList(unit)));
        data.civilianMusters.put(record.key(), record); return record;
    }

    @Test public void canonicalConflictAndOwnershipProduceClearEncircledOrUnknown() {
        KOMEWorldData data = world(); KOMEFactionCapitalRecord capital = KOMEFactionCapitalService.getCapital(data, "gondor");
        KOMEMusterArrivalAuthority authority = KOMEMusterArrivalAuthority.INSTANCE;
        assertEquals(KOMEMusterService.CapitalState.CLEAR, authority.capitalState(data, capital));
        assertEquals(KOMEMusterService.CapitalState.UNKNOWN, authority.capitalState(new KOMEWorldData("uninitialized"), capital));
        data.conquestTiles.get(capital.getCapitalTileId()).claim("mordor", 0);
        assertEquals(KOMEMusterService.CapitalState.UNKNOWN, authority.capitalState(data, capital));
        data.conquestTiles.get(capital.getCapitalTileId()).claim("gondor", 0);
        assertTrue(data.getConflictService().start(capital.getCapitalTileId(), KOMEConflictRecord.State.ENCIRCLEMENT,
            ExpectedConflict.absent(), Collections.<GarrisonSeed>emptyList(), new Context(1, "fixture", "canonical encirclement")).isSuccess());
        assertEquals(KOMEMusterService.CapitalState.ENCIRCLED, authority.capitalState(data, capital));
        KOMEWorldData ordinary = world();
        ordinary.getConflictService().start(capital.getCapitalTileId(), KOMEConflictRecord.State.ORDINARY,
            ExpectedConflict.absent(), Collections.<GarrisonSeed>emptyList(), new Context(1, "fixture", "canonical ordinary battle"));
        assertEquals(KOMEMusterService.CapitalState.UNKNOWN, authority.capitalState(ordinary, capital));
    }

    @Test public void nonLoadingReadinessKeepsMissingWorldChunkAndForceAuthorityExplicit() throws Exception {
        KOMEWorldData data = world(); KOMEMusterRecord record = reserve(data);
        Field registry = DimensionManager.class.getDeclaredField("worlds"); registry.setAccessible(true);
        Object before = registry.get(null);
        try {
            Hashtable<Integer, WorldServer> worlds = new Hashtable<Integer, WorldServer>(); registry.set(null, worlds);
            assertTrue(KOMEMusterArrivalAuthority.INSTANCE.deliveryBlockReason(data, record).startsWith("DEPLOYMENT_DIMENSION_UNAVAILABLE"));
            InspectionWorld loaded = kome.common.KOMEAccessFixture.allocate(InspectionWorld.class);
            Field provider = World.class.getDeclaredField("provider"); provider.setAccessible(true);
            provider.set(loaded, kome.common.KOMEAccessFixture.allocate(WorldProviderSurface.class));
            loaded.provider.dimensionId = record.capital.getDeploymentDimensionId(); worlds.put(loaded.provider.dimensionId, loaded);
            assertTrue(KOMEMusterArrivalAuthority.INSTANCE.deliveryBlockReason(data, record).startsWith("DEPLOYMENT_CHUNK_UNAVAILABLE"));
            loaded.loaded = true;
            assertEquals(KOMEMusterArrivalAuthority.FORCE_UNAVAILABLE, KOMEMusterArrivalAuthority.INSTANCE.deliveryBlockReason(data, record));
            assertEquals(0, KOMEMusterService.processDue(data, record.dueAtMillis));
            assertEquals(KOMEMusterArrivalAuthority.FORCE_UNAVAILABLE, record.getPendingReason());
            int audits = data.centralAudit.size();
            assertEquals(0, KOMEMusterService.processDue(data, record.dueAtMillis + 1));
            assertEquals(audits, data.centralAudit.size());
            assertTrue(data.hiredUnits.isEmpty()); assertTrue(data.armyCompanies.isEmpty());
            assertEquals(0L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        } finally { registry.set(null, before); }
    }

    @Test public void encircledPendingSnapshotAndOldSeasonReserveSurviveRestartWithoutReroll() {
        KOMEWorldData data = world(); KOMEMusterRecord record = reserve(data);
        data.getConflictService().start(record.capital.getCapitalTileId(), KOMEConflictRecord.State.ENCIRCLEMENT,
            ExpectedConflict.absent(), Collections.<GarrisonSeed>emptyList(), new Context(1, "fixture", "encircled capital"));
        assertEquals(0, KOMEMusterService.processDue(data, record.dueAtMillis));
        assertTrue(record.getPendingReason().startsWith("ENCIRCLED_CAPITAL_POLICY_TBD"));
        NBTTagCompound nbt = new NBTTagCompound(); data.writeToNBT(nbt);
        KOMEWorldData loaded = new KOMEWorldData("restart"); loaded.readFromNBT(nbt);
        assertEquals(record.writeToNBT(), loaded.civilianMusters.get(record.key()).writeToNBT());
        loaded.warSeason.seasonId++;
        assertEquals(0, KOMEMusterService.processDue(loaded, record.dueAtMillis + 1));
        KOMEMusterRecord retained = loaded.civilianMusters.get(record.key());
        assertEquals("SEASON_POLICY_TBD", retained.getPendingReason());
        assertEquals(record.seed, retained.seed); assertEquals(record.spentUnits, retained.spentUnits);
        assertEquals(record.rosterSummary(), retained.rosterSummary()); assertEquals(record.dueAtMillis, retained.dueAtMillis);
    }

    @Test public void uncertainPartialAttemptNeverAutomaticallyRetriesThroughProductionAdapter() {
        KOMEWorldData data = world(); KOMEMusterRecord record = reserve(data);
        record.pending("DEPLOYMENT_CONFIRMATION_REQUIRED");
        NBTTagCompound saved = new NBTTagCompound(); data.writeToNBT(saved);
        KOMEWorldData loaded = new KOMEWorldData("uncertain"); loaded.readFromNBT(saved);
        assertEquals(0, KOMEMusterService.processDue(loaded, record.dueAtMillis));
        assertEquals(record.writeToNBT(), loaded.civilianMusters.get(record.key()).writeToNBT());
    }

    private static final class InspectionWorld extends WorldServer {
        boolean loaded;
        private InspectionWorld() { super((MinecraftServer)null, (ISaveHandler)null, "unused", 0, (WorldSettings)null, (Profiler)null); }
        @Override protected IChunkProvider createChunkProvider() { throw new AssertionError("Cannot create provider"); }
        @Override protected int func_152379_p() { return 0; }
        @Override public net.minecraft.entity.Entity getEntityByID(int id) { return null; }
        @Override public IChunkProvider getChunkProvider() {
            return (IChunkProvider)Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IChunkProvider.class},
                (proxy, method, args) -> { if (method.getName().equals("chunkExists")) return loaded;
                    throw new AssertionError("Must not load/provide chunk: " + method.getName()); });
        }
    }
}
