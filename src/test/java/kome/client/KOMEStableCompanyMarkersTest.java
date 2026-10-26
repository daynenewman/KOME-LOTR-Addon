package kome.client;

import java.lang.reflect.*;
import java.util.*;
import kome.common.KOMEAccessFixture;
import kome.common.data.*;
import kome.common.network.KOMEPacketUnitMapMarkers;
import lotr.common.LOTRDimension;
import lotr.common.entity.npc.LOTREntityRohanMan;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Actual server projection through wire replacement and native company-art selection. */
public class KOMEStableCompanyMarkersTest {
    KOMEWorldData data; KOMEArmyCompany company; KOMEHiredUnitRecord unit;
    WorldServer world; UUID viewer; String viewerFaction;
    @Before public void setup() throws Exception {
        data=new KOMEWorldData("marker-test");viewer=UUID.randomUUID();viewerFaction="mordor";
        company=new KOMEArmyCompany();company.id="C1";company.name="Riders";company.owner=viewer;
        company.faction="mordor";company.nativeFaction="rohan";company.currentTile="T100";
        data.armyCompanies.put(company.id,company);
        world=KOMEAccessFixture.allocate(WorldServer.class);
        Field provider=World.class.getDeclaredField("provider");provider.setAccessible(true);provider.set(world,new WorldProviderSurface());
        world.provider.dimensionId=LOTRDimension.MIDDLE_EARTH.dimensionID;world.loadedEntityList=new ArrayList();
        unit=member();load(unit,123,456);
    }
    KOMEHiredUnitRecord member() {
        KOMEHiredUnitRecord r=new KOMEHiredUnitRecord();r.entity=UUID.randomUUID();r.owner=viewer;
        NBTTagCompound n=r.writeToNBT();n.setString("UnitClass","CAMPAIGN");r.readFromNBT(n);
        r.companyId=company.id;r.currentTile=company.currentTile;r.cost=25;
        data.hiredUnits.put(r.entity,r);company.units.add(r.entity);return r;
    }
    void load(KOMEHiredUnitRecord r,double x,double z)throws Exception {
        LOTREntityRohanMan npc=KOMEAccessFixture.allocate(LOTREntityRohanMan.class);
        Field id=Entity.class.getDeclaredField("entityUniqueID");id.setAccessible(true);id.set(npc,r.entity);
        npc.posX=x;npc.posZ=z;npc.posY=70;world.loadedEntityList.add(npc);
    }
    List<KOMEUnitMapMarker> publish()throws Exception {
        return publish(false);
    }
    List<KOMEUnitMapMarker> publish(boolean admin)throws Exception {
        Method m=KOMEPacketUnitMapMarkers.class.getDeclaredMethod("buildMarkers",KOMEWorldData.class,UUID.class,String.class,boolean.class,WorldServer[].class);m.setAccessible(true);
        List result=(List)m.invoke(null,data,viewer,viewerFaction,admin,new WorldServer[]{world});
        KOMEPacketUnitMapMarkers packet=new KOMEPacketUnitMapMarkers(result);
        io.netty.buffer.ByteBuf wire=io.netty.buffer.Unpooled.buffer();packet.toBytes(wire);
        KOMEPacketUnitMapMarkers received=new KOMEPacketUnitMapMarkers();received.fromBytes(wire);wire.release();
        return received.markers;
    }
    @Test public void unloadedCompanyRetainsIdentityAndShieldThenReconstructedUuidRestoresPhysicalCenter()throws Exception {
        KOMEUnitMapMarker loaded=publish().get(0);assertEquals("C1",loaded.entityId);assertEquals(123,loaded.x,0);assertEquals(1,loaded.unitCount);
        assertSame(KOMEMapMarkerArt.shield("rohan"),KOMEMapMarkerArt.company(loaded,data.armyCompanies));
        world.loadedEntityList.clear();
        List<KOMEUnitMapMarker> unloaded=publish();assertEquals("Unloading must not erase a committed company",1,unloaded.size());
        KOMEUnitMapMarker strategic=unloaded.get(0);assertEquals(0,strategic.unitCount);assertEquals("T100",strategic.currentTile);assertEquals(25,strategic.population);
        assertSame(KOMEMapMarkerArt.shield("rohan"),KOMEMapMarkerArt.company(strategic,data.armyCompanies));
        UUID previous=unit.entity;data.hiredUnits.remove(previous);company.units.remove(previous);
        unit=member();assertNotEquals(previous,unit.entity);load(unit,789,987);
        KOMEUnitMapMarker rebuilt=publish().get(0);assertEquals("C1",rebuilt.entityId);assertEquals(789,rebuilt.x,0);assertEquals(1,rebuilt.unitCount);assertEquals(25,rebuilt.population);
    }
    @Test public void committedWaitingStepKeepsLoadedAndUnloadedShieldUntilActualDeparture()throws Exception {
        KOMEArmyMovementOrder order=new KOMEArmyMovementOrder();order.id="STEP";order.companyId=company.id;
        order.currentTile="T100";order.status=KOMEArmyMovementOrder.WAITING_NEXT_STEP;
        data.armyMovements.put(order.id,order);
        company.status=KOMEArmyCompany.MOVING;company.movementOrderId=order.id;
        unit.movementOrderId=order.id;unit.stationedEntityData=new NBTTagCompound();
        assertEquals(1,publish().size());assertEquals(1,publish().get(0).unitCount);
        world.loadedEntityList.clear();
        assertEquals(1,publish().size());assertEquals(0,publish().get(0).unitCount);
        assertEquals(25,publish().get(0).population);
        unit.movingEntityData=new NBTTagCompound();order.status=KOMEArmyMovementOrder.MOVING;
        assertTrue(publish().isEmpty());
    }
    @Test public void removalMovementTerminalAndUnauthorizedRecordsNeverManufacturePresence()throws Exception {
        world.loadedEntityList.clear();assertEquals(1,publish().size());
        company.movementOrderId="moving";assertTrue(publish().isEmpty());company.movementOrderId="";
        unit.movementOrderId="moving";assertTrue(publish().isEmpty());unit.movementOrderId="";
        unit.populationReturned=true;assertTrue(publish().isEmpty());unit.populationReturned=false;
        unit.currentTile="T101";assertTrue(publish().isEmpty());unit.currentTile="T100";
        viewer=UUID.randomUUID();assertEquals(1,publish().size());viewerFaction="gondor";assertTrue(publish().isEmpty());viewer=company.owner;viewerFaction="mordor";
        company.units.clear();assertTrue(publish().isEmpty());company.units.add(unit.entity);
        data.hiredUnits.clear();assertTrue(publish().isEmpty());data.hiredUnits.put(unit.entity,unit);
        data.armyCompanies.clear();assertTrue(publish().isEmpty());
    }
    @Test public void mixedLoadedAndUnloadedMembersCountOnceAndFreshFactionWins()throws Exception {
        member();assertEquals(50,publish().get(0).population);assertEquals(1,publish().size());
        company.nativeFaction="gondor";assertSame(KOMEMapMarkerArt.shield("gondor"),KOMEMapMarkerArt.company(publish().get(0),data.armyCompanies));
        world.loadedEntityList.clear();assertEquals(50,publish().get(0).population);
    }
    @Test public void staleTerminalOrdinaryAndDuplicateMembershipCannotInflatePopulation()throws Exception {
        member();company.units.add(unit.entity);assertEquals(50,publish().get(0).population);
        unit.survivingHealth=new KOMECampaignHealthProjection(UUID.randomUUID(),
            KOMEOrdinaryBattleRecoveryRegistry.Component.RIDER,unit.entity,0,1,UUID.randomUUID(),true,null).write();
        assertEquals(25,publish().get(0).population);assertEquals(0,publish().get(0).unitCount);
        unit.survivingHealth=null;unit.farmhand=true;assertEquals(25,publish().get(0).population);unit.farmhand=false;
        company.units.removeIf(id->!id.equals(unit.entity));unit.currentTile="T101";assertTrue(publish().isEmpty());
    }
    @Test public void haltedCompanySurvivesUnloadingButRetreatMovementNeverLeavesStationedDuplicate()throws Exception {
        world.loadedEntityList.clear();company.status=KOMEArmyCompany.WAR_ENDED_HALTED;assertEquals(1,publish().size());
        company.movementOrderId="retreat";assertTrue(publish().isEmpty());company.movementOrderId="";
        company.status=KOMEArmyCompany.STATIONED;company.currentTile=unit.currentTile="T101";
        assertEquals("T101",publish().get(0).currentTile);company.currentTile="";assertTrue(publish().isEmpty());
    }
    @Test public void authorizedAdminCanSeeUnloadedCompaniesButOtherRecipientsCannot()throws Exception {
        world.loadedEntityList.clear();viewer=UUID.randomUUID();viewerFaction="gondor";assertTrue(publish().isEmpty());assertEquals(1,publish(true).size());
        unit.owner=viewer;assertTrue("Incoherent company membership is not presence",publish(true).isEmpty());
    }
    @Test public void personalSpotterPublishesForeignShieldOnlyWhileSharingCanonicalTile()throws Exception {
        world.loadedEntityList.clear();
        KOMEArmyCompany foreign=new KOMEArmyCompany();foreign.id="FOREIGN";
        foreign.name="Foreign guard";foreign.owner=UUID.randomUUID();
        foreign.faction="gondor";foreign.nativeFaction="gondor";foreign.currentTile="T100";
        data.armyCompanies.put(foreign.id,foreign);
        KOMEHiredUnitRecord member=new KOMEHiredUnitRecord();member.entity=UUID.randomUUID();member.owner=foreign.owner;
        NBTTagCompound saved=member.writeToNBT();saved.setString("UnitClass","CAMPAIGN");member.readFromNBT(saved);
        member.companyId=foreign.id;member.currentTile="T100";member.cost=50;
        data.hiredUnits.put(member.entity,member);foreign.units.add(member.entity);
        assertEquals(2,publish().size());
        KOMEUnitMapMarker spotted=publish().stream().filter(m->!"C1".equals(m.entityId)).findFirst().get();
        assertNotEquals("FOREIGN",spotted.entityId);assertEquals("",spotted.companyName);
        assertEquals(0,spotted.population);assertEquals(1,spotted.soldierCount);
        load(member,999,777);
        assertEquals(0,publish().stream().filter(m->!"C1".equals(m.entityId)).findFirst().get().unitCount);
        foreign.currentTile="T101";
        assertEquals(1,publish().size()); // A loaded NPC cannot override canonical tile coherence.
        foreign.currentTile="T100";
        unit.currentTile="T101";company.currentTile="T101";
        assertEquals(1,publish().size());assertEquals("C1",publish().get(0).entityId);
    }

    @Test public void soldierBadgeCountsCommittedRecordsRegardlessOfCostDamageOrChunkLoading()throws Exception {
        unit.cost=30;KOMEHiredUnitRecord second=member(),third=member();second.cost=third.cost=30;
        assertEquals(90,publish().get(0).population);assertEquals(3,publish().get(0).soldierCount);
        unit.cost=5;world.loadedEntityList.clear();
        assertEquals(3,publish().get(0).soldierCount);
        assertEquals(0,publish().get(0).unitCount);
        assertEquals(65,publish().get(0).population);
    }
}
