package kome.client;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMECompanyDestinationPreviewTest {
    @Test public void exactOrderedTilesAreResolvedOnceWithoutInventingMissingSegments() {
        List<String> seen=new ArrayList<String>();
        KOMECompanyDestinationPreview preview=KOMECompanyDestinationPreview.of(Arrays.asList("T001","T050","T100"),tile->{
            seen.add(tile);return tile.equals("T050")?null:new double[] {tile.equals("T001")?10:30,20};
        });
        assertEquals(Arrays.asList("T001","T050","T100"),seen);assertEquals(3,preview.points.size());
        assertNotNull(preview.points.get(0));assertNull(preview.points.get(1));assertNotNull(preview.points.get(2));
        // Renderer joins only adjacent non-null entries, never the two endpoints across this gap.
        int lines=0;for(int i=1;i<preview.points.size();i++)if(preview.points.get(i-1)!=null&&preview.points.get(i)!=null)lines++;
        assertEquals(0,lines);
    }
    @Test public void cachedMapPointsTrackPanZoomThroughExistingViewport() {
        KOMECompanyDestinationPreview preview=KOMECompanyDestinationPreview.of(Arrays.asList("T001","T100"),t->new double[] {100,200});
        KOMECompanyDestinationPreview.Point point=preview.points.get(0);
        KOMEMapViewport initial=new KOMEMapViewport(0,0,800,600,100,200,1);
        KOMEMapViewport moved=new KOMEMapViewport(0,0,800,600,200,250,2);
        assertEquals(400,initial.screenX(point.x),0);assertEquals(300,initial.screenY(point.y),0);
        assertEquals(200,moved.screenX(point.x),0);assertEquals(200,moved.screenY(point.y),0);
    }
    @Test public void emptyRouteCreatesNoMarkersAndPreviewPointsAreImmutable() {
        assertTrue(KOMECompanyDestinationPreview.of(Collections.<String>emptyList(),t->{throw new AssertionError();}).points.isEmpty());
        KOMECompanyDestinationPreview preview=KOMECompanyDestinationPreview.of(Arrays.asList("T001"),t->new double[] {1,2});
        try {preview.points.clear();fail();}catch(UnsupportedOperationException expected) { }
    }
}
