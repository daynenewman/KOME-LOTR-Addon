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
    enum Filter {
        ALL("All"), READY("Ready"), ARRIVE_NOW("Arrive Now");
        final String label;
        Filter(String label){this.label=label;}
        boolean matches(Row row) {
            if(this==ALL)return true;
            if(row.evaluationState!=EvaluationState.COMPLETE)return false;
            return row.status==KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW
                ||this==READY&&row.status==KOMECompanyMovementService.Status.CAN_START_BUT_CONTINUES_LATER;
        }
    }
    // Explicit browsing/filter gestures may inspect only this many additional server pages.
    static final int MAX_SEARCH_PAGES=3;
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
    private String selectedCompanyId="";
    private int pageIndex, rowOffset;
    private int windowCapacity=4;
    private boolean enterAtEnd;
    private List<Row> orderedRows=Collections.emptyList(), filteredRows=Collections.emptyList();
    private Filter filter=Filter.ALL;
    private int searchDirection, searchRemaining;
    private boolean wheelAtEnd;
    private KOMEPacketCompanyDestinationRequest browseFollowup;
    private final KOMECompanyBrowseWindow window=new KOMECompanyBrowseWindow();
    private final KOMEJoinConfirmationLatch joinLatch=new KOMEJoinConfirmationLatch();
    private boolean rolling, filterSuperseded, filterNeedsRestart, filterResetPending;
    private int pendingStep, pendingSelectionPage=-1;
    private String pendingSelection="";
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
        window.clear();window.refilter(Filter.ALL);rolling=false;filterSuperseded=filterNeedsRestart=filterResetPending=false;pendingStep=0;pendingSelection="";joinLatch.clear();
        filter=Filter.ALL;filteredRows=Collections.emptyList();searchDirection=0;searchRemaining=0;wheelAtEnd=false;browseFollowup=null;
        phase=Phase.CLOSED;session=null;destination="";history.clear();page=null;pending=null;setSelected(null);rowOffset=0;pageIndex=0;error="";failure=null;movement=null;notice="";enterAtEnd=false;orderedRows=Collections.emptyList();
    }
    private KOMEPacketCompanyDestinationRequest request() {
        joinLatch.clear();if(!rolling){page=null;orderedRows=Collections.emptyList();filteredRows=Collections.emptyList();rowOffset=0;}error="";failure=null;phase=Phase.LOADING;
        Cursor cursor=history.get(pageIndex);
        try {pending=queries.request(destination,KOMECompanyDestinationOptions.MAX_ROWS,cursor.after,cursor.after.isEmpty()?"":cursor.catalogue);return pending;}
        catch(IllegalStateException disconnected) {pending=null;phase=Phase.ERROR;error="Not connected. Reopen after reconnect.";return null;}
    }
    /** Proxy already accepted connection/session/sequence/destination through Slice 2 state. */
    boolean accept(KOMECompanyDestinationOptions response,long now) {
        if(!isOpen()||phase!=Phase.LOADING||pending==null||queries.latest()!=response
                ||!pending.session.equals(response.session)||pending.sequence!=response.sequence
                ||!destination.equals(response.destination))return false;
        joinLatch.clear();rowOffset=0;failure=response.result;
        // First-page requests have no wire cursor. Revisited first pages still must match retained history.
        String expectedCatalogue=history.get(pageIndex).catalogue;
        if(response.result==Result.OK&&!expectedCatalogue.isEmpty()&&!expectedCatalogue.equals(response.catalogueToken)) {
            window.clear();setSelected(null);page=null;orderedRows=filteredRows=Collections.emptyList();filterSuperseded=filterNeedsRestart=filterResetPending=false;pendingSelection="";pendingStep=0;searchRemaining=0;browseFollowup=null;
            phase=Phase.ERROR;failure=Result.CATALOGUE_CHANGED;error=queryError(failure);return true;
        }
        // Filter-free wire queries are serialized. A superseded search response only drains
        // its outstanding request; it must not restore its rows, position or selection.
        if(filterSuperseded&&page!=null&&(response.result==Result.OK||response.result==Result.RATE_LIMITED)) {
            filterSuperseded=false;pending=null;browseFollowup=null;pendingStep=0;
            if(response.result==Result.RATE_LIMITED) {
                phase=Phase.THROTTLED;retryAt=now+(response.retryAfterMillis<0?1000:response.retryAfterMillis);
                error="More companies available shortly.";searchRemaining=0;
            } else {
                phase=Phase.READY;searchRemaining=MAX_SEARCH_PAGES;
                browseFollowup=!pendingSelection.isEmpty()&&pendingSelectionPage>=0?queryBrowsePage(pendingSelectionPage):continueFilterSearch();
            }
            return true;
        }
        if(response.result==Result.OK) {
            if(filterResetPending){window.clear();filterResetPending=false;}
            filterSuperseded=false;page=response;orderedRows=new ArrayList<Row>(response.rows);orderedRows.sort(ROW_ORDER);
            orderedRows=Collections.unmodifiableList(orderedRows);applyFilter();
            Cursor cursor=history.get(pageIndex);
            history.set(pageIndex,new Cursor(cursor.after,response.catalogueToken,cursor.start));
            rowOffset=enterAtEnd&&!filteredRows.isEmpty()?(wheelAtEnd?filteredRows.size()-1:
                (filteredRows.size()-1)/windowCapacity*windowCapacity):0;
            enterAtEnd=false;wheelAtEnd=false;phase=Phase.READY;error="";
            if(!selectedCompanyId.isEmpty())for(Row row:orderedRows)if(isSelected(row.companyId)){setSelected(filter.matches(row)?row:null);break;}
            if(rolling)acceptRolling(response);
            else if(filteredRows.isEmpty()&&searchRemaining>0)browseFollowup=browsePage(searchDirection);
            else searchRemaining=0;
        }
        else if(response.result==Result.RATE_LIMITED) {
            phase=Phase.THROTTLED;retryAt=now+(response.retryAfterMillis<0?1000:response.retryAfterMillis);
            error="More companies available shortly.";browseFollowup=null;searchRemaining=0;
        } else {window.clear();setSelected(null);page=null;orderedRows=filteredRows=Collections.emptyList();filterSuperseded=filterNeedsRestart=filterResetPending=false;pendingSelection="";pendingStep=0;searchRemaining=0;browseFollowup=null;phase=Phase.ERROR;error=queryError(response.result);}
        return true;
    }
    KOMEPacketCompanyDestinationRequest next() {
        if(!hasNext())return null;
        if(rowOffset+windowCapacity<rows().size()) {rowOffset+=windowCapacity;return null;}
        if(pageIndex+1>=MAX_PAGE_HISTORY)return null;
        Cursor next=new Cursor(page.nextCompanyId,page.catalogueToken,history.get(pageIndex).start+page.rows.size());
        while(history.size()>pageIndex+1)history.remove(history.size()-1);
        history.add(next);pageIndex++;return request();
    }
    KOMEPacketCompanyDestinationRequest previous() {
        if(!hasPrevious())return null;
        if(phase==Phase.READY&&rowOffset>0) {rowOffset=Math.max(0,rowOffset-windowCapacity);return null;}
        enterAtEnd=true;
        pageIndex--;return request();
    }
    KOMEPacketCompanyDestinationRequest retry(long now) {
        if(!canRetry(now))return null;
        joinLatch.clear();if(phase!=Phase.THROTTLED)setSelected(null);
        browseFollowup=null;searchRemaining=filter==Filter.ALL?0:MAX_SEARCH_PAGES;
        if(phase!=Phase.THROTTLED)searchDirection=1;
        if(phase==Phase.READY||pending!=null&&phase==Phase.ERROR&&failure==Result.CATALOGUE_CHANGED) {
            window.clear();pendingStep=0;pendingSelection="";
            history.clear();history.add(new Cursor("",""));pageIndex=0;
            enterAtEnd=false;
        }
        return request();
    }
    boolean canRetry(long now) {return isOpen()&&(phase==Phase.READY||phase==Phase.ERROR||phase==Phase.THROTTLED&&now>=retryAt);}
    boolean hasNext() {if(rolling)return canBrowse(1,0);return isOpen()&&phase==Phase.READY&&page!=null&&(rowOffset+windowCapacity<rows().size()||page.hasMore&&pageIndex+1<MAX_PAGE_HISTORY);}
    boolean hasPrevious() {if(rolling)return canBrowse(-1,0);return isOpen()&&phase!=Phase.LOADING&&phase!=Phase.SUBMITTING&&phase!=Phase.JOINING&&(pageIndex>0||phase==Phase.READY&&rowOffset>0);}
    Phase phase() {isOpen();return phase;}
    String destination() {isOpen();return destination;}
    int pageNumber() {return pageIndex+1;}
    int rowOffset() {return rolling?window.top():rowOffset;}
    void scrollRows(int amount,int capacity) {
        windowCapacity=Math.max(1,capacity);
        if(rolling){window.position(window.top()+amount,windowCapacity);return;}
        rowOffset=Math.max(0,Math.min(Math.max(0,rows().size()-1),rowOffset+amount));
    }
    List<Row> rows() {
        if(!isOpen())return Collections.emptyList();
        if(rolling&&(phase==Phase.READY||phase==Phase.LOADING||phase==Phase.THROTTLED))return window.rows();
        return phase==Phase.READY&&page!=null?filteredRows:Collections.<Row>emptyList();
    }
    Row visibleRow(int visibleIndex) {
        List<Row> backing=rows();int index=rowOffset()+visibleIndex;
        return visibleIndex<0||index<0||index>=backing.size()?null:backing.get(index);
    }    Filter filter(){return filter;}
    private void applyFilter() {
        List<Row> matching=new ArrayList<Row>();
        for(Row row:orderedRows)if(filter.matches(row))matching.add(row);
        filteredRows=Collections.unmodifiableList(matching);
    }
    private void activateRolling() {
        if(rolling)return;
        rolling=true;window.reset(pageIndex,history.get(pageIndex).start,page,orderedRows);
        window.position(rowOffset,windowCapacity);
    }
    boolean canChangeFilter() {
        Phase current=phase();
        return current==Phase.READY||current==Phase.LOADING||current==Phase.THROTTLED;
    }
    KOMEPacketCompanyDestinationRequest setFilter(Filter next) {
        if(!canChangeFilter()||next==null||next==filter)return null;
        joinLatch.clear();pendingSelection="";pendingStep=0;browseFollowup=null;
        if(!rolling&&page!=null)activateRolling();
        filterNeedsRestart=filterNeedsRestart||window.hasPartialEvidence();filter=next;applyFilter();window.refilter(next);window.position(0,windowCapacity);
        if(selected!=null&&!filter.matches(selected))setSelected(null);
        searchRemaining=MAX_SEARCH_PAGES;
        if(phase==Phase.LOADING){filterSuperseded=true;return null;}
        // Throttle is transport-wide, never an input lock. No new request until an
        // explicit browse/retry after the authoritative retry deadline.
        if(phase==Phase.THROTTLED){searchRemaining=0;return null;}
        return continueFilterSearch();
    }
    private KOMEPacketCompanyDestinationRequest continueFilterSearch() {
        if(filterNeedsRestart||window.earlier()&&selected==null){filterNeedsRestart=false;filterResetPending=true;return queryBrowsePage(0);}
        if(window.rows().size()<windowCapacity&&window.later())return loadAdjacent(1);
        searchRemaining=0;return null;
    }
    KOMEPacketCompanyDestinationRequest takeBrowseFollowup() {
        if(!isOpen())return null;
        KOMEPacketCompanyDestinationRequest request=browseFollowup;browseFollowup=null;return request;
    }
    boolean canBrowse(int direction,long now) {
        if(!isOpen())return false;
        if(phase==Phase.THROTTLED)return now>=retryAt;
        if(phase!=Phase.READY)return false;
        if(rolling)return window.canMove(direction,windowCapacity)||window.needsPage(direction,windowCapacity);
        return direction<0?rowOffset>0||pageIndex>0:rowOffset+windowCapacity<rows().size()||page.hasMore;
    }
    /** Loads a neighbor before moving into a shortened window; keeps the previous full viewport while waiting. */
    KOMEPacketCompanyDestinationRequest browse(int direction,int capacity,long now) {
        windowCapacity=Math.max(1,capacity);
        if(direction==0||!canBrowse(direction,now))return null;
        if(phase==Phase.THROTTLED){if(filterNeedsRestart){phase=Phase.READY;searchRemaining=MAX_SEARCH_PAGES;return continueFilterSearch();}return retry(now);}
        activateRolling();
        if(window.canMove(direction,windowCapacity)){window.move(direction,windowCapacity);return null;}
        pendingStep=direction<0?-1:1;searchRemaining=MAX_SEARCH_PAGES;
        return loadAdjacent(pendingStep);
    }
    private KOMEPacketCompanyDestinationRequest queryBrowsePage(int index) {
        pageIndex=index;searchRemaining--;return request();
    }
    private KOMEPacketCompanyDestinationRequest loadAdjacent(int direction) {
        if(searchRemaining<=0)return null;
        if(direction<0) {
            if(!window.earlier())return null;
            return queryBrowsePage(window.previousPage());
        }
        if(!window.later()||window.nextPage()>=MAX_PAGE_HISTORY)return null;
        int next=window.nextPage();
        if(history.size()==next)history.add(new Cursor(window.nextAfter(),window.token(),window.nextStart()));
        return queryBrowsePage(next);
    }
    private void acceptRolling(KOMECompanyDestinationOptions response) {
        window.update(pageIndex,history.get(pageIndex).start,response,orderedRows);
        if(!pendingSelection.isEmpty()) {
            Row fresh=null;for(Row row:filteredRows)if(row.companyId.equals(pendingSelection)){fresh=row;break;}
            pendingStep=0;searchRemaining=0;
            if(fresh!=null){setSelected(fresh);pendingSelection="";return;}
            int selectedPage=pendingSelectionPage>=0?pendingSelectionPage:window.pageOf(pendingSelection);
            if(selectedPage>=0&&selectedPage!=pageIndex) {
                // A later click supersedes only local selection. Serialize its
                // evidence request after the outstanding reply, never deploy from cache.
                browseFollowup=queryBrowsePage(selectedPage);return;
            }
            setSelected(null);pendingSelection="";return;
        }
        if(pendingStep!=0&&window.canMove(pendingStep,windowCapacity)){window.move(pendingStep,windowCapacity);pendingStep=0;}
        boolean needs=pendingStep<0?window.needsPage(-1,windowCapacity):
            (pendingStep>0||window.rows().size()<windowCapacity)&&window.needsPage(1,windowCapacity);
        if(needs&&searchRemaining>0)browseFollowup=loadAdjacent(pendingStep<0?-1:1);
        else {pendingStep=0;searchRemaining=0;window.position(window.top(),windowCapacity);}
    }
    KOMEPacketCompanyDestinationRequest selectForBrowse(String company) {
        if(!canChangeFilter())return null;
        Row candidate=null;for(Row row:rows())if(row.companyId.equals(company)){candidate=row;break;}
        if(candidate==null)return null;
        if(phase==Phase.LOADING||phase==Phase.THROTTLED) {
            select(company);pendingSelection=selectedCompanyId;pendingSelectionPage=window.pageOf(company);pendingStep=0;searchRemaining=0;return null;
        }
        if(queries.latest()!=null&&queries.latest().rows.contains(candidate)){select(company);return null;}
        int index=window.pageOf(company);if(index<0)return null;
        select(company);if(selectedCompanyId.isEmpty())return null;
        pendingStep=0;searchRemaining=0;KOMEPacketCompanyDestinationRequest fresh=queryBrowsePage(index);pendingSelection=company;pendingSelectionPage=index;return fresh;
    }
    int retainedBrowseRows(){return window.rows().size();}
    int retainedBrowseProjections(){return window.retainedProjections();}
    boolean earlierRows(){return rolling?window.earlier():pageIndex>0;}
    boolean laterRows(){return rolling?window.later():page!=null&&page.hasMore;}
    private KOMEPacketCompanyDestinationRequest browsePage(int direction) {
        if(phase!=Phase.READY||page==null||searchRemaining<=0)return null;
        if(direction<0) {
            if(pageIndex==0){searchRemaining=0;return null;}
            pageIndex--;enterAtEnd=true;wheelAtEnd=true;
        }else {
            if(!page.hasMore||pageIndex+1>=MAX_PAGE_HISTORY){searchRemaining=0;return null;}
            Cursor next=new Cursor(page.nextCompanyId,page.catalogueToken,history.get(pageIndex).start+page.rows.size());
            while(history.size()>pageIndex+1)history.remove(history.size()-1);
            history.add(next);pageIndex++;enterAtEnd=false;wheelAtEnd=false;
        }
        searchRemaining--;return request();
    }
    int historySize() {return history.size();}
    String companyRange(int capacity) {
        if(filter!=Filter.ALL)return filter.label+" companies";
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
    /** Presentation identity is independent of the row object retained as submission evidence. */
    String selectedCompanyId() {return isOpen()?selectedCompanyId:"";}
    boolean isSelected(String company) {return company!=null&&!company.isEmpty()&&company.equals(selectedCompanyId());}
    private void setSelected(Row row) {selected=row;selectedCompanyId=row==null?"":row.companyId;}
    Row selected() {return isOpen()?selected:null;}
    void select(String companyId) {
        if(!canChangeFilter())return;
        joinLatch.clear();
        for(Row row:rows())if(row.companyId.equals(companyId)) {
            notice="";setSelected(isSelected(companyId)?null:row);return;
        }
    }
    void clearSelection() {joinLatch.clear();pendingSelection="";if(phase!=Phase.SUBMITTING)setSelected(null);}
    List<String> selectedRoute() {
        Row row=selected();
        return canChangeFilter()&&row!=null&&row.evaluationState==EvaluationState.COMPLETE&&row.legalRoute?row.route:Collections.<String>emptyList();
    }
    String message(long now) {
        switch(phase()) {
            case SUBMITTING:return "Submitting movement order...";
            case JOINING:return "Company arrived. Loading Join Battle...";
            case LOADING:return "Loading companies...";
            case THROTTLED:return now<retryAt?"More companies available shortly":"Scroll or Retry to load more companies.";
            case ERROR:return error;
            case READY:
                if(!rows().isEmpty())return "Select a company to preview.";
                if(laterRows()&&pageIndex+1<MAX_PAGE_HISTORY)return "More companies may match. Scroll down to keep looking.";
                if(pageIndex>0)return "No matches here. Scroll up to browse earlier companies.";
                return filter==Filter.ALL?"No relevant Campaign companies.":"No matching companies.";
            default:return "";
        }
    }
    boolean canConfirm() {
        Row row=selected();
        return phase==Phase.READY&&row!=null&&isSelected(row.companyId)&&queries.latest()!=null&&queries.latest().rows.contains(row)
            &&row.evaluationState==EvaluationState.COMPLETE&&row.canStartNow&&row.legalRoute
            &&row.legacyFingerprint.matches("[0-9a-f]{64}")&&row.confirmationFingerprint.matches("[0-9a-f]{64}");
    }
    KOMEPacketCompanyMovementRequest confirm() {
        return confirm(false);
    }
    boolean canConfirmAndJoin() {
        Row row=selected();return canConfirm()&&row.canArriveNow&&row.status==KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW
            &&(row.consequence==KOMECompanyMovementService.Consequence.EXPECTED_CREATE_CONFLICT||row.consequence==KOMECompanyMovementService.Consequence.EXPECTED_JOIN_CONFLICT);
    }
    private Object joinKey() {
        Row row=selected();
        return canConfirmAndJoin()?Arrays.asList(session,destination,row.companyId,queries.latest().sequence,row.legacyFingerprint,row.confirmationFingerprint):null;
    }
    boolean joinArmed(long now) {Object key=joinKey();return key!=null&&joinLatch.armed(key,now);}
    KOMEPacketCompanyMovementRequest confirmJoinClick(long now) {
        Object key=joinKey();if(key==null){joinLatch.clear();return null;}
        return joinLatch.click(key,now)?confirm(true):null;
    }
    KOMEPacketCompanyMovementRequest confirm(boolean join) {
        if(join&&!canConfirmAndJoin())return null;
        if(!canConfirm())return null;
        joinLatch.clear();joinAfterArrival=join;
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
        setSelected(null);page=null;movement=null;
        if(result.accepted()) {
            notice=result.outcome==KOMECompanyMovementResult.Outcome.ARRIVED?"Arrived at "+destination+("conflict_held".equals(result.orderStatus)?"; committed to battle.":
                "access_halted".equals(result.orderStatus)?"; halted after access changed.":"."):
                "Order accepted: "+result.orderStatus.replace('_',' ')+". Arrival not completed.";
            if(continueJoin) {
                arrivedMovement=result;
                arrivedCompany=result.companyId;joinView=new kome.common.network.KOMEPacketJoinBattleViewRequest(destination,UUID.randomUUID());
                phase=Phase.JOINING;return null;
            }
            window.clear();pendingStep=0;pendingSelection="";
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
        joinLatch.clear();
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
            case CATALOGUE_LIMIT:return "Company list is too large to load.";
            case COMPANY_LIMIT:return "A company is too large to load.";
            case ARRIVAL_BUDGET_EXCEEDED:return "Movement preview could not finish. Retry to continue.";
            case PROJECTION_LIMIT:return "Company details could not be loaded. Retry to continue.";
            default:return "Could not load companies. Retry or choose another destination.";
        }
    }
}
