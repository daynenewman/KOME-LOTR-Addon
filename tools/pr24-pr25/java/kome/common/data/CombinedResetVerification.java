package kome.common.data;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import lotr.common.LOTRDimension;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.chunk.storage.*;
import net.minecraft.world.storage.ThreadedFileIOBase;
import net.minecraftforge.common.DimensionManager;

/** Real native NPCs, mounted trees, Anvil chunks and independent Forge JVMs; never a connected client. */
@Mod(modid="combinedverification",name="Disposable combined reset verification",version="1",dependencies="required-after:kome")
public final class CombinedResetVerification {
    private final StringBuilder evidence = new StringBuilder();
    private String phase="unknown", order="unknown";
    private File chunkDirectory;
    private NBTTagCompound fixture;
    private boolean injectStale;
    private static final UUID OWNER=UUID.fromString("58e04983-e981-4d9b-b189-9d68011a849a");

    @Mod.EventHandler public void started(FMLServerStartedEvent event) {
        MinecraftServer server=MinecraftServer.getServer();
        try {
            phase=readText("verification-phase.txt"); order=readText("verification-order.txt");
            int dimension=LOTRDimension.MIDDLE_EARTH.dimensionID;
            if (DimensionManager.getWorld(dimension)==null) DimensionManager.initDimension(dimension);
            WorldServer world=DimensionManager.getWorld(dimension);
            KOMEWorldData data=KOMEWorldData.get(world); data.initializeIntegratedWorld(world);
            world.getGameRules().setOrCreateGameRule("doMobSpawning","false");
            chunkDirectory=((AnvilChunkLoader)((ChunkProviderServer)world.getChunkProvider()).currentChunkLoader).chunkSaveLocation;
            if ("first".equals(phase)) first(world,data);
            else {
                try(InputStream in=Files.newInputStream(Paths.get("fixture.dat"))) { fixture=CompressedStreamTools.readCompressed(in); }
                check(data.hiredUnits.size()==2,"cold canonical root retained both purchased units");
                check(data.dailyJournal.completedStages()==2,"cold root retained interrupted daily cursor");
                check(KOMEGovernanceService.records(data,OWNER).size()==1,"cold root retained governance history");
                loadChunks(world,data);
                if ("recover".equals(phase)) recover(world,data); else completed(world,data);
            }
            verifyJoinRecovery(data);
            check(server.getCommandManager().executeCommand(server,"save-all")==1,"real save-all completed");
            data.checkpointReset(world);
            evidence.append("PASS ").append(phase).append(' ').append(order).append("; no connected client\n");
        } catch(Throwable failure) { evidence.append("FAIL ").append(failure).append('\n'); failure.printStackTrace(); }
        finally {
            writeEvidence(); server.initiateShutdown();
        }
    }

    private void first(WorldServer world,KOMEWorldData data) throws Exception {
        fixture=new NBTTagCompound();
        KOMEFactionCapitalRecord origin=KOMEFactionCapitalService.getCapital(data,"mordor");
        KOMEFactionCapitalRecord capital=KOMEFactionCapitalService.getCapital(data,"gondor");
        platform(world,origin); platform(world,capital);
        capital=new KOMEFactionCapitalRecord("gondor",capital.getCapitalTileId(),world.provider.dimensionId,
            capital.getDeploymentX(),200,capital.getDeploymentZ(),1,"disposable verification","system");
        data.factionCapitals.put("gondor",capital);
        fixture.setInteger("OriginX",(int)Math.floor(origin.getDeploymentX())>>4);
        fixture.setInteger("OriginZ",(int)Math.floor(origin.getDeploymentZ())>>4);
        fixture.setInteger("DestinationX",(int)Math.floor(capital.getDeploymentX())>>4);
        fixture.setInteger("DestinationZ",(int)Math.floor(capital.getDeploymentZ())>>4);
        data.lastKnownPlayerFactions.put(OWNER,"gondor"); data.grantFactionPopulationCenti("gondor",777);
        data.warSeason.phase=KOMEWarSeasonState.Phase.FINALE;
        KOMEWar war=new KOMEWar(); war.id="W-verify";war.initiatingFaction="gondor";war.defendingFaction="mordor";
        war.sideOneFactions.add("gondor");war.sideTwoFactions.add("mordor");data.wars.put(war.id,war);
        data.warSeason.factionDefeats.put("gondor",1L); KOMEGovernanceService.retainDefeat(data,OWNER,"gondor",2);
        data.dailyJournal.boundary=data.dailyJournal.anchor=100;data.dailyJournal.nextStage=2;data.dailyJournal.status="RUNNING";
        for(int index=1;index<=2;index++) {
            LOTREntityNPC rider=(LOTREntityNPC)EntityList.createEntityByName("lotr.GondorSoldier",world);
            EntityLivingBase mount=(EntityLivingBase)EntityList.createEntityByName("lotr.Horse",world);
            rider.setLocationAndAngles(origin.getDeploymentX()+index*3,200,origin.getDeploymentZ(),0,0);
            mount.setLocationAndAngles(rider.posX,200,rider.posZ,0,0);
            rider.setHealth(3.25F);mount.setHealth(7.125F);
            rider.func_110163_bv();
            check(world.spawnEntityInWorld(mount),"spawn native mount "+index);
            check(world.spawnEntityInWorld(rider),"spawn native rider "+index);rider.mountEntity(mount);
            KOMEArmyCompany c=new KOMEArmyCompany();c.id="C"+index;c.name=c.id;c.owner=OWNER;c.faction=c.nativeFaction="gondor";
            c.currentTile=origin.getCapitalTileId();c.totalPopulation=c.mountedPopulation=50;c.units.add(rider.getUniqueID());
            data.armyCompanies.put(c.id,c);
            KOMEHiredUnitRecord unit=new KOMEHiredUnitRecord();unit.entity=rider.getUniqueID();unit.owner=OWNER;unit.companyId=c.id;
            unit.currentTile=c.currentTile;unit.sourceTileId=capital.getCapitalTileId();unit.mounted=true;unit.cost=unit.baseCost=unit.populationSpent=50;
            unit.unitFaction=unit.populationOwningFaction=unit.sourceFaction="gondor";unit.sourceType=KOMEHiredUnitRecord.SOURCE_FACTION_POPULATION_BANK;
            KOMEHiredUnitClassification.assignForCampaignWorkflow(unit);
            unit.stationedEntityData=KOMEEntitySnapshots.snapshot(rider);check(unit.stationedEntityData!=null,"snapshot mounted survivor "+index);
            data.hiredUnits.put(unit.entity,unit);fixture.setString(c.id,rider.getUniqueID().toString());fixture.setString(c.id+"Mount",mount.getUniqueID().toString());
            fixture.setTag(c.id+"Snapshot",unit.stationedEntityData.copy());
            if(index==2) {
                KOMEArmyMovementOrder movement=KOMEArmyMovementOrder.newRoute(1);movement.id="M2";movement.companyId=c.id;movement.owner=OWNER;movement.ownerFaction="gondor";
                movement.currentTile=movement.originTile=movement.currentStepOriginTile=c.currentTile;
                movement.destinationTile=movement.finalDestinationTile=movement.currentStepDestinationTile=capital.getCapitalTileId();
                movement.routeTiles.add(c.currentTile);movement.routeTiles.add(capital.getCapitalTileId());movement.units.addAll(c.units);movement.status=KOMEArmyMovementOrder.MOVING;
                c.status=KOMEArmyCompany.MOVING;c.movementOrderId=movement.id;unit.movementOrderId=movement.id;unit.movingEntityData=(NBTTagCompound)unit.stationedEntityData.copy();
                data.armyMovements.put(movement.id,movement);
                world.removePlayerEntityDangerously(rider);world.removePlayerEntityDangerously(mount);
            }
        }
        seedOfflineJoinRecovery(world,data,origin);
        try(OutputStream out=Files.newOutputStream(Paths.get("fixture.dat"))) {CompressedStreamTools.writeCompressed(fixture,out);}
        check(KOMESeasonResetService.begin(data,10).allowed,"begin canonical reset");
        KOMESeasonResetDeployment actual=new KOMESeasonResetDeployment(world);
        KOMESeasonResetService.process(data,world.getTotalWorldTime(),11,new KOMESeasonResetService.Deployment() {
            public void checkpoint(KOMEWorldData value){actual.checkpoint(value);}
            public String apply(KOMEWorldData value,KOMESeasonResetState.Return entry,String token){
                String pending=actual.apply(value,entry,token);
                if(pending.isEmpty() && "C1".equals(entry.companyId))throw new IllegalStateException("intentional interruption after durable entity return");
                return pending;
            }
        });
        check(data.seasonReset.failure.contains("intentional interruption"),"interrupted after actual Anvil receipts: "+data.seasonReset.failure);
        check(!data.seasonReset.complete() && count(data,"COMPANY_RETURN")==0,"strategic publication remains pending after physical save");
        check(KOMESeasonResetDeployment.find(UUID.fromString(fixture.getString("C1")))!=null,"stationary survivor still loaded");
        check(!KOMESeasonResetService.finish(data,12,value->value.checkpointReset(world)).allowed,"pending completion denied");
        injectStale=true;
    }

    private void loadChunks(WorldServer world,KOMEWorldData data) {
        int ox=fixture.getInteger("OriginX"),oz=fixture.getInteger("OriginZ"),dx=fixture.getInteger("DestinationX"),dz=fixture.getInteger("DestinationZ");
        check(!world.getChunkProvider().chunkExists(ox,oz) && !world.getChunkProvider().chunkExists(dx,dz),"both test regions initially unloaded");
        if("origin-first".equals(order)){world.getChunkFromChunkCoords(ox,oz);loadDestinationRegion(world,dx,dz);}
        else {loadDestinationRegion(world,dx,dz);world.getChunkFromChunkCoords(ox,oz);}
        check(KOMESeasonResetDeployment.find(UUID.fromString(fixture.getString("C2")))==null || "completed".equals(phase),"obsolete virtual rider rejected before recovery");
        check(KOMESeasonResetDeployment.find(UUID.fromString(fixture.getString("C2Mount")))==null || "completed".equals(phase),"obsolete virtual mount rejected before recovery");
    }
    /** Actual safe placements may cross the capital reference chunk; load saved neighboring chunks. */
    private void loadDestinationRegion(WorldServer world,int dx,int dz) {
        world.getChunkFromChunkCoords(dx,dz);
        AnvilChunkLoader loader=(AnvilChunkLoader)((ChunkProviderServer)world.getChunkProvider()).currentChunkLoader;
        for(int x=dx-2;x<=dx+2;x++)for(int z=dz-2;z<=dz+2;z++)
            if(loader.chunkExists(world,x,z))world.getChunkFromChunkCoords(x,z);
    }
    private void recover(WorldServer world,KOMEWorldData data) throws Exception {
        check(data.warSeason.phase==KOMEWarSeasonState.Phase.RESET,"cold reset remains active");
        NBTTagCompound daily=data.dailyJournal.write();
        check(new KOMEDailyCoordinator().process(data,value->{throw new AssertionError("daily write during reset");}).handled,"RESET preempts direct coordinator");
        check(daily.equals(data.dailyJournal.write()),"interrupted daily authority unchanged");
        check(!KOMEGovernanceService.militaryAction(data,OWNER,"gondor").allowed,"Submitted restriction survived interrupted return");
        KOMESeasonResetService.process(data,world,System.currentTimeMillis());
        check(data.seasonReset.complete(),"real retry completes: "+KOMESeasonResetService.status(data));
        verifySurvivors(data);
        check(!KOMESeasonResetService.finish(data,20,value->{throw new IOException("injected checkpoint denial");}).allowed,"completion save failure denied");
        check(data.warSeason.phase==KOMEWarSeasonState.Phase.RESET && count(data,"RESET_COMPLETE")==0,"failed completion retains RESET and audit");
        check(KOMESeasonResetService.finish(data,21,value->value.checkpointReset(world)).allowed,"durable completion succeeds");
        completed(world,data);
    }
    private void completed(WorldServer world,KOMEWorldData data) throws Exception {
        verifySurvivors(data);
        check(data.warSeason.seasonId==2 && data.warSeason.phase==KOMEWarSeasonState.Phase.MAINTENANCE,"season advanced exactly once");
        check(KOMESeasonResetService.finish(data,22,value->value.checkpointReset(world)).allowed,"repeat completion succeeds without new outcome");
        check(count(data,"RESET_COMPLETE")==1 && count(data,"COMPANY_RETURN")==2,"one completion and one audit per returned company");
        check(KOMEPopulationService.getAvailablePopulationCenti(data,"gondor")==777,"purchased population preserved");
    }
    private void verifySurvivors(KOMEWorldData data) {
        for(int index=1;index<=2;index++) {
            String id="C"+index;Entity rider=KOMESeasonResetDeployment.find(UUID.fromString(fixture.getString(id)));
            Entity mount=KOMESeasonResetDeployment.find(UUID.fromString(fixture.getString(id+"Mount")));
            check(rider!=null && mount!=null && rider.ridingEntity==mount,"one original mounted tree "+id+" rider="+fixture.getString(id)+" mount="+fixture.getString(id+"Mount"));
            evidence.append("NATIVE ").append(id).append(" position=").append(rider.posX).append(",").append(rider.posY).append(",").append(rider.posZ).append(" chunk=").append((int)Math.floor(rider.posX)>>4).append(",").append((int)Math.floor(rider.posZ)>>4).append("\n");
            check(((EntityLivingBase)rider).getHealth()==3.25F && ((EntityLivingBase)mount).getHealth()==7.125F,"exact fractional survivor HP "+id);
            check(KOMESeasonResetDeployment.inTile(rider,data.armyCompanies.get(id).currentTile),"physical and strategic destination agree "+id);
            check(("1:"+id).equals(rider.getEntityData().getString(KOMESeasonResetDeployment.RECEIPT)) && ("1:"+id).equals(mount.getEntityData().getString(KOMESeasonResetDeployment.RECEIPT)),"rider/mount receipts retained "+id);
            check(fixture.getCompoundTag(id+"Snapshot").equals(data.hiredUnits.get(rider.getUniqueID()).stationedEntityData),"original snapshot preserved "+id);
        }
    }
    /** Synthetic offline receipt beside real NPC returns; tests durable coexistence, not player deployment. */
    private void seedOfflineJoinRecovery(WorldServer world,KOMEWorldData data,KOMEFactionCapitalRecord origin) {
        KOMEConflictService.Result started=data.getConflictService().start(origin.getCapitalTileId(),
            KOMEConflictRecord.State.ORDINARY,KOMEConflictContracts.ExpectedConflict.absent(),
            Collections.<KOMEConflictContracts.GarrisonSeed>emptyList(),
            new KOMEConflictContracts.Context(3L,"verification","offline Join recovery coexistence"));
        check(started.isSuccess(),"create isolated receipt conflict");
        net.minecraft.entity.passive.EntityHorse mount=new net.minecraft.entity.passive.EntityHorse(world);
        mount.setHealth(7.125F);
        NBTTagCompound snapshot=KOMEEntitySnapshots.snapshot(mount);
        check(snapshot!=null,"snapshot native unspawned recovery mount");
        KOMEJoinBattleDeploymentReceipt.Pose pose=new KOMEJoinBattleDeploymentReceipt.Pose(
            world.provider.dimensionId,origin.getDeploymentX(),200,origin.getDeploymentZ(),0F,0F);
        data.getJoinBattleDeploymentReceipts().publishNew(KOMEJoinBattleDeploymentReceipt.builder()
            .receiptId("JB1").actionToken("offline-combined-recovery").playerId(OWNER)
            .conflictId(started.record.getConflictId()).tileId(origin.getCapitalTileId())
            .acceptedConflictRevision(started.record.getRevision()).factionId("gondor").selectedCompanyId("C1")
            .createdAtMillis(4L).updatedAtMillis(4L).returnAnchor(pose).deploymentDestination(pose)
            .participationRecovery(KOMEJoinBattleDeploymentReceipt.ParticipationRecovery.REGISTRATION_REQUIRED)
            .enteredMounted(true).mountUuid(mount.getUniqueID()).mountEntityType("Horse")
            .mountProfile(KOMEJoinBattleDeploymentReceipt.MountProfile.VANILLA_HORSE).mountSourceAnchor(pose)
            .mountTransferPhase(KOMEJoinBattleDeploymentReceipt.MountTransferPhase.DESTINATION_PUBLICATION_PENDING)
            .temporaryMountNbt(snapshot).build());
        fixture.setString("JoinMountUuid",mount.getUniqueID().toString());
        fixture.setTag("JoinMountSnapshot",snapshot.copy());
    }
    private void verifyJoinRecovery(KOMEWorldData data) {
        KOMEJoinBattleDeploymentReceipt receipt=data.getJoinBattleDeploymentReceipts().get("JB1");
        check(receipt!=null&&receipt.getState()==KOMEJoinBattleDeploymentReceipt.State.PENDING_ENTRY,
            "offline Join Battle entry remains owned by entry recovery");
        check(receipt.getMountTransferPhase()==KOMEJoinBattleDeploymentReceipt.MountTransferPhase.DESTINATION_PUBLICATION_PENDING,
            "Join Battle recovery phase retained alongside reset");
        check(fixture.getString("JoinMountUuid").equals(receipt.getMountUuid().toString()),"original offline Join mount identity retained");
        check(fixture.getCompoundTag("JoinMountSnapshot").equals(receipt.getTemporaryMountNbt()),
            "native unspawned mount snapshot retained through reset and restart; no player deployment pass");
        check(receipt.getTemporaryMountNbt().getFloat("HealF")==7.125F,"offline Join snapshot partial HP retained");
        NBTTagCompound root=new NBTTagCompound();data.writeToNBT(root);
        check(root.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY)==12&&root.getInteger("ConflictDataSchemaVersion")==2,
            "combined root 12 and ConflictData v2 serialize together");
    }
    private void platform(WorldServer world,KOMEFactionCapitalRecord capital) {
        int x=(int)Math.floor(capital.getDeploymentX()),z=(int)Math.floor(capital.getDeploymentZ());
        for(int a=-8;a<=10;a++)for(int b=-8;b<=8;b++) {
            world.setBlock(x+a,199,z+b,Blocks.stone,0,2);
            for(int y=200;y<=207;y++) world.setBlockToAir(x+a,y,z+b);
        }
    }
    private int count(KOMEWorldData data,String action){int n=0;for(KOMEAuditEntry e:data.centralAudit)if(action.equals(e.action))n++;return n;}
    private void check(boolean value,String message){if(!value)throw new IllegalStateException(message);evidence.append("OK ").append(message).append('\n');}
    private String readText(String name)throws Exception{return new String(Files.readAllBytes(Paths.get(name)),StandardCharsets.UTF_8).trim();}
    private void writeEvidence(){try{Files.write(Paths.get("combined-"+phase+"-"+order+".txt"),evidence.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception e){e.printStackTrace();}}

    /** Model an old virtual source chunk surviving a crash, after the server's orderly saves finish. */
    @Mod.EventHandler public void stopped(FMLServerStoppedEvent event) throws Exception {
        if(!injectStale)return;
        ThreadedFileIOBase.threadedIOInstance.waitForFinish();
        NBTTagCompound stale=fixture.getCompoundTag("C2Snapshot");NBTTagList pos=stale.getTagList("Pos",6);
        int x=(int)Math.floor(pos.func_150309_d(0))>>4,z=(int)Math.floor(pos.func_150309_d(2))>>4;
        NBTTagCompound root;
        try(DataInputStream in=RegionFileCache.getChunkInputStream(chunkDirectory,x,z)){root=CompressedStreamTools.read(in);}
        root.getCompoundTag("Level").getTagList("Entities",10).appendTag(stale.copy());
        try(DataOutputStream out=RegionFileCache.getChunkOutputStream(chunkDirectory,x,z)){CompressedStreamTools.write(root,out);}
        RegionFileCache.clearRegionFileReferences();
        evidence.append("OK injected obsolete virtual source rider/mount into disposable Anvil after shutdown\n");writeEvidence();
    }
}
