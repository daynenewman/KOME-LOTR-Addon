package kome.client;

import java.util.*;
import kome.client.gui.KOMEGuiTheme;
import kome.common.data.KOMECompanyDestinationOptions.Row;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/** Compact native-GUI-space selector; rectangle geometry is shared by drawing and click capture. */
final class KOMECompanyDestinationPanel {
    static final int ROW_HEIGHT=42;
    enum Control { ROWS_UP, ROWS_DOWN, FILTER_ALL, FILTER_READY, FILTER_ARRIVE, RETRY, CONFIRM, CONFIRM_JOIN, BACK, CLEAR, CLOSE }
    static final class Rect {
        final int x,y,width,height;
        Rect(int x,int y,int width,int height) {this.x=x;this.y=y;this.width=width;this.height=height;}
        boolean contains(int mx,int my) {return mx>=x&&mx<x+width&&my>=y&&my<y+height;}
    }
    static final class Layout {
        final Rect panel,scrollTrack;
        final boolean showJoin;
        final int capacity,rowTop,detailTop,detailHeight,viewRight,viewBottom,viewTop;
        final EnumMap<Control,Rect> controls=new EnumMap<Control,Rect>(Control.class);
        Layout(KOMEMapViewport view,boolean left) {
            this(view,left,false);
        }
        Layout(KOMEMapViewport view,boolean left,boolean showJoin) {
            this.showJoin=showJoin;
            viewRight=(int)view.right;viewBottom=(int)view.bottom;viewTop=(int)view.top;
            int available=Math.max(1,(int)(view.bottom-view.top)-16);
            int width=Math.min((int)(view.right-view.left)-16,Math.min(260,Math.max(180,(int)(view.right-view.left)/3)));
            int chrome=176;
            capacity=Math.max(1,Math.min(4,(available-chrome)/ROW_HEIGHT));
            int height=Math.min(available,230+capacity*ROW_HEIGHT);
            int x=left?(int)view.left+8:(int)view.right-width-8,y=(int)view.top+8;
            panel=new Rect(x,y,width,height);rowTop=y+48;detailTop=rowTop+capacity*ROW_HEIGHT+4;
            int footer=y+height-90;detailHeight=Math.max(0,footer-detailTop-4);
            scrollTrack=new Rect(x+width-14,rowTop,8,capacity*ROW_HEIGHT-2);
            Control[] filters={Control.FILTER_ALL,Control.FILTER_READY,Control.FILTER_ARRIVE};
            int filterWidth=(width-20)/3;
            for(int i=0;i<filters.length;i++)controls.put(filters[i],new Rect(x+6+i*(filterWidth+4),y+24,filterWidth,18));
            controls.put(Control.ROWS_UP,new Rect(x+width-44,y+4,18,14));
            controls.put(Control.ROWS_DOWN,new Rect(x+width-24,y+4,18,14));
            controls.put(Control.CONFIRM,new Rect(x+6,footer,panel.width-12,20));
            footer+=28;
            if(showJoin)controls.put(Control.CONFIRM_JOIN,new Rect(x+6,footer,panel.width-12,20));
            footer+=24;
            int buttonWidth=(width-20)/3;
            controls.put(Control.RETRY,new Rect(x+6,footer,panel.width-12,18));
            Control[] lower={Control.BACK,Control.CLEAR,Control.CLOSE};
            for(int i=0;i<3;i++) {
                controls.put(lower[i],new Rect(x+6+i*(buttonWidth+4),footer+20,buttonWidth,18));
            }
        }
        Rect row(int visible) {return new Rect(panel.x+6,rowTop+visible*ROW_HEIGHT,panel.width-26,ROW_HEIGHT-2);}
        int rowAt(int x,int y,int count) {
            for(int i=0;i<Math.min(capacity,count);i++)if(row(i).contains(x,y))return i;
            return -1;
        }
        Control controlAt(int x,int y) {for(Control c:Control.values())if(controls.get(c)!=null&&controls.get(c).contains(x,y))return c;return null;}
    }
    static boolean enabled(Control control,KOMECompanyDestinationSelector model,Layout layout,long now) {
        switch(control) {
            case ROWS_UP:return model.canBrowse(-1,now);
            case ROWS_DOWN:return model.canBrowse(1,now);
            case FILTER_ALL:case FILTER_READY:case FILTER_ARRIVE:return model.canChangeFilter();
            case RETRY:return model.canRetry(now);
            case CONFIRM:return model.canConfirm();
            case CONFIRM_JOIN:return model.canConfirmAndJoin();
            case CLEAR:return model.selected()!=null&&model.phase()!=KOMECompanyDestinationSelector.Phase.SUBMITTING;
            case BACK:case CLOSE:
                return model.phase()!=KOMECompanyDestinationSelector.Phase.SUBMITTING
                    &&model.phase()!=KOMECompanyDestinationSelector.Phase.JOINING;
            default:return true;
        }
    }
    static void draw(KOMECompanyDestinationSelector model,Layout layout,FontRenderer font,int mouseX,int mouseY,long now) {
        Rect panel=layout.panel;
        Gui.drawRect(panel.x,panel.y,panel.x+panel.width,panel.y+panel.height,0xFF24170E);
        KOMEGuiTheme.drawSubPanel(panel.x,panel.y,panel.width,panel.height);
        text(font,model.sourceScoped()?"Move "+model.source()+" -> "+model.destination():"Move to "+model.destination(),panel.x+8,panel.y+6,panel.width-16,KOMEGuiTheme.COLOR_GOLD);
        if(model.sourceScoped())text(font,"Choose company from "+model.source(),panel.x+8,panel.y+28,panel.width-16,KOMEGuiTheme.COLOR_TEXT_MUTED);
        int count=model.rows().size(),offset=model.rowOffset(),shown=Math.min(layout.capacity,Math.max(0,count-offset));
        Scrollbar scrollbar=new Scrollbar(model,layout);
        Gui.drawRect(layout.scrollTrack.x,layout.scrollTrack.y,layout.scrollTrack.x+layout.scrollTrack.width,layout.scrollTrack.y+layout.scrollTrack.height,0xFF120D08);
        Gui.drawRect(scrollbar.thumb.x,scrollbar.thumb.y,scrollbar.thumb.x+scrollbar.thumb.width,scrollbar.thumb.y+scrollbar.thumb.height,scrollbar.pending?KOMEGuiTheme.COLOR_WARN:KOMEGuiTheme.COLOR_GOLD);
        if(scrollbar.earlier)Gui.drawRect(layout.scrollTrack.x-2,layout.scrollTrack.y,layout.scrollTrack.x+10,layout.scrollTrack.y+2,KOMEGuiTheme.COLOR_WARN);
        if(scrollbar.later)Gui.drawRect(layout.scrollTrack.x-2,layout.scrollTrack.y+layout.scrollTrack.height-2,layout.scrollTrack.x+10,layout.scrollTrack.y+layout.scrollTrack.height,KOMEGuiTheme.COLOR_WARN);
        for(int i=0;i<shown;i++) {
            Row row=model.visibleRow(i);Rect box=layout.row(i);
            boolean selected=model.isSelected(row.companyId),hover=box.contains(mouseX,mouseY);
            KOMEGuiTheme.drawCard(box.x,box.y,box.width,box.height,selected||hover);
            if(selected)Gui.drawRect(box.x,box.y,box.x+3,box.y+box.height,KOMEGuiTheme.COLOR_GOLD);
            text(font,KOMECompanyDestinationSelector.name(row),box.x+5,box.y+2,box.width-10,KOMEGuiTheme.COLOR_TEXT_LIGHT);
            text(font,stats(row),box.x+5,box.y+12,box.width-10,KOMEGuiTheme.COLOR_TEXT_MUTED);
            String state=KOMECompanyDestinationSelector.state(row);
            if(state.equals("Blocked")&&!row.denialDetail.isEmpty())state+=": "+row.denialDetail;
            text(font,state,box.x+5,box.y+22,box.width-10,color(row));
            text(font,KOMECompanyDestinationSelector.eta(row)+" | "+KOMECompanyDestinationSelector.population(row),
                box.x+5,box.y+32,box.width-10,KOMEGuiTheme.COLOR_TEXT_MUTED);
        }
        if(count==0) {
            List<String> lines=font.listFormattedStringToWidth(model.message(now),panel.width-16);
            for(int i=0;i<lines.size()&&layout.rowTop+6+i*10<layout.detailTop-4;i++)
                font.drawString(lines.get(i),panel.x+8,layout.rowTop+6+i*10,KOMEGuiTheme.COLOR_TEXT_LIGHT);
        }
        List<String> details=new ArrayList<String>();
        Row selected=model.selected();
        if(model.phase()!=KOMECompanyDestinationSelector.Phase.READY) {
            details.add(model.message(now));
            if(!model.notice().isEmpty())details.add(model.notice());
        }
        else if(model.joinArmed(now))details.add("Confirm Join Battle to deploy yourself.");
        else if(!model.notice().isEmpty())details.addAll(font.listFormattedStringToWidth(model.notice(),panel.width-16));
        else if(model.phase()==KOMECompanyDestinationSelector.Phase.SUBMITTING)details.add("Submitting movement order...");
        else if(selected!=null) {
            // Consequence stays visible above the explicit button, including at the minimum GUI size.
            details.add(KOMECompanyDestinationSelector.confirmationSummary(selected).get(3));
            details.add(KOMECompanyDestinationSelector.name(selected));
            details.add(selected.currentTile+" -> "+selected.destinationTile);
            details.add(KOMECompanyDestinationSelector.population(selected));
            details.add(KOMECompanyDestinationSelector.classification(selected)+" | "+selected.distance+" tiles");
            details.add(KOMECompanyDestinationSelector.eta(selected));
            if(!KOMECompanyDestinationSelector.reason(selected).isEmpty())details.add(KOMECompanyDestinationSelector.reason(selected));
        } else details.add("Select a company to review its move.");
        for(int line=0;line<details.size()&&line*10+9<=layout.detailHeight;line++)
            text(font,details.get(line),panel.x+8,layout.detailTop+line*10,panel.width-16,
                line==0?KOMEGuiTheme.COLOR_WARN:KOMEGuiTheme.COLOR_TEXT_MUTED);
        for(Control control:Control.values()) {
            Rect rect=layout.controls.get(control);
            if(rect==null)continue;
            boolean active=enabled(control,model,layout,now);
            boolean chosen=filterSelected(control,model);
            KOMEGuiTheme.drawCard(rect.x,rect.y,rect.width,rect.height,chosen||active&&rect.contains(mouseX,mouseY));
            if(chosen)Gui.drawRect(rect.x,rect.y+rect.height-2,rect.x+rect.width,rect.y+rect.height,KOMEGuiTheme.COLOR_GOLD);
            if(control==Control.CONFIRM_JOIN) {
                Gui.drawRect(rect.x,rect.y,rect.x+rect.width,rect.y+1,KOMEGuiTheme.COLOR_BAD);
                Gui.drawRect(rect.x,rect.y+rect.height-1,rect.x+rect.width,rect.y+rect.height,KOMEGuiTheme.COLOR_BAD);
                Gui.drawRect(rect.x,rect.y,rect.x+1,rect.y+rect.height,KOMEGuiTheme.COLOR_BAD);
                Gui.drawRect(rect.x+rect.width-1,rect.y,rect.x+rect.width,rect.y+rect.height,KOMEGuiTheme.COLOR_BAD);
            }
            String label=label(control,model,now);
            font.drawString(label,rect.x+(rect.width-font.getStringWidth(label))/2,rect.y+(rect.height-8)/2,
                active?KOMEGuiTheme.COLOR_TEXT_LIGHT:KOMEGuiTheme.COLOR_TEXT_DISABLED);
        }
        if(layout.scrollTrack.contains(mouseX,mouseY)&&KOMEConquestMapOverlay.panelHoverAllowed("destination:scroll"))
            KOMEGuiTheme.drawTooltip(font,Arrays.asList("Loaded companies", "The thumb shows loaded rows, not a global total.",
                scrollbar.pending?"Loading companies...":scrollbar.later?"Scroll to load more companies.":"End of the loaded list."),
                mouseX,mouseY,layout.viewRight,layout.viewBottom);
        int hover=layout.rowAt(mouseX,mouseY,Math.max(0,count-offset));
        if(hover>=0&&KOMEConquestMapOverlay.panelHoverAllowed("destination:"+model.rows().get(offset+hover).companyId)) {
            Row row=model.rows().get(offset+hover);
            List<String> help=new ArrayList<String>();
            for(String info:Arrays.asList(KOMECompanyDestinationSelector.name(row),
                    stats(row),KOMECompanyDestinationSelector.population(row),KOMECompanyDestinationSelector.eta(row),
                    "ETA is measured at the server preview; population is permanent faction investment.",
                    KOMECompanyDestinationSelector.state(row),KOMECompanyDestinationSelector.reason(row),
                    KOMECompanyDestinationSelector.consequence(row),KOMECompanyDestinationSelector.timing(row))) {
                if(!info.isEmpty())help.addAll(font.listFormattedStringToWidth(info,Math.min(280,panel.width)));
            }
            int maxLines=Math.max(1,(layout.viewBottom-layout.viewTop-24)/10);
            if(help.size()>maxLines) {help=new ArrayList<String>(help.subList(0,maxLines));help.set(maxLines-1,"...");}
            int longest=0;for(String lineText:help)longest=Math.max(longest,font.getStringWidth(lineText));
            int tipMouseX=Math.max(longest+12,mouseX);
            KOMEGuiTheme.drawTooltip(font,help,tipMouseX,mouseY,layout.viewRight,layout.viewBottom);
        }
    }
    static String label(Control control,KOMECompanyDestinationSelector model,long now) {
        return control==Control.ROWS_UP?"^":control==Control.ROWS_DOWN?"v":
                control==Control.FILTER_ALL?"All":control==Control.FILTER_READY?"Ready":control==Control.FILTER_ARRIVE?"Arrive Now":control==Control.RETRY?(model.canContinueBudget()?"Continue":model.phase()==KOMECompanyDestinationSelector.Phase.THROTTLED?"Retry":"Refresh"):
                control==Control.CONFIRM_JOIN?(model.joinArmed(now)?"Confirm Join Battle":"Move & Join Battle"):control==Control.CONFIRM?(model.phase()==KOMECompanyDestinationSelector.Phase.SUBMITTING?"Submitting...":
                    model.canConfirm()?"Confirm Move":"Movement unavailable"):control==Control.BACK?"Back":control==Control.CLEAR?"Clear":"Close";
    }
    static boolean filterSelected(Control control,KOMECompanyDestinationSelector model) {
        return control==Control.FILTER_ALL&&model.filter()==KOMECompanyDestinationSelector.Filter.ALL
            ||control==Control.FILTER_READY&&model.filter()==KOMECompanyDestinationSelector.Filter.READY
            ||control==Control.FILTER_ARRIVE&&model.filter()==KOMECompanyDestinationSelector.Filter.ARRIVE_NOW;
    }
    /** Display-only: thumb represents retained rows plus continuation caps, never a global percentage. */
    static final class Scrollbar {
        final Rect thumb;
        final int knownRows;
        final boolean earlier,later,pending,exactGlobalTotal=false;
        Scrollbar(KOMECompanyDestinationSelector model,Layout layout) {
            knownRows=model.rows().size();earlier=model.earlierRows();later=model.laterRows();
            pending=model.phase()==KOMECompanyDestinationSelector.Phase.LOADING||model.phase()==KOMECompanyDestinationSelector.Phase.THROTTLED;
            int extent=Math.max(layout.capacity,knownRows+(earlier?1:0)+(later?1:0));
            int height=Math.max(8,layout.scrollTrack.height*Math.min(layout.capacity,extent)/extent);
            int travel=layout.scrollTrack.height-height,position=model.rowOffset()+(earlier?1:0);
            int denominator=Math.max(1,extent-layout.capacity);
            thumb=new Rect(layout.scrollTrack.x,layout.scrollTrack.y+Math.min(travel,travel*position/denominator),layout.scrollTrack.width,height);
        }
    }
    static String stats(Row row) {
        return row.currentTile+" | "+KOMECompanyDestinationSelector.classification(row)+" | "
            +row.remainingAllowance+"/"+row.tilesPerDay+" | "+(row.distance<0?"?":row.distance)+" tiles";
    }
    private static void text(FontRenderer font,String value,int x,int y,int width,int color) {
        font.drawString(KOMEGuiTheme.trimToWidth(font,value,width),x,y,color);
    }
    private static int color(Row row) {
        String state=KOMECompanyDestinationSelector.state(row);
        return state.equals("Blocked")?KOMEGuiTheme.COLOR_BAD:state.equals("Can arrive now")?KOMEGuiTheme.COLOR_GOOD:KOMEGuiTheme.COLOR_WARN;
    }
}
