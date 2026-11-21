package kome.client;

import java.util.*;
import kome.client.gui.KOMEGuiTheme;
import kome.common.data.KOMECompanySourceOptions.Row;
import net.minecraft.client.gui.*;
import static kome.client.KOMECompanyDestinationPanel.*;

/** Read-only metadata presentation over the accepted selector geometry. */
final class KOMECompanyUnitsPanel {
    static Layout layout(KOMEMapViewport view,boolean left){
        Layout layout=new Layout(view,left);
        for(Control c:new Control[]{Control.FILTER_ALL,Control.FILTER_READY,Control.FILTER_ARRIVE,Control.CONFIRM,Control.CONFIRM_JOIN})layout.controls.remove(c);
        return layout;
    }
    static boolean selected(KOMECompanyUnitsBrowser browser,Row row){return row.companyId.equals(browser.selectedCompanyId());}
    private static void text(FontRenderer f,String value,int x,int y,int width,int color){f.drawString(KOMEGuiTheme.trimToWidth(f,value,width),x,y,color);}
    static void draw(KOMECompanyUnitsBrowser browser,Layout layout,FontRenderer font,int mx,int my){
        Rect p=layout.panel;KOMEGuiTheme.drawSubPanel(p.x,p.y,p.width,p.height);browser.capacity(layout.capacity);
        text(font,"Units on "+browser.tile(),p.x+8,p.y+6,p.width-60,KOMEGuiTheme.COLOR_GOLD);
        text(font,browser.message(),p.x+8,p.y+28,p.width-16,KOMEGuiTheme.COLOR_TEXT_MUTED);Row hover=null;
        for(int i=0;i<layout.capacity;i++){
            Row row=browser.visible(i);if(row==null)break;Rect box=layout.row(i);boolean selected=selected(browser,row);
            KOMEGuiTheme.drawCard(box.x,box.y,box.width,box.height,selected||box.contains(mx,my));
            if(selected)Gui.drawRect(box.x,box.y,box.x+3,box.y+box.height,KOMEGuiTheme.COLOR_GOLD);
            text(font,KOMECompanyUnitsBrowser.name(row),box.x+5,box.y+2,box.width-10,KOMEGuiTheme.COLOR_TEXT_LIGHT);
            text(font,KOMECompanyUnitsBrowser.population(row),box.x+5,box.y+12,box.width-10,KOMEGuiTheme.COLOR_TEXT_MUTED);
            text(font,(row.mounted?"Mounted":"Foot / mixed")+" | "+row.allowance+"/"+row.tilesPerDay+" allowance",box.x+5,box.y+22,box.width-10,KOMEGuiTheme.COLOR_TEXT_MUTED);
            text(font,row.held?"Committed to battle":row.state,box.x+5,box.y+32,box.width-10,row.held?KOMEGuiTheme.COLOR_WARN:KOMEGuiTheme.COLOR_TEXT_MUTED);
            if(box.contains(mx,my))hover=row;
        }
        Row selected=browser.selected();List<String> details=new ArrayList<>();
        if(selected!=null)for(String line:Arrays.asList(KOMECompanyUnitsBrowser.name(selected),"On "+browser.tile(),KOMECompanyUnitsBrowser.population(selected),selected.state+(selected.held?" | Committed to battle":"")))details.addAll(font.listFormattedStringToWidth(line,p.width-16));
        else details.addAll(font.listFormattedStringToWidth(browser.message(),p.width-16));
        for(int i=0;i<details.size()&&i*10+9<=layout.detailHeight;i++)text(font,details.get(i),p.x+8,layout.detailTop+i*10,p.width-16,KOMEGuiTheme.COLOR_TEXT_LIGHT);
        for(Control c:layout.controls.keySet()){
            Rect box=layout.controls.get(c);boolean enabled=c!=Control.RETRY||!browser.loading();
            KOMEGuiTheme.drawCard(box.x,box.y,box.width,box.height,enabled&&box.contains(mx,my));
            String label=c==Control.ROWS_UP?"^":c==Control.ROWS_DOWN?"v":c==Control.RETRY?"Refresh":c==Control.BACK?"Back":c==Control.CLEAR?"Clear":"Close";
            font.drawString(label,box.x+(box.width-font.getStringWidth(label))/2,box.y+5,enabled?KOMEGuiTheme.COLOR_TEXT_LIGHT:KOMEGuiTheme.COLOR_TEXT_DISABLED);
        }
        Rect track=layout.scrollTrack;int total=Math.max(layout.capacity,browser.rows().size()),height=Math.max(8,track.height*layout.capacity/total);
        int y=track.y+(track.height-height)*browser.offset()/Math.max(1,total-layout.capacity);
        Gui.drawRect(track.x,track.y,track.x+track.width,track.y+track.height,0xFF120D08);Gui.drawRect(track.x,y,track.x+track.width,y+height,browser.loading()?KOMEGuiTheme.COLOR_WARN:KOMEGuiTheme.COLOR_GOLD);
        if(browser.earlier())Gui.drawRect(track.x-2,track.y,track.x+10,track.y+2,KOMEGuiTheme.COLOR_WARN);
        if(browser.later())Gui.drawRect(track.x-2,track.y+track.height-2,track.x+10,track.y+track.height,KOMEGuiTheme.COLOR_WARN);
        if(track.contains(mx,my)&&KOMEConquestMapOverlay.panelHoverAllowed("units:scroll"))KOMEGuiTheme.drawTooltip(font,Arrays.asList("Loaded companies","Scrollbar shows loaded rows, not a global total."),mx,my,layout.viewRight,layout.viewBottom);
        else if(hover!=null&&KOMEConquestMapOverlay.panelHoverAllowed("units:"+hover.companyId))KOMEGuiTheme.drawTooltip(font,Arrays.asList(KOMECompanyUnitsBrowser.name(hover),KOMECompanyUnitsBrowser.population(hover),hover.state,hover.held?"Committed to battle":"Not committed to battle"),mx,my,layout.viewRight,layout.viewBottom);
    }
}
