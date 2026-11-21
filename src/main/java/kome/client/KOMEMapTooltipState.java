package kome.client;

/** Client-only hover clock. A target is identified by its current presentation identity. */
final class KOMEMapTooltipState {
    static final long DELAY_MS=500L;
    private Object map;
    private Object target;
    private long enteredAt;
    private boolean nativeOwned;

    void beginFrame(){nativeOwned=false;}
    boolean nativeOwned(){return nativeOwned;}
    void markNative(){nativeOwned=true;}
    void clear(){map=null;target=null;nativeOwned=false;}
    boolean show(Object currentMap,Object currentTarget,KOMEClientConfig.MapTooltipMode mode,long now) {
        if(currentMap==null||currentTarget==null||mode==KOMEClientConfig.MapTooltipMode.OFF){clear();return false;}
        if(map!=currentMap||!currentTarget.equals(target)){
            map=currentMap;target=currentTarget;enteredAt=now;
        }
        return mode==KOMEClientConfig.MapTooltipMode.IMMEDIATE || now-enteredAt>=DELAY_MS;
    }
}
