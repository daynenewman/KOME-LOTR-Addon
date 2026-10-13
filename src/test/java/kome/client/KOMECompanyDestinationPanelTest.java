package kome.client;

import java.util.*;
import kome.common.command.KOMECompanyMovementService.Status;
import kome.common.data.KOMECompanyDestinationOptions;
import kome.common.data.KOMECompanyDestinationOptions.*;
import kome.common.network.KOMEPacketCompanyDestinationRequest;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMECompanyDestinationPanelTest {
    @Test public void compactLayoutFitsNormalLargeAndMinimumGuiSizesWithExplicitRowWindow() {
        for(int[] size:new int[][] {{960,540},{640,360},{480,270},{320,240}}) {
            KOMEMapViewport view=new KOMEMapViewport(0,0,size[0],size[1],0,0,1);
            for(boolean left:new boolean[] {false,true}) {
                KOMECompanyDestinationPanel.Layout layout=new KOMECompanyDestinationPanel.Layout(view,left);
                assertTrue(layout.panel.width<=260);assertTrue(layout.capacity>=1&&layout.capacity<=8);
                assertTrue(layout.panel.x>=0&&layout.panel.x+layout.panel.width<=size[0]);
                assertTrue(layout.panel.y+layout.panel.height<=size[1]);
                for(KOMECompanyDestinationPanel.Control c:KOMECompanyDestinationPanel.Control.values()) {
                    KOMECompanyDestinationPanel.Rect rect=layout.controls.get(c);
                    if(c==KOMECompanyDestinationPanel.Control.CONFIRM_JOIN) {assertNull(rect);continue;}
                    assertEquals(c,layout.controlAt(rect.x+1,rect.y+1));assertTrue(layout.panel.contains(rect.x+1,rect.y+1));
                }
                for(int i=0;i<layout.capacity;i++)assertEquals(i,layout.rowAt(layout.row(i).x+1,layout.row(i).y+1,8));
                assertEquals(-1,layout.rowAt(layout.panel.x-1,layout.rowTop,8));
            }
        }
    }
    @Test public void controlsDistinguishPagingLocalRowsAndThrottledRetry() {
        Object connection=new Object();KOMECompanyDestinationClientState queries=new KOMECompanyDestinationClientState();queries.start(connection);
        KOMECompanyDestinationSelector model=new KOMECompanyDestinationSelector(queries);
        KOMEPacketCompanyDestinationRequest request=model.open("T100");
        KOMECompanyDestinationPanel.Layout layout=new KOMECompanyDestinationPanel.Layout(new KOMEMapViewport(0,0,640,360,0,0,1),false);
        assertFalse(KOMECompanyDestinationPanel.enabled(KOMECompanyDestinationPanel.Control.ROWS_DOWN,model,layout,0));
        List<Row> rows=new ArrayList<Row>();for(int i=0;i<8;i++)rows.add(KOMECompanyDestinationSelectorTest.row("C"+i,Status.CAN_START_AND_ARRIVE_NOW));
        KOMECompanyDestinationOptions response=KOMECompanyDestinationSelectorTest.page(request,Result.OK,rows,true,0);
        assertTrue(queries.accept(response,connection));model.accept(response,0);
        assertTrue(KOMECompanyDestinationPanel.enabled(KOMECompanyDestinationPanel.Control.ROWS_DOWN,model,layout,0));
        assertTrue(KOMECompanyDestinationPanel.enabled(KOMECompanyDestinationPanel.Control.ROWS_DOWN,model,layout,0));
        request=model.browse(1,8,0);response=KOMECompanyDestinationSelectorTest.page(request,Result.RATE_LIMITED,Collections.<Row>emptyList(),false,500);
        assertTrue(queries.accept(response,connection));model.accept(response,0);
        assertFalse(KOMECompanyDestinationPanel.enabled(KOMECompanyDestinationPanel.Control.RETRY,model,layout,499));
        assertTrue(KOMECompanyDestinationPanel.enabled(KOMECompanyDestinationPanel.Control.RETRY,model,layout,500));
        assertTrue(KOMECompanyDestinationPanel.enabled(KOMECompanyDestinationPanel.Control.BACK,model,layout,0));
    }
}
