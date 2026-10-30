package kome.client;

import lotr.client.gui.LOTRGuiMap;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Native atlas artwork, with small KOME-only pictograms. Never added to LOTR's widget list. */
final class KOMEMapControlRenderer extends GuiScreen {
    static final ResourceLocation ATLAS=new ResourceLocation("lotr:map/mapScreen.png");
    static final ResourceLocation ICON_ATLAS=new ResourceLocation("kome:textures/gui/map_button_icons.png");
    static final int ICON_CELL=8, ICON_WIDTH=128, ICON_HEIGHT=32;
    private static final KOMEMapControlRenderer TOOLTIP=new KOMEMapControlRenderer();
    interface Artwork {void blit(int x,int y,int width,int height,int u,int v);}
    static void artwork(int control,int x,int y,boolean interactive,Artwork sink) {
        int v=interactive?0:10;
        // Zoom Out's top/bottom rows are the native blank frame. Reuse a blank row for its minus sign.
        sink.blit(x,y,10,4,40,v);sink.blit(x,y+4,10,2,40,v+2);sink.blit(x,y+6,10,4,40,v+6);
    }
    /** Columns are controls; row 3 is the company descriptor state; row 2 remains input-locked. */
    static int spriteRow(int control,boolean active,boolean interactive,KOMETerritoryStyle style) {
        return spriteRow(control,active,interactive,style,
            active?KOMEMapPreferences.CompanyDisplay.SHIELDS_ONLY:KOMEMapPreferences.CompanyDisplay.HIDE_COMPANIES);
    }
    static int spriteRow(int control,boolean active,boolean interactive,KOMETerritoryStyle style,
            KOMEMapPreferences.CompanyDisplay companyDisplay) {
        if(!interactive)return 2;
        if(control==7)return companyDisplay==KOMEMapPreferences.CompanyDisplay.SHIELDS_AND_DESCRIPTORS?3:
            companyDisplay==KOMEMapPreferences.CompanyDisplay.HIDE_COMPANIES?1:0;
        if(control==0)return style.palette==KOMETerritoryStyle.Palette.CLASSIC_LOTR?0:1;
        if(control==1)return style.mode.fillsTerritory()?0:1;
        return active?0:1;
    }
    static void pictogram(int control,int x,int y,boolean active,boolean interactive,KOMETerritoryStyle style,Artwork sink) {
        pictogram(control,x,y,active,interactive,style,
            active?KOMEMapPreferences.CompanyDisplay.SHIELDS_ONLY:KOMEMapPreferences.CompanyDisplay.HIDE_COMPANIES,sink);
    }
    static void pictogram(int control,int x,int y,boolean active,boolean interactive,KOMETerritoryStyle style,
            KOMEMapPreferences.CompanyDisplay companyDisplay,Artwork sink) {
        sink.blit(x+1,y+1,ICON_CELL,ICON_CELL,control*ICON_CELL,spriteRow(control,active,interactive,style,companyDisplay)*ICON_CELL);
    }
    /** Column 9 contains the four owner-painted map hover mode sprites. */
    static int tooltipSpriteRow(KOMEClientConfig.MapTooltipMode mode,boolean interactive) {
        if(!interactive)return 3;
        return mode==KOMEClientConfig.MapTooltipMode.IMMEDIATE?0:
            mode==KOMEClientConfig.MapTooltipMode.OFF?2:1;
    }
    static void tooltipPictogram(int x,int y,KOMEClientConfig.MapTooltipMode mode,boolean interactive,Artwork sink) {
        sink.blit(x+1,y+1,ICON_CELL,ICON_CELL,9*ICON_CELL,
            tooltipSpriteRow(mode,interactive)*ICON_CELL);
    }
    static void draw(int control,int x,int y,boolean active,boolean interactive,KOMETerritoryStyle style) {
        draw(control,x,y,active,interactive,style,
            active?KOMEMapPreferences.CompanyDisplay.SHIELDS_ONLY:KOMEMapPreferences.CompanyDisplay.HIDE_COMPANIES);
    }
    static void draw(int control,int x,int y,boolean active,boolean interactive,KOMETerritoryStyle style,
            KOMEMapPreferences.CompanyDisplay companyDisplay) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_TEXTURE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glColor4f(1,1,1,1);
            KOMEMinecraftClient.textureManager().bindTexture(ATLAS);
            Tessellator tess=Tessellator.instance;tess.startDrawingQuads();
            artwork(control,x,y,interactive,KOMEMapControlRenderer::blit);tess.draw();
            KOMEMinecraftClient.textureManager().bindTexture(ICON_ATLAS);tess.startDrawingQuads();
            pictogram(control,x,y,active,interactive,style,companyDisplay,(xx,yy,w,h,u,v)->blit(xx,yy,w,h,u,v,ICON_WIDTH,ICON_HEIGHT));
            tess.draw();
        } finally {GL11.glPopAttrib();}
    }
    static void drawTooltipControl(int x,int y,KOMEClientConfig.MapTooltipMode mode,boolean interactive) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_TEXTURE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glColor4f(1,1,1,1);
            KOMEMinecraftClient.textureManager().bindTexture(ATLAS);
            Tessellator tess=Tessellator.instance;tess.startDrawingQuads();
            artwork(9,x,y,interactive,KOMEMapControlRenderer::blit);tess.draw();
            KOMEMinecraftClient.textureManager().bindTexture(ICON_ATLAS);tess.startDrawingQuads();
            tooltipPictogram(x,y,mode,interactive,(xx,yy,w,h,u,v)->blit(xx,yy,w,h,u,v,ICON_WIDTH,ICON_HEIGHT));
            tess.draw();
        } finally {GL11.glPopAttrib();}
    }
    private static void blit(int x,int y,int width,int height,int u,int v) {
        blit(x,y,width,height,u,v,256,256);
    }
    private static void blit(int x,int y,int width,int height,int u,int v,int textureWidth,int textureHeight) {
        Tessellator tess=Tessellator.instance;
        tess.addVertexWithUV(x,y+height,0,u/(double)textureWidth,(v+height)/(double)textureHeight);tess.addVertexWithUV(x+width,y+height,0,(u+width)/(double)textureWidth,(v+height)/(double)textureHeight);
        tess.addVertexWithUV(x+width,y,0,(u+width)/(double)textureWidth,v/(double)textureHeight);tess.addVertexWithUV(x,y,0,u/(double)textureWidth,v/(double)textureHeight);
    }
    static void tooltip(LOTRGuiMap map,String label,int x,int y) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushMatrix();float itemZ=itemRender.zLevel;
        try {
            TOOLTIP.width=map.width;TOOLTIP.height=map.height;
            net.minecraft.client.gui.FontRenderer font=KOMEMinecraftClient.fontRenderer();
            TOOLTIP.drawHoveringText(font.listFormattedStringToWidth(label,200),x,y,font);
        } finally {itemRender.zLevel=itemZ;GL11.glPopMatrix();GL11.glPopAttrib();}
    }
}
