package kome.client.gui;
import kome.client.KOMEMinecraftClient;import kome.common.network.KOMEPacketAllianceAction;import kome.common.network.KOMEPacketHandler;import lotr.client.gui.LOTRGuiMenu;import lotr.client.gui.LOTRGuiMenuBase;import net.minecraft.client.gui.GuiButton;import java.util.*;
/** Compact canonical Neutral/Friends/Allies diplomacy screen. */
public class KOMEGuiAllianceUnified extends LOTRGuiMenuBase {
 private static final int REFRESH=3,REQUEST=7,ACCEPT=20,CANCEL=21;private static final List<String[]> relations=new ArrayList<String[]>(),options=new ArrayList<String[]>();private static String viewerFaction="",summary="Diplomacy: 0";
 public static void update(List lines){relations.clear();options.clear();if(lines==null)return;for(Object value:lines){String[] p=String.valueOf(value).split("\t",-1);if(p.length>0&&"VIEWER".equals(p[0]))viewerFaction=p.length>1?p[1]:"";else if(p.length>1&&"SUMMARY".equals(p[0]))summary="Diplomacy: "+p[1];else if(p.length>=12&&"DIPLOMACY_RELATION".equals(p[0]))relations.add(p);else if(p.length>=8&&"DIPLOMACY_REQUEST_OPTION".equals(p[0]))options.add(p);}}
 public static void resetData(){relations.clear();options.clear();viewerFaction="";summary="Diplomacy: 0";}
 @Override public void initGui(){buttonList.clear();buttonList.add(new KOMEGuiButton(1,8,8,80,22,"Menu"));buttonList.add(new KOMEGuiButton(REFRESH,96,8,80,22,"Refresh"));if(!options.isEmpty())buttonList.add(new KOMEGuiButton(REQUEST,184,8,120,22,"Send Request"));if(!relations.isEmpty()){buttonList.add(new KOMEGuiButton(ACCEPT,312,8,110,22,"Accept Request"));buttonList.add(new KOMEGuiButton(CANCEL,430,8,110,22,"Cancel Request"));}}
 @Override public void actionPerformed(GuiButton button){if(button.id==1){mc.displayGuiScreen(new LOTRGuiMenu());return;}if(button.id==REFRESH){requestData();return;}if(button.id==REQUEST&&!options.isEmpty()){String[] p=options.get(0);send("request",viewerFaction,p[1],"friends");}else if(button.id==ACCEPT&&!relations.isEmpty()){String[] p=relations.get(0);send("accept",p[2],p[3],"");}else if(button.id==CANCEL&&!relations.isEmpty()){String[] p=relations.get(0);send("cancel",p[2],p[3],"");}}
 private void send(String action,String first,String second,String relation){KOMEPacketHandler.network.sendToServer(new KOMEPacketAllianceAction(action,relation,first,second));}private void requestData(){if(mc!=null&&mc.thePlayer!=null)KOMEMinecraftClient.sendChat("/alliance list");}
 @Override public void drawScreen(int mouseX,int mouseY,float partialTicks){drawDefaultBackground();drawCenteredString(fontRendererObj,summary,width/2,42,0xFFFFFF);int y=65;for(String[] p:relations){drawString(fontRendererObj,p[2]+" / "+p[3]+"  "+p[4]+(p[6].length()>0?" pending "+p[6]:""),20,y,0xFFFFFF);y+=14;}for(String[] p:options){drawString(fontRendererObj,p[2]+" current "+p[3]+" Friends "+p[5]+" Allies "+p[6]+(p[7].length()>0?" - "+p[7]:""),20,y,0xCCCCCC);y+=14;}super.drawScreen(mouseX,mouseY,partialTicks);}

 /**
  * Legacy visual-capture compatibility only.
  * Canonical diplomacy no longer has the retired Stage/tab/break UI states.
  */
 public void setVisualTestState(int mode, int tab, String pair, boolean confirmBreak) {
 }
}
