package kome.client;

import java.util.*;
import kome.common.data.KOMECompanyDestinationOptions;
import kome.common.data.KOMECompanyDestinationOptions.Row;

/** Rolling presentation only: at most 23 raw row projections (each route at most 64 tiles).
 * The destination state separately owns its current <=8-row authoritative query.
 * One independently retained selected row reserves the final projection (32 total).
 * Cursor history contains no route projections. Cached rows must be revalidated before submission.
 */
final class KOMECompanyBrowseWindow {
    static final int MAX_ROWS=23;
    private static final class Group {
        final int index,start,nextStart;
        final String after,token;
        final List<Row> rows;
        final boolean more;
        Group(int index,int start,KOMECompanyDestinationOptions page,List<Row> rows) {
            this.index=index;this.start=start;nextStart=start+page.rows.size();after=page.nextCompanyId;token=page.catalogueToken;
            this.rows=new ArrayList<Row>(rows);more=page.hasMore;
        }
    }
    private final TreeMap<Integer,Group> groups=new TreeMap<Integer,Group>();
    private List<Row> rows=Collections.emptyList();
    private int top,left,right;
    private Group tail;
    private boolean partialEvidence;
    private KOMECompanyDestinationSelector.Filter filter=KOMECompanyDestinationSelector.Filter.ALL;
    void clear(){groups.clear();rows=Collections.emptyList();top=0;left=right=0;tail=null;partialEvidence=false;}
    void reset(int index,int start,KOMECompanyDestinationOptions page,List<Row> matching) {
        clear();left=right=index;update(index,start,page,matching);
    }
    void update(int index,int start,KOMECompanyDestinationOptions page,List<Row> matching) {
        String anchor=top<rows.size()?rows.get(top).companyId:"";
        Group group=new Group(index,start,page,matching);
        if(!matching.isEmpty())groups.put(index,group);else groups.remove(index);
        if(tail==null||index>=right){right=index;tail=group;}
        left=Math.min(left,index);rebuild(anchor);
        // Discard excluded evidence before visible matches. Sparse searches must
        // not shorten the viewport just because intervening raw pages are full.
        for(Group cached:groups.values()) {
            if(rawCount()<=MAX_ROWS)break;
            if(cached.index==index)continue;
            for(Iterator<Row> it=cached.rows.iterator();it.hasNext()&&rawCount()>MAX_ROWS;) {
                if(!filter.matches(it.next())){it.remove();partialEvidence=true;}
            }
        }
        groups.entrySet().removeIf(entry->entry.getValue().rows.isEmpty());
        while(rawCount()>MAX_ROWS) {
            boolean removeFirst=index>=groups.lastKey();
            groups.remove(removeFirst?groups.firstKey():groups.lastKey());
            if(removeFirst)left=groups.firstKey();else {right=groups.lastKey();tail=groups.get(right);}
            rebuild(anchor);
        }
    }
    private void rebuild(String anchor) {
        List<Row> merged=new ArrayList<Row>();for(Group g:groups.values())for(Row row:g.rows)if(filter.matches(row))merged.add(row);
        rows=Collections.unmodifiableList(merged);
        int found=-1;for(int i=0;i<rows.size();i++)if(rows.get(i).companyId.equals(anchor)){found=i;break;}
        top=found>=0?found:Math.min(top,Math.max(0,rows.size()-1));
    }
    boolean hasPartialEvidence(){return partialEvidence;}
    int retainedProjections(){return rawCount();}
    private int rawCount(){int count=0;for(Group group:groups.values())count+=group.rows.size();return count;}
    void refilter(KOMECompanyDestinationSelector.Filter next){filter=next;rebuild("");top=0;}
    List<Row> rows(){return rows;}
    int top(){return top;}
    void position(int offset,int capacity){top=Math.max(0,Math.min(Math.max(0,rows.size()-capacity),offset));}
    boolean canMove(int direction,int capacity){return direction<0?top>0:top+capacity<rows.size();}
    void move(int direction,int capacity){position(top+direction,capacity);}
    boolean needsPage(int direction,int capacity){return direction<0?top==0&&earlier():top+capacity>=rows.size()&&later();}
    boolean earlier(){return left>0;}
    boolean later(){return tail!=null&&tail.more;}
    int previousPage(){return left-1;}
    int nextPage(){return right+1;}
    String nextAfter(){return tail.after;}
    String token(){return tail.token;}
    int nextStart(){return tail.nextStart;}
    int pageOf(String company) {
        for(Group g:groups.values())for(Row row:g.rows)if(row.companyId.equals(company))return g.index;
        return -1;
    }
}
