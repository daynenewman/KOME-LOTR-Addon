package kome.common.data;

import java.util.*;
import lotr.common.entity.npc.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;
import static kome.common.data.KOMELordshipTrial.Scenario.*;

public class KOMELordshipTrialIntegrationTest {
    @Test public void readyUnloadedTroopsArePreservedAndDoNotTriggerRecall()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.READY_TO_REPORT);f.s.f.world.loadedEntityList.removeIf(e->e instanceof KOMELordshipTrialFixture.TestGuard);f.s.chunks=false;
        for(int i=0;i<100;i++)KOMELordshipTrialService.tickPlayer(f.s.f.player);assertTrue(t.ready());assertFalse(t.forceReleased);assertEquals(4,t.confirmedSurvivors());
    }}
    @Test public void invalidLoadedEntityLossAfterFieldSuccessRecallsForceAndPreservesEvidence()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.READY_TO_REPORT);
        for(Actor a:t.objective.actors){a.x=f.s.f.player.posX;a.z=f.s.f.player.posZ;}
        f.s.f.world.loadedEntityList.removeIf(e->e instanceof KOMELordshipTrialFixture.TestGuard);
        for(int i=0;i<59;i++)KOMELordshipTrialService.tickPlayer(f.s.f.player);assertFalse(t.forceReleased);assertTrue(t.ready());
        KOMELordshipTrialService.tickPlayer(f.s.f.player);assertTrue(t.forceReleased);assertEquals(4,t.confirmedSurvivors());assertTrue(t.ready());
        f.s.reload();assertTrue(KOMELordshipTrialService.promote(f.s.f.player,f.liege));
    }}
    @Test public void normalReportRequiresReturningSoldiersAndPromotionCleansOwnership()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.READY_TO_REPORT);
        for(Actor a:t.objective.actors){LOTREntityNPC n=(LOTREntityNPC)KOMEKnightCommissionService.findLoaded(f.s.f.world,a.id);n.posX+=200;}
        assertFalse(KOMELordshipTrialService.promote(f.s.f.player,f.liege));assertTrue(t.ready());
        for(int i=0;i<2;i++){LOTREntityNPC n=(LOTREntityNPC)KOMEKnightCommissionService.findLoaded(f.s.f.world,t.objective.actors.get(i).id);n.posX=f.liege.posX;}
        assertTrue(KOMELordshipTrialService.promote(f.s.f.player,f.liege));
        for(Actor a:t.objective.actors){LOTREntityNPC n=(LOTREntityNPC)KOMEKnightCommissionService.findLoaded(f.s.f.world,a.id);assertTrue(n.isDead);assertFalse(n.hiredNPCInfo.isActive);assertFalse(KOMEProgressionNpcRoles.protects(f.s.f.data,n.getUniqueID()));}
    }}
    @Test public void readyGuardsSurviveReloadAndReconnectUntilReport()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.READY_TO_REPORT);LOTREntityNPC guard=(LOTREntityNPC)KOMEKnightCommissionService.findLoaded(f.s.f.world,t.objective.actors.get(0).id);
        f.s.reload();KOMELordshipTrialService.reconcileNpc(f.s.f.data,guard);assertFalse(guard.isDead);assertTrue(KOMESerfKnightEscortService.isActiveEscort(f.s.f.data,guard));
        KOMELordshipTrialService.tickPlayer(f.s.f.player);assertTrue(f.s.p.getLordship().assignment().ready());
    }}
    @Test public void confirmedExcessiveCasualtiesOnReturnStillFailResponsibility()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.READY_TO_REPORT);for(int i=0;i<3;i++)f.kill((LOTREntityNPC)KOMEKnightCommissionService.findLoaded(f.s.f.world,t.objective.actors.get(i).id));
        assertEquals(Stage.FAILED,t.objective.stage);assertEquals("casualties",t.failureReason);
    }}
    @Test public void knightQuestPacketOpensSavedTrialThroughNativeTransport()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.OFFERED);KOMEProgressionOfferBridge.registerQuestType();
        KOMEProgressionOfferBridge.OfferSender old=KOMEProgressionOfferBridge.offerSender;List<NBTTagCompound> sent=new ArrayList<NBTTagCompound>();
        try{KOMEProgressionOfferBridge.offerSender=(player,npc,tag)->sent.add(tag);
            new kome.common.network.KOMEPacketRelationshipAction.Handler().onMessage(new kome.common.network.KOMEPacketRelationshipAction(f.liege.getEntityId(),kome.common.network.KOMEPacketRelationshipAction.LIEGE,kome.common.network.KOMEPacketRelationshipAction.SERVICE),f.s.f.context);
            assertEquals(1,sent.size());assertEquals("lordship_trial",sent.get(0).getString("KOMEKind"));assertEquals(t.objective.token,sent.get(0).getString("KOMECommission"));assertEquals(Stage.OFFERED,t.objective.stage);
        }finally{KOMEProgressionOfferBridge.offerSender=old;}
    }}
    @Test public void staleOfferTokenCannotAcceptOrPromoteCurrentTrial()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);KOMELordshipTrial old=f.trial(BORDER_PATROL,Stage.OFFERED);KOMELiegeOfferQuest shell=KOMELiegeOfferQuest.createLordship(lotr.common.LOTRLevelData.getData(f.s.f.player),f.liege,1,old);
        old.objective.stage=Stage.FAILED;f.s.p.getLordship().archive();KOMELordshipTrial current=f.trial(RELIEF_FORCE,Stage.OFFERED);
        f.liege.questInfo.setPlayerSpecificOffer(f.s.f.player,shell);KOMEProgressionOfferBridge.handleResponse(f.liege.questInfo,f.s.f.player,true);
        assertEquals(Stage.OFFERED,current.objective.stage);assertTrue(current.objective.actors.isEmpty());assertEquals(KOMEProgressionRank.KNIGHT,f.s.p.getCanonicalRank());
    }}
    @Test public void acceptanceRechecksStandingAndServiceInsteadOfTrustingPreview()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.OFFERED);f.alignment(1999);
        assertFalse(KOMELordshipTrialService.acceptOrReport(f.s.f.player,f.liege));assertEquals(Stage.OFFERED,t.objective.stage);assertTrue(t.objective.actors.isEmpty());
    }}
    @Test public void previewSurvivesReloadAndReopeningWithoutChangingForceOrDestination()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);KOMELordshipTrial t=f.trial(PROTECTED_EXPEDITION,Stage.OFFERED);NBTTagCompound before=t.writeToNBT();f.s.reload();
        assertEquals(before,KOMELordshipTrialService.prepare(f.s.f.player,f.liege).writeToNBT());KOMEProgressionOfferBridge.prepareStandingTrialInteraction(f.s.f.player,f.liege);assertEquals(before,f.s.p.getLordship().assignment().writeToNBT());
    }}
    @Test public void forgedMarkerCannotExemptAnUnboundOrdinaryHire()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.ACTIVE);LOTREntityNPC npc=f.guard();npc.hiredNPCInfo.isActive=true;npc.hiredNPCInfo.setHiringPlayer(f.s.f.player);
        KOMEProgressionEncounterMarker.mark(npc,KOMELordshipTrialService.MARKER,f.s.f.player.id,t.objective.token);
        assertFalse(KOMESerfKnightEscortService.isActiveEscort(f.s.f.data,npc));KOMELordshipTrialService.reconcileNpc(f.s.f.data,npc);assertTrue(npc.isDead);assertFalse(npc.hiredNPCInfo.isActive);
    }}
    @Test public void guardFollowWaitUsesNativeBehaviorWithoutOpeningHiring()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.ACTIVE);f.atSite(t);LOTREntityNPC guard=f.actor(t,Role.GUARD);
        java.lang.reflect.Field watcher=net.minecraft.entity.Entity.class.getDeclaredField("dataWatcher");watcher.setAccessible(true);net.minecraft.entity.DataWatcher dw=new net.minecraft.entity.DataWatcher(f.s.f.player);dw.addObject(0,Byte.valueOf((byte)0));watcher.set(f.s.f.player,dw);f.s.f.player.setSneaking(true);
        assertTrue(KOMELordshipTrialService.interactFollower(f.s.f.player,guard));assertTrue(guard.hiredNPCInfo.isHalted());assertFalse(KOMEHaltedUnitProtection.isProtected(guard));
        assertTrue(KOMELordshipTrialService.interactFollower(f.s.f.player,guard));assertFalse(guard.hiredNPCInfo.isHalted());assertTrue(f.s.f.data.hiredUnits.isEmpty());
    }}
    @Test public void nativeQuestAcceptsFourGuardsAndRepeatedInterfaceDoesNotReroll()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.OFFERED);
        assertTrue(KOMEProgressionOfferBridge.prepareStandingTrialInteraction(f.s.f.player,f.liege));
        KOMELiegeOfferQuest offer=(KOMELiegeOfferQuest)f.liege.questInfo.getOfferFor(f.s.f.player);assertTrue(offer.isLordshipOffer());
        KOMEProgressionOfferBridge.handleResponse(f.liege.questInfo,f.s.f.player,false);assertEquals(Stage.OFFERED,t.objective.stage);
        KOMEProgressionOfferBridge.prepareStandingTrialInteraction(f.s.f.player,f.liege);assertSame(t,KOMELordshipTrialService.prepare(f.s.f.player,f.liege));
        assertTrue(KOMEProgressionOfferBridge.handleResponse(f.liege.questInfo,f.s.f.player,true));assertEquals(Stage.ACTIVE,t.objective.stage);assertEquals(4,t.confirmedSurvivors());
        assertTrue(f.s.f.data.hiredUnits.isEmpty());f.s.reload();assertEquals(t.objective.token,f.s.p.getLordship().assignment().objective.token);
    }}
    @Test public void fieldTrialNeverAutoPromotesAndLowLiveStandingPreventsReport()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.READY_TO_REPORT);f.alignment(1999);f.s.p.grant("knight.alignment_2000");
        KOMELordshipTrialService.tickPlayer(f.s.f.player);assertEquals(KOMEProgressionRank.KNIGHT,f.s.p.getCanonicalRank());
        assertFalse(KOMELordshipTrialService.promote(f.s.f.player,f.liege));assertTrue(t.ready());f.alignment(2000);
        KOMEProgressionOfferBridge.prepareStandingTrialInteraction(f.s.f.player,f.liege);KOMEProgressionOfferBridge.handleResponse(f.liege.questInfo,f.s.f.player,true);
        assertEquals(KOMEProgressionRank.LORD,f.s.p.getCanonicalRank());String liege=f.s.p.getSerfKnightProgression().getLiege().entityUuid;
        f.s.reload();assertEquals(KOMEProgressionRank.LORD,f.s.p.getCanonicalRank());assertEquals(liege,f.s.p.getSerfKnightProgression().getLiege().entityUuid);
    }}
    @Test public void personalNearbyReportIsRequired()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){f.credits(3);f.trial(BORDER_PATROL,Stage.READY_TO_REPORT);f.s.f.player.posX=f.liege.posX+9;assertFalse(KOMELordshipTrialService.promote(f.s.f.player,f.liege));assertEquals(KOMEProgressionRank.KNIGHT,f.s.p.getCanonicalRank());}}
    @Test public void legacyChecklistAndCompleteCommandCannotBypass()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        for(KOMEHigherRankTransitionService.RequirementGroup g:KOMEHigherRankTransitionService.forCurrentRank(KOMEProgressionRank.KNIGHT).groups)for(String id:g.achievementIds)f.s.p.grant(id);
        assertFalse(KOMEHigherRankTransitionService.promote(f.s.f.data,f.s.f.player.id,"knight.title_lord").success);
        try{new kome.common.command.KOMECommandProgression().processCommand(f.s.f.player,new String[]{"complete","knight.title_lord"});fail("Player self-certification must be rejected");}catch(net.minecraft.command.WrongUsageException expected){}
        assertEquals(KOMEProgressionRank.KNIGHT,f.s.p.getCanonicalRank());assertFalse(KOMELordshipTrialService.eligible(f.s.f.player,f.liege));
    }}
    @Test public void completedTrialStillRequiresThreeServiceCredits()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){f.credits(2);f.trial(BORDER_PATROL,Stage.READY_TO_REPORT);assertFalse(KOMELordshipTrialService.promote(f.s.f.player,f.liege));}}
    @Test public void failureMustBeAccountedForThenRetryIsImmediatelyRecoverable()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){f.credits(3);KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.ACTIVE);f.force(t);KOMELordshipTrialService.cancel(f.s.f.world,f.s.f.player.id,f.s.p);assertTrue(KOMELordshipTrialService.acceptOrReport(f.s.f.player,f.liege));assertNull(f.s.p.getLordship().assignment());assertTrue(KOMELordshipTrialService.eligible(f.s.f.player,f.liege));assertEquals(3,f.s.p.getKnightService().completedTypes().size());}}
    @Test public void loadedInvalidLiegeCancelsActiveTrialAndReplacementCanRetry()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){f.credits(3);KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.ACTIVE);f.force(t);f.liege.setDead();assertTrue(KOMEProgressionRelationshipLifecycle.reconcileLoadedRelationships(f.s.f.data,f.s.f.player));assertEquals(Stage.FAILED,t.objective.stage);assertFalse(f.s.p.getSerfKnightProgression().hasLiege());LOTREntityNPC replacement=KOMEKnightCommissionInteractionTest.captain(f.s);assertTrue(KOMELordshipTrialService.acceptOrReport(f.s.f.player,replacement));assertTrue(KOMELordshipTrialService.eligible(f.s.f.player,replacement));}}
    @Test public void completedFieldEvidenceSurvivesLiegeDeathAndReplacementReport()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){f.credits(3);KOMELordshipTrial t=f.trial(RELIEF_FORCE,Stage.READY_TO_REPORT);f.liege.setDead();KOMEProgressionRelationshipLifecycle.reconcileLoadedRelationships(f.s.f.data,f.s.f.player);assertTrue(t.ready());assertFalse(f.s.p.getSerfKnightProgression().hasLiege());f.s.reload();LOTREntityNPC replacement=KOMEKnightCommissionInteractionTest.captain(f.s);assertTrue(KOMELordshipTrialService.promote(f.s.f.player,replacement));assertEquals(KOMEProgressionRank.LORD,f.s.p.getCanonicalRank());}}
    @Test public void lostLiegeDeathEventPreservesReadyEvidence()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.READY_TO_REPORT);KOMELordshipTrialService.npcDeath(f.s.f.data,f.s.f.world,f.liege.getUniqueID().toString());assertTrue(t.ready());}}
    @Test public void changedPledgeCannotUseOldTrialEvidence()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){f.trial(BORDER_PATROL,Stage.READY_TO_REPORT);f.s.f.pledge(lotr.common.fac.LOTRFaction.GONDOR);KOMELordshipTrialService.tickPlayer(f.s.f.player);assertNull(f.s.p.getLordship().assignment());assertEquals(Stage.FAILED,f.s.p.getLordship().history().get(0).objective.stage);}}
    @Test public void beneficiaryDeathFailsReliefAndExpedition()throws Exception {for(KOMELordshipTrial.Scenario scenario:new KOMELordshipTrial.Scenario[]{RELIEF_FORCE,PROTECTED_EXPEDITION})try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){KOMELordshipTrial t=f.trial(scenario,Stage.ACTIVE);f.force(t);f.kill(f.actor(t,scenario==PROTECTED_EXPEDITION?Role.CHARGE:Role.BENEFICIARY));assertEquals(Stage.FAILED,t.objective.stage);assertEquals("beneficiary",t.failureReason);}}
    @Test public void expeditionRequiresActualChargeArrivalWithSurvivingGuards()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){KOMELordshipTrial t=f.trial(PROTECTED_EXPEDITION,Stage.ACTIVE);f.atSite(t);f.force(t);LOTREntityNPC charge=f.actor(t,Role.CHARGE),enemy=f.actor(t,Role.ENEMY);t.objective.encounterCreated=true;KOMELordshipTrialService.noteDamage(f.s.f.data,f.s.f.player.id,enemy.getUniqueID().toString());f.kill(enemy);f.s.f.player.posX=1000;f.s.f.player.posZ=1000;assertFalse(KOMELordshipTrialService.objectiveComplete(t,f.s.f.player,true));charge.posX=1000;charge.posZ=1000;assertFalse(KOMELordshipTrialService.objectiveComplete(t,f.s.f.player,true));for(Actor a:t.objective.actors)if(a.role==Role.GUARD){LOTREntityNPC n=(LOTREntityNPC)KOMEKnightCommissionService.findLoaded(f.s.f.world,a.id);n.posX=1000;n.posZ=1000;}assertTrue(KOMELordshipTrialService.objectiveComplete(t,f.s.f.player,true));}}
    @Test public void absentTroopCannotBeCountedAtResolution()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.ACTIVE);f.atSite(t);f.force(t);LOTREntityNPC enemy=f.actor(t,Role.ENEMY);t.objective.encounterCreated=true;Set<String> missing=new HashSet<String>();for(int i=0;i<3;i++)missing.add(t.objective.actors.get(i).id);f.s.f.world.loadedEntityList.removeIf(e->e instanceof LOTREntityNPC&&missing.contains(((LOTREntityNPC)e).getUniqueID().toString()));t.objective.participated=true;f.kill(enemy);assertEquals(Stage.ACTIVE,t.objective.stage);}}
    @Test public void cancelledDamageAndUnboundTargetsNeverQualify()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.ACTIVE);LOTREntityNPC enemy=f.actor(t,Role.ENEMY);LivingHurtEvent event=new LivingHurtEvent(enemy,DamageSource.causePlayerDamage(f.s.f.player),5){@Override public boolean isCancelable(){return true;}};event.setCanceled(true);new KOMEEvents().onDefenseParticipation(event);assertFalse(t.objective.participated);assertFalse(KOMELordshipTrialService.noteDamage(f.s.f.data,f.s.f.player.id,UUID.randomUUID().toString()));}}
    @Test public void ownerDamageEventCountsAndHelperDamageDoesNotImpersonateOwner()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.ACTIVE);LOTREntityNPC enemy=f.actor(t,Role.ENEMY);assertFalse(KOMELordshipTrialService.noteDamage(f.s.f.data,UUID.randomUUID(),enemy.getUniqueID().toString()));new KOMEEvents().onDefenseParticipation(new LivingHurtEvent(enemy,DamageSource.causePlayerDamage(f.s.f.player),5){@Override public boolean isCancelable(){return true;}});assertTrue(t.objective.participated);}}
    @Test public void orphanAfterCancellationIsDismissedOnNaturalReload()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.ACTIVE);LOTREntityNPC guard=f.actor(t,Role.GUARD);f.s.f.world.loadedEntityList.removeIf(e->e==guard);KOMELordshipTrialService.cancel(f.s.f.world,f.s.f.player.id,f.s.p);f.s.reload();KOMELordshipTrialService.reconcileNpc(f.s.f.data,guard);assertTrue(guard.isDead);assertFalse(guard.hiredNPCInfo.isActive);}}
    @Test public void guardFollowerOwnershipIsRestoredWithoutOrdinaryArmyRecord()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.ACTIVE);LOTREntityNPC guard=f.actor(t,Role.GUARD);guard.hiredNPCInfo.dismissUnit(false);KOMELordshipTrialService.reconcileNpc(f.s.f.data,guard);assertTrue(KOMELordshipTrialService.temporaryFollower(f.s.f.data,guard));assertTrue(f.s.f.data.hiredUnits.isEmpty());}}
    @Test public void guardWaitDoesNotGrantHaltedInvulnerability()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.ACTIVE);LOTREntityNPC guard=f.actor(t,Role.GUARD);guard.hiredNPCInfo.halt();assertFalse(KOMEHaltedUnitProtection.isProtected(guard));assertTrue(KOMELordshipTrialService.interactFollower(f.s.f.player,guard));}}
    @Test public void rankBookHudAndMarkersShowActualRulesWithoutTokens()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){f.credits(3);KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.ACTIVE);KOMEProgressionRankSummary summary=KOMEProgressionRankSummary.project(f.s.p,1999,"rohan");assertEquals("Service to your Liege",summary.requirements.get(0).label);assertTrue(summary.requirements.get(0).complete);assertFalse(summary.requirements.get(1).complete);assertEquals("Trial of Lordship",summary.requirements.get(2).label);assertFalse(summary.activityObjective.contains(t.objective.token));assertFalse(KOMEProgressionSummary.text(f.s.p,"Rohan","rohan").contains(t.objective.token));assertTrue(KOMEProgressionTrackerSnapshot.project(f.s.f.player,f.s.p).visible);assertTrue(KOMEVisualLocationService.markersFor(f.s.p).stream().anyMatch(m->m.role==KOMEVisualMarker.Role.COMMISSION));t.objective.stage=Stage.READY_TO_REPORT;assertEquals(1,KOMEProgressionTrackerSnapshot.project(f.s.f.player,f.s.p).completion,0);assertFalse(KOMEVisualLocationService.markersFor(f.s.p).stream().anyMatch(m->m.role==KOMEVisualMarker.Role.COMMISSION));}}
    @Test public void nativeLordshipShellRoundTripsTokenAndZeroRewards()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){KOMELordshipTrial t=f.trial(BORDER_PATROL,Stage.OFFERED);KOMEProgressionOfferBridge.registerQuestType();KOMELiegeOfferQuest shell=KOMELiegeOfferQuest.createLordship(lotr.common.LOTRLevelData.getData(f.s.f.player),f.liege,4,t);NBTTagCompound n=new NBTTagCompound();shell.writeToNBT(n);KOMELiegeOfferQuest loaded=new KOMELiegeOfferQuest(lotr.common.LOTRLevelData.getData(f.s.f.player));loaded.readFromNBT(n);assertTrue(loaded.isLordshipOffer());assertEquals(t.objective.token,loaded.commissionToken());assertEquals(0,loaded.getCoinBonus());assertEquals(0,loaded.getAlignmentBonus(),0);}}
}
