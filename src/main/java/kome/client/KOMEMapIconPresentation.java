package kome.client;

/** Client-only GUI-pixel footprint/fade. Never changes a projected marker anchor or marker data. */
final class KOMEMapIconPresentation {
    final double scale;
    final int alpha;
    final boolean labels;
    final double enlargement;
    final double spriteScale;
    final double spriteEnlargement;
    final double shieldBump;
    private KOMEMapIconPresentation(double scale,int alpha,boolean labels,double enlargement,
            double spriteScale,double spriteEnlargement,double shieldBump) {
        this.scale=scale;this.alpha=alpha;this.labels=labels;this.enlargement=enlargement;
        this.spriteScale=spriteScale;this.spriteEnlargement=spriteEnlargement;this.shieldBump=shieldBump;
    }
    static KOMEMapIconPresentation at(double zoom) {
        if(!Double.isFinite(zoom)||zoom<=0)return new KOMEMapIconPresentation(.65,0,false,0,.65,0,0);
        double nativePower=Math.log(zoom)/Math.log(2);
        double detail=Math.max(0,Math.min(1,(nativePower+3)/3));
        double sizeZoom=sizingZoom(zoom);
        double sizeDetail=sizeZoom==zoom?detail:Math.max(0,Math.min(1,(Math.log(sizeZoom)/Math.log(2)+3)/3));
        double t=Math.max(0,Math.min(1,(sizeZoom-.5)*2));
        // Native LOTR stops span powers -3..4 (scale .125..16). Sprite size must
        // progress across all eight stops; scale 1 is only the fifth, not the maximum.
        double spriteZoom=Math.max(.125,Math.min(1,(nativePower+4)/8));
        double spriteSizeZoom=sizingZoom(spriteZoom);
        double spriteDetail=Math.max(0,Math.min(1,(Math.log(spriteSizeZoom)/Math.log(2)+3)/3));
        double spriteT=Math.max(0,Math.min(1,(spriteSizeZoom-.5)*2));
        // A zero-slope, shield-only bump between native scales 1 and 4 raises
        // the rounded footprint by one GUI pixel at scale 2.
        double shieldBump=0;
        if(spriteZoom>.5&&spriteZoom<.75) {
            double sine=Math.sin(Math.PI*(spriteZoom-.5)/.25);
            shieldBump=.75*sine*sine;
        }
        return new KOMEMapIconPresentation(.65+.35*sizeDetail,(int)Math.round(64+191*detail),zoom>=1,t*t*(3-2*t),
            .65+.35*spriteDetail,spriteT*spriteT*(3-2*spriteT),shieldBump);
    }
    /** Local size-only delay: identity with matching slopes at both accepted neighboring zooms.
     * Its derivative is >= 1 - pi/4 > 0, so continuous inward zoom can never shrink icons.
     * Opacity, labels, geography dimensions and map anchors continue using actual zoom. */
    private static double sizingZoom(double zoom) {
        if(zoom<=.375||zoom>=.675)return zoom;
        double sine=Math.sin(Math.PI*(zoom-.375)/.3);
        return zoom-.075*sine*sine;
    }
    int color(int argb){return ((argb>>>24)*alpha/255)<<24 | (argb&0xFFFFFF);}
    static MapBounds geography(double zoom,int anchorX,int anchorY,int width,int height) {
        KOMEMapIconPresentation policy=at(zoom);
        return new MapBounds(anchorX,anchorY,width,height,policy.alpha==0?0:zoom,policy.alpha);
    }
    /** Fractional GUI bounds preserve map-relative sizes below one pixel, without a minimum. */
    static final class MapBounds {
        final double x,y,width,height;
        final int alpha;
        MapBounds(int anchorX,int anchorY,int width,int height,double zoom,int alpha) {
            this.alpha=alpha;
            x=anchorX-(width/2)*zoom;y=anchorY-(height/2)*zoom;
            this.width=width*zoom;this.height=height*zoom;
        }
        boolean visible(KOMEMapViewport view){return alpha>0&&width>0&&height>0&&x>=view.left&&y>=view.top&&x+width<=view.right&&y+height<=view.bottom;}
        boolean contains(double px,double py){return alpha>0&&width>0&&height>0&&px>=x&&py>=y&&px<x+width&&py<y+height;}
    }
    interface RectSink {void rect(int x,int y,int right,int bottom,int argb);}
    /** Disjoint frame/diamond rings keep the requested fade from accumulating in nested fills. */
    void frame(Bounds b,int inset,int argb,RectSink sink) {
        int x=b.x+inset,y=b.y+inset,r=b.x+b.width-inset,d=b.y+b.height-inset,c=color(argb);
        sink.rect(x,y,r,y+1,c);sink.rect(x,d-1,r,d,c);
        if(d-y>2){sink.rect(x,y+1,x+1,d-1,c);sink.rect(r-1,y+1,r,d-1,c);}
    }
    void diamondRing(int x,int y,int outer,int inner,int argb,RectSink sink) {
        int c=color(argb);
        for(int dy=-outer;dy<=outer;dy++) {
            int half=outer-Math.abs(dy),cut=inner-Math.abs(dy);
            if(cut<0)sink.rect(x-half,y+dy,x+half+1,y+dy+1,c);
            else if(half>cut){sink.rect(x-half,y+dy,x-cut,y+dy+1,c);sink.rect(x+cut+1,y+dy,x+half+1,y+dy+1,c);}
        }
    }
    Bounds bounds(int anchorX,int anchorY,int baseWidth,int baseHeight) {
        int w=Math.max(1,(int)Math.round(baseWidth*scale)),h=Math.max(1,(int)Math.round(baseHeight*scale));
        return new Bounds(anchorX-w/2,anchorY-h/2,w,h,alpha);
    }
    Bounds diamond(int anchorX,int anchorY,int baseRadius) {
        int radius=Math.max(1,(int)Math.round(baseRadius*scale));
        return new Bounds(anchorX-radius,anchorY-radius,radius*2+1,radius*2+1,alpha);
    }
    static final class Bounds {
        final int x,y,width,height,alpha;
        Bounds(int x,int y,int width,int height,int alpha){this.x=x;this.y=y;this.width=width;this.height=height;this.alpha=alpha;}
        boolean visible(KOMEMapViewport view){return alpha>0&&x>=view.left&&y>=view.top&&x+width<=view.right&&y+height<=view.bottom;}
        boolean contains(int px,int py){return alpha>0&&px>=x&&py>=y&&px<x+width&&py<y+height;}
        boolean containsDiamond(int px,int py){return contains(px,py)&&Math.abs(px-(x+width/2))+Math.abs(py-(y+height/2))<=width/2;}
    }
}
