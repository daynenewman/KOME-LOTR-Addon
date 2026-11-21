package kome.client;

import kome.common.data.KOMEUnitMapMarker;
import net.minecraft.init.Items;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.lwjgl.opengl.GL11;
import static org.junit.Assert.*;

public class KOMECompanyDescriptorRendererTest {
    @Test public void onlyAuthorizedSingletonDescriptorModeDraws() {
        KOMEUnitMapMarker marker=new KOMEUnitMapMarker();
        marker.meleeItemId="minecraft:iron_sword";
        assertFalse(KOMECompanyDescriptorRenderer.enabled(KOMEMapPreferences.CompanyDisplay.HIDE_COMPANIES,false,marker));
        assertFalse(KOMECompanyDescriptorRenderer.enabled(KOMEMapPreferences.CompanyDisplay.SHIELDS_ONLY,false,marker));
        assertFalse(KOMECompanyDescriptorRenderer.enabled(KOMEMapPreferences.CompanyDisplay.SHIELDS_AND_DESCRIPTORS,true,marker));
        assertTrue(KOMECompanyDescriptorRenderer.enabled(KOMEMapPreferences.CompanyDisplay.SHIELDS_AND_DESCRIPTORS,false,marker));
    }

    @Test public void weaponAndMovementQuadsFollowPaintedShieldAtEveryNativeStop() {
        KOMEMapMarkerArt.Sprite art=KOMEMapMarkerArt.shield("gondor");
        KOMEMapMarkerArt.VisibleRect old=art.visible;
        art.visible=new KOMEMapMarkerArt.VisibleRect(7F/32F,25F/32F,31F/32F);
        try {
            int[] weapons={3,4,4,4,5,6,8,8};int[] movement={4,4,5,5,7,8,9,10};int stop=0;
            for(double zoom:new double[]{.125,.25,.5,1,2,4,8,16}) {
                KOMEMapIconPresentation.Bounds shield=art.bounds(KOMEMapIconPresentation.at(zoom),100,100);
                int weapon=KOMECompanyDescriptorRenderer.weaponSize(shield,art);
                int boot=KOMECompanyDescriptorRenderer.movementSize(shield);
                assertEquals(weapons[stop],weapon);assertEquals(movement[stop++],boot);
                float visibleWidth=shield.width*(art.visible.right-art.visible.left);
                assertTrue(weapon>=visibleWidth*.7F&&weapon<=visibleWidth+1F);
                int x=KOMECompanyDescriptorRenderer.meleeX(shield,art,weapon);
                int rangedX=KOMECompanyDescriptorRenderer.rangedX(shield,art,weapon);
                float paintedCenter=shield.x+(art.visible.left+art.visible.right)*shield.width/2F;
                assertEquals("Melee centers over the painted face",paintedCenter,x+weapon/2F,.51F);
                assertEquals("Ranged centers over the same painted face",paintedCenter,rangedX+weapon/2F,.51F);
                assertEquals("Mixed item quads overlap almost completely",x,rangedX);
                int weaponY=KOMECompanyDescriptorRenderer.weaponY(shield,art,weapon);
                float paintedMiddle=shield.y+(art.visible.top+art.visible.bottom)*shield.height/2F;
                assertEquals(paintedMiddle,weaponY+weapon/2F,.51F);
                int mixedMeleeX=x+KOMECompanyDescriptorRenderer.mixedMeleeOffset(weapon);
                assertTrue("Mixed sprites retain at least 80% horizontal overlap",
                    weapon-Math.abs(mixedMeleeX-rangedX)>=weapon*.8F);
                assertTrue("Both mixed icons remain near the shield center",
                    Math.abs(mixedMeleeX+weapon/2F-paintedCenter)<=1.5F);
                int movementX=KOMECompanyDescriptorRenderer.movementX(shield,art,boot);
                assertEquals(paintedCenter,movementX+boot/2F,.51F);
                int movementY=KOMECompanyDescriptorRenderer.movementY(shield,art);
                assertEquals(shield.y+art.visible.bottom*shield.height-1F,movementY,.51F);
                assertTrue(movementY<=shield.y+shield.height);
            }
        } finally {art.visible=old;}
    }

    @Test public void creditAndEquipmentPlansNeverInventUnknownIcons() {
        KOMEUnitMapMarker marker=new KOMEUnitMapMarker();
        assertFalse(KOMECompanyDescriptorRenderer.hasMovementIcon(marker));
        marker.movementEntitlement=KOMEUnitMapMarker.ENTITLEMENT_MOUNTED;
        assertFalse(KOMECompanyDescriptorRenderer.hasMovementIcon(marker));
        marker.movementCredit=KOMEUnitMapMarker.CREDIT_AVAILABLE;
        assertTrue(KOMECompanyDescriptorRenderer.hasMovementIcon(marker));
        assertEquals(1F,KOMECompanyDescriptorRenderer.movementBrightness(marker),0);
        marker.movementCredit=KOMEUnitMapMarker.CREDIT_EXHAUSTED;
        assertEquals(.25F,KOMECompanyDescriptorRenderer.movementBrightness(marker),0);
        KOMEMapIconPresentation.Bounds shield=new KOMEMapIconPresentation.Bounds(0,0,9,9,128);
        assertEquals(128F/255F,KOMECompanyDescriptorRenderer.movementAlpha(marker,shield),.001F);
        assertEquals(96F/255F*.25F,KOMECompanyDescriptorRenderer.itemColorChannel(0x6090C0,16,.25F),.001F);
        assertEquals(144F/255F*.25F,KOMECompanyDescriptorRenderer.itemColorChannel(0x6090C0,8,.25F),.001F);
        assertEquals(192F/255F*.25F,KOMECompanyDescriptorRenderer.itemColorChannel(0x6090C0,0,.25F),.001F);
        assertEquals(96F/255F,KOMECompanyDescriptorRenderer.itemColorChannel(0x6090C0,16,1F),.001F);
        marker.rangedItemId="minecraft:bow";
        assertTrue(KOMECompanyDescriptorRenderer.hasWeapon(marker.rangedItemId));
        assertFalse(KOMECompanyDescriptorRenderer.hasWeapon("bad/path"));
    }

    @Test public void asymmetricFactionFrontFaceCentersBothWeaponsOverThePaintedShield() {
        KOMEMapMarkerArt.Sprite art=KOMEMapMarkerArt.shield("rohan");
        KOMEMapMarkerArt.VisibleRect old=art.visible;
        art.visible=new KOMEMapMarkerArt.VisibleRect(5F/32F,4F/32F,27F/32F,27F/32F);
        try {
            for(double zoom:new double[]{.125,.25,.5,1,2,4,8,16}) {
                KOMEMapIconPresentation.Bounds shield=art.bounds(KOMEMapIconPresentation.at(zoom),100,100);
                int size=KOMECompanyDescriptorRenderer.weaponSize(shield,art);
                int ranged=KOMECompanyDescriptorRenderer.rangedX(shield,art,size);
                int melee=KOMECompanyDescriptorRenderer.meleeX(shield,art,size);
                float center=shield.x+shield.width*(art.visible.left+art.visible.right)/2F;
                assertEquals(center,ranged+size/2F,.51F);
                assertEquals(center,melee+size/2F,.51F);
                float middle=shield.y+shield.height*(art.visible.top+art.visible.bottom)/2F;
                assertEquals(middle,KOMECompanyDescriptorRenderer.weaponY(shield,art,size)+size/2F,.51F);
            }
        } finally {art.visible=old;}
    }

    @Test public void allEightZoomStopsKeepTheirExistingAlphaForAvailableAndExhaustedCredit() {
        KOMEUnitMapMarker marker=new KOMEUnitMapMarker();
        marker.movementEntitlement=KOMEUnitMapMarker.ENTITLEMENT_FOOT_OR_MIXED;
        int[] expected={64,128,191,255,255,255,255,255};int i=0;
        for(double zoom:new double[]{.125,.25,.5,1,2,4,8,16}){
            KOMEMapIconPresentation.Bounds shield=KOMEMapMarkerArt.NEUTRAL_SHIELD.bounds(KOMEMapIconPresentation.at(zoom),100,100);
            assertEquals(expected[i++],shield.alpha);
            marker.movementCredit=KOMEUnitMapMarker.CREDIT_AVAILABLE;
            assertTrue(KOMECompanyDescriptorRenderer.hasMovementIcon(marker));
            assertEquals(shield.alpha/255F,KOMECompanyDescriptorRenderer.movementAlpha(marker,shield),.001F);
            assertEquals(1F,KOMECompanyDescriptorRenderer.movementBrightness(marker),0);
            marker.movementCredit=KOMEUnitMapMarker.CREDIT_EXHAUSTED;
            assertEquals(shield.alpha/255F,KOMECompanyDescriptorRenderer.movementAlpha(marker,shield),.001F);
            assertEquals(.25F,KOMECompanyDescriptorRenderer.movementBrightness(marker),0);
        }
        marker.movementCredit=KOMEUnitMapMarker.CREDIT_UNKNOWN;
        assertFalse(KOMECompanyDescriptorRenderer.hasMovementIcon(marker));
    }

    @Test public void savedMovementEquipmentWinsAndMissingExamplesUseTheHonestFallbacks() throws Exception {
        new kome.common.KOMEAccessFixture();
        KOMEUnitMapMarker marker=new KOMEUnitMapMarker();
        marker.movementEntitlement=KOMEUnitMapMarker.ENTITLEMENT_FOOT_OR_MIXED;
        assertSame(Items.leather_boots,KOMECompanyDescriptorRenderer.movementIcon(marker,null).getItem());
        net.minecraft.item.ItemStack actual=new net.minecraft.item.ItemStack(Items.iron_boots);
        assertSame(actual,KOMECompanyDescriptorRenderer.movementIcon(marker,actual));
        marker.movementEntitlement=KOMEUnitMapMarker.ENTITLEMENT_MOUNTED;
        assertSame(Items.saddle,KOMECompanyDescriptorRenderer.movementIcon(marker,null).getItem());
        actual=new net.minecraft.item.ItemStack(Items.iron_horse_armor);
        assertSame(actual,KOMECompanyDescriptorRenderer.movementIcon(marker,actual));
    }

    @Test public void actualRegistryItemsResolveWithoutInventedFallbacks() throws Exception {
        new kome.common.KOMEAccessFixture();
        String melee=String.valueOf(net.minecraft.item.Item.itemRegistry.getNameForObject(Items.iron_sword));
        String ranged=String.valueOf(net.minecraft.item.Item.itemRegistry.getNameForObject(Items.bow));
        assertSame(Items.iron_sword,KOMECompanyDescriptorRenderer.resolve(melee,0).getItem());
        assertSame(Items.bow,KOMECompanyDescriptorRenderer.resolve(ranged,0).getItem());
        assertNull(KOMECompanyDescriptorRenderer.resolve("minecraft:no_such_weapon",0));
        assertNull(KOMECompanyDescriptorRenderer.resolve("invalid/path",0));
        assertNull(KOMECompanyDescriptorRenderer.resolve(melee,-1));
    }

    @Test public void decorativeItemQuadsClipAtAllViewportEdgesWithoutExpandingShieldPicking() {
        KOMEMapViewport view=new KOMEMapViewport(20,30,100,90,0,0,1);
        assertTrue(KOMECompanyDescriptorRenderer.intersectsViewport(18,40,4,view));
        assertTrue(KOMECompanyDescriptorRenderer.intersectsViewport(98,40,4,view));
        assertTrue(KOMECompanyDescriptorRenderer.intersectsViewport(40,28,4,view));
        assertTrue(KOMECompanyDescriptorRenderer.intersectsViewport(40,88,4,view));
        assertFalse(KOMECompanyDescriptorRenderer.intersectsViewport(16,40,4,view));
        assertFalse(KOMECompanyDescriptorRenderer.intersectsViewport(100,40,4,view));
        assertFalse(KOMECompanyDescriptorRenderer.intersectsViewport(40,26,4,view));
        assertFalse(KOMECompanyDescriptorRenderer.intersectsViewport(40,90,4,view));
        KOMEMapIconPresentation.Bounds shield=new KOMEMapIconPresentation.Bounds(30,40,9,9,255);
        KOMEMapMarkerArt.Sprite art=KOMEMapMarkerArt.NEUTRAL_SHIELD;
        int weapon=KOMECompanyDescriptorRenderer.weaponSize(shield,art);
        assertEquals(KOMECompanyDescriptorRenderer.meleeX(shield,art,weapon),
            KOMECompanyDescriptorRenderer.rangedX(shield,art,weapon));
        assertFalse(shield.contains(45,44));
    }

    @Test public void nativeAtlasTintIsScopedAndRestoredOnFailure() throws Exception {
        ClassNode node=new ClassNode();
        try(InputStream in=KOMECompanyDescriptorRenderer.class.getResourceAsStream("KOMECompanyDescriptorRenderer.class")){
            new ClassReader(in).accept(node,0);
        }
        MethodNode draw=null;
        for(Object entry:node.methods)if(((MethodNode)entry).name.equals("drawItem"))draw=(MethodNode)entry;
        assertNotNull(draw);
        Set<String> calls=new HashSet<String>();int mask=0,pops=0;
        for(AbstractInsnNode step:draw.instructions.toArray())if(step instanceof MethodInsnNode){
            MethodInsnNode call=(MethodInsnNode)step;calls.add(call.owner+"."+call.name);
            if(call.owner.equals("org/lwjgl/opengl/GL11")&&call.name.equals("glPushAttrib"))
                mask=((Number)((LdcInsnNode)step.getPrevious()).cst).intValue();
            if(call.owner.equals("org/lwjgl/opengl/GL11")&&call.name.equals("glPopAttrib"))pops++;
        }
        assertTrue(calls.contains("net/minecraft/client/renderer/texture/TextureManager.bindTexture"));
        assertTrue(calls.contains("net/minecraft/item/Item.getIcon"));
        assertTrue(calls.contains("org/lwjgl/opengl/GL11.glTexEnvi"));
        assertTrue(calls.contains("org/lwjgl/opengl/GL11.glColor4f"));
        int required=GL11.GL_TEXTURE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_COLOR_BUFFER_BIT;
        assertEquals(required,mask&required);
        assertTrue("Normal, malformed-art, and exceptional exits restore GL state",pops>=2);
        boolean finallyHandler=false;
        for(Object entry:draw.tryCatchBlocks)if(((TryCatchBlockNode)entry).type==null)finallyHandler=true;
        assertTrue(finallyHandler);
        MethodNode dispatch=null;
        for(Object entry:node.methods)if(((MethodNode)entry).name.equals("draw"))dispatch=(MethodNode)entry;
        assertNotNull(dispatch);
        Set<String> placements=new HashSet<String>();
        for(AbstractInsnNode step:dispatch.instructions.toArray())if(step instanceof MethodInsnNode){
            MethodInsnNode call=(MethodInsnNode)step;
            if(call.owner.equals("kome/client/KOMECompanyDescriptorRenderer"))placements.add(call.name);
        }
        assertTrue(placements.contains("rangedX"));
        assertTrue(placements.contains("meleeX"));
        assertTrue(placements.contains("mixedMeleeOffset"));
    }
}
