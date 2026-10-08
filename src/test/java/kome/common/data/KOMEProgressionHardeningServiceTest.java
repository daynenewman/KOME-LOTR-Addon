package kome.common.data;

import java.util.*;
import lotr.common.fac.LOTRFaction;
import net.minecraft.nbt.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;

public class KOMEProgressionHardeningServiceTest {
    static KOMEProgressionNpcRef liege(String faction){return new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Captain",faction,0,0,65,0);}
    static KOMEKnightCommission report(KOMEKnightServiceRecord s,Type type,String faction){KOMEKnightCommission a=new KOMEKnightCommission(type,liege(faction));assertTrue(s.offer(a));a.stage=Stage.READY_TO_REPORT;assertTrue(s.report(a.liege));return a;}
    @Test public void politicalServiceFollowsFactionWhileDeedsRemainHistorical(){
        KOMEPlayerProgression p=new KOMEPlayerProgression();p.setCanonicalRank(KOMEProgressionRank.KNIGHT);
        for(int i=0;i<3;i++)report(p.getKnightService(),Type.values()[i],"rohan");
        assertTrue(KOMELordshipTrialService.prerequisites(p,2000,"rohan"));assertFalse(KOMELordshipTrialService.prerequisites(p,2000,"gondor"));
        assertEquals(5,p.getKnightService().eligible(Arrays.asList(Type.values()),"gondor").size());
        for(int i=0;i<3;i++)report(p.getKnightService(),Type.values()[i],"gondor");
        p.readFromNBT(p.writeToNBT());assertEquals(6,p.getKnightService().history().size());assertEquals(3,p.getKnightService().qualifyingTypes("gondor").size());assertEquals(3,p.getKnightService().qualifyingTypes("rohan").size());
        assertTrue(KOMELordshipTrialService.prerequisites(p,2000,"gondor"));assertFalse(KOMELordshipTrialService.prerequisites(p,1999,"gondor"));
    }
    @Test public void repeatedReportAndReofferedTokenCannotMintCredit(){KOMEKnightServiceRecord s=new KOMEKnightServiceRecord();KOMEKnightCommission a=report(s,Type.RELIEF,"rohan");assertFalse(s.report(a.liege));a.stage=Stage.READY_TO_REPORT;assertFalse(s.offer(a));assertEquals(1,s.history().size());}
    @Test public void repeatedTypeCannotCountTwiceWithinFaction(){KOMEKnightServiceRecord s=new KOMEKnightServiceRecord();report(s,Type.RELIEF,"rohan");report(s,Type.RELIEF,"rohan");assertEquals(2,s.history().size());assertEquals(1,s.qualifyingTypes("rohan").size());}
    @Test public void readyReportReloadAndDuplicateHistoryAreIdempotent(){
        KOMEKnightServiceRecord s=new KOMEKnightServiceRecord();KOMEKnightCommission a=new KOMEKnightCommission(Type.RELIEF,liege("rohan"));s.offer(a);a.stage=Stage.READY_TO_REPORT;
        s.readFromNBT(s.writeToNBT());assertTrue(s.report(a.liege));NBTTagCompound n=s.writeToNBT();n.getTagList("History",10).appendTag(n.getTagList("History",10).getCompoundTagAt(0).copy());
        n.setTag("Assignment",a.writeToNBT());s.readFromNBT(n);assertNull(s.assignment());assertEquals(1,s.history().size());assertFalse(s.report(a.liege));assertEquals(1,s.qualifyingTypes("rohan").size());
    }
    @Test public void failedTokenCannotBeResurrectedButFreshAttemptCanReport(){KOMEKnightServiceRecord s=new KOMEKnightServiceRecord();KOMEKnightCommission a=new KOMEKnightCommission(Type.RELIEF,liege("rohan"));s.offer(a);a.stage=Stage.FAILED;s.clearFailed();assertFalse(s.offer(a));report(s,Type.RELIEF,"rohan");assertEquals(1,s.qualifyingTypes("rohan").size());}
    @Test public void allSuccessfulReportsSurviveMoreThanThirtyTwoFactionDeeds(){KOMEKnightServiceRecord s=new KOMEKnightServiceRecord();for(int i=0;i<40;i++)report(s,Type.RELIEF,i%2==0?"rohan":"gondor");s.readFromNBT(s.writeToNBT());assertEquals(40,s.history().size());}
    @Test public void provenanceFreeLegacyTypesDoNotFabricateFactionCredit(){KOMEKnightServiceRecord s=new KOMEKnightServiceRecord();NBTTagCompound n=new NBTTagCompound(),t=new NBTTagCompound();NBTTagList types=new NBTTagList();t.setString("Type","RELIEF");types.appendTag(t);n.setTag("CompletedTypes",types);s.readFromNBT(n);assertTrue(s.completedTypes().contains(Type.RELIEF));assertTrue(s.qualifyingTypes("rohan").isEmpty());}
    @Test public void historicalReportWithUnknownFactionSurvivesRepeatedReloadWithoutPoliticalCredit(){
        KOMEKnightCommission a=new KOMEKnightCommission(Type.RELIEF,liege(""));a.stage=Stage.REPORTED;
        NBTTagCompound n=new NBTTagCompound();NBTTagList h=new NBTTagList();h.appendTag(a.writeToNBT());n.setTag("History",h);
        KOMEKnightServiceRecord s=new KOMEKnightServiceRecord();s.readFromNBT(n);s.readFromNBT(s.writeToNBT());assertEquals(1,s.history().size());assertEquals(a.token,s.history().get(0).token);assertTrue(s.completedTypes().contains(Type.RELIEF));assertTrue(s.qualifyingTypes("rohan").isEmpty());assertTrue(s.qualifyingTypes("gondor").isEmpty());
    }
    @Test public void staleReportedAssignmentDoesNotBlockOrFabricateHistory(){KOMEKnightServiceRecord s=new KOMEKnightServiceRecord();NBTTagCompound n=new NBTTagCompound();KOMEKnightCommission a=new KOMEKnightCommission(Type.RELIEF,liege("rohan"));a.stage=Stage.REPORTED;n.setTag("Assignment",a.writeToNBT());s.readFromNBT(n);assertNull(s.assignment());assertTrue(s.history().isEmpty());assertTrue(s.completedTypes().isEmpty());}
    @Test public void wrongFactionReferenceWithSameUuidCannotReport(){KOMEKnightServiceRecord s=new KOMEKnightServiceRecord();KOMEKnightCommission a=new KOMEKnightCommission(Type.RELIEF,liege("rohan"));s.offer(a);a.stage=Stage.READY_TO_REPORT;KOMEProgressionNpcRef wrong=new KOMEProgressionNpcRef(a.liege.entityUuid,"Captain","gondor",0,0,65,0);assertFalse(s.report(wrong));assertTrue(s.report(a.liege));}
    @Test public void malformedCoordinatesAndFactionConflictDoNotLoad(){KOMEKnightCommission a=new KOMEKnightCommission(Type.RELIEF,liege("rohan"));NBTTagCompound n=a.writeToNBT();n.setDouble("X",3e7);assertNull(KOMEKnightCommission.readFromNBT(n));n=a.writeToNBT();n.setString("ServedFaction","gondor");assertNull(KOMEKnightCommission.readFromNBT(n));a.actors.add(new Actor(UUID.randomUUID().toString(),"Guard",Role.GUARD,Double.NaN,65,0));assertNull(KOMEKnightCommission.readFromNBT(a.writeToNBT()));}
    @Test public void alreadyLordReloadArchivesStaleTrialWithoutDemotion()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){f.trial(KOMELordshipTrial.Scenario.BORDER_PATROL,Stage.ACTIVE);f.s.p.setCanonicalRank(KOMEProgressionRank.LORD);f.s.reload();assertEquals(KOMEProgressionRank.LORD,f.s.p.getCanonicalRank());assertNull(f.s.p.getLordship().assignment());assertEquals("rank",f.s.p.getLordship().history().get(0).failureReason);}}
    @Test public void duplicateQuestClicksAndReconnectGrantOneReport()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        KOMEProgressionOfferBridge.registerQuestType();KOMEKnightCommission a=new KOMEKnightCommission(Type.RELIEF,f.s.p.getSerfKnightProgression().getLiege());f.s.p.getKnightService().offer(a);a.stage=Stage.READY_TO_REPORT;
        assertTrue(KOMELiegeProgressionInteraction.quest(f.s.f.player,f.liege));
        assertTrue(KOMELiegeProgressionInteraction.quest(f.s.f.player,f.liege));f.s.reload();assertTrue(KOMELiegeProgressionInteraction.quest(f.s.f.player,f.liege));
        assertEquals(1,f.s.p.getKnightService().history().size());assertEquals(1,f.s.p.getKnightService().qualifyingTypes("rohan").size());assertEquals(KOMEProgressionRank.KNIGHT,f.s.p.getCanonicalRank());
    }}
    @Test public void simultaneousReportRetriesCommitOneDeed()throws Exception {
        KOMEKnightServiceRecord s=new KOMEKnightServiceRecord();KOMEKnightCommission a=new KOMEKnightCommission(Type.RELIEF,liege("rohan"));s.offer(a);a.stage=Stage.READY_TO_REPORT;
        java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newFixedThreadPool(4);
        try{List<java.util.concurrent.Callable<Boolean>> retries=new ArrayList<>();for(int i=0;i<16;i++)retries.add(()->s.report(a.liege));int successes=0;for(java.util.concurrent.Future<Boolean> f:workers.invokeAll(retries))if(f.get())successes++;
            assertEquals(1,successes);assertEquals(1,s.history().size());assertEquals(1,s.qualifyingTypes("rohan").size());
        }finally{workers.shutdownNow();}
    }
    @Test public void orphanedTrialFollowersAreDismissedAfterStaleLordReload()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        KOMELordshipTrial t=f.trial(KOMELordshipTrial.Scenario.BORDER_PATROL,Stage.ACTIVE);f.force(t);f.s.p.setCanonicalRank(KOMEProgressionRank.LORD);
        KOMEProgressionNpcRoles.syncPlayer(f.s.f.data,f.s.f.player.id);for(Actor a:t.objective.actors)assertFalse(KOMEProgressionNpcRoles.protects(f.s.f.data,java.util.UUID.fromString(a.id)));f.s.reload();
        for(Actor a:t.objective.actors){lotr.common.entity.npc.LOTREntityNPC npc=(lotr.common.entity.npc.LOTREntityNPC)KOMEKnightCommissionService.findLoaded(f.s.f.world,a.id);KOMELordshipTrialService.reconcileNpc(f.s.f.data,npc);assertTrue(npc.isDead);assertFalse(npc.hiredNPCInfo.isActive);assertFalse(KOMEProgressionNpcRoles.protects(f.s.f.data,npc.getUniqueID()));}
        assertEquals(KOMEProgressionRank.LORD,f.s.p.getCanonicalRank());
    }}
    @Test public void reportedTrialLeftActiveCannotFabricatePromotionOrBlockRetry()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        KOMELordshipTrial t=f.trial(KOMELordshipTrial.Scenario.BORDER_PATROL,Stage.READY_TO_REPORT);t.objective.stage=Stage.REPORTED;f.s.reload();assertNull(f.s.p.getLordship().assignment());assertTrue(f.s.p.getLordship().history().isEmpty());assertEquals(KOMEProgressionRank.KNIGHT,f.s.p.getCanonicalRank());
    }}
}
