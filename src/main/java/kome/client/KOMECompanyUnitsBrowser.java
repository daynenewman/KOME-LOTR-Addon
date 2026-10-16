package kome.client;

import java.util.*;
import kome.common.data.*;
import kome.common.data.KOMECompanySourceOptions.Row;
import kome.common.network.KOMEPacketCompanyOriginRequest;

/** Metadata-only client. The sole outgoing operation is the existing read-only source query. */
final class KOMECompanyUnitsBrowser {
    private static final class Cursor {
        final String after,token;final int start;
        Cursor(String after,String token,int start){this.after=after;this.token=token;this.start=start;}
    }
    private final KOMECompanyDestinationClientState connection;
    private final KOMECompanyListWindow<Row> window=new KOMECompanyListWindow<Row>(row->row.companyId);
    private final List<Cursor> history=new ArrayList<>();
    private UUID session;
    private String tile="",selectedCompanyId="",message="";
    private Row selected;
    private KOMEPacketCompanyOriginRequest pending;
    private long sequence,retryAt;
    private int pendingPage,step,capacity=4;
    KOMECompanyUnitsBrowser(KOMECompanyDestinationClientState connection){this.connection=connection;}
    boolean active(){if(session!=null&&(!connection.isConnected()||!connection.isSession(session)))clear();return session!=null;}
    void clear(){session=null;pending=null;history.clear();window.clear();tile=selectedCompanyId=message="";selected=null;sequence=retryAt=0;step=0;}
    KOMEPacketCompanyOriginRequest open(String tile){
        clear();if(!connection.isConnected())return null;connection.close();session=connection.sessionId();this.tile=tile;
        history.add(new Cursor("","",0));return request(0);
    }
    KOMEPacketCompanyOriginRequest refresh(){String current=tile;return open(current);}
    private KOMEPacketCompanyOriginRequest request(int index){
        if(!active()||pending!=null)return null;pendingPage=index;Cursor cursor=history.get(index);
        pending=new KOMEPacketCompanyOriginRequest(session,++sequence,tile,cursor.after,cursor.after.isEmpty()?"":cursor.token);
        message="Loading companies...";return pending;
    }
    boolean accept(KOMECompanySourceOptions response,long now){
        if(!active()||pending==null||!session.equals(response.session)||pending.sequence!=response.sequence||!tile.equals(response.source))return false;
        pending=null;
        if(response.result==KOMECompanyDestinationOptions.Result.RATE_LIMITED){retryAt=now+Math.max(1000,response.retryAfterMillis);message="More companies available shortly";step=0;return true;}
        if(response.result!=KOMECompanyDestinationOptions.Result.OK){invalidate("Company list changed or could not load. Refresh to try again.");return true;}
        retryAt=0;Cursor cursor=history.get(pendingPage);
        if(!cursor.token.isEmpty()&&!cursor.token.equals(response.token)){invalidate("Company list changed. Refresh to try again.");return true;}
        history.set(pendingPage,new Cursor(cursor.after,response.token,cursor.start));
        window.update(pendingPage,cursor.start,response.rows,response.next,response.token,response.more,response.rows);
        if(selected!=null){
            boolean found=false;for(Row row:response.rows)if(row.companyId.equals(selectedCompanyId)){selected=row;found=true;break;}
            if(!found&&selectedCompanyId.compareTo(cursor.after)>0&&(!response.more||selectedCompanyId.compareTo(response.next)<=0))clearSelection();
        }
        if(step!=0&&window.canMove(step,capacity))window.move(step,capacity);step=0;window.position(window.top(),capacity);
        message=window.rows().isEmpty()?"No Campaign companies on this tile.":"Select a company to inspect.";return true;
    }
    private void invalidate(String reason){window.clear();clearSelection();step=0;retryAt=0;message=reason;}
    KOMEPacketCompanyOriginRequest scroll(int direction,int capacity,long now){
        this.capacity=Math.max(1,capacity);if(!active()||pending!=null||now<retryAt)return null;
        if(window.canMove(direction,this.capacity)){window.move(direction,this.capacity);return null;}
        if(history.isEmpty())return null;
        if(window.rows().isEmpty())return retryAt>0?request(pendingPage):null;
        if(!window.needsPage(direction,this.capacity))return null;
        int index=direction<0?window.previousPage():window.nextPage();
        if(index<0||index>=KOMECompanyDestinationOptions.MAX_CATALOGUE)return null;
        if(direction>0){while(history.size()>index)history.remove(history.size()-1);history.add(new Cursor(window.nextAfter(),window.token(),window.nextStart()));}
        step=direction;return request(index);
    }
    List<Row> rows(){return active()?window.rows():Collections.<Row>emptyList();}
    Row visible(int index){int position=offset()+index;return index<0||position>=rows().size()?null:rows().get(position);}
    int offset(){return window.top();}
    void capacity(int value){capacity=Math.max(1,value);window.position(window.top(),capacity);}
    void select(int index){Row row=visible(index);if(row==null)return;if(row.companyId.equals(selectedCompanyId))clearSelection();else{selectedCompanyId=row.companyId;selected=row;}}
    void clearSelection(){selectedCompanyId="";selected=null;}
    String selectedCompanyId(){return active()?selectedCompanyId:"";}
    Row selected(){return active()?selected:null;}
    String tile(){return active()?tile:"";}
    String message(){active();return message;}
    boolean loading(){return pending!=null;}
    boolean earlier(){return window.earlier();}
    boolean later(){return window.later();}
    int retainedRows(){return window.retainedProjections()+(selected==null?0:1);}
    static String name(Row row){return row.name.trim().isEmpty()||row.name.trim().equals(row.companyId)?"Campaign Company":row.name;}
    static String population(Row row){return row.units+" units | Pop "+(row.populationCenti<0?"unknown":java.math.BigDecimal.valueOf(row.populationCenti,2).stripTrailingZeros().toPlainString());}
}
