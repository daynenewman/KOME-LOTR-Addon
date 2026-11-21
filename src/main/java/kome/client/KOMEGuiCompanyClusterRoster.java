package kome.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kome.client.gui.KOMEGuiTheme;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEUnitMapMarker;
import kome.common.network.KOMECompanyMapProjection;
import lotr.client.gui.LOTRGuiMap;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import static kome.client.KOMECompanyDestinationPanel.*;

/** Map-side read-only selection of exactly one projected cluster; no server query or company action. */
public final class KOMEGuiCompanyClusterRoster {
    private final List<String> memberIds;
    private final long sessionEpoch;
    private final LOTRGuiMap sourceMap;
    private int scroll;
    private String selectedId;

    public KOMEGuiCompanyClusterRoster(List<String> memberIds,LOTRGuiMap sourceMap) {
        this.memberIds=Collections.unmodifiableList(new ArrayList<String>(memberIds));
        this.sessionEpoch=KOMEClientData.INSTANCE.clientSessionEpoch;
        this.sourceMap=sourceMap;
    }
    static final class PanelLayout {
        final Rect panel,close,up,down;
        final int capacity,rowTop,footer;
        PanelLayout(KOMEMapViewport view,int top){
            int width=Math.min(220,(int)(view.right-view.left)-16);
            int height=Math.min(220,(int)view.bottom-top-8);
            panel=new Rect((int)view.right-width-8,top,width,Math.max(0,height));
            rowTop=top+22;footer=top+height-24;
            capacity=Math.max(1,(height-22-26)/30);
            close=new Rect(panel.x+width-23,top+3,18,14);
            up=new Rect(panel.x+width-46,footer+4,18,14);
            down=new Rect(panel.x+width-25,footer+4,18,14);
        }
        Rect row(int index){return new Rect(panel.x+6,rowTop+index*30,panel.width-12,28);}
        int rowAt(int x,int y,int count){
            for(int i=0;i<Math.min(capacity,count);i++)if(row(i).contains(x,y))return i;
            return -1;
        }
    }
    List<String> memberIds(){return memberIds;}
    LOTRGuiMap sourceMap(){return sourceMap;}
    String selectedId(){return selectedId;}
    void scroll(int direction,int capacity){scroll=Math.max(0,Math.min(Math.max(0,memberIds.size()-capacity),scroll+direction));}
    void select(int visibleIndex,int capacity){
        List<Row> rows=currentRows(memberIds,sessionEpoch,sourceMap);
        int index=scroll+visibleIndex;
        if(rows!=null&&visibleIndex>=0&&visibleIndex<capacity&&index<rows.size())selectedId=rows.get(index).id;
    }
    void draw(PanelLayout layout,FontRenderer font,int mouseX,int mouseY){
        List<Row> rows=currentRows(memberIds,sessionEpoch,sourceMap);
        if(rows==null)return;
        Rect p=layout.panel;KOMEGuiTheme.drawSubPanel(p.x,p.y,p.width,p.height);
        font.drawString(rows.size()+" companies | read-only",p.x+8,p.y+6,KOMEGuiTheme.COLOR_GOLD);
        scroll=Math.min(scroll,Math.max(0,rows.size()-layout.capacity));
        for(int i=0;i<layout.capacity&&scroll+i<rows.size();i++){
            Row row=rows.get(scroll+i);Rect box=layout.row(i);
            boolean selected=row.id.equals(selectedId);
            KOMEGuiTheme.drawCard(box.x,box.y,box.width,box.height,selected||box.contains(mouseX,mouseY));
            if(selected)Gui.drawRect(box.x,box.y,box.x+3,box.y+box.height,KOMEGuiTheme.COLOR_GOLD);
            KOMEConquestMapOverlay.drawTexturedMarker(row.art,
                new KOMEMapIconPresentation.Bounds(box.x+5,box.y+5,18,18,255));
            String label=row.spotted?"":row.name;
            font.drawString(KOMEGuiTheme.trimToWidth(font,label,box.width-34),
                box.x+29,box.y+3,KOMEGuiTheme.COLOR_TEXT_LIGHT);
            font.drawString("Units: "+row.units,box.x+29,box.y+16,KOMEGuiTheme.COLOR_TEXT_MUTED);
        }
        Row selected=null;
        for(Row row:rows)if(row.id.equals(selectedId)){selected=row;break;}
        if(selected!=null)
            font.drawString(KOMEGuiTheme.trimToWidth(font,
                selected.spotted?"Units: "+selected.units:selected.name+" | units: "+selected.units,
                p.width-58),p.x+8,layout.footer+7,KOMEGuiTheme.COLOR_TEXT_LIGHT);
        for(Rect box:new Rect[]{layout.up,layout.down,layout.close}){
            KOMEGuiTheme.drawCard(box.x,box.y,box.width,box.height,box.contains(mouseX,mouseY));
            String label=box==layout.close?"X":box==layout.up?"^":"v";
            font.drawString(label,box.x+(box.width-font.getStringWidth(label))/2,box.y+3,KOMEGuiTheme.COLOR_TEXT_LIGHT);
        }
    }

    /** Re-resolve every row against current completed projection and marker replacement. */
    static List<Row> currentRows(List<String> ids,long epoch){
        KOMEClientData data=KOMEClientData.INSTANCE;
        if(epoch!=data.clientSessionEpoch||!data.hasCompleteConquestSnapshot()||ids.size()<2)return null;
        Map<String,KOMEUnitMapMarker> markers=new HashMap<String,KOMEUnitMapMarker>();
        for(KOMEUnitMapMarker marker:data.unitMapMarkers)
            if(marker!=null&&KOMEConquestMapOverlay.companyMarkerCurrent(marker))markers.put(marker.entityId,marker);
        List<Row> rows=new ArrayList<Row>(ids.size());
        for(String id:ids){
            KOMEArmyCompany company=data.armyCompanies.get(id);KOMEUnitMapMarker marker=markers.get(id);
            if(company==null||marker==null)return null;
            boolean spotted=KOMECompanyMapProjection.SPOTTED_STATUS.equals(company.status);
            KOMEMapMarkerArt.Sprite art=KOMEMapMarkerArt.displayed(KOMEMapMarkerArt.company(marker,data.armyCompanies));
            if(art==null)return null;
            rows.add(new Row(id,spotted?"":marker.companyName,Math.max(0,marker.soldierCount),spotted,art));
        }
        return rows;
    }
    static List<Row> currentRows(List<String> ids,long epoch,LOTRGuiMap sourceMap){
        if(epoch!=KOMEClientData.INSTANCE.clientSessionEpoch
                ||!KOMEConquestMapOverlay.companyClusterMembersCurrent(sourceMap,ids))return null;
        return currentRows(ids,epoch);
    }
    boolean stillCurrent(){return currentRows(memberIds,sessionEpoch,sourceMap)!=null;}
    static final class Row {
        final String id,name;final int units;final boolean spotted;final KOMEMapMarkerArt.Sprite art;
        Row(String id,String name,int units,boolean spotted,KOMEMapMarkerArt.Sprite art){
            this.id=id;this.name=name;this.units=units;this.spotted=spotted;this.art=art;
        }
    }
}
