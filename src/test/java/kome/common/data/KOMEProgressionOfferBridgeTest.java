package kome.common.data;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
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
    @Test public void liegeScarcityUsesStableHalfRateWithItsOwnSalt() {
        UUID player=UUID.fromString("11111111-1111-1111-1111-111111111111");
        int selected=0; for(int i=0;i<30;i++){UUID npc=new UUID(i,i*37L);boolean first=KOMEProgressionOfferBridge.isLiegeSelected(player,npc,8);assertEquals(first,KOMEProgressionOfferBridge.isLiegeSelected(player,npc,8));if(first)selected++;}
        assertEquals(2,KOMEProgressionOfferBridge.LIEGE_OFFER_DENOMINATOR);assertTrue(selected>0);assertTrue(selected<30);
    }
    @Test public void bothExternalOfferTypesShareTheOneBridgeAndTransformerPath() throws Exception {
        String bridge=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionOfferBridge.java")),StandardCharsets.UTF_8);
        assertTrue(bridge.contains("KOMESerfdomOfferQuest.TYPE"));assertTrue(bridge.contains("KOMELiegeOfferQuest.TYPE"));assertTrue(bridge.contains("isExternalOffer"));assertTrue(bridge.contains("ensureLiegeOffer"));
        String transformer=new String(Files.readAllBytes(Paths.get("src/main/java/kome/core/KOMEProgressionOfferTransformer.java")),StandardCharsets.UTF_8);
        assertTrue(transformer.contains("KOMEProgressionOfferBridge"));
    }
    @Test public void liegeOfferAndRelationshipRoutesRetainTheirCanonicalGuards() throws Exception {
        String bridge=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionOfferBridge.java")),StandardCharsets.UTF_8);
        assertTrue(bridge.contains("allDutiesComplete(s)"));assertTrue(bridge.contains("s.getSerfdomMaster().isSet()"));assertTrue(bridge.contains("KOMEProgressionRank.SERF"));assertTrue(bridge.contains("KOMEProgressionNpcRank.LORD"));assertTrue(bridge.contains("KOMEProgressionLords.isCombatUnitHiringNpc"));assertTrue(bridge.contains("!n.isChild()"));assertTrue(bridge.contains("!n.hiredNPCInfo.isActive"));assertTrue(bridge.contains("player.getDistanceSqToEntity(npc)<=64"));assertTrue(bridge.contains("selectProspectiveLiege"));
        String route=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/network/KOMEPacketRelationshipAction.java")),StandardCharsets.UTF_8);
        assertTrue(route.contains("n.interactFirst(p)"));assertTrue(route.contains("You are in my service now"));assertTrue(route.contains("You have done enough for one day"));assertFalse(route.contains("KOMEPacketLordMenu"));assertFalse(route.contains("KOMEGuiLordMenu"));
    }
}
