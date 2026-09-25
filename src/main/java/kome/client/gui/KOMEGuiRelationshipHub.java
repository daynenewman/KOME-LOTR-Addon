package kome.client.gui;
import kome.common.network.*;import lotr.client.gui.LOTRGuiMenuBase;import net.minecraft.client.gui.GuiButton;
/** Deliberately small LOTR-menu-style intermediate for canonical relationships. */
public class KOMEGuiRelationshipHub extends LOTRGuiMenuBase {
 private final int id,type;private final String name,faction;
 public KOMEGuiRelationshipHub(int i,int t,String n,String f){id=i;type=t;name=n==null||n.length()==0?"This NPC":n;faction=f==null?"":f;}
 public void initGui(){xSize=220;ySize=174;super.initGui();buttonList.clear();buttonMenuReturn=null;int c=width/2;buttonList.add(KOMEGuiButton.wide(0,c-72,guiTop+90,"Talk"));buttonList.add(KOMEGuiButton.wide(1,c-72,guiTop+114,"Service"));buttonList.add(KOMEGuiButton.wide(2,c-72,guiTop+138,type==KOMEPacketRelationshipAction.MASTER?"Leave Master":"Leave Liege"));}
 public void drawScreen(int mx,int my,float pt){drawDefaultBackground();KOMEGuiTheme.drawMainPanel(guiLeft,guiTop,xSize,ySize);KOMEGuiTheme.drawHeader(fontRendererObj,type==KOMEPacketRelationshipAction.MASTER?"Serfdom Master":"Prospective Liege",guiLeft+10,guiTop+10,xSize-20);int x=guiLeft+18,y=guiTop+42,w=xSize-36;KOMEGuiTheme.drawCard(x,y,w,33,KOMEGuiTheme.isHovered(mx,my,x,y,w,33));drawCenteredString(fontRendererObj,KOMEGuiTheme.trimToWidth(fontRendererObj,name,w-12),width/2,y+7,KOMEGuiTheme.COLOR_BORDER_RED);drawCenteredString(fontRendererObj,KOMEGuiTheme.trimToWidth(fontRendererObj,faction.length()==0?"":faction,w-12),width/2,y+19,KOMEGuiTheme.COLOR_TEXT_MUTED);super.drawScreen(mx,my,pt);}
 public void actionPerformed(GuiButton b){if(!b.enabled)return;int action=b.id==0?KOMEPacketRelationshipAction.TALK:b.id==1?KOMEPacketRelationshipAction.SERVICE:KOMEPacketRelationshipAction.LEAVE;KOMEPacketHandler.network.sendToServer(new KOMEPacketRelationshipAction(id,type,action));mc.displayGuiScreen(null);}
}
