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
    @Test public void bothExternalOfferTypesShareTheOneBridgeAndTransformerPath() throws Exception {
        String bridge=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionOfferBridge.java")),StandardCharsets.UTF_8);
        assertTrue(bridge.contains("KOMESerfdomOfferQuest.TYPE"));assertTrue(bridge.contains("KOMELiegeOfferQuest.TYPE"));assertTrue(bridge.contains("isExternalOffer"));assertFalse(bridge.contains("ensureLiegeOffer"));assertTrue(bridge.contains("openStandingTrialOffer"));
        String transformer=new String(Files.readAllBytes(Paths.get("src/main/java/kome/core/KOMEProgressionOfferTransformer.java")),StandardCharsets.UTF_8);
        assertTrue(transformer.contains("KOMEProgressionOfferBridge"));
    }
    @Test public void nativeMiniquestsAreNotGatedAndKomeOffersAreBuiltBeforeAttachment() throws Exception {
        String bridge=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionOfferBridge.java")),StandardCharsets.UTF_8);
        String events=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEEvents.java")),StandardCharsets.UTF_8);
        String offer=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMESerfdomOfferQuest.java")),StandardCharsets.UTF_8);
        assertFalse(bridge.contains("bypassesMiniQuestPermission"));assertFalse(events.contains("enforceMiniQuestPermission"));assertFalse(events.contains("KOMEProgressionPermissions.MINIQUESTS"));assertFalse(events.contains("removeMiniQuest"));
        assertTrue(bridge.contains("KOMESerfdomOfferQuest.create"));assertTrue(bridge.contains("KOMELiegeOfferQuest.createStandingTrial"));assertTrue(bridge.contains("if (offer == null) return false"));assertTrue(offer.contains("npc.createMiniQuest()"));assertTrue(offer.contains("speechBankStart"));assertTrue(offer.contains("quoteComplete"));assertFalse(offer.contains("questInfo"));
    }
    @Test public void nativeQuestInfoOfferRefreshPrecedesInteractionWithoutReplacingExistingOffers() throws Exception {
        String bridge=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionOfferBridge.java")),StandardCharsets.UTF_8);
        String events=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEEvents.java")),StandardCharsets.UTF_8);
        assertTrue(events.contains("refreshNearbyOffers"));assertTrue(bridge.contains("ensureSerfdomOffer(player, npc)"));assertTrue(bridge.contains("refreshStandingTrialOffer(player, npc, false)"));assertTrue(bridge.contains("getEntitiesWithinAABB(")&&bridge.contains("LOTREntityNPC.class"));assertFalse(bridge.contains("for (Object value : player.worldObj.loadedEntityList)"));assertTrue(bridge.contains("getDistanceSqToEntity(npc) <= 1024.0D"));assertTrue(bridge.contains("npc.questInfo.setPlayerSpecificOffer"));assertTrue(bridge.contains("npc.questInfo.sendData"));
    }
    @Test public void liegeOfferAndRelationshipRoutesRetainTheirCanonicalGuards() throws Exception {
        String bridge=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionOfferBridge.java")),StandardCharsets.UTF_8);
        assertTrue(bridge.contains("canRequestTrialFromProspectiveLiege"));assertTrue(bridge.contains("KOMEProgressionRank.SERF"));assertTrue(bridge.contains("KOMEProgressionLiegePolicy.requiredSuperior(progression.getCanonicalRank())"));assertTrue(bridge.contains("KOMEProgressionLords.isStandingTrialLiegeCandidate"));assertTrue(bridge.contains("isChild()"));assertTrue(bridge.contains("hiredNPCInfo.isActive"));assertTrue(bridge.contains("getDistanceSqToEntity(npc)>64.0D"));assertTrue(bridge.contains("acceptStandingTrial"));
        String route=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/network/KOMEPacketRelationshipAction.java")),StandardCharsets.UTF_8);
        assertTrue(route.contains("n.interactFirst(p)"));assertTrue(route.contains("openStandingTrialOffer(p,n)"));assertTrue(route.contains("mayIssueTrial(s,day,p.getUniqueID())"));assertFalse(route.contains("mayIssueAssignment(s,day,p.getUniqueID())"));assertFalse(route.contains("assignTrial(s,p.worldObj.rand,day"));assertFalse(route.contains("KOMEPacketLordMenu"));assertFalse(route.contains("KOMEGuiLordMenu"));
    }

    @Test public void eligibleProspectiveLiegeGetsQuestButtonAndTrialUsesNativeOfferScreen() throws Exception {
        String overlay=new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/KOMELiegeQuestButtonOverlay.java")),StandardCharsets.UTF_8);
        String bridge=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionOfferBridge.java")),StandardCharsets.UTF_8);
        String events=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEEvents.java")),StandardCharsets.UTF_8);
        String offer=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMELiegeOfferQuest.java")),StandardCharsets.UTF_8);
        assertTrue(overlay.contains("LOTRGuiUnitTradeInteract"));assertTrue(overlay.contains("\"Quest\""));assertTrue(overlay.contains("KOMEPacketRelationshipAction.LIEGE"));assertTrue(overlay.contains("KOMEStandingTrialEligibilityState"));assertTrue(overlay.contains("addQuestButton(activeGui, activeButtons)"));assertFalse(overlay.contains("isReadyForStandingTrial"));assertFalse(overlay.contains("standingTrialReady"));assertFalse(overlay.contains("isCurrentLiege"));
        assertTrue(bridge.contains("LOTRPacketMiniquestOffer"));assertTrue(bridge.contains("createStandingTrial"));assertTrue(bridge.contains("canRequestStandingTrialFrom"));assertTrue(bridge.contains("acceptStandingTrial"));
        assertTrue(bridge.contains("KOMEPacketStandingTrialEligibility"));
        assertTrue(events.contains("The current Liege's native interaction GUI owns service through Quest"));
        assertFalse(events.contains("currentLiege && progression.getCanonicalRank()==KOMEProgressionRank.SERF"));
        assertTrue(offer.contains("Trial of Standing"));assertTrue(offer.contains("KOMEKind"));
    }

    @Test public void standingTrialIndicatorLifecycleUsesNativeOfferStateAndCanonicalEligibility() throws Exception {
        String bridge=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionOfferBridge.java")),StandardCharsets.UTF_8);
        String events=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEEvents.java")),StandardCharsets.UTF_8);
        assertTrue(bridge.contains("refreshStandingTrialOffer(player, npc, false)"));
        assertTrue(bridge.contains("canRequestStandingTrialFrom(player, npc)"));
        assertTrue(bridge.contains("ensureStandingTrialOffer(player, npc)"));
        assertTrue(bridge.contains("prepareStandingTrialInteraction"));
        assertTrue(events.contains("prepareStandingTrialInteraction(player, offerNpc)"));
        assertTrue(events.contains("standingTrialCandidate && event.target instanceof LOTRUnitTradeable"));
        assertTrue(events.contains("event.target instanceof LOTRTradeable ? 24 : 20"));
        assertTrue(events.contains("player.openGui(LOTRMod.instance, guiId"));
        assertFalse(events.contains(".speakTo(player)"));
        assertFalse(bridge.contains("npc.questInfo.clientIsOffering"));
    }

    @Test public void legacyLiegeSponsorshipCanNeitherBeCreatedNorAccepted() throws Exception {
        String bridge=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionOfferBridge.java")),StandardCharsets.UTF_8);
        String events=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEEvents.java")),StandardCharsets.UTF_8);
        String offer=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMELiegeOfferQuest.java")),StandardCharsets.UTF_8);
        assertFalse(bridge.contains("isLiegeSelected"));
        assertFalse(bridge.contains("ensureLiegeOffer"));
        assertFalse(events.contains("ensureLiegeOffer"));
        assertFalse(bridge.contains("selectProspectiveLiege"));
        assertFalse(offer.contains("public static KOMELiegeOfferQuest create("));
        assertTrue(bridge.contains("Compatibility cleanup for old saves"));
        assertTrue(offer.contains("return isStandingTrialOffer()"));
    }

    @Test public void escortUsesNativeHiredFollowAndTrustedCompletionOnly() throws Exception {
        String escort=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMESerfKnightEscortService.java")),StandardCharsets.UTF_8);
        assertTrue(escort.contains("hiredNPCInfo.setHiringPlayer(player)"));assertTrue(escort.contains("LOTRHiredNPCInfo.Task.WARRIOR"));assertTrue(escort.contains("data.hasKey(TARGET,10)"));assertTrue(escort.contains("findLoaded"));assertTrue(escort.contains("markTrialObjectiveComplete(state)"));assertTrue(escort.contains("MIN_ESCORT_DISTANCE"));assertFalse(escort.contains("new KOMESerfKnightTrialAssignment"));
    }
    @Test public void standingTrialCandidatesMustHaveTheActualLotrUnitTraderGuiRoute() throws Exception {
        String lords=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionLords.java")),StandardCharsets.UTF_8);
        String bridge=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionOfferBridge.java")),StandardCharsets.UTF_8);
        String service=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMESerfKnightService.java")),StandardCharsets.UTF_8);
        assertTrue(lords.contains("isStandingTrialLiegeCandidate"));
        assertTrue(lords.contains("npc instanceof LOTRUnitTradeable"));
        assertTrue(lords.contains("hasCombatUnitTrades((LOTRUnitTradeable) npc)"));
        assertTrue(bridge.contains("KOMEProgressionLords.isStandingTrialLiegeCandidate(npc)"));
        assertTrue(service.contains("KOMEProgressionLords.isStandingTrialLiegeCandidate(npc)"));
    }

}
