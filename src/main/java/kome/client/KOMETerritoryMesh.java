package kome.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Exact axis-aligned rectangle union: one winning band, and therefore one alpha contribution, per point.
 * All input rectangles have already been clipped to owned land. Priorities separate the pale rim
 * from the four faction bands. Different owners cannot overlap in that input. No pixel rasterization. */
final class KOMETerritoryMesh {
    static final int MAX_CACHED_QUADS = 250_000;
    static final int VERTEX_BYTES = 16;
    final Rectangles rectangles;
    private ByteBuffer vertices;

    private KOMETerritoryMesh(Rectangles rectangles) { this.rectangles = rectangles; }
    int quads() { return rectangles.size; }
    long retainedBytes() { return rectangles.xy.length * 8L + rectangles.argb.length * 4L + (vertices == null ? 0 : vertices.capacity()); }
    boolean cacheable() { return quads() <= MAX_CACHED_QUADS; }
    boolean verticesPrepared() { return vertices != null; }

    /** OpenGL 1.1 C4UB_V3F interleaved layout. Construct once; submission does not replay Java quads. */
    ByteBuffer vertices() {
        if (!cacheable()) throw new IllegalStateException("Oversized meshes use the complete streaming path");
        if (vertices == null) {
            long profileStart=KOMEMapProfiler.start();
            ByteBuffer complete = ByteBuffer.allocateDirect(quads() * 4 * VERTEX_BYTES).order(ByteOrder.nativeOrder());
            for (int i=0;i<quads();i++) {
                int p=i*4,c=rectangles.argb[i];
                vertex(complete,rectangles.xy[p],rectangles.xy[p+3],c);
                vertex(complete,rectangles.xy[p+2],rectangles.xy[p+3],c);
                vertex(complete,rectangles.xy[p+2],rectangles.xy[p+1],c);
                vertex(complete,rectangles.xy[p],rectangles.xy[p+1],c);
            }
            complete.flip(); vertices=complete;
            KOMEMapProfiler.elapsed(KOMEMapProfiler.Stage.VERTICES,profileStart);
        }
        vertices.position(0); return vertices;
    }

    private static void vertex(ByteBuffer b,double x,double y,int c) {
        b.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)(c>>>24));
        b.putFloat((float)x).putFloat((float)y).putFloat(0);
    }

    void draw(KOMEMapViewport view, KOMEMapViewport builtView, KOMEMapBorders.QuadSink sink) {
        double scale=view.scaleFrom(builtView),dx=view.offsetXFrom(builtView),dy=view.offsetYFrom(builtView);
        for(int i=0;i<quads();i++) {
            int p=i*4;double x0=Math.max(view.left,rectangles.xy[p]*scale+dx),y0=Math.max(view.top,rectangles.xy[p+1]*scale+dy);
            double x1=Math.min(view.right,rectangles.xy[p+2]*scale+dx),y1=Math.min(view.bottom,rectangles.xy[p+3]*scale+dy);
            if(x1>x0 && y1>y0)sink.quad(x0,y0,x1,y1,rectangles.argb[i]);
        }
    }

    /** Cropping a disjoint partition preserves its colors, fade and single alpha contribution.
     * Count first so an over-budget fallback never allocates another large rectangle array. */
    KOMETerritoryMesh clipped(KOMEMapViewport view,KOMEMapViewport builtView,int limit) {
        double scale=view.scaleFrom(builtView),dx=view.offsetXFrom(builtView),dy=view.offsetYFrom(builtView);
        int count=0;
        for(int i=0;i<quads();i++) {
            int p=i*4;
            if(Math.min(view.right,rectangles.xy[p+2]*scale+dx)>Math.max(view.left,rectangles.xy[p]*scale+dx)
                    && Math.min(view.bottom,rectangles.xy[p+3]*scale+dy)>Math.max(view.top,rectangles.xy[p+1]*scale+dy)
                    && ++count>limit)return null;
        }
        Rectangles output=new Rectangles(limit);
        draw(view,builtView,(x,y,r,b,c)->output.add(x,y,r,b,c,1));
        output.trim();return new KOMETerritoryMesh(output);
    }

    static final class Rectangles {
        private final int limit;
        Rectangles(){this(Integer.MAX_VALUE/4);}
        Rectangles(int limit){this.limit=limit;}
        double[] xy = new double[4096];
        int[] argb = new int[1024];
        byte[] priority = new byte[1024];
        int size;
        int add(double x0,double y0,double x1,double y1,int c,int rank) {
            if(x1<=x0 || y1<=y0)return -1;
            if(size>=limit)throw new LimitExceeded();
            if(size==argb.length){int cap=Math.multiplyExact(size,2);xy=Arrays.copyOf(xy,cap*4);argb=Arrays.copyOf(argb,cap);priority=Arrays.copyOf(priority,cap);}
            int p=size*4;xy[p]=x0;xy[p+1]=y0;xy[p+2]=x1;xy[p+3]=y1;argb[size]=c;priority[size]=(byte)rank;
            return size++;
        }
        void trim() { xy=Arrays.copyOf(xy,size*4);argb=Arrays.copyOf(argb,size);priority=new byte[0]; }
    }

    static KOMETerritoryMesh union(Rectangles input) {
        return union(input,Integer.MAX_VALUE/4);
    }
    static KOMETerritoryMesh union(Rectangles input,int limit) {
        long profileStart=KOMEMapProfiler.start();
        try{return partition(input,limit);}
        finally{KOMEMapProfiler.elapsed(KOMEMapProfiler.Stage.PARTITION,profileStart);}
    }
    static final class LimitExceeded extends RuntimeException {
        LimitExceeded(){super(null,null,false,false);}
    }
    private static KOMETerritoryMesh partition(Rectangles input,int limit) {
        Rectangles output=new Rectangles(limit);int n=input.size;
        if(n==0){output.trim();return new KOMETerritoryMesh(output);}
        double[] xs=new double[n*2],ys=new double[n*2];
        for(int i=0;i<n;i++){int p=i*4;xs[i*2]=input.xy[p];xs[i*2+1]=input.xy[p+2];ys[i*2]=input.xy[p+1];ys[i*2+1]=input.xy[p+3];}
        xs=unique(xs);ys=unique(ys);
        int[] left=new int[n],right=new int[n],active=new int[n],position=new int[n];Arrays.fill(position,-1);
        long[] events=new long[n*2],xEvents=new long[n*2];
        for(int i=0;i<n;i++) {
            int p=i*4;left[i]=Arrays.binarySearch(xs,input.xy[p]);right[i]=Arrays.binarySearch(xs,input.xy[p+2]);
            events[i*2]=event(Arrays.binarySearch(ys,input.xy[p+1]),i,false);
            events[i*2+1]=event(Arrays.binarySearch(ys,input.xy[p+3]),i,true);
        }
        Arrays.sort(events);int cursor=0,activeCount=0;
        Map<Long,Integer> previous=new HashMap<>(),current=new HashMap<>();
        int[] counts=new int[6],colors=new int[6];
        while(cursor<events.length) {
            int y=(int)(events[cursor]>>>32);
            do {
                long event=events[cursor++];int id=(int)event>>>1;
                if((event&1)==0){position[id]=activeCount;active[activeCount++]=id;}
                else{int at=position[id],moved=active[--activeCount];active[at]=moved;position[moved]=at;position[id]=-1;}
            } while(cursor<events.length && (int)(events[cursor]>>>32)==y);
            if(cursor==events.length)break;
            int nextY=(int)(events[cursor]>>>32),used=0;
            for(int i=0;i<activeCount;i++){int id=active[i];xEvents[used++]=event(left[id],id,false);xEvents[used++]=event(right[id],id,true);}
            Arrays.sort(xEvents,0,used);Arrays.fill(counts,0);current.clear();
            int xCursor=0,spanStart=-1,spanEnd=-1,spanColor=0;
            while(xCursor<used) {
                int x=(int)(xEvents[xCursor]>>>32);
                do {
                    long event=xEvents[xCursor++];int id=(int)event>>>1,rank=input.priority[id];
                    if((event&1)==0){counts[rank]++;colors[rank]=input.argb[id];}else counts[rank]--;
                }while(xCursor<used && (int)(xEvents[xCursor]>>>32)==x);
                int rank=5;while(rank>0 && counts[rank]==0)rank--;
                int end=xCursor==used?x:(int)(xEvents[xCursor]>>>32),c=rank==0?0:colors[rank];
                if(c!=0 && end>x && spanEnd==x && spanColor==c)spanEnd=end;
                else {
                    if(spanStart>=0)append(output,previous,current,xs,ys,y,nextY,spanStart,spanEnd,spanColor);
                    spanStart=c==0 || end==x?-1:x;spanEnd=end;spanColor=c;
                }
            }
            if(spanStart>=0)append(output,previous,current,xs,ys,y,nextY,spanStart,spanEnd,spanColor);
            Map<Long,Integer> swap=previous;previous=current;current=swap;
        }
        output.trim();return new KOMETerritoryMesh(output);
    }

    private static void append(Rectangles output,Map<Long,Integer> previous,Map<Long,Integer> current,
            double[] xs,double[] ys,int y,int nextY,int start,int end,int color) {
        long key=(long)start<<32 | end&0xFFFFFFFFL;Integer old=previous.get(key);int index;
        if(old!=null && output.argb[old]==color && output.xy[old*4+3]==ys[y]){index=old;output.xy[index*4+3]=ys[nextY];}
        else index=output.add(xs[start],ys[y],xs[end],ys[nextY],color,0);
        current.put(key,index);
    }
    private static long event(int coordinate,int id,boolean remove){return (long)coordinate<<32 | (long)id<<1 | (remove?1:0);}
    private static double[] unique(double[] values) {
        Arrays.sort(values);int used=0;for(double value:values)if(used==0 || values[used-1]!=value)values[used++]=value;
        return Arrays.copyOf(values,used);
    }
}
