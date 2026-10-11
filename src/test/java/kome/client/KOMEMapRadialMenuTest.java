package kome.client;

import org.junit.Test;
import static org.junit.Assert.*;
import static kome.client.KOMEMapRadialMenu.*;

public class KOMEMapRadialMenuTest {
    @Test public void rootMovementBackPreservesSelectedTile() {
        KOMEMapRadialMenu menu = open();
        assertEquals(5,menu.segments().size()); assertEquals(Action.MOVEMENT,menu.activate(0));
        assertEquals(State.MOVEMENT,menu.state()); assertEquals(2,menu.segments().size());
        assertEquals(Action.BACK,menu.activate(1)); assertEquals(State.ROOT,menu.state()); assertEquals("T357",menu.tileId());
    }
    @Test public void battleShellAndBackHaveNoGameplayAction() {
        KOMEMapRadialMenu menu = open(); menu.activate(1);
        assertEquals(State.BATTLE,menu.state()); assertNull(menu.activate(0)); assertEquals(State.BATTLE,menu.state());
        assertEquals(Action.BACK,menu.activate(1)); assertEquals(State.ROOT,menu.state());
    }
    @Test public void disabledActionsDoNotTransition() {
        KOMEMapRadialMenu menu = open();
        assertNull(menu.activate(4)); assertEquals(State.ROOT,menu.state()); menu.activate(0);
        assertEquals(Action.MOVE_HERE,menu.activate(0)); assertEquals(State.MOVEMENT,menu.state());
    }
    @Test public void replacementAndInvalidEmptySelectionBehaveDeliberately() {
        KOMEMapRadialMenu menu = open(); menu.activate(0);
        assertFalse(menu.open("")); assertEquals("T357",menu.tileId()); assertEquals(State.MOVEMENT,menu.state());
        assertTrue(menu.open("T001")); assertEquals("T001",menu.tileId()); assertEquals(State.ROOT,menu.state());
    }
    @Test public void closeClearsSelectionNavigationAndHitboxes() {
        KOMEMapRadialMenu menu = open(); menu.activate(0); menu.close();
        assertFalse(menu.isOpen()); assertEquals("",menu.tileId()); assertEquals(State.ROOT,menu.state());
        assertEquals(-1,menu.hit(0,-57)); assertNull(menu.activate(0)); menu.close();
    }
    @Test public void equalWidthsPlacePrimaryAboveSecondaryBelowAndCloseAtBottom() {
        KOMEMapRadialMenu menu = open();
        String[] labels = {"Move","Battle","Info","Close","Units"};
        double[] centers = {-126,-54,18,90,162}, widths = {44,44,44,44,44};
        double total = 0;
        for (int i = 0; i < 5; i++) {
            Segment segment = menu.segments().get(i);
            assertEquals(labels[i],segment.action.label);
            assertEquals(centers[i],segment.centerDegrees,0); assertEquals(widths[i],segment.widthDegrees,0);
            assertEquals(i,menu.hit(segment.labelX(),segment.labelY()));
            assertTrue(i < 2 ? segment.labelY() < 0 : segment.labelY() > 0);
            total += segment.widthDegrees;
        }
        assertEquals(140,360-total,0);
        assertEquals(0,menu.segments().get(3).labelX(),1e-9);
        assertEquals(menu.segments().get(0).widthDegrees,menu.segments().get(3).widthDegrees,0);
    }
    @Test public void intentionalRootGapsDoNotExpandToFillTheCircumference() {
        KOMEMapRadialMenu menu = open();
        for (double angle : new double[] {-90,-18,54,126,198}) {
            double a = Math.toRadians(angle);
            for (double r : new double[] {43,57,70}) assertEquals(-1,menu.hit(Math.cos(a)*r,Math.sin(a)*r));
        }
        Segment move = menu.segments().get(0);
        menu.activate(0);
        assertEquals(56,menu.segments().get(0).widthDegrees,0); assertEquals(56,menu.segments().get(1).widthDegrees,0);
        assertEquals(0,menu.hit(0,-57)); assertEquals(1,menu.hit(0,57)); assertEquals(-1,menu.hit(57,0));
        menu.activate(1); assertSame(move,menu.segments().get(0));
    }
    @Test public void centerAndOutsidePassThroughAtEveryAngleAndState() {
        KOMEMapRadialMenu menu = open();
        for (int state = 0; state < 3; state++) {
            if (state > 0) { menu.open("T357"); menu.activate(state == 1 ? 0 : 1); }
            for (int degree = 0; degree < 360; degree++) {
                double a = Math.toRadians(degree);
                for (double r : new double[] {0,5,41.999,72,150})
                    assertEquals(-1,menu.hit(Math.cos(a)*r,Math.sin(a)*r));
            }
        }
    }
    @Test public void everyDrawnCellAndItsSnappedEdgesAgreeWithHitTesting() {
        KOMEMapRadialMenu menu = open();
        for (int state = 0; state < 3; state++) {
            if (state > 0) { menu.open("T357"); menu.activate(state == 1 ? 0 : 1); }
            for (int i = 0; i < menu.segments().size(); i++) {
                Segment segment = menu.segments().get(i); assertFalse(segment.cells.isEmpty());
                for (Cell cell : segment.cells) {
                    assertEquals(0,cell.x % PIXEL_GRID); assertEquals(0,cell.y % PIXEL_GRID);
                    assertEquals(i,menu.hit(cell.x+0.001,cell.y+0.001));
                    assertEquals(i,menu.hit(cell.x+PIXEL_GRID-0.001,cell.y+PIXEL_GRID-0.001));
                    assertEquals(i,menu.hit(cell.x+1.5,cell.y+1.5));
                    if ((cell.edges & Cell.LEFT) != 0) assertEquals(-1,menu.hit(cell.x-0.001,cell.y+1.5));
                    if ((cell.edges & Cell.RIGHT) != 0) assertEquals(-1,menu.hit(cell.x+PIXEL_GRID,cell.y+1.5));
                    if ((cell.edges & Cell.TOP) != 0) assertEquals(-1,menu.hit(cell.x+1.5,cell.y-0.001));
                    if ((cell.edges & Cell.BOTTOM) != 0) assertEquals(-1,menu.hit(cell.x+1.5,cell.y+PIXEL_GRID));
                }
            }
        }
    }
    @Test public void allGridCellsMatchDrawingWithNoOverlappingTargets() {
        KOMEMapRadialMenu menu = open();
        int bound=(int)Math.ceil(OUTER_RADIUS/PIXEL_GRID)+1;
        for (int gy = -bound; gy <= bound; gy++) for (int gx = -bound; gx <= bound; gx++) {
            int expected = -1;
            for (int i = 0; i < menu.segments().size(); i++) if (menu.segments().get(i).containsCell(gx,gy)) {
                assertEquals("No overlapping action cells",-1,expected); expected = i;
            }
            assertEquals(expected,menu.hit(gx*PIXEL_GRID+1.5,gy*PIXEL_GRID+1.5));
        }
    }
    @Test public void closeClearsTheSameStateAsEscape() {
        KOMEMapRadialMenu menu = open(); assertEquals(Action.CLOSE,menu.activate(3));
        assertFalse(menu.isOpen()); assertEquals("",menu.tileId()); assertEquals(State.ROOT,menu.state());
        assertEquals(-1,menu.hit(0,57));
    }
    @Test public void twoPixelGridKeepsAllRootTargetsTheSameNominalThicknessAndWeight() {
        assertEquals(2,PIXEL_GRID);
        KOMEMapRadialMenu menu=open();
        int smallest=Integer.MAX_VALUE,largest=0;
        for (Segment segment : menu.segments()) {
            assertEquals(44,segment.widthDegrees,0);
            double nearest=Double.POSITIVE_INFINITY,farthest=0;
            for (Cell cell : segment.cells) {
                double x=cell.x+PIXEL_GRID/2.0,y=cell.y+PIXEL_GRID/2.0;
                nearest=Math.min(nearest,Math.hypot(x,y));farthest=Math.max(farthest,Math.hypot(x,y));
            }
            assertTrue("Each action reaches the same inner boundary",nearest<INNER_RADIUS+PIXEL_GRID*2);
            assertTrue("Each action reaches the same outer boundary",farthest>OUTER_RADIUS-PIXEL_GRID*2);
            smallest=Math.min(smallest,segment.cells.size());largest=Math.max(largest,segment.cells.size());
        }
        // A shared square grid quantizes different orientations slightly; no action gets a smaller target policy.
        assertTrue("Stepped targets have comparable occupied area",largest-smallest<=largest*0.05);
    }
    @Test public void countAwareLayoutReducesGapsBeforeShrinkingSiblingWidths() {
        double previousGap=Double.POSITIVE_INFINITY;
        for (int count=2;count<=6;count++) {
            java.util.List<Segment> segments=layout(44,-90,actions(count));
            double gap=360.0/count-segments.get(0).widthDegrees;
            assertTrue(gap<previousGap);assertTrue(gap>=MIN_GAP_DEGREES);
            for (Segment segment : segments) assertEquals(44,segment.widthDegrees,0);
            previousGap=gap;
        }
    }
    @Test public void crowdedLayoutsShrinkEverySiblingUniformlyAndKeepMinimumGap() {
        for (int count=7;count<=10;count++) {
            java.util.List<Segment> segments=layout(44,-90,actions(count));
            double width=360.0/count-MIN_GAP_DEGREES;
            assertTrue(width<44);
            for (int i=0;i<count;i++) {
                assertEquals(width,segments.get(i).widthDegrees,1e-9);
                assertEquals(-90+i*360.0/count,segments.get(i).centerDegrees,1e-9);
                assertFalse(segments.get(i).cells.isEmpty());
            }
            assertEquals(MIN_GAP_DEGREES,360.0/count-width,1e-9);
        }
    }
    @Test public void generatedLayoutsAreDeterministicOrderedAndNeverOverlap() {
        for (int count=1;count<=10;count++) {
            Action[] actions=actions(count);
            java.util.List<Segment> first=layout(44,-90,actions),second=layout(44,-90,actions);
            java.util.Set<String> occupied=new java.util.HashSet<String>();
            for (int i=0;i<count;i++) {
                Segment a=first.get(i),b=second.get(i);
                assertSame(actions[i],a.action);assertEquals(a.centerDegrees,b.centerDegrees,0);
                assertEquals(a.widthDegrees,b.widthDegrees,0);assertEquals(a.cells.size(),b.cells.size());
                for (int c=0;c<a.cells.size();c++) {
                    Cell cell=a.cells.get(c),other=b.cells.get(c);
                    assertEquals(cell.x,other.x);assertEquals(cell.y,other.y);assertEquals(cell.edges,other.edges);
                    assertTrue("No sibling cells overlap",occupied.add(cell.x+":"+cell.y));
                }
            }
        }
        assertTrue(layout(44,-90,new Action[0]).isEmpty());
    }
    private static Action[] actions(int count) {
        Action[] choices=Action.values(),result=new Action[count];
        for(int i=0;i<count;i++)result[i]=choices[i%choices.length];
        return result;
    }
    @Test public void guiScalePreservesSmallNormalAndCapsLargerPhysicalGrowth() {
        assertEquals(1,scaleForGuiFactor(1),0); assertEquals(1,scaleForGuiFactor(2),0);
        assertEquals(0.85,scaleForGuiFactor(3),1e-9); assertEquals(0.6375,scaleForGuiFactor(4),1e-9);
        for (int factor = 3; factor <= 16; factor++) assertEquals(2.55,scaleForGuiFactor(factor)*factor,1e-9);
        assertEquals(42,INNER_RADIUS,0); assertEquals(72,OUTER_RADIUS,0); assertEquals(0.9,FONT_SCALE,0);
        double largeDiameterRatio = OUTER_RADIUS*scaleForGuiFactor(3)/74;
        assertTrue(largeDiameterRatio >= 0.8 && largeDiameterRatio <= 0.85);
    }
    @Test public void scaledHitCoordinatesStayAlignedAtAllGuiFactors() {
        KOMEMapRadialMenu menu = open();
        for (int factor : new int[] {1,2,3,4,8}) {
            double scale = scaleForGuiFactor(factor);
            for (int i = 0; i < menu.segments().size(); i++) for (Cell cell : menu.segments().get(i).cells)
                assertEquals(i,menu.hit((cell.x+1.5)*scale,(cell.y+1.5)*scale,scale));
            assertEquals(-1,menu.hit(0,-57*scale,scale)); // Root's intentionally empty top.
            assertEquals(-1,menu.hit(0,0,scale));
        }
    }
    @Test public void invalidCoordinatesScalesAndIndicesFailClosed() {
        KOMEMapRadialMenu menu = open();
        assertEquals(-1,menu.hit(Double.NaN,0)); assertEquals(-1,menu.hit(Double.POSITIVE_INFINITY,0));
        assertEquals(-1,menu.hit(0,57,0)); assertEquals(-1,menu.hit(0,57,Double.NaN));
        assertNull(menu.activate(-1)); assertNull(menu.activate(5));
    }
    private static KOMEMapRadialMenu open() { KOMEMapRadialMenu menu = new KOMEMapRadialMenu(); assertTrue(menu.open("T357")); return menu; }
}
