package kome.common.data;

import java.util.*;
import lotr.common.fac.LOTRFaction;
import lotr.common.entity.npc.LOTREntityNPC;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;

/** Both pledge loss and change at every persisted lifecycle phase, including missing relationship metadata. */
@RunWith(Parameterized.class)
public class KOMEProgressionHardeningAllegianceTest {
    @Parameterized.Parameters(name="{0}: {1}")public static Collection<Object[]> phases(){List<Object[]> cases=new ArrayList<>();for(Stage s:Stage.values())if(s!=Stage.REPORTED)for(LOTRFaction pledge:new LOTRFaction[]{LOTRFaction.GONDOR,null})cases.add(new Object[]{s,pledge});return cases;}
    @Parameterized.Parameter public Stage stage;
    @Parameterized.Parameter(1)public LOTRFaction pledge;
    @Test public void ordinaryChargeCleansAndArchivesWithoutPoliticalCredit()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);KOMEKnightCommission a=new KOMEKnightCommission(Type.RELIEF,f.s.p.getSerfKnightProgression().getLiege());a.stage=stage;assertTrue(f.s.p.getKnightService().offer(a));
        LOTREntityNPC actor=KOMEKnightCommissionGameplayTest.actor(f.s,a,Role.BENEFICIARY);
        f.s.p.getSerfKnightProgression().releaseLiegeAfterPromotion();f.s.f.pledge(pledge);
        assertTrue(KOMEProgressionRelationshipLifecycle.reconcilePledgeChange(f.s.f.data,f.s.f.player,pledge==null?"":pledge.codeName()));
        assertNull(f.s.p.getKnightService().assignment());assertTrue(actor.isDead);assertFalse(KOMEProgressionNpcRoles.protects(f.s.f.data,actor.getUniqueID()));
        assertEquals(KOMEProgressionRank.KNIGHT,f.s.p.getCanonicalRank());assertEquals(3,f.s.p.getKnightService().qualifyingTypes("rohan").size());assertTrue(f.s.p.getKnightService().qualifyingTypes("gondor").isEmpty());
        assertEquals(4,f.s.p.getKnightService().history().size());assertEquals(stage==Stage.READY_TO_REPORT,a.threatResolved);
        f.s.reload();assertNull(f.s.p.getKnightService().assignment());assertEquals(4,f.s.p.getKnightService().history().size());
    }}
    @Test public void lordshipChargeCleansForceAndPreservesHistoricalService()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);KOMELordshipTrial t=f.trial(KOMELordshipTrial.Scenario.BORDER_PATROL,stage);if(stage!=Stage.READY_TO_REPORT)f.force(t);
        f.s.p.getSerfKnightProgression().releaseLiegeAfterPromotion();f.s.f.pledge(pledge);
        assertTrue(KOMEProgressionRelationshipLifecycle.reconcilePledgeChange(f.s.f.data,f.s.f.player,pledge==null?"":pledge.codeName()));
        assertNull(f.s.p.getLordship().assignment());assertEquals("allegiance",t.failureReason);assertEquals(Stage.FAILED,t.objective.stage);
        for(Actor a:t.objective.actors){LOTREntityNPC n=(LOTREntityNPC)KOMEKnightCommissionService.findLoaded(f.s.f.world,a.id);assertTrue(n.isDead);assertFalse(n.hiredNPCInfo.isActive);assertFalse(KOMEProgressionNpcRoles.protects(f.s.f.data,n.getUniqueID()));}
        assertEquals(KOMEProgressionRank.KNIGHT,f.s.p.getCanonicalRank());assertEquals(3,f.s.p.getKnightService().history().size());assertTrue(f.s.p.getKnightService().qualifyingTypes("gondor").isEmpty());
        assertEquals(stage==Stage.READY_TO_REPORT,t.objective.threatResolved);f.s.reload();assertNull(f.s.p.getLordship().assignment());assertEquals(1,f.s.p.getLordship().history().size());
    }}
}
