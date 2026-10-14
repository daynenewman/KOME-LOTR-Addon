package atomicstryker.dynamiclights.client;

/** Test-only registry records API lifecycle; it performs no real lighting. */
public final class DynamicLights {
    public static final java.util.List<IDynamicLightSource> sources=new java.util.ArrayList<IDynamicLightSource>();
    public static int added,removed;
    public static void addLightSource(IDynamicLightSource source){sources.add(source);added++;}
    public static void removeLightSource(IDynamicLightSource source){sources.remove(source);removed++;}
    public static void reset(){sources.clear();added=removed=0;}
}
