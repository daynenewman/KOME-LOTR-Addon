package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import kome.common.data.KOMEVisualMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import java.nio.FloatBuffer;

/** 1.7.10 stencil silhouette: native textured geometry, a thin red rim, no replacement texture. */
public final class KOMEProgressionEnemyOutline {
    static final double MIN_DISTANCE=32,MAX_DISTANCE=96;
    private static boolean rendering;
    private static final FloatBuffer RED=(FloatBuffer)BufferUtils.createFloatBuffer(4).put(new float[]{1F,0.08F,0.08F,0.85F}).flip();
    public static boolean isRendering(){return rendering;}
    static boolean relevant(String id,int dimension,double distanceSq){
        if(distanceSq<MIN_DISTANCE*MIN_DISTANCE||distanceSq>MAX_DISTANCE*MAX_DISTANCE)return false;
        for(KOMEVisualMarker m:KOMEVisualMarkerClientState.markers())
            if(m.role==KOMEVisualMarker.Role.ENCOUNTER_ENEMY&&m.dimension==dimension&&m.entityUuid.equals(id))return true;
        return false;
    }
    @SubscribeEvent public void specials(RenderLivingEvent.Specials.Pre event){if(rendering)event.setCanceled(true);}
    @SubscribeEvent public void render(RenderWorldLastEvent event){
        Minecraft mc=Minecraft.getMinecraft();if(rendering||mc.theWorld==null||mc.thePlayer==null)return;
        int bit=MinecraftForgeClient.reserveStencilBit();if(bit<0)return;
        try{
            for(Object value:mc.theWorld.loadedEntityList)if(value instanceof lotr.common.entity.npc.LOTREntityNPC){
                Entity entity=(Entity)value;if(entity.isDead||!entity.isEntityAlive()||!relevant(entity.getUniqueID().toString(),mc.thePlayer.dimension,mc.thePlayer.getDistanceSqToEntity(entity)))continue;
                outline(entity,event.partialTicks,bit);
            }
        }finally{MinecraftForgeClient.releaseStencilBit(bit);}
    }
    private static void outline(Entity entity,float partial,int bit){
        int mask=1<<bit;GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushMatrix();rendering=true;
        try{
            GL11.glEnable(GL11.GL_STENCIL_TEST);GL11.glStencilMask(mask);GL11.glClearStencil(0);GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_FOG);
            GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,0.1F);
            GL11.glColorMask(false,false,false,false);GL11.glStencilFunc(GL11.GL_ALWAYS,mask,mask);GL11.glStencilOp(GL11.GL_KEEP,GL11.GL_KEEP,GL11.GL_REPLACE);
            draw(entity,partial,0,0,0);
            GL11.glColorMask(true,true,true,true);GL11.glStencilMask(0);GL11.glStencilFunc(GL11.GL_NOTEQUAL,mask,mask);GL11.glStencilOp(GL11.GL_KEEP,GL11.GL_KEEP,GL11.GL_KEEP);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL13.GL_COMBINE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL13.GL_COMBINE_RGB,GL11.GL_REPLACE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL13.GL_SOURCE0_RGB,GL13.GL_CONSTANT);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL13.GL_COMBINE_ALPHA,GL11.GL_REPLACE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL13.GL_SOURCE0_ALPHA,GL11.GL_TEXTURE);
            GL11.glTexEnv(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_COLOR,RED);
            double radius=Math.sqrt(Minecraft.getMinecraft().thePlayer.getDistanceSqToEntity(entity))*0.0025;
            double yaw=Math.toRadians(RenderManager.instance.playerViewY);
            for(int i=0;i<8;i++){double angle=i*Math.PI/4,side=Math.cos(angle)*radius;draw(entity,partial,Math.cos(yaw)*side,Math.sin(angle)*radius,Math.sin(yaw)*side);}
        }finally{
            rendering=false;OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);GL11.glPopMatrix();GL11.glPopAttrib();
        }
    }
    private static void draw(Entity entity,float partial,double dx,double dy,double dz){
        RenderManager.instance.getEntityRenderObject(entity).doRender(entity,
            entity.lastTickPosX+(entity.posX-entity.lastTickPosX)*partial-RenderManager.renderPosX+dx,
            entity.lastTickPosY+(entity.posY-entity.lastTickPosY)*partial-RenderManager.renderPosY+dy,
            entity.lastTickPosZ+(entity.posZ-entity.lastTickPosZ)*partial-RenderManager.renderPosZ+dz,
            entity.prevRotationYaw+(entity.rotationYaw-entity.prevRotationYaw)*partial,partial);
    }
}
