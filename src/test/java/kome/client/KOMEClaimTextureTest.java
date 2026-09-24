package kome.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEConquestTile;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEClaimTextureTest {
    @Test public void identicalEffectiveColorsSkipAndActualChangesRebuildExactly() {
        KOMEConquestMapOverlay.ClaimTextureState state = new KOMEConquestMapOverlay.ClaimTextureState();
        List<Map<Integer,Integer>> uploads = new ArrayList<>();
        Map<Integer,Integer> colors = new HashMap<>(); colors.put(1, 0x112233);
        assertTrue(state.update(colors, uploads::add));
        for(int i=0;i<100;i++) assertFalse(state.update(new HashMap<>(colors), uploads::add));
        colors.put(1, 0x445566); assertTrue(state.update(colors, uploads::add));
        assertEquals(Integer.valueOf(0x112233), uploads.get(0).get(1));
        assertEquals(Integer.valueOf(0x445566), uploads.get(1).get(1));
        colors.clear(); assertTrue(state.update(colors, uploads::add));
        assertTrue(uploads.get(2).isEmpty()); // Visibility removal must clear old territory.
        assertEquals(3, uploads.size());
    }

    @Test public void failedUploadIsNotCachedAndInputsCannotMutatePublishedColors() {
        KOMEConquestMapOverlay.ClaimTextureState state = new KOMEConquestMapOverlay.ClaimTextureState();
        Map<Integer,Integer> colors = Collections.singletonMap(1, 2);
        try {state.update(colors, c -> {throw new IllegalStateException("upload");});fail();}
        catch(IllegalStateException expected) { }
        assertTrue(state.update(colors, c -> {
            try {c.put(3,4);fail();} catch(UnsupportedOperationException expected) { }
        }));
        assertFalse(state.update(colors, c -> fail()));
    }

    @Test public void productionReloadDisconnectAndGeometryClearInvalidateColorCache() throws Exception {
        Field cache = field("claimTextureState");
        KOMEConquestMapOverlay.ClaimTextureState state=(KOMEConquestMapOverlay.ClaimTextureState)cache.get(null);
        Map<Integer,Integer> colors=Collections.singletonMap(1,2);
        Method clear=KOMEConquestMapOverlay.class.getDeclaredMethod("clearTileGeometry");clear.setAccessible(true);
        for(Runnable reset : Arrays.<Runnable>asList(
            () -> KOMEConquestMapOverlay.resetClientMapState(),
            () -> new KOMEConquestMapOverlay().onResourceManagerReload(null),
            () -> {try {clear.invoke(null);}catch(Exception e){throw new AssertionError(e);}})) {
            state.update(colors,c -> {});assertFalse(state.update(colors,c -> fail()));
            reset.run();assertTrue(state.update(colors,c -> {}));
            assertEquals(-1,field("renderedClaimRevision").getInt(null));
        }
        state.clear();
    }

    @Test public void colorProjectionUsesOnlyVisibleKnownClaimedTilesAndIgnoresUnrelatedChanges() throws Exception {
        KOMEClientData client=KOMEClientData.INSTANCE;
        Map saved=new HashMap(client.conquestTiles); int revision=client.conquestRevision;
        Map<String,Integer> ids=(Map<String,Integer>)field("tileColorsById").get(null);
        Map<String,Integer> savedIds=new HashMap<>(ids);
        try {
            ids.clear();ids.put("T001",1);ids.put("T002",2);client.conquestTiles.clear();
            KOMEConquestTile visible=new KOMEConquestTile("T001");visible.claim("angmar",0L);
            client.conquestTiles.put(visible.id,visible);
            Map<Integer,Integer> first=KOMEConquestMapOverlay.effectiveClaimColors();
            assertEquals(1,first.size());
            client.conquestRevision++; // Other conquest summaries must not alter colors.
            assertEquals(first,KOMEConquestMapOverlay.effectiveClaimColors());
            KOMEConquestTile unknown=new KOMEConquestTile("UNKNOWN");unknown.claim("gondor",0L);
            client.conquestTiles.put(unknown.id,unknown);
            client.conquestTiles.put("T002",new KOMEConquestTile("T002"));
            assertEquals(first,KOMEConquestMapOverlay.effectiveClaimColors());
            visible.claim("gondor",0L);assertNotEquals(first,KOMEConquestMapOverlay.effectiveClaimColors());
            client.conquestTiles.remove("T001");assertTrue(KOMEConquestMapOverlay.effectiveClaimColors().isEmpty());
        } finally {client.conquestTiles.clear();client.conquestTiles.putAll(saved);ids.clear();ids.putAll(savedIds);client.conquestRevision=revision;}
    }
    private static Field field(String name)throws Exception {Field f=KOMEConquestMapOverlay.class.getDeclaredField(name);f.setAccessible(true);return f;}
}
