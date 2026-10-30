package kome.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEMapTooltipStateTest {
    @Test public void delayedTargetIsStableAndResetsOnChange() {
        KOMEMapTooltipState state=new KOMEMapTooltipState();Object map=new Object();
        assertFalse(state.show(map,"company:a",KOMEClientConfig.MapTooltipMode.DELAYED,1000));
        assertFalse(state.show(map,"company:a",KOMEClientConfig.MapTooltipMode.DELAYED,1499));
        assertTrue(state.show(map,"company:a",KOMEClientConfig.MapTooltipMode.DELAYED,1500));
        assertFalse(state.show(map,"company:b",KOMEClientConfig.MapTooltipMode.DELAYED,1501));
        assertFalse(state.show(map,"company:a",KOMEClientConfig.MapTooltipMode.DELAYED,1502));
        assertTrue(state.show(map,"company:a",KOMEClientConfig.MapTooltipMode.DELAYED,2002));
        state.clear();assertFalse(state.show(map,"company:a",KOMEClientConfig.MapTooltipMode.DELAYED,2003));
    }
    @Test public void modesAndMapReplacement() {
        KOMEMapTooltipState state=new KOMEMapTooltipState();Object map=new Object();
        assertTrue(state.show(map,"build:x",KOMEClientConfig.MapTooltipMode.IMMEDIATE,10));
        assertFalse(state.show(map,"build:x",KOMEClientConfig.MapTooltipMode.OFF,1000));
        assertFalse(state.show(map,"build:x",KOMEClientConfig.MapTooltipMode.DELAYED,1001));
        assertFalse(state.show(new Object(),"build:x",KOMEClientConfig.MapTooltipMode.DELAYED,1300));
    }
    @Test public void nativeOwnershipIsFrameLocalAndSelectedWaypointBypassesOff() {
        KOMEMapTooltipState state=new KOMEMapTooltipState();
        state.markNative();assertTrue(state.nativeOwned());
        state.beginFrame();assertFalse(state.nativeOwned());
        KOMEClientConfig.MapTooltipMode previous=KOMEConquestMapOverlay.tooltipMode();
        try{
            KOMEConquestMapOverlay.applyTooltipMode(KOMEClientConfig.MapTooltipMode.OFF);
            assertFalse(KOMEConquestMapOverlay.suppressNativeWaypointTooltip(null,null,true,0,0));
        }finally{KOMEConquestMapOverlay.applyTooltipMode(previous);}
    }
}
