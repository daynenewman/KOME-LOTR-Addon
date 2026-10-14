package kome.client;

import java.util.*;



/** Shared bounded rolling list; projection type and filtering do not grant gameplay authority. */
class KOMECompanyListWindow<R> {
    static final int MAX_ROWS=23;
    private final class Group {
        final int index,start,nextStart;
        final String after,token;
        final List<R> rows;
        final boolean more;
        Group(int index,int start,List<R> raw,String after,String token,boolean more,List<R> rows) {
            this.index=index;this.start=start;nextStart=start+raw.size();this.after=after;this.token=token;
            this.rows=new ArrayList<R>(rows);this.more=more;
        }
    }
    private final TreeMap<Integer,Group> groups=new TreeMap<Integer,Group>();
    private List<R> rows=Collections.emptyList();
    private int top,left,right;
    private Group tail;
    private boolean partialEvidence;
    private final java.util.function.Function<R,String> identity;
    private java.util.function.Predicate<R> filter=row->true;
    KOMECompanyListWindow(java.util.function.Function<R,String> identity){this.identity=identity;}
    void clear(){groups.clear();rows=Collections.emptyList();top=0;left=right=0;tail=null;partialEvidence=false;}
    void reset(int index,int start,List<R> raw,String after,String token,boolean more,List<R> matching){clear();left=right=index;update(index,start,raw,after,token,more,matching);}
    void update(int index,int start,List<R> raw,String after,String token,boolean more,List<R> matching) {
        String anchor=top<rows.size()?identity.apply(rows.get(top)):"";
        Group group=new Group(index,start,raw,after,token,more,matching);
        if(!matching.isEmpty())groups.put(index,group);else groups.remove(index);
        if(tail==null||index>=right){right=index;tail=group;}
        left=Math.min(left,index);rebuild(anchor);
        // Discard excluded evidence before visible matches. Sparse searches must
        // not shorten the viewport just because intervening raw pages are full.
        for(Group cached:groups.values()) {
            if(rawCount()<=MAX_ROWS)break;
            if(cached.index==index)continue;
            for(Iterator<R> it=cached.rows.iterator();it.hasNext()&&rawCount()>MAX_ROWS;) {
                if(!filter.test(it.next())){it.remove();partialEvidence=true;}
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
        List<R> merged=new ArrayList<R>();for(Group g:groups.values())for(R row:g.rows)if(filter.test(row))merged.add(row);
        rows=Collections.unmodifiableList(merged);
        int found=-1;for(int i=0;i<rows.size();i++)if(identity.apply(rows.get(i)).equals(anchor)){found=i;break;}
        top=found>=0?found:Math.min(top,Math.max(0,rows.size()-1));
    }
    boolean hasPartialEvidence(){return partialEvidence;}
    int retainedProjections(){return rawCount();}
    private int rawCount(){int count=0;for(Group group:groups.values())count+=group.rows.size();return count;}
    void refilter(java.util.function.Predicate<R> next){filter=next;rebuild("");top=0;}
    List<R> rows(){return rows;}
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
        for(Group g:groups.values())for(R row:g.rows)if(identity.apply(row).equals(company))return g.index;
        return -1;
    }
}
