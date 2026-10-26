package kome.client;

import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import kome.common.KOMEAddon;
import kome.common.KOMEAccessFixture;
import kome.common.KOMECommonProxy;
import kome.common.data.*;
import kome.common.network.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEClientTaskQueueTest {
    @Test public void cancelledMoveConfirmMustNotRefreshOrReopenCompanyList() throws Exception {
        SavedClientState saved=new SavedClientState();RecordingProxy proxy=proxy();
        KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        try {
            net.minecraft.client.network.NetHandlerPlayClient connection=KOMEAccessFixture.allocate(net.minecraft.client.network.NetHandlerPlayClient.class);
            cpw.mods.fml.common.network.simpleimpl.MessageContext context=clientContext(connection);
            proxy.snapshots.start(connection);proxy.details.start(connection);
            final boolean[] closed={false};
            kome.client.gui.KOMEGuiCompanyMoveConfirm moveConfirm=
                new kome.client.gui.KOMEGuiCompanyMoveConfirm(new KOMEPacketCompanyMoveConfirmGui()) {
                    @Override protected void closeAfterCancel() { closed[0]=true; }
                };
            long requestId=proxy.details.begin("companies","T100","",moveConfirm,true);
            KOMEPacketConquestData initial=companySnapshot(true,markerCompany());initial.publicationSerial=101;
            new KOMEPacketConquestData.Handler().onMessage(initial,context);proxy.queue.drain();
            java.lang.reflect.Method cancel=kome.client.gui.KOMEGuiCompanyMoveConfirm.class
                .getDeclaredMethod("actionPerformed",net.minecraft.client.gui.GuiButton.class);
            cancel.setAccessible(true);
            cancel.invoke(moveConfirm,new net.minecraft.client.gui.GuiButton(1,0,0,"Cancel"));
            assertTrue("Production Cancel closes Move Confirm",closed[0]);
            KOMEPacketConquestData update=companySnapshot(true,markerCompany());update.publicationSerial=102;
            new KOMEPacketConquestData.Handler().onMessage(update,context);proxy.queue.drain();
            KOMEPacketCompanyListGui old=new KOMEPacketCompanyListGui();
            old.tileId="T100";old.mapPublicationSerial=101;old.mapRequestId=requestId;
            new KOMEPacketCompanyListGui.Handler().onMessage(old,context);proxy.queue.drain();
            assertTrue("Cancel must not re-query the company list",proxy.refreshes.isEmpty());
            assertFalse("Cancel must not reopen the company list",proxy.calls.contains("companies:T100"));
        }finally{saved.restore();KOMEAddon.proxy=previous;}
    }
    @Test public void onlyIntentionalPopulationNavigationMayCloseBeforeMapDetail() {
        assertTrue(KOMEClientProxy.mayCloseForMapDetail(
            new kome.client.gui.KOMEGuiPopulation(new KOMEPacketPopulationGui())));
        assertTrue(KOMEClientProxy.mayCloseForMapDetail(
            new kome.client.gui.KOMEGuiPopulationUnits(new KOMEPacketPopulationUnitsGui())));
        assertFalse(KOMEClientProxy.mayCloseForMapDetail(
            new kome.client.gui.KOMEGuiCompanyMoveConfirm(new KOMEPacketCompanyMoveConfirmGui())));
    }
    @Test public void populationAndUnitsNavigationStillOpenCurrentAuthorizedDetails() throws Exception {
        SavedClientState saved=new SavedClientState();RecordingProxy proxy=proxy();
        KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        try {
            net.minecraft.client.network.NetHandlerPlayClient connection=KOMEAccessFixture.allocate(net.minecraft.client.network.NetHandlerPlayClient.class);
            cpw.mods.fml.common.network.simpleimpl.MessageContext context=clientContext(connection);
            proxy.snapshots.start(connection);proxy.details.start(connection);
            KOMEPacketConquestData snapshot=companySnapshot(true,markerCompany());snapshot.publicationSerial=101;
            new KOMEPacketConquestData.Handler().onMessage(snapshot,context);proxy.queue.drain();
            Object population=new kome.client.gui.KOMEGuiPopulation(new KOMEPacketPopulationGui());
            long companyId=proxy.details.begin("companies","T100","",population,true);
            KOMEPacketCompanyListGui companies=new KOMEPacketCompanyListGui();
            companies.tileId="T100";companies.mapPublicationSerial=101;companies.mapRequestId=companyId;
            new KOMEPacketCompanyListGui.Handler().onMessage(companies,context);proxy.queue.drain();
            assertTrue(proxy.calls.contains("companies:T100"));
            Object units=new kome.client.gui.KOMEGuiPopulationUnits(new KOMEPacketPopulationUnitsGui());
            long captureId=proxy.details.begin("capture","T100","",units,true);
            KOMEPacketConquestCaptureGui capture=new KOMEPacketConquestCaptureGui();
            capture.tileId="T100";capture.mapPublicationSerial=101;capture.mapRequestId=captureId;
            new KOMEPacketConquestCaptureGui.Handler().onMessage(capture,context);proxy.queue.drain();
            assertTrue(proxy.calls.contains("capture:T100"));
            assertTrue(proxy.refreshes.isEmpty());
        }finally{saved.restore();KOMEAddon.proxy=previous;}
    }
    @Test public void unrelatedConquestUpdateMustNotStrandOrdinaryMapDetailRequest() throws Exception {
        SavedClientState saved=new SavedClientState();RecordingProxy proxy=proxy();
        KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        try {
            net.minecraft.client.network.NetHandlerPlayClient connection=KOMEAccessFixture.allocate(net.minecraft.client.network.NetHandlerPlayClient.class);
            cpw.mods.fml.common.network.simpleimpl.MessageContext context=clientContext(connection);
            proxy.snapshots.start(connection);proxy.details.start(connection);
            long requestId=proxy.details.begin("companies","T100","",null,true);
            KOMEPacketConquestData original=companySnapshot(true,markerCompany());original.publicationSerial=101;
            new KOMEPacketConquestData.Handler().onMessage(original,context);proxy.queue.drain();
            KOMEPacketConquestData unrelated=companySnapshot(true,markerCompany());unrelated.publicationSerial=102;
            new KOMEPacketConquestData.Handler().onMessage(unrelated,context);proxy.queue.drain();
            KOMEPacketCompanyListGui list=new KOMEPacketCompanyListGui();list.tileId="T100";list.mapPublicationSerial=101;list.mapRequestId=requestId;
            new KOMEPacketCompanyListGui.Handler().onMessage(list,context);proxy.queue.drain();
            assertEquals("Only a fresh server response may open the browser",1,proxy.refreshes.size());
            assertFalse(proxy.calls.contains("companies:T100"));
            KOMEPacketCompanyListGui refreshed=new KOMEPacketCompanyListGui();
            refreshed.tileId="T100";refreshed.mapPublicationSerial=102;refreshed.mapRequestId=requestId;
            new KOMEPacketCompanyListGui.Handler().onMessage(refreshed,context);proxy.queue.drain();
            assertTrue("Ordinary company browser should remain available after an unrelated snapshot",
                proxy.calls.contains("companies:T100"));
        }finally{saved.restore();KOMEAddon.proxy=previous;}
    }
    @Test public void captureAndUnitsRefreshUnderCurrentAuthorizationAfterUnrelatedUpdate() throws Exception {
        SavedClientState saved=new SavedClientState();RecordingProxy proxy=proxy();
        KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        try {
            net.minecraft.client.network.NetHandlerPlayClient connection=KOMEAccessFixture.allocate(net.minecraft.client.network.NetHandlerPlayClient.class);
            cpw.mods.fml.common.network.simpleimpl.MessageContext context=clientContext(connection);
            proxy.snapshots.start(connection);proxy.details.start(connection);
            KOMEPacketConquestData one=companySnapshot(true,markerCompany());one.publicationSerial=101;
            new KOMEPacketConquestData.Handler().onMessage(one,context);proxy.queue.drain();
            KOMEPacketConquestData two=companySnapshot(true,markerCompany());two.publicationSerial=102;
            new KOMEPacketConquestData.Handler().onMessage(two,context);proxy.queue.drain();
            long captureId=proxy.details.begin("capture","T100","",null,true);
            KOMEPacketConquestCaptureGui oldCapture=new KOMEPacketConquestCaptureGui();
            oldCapture.tileId="T100";oldCapture.mapPublicationSerial=101;oldCapture.mapRequestId=captureId;
            new KOMEPacketConquestCaptureGui.Handler().onMessage(oldCapture,context);proxy.queue.drain();
            assertEquals(Arrays.asList("capture:T100"),proxy.refreshes);
            assertFalse(proxy.calls.contains("capture:T100"));
            KOMEPacketConquestCaptureGui freshCapture=new KOMEPacketConquestCaptureGui();
            freshCapture.tileId="T100";freshCapture.mapPublicationSerial=102;freshCapture.mapRequestId=captureId;
            new KOMEPacketConquestCaptureGui.Handler().onMessage(freshCapture,context);proxy.queue.drain();
            assertTrue(proxy.calls.contains("capture:T100"));
            long unitsId=proxy.details.begin("units","T100","",null,true);
            KOMEPacketPopulationUnitsGui oldUnits=new KOMEPacketPopulationUnitsGui();
            oldUnits.playerName="old";oldUnits.filterTile="T100";oldUnits.mapPublicationSerial=101;oldUnits.mapRequestId=unitsId;
            new KOMEPacketPopulationUnitsGui.Handler().onMessage(oldUnits,context);proxy.queue.drain();
            assertEquals(Arrays.asList("capture:T100","units:T100"),proxy.refreshes);
            assertFalse(proxy.calls.contains("units:old"));
            KOMEPacketPopulationUnitsGui freshUnits=new KOMEPacketPopulationUnitsGui();
            freshUnits.playerName="fresh";freshUnits.filterTile="T100";freshUnits.mapPublicationSerial=102;freshUnits.mapRequestId=unitsId;
            new KOMEPacketPopulationUnitsGui.Handler().onMessage(freshUnits,context);proxy.queue.drain();
            assertTrue(proxy.calls.contains("units:fresh"));
        }finally{saved.restore();KOMEAddon.proxy=previous;}
    }
    @Test public void revokedCompanyAndUnitsDetailsRefreshOnlyThroughFilteredServerReply() throws Exception {
        SavedClientState saved=new SavedClientState();RecordingProxy proxy=proxy();
        KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        try {
            net.minecraft.client.network.NetHandlerPlayClient connection=KOMEAccessFixture.allocate(net.minecraft.client.network.NetHandlerPlayClient.class);
            cpw.mods.fml.common.network.simpleimpl.MessageContext context=clientContext(connection);
            proxy.snapshots.start(connection);proxy.details.start(connection);
            KOMEPacketConquestData on=companySnapshot(true,markerCompany());on.publicationSerial=201;
            new KOMEPacketConquestData.Handler().onMessage(on,context);proxy.queue.drain();
            long listId=proxy.details.begin("companies","T100","",null,true);
            KOMEPacketCompanyListGui oldList=new KOMEPacketCompanyListGui();
            oldList.tileId="T100";oldList.mapPublicationSerial=201;oldList.mapRequestId=listId;
            KOMEPacketConquestData off=companySnapshot(true);off.publicationSerial=202;
            new KOMEPacketConquestData.Handler().onMessage(off,context);proxy.queue.drain();
            new KOMEPacketCompanyListGui.Handler().onMessage(oldList,context);proxy.queue.drain();
            assertFalse(proxy.calls.contains("companies:T100"));
            assertEquals(Arrays.asList("companies:T100"),proxy.refreshes);
            KOMEPacketCompanyListGui filtered=new KOMEPacketCompanyListGui();
            filtered.tileId="T100";filtered.mapPublicationSerial=202;filtered.mapRequestId=listId;
            new KOMEPacketCompanyListGui.Handler().onMessage(filtered,context);proxy.queue.drain();
            assertEquals(1,Collections.frequency(proxy.calls,"companies:T100"));
            long unitsId=proxy.details.begin("units","T100","",null,true);
            KOMEPacketPopulationUnitsGui oldUnits=new KOMEPacketPopulationUnitsGui();
            oldUnits.playerName="Privileged";oldUnits.filterTile="T100";
            oldUnits.mapPublicationSerial=201;oldUnits.mapRequestId=unitsId;
            new KOMEPacketPopulationUnitsGui.Handler().onMessage(oldUnits,context);proxy.queue.drain();
            assertFalse(proxy.calls.contains("units:Privileged"));
            KOMEPacketPopulationUnitsGui safeUnits=new KOMEPacketPopulationUnitsGui();
            safeUnits.playerName="Current";safeUnits.filterTile="T100";
            safeUnits.mapPublicationSerial=202;safeUnits.mapRequestId=unitsId;
            new KOMEPacketPopulationUnitsGui.Handler().onMessage(safeUnits,context);proxy.queue.drain();
            assertTrue(proxy.calls.contains("units:Current"));
        }finally{saved.restore();KOMEAddon.proxy=previous;}
    }

    @Test public void revokedQueuedPrivilegedDetailNeverOpensAndOldRequestIdentityCannotReturn() throws Exception {
        SavedClientState saved=new SavedClientState();RecordingProxy proxy=proxy();
        KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        try {
            net.minecraft.client.network.NetHandlerPlayClient connection=KOMEAccessFixture.allocate(net.minecraft.client.network.NetHandlerPlayClient.class);
            cpw.mods.fml.common.network.simpleimpl.MessageContext context=clientContext(connection);
            proxy.snapshots.start(connection);proxy.details.start(connection);
            long id=proxy.details.begin("capture","T100","",null,true);
            KOMEPacketConquestData on=companySnapshot(true,markerCompany());on.publicationSerial=201;
            new KOMEPacketConquestData.Handler().onMessage(on,context);
            KOMEPacketConquestCaptureGui privileged=new KOMEPacketConquestCaptureGui();
            privileged.tileId="T100";privileged.myOffensivePop=45;
            privileged.mapPublicationSerial=201;privileged.mapRequestId=id;
            new KOMEPacketConquestCaptureGui.Handler().onMessage(privileged,context);
            KOMEPacketConquestData off=companySnapshot(true);off.publicationSerial=202;
            new KOMEPacketConquestData.Handler().onMessage(off,context);proxy.queue.drain();
            assertFalse(proxy.calls.contains("capture:T100"));
            assertEquals(1,proxy.refreshes.size());
            long newer=proxy.details.begin("capture","T100","",null,true);
            KOMEPacketConquestData nextOn=companySnapshot(true,markerCompany());nextOn.publicationSerial=203;
            new KOMEPacketConquestData.Handler().onMessage(nextOn,context);proxy.queue.drain();
            new KOMEPacketConquestCaptureGui.Handler().onMessage(privileged,context);proxy.queue.drain();
            assertFalse("Old request cannot regain validity after ON",proxy.calls.contains("capture:T100"));
            KOMEPacketConquestCaptureGui current=new KOMEPacketConquestCaptureGui();
            current.tileId="T100";current.mapPublicationSerial=203;current.mapRequestId=newer;
            new KOMEPacketConquestCaptureGui.Handler().onMessage(current,context);proxy.queue.drain();
            assertEquals(1,Collections.frequency(proxy.calls,"capture:T100"));
            assertEquals(1,proxy.refreshes.size());
        }finally{saved.restore();KOMEAddon.proxy=previous;}
    }
    @Test public void dismissedOrSupersededDetailRequestsDoNotRetryIndefinitely() throws Exception {
        SavedClientState saved=new SavedClientState();
        RecordingProxy proxy=proxy();Object connection=new Object(),screen=new Object();
        try {
        proxy.snapshots.start(connection);proxy.details.start(connection);
        long id=proxy.details.begin("companies","T100","",screen,false);
        assertEquals(KOMEMapDetailRequests.Decision.DROP,
            proxy.details.resolve(id,"companies","T100","",101,connection,new Object(),proxy.snapshots));
        long active=proxy.details.begin("companies","T100","",screen,false);
        assertNotEquals(id,active);
        assertEquals(KOMEMapDetailRequests.Decision.DROP,
            proxy.details.resolve(id,"companies","T100","",101,connection,screen,proxy.snapshots));
        net.minecraft.client.network.NetHandlerPlayClient real=KOMEAccessFixture.allocate(net.minecraft.client.network.NetHandlerPlayClient.class);
        cpw.mods.fml.common.network.simpleimpl.MessageContext context=clientContext(real);
        proxy.snapshots.start(real);proxy.details.start(real);
        active=proxy.details.begin("companies","T100","",screen,false);
        KOMEPacketConquestData latest=companySnapshot(true,markerCompany());latest.publicationSerial=102;
        KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        try {new KOMEPacketConquestData.Handler().onMessage(latest,context);proxy.queue.drain();}
        finally {KOMEAddon.proxy=previous;}
        assertEquals(KOMEMapDetailRequests.Decision.REFRESH,
            proxy.details.resolve(active,"companies","T100","",101,real,screen,proxy.snapshots));
        assertEquals(KOMEMapDetailRequests.Decision.REFRESH,
            proxy.details.resolve(active,"companies","T100","",101,real,screen,proxy.snapshots));
        assertEquals(KOMEMapDetailRequests.Decision.DROP,
            proxy.details.resolve(active,"companies","T100","",101,real,screen,proxy.snapshots));
        proxy.details.start(new Object());
        assertEquals(KOMEMapDetailRequests.Decision.DROP,
            proxy.details.resolve(active,"companies","T100","",102,real,screen,proxy.snapshots));
        } finally {saved.restore();}
    }
    @Test public void revokedSnapshotReplacesPendingOnSnapshotBeforeQueuedMapDetails() throws Exception {
        SavedClientState saved=new SavedClientState();RecordingProxy proxy=proxy();
        KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        try {
            KOMEClientData.INSTANCE.resetClientState();
            net.minecraft.client.network.NetHandlerPlayClient connection=KOMEAccessFixture.allocate(net.minecraft.client.network.NetHandlerPlayClient.class);
            cpw.mods.fml.common.network.simpleimpl.MessageContext context=clientContext(connection);
            proxy.snapshots.start(connection);
            KOMEPacketConquestData on=companySnapshot(true,markerCompany());on.publicationSerial=101;
            new KOMEPacketConquestData.Handler().onMessage(on,context);
            KOMEPacketConquestCaptureGui capture=new KOMEPacketConquestCaptureGui();capture.tileId="T100";
            capture.mapPublicationSerial=101;
            new KOMEPacketConquestCaptureGui.Handler().onMessage(capture,context);
            KOMEPacketCompanyListGui companies=new KOMEPacketCompanyListGui();companies.tileId="T100";
            companies.mapPublicationSerial=101;
            new KOMEPacketCompanyListGui.Handler().onMessage(companies,context);
            KOMEPacketPopulationUnitsGui units=new KOMEPacketPopulationUnitsGui();units.playerName="Privileged";
            units.mapPublicationSerial=101;
            new KOMEPacketPopulationUnitsGui.Handler().onMessage(units,context);
            KOMEPacketConquestData off=companySnapshot(true);off.publicationSerial=102;
            new KOMEPacketConquestData.Handler().onMessage(off,context);
            proxy.queue.drain();
            assertTrue(KOMEClientData.INSTANCE.armyCompanies.isEmpty());
            assertFalse("Queued capture must not reopen after revocation",proxy.calls.contains("capture:T100"));
            assertFalse("Queued company list must not reopen after revocation",proxy.calls.contains("companies:T100"));
            assertFalse("Queued Units view must not reopen after revocation",proxy.calls.contains("units:Privileged"));
        }finally{saved.restore();KOMEAddon.proxy=previous;}
    }
    @Test public void mapDetailExecutionRejectsOldGenerationsButAcceptsFreshAndExplicitCommands() throws Exception {
        SavedClientState saved=new SavedClientState();RecordingProxy proxy=proxy();
        KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        try {
            KOMEClientData.INSTANCE.resetClientState();
            net.minecraft.client.network.NetHandlerPlayClient connection=KOMEAccessFixture.allocate(net.minecraft.client.network.NetHandlerPlayClient.class);
            cpw.mods.fml.common.network.simpleimpl.MessageContext context=clientContext(connection);
            proxy.snapshots.start(connection);
            KOMEPacketConquestData on=companySnapshot(true,markerCompany());on.publicationSerial=201;
            new KOMEPacketConquestData.Handler().onMessage(on,context);proxy.queue.drain();
            assertTrue(proxy.snapshots.allowsMapDetail(201,connection));
            KOMEPacketConquestCaptureGui old=new KOMEPacketConquestCaptureGui();old.tileId="old";old.mapPublicationSerial=201;
            new KOMEPacketConquestCaptureGui.Handler().onMessage(old,context);
            KOMEPacketConquestData off=companySnapshot(true);off.publicationSerial=202;
            new KOMEPacketConquestData.Handler().onMessage(off,context);
            KOMEPacketConquestData nextOn=companySnapshot(true,markerCompany());nextOn.publicationSerial=203;
            new KOMEPacketConquestData.Handler().onMessage(nextOn,context);
            proxy.queue.drain();
            assertFalse(proxy.calls.contains("capture:old"));
            assertTrue(proxy.snapshots.allowsMapDetail(203,connection));
            KOMEPacketConquestCaptureGui fresh=new KOMEPacketConquestCaptureGui();fresh.tileId="fresh";fresh.mapPublicationSerial=203;
            new KOMEPacketConquestCaptureGui.Handler().onMessage(fresh,context);
            KOMEPacketCompanyListGui list=new KOMEPacketCompanyListGui();list.tileId="current";list.mapPublicationSerial=203;
            new KOMEPacketCompanyListGui.Handler().onMessage(list,context);
            KOMEPacketPopulationUnitsGui units=new KOMEPacketPopulationUnitsGui();units.playerName="current";units.mapPublicationSerial=203;
            new KOMEPacketPopulationUnitsGui.Handler().onMessage(units,context);
            KOMEPacketCompanyListGui command=new KOMEPacketCompanyListGui();command.tileId="staff-command";
            new KOMEPacketCompanyListGui.Handler().onMessage(command,context);
            proxy.queue.drain();
            assertTrue(proxy.calls.contains("capture:fresh"));
            assertTrue(proxy.calls.contains("companies:current"));
            assertTrue(proxy.calls.contains("units:current"));
            assertTrue("Explicit command response does not depend on map vision",proxy.calls.contains("companies:staff-command"));
            proxy.snapshots.invalidateWorld();
            assertFalse(proxy.snapshots.allowsMapDetail(203,connection));
            KOMEPacketPopulationUnitsGui staleWorld=new KOMEPacketPopulationUnitsGui();
            staleWorld.playerName="former-world";staleWorld.mapPublicationSerial=203;
            new KOMEPacketPopulationUnitsGui.Handler().onMessage(staleWorld,context);
            proxy.queue.drain();
            assertFalse(proxy.calls.contains("units:former-world"));
        }finally{saved.restore();KOMEAddon.proxy=previous;}
    }

    @Test public void mapDetailResponseFromFormerConnectionCannotOpenAfterReconnect() throws Exception {
        SavedClientState saved=new SavedClientState();RecordingProxy proxy=proxy();
        KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        try {
            net.minecraft.client.network.NetHandlerPlayClient former=KOMEAccessFixture.allocate(net.minecraft.client.network.NetHandlerPlayClient.class);
            net.minecraft.client.network.NetHandlerPlayClient current=KOMEAccessFixture.allocate(net.minecraft.client.network.NetHandlerPlayClient.class);
            cpw.mods.fml.common.network.simpleimpl.MessageContext formerContext=clientContext(former);
            cpw.mods.fml.common.network.simpleimpl.MessageContext currentContext=clientContext(current);
            proxy.snapshots.start(former);
            KOMEPacketConquestData first=companySnapshot(true,markerCompany());first.publicationSerial=301;
            new KOMEPacketConquestData.Handler().onMessage(first,formerContext);proxy.queue.drain();
            proxy.snapshots.start(current);proxy.queue.resetSession(true,()->{});proxy.queue.drain();
            KOMEPacketConquestData second=companySnapshot(true);second.publicationSerial=302;
            new KOMEPacketConquestData.Handler().onMessage(second,currentContext);proxy.queue.drain();
            KOMEPacketConquestCaptureGui stale=new KOMEPacketConquestCaptureGui();stale.tileId="former";stale.mapPublicationSerial=301;
            new KOMEPacketConquestCaptureGui.Handler().onMessage(stale,formerContext);
            KOMEPacketConquestCaptureGui wrongConnection=new KOMEPacketConquestCaptureGui();wrongConnection.tileId="wrong";wrongConnection.mapPublicationSerial=302;
            new KOMEPacketConquestCaptureGui.Handler().onMessage(wrongConnection,formerContext);
            proxy.queue.drain();
            assertFalse(proxy.calls.contains("capture:former"));
            assertFalse(proxy.calls.contains("capture:wrong"));
            assertTrue(proxy.snapshots.allowsMapDetail(302,current));
        }finally{saved.restore();KOMEAddon.proxy=previous;}
    }
    @Test public void completeReplacementRemovesPrivilegedStandaloneCountBeforeMarkerRefresh() throws Exception {
        SavedClientState saved=new SavedClientState();RecordingProxy proxy=proxy();
        java.util.concurrent.atomic.AtomicInteger clearedViews=new java.util.concurrent.atomic.AtomicInteger();
        proxy.snapshots=new KOMEConquestSnapshotPublisher(proxy.queue,clearedViews::incrementAndGet);
        KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        KOMEClientData client=KOMEClientData.INSTANCE;
        try {
            client.resetClientState();
            KOMEPacketConquestData old=companySnapshot(true,markerCompany());old.publicationSerial=7;
            KOMETileTroopSummary summary=new KOMETileTroopSummary();summary.tileId="T100";
            summary.stationedPop=45;row(old,"TroopSummaries",summary.writeToNBT());
            new KOMEPacketConquestData.Handler().onMessage(old,null);proxy.queue.drain();
            assertEquals(45,client.troopSummaries.get("T100").stationedPop);
            KOMEPacketConquestData revocation=companySnapshot(false);revocation.publicationSerial=8;
            new KOMEPacketConquestData.Handler().onMessage(revocation,null);proxy.queue.drain();
            assertEquals("Partial publications must remain atomic",45,
                client.troopSummaries.get("T100").stationedPop);
            new KOMEPacketConquestData.Handler().onMessage(old,null);proxy.queue.drain();
            assertEquals(45,client.troopSummaries.get("T100").stationedPop);
            KOMEPacketConquestData complete=new KOMEPacketConquestData();
            complete.publicationSerial=8;complete.complete=true;
            new KOMEPacketConquestData.Handler().onMessage(complete,null);proxy.queue.drain();
            assertTrue(client.troopSummaries.isEmpty());
            assertTrue(client.armyCompanies.isEmpty());
            assertEquals("Privileged map browsers must be invalidated",1,clearedViews.get());
        }finally{saved.restore();KOMEAddon.proxy=previous;}
    }
    @Test public void completedSpottedReplacementCannotReattributeAnOldForeignMarker()throws Exception {
        SavedClientState saved=new SavedClientState();RecordingProxy proxy=proxy();
        KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        try {
            KOMEClientData client=KOMEClientData.INSTANCE;client.resetClientState();
            KOMEArmyCompany a=new KOMEArmyCompany(),b=new KOMEArmyCompany();
            a.id="CANONICAL_A";b.id="CANONICAL_B";a.faction=b.faction="mordor";
            a.currentTile=b.currentTile="T100";
            String aliasA="~recon:opaque-a",aliasB="~recon:opaque-b";
            KOMEPacketConquestData initial=spottedSnapshot(a,aliasA,b,aliasB);
            new KOMEPacketConquestData.Handler().onMessage(initial,null);proxy.queue.drain();
            KOMEUnitMapMarker old=new KOMEUnitMapMarker(),current=new KOMEUnitMapMarker();
            old.entityId=aliasA;old.currentTile="T100";old.soldierCount=1;
            current.entityId=aliasB;current.currentTile="T100";current.soldierCount=3;
            assertTrue(markerCurrent(old));assertTrue(markerCurrent(current));
            new KOMEPacketConquestData.Handler().onMessage(spottedSnapshot(b,aliasB),null);proxy.queue.drain();
            assertFalse("Old alias cannot identify the surviving foreign company",markerCurrent(old));
            assertTrue(markerCurrent(current));
            client.unitMapMarkers.clear();client.unitMapMarkers.add(old); // A late older marker list.
            assertFalse(markerCurrent(client.unitMapMarkers.get(0)));
        }finally{saved.restore();KOMEAddon.proxy=previous;}
    }

    private static KOMEPacketConquestData spottedSnapshot(Object... companyAndAlias) {
        KOMEPacketConquestData packet=new KOMEPacketConquestData();packet.reset=true;packet.complete=true;
        NBTTagList rows=new NBTTagList();
        for(int i=0;i<companyAndAlias.length;i+=2)
            rows.appendTag(KOMECompanyMapProjection.spottedTag((KOMEArmyCompany)companyAndAlias[i],
                (String)companyAndAlias[i+1]));
        packet.data.setTag("ArmyCompanies",rows);return packet;
    }

    @Test public void completedCompanyRemovalImmediatelyRejectsOldPhysicalMarkerAndItsAggregateDeduction()throws Exception {
        SavedClientState saved=new SavedClientState();RecordingProxy proxy=proxy();KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        KOMEClientData client=KOMEClientData.INSTANCE;
        try {
            client.resetClientState();KOMEArmyCompany company=markerCompany();KOMEUnitMapMarker marker=physicalMarker();
            new KOMEPacketConquestData.Handler().onMessage(companySnapshot(true,company),null);proxy.queue.drain();
            client.unitMapMarkers.add(marker);assertTrue(markerCurrent(marker));assertEquals(Integer.valueOf(25),markerDeductions().get("T100"));
            KOMEPacketConquestData removal=companySnapshot(true);
            KOMETileTroopSummary summary=new KOMETileTroopSummary();summary.tileId="T100";summary.stationedPop=40;
            row(removal,"TroopSummaries",summary.writeToNBT());
            new KOMEPacketConquestData.Handler().onMessage(removal,null);
            assertTrue("Queued removal is not yet canonical",markerCurrent(marker));proxy.queue.drain();
            assertFalse("A completed removal must not turn an old physical marker into a neutral shield",markerCurrent(marker));
            assertTrue(markerDeductions().isEmpty());assertEquals(40,client.troopSummaries.get("T100").stationedPop);
        }finally{saved.restore();KOMEAddon.proxy=previous;}
    }

    @Test public void initialCompanySnapshotUnavailabilityDiffersFromCompletedEmptySnapshot()throws Exception {
        SavedClientState saved=new SavedClientState();RecordingProxy proxy=proxy();KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        try {
            KOMEClientData.INSTANCE.resetClientState();KOMEUnitMapMarker marker=physicalMarker();
            assertTrue("Initial physical fix can use neutral art until the first snapshot",markerCurrent(marker));
            assertSame(KOMEMapMarkerArt.NEUTRAL_SHIELD,KOMEMapMarkerArt.company(marker,KOMEClientData.INSTANCE.armyCompanies));
            new KOMEPacketConquestData.Handler().onMessage(companySnapshot(false),null);proxy.queue.drain();assertTrue(markerCurrent(marker));
            KOMEPacketConquestData end=new KOMEPacketConquestData();end.complete=true;
            new KOMEPacketConquestData.Handler().onMessage(end,null);proxy.queue.drain();assertFalse("Only completed publication establishes absence",markerCurrent(marker));
            new KOMEPacketConquestData.Handler().onMessage(companySnapshot(false,markerCompany()),null);proxy.queue.drain();
            assertFalse("An incomplete replacement cannot restore a removed ID",markerCurrent(marker));
            new KOMEPacketConquestData.Handler().onMessage(end,null);proxy.queue.drain();assertTrue(markerCurrent(marker));
        }finally{saved.restore();KOMEAddon.proxy=previous;}
    }

    @Test public void currentCompanySurvivesUnloadingMovementAndReconstructionWithExactIdentity()throws Exception {
        SavedClientState saved=new SavedClientState();RecordingProxy proxy=proxy();KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        try {
            KOMEClientData.INSTANCE.resetClientState();KOMEArmyCompany company=markerCompany();KOMEUnitMapMarker marker=physicalMarker();
            new KOMEPacketConquestData.Handler().onMessage(companySnapshot(true,company),null);proxy.queue.drain();assertTrue(markerCurrent(marker));
            marker.unitCount=0;assertTrue(markerCurrent(marker));assertSame(KOMEMapMarkerArt.shield("rohan"),KOMEMapMarkerArt.company(marker,KOMEClientData.INSTANCE.armyCompanies));
            company.movementOrderId="M1";new KOMEPacketConquestData.Handler().onMessage(companySnapshot(true,company),null);proxy.queue.drain();assertFalse(markerCurrent(marker));
            company.movementOrderId="";company.currentTile="T101";new KOMEPacketConquestData.Handler().onMessage(companySnapshot(true,company),null);proxy.queue.drain();assertFalse(markerCurrent(marker));
            KOMEUnitMapMarker reconstructed=physicalMarker();reconstructed.currentTile="T101";reconstructed.x=789;reconstructed.z=987;
            assertEquals(marker.entityId,reconstructed.entityId);assertTrue(markerCurrent(reconstructed));
            KOMEUnitMapMarker different=physicalMarker();different.entityId="C-other";assertFalse(markerCurrent(different));
        }finally{saved.restore();KOMEAddon.proxy=previous;}
    }

    @Test public void olderCompanyPublicationCannotUndoNewerRevocation() throws Exception {
        SavedClientState saved=new SavedClientState();RecordingProxy proxy=proxy();
        KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        KOMEClientData client=KOMEClientData.INSTANCE;
        try {
            client.resetClientState();
            KOMEPacketConquestData old=companySnapshot(true,markerCompany()); old.publicationSerial=7;
            new KOMEPacketConquestData.Handler().onMessage(old,null);
            KOMEPacketConquestData revocation=companySnapshot(false); revocation.publicationSerial=8;
            new KOMEPacketConquestData.Handler().onMessage(revocation,null);
            proxy.queue.drain();
            assertFalse("An older queued company snapshot must not publish after a newer reset",
                client.armyCompanies.containsKey("C1"));
            new KOMEPacketConquestData.Handler().onMessage(old,null);
            KOMEPacketConquestData completion=new KOMEPacketConquestData();
            completion.publicationSerial=8;completion.reset=false;completion.complete=true;
            new KOMEPacketConquestData.Handler().onMessage(completion,null);proxy.queue.drain();
            assertTrue(client.armyCompanies.isEmpty());
            new KOMEPacketConquestData.Handler().onMessage(old,null);proxy.queue.drain();
            assertTrue(client.armyCompanies.isEmpty());
        } finally {saved.restore();KOMEAddon.proxy=previous;}
    }

    @Test public void formerConnectionCannotRestoreCompanyAfterReconnect() throws Exception {
        SavedClientState saved=new SavedClientState();RecordingProxy proxy=proxy();
        KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        KOMEClientData client=KOMEClientData.INSTANCE;
        net.minecraft.client.network.NetHandlerPlayClient old=KOMEAccessFixture.allocate(net.minecraft.client.network.NetHandlerPlayClient.class);
        net.minecraft.client.network.NetHandlerPlayClient currentConnection=KOMEAccessFixture.allocate(net.minecraft.client.network.NetHandlerPlayClient.class);
        java.lang.reflect.Constructor<cpw.mods.fml.common.network.simpleimpl.MessageContext> constructor=
            cpw.mods.fml.common.network.simpleimpl.MessageContext.class.getDeclaredConstructor(
                net.minecraft.network.INetHandler.class,cpw.mods.fml.relauncher.Side.class);
        constructor.setAccessible(true);
        cpw.mods.fml.common.network.simpleimpl.MessageContext oldContext=constructor.newInstance(old,cpw.mods.fml.relauncher.Side.CLIENT);
        cpw.mods.fml.common.network.simpleimpl.MessageContext currentContext=constructor.newInstance(currentConnection,cpw.mods.fml.relauncher.Side.CLIENT);
        try {
            client.resetClientState();proxy.snapshots.start(old);
            KOMEPacketConquestData authorized=companySnapshot(true,markerCompany());authorized.publicationSerial=7;
            new KOMEPacketConquestData.Handler().onMessage(authorized,oldContext);proxy.queue.drain();
            assertTrue(client.armyCompanies.containsKey("C1"));
            KOMEPacketConquestData pending=companySnapshot(true,markerCompany());pending.publicationSerial=9;
            new KOMEPacketConquestData.Handler().onMessage(pending,oldContext);
            proxy.snapshots.start(null);proxy.queue.resetSession(false,client::resetClientState);proxy.queue.drain();
            proxy.snapshots.start(currentConnection);proxy.queue.resetSession(true,client::resetClientState);proxy.queue.drain();
            KOMEPacketConquestData incomplete=companySnapshot(false,markerCompany());incomplete.publicationSerial=10;
            new KOMEPacketConquestData.Handler().onMessage(incomplete,oldContext);
            new KOMEPacketConquestData.Handler().onMessage(authorized,oldContext);proxy.queue.drain();
            assertFalse("A former connection must not initialize a new session",client.armyCompanies.containsKey("C1"));
            KOMEPacketConquestData current=companySnapshot(true,markerCompany());current.publicationSerial=8;
            new KOMEPacketConquestData.Handler().onMessage(current,currentContext);proxy.queue.drain();
            assertTrue("A current connection must still publish",client.armyCompanies.containsKey("C1"));
        }finally{saved.restore();KOMEAddon.proxy=previous;}
    }

    @Test public void worldReplacementDiscardsPartialCompanyBatchAndKeepsSerialFloor() throws Exception {
        SavedClientState saved=new SavedClientState();RecordingProxy proxy=proxy();
        KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        KOMEClientData client=KOMEClientData.INSTANCE;
        net.minecraft.client.network.NetHandlerPlayClient connection=KOMEAccessFixture.allocate(net.minecraft.client.network.NetHandlerPlayClient.class);
        java.lang.reflect.Constructor<cpw.mods.fml.common.network.simpleimpl.MessageContext> constructor=
            cpw.mods.fml.common.network.simpleimpl.MessageContext.class.getDeclaredConstructor(
                net.minecraft.network.INetHandler.class,cpw.mods.fml.relauncher.Side.class);
        constructor.setAccessible(true);
        cpw.mods.fml.common.network.simpleimpl.MessageContext context=constructor.newInstance(connection,cpw.mods.fml.relauncher.Side.CLIENT);
        try {
            client.resetClientState();proxy.snapshots.start(connection);
            KOMEPacketConquestData old=companySnapshot(false,markerCompany());old.publicationSerial=10;
            new KOMEPacketConquestData.Handler().onMessage(old,context);
            proxy.snapshots.invalidateWorld();client.armyCompanies.clear();
            KOMEPacketConquestData tail=new KOMEPacketConquestData();tail.publicationSerial=10;tail.complete=true;
            new KOMEPacketConquestData.Handler().onMessage(tail,context);proxy.queue.drain();
            assertTrue(client.armyCompanies.isEmpty());
            KOMEPacketConquestData current=companySnapshot(true,markerCompany());current.publicationSerial=11;
            new KOMEPacketConquestData.Handler().onMessage(current,context);proxy.queue.drain();
            assertTrue(client.armyCompanies.containsKey("C1"));
        }finally{saved.restore();KOMEAddon.proxy=previous;}
    }

    @Test public void lateUnitPublicationCannotResurrectRemovedCompanyAndReconnectRequiresNewSnapshot()throws Exception {
        SavedClientState saved=new SavedClientState();RecordingProxy proxy=proxy();KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        KOMEClientData client=KOMEClientData.INSTANCE;
        KOMEUnitMapMarkerPublisher markers=new KOMEUnitMapMarkerPublisher(proxy.queue,rows->{client.unitMapMarkers.clear();client.unitMapMarkers.addAll(rows);});
        Object old=new Object(),next=new Object();markers.start(old);
        try {
            client.resetClientState();KOMEUnitMapMarker marker=physicalMarker();
            new KOMEPacketConquestData.Handler().onMessage(companySnapshot(true,markerCompany()),null);proxy.queue.drain();
            new KOMEPacketConquestData.Handler().onMessage(companySnapshot(true),null);markers.accept(Arrays.asList(marker),old);proxy.queue.drain();
            assertFalse("A late unit packet cannot override completed company absence",markerCurrent(client.unitMapMarkers.get(0)));assertTrue(markerDeductions().isEmpty());
            new KOMEPacketConquestData.Handler().onMessage(companySnapshot(true,markerCompany()),null);
            markers.accept(Arrays.asList(marker),old);markers.start(null);proxy.resetSession(false,client::resetClientState);
            markers.start(next);proxy.resetSession(true,client::resetClientState);proxy.queue.drain();assertTrue(client.unitMapMarkers.isEmpty());assertTrue(client.armyCompanies.isEmpty());
            markers.accept(Arrays.asList(marker),old);proxy.queue.drain();assertTrue(client.unitMapMarkers.isEmpty());
            markers.accept(Arrays.asList(marker),next);proxy.queue.drain();assertTrue("Fresh connection is initially awaiting its company snapshot",markerCurrent(client.unitMapMarkers.get(0)));
            new KOMEPacketConquestData.Handler().onMessage(companySnapshot(true,markerCompany()),null);proxy.queue.drain();assertTrue(markerCurrent(marker));
        }finally{saved.restore();KOMEAddon.proxy=previous;}
    }

    private static KOMEArmyCompany markerCompany(){KOMEArmyCompany c=new KOMEArmyCompany();c.id="C1";c.currentTile="T100";c.nativeFaction="rohan";c.totalPopulation=25;return c;}
    private static KOMEUnitMapMarker physicalMarker(){KOMEUnitMapMarker m=new KOMEUnitMapMarker();m.entityId="C1";m.currentTile="T100";m.unitCount=1;m.population=25;m.dimensionId=lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID;return m;}
    private static KOMEPacketConquestData companySnapshot(boolean complete,KOMEArmyCompany... companies){KOMEPacketConquestData p=new KOMEPacketConquestData();p.reset=true;p.complete=complete;NBTTagList rows=new NBTTagList();for(KOMEArmyCompany c:companies)rows.appendTag(KOMECompanyMapProjection.companyTag(c));p.data.setTag("ArmyCompanies",rows);return p;}
    private static boolean markerCurrent(KOMEUnitMapMarker marker)throws Exception{java.lang.reflect.Method m=KOMEConquestMapOverlay.class.getDeclaredMethod("companyMarkerCurrent",KOMEUnitMapMarker.class);m.setAccessible(true);return (Boolean)m.invoke(null,marker);}
    private static Map markerDeductions()throws Exception{java.lang.reflect.Method m=KOMEConquestMapOverlay.class.getDeclaredMethod("liveCompanyStationedPopulationByTile");m.setAccessible(true);return (Map)m.invoke(null);}
    @Test public void startTickDrainsFifoSnapshotAndFailureDoesNotBlockLaterTasks() {
        KOMEClientTaskQueue queue = connected(); List<Integer> order = new ArrayList<Integer>();
        queue.enqueue(() -> { order.add(1); queue.enqueue(() -> order.add(4)); });
        queue.enqueue(() -> { throw new IllegalStateException("Injected client publication failure"); });
        queue.enqueue(() -> order.add(3));
        queue.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END)); assertTrue(order.isEmpty());
        queue.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.START));
        assertEquals(Arrays.asList(1, 3), order); assertEquals(1, queue.pendingTasks());
        queue.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.START)); assertEquals(Arrays.asList(1, 3, 4), order);
    }

    @Test public void queueRejectsNewestAtBoundAndDisconnectDiscardsOldSession() {
        KOMEClientTaskQueue queue = connected(); List<Integer> calls = new ArrayList<Integer>();
        for (int i = 0; i < KOMEClientTaskQueue.MAX_PENDING_TASKS; i++) queue.enqueue(() -> calls.add(1));
        try { queue.enqueue(() -> calls.add(2)); fail("overflow"); } catch (RejectedExecutionException expected) { }
        assertEquals(KOMEClientTaskQueue.MAX_PENDING_TASKS, queue.pendingTasks());
        queue.resetSession(false, () -> calls.add(0));
        assertTrue(calls.isEmpty()); assertEquals(1, queue.pendingTasks());
        try { queue.enqueue(() -> calls.add(3)); fail("disconnected"); } catch (RejectedExecutionException expected) { }
        queue.drain(); assertEquals(Arrays.asList(0), calls);
        queue.resetSession(true, () -> calls.add(4)); queue.enqueue(() -> calls.add(5)); queue.drain();
        assertEquals(Arrays.asList(0, 4, 5), calls);
    }

    @Test public void latestPublicationReplacesPendingValueAndDisconnectedSessionIgnoresIt() {
        KOMEClientTaskQueue queue = connected(); List<Integer> calls = new ArrayList<Integer>();
        assertTrue(queue.enqueueLatest("snapshot", () -> calls.add(1)));
        assertTrue(queue.enqueueLatest("snapshot", () -> calls.add(2)));
        assertEquals(1, queue.pendingTasks());
        queue.drain(); assertEquals(Arrays.asList(2), calls);

        assertTrue(queue.enqueueLatest("snapshot", () -> calls.add(3)));
        queue.resetSession(false, () -> calls.add(4));
        assertFalse(queue.enqueueLatest("snapshot", () -> calls.add(5)));
        queue.drain(); assertEquals(Arrays.asList(2, 4), calls);
    }

    @Test public void fourGuiHandlersDeepCopyAndOnlyDisplayOnClientDrain() throws Exception {
        RecordingProxy proxy = proxy(); KOMECommonProxy previous = KOMEAddon.proxy; KOMEAddon.proxy = proxy;
        try {
            KOMEPacketPopulationGui population = new KOMEPacketPopulationGui(); population.playerName = "Before";
            KOMEPacketPopulationUnitsGui units = new KOMEPacketPopulationUnitsGui(); units.playerName = "Before";
            KOMEPacketConquestCaptureGui capture = new KOMEPacketConquestCaptureGui(); capture.tileId = "T1";
            KOMEPacketCompanyListGui companies = new KOMEPacketCompanyListGui(); companies.tileId = "T1";
            onNetwork(() -> {
                new KOMEPacketPopulationGui.Handler().onMessage(population, null);
                new KOMEPacketPopulationUnitsGui.Handler().onMessage(units, null);
                new KOMEPacketConquestCaptureGui.Handler().onMessage(capture, null);
                new KOMEPacketCompanyListGui.Handler().onMessage(companies, null);
            });
            assertTrue(proxy.calls.isEmpty()); assertEquals(4, proxy.queue.pendingTasks());
            population.playerName = "After"; units.playerName = "After"; capture.tileId = "T2"; companies.tileId = "T2";
            proxy.queue.drain();
            assertEquals(Arrays.asList("population:Before", "units:Before", "capture:T1", "companies:T1"), proxy.calls);
            for (Thread thread : proxy.threads) assertSame(Thread.currentThread(), thread);
        } finally { KOMEAddon.proxy = previous; }
    }

    @Test public void movementPreviewHandlersPublishDetachedStateOnClientQueue() throws Exception {
        RecordingProxy proxy = proxy(); KOMECommonProxy previous = KOMEAddon.proxy; KOMEAddon.proxy = proxy;
        try {
            KOMEPacketCompanyMoveConfirmGui confirm = new KOMEPacketCompanyMoveConfirmGui();
            confirm.companyId = "C1"; confirm.destinationTile = "T376"; confirm.previewToken = "before";
            confirm.routeTiles.addAll(Arrays.asList("T388", "T379", "T376"));
            KOMEPacketCompanyMovePreviewResult denied = new KOMEPacketCompanyMovePreviewResult();
            denied.companyId = "C2"; denied.failureCode = "MOUNTAIN_EDGE_BLOCKED";
            onNetwork(() -> {
                new KOMEPacketCompanyMoveConfirmGui.Handler().onMessage(confirm, null);
                new KOMEPacketCompanyMovePreviewResult.Handler().onMessage(denied, null);
            });
            assertTrue(proxy.calls.isEmpty()); assertEquals(2, proxy.queue.pendingTasks());
            confirm.destinationTile = "T999"; confirm.previewToken = "after"; confirm.routeTiles.clear();
            denied.failureCode = "after";
            proxy.queue.drain();
            assertEquals(Arrays.asList("move:C1:T376:before:3", "move-denied:C2:MOUNTAIN_EDGE_BLOCKED"), proxy.calls);
            for (Thread thread : proxy.threads) assertSame(Thread.currentThread(), thread);
        } finally { KOMEAddon.proxy = previous; }
    }

    @Test public void clientProxyOpensMapBeforePublishingPreviewState() throws Exception {
        String source = source("client/KOMEClientProxy.java");
        int method = source.indexOf("displayCompanyMoveConfirmGui");
        int open = source.indexOf("openPreservedMap()", method);
        int publish = source.indexOf("beginRoutePreview(message)", method);
        assertTrue(method >= 0 && open > method && publish > open);
        int denied = source.indexOf("displayCompanyMovePreviewResult", publish);
        int deniedOpen = source.indexOf("openPreservedMap()", denied);
        int deniedPublish = source.indexOf("showCompanyMovePreviewResult(message)", denied);
        assertTrue(deniedOpen > denied && deniedPublish > deniedOpen);
    }

    @Test public void conquestPublishesSevenValidatedMapsAndRevisionInExactlyOneClientTask() throws Exception {
        RecordingProxy proxy = proxy(); KOMECommonProxy previous = KOMEAddon.proxy; KOMEAddon.proxy = proxy;
        KOMEClientData client = KOMEClientData.INSTANCE;
        List<Map> maps = Arrays.<Map>asList(client.armyCompanies, client.conquestTiles, client.armyMovements,
                client.troopSummaries, client.routeEdges, client.tileWaypointLinksByTileId, client.builds);
        SavedClientState saved = new SavedClientState();
        int revision = client.conquestRevision;
        try {
            saved.clear();
            KOMEPacketConquestData packet = new KOMEPacketConquestData(); packet.reset = true; packet.complete = true;
            KOMEArmyCompany company = new KOMEArmyCompany(); company.id = "C1";
            row(packet, "ArmyCompanies", company.writeToNBT());
            KOMEConquestTile tile = new KOMEConquestTile("T1"); tile.claim("gondor", 0L); row(packet, "ConquestTiles", tile.projectToNBT());
            KOMEArmyMovementOrder movement = new KOMEArmyMovementOrder(); movement.id = "M1"; movement.status = KOMEArmyMovementOrder.MOVING;
            row(packet, "ArmyMovements", movement.writeToNBT());
            KOMETileTroopSummary summary = new KOMETileTroopSummary(); summary.tileId = "T1";
            summary.population = new KOMEPopulationProjection("gondor", 2450L, BigInteger.ZERO, BigInteger.ZERO, false, 0L);
            row(packet, "TroopSummaries", summary.writeToNBT());
            row(packet, "RouteEdges", new KOMEConquestRouteEdge("T1", "T2", "open").writeToNBT());
            KOMETileWaypointLink link = new KOMETileWaypointLink(); link.tileId = "T1"; link.lotrWaypointKey = "waypoint";
            row(packet, "TileWaypointLinks", link.writeToNBT());
            NBTTagCompound marker = new NBTTagCompound(); marker.setString("Id", "B1"); marker.setString("BuildType", "NORMAL");
            marker.setBoolean("Active", true); marker.setBoolean("MarkerVisible", true); row(packet, "BuildMarkers", marker);
            onNetwork(() -> new KOMEPacketConquestData.Handler().onMessage(packet, null));
            assertEquals(1, proxy.queue.pendingTasks()); for (Map map : maps) assertTrue(map.isEmpty());
            assertEquals(revision, client.conquestRevision);
            packet.data = new NBTTagCompound(); packet.reset = false; packet.complete = false;
            proxy.queue.drain();
            for (Map map : maps) assertEquals(1, map.size()); assertEquals(revision + 1, client.conquestRevision);
            assertEquals(2450L, client.troopSummaries.get("T1").population.availablePopulationCenti);
        } finally {
            saved.restore(); KOMEAddon.proxy = previous;
        }
    }

    @Test public void resetIntermediateCompletePublishesAtomicallyAndNewResetReplacesIncompleteGeneration() throws Exception {
        RecordingProxy proxy = proxy(); KOMECommonProxy previous = KOMEAddon.proxy; KOMEAddon.proxy = proxy;
        SavedClientState saved = new SavedClientState(); KOMEClientData client = KOMEClientData.INSTANCE;
        int revision = client.conquestRevision;
        try {
            saved.clear();
            KOMEConquestTile sentinel = new KOMEConquestTile("T9000"); sentinel.claim("rohan", 0L);
            client.conquestTiles.put(sentinel.id, sentinel);

            onNetwork(() -> new KOMEPacketConquestData.Handler().onMessage(tilePacket("T1", false, true), null));
            assertEquals(0, proxy.queue.pendingTasks());
            onNetwork(() -> {
                new KOMEPacketConquestData.Handler().onMessage(tilePacket("T10", true, false), null);
                new KOMEPacketConquestData.Handler().onMessage(tilePacket("T11", false, false), null);
                new KOMEPacketConquestData.Handler().onMessage(tilePacket("T20", true, false), null);
                new KOMEPacketConquestData.Handler().onMessage(tilePacket("T21", false, true), null);
            });
            assertEquals(1, proxy.queue.pendingTasks());
            assertSame(sentinel, client.conquestTiles.get(sentinel.id));
            assertEquals(revision, client.conquestRevision);
            proxy.queue.drain();
            assertEquals(new HashSet<String>(Arrays.asList("T20", "T21")), client.conquestTiles.keySet());
            assertFalse(client.conquestTiles.containsKey("T10"));
            assertFalse(client.conquestTiles.containsKey("T11"));
            assertEquals(revision + 1, client.conquestRevision);
        } finally { saved.restore(); KOMEAddon.proxy = previous; }
    }

    @Test public void rapidCompletedRefreshesCoalescePastOldQueueLimitAndPublishLatestOnly() throws Exception {
        RecordingProxy proxy = proxy(); KOMECommonProxy previous = KOMEAddon.proxy; KOMEAddon.proxy = proxy;
        SavedClientState saved = new SavedClientState(); KOMEClientData client = KOMEClientData.INSTANCE;
        int revision = client.conquestRevision;
        try {
            saved.clear();
            onNetwork(() -> {
                for (int i = 0; i < KOMEClientTaskQueue.MAX_PENDING_TASKS + 72; i++)
                    new KOMEPacketConquestData.Handler().onMessage(tilePacket("T" + (1000 + i), true, true), null);
            });
            assertEquals(1, proxy.queue.pendingTasks());
            assertEquals(revision, client.conquestRevision);
            proxy.queue.drain();
            assertEquals(Collections.singleton("T1199"), client.conquestTiles.keySet());
            assertEquals(revision + 1, client.conquestRevision);
        } finally { saved.restore(); KOMEAddon.proxy = previous; }
    }

    @Test public void realChunkerCanExceedOldTaskBoundButPublishesOneCompleteSnapshot() throws Exception {
        KOMEAccessFixture fixture = new KOMEAccessFixture();
        RecordingProxy proxy = proxy(); KOMECommonProxy previousProxy = KOMEAddon.proxy;
        cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper previousNetwork = KOMEPacketHandler.network;
        SavedClientState saved = new SavedClientState(); KOMEClientData client = KOMEClientData.INSTANCE;
        int revision = client.conquestRevision;
        try {
            saved.clear(); KOMEAddon.proxy = proxy; KOMEPacketHandler.network = fixture.network;
            KOMEWorldData data = new KOMEWorldData("large-conquest-snapshot");
            for (int i = 0; i < 3100; i++) {
                KOMEConquestTile tile = new KOMEConquestTile("T" + (10000 + i));
                tile.claim("gondor", i); data.conquestTiles.put(tile.id, tile);
            }
            KOMEPacketConquestData.sendChunked(data, fixture.player);
            assertTrue(fixture.network.messages.size() > KOMEClientTaskQueue.MAX_PENDING_TASKS);
            int resets = 0, completes = 0;
            for (IMessage value : fixture.network.messages) {
                KOMEPacketConquestData packet = (KOMEPacketConquestData) value;
                if (packet.reset) resets++;
                if (packet.complete) completes++;
            }
            assertEquals(1, resets); assertEquals(1, completes);

            onNetwork(() -> {
                for (IMessage value : fixture.network.messages)
                    new KOMEPacketConquestData.Handler().onMessage((KOMEPacketConquestData) value, null);
            });
            assertEquals(1, proxy.queue.pendingTasks());
            assertTrue(client.conquestTiles.isEmpty()); assertEquals(revision, client.conquestRevision);
            proxy.queue.drain();
            assertEquals(3100, client.conquestTiles.size());
            assertEquals(3100, client.troopSummaries.size());
            assertEquals(revision + 1, client.conquestRevision);
        } finally {
            saved.restore(); KOMEAddon.proxy = previousProxy; KOMEPacketHandler.network = previousNetwork;
        }
    }

    @Test public void disconnectIgnoresInFlightPacketsAndReconnectRequiresFreshResetSnapshot() throws Exception {
        RecordingProxy proxy = proxy(); KOMECommonProxy previous = KOMEAddon.proxy; KOMEAddon.proxy = proxy;
        SavedClientState saved = new SavedClientState(); KOMEClientData client = KOMEClientData.INSTANCE;
        int revision = client.conquestRevision;
        try {
            saved.clear();
            KOMEConquestTile sentinel = new KOMEConquestTile("T9000"); sentinel.claim("rohan", 0L);
            client.conquestTiles.put(sentinel.id, sentinel);
            onNetwork(() -> new KOMEPacketConquestData.Handler().onMessage(tilePacket("T1", true, false), null));

            proxy.resetSession(false, () -> {});
            onNetwork(() -> {
                new KOMEPacketConquestData.Handler().onMessage(tilePacket("T2", false, true), null);
                new KOMEPacketConquestData.Handler().onMessage(tilePacket("T3", true, true), null);
            });
            assertEquals(1, proxy.queue.pendingTasks());
            proxy.queue.drain(); assertSame(sentinel, client.conquestTiles.get(sentinel.id));
            assertEquals(revision, client.conquestRevision);

            proxy.resetSession(true, () -> {});
            onNetwork(() -> {
                new KOMEPacketConquestData.Handler().onMessage(tilePacket("T4", false, true), null);
                new KOMEPacketConquestData.Handler().onMessage(tilePacket("T5", true, true), null);
            });
            assertEquals(2, proxy.queue.pendingTasks());
            proxy.queue.drain();
            assertEquals(Collections.singleton("T5"), client.conquestTiles.keySet());
            assertEquals(revision + 1, client.conquestRevision);
        } finally { saved.restore(); KOMEAddon.proxy = previous; }
    }

    @Test public void invalidGuiAndLaterConquestRowsEnqueueNothing() throws Exception {
        RecordingProxy proxy = proxy(); KOMECommonProxy previous = KOMEAddon.proxy; KOMEAddon.proxy = proxy;
        try {
            KOMEPacketPopulationGui invalid = new KOMEPacketPopulationGui(); invalid.playerName = repeat('x', 4097);
            try { new KOMEPacketPopulationGui.Handler().onMessage(invalid, null); fail("invalid text"); } catch (IllegalArgumentException expected) { }
            KOMEPacketConquestData conquest = new KOMEPacketConquestData();
            NBTTagCompound invalidMarker = new NBTTagCompound(); invalidMarker.setString("BuildType", "UNKNOWN");
            row(conquest, "BuildMarkers", invalidMarker);
            try { new KOMEPacketConquestData.Handler().onMessage(conquest, null); fail("invalid marker"); } catch (IllegalArgumentException expected) { }
            assertEquals(0, proxy.queue.pendingTasks()); assertTrue(proxy.calls.isEmpty());
        } finally { KOMEAddon.proxy = previous; }
    }

    @Test public void commonInitializationDoesNotReferenceClientQueueAndClientProxyOwnsRegistration() throws Exception {
        String common = source("common/KOMECommonProxy.java");
        assertFalse(common.contains("kome.client")); assertFalse(common.contains("KOMEClientTaskQueue"));
        assertFalse(source("common/KOMEAddon.java").contains("new KOMEClientTaskQueue"));
        String client = source("client/KOMEClientProxy.java");
        assertTrue(client.contains("bus().register(clientTasks)"));
        assertTrue(client.contains("Minecraft.getMinecraft().func_152345_ab()"));
        assertTrue(client.contains("ClientDisconnectionFromServerEvent"));
        assertTrue(client.contains("clientTasks.resetSession(false, this::resetClientSessionState)"));
        assertTrue(client.contains("conquestSnapshots.start(event == null ? null : event.handler)"));
        assertTrue(client.contains("conquestSnapshots.start(null)"));
        assertTrue(client.contains("conquestSnapshots.invalidateWorld()"));
        String clientData = source("common/data/KOMEClientData.java");
        String reset = clientData.substring(clientData.indexOf("public void resetClientState()"));
        assertFalse(reset.contains("conquestRevision++"));
        for (String name : new String[] {"PopulationGui", "PopulationUnitsGui", "ConquestCaptureGui", "CompanyListGui"}) {
            String packet = source("common/network/KOMEPacket" + name + ".java");
            assertTrue(packet.contains("copyForPublication(")); assertTrue(packet.contains("enqueueClientTask("));
            assertFalse(packet.contains("displayGuiScreen("));
        }
        String conquest = source("common/network/KOMEPacketConquestData.java");
        assertTrue(conquest.contains("copyForPublication("));
        assertTrue(conquest.contains("acceptConquestSnapshotChunk("));
        assertFalse(conquest.contains("enqueueClientTask("));
    }

    @Test public void clientThreadConquestBurstDoesNotOverflowOrLoseResetAndCompletion() throws Exception {
        RecordingProxy proxy = proxy(); Thread clientThread = Thread.currentThread();
        proxy.queue = new KOMEClientTaskQueue(() -> Thread.currentThread() == clientThread);
        proxy.snapshots = new KOMEConquestSnapshotPublisher(proxy.queue);
        KOMECommonProxy previous = KOMEAddon.proxy; KOMEAddon.proxy = proxy;
        KOMEClientData client = KOMEClientData.INSTANCE;
        List<Map> maps = Arrays.<Map>asList(client.armyCompanies, client.conquestTiles, client.capitalTilesByFaction,
                client.armyMovements, client.troopSummaries, client.routeEdges, client.tileWaypointLinksByTileId, client.builds);
        List<Map> saved = new ArrayList<Map>(); for (Map map : maps) saved.add(new HashMap(map));
        int revision = client.conquestRevision;
        try {
            proxy.queue.resetSession(true, () -> client.conquestTiles.clear()); proxy.queue.drain();
            // Same production handler and reset/continuation/complete sequence as a hire-triggered sync.
            // Four queue capacities between ticks would disconnect the previous implementation.
            int count = KOMEClientTaskQueue.MAX_PENDING_TASKS * 4;
            for (int i = 0; i < count; i++) {
                KOMEPacketConquestData packet = new KOMEPacketConquestData();
                packet.reset = i == 0; packet.complete = i == count - 1;
                KOMEConquestTile tile = new KOMEConquestTile("T" + (i + 1)); tile.claim("angmar", 0L);
                row(packet, "ConquestTiles", tile.projectToNBT());
                io.netty.buffer.ByteBuf wire=io.netty.buffer.Unpooled.buffer();
                KOMEPacketConquestData received=new KOMEPacketConquestData();
                try {packet.toBytes(wire);received.fromBytes(wire);}finally{wire.release();}
                new KOMEPacketConquestData.Handler().onMessage(received, null);
                assertEquals(0, proxy.queue.pendingTasks());
                assertEquals(revision + (packet.complete ? 1 : 0), client.conquestRevision);
            }
            assertEquals(count, client.conquestTiles.size());
            assertTrue(client.conquestTiles.containsKey("T1"));
            assertTrue(client.conquestTiles.containsKey("T" + count));
        } finally {
            for (int i = 0; i < maps.size(); i++) { maps.get(i).clear(); maps.get(i).putAll(saved.get(i)); }
            client.conquestRevision = revision; KOMEAddon.proxy = previous;
        }
    }

    @Test public void clientDispatchPreservesOlderWorkAndBoundsOffThreadIngress() throws Exception {
        Thread clientThread = Thread.currentThread();
        KOMEClientTaskQueue queue = new KOMEClientTaskQueue(() -> Thread.currentThread() == clientThread);
        List<Integer> calls = new ArrayList<Integer>();
        queue.resetSession(true, () -> calls.add(0)); queue.drain();
        onNetwork(() -> { for (int i = 0; i < KOMEClientTaskQueue.MAX_PENDING_TASKS; i++) queue.enqueue(() -> calls.add(1)); });
        assertEquals(1, calls.size());
        queue.enqueue(() -> calls.add(2));
        assertEquals(KOMEClientTaskQueue.MAX_PENDING_TASKS + 2, calls.size());
        assertEquals(Integer.valueOf(0), calls.get(0)); assertEquals(Integer.valueOf(2), calls.get(calls.size() - 1));
        assertEquals(0, queue.pendingTasks());
        queue.resetSession(false, () -> calls.add(3)); queue.drain();
        try { queue.enqueue(() -> calls.add(99)); fail("Disconnected work must not run"); } catch (RejectedExecutionException expected) { }
        queue.resetSession(true, () -> calls.add(4)); queue.enqueue(() -> calls.add(5));
        assertEquals(Arrays.asList(3, 4, 5), calls.subList(calls.size() - 3, calls.size()));
    }

    @Test public void immediateDispatchDoesNotRecursivelyDrainTasksCreatedByTasks() {
        KOMEClientTaskQueue queue = new KOMEClientTaskQueue(() -> true);
        List<Integer> calls = new ArrayList<Integer>(); queue.resetSession(true, () -> {}); queue.drain();
        queue.enqueue(() -> { calls.add(1); queue.enqueue(() -> calls.add(2)); assertEquals(0, queue.drain()); });
        assertEquals(Collections.singletonList(1), calls); assertEquals(1, queue.pendingTasks());
        queue.drain(); assertEquals(Arrays.asList(1, 2), calls);
    }

    @Test public void decodedConquestHandoffAvoidsReencodingAndIsolatesPublicMutation() throws Exception {
        RecordingProxy proxy = proxy(); KOMECommonProxy previous=KOMEAddon.proxy; KOMEAddon.proxy=proxy;
        KOMEClientData client=KOMEClientData.INSTANCE; Map saved=new HashMap(client.conquestTiles);
        int revision=client.conquestRevision;
        try {
            for(String owner : Arrays.asList("angmar","gondor")) {
                KOMEPacketConquestData source=new KOMEPacketConquestData(); source.reset=true;
                KOMEConquestTile tile=new KOMEConquestTile("T001");tile.claim(owner,0L);
                row(source,"ConquestTiles",tile.projectToNBT());
                // Initialize a full generation as required by dev; decode through the actual wire reader.
                io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();
                KOMEPacketConquestData decoded=new KOMEPacketConquestData(){
                    @Override public void toBytes(io.netty.buffer.ByteBuf b){throw new AssertionError("decoded packet reencoded");}
                };
                try {source.toBytes(bytes);decoded.fromBytes(bytes);}finally{bytes.release();}
                decoded.data.setString("ConquestTiles","mutated after decode");decoded.complete=false;decoded.reset=true;
                onNetwork(() -> new KOMEPacketConquestData.Handler().onMessage(decoded,null));
                assertEquals(1,proxy.queue.pendingTasks());
                proxy.queue.drain();
                assertEquals(owner,((KOMEConquestTile)client.conquestTiles.get("T001")).projectRulingFaction());
            }
            // Both snapshots were materialized off-thread and published in order.
        } finally {
            proxy.queue.drain();
            assertEquals("gondor",((KOMEConquestTile)client.conquestTiles.get("T001")).projectRulingFaction());
            assertEquals(revision+2,client.conquestRevision);
            client.conquestTiles.clear();client.conquestTiles.putAll(saved);client.conquestRevision=revision;KOMEAddon.proxy=previous;
        }
    }

    @Test public void completedOwnershipColorsSurviveFollowingPartialResetUntilCompletion() throws Exception {
        RecordingProxy proxy=proxy();KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        KOMEClientData client=KOMEClientData.INSTANCE;Map saved=new HashMap(client.conquestTiles);int revision=client.conquestRevision;
        Field colorField=KOMEConquestMapOverlay.class.getDeclaredField("tileColorsById");colorField.setAccessible(true);
        Map colors=(Map)colorField.get(null);Map oldColors=new HashMap(colors);
        try {
            colors.clear();colors.put("T001",1);colors.put("T002",2);
            KOMEPacketConquestData full=new KOMEPacketConquestData();full.reset=true;
            NBTTagList rows=new NBTTagList();
            for(String id:Arrays.asList("T001","T002")){KOMEConquestTile tile=new KOMEConquestTile(id);tile.claim("angmar",0);rows.appendTag(tile.projectToNBT());}
            full.data.setTag("ConquestTiles",rows);new KOMEPacketConquestData.Handler().onMessage(full,null);proxy.queue.drain();
            Map<Integer,Integer> completed=KOMEConquestMapOverlay.effectiveClaimColors();assertEquals(2,completed.size());
            KOMEPacketConquestData partial=new KOMEPacketConquestData();partial.reset=true;partial.complete=false;
            KOMEConquestTile one=new KOMEConquestTile("T002");one.claim("gondor",0);row(partial,"ConquestTiles",one.projectToNBT());
            new KOMEPacketConquestData.Handler().onMessage(partial,null);proxy.queue.drain();
            // Renderer may run after completion N but before completion N+1. Do not read the partial batch.
            assertEquals(completed,KOMEConquestMapOverlay.effectiveClaimColors());
            new KOMEPacketConquestData.Handler().onMessage(new KOMEPacketConquestData(),null);proxy.queue.drain();
            Map<Integer,Integer> next=KOMEConquestMapOverlay.effectiveClaimColors();assertEquals(1,next.size());
            assertFalse(next.containsKey(1));assertNotEquals(completed.get(2),next.get(2));
        }finally{client.conquestTiles.clear();client.conquestTiles.putAll(saved);client.completeConquestUpdate();client.conquestRevision=revision;colors.clear();colors.putAll(oldColors);KOMEAddon.proxy=previous;}
    }

    private static KOMEClientTaskQueue connected() { KOMEClientTaskQueue queue = new KOMEClientTaskQueue(); queue.resetSession(true, () -> {}); queue.drain(); return queue; }
    private static KOMEPacketConquestData tilePacket(String tileId, boolean reset, boolean complete) {
        KOMEPacketConquestData packet = new KOMEPacketConquestData();
        packet.reset = reset; packet.complete = complete;
        KOMEConquestTile tile = new KOMEConquestTile(tileId); tile.claim("gondor", 0L);
        row(packet, "ConquestTiles", tile.projectToNBT()); return packet;
    }
    private static void row(KOMEPacketConquestData packet, String key, NBTTagCompound value) {
        NBTTagList list = new NBTTagList(); list.appendTag(value); packet.data.setTag(key, list);
    }

    private static final class SavedClientState {
        private final KOMEClientData client = KOMEClientData.INSTANCE;
        private final List<Map> maps = Arrays.<Map>asList(client.armyCompanies, client.conquestTiles,
            client.capitalTilesByFaction, client.armyMovements, client.troopSummaries,
            client.routeEdges, client.tileWaypointLinksByTileId, client.builds);
        private final List<Map> values = new ArrayList<Map>();
        private final int revision = client.conquestRevision;
        private final List<KOMEUnitMapMarker> markers=new ArrayList<>(client.unitMapMarkers);
        private final Map<Field,Object> lifecycle=new HashMap<>();

        private SavedClientState() {
            for (Map map : maps) values.add(new HashMap(map));
            try {for(Field f:KOMEClientData.class.getDeclaredFields())if(f.getType()==boolean.class||f.getName().equals("conquestRenderOwners")){f.setAccessible(true);lifecycle.put(f,f.get(client));}}
            catch(IllegalAccessException e){throw new AssertionError(e);}
        }

        private void clear() {
            for (Map map : maps) map.clear();
        }

        private void restore() {
            for (int i = 0; i < maps.size(); i++) {
                maps.get(i).clear(); maps.get(i).putAll(values.get(i));
            }
            client.conquestRevision = revision;
            client.unitMapMarkers.clear();client.unitMapMarkers.addAll(markers);
            try{for(Map.Entry<Field,Object> e:lifecycle.entrySet())e.getKey().set(client,e.getValue());}catch(IllegalAccessException e){throw new AssertionError(e);}
        }
    }

    private static void onNetwork(Runnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread network = new Thread(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } }, "simulated-Netty");
        network.start(); network.join(10000L); assertFalse("Handler must return", network.isAlive());
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
    private static cpw.mods.fml.common.network.simpleimpl.MessageContext clientContext(
            net.minecraft.client.network.NetHandlerPlayClient connection) throws Exception {
        java.lang.reflect.Constructor<cpw.mods.fml.common.network.simpleimpl.MessageContext> constructor=
            cpw.mods.fml.common.network.simpleimpl.MessageContext.class.getDeclaredConstructor(
                net.minecraft.network.INetHandler.class,cpw.mods.fml.relauncher.Side.class);
        constructor.setAccessible(true);
        return constructor.newInstance(connection,cpw.mods.fml.relauncher.Side.CLIENT);
    }
    private static RecordingProxy proxy() throws Exception {
        // Do not initialize the three imported mods merely to observe GUI dispatch.
        Class<?> unsafe = Class.forName("sun.misc.Unsafe"); Field singleton = unsafe.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        RecordingProxy proxy = (RecordingProxy) unsafe.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), RecordingProxy.class);
        proxy.queue = connected(); proxy.snapshots = new KOMEConquestSnapshotPublisher(proxy.queue);
        proxy.calls = new ArrayList<String>(); proxy.threads = new ArrayList<Thread>();
        proxy.details=new KOMEMapDetailRequests();proxy.refreshes=new ArrayList<String>();return proxy;
    }
    private static class RecordingProxy extends KOMECommonProxy {
        KOMEClientTaskQueue queue; KOMEConquestSnapshotPublisher snapshots;
        KOMEMapDetailRequests details;
        List<String> calls; List<Thread> threads;List<String> refreshes;
        @Override public void enqueueClientTask(Runnable task) { queue.enqueue(task); }
        @Override public void acceptConquestSnapshotChunk(KOMEPacketConquestData.PublicationChunk chunk,Object connection) {
            if(connection==null)snapshots.accept(chunk);else snapshots.accept(chunk,connection);
        }
        @Override public boolean allowsMapDetail(long serial,Object connection) {
            return connection==null && serial==0L || snapshots.allowsMapDetail(serial,connection);
        }
        @Override public void cancelMapDetailRequest(Object screen) { details.cancelFromScreen(screen); }
        @Override public void handleMapDetailResponse(long id,String kind,String tile,String focus,
                long serial,Object connection,Runnable publish) {
            if(id==0L){if(allowsMapDetail(serial,connection))publish.run();return;}
            KOMEMapDetailRequests.Decision decision=details.resolve(id,kind,tile,focus,
                serial,connection,null,snapshots);
            if(decision==KOMEMapDetailRequests.Decision.OPEN)publish.run();
            else if(decision==KOMEMapDetailRequests.Decision.REFRESH)refreshes.add(kind+":"+tile);
        }
        void resetSession(boolean connected, Runnable reset) {
            snapshots.resetSession(); queue.resetSession(connected, reset);
        }
        private void display(String call) { calls.add(call); threads.add(Thread.currentThread()); }
        @Override public void displayPopulationGui(KOMEPacketPopulationGui packet) { display("population:" + packet.playerName); }
        @Override public void displayPopulationUnitsGui(KOMEPacketPopulationUnitsGui packet) { display("units:" + packet.playerName); }
        @Override public void displayConquestCaptureGui(KOMEPacketConquestCaptureGui packet) { display("capture:" + packet.tileId); }
        @Override public void displayCompanyListGui(String tile, String name, List companies, boolean create) { display("companies:" + tile); }
        @Override public void displayCompanyMoveConfirmGui(KOMEPacketCompanyMoveConfirmGui packet) {
            display("move:" + packet.companyId + ":" + packet.destinationTile + ":" + packet.previewToken
                + ":" + packet.routeTiles.size());
        }
        @Override public void displayCompanyMovePreviewResult(KOMEPacketCompanyMovePreviewResult packet) {
            display("move-denied:" + packet.companyId + ":" + packet.failureCode);
        }
    }
    private static String repeat(char ch, int length) { char[] chars = new char[length]; Arrays.fill(chars, ch); return new String(chars); }
    private static String source(String path) throws Exception { return new String(Files.readAllBytes(Paths.get("src/main/java/kome/" + path)), StandardCharsets.UTF_8); }
}
