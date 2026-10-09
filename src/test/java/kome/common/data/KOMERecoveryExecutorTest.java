package kome.common.data;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import kome.common.KOMEAccessFixture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;
import static kome.common.data.KOMERecoveryJournalRecord.*;
import static kome.common.data.KOMERecoveryJournalTestSupport.*;
import static kome.common.data.KOMERecoveryExecutor.Outcome.*;

public class KOMERecoveryExecutorTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Rule public KOMETileTestResources geometry = new KOMETileTestResources();
    static final class Living extends EntityPig {
        boolean failSave, observeHooks;
        int recoveryWrites;
        Living(net.minecraft.world.World world) { super(world); }
        @Override public void setHealth(float hp) {
            if (observeHooks && !KOMECampaignHealthEntity.allowChange(this,hp)) return;
            super.setHealth(hp);
            if (observeHooks) { recoveryWrites++; KOMECampaignHealthEntity.changed(this); }
        }
        @Override public void writeEntityToNBT(NBTTagCompound nbt) {
            super.writeEntityToNBT(nbt);
            if (failSave) nbt.setFloat("HealF",-1F); // Native serializer failed to acknowledge adopted HP.
        }
    }
    final class Fixture implements AutoCloseable {
        final KOMEAccessFixture access = new KOMEAccessFixture();
        final KOMEWorldData data = access.data;
        final Living rider = new Living(access.world), mount = new Living(access.world);
        final KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
        final File directory = temporary.newFolder();
        KOMERecoveryJournalManager manager;
        KOMERecoveryStartup startup;
        final Field singleton;
        final Object previous;
        Fixture() throws Exception {
            alias(rider,RIDER_ALIAS); alias(mount,MOUNT_ALIAS);
            unit.entity=RIDER_ALIAS; unit.owner=UUID.randomUUID(); unit.currentTile="T101";
            KOMEHiredUnitClassification.assignForCampaignWorkflow(unit); data.hiredUnits.put(unit.entity,unit);
            set(Component.RIDER,4,3.25F,new UUID(4,1),null);
            set(Component.MOUNT,4,3.25F,new UUID(4,1),null);
            access.world.loadedEntityList.add(rider); access.world.loadedEntityList.add(mount);
            EpisodeContext c=context(); episode(c.episode);
            data.getConflictService().replaceFrom(new KOMEConflictService(2));
            singleton=KOMERecoveryStartup.class.getDeclaredField("session"); singleton.setAccessible(true);
            previous=singleton.get(null); start(section());
            rider.observeHooks=true; mount.observeHooks=true;
        }
        NBTTagCompound section() {
            NBTTagCompound n=KOMEConflictPersistence.write(data.getConflictService(),data.getJoinBattleDeploymentReceipts(),data.getOrdinaryBattleRecovery());
            n.setTag("SavedHealth",unit.survivingHealth.copy()); return n;
        }
        void start(NBTTagCompound section) throws Exception {
            manager=new KOMERecoveryJournalManager(directory,new Object(),KOMERecoveryJournalIO.FILES);
            startup=new KOMERecoveryStartup(net.minecraft.server.MinecraftServer.getServer());
            singleton.set(null,startup); startup.initialize(manager,section,data);
            assertEquals(startup.diagnostic(),KOMERecoveryStartup.State.READY,startup.state());
            startup.runtime.observe(rider); startup.runtime.observe(mount);
        }
        void restart(NBTTagCompound section) throws Exception { startup.stop(); start(section); }
        Living entity(Component c) { return c==Component.RIDER?rider:mount; }
        UUID target(Component c) { return c==Component.RIDER?RIDER:MOUNT; }
        void set(Component c,long revision,float hp,UUID token,KOMECampaignHealthProjection.Fence fence) {
            Living entity=entity(c); boolean hooks=entity.observeHooks; entity.observeHooks=false; entity.setHealth(hp); entity.observeHooks=hooks;
            KOMECampaignHealthProjection p=new KOMECampaignHealthProjection(target(c),c,entity.getUniqueID(),hp,revision,token,false,fence);
            KOMECampaignHealth.updateProjection(unit,p); KOMECampaignHealthEntity.install(entity,p,unit,false);
        }
        Episode episode() { return data.getOrdinaryBattleRecovery().get("CF1","CF1:E1"); }
        void episode(Episode e) { data.getOrdinaryBattleRecovery().replaceFrom(KOMEOrdinaryBattleRecoveryRegistry.restore(context().targets,Collections.singletonMap(e.episodeId,e))); }
        Grant grant(Component c) { return episode().grants.get(c+":"+target(c)); }
        KOMERecoveryExecutor.Outcome run(Component c) { return KOMERecoveryExecutor.execute(data,"CF1","CF1:E1",c,target(c),entity(c)); }
        KOMERecoveryReservations.Result prepare(Component c) { return KOMERecoveryReservations.prepare(data,"CF1","CF1:E1",c,target(c),entity(c)); }
        void amount(float value) {
            Episode e=episode(); Intent i=e.intent;
            Intent next=new Intent(i.conflictId,i.episodeId,i.expectedRevision,i.expectedState,i.kind,i.expectedOwner,i.winner,value,i.stage,i.closedAt);
            Map<String,Grant> grants=new TreeMap<String,Grant>();
            for (Grant g:e.grants.values()) grants.put(g.key(),new Grant(g.participantId,g.targetId,g.component,value,g.state,null,""));
            episode(e.update(e.endedAt,e.actualHostileCombatObserved,e.participants,next,grants));
        }
        KOMECampaignHealthProjection.Fence fence(KOMERecoveryReservations.Result r) {
            return new KOMECampaignHealthProjection.Fence(r.expectedFence.journalId,r.expectedFence.prepareSequence,r.expectedFence.transitionId);
        }
        @Override public void close() throws Exception { try { startup.stop(); } finally { singleton.set(null,previous); } }
    }
    static void alias(Entity e,UUID id) throws Exception { Field f=Entity.class.getDeclaredField("entityUniqueID"); f.setAccessible(true); f.set(e,id); }
    @Test public void defaultFractionalRecoveryAdoptsExactlyOneRevisionAndAcknowledgment() throws Exception {
        try(Fixture f=new Fixture()) {
            assertEquals(APPLIED,f.run(Component.RIDER)); assertEquals(8.25F,f.rider.getHealth(),0);
            KOMECampaignHealthProjection p=KOMECampaignHealthEntity.projection(f.rider);
            assertEquals(5,p.revision); assertEquals(f.grant(Component.RIDER).transition.token,p.transition);
            assertEquals(GrantState.APPLIED,f.grant(Component.RIDER).state); assertEquals(1,f.rider.recoveryWrites);
            assertTrue(f.startup.permitsMutation(p)); assertEquals(2,f.manager.view().lastSequence);
            NBTTagCompound saved=new NBTTagCompound(); f.rider.writeToNBT(saved); KOMECampaignHealthEntity.endWrite(f.rider,saved);
            assertEquals(p.write(),saved.getCompoundTag(KOMECampaignHealthProjection.ENTITY_TAG));
            assertEquals(p.write(),KOMECampaignHealthProjection.read(f.unit.survivingHealth).write());
            assertEquals(10F,f.rider.getMaxHealth(),0);
        }
    }
    @Test public void alternativeAmountUsesFrozenEpisodeSnapshot() throws Exception {
        try(Fixture f=new Fixture()) { f.amount(2.5F); assertEquals(APPLIED,f.run(Component.RIDER)); assertEquals(5.75F,f.rider.getHealth(),0); }
    }
    @Test public void cappedAndAlreadyAtMaximumConsumeWithoutExcess() throws Exception {
        for(float source:new float[]{8.75F,10F}) try(Fixture f=new Fixture()) {
            f.set(Component.RIDER,5,source,UUID.randomUUID(),null); assertEquals(APPLIED,f.run(Component.RIDER));
            assertEquals(10F,f.rider.getHealth(),0); assertEquals(6,KOMECampaignHealthEntity.projection(f.rider).revision);
            assertEquals(GrantState.APPLIED,f.grant(Component.RIDER).state);
        }
    }
    @Test public void zeroConsumesGrantWithoutChangingHp() throws Exception {
        try(Fixture f=new Fixture()) { f.amount(0); assertEquals(APPLIED,f.run(Component.RIDER)); assertEquals(3.25F,f.rider.getHealth(),0); assertEquals(5,KOMECampaignHealthEntity.projection(f.rider).revision); }
    }
    @Test public void retryDoesNotHealTwiceOrAllocatePrepare() throws Exception {
        try(Fixture f=new Fixture()) {
            assertEquals(APPLIED,f.run(Component.RIDER)); UUID token=f.grant(Component.RIDER).transition.token;
            assertEquals(RECONCILED,f.run(Component.RIDER)); assertEquals(8.25F,f.rider.getHealth(),0);
            assertEquals(1,f.rider.recoveryWrites); assertEquals(2,f.manager.view().lastSequence); assertEquals(token,f.grant(Component.RIDER).transition.token);
        }
    }
    @Test public void riderAndExactDismountedMountAreIndependent() throws Exception {
        try(Fixture f=new Fixture()) {
            assertNull(f.rider.ridingEntity); assertEquals(APPLIED,f.run(Component.RIDER));
            assertEquals(3.25F,f.mount.getHealth(),0); assertEquals(GrantState.PENDING,f.grant(Component.MOUNT).state);
            assertEquals(Stage.RECOVERY_PENDING,f.episode().intent.stage);
            assertEquals(APPLIED,f.run(Component.MOUNT)); assertEquals(8.25F,f.mount.getHealth(),0);
            assertEquals(3,f.manager.view().lastSequence); assertEquals(Stage.COMPLETE,f.episode().intent.stage);
            assertNull(f.rider.ridingEntity);
        }
    }
    @Test public void unavailableMountKeepsClosurePendingUntilLoadedCallback() throws Exception {
        try(Fixture f=new Fixture()) {
            f.access.world.loadedEntityList.remove(f.mount); KOMERecoveryStartup.forgetEntity(f.mount);
            assertEquals(APPLIED,f.run(Component.RIDER)); assertEquals(UNAVAILABLE,f.run(Component.MOUNT));
            assertEquals(2,f.manager.view().lastSequence); assertEquals(Stage.RECOVERY_PENDING,f.episode().intent.stage);
            f.access.world.loadedEntityList.add(f.mount); KOMERecoveryStartup.observeEntity(f.mount);
            assertEquals(1,KOMERecoveryExecutor.processCampaignTick(f.data)); assertEquals(Stage.COMPLETE,f.episode().intent.stage);
        }
    }
    @Test public void noPrepareForUnavailableRider() throws Exception {
        try(Fixture f=new Fixture()) { f.access.world.loadedEntityList.remove(f.rider); assertEquals(UNAVAILABLE,f.run(Component.RIDER)); assertEquals(0,f.manager.view().lastSequence); }
    }
    @Test public void durablePrepareReplayUsesExactAbsoluteHpWithOlderBothProjections() throws Exception {
        try(Fixture f=new Fixture()) {
            NBTTagCompound oldHealth=(NBTTagCompound)f.unit.survivingHealth.copy(), oldConflict=f.section();
            f.set(Component.RIDER,5,4.25F,UUID.randomUUID(),null); KOMERecoveryReservations.Result r=f.prepare(Component.RIDER);
            Prepare p=(Prepare)r.evidence.record;
            f.startup.stop(); f.unit.healthAuthorityData=null; f.unit.survivingHealth=oldHealth;
            f.rider.observeHooks=false; f.rider.setHealth(3.25F);
            // A fresh physical instance carries the older saved acknowledgment after a crash.
            Living reloaded=new Living(f.access.world); alias(reloaded,RIDER_ALIAS); reloaded.setHealth(3.25F);
            KOMECampaignHealthEntity.install(reloaded,KOMECampaignHealthProjection.read(oldHealth),f.unit,false);
            f.access.world.loadedEntityList.remove(f.rider); f.access.world.loadedEntityList.add(reloaded);
            f.start(oldConflict); f.startup.runtime.observe(reloaded);
            assertEquals(APPLIED,KOMERecoveryExecutor.execute(f.data,"CF1","CF1:E1",Component.RIDER,RIDER,reloaded));
            assertEquals(9.25F,reloaded.getHealth(),0); assertEquals(p.recoveryRevision,KOMECampaignHealthEntity.projection(reloaded).revision);
            assertEquals(p.recoveryTransitionId,KOMECampaignHealthEntity.projection(reloaded).transition); assertEquals(2,f.manager.view().lastSequence);
        }
    }
    @Test public void exactConsumedProjectionRepairsPendingWithoutHpOperation() throws Exception {
        try(Fixture f=new Fixture()) {
            KOMERecoveryReservations.Result r=f.prepare(Component.RIDER); Prepare p=(Prepare)r.evidence.record;
            f.set(Component.RIDER,p.recoveryRevision,p.targetHp,p.recoveryTransitionId,f.fence(r));
            assertEquals(RECONCILED,f.run(Component.RIDER)); assertEquals(0,f.rider.recoveryWrites);
            assertEquals(GrantState.APPLIED,f.grant(Component.RIDER).state); assertEquals(2,f.manager.view().lastSequence);
        }
    }
    @Test public void descendantDamageRepairsPendingAndPreservesNewerHp() throws Exception {
        try(Fixture f=new Fixture()) {
            KOMERecoveryReservations.Result r=f.prepare(Component.RIDER);
            f.set(Component.RIDER,6,1.125F,UUID.randomUUID(),f.fence(r));
            assertEquals(RECONCILED,f.run(Component.RIDER)); assertEquals(1.125F,f.rider.getHealth(),0);
            assertEquals(6,KOMECampaignHealthEntity.projection(f.rider).revision); assertEquals(0,f.rider.recoveryWrites);
        }
    }
    @Test public void startupRepairsConsumedRiderAndLeavesMountPending() throws Exception {
        try(Fixture f=new Fixture()) {
            KOMERecoveryReservations.Result r=f.prepare(Component.RIDER); NBTTagCompound pending=f.section(); Prepare p=(Prepare)r.evidence.record;
            f.set(Component.RIDER,p.recoveryRevision,p.targetHp,p.recoveryTransitionId,f.fence(r));
            f.restart(pending); assertEquals(GrantState.APPLIED,f.grant(Component.RIDER).state);
            assertEquals(GrantState.PENDING,f.grant(Component.MOUNT).state); assertEquals(0,f.rider.recoveryWrites);
            assertEquals(APPLIED,f.run(Component.MOUNT)); assertEquals(Stage.COMPLETE,f.episode().intent.stage);
        }
    }
    @Test public void crashAfterConsumptionBeforeClosureDoesNotRepeatEffects() throws Exception {
        try(Fixture f=new Fixture()) {
            KOMERecoveryReservations.Result r=f.prepare(Component.RIDER), m=f.prepare(Component.MOUNT); NBTTagCompound pending=f.section();
            Prepare rp=(Prepare)r.evidence.record, mp=(Prepare)m.evidence.record;
            f.set(Component.RIDER,5,rp.targetHp,rp.recoveryTransitionId,f.fence(r));
            f.set(Component.MOUNT,5,mp.targetHp,mp.recoveryTransitionId,f.fence(m));
            f.restart(pending); assertEquals(Stage.COMPLETE,f.episode().intent.stage);
            assertEquals(0,KOMERecoveryExecutor.processCampaignTick(f.data)); assertEquals(0,f.rider.recoveryWrites+f.mount.recoveryWrites);
            assertEquals(3,f.manager.view().lastSequence);
        }
    }
    @Test public void failedSerializationRemainsGuardedAndNeverMarksApplied() throws Exception {
        try(Fixture f=new Fixture()) {
            f.rider.failSave=true; assertEquals(QUARANTINED,f.run(Component.RIDER));
            assertEquals(GrantState.PENDING,f.grant(Component.RIDER).state); assertNotNull(f.grant(Component.RIDER).transition);
            assertFalse(f.startup.permitsMutation(KOMECampaignHealthEntity.projection(f.rider)));
            assertEquals(0,KOMERecoveryExecutor.processCampaignTick(f.data)); assertEquals(2,f.manager.view().lastSequence);
        }
    }
    @Test public void guardBlocksReservedSourceButReleasesOnlyAfterAdoption() throws Exception {
        try(Fixture f=new Fixture()) {
            f.prepare(Component.RIDER); assertFalse(f.startup.permitsMutation(KOMECampaignHealthEntity.projection(f.rider)));
            assertTrue(f.startup.permitsMutation(KOMECampaignHealthEntity.projection(f.mount)));
            assertEquals(APPLIED,f.run(Component.RIDER)); assertTrue(f.startup.permitsMutation(KOMECampaignHealthEntity.projection(f.rider)));
        }
    }
    @Test public void terminalMountSuppressesStalePrepareAndNeverResurrects() throws Exception {
        try(Fixture f=new Fixture()) {
            f.prepare(Component.MOUNT); KOMEOrdinaryBattleRecoveryService.terminal(f.data,MOUNT_ALIAS,Disposition.DESTROYED,30,"Destroyed");
            assertEquals(TERMINAL,f.run(Component.MOUNT)); assertEquals(3.25F,f.mount.getHealth(),0);
            assertEquals(GrantState.TERMINAL,f.grant(Component.MOUNT).state); assertEquals(APPLIED,f.run(Component.RIDER));
            assertEquals(Stage.COMPLETE,f.episode().intent.stage);
        }
    }
    @Test public void destroyedRiderGetsNoRecovery() throws Exception {
        try(Fixture f=new Fixture()) { KOMEOrdinaryBattleRecoveryService.terminal(f.data,RIDER_ALIAS,Disposition.DESTROYED,30,"Destroyed"); assertEquals(TERMINAL,f.run(Component.RIDER)); assertEquals(3.25F,f.rider.getHealth(),0); }
    }
    @Test public void unrelatedReplacementMountNeverReceivesRecovery() throws Exception {
        try(Fixture f=new Fixture()) {
            Living unrelated=new Living(f.access.world); unrelated.setHealth(2F); f.access.world.loadedEntityList.add(unrelated);
            assertEquals(QUARANTINED,KOMERecoveryExecutor.execute(f.data,"CF1","CF1:E1",Component.MOUNT,MOUNT,unrelated));
            assertEquals(2F,unrelated.getHealth(),0); assertEquals(0,f.manager.view().lastSequence);
        }
    }
    @Test public void wrongEpisodeAndUnopposedOrIncompleteCoverageCannotExecute() throws Exception {
        try(Fixture f=new Fixture()) {
            assertEquals(INELIGIBLE,KOMERecoveryExecutor.execute(f.data,"CF1","CF1:E2",Component.RIDER,RIDER,f.rider));
            Episode e=f.episode(); f.episode(new Episode(e.conflictId,e.tileId,e.episodeId,e.startedAt,e.endedAt,Coverage.COMPLETE,false,e.participants,e.intent,Collections.emptyMap()));
            assertEquals(INELIGIBLE,f.run(Component.RIDER)); assertEquals(0,f.manager.view().lastSequence);
        }
        try(Fixture f=new Fixture()) {
            Episode e=f.episode(); f.episode(new Episode(e.conflictId,e.tileId,e.episodeId,e.startedAt,e.endedAt,Coverage.LEGACY_INCOMPLETE,true,e.participants,e.intent,Collections.emptyMap()));
            assertEquals(INELIGIBLE,f.run(Component.RIDER)); assertEquals(0,f.manager.view().lastSequence);
        }
    }
    @Test public void ordinaryHireAndEmergencyDefenseCannotExecuteCampaignGrant() throws Exception {
        for(KOMEHiredUnitClass kind:KOMEHiredUnitClass.values()) if(kind!=KOMEHiredUnitClass.CAMPAIGN) try(Fixture f=new Fixture()) {
            f.unit.assignPersistedUnitClass(kind); assertEquals(UNAVAILABLE,f.run(Component.RIDER)); assertEquals(0,f.manager.view().lastSequence);
        }
    }
    @Test public void formalRetreatSurvivorQualifies() throws Exception {
        try(Fixture f=new Fixture()) { assertEquals(Disposition.FORMAL_RETREAT,f.episode().participants.get(RIDER).disposition); assertEquals(APPLIED,f.run(Component.RIDER)); }
    }
    @Test public void blockedSessionAndStoppingNeverSchedule() throws Exception {
        try(Fixture f=new Fixture()) {
            f.startup.block(KOMERecoveryStartup.State.BLOCKED_MISSING,"Missing WAL"); assertEquals(0,KOMERecoveryExecutor.processCampaignTick(f.data));
            f.startup.stop(); assertEquals(0,KOMERecoveryExecutor.processCampaignTick(f.data)); assertEquals(3.25F,f.rider.getHealth(),0);
        }
    }
    @Test public void contradictoryExactRevisionAndUnfencedNewerRevisionQuarantineWithoutEffects() throws Exception {
        for(long revision:new long[]{5,6}) try(Fixture f=new Fixture()) {
            f.prepare(Component.RIDER); f.rider.observeHooks=false; f.rider.setHealth(2F);
            KOMECampaignHealthEntity.install(f.rider,new KOMECampaignHealthProjection(RIDER,Component.RIDER,RIDER_ALIAS,
                2F,revision,UUID.randomUUID(),false,null),f.unit,false);
            assertEquals(QUARANTINED,f.run(Component.RIDER)); assertEquals(2F,f.rider.getHealth(),0);
            assertEquals(GrantState.PENDING,f.grant(Component.RIDER).state); assertEquals(2,f.manager.view().lastSequence);
        }
    }
    @Test public void normalDamageAfterRecoveryRetainsFenceAcrossRestart() throws Exception {
        try(Fixture f=new Fixture()) {
            assertEquals(APPLIED,f.run(Component.RIDER)); KOMECampaignHealthProjection recovered=KOMECampaignHealthEntity.projection(f.rider);
            f.rider.setHealth(2.125F); KOMECampaignHealthProjection damaged=KOMECampaignHealthEntity.projection(f.rider);
            assertEquals(6,damaged.revision); assertEquals(recovered.fence,damaged.fence);
            NBTTagCompound saved=f.section(); f.restart(saved);
            assertEquals(RECONCILED,f.run(Component.RIDER)); assertEquals(2.125F,f.rider.getHealth(),0);
            assertEquals(2,f.rider.recoveryWrites); assertEquals(damaged.write(),KOMECampaignHealthEntity.projection(f.rider).write());
        }
    }
    @Test public void adoptionLostBeforeOrdinarySaveReprojectsSameRWithoutAddingAgain() throws Exception {
        try(Fixture f=new Fixture()) {
            NBTTagCompound before=f.section(); assertEquals(APPLIED,f.run(Component.RIDER));
            Prepare p=(Prepare)f.manager.view().preparations.values().iterator().next().record;
            f.startup.stop(); f.unit.healthAuthorityData=null; f.unit.survivingHealth=before.getCompoundTag("SavedHealth");
            Living loaded=new Living(f.access.world); alias(loaded,RIDER_ALIAS); loaded.setHealth(3.25F);
            KOMECampaignHealthEntity.install(loaded,KOMECampaignHealthProjection.read(f.unit.survivingHealth),f.unit,false);
            f.access.world.loadedEntityList.remove(f.rider); f.access.world.loadedEntityList.add(loaded); f.start(before);
            assertEquals(APPLIED,KOMERecoveryExecutor.execute(f.data,"CF1","CF1:E1",Component.RIDER,RIDER,loaded));
            assertEquals(8.25F,loaded.getHealth(),0); assertEquals(p.recoveryTransitionId,KOMECampaignHealthEntity.projection(loaded).transition);
            assertEquals(2,f.manager.view().lastSequence);
        }
    }
    @Test public void schedulerIsBoundedAndMultipleEpisodesCarryAncestryWithoutGlobalScan() throws Exception {
        try(Fixture f=new Fixture()) {
            Episode e=f.episode(); Map<String,Episode> episodes=new TreeMap<String,Episode>();
            for(int i=1;i<=6;i++) {
                String id="CF1:E"+i; Intent old=e.intent;
                Intent intent=new Intent(e.conflictId,id,old.expectedRevision,old.expectedState,old.kind,old.expectedOwner,old.winner,old.recoveryAmount,old.stage,old.closedAt);
                episodes.put(id,new Episode(e.conflictId,e.tileId,id,e.startedAt,e.endedAt,e.coverage,true,e.participants,intent,e.grants));
            }
            f.data.getOrdinaryBattleRecovery().replaceFrom(KOMEOrdinaryBattleRecoveryRegistry.restore(context().targets,episodes));
            assertEquals(8,KOMERecoveryExecutor.processCampaignTick(f.data)); assertEquals(8,f.manager.view().preparations.size());
            assertEquals(4,KOMERecoveryExecutor.processCampaignTick(f.data)); assertEquals(12,f.manager.view().preparations.size());
            assertEquals(0,KOMERecoveryExecutor.processCampaignTick(f.data));
            assertEquals(10F,f.rider.getHealth(),0); assertEquals(10,KOMECampaignHealthEntity.projection(f.rider).revision);
            for(Episode complete:f.data.getOrdinaryBattleRecovery().episodes().values()) assertEquals(Stage.COMPLETE,complete.intent.stage);
            f.restart(f.section()); assertEquals(0,KOMERecoveryExecutor.processCampaignTick(f.data));
        }
    }
    @Test public void verifiedLineageBeforePrepareExecutesOriginalLogicalMountGrantAfterRestart() throws Exception {
        try(Fixture f=new Fixture()) {
            f.startup.append(context()); UUID next=UUID.randomUUID(); KOMECampaignHealthProjection old=KOMECampaignHealthEntity.projection(f.mount);
            KOMERecoveryStartup.stageAlias(old,next);
            KOMEOrdinaryBattleRecoveryService.reconstructed(f.data,RIDER_ALIAS,RIDER_ALIAS,MOUNT_ALIAS,next);
            KOMECampaignHealth.reconstructed(f.unit,RIDER_ALIAS,RIDER_ALIAS,MOUNT_ALIAS,next);
            Living replacement=new Living(f.access.world); alias(replacement,next); replacement.setHealth(old.hp);
            KOMECampaignHealthEntity.install(replacement,old.rekey(MOUNT_ALIAS,next),f.unit,false);
            f.access.world.loadedEntityList.remove(f.mount); f.access.world.loadedEntityList.add(replacement);
            f.restart(f.section()); f.startup.runtime.observe(replacement);
            assertEquals(APPLIED,KOMERecoveryExecutor.execute(f.data,"CF1","CF1:E1",Component.MOUNT,MOUNT,replacement));
            assertEquals(8.25F,replacement.getHealth(),0); assertEquals(MOUNT,KOMECampaignHealthEntity.projection(replacement).target);
            assertEquals(1,f.manager.view().lineages.size()); assertEquals(1,f.manager.view().preparations.size());
        }
    }
    @Test public void publicationStagesAndPendingGrantPreventPrematureClosure() throws Exception {
        try(Fixture f=new Fixture()) {
            Episode e=f.episode(); Intent i=e.intent;
            f.episode(e.update(e.endedAt,true,e.participants,new Intent(i.conflictId,i.episodeId,i.expectedRevision,i.expectedState,i.kind,
                i.expectedOwner,i.winner,i.recoveryAmount,Stage.PUBLICATION_STARTED,null),e.grants));
            assertEquals(INELIGIBLE,f.run(Component.RIDER)); assertEquals(0,f.manager.view().lastSequence);
            KOMERecoveryExecutor.progress(f.data,"CF1","CF1:E1"); assertEquals(Stage.PUBLICATION_STARTED,f.episode().intent.stage);
        }
    }
    @Test public void emergencyDefenseOrUnrelatedCompanyWithoutExactParticipantAuthorityCannotExecute() throws Exception {
        try(Fixture f=new Fixture()) {
            Living unrelated=new Living(f.access.world); unrelated.setHealth(1F); f.access.world.loadedEntityList.add(unrelated);
            assertEquals(INELIGIBLE,KOMERecoveryExecutor.execute(f.data,"CF1","CF1:E1",Component.RIDER,unrelated.getUniqueID(),unrelated));
            assertEquals(1F,unrelated.getHealth(),0); assertEquals(0,f.manager.view().lastSequence);
        }
    }
    @Test public void nativeServerGuardRejectsBranchesThenAllowsDamageAtRPlusOne() throws Exception {
        try(kome.common.KOMETestServerSession server=new kome.common.KOMETestServerSession(); Fixture f=new Fixture()) {
            net.minecraft.world.WorldServer world=KOMEAccessFixture.allocate(net.minecraft.world.WorldServer.class);
            Field provider=net.minecraft.world.World.class.getDeclaredField("provider"); provider.setAccessible(true);
            provider.set(world,new net.minecraft.world.WorldProviderSurface()); world.mapStorage=f.access.world.mapStorage;
            world.loadedEntityList=f.access.world.loadedEntityList;
            net.minecraft.util.IntHashMap ids=new net.minecraft.util.IntHashMap(); ids.addKey(f.rider.getEntityId(),f.rider); ids.addKey(f.mount.getEntityId(),f.mount);
            Field idMap=net.minecraft.world.WorldServer.class.getDeclaredField("entityIdMap"); idMap.setAccessible(true); idMap.set(world,ids);
            server.server.worldServers=new net.minecraft.world.WorldServer[]{world}; f.rider.worldObj=world; f.mount.worldObj=world;
            f.prepare(Component.RIDER);
            assertThrows(IllegalArgumentException.class,()->f.rider.setHealth(2F));
            assertThrows(IllegalArgumentException.class,()->f.rider.setHealth(4F));
            assertEquals(3.25F,f.rider.getHealth(),0); assertEquals(APPLIED,f.run(Component.RIDER));
            assertEquals(5,KOMECampaignHealthEntity.projection(f.rider).revision);
            f.rider.setHealth(1.25F); assertEquals(6,KOMECampaignHealthEntity.projection(f.rider).revision);
            assertEquals(1.25F,f.rider.getHealth(),0); assertEquals(2,f.manager.view().lastSequence);
        }
    }
    @Test public void entityEvidenceNewerThanWorldSaveRepairsAuthorityBeforeCallbacksWithoutHpEffects() throws Exception {
        try(Fixture f=new Fixture()) {
            KOMERecoveryReservations.Result r=f.prepare(Component.RIDER); NBTTagCompound pending=f.section();
            KOMECampaignHealthProjection evidence=new KOMECampaignHealthProjection(RIDER,Component.RIDER,RIDER_ALIAS,1.5F,6,UUID.randomUUID(),false,f.fence(r));
            f.startup.stop(); f.manager=new KOMERecoveryJournalManager(f.directory,new Object(),KOMERecoveryJournalIO.FILES);
            f.startup=new KOMERecoveryStartup(net.minecraft.server.MinecraftServer.getServer()); f.singleton.set(null,f.startup);
            File directory=new File(f.directory,"region"); assertTrue(directory.mkdir());
            NBTTagCompound entity=new NBTTagCompound(); entity.setLong("UUIDMost",RIDER_ALIAS.getMostSignificantBits());
            entity.setLong("UUIDLeast",RIDER_ALIAS.getLeastSignificantBits()); entity.setFloat("HealF",evidence.hp);
            entity.setTag(KOMECampaignHealthProjection.ENTITY_TAG,evidence.write());
            net.minecraft.nbt.NBTTagList entities=new net.minecraft.nbt.NBTTagList(); entities.appendTag(entity);
            NBTTagCompound chunk=new NBTTagCompound(); chunk.setTag("Entities",entities);
            net.minecraft.world.chunk.storage.RegionFile region=new net.minecraft.world.chunk.storage.RegionFile(new File(directory,"r.0.0.mca"));
            try(DataOutputStream out=region.getChunkDataOutputStream(0,0)) { net.minecraft.nbt.CompressedStreamTools.write(chunk,out); }
            region.close(); f.startup.initialize(f.manager,pending,f.data);
            assertEquals(GrantState.APPLIED,f.grant(Component.RIDER).state); assertEquals(3.25F,f.rider.getHealth(),0);
            assertEquals(evidence.write(),KOMECampaignHealthProjection.read(f.unit.survivingHealth).write());
            KOMECampaignHealth.observe(f.unit,f.rider); assertEquals(1.5F,f.rider.getHealth(),0);
            assertEquals(evidence.write(),KOMECampaignHealthEntity.projection(f.rider).write()); assertEquals(2,f.manager.view().lastSequence);
        }
    }
    @Test public void oldGeometrySnapshotAcceptsVerifiedRecoveryFenceDescendant() throws Exception {
        try(Fixture f=new Fixture()) {
            f.run(Component.RIDER); KOMECampaignHealth.observe(f.unit,f.rider);
            NBTTagCompound geometry=new NBTTagCompound(); f.rider.writeToNBT(geometry); KOMECampaignHealthEntity.endWrite(f.rider,geometry);
            Episode e=f.episode(); Intent old=e.intent;
            Intent intent=new Intent("CF1","CF1:E2",old.expectedRevision,old.expectedState,old.kind,old.expectedOwner,old.winner,5,Stage.RECOVERY_PENDING,old.closedAt);
            Map<String,Grant> grants=new TreeMap<String,Grant>();
            for(Grant g:e.grants.values()) grants.put(g.key(),new Grant(g.participantId,g.targetId,g.component,g.amount,GrantState.PENDING,null,""));
            Map<String,Episode> episodes=new TreeMap<String,Episode>(f.data.getOrdinaryBattleRecovery().episodes());
            episodes.put("CF1:E2",new Episode("CF1",e.tileId,"CF1:E2",e.startedAt,e.endedAt,e.coverage,true,e.participants,intent,grants));
            f.data.getOrdinaryBattleRecovery().replaceFrom(KOMEOrdinaryBattleRecoveryRegistry.restore(context().targets,episodes));
            assertEquals(APPLIED,KOMERecoveryExecutor.execute(f.data,"CF1","CF1:E2",Component.RIDER,RIDER,f.rider));
            NBTTagCompound reconciled=KOMECampaignHealth.movementSnapshot(f.unit,geometry);
            assertEquals(10F,reconciled.getFloat("HealF"),0); assertEquals(6,reconciled.getCompoundTag(KOMECampaignHealthProjection.ENTITY_TAG).getLong("HealthRevision"));
        }
    }
}
