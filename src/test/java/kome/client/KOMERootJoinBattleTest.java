package kome.client;

import java.util.*;
import kome.common.data.*;
import kome.common.network.*;
import org.junit.*;
import static org.junit.Assert.*;

public class KOMERootJoinBattleTest {
    private KOMECompanyDestinationClientState state;
    private KOMERootJoinBattle root;
    private KOMEPacketJoinBattleViewRequest query;
    @Before public void setup(){state=new KOMECompanyDestinationClientState();state.start(new Object());root=new KOMERootJoinBattle(state);query=root.open("T001");}
    static KOMEPacketJoinBattleViewResponse allowed(KOMEPacketJoinBattleViewRequest query) {
        KOMEPacketJoinBattleViewResponse view=new KOMEPacketJoinBattleViewResponse();
        view.tileId=query.tileId;view.correlation=query.correlation;view.conflictId="CF_ROOT";view.conflictRevision=7;
        view.conflictState=KOMEConflictRecord.State.ORDINARY;view.reason=KOMEJoinBattleService.Reason.ALLOWED;
        view.reasonText="You may join this battle.";view.playerFactionId="gondor";view.actionToken="0123456789abcdef0123456789abcdef";
        addRow(view,"C1",true);addRow(view,"C2",true);return view;
    }
    static void addRow(KOMEPacketJoinBattleViewResponse view,String id,boolean selectable) {
        try {
            java.lang.reflect.Constructor<KOMEPacketJoinBattleViewResponse.CompanyRow> c=
                KOMEPacketJoinBattleViewResponse.CompanyRow.class.getDeclaredConstructor(String.class,String.class,String.class,int.class,boolean.class);
            c.setAccessible(true);view.companies.add(c.newInstance(id,"Company "+id,"gondor",2,selectable));
        } catch(Exception failure){throw new AssertionError(failure);}
    }
    @Test public void visibilityUsesOnlyCompleteCorrelatedKOM19Evidence() {
        for(int denied=0;denied<10;denied++){
            setup();KOMEPacketJoinBattleViewResponse view=allowed(query);
            if(denied==0)view.conflictId="";if(denied==1)view.conflictState=KOMEConflictRecord.State.ENDED;
            if(denied==2)view.reason=KOMEJoinBattleService.Reason.WITHDRAWN;
            if(denied==3)view.companies.clear();
            if(denied==4){view.companies.clear();addRow(view,"C1",false);}
            if(denied==5)view.currentDeploymentConflict="OTHER";if(denied==6)view.actionToken="";
            if(denied==7)view.conflictRevision=0;if(denied==8)view.currentDeploymentState="RETURNING";if(denied==9)view.conflictState=null;
            assertEquals(KOMERootJoinBattle.Reception.UPDATED,root.accept(view,0));assertFalse(root.available(1));assertEquals("",root.status());
        }
        setup();assertFalse(root.available(0));root.accept(allowed(query),0);assertTrue(root.available(1));
        KOMEMapRadialMenu menu=new KOMEMapRadialMenu();menu.open("T001");assertEquals(5,menu.segments().size());
        menu.rootJoin(root.available(1),false,"");assertEquals(6,menu.segments().size());
        query=root.refresh();KOMEPacketJoinBattleViewResponse lost=allowed(query);lost.companies.clear();root.accept(lost,2);
        menu.rootJoin(root.available(2),false,"");assertEquals(5,menu.segments().size());
    }
    static KOMEPacketJoinBattleViewResponse one(KOMEPacketJoinBattleViewRequest request) {
        KOMEPacketJoinBattleViewResponse view=allowed(request);view.companies.remove(1);return view;
    }
    private KOMEPacketJoinBattleViewRequest arm() {
        root.accept(allowed(query),0);
        KOMEPacketJoinBattleViewRequest fresh=root.click(1);assertNotNull(fresh);
        assertFalse(root.armed(1));
        assertEquals(KOMERootJoinBattle.Reception.UPDATED,root.accept(one(fresh),2));
        assertTrue(root.armed(3));return fresh;
    }
    @Test public void freshFirstClickCountsZeroOneAndMultipleWithoutDeployment() {
        for(int count=0;count<3;count++) {
            setup();root.accept(allowed(query),0);
            KOMEPacketJoinBattleViewRequest fresh=root.click(1);
            assertNotNull(fresh);assertNotEquals(query.correlation,fresh.correlation);assertTrue(root.chooserPending());
            for(int rapid=0;rapid<20;rapid++)assertNull(root.click(2));
            KOMEPacketJoinBattleViewResponse view=allowed(fresh);view.conflictRevision++; // First activation trusts the fresh revision, never radial evidence.
            while(view.companies.size()>count)view.companies.remove(view.companies.size()-1);
            assertEquals(count==0?KOMERootJoinBattle.Reception.DENIED:count==1?KOMERootJoinBattle.Reception.UPDATED:
                KOMERootJoinBattle.Reception.OPEN_CHOOSER,root.accept(view,3));
            assertEquals(count==1,root.armed(4));assertFalse(root.deploying());
            assertEquals(count,KOMERootJoinBattle.selectableCount(view));
        }
    }
    @Test public void confirmationRevalidatesOneManyAndZeroAndNeverUsesOldRows() {
        for(int count=0;count<3;count++) {
            setup();arm();assertEquals("Confirm",root.label(3));
            KOMEPacketJoinBattleViewRequest confirm=root.click(4);assertNotNull(confirm);
            KOMEPacketJoinBattleViewResponse latest=allowed(confirm);
            while(latest.companies.size()>count)latest.companies.remove(latest.companies.size()-1);
            assertEquals(count==0?KOMERootJoinBattle.Reception.DENIED:count==1?KOMERootJoinBattle.Reception.DIRECT_JOIN:
                KOMERootJoinBattle.Reception.OPEN_CHOOSER,root.accept(latest,5));
            assertEquals(count==1,root.deploying());
            assertFalse(root.armed(6));if(count<2)assertNull(root.click(7));
        }
    }
    @Test public void countsOnlySelectableCombatRowsAndNeverCompanyNames() {
        KOMEPacketJoinBattleViewResponse view=one(query);addRow(view,"C2",false);
        assertEquals(1,KOMERootJoinBattle.selectableCount(view));
        addRow(view,"C3",true);assertEquals(2,KOMERootJoinBattle.selectableCount(view));
    }
    @Test public void eligibilityLossAndChangedConflictDoNotDeployFromOldArm() {
        for(int changed=0;changed<3;changed++) {
            setup();arm();KOMEPacketJoinBattleViewRequest fresh=root.click(4);
            KOMEPacketJoinBattleViewResponse latest=one(fresh);
            if(changed==0){latest.reason=KOMEJoinBattleService.Reason.WITHDRAWN;latest.reasonText="You have withdrawn.";}
            if(changed==1)latest.conflictRevision++;if(changed==2)latest.conflictId="NEW";
            assertEquals(changed==0?KOMERootJoinBattle.Reception.DENIED:KOMERootJoinBattle.Reception.UPDATED,root.accept(latest,5));
            assertFalse(root.armed(5));assertFalse(root.deploying());assertFalse(root.status().isEmpty());
        }
    }
    @Test public void timeoutRefreshStaleViewSessionAndTileChangesClearArm() {
        for(int change=0;change<6;change++) {
            setup();KOMEPacketJoinBattleViewRequest fresh=arm();
            if(change==0)assertFalse(root.armed(5002));
            if(change==1)root.refresh();
            if(change==2)root.accept(one(fresh),3);
            if(change==3)state.start(new Object());
            if(change==4)root.open("T002");if(change==5)root.cancelAction();
            assertFalse(root.armed(5003));assertFalse(root.deploying());
        }
        setup();root.accept(allowed(query),0);assertFalse(root.available(KOMERootJoinBattle.VIEW_LIFETIME_MILLIS));
        assertNull(root.click(KOMERootJoinBattle.VIEW_LIFETIME_MILLIS));assertNull(root.pending());
    }
    @Test public void lateReplyCannotOpenReplacementTileOrClosedSession() {
        for(int change=0;change<3;change++) {
            setup();arm();KOMEPacketJoinBattleViewRequest fresh=root.click(4);
            if(change==0)root.open("T002");if(change==1)root.close();if(change==2)state.close();
            assertEquals(KOMERootJoinBattle.Reception.IGNORED,root.accept(one(fresh),5));assertFalse(root.available(5));
        }
    }
    @Test public void staleViewDuringConfirmationCancelsIntentAndCannotDeployLater() {
        arm();KOMEPacketJoinBattleViewRequest fresh=root.click(4);
        assertEquals(KOMERootJoinBattle.Reception.IGNORED,root.accept(allowed(query),5));
        assertFalse(root.chooserPending());
        assertEquals(KOMERootJoinBattle.Reception.UPDATED,root.accept(one(fresh),6));
        assertFalse(root.armed(6));assertFalse(root.deploying());
    }
    @Test public void sixEqualSegmentsAndDynamicGeometryHaveNoOverlapOrGhostJoinAcrossScales() {
        KOMEMapRadialMenu menu=new KOMEMapRadialMenu();menu.open("T001");menu.rootJoin(true,false,"");
        double[] angles={-90,-30,30,90,150,210};Set<String> cells=new HashSet<String>();
        for(int i=0;i<6;i++){
            KOMEMapRadialMenu.Segment segment=menu.segments().get(i);assertEquals(44,segment.widthDegrees,0);
            assertEquals(angles[i],segment.centerDegrees,0);
            for(KOMEMapRadialMenu.Cell cell:segment.cells)assertTrue(cells.add(cell.x+","+cell.y));
            for(int factor=1;factor<=4;factor++){
                double scale=KOMEMapRadialMenu.scaleForGuiFactor(factor);
                assertEquals(i,menu.hit(segment.labelX()*scale,segment.labelY()*scale,scale));
                assertEquals(-1,menu.hit(0,0,scale));
                double gap=Math.toRadians(angles[i]+30);
                assertEquals(-1,menu.hit(Math.cos(gap)*57*scale,Math.sin(gap)*57*scale,scale));
            }
        }
        assertEquals(KOMEMapRadialMenu.Action.JOIN_BATTLE,menu.segments().get(0).action);
        assertEquals(0,menu.segments().get(0).labelX(),1e-9);assertTrue(menu.segments().get(0).labelY()<0);
        assertArrayEquals(new String[]{"Join","Battle"},KOMEMapRadialRenderer.labelLines(menu,KOMEMapRadialMenu.Action.JOIN_BATTLE));
        menu.rootJoin(true,true,"");assertEquals("Confirm",menu.label(menu.segments().get(0).action));
        assertArrayEquals(new String[]{"Confirm"},KOMEMapRadialRenderer.labelLines(menu,KOMEMapRadialMenu.Action.JOIN_BATTLE));
        assertArrayEquals(new String[]{"Move"},KOMEMapRadialRenderer.labelLines(menu,KOMEMapRadialMenu.Action.MOVEMENT));
        assertEquals(2,KOMEMapRadialRenderer.borderStroke(KOMEMapRadialMenu.Action.JOIN_BATTLE));
        assertEquals(1,KOMEMapRadialRenderer.borderStroke(KOMEMapRadialMenu.Action.MOVEMENT));
        menu.rootJoin(true,false,"Refreshing Join Battle...",true);
        assertEquals("Loading Join Battle...",menu.label(menu.segments().get(0).action));
        assertFalse(menu.enabled(KOMEMapRadialMenu.Action.JOIN_BATTLE));assertNull(menu.activate(0));assertEquals(0,menu.hit(0,-57));
        menu.rootJoin(false,false,"");assertEquals(5,menu.segments().size());assertEquals(-1,menu.hit(0,-57));
        menu.open("T002");assertEquals(5,menu.segments().size());menu.close();assertEquals(-1,menu.hit(0,-57));
    }
    @Test public void warningHoverBrightensBothLabelsButNeverLoadingAndRetainsBorder() {
        KOMEMapRadialMenu menu=new KOMEMapRadialMenu();menu.open("T001");
        KOMEMapRadialMenu.Action join=KOMEMapRadialMenu.Action.JOIN_BATTLE;
        for(boolean armed:new boolean[]{false,true}) {
            menu.rootJoin(true,armed,"");
            assertEquals(armed?"Confirm":"Join Battle",menu.label(join));
            assertNotEquals(KOMEMapRadialRenderer.fillColor(menu,join,false),KOMEMapRadialRenderer.fillColor(menu,join,true));
            assertEquals(kome.client.gui.KOMEGuiTheme.COLOR_WARN,KOMEMapRadialRenderer.borderColor(menu,join,true));
            assertEquals(2,KOMEMapRadialRenderer.borderStroke(join));
        }
        menu.rootJoin(true,false,"Loading",true);
        assertFalse(menu.enabled(join));
        assertEquals(kome.client.gui.KOMEGuiTheme.COLOR_PANEL_DARK,KOMEMapRadialRenderer.fillColor(menu,join,true));
        assertEquals(KOMEMapRadialRenderer.fillColor(menu,join,false),KOMEMapRadialRenderer.fillColor(menu,join,true));
    }}