package kome.client.gui;

import kome.common.network.*;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import kome.client.KOMEMinecraftClient;
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
    private long pendingJoinRevision;
    private String pendingReceipt="", pendingTile="", pendingConflict="";
    private boolean closed;
    private GuiScreen successOwner=this;
    private final Presentation presentation;
    interface Presentation {
        boolean isCurrent(KOMEGuiJoinBattle screen);
        void notifySuccess(String message);
        void close();
    }
    private static final Presentation LIVE=new Presentation() {
        public boolean isCurrent(KOMEGuiJoinBattle screen){return KOMEMinecraftClient.currentScreen()==screen.successOwner;}
        public void notifySuccess(String message){
            if(KOMEMinecraftClient.player()!=null)KOMEMinecraftClient.player().addChatMessage(new net.minecraft.util.ChatComponentText(message));
        }
        public void close(){KOMEMinecraftClient.displayGui(null);}
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
        if(closed||pendingJoin==null||result==null||result.current==null||result.status==null
                ||result.reason==null||result.message==null||result.message.trim().isEmpty()||result.receiptId==null)return false;
        boolean recovery=new UUID(0,0).equals(result.correlation)&&!pendingReceipt.isEmpty()&&pendingReceipt.equals(result.receiptId);
        if(!pendingJoin.equals(result.correlation)&&!recovery)return false;
        if(result.status==KOMEPacketJoinBattleSelectionResult.Status.DEPLOYED
                &&(result.reason!=kome.common.data.KOMEJoinBattleService.Reason.ALLOWED||result.receiptId.isEmpty()
                    ||!pendingTile.equals(result.current.currentDeploymentTile)
                    ||!pendingConflict.equals(result.current.currentDeploymentConflict)
                    ||!"IN_BATTLE".equals(result.current.currentDeploymentState)))return false;
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
        if(closed||!presentation.isCurrent(this))return;
        closed=true;
        try {presentation.notifySuccess(message);}
        finally {presentation.close();}
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
                (row.displayName.trim().isEmpty()||row.displayName.equals(row.companyId)?"Campaign Company":row.displayName)
                    +" - "+row.campaignCombatMembers+" combat");
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
                "Confirm: retreat all personally commanded companies in "+model.view().conflictId+".");initGui();return;}
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
            pendingTile=request.tileId;pendingConflict=request.conflictId;pendingJoinRevision=request.conflictRevision;
            KOMEPacketHandler.network.sendToServer(request);initGui();
        }
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partial){
        drawDefaultBackground();int x=(width-WIDTH)/2,y=(height-HEIGHT)/2;
        KOMEGuiTheme.drawMainPanel(x,y,WIDTH,HEIGHT);KOMEGuiTheme.drawHeader(fontRendererObj,"Join Battle",x+100,y+12,WIDTH-200);
        KOMEPacketJoinBattleViewResponse v=model.view();
        fontRendererObj.drawString("Tile: "+shown(v.tileId),x+24,y+48,KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Conflict: "+(v.conflictId.isEmpty()?"none":v.conflictId+" r"+v.conflictRevision+" "+v.conflictState),x+24,y+62,KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Faction: "+shown(v.playerFactionId),x+24,y+76,KOMEGuiTheme.COLOR_TEXT);
        String stateLabel=v.currentDeploymentConflict.isEmpty()?(v.isAllowed()?"ALLOWED":"BLOCKED"):
            v.currentDeploymentState.replace('_',' ');
        fontRendererObj.drawString(stateLabel,x+330,y+48,v.currentDeploymentConflict.isEmpty()
            ?(v.isAllowed()?KOMEGuiTheme.COLOR_GOOD:KOMEGuiTheme.COLOR_BAD):KOMEGuiTheme.COLOR_WARN);
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(model.status(),WIDTH-48),x+24,y+91,
            v.isAllowed()?KOMEGuiTheme.COLOR_GOOD:KOMEGuiTheme.COLOR_WARN);
        if(v.companyRows().isEmpty())fontRendererObj.drawString("No eligible committed Campaign companies.",x+24,y+120,KOMEGuiTheme.COLOR_TEXT_MUTED);
        if(afterMovement)fontRendererObj.drawString("Company moved successfully.",x+24,y+260,KOMEGuiTheme.COLOR_GOOD);
        if(!v.currentDeploymentConflict.isEmpty()){
            String deployment="IN_BATTLE".equals(v.currentDeploymentState)?"In battle":
                "RETURNING".equals(v.currentDeploymentState)?"Returning from":"Joining";
            fontRendererObj.drawString(deployment+": "+v.currentDeploymentTile+" / "
                +v.currentDeploymentConflict,x+24,y+270,KOMEGuiTheme.COLOR_WARN);
        }
        if(!v.formalRetreatCompanyIds.isEmpty()){
            String companies=v.formalRetreatCompanyIds.isEmpty()?"none":join(v.formalRetreatCompanyIds);
            fontRendererObj.drawString(fontRendererObj.trimStringToWidth(
                "Formal Retreat from "+v.conflictId+": "+companies,WIDTH-48),x+24,y+286,KOMEGuiTheme.COLOR_TEXT);
            fontRendererObj.drawString(fontRendererObj.trimStringToWidth(
                v.formalRetreatAvailable
                    ?"Withdraws only companies you personally command in this battle."
                    :v.formalRetreatReason,WIDTH-48),x+24,y+300,
                v.formalRetreatAvailable?KOMEGuiTheme.COLOR_TEXT_MUTED:KOMEGuiTheme.COLOR_WARN);
        }
        super.drawScreen(mouseX,mouseY,partial);
    }
    private static String shown(String value){return value==null||value.isEmpty()?"none":value;}
    private static String join(java.util.List<String> values){StringBuilder out=new StringBuilder();for(String value:values){if(out.length()>0)out.append(", ");out.append(value);}return out.toString();}
    private void requestFreshView(){
        KOMEPacketHandler.network.sendToServer(new KOMEPacketJoinBattleViewRequest(model.view().tileId));
    }
}
