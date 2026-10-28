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
        final double layoutX;
        Entry(KOMEUnitMapMarker marker,KOMEMapMarkerArt.Sprite art,
                KOMEMapIconPresentation.Bounds bounds,String faction,boolean spotted) {
            this(marker,art,bounds,faction,spotted,bounds.x+bounds.width/2.0);
        }
        Entry(KOMEUnitMapMarker marker,KOMEMapMarkerArt.Sprite art,
                KOMEMapIconPresentation.Bounds bounds,String faction,boolean spotted,double layoutX) {
            this.marker=marker;this.art=art;this.bounds=bounds;
            this.faction=KOMEAlliance.normalizeFactionKey(faction);this.spotted=spotted;
            this.layoutX=layoutX;
        }
    }
    static final class PaintedBounds {
        final double left,top,right,bottom;
        PaintedBounds(double left,double top,double right,double bottom){
            this.left=left;this.top=top;this.right=right;this.bottom=bottom;
        }
        boolean touches(PaintedBounds other){
            return left<=other.right&&other.left<=right&&top<=other.bottom&&other.top<=bottom;
        }
    }
    static final class Group {
        final List<Entry> members;
        final KOMEMapMarkerArt.Sprite art;
        final boolean mixedFactions;
        final KOMEMapIconPresentation.Bounds iconBounds,compositeBounds,hitBounds,badgeBounds;
        final List<KOMEMapIconPresentation.Bounds> shieldLayers;
        final PaintedBounds paintedBounds;
        final double layoutX;
        final int badgeX,badgeY;
        final float badgeTextX;
        final KOMEMapViewport view;
        final boolean showBadge;
        final boolean paintedHitOnly;
        Group(List<Entry> members,KOMEMapIconPresentation presentation,KOMEMapViewport view,int textWidth) {
            this(members,presentation,view,textWidth,0,0,false);
        }
        Group(List<Entry> members,KOMEMapIconPresentation presentation,KOMEMapViewport view,int textWidth,int dx,int dy) {
            this(members,presentation,view,textWidth,dx,dy,false);
        }
        Group(List<Entry> members,KOMEMapIconPresentation presentation,KOMEMapViewport view,
                int textWidth,int dx,int dy,boolean paintedHitOnly) {
            this.members=Collections.unmodifiableList(new ArrayList<Entry>(members));
            this.view=view;this.showBadge=members.size()>1&&badgeVisible(presentation);
            this.paintedHitOnly=paintedHitOnly;
            Entry first=members.get(0);
            boolean uniform=true,mixed=false;
            for(Entry entry:members){
                if(!first.faction.equals(entry.faction))mixed=true;
                if(!first.faction.equals(entry.faction)||!first.art.texture.equals(entry.art.texture))uniform=false;
            }
            mixedFactions=mixed;
            art=members.size()==1?first.art:uniform?first.art:KOMEMapMarkerArt.NEUTRAL_SHIELD;
            double centerX=0,centerY=0,sourceX=0;
            for(Entry entry:members){
                centerX+=entry.bounds.x+entry.bounds.width/2.0;
                centerY+=entry.bounds.y+entry.bounds.height/2.0;
                sourceX+=entry.layoutX;
            }
            layoutX=sourceX/members.size();
            iconBounds=members.size()==1?(dx==0&&dy==0?first.bounds:shift(first.bounds,dx,dy)):art.bounds(presentation,
                (int)Math.round(centerX/members.size())+dx,(int)Math.round(centerY/members.size())+dy);
            if(members.size()==1){
                shieldLayers=Collections.singletonList(iconBounds);
                compositeBounds=iconBounds;badgeBounds=null;
                paintedBounds=painted(shieldLayers,art.visible);
                hitBounds=paintedHitOnly?paintedHitBounds(paintedBounds,iconBounds.alpha):iconBounds;
                badgeX=badgeY=0;badgeTextX=0;return;
            }
            int offset=Math.max(1,Math.min(2,Math.round(iconBounds.width/6.0F)));
            List<KOMEMapIconPresentation.Bounds> layers=new ArrayList<KOMEMapIconPresentation.Bounds>(3);
            layers.add(shift(iconBounds,-offset,-1));
            layers.add(shift(iconBounds,offset,-1));
            layers.add(shift(iconBounds,0,1));
            shieldLayers=Collections.unmodifiableList(layers);
            compositeBounds=union(layers);
            paintedBounds=painted(shieldLayers,art.visible);
            // Below the native label threshold the former two-layer bounds anchored a hidden badge.
            // Keep that anchor stable while the visible artwork gains its third layer.
            int badgeRight=showBadge?compositeBounds.x+compositeBounds.width:iconBounds.x+iconBounds.width;
            int badgeBottom=showBadge?compositeBounds.y+compositeBounds.height:iconBounds.y+iconBounds.height;
            badgeTextX=badgeRight-1-textWidth*KOMEConquestMapOverlay.TROOP_COUNT_TEXT_SCALE;
            badgeX=(int)Math.floor(badgeTextX-KOMEConquestMapOverlay.TROOP_COUNT_TEXT_SCALE);
            badgeY=badgeBottom-7;
            badgeBounds=showBadge?new KOMEMapIconPresentation.Bounds(badgeX,badgeY-1,
                compositeBounds.x+compositeBounds.width-badgeX,8,iconBounds.alpha):null;
            KOMEMapIconPresentation.Bounds shieldHit=paintedHitOnly
                ?paintedHitBounds(paintedBounds,iconBounds.alpha):compositeBounds;
            hitBounds=badgeBounds==null?shieldHit:union(shieldHit,badgeBounds);
        }
        boolean clustered(){return members.size()>1;}
        boolean visibleShield(){return iconBounds.visible(view);}
        boolean compositeNeedsClip(){return !compositeBounds.visible(view);}
        boolean badgeNeedsClip(){return badgeBounds!=null&&!badgeBounds.visible(view);}
        boolean contains(int x,int y){
            if(!view.contains(x,y))return false;
            if(paintedHitOnly)return x>=paintedBounds.left&&x<paintedBounds.right
                &&y>=paintedBounds.top&&y<paintedBounds.bottom
                ||badgeBounds!=null&&badgeBounds.contains(x,y);
            for(KOMEMapIconPresentation.Bounds layer:shieldLayers)if(layer.contains(x,y))return true;
            return badgeBounds!=null&&badgeBounds.contains(x,y);
        }
        List<String> ids(){List<String> ids=new ArrayList<String>(members.size());
            for(Entry entry:members)ids.add(entry.marker.entityId);return ids;}
    }
    static boolean badgeVisible(KOMEMapIconPresentation presentation){return presentation.labels;}

    private static PaintedBounds painted(List<KOMEMapIconPresentation.Bounds> layers,KOMEMapMarkerArt.VisibleRect face){
        double left=Double.POSITIVE_INFINITY,top=Double.POSITIVE_INFINITY;
        double right=Double.NEGATIVE_INFINITY,bottom=Double.NEGATIVE_INFINITY;
        for(KOMEMapIconPresentation.Bounds layer:layers){
            left=Math.min(left,layer.x+layer.width*face.left);
            top=Math.min(top,layer.y+layer.height*face.top);
            right=Math.max(right,layer.x+layer.width*face.right);
            bottom=Math.max(bottom,layer.y+layer.height*face.bottom);
        }
        return new PaintedBounds(left,top,right,bottom);
    }
    private static KOMEMapIconPresentation.Bounds paintedHitBounds(PaintedBounds painted,int alpha){
        int x=(int)Math.floor(painted.left),y=(int)Math.floor(painted.top);
        return new KOMEMapIconPresentation.Bounds(x,y,(int)Math.ceil(painted.right)-x,
            (int)Math.ceil(painted.bottom)-y,alpha);
    }

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
                if(entries.get(byX[i]).faction.equals(entries.get(byX[j]).faction)
                    &&entries.get(byX[i]).art.texture.equals(entries.get(byX[j]).art.texture)
                    &&a.y<b.y+b.height&&b.y<a.y+a.height)join(parent,byX[i],byX[j]);
            }
        }
        Map<Integer,List<Entry>> components=new LinkedHashMap<Integer,List<Entry>>();
        for(int i=0;i<count;i++)components.computeIfAbsent(root(parent,i),key->new ArrayList<Entry>()).add(entries.get(i));
        List<Group> groups=new ArrayList<Group>(components.size());
        for(List<Entry> component:components.values())groups.add(new Group(component,presentation,view,
            badgeTextWidth.applyAsInt(component.size())));
        groups.sort(Comparator.comparing(group->group.members.get(0).marker.entityId));
        return separateTouchingGroups(groups,presentation,view,badgeTextWidth);
    }
    /** Only painted shield contact can move a group; labels and descriptors never trigger layout. */
    private static List<Group> separateTouchingGroups(List<Group> groups,
            KOMEMapIconPresentation presentation,KOMEMapViewport view,IntUnaryOperator badgeTextWidth) {
        int count=groups.size();
        if(count<2)return groups;
        int[] parent=new int[count];for(int i=0;i<count;i++)parent[i]=i;
        for(int i=0;i<count;i++)for(int j=i+1;j<count;j++){
            Group a=groups.get(i),b=groups.get(j);
            if((!a.members.get(0).faction.equals(b.members.get(0).faction)
                    ||!a.art.texture.equals(b.art.texture))
                    &&a.paintedBounds.touches(b.paintedBounds))join(parent,i,j);
        }
        Map<Integer,List<Integer>> components=new LinkedHashMap<Integer,List<Integer>>();
        for(int i=0;i<count;i++)components.computeIfAbsent(root(parent,i),key->new ArrayList<Integer>()).add(i);
        List<Group> result=new ArrayList<Group>(groups);
        for(List<Integer> component:components.values())if(component.size()>1)
            separateComponent(groups,result,component,presentation,view,badgeTextWidth);
        return result;
    }
    private static void separateComponent(List<Group> groups,List<Group> result,List<Integer> component,
            KOMEMapIconPresentation presentation,KOMEMapViewport view,IntUnaryOperator badgeTextWidth){
        component.sort((i,j)->{
            Group a=groups.get(i),b=groups.get(j);
            int x=Double.compare(a.layoutX,b.layoutX);
            if(x!=0)return x;
            int faction=a.members.get(0).faction.compareTo(b.members.get(0).faction);
            if(faction!=0)return faction;
            int art=a.art.texture.toString().compareTo(b.art.texture.toString());
            return art!=0?art:a.members.get(0).marker.entityId.compareTo(b.members.get(0).marker.entityId);
        });
        int n=component.size();double[] required=new double[n];
        final double gap=1.5;
        for(int i=1;i<n;i++){
            Group previous=groups.get(component.get(i-1)),current=groups.get(component.get(i));
            required[i]=required[i-1]+Math.max(0,previous.paintedBounds.right+gap-current.paintedBounds.left);
        }
        // Pool-adjacent-violators gives the least squared horizontal displacement
        // satisfying every adjacent painted-face gap, with no iterative layout search.
        int[] starts=new int[n],ends=new int[n],sizes=new int[n];double[] sums=new double[n];int blocks=0;
        for(int i=0;i<n;i++){
            starts[blocks]=ends[blocks]=i;sizes[blocks]=1;sums[blocks]=-required[i];blocks++;
            while(blocks>1&&sums[blocks-2]/sizes[blocks-2]>sums[blocks-1]/sizes[blocks-1]){
                ends[blocks-2]=ends[blocks-1];sizes[blocks-2]+=sizes[blocks-1];
                sums[blocks-2]+=sums[blocks-1];blocks--;
            }
        }
        int[] shift=new int[n];
        for(int block=0;block<blocks;block++)for(int i=starts[block];i<=ends[block];i++)
            shift[i]=(int)Math.round(sums[block]/sizes[block]+required[i]);
        for(int i=1;i<n;i++){
            Group previous=groups.get(component.get(i-1)),current=groups.get(component.get(i));
            double remaining=1.0+previous.paintedBounds.right+shift[i-1]
                -current.paintedBounds.left-shift[i];
            if(remaining>0)shift[i]+=(int)Math.ceil(remaining);
        }
        int minTranslation=Integer.MIN_VALUE,maxTranslation=Integer.MAX_VALUE;
        for(int i=0;i<n;i++){
            Group group=groups.get(component.get(i));
            minTranslation=Math.max(minTranslation,(int)Math.ceil(view.left-group.iconBounds.x-shift[i]));
            maxTranslation=Math.min(maxTranslation,(int)Math.floor(view.right
                -group.iconBounds.x-group.iconBounds.width-shift[i]));
        }
        if(minTranslation>maxTranslation)return; // No honest in-viewport separation exists.
        int translation=Math.max(minTranslation,Math.min(0,maxTranslation));
        for(int i=0;i<n;i++){
            int index=component.get(i),dx=shift[i]+translation;
            Group original=groups.get(index);
            result.set(index,new Group(original.members,presentation,view,
                badgeTextWidth.applyAsInt(original.members.size()),dx,0,true));
        }
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
