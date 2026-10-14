package kome.client;

import java.util.*;
import java.nio.file.*;
import java.lang.reflect.Field;
import kome.common.KOMEAccessFixture;
import kome.common.data.*;
import lotr.common.entity.npc.*;
import net.minecraft.init.Items;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class KOMEKnightPassPresentationTest {
    @Test public void provisionIconsAreBreadInBothNativeMarkerAndTrackerPaths()throws Exception {
        new KOMEAccessFixture(); {
            assertSame(Items.bread,KOMEVisualRenderBridge.icon(KOMEVisualMarker.Role.PROVISION_DUTY).getItem());
            java.lang.reflect.Method icon=KOMEProgressionTrackerOverlay.class.getDeclaredMethod("icon",String.class);icon.setAccessible(true);
            assertSame(Items.bread,((net.minecraft.item.ItemStack)icon.invoke(null,"provisioning")).getItem());
            assertSame(Items.iron_ingot,KOMEVisualRenderBridge.icon(KOMEVisualMarker.Role.PROFESSION_DUTY).getItem());
            KOMEVisualMarker profession=new KOMEVisualMarker(KOMEVisualMarker.Role.PROFESSION_DUTY,"master","Materials","",0,0,65,0).withItemIcon(new net.minecraft.item.ItemStack(Items.stick));
            assertSame(Items.stick,KOMEVisualRenderBridge.icon(profession).getItem());
            io.netty.buffer.ByteBuf buffer=io.netty.buffer.Unpooled.buffer();new kome.common.network.KOMEPacketVisualMarkers(Arrays.asList(profession)).toBytes(buffer);
            kome.common.network.KOMEPacketVisualMarkers decoded=new kome.common.network.KOMEPacketVisualMarkers();decoded.fromBytes(buffer);assertSame(Items.stick,decoded.markers.get(0).itemIcon.getItem());
        }
    }
    @Test public void objectiveHullIsPlayerSpecificBoundedAndIncludesNearTargets(){
        KOMEVisualMarkerClientState.update(Arrays.asList(new KOMEVisualMarker(KOMEVisualMarker.Role.ENCOUNTER_ENEMY,"required","","",0,0,0,0),new KOMEVisualMarker(KOMEVisualMarker.Role.ESCORT,"incidental","","",0,0,0,0)));
        try{assertTrue(KOMEProgressionEnemyOutline.relevant("required",0,9));assertTrue(KOMEProgressionEnemyOutline.relevant("required",0,96*96));assertFalse(KOMEProgressionEnemyOutline.relevant("required",0,97*97));assertFalse(KOMEProgressionEnemyOutline.relevant("required",1,9));assertFalse(KOMEProgressionEnemyOutline.relevant("incidental",0,9));assertFalse(KOMEProgressionEnemyOutline.relevant("unrelated",0,9));}
        finally{KOMEVisualMarkerClientState.clear();}assertFalse(KOMEProgressionEnemyOutline.relevant("required",0,9));
    }
    @Test public void hullUsesOccludedAndVisibleDepthFunctionsAndRestoresState()throws Exception {
        ClassNode node=new ClassNode();new ClassReader("kome/client/KOMEProgressionEnemyOutline").accept(node,0);
        boolean greater=false,lequal=false,push=false,pop=false,depthMask=false,framebuffer=false,aliveCheck=false;
        for(MethodNode m:node.methods)for(AbstractInsnNode i:m.instructions.toArray()){
            if(i instanceof IntInsnNode){greater|=((IntInsnNode)i).operand==516;lequal|=((IntInsnNode)i).operand==515;}
            if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;push|=call.name.equals("glPushAttrib");pop|=call.name.equals("glPopAttrib");depthMask|=call.name.equals("glDepthMask");framebuffer|=call.name.toLowerCase(Locale.ROOT).contains("framebuffer");aliveCheck|=call.name.equals("isEntityAlive");}
        }
        assertTrue(greater&&lequal&&push&&pop&&depthMask&&aliveCheck);assertFalse(framebuffer);
    }
    @Test public void elfEligibilityUsesNativeTypesAndExcludesTormentedElves()throws Exception {
        assertTrue(KOMEElvenFootGlow.nativeElf(KOMEAccessFixture.allocate(LOTREntityGaladhrimElf.class)));
        assertTrue(KOMEElvenFootGlow.nativeElf(KOMEAccessFixture.allocate(LOTREntityDorwinionElf.class)));
        assertFalse(KOMEElvenFootGlow.nativeElf(KOMEAccessFixture.allocate(LOTREntityTormentedElf.class)));
        assertFalse(KOMEElvenFootGlow.nativeElf(KOMEAccessFixture.allocate(LOTREntityRohanMan.class)));
    }
    @Test public void nightUsesCelestialTimeRatherThanIndoorDarkness(){
        assertFalse(KOMEElvenFootGlow.night(false,0F));assertFalse(KOMEElvenFootGlow.night(false,.25F));assertTrue(KOMEElvenFootGlow.night(false,.5F));assertFalse(KOMEElvenFootGlow.night(false,.75F));assertFalse(KOMEElvenFootGlow.night(true,.5F));
    }
    @Test public void elvenClientOptionsClampUnsafeIntensityAndDensity()throws Exception {
        Field instance=KOMEClientConfig.class.getDeclaredField("instance");instance.setAccessible(true);Object previous=instance.get(null);
        Field injection=cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");injection.setAccessible(true);Object previousHome=injection.get(null);injection.set(null,new java.io.File(".").getAbsoluteFile());
        Path folder=Files.createTempDirectory("kome-elven-options");Path file=folder.resolve("client.cfg");
        try{new KOMEClientConfig(file.toFile());assertTrue(KOMEClientConfig.elvenAuraGlobal());assertTrue(KOMEClientConfig.elvenDynamicGlobal());assertEquals(.28F,KOMEClientConfig.elvenIntensityGlobal(),0);assertEquals(8,KOMEClientConfig.elvenIntervalGlobal());
            assertEquals(.4F,KOMEClientConfig.clampElvenIntensity(200F),0);assertEquals(.28F,KOMEClientConfig.clampElvenIntensity(Float.NaN),0);assertEquals(8,KOMEClientConfig.clampElvenInterval(-1));assertEquals(40,KOMEClientConfig.clampElvenInterval(200));
            String settings=new String(Files.readAllBytes(file),java.nio.charset.StandardCharsets.UTF_8).replace("B:visualAura=true","B:visualAura=false").replace("B:dynamicIllumination=true","B:dynamicIllumination=false");Files.write(file,settings.getBytes(java.nio.charset.StandardCharsets.UTF_8));new KOMEClientConfig(file.toFile());assertFalse(KOMEClientConfig.elvenAuraGlobal());assertFalse(KOMEClientConfig.elvenDynamicGlobal());
        }finally{instance.set(null,previous);injection.set(null,previousHome);Files.deleteIfExists(file);Files.deleteIfExists(folder);}
    }
}
