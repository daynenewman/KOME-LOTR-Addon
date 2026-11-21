package kome.common.data;

import java.util.UUID;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;

/** Event-to-service regressions for damage evidence and personal field casualties. */
public class KOMEProgressionCombatLifecycleTest {
    @Test public void commissionDamageUsesActualHealthAndSurvivesReload() throws Exception {
        try (KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()) {
            KOMEKnightCommission a=KOMEKnightFieldServiceTest.field(s,Type.BORDER_INCURSION,2);
            LOTREntityNPC enemy=KOMEKnightCommissionGameplayTest.actor(s,a,Role.ENEMY);
            new KOMEEvents().onDefenseParticipation(new LivingHurtEvent(enemy,DamageSource.causePlayerDamage(s.f.player),50F));
            assertTrue(a.participated);assertEquals(1,a.combatHits);assertEquals(20F,a.combatDamage,0F);
            assertTrue(KOMEKnightCommissionService.noteDamage(s.f.data,s.f.player.id,enemy.getUniqueID().toString(),7F));
            s.reload();a=s.p.getKnightService().assignment();
            assertEquals(2,a.combatHits);assertEquals(27F,a.combatDamage,0F);
            NBTTagCompound saved=a.writeToNBT();
            for(float invalid:new float[]{0F,-1F,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})
                assertFalse(KOMEKnightCommissionService.noteDamage(s.f.data,s.f.player.id,enemy.getUniqueID().toString(),invalid));
            assertFalse(KOMEKnightCommissionService.noteDamage(s.f.data,UUID.randomUUID(),enemy.getUniqueID().toString(),5F));
            assertFalse(KOMEKnightCommissionService.noteDamage(s.f.data,s.f.player.id,UUID.randomUUID().toString(),5F));
            assertEquals(saved,a.writeToNBT());
        }
    }

    @Test public void invasionDamageUsesSameEventEvidenceWithoutCreditingInvalidHits() throws Exception {
        try (KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()) {
            KOMELordshipTrial t=f.trial(KOMELordshipTrial.Scenario.NATIVE_INVASION,Stage.ACTIVE);
            LOTREntityNPC enemy=f.actor(t,Role.ENEMY);
            new KOMEEvents().onDefenseParticipation(new LivingHurtEvent(enemy,DamageSource.causePlayerDamage(f.s.f.player),50F));
            assertTrue(t.objective.participated);assertEquals(1,t.objective.combatHits);assertEquals(20F,t.objective.combatDamage,0F);
            f.s.reload();t=f.s.p.getLordship().assignment();
            assertEquals(1,t.objective.combatHits);assertEquals(20F,t.objective.combatDamage,0F);
            NBTTagCompound saved=t.objective.writeToNBT();
            for(float invalid:new float[]{0F,-1F,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})
                assertFalse(KOMELordshipTrialService.noteDamage(f.s.f.data,f.s.f.player.id,enemy.getUniqueID().toString(),invalid));
            assertFalse(KOMELordshipTrialService.noteDamage(f.s.f.data,UUID.randomUUID(),enemy.getUniqueID().toString(),5F));
            assertEquals(saved,t.objective.writeToNBT());
        }
    }

    @Test public void uncancelledPlayerDeathCleansFieldFollowersAndPreservesEarnedHistory() throws Exception {
        try (KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()) {
            KOMEKnightCommission earned=s.active(Type.BORDER_INCURSION);earned.stage=Stage.READY_TO_REPORT;
            assertTrue(s.p.getKnightService().report(earned.liege));NBTTagCompound history=earned.writeToNBT();
            KOMEKnightCommission a=KOMEKnightFieldServiceTest.field(s,Type.FIELD_COMMAND,2);
            LOTREntityNPC guard=KOMEKnightCommissionGameplayTest.actor(s,a,Role.GUARD);
            KOMEKnightFieldService.follow(guard,s.f.player);
            LivingDeathEvent cancelled=new LivingDeathEvent(s.f.player,DamageSource.generic){@Override public boolean isCancelable(){return true;}};cancelled.setCanceled(true);
            new KOMEEvents().onCourierRecipientDeath(cancelled);
            assertEquals(Stage.ACTIVE,a.stage);assertTrue(guard.hiredNPCInfo.isActive);
            new KOMEEvents().onCourierRecipientDeath(new LivingDeathEvent(s.f.player,DamageSource.generic));
            assertEquals(Stage.FAILED,a.stage);assertFalse(a.failureReason.isEmpty());assertTrue(guard.isDead);assertFalse(guard.hiredNPCInfo.isActive);
            s.reload();assertEquals(Stage.FAILED,s.p.getKnightService().assignment().stage);
            assertEquals(history,s.p.getKnightService().history().get(0).writeToNBT());
            assertEquals(KOMEProgressionRank.KNIGHT,s.p.getCanonicalRank());
            assertTrue(s.f.data.armyCompanies.isEmpty());assertTrue(s.f.data.hiredUnits.isEmpty());
        }
    }

    @Test public void playerDeathPreservesLegacyObjectivesAndReportReadyEvidence() throws Exception {
        try (KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()) {
            KOMEKnightCommission legacy=s.active(Type.BORDER_INCURSION);
            KOMEKnightCommissionService.playerDeath(s.f.player);assertEquals(Stage.ACTIVE,legacy.stage);
            KOMEKnightFieldService.profile(legacy,2);legacy.stage=Stage.READY_TO_REPORT;
            KOMEKnightCommissionService.playerDeath(s.f.player);assertEquals(Stage.READY_TO_REPORT,legacy.stage);
        }
    }
}
