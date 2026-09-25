package kome.client;

import java.util.Arrays;
import kome.common.data.KOMEVisualMarker;
import lotr.common.LOTRMod;
import net.minecraft.init.Items;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEVisualRenderBridgeTest {
    @After public void clear() { KOMEVisualMarkerClientState.clear(); }

    @Test public void relationshipLookupIsIdentityAndDimensionSpecific() {
        KOMEVisualMarker marker = new KOMEVisualMarker(KOMEVisualMarker.Role.SERFDOM_MASTER,
            "npc-one", "Master", "Serfdom Master", 100, 1, 2, 3);
        KOMEVisualMarkerClientState.update(Arrays.asList(marker));
        assertSame(marker, KOMEVisualRenderBridge.relationshipFor("npc-one", 100));
        assertNull(KOMEVisualRenderBridge.relationshipFor("npc-two", 100));
        assertNull(KOMEVisualRenderBridge.relationshipFor("npc-one", 0));
    }

    @Test public void nativeItemMappingsMatchRelationshipRoles() {
        assertEquals(Items.iron_hoe, KOMEVisualRenderBridge.icon(KOMEVisualMarker.Role.SERFDOM_MASTER).getItem());
        assertEquals(Items.iron_sword, KOMEVisualRenderBridge.icon(KOMEVisualMarker.Role.KNIGHT_LIEGE).getItem());
        assertEquals(LOTRMod.commandHorn, KOMEVisualRenderBridge.icon(KOMEVisualMarker.Role.LORD_LIEGE).getItem());
        assertEquals(Items.paper, KOMEVisualRenderBridge.icon(KOMEVisualMarker.Role.COURIER).getItem());
    }

    @Test public void boundCourierRecipientIsAnOverheadPaperTarget() {
        KOMEVisualMarker courier=new KOMEVisualMarker(KOMEVisualMarker.Role.COURIER,"recipient","Háma","Deliver the dispatch",100,1,2,3);
        KOMEVisualMarkerClientState.update(Arrays.asList(courier));
        assertSame(courier,KOMEVisualRenderBridge.overheadFor("recipient",100));
        assertEquals(Items.paper,KOMEVisualRenderBridge.icon(KOMEVisualRenderBridge.overheadFor("recipient",100).role).getItem());
        assertNull(KOMEVisualRenderBridge.overheadFor("recipient",0));
    }
}
