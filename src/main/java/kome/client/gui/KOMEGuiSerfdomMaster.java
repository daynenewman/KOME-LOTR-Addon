package kome.client.gui;

import java.util.ArrayList;
import java.util.List;
import kome.client.KOMEMinecraftClient;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketSerfdomMasterAction;
import lotr.client.gui.LOTRGuiMenuBase;
import net.minecraft.client.gui.GuiButton;

/** Measured LOTR-menu presentation for canonical Serfdom Master work. */
public class KOMEGuiSerfdomMaster extends LOTRGuiMenuBase {
    private final int entityId,mode;
    private final String masterName,factionName,dutyStatus;
    private final boolean canRequestDuty,hasActiveDuty;
    private List<String> detailLines=new ArrayList<String>();
    private int detailY,buttonY;

    public KOMEGuiSerfdomMaster(int id,String master,String faction,int mode,String duty){this(id,master,faction,mode,duty,false,duty!=null&&duty.toLowerCase().contains("active"));}
    public KOMEGuiSerfdomMaster(int id,String master,String faction,int mode,String duty,boolean canRequest,boolean active){entityId=id;this.mode=mode;masterName=master==null||master.length()==0?"This master":master;factionName=faction==null?"":faction;dutyStatus=duty==null?"":duty;canRequestDuty=canRequest;hasActiveDuty=active;}

    @Override public void initGui(){
        xSize=230;detailLines=wrappedDetails(194);int buttons=buttonCount();
        ySize=panelHeight(detailLines.size(),buttons);super.initGui();
        buttonList.clear();buttonMenuReturn=null;detailY=guiTop+88;buttonY=guiTop+buttonStartOffset(detailLines.size());int center=width/2,row=0;
        if(mode==1&&dutyStatus.startsWith("Trial complete"))buttonList.add(KOMEGuiButton.wide(8,center-72,buttonY+24*row++,"Receive parting gift"));
        else if(mode==1&&canRequestDuty)buttonList.add(KOMEGuiButton.wide(1,center-72,buttonY+24*row++,"Request today's duty"));
        if(mode==1&&hasActiveDuty){int action=activeAction();if(action>=0)buttonList.add(KOMEGuiButton.wide(action,center-72,buttonY+24*row++,actionLabel(action)));buttonList.add(KOMEGuiButton.wide(2,center-72,buttonY+24*row,"View current duty"));}
    }

    private int buttonCount(){if(mode!=1)return 0;if(dutyStatus.startsWith("Trial complete"))return 1;if(hasActiveDuty)return activeAction()>=0?2:1;return canRequestDuty?1:0;}
    static int buttonStartOffset(int detailLines){return 96+Math.max(0,detailLines)*10;}
    static int panelHeight(int detailLines,int buttons){return Math.max(164,buttonStartOffset(detailLines)+Math.max(0,buttons)*24+8);}
    static int detailBottomOffsetForTest(int detailLines){return 88+Math.max(0,detailLines)*10;}
    static int firstButtonOffsetForTest(int detailLines){return buttonStartOffset(detailLines);}
    private int activeAction(){if(dutyStatus.startsWith("Provisioning duty"))return 4;if(dutyStatus.startsWith("Profession duty"))return 5;if(dutyStatus.startsWith("Courier duty - delivered"))return 7;if(dutyStatus.startsWith("Courier duty - outbound"))return 6;return -1;}
    private String actionLabel(int action){return action==4?"Deliver provisions":action==5?"Deliver materials":action==7?"Report to Master":"Replace message";}
    private List<String> wrappedDetails(int width){List<String> result=new ArrayList<String>();String status=mode==1?"Your Serfdom Master":mode==2?"Replacement Master Required":mode==3?"You already serve another master":"Available Serfdom Master";appendWrapped(result,status,width);if(dutyStatus.length()!=0)appendWrapped(result,dutyStatus,width);return result;}
    private void appendWrapped(List<String> output,String text,int width){for(String paragraph:text.split("\\n"))output.addAll(fontRendererObj.listFormattedStringToWidth(paragraph,width));}

    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks){
        drawDefaultBackground();KOMEGuiTheme.drawMainPanel(guiLeft,guiTop,xSize,ySize);KOMEGuiTheme.drawHeader(fontRendererObj,"Serfdom Master",guiLeft+10,guiTop+10,xSize-20);
        int cardX=guiLeft+18,cardY=guiTop+40,cardW=xSize-36;KOMEGuiTheme.drawCard(cardX,cardY,cardW,39,KOMEGuiTheme.isHovered(mouseX,mouseY,cardX,cardY,cardW,39));
        drawCenteredString(fontRendererObj,KOMEGuiTheme.trimToWidth(fontRendererObj,masterName,cardW-12),width/2,cardY+6,KOMEGuiTheme.COLOR_BORDER_RED);
        drawCenteredString(fontRendererObj,KOMEGuiTheme.trimToWidth(fontRendererObj,factionName.length()==0?"No faction":factionName,cardW-12),width/2,cardY+19,KOMEGuiTheme.COLOR_TEXT_MUTED);
        for(int i=0;i<detailLines.size();i++)drawCenteredString(fontRendererObj,detailLines.get(i),width/2,detailY+i*10,KOMEGuiTheme.COLOR_TEXT_MUTED);
        super.drawScreen(mouseX,mouseY,partialTicks);
    }

    @Override public void actionPerformed(GuiButton button){
        if(!button.enabled)return;
        if(button.id==2){KOMEMinecraftClient.displayGui(KOMEGuiProgression.dutyView());return;}
        int action=button.id==8?KOMEPacketSerfdomMasterAction.RECEIVE_PARTING_GIFT:button.id==1?KOMEPacketSerfdomMasterAction.REQUEST_DUTY:button.id==4?KOMEPacketSerfdomMasterAction.DELIVER_PROVISIONS:button.id==5?KOMEPacketSerfdomMasterAction.DELIVER_PROFESSION:button.id==6?KOMEPacketSerfdomMasterAction.REPLACE_COURIER_MESSAGE:button.id==7?KOMEPacketSerfdomMasterAction.REPORT_COURIER:-1;
        if(action>=0)KOMEPacketHandler.network.sendToServer(new KOMEPacketSerfdomMasterAction(entityId,action));
    }

    int detailBottom(){return detailY+detailLines.size()*10;}
    int firstButtonY(){return buttonY;}
}
