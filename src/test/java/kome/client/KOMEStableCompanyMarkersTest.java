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
    WorldServer world; UUID viewer;
    @Before public void setup() throws Exception {
        data=new KOMEWorldData("marker-test");viewer=UUID.randomUUID();
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
        Method m=KOMEPacketUnitMapMarkers.class.getDeclaredMethod("buildMarkers",KOMEWorldData.class,UUID.class,boolean.class,WorldServer[].class);m.setAccessible(true);
        List result=(List)m.invoke(null,data,viewer,admin,new WorldServer[]{world});
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
    @Test public void removalMovementTerminalAndUnauthorizedRecordsNeverManufacturePresence()throws Exception {
        world.loadedEntityList.clear();assertEquals(1,publish().size());
        company.movementOrderId="moving";assertTrue(publish().isEmpty());company.movementOrderId="";
        unit.movementOrderId="moving";assertTrue(publish().isEmpty());unit.movementOrderId="";
        unit.populationReturned=true;assertTrue(publish().isEmpty());unit.populationReturned=false;
        unit.currentTile="T101";assertTrue(publish().isEmpty());unit.currentTile="T100";
        viewer=UUID.randomUUID();assertTrue(publish().isEmpty());viewer=company.owner;
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
        world.loadedEntityList.clear();viewer=UUID.randomUUID();assertTrue(publish().isEmpty());assertEquals(1,publish(true).size());
        unit.owner=viewer;assertTrue("Incoherent company membership is not presence",publish(true).isEmpty());
    }
}
