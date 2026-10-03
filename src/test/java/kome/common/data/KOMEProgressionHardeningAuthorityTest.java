package kome.common.data;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import lotr.common.fac.LOTRFaction;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;

public class KOMEProgressionHardeningAuthorityTest {
    @Test public void loadedLiegeAuthorityAgreesAcrossCommissionTrialAndReport()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        assertTrue(KOMECurrentLiege.valid(f.s.f.player,f.liege));assertTrue(KOMEKnightCommissionService.eligible(f.s.f.player,f.liege));f.credits(3);assertTrue(KOMELordshipTrialService.eligible(f.s.f.player,f.liege));
        f.liege.hiredNPCInfo.isActive=true;assertFalse(KOMECurrentLiege.valid(f.s.f.player,f.liege));assertFalse(KOMEKnightCommissionService.eligible(f.s.f.player,f.liege));assertFalse(KOMELordshipTrialService.eligible(f.s.f.player,f.liege));assertFalse(KOMELordshipTrialService.promote(f.s.f.player,f.liege));
    }}
    @Test public void wrongPledgeRejectsEveryLiegeAction()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);f.s.f.pledge(LOTRFaction.GONDOR);assertFalse(KOMECurrentLiege.valid(f.s.f.player,f.liege));assertFalse(KOMEKnightCommissionService.eligible(f.s.f.player,f.liege));assertFalse(KOMELordshipTrialService.eligible(f.s.f.player,f.liege));assertFalse(KOMELordshipTrialService.promote(f.s.f.player,f.liege));
    }}
    @Test public void unloadedLiegeNeverInvalidatesCompletedTrialEvidence()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        KOMELordshipTrial t=f.trial(KOMELordshipTrial.Scenario.BORDER_PATROL,Stage.READY_TO_REPORT);f.s.f.world.loadedEntityList.removeIf(e->e==f.liege);f.s.chunks=false;
        assertFalse(KOMEProgressionRelationshipLifecycle.reconcileLoadedRelationships(f.s.f.data,f.s.f.player));KOMELordshipTrialService.tickPlayer(f.s.f.player);assertTrue(t.ready());assertTrue(f.s.p.getSerfKnightProgression().hasLiege());
    }}
    @Test public void oldQuotaFlagsMarkerAndDirectLegacyServiceCannotPromote()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        for(KOMEProgressionAchievement a:KOMEProgressionAchievement.ALL)if("knight".equals(a.group))f.s.p.grant(a.id);
        KOMEProgressionAutoCompleter.applyUnlocks(f.s.p);assertEquals(KOMEProgressionRank.KNIGHT,f.s.p.getCanonicalRank());
        assertFalse(KOMEHigherRankTransitionService.requirementsComplete(f.s.p,KOMEHigherRankTransitionService.forCurrentRank(KOMEProgressionRank.KNIGHT)));
        assertFalse(KOMEHigherRankTransitionService.promote(f.s.f.data,f.s.f.player.id,"knight.title_lord").success);assertFalse(KOMELordshipTrialService.promote(f.s.f.player,f.liege));f.s.reload();assertEquals(KOMEProgressionRank.KNIGHT,f.s.p.getCanonicalRank());
    }}
    @Test public void commandReplayAndNewGuiRequestNeverSelfCertifyLordship()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);f.trial(KOMELordshipTrial.Scenario.BORDER_PATROL,Stage.ACTIVE);
        kome.common.command.KOMECommandProgression cmd=new kome.common.command.KOMECommandProgression();
        for(int i=0;i<2;i++)try{cmd.processCommand(f.s.f.player,new String[]{"complete","knight.title_lord"});fail("Normal complete must fail");}catch(net.minecraft.command.CommandException expected){}
        assertFalse(KOMELordshipTrialService.promote(f.s.f.player,f.liege));assertEquals(KOMEProgressionRank.KNIGHT,f.s.p.getCanonicalRank());
    }}
    @Test public void rankViewBookAndIdleHudUseCurrentFactionServiceAndLiveStanding()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);KOMEProgressionRankSummary rohan=KOMEProgressionRankSummary.project(f.s.p,2100,"rohan"),gondor=KOMEProgressionRankSummary.project(f.s.p,2100,"gondor");assertEquals(3,rohan.requirements.get(0).current);assertEquals(0,gondor.requirements.get(0).current);
        assertEquals(3,gondor.requirements.size());assertFalse(KOMEProgressionRankSummary.project(f.s.p,Double.POSITIVE_INFINITY,"rohan").requirements.get(1).complete);
        String book=KOMEProgressionSummary.text(f.s.p,"Gondor","gondor",2100);assertFalse(book.contains("drunk"));assertFalse(book.contains("spear"));assertTrue(KOMEProgressionTrackerSnapshot.project(f.s.f.player,f.s.p).visible);
        assertTrue(KOMEProgressionFactionResolver.missingNativeLiege("fangorn"));assertFalse(KOMEProgressionFactionResolver.missingNativeLiege("rohan"));
    }}
    @Test public void rankMutationCallerInventoryHasNoAdditionalGameplayLordSetter()throws Exception {
        String transition=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEHigherRankTransitionService.java")),StandardCharsets.UTF_8);
        assertTrue(transition.contains("transition != KNIGHT_TO_LORD"));assertTrue(transition.contains("transition == KNIGHT_TO_LORD"));
        String admin=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/command/KOMECommandProgression.java")),StandardCharsets.UTF_8);assertTrue(admin.contains("requireStaff(sender)"));
    }
    @Test public void legacyLordMarkerAndPermissionFlagsCannotGiveKnightCaptureAuthority()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.s.p.grant("knight.title_lord");f.s.p.grant("baseline.take_waypoints");f.s.p.grant("baseline.reclaim_waypoints");KOMEProgressionAutoCompleter.applyUnlocks(f.s.p);
        assertFalse(KOMEProgressionPermissions.has(f.s.f.player,KOMEProgressionPermissions.TAKE_WAYPOINTS));assertFalse(KOMEProgressionPermissions.has(f.s.f.player,KOMEProgressionPermissions.RECLAIM_WAYPOINTS));
        f.s.p.setCanonicalRank(KOMEProgressionRank.LORD);assertTrue(KOMEProgressionPermissions.has(f.s.f.player,KOMEProgressionPermissions.TAKE_WAYPOINTS));
    }}
    @Test public void existingCanonicalLordReceivesAuthorityEvenWithoutLegacyTitleMarker()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.s.p.setCanonicalRank(KOMEProgressionRank.LORD);KOMEProgressionAutoCompleter.applyUnlocks(f.s.p);
        assertFalse(f.s.p.isCompleted(KOMEProgressionAchievement.forID("knight.title_lord")));assertTrue(KOMEProgressionPermissions.has(f.s.f.player,KOMEProgressionPermissions.TAKE_WAYPOINTS));assertTrue(KOMEProgressionPermissions.has(f.s.f.player,KOMEProgressionPermissions.RECLAIM_WAYPOINTS));
    }}
}
