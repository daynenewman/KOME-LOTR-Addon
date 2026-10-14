package kome.common.data;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.util.*;
import static org.junit.Assert.*;
import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;
import static kome.common.data.KOMERecoveryExecutor.Outcome.*;
import static kome.common.data.KOMERecoveryJournalTestSupport.*;

/** Exercise the complete forced PREPARE/executor path, rather than a synthetic bypass token. */
public class KOMECampaignHealingRecoveryTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Rule public KOMETileTestResources geometry=new KOMETileTestResources();
    private KOMECampaignHealingPolicyTest config;
    @Before public void before() throws Exception { config=new KOMECampaignHealingPolicyTest(); config.temporary=temporary; config.defaults(); }
    @After public void after(){config.restore();}
    private KOMERecoveryExecutorTest support(){KOMERecoveryExecutorTest s=new KOMERecoveryExecutorTest();s.temporary=temporary;return s;}
    private void nextBattle(KOMERecoveryExecutorTest.Fixture f) {
        KOMEConflictRecord c=f.data.getConflictService().start("T101",KOMEConflictRecord.State.ORDINARY,ExpectedConflict.absent(),Collections.emptyList(),new Context(25,"test","next conflict")).record;
        c=f.data.getConflictService().checkpointCombat("T101",ExpectedConflict.at(c.getConflictId(),c.getRevision()),
            new KOMEConflictRecord.CombatEpisode(1,c.getConflictId()+":E1",KOMEConflictRecord.EpisodeState.ACTIVE,30L,null),
            new KOMEConflictRecord.TimerSlot(KOMEConflictRecord.TimerStatus.RUNNING,60000,0,30L,c.getConflictId()+":E1"),KOMEConflictRecord.TimerSlot.unstarted(),new Context(30,"test","next battle")).record;
        KOMEOrdinaryBattleRecoveryRegistry registry=f.data.getOrdinaryBattleRecovery(); registry.begin(c,Coverage.COMPLETE);
        registry.enroll(c,RIDER_ALIAS,"C1","gondor",30,MOUNT_ALIAS,RIDER,MOUNT);
    }
    @Test public void fullRiderMountRecoveryAndRestartAreExactlyOnceWithEveryPolicyCombination() throws Exception {
        for(boolean battle:new boolean[]{false,true}) for(boolean lifespan:new boolean[]{false,true}) {
            config.config(false,false);
            try(KOMERecoveryExecutorTest.Fixture f=support().new Fixture()) {
                nextBattle(f); config.config(battle,lifespan);
                assertEquals(battle||lifespan,KOMECampaignHealingPolicy.blocksNormalHealing(f.data,RIDER_ALIAS));
                assertEquals(battle||lifespan,KOMECampaignHealingPolicy.blocksNormalHealing(f.data,MOUNT_ALIAS));
                assertEquals(APPLIED,f.run(Component.RIDER)); assertEquals(APPLIED,f.run(Component.MOUNT));
                assertEquals(8.25F,f.rider.getHealth(),0); assertEquals(8.25F,f.mount.getHealth(),0);
                assertEquals(5,KOMECampaignHealthEntity.projection(f.rider).revision); assertEquals(5,KOMECampaignHealthEntity.projection(f.mount).revision);
                f.restart(f.section()); assertEquals(RECONCILED,f.run(Component.RIDER)); assertEquals(RECONCILED,f.run(Component.MOUNT));
                assertEquals(2,f.manager.view().preparations.size()); assertEquals(3,f.manager.view().lastSequence);
                assertEquals(GrantState.APPLIED,f.grant(Component.RIDER).state); assertEquals(GrantState.APPLIED,f.grant(Component.MOUNT).state);
                assertEquals(1,f.rider.recoveryWrites); assertEquals(1,f.mount.recoveryWrites);
                KOMECampaignHealthProjection recovered=KOMECampaignHealthEntity.projection(f.rider);
                f.rider.setHealth(4F); assertEquals(6,KOMECampaignHealthEntity.projection(f.rider).revision);
                assertEquals(recovered.fence,KOMECampaignHealthEntity.projection(f.rider).fence);
                f.rider.heal(1F); assertEquals(battle||lifespan?4F:5F,f.rider.getHealth(),0);
                assertEquals(battle||lifespan?6:7,KOMECampaignHealthEntity.projection(f.rider).revision);
                assertEquals(recovered.fence,KOMECampaignHealthEntity.projection(f.rider).fence);
            }
        }
    }
    @Test public void cappedAndZeroRecoveryConsumeBothGrantsWithEveryPolicyCombination() throws Exception {
        for(boolean battle:new boolean[]{false,true}) for(boolean lifespan:new boolean[]{false,true}) for(boolean zero:new boolean[]{false,true}) {
            config.config(false,false);
            try(KOMERecoveryExecutorTest.Fixture f=support().new Fixture()) {
                f.amount(zero?0:5); f.set(Component.RIDER,5,9.25F,UUID.randomUUID(),null); f.set(Component.MOUNT,5,10F,UUID.randomUUID(),null);
                nextBattle(f); config.config(battle,lifespan);
                assertEquals(APPLIED,f.run(Component.RIDER)); assertEquals(APPLIED,f.run(Component.MOUNT));
                assertEquals(zero?9.25F:10F,f.rider.getHealth(),0); assertEquals(10F,f.mount.getHealth(),0);
                assertEquals(6,KOMECampaignHealthEntity.projection(f.rider).revision);
                assertEquals(GrantState.APPLIED,f.grant(Component.RIDER).state); assertEquals(GrantState.APPLIED,f.grant(Component.MOUNT).state);
                assertEquals(2,f.manager.view().preparations.size());
            }
        }
    }
    @Test public void durablePrepareBeforeExecutionReplaysUnderBothLocks() throws Exception {
        try(KOMERecoveryExecutorTest.Fixture f=support().new Fixture()) {
            f.prepare(Component.RIDER); config.config(true,true); f.restart(f.section());
            assertEquals(APPLIED,f.run(Component.RIDER)); assertEquals(8.25F,f.rider.getHealth(),0);
            assertEquals(RECONCILED,f.run(Component.RIDER)); assertEquals(2,f.manager.view().lastSequence);
            assertEquals(1,f.rider.recoveryWrites);
        }
    }
    @Test public void consumedEvidenceWithLaterDamageRepairsPendingWithoutHealingUnderLocks() throws Exception {
        try(KOMERecoveryExecutorTest.Fixture f=support().new Fixture()) {
            KOMERecoveryReservations.Result reservation=f.prepare(Component.RIDER);
            net.minecraft.nbt.NBTTagCompound pending=f.section();
            f.set(Component.RIDER,6,1.5F,UUID.randomUUID(),f.fence(reservation)); config.config(true,true); f.restart(pending);
            assertEquals(GrantState.APPLIED,f.grant(Component.RIDER).state); assertEquals(1.5F,f.rider.getHealth(),0);
            assertEquals(RECONCILED,f.run(Component.RIDER)); assertEquals(0,f.rider.recoveryWrites);
            f.rider.heal(1F); assertEquals(1.5F,f.rider.getHealth(),0); assertEquals(2,f.manager.view().lastSequence);
        }
    }
}
