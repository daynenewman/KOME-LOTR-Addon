package kome.common.data;

import java.util.*;
import kome.common.KOMEAccessFixture;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.*;
import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.*;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;
import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;

public class KOMEKnightBattlefieldServiceTest {
    @Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    static KOMEConflictRecord ok(KOMEConflictService.Result result){assertTrue(result.reason,result.isSuccess());return result.record;}
    static final class Battle implements AutoCloseable {
        final KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session();
        final long base=System.currentTimeMillis()-10000;
        final KOMEKnightBattlefieldService.Presence old=KOMEKnightBattlefieldService.presence;
        final KOMEKnightCommission a;
        Battle()throws Exception{
            s.f.world.provider.dimensionId=KOMETileTestResources.dimension();s.f.player.dimension=s.f.world.provider.dimensionId;
            s.f.player.posX=KOMETileTestResources.x();s.f.player.posZ=KOMETileTestResources.z();s.f.player.posY=65;
            KOMEProgressionNpcRef ref=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Marshal","rohan",s.f.player.dimension,s.f.player.posX,65,s.f.player.posZ);s.p.getSerfKnightProgression().setLiege(ref);
            a=new KOMEKnightCommission(Type.BORDER_INCURSION,ref);KOMEKnightFieldService.profile(a,2);a.complication=Complication.NONE;a.stage=Stage.ACTIVE;a.acceptedMillis=base;a.x=s.f.player.posX;a.z=s.f.player.posZ;a.y=65;a.enemyFaction="mordor";s.p.getKnightService().offer(a);
            KOMEConquestTile tile=new KOMEConquestTile("T100");tile.setWaypointDefaults(1,"rohan","test");tile.setCurrentRulingFaction("rohan");s.f.data.conquestTiles.put(tile.id,tile);
            ok(s.f.data.getConflictService().acceptValidatedCommitment(new ValidatedCommitmentRequest("T100","C1","mordor",ValidatedConflictAuthority.defender("rohan"),base+1,EntryOrigin.LEGAL_ARRIVAL,"M1",false,Collections.<GarrisonParticipantSeed>emptyList(),ExpectedConflict.absent()),(first,second)->first.equals(second)?Hostility.NON_HOSTILE:Hostility.HOSTILE,id->new DetachmentResolution(id,ReferenceStatus.RESOLVED,KOMEHiredUnitClass.CAMPAIGN,"mordor","T100","validated fixture arrival"),context(1)));
            ok(s.f.data.getConflictService().acceptValidatedCommitment(new ValidatedCommitmentRequest("T100","C2","rohan",ValidatedConflictAuthority.participant("mordor"),base+5,EntryOrigin.LEGAL_ARRIVAL,"M2",false,Collections.<GarrisonParticipantSeed>emptyList(),expected()),(first,second)->first.equals(second)?Hostility.NON_HOSTILE:Hostility.HOSTILE,id->new DetachmentResolution(id,ReferenceStatus.RESOLVED,KOMEHiredUnitClass.CAMPAIGN,id.equals("C1")?"mordor":"rohan","T100","validated fixture arrival"),context(5)));
            KOMEArmyCompany company=new KOMEArmyCompany();company.id="C1";company.faction="mordor";company.currentTile="T100";s.f.data.armyCompanies.put(company.id,company);
            KOMEKnightBattlefieldService.presence=new KOMEKnightBattlefieldService.Presence(){public EntityPlayerMP player(UUID id){return id.equals(s.f.player.id)?s.f.player:null;}public boolean inTile(EntityPlayerMP p,String tile){return KOMETileWorldResolver.INSTANCE.resolveWorldPosition(p.dimension,p.posX,p.posZ).resolvedTileId().orElse("").equals(tile);}};
            LOTRFactionRelations.overrideRelations(LOTRFaction.ROHAN,LOTRFaction.MORDOR,LOTRFactionRelations.Relation.ENEMY);
        }
        Context context(long offset){return new Context(base+offset,"field-test","deterministic service fixture");}
        KOMEConflictRecord conflict(){return s.f.data.getConflictService().get("T100");}
        ExpectedConflict expected(){KOMEConflictRecord c=conflict();return ExpectedConflict.at(c.getConflictId(),c.getRevision());}
        void join(){
            ok(s.f.data.getConflictService().registerPlayer("T100",expected(),s.f.player.id,"rohan",context(6)));
            KOMEJoinBattleDeploymentReceipt.Pose pose=new KOMEJoinBattleDeploymentReceipt.Pose(s.f.player.dimension,s.f.player.posX,65,s.f.player.posZ,0,0);
            s.f.data.getJoinBattleDeploymentReceipts().publishNew(KOMEJoinBattleDeploymentReceipt.builder().receiptId("JB1").actionToken("field-token").playerId(s.f.player.id).conflictId(conflict().getConflictId()).tileId("T100").factionId("rohan").selectedCompanyId("C2").acceptedConflictRevision(conflict().getRevision()).createdAtMillis(base+6).updatedAtMillis(base+7).state(KOMEJoinBattleDeploymentReceipt.State.DEPLOYED).deployedAtMillis(base+7).returnAnchor(pose).deploymentDestination(pose).participationRecovery(KOMEJoinBattleDeploymentReceipt.ParticipationRecovery.PREEXISTING_ACTIVE).build());
        }
        LOTREntityNPC enemy()throws Exception{
            LOTREntityNPC npc=s.npc(LOTRFaction.MORDOR);npc.posX=s.f.player.posX;npc.posZ=s.f.player.posZ;s.f.world.loadedEntityList.add(npc);
            KOMEHiredUnitRecord unit=new KOMEHiredUnitRecord();unit.entity=npc.getUniqueID();unit.owner=UUID.randomUUID();unit.unitFaction="mordor";unit.currentTile="T100";unit.companyId="C1";KOMEHiredUnitClassification.assignForCampaignWorkflow(unit);s.f.data.hiredUnits.put(unit.entity,unit);s.f.data.armyCompanies.get("C1").units.add(unit.entity);return npc;
        }
        void fight()throws Exception{for(int i=0;i<2;i++){LOTREntityNPC npc=enemy();KOMEKnightBattlefieldService.damage(s.f.data,s.f.player,npc,12);KOMEKnightBattlefieldService.death(s.f.data,npc.getUniqueID());npc.setDead();}}
        KOMEConflictRecord end(){return ok(s.f.data.getConflictService().end("T100",expected(),context(20000)));}
        @Override public void close()throws Exception{KOMEKnightBattlefieldService.presence=old;LOTRFactionRelations.overrideRelations(LOTRFaction.ROHAN,LOTRFaction.MORDOR,LOTRFactionRelations.Relation.NEUTRAL);s.close();}
    }
    @Test public void registrationAndSpectatingOrGenericNpcDamageNeverCount()throws Exception{try(Battle b=new Battle()){
        LOTREntityNPC target=b.enemy();KOMEKnightBattlefieldService.damage(b.s.f.data,b.s.f.player,target,12);assertTrue(b.a.battleEvidence.hasNoTags());b.join();assertFalse(KOMEKnightBattlefieldService.enough(b.a));
        LOTREntityNPC ordinary=b.s.npc(LOTRFaction.MORDOR);KOMEKnightBattlefieldService.damage(b.s.f.data,b.s.f.player,ordinary,12);assertTrue(b.a.battleEvidence.hasNoTags());
    }}
    @Test public void genuineCommittedCombatPersistsAndRequiresActualWinningClosure()throws Exception{try(Battle b=new Battle()){
        b.join();b.fight();assertTrue(KOMEKnightBattlefieldService.enough(b.a));KOMEKnightCommission loaded=KOMEKnightCommission.readFromNBT(b.a.writeToNBT());assertEquals(b.a.battleEvidence,loaded.battleEvidence);
        assertFalse(KOMEKnightBattlefieldService.qualifies(b.s.f.player.id,b.a,b.conflict(),"rohan"));KOMEConflictRecord ended=b.end();assertTrue(KOMEKnightBattlefieldService.qualifies(b.s.f.player.id,b.a,ended,"rohan"));assertFalse(KOMEKnightBattlefieldService.qualifies(b.s.f.player.id,b.a,ended,"mordor"));assertEquals(Stage.ACTIVE,b.a.stage);
        // Administrative ending has no publication callback, even when the evidence itself is genuine.
        assertTrue(b.s.p.getKnightService().qualifyingTypes("rohan").isEmpty());
    }}
    @Test public void foreignFactionForgedTokenStaleAndTestEvidenceAreRejected()throws Exception{try(Battle b=new Battle()){
        b.join();LOTREntityNPC npc=b.enemy();b.a.adminForced=true;KOMEKnightBattlefieldService.damage(b.s.f.data,b.s.f.player,npc,12);assertTrue(b.a.battleEvidence.hasNoTags());b.a.adminForced=false;
        b.s.f.pledge(LOTRFaction.GONDOR);KOMEKnightBattlefieldService.damage(b.s.f.data,b.s.f.player,npc,12);assertTrue(b.a.battleEvidence.hasNoTags());b.s.f.pledge(LOTRFaction.ROHAN);
        assertFalse(KOMEKnightBattlefieldService.recordDamage(b.s.f.data,UUID.randomUUID(),b.a,b.conflict(),npc.getUniqueID(),12,b.base+9));
        assertFalse(KOMEKnightBattlefieldService.recordDamage(b.s.f.data,b.s.f.player.id,b.a,b.conflict(),npc.getUniqueID(),12,b.base-1));b.fight();KOMEConflictRecord ended=b.end();b.a.battleEvidence.setString("Token",UUID.randomUUID().toString());assertFalse(KOMEKnightBattlefieldService.qualifies(b.s.f.player.id,b.a,ended,"rohan"));
    }}
    @Test public void withdrawnPlayersAndReplayedConflictCannotEarnSecondCredit()throws Exception{try(Battle b=new Battle()){
        b.join();b.fight();KOMEConflictRecord ended=b.end();assertTrue(KOMEKnightBattlefieldService.qualifies(b.s.f.player.id,b.a,ended,"rohan"));b.a.battleEvidence.setString("CreditedConflict",ended.getConflictId());assertFalse(KOMEKnightBattlefieldService.qualifies(b.s.f.player.id,b.a,ended,"rohan"));
        b.a.stage=Stage.READY_TO_REPORT;assertTrue(b.s.p.getKnightService().report(b.a.liege));KOMEKnightCommission next=new KOMEKnightCommission(Type.FIELD_COMMAND,b.a.liege);next.tier=3;next.stage=Stage.ACTIVE;next.acceptedMillis=b.base;b.s.p.getKnightService().offer(next);
        assertFalse(KOMEKnightBattlefieldService.recordDamage(b.s.f.data,b.s.f.player.id,next,ended,UUID.randomUUID(),12,b.base+20001));assertEquals(1,b.s.p.getKnightService().qualifyingTypes("rohan").size());
    }}
    @Test public void fieldEvidenceDoesNotAuthorizeAnyStrategicOperation()throws Exception{try(Battle b=new Battle()){
        b.join();b.fight();assertEquals(KOMEProgressionRank.KNIGHT,b.s.p.getCanonicalRank());assertFalse(KOMEProgressionAbilityPolicy.unlocked(b.s.f.data,b.s.f.player.id,KOMEProgressionPermissionRegistry.Ability.CAMPAIGN_RECRUITMENT));assertEquals(1,b.s.f.data.armyCompanies.size());assertEquals("rohan",b.s.f.data.getConquestTileIfPresent("T100").projectRulingFaction());
    }}
    @Test public void withdrawalInvalidatesOtherwiseMeaningfulEvidence()throws Exception{try(Battle b=new Battle()){
        b.join();b.fight();ok(b.s.f.data.getConflictService().withdrawPlayer("T100",b.expected(),b.s.f.player.id,b.context(100)));
        assertFalse(KOMEKnightBattlefieldService.qualifies(b.s.f.player.id,b.a,b.end(),"rohan"));
    }}
    @Test public void rawAdministrativeConflictCannotCreatePersonalEvidence()throws Exception{try(Battle b=new Battle()){
        KOMEConflictService raw=new KOMEConflictService();
        KOMEConflictRecord synthetic=ok(raw.start("T101",State.ORDINARY,ExpectedConflict.absent(),Collections.<GarrisonSeed>emptyList(),b.context(1)));
        assertFalse(KOMEKnightBattlefieldService.canonical(synthetic));
        assertFalse(KOMEKnightBattlefieldService.recordDamage(b.s.f.data,b.s.f.player.id,b.a,synthetic,UUID.randomUUID(),20,b.base+10));assertTrue(b.a.battleEvidence.hasNoTags());
    }}
}
