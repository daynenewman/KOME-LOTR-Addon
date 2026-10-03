package kome.client;
import java.lang.reflect.Field;
import java.util.*;
import kome.common.data.*;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.world.map.LOTRAbstractWaypoint;
import net.minecraft.client.Minecraft;

/** Exact v36.15 UI boundary: refresh selection and suppress controls that cannot change canonical public records. */
public final class KOMEPublicWaypointMapHooks {
    private KOMEPublicWaypointMapHooks(){ }
    private static final Field selected=field(LOTRGuiMap.class,"selectedWaypoint");
    private static final Field[] controls={field(LOTRGuiMap.class,"widgetDelCWP"),field(LOTRGuiMap.class,"widgetRenameCWP"),
        field(LOTRGuiMap.class,"widgetShareCWP"),field(LOTRGuiMap.class,"widgetHideSWP"),field(LOTRGuiMap.class,"widgetUnhideSWP")};
    private static final Map<Class<?>,Field> visible=new HashMap<Class<?>,Field>();
    private static Field field(Class<?> type,String name){
        try { Field f=type.getDeclaredField(name); f.setAccessible(true); return f; }
        catch(ReflectiveOperationException bad){ throw new IllegalStateException("Unsupported v36.15 public map boundary: "+name,bad); }
    }
    public static void beforeWidgets(LOTRGuiMap map) {
        try {
            Object value=selected.get(map); if(!(value instanceof KOMEPublicWaypointAdapter)) return;
            KOMEPublicWaypointAdapter old=(KOMEPublicWaypointAdapter)value;
            KOMEPublicWaypointRegistry.View current=KOMEPublicWaypointClientState.INSTANCE.snapshot().byId(old.view.record.id);
            selected.set(map,current==null?null:new KOMEPublicWaypointAdapter(current));
            for(Field control:controls) {
                Object widget=control.get(map); if(widget==null) continue;
                Field f=visible.get(widget.getClass()); if(f==null) { f=field(widget.getClass(),"visible"); visible.put(widget.getClass(),f); }
                f.setBoolean(widget,false);
            }
        } catch(ReflectiveOperationException bad){ throw new IllegalStateException("Unable to suppress public waypoint mutation controls",bad); }
    }
    public static boolean isPublicVisible(LOTRAbstractWaypoint point) {
        if(!(point instanceof KOMEPublicWaypointAdapter) || Minecraft.getMinecraft().thePlayer==null) return false;
        KOMEPublicWaypointRegistry.View view=KOMEPublicWaypointClientState.INSTANCE.snapshot().byId(((KOMEPublicWaypointAdapter)point).view.record.id);
        return view!=null && Minecraft.getMinecraft().thePlayer.dimension==view.record.dimension;
    }
}
