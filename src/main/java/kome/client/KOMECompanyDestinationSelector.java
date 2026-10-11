package kome.client;

import java.util.*;
import kome.common.command.KOMECompanyMovementService;
import kome.common.data.KOMECompanyDestinationOptions;
import kome.common.data.KOMECompanyDestinationOptions.*;
import kome.common.network.KOMEPacketCompanyDestinationRequest;

/** Presentation/navigation over accepted server projections. Never submits movement or derives eligibility. */
final class KOMECompanyDestinationSelector {
    enum Phase { CLOSED, LOADING, READY, THROTTLED, ERROR }
    private static final class Cursor {
        final String after, catalogue;
        Cursor(String after,String catalogue) {this.after=after;this.catalogue=catalogue;}
    }
    private final KOMECompanyDestinationClientState queries;
    private final List<Cursor> history=new ArrayList<Cursor>();
    private UUID session;
    private String destination="";
    private Phase phase=Phase.CLOSED;
    private KOMEPacketCompanyDestinationRequest pending;
    private KOMECompanyDestinationOptions page;
    private Row selected;
    private int pageIndex, rowOffset;
    private long retryAt;
    private String error="";
    private Result failure;
    KOMECompanyDestinationSelector(KOMECompanyDestinationClientState queries) {this.queries=Objects.requireNonNull(queries);}
    KOMEPacketCompanyDestinationRequest open(String tile) {
        close();queries.close();session=queries.sessionId();destination=tile;
        history.add(new Cursor("",""));pageIndex=0;
        return request();
    }
    boolean isOpen() {
        if(phase!=Phase.CLOSED&&!queries.isSession(session))clear();
        return phase!=Phase.CLOSED;
    }
    void close() {
        if(session!=null&&queries.isSession(session))queries.close();
        clear();
    }
    private void clear() {
        phase=Phase.CLOSED;session=null;destination="";history.clear();page=null;pending=null;selected=null;rowOffset=0;pageIndex=0;error="";failure=null;
    }
    private KOMEPacketCompanyDestinationRequest request() {
        selected=null;page=null;rowOffset=0;error="";failure=null;phase=Phase.LOADING;
        Cursor cursor=history.get(pageIndex);
        try {pending=queries.request(destination,KOMECompanyDestinationOptions.MAX_ROWS,cursor.after,cursor.catalogue);return pending;}
        catch(IllegalStateException disconnected) {pending=null;phase=Phase.ERROR;error="Not connected. Reopen after reconnect.";return null;}
    }
    /** Proxy already accepted connection/session/sequence/destination through Slice 2 state. */
    boolean accept(KOMECompanyDestinationOptions response,long now) {
        if(!isOpen()||phase!=Phase.LOADING||pending==null||queries.latest()!=response
                ||!pending.session.equals(response.session)||pending.sequence!=response.sequence
                ||!destination.equals(response.destination))return false;
        selected=null;rowOffset=0;failure=response.result;
        if(response.result==Result.OK) {page=response;phase=Phase.READY;error="";}
        else if(response.result==Result.RATE_LIMITED) {
            phase=Phase.THROTTLED;retryAt=now+(response.retryAfterMillis<0?1000:response.retryAfterMillis);
            error="Server busy. Retry shortly.";
        } else {phase=Phase.ERROR;error=queryError(response.result);}
        return true;
    }
    KOMEPacketCompanyDestinationRequest next() {
        if(!isOpen()||phase!=Phase.READY||page==null||!page.hasMore)return null;
        Cursor next=new Cursor(page.nextCompanyId,page.catalogueToken);
        while(history.size()>pageIndex+1)history.remove(history.size()-1);
        history.add(next);pageIndex++;return request();
    }
    KOMEPacketCompanyDestinationRequest previous() {
        if(!isOpen()||phase==Phase.LOADING||pageIndex==0)return null;
        pageIndex--;return request();
    }
    KOMEPacketCompanyDestinationRequest retry(long now) {
        if(!canRetry(now))return null;
        if(pending!=null&&phase==Phase.ERROR&&failure==Result.CATALOGUE_CHANGED) {
            history.clear();history.add(new Cursor("",""));pageIndex=0;
        }
        return request();
    }
    boolean canRetry(long now) {return isOpen()&&(phase==Phase.ERROR||phase==Phase.THROTTLED&&now>=retryAt);}
    boolean hasNext() {return isOpen()&&phase==Phase.READY&&page!=null&&page.hasMore;}
    boolean hasPrevious() {return isOpen()&&phase!=Phase.LOADING&&pageIndex>0;}
    Phase phase() {isOpen();return phase;}
    String destination() {isOpen();return destination;}
    int pageNumber() {return pageIndex+1;}
    int rowOffset() {return rowOffset;}
    void scrollRows(int amount,int capacity) {
        rowOffset=Math.max(0,Math.min(Math.max(0,rows().size()-capacity),rowOffset+amount));
    }
    List<Row> rows() {return isOpen()&&phase==Phase.READY&&page!=null?page.rows:Collections.<Row>emptyList();}
    Row selected() {return isOpen()?selected:null;}
    void select(String companyId) {
        if(!isOpen()||phase!=Phase.READY)return;
        for(Row row:rows())if(row.companyId.equals(companyId)) {
            selected=selected!=null&&selected.companyId.equals(companyId)?null:row;return;
        }
    }
    void clearSelection() {selected=null;}
    List<String> selectedRoute() {
        Row row=selected();
        return row!=null&&row.evaluationState==EvaluationState.COMPLETE&&row.legalRoute?row.route:Collections.<String>emptyList();
    }
    String message(long now) {
        switch(phase()) {
            case LOADING:return "Loading company options...";
            case THROTTLED:return now<retryAt?"Server busy; retry in "+(retryAt-now)+" ms.":"Server busy; Retry is available.";
            case ERROR:return error;
            case READY:return rows().isEmpty()?"No relevant Campaign companies.":"Select a company to preview.";
            default:return "";
        }
    }
    static String name(Row row) {return row.name.isEmpty()?row.companyId:row.name;}
    static String classification(Row row) {return row.entitlement==Entitlement.MOUNTED?"Mounted":"Foot/Mixed";}
    static String state(Row row) {
        if(row.evaluationState==EvaluationState.DEFERRED_ROUTE_LIMIT)return "Route evaluation deferred";
        switch(row.status) {
            case CAN_START_AND_ARRIVE_NOW:return "Can arrive now";
            case CAN_START_BUT_CONTINUES_LATER:return "Can start; arrives later";
            default:return "Blocked";
        }
    }
    static String reason(Row row) {
        if(row.evaluationState==EvaluationState.DEFERRED_ROUTE_LIMIT)
            return "Route exceeds this bounded preview. Movement eligibility is unknown.";
        return row.denialDetail;
    }
    static String consequence(Row row) {
        if(row.consequence==KOMECompanyMovementService.Consequence.EXPECTED_CREATE_CONFLICT)return "Expected: create battle on arrival";
        if(row.consequence==KOMECompanyMovementService.Consequence.EXPECTED_JOIN_CONFLICT)return "Expected: join battle on arrival";
        return "";
    }
    static String population(Row row) {
        return (row.unitCount<0?"Units unknown":row.unitCount+" units")+" | Pop "+
            (row.populationCenti<0?"unknown":java.math.BigDecimal.valueOf(row.populationCenti,2).stripTrailingZeros().toPlainString());
    }
    static String eta(Row row) {
        if(!row.canStartNow || row.evaluationState!=EvaluationState.COMPLETE) return "ETA unavailable";
        if(row.scheduleMode==KOMECompanyMovementService.ScheduleMode.TIMED_OVERRIDE) return "Next movement by override";
        if(row.etaMillis<0) return "ETA unavailable";
        if(row.canArriveNow && row.etaMillis==0) return "Arrives now";
        long minutes=row.etaMillis/60000+(row.etaMillis%60000==0?0:1);
        long days=minutes/1440,hours=minutes%1440/60,remaining=minutes%60;
        String value=days>0?days+"d":"";
        if(hours>0)value+=(value.isEmpty()?"":" ")+hours+"h";
        if(remaining>0)value+=(value.isEmpty()?"":" ")+remaining+"m";
        return "ETA: "+value;
    }
    static String timing(Row row) {
        if(row.timingAvailable)return "Future daily boundaries: "+row.futureBoundaries;
        if(row.scheduleMode==KOMECompanyMovementService.ScheduleMode.TIMED_OVERRIDE)return "Timed schedule; no ETA projected";
        return "";
    }
    private static String queryError(Result result) {
        switch(result) {
            case CATALOGUE_CHANGED:return "Company list changed. Retry to refresh.";
            case CATALOGUE_LIMIT:return "Company catalogue exceeds the query limit.";
            case COMPANY_LIMIT:return "A company exceeds the member limit.";
            case ARRIVAL_BUDGET_EXCEEDED:return "Preview work limit reached. Retry this page.";
            case PROJECTION_LIMIT:return "Company projection exceeds the response limit.";
            default:return "Destination query failed. Retry or choose another tile.";
        }
    }
}
