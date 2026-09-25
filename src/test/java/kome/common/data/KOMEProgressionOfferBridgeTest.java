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
    @Test public void nativeMiniquestsAreNotGatedAndKomeOffersAreBuiltBeforeAttachment() throws Exception {
        String bridge=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionOfferBridge.java")),StandardCharsets.UTF_8);
        String events=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEEvents.java")),StandardCharsets.UTF_8);
        String offer=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMESerfdomOfferQuest.java")),StandardCharsets.UTF_8);
        assertFalse(bridge.contains("bypassesMiniQuestPermission"));assertFalse(events.contains("enforceMiniQuestPermission"));assertFalse(events.contains("KOMEProgressionPermissions.MINIQUESTS"));assertFalse(events.contains("removeMiniQuest"));
        assertTrue(bridge.contains("KOMESerfdomOfferQuest.create"));assertTrue(bridge.contains("KOMELiegeOfferQuest.create"));assertTrue(bridge.contains("if (offer == null) return false"));assertTrue(offer.contains("npc.createMiniQuest()"));assertTrue(offer.contains("speechBankStart"));assertTrue(offer.contains("quoteComplete"));assertFalse(offer.contains("questInfo"));
    }
    @Test public void nativeQuestInfoOfferRefreshPrecedesInteractionWithoutReplacingExistingOffers() throws Exception {
        String bridge=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionOfferBridge.java")),StandardCharsets.UTF_8);
        String events=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEEvents.java")),StandardCharsets.UTF_8);
        assertTrue(events.contains("refreshNearbySerfdomOffers"));assertTrue(bridge.contains("getDistanceSqToEntity(npc) <= 1024.0D"));assertTrue(bridge.contains("else if (current != null) return false"));assertTrue(bridge.contains("npc.questInfo.setPlayerSpecificOffer"));assertTrue(bridge.contains("npc.questInfo.sendData"));
    }
    @Test public void liegeOfferAndRelationshipRoutesRetainTheirCanonicalGuards() throws Exception {
        String bridge=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionOfferBridge.java")),StandardCharsets.UTF_8);
        assertTrue(bridge.contains("allDutiesComplete(s)"));assertTrue(bridge.contains("s.getSerfdomMaster().isSet()"));assertTrue(bridge.contains("KOMEProgressionRank.SERF"));assertTrue(bridge.contains("KOMEProgressionNpcRank.LORD"));assertTrue(bridge.contains("KOMEProgressionLords.isCombatUnitHiringNpc"));assertTrue(bridge.contains("!n.isChild()"));assertTrue(bridge.contains("!n.hiredNPCInfo.isActive"));assertTrue(bridge.contains("player.getDistanceSqToEntity(npc)<=64"));assertTrue(bridge.contains("selectProspectiveLiege"));
        String route=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/network/KOMEPacketRelationshipAction.java")),StandardCharsets.UTF_8);
        assertTrue(route.contains("n.interactFirst(p)"));assertTrue(route.contains("assignTrial(s,p.worldObj.rand,day)"));assertTrue(route.contains("trialSpeech(s.getTrialAssignment())"));assertTrue(route.contains("You have done enough for one day"));assertFalse(route.contains("KOMEPacketLordMenu"));assertFalse(route.contains("KOMEGuiLordMenu"));
    }
    @Test public void escortUsesNativeHiredFollowAndTrustedCompletionOnly() throws Exception {
        String escort=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMESerfKnightEscortService.java")),StandardCharsets.UTF_8);
        assertTrue(escort.contains("hiredNPCInfo.setHiringPlayer(player)"));assertTrue(escort.contains("LOTRHiredNPCInfo.Task.WARRIOR"));assertTrue(escort.contains("data.hasKey(TARGET,10)"));assertTrue(escort.contains("findLoaded"));assertTrue(escort.contains("markTrialObjectiveComplete(state)"));assertTrue(escort.contains("MIN_ESCORT_DISTANCE"));assertFalse(escort.contains("new KOMESerfKnightTrialAssignment"));
    }
}
