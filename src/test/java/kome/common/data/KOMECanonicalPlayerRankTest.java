package kome.common.data;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import net.minecraft.nbt.NBTTagCompound;
import lotr.common.fac.LOTRFaction;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMECanonicalPlayerRankTest {
    @Test public void canonicalRankDefaultsRoundTripsAndNeverMigratesLegacyAchievements() {
        KOMEPlayerProgression progression = new KOMEPlayerProgression();
        assertEquals(KOMEProgressionRank.WANDERER, progression.getCanonicalRank());
        assertTrue(progression.grant("knight.craftsman"));
        NBTTagCompound before = progression.writeToNBT();
        KOMEPlayerProgression restored = new KOMEPlayerProgression(); restored.readFromNBT(before);
        assertEquals(KOMEProgressionRank.WANDERER, restored.getCanonicalRank());
        assertTrue(restored.isCompleted(KOMEProgressionAchievement.forID("knight.craftsman")));
        KOMEWorldData data = new KOMEWorldData("ranks"); UUID id = UUID.randomUUID();
        assertTrue(KOMECanonicalRankService.setCanonicalRank(data, id, KOMEProgressionRank.SERF));
        NBTTagCompound saved = data.getProgression(id).writeToNBT();
        KOMEPlayerProgression loaded = new KOMEPlayerProgression(); loaded.readFromNBT(saved);
        assertEquals(KOMEProgressionRank.SERF, loaded.getCanonicalRank());
        assertFalse(loaded.isCompleted(KOMEProgressionAchievement.forID("serf.quest_seeker")));
    }

    @Test public void malformedCanonicalRankFailsSafelyAndKingIsNotAPlayerRank() {
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("CanonicalRank", "king");
        KOMEPlayerProgression progression = new KOMEPlayerProgression(); progression.readFromNBT(tag);
        assertEquals(KOMEProgressionRank.WANDERER, progression.getCanonicalRank());
        assertNull(KOMEProgressionRank.forKey("king"));
    }

    @Test public void resetReturnsToWandererAndRankChangesDoNotEraseCanonicalSerfState() {
        KOMEWorldData data = new KOMEWorldData("ranks"); UUID id = UUID.randomUUID();
        KOMEPlayerProgression progression = data.getProgression(id);
        KOMEProgressionNpcRef master = new KOMEProgressionNpcRef(UUID.randomUUID().toString(), "Master", "rohan", 0, 1, 2, 3);
        assertTrue(KOMESerfKnightService.setSerfdomMaster(progression.getSerfKnightProgression(), master).success);
        assertTrue(KOMECanonicalRankService.setCanonicalRank(data, id, KOMEProgressionRank.SERF));
        assertTrue(KOMECanonicalRankService.setCanonicalRank(data, id, KOMEProgressionRank.KNIGHT));
        assertEquals(KOMEProgressionRank.KNIGHT, progression.getCanonicalRank());
        assertEquals(master.entityUuid, progression.getSerfKnightProgression().getSerfdomMaster().entityUuid);
        progression.reset();
        assertEquals(KOMEProgressionRank.WANDERER, progression.getCanonicalRank());
    }

    @Test public void masterSelectionAllowsWandererAndSerfButRejectsHigherRanksAndInvalidNpc() {
        assertTrue(KOMESerfdomMasterService.validateMasterSelection(KOMEProgressionRank.WANDERER,"rohan","rohan",KOMEProgressionNpcRank.UNRANKED,true).success);
        assertTrue(KOMESerfdomMasterService.validateMasterSelection(KOMEProgressionRank.SERF,"rohan","rohan",KOMEProgressionNpcRank.UNRANKED,true).success);
        for(KOMEProgressionRank rank:new KOMEProgressionRank[]{KOMEProgressionRank.KNIGHT,KOMEProgressionRank.LORD,KOMEProgressionRank.PRINCE}) assertFalse(KOMESerfdomMasterService.validateMasterSelection(rank,"rohan","rohan",KOMEProgressionNpcRank.UNRANKED,true).success);
        for(KOMEProgressionNpcRank rank:new KOMEProgressionNpcRank[]{KOMEProgressionNpcRank.LORD,KOMEProgressionNpcRank.PRINCE,KOMEProgressionNpcRank.KING}) assertFalse(KOMESerfdomMasterService.validateMasterSelection(KOMEProgressionRank.WANDERER,"rohan","rohan",rank,true).success);
        assertFalse(KOMESerfdomMasterService.validateMasterSelection(KOMEProgressionRank.WANDERER,"rohan","gondor",KOMEProgressionNpcRank.UNRANKED,true).success);
        assertFalse(KOMESerfdomMasterService.validateMasterSelection(KOMEProgressionRank.WANDERER,"","rohan",KOMEProgressionNpcRank.UNRANKED,true).success);
        assertFalse(KOMESerfdomMasterService.validateMasterSelection(KOMEProgressionRank.WANDERER,"rohan","rohan",KOMEProgressionNpcRank.UNRANKED,false).success);
        assertFalse(KOMESerfdomMasterService.validateCurrentMasterInteraction(KOMEProgressionRank.WANDERER,"rohan","rohan",KOMEProgressionNpcRank.UNRANKED,true).success);
        assertTrue(KOMESerfdomMasterService.validateCurrentMasterInteraction(KOMEProgressionRank.SERF,"rohan","rohan",KOMEProgressionNpcRank.UNRANKED,true).success);
    }

    @Test public void serfdomRoutingUsesFullEligibilityAndGuiHasNoLocalHighlightAuthority() throws Exception {
        assertTrue(KOMESerfdomMasterService.validateMasterSelection(KOMEProgressionRank.WANDERER,"rohan","rohan",KOMEProgressionNpcRank.UNRANKED,true).success);
        String gui = new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiSerfdomMaster.java")), StandardCharsets.UTF_8);
        assertFalse(gui.contains("KOMEEntityHighlightOverlay"));
        assertFalse(gui.contains("Highlight master"));
        assertTrue(gui.contains("if(mode==1&&hasActiveDuty)"));
        assertTrue(gui.contains("View current duty"));
    }

    @Test public void dutyOrchestrationRequiresCurrentMasterUuidAndUsesCanonicalCadence() {
        KOMESerfKnightProgression state = new KOMESerfKnightProgression();
        KOMEProgressionNpcRef master = new KOMEProgressionNpcRef(UUID.randomUUID().toString(), "Master", "rohan", 0, 0, 0, 0);
        KOMEProgressionNpcRef other = new KOMEProgressionNpcRef(UUID.randomUUID().toString(), "Other", "rohan", 0, 0, 0, 0);
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state, master).success);
        assertTrue(KOMESerfdomMasterService.requestDuty(state, master, 10L, new net.minecraft.nbt.NBTTagCompound(), new java.util.Random(1L)).success);
        assertTrue(state.hasActiveAssignment());
        assertEquals(10L, state.getLastAssignmentEpochDay());
        assertFalse(KOMESerfdomMasterService.requestDuty(state, other, 10L).success);
        assertFalse(KOMESerfdomMasterService.requestDuty(state, master, 10L).success);
        assertTrue(KOMESerfKnightService.completeDuty(state, KOMESerfKnightDutyType.forKey(state.getActiveAssignmentKind())).success);
        assertFalse(KOMESerfdomMasterService.requestDuty(state, master, 10L).success);
        assertTrue(KOMESerfdomMasterService.requestDuty(state, master, 11L).success);
        assertTrue(state.hasActiveAssignment());
    }
    @Test public void voluntaryDepartureResetsOnlyRelationshipScopedProgressWithoutCadencePenalty() {
        KOMESerfKnightProgression state=new KOMESerfKnightProgression();KOMEProgressionNpcRef master=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Master","rohan",0,0,0,0);assertTrue(KOMESerfKnightService.setSerfdomMaster(state,master).success);assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.PROVISIONING,null,20L).success);assertEquals(20L,state.getLastAssignmentEpochDay());assertTrue(KOMESerfKnightService.leaveSerfdomMaster(state).success);assertFalse(state.getSerfdomMaster().isSet());assertFalse(state.getDuty(KOMESerfKnightDutyType.PROVISIONING).isAssigned());assertFalse(state.isMasterReplacementRequired());assertEquals(20L,state.getLastAssignmentEpochDay());assertTrue(KOMESerfKnightService.setSerfdomMaster(state,new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"New","rohan",0,0,0,0)).success);assertFalse(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.PROVISIONING,null,20L).success);assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.PROVISIONING,null,21L).success);
    }

    @Test public void enteringSerfdomRequiresMasterFirstAndIsPermanentAcrossDepartureAndDeath() {
        KOMEWorldData data=new KOMEWorldData("entry");UUID id=UUID.randomUUID();KOMEPlayerProgression player=data.getProgression(id);KOMEProgressionNpcRef first=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"First","rohan",0,0,0,0);
        assertFalse(KOMECanonicalRankService.enterSerfdom(data,id));assertEquals(KOMEProgressionRank.WANDERER,player.getCanonicalRank());
        assertTrue(KOMESerfKnightService.setSerfdomMaster(player.getSerfKnightProgression(),first).success);assertTrue(KOMECanonicalRankService.enterSerfdom(data,id));assertEquals(KOMEProgressionRank.SERF,player.getCanonicalRank());assertFalse(KOMECanonicalRankService.enterSerfdom(data,id));
        assertTrue(KOMESerfKnightService.leaveSerfdomMaster(player.getSerfKnightProgression()).success);assertEquals(KOMEProgressionRank.SERF,player.getCanonicalRank());KOMEProgressionNpcRef replacement=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Replacement","rohan",0,0,0,0);assertTrue(KOMESerfKnightService.setSerfdomMaster(player.getSerfKnightProgression(),replacement).success);assertEquals(KOMEProgressionRank.SERF,player.getCanonicalRank());assertTrue(KOMESerfKnightService.handleNpcDeath(player.getSerfKnightProgression(),replacement.entityUuid,false,20L));assertEquals(KOMEProgressionRank.SERF,player.getCanonicalRank());
    }

    @Test public void pledgeAchievementAloneNeverPromotesAndWandererCannotPerformDuties() {
        KOMEWorldData data=new KOMEWorldData("entry-security");UUID id=UUID.randomUUID();KOMEPlayerProgression player=data.getProgression(id);player.grant("serf.pledge");assertEquals(KOMEProgressionRank.WANDERER,player.getCanonicalRank());KOMEProgressionNpcRef master=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Malformed","rohan",0,0,0,0);assertTrue(KOMESerfKnightService.setSerfdomMaster(player.getSerfKnightProgression(),master).success);assertFalse(KOMESerfdomMasterService.requestDuty(player,master,10L,new NBTTagCompound(),new java.util.Random(1L)).success);player.getSerfKnightProgression().assignDuty(KOMESerfKnightDutyType.PROVISIONING,new NBTTagCompound());assertFalse(KOMESerfKnightService.completeDuty(player,KOMESerfKnightDutyType.PROVISIONING).success);assertFalse(player.getSerfKnightProgression().getDuty(KOMESerfKnightDutyType.PROVISIONING).isCompleted());
    }

    @Test public void summaryDistinguishesPledgeEntryAndPermanentSerfReplacementGuidance() {
        KOMEPlayerProgression player=new KOMEPlayerProgression();String unpledged=KOMEProgressionSummary.text(player,"");assertTrue(unpledged.contains("Pledge: None"));assertTrue(unpledged.contains("Next: Pledge to a faction"));String pledged=KOMEProgressionSummary.text(player,"Rohan");assertTrue(pledged.contains("Pledge: Rohan"));assertTrue(pledged.contains("Next: Find a Serfdom Master"));player.setCanonicalRank(KOMEProgressionRank.SERF);String serf=KOMEProgressionSummary.text(player,"Rohan");assertTrue(serf.contains("Rank: Serf"));assertTrue(serf.contains("Serfdom Master: None"));assertTrue(serf.contains("Next: Find a Serfdom Master"));assertFalse(serf.contains("Sneak-right-click"));
    }

    @Test public void factionSelectionPledgeReconcilesBothCanonicalPledgeDutiesWithoutKomeEvent() throws Exception {
        KOMEPlayerProgression player = new KOMEPlayerProgression();
        for (KOMEProgressionAchievement achievement : KOMEProgressionAchievement.forGroup("wanderer")) player.grant(achievement.id);
        KOMEProgressionNpcRef master = new KOMEProgressionNpcRef(UUID.randomUUID().toString(), "Master", "gondor", 0, 0, 0, 0);
        assertTrue(KOMESerfKnightService.setSerfdomMaster(player.getSerfKnightProgression(), master).success);
        assertTrue(KOMEProgressionAutoCompleter.hasValidFactionCommitment(LOTRFaction.GONDOR));
        assertTrue(KOMEProgressionAutoCompleter.hasValidSerfPledge(player, LOTRFaction.GONDOR));
        assertFalse(player.isCompleted(KOMEProgressionAchievement.forID("baseline.pledge")));
        assertFalse(player.isCompleted(KOMEProgressionAchievement.forID("serf.pledge")));
        assertEquals(2, KOMEProgressionAutoCompleter.reconcilePledgeDuties(player, LOTRFaction.GONDOR));
        assertEquals(0, KOMEProgressionAutoCompleter.reconcilePledgeDuties(player, LOTRFaction.GONDOR));
        assertTrue(player.isCompleted(KOMEProgressionAchievement.forID("baseline.pledge")));
        assertTrue(player.isCompleted(KOMEProgressionAchievement.forID("serf.pledge")));
        assertTrue(KOMEProgressionSummary.text(player, "Gondor").contains("Next: Find a Serfdom Master"));
        assertFalse(KOMEProgressionAutoCompleter.hasValidSerfPledge(player, null));
        assertFalse(KOMEProgressionAutoCompleter.hasValidSerfPledge(player, LOTRFaction.ROHAN));
        NBTTagCompound saved = player.writeToNBT();
        KOMEPlayerProgression restored = new KOMEPlayerProgression();
        restored.readFromNBT(saved);
        assertTrue(restored.isCompleted(KOMEProgressionAchievement.forID("baseline.pledge")));
        assertTrue(restored.isCompleted(KOMEProgressionAchievement.forID("serf.pledge")));
        assertFalse(restored.grant("serf.pledge"));
        String creation = new String(Files.readAllBytes(Paths.get("src/main/java/com/lotrcharactercreation/faction/StartingFactionApplication.java")), StandardCharsets.UTF_8);
        assertTrue(creation.contains("lotrData.setPledgeFaction(selectedPledge)"));
    }

    @Test public void unpledgedSerfPledgeDutyRemainsIncompleteAndUsesNoCharacterCreationCompatibility() throws Exception {
        KOMEPlayerProgression player = new KOMEPlayerProgression();
        for (KOMEProgressionAchievement achievement : KOMEProgressionAchievement.forGroup("wanderer")) player.grant(achievement.id);
        assertEquals(0, KOMEProgressionAutoCompleter.reconcilePledgeDuties(player, null));
        assertFalse(player.isCompleted(KOMEProgressionAchievement.forID("baseline.pledge")));
        assertFalse(player.isCompleted(KOMEProgressionAchievement.forID("serf.pledge")));
        String source = new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionAutoCompleter.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("LOTRLevelData.getData(player).getPledgeFaction()"));
        assertFalse(source.contains("lotrcharactercreation"));
    }

    @Test public void firstBookReconciliationCompletesTheBaselinePledgeBeforeSerfPrerequisites() {
        KOMEPlayerProgression player = new KOMEPlayerProgression();
        assertEquals(1, KOMEProgressionAutoCompleter.reconcilePledgeDuties(player, LOTRFaction.GONDOR));
        assertTrue(player.isCompleted(KOMEProgressionAchievement.forID("baseline.pledge")));
        assertFalse(player.isCompleted(KOMEProgressionAchievement.forID("serf.pledge")));
    }

    @Test public void wrongMasterFactionCompletesOnlyTheGeneralFactionCommitmentDuty() {
        KOMEPlayerProgression player = new KOMEPlayerProgression();
        for (KOMEProgressionAchievement achievement : KOMEProgressionAchievement.forGroup("wanderer")) player.grant(achievement.id);
        assertTrue(KOMESerfKnightService.setSerfdomMaster(player.getSerfKnightProgression(), new KOMEProgressionNpcRef(UUID.randomUUID().toString(), "Master", "gondor", 0, 0, 0, 0)).success);
        assertEquals(1, KOMEProgressionAutoCompleter.reconcilePledgeDuties(player, LOTRFaction.ROHAN));
        assertTrue(player.isCompleted(KOMEProgressionAchievement.forID("baseline.pledge")));
        assertFalse(player.isCompleted(KOMEProgressionAchievement.forID("serf.pledge")));
    }

    @Test public void progressionBookRequestRunsCanonicalReconciliationBeforeProjection() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/network/KOMEPacketProgressionRequest.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("KOMEProgressionAutoCompleter.runForPlayer(player, false)"));
        assertTrue(source.contains("data.progressions.containsKey(KOMEReflection.getEntityUUID(player))"));
    }

    @Test public void interactionRoutingPreservesLordPriorityAndDoesNotOfferReplacementOverActiveMaster() throws Exception {
        String events=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEEvents.java")),StandardCharsets.UTF_8);int offer=events.indexOf("isExternalOffer");int master=events.indexOf("boolean currentMaster");int liege=events.indexOf("boolean currentLiege");assertTrue(offer>=0&&master>offer&&liege>master);assertTrue(events.contains("KOMEProgressionOfferBridge.ensureSerfdomOffer"));assertFalse(events.contains("event.entityPlayer.isSneaking() && event.target instanceof LOTREntityNPC"));assertTrue(events.contains("KOMEPacketRelationshipAction.sendHub"));String hub=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/network/KOMEPacketRelationshipAction.java")),StandardCharsets.UTF_8);assertTrue(hub.contains("n.interactFirst(p)"));assertTrue(hub.contains("KOMEPacketSerfdomMasterAction.sendMenu(p,n)"));assertTrue(hub.contains("getProspectiveLiege().hasSameIdentity"));String bridge=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionOfferBridge.java")),StandardCharsets.UTF_8);assertTrue(bridge.contains("!progression.getSerfKnightProgression().getSerfdomMaster().isSet()"));assertTrue(bridge.contains("KOMEProgressionAutoCompleter.syncPlayer"));
    }

    @Test public void masterDialogueUsesNativeLotrSpeechWhileTechnicalFailuresRemainSystemFeedback() throws Exception {
        String speech=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionNpcSpeech.java")),StandardCharsets.UTF_8);assertTrue(speech.contains("LOTRSpeech.sendSpeech(player, npc, text)"));assertTrue(speech.contains("You are in my service now"));assertTrue(speech.contains("You may serve me"));assertTrue(speech.contains("I have need of provisions"));assertTrue(speech.contains("Return tomorrow"));assertTrue(speech.contains("I am still waiting on those provisions"));assertTrue(speech.contains("I see nothing here that I asked for"));assertTrue(speech.contains("Bring me the rest"));assertTrue(speech.contains("That is everything I asked for"));String bridge=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionOfferBridge.java")),StandardCharsets.UTF_8);assertTrue(bridge.contains("KOMEProgressionNpcSpeech.welcomeSerf"));String packet=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/network/KOMEPacketSerfdomMasterAction.java")),StandardCharsets.UTF_8);assertTrue(packet.contains("KOMEProgressionNpcSpeech.assignDuty"));assertTrue(packet.contains("KOMEProgressionNpcSpeech.sameDay"));assertTrue(packet.contains("KOMEProgressionNpcSpeech.viewDuty"));assertTrue(packet.contains("KOMEProgressionNpcSpeech.noMatchingProvisions"));assertTrue(packet.contains("KOMEProgressionNpcSpeech.partialProvisions"));assertTrue(packet.contains("KOMEProgressionNpcSpeech.completedProvisions"));assertTrue(packet.contains("Unknown Serfdom Master action."));assertTrue(packet.contains("new ChatComponentText(result.reason)"));
    }
}
