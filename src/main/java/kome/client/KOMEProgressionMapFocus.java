package kome.client;

import java.lang.reflect.Field;
import java.util.List;
import kome.common.data.KOMEVisualMarker;
import kome.common.data.KOMEKnightCommissionLocations;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import net.minecraft.client.Minecraft;

/** Reuses server-authored markers. Opening/focusing never creates a waypoint or assignment. */
public final class KOMEProgressionMapFocus {
    private static KOMEVisualMarker focused;
    private static long focusedUntil;
    static final long HIGHLIGHT_NANOS=10_000_000_000L;
    private KOMEProgressionMapFocus() {}
    interface MapHost { void show(LOTRGuiMap map); }
    static java.util.function.Supplier<LOTRGuiMap> mapFactory=FocusedMap::new;
    static final class FocusedMap extends LOTRGuiMap {
        private int previousZoom;private boolean captured;
        private KOMEVisualMarker objective;
        @Override public void initGui(){
            if(!captured)try{previousZoom=integer(this,"zoomPower");captured=true;}catch(ReflectiveOperationException e){captured=false;}
            super.initGui();
            if(objective!=null)try{focus(this,current(objective));}catch(ReflectiveOperationException e){throw new IllegalStateException("Native LOTR map camera changed",e);}
        }
        @Override public void onGuiClosed(){try{if(captured)setInteger(this,"zoomPower",previousZoom);}catch(ReflectiveOperationException ignored){}finally{super.onGuiClosed();}}
    }
    static LOTRGuiMap open(KOMEVisualMarker marker,MapHost host,long now)throws ReflectiveOperationException{
        LOTRGuiMap map=mapFactory.get();if(map instanceof FocusedMap)((FocusedMap)map).objective=marker;
        host.show(map);focus(map,current(marker));highlight(marker,now);return map;
    }
    /** Temporary views must neither restore nor replace the conquest overlay's manual viewport. */
    public static boolean isObjectiveView(Object screen){return screen instanceof FocusedMap;}
    private static KOMEVisualMarker current(KOMEVisualMarker requested){
        for(KOMEVisualMarker marker:KOMEVisualMarkerClientState.markers())
            if(marker.objective&&marker.role==requested.role&&marker.dimension==requested.dimension
                    &&!requested.entityUuid.isEmpty()&&requested.entityUuid.equals(marker.entityUuid))return marker;
        return requested;
    }
    public static KOMEVisualMarker target(List<KOMEVisualMarker> markers,int dimension) {
        if(markers==null)return null;
        for(KOMEVisualMarker marker:markers)if(marker.dimension==dimension&&marker.role==KOMEVisualMarker.Role.ESCORT
                &&marker.objective&&!marker.entityUuid.isEmpty()
                &&KOMEKnightCommissionLocations.coordinate(marker.x)&&KOMEKnightCommissionLocations.coordinate(marker.z))return marker;
        for(KOMEVisualMarker marker:markers)
            if(marker.dimension==dimension&&marker.objective&&marker.role!=KOMEVisualMarker.Role.ENCOUNTER_ENEMY
                    &&KOMEKnightCommissionLocations.coordinate(marker.x)&&KOMEKnightCommissionLocations.coordinate(marker.z)
                    &&(marker.role!=KOMEVisualMarker.Role.ESCORT||marker.entityUuid.isEmpty()))return marker;
        return null;
    }
    public static KOMEVisualMarker target() {
        Minecraft mc=Minecraft.getMinecraft();return mc.thePlayer==null?null:target(KOMEVisualMarkerClientState.markers(),mc.thePlayer.dimension);
    }
    public static float mapX(double x){return (float)(x/LOTRGenLayerWorld.scale+LOTRGenLayerWorld.originX);}
    public static float mapY(double z){return (float)(z/LOTRGenLayerWorld.scale+LOTRGenLayerWorld.originZ);}
    public static void open() {
        KOMEVisualMarker marker=target();if(marker==null)return;
        Minecraft mc=Minecraft.getMinecraft();
        try{
            open(marker,mc::displayGuiScreen,System.nanoTime());
        }catch(ReflectiveOperationException failure){focused=null;mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("Map destination: "+marker.subtitle+" ("+(int)marker.x+", "+(int)marker.z+")"));}
    }
    static void focus(LOTRGuiMap map,KOMEVisualMarker marker)throws ReflectiveOperationException {
        set(map,"posX",mapX(marker.x));set(map,"prevPosX",mapX(marker.x));
        set(map,"posY",mapY(marker.z));set(map,"prevPosY",mapY(marker.z));
        set(map,"posXMove",0F);set(map,"posYMove",0F);
        // Native v36.15 widget permits powers -3 through 4. No animation from old zoom.
        setInteger(map,"zoomPower",4);setInteger(map,"prevZoomPower",4);setInteger(map,"zoomTicks",0);
        set(map,"zoomScale",16F);set(map,"zoomScaleStable",16F);set(map,"zoomExp",4F);
    }
    private static int integer(LOTRGuiMap map,String name)throws ReflectiveOperationException{Field field=LOTRGuiMap.class.getDeclaredField(name);field.setAccessible(true);return field.getInt(map);}
    private static void setInteger(LOTRGuiMap map,String name,int value)throws ReflectiveOperationException{Field field=LOTRGuiMap.class.getDeclaredField(name);field.setAccessible(true);field.setInt(map,value);}
    private static void set(LOTRGuiMap map,String name,float value)throws ReflectiveOperationException{
        Field field=LOTRGuiMap.class.getDeclaredField(name);field.setAccessible(true);field.setFloat(map,value);
    }
    static void highlight(KOMEVisualMarker marker,long now){focused=marker;focusedUntil=now+HIGHLIGHT_NANOS;}
    static boolean isFocused(KOMEVisualMarker marker,long now){return focused!=null&&now-focusedUntil<0
        &&marker!=null&&focused.role==marker.role&&focused.entityUuid.equals(marker.entityUuid)
        &&focused.dimension==marker.dimension&&(!marker.entityUuid.isEmpty()||focused.x==marker.x&&focused.z==marker.z);}
    public static boolean isFocused(KOMEVisualMarker marker){return isFocused(marker,System.nanoTime());}
    public static void clear(){focused=null;focusedUntil=0;}
}
