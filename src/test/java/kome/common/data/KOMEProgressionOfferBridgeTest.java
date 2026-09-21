package kome.common.data;

import static org.junit.Assert.*;
import java.util.UUID;
import org.junit.Test;

public class KOMEProgressionOfferBridgeTest {
    @Test public void scarcitySelectionIsDeterministicAndNotUniversal() {
        UUID player=UUID.fromString("11111111-1111-1111-1111-111111111111");
        int selected=0; for(int i=0;i<30;i++){UUID npc=new UUID(i,i*37L);boolean first=KOMEProgressionOfferBridge.isSelected(player,npc,8);assertEquals(first,KOMEProgressionOfferBridge.isSelected(player,npc,8));if(first)selected++;}
        assertTrue(selected>0);assertTrue(selected<30);
    }
    @Test public void opportunityWindowIsStableForThreeDays() { assertEquals(KOMEProgressionOfferBridge.opportunityWindow(3),KOMEProgressionOfferBridge.opportunityWindow(5));assertNotEquals(KOMEProgressionOfferBridge.opportunityWindow(5),KOMEProgressionOfferBridge.opportunityWindow(6)); }
    @Test public void declineLedgerPrunesOldDays() { KOMEPlayerProgression p=new KOMEPlayerProgression();p.declineSerfdomOffer("npc",10);assertTrue(p.declinedSerfdomOfferToday("npc",10));assertFalse(p.declinedSerfdomOfferToday("npc",11)); }
}
