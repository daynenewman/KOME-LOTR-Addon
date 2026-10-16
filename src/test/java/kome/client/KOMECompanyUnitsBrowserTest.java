package kome.client;

import java.util.*;
import kome.common.data.*;
import kome.common.data.KOMECompanySourceOptions.Row;
import kome.common.network.KOMEPacketCompanyOriginRequest;
import org.junit.*;
import static org.junit.Assert.*;

public class KOMECompanyUnitsBrowserTest {
    private KOMECompanyDestinationClientState state;
    private KOMECompanyUnitsBrowser browser;
    private KOMEPacketCompanyOriginRequest request;
    @Before public void setup(){state=new KOMECompanyDestinationClientState();state.start(new Object());browser=new KOMECompanyUnitsBrowser(state);request=browser.open("T001");}
    static KOMECompanySourceOptions page(KOMEPacketCompanyOriginRequest r,int count){
        int from=r.after.isEmpty()?1:Integer.parseInt(r.after.substring(1))+1;List<Row> rows=new ArrayList<>();
        for(int i=from;i<=Math.min(count,from+7);i++)rows.add(new Row(String.format(Locale.ROOT,"C%02d",i),"Same company",2,1250,i%2==0,i%3==0?0:1,1,"Stationed",i%2==0));
        return new KOMECompanySourceOptions(r.session,r.sequence,r.source,KOMECompanyDestinationOptions.Result.OK,rows,KOMECompanyDestinationSelectorTest.TOKEN,rows.isEmpty()?"":rows.get(rows.size()-1).companyId,from+7<count,0);
    }
    @Test public void fourRowsAndSmallScreenUseSharedGeometryWithOnlyInspectionControls(){
        for(int[] dimensions:new int[][]{{800,600},{640,360},{480,270},{320,240}}){
            KOMECompanyDestinationPanel.Layout l=KOMECompanyUnitsPanel.layout(new KOMEMapViewport(0,0,dimensions[0],dimensions[1],0,0,1),false);
            assertTrue(l.capacity>=1&&l.capacity<=4);if(dimensions[1]==600)assertEquals(4,l.capacity);
            assertFalse(l.controls.containsKey(KOMECompanyDestinationPanel.Control.CONFIRM));assertFalse(l.controls.containsKey(KOMECompanyDestinationPanel.Control.CONFIRM_JOIN));assertFalse(l.controls.containsKey(KOMECompanyDestinationPanel.Control.FILTER_ALL));
        }
    }
    @Test public void zeroOneAndManyLocalRowsRemainReadOnlyAndDoNotImplyMovementEligibility(){
        assertFalse(request.targeted);assertEquals("",request.destination);assertEquals("",request.company);
        browser.accept(page(request,0),0);assertEquals("No Campaign companies on this tile.",browser.message());assertNull(browser.scroll(1,4,0));
        request=browser.refresh();browser.accept(page(request,4),0);browser.select(1);assertEquals("C02",browser.selectedCompanyId());
        assertTrue(browser.selected().held);assertTrue(KOMECompanyUnitsPanel.selected(browser,browser.visible(1)));
        browser.select(2);assertEquals("C03",browser.selectedCompanyId());assertEquals(0,browser.selected().allowance);
        assertEquals("Campaign Company",KOMECompanyUnitsBrowser.name(new Row("C1","C1",1,100,false,0,1,"Stationed",false)));
        assertEquals("Campaign Company",KOMECompanyUnitsBrowser.name(new Row("C1"," C1 ",1,100,false,0,1,"Stationed",false)));
        browser.clearSelection();assertEquals("",browser.selectedCompanyId());
    }
    @Test public void nineEighteenTwentyAndFortyRowsTraverseSeamsBothWaysWithoutDuplicatesOrLostSelection(){
        for(int total:new int[]{9,18,20,40}){
            setup();browser.accept(page(request,total),0);browser.select(1);
            for(int top=0;top<=total-4;top++){
                for(int i=0;i<4;i++)assertEquals(String.format(Locale.ROOT,"C%02d",top+i+1),browser.visible(i).companyId);
                assertEquals("C02",browser.selectedCompanyId());assertTrue(browser.retainedRows()<=24);
                if(top<total-4){KOMEPacketCompanyOriginRequest next=browser.scroll(1,4,0);if(next!=null){assertEquals("",next.destination);assertTrue(browser.accept(page(next,total),0));}}
            }
            for(int top=total-5;top>=0;top--){KOMEPacketCompanyOriginRequest next=browser.scroll(-1,4,0);if(next!=null)assertTrue(browser.accept(page(next,total),0));for(int i=0;i<4;i++)assertEquals(String.format(Locale.ROOT,"C%02d",top+i+1),browser.visible(i).companyId);}
            assertEquals("C02",browser.selectedCompanyId());assertTrue(KOMECompanyUnitsPanel.selected(browser,browser.visible(1)));
        }
    }
    @Test public void refreshDisconnectReplacementAndCatalogueInvalidationClearSelectionAndRejectLatePages(){
        browser.accept(page(request,9),0);browser.select(2);KOMEPacketCompanyOriginRequest old=request;request=browser.refresh();
        assertEquals("T001",request.source);assertEquals("",browser.selectedCompanyId());assertFalse(browser.accept(page(old,9),0));
        browser.accept(page(request,9),0);browser.select(0);KOMEPacketCompanyOriginRequest next=browser.scroll(1,8,0);assertNotNull(next);
        browser.accept(new KOMECompanySourceOptions(next.session,next.sequence,next.source,KOMECompanyDestinationOptions.Result.CATALOGUE_CHANGED,Collections.emptyList(),"","",false,0),0);
        assertTrue(browser.rows().isEmpty());assertEquals("",browser.selectedCompanyId());assertTrue(browser.message().contains("Refresh"));
        request=browser.refresh();browser.accept(page(request,1),0);browser.select(0);state.start(new Object());assertFalse(browser.active());assertEquals("",browser.selectedCompanyId());assertFalse(browser.accept(page(request,1),0));
        setup();browser.clear();assertFalse(browser.accept(page(request,1),0));assertFalse(browser.active());
    }
    @Test public void loadingAndThrottleNeverPollOrDuplicatePendingSourceRequests(){
        for(int i=0;i<20;i++)assertNull(browser.scroll(1,4,0));
        assertTrue(browser.accept(new KOMECompanySourceOptions(request.session,request.sequence,request.source,KOMECompanyDestinationOptions.Result.RATE_LIMITED,Collections.emptyList(),"","",false,1000),0));
        assertTrue(browser.message().contains("shortly"));assertNull(browser.scroll(1,4,999));
        KOMEPacketCompanyOriginRequest retry=browser.scroll(1,4,1000);assertNotNull(retry);assertFalse(browser.accept(page(request,9),1001));assertTrue(browser.accept(page(retry,9),1001));
        assertEquals(8,browser.rows().size());
    }
}
