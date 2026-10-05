package kome.client.gui;

import kome.common.network.*;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/** Compact KOM-19 read/selection surface. Physical deployment is deliberately absent. */
public final class KOMEGuiJoinBattle extends GuiScreen {
    private static final int WIDTH=430, HEIGHT=320, JOIN=1, REFRESH=2, CLOSE=3, PREVIOUS=4, NEXT=5, ROW=100, PAGE_SIZE=5;
    private KOMEJoinBattleViewModel model;
    private int page;
    public KOMEGuiJoinBattle(KOMEPacketJoinBattleViewResponse view){model=new KOMEJoinBattleViewModel(view);}
    public String tileId(){return model.view().tileId;}
    public void acceptView(KOMEPacketJoinBattleViewResponse view){model.accept(view);if(mc!=null)initGui();}
    public void acceptSelection(KOMEPacketJoinBattleSelectionResult result){model.accept(result);if(mc!=null)initGui();}

    @Override public void initGui(){
        buttonList.clear();int x=(width-WIDTH)/2,y=(height-HEIGHT)/2;
        int pages=Math.max(1,(model.view().companyRows().size()+PAGE_SIZE-1)/PAGE_SIZE);
        page=Math.max(0,Math.min(page,pages-1));int start=page*PAGE_SIZE;
        int shown=Math.min(PAGE_SIZE,model.view().companyRows().size()-start);
        for(int i=0;i<shown;i++){
            KOMEPacketJoinBattleViewResponse.CompanyRow row=model.view().companyRows().get(start+i);
            KOMEGuiButton button=new KOMEGuiButton(ROW+i,x+24,y+106+i*25,WIDTH-48,21,
                row.displayName+" ["+row.companyId+"] - "+row.campaignCombatMembers+" combat");
            button.enabled=row.selectable;button.setSelected(row.companyId.equals(model.selectedCompanyId()));buttonList.add(button);
        }
        KOMEGuiButton previous=new KOMEGuiButton(PREVIOUS,x+24,y+238,80,20,"Previous");previous.enabled=page>0;buttonList.add(previous);
        KOMEGuiButton next=new KOMEGuiButton(NEXT,x+112,y+238,80,20,"Next");next.enabled=page+1<pages;buttonList.add(next);
        KOMEGuiButton join=new KOMEGuiButton(JOIN,x+24,y+HEIGHT-36,150,22,"Select / Join Battle");
        join.enabled=model.canSubmit();buttonList.add(join);
        buttonList.add(new KOMEGuiButton(REFRESH,x+184,y+HEIGHT-36,100,22,"Refresh"));
        buttonList.add(new KOMEGuiButton(CLOSE,x+294,y+HEIGHT-36,112,22,"Close"));
    }
    @Override protected void actionPerformed(GuiButton button){
        if(!button.enabled)return;
        if(button.id>=ROW&&button.id<ROW+PAGE_SIZE){model.select(model.view().companyRows().get(page*PAGE_SIZE+button.id-ROW).companyId);initGui();return;}
        if(button.id==PREVIOUS){page--;initGui();return;}if(button.id==NEXT){page++;initGui();return;}
        if(button.id==REFRESH){KOMEPacketHandler.network.sendToServer(new KOMEPacketJoinBattleViewRequest(model.view().tileId));return;}
        if(button.id==CLOSE){mc.displayGuiScreen(null);return;}
        if(button.id==JOIN&&model.canSubmit())KOMEPacketHandler.network.sendToServer(new KOMEPacketJoinBattleSelectionRequest(
            model.view().tileId,model.view().conflictId,model.view().conflictRevision,model.selectedCompanyId()));
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partial){
        drawDefaultBackground();int x=(width-WIDTH)/2,y=(height-HEIGHT)/2;
        KOMEGuiTheme.drawMainPanel(x,y,WIDTH,HEIGHT);KOMEGuiTheme.drawHeader(fontRendererObj,"Join Battle",x+100,y+12,WIDTH-200);
        KOMEPacketJoinBattleViewResponse v=model.view();
        fontRendererObj.drawString("Tile: "+shown(v.tileId),x+24,y+48,KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Conflict: "+(v.conflictId.isEmpty()?"none":v.conflictId+" r"+v.conflictRevision+" "+v.conflictState),x+24,y+62,KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Faction: "+shown(v.playerFactionId),x+24,y+76,KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString(v.isAllowed()?"ALLOWED":"BLOCKED",x+330,y+48,v.isAllowed()?KOMEGuiTheme.COLOR_GOOD:KOMEGuiTheme.COLOR_BAD);
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(model.status(),WIDTH-48),x+24,y+91,
            v.isAllowed()?KOMEGuiTheme.COLOR_GOOD:KOMEGuiTheme.COLOR_WARN);
        if(v.companyRows().isEmpty())fontRendererObj.drawString("No eligible committed Campaign companies.",x+24,y+120,KOMEGuiTheme.COLOR_TEXT_MUTED);
        super.drawScreen(mouseX,mouseY,partial);
    }
    private static String shown(String value){return value==null||value.isEmpty()?"none":value;}
}
