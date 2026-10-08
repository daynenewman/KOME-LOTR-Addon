package kome.client;

import java.lang.reflect.Field;
import java.util.*;
import kome.common.KOMEAccessFixture;
import kome.common.data.KOMEVisualMarker;
import lotr.client.gui.LOTRGuiMap;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionMapFocusTest {
    @Test public void onlyValidCurrentGeographicMarkerProvidesShowMe() {
        KOMEVisualMarker relationship=new KOMEVisualMarker(KOMEVisualMarker.Role.KNIGHT_LIEGE,"liege","Liege","",100,1,65,2);
        assertNull(KOMEProgressionMapFocus.target(Arrays.asList(relationship),100));
        KOMEVisualMarker invalid=new KOMEVisualMarker(KOMEVisualMarker.Role.COMMISSION,"","","",100,Double.NaN,0,2);
        assertNull(KOMEProgressionMapFocus.target(Arrays.asList(invalid),100));
        KOMEVisualMarker valid=new KOMEVisualMarker(KOMEVisualMarker.Role.ESCORT,"","Escort","Edoras",100,400,0,500);
        List<KOMEVisualMarker> markers=Arrays.asList(relationship,valid);
        for(int i=0;i<5;i++)assertSame(valid,KOMEProgressionMapFocus.target(markers,100));assertEquals(2,markers.size());
        assertNull(KOMEProgressionMapFocus.target(markers,101));
        KOMEVisualMarkerClientState.update(markers);KOMEVisualMarkerClientState.clear();assertTrue(KOMEVisualMarkerClientState.markers().isEmpty());
    }
    @Test public void persistedCoordinatesFocusNativeMapAndClearPendingPan()throws Exception {
        LOTRGuiMap map=KOMEAccessFixture.allocate(LOTRGuiMap.class);
        KOMEVisualMarker marker=new KOMEVisualMarker(KOMEVisualMarker.Role.COMMISSION,"","Service","Refuge",100,10000,0,-20000);
        for(int i=0;i<3;i++)KOMEProgressionMapFocus.focus(map,marker);
        assertEquals(KOMEProgressionMapFocus.mapX(marker.x),value(map,"posX"),0);assertEquals(value(map,"posX"),value(map,"prevPosX"),0);
        assertEquals(KOMEProgressionMapFocus.mapY(marker.z),value(map,"posY"),0);assertEquals(value(map,"posY"),value(map,"prevPosY"),0);
        assertEquals(0,value(map,"posXMove"),0);assertEquals(0,value(map,"posYMove"),0);
    }
    private static float value(LOTRGuiMap map,String name)throws Exception{Field field=LOTRGuiMap.class.getDeclaredField(name);field.setAccessible(true);return field.getFloat(map);}
}
