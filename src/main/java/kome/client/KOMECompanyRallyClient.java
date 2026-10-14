package kome.client;

import java.util.*;
import kome.common.data.*;
import kome.common.network.KOMEPacketCompanyRallyRequest;
import static kome.common.data.KOMECompanyRallySnapshot.*;

/** Compact emergency Rally state. Complete server rows authorize confirmation; no company picker or player Join. */
final class KOMECompanyRallyClient {
    private final KOMECompanyDestinationClientState connection;
    private final java.util.function.LongSupplier clock;
    private long expiresAt;
    private UUID session,token=new UUID(0,0);
    private String destination="",error="";
    private final Map<Integer,Entry> entries=new HashMap<Integer,Entry>();
    private KOMECompanyRallySnapshot latest;
    private int revision=-1;
    private boolean confirmSent;
    KOMECompanyRallyClient(KOMECompanyDestinationClientState connection){this(connection,()->System.nanoTime()/1000000L);}
    KOMECompanyRallyClient(KOMECompanyDestinationClientState connection,java.util.function.LongSupplier clock){this.connection=connection;this.clock=clock;}
    boolean active(){
        if(session!=null&&(!connection.isConnected()||!connection.isSession(session)))clear();
        if(session!=null&&!confirmSent&&error.isEmpty()&&clock.getAsLong()>=expiresAt){
            entries.clear();latest=null;token=new UUID(0,0);error="Rally preview expired. Click Rally Here to refresh.";
        }
        return session!=null;
    }
    void clear(){session=null;entries.clear();latest=null;destination=error="";expiresAt=0;revision=-1;confirmSent=false;token=new UUID(0,0);}
    KOMEPacketCompanyRallyRequest open(String tile){
        clear();if(!connection.isConnected())return null;
        connection.close();session=connection.sessionId();destination=tile;expiresAt=clock.getAsLong()+LIFETIME_MILLIS;
        return new KOMEPacketCompanyRallyRequest(KOMEPacketCompanyRallyRequest.Action.OPEN,session,token,tile);
    }
    KOMEPacketCompanyRallyRequest cancel(){
        KOMEPacketCompanyRallyRequest request=active()?new KOMEPacketCompanyRallyRequest(KOMEPacketCompanyRallyRequest.Action.CANCEL,session,token,destination):null;
        clear();return request;
    }
    boolean accept(KOMECompanyRallySnapshot snapshot){
        if(!active()||!session.equals(snapshot.session)||!destination.equals(snapshot.destination)||!error.isEmpty()&&snapshot.phase!=Phase.ERROR)return false;
        boolean rejection=snapshot.phase==Phase.ERROR&&snapshot.token.equals(new UUID(0,0))&&confirmSent&&latest!=null&&latest.phase==Phase.READY;
        if(snapshot.revision<=revision&&!rejection)return false;
        if(!token.equals(new UUID(0,0))&&!token.equals(snapshot.token)&&snapshot.phase!=Phase.ERROR)return false;
        if(latest!=null&&(snapshot.total!=latest.total||snapshot.evaluated<latest.evaluated||snapshot.processed<latest.processed)
                &&snapshot.phase!=Phase.ERROR)return false;
        if(confirmSent&&(snapshot.phase==Phase.LOADING||snapshot.phase==Phase.READY))return false;
        if(snapshot.entry!=null){
            Entry entry=snapshot.entry,old=entries.get(entry.index);
            if(old!=null&&!old.company.companyId.equals(entry.company.companyId))return invalid();
            for(Entry other:entries.values())if(other.index!=entry.index&&other.company.companyId.equals(entry.company.companyId))return invalid();
            entries.put(entry.index,entry);
        }
        revision=Math.max(revision,snapshot.revision);latest=snapshot;token=snapshot.token;
        if(snapshot.phase==Phase.ERROR){entries.clear();error=snapshot.message;}
        return true;
    }
    private boolean invalid(){error="Rally evidence changed. Refresh before confirming.";entries.clear();token=new UUID(0,0);confirmSent=true;return false;}
    boolean canConfirm(){
        return active()&&!confirmSent&&error.isEmpty()&&latest!=null&&latest.phase==Phase.READY&&latest.included>0
            &&latest.evaluated==latest.total&&entries.size()==latest.total&&!token.equals(new UUID(0,0));
    }
    KOMEPacketCompanyRallyRequest confirm(){
        if(!canConfirm())return null;confirmSent=true;
        return new KOMEPacketCompanyRallyRequest(KOMEPacketCompanyRallyRequest.Action.CONFIRM,session,token,destination);
    }
    boolean canRefresh(){return active()&&(!error.isEmpty()||!confirmSent||latest!=null&&(latest.phase==Phase.DONE||latest.phase==Phase.ERROR));}
    String destination(){return active()?destination:"";}
    Phase phase(){return !active()||latest==null?Phase.LOADING:latest.phase;}
    int total(){return latest==null?0:latest.total;}
    int included(){return latest==null?0:latest.included;}
    String message(){
        if(!error.isEmpty())return error;
        if(latest==null)return "Loading complete Rally preview...";
        if(latest.phase==Phase.READY&&entries.size()!=latest.total)return "Waiting for complete Rally preview...";
        if(confirmSent&&latest.phase==Phase.READY)return "Submitting Rally...";
        return latest.message+(latest.phase==Phase.LOADING?" "+latest.evaluated+"/"+latest.total:"");
    }
    List<Entry> rows(){
        if(!active())return Collections.emptyList();List<Entry> list=new ArrayList<Entry>(entries.values());
        list.sort(Comparator.comparing((Entry e)->!e.included).thenComparingInt(e->e.index));return list;
    }
    /** Exact authoritative identities retained as the future KOM-85 presentation seam. */
    List<String> readyCompanyIds(){
        if(!canConfirm())return Collections.emptyList();
        List<String> ids=new ArrayList<String>();
        for(Entry entry:rows())if(entry.included)ids.add(entry.company.companyId);
        return Collections.unmodifiableList(ids);
    }
    String label(){
        if(!active())return "Rally Here";
        if(!error.isEmpty()||latest!=null&&(latest.phase==Phase.ERROR||latest.phase==Phase.DONE))return "Rally Here";
        if(confirmSent)return "Rallying...";
        if(latest==null||latest.phase==Phase.LOADING||latest.phase==Phase.READY&&entries.size()!=latest.total)return "Loading Rally...";
        return canConfirm()?"Confirm Rally":"Rally Here";
    }
    boolean enabled(){
        if(!active())return false;
        if(canConfirm())return true;
        if(!canRefresh())return false;
        if(!error.isEmpty())return true;
        return latest!=null&&(latest.phase==Phase.DONE||latest.phase==Phase.ERROR
            ||latest.phase==Phase.READY&&entries.size()==latest.total&&latest.included==0);
    }
    String summary(){
        if(!active())return "";
        if(!error.isEmpty())return error;
        if(latest==null||latest.phase==Phase.LOADING)return "Loading Rally..."+(latest==null?"":" "+latest.evaluated+"/"+latest.total);
        if(latest.phase!=Phase.READY||confirmSent)return message();
        if(entries.size()!=latest.total)return "Loading Rally...";
        String summary=latest.included+" ready / "+(latest.total-latest.included)+" skipped";
        if(latest.included==0)return summary+" | No companies can arrive now. Click to refresh.";
        for(Entry e:entries.values())if(e.included&&e.company.consequence!=kome.common.command.KOMECompanyMovementService.Consequence.NONE)
            return summary+" | Will enter battle";
        return summary;
    }

}