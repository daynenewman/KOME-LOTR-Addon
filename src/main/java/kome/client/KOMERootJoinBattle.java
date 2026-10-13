package kome.client;

import java.util.Arrays;
import java.util.UUID;
import kome.common.data.KOMEConflictRecord;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEJoinBattleActionTokenService;
import kome.common.network.KOMEPacketJoinBattleViewRequest;
import kome.common.network.KOMEPacketJoinBattleViewResponse;

/** Correlated presentation of KOM-19 authority. Never sends deployment; only publishes fresh continuation decisions. */
final class KOMERootJoinBattle {
    enum Reception { IGNORED, UPDATED, OPEN_CHOOSER, DIRECT_JOIN, DENIED }
    // Presentation expires conservatively; expiry never polls or sends a request.
    static final long VIEW_LIFETIME_MILLIS=30000L;
    private final KOMECompanyDestinationClientState connection;
    private final KOMEJoinConfirmationLatch latch=new KOMEJoinConfirmationLatch();
    private final java.util.LinkedHashSet<UUID> correlations=new java.util.LinkedHashSet<UUID>();
    private UUID session;
    private String tile="", expectedConflict="", status="";
    private long expectedRevision, acceptedAt;
    private boolean chooserPending, deploying;
    private int intent; // 0 discovery, 1 first activation, 2 armed confirmation
    private KOMEPacketJoinBattleViewRequest pending;
    private KOMEPacketJoinBattleViewResponse view;
    KOMERootJoinBattle(KOMECompanyDestinationClientState connection){this.connection=connection;}
    KOMEPacketJoinBattleViewRequest open(String tile) {
        close();
        if(!connection.isConnected()||!KOMEConquestTile.isCanonicalTileId(tile))return null;
        session=connection.sessionId();this.tile=tile;return refresh();
    }
    boolean current(){
        if(session!=null&&(!connection.isConnected()||!connection.isSession(session)))close();
        return session!=null;
    }
    String tile(){return current()?tile:"";}
    void close(){session=null;tile="";view=null;pending=null;chooserPending=false;deploying=false;intent=0;status="";latch.clear();}
    void cancelAction(){close();}
    boolean boundTo(KOMECompanyDestinationClientState state){return connection==state;}
    boolean owns(UUID correlation){return correlations.contains(correlation);}
    private KOMEPacketJoinBattleViewRequest request(){
        pending=new KOMEPacketJoinBattleViewRequest(tile,UUID.randomUUID());correlations.add(pending.correlation);
        while(correlations.size()>32)correlations.remove(correlations.iterator().next());return pending;
    }
    KOMEPacketJoinBattleViewRequest refresh() {
        if(!current())return null;
        latch.clear();view=null;chooserPending=false;deploying=false;intent=0;status="";
        return request();
    }
    static boolean eligible(KOMEPacketJoinBattleViewResponse view,String tile) {
        if(view==null||!tile.equals(view.tileId)||!view.isAllowed()||view.conflictId.isEmpty()
                ||view.conflictRevision<1||view.conflictState==null||view.conflictState==KOMEConflictRecord.State.ENDED
                ||!view.currentDeploymentConflict.isEmpty()||!view.currentDeploymentTile.isEmpty()
                ||!view.currentDeploymentState.isEmpty()||!KOMEJoinBattleActionTokenService.isUsableToken(view.actionToken))return false;
        return selectableCount(view)>0;
    }
    static int selectableCount(KOMEPacketJoinBattleViewResponse view) {
        int count=0;
        for(KOMEPacketJoinBattleViewResponse.CompanyRow row:view.companyRows())
            if(row.selectable&&!row.companyId.isEmpty()&&row.campaignCombatMembers>0)count++;
        return count;
    }
    UUID session(){return current()?session:null;}
    boolean deploying(){return current()&&deploying;}
    boolean available(long now) {
        if(!current())return false;
        if(view!=null&&now-acceptedAt>=VIEW_LIFETIME_MILLIS){view=null;latch.clear();status="Battle view expired. Reopen the radial to refresh.";}
        boolean eligible=!deploying&&pending==null&&eligible(view,tile);if(!eligible)latch.clear();return eligible;
    }
    private Object key(){return view==null?null:Arrays.asList(session,tile,view.correlation,view.conflictId,view.conflictRevision,view.actionToken);}
    boolean armed(long now){return available(now)&&latch.armed(key(),now);}
    String label(long now){return armed(now)?"Confirm":"Join Battle";}
    String status(){current();return status;}
    boolean chooserPending(){return current()&&(chooserPending||deploying);}
    KOMEPacketJoinBattleViewRequest pending(){return current()?pending:null;}
    KOMEPacketJoinBattleViewRequest click(long now) {
        if(!available(now)){latch.clear();return null;}
        intent=latch.armed(key(),now)?2:1;latch.clear();
        expectedConflict=view.conflictId;expectedRevision=view.conflictRevision;
        chooserPending=true;view=null;status="Refreshing Join Battle...";
        return request();
    }
    Reception accept(KOMEPacketJoinBattleViewResponse response,long now) {
        if(!current())return Reception.IGNORED;
        if(pending==null||!pending.correlation.equals(response.correlation)||!tile.equals(response.tileId)) {
            latch.clear();view=null;chooserPending=false;deploying=false;intent=0;status="Battle view changed. Reopen the radial to refresh.";return Reception.IGNORED;
        }
        boolean opening=chooserPending;int action=intent;intent=0;pending=null;chooserPending=false;latch.clear();view=response;acceptedAt=now;status="";
        if(!eligible(response,tile)) {
            status=response.isAllowed()&&response.currentDeploymentConflict.isEmpty()||response.reasonText==null||response.reasonText.trim().isEmpty()?
                "Join Battle is not currently available. Reopen the radial to refresh.":response.reasonText;
            if(!opening)status=""; // Ordinary unavailable tiles retain the uncluttered five-action root.
            return opening?Reception.DENIED:Reception.UPDATED;
        }
        if(action==2&&(!expectedConflict.equals(response.conflictId)||expectedRevision!=response.conflictRevision)) {
            status="Battle changed. Choose Join Battle again.";return Reception.UPDATED;
        }
        if(!opening)return Reception.UPDATED;
        if(selectableCount(response)>1)return Reception.OPEN_CHOOSER;
        if(action==2){deploying=true;status="Joining battle...";return Reception.DIRECT_JOIN;}
        latch.click(key(),now);status="";return Reception.UPDATED;
    }
}