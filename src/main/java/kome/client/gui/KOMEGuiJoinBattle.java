package kome.client.gui;

import kome.common.network.*;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/** Compact KOM-19 Join Battle entry surface. */
public final class KOMEGuiJoinBattle extends GuiScreen {
    private static final int WIDTH=430, HEIGHT=380, JOIN=1, REFRESH=2, CLOSE=3, PREVIOUS=4, NEXT=5, RETREAT=6, ROW=100, PAGE_SIZE=5;
    private KOMEJoinBattleViewModel model;
    private int page;
    private boolean tokenRefreshPending;
    private boolean retreatConfirmation;
    public KOMEGuiJoinBattle(KOMEPacketJoinBattleViewResponse view){model=new KOMEJoinBattleViewModel(view);}
    public String tileId(){return model.view().tileId;}
    KOMEJoinBattleViewModel viewModel(){return model;}
    public void acceptView(KOMEPacketJoinBattleViewResponse view){tokenRefreshPending=false;model.accept(view);if(mc!=null)initGui();}
    public void acceptSelection(KOMEPacketJoinBattleSelectionResult result){
        model.accept(result);
        if(mc!=null&&result.status==KOMEPacketJoinBattleSelectionResult.Status.DEPLOYED){
            if(mc.thePlayer!=null)mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(result.message));
            mc.displayGuiScreen(null);return;
        }
        if(mc!=null)initGui();
    }
    public void acceptRetreat(KOMEPacketJoinBattleRetreatResult result){
        retreatConfirmation=false;model.setStatus(result.message);
        if(mc!=null&&result.code==kome.common.data.KOMEFormalRetreatService.Code.RETREATED){
            if(mc.thePlayer!=null)mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(result.message));
            mc.displayGuiScreen(null);return;
        }
        if(mc!=null)initGui();
    }

    @Override public void initGui(){
        buttonList.clear();int x=(width-WIDTH)/2,y=(height-HEIGHT)/2;
        int pages=Math.max(1,(model.view().companyRows().size()+PAGE_SIZE-1)/PAGE_SIZE);
        page=Math.max(0,Math.min(page,pages-1));int start=page*PAGE_SIZE;
        int shown=Math.min(PAGE_SIZE,model.view().companyRows().size()-start);
        for(int i=0;i<shown;i++){
            KOMEPacketJoinBattleViewResponse.CompanyRow row=model.view().companyRows().get(start+i);
            KOMEGuiButton button=new KOMEGuiButton(ROW+i,x+24,y+106+i*25,WIDTH-48,21,
                row.displayName+" ["+row.companyId+"] - "+row.campaignCombatMembers+" combat");
            button.enabled=row.selectable&&model.view().currentDeploymentConflict.isEmpty();
            button.setSelected(row.companyId.equals(model.selectedCompanyId()));buttonList.add(button);
        }
        KOMEGuiButton previous=new KOMEGuiButton(PREVIOUS,x+24,y+238,80,20,"Previous");previous.enabled=page>0;buttonList.add(previous);
        KOMEGuiButton next=new KOMEGuiButton(NEXT,x+112,y+238,80,20,"Next");next.enabled=page+1<pages;buttonList.add(next);
        KOMEGuiButton join=new KOMEGuiButton(JOIN,x+24,y+HEIGHT-36,150,22,"Select / Join Battle");
        join.enabled=model.canAttemptSubmit()&&model.view().currentDeploymentConflict.isEmpty();buttonList.add(join);
        if(!model.view().formalRetreatCompanyIds.isEmpty()){
            KOMEGuiButton retreat=new KOMEGuiButton(RETREAT,x+24,y+HEIGHT-64,382,22,
                retreatConfirmation?"Confirm Formal Retreat":"Formal Retreat");
            retreat.enabled=model.view().formalRetreatAvailable;buttonList.add(retreat);
        }
        buttonList.add(new KOMEGuiButton(REFRESH,x+184,y+HEIGHT-36,100,22,"Refresh"));
        buttonList.add(new KOMEGuiButton(CLOSE,x+294,y+HEIGHT-36,112,22,"Close"));
    }
    @Override protected void actionPerformed(GuiButton button){
        if(!button.enabled)return;
        if(button.id>=ROW&&button.id<ROW+PAGE_SIZE){model.select(model.view().companyRows().get(page*PAGE_SIZE+button.id-ROW).companyId);initGui();return;}
        if(button.id==PREVIOUS){page--;initGui();return;}if(button.id==NEXT){page++;initGui();return;}
        if(button.id==REFRESH){requestFreshView();return;}
        if(button.id==CLOSE){mc.displayGuiScreen(null);return;}
        if(button.id==RETREAT){
            if(!retreatConfirmation){retreatConfirmation=true;model.setStatus(
                "Confirm: retreat all personally commanded companies in "+model.view().conflictId+".");initGui();return;}
            retreatConfirmation=false;
            KOMEPacketHandler.network.sendToServer(new KOMEPacketJoinBattleRetreatRequest(
                model.view().conflictId));return;
        }
        if(button.id==JOIN&&model.canAttemptSubmit()){
            if(!model.canSubmit()){
                model.markTokenRefresh();initGui();
                if(!tokenRefreshPending){tokenRefreshPending=true;requestFreshView();}
                return;
            }
            KOMEPacketHandler.network.sendToServer(new KOMEPacketJoinBattleSelectionRequest(
                model.view().tileId,model.view().conflictId,model.view().conflictRevision,
                model.selectedCompanyId(),model.view().actionToken));
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
