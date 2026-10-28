package kome.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEUnitMapMarker;

/** Screen-space connected components of already authorized and visible company shields. */
final class KOMECompanyShieldClusters {
    static final class Entry {
        final KOMEUnitMapMarker marker;
        final KOMEMapMarkerArt.Sprite art;
        final KOMEMapIconPresentation.Bounds bounds;
        final String faction;
        final boolean spotted;
        Entry(KOMEUnitMapMarker marker,KOMEMapMarkerArt.Sprite art,
                KOMEMapIconPresentation.Bounds bounds,String faction,boolean spotted) {
            this.marker=marker;this.art=art;this.bounds=bounds;
            this.faction=KOMEAlliance.normalizeFactionKey(faction);this.spotted=spotted;
        }
    }
    static final class Group {
        final List<Entry> members;
        final KOMEMapMarkerArt.Sprite art;
        final boolean mixedFactions;
        final KOMEMapIconPresentation.Bounds iconBounds,compositeBounds,hitBounds,badgeBounds;
        final List<KOMEMapIconPresentation.Bounds> shieldLayers;
        final int badgeX,badgeY;
        final float badgeTextX;
        final KOMEMapViewport view;
        final boolean showBadge;
        Group(List<Entry> members,KOMEMapIconPresentation presentation,KOMEMapViewport view,int textWidth) {
            this.members=Collections.unmodifiableList(new ArrayList<Entry>(members));
            this.view=view;this.showBadge=members.size()>1&&badgeVisible(presentation);
            Entry first=members.get(0);
            boolean uniform=true,mixed=false;
            for(Entry entry:members){
                if(!first.faction.equals(entry.faction))mixed=true;
                if(!first.faction.equals(entry.faction)||!first.art.texture.equals(entry.art.texture))uniform=false;
            }
            mixedFactions=mixed;
            art=members.size()==1?first.art:uniform?first.art:KOMEMapMarkerArt.NEUTRAL_SHIELD;
            double centerX=0,centerY=0;
            for(Entry entry:members){
                centerX+=entry.bounds.x+entry.bounds.width/2.0;
                centerY+=entry.bounds.y+entry.bounds.height/2.0;
            }
            iconBounds=members.size()==1?first.bounds:art.bounds(presentation,
                (int)Math.round(centerX/members.size()),(int)Math.round(centerY/members.size()));
            if(members.size()==1){
                shieldLayers=Collections.singletonList(iconBounds);
                compositeBounds=hitBounds=iconBounds;badgeBounds=null;
                badgeX=badgeY=0;badgeTextX=0;return;
            }
            int offset=Math.max(1,Math.min(2,Math.round(iconBounds.width/6.0F)));
            List<KOMEMapIconPresentation.Bounds> layers=new ArrayList<KOMEMapIconPresentation.Bounds>(3);
            layers.add(shift(iconBounds,-offset,-1));
            layers.add(shift(iconBounds,offset,-1));
            layers.add(shift(iconBounds,0,1));
            shieldLayers=Collections.unmodifiableList(layers);
            compositeBounds=union(layers);
            // Below the native label threshold the former two-layer bounds anchored a hidden badge.
            // Keep that anchor stable while the visible artwork gains its third layer.
            int badgeRight=showBadge?compositeBounds.x+compositeBounds.width:iconBounds.x+iconBounds.width;
            int badgeBottom=showBadge?compositeBounds.y+compositeBounds.height:iconBounds.y+iconBounds.height;
            badgeTextX=badgeRight-1-textWidth*KOMEConquestMapOverlay.TROOP_COUNT_TEXT_SCALE;
            badgeX=(int)Math.floor(badgeTextX-KOMEConquestMapOverlay.TROOP_COUNT_TEXT_SCALE);
            badgeY=badgeBottom-7;
            badgeBounds=showBadge?new KOMEMapIconPresentation.Bounds(badgeX,badgeY-1,
                compositeBounds.x+compositeBounds.width-badgeX,8,iconBounds.alpha):null;
            hitBounds=badgeBounds==null?compositeBounds:union(compositeBounds,badgeBounds);
        }
        boolean clustered(){return members.size()>1;}
        boolean visibleShield(){return iconBounds.visible(view);}
        boolean compositeNeedsClip(){return !compositeBounds.visible(view);}
        boolean badgeNeedsClip(){return badgeBounds!=null&&!badgeBounds.visible(view);}
        boolean contains(int x,int y){
            if(!view.contains(x,y))return false;
            for(KOMEMapIconPresentation.Bounds layer:shieldLayers)if(layer.contains(x,y))return true;
            return badgeBounds!=null&&badgeBounds.contains(x,y);
        }
        List<String> ids(){List<String> ids=new ArrayList<String>(members.size());
            for(Entry entry:members)ids.add(entry.marker.entityId);return ids;}
    }
    static boolean badgeVisible(KOMEMapIconPresentation presentation){return presentation.labels;}

    private static KOMEMapIconPresentation.Bounds shift(KOMEMapIconPresentation.Bounds b,int dx,int dy){
        return new KOMEMapIconPresentation.Bounds(b.x+dx,b.y+dy,b.width,b.height,b.alpha);
    }
    private static KOMEMapIconPresentation.Bounds union(List<KOMEMapIconPresentation.Bounds> bounds){
        int left=Integer.MAX_VALUE,top=Integer.MAX_VALUE,right=Integer.MIN_VALUE,bottom=Integer.MIN_VALUE;
        for(KOMEMapIconPresentation.Bounds b:bounds){
            left=Math.min(left,b.x);top=Math.min(top,b.y);
            right=Math.max(right,b.x+b.width);bottom=Math.max(bottom,b.y+b.height);
        }
        return new KOMEMapIconPresentation.Bounds(left,top,right-left,bottom-top,bounds.get(0).alpha);
    }
    private static KOMEMapIconPresentation.Bounds union(KOMEMapIconPresentation.Bounds a,KOMEMapIconPresentation.Bounds b){
        int left=Math.min(a.x,b.x),top=Math.min(a.y,b.y);
        int right=Math.max(a.x+a.width,b.x+b.width),bottom=Math.max(a.y+a.height,b.y+b.height);
        return new KOMEMapIconPresentation.Bounds(left,top,right-left,bottom-top,a.alpha);
    }

    static List<Group> group(List<Entry> input,KOMEMapIconPresentation presentation,KOMEMapViewport view) {
        return group(input,presentation,view,count->6*String.valueOf(count).length());
    }
    static List<Group> group(List<Entry> input,KOMEMapIconPresentation presentation,KOMEMapViewport view,
            IntUnaryOperator badgeTextWidth) {
        if(input.isEmpty())return Collections.emptyList();
        List<Entry> entries=new ArrayList<Entry>(input);
        entries.sort(Comparator.comparing(entry->entry.marker.entityId));
        // One company identity can contribute only once even if an old marker replacement duplicates it.
        Map<String,Entry> unique=new LinkedHashMap<String,Entry>();
        for(Entry entry:entries) {
            Entry previous=unique.get(entry.marker.entityId);
            if(previous==null || preferred(entry,previous))
                unique.put(entry.marker.entityId,entry);
        }
        entries=new ArrayList<Entry>(unique.values());
        int count=entries.size();int[] parent=new int[count];
        for(int i=0;i<count;i++)parent[i]=i;
        Integer[] byX=new Integer[count];for(int i=0;i<count;i++)byX[i]=i;
        final List<Entry> sorted=entries;
        Arrays.sort(byX,(a,b)->{
            int x=Integer.compare(sorted.get(a).bounds.x,sorted.get(b).bounds.x);
            return x!=0?x:sorted.get(a).marker.entityId.compareTo(sorted.get(b).marker.entityId);
        });
        for(int i=0;i<count;i++) {
            KOMEMapIconPresentation.Bounds a=entries.get(byX[i]).bounds;
            for(int j=i+1;j<count;j++) {
                KOMEMapIconPresentation.Bounds b=entries.get(byX[j]).bounds;
                if(b.x>=a.x+a.width)break;
                if(a.y<b.y+b.height&&b.y<a.y+a.height)join(parent,byX[i],byX[j]);
            }
        }
        Map<Integer,List<Entry>> components=new LinkedHashMap<Integer,List<Entry>>();
        for(int i=0;i<count;i++)components.computeIfAbsent(root(parent,i),key->new ArrayList<Entry>()).add(entries.get(i));
        List<Group> groups=new ArrayList<Group>(components.size());
        for(List<Entry> component:components.values())groups.add(new Group(component,presentation,view,
            badgeTextWidth.applyAsInt(component.size())));
        groups.sort(Comparator.comparing(group->group.members.get(0).marker.entityId));
        return groups;
    }
    private static boolean preferred(Entry candidate,Entry previous) {
        boolean loaded=candidate.marker.unitCount>0,priorLoaded=previous.marker.unitCount>0;
        if(loaded!=priorLoaded)return loaded;
        if(candidate.bounds.x!=previous.bounds.x)return candidate.bounds.x<previous.bounds.x;
        if(candidate.bounds.y!=previous.bounds.y)return candidate.bounds.y<previous.bounds.y;
        return candidate.art.texture.toString().compareTo(previous.art.texture.toString())<0;
    }
    private static int root(int[] parent,int i){while(parent[i]!=i){parent[i]=parent[parent[i]];i=parent[i];}return i;}
    private static void join(int[] parent,int a,int b){parent[root(parent,b)]=root(parent,a);}
}
