package kome.client;

import kome.common.data.*;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMETileClientAdapterTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    @Test public void clientServerBuildAndHoverAdaptersAgreeOnRealRegressionAndBounds() {
        int dimension = KOMETileTestResources.dimension();
        for (int maskX : new int[] {-1, 0, 2291, 2292, 3199, 3200}) {
            int x = KOMETileTestResources.worldX(maskX), z = KOMETileTestResources.worldZ(58);
            KOMETileResolution server = KOMEConquestTileDefaults.resolveWorldCoordinates(dimension, x, z);
            assertEquals(server.toString(), KOMEConquestMapOverlay.tileAtWorld(dimension, x, z).toString());
            assertEquals(server.toString(), KOMEConquestMapOverlay.tileAtMapPosition(dimension, maskX, 58).toString());
            assertEquals(server.toString(), KOMEBuildService.tileAtWorldCoordinates(dimension, x, z).toString());
        }
        assertEquals(KOMETileResolution.Status.IN_BOUNDS_GAP, KOMEConquestMapOverlay.tileAtMapPosition(dimension, 2291, 58).status);
        assertEquals("T001", KOMEConquestMapOverlay.tileAtMapPosition(dimension, 2292, 58).tileId);
        assertEquals(KOMETileResolution.Status.UNSUPPORTED_DIMENSION, KOMEConquestMapOverlay.tileAtWorld(dimension + 1, 189696, -86016).status);
    }
    @Test public void productionHoverPathHonorsPanZoomViewportAndGaps() throws Exception {
        // Exercise the private path shared by rendered hover and right-click selection.
        // Supply its already-loaded CPU palette so this test does not create GL textures.
        java.lang.reflect.Field raster = KOMEConquestMapOverlay.class.getDeclaredField("tileRaster");
        java.lang.reflect.Field colors = KOMEConquestMapOverlay.class.getDeclaredField("tileColorsById");
        raster.setAccessible(true); colors.setAccessible(true);
        Object previousRaster = raster.get(null);
        @SuppressWarnings("unchecked") java.util.Map<String,Integer> palette = (java.util.Map<String,Integer>)colors.get(null);
        java.util.Map<String,Integer> previousColors = new java.util.HashMap<String,Integer>(palette);
        String[] names = {"mapXMin","mapXMax","mapYMin","mapYMax","mapWidth","mapHeight"};
        int[] original = new int[names.length];
        for (int i=0; i<names.length; i++) original[i]=mapField(names[i]).getInt(null);
        java.lang.reflect.Method hover = KOMEConquestMapOverlay.class.getDeclaredMethod("getHoveredTileColor", lotr.client.gui.LOTRGuiMap.class, int.class, int.class);
        hover.setAccessible(true);
        lotr.client.gui.LOTRGuiMap map = kome.common.KOMEAccessFixture.allocate(lotr.client.gui.LOTRGuiMap.class);
        try {
            KOMETileRasterSnapshot snapshot = KOMETileTestResources.real();
            raster.set(null, snapshot); palette.clear(); palette.putAll(snapshot.colorsById());
            for (int[] viewport : new int[][] {{10,410,20,320,400,300},{0,800,0,600,800,600}}) {
                for (int i=0; i<names.length; i++) mapField(names[i]).setInt(null,viewport[i]);
                for (float zoom : new float[] {0.5F,1F,2F,4F,8F}) for (int pan : new int[] {-24,0,24}) {
                    mapField("zoomScale").setFloat(map, zoom);
                    mapField("posX").setFloat(map, 2292F - pan / zoom);
                    mapField("posY").setFloat(map, 58F - 8F / zoom);
                    int mouseX=viewport[0]+viewport[4]/2+pan, mouseY=viewport[2]+viewport[5]/2+8;
                    assertEquals(palette.get("T001"), hover.invoke(null,map,mouseX,mouseY));
                    assertEquals(0, hover.invoke(null,map,mouseX-1,mouseY));
                    assertEquals(0, hover.invoke(null,map,viewport[1],mouseY));
                    assertEquals(0, hover.invoke(null,map,mouseX,viewport[3]));
                    assertEquals(0, hover.invoke(null,map,viewport[0]-1,mouseY));
                    assertEquals(0, hover.invoke(null,map,mouseX,viewport[2]-1));
                }
            }
            mapField("posX").setFloat(map,-1); mapField("posY").setFloat(map,58); mapField("zoomScale").setFloat(map,1);
            assertEquals(0, hover.invoke(null,map,400,300));
            KOMETileWorldResolver.INSTANCE.invalidate();
            assertEquals(0, hover.invoke(null,map,400,300));
        } finally {
            raster.set(null,previousRaster); palette.clear(); palette.putAll(previousColors);
            for (int i=0; i<names.length; i++) mapField(names[i]).setInt(null,original[i]);
        }
    }
    private static java.lang.reflect.Field mapField(String name) throws Exception {
        java.lang.reflect.Field field=lotr.client.gui.LOTRGuiMap.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }}
