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
        for (String[] args : new String[][] {
                {"conflict", "inspect", "T100"}, {"conflict", "end", "T100", "CF1", "reason"},
                {"repair", "conflict", "T100", "preview"}, {"repair", "conflict", "T100", "apply"},
                {"repair", "stewardship", "gondor"}, {"repair", "war", "W1"},
                {"progression", "cooldown", "off"}, {"progression", "relationship", "force", "lord"},
                {"progression", "relationship", "clear"}, {"ruler", "repair", "gondor", "Player"}}) {
            denied(() -> root.processCommand(sender, args));
            assertTrue(root.addTabCompletionOptions(sender, args).isEmpty());
        }
    }

    @Test public void mergedUsageAndCompletionRetainDiagnosticsConflictRepairsAndProgression() throws Exception {
        KOMECommandKome root = new KOMECommandKome(); ICommandSender staff = sender(true, new ArrayList<String>());
        String usage = root.getCommandUsage(staff);
        for (String part : new String[] {"diagnostics", "preview domain subject|apply token", "conflict <inspect|end>",
                "repair conflict <tile> <preview|apply>", "audit <list|summary> [page]", "repair stewardship",
                "repair war", "progression cooldown", "progression relationship"}) assertTrue(part, usage.contains(part));
        completion(root, staff, new String[] {""}, "gui", "help", "tile", "waypoint", "character", "config",
            "conquest", "waypointdefaults", "adminmarkers", "capital", "ruler", "audit", "diagnostics", "conflict", "repair", "progression");
        completion(root, staff, new String[] {"repair", ""}, "preview", "apply", "conflict", "stewardship", "war");
        completion(root, staff, new String[] {"diagnostics", ""}, "population", "ruler", "capital", "diplomacy", "ownership", "waypoint");
        completion(root, staff, new String[] {"repair", "preview", ""}, "ownership", "diplomacy", "ruler", "waypoint");
        completion(root, staff, new String[] {"repair", "conflict", "T100", ""}, "preview", "apply");
        completion(root, staff, new String[] {"conflict", ""}, "inspect", "end");
        completion(root, staff, new String[] {"conflict", "inspect", ""}, "T");
        completion(root, staff, new String[] {"conflict", "end", ""}, "T");
        completion(root, staff, new String[] {"audit", ""}, "list", "summary");
        completion(root, staff, new String[] {"progression", ""}, "cooldown", "relationship");
        completion(root, staff, new String[] {"progression", "cooldown", ""}, "on", "off");
        completion(root, staff, new String[] {"progression", "relationship", ""}, "force", "clear");
        completion(root, staff, new String[] {"progression", "relationship", "force", ""}, "serf", "knight", "lord");
        assertTrue(root.addTabCompletionOptions(staff, new String[] {"repair", "stewardship", ""})
            .contains(lotr.common.fac.LOTRFaction.GONDOR.codeName()));
        kome.common.KOMEAccessFixture fixture = new kome.common.KOMEAccessFixture(); fixture.player.operator = true;
        KOMEWar war = new KOMEWar(); war.id = "W1"; fixture.data.wars.put(war.id, war);
        completion(root, fixture.player, new String[] {"repair", "war", ""}, "W1");
        ICommandSender ordinary = sender(false, new ArrayList<String>());
        completion(root, ordinary, new String[] {""}, "gui", "help", "tile", "waypoint");
        assertFalse(root.getCommandUsage(ordinary).contains("diagnostics"));
    }

    @Test public void mergedRootDispatchKeepsGuardedRepairsConflictCommandsAndPagedAudit() throws Exception {
        kome.common.KOMEAccessFixture fixture = new kome.common.KOMEAccessFixture(); fixture.player.operator = true;
        KOMEWorldData data = fixture.data; data.initializeIntegratedWorld();
        KOMEConquestTile tile = data.conquestTiles.get("T100"); tile.setCurrentRulingFaction("gondor"); tile.ownerFaction = "";
        KOMECommandKome root = new KOMECommandKome();
        assertFalse(runRoot(root, fixture, "diagnostics", "ownership", "T100").isEmpty());
        assertEquals("", tile.ownerFaction);
        String preview = runRoot(root, fixture, "repair", "preview", "ownership", "T100").get(0);
        String token = preview.split("/kome repair apply ")[1].split(" ")[0];
        assertEquals("", tile.ownerFaction);
        KOMEConflictRecord conflict = data.getConflictService().start("T100", KOMEConflictRecord.State.ORDINARY,
            KOMEConflictContracts.ExpectedConflict.absent(), java.util.Collections.<KOMEConflictContracts.GarrisonSeed>emptyList(),
            new KOMEConflictContracts.Context(10L, "test", "integration fixture")).record;
        assertNotNull(conflict);
        assertTrue(runRoot(root, fixture, "conflict", "inspect", "T100").get(0).contains(conflict.getConflictId()));
        assertTrue(runRoot(root, fixture, "repair", "conflict", "T100", "preview").get(0).startsWith("Conflict repair preview"));
        assertTrue(runRoot(root, fixture, "repair", "conflict", "T100", "apply").get(0).contains("0 deterministic"));
        assertEquals("", tile.ownerFaction);
        assertTrue(runRoot(root, fixture, "repair", "apply", token).get(0).startsWith("Applied:"));
        assertEquals("gondor", tile.ownerFaction);
        assertTrue(runRoot(root, fixture, "repair", "apply", token).get(0).contains("consumed"));
        denied(() -> root.processCommand(fixture.player, new String[] {"conflict", "end", "T100", "CF999", "stale"}));
        assertEquals(KOMEConflictRecord.State.ORDINARY, data.getConflictService().get("T100").getState());
        assertTrue(runRoot(root, fixture, "conflict", "end", "T100", conflict.getConflictId(), "integration", "end")
            .get(0).startsWith("Ended conflict"));
        assertEquals(KOMEConflictRecord.State.ENDED, data.getConflictService().get("T100").getState());
        assertEquals("gondor", tile.projectRulingFaction());
        assertEquals(1L, data.centralAudit.stream().filter(e -> "CONFLICT".equals(e.domain) && "FORCED_END".equals(e.action)).count());
        for (int i = 0; i < 40; i++) KOMEAuditService.record(data, i, "TEST", "EVENT", "test", "S" + i, "fixture", "");
        List<String> page = runRoot(root, fixture, "audit", "list", "2");
        assertEquals(19, page.size()); assertTrue(page.get(0).startsWith("Audit page 2/"));
        for (String line : page) assertTrue(line.length() <= KOMEAdminDiagnostics.MAX_LINE_LENGTH);
        assertTrue(runRoot(root, fixture, "audit", "summary", "1").get(0).startsWith("Audit page 1/"));
        try {
            runRoot(root, fixture, "progression", "cooldown", "off");
            assertTrue(KOMESerfKnightCadenceOverride.isEnabled(fixture.player.id));
            runRoot(root, fixture, "progression", "cooldown", "on");
            assertFalse(KOMESerfKnightCadenceOverride.isEnabled(fixture.player.id));
        } finally { KOMESerfKnightCadenceOverride.clear(fixture.player.id); }
    }

    @Test public void legacyRulerNameRepairStillPreviewsUntilExplicitTokenApply() throws Exception {
        kome.common.KOMEAccessFixture fixture = new kome.common.KOMEAccessFixture(); fixture.player.operator = true;
        fixture.data.initializeIntegratedWorld();
        KOMERulerService.assignRuler(fixture.data, "gondor", fixture.player.id, "OldName");
        Field singleton = null;
        for (Field field : MinecraftServer.class.getDeclaredFields())
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == MinecraftServer.class) singleton = field;
        assertNotNull(singleton); singleton.setAccessible(true); Object previousServer = singleton.get(null);
        try {
            DedicatedServer server = kome.common.KOMEAccessFixture.allocate(DedicatedServer.class);
            net.minecraft.server.dedicated.DedicatedPlayerList players =
                kome.common.KOMEAccessFixture.allocate(net.minecraft.server.dedicated.DedicatedPlayerList.class);
            Field playerList = net.minecraft.server.management.ServerConfigurationManager.class.getDeclaredField("playerEntityList");
            playerList.setAccessible(true); playerList.set(players, fixture.world.playerEntities);
            server.func_152361_a(players); singleton.set(null, server);
            KOMECommandKome root = new KOMECommandKome();
            String preview = runRoot(root, fixture, "ruler", "repair", "gondor", fixture.player.getCommandSenderName()).get(0);
            assertEquals("OldName", KOMERulerService.getRulerName(fixture.data, "gondor"));
            assertEquals(fixture.player.id, KOMERulerService.getRuler(fixture.data, "gondor"));
            assertEquals(1L, fixture.data.centralAudit.stream().filter(e -> "REPAIR_PREVIEW".equals(e.action)).count());
            assertEquals(0L, fixture.data.centralAudit.stream().filter(e -> "REPAIR_APPLY".equals(e.action)).count());
            String token = preview.split("/kome repair apply ")[1].split(" ")[0];
            assertTrue(runRoot(root, fixture, "repair", "apply", token).get(0).startsWith("Applied:"));
            assertEquals(fixture.player.getCommandSenderName(), KOMERulerService.getRulerName(fixture.data, "gondor"));
            assertEquals(fixture.player.id, KOMERulerService.getRuler(fixture.data, "gondor"));
        } finally { singleton.set(null, previousServer); }
    }

    private static List<String> runRoot(KOMECommandKome root, kome.common.KOMEAccessFixture fixture, String... args) {
        fixture.player.messages.clear(); root.processCommand(fixture.player, args);
        return new ArrayList<String>(fixture.player.messages);
    }
    private static void completion(KOMECommandKome root, ICommandSender sender, String[] args, String... expected) {
        List actual = root.addTabCompletionOptions(sender, args);
        assertEquals(java.util.Arrays.toString(args), new java.util.HashSet<String>(java.util.Arrays.asList(expected)),
            new java.util.HashSet(actual));
        assertEquals(expected.length, actual.size());
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
