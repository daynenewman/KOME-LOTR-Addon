package kome.client;

import java.util.Arrays;
import kome.common.data.KOMEVisualMarker;
import kome.common.data.KOMEProgressionVisualItems;
import net.minecraft.init.Items;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEVisualRenderBridgeTest {
    @Test public void giftPouchAndLiegeMedallionRemainOnTheirOwnNpcsAcrossSnapshots() {
        KOMEVisualMarker gift=new KOMEVisualMarker(KOMEVisualMarker.Role.MASTER_GIFT,"old-master","Master","Parting Gift",100,1,2,3);
        KOMEVisualMarker liege=new KOMEVisualMarker(KOMEVisualMarker.Role.KNIGHT_LIEGE,"new-liege","Liege","Liege",100,4,5,6);
        KOMEVisualMarker stale=new KOMEVisualMarker(KOMEVisualMarker.Role.RULER,"old-master","Master","Ruler",100,1,2,3);
        KOMEVisualMarkerClientState.update(Arrays.asList(stale,liege,gift));
        assertSame(gift,KOMEVisualRenderBridge.overheadFor("old-master",100));assertSame(liege,KOMEVisualRenderBridge.overheadFor("new-liege",100));
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
