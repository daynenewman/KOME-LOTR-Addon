package kome.common.data;

import java.io.File;
import java.util.*;
import kome.common.KOMEAccessFixture;
import kome.common.config.*;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.config.Configuration;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;

public class KOMECampaignHealingPolicyTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Rule public KOMETileTestResources geometry = new KOMETileTestResources();
    private KOMEConfigRegistry.ValidatedConfig previous;
    private static final KOMEConfigRegistry.RuntimeActivity IDLE = new KOMEConfigRegistry.RuntimeActivity() {
        public boolean isDailyTransactionInProgress(){return false;}
        public boolean isActiveSiegeInProgress(){return false;}
        public Collection<String> getActiveSiegeLockedConfigKeys(){return Collections.emptyList();}
    };
    @Before public void defaults() throws Exception {
        java.lang.reflect.Field home=cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome"); home.setAccessible(true); home.set(null,new File(".").getAbsoluteFile());
        KOMEConfigRegistry.onServerStop();
        if(!KOMEConfigRegistry.isReady()) KOMEConfigRegistry.load(new File(temporary.getRoot(),"initial.cfg"));
        previous=KOMEConfigRegistry.currentValidated(); config(false,false);
    }
    @After public void restore(){KOMEConfigRegistry.applyValidated(previous,IDLE);}
    void config(boolean battle,boolean lifespan) {
        File file=new File(temporary.getRoot(),"healing.cfg"); Configuration c=new Configuration(file); c.load();
        c.get("battle",KOMEConfigRegistry.DISABLE_NORMAL_HEALING_IN_BATTLE,"false").set(Boolean.toString(battle));
        c.get("battle",KOMEConfigRegistry.DISABLE_NORMAL_HEALING_DURING_CAMPAIGN_COMMITMENT,"false").set(Boolean.toString(lifespan));
        c.save(); KOMEConfigRegistry.load(file);
    }
    final class Fixture {
        final KOMEAccessFixture access=new KOMEAccessFixture();
        final KOMEWorldData data=access.data;
        final EntityPig rider,defender,mount;
        final KOMEHiredUnitRecord unit,defending;
        Fixture() throws Exception {
            Class<?> nativeClass=new kome.core.KOMECampaignHealthTransformerTest().executable(false);
            rider=(EntityPig)nativeClass.getConstructor(net.minecraft.world.World.class).newInstance(access.world);
            defender=(EntityPig)nativeClass.getConstructor(net.minecraft.world.World.class).newInstance(access.world);
            mount=(EntityPig)nativeClass.getConstructor(net.minecraft.world.World.class).newInstance(access.world);
            rider.setHealth(3.25F); defender.setHealth(3.25F); mount.setHealth(3.25F);
            unit=record(rider,"C1"); defending=record(defender,"C2");
            rider.ridingEntity=mount; mount.riddenByEntity=rider; unit.mounted=true;
            KOMECampaignHealth.observe(unit,rider); KOMECampaignHealth.observe(defending,defender);
            NBTTagCompound snapshot=new NBTTagCompound(), savedMount=new NBTTagCompound(); rider.writeToNBT(snapshot); mount.writeToNBT(savedMount);
            snapshot.setTag("Riding",savedMount); unit.stationedEntityData=snapshot;
            access.world.loadedEntityList.add(rider); access.world.loadedEntityList.add(defender); access.world.loadedEntityList.add(mount);
        }
        KOMEHiredUnitRecord record(EntityPig entity,String company) {
            KOMEHiredUnitRecord r=new KOMEHiredUnitRecord(); r.entity=entity.getUniqueID(); r.owner=UUID.randomUUID(); r.currentTile="T101"; r.companyId=company;
            KOMEHiredUnitClassification.assignForCampaignWorkflow(r); data.hiredUnits.put(r.entity,r); return r;
        }
        KOMEConflictRecord battle() {
            KOMEConflictRecord c=data.getConflictService().start("T101",KOMEConflictRecord.State.ORDINARY,ExpectedConflict.absent(),Collections.emptyList(),new Context(1,"test","start")).record;
            c=data.getConflictService().checkpointCombat("T101",ExpectedConflict.at(c.getConflictId(),c.getRevision()),
                new KOMEConflictRecord.CombatEpisode(1,c.getConflictId()+":E1",KOMEConflictRecord.EpisodeState.ACTIVE,10L,null),
                new KOMEConflictRecord.TimerSlot(KOMEConflictRecord.TimerStatus.RUNNING,60000,0,10L,c.getConflictId()+":E1"),
                KOMEConflictRecord.TimerSlot.unstarted(),new Context(10,"test","battle")).record;
            KOMEOrdinaryBattleRecoveryRegistry r=data.getOrdinaryBattleRecovery(); r.begin(c,Coverage.COMPLETE);
            r.enroll(c,unit.entity,"C1","gondor",10,mount.getUniqueID(),KOMECampaignHealthEntity.projection(rider).target,KOMECampaignHealthEntity.projection(mount).target);
            r.enroll(c,defending.entity,"C2","mordor",10,null,KOMECampaignHealthEntity.projection(defender).target,null); return c;
        }
    }
    @Test public void configurationMatrixAppliesIndependentScopes() throws Exception {
        for(boolean battle:new boolean[]{false,true}) for(boolean lifespan:new boolean[]{false,true}) {
            config(false,false); Fixture f=new Fixture(); config(battle,lifespan);
            assertEquals(lifespan,KOMECampaignHealingPolicy.blocksNormalHealing(f.data,f.unit.entity));
            f.battle(); assertEquals(battle||lifespan,KOMECampaignHealingPolicy.blocksNormalHealing(f.data,f.unit.entity));
            assertEquals(battle||lifespan,KOMECampaignHealingPolicy.blocksNormalHealing(f.data,f.defending.entity));
        }
    }
    @Test public void directSetterAndNativeHealAreCancelledWithoutAnyRevisionChange() throws Exception {
        Fixture f=new Fixture(); f.battle(); config(true,false); KOMECampaignHealthProjection old=KOMECampaignHealthEntity.projection(f.rider);
        f.rider.setHealth(8F); f.rider.heal(2F);
        assertEquals(3.25F,f.rider.getHealth(),0); assertEquals(old.write(),KOMECampaignHealthEntity.projection(f.rider).write());
        assertEquals(old.write(),KOMECampaignHealthProjection.read(f.unit.survivingHealth).write());
    }
    @Test public void forgeEventCancellationUsesSamePolicyWithoutARevision() throws Exception {
        Fixture f=new Fixture(); f.battle(); config(true,false);
        // Forge's launch transformer supplies cancelability in-game; unit tests run without it.
        net.minecraftforge.event.entity.living.LivingHealEvent event=new net.minecraftforge.event.entity.living.LivingHealEvent(f.rider,2F) {
            @Override public boolean isCancelable(){return true;}
        };
        new KOMEEvents().onCampaignHealing(event); assertTrue(event.isCanceled());
        assertEquals(0,KOMECampaignHealthEntity.projection(f.rider).revision);
        config(false,false); event=new net.minecraftforge.event.entity.living.LivingHealEvent(f.rider,2F) {
            @Override public boolean isCancelable(){return true;}
        };
        new KOMEEvents().onCampaignHealing(event); assertFalse(event.isCanceled());
    }
    @Test public void outsideBattleAndAfterFormalRetreatBattleLockAllowsHealing() throws Exception {
        Fixture f=new Fixture(); config(true,false); f.rider.heal(1F); assertEquals(4.25F,f.rider.getHealth(),0);
        KOMEConflictRecord c=f.battle(); f.data.getOrdinaryBattleRecovery().exit(c,Collections.singleton(f.unit.entity),Disposition.FORMAL_RETREAT,15,"Retreat");
        assertFalse(KOMECampaignHealingPolicy.activelyParticipatingInBattle(f.data,f.unit)); f.rider.heal(1F); assertEquals(5.25F,f.rider.getHealth(),0);
        config(false,true); f.rider.heal(1F); assertEquals(5.25F,f.rider.getHealth(),0);
    }
    @Test public void closedEpisodeAllowsNormalHealingWithOnlyBattleLock() throws Exception {
        Fixture f=new Fixture(); KOMEConflictRecord c=f.battle(); config(true,false);
        f.data.getOrdinaryBattleRecovery().end(c,20); f.rider.heal(1F); assertEquals(4.25F,f.rider.getHealth(),0);
    }
    @Test public void stationedMovingWaitingExhaustedAndRetreatingRemainCommitted() throws Exception {
        Fixture f=new Fixture(); config(false,true);
        KOMEArmyCompany company=new KOMEArmyCompany(); company.id=f.unit.companyId; company.units.add(f.unit.entity); f.data.armyCompanies.put(company.id,company);
        KOMEArmyMovementOrder order=new KOMEArmyMovementOrder(); order.id="campaign-route"; order.companyId=company.id; order.units.add(f.unit.entity);
        for(String status:new String[]{KOMEArmyCompany.STATIONED,KOMEArmyMovementOrder.MOVING,KOMEArmyMovementOrder.WAITING_NEXT_STEP,KOMEArmyMovementOrder.STOPPED,KOMEArmyMovementOrder.RETREATING,KOMEArmyMovementOrder.CONFLICT_HELD}) {
            company.status=status; order.status=status; company.movementAllowance=0; order.dailyStepsRemaining=0;
            f.unit.movementOrderId=KOMEArmyCompany.STATIONED.equals(status)?"":order.id;
            f.rider.heal(1F); assertEquals(3.25F,f.rider.getHealth(),0);
            assertTrue(KOMEHiredUnitClassification.isWithinCommittedCampaignLifespan(f.data,f.unit));
        }
    }
    @Test public void unloadAndOfflineOwnerDoNotEndEitherStrategicScope() throws Exception {
        Fixture f=new Fixture(); f.battle(); config(true,true); f.access.world.loadedEntityList.clear(); f.unit.healthObservedEntity=null;
        assertTrue(KOMECampaignHealingPolicy.blocksNormalHealing(f.data,f.unit.entity));
        assertTrue(KOMECampaignHealingPolicy.blocksNormalHealing(f.data,f.mount.getUniqueID()));
        f.rider.heal(2F); assertEquals(3.25F,f.rider.getHealth(),0);
    }
    @Test public void canonicalReturnAndRemovalEndCommittedScope() throws Exception {
        Fixture f=new Fixture(); config(false,true); f.unit.populationReturned=true;
        assertFalse(KOMEHiredUnitClassification.isWithinCommittedCampaignLifespan(f.data,f.unit)); f.rider.heal(1F); assertEquals(4.25F,f.rider.getHealth(),0);
        f.unit.populationReturned=false; f.data.hiredUnits.remove(f.unit.entity); f.rider.heal(1F); assertEquals(5.25F,f.rider.getHealth(),0);
        assertFalse(KOMECampaignHealingPolicy.blocksNormalHealing(f.data,f.mount.getUniqueID()));
    }
    @Test public void ordinaryAndEmergencyDefenseEntitiesRemainUnaffected() throws Exception {
        Fixture f=new Fixture(); config(true,true); f.unit.assignPersistedUnitClass(KOMEHiredUnitClass.ORDINARY); f.rider.heal(1F); assertEquals(4.25F,f.rider.getHealth(),0);
        EntityPig emergency=new EntityPig(f.access.world); emergency.setHealth(2F); emergency.heal(1F);
        assertFalse(KOMECampaignHealingPolicy.blocksNormalHealing(f.data,emergency.getUniqueID())); assertEquals(3F,emergency.getHealth(),0);
    }
    @Test public void verifiedDismountedMountStaysCoveredButObservedReplacementDoesNotInherit() throws Exception {
        Fixture f=new Fixture(); f.battle(); config(true,true); f.rider.ridingEntity=null; f.mount.riddenByEntity=null;
        f.mount.heal(2F); assertEquals(3.25F,f.mount.getHealth(),0);
        config(false,false); EntityPig unrelated=(EntityPig)new kome.core.KOMECampaignHealthTransformerTest().executable(false).getConstructor(net.minecraft.world.World.class).newInstance(f.access.world);
        unrelated.setHealth(2F); f.rider.ridingEntity=unrelated; unrelated.riddenByEntity=f.rider;
        KOMECampaignHealth.observe(f.unit,f.rider); config(true,true); unrelated.heal(1F); assertEquals(3F,unrelated.getHealth(),0);
        assertFalse(KOMECampaignHealingPolicy.blocksNormalHealing(f.data,unrelated.getUniqueID()));
        // Even a later geometry snapshot must not substitute an unrelated alias for durable lineage.
        NBTTagCompound replacement=new NBTTagCompound(); unrelated.writeToNBT(replacement);
        f.unit.stationedEntityData.setTag("Riding",replacement);
        assertFalse(KOMECampaignHealingPolicy.blocksNormalHealing(f.data,unrelated.getUniqueID()));
    }
    @Test public void preBattleCanonicalMountSnapshotIsCoveredWithoutBattleHistory() throws Exception {
        Fixture f=new Fixture(); config(false,true); assertTrue(f.data.getOrdinaryBattleRecovery().episodes().isEmpty());
        f.mount.heal(1F); assertEquals(3.25F,f.mount.getHealth(),0);
    }
    @Test public void terminalMountCannotBeResurrectedAndPolicyDoesNotReclassifyIt() throws Exception {
        Fixture f=new Fixture(); f.battle(); KOMEOrdinaryBattleRecoveryService.terminal(f.data,f.mount.getUniqueID(),Disposition.DEAD,20,"Death");
        KOMECampaignHealthEntity.terminal(f.mount); config(true,true);
        assertFalse(KOMECampaignHealingPolicy.blocksNormalHealing(f.data,f.mount.getUniqueID()));
        assertThrows(IllegalArgumentException.class,()->f.mount.setHealth(9F)); assertEquals(3.25F,f.mount.getHealth(),0);
    }
    @Test public void deathBeforeSealedContextReusesCanonicalTerminalTransitionUnderBothLocks() throws Exception {
        Fixture f=new Fixture(); f.battle(); config(true,true); f.rider.setHealth(0F);
        KOMEOrdinaryBattleRecoveryService.terminal(f.data,f.unit.entity,Disposition.DEAD,20,"Death before recovery context");
        KOMECampaignHealthProjection canonical=KOMECampaignHealthProjection.read(f.unit.survivingHealth);
        KOMECampaignHealthEntity.terminal(f.rider);
        assertEquals(canonical.write(),KOMECampaignHealthEntity.projection(f.rider).write());
        assertEquals(2,canonical.revision); assertTrue(canonical.terminal);
        assertFalse(KOMEHiredUnitClassification.isWithinCommittedCampaignLifespan(f.data,f.unit));
        assertThrows(IllegalArgumentException.class,()->f.rider.setHealth(5F)); assertEquals(0F,f.rider.getHealth(),0);
    }
    @Test public void damageSameRevisionAndAuthoritativeCorrectionRemainPossible() throws Exception {
        Fixture f=new Fixture(); config(true,true); f.rider.setHealth(2F); assertEquals(1,KOMECampaignHealthEntity.projection(f.rider).revision);
        KOMECampaignHealthProjection old=KOMECampaignHealthEntity.projection(f.rider);
        KOMECampaignHealthEntity.install(f.rider,old,f.unit,true); assertEquals(old.write(),KOMECampaignHealthEntity.projection(f.rider).write());
        KOMECampaignHealthProjection newer=old.change(7F,false); f.unit.survivingHealth=newer.write();
        KOMECampaignHealth.reconcileLoaded(f.unit,f.rider); assertEquals(7F,f.rider.getHealth(),0);
        assertEquals(newer.write(),KOMECampaignHealthEntity.projection(f.rider).write());
    }
    @Test public void unversionedCampaignRiderCannotBypassDirectSetter() throws Exception {
        Fixture f=new Fixture(); EntityPig fresh=(EntityPig)new kome.core.KOMECampaignHealthTransformerTest().executable(false).getConstructor(net.minecraft.world.World.class).newInstance(f.access.world);
        fresh.setHealth(2F); f.record(fresh,"C3"); config(false,true); fresh.setHealth(7F); assertEquals(2F,fresh.getHealth(),0);
    }
    @Test public void unknownNestedPositiveSetterCannotBorrowReconciliationAuthorization() throws Exception {
        Fixture f=new Fixture(); config(false,true); KOMECampaignHealthEntity s=KOMECampaignHealthEntity.state(f.rider,false); s.projecting=true;
        try { f.rider.setHealth(9F); assertEquals(3.25F,f.rider.getHealth(),0); } finally { s.projecting=false; }
    }
    @Test public void deserializationAndVerifiedRekeyPreserveTheExistingTransitionUnderBothLocks() throws Exception {
        Fixture f=new Fixture(); config(true,true);
        KOMECampaignHealthProjection old=KOMECampaignHealthEntity.projection(f.rider);
        // The test's transformed subclass has no EntityList ID for writeToNBTOptional.
        NBTTagCompound source=(NBTTagCompound)f.unit.stationedEntityData.copy();
        KOMECampaignHealthEntity.endWrite(f.rider,source);
        KOMECampaignHealthEntity.endWrite(f.mount,source.getCompoundTag("Riding"));
        f.unit.healthObservedEntity=null; f.unit.healthObservedMount=null;
        NBTTagCompound replacement=KOMECampaignHealth.movementSnapshot(f.unit,source);
        KOMECampaignHealth.prepareReconstruction(replacement);
        KOMECampaignHealthProjection rider=KOMECampaignHealthProjection.read(replacement.getCompoundTag(KOMECampaignHealthProjection.ENTITY_TAG));
        KOMECampaignHealthProjection mount=KOMECampaignHealthProjection.read(replacement.getCompoundTag("Riding").getCompoundTag(KOMECampaignHealthProjection.ENTITY_TAG));
        f.data.hiredUnits.remove(f.unit.entity); f.unit.entity=rider.alias; f.data.hiredUnits.put(f.unit.entity,f.unit);
        KOMECampaignHealth.reconstructed(f.unit,old.alias,rider.alias,f.mount.getUniqueID(),mount.alias);
        f.unit.stationedEntityData=replacement;
        EntityPig loaded=(EntityPig)new kome.core.KOMECampaignHealthTransformerTest().executable(false).getConstructor(net.minecraft.world.World.class).newInstance(f.access.world);
        loaded.setHealth(1F);
        KOMECampaignHealthEntity.beginRead(loaded,replacement); loaded.readFromNBT(replacement); KOMECampaignHealthEntity.endRead(loaded,replacement);
        assertEquals(3.25F,loaded.getHealth(),0); assertEquals(old.revision,KOMECampaignHealthEntity.projection(loaded).revision);
        assertEquals(old.transition,KOMECampaignHealthEntity.projection(loaded).transition);
        assertEquals(old.target,KOMECampaignHealthEntity.projection(loaded).target);
        assertTrue(KOMECampaignHealingPolicy.blocksNormalHealing(f.data,mount.alias));
        loaded.heal(2F); assertEquals(3.25F,loaded.getHealth(),0);
    }
    @Test public void maxHpClampingIsNotAnAdditionalTrueHealingTransition() throws Exception {
        Fixture f=new Fixture(); f.rider.setHealth(f.rider.getMaxHealth()); config(true,true);
        KOMECampaignHealthProjection old=KOMECampaignHealthEntity.projection(f.rider);
        f.rider.setHealth(f.rider.getMaxHealth()+3F);
        assertEquals(old.write(),KOMECampaignHealthEntity.projection(f.rider).write());
        assertEquals(f.rider.getMaxHealth(),f.rider.getHealth(),0);
    }
    @Test public void battleParticipationHasAnExplicitFutureLifecycleAuthoritySeam() throws Exception {
        Fixture f=new Fixture(); f.battle();
        assertFalse(KOMECampaignHealingPolicy.activelyParticipatingInBattle(f.data,f.unit,(d,u,c)->false));
        assertTrue(KOMECampaignHealingPolicy.activelyParticipatingInBattle(f.data,f.unit,(d,u,c)->true));
    }
}
