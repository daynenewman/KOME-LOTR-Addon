package kome.client;

import java.util.*;
import kome.common.data.*;
import kome.common.network.*;

/** Origin-first navigation only. All final evaluation/submission is owned by the existing selector. */
final class KOMECompanyOriginWorkflow {
    enum Stage { CLOSED, SOURCE, PICK, PREVIEW }
    private static final class Cursor {
        final String after,token;final int start;
        Cursor(String after,String token,int start){this.after=after;this.token=token;this.start=start;}
    }
    private final KOMECompanyDestinationClientState connection;
    private UUID session;
    private long sequence,retryAt;
    private final List<Cursor> history=new ArrayList<Cursor>();
    // Three eight-row metadata pages; no routes or world entities are cached here.
    private final TreeMap<Integer,KOMECompanySourceOptions> pages=new TreeMap<Integer,KOMECompanySourceOptions>();
    private KOMEPacketCompanyOriginRequest pending;
    private int pendingPage,top,step,capacity=4;
    private Stage stage=Stage.CLOSED;
    private String source="",selectedCompanyId="",selectedName="",message="";
    KOMECompanyOriginWorkflow(KOMECompanyDestinationClientState connection){this.connection=connection;}
    boolean active(){
        if(stage!=Stage.CLOSED&&(!connection.isConnected()||!connection.isSession(session)))clear();
        return stage!=Stage.CLOSED;
    }
    Stage stage(){active();return stage;}
    String source(){return active()?source:"";}
    String selectedCompanyId(){return active()?selectedCompanyId:"";}
    String selectedName(){return active()?selectedName:"";}
    void clear(){stage=Stage.CLOSED;session=null;pending=null;pages.clear();history.clear();source=selectedCompanyId=selectedName="";top=0;step=0;message="";}
    KOMEPacketCompanyOriginRequest open(String tile){
        clear();if(!connection.isConnected())return null;source=tile;stage=Stage.SOURCE;return reload();
    }
    void adoptSession(){session=connection.sessionId();pending=null;}
    KOMEPacketCompanyOriginRequest reload(){
        connection.close();adoptSession();sequence=0;pages.clear();history.clear();history.add(new Cursor("","",0));
        top=0;step=0;selectedCompanyId=selectedName="";stage=Stage.SOURCE;return request(0);
    }
    private KOMEPacketCompanyOriginRequest request(int page){
        if(!active()||pending!=null)return null;
        pendingPage=page;Cursor cursor=history.get(page);
        pending=new KOMEPacketCompanyOriginRequest(session,++sequence,source,cursor.after,cursor.after.isEmpty()?"":cursor.token);
        message="Loading companies...";return pending;
    }
    boolean accept(KOMECompanySourceOptions response,long now){
        if(!active()||stage!=Stage.SOURCE||pending==null||!session.equals(response.session)||pending.sequence!=response.sequence||!source.equals(response.source))return false;
        pending=null;
        if(response.result==KOMECompanyDestinationOptions.Result.RATE_LIMITED){
            retryAt=now+Math.max(1000,response.retryAfterMillis);message="More companies available shortly";return true;
        }
        if(response.result!=KOMECompanyDestinationOptions.Result.OK){
            pages.clear();top=0;step=0;message="Company list changed or could not load. Refresh to try again.";return true;
        }
        String token=history.get(pendingPage).token;
        if(!token.isEmpty()&&!token.equals(response.token)){pages.clear();message="Company list changed. Refresh to try again.";return true;}
        Cursor old=history.get(pendingPage);history.set(pendingPage,new Cursor(old.after,response.token,old.start));
        pages.put(pendingPage,response);
        while(pages.size()>3){
            int first=pages.firstKey(),last=pages.lastKey();
            pages.remove(Math.abs(first-pendingPage)>Math.abs(last-pendingPage)?first:last);
        }
        top=Math.max(first(),Math.min(Math.max(first(),end()-capacity),top+step));step=0;
        message=response.rows.isEmpty()?"No controlled Campaign companies on this tile.":"Choose a company, then its destination.";
        return true;
    }
    List<KOMECompanySourceOptions.Row> rows(){
        if(!active())return Collections.emptyList();
        List<KOMECompanySourceOptions.Row> rows=new ArrayList<KOMECompanySourceOptions.Row>();
        for(KOMECompanySourceOptions p:pages.values())rows.addAll(p.rows);return rows;
    }
    private int first(){return pages.isEmpty()?0:history.get(pages.firstKey()).start;}
    private int end(){return pages.isEmpty()?0:history.get(pages.lastKey()).start+pages.lastEntry().getValue().rows.size();}
    int offset(){return top-first();}
    boolean loading(){return pending!=null;}
    String message(){return message;}
    void capacity(int value){capacity=Math.max(1,value);top=Math.max(first(),Math.min(top,Math.max(first(),end()-capacity)));}
    KOMECompanySourceOptions.Row visible(int index){
        List<KOMECompanySourceOptions.Row> list=rows();int backing=offset()+index;
        return index<0||backing<0||backing>=list.size()?null:list.get(backing);
    }
    KOMEPacketCompanyOriginRequest scroll(int direction,int visible,long now){
        if(!active()||stage!=Stage.SOURCE||loading()||now<retryAt)return null;
        capacity(visible);int next=top+direction;
        if(next>=first()&&next+capacity<=end()){top=next;return null;}
        if(pages.isEmpty())return request(pendingPage);
        if(direction>0) {
            int last=pages.lastKey();KOMECompanySourceOptions page=pages.get(last);
            if(!page.more)return null;
            while(history.size()>last+1)history.remove(history.size()-1);
            if(history.size()>=KOMECompanyDestinationOptions.MAX_CATALOGUE)return null;
            history.add(new Cursor(page.next,page.token,end()));step=1;return request(last+1);
        }
        int first=pages.firstKey();if(first==0)return null;step=-1;return request(first-1);
    }
    boolean select(int visible){
        if(stage()!=Stage.SOURCE||loading())return false;KOMECompanySourceOptions.Row row=visible(visible);if(row==null)return false;
        selectedCompanyId=row.companyId;selectedName=name(row);stage=Stage.PICK;pending=null;return true;
    }
    void preview(){stage=Stage.PREVIEW;adoptSession();}
    void pickAgain(){stage=Stage.PICK;adoptSession();}
    KOMEPacketCompanyOriginRequest backToSource(){return reload();}
    static String name(KOMECompanySourceOptions.Row row){
        return row.name==null||row.name.trim().isEmpty()||row.name.equals(row.companyId)?"Campaign Company":row.name;
    }
}