package kome.client.gui;

import kome.common.network.*;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import kome.client.KOMEMinecraftClient;
import kome.client.KOMEJoinBattleDiagnostics;
import java.util.UUID;

/** Compact KOM-19 Join Battle entry surface. */
public final class KOMEGuiJoinBattle extends GuiScreen {
    private static final int WIDTH=430, HEIGHT=380, JOIN=1, REFRESH=2, CLOSE=3, PREVIOUS=4, NEXT=5, RETREAT=6, ROW=100, PAGE_SIZE=5;
    private KOMEJoinBattleViewModel model;
    private int page;
    private boolean tokenRefreshPending;
    private boolean retreatConfirmation;
    private boolean afterMovement;
    private boolean preferredEntryAttempted;
    private String preferredCompanyId="";
    private UUID pendingJoin, pendingRetreat;
    private kome.client.KOMECompanyDestinationClientState joinSessionState;
    private UUID joinSession;
    private long pendingJoinRevision;
    private String pendingReceipt="", pendingTile="", pendingConflict="";
    private boolean closed;
    private GuiScreen successOwner=this;
    private final Presentation presentation;
    interface Presentation {
        boolean isCurrent(KOMEGuiJoinBattle screen);
        void notifySuccess(String message);
        void close(KOMEGuiJoinBattle screen);
    }
    private static final Presentation LIVE=new Presentation() {
        public boolean isCurrent(KOMEGuiJoinBattle screen){return KOMEMinecraftClient.currentScreen()==screen.successOwner;}
        public void notifySuccess(String message){
            if(KOMEMinecraftClient.player()!=null)KOMEMinecraftClient.player().addChatMessage(new net.minecraft.util.ChatComponentText(message));
        }
        public void close(KOMEGuiJoinBattle screen){
            // Use the mapped vanilla method directly: do not hide an invocation failure
            // inside the reflection compatibility helper after publishing success.
            KOMEMinecraftClient.get().displayGuiScreen(null);
            GuiScreen after=KOMEMinecraftClient.currentScreen();
            if(after==screen.successOwner)KOMEJoinBattleDiagnostics.event("close skipped: GUI-open event retained owning screen");
            else if(after!=null)KOMEJoinBattleDiagnostics.event("close target replaced by GUI-open event; replacement left open");
            else KOMEJoinBattleDiagnostics.event("close completed: world view");
        }
    };
    public KOMEGuiJoinBattle(KOMEPacketJoinBattleViewResponse view){this(view,LIVE);}
    KOMEGuiJoinBattle(KOMEPacketJoinBattleViewResponse view,Presentation presentation){
        model=new KOMEJoinBattleViewModel(view);this.presentation=presentation;
    }
    /** Preference is presentation only: select accepts only the current server's eligible rows. */
    public KOMEGuiJoinBattle(KOMEPacketJoinBattleViewResponse view,String preferredCompany) {
        this(view,preferredCompany,LIVE);
    }
    KOMEGuiJoinBattle(KOMEPacketJoinBattleViewResponse view,String preferredCompany,Presentation presentation) {
        this(view,presentation);afterMovement=true;preferredCompanyId=preferredCompany;
        if(view.currentDeploymentConflict.isEmpty())model.select(preferredCompany);
        if(!view.isAllowed())model.setStatus("Could not join: "+view.reasonText);
        for(int i=0;i<view.companyRows().size();i++)if(view.companyRows().get(i).companyId.equals(model.selectedCompanyId()))page=i/PAGE_SIZE;
    }
    public String tileId(){return model.view().tileId;}
    /** Calls the same KOM-19 action as its Join button; the server still validates the physical anchor. */
    public boolean beginPreferredEntry() {
        String fallback=preferredEntryFallback();
        if(!fallback.isEmpty()) {
            org.apache.logging.log4j.LogManager.getLogger("KOME").debug("Direct Join fallback: {}; tile={} conflict={} revision={}",
                fallback,model.view().tileId,model.view().conflictId,model.view().conflictRevision);
            if(afterMovement)model.setStatus("Could not join: "+fallback);
            return false;
        }
        preferredEntryAttempted=true;
        actionPerformed(new KOMEGuiButton(JOIN,0,0,150,22,"Select / Join Battle"));return true;
    }
    /** One wire row is a company, never a physical anchor. KOM-19 resolves the
     * company's verified member anchor server-side. Duplicate rows are invalid
     * projection evidence, not permission to pick a different company. */
    String preferredEntryFallback() {
        KOMEPacketJoinBattleViewResponse view=model.view();
        if(closed||actionPending()||!afterMovement||preferredEntryAttempted)return "Continuation is no longer current.";
        if(!view.currentDeploymentConflict.isEmpty())return "Existing deployment: "+view.currentDeploymentState.replace('_',' ')+". "+view.reasonText;
        if(!view.isAllowed())return view.reasonText==null||view.reasonText.trim().isEmpty()?"Join denied: "+view.reason:view.reasonText;
        if(view.conflictId.isEmpty()||view.conflictState==null||view.conflictState==kome.common.data.KOMEConflictRecord.State.ENDED)
            return "Battle is no longer active. Refresh the battle view.";
        if(model.selectedCompanyId().isEmpty()) {
            for(KOMEPacketJoinBattleViewResponse.CompanyRow row:view.companyRows())
                if(row.companyId.equals(preferredCompanyId))return "The moved company is not selectable in this battle.";
            return "The moved company is not selectable: absent from the fresh battle view.";
        }
        if(!model.hasUsableActionToken())return "Battle view expired. Refresh before joining.";
        int matches=0;
        for(KOMEPacketJoinBattleViewResponse.CompanyRow row:view.companyRows())
            if(row.selectable&&row.companyId.equals(model.selectedCompanyId()))matches++;
        return matches==1?"":"Company projection is ambiguous. Refresh before joining.";
    }
    /** Reuses KOM-19's action/result state without displaying a redundant picker. */
    public boolean beginPreferredEntry(GuiScreen owner) {
        if(closed||preferredEntryAttempted||actionPending()||owner==null||KOMEMinecraftClient.currentScreen()!=owner)return false;
        successOwner=owner;
        if(beginPreferredEntry())return true;
        successOwner=this;return false;
    }
    /** Root confirmation already chose the sole fresh server-listed company; anchors remain server-only. */
    public boolean beginRootEntry(GuiScreen owner) {
        KOMEPacketJoinBattleViewResponse view=model.view();
        if(closed||preferredEntryAttempted||actionPending()||owner==null||KOMEMinecraftClient.currentScreen()!=owner
                ||!view.isAllowed()||!model.hasUsableActionToken()||!view.currentDeploymentConflict.isEmpty())return false;
        String sole="";int count=0;
        for(KOMEPacketJoinBattleViewResponse.CompanyRow row:view.companyRows())
            if(row.selectable&&!row.companyId.isEmpty()&&row.campaignCombatMembers>0){sole=row.companyId;count++;}
        if(count!=1)return false;
        model.select(sole);successOwner=owner;preferredEntryAttempted=true;
        actionPerformed(new KOMEGuiButton(JOIN,0,0,150,22,"Select / Join Battle"));
        return pendingJoin!=null;
    }
    @Override public boolean doesGuiPauseGame(){return false;}
    public boolean matchesEntryView(KOMEPacketJoinBattleViewResponse view) {
        return pendingJoin!=null&&pendingTile.equals(view.tileId)&&pendingConflict.equals(view.conflictId)
            &&pendingJoinRevision==view.conflictRevision;
    }
    public void useOwnScreen(){successOwner=this;}
    public String status(){return model.status();}
    KOMEJoinBattleViewModel viewModel(){return model;}
    public void acceptView(KOMEPacketJoinBattleViewResponse view){
        if(closed)return;
        pendingJoin=null;pendingRetreat=null;pendingReceipt="";retreatConfirmation=false;
        tokenRefreshPending=false;model.accept(view);if(mc!=null)initGui();
    }
    public boolean acceptSelection(KOMEPacketJoinBattleSelectionResult result){
        if(result!=null)KOMEJoinBattleDiagnostics.event(result.status==KOMEPacketJoinBattleSelectionResult.Status.DEPLOYED
            ?"DEPLOYED received":"non-DEPLOYED received: "+result.status);
        if(joinSessionState!=null&&(!joinSessionState.isConnected()||!joinSessionState.isSession(joinSession)))
            return rejectSelection("session mismatch");
        if(closed)return rejectSelection("owning action/screen closed");
        if(pendingJoin==null)return rejectSelection("no pending Join action (retired or duplicate)");
        if(result==null||result.current==null||result.status==null||result.reason==null
                ||result.message==null||result.message.trim().isEmpty()||result.receiptId==null)
            return rejectSelection("malformed result");
        boolean recovery=new UUID(0,0).equals(result.correlation)&&!pendingReceipt.isEmpty()&&pendingReceipt.equals(result.receiptId);
        if(!pendingJoin.equals(result.correlation)&&!recovery)return rejectSelection("request correlation mismatch");
        if(!presentation.isCurrent(this))return rejectSelection("screen ownership mismatch");
        if(result.status==KOMEPacketJoinBattleSelectionResult.Status.DEPLOYED
                &&(result.reason!=kome.common.data.KOMEJoinBattleService.Reason.ALLOWED||result.receiptId.isEmpty()
                    ||!pendingTile.equals(result.current.currentDeploymentTile)
                    ||!pendingConflict.equals(result.current.currentDeploymentConflict)
                    ||!"IN_BATTLE".equals(result.current.currentDeploymentState)))
            return rejectSelection("DEPLOYED receipt/deployment evidence mismatch");
        if(result.status==KOMEPacketJoinBattleSelectionResult.Status.REJECTED&&afterMovement)
            org.apache.logging.log4j.LogManager.getLogger("KOME").debug("Direct Join rejected after successful movement: {} ({})",result.reason,result.message);
        model.accept(result);
        if(result.status==KOMEPacketJoinBattleSelectionResult.Status.ENTRY_PENDING){
            pendingReceipt=result.receiptId;
        }else{
            pendingJoin=null;pendingReceipt="";
        }
        if(result.status==KOMEPacketJoinBattleSelectionResult.Status.DEPLOYED){
            closeAfterSuccess(result.message);return true;
        }
        if(mc!=null)initGui();
        return true;
    }
    private boolean rejectSelection(String reason){
        KOMEJoinBattleDiagnostics.event("result ignored; close skipped: "+reason);return false;
    }
    public void acceptRetreat(KOMEPacketJoinBattleRetreatResult result){
        if(closed||pendingRetreat==null||result==null||result.code==null||result.message==null
                ||result.message.trim().isEmpty()||!pendingRetreat.equals(result.correlation))return;
        if(result.code==kome.common.data.KOMEFormalRetreatService.Code.RETREATED
                &&(!pendingTile.equals(result.tileId)||!pendingConflict.equals(result.conflictId)))return;
        pendingRetreat=null;
        retreatConfirmation=false;model.setStatus(result.message);
        if(result.code==kome.common.data.KOMEFormalRetreatService.Code.RETREATED){
            closeAfterSuccess(result.message);return;
        }
        if(mc!=null)initGui();
    }
    private boolean actionPending(){return pendingJoin!=null||pendingRetreat!=null;}
    private void closeAfterSuccess(String message){
        if(closed){KOMEJoinBattleDiagnostics.event("close skipped: owning action/screen closed");return;}
        if(!presentation.isCurrent(this)){KOMEJoinBattleDiagnostics.event("close skipped: screen ownership mismatch");return;}
        KOMEJoinBattleDiagnostics.event("close attempted: authoritative success for owning screen");
        closed=true;
        try {presentation.notifySuccess(message);}
        finally {
            if(presentation.isCurrent(this))presentation.close(this);
            else KOMEJoinBattleDiagnostics.event("close skipped: screen ownership changed during success notification");
        }
    }
    @Override public void onGuiClosed(){closed=true;pendingJoin=null;pendingRetreat=null;pendingReceipt="";}

    @Override public void initGui(){
        buttonList.clear();int x=(width-WIDTH)/2,y=(height-HEIGHT)/2;
        int pages=Math.max(1,(model.view().companyRows().size()+PAGE_SIZE-1)/PAGE_SIZE);
        page=Math.max(0,Math.min(page,pages-1));int start=page*PAGE_SIZE;
        int shown=Math.min(PAGE_SIZE,model.view().companyRows().size()-start);
        for(int i=0;i<shown;i++){
            KOMEPacketJoinBattleViewResponse.CompanyRow row=model.view().companyRows().get(start+i);
            KOMEGuiButton button=new KOMEGuiButton(ROW+i,x+24,y+106+i*25,WIDTH-48,21,
                companyLabel(row));
            button.enabled=row.selectable&&model.view().currentDeploymentConflict.isEmpty();
            button.setSelected(row.companyId.equals(model.selectedCompanyId()));buttonList.add(button);
        }
        KOMEGuiButton previous=new KOMEGuiButton(PREVIOUS,x+24,y+238,80,20,"Previous");previous.enabled=page>0;buttonList.add(previous);
        KOMEGuiButton next=new KOMEGuiButton(NEXT,x+112,y+238,80,20,"Next");next.enabled=page+1<pages;buttonList.add(next);
        KOMEGuiButton join=new KOMEGuiButton(JOIN,x+24,y+HEIGHT-36,150,22,"Select / Join Battle");
        join.enabled=!actionPending()&&model.canAttemptSubmit()&&model.view().currentDeploymentConflict.isEmpty();buttonList.add(join);
        if(!model.view().formalRetreatCompanyIds.isEmpty()){
            KOMEGuiButton retreat=new KOMEGuiButton(RETREAT,x+24,y+HEIGHT-64,382,22,
                retreatConfirmation?"Confirm Formal Retreat":"Formal Retreat");
            retreat.enabled=!actionPending()&&model.view().formalRetreatAvailable;buttonList.add(retreat);
        }
        KOMEGuiButton refresh=new KOMEGuiButton(REFRESH,x+184,y+HEIGHT-36,100,22,"Refresh");
        refresh.enabled=!actionPending();buttonList.add(refresh);
        buttonList.add(new KOMEGuiButton(CLOSE,x+294,y+HEIGHT-36,112,22,"Close"));
    }
    @Override protected void actionPerformed(GuiButton button){
        if(closed||!button.enabled||actionPending()&&button.id!=CLOSE)return;
        if(button.id>=ROW&&button.id<ROW+PAGE_SIZE){model.select(model.view().companyRows().get(page*PAGE_SIZE+button.id-ROW).companyId);initGui();return;}
        if(button.id==PREVIOUS){page--;initGui();return;}if(button.id==NEXT){page++;initGui();return;}
        if(button.id==REFRESH){requestFreshView();return;}
        if(button.id==CLOSE){mc.displayGuiScreen(null);return;}
        if(button.id==RETREAT){
            if(!retreatConfirmation){retreatConfirmation=true;model.setStatus(
                "Confirm: "+retreatCompanies(model.view())+" will retreat.");initGui();return;}
            retreatConfirmation=false;
            KOMEPacketJoinBattleRetreatRequest request=new KOMEPacketJoinBattleRetreatRequest(model.view().conflictId);
            pendingRetreat=request.correlation=UUID.randomUUID();
            pendingTile=model.view().tileId;pendingConflict=model.view().conflictId;
            KOMEPacketHandler.network.sendToServer(request);initGui();return;
        }
        if(button.id==JOIN&&model.canAttemptSubmit()){
            if(!model.canSubmit()){
                model.markTokenRefresh();initGui();
                if(!tokenRefreshPending){tokenRefreshPending=true;requestFreshView();}
                return;
            }
            KOMEPacketJoinBattleSelectionRequest request=new KOMEPacketJoinBattleSelectionRequest(
                model.view().tileId,model.view().conflictId,model.view().conflictRevision,
                model.selectedCompanyId(),model.view().actionToken);
            pendingJoin=request.correlation=UUID.randomUUID();
            if(presentation==LIVE&&kome.common.KOMEAddon.proxy instanceof kome.client.KOMEClientProxy) {
                joinSessionState=((kome.client.KOMEClientProxy)kome.common.KOMEAddon.proxy).destinationOptions();
                joinSession=joinSessionState.sessionId();
            }
            pendingTile=request.tileId;pendingConflict=request.conflictId;pendingJoinRevision=request.conflictRevision;
            KOMEPacketHandler.network.sendToServer(request);initGui();
        }
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partial){
        drawDefaultBackground();int x=(width-WIDTH)/2,y=(height-HEIGHT)/2;
        KOMEGuiTheme.drawMainPanel(x,y,WIDTH,HEIGHT);KOMEGuiTheme.drawHeader(fontRendererObj,"Join Battle",x+100,y+12,WIDTH-200);
        KOMEPacketJoinBattleViewResponse v=model.view();
        fontRendererObj.drawString("Tile: "+shown(v.tileId),x+24,y+48,KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString(battleSummary(v),x+24,y+62,KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Faction: "+shown(v.playerFactionId),x+24,y+76,KOMEGuiTheme.COLOR_TEXT);
        String stateLabel=v.currentDeploymentConflict.isEmpty()?(v.isAllowed()?"Can join":"Cannot join"):
            readable(v.currentDeploymentState);
        fontRendererObj.drawString(stateLabel,x+330,y+48,v.currentDeploymentConflict.isEmpty()
            ?(v.isAllowed()?KOMEGuiTheme.COLOR_GOOD:KOMEGuiTheme.COLOR_BAD):KOMEGuiTheme.COLOR_WARN);
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(model.status(),WIDTH-48),x+24,y+91,
            v.isAllowed()?KOMEGuiTheme.COLOR_GOOD:KOMEGuiTheme.COLOR_WARN);
        if(v.companyRows().isEmpty())fontRendererObj.drawString("No eligible committed Campaign companies.",x+24,y+120,KOMEGuiTheme.COLOR_TEXT_MUTED);
        if(afterMovement)fontRendererObj.drawString("Company moved successfully.",x+24,y+260,KOMEGuiTheme.COLOR_GOOD);
        if(!v.currentDeploymentConflict.isEmpty()){
            fontRendererObj.drawString(deploymentSummary(v),x+24,y+270,KOMEGuiTheme.COLOR_WARN);
        }
        if(!v.formalRetreatCompanyIds.isEmpty()){
            fontRendererObj.drawString(fontRendererObj.trimStringToWidth(
                "Formal Retreat: "+retreatCompanies(v),WIDTH-48),x+24,y+286,KOMEGuiTheme.COLOR_TEXT);
            fontRendererObj.drawString(fontRendererObj.trimStringToWidth(
                v.formalRetreatAvailable
                    ?"Withdraws only companies you personally command in this battle."
                    :v.formalRetreatReason,WIDTH-48),x+24,y+300,
                v.formalRetreatAvailable?KOMEGuiTheme.COLOR_TEXT_MUTED:KOMEGuiTheme.COLOR_WARN);
        }
        super.drawScreen(mouseX,mouseY,partial);
    }
    private static String shown(String value){return value==null||value.isEmpty()?"none":value;}
    static String companyLabel(KOMEPacketJoinBattleViewResponse.CompanyRow row){
        String name=row.displayName.trim();
        if(name.isEmpty()||name.equals(row.companyId)||name.matches("C[0-9]+"))name="Campaign Company";
        return name+" - "+row.campaignCombatMembers+(row.campaignCombatMembers==1?" combat unit":" combat units");
    }
    static String battleSummary(KOMEPacketJoinBattleViewResponse view){
        if(view.conflictId.isEmpty()||view.conflictState==null)return "Battle: None";
        switch(view.conflictState){
            case ORDINARY:return "Battle: Active";
            case ENCIRCLEMENT:return "Battle: Encirclement";
            case ENDED:return "Battle: Ended";
            default:return "Battle: Unknown";
        }
    }
    static String deploymentSummary(KOMEPacketJoinBattleViewResponse view){
        String deployment="IN_BATTLE".equals(view.currentDeploymentState)?"In battle":
            "RETURNING".equals(view.currentDeploymentState)?"Returning from":"Joining";
        return deployment+": "+shown(view.currentDeploymentTile);
    }
    static String retreatCompanies(KOMEPacketJoinBattleViewResponse view){
        int count=view.formalRetreatCompanyIds.size();
        return count+(count==1?" personally commanded company":" personally commanded companies");
    }
    private static String readable(String value){
        if(value==null||value.isEmpty())return "Unknown";
        String text=value.replace('_',' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(text.charAt(0))+text.substring(1);
    }
    private void requestFreshView(){
        KOMEPacketHandler.network.sendToServer(new KOMEPacketJoinBattleViewRequest(model.view().tileId));
    }
}
