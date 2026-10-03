package kome.common.tactical.edit;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.mojang.authlib.GameProfile;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import kome.common.KOMEAccessFixture;
import kome.common.data.*;
import kome.common.network.*;
import kome.common.tactical.KOMETacticalConfigurationCodec;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.server.management.UserList;
import net.minecraft.server.management.UserListOps;
import net.minecraft.server.management.UserListOpsEntry;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import static kome.common.data.KOMETacticalEditorFixtures.*;
import static kome.common.tactical.edit.KOMETacticalEditSessionManager.Status.*;
import static org.junit.Assert.*;

public class KOMETacticalEditRuntimeTest {
    @Rule public final KOMETileTestResources tiles = new KOMETileTestResources();
    private KOMEAccessFixture fixture;
    private SimpleNetworkWrapper previousNetwork;
    private Field serverField;
    private Object previousServer;
    private final KOMETacticalEditRuntime runtime = new KOMETacticalEditRuntime();
    @Before public void setup() throws Exception {
        fixture = new KOMEAccessFixture(); install(fixture.data);
        fixture.player.dimension = dimension(); fixture.world.provider.dimensionId = dimension();
        Field capabilities = EntityPlayer.class.getDeclaredField("capabilities"); capabilities.setAccessible(true);
        capabilities.set(fixture.player, new PlayerCapabilities()); fixture.player.capabilities.isCreativeMode = true;
        previousNetwork = KOMEPacketHandler.network; KOMEPacketHandler.network = fixture.network;
        for (Field field : MinecraftServer.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == MinecraftServer.class) serverField = field;
        }
        assertNotNull(serverField); serverField.setAccessible(true); previousServer = serverField.get(null); serverField.set(null, null);
        KOMETacticalEditRuntime.resetServerState();
    }
    @After public void cleanup() throws Exception {
        KOMETacticalEditRuntime.resetServerState(); KOMEPacketHandler.network = previousNetwork;
        if (serverField != null) serverField.set(null, previousServer);
    }
    private void tick() { runtime.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END)); }
    private KOMEPacketTacticalEditSnapshot last() { return (KOMEPacketTacticalEditSnapshot) fixture.network.messages.get(fixture.network.messages.size() - 1); }
    private KOMETacticalEditSnapshot open() {
        new KOMEPacketTacticalEditRequest.Handler().onMessage(new KOMEPacketTacticalEditRequest(KOMETacticalEditRequest.open(areaScope("FIELD"))), fixture.context);
        tick(); assertEquals(OPENED, last().getStatus()); return last().getSnapshot();
    }
    private NBTTagCompound state() { return KOMETacticalConfigurationCodec.encode(fixture.data.getTacticalConfigurationSnapshot()); }
    @Test public void creativeModeIsAuthorizedWithoutAnOperator() { assertTrue(KOMETacticalEditAccess.isAuthorized(fixture.player)); assertEquals(OPENED, open() == null ? null : last().getStatus()); }
    @Test public void explicitLevelTwoOperatorIsAuthorizedWithoutCreative() throws Exception {
        fixture.player.capabilities.isCreativeMode = false; operator(2);
        assertTrue(KOMETacticalEditAccess.isAuthorized(fixture.player)); open();
    }
    @Test public void ordinaryPlayerAndLevelOneOperatorAreDeniedEvenIfCommandCheckClaimsCheats() throws Exception {
        fixture.player.capabilities.isCreativeMode = false; fixture.player.operator = true;
        assertFalse(KOMETacticalEditAccess.isAuthorized(fixture.player));
        operator(1); assertFalse(KOMETacticalEditAccess.isAuthorized(fixture.player));
        assertTrue(KOMETacticalEditRuntime.enqueue(fixture.player, KOMETacticalEditRequest.open(areaScope("FIELD"))));
        tick(); assertEquals(DENIED, last().getStatus()); assertFalse(fixture.data.isDirty());
    }
    private void operator(int level) throws Exception {
        DedicatedServer server = KOMEAccessFixture.allocate(DedicatedServer.class);
        DedicatedPlayerList players = KOMEAccessFixture.allocate(DedicatedPlayerList.class);
        UserListOps ops = KOMEAccessFixture.allocate(UserListOps.class);
        GameProfile profile = new GameProfile(fixture.player.getUniqueID(), fixture.player.getCommandSenderName());
        UserListOpsEntry entry = new UserListOpsEntry(profile, level);
        Map<String, UserListOpsEntry> values = new HashMap<String, UserListOpsEntry>(); values.put(profile.getId().toString(), entry);
        for (Field field : UserList.class.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(field.getType())) { field.setAccessible(true); field.set(ops, values); }
        }
        for (Field field : ServerConfigurationManager.class.getDeclaredFields()) {
            if (field.getType() == UserListOps.class) { field.setAccessible(true); field.set(players, ops); }
        }
        server.func_152361_a(players); serverField.set(null, server);
    }
    @Test public void ordinaryAreaEditingDoesNotDependOnPhysicalGateFlag() {
        boolean previous = MumakilConfig.enableSiegeGates;
        try { MumakilConfig.enableSiegeGates = false; KOMETacticalEditSnapshot s = open();
            assertTrue(KOMETacticalEditRuntime.enqueue(fixture.player, KOMETacticalEditRequest.update(s, new KOMETacticalEditDraft(area("FIELD", "Saved without gates", 7)))));
            tick(); s = last().getSnapshot(); assertEquals(UPDATED, last().getStatus());
            assertTrue(KOMETacticalEditRuntime.enqueue(fixture.player, KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.SAVE, s)));
            tick(); assertEquals(SAVED, last().getStatus()); assertEquals("Saved without gates", fixture.data.getTacticalConfigurationSnapshot().findForceDeploymentArea("FIELD").getLabel());
        } finally { MumakilConfig.enableSiegeGates = previous; }
    }
    @Test public void packetIntakeFromWorkerDoesNoWorldWorkAndOnlyEndTickExecutesIt() throws Exception {
        NBTTagCompound before = state(); AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread worker = new Thread(() -> { try {
            assertNull(new KOMEPacketTacticalEditRequest.Handler().onMessage(new KOMEPacketTacticalEditRequest(KOMETacticalEditRequest.open(areaScope("FIELD"))), fixture.context));
        } catch (Throwable error) { failure.set(error); } });
        worker.start(); worker.join(); assertNull(failure.get()); assertTrue(fixture.network.messages.isEmpty()); assertEquals(before, state());
        runtime.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START)); assertTrue(fixture.network.messages.isEmpty());
        tick(); assertEquals(OPENED, last().getStatus()); assertFalse(fixture.data.isDirty());
    }
    @Test public void permissionIsRecheckedAfterQueueingRatherThanTrustingClientOrIntake() {
        KOMETacticalEditSnapshot s = open(); NBTTagCompound before = state();
        assertTrue(KOMETacticalEditRuntime.enqueue(fixture.player, KOMETacticalEditRequest.update(s, new KOMETacticalEditDraft(area("FIELD", "Attempt", 7)))));
        fixture.player.capabilities.isCreativeMode = false; tick();
        assertEquals(DENIED, last().getStatus()); assertEquals(before, state()); assertFalse(fixture.data.isDirty());
    }
    @Test public void closedConnectionCannotOpenASessionFromLateQueuedIntent() throws Exception {
        NBTTagCompound before = state();
        assertTrue(KOMETacticalEditRuntime.enqueue(fixture.player, KOMETacticalEditRequest.open(areaScope("FIELD"))));
        Field connection = net.minecraft.network.NetHandlerPlayServer.class.getDeclaredField("netManager");
        connection.setAccessible(true); connection.set(fixture.player.playerNetServerHandler, new net.minecraft.network.NetworkManager(false));
        tick(); assertTrue(fixture.network.messages.isEmpty()); assertEquals(before, state()); assertFalse(fixture.data.isDirty());
    }
    @Test public void logoutRemovesSessionAndQueuedDraftRequests() { cleanupLifecycle(0); }
    @Test public void dimensionChangeRemovesSessionAndQueuedDraftRequests() { cleanupLifecycle(1); }
    @Test public void respawnRemovesSessionAndQueuedDraftRequests() { cleanupLifecycle(2); }
    @Test public void deathRemovesSessionAndQueuedDraftRequests() { cleanupLifecycle(3); }
    private void cleanupLifecycle(int kind) {
        KOMETacticalEditSnapshot s = open(); NBTTagCompound before = state();
        assertTrue(KOMETacticalEditRuntime.enqueue(fixture.player, KOMETacticalEditRequest.update(s, new KOMETacticalEditDraft(area("FIELD", "Never saved", 7)))));
        if (kind == 0) runtime.onLogout(new PlayerEvent.PlayerLoggedOutEvent(fixture.player));
        else if (kind == 1) runtime.onDimension(new PlayerEvent.PlayerChangedDimensionEvent(fixture.player, dimension(), dimension() + 1));
        else if (kind == 2) runtime.onRespawn(new PlayerEvent.PlayerRespawnEvent(fixture.player));
        else runtime.onDeath(new LivingDeathEvent(fixture.player, DamageSource.generic));
        int replies = fixture.network.messages.size(); tick(); assertEquals(replies, fixture.network.messages.size());
        assertTrue(KOMETacticalEditRuntime.enqueue(fixture.player, KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.SAVE, s)));
        tick(); assertEquals(INVALID_SESSION, last().getStatus()); assertEquals(before, state()); assertFalse(fixture.data.isDirty());
    }
    @Test public void boundedNetworkIntakeReturnsRateLimitedInsteadOfUnboundedWork() {
        KOMEPacketTacticalEditRequest.Handler handler = new KOMEPacketTacticalEditRequest.Handler();
        for (int i = 0; i < 8; i++) assertNull(handler.onMessage(new KOMEPacketTacticalEditRequest(KOMETacticalEditRequest.open(areaScope("FIELD"))), fixture.context));
        IMessage rejected = handler.onMessage(new KOMEPacketTacticalEditRequest(KOMETacticalEditRequest.open(areaScope("FIELD"))), fixture.context);
        assertTrue(rejected instanceof KOMEPacketTacticalEditSnapshot); assertEquals(RATE_LIMITED, ((KOMEPacketTacticalEditSnapshot) rejected).getStatus());
        assertTrue(fixture.network.messages.isEmpty()); tick(); assertEquals(8, fixture.network.messages.size()); assertFalse(fixture.data.isDirty());
    }
    @Test public void resetDropsPendingRequestsAndPreviouslyIssuedTokens() {
        KOMETacticalEditSnapshot s = open(); KOMETacticalEditRuntime.enqueue(fixture.player, KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.SAVE, s));
        KOMETacticalEditRuntime.resetServerState(); int replies = fixture.network.messages.size(); tick(); assertEquals(replies, fixture.network.messages.size());
        KOMETacticalEditRuntime.enqueue(fixture.player, KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.SAVE, s)); tick(); assertEquals(INVALID_SESSION, last().getStatus());
    }
}
