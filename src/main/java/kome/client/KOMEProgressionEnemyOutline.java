package kome.client;

import kome.common.data.KOMEVisualMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

/** Stencil-free inverted hull, inside the native model render transform.
 * No framebuffer mutation, recursive entity renderer, texture replacement or through-wall pass.
 * The normal model writes depth first; front-culling an expanded hull leaves a silhouette rim.
 */
public final class KOMEProgressionEnemyOutline {
    static final double MIN_DISTANCE=32,MAX_DISTANCE=96;
    private static boolean rendering;
    public static boolean isRendering(){return rendering;}
    static boolean relevant(String id,int dimension,double distanceSq){
        if(distanceSq<MIN_DISTANCE*MIN_DISTANCE||distanceSq>MAX_DISTANCE*MAX_DISTANCE)return false;
        for(KOMEVisualMarker m:KOMEVisualMarkerClientState.markers())if(m.role==KOMEVisualMarker.Role.ENCOUNTER_ENEMY&&m.dimension==dimension&&m.entityUuid.equals(id))return true;
        return false;
    }
    public static void render(ModelBase model,Entity entity,float limb,float amount,float age,float yaw,float pitch,float scale){
        model.render(entity,limb,amount,age,yaw,pitch,scale);
        Minecraft mc=Minecraft.getMinecraft();
        if(rendering||mc.thePlayer==null||entity.isDead||entity.isInvisible()||!(entity instanceof lotr.common.entity.npc.LOTREntityNPC)
                ||!relevant(entity.getUniqueID().toString(),entity.dimension,mc.thePlayer.getDistanceSqToEntity(entity)))return;
        int active=GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushMatrix();rendering=true;
        try{
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_FOG);GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_CULL_FACE);GL11.glCullFace(GL11.GL_FRONT);
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1F,0.05F,0.05F,0.9F);GL11.glScalef(1.07F,1.035F,1.07F);
            model.render(entity,limb,amount,age,yaw,pitch,scale);
        }finally{rendering=false;GL11.glPopMatrix();GL11.glPopAttrib();OpenGlHelper.setActiveTexture(active);}
    }
}
