package kome.client;

import java.util.*;
import kome.common.command.KOMECompanyMovementService;
import kome.common.data.KOMECompanyDestinationOptions;
import kome.common.data.KOMECompanyDestinationOptions.*;
import kome.common.network.KOMEPacketCompanyDestinationRequest;
import kome.common.network.KOMEPacketCompanyMovementRequest;
import kome.common.data.KOMECompanyMovementResult;

/** Presentation/navigation over accepted server projections. Submits only explicitly confirmed server evidence; never derives eligibility. */
final class KOMECompanyDestinationSelector {
    enum Phase { CLOSED, LOADING, READY, THROTTLED, ERROR, SUBMITTING, JOINING }
    private static final class Cursor {
        final String after, catalogue;
        final int start;
        Cursor(String after,String catalogue) {this(after,catalogue,0);}
        Cursor(String after,String catalogue,int start) {this.after=after;this.catalogue=catalogue;this.start=start;}
    }
    private final KOMECompanyDestinationClientState queries;
    private final List<Cursor> history=new ArrayList<Cursor>();
    // Only cursors, never cached route projections. The server catalogue bound limits history.
    // Byte packing can return fewer than eight rows. At least one candidate advances per page.
    static final int MAX_PAGE_HISTORY=KOMECompanyDestinationOptions.MAX_CATALOGUE;
    private UUID session;
    private String destination="";
    private Phase phase=Phase.CLOSED;
    private KOMEPacketCompanyDestinationRequest pending;
    private KOMECompanyDestinationOptions page;
    private Row selected;
    private int pageIndex, rowOffset;
    private int windowCapacity=KOMECompanyDestinationOptions.MAX_ROWS;
    private boolean enterAtEnd;
    private List<Row> orderedRows=Collections.emptyList();
    private long retryAt;
    private String error="";
    private Result failure;
    private KOMEPacketCompanyMovementRequest movement;
    private String notice="";
    private boolean joinAfterArrival;
    private kome.common.network.KOMEPacketJoinBattleViewRequest joinView;
    private String arrivedCompany="";
    private KOMECompanyMovementResult arrivedMovement;
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
        joinAfterArrival=false;joinView=null;arrivedCompany="";arrivedMovement=null;
        phase=Phase.CLOSED;session=null;destination="";history.clear();page=null;pending=null;selected=null;rowOffset=0;pageIndex=0;error="";failure=null;movement=null;notice="";enterAtEnd=false;orderedRows=Collections.emptyList();
    }
    private KOMEPacketCompanyDestinationRequest request() {
        selected=null;page=null;rowOffset=0;error="";failure=null;phase=Phase.LOADING;
        Cursor cursor=history.get(pageIndex);
        try {pending=queries.request(destination,KOMECompanyDestinationOptions.MAX_ROWS,cursor.after,cursor.after.isEmpty()?"":cursor.catalogue);return pending;}
        catch(IllegalStateException disconnected) {pending=null;phase=Phase.ERROR;error="Not connected. Reopen after reconnect.";return null;}
    }
    /** Proxy already accepted connection/session/sequence/destination through Slice 2 state. */
    boolean accept(KOMECompanyDestinationOptions response,long now) {
        if(!isOpen()||phase!=Phase.LOADING||pending==null||queries.latest()!=response
                ||!pending.session.equals(response.session)||pending.sequence!=response.sequence
                ||!destination.equals(response.destination))return false;
        selected=null;rowOffset=0;failure=response.result;
        // First-page requests have no wire cursor. Revisited first pages still must match retained history.
        String expectedCatalogue=history.get(pageIndex).catalogue;
        if(response.result==Result.OK&&!expectedCatalogue.isEmpty()&&!expectedCatalogue.equals(response.catalogueToken)) {
            phase=Phase.ERROR;failure=Result.CATALOGUE_CHANGED;error=queryError(failure);return true;
        }
        if(response.result==Result.OK) {
            page=response;orderedRows=new ArrayList<Row>(response.rows);orderedRows.sort(ROW_ORDER);
            orderedRows=Collections.unmodifiableList(orderedRows);
            Cursor cursor=history.get(pageIndex);
            history.set(pageIndex,new Cursor(cursor.after,response.catalogueToken,cursor.start));
            rowOffset=enterAtEnd&&!orderedRows.isEmpty()?(orderedRows.size()-1)/windowCapacity*windowCapacity:0;
            enterAtEnd=false;phase=Phase.READY;error="";
        }
        else if(response.result==Result.RATE_LIMITED) {
            phase=Phase.THROTTLED;retryAt=now+(response.retryAfterMillis<0?1000:response.retryAfterMillis);
            error="Server busy. Retry shortly.";
        } else {phase=Phase.ERROR;error=queryError(response.result);}
        return true;
    }
    KOMEPacketCompanyDestinationRequest next() {
        if(!hasNext())return null;
        if(rowOffset+windowCapacity<rows().size()) {rowOffset+=windowCapacity;selected=null;return null;}
        if(pageIndex+1>=MAX_PAGE_HISTORY)return null;
        Cursor next=new Cursor(page.nextCompanyId,page.catalogueToken,history.get(pageIndex).start+page.rows.size());
        while(history.size()>pageIndex+1)history.remove(history.size()-1);
        history.add(next);pageIndex++;return request();
    }
    KOMEPacketCompanyDestinationRequest previous() {
        if(!hasPrevious())return null;
        if(phase==Phase.READY&&rowOffset>0) {rowOffset=Math.max(0,rowOffset-windowCapacity);selected=null;return null;}
        enterAtEnd=true;
        pageIndex--;return request();
    }
    KOMEPacketCompanyDestinationRequest retry(long now) {
        if(!canRetry(now))return null;
        if(phase==Phase.READY||pending!=null&&phase==Phase.ERROR&&failure==Result.CATALOGUE_CHANGED) {
            history.clear();history.add(new Cursor("",""));pageIndex=0;
            enterAtEnd=false;
        }
        return request();
    }
    boolean canRetry(long now) {return isOpen()&&(phase==Phase.READY||phase==Phase.ERROR||phase==Phase.THROTTLED&&now>=retryAt);}
    boolean hasNext() {return isOpen()&&phase==Phase.READY&&page!=null&&(rowOffset+windowCapacity<rows().size()||page.hasMore&&pageIndex+1<MAX_PAGE_HISTORY);}
    boolean hasPrevious() {return isOpen()&&phase!=Phase.LOADING&&phase!=Phase.SUBMITTING&&phase!=Phase.JOINING&&(pageIndex>0||phase==Phase.READY&&rowOffset>0);}
    Phase phase() {isOpen();return phase;}
    String destination() {isOpen();return destination;}
    int pageNumber() {return pageIndex+1;}
    int rowOffset() {return rowOffset;}
    void scrollRows(int amount,int capacity) {
        windowCapacity=Math.max(1,capacity);
        rowOffset=Math.max(0,Math.min(Math.max(0,rows().size()-1),rowOffset+amount));
    }
    List<Row> rows() {return isOpen()&&phase==Phase.READY&&page!=null?orderedRows:Collections.<Row>emptyList();}
    int historySize() {return history.size();}
    String companyRange(int capacity) {
        if(rows().isEmpty())return "Companies";
        int first=history.get(pageIndex).start+rowOffset+1;
        return "Companies "+first+"–"+(first+Math.min(capacity,rows().size()-rowOffset)-1);
    }
    private static int priority(Row row) {
        if(row.evaluationState!=EvaluationState.COMPLETE)return 4;
        if(row.canStartNow&&row.canArriveNow)return 0;
        if(row.canStartNow)return 1;
        if(row.remainingAllowance==0||Arrays.asList("ALREADY_MOVING","CONFLICT_HELD","FORMAL_RETREAT_BLOCKED","UNAUTHORIZED","UNKNOWN_COMPANY","EMPTY_COMPANY").contains(row.denialCode))return 3;
        return 2;
    }
    private static final Comparator<Row> ROW_ORDER=Comparator.comparingInt(KOMECompanyDestinationSelector::priority)
        .thenComparingInt(row->row.distance<0?Integer.MAX_VALUE:row.distance)
        .thenComparing(row->name(row),String.CASE_INSENSITIVE_ORDER).thenComparing(row->name(row)).thenComparing(row->row.companyId);
    Row selected() {return isOpen()?selected:null;}
    void select(String companyId) {
        if(!isOpen()||phase!=Phase.READY)return;
        for(Row row:rows())if(row.companyId.equals(companyId)) {
            notice="";selected=selected!=null&&selected.companyId.equals(companyId)?null:row;return;
        }
    }
    void clearSelection() {if(phase!=Phase.SUBMITTING)selected=null;}
    List<String> selectedRoute() {
        Row row=selected();
        return phase==Phase.READY&&row!=null&&row.evaluationState==EvaluationState.COMPLETE&&row.legalRoute?row.route:Collections.<String>emptyList();
    }
    String message(long now) {
        switch(phase()) {
            case SUBMITTING:return "Submitting movement order...";
            case JOINING:return "Company arrived. Loading Join Battle...";
            case LOADING:return "Loading company options...";
            case THROTTLED:return now<retryAt?"Server busy; retry in "+(retryAt-now)+" ms.":"Server busy; Retry is available.";
            case ERROR:return error;
            case READY:return rows().isEmpty()?"No relevant Campaign companies.":"Select a company to preview.";
            default:return "";
        }
    }
    boolean canConfirm() {
        Row row=selected();
        return phase==Phase.READY&&row!=null&&row.evaluationState==EvaluationState.COMPLETE&&row.canStartNow&&row.legalRoute
            &&row.legacyFingerprint.matches("[0-9a-f]{64}")&&row.confirmationFingerprint.matches("[0-9a-f]{64}");
    }
    KOMEPacketCompanyMovementRequest confirm() {
        return confirm(false);
    }
    boolean canConfirmAndJoin() {
        Row row=selected();return canConfirm()&&row.canArriveNow&&row.status==KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW
            &&(row.consequence==KOMECompanyMovementService.Consequence.EXPECTED_CREATE_CONFLICT||row.consequence==KOMECompanyMovementService.Consequence.EXPECTED_JOIN_CONFLICT);
    }
    KOMEPacketCompanyMovementRequest confirm(boolean join) {
        if(join&&!canConfirmAndJoin())return null;
        if(!canConfirm())return null;
        joinAfterArrival=join;
        movement=queries.submit(selected);phase=Phase.SUBMITTING;notice="";return movement;
    }
    /** Connection/session acceptance happens in the proxy before this presentation transition. */
    KOMEPacketCompanyDestinationRequest acceptMovement(KOMECompanyMovementResult result) {
        if(!isOpen()||phase!=Phase.SUBMITTING||movement==null||!session.equals(result.session)
                ||movement.sequence!=result.sequence||movement.previewSequence!=result.previewSequence
                ||!movement.companyId.equals(result.companyId)||!destination.equals(result.destination)) {cancelJoinIntent();return null;}
        boolean continueJoin=joinAfterArrival&&result.outcome==KOMECompanyMovementResult.Outcome.ARRIVED
            &&kome.common.data.KOMEArmyMovementOrder.CONFLICT_HELD.equals(result.orderStatus)&&destination.equals(result.currentTile);
        joinAfterArrival=false;
        selected=null;page=null;movement=null;
        if(result.accepted()) {
            notice=result.outcome==KOMECompanyMovementResult.Outcome.ARRIVED?"Arrived at "+destination+("conflict_held".equals(result.orderStatus)?"; committed to battle.":
                "access_halted".equals(result.orderStatus)?"; halted after access changed.":"."):
                "Order accepted: "+result.orderStatus.replace('_',' ')+". Arrival not completed.";
            if(continueJoin) {
                arrivedMovement=result;
                arrivedCompany=result.companyId;joinView=new kome.common.network.KOMEPacketJoinBattleViewRequest(destination,UUID.randomUUID());
                phase=Phase.JOINING;return null;
            }
            history.clear();history.add(new Cursor("",""));pageIndex=0;return request();
        }
        phase=Phase.ERROR;failure=Result.CATALOGUE_CHANGED;
        error=(result.outcome==KOMECompanyMovementResult.Outcome.STALE_PREVIEW?"Preview is stale. ":"")+result.detail;
        notice=error+" Refresh Preview before confirming again.";return null;
    }
    String notice() {return notice;}
    boolean isDuplicateArrival(KOMECompanyMovementResult result) {
        KOMECompanyMovementResult accepted=arrivedMovement;
        return isOpen()&&accepted!=null&&accepted.session.equals(result.session)&&accepted.sequence==result.sequence
            &&accepted.previewSequence==result.previewSequence&&accepted.companyId.equals(result.companyId)
            &&accepted.destination.equals(result.destination)&&accepted.outcome==result.outcome
            &&accepted.orderId.equals(result.orderId)&&accepted.orderStatus.equals(result.orderStatus)
            &&accepted.currentTile.equals(result.currentTile);
    }
    UUID sessionId() {return session;}
    boolean ownsJoinContinuation(UUID expected) {return isOpen()&&phase==Phase.JOINING&&arrivedMovement!=null&&session.equals(expected);}
    void joinStatus(String message) {if(isOpen()&&phase==Phase.JOINING)notice=message;}
    void cancelJoinIntent() {
        joinAfterArrival=false;joinView=null;arrivedCompany="";arrivedMovement=null;
        if(phase==Phase.JOINING) {phase=Phase.ERROR;failure=Result.CATALOGUE_CHANGED;error="Company moved successfully. Join Battle continuation was cancelled.";notice=error;}
    }
    kome.common.network.KOMEPacketJoinBattleViewRequest joinViewRequest() {return isOpen()?joinView:null;}
    String acceptJoinView(kome.common.network.KOMEPacketJoinBattleViewResponse view) {
        if(!isOpen()||phase!=Phase.JOINING||joinView==null)return null;
        if(!joinView.correlation.equals(view.correlation)||!destination.equals(view.tileId)) {cancelJoinIntent();return null;}
        String company=arrivedCompany;joinView=null;arrivedCompany="";return company;
    }
    static List<String> confirmationSummary(Row row) {
        return Arrays.asList(name(row)+": "+row.currentTile+" -> "+row.destinationTile,population(row),
            classification(row)+" | "+row.distance+" tiles | "+eta(row),
            row.consequence==KOMECompanyMovementService.Consequence.EXPECTED_CREATE_CONFLICT?"Will create a battle on arrival.":
            row.consequence==KOMECompanyMovementService.Consequence.EXPECTED_JOIN_CONFLICT?"Will join the existing battle on arrival.":row.evaluationState!=EvaluationState.COMPLETE?"Route evaluation deferred.":
            !row.canStartNow?"Movement blocked.":"Normal movement.");
    }
    static String name(Row row) {return row.name.trim().isEmpty()||row.name.trim().equals(row.companyId)?"Campaign Company":row.name;}
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
