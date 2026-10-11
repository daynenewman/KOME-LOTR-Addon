package kome.common.data;

import kome.common.KOMEAccessFixture;
import kome.common.command.KOMECommandTroops;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.util.*;
import static org.junit.Assert.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.Component;
import static kome.common.data.KOMERecoveryJournalTestSupport.*;

public class KOMECampaignHealthDiagnosticsTest {
    @Rule public final TemporaryFolder temporary=new TemporaryFolder();
    private KOMEAccessFixture f;
    private KOMEHiredUnitRecord record;
    private EntityPig npc;
    private KOMECampaignHealthProjection health;
    @Before public void setup() throws Exception {
        f=new KOMEAccessFixture();npc=new EntityPig(f.world);
        npc.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(20D);npc.setHealth(13F);
        record=new KOMEHiredUnitRecord();record.entity=npc.getUniqueID();record.owner=f.player.getUniqueID();
        KOMEHiredUnitClassification.assignForCampaignWorkflow(record);f.data.hiredUnits.put(record.entity,record);
        health=new KOMECampaignHealthProjection(UUID.randomUUID(),Component.RIDER,record.entity,13F,4,UUID.randomUUID(),false,null);
        record.survivingHealth=health.write();KOMECampaignHealthEntity.install(npc,health,record,false);
        record.stationedEntityData=KOMEEntitySnapshots.snapshot(record,npc);f.world.loadedEntityList.add(npc);
        health=KOMECampaignHealthEntity.projection(npc);
    }
    private String inspect(boolean loaded){
        if(!loaded)record.healthObservedEntity=null;
        return KOMECampaignHealthDiagnostics.describe(f.data,record,loaded?npc:null);
    }
    @Test public void canonicalObservedEntityIsReadWithoutScanningOtherDimensions(){
        npc.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(24D);
        assertEquals("Health: 13.0 / 24.0 | revision 4 | terminal no | recovery fence none",
            KOMECampaignHealthDiagnostics.describe(f.data,record,null));
        assertEquals(4,KOMECampaignHealthEntity.projection(npc).revision);
    }
    @Test public void loadedCommandReportsExactCanonicalHealthWithoutMutation(){
        NBTTagCompound before=(NBTTagCompound)record.survivingHealth.copy();
        new KOMECommandTroops().processCommand(f.player,new String[]{"unit",record.entity.toString()});
        assertTrue(f.player.messages.toString().contains("Health: 13.0 / 20.0 | revision 4 | terminal no | recovery fence none"));
        assertEquals(13F,npc.getHealth(),0);assertSame(health,KOMECampaignHealthEntity.projection(npc));assertEquals(before,record.survivingHealth);
    }
    @Test public void unloadedReportsPersistedHpAndNativeMaximumIncludingModifiers(){
        npc.getEntityAttribute(SharedMonsterAttributes.maxHealth).applyModifier(new AttributeModifier(UUID.randomUUID(),"test max",4D,0));
        record.stationedEntityData=KOMEEntitySnapshots.snapshot(record,npc);
        assertEquals("Health: 13.0 / 24.0 | revision 4 | terminal no | recovery fence none | persisted",inspect(false));
    }
    @Test public void terminalPersistedStateIsReportedWithoutResurrection(){
        record.survivingHealth=new KOMECampaignHealthProjection(health.target,Component.RIDER,record.entity,0F,5,UUID.randomUUID(),true,null).write();
        assertTrue(inspect(false).contains("Health: 0.0 / 20.0 | revision 5 | terminal yes"));assertEquals(13F,npc.getHealth(),0);
    }
    @Test public void missingAuthorityDoesNotInitializeHealthAndMissingMaxIsExplicit(){
        record.survivingHealth=null;assertTrue(inspect(false).startsWith("Health: unresolved"));assertNull(record.survivingHealth);
        record.survivingHealth=health.write();record.stationedEntityData=null;
        assertEquals("Health: 13.0 / unresolved | revision 4 | terminal no | recovery fence none | persisted",inspect(false));
    }
    @Test public void unobservedNativeDamageAndEqualRevisionContradictionStayUnresolved(){
        NBTTagCompound before=(NBTTagCompound)record.survivingHealth.copy();npc.setHealth(12F);
        assertTrue(inspect(true).contains("unresolved"));assertSame(health,KOMECampaignHealthEntity.projection(npc));assertEquals(before,record.survivingHealth);
        npc.setHealth(13F);
        record.survivingHealth=new KOMECampaignHealthProjection(health.target,Component.RIDER,record.entity,13F,4,UUID.randomUUID(),false,null).write();
        assertTrue(inspect(true).contains("Contradictory health state"));assertEquals(13F,npc.getHealth(),0);
    }
    @Test public void unknownFenceIsNotPresentedAsValidatedHealth(){
        record.survivingHealth=new KOMECampaignHealthProjection(health.target,Component.RIDER,record.entity,13F,4,health.transition,false,
            new KOMECampaignHealthProjection.Fence(UUID.randomUUID(),2,UUID.randomUUID())).write();
        assertTrue(inspect(false).contains("recovery fence awaits journal validation"));
    }
    @Test public void validatedConsumedFenceIsShownWithoutApplyingOrChangingGrant() throws Exception {
        java.lang.reflect.Field uuid=net.minecraft.entity.Entity.class.getDeclaredField("entityUniqueID");uuid.setAccessible(true);uuid.set(npc,RIDER_ALIAS);record.entity=RIDER_ALIAS;f.data.hiredUnits.clear();f.data.hiredUnits.put(record.entity,record);
        npc.setHealth(3.25F);health=new KOMECampaignHealthProjection(RIDER,Component.RIDER,RIDER_ALIAS,3.25F,4,new UUID(4,1),false,null);
        record.survivingHealth=health.write();KOMECampaignHealthEntity.install(npc,health,record,false);
        record.stationedEntityData=KOMEEntitySnapshots.snapshot(record,npc);
        KOMERecoveryJournalRecord.EpisodeContext context=context();
        f.data.getOrdinaryBattleRecovery().replaceFrom(KOMEOrdinaryBattleRecoveryRegistry.restore(context.targets,
            Collections.singletonMap(context.episode.episodeId,context.episode)));
        f.data.getConflictService().replaceFrom(new KOMEConflictService(2));
        KOMERecoveryJournalManager manager=new KOMERecoveryJournalManager(temporary.newFolder(),new Object(),KOMERecoveryJournalIO.FILES);
        KOMERecoveryStartup startup=new KOMERecoveryStartup(net.minecraft.server.MinecraftServer.getServer());
        try {
            startup.initialize(manager,KOMEConflictPersistence.write(f.data.getConflictService(),f.data.getJoinBattleDeploymentReceipts(),f.data.getOrdinaryBattleRecovery()),f.data);
            assertEquals(startup.diagnostic(),KOMERecoveryStartup.State.READY,startup.state());
            KOMERecoveryJournalView.Entry ctx=startup.append(context);
            KOMERecoveryJournalRecord.Prepare prepared=new KOMERecoveryJournalRecord.Prepare(
                new KOMERecoveryJournalRecord.Binding(startup.journal().journalId,ctx.sequence,ctx.hash,"CF1","CF1:E1",RIDER,Component.RIDER),
                RIDER,RIDER_ALIAS,0,4,new UUID(4,1),3.25F,null,5,new UUID(5,1),8.25F,5,20,context.intentFingerprint());
            KOMERecoveryJournalView.Entry prepare=startup.append(prepared);
            record.survivingHealth=new KOMECampaignHealthProjection(RIDER,Component.RIDER,RIDER_ALIAS,8.25F,5,prepared.recoveryTransitionId,false,
                new KOMECampaignHealthProjection.Fence(startup.journal().journalId,prepare.sequence,prepared.recoveryTransitionId)).write();
            NBTTagCompound before=(NBTTagCompound)record.survivingHealth.copy();
            assertEquals("Health: 8.25 / 20.0 | revision 5 | terminal no | recovery fence present #2 | persisted",inspect(false));
            assertEquals(before,record.survivingHealth);assertEquals(3.25F,npc.getHealth(),0);
            assertEquals(KOMEOrdinaryBattleRecoveryRegistry.GrantState.PENDING,f.data.getOrdinaryBattleRecovery().get("CF1","CF1:E1").grants.values().iterator().next().state);
        } finally {startup.stop();manager.close();}
    }
}
