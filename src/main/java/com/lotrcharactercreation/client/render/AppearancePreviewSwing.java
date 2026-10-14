package com.lotrcharactercreation.client.render;

/** Vanilla's six-tick swing clock, scoped to the preview model rather than the live player. */
public final class AppearancePreviewSwing {
    private int tick=-1;
    public void click(){if(tick<0||tick>=3)tick=0;}
    public void update(){if(tick>=0&&++tick>=6)tick=-1;}
    public float progress(float partialTicks){return tick<0?0F:Math.min(0.999F,(tick+partialTicks)/6F);}
    public static boolean hitsAvatar(int x,int y,int centerX,int centerY){
        return x>=centerX-35&&x<=centerX+35&&y>=centerY-49&&y<=centerY+42;
    }
}
