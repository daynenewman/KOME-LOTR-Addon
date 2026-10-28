package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kome.common.KOMEAccessFixture;
import kome.common.data.*;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.*;
import static org.junit.Assert.*;

/** Exercises the actual server projection, chunk dispatch and registered lifecycle handlers. */
public class KOMEConquestSyncTest {
    private KOMEAccessFixture fixture;
    private SimpleNetworkWrapper previous;
    private KOMEPopulationTestConfig config;

    @Before public void setUp() throws Exception {
        config = new KOMEPopulationTestConfig();
        KOMEAdminMapVision.clear();
        fixture = new KOMEAccessFixture();
        previous = KOMEPacketHandler.network;
        KOMEPacketHandler.network = fixture.network;
        KOMEPacketConquestData.clearSentSnapshots();
        KOMEAdminMapVision.clear();
        KOMEConquestTile tile = new KOMEConquestTile("T132");
        tile.claim("dunedain", 1); fixture.data.conquestTiles.put(tile.id, tile);
    }
    @After public void tearDown() throws Exception {
        KOMEPacketConquestData.clearSentSnapshots();
        KOMEPacketHandler.network = previous; config.close();
    }
    private void send() { KOMEPacketConquestData.sendIfChanged(fixture.data, fixture.player); }
    private List<KOMEPacketConquestData> take() {
        List<KOMEPacketConquestData> result = new ArrayList<KOMEPacketConquestData>();
        for (IMessage m : fixture.network.messages) result.add((KOMEPacketConquestData)m);
        fixture.network.messages.clear(); return result;
    }
    private static NBTTagCompound assembled(List<KOMEPacketConquestData> packets) {
        assertFalse(packets.isEmpty());
        NBTTagCompound result = new NBTTagCompound(); int resets = 0, completes = 0;
        for (KOMEPacketConquestData packet : packets) {
            if (packet.reset) resets++; if (packet.complete) completes++;
            for (Object keyObject : packet.data.func_150296_c()) {
                String key = (String) keyObject;
                NBTTagList rows = packet.data.getTagList(key, 10);
                assertTrue(rows.tagCount() <= 48);
                NBTTagList all = result.getTagList(key, 10);
                for (int i=0;i<rows.tagCount();i++) all.appendTag(rows.getCompoundTagAt(i).copy());
                result.setTag(key, all);
            }
        }
        assertEquals(1, resets); assertEquals(1, completes);
        assertTrue(packets.get(0).reset); assertTrue(packets.get(packets.size()-1).complete);
        return result;
    }
    @Test public void repeatedIdenticalBroadcastsSendNothingAndDoNotDirtyWorld() {
        fixture.data.setDirty(false); send(); assembled(take());
        for(int i=0;i<100;i++) send();
        assertTrue(take().isEmpty()); assertFalse(fixture.data.isDirty());
    }
    @Test public void ownershipChangeProducesCompleteSnapshotOnce() {
        send(); take(); fixture.data.conquestTiles.get("T132").claim("gondor", 2);
        send(); NBTTagCompound actual = assembled(take());
        assertEquals(new KOMEPacketConquestData(fixture.data).data.getTag("ConquestTiles"), actual.getTag("ConquestTiles"));
        assertEquals(new KOMEPacketConquestData(fixture.data).data.getTag("TroopSummaries"), actual.getTag("TroopSummaries"));
        send(); assertTrue(take().isEmpty());
    }
    @Test public void companyMovementAndRouteUpdatesAreNotSuppressed() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        send(); take();
        KOMEArmyCompany company = new KOMEArmyCompany(); company.id = "C1";
        company.name = "Test company"; company.currentTile = "T132"; company.faction="gondor";
        fixture.data.armyCompanies.put(company.id, company); send();
        assertEquals(1, assembled(take()).getTagList("ArmyCompanies", 10).tagCount());
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder(); order.id = "M1";
        order.companyId=company.id; order.ownerFaction=company.faction;
        order.originTile = "T132"; order.destinationTile = "T149";
        fixture.data.armyMovements.put(order.id, order); send();
        assertEquals(1, assembled(take()).getTagList("ArmyMovements", 10).tagCount());
        order.status = KOMEArmyMovementOrder.ARRIVED; send();
        assertEquals(0, assembled(take()).getTagList("ArmyMovements", 10).tagCount());
        KOMEConquestRouteEdge edge = new KOMEConquestRouteEdge("T132", "T149", KOMEConquestRouteEdge.OPEN);
        fixture.data.routeEdges.put(KOMEConquestRouteEdge.key(edge.fromTile, edge.toTile), edge);
        send(); assembled(take()); edge.edgeType = KOMEConquestRouteEdge.BLOCKED;
        send(); assertEquals(edge.writeToNBT(), assembled(take()).getTagList("RouteEdges", 10).getCompoundTagAt(0));
    }

    @Test public void ordinaryRecipientNeverReceivesEnemyCompanyOrMovementDetails() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        KOMEArmyCompany own = new KOMEArmyCompany(); own.id="OWN"; own.faction="gondor";
        own.nativeFaction="gondor"; own.currentTile="T132"; own.name="Guard";
        KOMEArmyCompany enemy = new KOMEArmyCompany(); enemy.id="ENEMY"; enemy.faction="mordor";
        enemy.nativeFaction="mordor"; enemy.currentTile="T149"; enemy.name="Hostile";
        enemy.ownerName="Private owner";
        fixture.data.armyCompanies.put(own.id,own); fixture.data.armyCompanies.put(enemy.id,enemy);
        KOMEArmyMovementOrder route = new KOMEArmyMovementOrder(); route.id="SECRET";
        route.companyId=enemy.id; route.ownerFaction=enemy.faction;
        route.originTile="T149"; route.destinationTile="T132";
        fixture.data.armyMovements.put(route.id,route);
        send(); NBTTagCompound visible=assembled(take());
        NBTTagList companies=visible.getTagList("ArmyCompanies",10);
        assertEquals(1,companies.tagCount());
        assertEquals("OWN",companies.getCompoundTagAt(0).getString("Id"));
        assertFalse(companies.getCompoundTagAt(0).hasKey("Owner"));
        assertFalse(companies.getCompoundTagAt(0).hasKey("Units"));
        assertEquals(0,visible.getTagList("ArmyMovements",10).tagCount());
    }

    @Test public void personalStationedCompanySpotsForeignCompanyOnlyInItsCanonicalTile() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        UUID viewer=fixture.player.getUniqueID();
        KOMEArmyCompany spotter=companyWithMember("SPOTTER","gondor","T132",viewer,30);
        KOMEArmyCompany observed=companyWithMember("OBSERVED","mordor","T132",UUID.randomUUID(),50);
        KOMEArmyCompany adjacent=companyWithMember("ADJACENT","mordor","T149",UUID.randomUUID(),70);
        NBTTagCompound first=assembled(sendAndTake());
        assertEquals(2,first.getTagList("ArmyCompanies",10).tagCount());
        assertTrue(companyIds(first).contains(spotter.id));
        assertTrue(hasSpottedFaction(first,"mordor"));
        assertFalse(companyIds(first).contains(observed.id));
        assertFalse(companyIds(first).contains(adjacent.id));
        assertEquals(30,companyRow(first,spotter.id).getInteger("Population"));
        assertFalse(spottedRow(first,"mordor").hasKey("Population"));
        assertFalse(spottedRow(first,"mordor").hasKey("Owner"));
        assertFalse(spottedRow(first,"mordor").hasKey("Units"));
        assertEquals(30,summary(first,"T132").getInteger("StationedPop"));
        assertEquals(0,summary(first,"T149").getInteger("StationedPop"));
        spotter.currentTile="T150";
        fixture.data.hiredUnits.get(spotter.units.get(0)).currentTile="T150";
        NBTTagCompound afterLeave=assembled(sendAndTake());
        assertFalse(hasSpottedFaction(afterLeave,"mordor"));
        assertEquals(0,summary(afterLeave,"T132").getInteger("StationedPop"));
    }

    @Test public void nonOperatorIsengardCannotReceiveUnobservedRohanFortyFiveCost() throws Exception {
        fixture.pledge(LOTRFaction.ISENGARD);
        assertFalse(fixture.player.canCommandSenderUseCommand(2, "troops"));
        KOMEConquestTile rohanTile = new KOMEConquestTile("T149");
        rohanTile.claim("rohan", 1); fixture.data.conquestTiles.put(rohanTile.id, rohanTile);
        UUID viewer = fixture.player.getUniqueID();
        KOMEArmyCompany own = companyWithMember("ISENGARD", "isengard", "T132", viewer, 20);
        KOMEHiredUnitRecord second = new KOMEHiredUnitRecord();
        second.entity = UUID.randomUUID(); second.owner = viewer;
        NBTTagCompound saved = second.writeToNBT(); saved.setString("UnitClass", "CAMPAIGN");
        second.readFromNBT(saved); second.companyId = own.id; second.currentTile = "T132"; second.cost = 20;
        fixture.data.hiredUnits.put(second.entity, second); own.units.add(second.entity);
        KOMEArmyCompany foreign = companyWithMember("ROHAN_PRIVATE", "rohan", "T149", UUID.randomUUID(), 45);

        List<KOMEPacketConquestData> outbound = sendAndTake();
        List<KOMEPacketConquestData> decoded = new ArrayList<KOMEPacketConquestData>();
        for (KOMEPacketConquestData packet : outbound) {
            io.netty.buffer.ByteBuf wire = io.netty.buffer.Unpooled.buffer();
            try {
                packet.toBytes(wire);
                KOMEPacketConquestData received = new KOMEPacketConquestData();
                received.fromBytes(wire);
                decoded.add(received);
            } finally { wire.release(); }
        }
        NBTTagCompound clientSnapshot = assembled(decoded);
        assertTrue(companyIds(clientSnapshot).contains(own.id));
        assertFalse(companyIds(clientSnapshot).contains(foreign.id));
        assertFalse(hasSpottedFaction(clientSnapshot, "rohan"));
        assertEquals(40, summary(clientSnapshot, "T132").getInteger("StationedPop"));
        KOMETileTroopSummary foreignSummary = new KOMETileTroopSummary();
        foreignSummary.readFromNBT(summary(clientSnapshot, "T149"));
        assertFalse("The aggregate number renderer must skip this tile", foreignSummary.hasAnyTroops());
        assertEquals(0, clientSnapshot.getTagList("ArmyMovements", 10).tagCount());

        List markers = KOMEPacketUnitMapMarkers.buildMarkers(fixture.data, viewer, "isengard", false,
            new net.minecraft.world.WorldServer[0]);
        KOMEPacketUnitMapMarkers outgoingMarkers = new KOMEPacketUnitMapMarkers(markers);
        io.netty.buffer.ByteBuf markerWire = io.netty.buffer.Unpooled.buffer();
        KOMEPacketUnitMapMarkers receivedMarkers = new KOMEPacketUnitMapMarkers();
        try { outgoingMarkers.toBytes(markerWire); receivedMarkers.fromBytes(markerWire); }
        finally { markerWire.release(); }
        assertEquals(1, receivedMarkers.markers.size());
        KOMEUnitMapMarker shield = (KOMEUnitMapMarker) receivedMarkers.markers.get(0);
        assertEquals(own.id, shield.entityId);
        assertEquals(2, shield.soldierCount);
        assertEquals(40, shield.population);
    }

    @Test public void spottedForeignCompanyExposesShieldButNoIdentityOrPopulationCost() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        UUID viewer=fixture.player.getUniqueID();
        companyWithMember("MINE","gondor","T132",viewer,30);
        KOMEArmyCompany enemy=companyWithMember("SECRET","mordor","T132",UUID.randomUUID(),50);
        for(int i=0;i<2;i++) {
            KOMEHiredUnitRecord member=new KOMEHiredUnitRecord();
            member.entity=UUID.randomUUID();member.owner=enemy.owner;
            NBTTagCompound saved=member.writeToNBT();saved.setString("UnitClass","CAMPAIGN");
            member.readFromNBT(saved);member.companyId=enemy.id;member.currentTile="T132";member.cost=20;
            fixture.data.hiredUnits.put(member.entity,member);enemy.units.add(member.entity);
        }
        enemy.name="Private enemy name";
        NBTTagCompound snapshot=assembled(sendAndTake());
        assertFalse(companyIds(snapshot).contains(enemy.id));
        NBTTagCompound spotted=new NBTTagCompound();
        for(int i=0;i<snapshot.getTagList("ArmyCompanies",10).tagCount();i++) {
            NBTTagCompound row=snapshot.getTagList("ArmyCompanies",10).getCompoundTagAt(i);
            if("mordor".equals(row.getString("Faction")))spotted=row;
        }
        assertFalse(spotted.getString("Id").isEmpty());
        assertFalse(spotted.hasKey("Name"));
        assertFalse(spotted.hasKey("Population"));
        assertEquals(30,summary(snapshot,"T132").getInteger("StationedPop"));
        List markers=KOMEPacketUnitMapMarkers.buildMarkers(fixture.data,viewer,"gondor",false,
            new net.minecraft.world.WorldServer[0]);
        assertEquals(2,markers.size());
        KOMEUnitMapMarker foreign=null;
        for(Object object:markers)if(spotted.getString("Id").equals(((KOMEUnitMapMarker)object).entityId))
            foreign=(KOMEUnitMapMarker)object;
        assertNotNull(foreign);
        assertEquals("",foreign.companyName);
        assertEquals("",foreign.unitName);
        assertEquals(0,foreign.population);
        assertEquals(0,foreign.unitCount);
        assertEquals(3,foreign.soldierCount);
        assertEquals(0.0,foreign.x,0.0);
        assertFalse(foreign.mounted);
    }

    @Test public void dualFactionSingleUnitTileDoesNotSerializeForeignCostAsFortyFive() throws Exception {
        fixture.pledge(LOTRFaction.ROHAN);
        UUID viewer=fixture.player.getUniqueID();
        KOMEArmyCompany own=companyWithMember("ROHAN_ONE","rohan","T132",viewer,45);
        KOMEArmyCompany foreign=companyWithMember("ISENGARD_ONE","isengard","T132",UUID.randomUUID(),50);
        NBTTagCompound snapshot=assembled(sendAndTake());
        assertEquals(45,summary(snapshot,"T132").getInteger("StationedPop"));
        assertEquals(45,companyRow(snapshot,own.id).getInteger("Population"));
        assertFalse(companyIds(snapshot).contains(foreign.id));
        assertFalse(spottedRow(snapshot,"isengard").hasKey("Population"));
        List markers=KOMEPacketUnitMapMarkers.buildMarkers(fixture.data,viewer,"rohan",false,
            new net.minecraft.world.WorldServer[0]);
        assertEquals(2,markers.size());
        for(Object object:markers){
            KOMEUnitMapMarker marker=(KOMEUnitMapMarker)object;
            assertEquals(1,marker.soldierCount);
            if(!own.id.equals(marker.entityId)){
                assertEquals(0,marker.population);
                assertEquals("",marker.companyName);
                assertEquals("",marker.meleeItemId);
                assertEquals("",marker.movementItemId);
            }
        }
    }

    @Test public void tileUnitsAndCompanyBrowsersDoNotUpgradeReconnaissanceToPrivateDetail() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        UUID viewer=fixture.player.getUniqueID();
        companyWithMember("MINE","gondor","T132",viewer,30);
        companyWithMember("SECRET","mordor","T132",UUID.randomUUID(),50);
        assertTrue(hasSpottedFaction(assembled(sendAndTake()),"mordor"));
        java.lang.reflect.Method units=kome.common.command.KOMECommandPopulation.class.getDeclaredMethod(
            "sendUnitBreakdown",net.minecraft.command.ICommandSender.class,
            net.minecraft.entity.player.EntityPlayerMP.class,String.class);
        units.setAccessible(true);
        int before=fixture.network.messages.size();
        units.invoke(new kome.common.command.KOMECommandPopulation(),fixture.player,fixture.player,"T132");
        KOMEPacketPopulationUnitsGui unitPacket=(KOMEPacketPopulationUnitsGui)fixture.network.messages.get(before);
        assertEquals(1,unitPacket.units.size());
        java.lang.reflect.Method companies=kome.common.command.KOMECommandTroops.class.getDeclaredMethod(
            "listCompanies",net.minecraft.command.ICommandSender.class,
            net.minecraft.entity.player.EntityPlayerMP.class,KOMEWorldData.class,UUID.class,String.class);
        companies.setAccessible(true);
        before=fixture.network.messages.size();
        companies.invoke(new kome.common.command.KOMECommandTroops(),fixture.player,fixture.player,
            fixture.data,viewer,"T132");
        KOMEPacketCompanyListGui browser=(KOMEPacketCompanyListGui)fixture.network.messages.get(before);
        assertEquals(1,browser.companies.size());
        assertEquals("MINE",browser.companies.get(0).id);
    }

    @Test public void formerOwnerCannotBrowseForeignCompanyAfterPledgeChange() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        UUID viewer=fixture.player.getUniqueID();
        KOMEArmyCompany former=companyWithMember("FORMER","gondor","T132",viewer,37);
        fixture.pledge(LOTRFaction.ROHAN);
        KOMEHiredUnitRecord ordinary=new KOMEHiredUnitRecord();ordinary.entity=UUID.randomUUID();
        ordinary.owner=viewer;ordinary.currentTile="T132";ordinary.cost=4;
        fixture.data.hiredUnits.put(ordinary.entity,ordinary);
        java.lang.reflect.Method units=kome.common.command.KOMECommandPopulation.class.getDeclaredMethod(
            "sendUnitBreakdown",net.minecraft.command.ICommandSender.class,
            net.minecraft.entity.player.EntityPlayerMP.class,String.class);
        units.setAccessible(true);
        int before=fixture.network.messages.size();
        units.invoke(new kome.common.command.KOMECommandPopulation(),fixture.player,fixture.player,"T132");
        KOMEPacketPopulationUnitsGui unitPacket=(KOMEPacketPopulationUnitsGui)fixture.network.messages.get(before);
        assertEquals("Normal hired units remain readable without foreign Campaign details",1,unitPacket.units.size());
        assertEquals(ordinary.entity.toString(),
            ((KOMEUnitGuiEntry)unitPacket.units.get(0)).entityId);
        java.lang.reflect.Method companies=kome.common.command.KOMECommandTroops.class.getDeclaredMethod(
            "listCompanies",net.minecraft.command.ICommandSender.class,
            net.minecraft.entity.player.EntityPlayerMP.class,KOMEWorldData.class,UUID.class,String.class);
        companies.setAccessible(true);before=fixture.network.messages.size();
        companies.invoke(new kome.common.command.KOMECommandTroops(),fixture.player,fixture.player,
            fixture.data,viewer,"T132");
        KOMEPacketCompanyListGui browser=(KOMEPacketCompanyListGui)fixture.network.messages.get(before);
        assertTrue("Former owner's company must not enter the catalogue",browser.companies.isEmpty());
        java.lang.reflect.Method detail=kome.common.command.KOMECommandTroops.class.getDeclaredMethod(
            "listCompany",net.minecraft.command.ICommandSender.class,net.minecraft.entity.player.EntityPlayerMP.class,
            KOMEWorldData.class,net.minecraft.world.World.class,String.class);
        detail.setAccessible(true);
        try {detail.invoke(new kome.common.command.KOMECommandTroops(),fixture.player,fixture.player,
            fixture.data,fixture.world,former.id);fail("Forged company ID exposed detail");}
        catch(java.lang.reflect.InvocationTargetException denied) {
            assertTrue(denied.getCause() instanceof net.minecraft.command.WrongUsageException);
        }
        kome.common.command.KOMECommandTroops troopCommand=new kome.common.command.KOMECommandTroops();
        fixture.player.messages.clear();troopCommand.processCommand(fixture.player,new String[]{"list","T132"});
        troopCommand.processCommand(fixture.player,new String[]{"tile","T132"});
        assertFalse("Older text summaries must not reveal former company cost",
            fixture.player.messages.toString().contains("37"));
        for(String[] forged:new String[][]{{"unit",former.units.get(0).toString()},
                {"locate","company",former.id},{"route","find",former.id,"T149"}}) {
            try {troopCommand.processCommand(fixture.player,forged);fail("Forged detail query succeeded");}
            catch(net.minecraft.command.WrongUsageException expected) { }
        }
        fixture.player.operator=true;before=fixture.network.messages.size();
        companies.invoke(new kome.common.command.KOMECommandTroops(),fixture.player,fixture.player,
            fixture.data,viewer,"T132");
        assertEquals("Operator retains administrative catalogue access",1,
            ((KOMEPacketCompanyListGui)fixture.network.messages.get(before)).companies.size());
        fixture.player.operator=false;fixture.pledge(LOTRFaction.GONDOR);before=fixture.network.messages.size();
        companies.invoke(new kome.common.command.KOMECommandTroops(),fixture.player,fixture.player,
            fixture.data,viewer,"T132");
        assertEquals("Current faction and owner regain existing detail access",1,
            ((KOMEPacketCompanyListGui)fixture.network.messages.get(before)).companies.size());
    }

    @Test public void unknownAndUnreadableCompanyDetailHaveIdenticalNonrevealingDenial() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        KOMEArmyCompany hidden=companyWithMember("HIDDEN_DETAIL","mordor","T132",UUID.randomUUID(),25);
        assertFalse(KOMECompanyMapProjection.view(fixture.data,fixture.player).directlyVisible(hidden));
        NBTTagCompound before=hidden.writeToNBT();
        fixture.data.setDirty(false);
        int packets=fixture.network.messages.size();
        String unknown=deniedCompanyDetail("NO_SUCH_COMPANY");
        String unreadable=deniedCompanyDetail(hidden.id);
        assertEquals("Company unavailable.",unknown);
        assertEquals(unknown,unreadable);
        assertFalse(unknown.contains(hidden.id));
        assertTrue(fixture.player.messages.isEmpty());
        assertEquals(packets,fixture.network.messages.size());
        assertEquals(before,hidden.writeToNBT());
        assertFalse(fixture.data.isDirty());
    }

    @Test public void spottingAndHistoricalOwnershipDoNotRevealCompanyDetailExistence() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        UUID viewer=fixture.player.getUniqueID();
        companyWithMember("SPOTTER_DETAIL","gondor","T132",viewer,30);
        KOMEArmyCompany foreign=companyWithMember("SPOTTED_DETAIL","mordor","T132",UUID.randomUUID(),50);
        assertTrue(KOMECompanyMapProjection.view(fixture.data,fixture.player).spotted(foreign));
        assertEquals("Company unavailable.",deniedCompanyDetail(foreign.id));
        KOMEArmyCompany former=companyWithMember("FORMER_DETAIL","gondor","T132",viewer,37);
        fixture.pledge(LOTRFaction.ROHAN);
        assertFalse(KOMECompanyMapProjection.view(fixture.data,fixture.player).directlyVisible(former));
        assertEquals("Company unavailable.",deniedCompanyDetail(former.id));
    }

    @Test public void currentOwnerAndOperatorStillReceiveCompanyDetail() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        KOMEArmyCompany owned=companyWithMember("OWN_DETAIL","gondor","T132",
            fixture.player.getUniqueID(),30);
        kome.common.command.KOMECommandTroops command=new kome.common.command.KOMECommandTroops();
        command.processCommand(fixture.player,new String[]{"company",owned.id});
        assertTrue(fixture.player.messages.toString().contains(owned.id+" - "+owned.name));
        fixture.player.messages.clear();
        KOMEArmyCompany foreign=companyWithMember("ADMIN_DETAIL","mordor","T132",UUID.randomUUID(),50);
        fixture.player.operator=true;
        command.processCommand(fixture.player,new String[]{"company",foreign.id});
        assertTrue(fixture.player.messages.toString().contains(foreign.id+" - "+foreign.name));
    }

    private String deniedCompanyDetail(String id) {
        try {
            new kome.common.command.KOMECommandTroops().processCommand(fixture.player,new String[]{"company",id});
            fail("Private company detail unexpectedly returned");
            return "";
        } catch(net.minecraft.command.WrongUsageException denied) {
            return denied.getMessage();
        }
    }

    @Test public void spottedAliasesDoNotShiftToAnotherCompanyAfterDeparture() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        UUID viewer=fixture.player.getUniqueID();
        companyWithMember("SPOTTER","gondor","T132",viewer,30);
        KOMEArmyCompany first=companyWithMember("A","mordor","T132",UUID.randomUUID(),50);
        KOMEArmyCompany second=companyWithMember("B","mordor","T132",UUID.randomUUID(),70);
        KOMECompanyMapProjection.View before=KOMECompanyMapProjection.view(fixture.data,viewer,"gondor",false);
        String firstAlias=before.presentationId(first),secondAlias=before.presentationId(second);
        assertNotEquals(firstAlias,secondAlias);
        List originalMarkers=KOMEPacketUnitMapMarkers.buildMarkers(fixture.data,viewer,"gondor",false,
            new net.minecraft.world.WorldServer[0]);
        assertTrue(originalMarkers.stream().anyMatch(m->firstAlias.equals(((KOMEUnitMapMarker)m).entityId)));
        assertTrue(originalMarkers.stream().anyMatch(m->secondAlias.equals(((KOMEUnitMapMarker)m).entityId)));
        fixture.data.armyCompanies.remove(first.id);
        KOMECompanyMapProjection.View after=KOMECompanyMapProjection.view(fixture.data,viewer,"gondor",false);
        assertEquals("Surviving company's marker identity must not shift",secondAlias,after.presentationId(second));
        assertNotEquals("Departed company's alias must not be reassigned",firstAlias,after.presentationId(second));
        NBTTagCompound replacement=assembled(sendAndTake());
        assertTrue(companyIds(replacement).contains(secondAlias));
        assertFalse(companyIds(replacement).contains(firstAlias));
        List replacementMarkers=KOMEPacketUnitMapMarkers.buildMarkers(fixture.data,viewer,"gondor",false,
            new net.minecraft.world.WorldServer[0]);
        assertTrue(replacementMarkers.stream().anyMatch(m->secondAlias.equals(((KOMEUnitMapMarker)m).entityId)));
        assertFalse(replacementMarkers.stream().anyMatch(m->firstAlias.equals(((KOMEUnitMapMarker)m).entityId)));
        KOMEArmyCompany spotter=fixture.data.armyCompanies.get("SPOTTER");
        spotter.currentTile="T149";fixture.data.hiredUnits.get(spotter.units.get(0)).currentTile="T149";
        second.currentTile="T149";fixture.data.hiredUnits.get(second.units.get(0)).currentTile="T149";
        assertNotEquals("A move to a different observed tile gets a new opaque key",secondAlias,
            KOMECompanyMapProjection.view(fixture.data,viewer,"gondor",false).presentationId(second));
    }

    @Test public void committedIntermediateArrivalRetainsReconnaissanceMarkerAndSingleTileTotal() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        KOMEArmyCompany spotter=companyWithMember("WAITING","gondor","T132",fixture.player.getUniqueID(),30);
        KOMEArmyCompany enemy=companyWithMember("FOREIGN","mordor","T132",UUID.randomUUID(),50);
        KOMEArmyMovementOrder order=new KOMEArmyMovementOrder();
        order.id="STEP";order.companyId=spotter.id;order.ownerFaction=spotter.faction;
        order.status=KOMEArmyMovementOrder.WAITING_NEXT_STEP;
        order.currentTile="T132";order.currentStepOriginTile="T132";order.nextTile="T149";
        order.units.add(spotter.units.get(0));fixture.data.armyMovements.put(order.id,order);
        spotter.status=KOMEArmyCompany.MOVING;spotter.movementOrderId=order.id;
        KOMEHiredUnitRecord member=fixture.data.hiredUnits.get(spotter.units.get(0));
        member.movementOrderId=order.id;member.stationedEntityData=new NBTTagCompound();
        NBTTagCompound snapshot=assembled(sendAndTake());
        assertTrue(hasSpottedFaction(snapshot,"mordor"));
        assertEquals(30,companyRow(snapshot,spotter.id).getInteger("Population"));
        assertEquals(KOMEArmyCompany.STATIONED,companyRow(snapshot,spotter.id).getString("Status"));
        assertEquals(30,summary(snapshot,"T132").getInteger("StationedPop"));
        assertEquals(1,snapshot.getTagList("ArmyMovements",10).tagCount());
        java.lang.reflect.Method summaries=KOMEPacketConquestData.class.getDeclaredMethod(
            "buildTroopSummaries",KOMEWorldData.class,KOMECompanyMapProjection.View.class,boolean.class);
        summaries.setAccessible(true);
        java.util.Map adminRows=(java.util.Map)summaries.invoke(null,fixture.data,
            KOMECompanyMapProjection.view(fixture.data,fixture.player.getUniqueID(),"gondor",true),true);
        assertEquals(80,((KOMETileTroopSummary)adminRows.get("T132")).stationedPop);
        KOMEHiredUnitRecord independent=new KOMEHiredUnitRecord();independent.entity=UUID.randomUUID();
        independent.owner=fixture.player.getUniqueID();
        NBTTagCompound independentTag=independent.writeToNBT();independentTag.setString("UnitClass","CAMPAIGN");
        independent.readFromNBT(independentTag);independent.currentTile="T132";independent.cost=7;
        fixture.data.hiredUnits.put(independent.entity,independent);
        java.util.Map withIndependent=(java.util.Map)summaries.invoke(null,fixture.data,
            KOMECompanyMapProjection.view(fixture.data,fixture.player.getUniqueID(),"gondor",true),true);
        assertEquals(87,((KOMETileTroopSummary)withIndependent.get("T132")).stationedPop);
        fixture.data.hiredUnits.remove(independent.entity);
        KOMEPacketUnitMapMarkers outbound=new KOMEPacketUnitMapMarkers(
            KOMEPacketUnitMapMarkers.buildMarkers(fixture.data,fixture.player.getUniqueID(),"gondor",false,
                new net.minecraft.world.WorldServer[0]));
        io.netty.buffer.ByteBuf wire=io.netty.buffer.Unpooled.buffer();outbound.toBytes(wire);
        KOMEPacketUnitMapMarkers decoded=new KOMEPacketUnitMapMarkers();decoded.fromBytes(wire);wire.release();
        assertEquals(2,decoded.markers.size());
        assertTrue(decoded.markers.stream().anyMatch(m -> "WAITING".equals(((KOMEUnitMapMarker)m).entityId)
            && ((KOMEUnitMapMarker)m).population==30));
        member.movingEntityData=new NBTTagCompound();order.status=KOMEArmyMovementOrder.MOVING;
        NBTTagCompound departed=assembled(sendAndTake());
        assertFalse(hasSpottedFaction(departed,"mordor"));
        assertEquals(0,summary(departed,"T132").getInteger("StationedPop"));
        assertTrue(KOMEPacketUnitMapMarkers.buildMarkers(fixture.data,fixture.player.getUniqueID(),"gondor",false,
            new net.minecraft.world.WorldServer[0]).isEmpty());
    }

    @Test public void waitingPhaseRequiresCommittedLocalMembersAndRevokesOnTransitOrRemoval() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        KOMEArmyCompany mine=companyWithMember("MINE","gondor","T132",fixture.player.getUniqueID(),30);
        KOMEArmyCompany enemy=companyWithMember("ENEMY","mordor","T132",UUID.randomUUID(),50);
        KOMEHiredUnitRecord member=fixture.data.hiredUnits.get(mine.units.get(0));
        KOMEArmyMovementOrder order=new KOMEArmyMovementOrder();order.id="MARCH";
        order.companyId=mine.id;order.ownerFaction=mine.faction;order.currentTile="T132";
        order.status=KOMEArmyMovementOrder.WAITING_NEXT_STEP;order.units.add(member.entity);
        fixture.data.armyMovements.put(order.id,order);
        mine.status=KOMEArmyCompany.MOVING;mine.movementOrderId=order.id;
        member.movementOrderId=order.id;
        assertFalse(hasSpottedFaction(assembled(sendAndTake()),"mordor")); // no committed local snapshot yet
        member.stationedEntityData=new NBTTagCompound();
        for(String presentPhase:new String[]{KOMEArmyMovementOrder.WAITING_NEXT_STEP,
                KOMEArmyMovementOrder.ACCESS_HALTED,KOMEArmyMovementOrder.HOLDING,
                KOMEArmyMovementOrder.CONFLICT_HELD}) {
            order.status=presentPhase;
            order.spawnRetryPaused=true; // blocked next-step departure has not removed local members
            NBTTagCompound visible=assembled(sendAndTake());
            assertTrue(presentPhase,hasSpottedFaction(visible,"mordor"));
            assertEquals(presentPhase,30,summary(visible,"T132").getInteger("StationedPop"));
        }
        order.status=KOMEArmyMovementOrder.WAITING_NEXT_STEP;
        order.currentTile="T149";
        assertFalse(hasSpottedFaction(assembled(sendAndTake()),"mordor"));
        order.currentTile="T132";order.status=KOMEArmyMovementOrder.MOVING;
        member.movingEntityData=new NBTTagCompound();
        assertFalse(hasSpottedFaction(assembled(sendAndTake()),"mordor"));
        order.status=KOMEArmyMovementOrder.PENDING_SPAWN;
        assertFalse(KOMECompanyMapProjection.view(fixture.data,fixture.player.getUniqueID(),"gondor",false).visible(enemy));
        order.status=KOMEArmyMovementOrder.SPAWN_BLOCKED;
        assertFalse(KOMECompanyMapProjection.view(fixture.data,fixture.player.getUniqueID(),"gondor",false).visible(enemy));
        order.status=KOMEArmyMovementOrder.WAITING_NEXT_STEP;member.movingEntityData=null;
        assertTrue(hasSpottedFaction(assembled(sendAndTake()),"mordor"));
        order.status=KOMEArmyMovementOrder.RETREATING;
        assertFalse(hasSpottedFaction(assembled(sendAndTake()),"mordor"));
        fixture.data.armyMovements.remove(order.id);mine.movementOrderId="";mine.status=KOMEArmyCompany.STATIONED;
        member.movementOrderId="";
        assertTrue(hasSpottedFaction(assembled(sendAndTake()),"mordor"));
        fixture.data.armyCompanies.remove(mine.id);
        assertFalse(hasSpottedFaction(assembled(sendAndTake()),"mordor"));
    }

    @Test public void factionPeerDoesNotBecomeASpotterAndEnemyRouteRemainsPrivateOnObservedTile() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        companyWithMember("PEER","gondor","T132",UUID.randomUUID(),30);
        KOMEArmyCompany enemy=companyWithMember("ENEMY","mordor","T132",UUID.randomUUID(),50);
        NBTTagCompound peer=assembled(sendAndTake());
        assertFalse(companyIds(peer).contains(enemy.id));
        assertEquals(30,summary(peer,"T132").getInteger("StationedPop"));
        KOMEArmyCompany spotter=companyWithMember("MINE","gondor","T132",fixture.player.getUniqueID(),5);
        KOMEArmyMovementOrder route=new KOMEArmyMovementOrder();route.id="SECRET";
        route.companyId=enemy.id;route.ownerFaction=enemy.faction;
        route.originTile="T132";route.destinationTile="T149";
        route.routeTiles.add("T132");route.routeTiles.add("T149");route.arrivalMillis=1234567L;
        fixture.data.armyMovements.put(route.id,route);
        NBTTagCompound observed=assembled(sendAndTake());
        assertTrue(hasSpottedFaction(observed,"mordor"));
        assertEquals(35,summary(observed,"T132").getInteger("StationedPop"));
        assertEquals(0,observed.getTagList("ArmyMovements",10).tagCount());
        spotter.movementOrderId="MARCH";
        NBTTagCompound departed=assembled(sendAndTake());
        assertFalse(hasSpottedFaction(departed,"mordor"));
        assertEquals(30,summary(departed,"T132").getInteger("StationedPop"));
    }

    @Test public void terminalSpotterPledgeLossAndForeignDepartureRevokeReconnaissance() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        KOMEArmyCompany spotter=companyWithMember("MINE","gondor","T132",fixture.player.getUniqueID(),30);
        KOMEArmyCompany enemy=companyWithMember("ENEMY","mordor","T132",UUID.randomUUID(),50);
        assertTrue(hasSpottedFaction(assembled(sendAndTake()),"mordor"));
        KOMEHiredUnitRecord member=fixture.data.hiredUnits.get(spotter.units.get(0));
        member.populationReturned=true;
        assertFalse(hasSpottedFaction(assembled(sendAndTake()),"mordor"));
        member.populationReturned=false;
        assertTrue(hasSpottedFaction(assembled(sendAndTake()),"mordor"));
        fixture.pledge(null);
        assertFalse(hasSpottedFaction(assembled(sendAndTake()),"mordor"));
        fixture.pledge(LOTRFaction.GONDOR);
        assertTrue(hasSpottedFaction(assembled(sendAndTake()),"mordor"));
        enemy.currentTile="T149";
        fixture.data.hiredUnits.get(enemy.units.get(0)).currentTile="T149";
        assertFalse(hasSpottedFaction(assembled(sendAndTake()),"mordor"));
        fixture.data.armyCompanies.remove(spotter.id);
        assertFalse(hasSpottedFaction(assembled(sendAndTake()),"mordor"));
    }

    @Test public void lastOfMultiplePersonalSpottersControlsTileRevocation() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        UUID viewer=fixture.player.getUniqueID();
        KOMEArmyCompany first=companyWithMember("FIRST","gondor","T132",viewer,10);
        KOMEArmyCompany second=companyWithMember("SECOND","gondor","T132",viewer,20);
        companyWithMember("RED","mordor","T132",UUID.randomUUID(),30);
        companyWithMember("WHITE","rohan","T132",UUID.randomUUID(),40);
        NBTTagCompound observed=assembled(sendAndTake());
        assertEquals(4,companyIds(observed).size());
        assertEquals(30,summary(observed,"T132").getInteger("StationedPop"));
        first.movementOrderId="LEFT";
        assertEquals(4,companyIds(assembled(sendAndTake())).size());
        second.movementOrderId="LEFT";
        NBTTagCompound revoked=assembled(sendAndTake());
        assertEquals(2,companyIds(revoked).size());
        assertFalse(hasSpottedFaction(revoked,"mordor"));
        assertFalse(hasSpottedFaction(revoked,"rohan"));
        assertEquals(0,summary(revoked,"T132").getInteger("StationedPop"));
    }

    private KOMEArmyCompany companyWithMember(String id,String faction,String tile,UUID owner,int population) {
        KOMEArmyCompany company=new KOMEArmyCompany();
        company.id=id;company.name=id;company.faction=faction;company.nativeFaction=faction;
        company.currentTile=tile;company.owner=owner;
        fixture.data.armyCompanies.put(id,company);
        KOMEHiredUnitRecord member=new KOMEHiredUnitRecord();
        member.entity=UUID.randomUUID();member.owner=owner;
        NBTTagCompound saved=member.writeToNBT();saved.setString("UnitClass","CAMPAIGN");
        member.readFromNBT(saved);member.companyId=id;member.currentTile=tile;member.cost=population;
        fixture.data.hiredUnits.put(member.entity,member);company.units.add(member.entity);
        return company;
    }

    private static java.util.Set<String> companyIds(NBTTagCompound snapshot) {
        java.util.Set<String> ids=new java.util.HashSet<String>();
        NBTTagList rows=snapshot.getTagList("ArmyCompanies",10);
        for(int i=0;i<rows.tagCount();i++)ids.add(rows.getCompoundTagAt(i).getString("Id"));
        return ids;
    }

    private static NBTTagCompound companyRow(NBTTagCompound snapshot,String id) {
        NBTTagList rows=snapshot.getTagList("ArmyCompanies",10);
        for(int i=0;i<rows.tagCount();i++)if(id.equals(rows.getCompoundTagAt(i).getString("Id")))
            return rows.getCompoundTagAt(i);
        return new NBTTagCompound();
    }

    private static boolean hasSpottedFaction(NBTTagCompound snapshot,String faction) {
        return !spottedRow(snapshot,faction).getString("Id").isEmpty();
    }

    private static NBTTagCompound spottedRow(NBTTagCompound snapshot,String faction) {
        NBTTagList rows=snapshot.getTagList("ArmyCompanies",10);
        for(int i=0;i<rows.tagCount();i++) {
            NBTTagCompound row=rows.getCompoundTagAt(i);
            if(faction.equals(row.getString("Faction"))
                    && KOMECompanyMapProjection.SPOTTED_STATUS.equals(row.getString("Status")))return row;
        }
        return new NBTTagCompound();
    }

    private static NBTTagCompound summary(NBTTagCompound snapshot,String tile) {
        NBTTagList rows=snapshot.getTagList("TroopSummaries",10);
        for(int i=0;i<rows.tagCount();i++)if(tile.equals(rows.getCompoundTagAt(i).getString("Tile")))
            return rows.getCompoundTagAt(i);
        return new NBTTagCompound();
    }

    @Test public void enemyMovementRouteAndTimingAreAbsentFromRecipientWireRows() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        KOMEArmyCompany enemy=new KOMEArmyCompany();enemy.id="ENEMY_ROUTE_COMPANY";
        enemy.faction="mordor";enemy.currentTile="T149";
        fixture.data.armyCompanies.put(enemy.id,enemy);
        KOMEArmyMovementOrder route=new KOMEArmyMovementOrder();route.id="ENEMY_ROUTE";
        route.companyId=enemy.id;route.companyName="Secret march";route.ownerFaction="mordor";
        route.originTile="T149";route.destinationTile="T132";
        route.routeTiles.add("T149");route.routeTiles.add("T132");route.arrivalMillis=987654321L;
        fixture.data.armyMovements.put(route.id,route);
        NBTTagCompound projection=assembled(sendAndTake());
        assertEquals(0,projection.getTagList("ArmyMovements",10).tagCount());
        assertEquals(0,projection.getTagList("ArmyCompanies",10).tagCount());
    }

    @Test public void peersReceiveOnlyPresentationRowsAndMovementForTheirFaction() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        KOMEArmyCompany company = new KOMEArmyCompany(); company.id="PEER";
        company.name="White Guard"; company.faction="gondor"; company.nativeFaction="rohan";
        company.currentTile="T132"; company.owner=UUID.randomUUID(); company.ownerName="secret owner";
        company.temporaryController=UUID.randomUUID(); company.units.add(UUID.randomUUID());
        fixture.data.armyCompanies.put(company.id,company);
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder(); order.id="PEER_ROUTE";
        order.companyId=company.id; order.companyName=company.name; order.ownerFaction="gondor";
        order.owner=company.owner; order.ownerName=company.ownerName; order.originTile="T132";
        order.destinationTile="T149"; order.routeTiles.add("T132"); order.routeTiles.add("T149");
        order.arrivalMillis=1234567L; order.mountedUnits=2; order.groundUnits=3;
        order.units.add(UUID.randomUUID()); fixture.data.armyMovements.put(order.id,order);
        NBTTagCompound projection=assembled(sendAndTake());
        NBTTagCompound row=projection.getTagList("ArmyCompanies",10).getCompoundTagAt(0);
        assertEquals("PEER",row.getString("Id")); assertEquals("gondor",row.getString("Faction"));
        assertEquals("rohan",row.getString("NativeFaction"));
        for(String privateKey:new String[]{"Owner","OwnerName","Units","TemporaryController",
                "ControllerAuthority","MovementAllowance","AuthorizedWars","TransferRecipient"})
            assertFalse(privateKey,row.hasKey(privateKey));
        NBTTagCompound movement=projection.getTagList("ArmyMovements",10).getCompoundTagAt(0);
        assertEquals("PEER_ROUTE",movement.getString("Id"));
        assertEquals(2,movement.getTagList("RouteTiles",10).tagCount());
        assertEquals(1234567L,movement.getLong("ArrivalMillis"));
        for(String privateKey:new String[]{"Owner","OwnerName","Units","AccessLossReason",
                "MovementAllowance","ConflictMovementRelease","StrategicArrival"})
            assertFalse(privateKey,movement.hasKey(privateKey));
        KOMEArmyCompany decoded=KOMECompanyMapProjection.readCompany(row);
        assertNull(decoded.owner); assertTrue(decoded.units.isEmpty());
        KOMEArmyMovementOrder decodedMovement=KOMECompanyMapProjection.readMovement(movement);
        assertTrue(decodedMovement.units.isEmpty()); assertEquals(5,decodedMovement.mountedUnits+decodedMovement.groundUnits);
    }

    @Test public void pledgeAndCompanyFactionChangesReplaceVisibleRows() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        KOMEArmyCompany company=new KOMEArmyCompany(); company.id="REVOKED";
        company.faction="gondor"; company.currentTile="T132";
        fixture.data.armyCompanies.put(company.id,company);
        assertEquals(1,assembled(sendAndTake()).getTagList("ArmyCompanies",10).tagCount());
        company.faction="mordor";
        assertEquals(0,assembled(sendAndTake()).getTagList("ArmyCompanies",10).tagCount());
        company.faction="gondor";
        assertEquals(1,assembled(sendAndTake()).getTagList("ArmyCompanies",10).tagCount());
        fixture.pledge(null);
        assertEquals(0,assembled(sendAndTake()).getTagList("ArmyCompanies",10).tagCount());
        fixture.pledge(LOTRFaction.GONDOR);
        assertEquals(1,assembled(sendAndTake()).getTagList("ArmyCompanies",10).tagCount());
        fixture.data.armyCompanies.remove(company.id);
        assertEquals(0,assembled(sendAndTake()).getTagList("ArmyCompanies",10).tagCount());
    }

    @Test public void operatorMapVisionRequiresExplicitSessionOptIn() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        KOMEArmyCompany enemy=new KOMEArmyCompany(); enemy.id="ENEMY"; enemy.faction="mordor";
        enemy.currentTile="T132"; enemy.owner=fixture.player.getUniqueID();
        fixture.data.armyCompanies.put(enemy.id,enemy);
        assertEquals(0,assembled(sendAndTake()).getTagList("ArmyCompanies",10).tagCount());
        fixture.player.operator=true;
        assertTrue(sendAndTake().isEmpty());
        assertTrue(KOMEAdminMapVision.setEnabled(fixture.player,true));
        assertEquals(1,assembled(sendAndTake()).getTagList("ArmyCompanies",10).tagCount());
        assertTrue(KOMEAdminMapVision.setEnabled(fixture.player,false));
        assertEquals(0,assembled(sendAndTake()).getTagList("ArmyCompanies",10).tagCount());
        assertTrue(KOMEAdminMapVision.setEnabled(fixture.player,true));
        assertEquals(1,assembled(sendAndTake()).getTagList("ArmyCompanies",10).tagCount());
        fixture.player.operator=false;
        assertEquals(0,assembled(sendAndTake()).getTagList("ArmyCompanies",10).tagCount());
        fixture.player.operator=true;
        assertTrue(sendAndTake().isEmpty());
    }

    @Test public void operatorStandalonePopulationIsRemovedByFilteredReplacement() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        KOMEHiredUnitRecord independent=new KOMEHiredUnitRecord();independent.entity=UUID.randomUUID();
        independent.owner=fixture.player.getUniqueID();independent.type=KOMEPopulationType.OFFENSIVE;
        NBTTagCompound tag=independent.writeToNBT();tag.setString("UnitClass","CAMPAIGN");
        independent.readFromNBT(tag);independent.currentTile="T132";independent.cost=45;
        fixture.data.hiredUnits.put(independent.entity,independent);
        fixture.player.operator=true;
        NBTTagCompound normal=assembled(sendAndTake());
        assertEquals(0,summary(normal,"T132").getInteger("StationedPop"));
        assertTrue(KOMEAdminMapVision.setEnabled(fixture.player,true));
        assertEquals(45,summary(assembled(sendAndTake()),"T132").getInteger("StationedPop"));
        fixture.player.operator=false;
        assertEquals(0,summary(assembled(sendAndTake()),"T132").getInteger("StationedPop"));
        assertFalse(KOMEAdminMapVision.enabled(fixture.player));
    }

    @Test public void permissionLossAdvancesPastQueuedPrivilegedMapDetailSerial() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        companyWithMember("PRIVILEGED_DETAIL","mordor","T132",UUID.randomUUID(),45);
        fixture.player.operator=true;
        assertTrue(KOMEAdminMapVision.setEnabled(fixture.player,true));
        KOMEPacketConquestOpenCapture.sendTileCommand(fixture.player,"T132");
        KOMEPacketConquestCaptureGui privileged=(KOMEPacketConquestCaptureGui)fixture.network.messages.get(
            fixture.network.messages.size()-1);
        assertTrue(privileged.mapPublicationSerial>0L);
        fixture.network.messages.clear();
        fixture.player.operator=false;
        KOMEPacketConquestData.sendIfChanged(fixture.data,fixture.player);
        List<KOMEPacketConquestData> replacement=take();
        assertFalse(replacement.isEmpty());
        assertTrue(replacement.get(0).publicationSerial>privileged.mapPublicationSerial);
        assertEquals(0,assembled(replacement).getTagList("ArmyCompanies",10).tagCount());
    }

    @Test public void mapBrowserVisionDoesNotRemoveExplicitOperatorCommandAccess() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        companyWithMember("FOREIGN_MAP_LIST","mordor","T132",UUID.randomUUID(),45);
        fixture.player.operator=true;
        KOMEPacketTroopGuiAction mapList=new KOMEPacketTroopGuiAction("list","","","T132");
        mapList.mapRequestId=71L;
        new KOMEPacketTroopGuiAction.Handler().onMessage(mapList,fixture.context);
        KOMEPacketCompanyListGui normal=(KOMEPacketCompanyListGui)fixture.network.messages.get(
            fixture.network.messages.size()-1);
        assertTrue(normal.companies.isEmpty());
        assertEquals(71L,normal.mapRequestId);
        assertTrue("Map response must reference a preceding conquest publication",normal.mapPublicationSerial>0L);
        boolean matchingSnapshot=false;
        for(Object sent:fixture.network.messages)
            if(sent instanceof KOMEPacketConquestData
                    && ((KOMEPacketConquestData)sent).publicationSerial==normal.mapPublicationSerial)
                matchingSnapshot=true;
        assertTrue(matchingSnapshot);
        fixture.network.messages.clear();
        new kome.common.command.KOMECommandTroops().processCommand(fixture.player,
            new String[]{"companies","T132"});
        assertEquals(1,((KOMEPacketCompanyListGui)fixture.network.messages.get(0)).companies.size());
        assertEquals("Explicit operator command is independent of map vision",0L,
            ((KOMEPacketCompanyListGui)fixture.network.messages.get(0)).mapPublicationSerial);
        assertTrue(KOMEAdminMapVision.setEnabled(fixture.player,true));
        fixture.network.messages.clear();
        new KOMEPacketTroopGuiAction.Handler().onMessage(
            new KOMEPacketTroopGuiAction("list","","","T132"),fixture.context);
        KOMEPacketCompanyListGui privileged=(KOMEPacketCompanyListGui)fixture.network.messages.get(
            fixture.network.messages.size()-1);
        assertEquals(1,privileged.companies.size());
        assertTrue(privileged.mapPublicationSerial>normal.mapPublicationSerial);
    }

    @Test public void captureAndMapUnitsResponsesReferenceCurrentServerPublication() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        KOMEPacketConquestOpenCapture.sendTileCommand(fixture.player,"T132","",72L);
        KOMEPacketConquestCaptureGui capture=(KOMEPacketConquestCaptureGui)fixture.network.messages.get(
            fixture.network.messages.size()-1);
        assertTrue(capture.mapPublicationSerial>0L);
        assertEquals(72L,capture.mapRequestId);
        boolean preceding=false;
        for(Object sent:fixture.network.messages)
            if(sent instanceof KOMEPacketConquestData
                    && ((KOMEPacketConquestData)sent).publicationSerial==capture.mapPublicationSerial)
                preceding=true;
        assertTrue(preceding);
        fixture.network.messages.clear();
        KOMEPacketTroopGuiAction mapUnits=new KOMEPacketTroopGuiAction("population_units","","","T132");
        mapUnits.mapRequestId=73L;
        new KOMEPacketTroopGuiAction.Handler().onMessage(mapUnits,fixture.context);
        KOMEPacketPopulationUnitsGui units=(KOMEPacketPopulationUnitsGui)fixture.network.messages.get(
            fixture.network.messages.size()-1);
        assertEquals(capture.mapPublicationSerial,units.mapPublicationSerial);
        assertEquals(73L,units.mapRequestId);
        fixture.network.messages.clear();
        new kome.common.command.KOMECommandPopulation().processCommand(fixture.player,new String[]{"units"});
        assertEquals(0L,((KOMEPacketPopulationUnitsGui)fixture.network.messages.get(0)).mapPublicationSerial);
        fixture.player.operator=true;
        assertTrue(KOMEAdminMapVision.setEnabled(fixture.player,true));
        fixture.network.messages.clear();
        KOMEPacketConquestOpenCapture.sendTileCommand(fixture.player,"T132");
        KOMEPacketConquestCaptureGui privileged=(KOMEPacketConquestCaptureGui)fixture.network.messages.get(
            fixture.network.messages.size()-1);
        assertTrue(privileged.mapPublicationSerial>capture.mapPublicationSerial);
    }

    @Test public void administrativeVisionIsConnectionScopedAndCannotBeEnabledByNonOperator() throws Exception {
        assertFalse(KOMEAdminMapVision.setEnabled(fixture.player,true));
        fixture.player.operator=true;
        assertTrue(KOMEAdminMapVision.setEnabled(fixture.player,true));
        assertTrue(KOMEAdminMapVision.enabled(fixture.player));
        fixture.player.operator=false;
        assertFalse(KOMEAdminMapVision.enabled(fixture.player));
        fixture.player.operator=true;
        assertFalse("A privilege loss observed by a query cannot be undone by OP regain",
            KOMEAdminMapVision.enabled(fixture.player));
        assertTrue(KOMEAdminMapVision.revokeIfUnauthorized(fixture.player));
        assertFalse(KOMEAdminMapVision.enabled(fixture.player));
        assertTrue(KOMEAdminMapVision.setEnabled(fixture.player,true));
        KOMEAdminMapVision.forget(fixture.player);
        assertFalse(KOMEAdminMapVision.enabled(fixture.player));
    }

    @Test public void administrativeMarkerProjectionTracksSessionVisionAndCurrentPermission() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        companyWithMember("ENEMY_MARKER","mordor","T132",UUID.randomUUID(),45);
        fixture.player.operator=true;
        net.minecraft.world.WorldServer[] noLoadedEntities=new net.minecraft.world.WorldServer[0];
        assertTrue(KOMEPacketUnitMapMarkers.buildMarkers(fixture.data,fixture.player.getUniqueID(),
            "gondor",KOMEAdminMapVision.enabled(fixture.player),noLoadedEntities).isEmpty());
        assertTrue(KOMEAdminMapVision.setEnabled(fixture.player,true));
        List visible=KOMEPacketUnitMapMarkers.buildMarkers(fixture.data,fixture.player.getUniqueID(),
            "gondor",KOMEAdminMapVision.enabled(fixture.player),noLoadedEntities);
        assertEquals(1,visible.size());
        KOMEPacketUnitMapMarkers packet=new KOMEPacketUnitMapMarkers(visible);
        io.netty.buffer.ByteBuf wire=io.netty.buffer.Unpooled.buffer();
        try {packet.toBytes(wire);KOMEPacketUnitMapMarkers decoded=new KOMEPacketUnitMapMarkers();
            decoded.fromBytes(wire);assertEquals(1,decoded.markers.size());}
        finally {wire.release();}
        fixture.player.operator=false;
        assertTrue(KOMEPacketUnitMapMarkers.buildMarkers(fixture.data,fixture.player.getUniqueID(),
            "gondor",KOMEAdminMapVision.enabled(fixture.player),noLoadedEntities).isEmpty());
    }


    @Test public void onlyCurrentCanonicalDelegationAddsForeignCompanyVisibility() {
        KOMEArmyCompany company=companyWithMember("DELEGATED","mordor","T132",UUID.randomUUID(),25);
        KOMEArmyCompany foreign=companyWithMember("FOREIGN","gondor","T132",UUID.randomUUID(),50);
        UUID viewer=fixture.player.getUniqueID();
        fixture.data.lastKnownPlayerFactions.put(viewer,"");
        assertFalse(KOMECompanyMapProjection.visible(fixture.data,company,viewer,"",false));
        company.temporaryController=viewer;
        company.controllerAuthority=KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE;
        assertTrue(KOMECompanyMapProjection.visible(fixture.data,company,viewer,"",false));
        assertTrue(KOMECompanyMapProjection.view(fixture.data,viewer,"",false).visible(foreign));
        company.controllerAuthority=KOMEArmyCompany.AUTHORITY_NATIVE;
        assertFalse(KOMECompanyMapProjection.visible(fixture.data,company,viewer,"",false));
        assertFalse(KOMECompanyMapProjection.view(fixture.data,viewer,"",false).visible(foreign));
        company.controllerAuthority=KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE;
        fixture.data.lastKnownPlayerFactions.put(viewer,"gondor");
        assertTrue("Current delegated command authority also grants read visibility",
            KOMECompanyMapProjection.visible(fixture.data,company,viewer,"",false));
        assertTrue(KOMECompanyMapProjection.visible(fixture.data,company,viewer,"gondor",false));
        company.temporaryController=null;
        assertFalse(KOMECompanyMapProjection.visible(fixture.data,company,viewer,"gondor",false));
    }

    @Test public void recognizedNativeRulerCanReadCompanyTheyMayAdministrateAcrossStrategicFaction() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        UUID viewer=fixture.player.getUniqueID();
        KOMEArmyCompany company=companyWithMember("RULER_VIEW","mordor","T132",UUID.randomUUID(),25);
        company.nativeFaction="gondor";
        assertFalse(KOMECompanyMapProjection.view(fixture.data,fixture.player).directlyVisible(company));
        assertTrue(KOMERulerService.assignRuler(fixture.data,"gondor",viewer,"Ruler"));
        assertTrue(KOMECompanyMapProjection.view(fixture.data,fixture.player).directlyVisible(company));
        fixture.pledge(LOTRFaction.ROHAN);
        assertFalse(KOMECompanyMapProjection.view(fixture.data,fixture.player).directlyVisible(company));
    }

    @Test public void recognizedNativeRulerReceivesNonOwnerCompanyCatalogueEntry() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        UUID viewer=fixture.player.getUniqueID();
        KOMEArmyCompany company=companyWithMember("RULER_LIST","mordor","T132",UUID.randomUUID(),25);
        company.nativeFaction="gondor";
        assertTrue(KOMERulerService.assignRuler(fixture.data,"gondor",viewer,"Ruler"));
        assertTrue(KOMECompanyMapProjection.view(fixture.data,fixture.player).directlyVisible(company));
        java.lang.reflect.Method list=kome.common.command.KOMECommandTroops.class.getDeclaredMethod(
            "listCompanies",net.minecraft.command.ICommandSender.class,EntityPlayerMP.class,
            KOMEWorldData.class,UUID.class,String.class);
        list.setAccessible(true);
        int before=fixture.network.messages.size();
        list.invoke(new kome.common.command.KOMECommandTroops(),fixture.player,fixture.player,
            fixture.data,viewer,"T132");
        KOMEPacketCompanyListGui packet=(KOMEPacketCompanyListGui)fixture.network.messages.get(before);
        assertEquals(1,packet.companies.size());
        assertEquals(company.id,packet.companies.get(0).id);
        assertFalse("Inspection must not grant owner-only mutation",packet.companies.get(0).canSetTendency);
    }

    @Test public void recognizedNativeRulerCanInspectNonOwnerCompanyDetail() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        UUID viewer=fixture.player.getUniqueID();
        KOMEArmyCompany company=companyWithMember("RULER_DETAIL","mordor","T132",UUID.randomUUID(),25);
        company.nativeFaction="gondor";
        assertTrue(KOMERulerService.assignRuler(fixture.data,"gondor",viewer,"Ruler"));
        assertTrue(KOMECompanyMapProjection.view(fixture.data,fixture.player).directlyVisible(company));
        java.lang.reflect.Method detail=kome.common.command.KOMECommandTroops.class.getDeclaredMethod(
            "listCompany",net.minecraft.command.ICommandSender.class,EntityPlayerMP.class,
            KOMEWorldData.class,net.minecraft.world.World.class,String.class);
        detail.setAccessible(true);
        detail.invoke(new kome.common.command.KOMECommandTroops(),fixture.player,fixture.player,
            fixture.data,fixture.world,company.id);
        assertTrue(fixture.player.messages.toString().contains(company.id+" - "+company.name));
    }

    @Test public void sameFactionPeerDoesNotGainLegacyCompanyInspection() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        UUID viewer=fixture.player.getUniqueID();
        KOMEArmyCompany company=companyWithMember("PEER_ONLY","gondor","T132",UUID.randomUUID(),25);
        assertTrue(KOMECompanyMapProjection.view(fixture.data,fixture.player).directlyVisible(company));
        java.lang.reflect.Method list=kome.common.command.KOMECommandTroops.class.getDeclaredMethod(
            "listCompanies",net.minecraft.command.ICommandSender.class,EntityPlayerMP.class,
            KOMEWorldData.class,UUID.class,String.class);
        list.setAccessible(true);
        int before=fixture.network.messages.size();
        list.invoke(new kome.common.command.KOMECommandTroops(),fixture.player,fixture.player,
            fixture.data,viewer,"T132");
        assertTrue(((KOMEPacketCompanyListGui)fixture.network.messages.get(before)).companies.isEmpty());
        java.lang.reflect.Method detail=kome.common.command.KOMECommandTroops.class.getDeclaredMethod(
            "listCompany",net.minecraft.command.ICommandSender.class,EntityPlayerMP.class,
            KOMEWorldData.class,net.minecraft.world.World.class,String.class);
        detail.setAccessible(true);
        try {
            detail.invoke(new kome.common.command.KOMECommandTroops(),fixture.player,fixture.player,
                fixture.data,fixture.world,company.id);
            fail("Faction-wide map visibility must not grant legacy detail inspection");
        } catch(java.lang.reflect.InvocationTargetException denied) {
            assertTrue(denied.getCause() instanceof net.minecraft.command.WrongUsageException);
        }
    }

    @Test public void validatedDelegateRetainsLegacyCompanyInspection() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        UUID viewer=fixture.player.getUniqueID();
        KOMEArmyCompany company=companyWithMember("DELEGATE_READ","mordor","T132",UUID.randomUUID(),25);
        company.nativeFaction="gondor";
        company.temporaryController=viewer;
        company.controllerAuthority=KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE;
        assertTrue(KOMECompanyMapProjection.view(fixture.data,fixture.player).directlyVisible(company));
        java.lang.reflect.Method list=kome.common.command.KOMECommandTroops.class.getDeclaredMethod(
            "listCompanies",net.minecraft.command.ICommandSender.class,EntityPlayerMP.class,
            KOMEWorldData.class,UUID.class,String.class);
        list.setAccessible(true);
        int before=fixture.network.messages.size();
        list.invoke(new kome.common.command.KOMECommandTroops(),fixture.player,fixture.player,
            fixture.data,viewer,"T132");
        assertEquals(company.id,((KOMEPacketCompanyListGui)fixture.network.messages.get(before)).companies.get(0).id);
        java.lang.reflect.Method detail=kome.common.command.KOMECommandTroops.class.getDeclaredMethod(
            "listCompany",net.minecraft.command.ICommandSender.class,EntityPlayerMP.class,
            KOMEWorldData.class,net.minecraft.world.World.class,String.class);
        detail.setAccessible(true);
        detail.invoke(new kome.common.command.KOMECommandTroops(),fixture.player,fixture.player,
            fixture.data,fixture.world,company.id);
        assertTrue(fixture.player.messages.toString().contains(company.id+" - "+company.name));
    }

    @Test public void authorizedCompanyRowsAreFilteredBeforeChunkingAndShareOneWireSerial() throws Exception {
        fixture.pledge(LOTRFaction.GONDOR);
        for(int i=0;i<110;i++) {
            KOMEArmyCompany company=new KOMEArmyCompany();company.id="VISIBLE_"+i;
            company.faction="gondor";company.currentTile="T132";
            fixture.data.armyCompanies.put(company.id,company);
            KOMEArmyCompany enemy=new KOMEArmyCompany();enemy.id="PRIVATE_"+i;
            enemy.faction="mordor";enemy.currentTile="T132";
            fixture.data.armyCompanies.put(enemy.id,enemy);
        }
        List<KOMEPacketConquestData> packets=sendAndTake();
        assertTrue(packets.size()>2);
        long serial=packets.get(0).publicationSerial;assertTrue(serial>0);
        for(KOMEPacketConquestData packet:packets) {
            assertEquals(serial,packet.publicationSerial);
            io.netty.buffer.ByteBuf wire=io.netty.buffer.Unpooled.buffer();
            try {
                packet.toBytes(wire);
                KOMEPacketConquestData decoded=new KOMEPacketConquestData();decoded.fromBytes(wire);
                assertEquals(serial,decoded.publicationSerial);
                assertEquals(packet.data,decoded.data);
            } finally {wire.release();}
        }
        NBTTagList rows=assembled(packets).getTagList("ArmyCompanies",10);
        assertEquals(110,rows.tagCount());
        for(int i=0;i<rows.tagCount();i++)
            assertTrue(rows.getCompoundTagAt(i).getString("Id").startsWith("VISIBLE_"));
    }

    private List<KOMEPacketConquestData> sendAndTake() { send(); return take(); }

    @Test public void waypointAdditionAndRemovalAreCompleteReplacements() {
        send(); take(); KOMETileWaypointLink link = new KOMETileWaypointLink();
        link.tileId="T132"; link.lotrWaypointKey="weathertop";
        fixture.data.tileWaypointLinksByTileId.put(link.tileId,link);
        send(); assertEquals(1,assembled(take()).getTagList("TileWaypointLinks",10).tagCount());
        fixture.data.tileWaypointLinksByTileId.clear(); send();
        assertEquals(0,assembled(take()).getTagList("TileWaypointLinks",10).tagCount());
    }
    @Test public void populationChangeAndReturnToZeroAreNotSuppressed() {
        KOMEPlayerBuild build=new KOMEPlayerBuild(); build.id="B1"; build.tileId="T132";
        build.populationFaction="dunedain"; build.type=KOMEBuildType.NORMAL; build.active=true;
        KOMEBuildContribution contribution=new KOMEBuildContribution();
        contribution.id="H1"; contribution.status=KOMEBuildContribution.APPROVED; contribution.centiHours=10000;
        build.contributions.add(contribution);
        fixture.data.builds.put(build.id,build); send(); NBTTagCompound zero=assembled(take());
        build.developedNativeCentiHours=10000; send(); NBTTagCompound positive=assembled(take());
        assertNotEquals(zero.getTag("TroopSummaries"),positive.getTag("TroopSummaries"));
        build.developedNativeCentiHours=0; send();
        assertEquals(zero.getTag("TroopSummaries"),assembled(take()).getTag("TroopSummaries"));
    }
    @Test public void buildMarkerVisibilityRemovalAndPrivateFieldsKeepProjectionContract() {
        KOMEPlayerBuild build=new KOMEPlayerBuild();build.id="B1";build.tileId="T132";
        build.type=KOMEBuildType.NORMAL;build.active=true;build.markerVisible=true;
        fixture.data.builds.put(build.id,build); send();
        NBTTagCompound marker=assembled(take()).getTagList("BuildMarkers",10).getCompoundTagAt(0);
        assertFalse(marker.hasKey("Contributions")); assertFalse(marker.hasKey("Audit"));
        build.markerVisible=false; send();
        assertEquals(0,assembled(take()).getTagList("BuildMarkers",10).tagCount());
    }
    @Test public void eachRecipientGetsIndependentInitialSnapshot() throws Exception {
        send(); take(); KOMEAccessFixture other=new KOMEAccessFixture();
        KOMEPacketConquestData.sendIfChanged(fixture.data,other.player); assembled(take());
        send(); assertTrue(take().isEmpty());
    }
    @Test public void permissionAndPledgeChangesInvalidateEvenWhenPublicProjectionIsEqual() throws Exception {
        send(); take(); fixture.player.operator=true; send(); assertTrue(take().isEmpty());
        assertTrue(KOMEAdminMapVision.setEnabled(fixture.player,true));send();assembled(take());
        fixture.player.operator=false; send(); assembled(take());
        fixture.pledge(LOTRFaction.RANGER_NORTH); send(); assembled(take());
        fixture.pledge(null); send(); assembled(take());
        send(); assertTrue(take().isEmpty());
    }
    @Test public void replacementPlayerWithSameUuidAndLogoutReconnectGetBaseline() throws Exception {
        send(); take(); KOMEAccessFixture other=new KOMEAccessFixture();other.player.id=fixture.player.id;
        KOMEPacketConquestData.sendIfChanged(fixture.data,other.player);assembled(take());
        new KOMEEvents().onPlayerLogout(new PlayerEvent.PlayerLoggedOutEvent(fixture.player));
        send(); assembled(take());
    }
    @Test public void worldDimensionAndDataIdentityChangesInvalidate() throws Exception {
        send(); take(); fixture.player.dimension=100;send();assembled(take());
        fixture.player.worldObj=new KOMEAccessFixture().world;send();assembled(take());
        KOMEWorldData replacement=new KOMEWorldData();replacement.conquestTiles.putAll(fixture.data.conquestTiles);
        KOMEPacketConquestData.sendIfChanged(replacement,fixture.player);assembled(take());
    }
    @Test public void actualRespawnAndDimensionHooksForceCompleteInitialization() {
        send();take();KOMEEvents events=new KOMEEvents();
        events.onPlayerRespawn(new PlayerEvent.PlayerRespawnEvent(fixture.player));assembled(take());
        events.onPlayerChangedDimension(new PlayerEvent.PlayerChangedDimensionEvent(fixture.player,0,100));assembled(take());
    }
    @Test public void sessionResetAndExplicitResyncAreNeverSuppressed() {
        send();take();new KOMEEvents().resetSessionState();send();assembled(take());
        fixture.data.syncConquestTiles(fixture.player);assembled(take());
    }
    @Test public void outgoingPacketsCannotMutateCachedProjectionAndWorldChangesCannotMutatePackets() {
        send();List<KOMEPacketConquestData> first=take();
        NBTTagCompound copy=assembled(first);
        // A transport observer changing its message must not poison the private baseline.
        first.get(0).data.setString("Unexpected","value");send();assertTrue(take().isEmpty());
        first.get(0).data.removeTag("Unexpected");
        fixture.data.conquestTiles.get("T132").claim("gondor",2);
        assertEquals(copy,assembled(first));send();assembled(take());
        send();assertTrue(take().isEmpty());
    }
    @Test public void failedDispatchRetriesFullResetEvenAfterWorldReverts() throws Exception {
        send();take();long originalClaimTime=fixture.data.conquestTiles.get("T132").claimedAtMillis;
        fixture.data.conquestTiles.get("T132").claim("gondor",2);
        FailingNetwork fail=KOMEAccessFixture.allocate(FailingNetwork.class);
        KOMEPacketHandler.network=fail;
        try { send(); fail("Expected dispatch failure"); } catch(IllegalStateException expected) { }
        finally { KOMEPacketHandler.network=fixture.network; }
        fixture.data.conquestTiles.get("T132").claim("dunedain",1);
        fixture.data.conquestTiles.get("T132").claimedAtMillis=originalClaimTime;
        send();assembled(take());
    }
    @Test public void boundedRecipientCacheEvictionResendsInsteadOfDroppingUpdates() throws Exception {
        send();take();
        for(int i=0;i<256;i++) {
            KOMEAccessFixture other=new KOMEAccessFixture();
            KOMEPacketConquestData.sendIfChanged(fixture.data,other.player);take();
        }
        java.lang.reflect.Field cache=KOMEPacketConquestData.class.getDeclaredField("SENT");
        cache.setAccessible(true);assertTrue(((java.util.Map<?,?>)cache.get(null)).size()<=256);
        fixture.data.conquestTiles.get("T132").claim("gondor",2);
        send();assembled(take());
    }
    @Test public void clearingLastVisibleRecordStillSendsResetAndCompletion() {
        send();take();fixture.data.conquestTiles.clear();send();assembled(take());
        send();assertTrue(take().isEmpty());
    }
    public static final class FailingNetwork extends SimpleNetworkWrapper {
        private FailingNetwork(){super("unused");}
        @Override public net.minecraft.network.Packet getPacketFrom(IMessage message) {
            throw new IllegalStateException("test dispatch failure");
        }
    }
}
