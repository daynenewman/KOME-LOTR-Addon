package kome.client;

import java.util.*;
import kome.common.KOMEAccessFixture;
import net.minecraft.entity.EntityLivingBase;
import atomicstryker.dynamiclights.client.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Adapter lifecycle only. This mock API does not prove real light propagation or FPS. */
public class KOMEElvenDynamicLightsTest {
    private static void place(EntityLivingBase entity,double x)throws Exception {
        java.lang.reflect.Field bounds=net.minecraft.entity.Entity.class.getDeclaredField("boundingBox");bounds.setAccessible(true);
        bounds.set(entity,net.minecraft.util.AxisAlignedBB.getBoundingBox(x-.3,65,x-.3,x+.3,66.8,x+.3));entity.setPosition(x,65,x);
    }
    @Test public void movingSourceRegistersOnceAndRemovalClearsTheAttachment()throws Exception {
        DynamicLights.reset();KOMEAccessFixture f=new KOMEAccessFixture();KOMEElvenDynamicLights adapter=new KOMEElvenDynamicLights();
        place(f.player,4);
        try{
            assertTrue(adapter.available());adapter.update(f.world,Arrays.<EntityLivingBase>asList(f.player));assertEquals(1,DynamicLights.added);
            IDynamicLightSource source=DynamicLights.sources.get(0);assertEquals(3,source.getLightLevel());assertEquals(f.player.boundingBox.minY+.05,source.getAttachmentEntity().posY,0);
            f.player.setPosition(8,65,8);adapter.update(f.world,Arrays.<EntityLivingBase>asList(f.player));assertEquals(1,DynamicLights.added);assertEquals(8,source.getAttachmentEntity().posX,0);
            adapter.update(f.world,Collections.<EntityLivingBase>emptyList());assertEquals(1,DynamicLights.removed);assertTrue(DynamicLights.sources.isEmpty());assertTrue(source.getAttachmentEntity().isDead);
        }finally{adapter.clear();DynamicLights.reset();}
    }
    @Test public void twoSourcesAndWorldDisconnectHaveIndependentCleanup()throws Exception {
        DynamicLights.reset();KOMEAccessFixture first=new KOMEAccessFixture(),second=new KOMEAccessFixture();second.player.worldObj=first.world;
        place(first.player,0);place(second.player,1);KOMEElvenDynamicLights adapter=new KOMEElvenDynamicLights();
        try{
            adapter.update(first.world,Arrays.<EntityLivingBase>asList(first.player,second.player));assertEquals(2,DynamicLights.sources.size());
            List<IDynamicLightSource> attachments=new ArrayList<IDynamicLightSource>(DynamicLights.sources);
            adapter.update(null,Collections.<EntityLivingBase>emptyList());assertEquals(2,DynamicLights.removed);assertTrue(DynamicLights.sources.isEmpty());for(IDynamicLightSource source:attachments)assertTrue(source.getAttachmentEntity().isDead);
        }finally{adapter.clear();DynamicLights.reset();}
    }
}
