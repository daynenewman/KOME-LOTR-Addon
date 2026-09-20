package kome.common.data;

import kome.common.command.KOMECommandTroops;
import lotr.common.LOTRDimension;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

/** Golden outputs captured from the original production inference, before replacing it. */
public class KOMETileGameplayParityTest {
    private int oldDimension, oldWidth, oldHeight;
    private Object oldBiomeData;
    private Optional<KOMETileRasterSnapshot> oldSnapshot;
    @Before public void retainEnvironment() throws Exception {
        oldDimension = LOTRDimension.MIDDLE_EARTH.dimensionID;
        oldWidth = LOTRGenLayerWorld.imageWidth; oldHeight = LOTRGenLayerWorld.imageHeight;
        Field f = LOTRGenLayerWorld.class.getDeclaredField("biomeImageData"); f.setAccessible(true); oldBiomeData = f.get(null);
        oldSnapshot = KOMETileWorldResolver.INSTANCE.snapshot();
    }
    @After public void restoreEnvironment() throws Exception {
        LOTRDimension.MIDDLE_EARTH.dimensionID = oldDimension;
        LOTRGenLayerWorld.imageWidth = oldWidth; LOTRGenLayerWorld.imageHeight = oldHeight;
        Field f = LOTRGenLayerWorld.class.getDeclaredField("biomeImageData"); f.setAccessible(true); f.set(null, oldBiomeData);
        if (oldSnapshot.isPresent()) KOMETileWorldResolver.INSTANCE.publish(oldSnapshot.get());
        else KOMETileWorldResolver.INSTANCE.invalidate();
    }
    static void prepare() throws Exception {
        LOTRDimension.MIDDLE_EARTH.dimensionID = 100;
        LOTRGenLayerWorld.imageWidth = 3200; LOTRGenLayerWorld.imageHeight = 4000;
        Field f = LOTRGenLayerWorld.class.getDeclaredField("biomeImageData");
        f.setAccessible(true); f.set(null, new byte[3200 * 4000]);
        assertTrue(KOMETileWorldResolver.INSTANCE.reloadBundled());
    }
    @Test public void productionRoutingInitializationAndReloadMatchOriginalBaseline() throws Exception {
        prepare();
        List<String> actual = capture();
        Path golden = Paths.get("src/test/resources/kome/tile/gameplay-baseline/production-paths.tsv");
        List<String> expected = Files.readAllLines(golden, StandardCharsets.UTF_8);
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) assertEquals("Original baseline row " + i, expected.get(i), actual.get(i));
    }
    @Test public void manualConsumptionUsesTheNextOriginalWaypointCandidate() throws Exception {
        prepare(); KOMEWorldData data = new KOMEWorldData("waypoint-alternative");
        data.initializeIntegratedWorld();
        assertNull("Links must still wait for the existing delayed path", data.getTileWaypointLink("T364"));
        data.ensureAutomaticTileWaypointLinks();
        assertEquals("FELLBEASTS", data.getTileWaypointLink("T364").lotrWaypointKey);
        assertEquals("HENNETH_ANNUN", data.getTileWaypointLink("T375").lotrWaypointKey);
        data.linkTileWaypoint("T001", lotr.common.world.map.LOTRWaypoint.FELLBEASTS, null, "manual fixture");
        data.linkTileWaypoint("T002", lotr.common.world.map.LOTRWaypoint.HENNETH_ANNUN, null, "manual fixture");
        data.ensureAutomaticTileWaypointLinks();
        assertEquals("AMON_ANGREN", data.getTileWaypointLink("T364").lotrWaypointKey);
        assertEquals("CROSSROADS_ITHILIEN", data.getTileWaypointLink("T375").lotrWaypointKey);
        assertTrue(data.getTileWaypointLink("T001").manualOverride);
        assertTrue(data.getTileWaypointLink("T002").manualOverride);
        NBTTagCompound saved = new NBTTagCompound(); data.writeToNBT(saved);
        KOMEWorldData restored = new KOMEWorldData("restored-alternatives"); restored.readFromNBT(saved);
        restored.ensureAutomaticTileWaypointLinks();
        assertEquals("AMON_ANGREN", restored.getTileWaypointLink("T364").lotrWaypointKey);
        assertEquals("CROSSROADS_ITHILIEN", restored.getTileWaypointLink("T375").lotrWaypointKey);
    }
    @Test public void activeStoredLegAndManualDestinationSurviveRestart() throws Exception {
        prepare();
        KOMEWorldData data = new KOMEWorldData("active-fixture"); data.initializeIntegratedWorld();
        data.ensureAutomaticTileWaypointLinks();
        data.getConquestTileIfPresent("T132").claim("gondor", 0L);
        data.getConquestTileIfPresent("T149").claim("gondor", 0L);
        data.setTileWaypoint("T132", KOMETileWaypoint.RALLY, 100, 24128.5, 83, -832.5, "manual fixture", true);
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder(); order.id = "active";
        order.ownerFaction = "gondor"; order.originTile = "T132"; order.destinationTile = "T149";
        order.currentStepOriginTile = "T132"; order.currentStepDestinationTile = "T149";
        order.status = KOMEArmyMovementOrder.MOVING; order.arrivalDimension = 100;
        order.arrivalX = 21119.5; order.arrivalY = 89; order.arrivalZ = -383.5;
        order.routeTiles.add("T132"); order.routeTiles.add("T149"); data.armyMovements.put(order.id, order);
        for (int restart = 0; restart < 2; restart++) {
            NBTTagCompound nbt = new NBTTagCompound(); data.writeToNBT(nbt);
            data = new KOMEWorldData("restarted"); data.readFromNBT(nbt); assertFalse(data.isWriteBlocked());
            data.ensureAutomaticTileWaypointLinks();
            KOMEArmyMovementOrder saved = data.armyMovements.get("active"); assertNotNull(saved);
            assertEquals(KOMEArmyMovementOrder.MOVING, saved.status);
            assertEquals(21119.5, saved.arrivalX, 0); assertEquals(89, saved.arrivalY, 0); assertEquals(-383.5, saved.arrivalZ, 0);
            assertEquals(Arrays.asList("T132", "T149"), saved.routeTiles);
            KOMETileWaypoint point = data.getTileWaypoint("T132", KOMETileWaypoint.RALLY);
            assertTrue(point.manualOverride); assertEquals(24128.5, point.x, 0); assertEquals(-832.5, point.z, 0);
        }
    }
    @Test public void riverNeedsExplicitPassableCrossingAndRemovingOverrideRevealsBaseline() throws Exception {
        prepare(); KOMEWorldData data = new KOMEWorldData("bridge-fixture");
        for (String id : KOMEConquestTileDefaults.getKnownTileIds()) {
            KOMEConquestTile tile = new KOMEConquestTile(id); tile.claim("gondor", 0); data.conquestTiles.put(id, tile);
        }
        // Isolate a real baseline bridge from alternative paths, using existing saved overrides.
        for (String key : KOMETileGameplayDefaults.get().routeKeys()) {
            String[] pair = key.split("\\|");
            if (!(pair[0].equals("T055") && pair[1].equals("T061")))
                data.setRouteEdge(pair[0], pair[1], KOMEConquestRouteEdge.BLOCKED, "fixture", 100, 0, 80, 0, "fixture");
        }
        assertEquals("true:[T055, T061]", route(data, "T055", "T061"));
        data.setRouteEdge("T055", "T061", KOMEConquestRouteEdge.RIVER, "fixture", 100, 0, 80, 0, "fixture");
        assertEquals("false:[]", route(data, "T055", "T061"));
        assertTrue(data.removeRouteEdgeOverride("T055", "T061"));
        assertEquals("true:[T055, T061]", route(data, "T055", "T061"));
    }
    static List<String> capture() throws Exception {
        List<String> rows = new ArrayList<String>();
        List<String> ids = new ArrayList<String>(KOMEConquestTileDefaults.getKnownTileIds()); Collections.sort(ids);
        KOMEWorldData d = new KOMEWorldData("gameplay-parity");
        for (String id : ids) {
            KOMEConquestTileDefaults.TileCenter c = KOMEConquestTileDefaults.getTileCenter(id);
            rows.add("center\t" + id + "\t" + c.dimensionId + "," + c.x + "," + c.y + "," + c.z);
            rows.add("neighbors\t" + id + "\t" + new TreeSet<String>(d.getRouteNeighbors(id)));
        }
        for (int i=0;i<ids.size();i++) for(int j=i+1;j<ids.size();j++) {
            KOMEConquestRouteEdge e=d.getRouteEdge(ids.get(i),ids.get(j));
            if(e!=null) rows.add("edge\t"+e.fromTile+"/"+e.toTile+"\t"+e.edgeType+","+e.isPassable()+","+e.isSpecialPassage()+","+e.name+","+e.manual);
        }
        for(Object m:KOMEConquestTileDefaults.getAutomaticBridgeMarkers()) rows.add("bridgeMarker\t"+fields(m));
        for(Object m:KOMEConquestTileDefaults.getAutomaticRiverBlockerMarkers()) rows.add("riverMarker\t"+fields(m));
        assertTrue(d.initializeIntegratedWorld()); assertFalse(d.initializeIntegratedWorld());
        state(rows,"fresh",d,ids);
        d.ensureAutomaticTileWaypointLinks(); state(rows,"delayed",d,ids);
        d.setTileWaypoint("T132",KOMETileWaypoint.RALLY,100,24128,83,-832,"manual fixture",true);
        d.setTileWaypoint("T242",KOMETileWaypoint.RALLY,100,12345,81,67890,"legacy fixture",false);
        d.setRouteEdge("T030","T033",KOMEConquestRouteEdge.BLOCKED,"manual block",100,10,80,20,"fixture");
        d.setRouteEdge("T001","T002",KOMEConquestRouteEdge.BRIDGE,"manual bridge",100,30,80,40,"fixture");
        // Consume a waypoint key manually so the normal delayed path must choose alternatives.
        d.linkTileWaypoint("T001",lotr.common.world.map.LOTRWaypoint.MITHLOND_SOUTH,null,"fixture");
        d.ensureAutomaticTileWaypointLinks(); state(rows,"overrides",d,ids);
        KOMEArmyMovementOrder order=new KOMEArmyMovementOrder(); order.id="parity-order";
        order.ownerFaction="gondor";order.originTile="T132";order.destinationTile="T149";
        order.status=KOMEArmyMovementOrder.ARRIVED;order.arrivalDimension=100;order.arrivalX=21119.5;order.arrivalY=87;order.arrivalZ=-383.5;
        order.routeTiles.add("T132");order.routeTiles.add("T149");d.armyMovements.put(order.id,order);
        NBTTagCompound nbt=new NBTTagCompound();d.writeToNBT(nbt);
        KOMEWorldData restored=new KOMEWorldData("reloaded");restored.readFromNBT(nbt);
        assertFalse(restored.getLoadFailureReason(),restored.isWriteBlocked());
        state(rows,"loaded",restored,ids);restored.ensureAutomaticTileWaypointLinks();state(rows,"restart-delayed",restored,ids);
        KOMEArmyMovementOrder saved=restored.armyMovements.get(order.id);assertNotNull(saved);
        rows.add("stored-leg\t"+saved.arrivalDimension+","+saved.arrivalX+","+saved.arrivalY+","+saved.arrivalZ+","+saved.routeTiles);
        // Exercise the actual private command route search with every tile explicitly authorized.
        for(String id:ids){KOMEConquestTile t=new KOMEConquestTile(id);t.claim("gondor",0L);d.conquestTiles.put(id,t);}
        for(String[] pair:new String[][]{{"T064","T148"},{"T041","T042"},{"T055","T061"},{"T001","T002"},{"T030","T036"}})
            rows.add("route\t"+pair[0]+"/"+pair[1]+"\t"+route(d,pair[0],pair[1]));
        Collections.sort(rows);return rows;
    }
    static String route(KOMEWorldData d,String a,String b)throws Exception{
        Method m=KOMECommandTroops.class.getDeclaredMethod("findLegalRoute",KOMEWorldData.class,String.class,String.class,String.class);m.setAccessible(true);
        Object value=m.invoke(new KOMECommandTroops(),d,a,b,"gondor");
        Field valid=value.getClass().getDeclaredField("valid"),path=value.getClass().getDeclaredField("routeTiles");valid.setAccessible(true);path.setAccessible(true);
        return valid.get(value)+":"+path.get(value);
    }
    static void state(List<String> rows,String phase,KOMEWorldData d,List<String> ids){
        for(String id:ids){
            KOMEConquestTile tile=d.getConquestTileIfPresent(id);KOMETileWaypoint p=d.getTileWaypoint(id,KOMETileWaypoint.RALLY);KOMETileWaypointLink link=d.getTileWaypointLink(id);
            rows.add(phase+"\t"+id+"\t"+(p==null?"none":p.dimensionId+","+p.x+","+p.y+","+p.z+","+p.manualOverride+","+p.createdBy)+"\t"+(link==null?"none":link.lotrWaypointKey+","+link.manualOverride+","+link.waypointWorldX+","+link.waypointWorldZ)+"\t"+(tile==null?"none":tile.projectRulingFaction()+","+tile.hasAnchor+","+tile.anchorDimension+","+tile.anchorX+","+tile.anchorY+","+tile.anchorZ));
        }
        for(KOMEConquestRouteEdge e:d.routeEdges.values())rows.add(phase+"-override\t"+e.fromTile+"/"+e.toTile+"\t"+e.edgeType+","+e.name+","+e.manual+","+e.markerDimension+","+e.markerX+","+e.markerY+","+e.markerZ);
    }
    static String fields(Object m)throws Exception{
        SortedMap<String,String> values=new TreeMap<String,String>();for(Field f:m.getClass().getFields())if(!Modifier.isStatic(f.getModifiers()))values.put(f.getName(),String.valueOf(f.get(m)));return values.toString();
    }
}
