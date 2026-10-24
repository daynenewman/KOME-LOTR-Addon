package kome.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMETerritoryMeshTest {
    @Test public void sweepPreservesCoverageAndOneWinningContributionForOverlapsBendsAndAdjacentStrips() {
        Random random=new Random(8501);
        for(int trial=0;trial<12;trial++) {
            KOMETerritoryMesh.Rectangles input=new KOMETerritoryMesh.Rectangles();
            for(int i=0;i<35;i++) {
                int rank=1+random.nextInt(5);int[] alpha={0,36,96,176,255,255};
                int x=random.nextInt(18),y=random.nextInt(18);
                input.add(x,y,x+1+random.nextInt(7),y+1+random.nextInt(7),alpha[rank]<<24 | (rank==5?0xB0AD9F:0xF9F9F9),rank);
            }
            KOMETerritoryMesh mesh=KOMETerritoryMesh.union(input);
            for(double y=0.25;y<26;y+=0.5)for(double x=0.25;x<26;x+=0.5) {
                int rank=0,expected=0;
                for(int i=0;i<input.size;i++)if(covers(input,i,x,y) && input.priority[i]>rank){rank=input.priority[i];expected=input.argb[i];}
                double alpha=0,red=0;int contributions=0;
                for(int i=0;i<mesh.quads();i++)if(covers(mesh.rectangles,i,x,y)) {
                    int c=mesh.rectangles.argb[i];double a=(c>>>24)/255D;
                    red=(c>>16&255)/255D*a+red*(1-a);alpha=a+alpha*(1-a);contributions++;
                }
                assertEquals((expected>>>24)/255D,alpha,1e-12);
                assertEquals((expected>>16&255)/255D*(expected>>>24)/255D,red,1e-12);
                assertTrue(contributions<=1);
            }
        }
    }

    @Test public void touchingRectanglesCoalesceAndInterleavedVerticesAreReusedWithExactLayout() {
        KOMETerritoryMesh.Rectangles input=new KOMETerritoryMesh.Rectangles();
        for(int y=0;y<20;y++)for(int x=0;x<20;x++)input.add(x,y,x+1,y+1,0x60F9F9F9,2);
        KOMETerritoryMesh mesh=KOMETerritoryMesh.union(input);assertEquals(1,mesh.quads());
        ByteBuffer buffer=mesh.vertices();assertTrue(buffer.isDirect());assertSame(buffer,mesh.vertices());
        assertEquals(64,buffer.remaining());ByteBuffer read=buffer.duplicate().order(ByteOrder.nativeOrder());
        assertEquals(249,read.get()&255);assertEquals(249,read.get()&255);assertEquals(249,read.get()&255);assertEquals(96,read.get()&255);
        assertEquals(0,read.getFloat(),0);assertEquals(20,read.getFloat(),0);assertEquals(0,read.getFloat(),0);
        assertEquals(100,mesh.retainedBytes());assertEquals(4,buffer.remaining()/KOMETerritoryMesh.VERTEX_BYTES);
    }

    @Test public void cacheBudgetPreservesOversizedGeometryForStreamingRatherThanTruncating() {
        KOMETerritoryMesh.Rectangles input=new KOMETerritoryMesh.Rectangles();
        // Disjoint alternating colors cannot be merged. Exercise the retained-memory limit directly.
        for(int x=0;x<=KOMETerritoryMesh.MAX_CACHED_QUADS;x++)input.add(x*2,0,x*2+1,1,0xFFF9F9F9,4);
        KOMETerritoryMesh mesh=KOMETerritoryMesh.union(input);
        assertEquals(KOMETerritoryMesh.MAX_CACHED_QUADS+1,mesh.quads());assertFalse(mesh.cacheable());
        try{mesh.vertices();fail();}catch(IllegalStateException expected){}
        assertEquals(mesh.quads()*36L,mesh.retainedBytes());
    }

    private static boolean covers(KOMETerritoryMesh.Rectangles rectangles,int i,double x,double y) {
        int p=i*4;return x>=rectangles.xy[p] && x<rectangles.xy[p+2] && y>=rectangles.xy[p+1] && y<rectangles.xy[p+3];
    }
}
