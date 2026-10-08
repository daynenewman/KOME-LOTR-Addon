package kome.client;

import java.util.Arrays;
import kome.common.data.KOMEVisualMarker;
import kome.common.data.KOMEProgressionVisualItems;
import net.minecraft.init.Items;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEVisualRenderBridgeTest {
    @Test public void nativeMediumPouchUsesRegisteredRenderPassInsteadOfMissingBaseIcon() {
        net.minecraft.item.Item previous=lotr.common.LOTRMod.pouch;
        lotr.common.LOTRCommonProxy previousProxy=lotr.common.LOTRMod.proxy;
        lotr.common.item.LOTRItemPouch pouch=new lotr.common.item.LOTRItemPouch();
        pouch.setTextureName("lotr:pouch");
        java.util.List<String> registered=new java.util.ArrayList<String>();
        try {
            lotr.common.LOTRMod.pouch=pouch;
            lotr.common.LOTRMod.proxy=new lotr.common.LOTRCommonProxy();
            pouch.registerIcons(name->{registered.add(name);return new net.minecraft.client.renderer.texture.TextureAtlasSprite(name){};});
            net.minecraft.item.ItemStack stack=KOMEVisualRenderBridge.icon(KOMEVisualMarker.Role.MASTER_GIFT);
            assertSame(pouch,stack.getItem());assertEquals(1,stack.getItemDamage());
            assertNull("Native multipass pouch has no base item icon",stack.getIconIndex());
            net.minecraft.util.IIcon icon=KOMEVisualRenderBridge.itemIcon(stack);assertNotNull(icon);
            assertSame(pouch.getIcon(stack,0),icon);assertTrue(registered.contains(icon.getIconName()));
            assertTrue(icon.getIconName().contains("pouch"));
            assertNotNull(getClass().getResource("/assets/lotr/textures/items/"+icon.getIconName().substring("lotr:".length())+".png"));
        } finally {lotr.common.LOTRMod.pouch=previous;lotr.common.LOTRMod.proxy=previousProxy;}
    }
    @Test public void nonActionableRelationshipKeepsItsOverheadIcon() {
        KOMEVisualMarker marker=new KOMEVisualMarker(KOMEVisualMarker.Role.SERFDOM_MASTER,"master","Master","Master",100,1,2,3,false);
        KOMEVisualMarkerClientState.update(Arrays.asList(marker));
        assertSame(marker,KOMEVisualRenderBridge.relationshipFor("master",100));
        assertSame(marker,KOMEVisualRenderBridge.overheadFor("master",100));
        KOMEVisualMarkerClientState.update(java.util.Collections.emptyList());assertNull(KOMEVisualRenderBridge.relationshipFor("master",100));
    }
    @Test public void giftPouchAndLiegeMedallionRemainOnTheirOwnNpcsAcrossSnapshots() {
        KOMEVisualMarker gift=new KOMEVisualMarker(KOMEVisualMarker.Role.MASTER_GIFT,"old-master","Master","Parting Gift",100,1,2,3);
        KOMEVisualMarker liege=new KOMEVisualMarker(KOMEVisualMarker.Role.KNIGHT_LIEGE,"new-liege","Liege","Liege",100,4,5,6);
        KOMEVisualMarker stale=new KOMEVisualMarker(KOMEVisualMarker.Role.RULER,"old-master","Master","Ruler",100,1,2,3);
        KOMEVisualMarkerClientState.update(Arrays.asList(stale,liege,gift));
        assertSame(stale,KOMEVisualRenderBridge.overheadFor("old-master",100));assertSame(liege,KOMEVisualRenderBridge.overheadFor("new-liege",100));
        KOMEVisualMarkerClientState.update(Arrays.asList(liege));assertNull(KOMEVisualRenderBridge.overheadFor("old-master",100));assertSame(liege,KOMEVisualRenderBridge.overheadFor("new-liege",100));
    }
    @After public void clear() { KOMEVisualMarkerClientState.clear(); }

    @Test public void relationshipLookupIsIdentityAndDimensionSpecific() {
        KOMEVisualMarker marker = new KOMEVisualMarker(KOMEVisualMarker.Role.SERFDOM_MASTER,
            "npc-one", "Master", "Master", 100, 1, 2, 3);
        KOMEVisualMarkerClientState.update(Arrays.asList(marker));
        assertSame(marker, KOMEVisualRenderBridge.relationshipFor("npc-one", 100));
        assertNull(KOMEVisualRenderBridge.relationshipFor("npc-two", 100));
        assertNull(KOMEVisualRenderBridge.relationshipFor("npc-one", 0));
    }

    @Test public void customItemMappingsMatchProgressionRoles() {
        assertEquals(KOMEProgressionVisualItems.RELATIONSHIP, KOMEVisualRenderBridge.icon(KOMEVisualMarker.Role.SERFDOM_MASTER).getItem());
        assertEquals(KOMEProgressionVisualItems.RELATIONSHIP, KOMEVisualRenderBridge.icon(KOMEVisualMarker.Role.KNIGHT_LIEGE).getItem());
        assertEquals(KOMEProgressionVisualItems.RELATIONSHIP, KOMEVisualRenderBridge.icon(KOMEVisualMarker.Role.LORD_LIEGE).getItem());
        assertEquals(KOMEProgressionVisualItems.RULER, KOMEVisualRenderBridge.icon(KOMEVisualMarker.Role.RULER).getItem());
        assertEquals(Items.paper, KOMEVisualRenderBridge.icon(KOMEVisualMarker.Role.COURIER).getItem());
        assertEquals(Items.gold_ingot, KOMEVisualRenderBridge.icon(KOMEVisualMarker.Role.RECOVERY_SEARCH).getItem());
    }

    @Test public void rulerMarkerOverridesRelationshipMarkerForSameNpc() {
        KOMEVisualMarker relationship = new KOMEVisualMarker(KOMEVisualMarker.Role.SERFDOM_MASTER,
            "npc-one", "Master", "Master", 100, 1, 2, 3);
        KOMEVisualMarker ruler = new KOMEVisualMarker(KOMEVisualMarker.Role.RULER,
            "npc-one", "King", "Ruler", 100, 1, 2, 3);
        KOMEVisualMarkerClientState.update(Arrays.asList(relationship, ruler));
        assertSame(ruler, KOMEVisualRenderBridge.overheadFor("npc-one", 100));
        assertSame(ruler, KOMEVisualRenderBridge.relationshipFor("npc-one", 100));
    }

    @Test public void boundCourierRecipientIsAnOverheadPaperTarget() {
        KOMEVisualMarker courier=new KOMEVisualMarker(KOMEVisualMarker.Role.COURIER,"recipient","Háma","Deliver the dispatch",100,1,2,3);
        KOMEVisualMarkerClientState.update(Arrays.asList(courier));
        assertSame(courier,KOMEVisualRenderBridge.overheadFor("recipient",100));
        assertEquals(Items.paper,KOMEVisualRenderBridge.icon(KOMEVisualRenderBridge.overheadFor("recipient",100).role).getItem());
        assertNull(KOMEVisualRenderBridge.overheadFor("recipient",0));
    }
}
