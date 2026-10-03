package kome.common.command;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import kome.common.data.*;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.IChatComponent;
import org.junit.Rule;
import org.junit.Test;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.profiler.Profiler;
import static org.junit.Assert.*;

public class KOMEAdminDiagnosticsCommandsTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    private ICommandSender sender(boolean allowed, List<String> messages) {
        return (ICommandSender) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {ICommandSender.class}, (proxy, method, args) -> {
                if (method.getName().equals("canCommandSenderUseCommand")) return allowed;
                if (method.getName().equals("getCommandSenderName")) return "Console";
                if (method.getName().equals("addChatMessage")) {
                    messages.add(((IChatComponent) args[0]).getUnformattedText()); return null;
                }
                throw new AssertionError("Unexpected sender/world access: " + method.getName());
            });
    }
    private KOMEWorldData data() {
        KOMEWorldData data = new KOMEWorldData("disposable-command-fixture");
        data.initializeIntegratedWorld(); return data;
    }
    private void denied(Runnable action) {
        try { action.run(); fail("Expected permission or input rejection"); }
        catch (WrongUsageException expected) { assertFalse(expected.getMessage().isEmpty()); }
    }

    @Test public void rootRejectsAllNewAdminPathsBeforeReadingWorld() {
        ICommandSender sender = sender(false, new ArrayList<String>());
        KOMECommandKome root = new KOMECommandKome();
        for (String domain : new String[] {"population", "ruler", "capital", "diplomacy", "ownership", "waypoint"})
            denied(() -> root.processCommand(sender, new String[] {"diagnostics", domain, "gondor"}));
        denied(() -> root.processCommand(sender, new String[] {"repair", "preview", "ownership", "T100"}));
        denied(() -> root.processCommand(sender, new String[] {"repair", "apply", "token"}));
        denied(() -> root.processCommand(sender, new String[] {"audit", "list", "1"}));
    }

    @Test public void commandPreviewAndApplyUseRealServiceAndSingleUseToken() {
        KOMEWorldData data = data(); KOMEConquestTile t = data.conquestTiles.get("T100");
        t.setCurrentRulingFaction("gondor"); t.ownerFaction = "";
        List<String> messages = new ArrayList<String>(); ICommandSender sender = sender(true, messages);
        KOMEAdminDiagnosticsCommands commands = new KOMEAdminDiagnosticsCommands();
        commands.process(sender, new String[] {"repair", "preview", "ownership", "T100"}, data);
        assertEquals("", t.ownerFaction);
        String token = messages.get(0).split("/kome repair apply ")[1].split(" ")[0];
        commands.process(sender, new String[] {"repair", "apply", token}, data);
        assertEquals("gondor", t.ownerFaction);
        assertTrue(messages.get(1).startsWith("Applied:"));
        commands.process(sender, new String[] {"repair", "apply", token}, data);
        assertTrue(messages.get(2).contains("consumed"));
    }

    @Test public void auditIsPagedNewestFirstWithBoundedRowsAndLineLength() {
        KOMEWorldData data = data();
        String longReason = new String(new char[2000]).replace('\0', 'x');
        for (int i = 0; i < 600; i++) KOMEAuditService.record(data, i, "TEST", "EVENT", "console", "S" + i, longReason, "");
        assertEquals(500, KOMEAuditService.entries(data).size());
        List<String> messages = new ArrayList<String>(); ICommandSender sender = sender(true, messages);
        KOMEAdminDiagnosticsCommands.audit(sender, new String[] {"audit", "list"}, data);
        assertEquals(19, messages.size()); assertTrue(messages.get(1).contains("S599"));
        for (String line : messages) assertTrue(line.length() <= KOMEAdminDiagnostics.MAX_LINE_LENGTH);
        messages.clear();
        KOMEAdminDiagnosticsCommands.audit(sender, new String[] {"audit", "list", "28"}, data);
        assertEquals(15, messages.size());
        denied(() -> KOMEAdminDiagnosticsCommands.audit(sender, new String[] {"audit", "list", "2147483647"}, data));
        denied(() -> KOMEAdminDiagnosticsCommands.audit(sender, new String[] {"audit", "summary", "0"}, data));
    }

    @Test public void diagnosticsAreReadOnlyAndDoNotNormalizeOwnerOrCreateBanks() {
        KOMEWorldData data = data(); KOMEConquestTile t = data.conquestTiles.get("T100");
        t.currentRulingFaction = ""; t.ownerFaction = "gondor";
        int banks = data.factionPopulations.size(); data.setDirty(false);
        for (String domain : new String[] {"population", "ruler", "capital", "diplomacy", "ownership"}) {
            List<String> rows = KOMEAdminDiagnostics.inspect(data, domain,
                domain.equals("ownership") ? "T100" : "gondor", "rohan", null);
            assertTrue(rows.size() <= KOMEAdminDiagnostics.MAX_LINES);
            for (String line : rows) assertTrue(line.length() <= KOMEAdminDiagnostics.MAX_LINE_LENGTH);
        }
        assertEquals("", t.currentRulingFaction); assertEquals("gondor", t.ownerFaction);
        assertEquals(banks, data.factionPopulations.size()); assertFalse(data.isDirty());
    }

    @Test public void populationContributionsAreBoundedAndShowOmittedRows() {
        KOMEWorldData data = data(); data.conquestTiles.get("T100").setCurrentRulingFaction("gondor");
        for (int i = 0; i < 50; i++) {
            KOMEPlayerBuild b = new KOMEPlayerBuild(); b.id = "B" + i; b.tileId = "T100";
            b.type = KOMEBuildType.NORMAL; b.populationFaction = "gondor"; b.active = true;
            data.builds.put(b.id, b);
        }
        List<String> rows = KOMEAdminDiagnostics.inspect(data, "population", "gondor", "", null);
        assertEquals(20, rows.size()); assertTrue(rows.get(19).contains("Omitted 35"));
    }

    @Test public void capitalInspectionChecksChunkPresenceWithoutProvidingChunksOrClaimingLiveSafety() throws Exception {
        KOMEWorldData data = data();
        InspectionWorld world = kome.common.KOMEAccessFixture.allocate(InspectionWorld.class);
        java.lang.reflect.Field provider = World.class.getDeclaredField("provider");
        provider.setAccessible(true);
        provider.set(world, kome.common.KOMEAccessFixture.allocate(WorldProviderSurface.class));
        world.provider.dimensionId = KOMEFactionCapitalService.getCapital(data, "gondor").getDeploymentDimensionId();
        for (boolean loaded : new boolean[] {false, true}) {
            world.loaded = loaded;
            String readiness = KOMEFactionCapitalService.inspectionReadiness(data, "gondor", world);
            assertTrue(readiness, readiness.contains(loaded ? "chunk loaded" : "chunk unloaded"));
            assertTrue(readiness.contains("safety is unverified"));
            assertEquals(0, world.provisionAttempts);
        }
    }

    @Test public void capitalCommandUsesOnlyLoadedDimensionsWithoutInitializationOrChunkProvision() throws Exception {
        KOMEWorldData data = data();
        int dimension = KOMEFactionCapitalService.getCapital(data, "gondor").getDeploymentDimensionId();
        Field worlds = DimensionManager.class.getDeclaredField("worlds");
        worlds.setAccessible(true);
        Object previousWorlds = worlds.get(null);
        Field singleton = null;
        for (Field field : MinecraftServer.class.getDeclaredFields())
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == MinecraftServer.class) singleton = field;
        assertNotNull(singleton); singleton.setAccessible(true);
        Object previousServer = singleton.get(null);
        try {
            // Keep the real worldServerForDimension implementation: on the old command path it
            // calls initDimension for an absent world, which first queries the overworld.
            singleton.set(null, kome.common.KOMEAccessFixture.allocate(DedicatedServer.class));
            for (boolean dimensionLoaded : new boolean[] {false, true}) {
                InspectionWorld world = kome.common.KOMEAccessFixture.allocate(InspectionWorld.class);
                Field provider = World.class.getDeclaredField("provider"); provider.setAccessible(true);
                provider.set(world, kome.common.KOMEAccessFixture.allocate(WorldProviderSurface.class));
                world.provider.dimensionId = dimension;
                LookupWorlds registry = new LookupWorlds(dimension);
                if (dimensionLoaded) registry.put(dimension, world);
                worlds.set(null, registry);
                for (boolean chunkLoaded : new boolean[] {false, true}) {
                    world.loaded = chunkLoaded;
                    List<String> messages = new ArrayList<String>();
                    new KOMEAdminDiagnosticsCommands().process(sender(true, messages),
                        new String[] {"diagnostics", "capital", "gondor"}, data);
                    String expected = dimensionLoaded ? (chunkLoaded ? "chunk loaded" : "chunk unloaded")
                        : "Deployment world unavailable";
                    assertTrue(messages.toString(), messages.toString().contains(expected));
                    assertTrue(messages.toString().contains("safety is unverified"));
                    assertEquals(0, registry.initializationReads);
                    assertEquals(dimensionLoaded ? 1 : 0, registry.size());
                    assertEquals(0, world.provisionAttempts);
                }
            }
        } finally {
            worlds.set(null, previousWorlds);
            singleton.set(null, previousServer);
        }
    }

    private static final class LookupWorlds extends Hashtable<Integer, WorldServer> {
        final int dimension;
        int initializationReads;
        LookupWorlds(int dimension) { this.dimension = dimension; }
        @Override public synchronized WorldServer get(Object id) {
            for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                if (frame.getClassName().equals(DimensionManager.class.getName())
                        && frame.getMethodName().equals("initDimension")) {
                    initializationReads++;
                    throw new AssertionError("Inspection entered DimensionManager.initDimension");
                }
            }
            if (!Integer.valueOf(dimension).equals(id)) {
                throw new AssertionError("Inspection queried an unrelated dimension");
            }
            return super.get(id);
        }
    }

    private static final class InspectionWorld extends WorldServer {
        boolean loaded;
        int provisionAttempts;
        private InspectionWorld() { super((MinecraftServer) null, (ISaveHandler) null, "disposable", 0, (WorldSettings) null, (Profiler) null); }
        @Override protected IChunkProvider createChunkProvider() { throw new AssertionError("No provider creation during inspection"); }
        @Override protected int func_152379_p() { return 0; }
        @Override public net.minecraft.entity.Entity getEntityByID(int id) { return null; }
        @Override public IChunkProvider getChunkProvider() {
            return (IChunkProvider) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {IChunkProvider.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("chunkExists")) return loaded;
                    provisionAttempts++; throw new AssertionError("Inspection must not provision chunks: " + method.getName());
                });
        }
    }
}
