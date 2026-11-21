package kome.client;

/** Actual native transform observations, not mouse/input state. Monotonic times are injectable. */
final class KOMEMapNavigation {
    static final long SETTLE_NANOS=250_000_000L;
    private KOMEMapViewport previous;
    private boolean animated;
    private long changed;
    boolean settled(KOMEMapViewport view,boolean animating,long now) {
        if(previous==null||animated||animating||previous.posX!=view.posX||previous.posY!=view.posY||previous.zoom!=view.zoom
                ||previous.left!=view.left||previous.top!=view.top||previous.right!=view.right||previous.bottom!=view.bottom)changed=now;
        previous=view;animated=animating;
        return !animating&&now-changed>=SETTLE_NANOS;
    }
    void clear(){previous=null;animated=false;}
}
