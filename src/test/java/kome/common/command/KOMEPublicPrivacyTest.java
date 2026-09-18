package kome.common.command;

import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import kome.common.KOMEAccessFixture;
import kome.common.data.*;
import kome.common.network.*;
import lotr.common.fac.LOTRFaction;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.IChatComponent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual command/handler behavior with inert players; no live connection or rendered GUI. */
public class KOMEPublicPrivacyTest {
    private KOMEAccessFixture f;
    private KOMEAccessFixture other;
    private SimpleNetworkWrapper previousNetwork;
    private Field serverField;
    private Object previousServer;
    private KOMEConquestTile tile;
    private KOMEHiredUnitRecord ownUnit;
    private KOMEHiredUnitRecord otherUnit;

    @Before public void setup() throws Exception {
        f = new KOMEAccessFixture();
        other = new KOMEAccessFixture();
        other.player.name = "OtherTester";
        other.player.worldObj = f.world;
        f.world.playerEntities.add(other.player);
        previousNetwork = KOMEPacketHandler.network;
        KOMEPacketHandler.network = f.network;
        for (Field field : MinecraftServer.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == MinecraftServer.class) serverField = field;
        }
        assertNotNull(serverField);
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        // Seed inert LOTR player data before exposing the lookup-only server fixture.
        f.pledge(LOTRFaction.GONDOR);
        other.pledge(LOTRFaction.GONDOR);
        DedicatedServer server = KOMEAccessFixture.allocate(DedicatedServer.class);
        DedicatedPlayerList players = KOMEAccessFixture.allocate(DedicatedPlayerList.class);
        Field playerList = ServerConfigurationManager.class.getDeclaredField("playerEntityList");
        playerList.setAccessible(true);
        playerList.set(players, f.world.playerEntities);
        server.func_152361_a(players);
        serverField.set(null, server);
        List<String> ids = new ArrayList<String>(KOMEConquestTileDefaults.getKnownTileIds());
        Collections.sort(ids);
        tile = new KOMEConquestTile(ids.get(0));
        tile.currentRulingFaction = " GONDOR ";
        tile.ownerFaction = "rohan"; // inspection must not repair divergent raw fields
        f.data.conquestTiles.put(tile.id, tile);
        ownUnit = unit(f.player.id, "Own warrior");
        otherUnit = unit(other.player.id, "Private warrior");
        f.data.setDirty(false);
    }

    @After public void cleanup() throws Exception {
        KOMEPacketHandler.network = previousNetwork;
        if (serverField != null) serverField.set(null, previousServer);
    }

    @Test public void selfUnitDetailsWorkAndOtherTargetsRejectBeforeLookupOrPublication() {
        KOMECommandPopulation command = new KOMECommandPopulation();
        command.processCommand(f.player, new String[] {"units"});
        KOMEPacketPopulationUnitsGui packet = (KOMEPacketPopulationUnitsGui) f.network.messages.get(0);
        assertEquals(1, packet.units.size());
        assertEquals(ownUnit.entity.toString(), ((KOMEUnitGuiEntry) packet.units.get(0)).entityId);
        f.network.messages.clear();
        for (String action : new String[] {"units", "get", "gui"}) {
            for (String target : new String[] {"OtherTester", "UnknownPlayer", "@a"}) {
                assertDenied(new KOMECommandPopulation(), f.player, action, target);
            }
        }
        assertTrue(f.network.messages.isEmpty());
        assertReadState();
        command.processCommand(f.player, new String[] {"units", f.player.getCommandSenderName(), tile.id});
        assertEquals(1, f.network.messages.size()); // existing GUI explicit-self route remains valid
    }

    @Test public void operatorsAndAuthorizedConsoleRetainTargetInspection() {
        f.player.operator = true;
        new KOMECommandPopulation().processCommand(f.player, new String[] {"units", "OtherTester"});
        KOMEPacketPopulationUnitsGui packet = (KOMEPacketPopulationUnitsGui) f.network.messages.get(0);
        assertEquals("OtherTester", packet.playerName);
        assertEquals(otherUnit.entity.toString(), ((KOMEUnitGuiEntry) packet.units.get(0)).entityId);
        List<String> messages = new ArrayList<String>();
        new KOMECommandPopulation().processCommand(nonPlayer(messages, true, true), new String[] {"units", "OtherTester"});
        assertTrue(messages.toString().contains("Private warrior"));
        new KOMECommandProgression().processCommand(nonPlayer(messages, true, true), new String[] {"get", "OtherTester"});
        assertTrue(messages.toString().contains("OtherTester progression"));
        assertReadState();
    }

    @Test public void progressionReadsProjectBaselineWithoutPublishingOrSynchronizing() {
        KOMECommandProgression command = new KOMECommandProgression();
        command.processCommand(f.player, new String[] {"get"});
        command.processCommand(f.player, new String[] {"list", "baseline"});
        assertTrue(f.player.messages.toString().contains("Pledged lord: None"));
        assertTrue(f.player.messages.toString().contains("[x]"));
        assertTrue(f.data.progressions.isEmpty());
        assertTrue(f.network.messages.isEmpty());
        assertReadState();
        assertDenied(command, f.player, "get", "OtherTester");
        assertDenied(command, f.player, "list", "OtherTester", "baseline");
        KOMEPlayerProgression privateProgression = f.data.getProgression(other.player.id);
        privateProgression.setPledgedLord("private-id", "PrivateLord", "gondor");
        f.player.messages.clear();
        f.player.operator = true;
        command.processCommand(f.player, new String[] {"get", "OtherTester"});
        command.processCommand(f.player, new String[] {"list", "OtherTester", "baseline"});
        assertTrue(f.player.messages.toString().contains("PrivateLord"));
        assertEquals(1, f.data.progressions.size());
        assertFalse(f.data.isDirty());
        assertTrue(f.network.messages.isEmpty());
    }

    @Test public void legitimateSelfMutationStillCreatesAndPersistsProgression() {
        KOMEProgressionAchievement chosen = null;
        for (KOMEProgressionAchievement achievement : KOMEProgressionAchievement.ALL) {
            if (!achievement.defaultUnlocked && !"baseline".equals(achievement.group)
                    && KOMEProgressionTaskGenerator.canRoll(achievement.id)) {
                chosen = achievement; break;
            }
        }
        assertNotNull(chosen);
        new KOMECommandProgression().processCommand(f.player, new String[] {"roll", chosen.id});
        KOMEPlayerProgression persisted = f.data.progressions.get(f.player.id);
        assertNotNull(persisted);
        assertNotNull(persisted.getAssignment(chosen.id));
        assertTrue(f.data.isDirty());
        KOMEPlayerProgression reload = new KOMEPlayerProgression();
        reload.readFromNBT(persisted.writeToNBT());
        assertEquals(persisted.getAssignment(chosen.id), reload.getAssignment(chosen.id));
        assertFalse(f.network.messages.isEmpty());
    }

    @Test public void progressionGuiAndRejectedLordActionsDoNotCreateRecords() {
        new KOMEPacketProgressionRequest.Handler().onMessage(new KOMEPacketProgressionRequest(), f.context);
        KOMEPacketProgressionData response = (KOMEPacketProgressionData) f.network.messages.get(0);
        assertEquals(f.player.getCommandSenderName(), response.playerName);
        assertTrue(response.completed.contains("baseline.wood"));
        f.network.messages.clear();
        assertDenied(new KOMECommandProgression(), f.player, "findlord");
        assertDenied(new KOMECommandProgression(), f.player, "offerings");
        assertTrue(f.data.progressions.isEmpty());
        assertTrue(f.network.messages.isEmpty());
        assertReadState();
    }

    @Test public void publicCompletionOffersNoPlayerTargetsButOperatorsKeepThem() {
        for (String action : new String[] {"get", "gui", "units"}) {
            assertTrue(new KOMECommandPopulation().addTabCompletionOptions(f.player, new String[] {action, ""}).isEmpty());
        }
        KOMECommandProgression progression = new KOMECommandProgression();
        assertTrue(progression.addTabCompletionOptions(f.player, new String[] {"get", ""}).isEmpty());
        List groups = progression.addTabCompletionOptions(f.player, new String[] {"list", ""});
        assertTrue(groups.contains("baseline"));
        assertFalse(groups.contains("OtherTester"));
        assertTrue(progression.addTabCompletionOptions(f.player, new String[] {"list", "OtherTester", ""}).isEmpty());
        f.player.operator = true;
        assertTrue(progression.addTabCompletionOptions(f.player, new String[] {"get", ""}).contains("OtherTester"));
        assertTrue(new KOMECommandPopulation().addTabCompletionOptions(f.player, new String[] {"units", ""}).contains("OtherTester"));
    }

    @Test public void loadedLordHighlightDoesNotRewriteSavedLocation() {
        KOMEPlayerProgression progression = f.data.getProgression(f.player.id);
        progression.setPledgedLord(other.player.id.toString(), "Lord", "gondor");
        progression.setPledgedLordLocation(0, 1, 2, 3);
        NBTTagCompound before = progression.writeToNBT();
        other.player.posX = 30; other.player.posY = 40; other.player.posZ = 50;
        f.world.loadedEntityList.add(other.player); // locator only requires the recorded entity UUID
        new KOMECommandProgression().processCommand(f.player, new String[] {"findlord"});
        assertTrue(f.network.messages.get(0) instanceof KOMEPacketLordHighlight);
        assertEquals(before, progression.writeToNBT());
        assertReadState();
    }

    @Test public void publicTilePopulationRejectsUnknownRetiredAndAbsentWithoutRepair() {
        KOMECommandPopulation command = new KOMECommandPopulation();
        command.processCommand(f.player, new String[] {"tile", " " + tile.id.toLowerCase() + " "});
        command.processCommand(f.player, new String[] {"faction", "gondor"});
        command.processCommand(f.player, new String[] {"rate", "gondor"});
        assertTrue(f.player.messages.toString().contains(tile.id));
        String absent = null;
        for (String id : KOMEConquestTileDefaults.getKnownTileIds()) if (!id.equals(tile.id)) { absent = id; break; }
        f.data.conquestTiles.put("T045", new KOMEConquestTile("T045"));
        for (String id : new String[] {"", "../bad", "T999999", "T045", absent}) {
            assertDenied(command, f.player, "tile", id);
            assertDenied(new KOMECommandBuild(), f.player, "list", id.length() == 0 ? "../bad" : id);
            for (String action : new String[] {"tile", "list", "companies", "arrivals"})
                assertDenied(new KOMECommandTroops(), f.player, action, id);
            assertDenied(new KOMECommandTroops(), f.player, "arrival", "get", id);
            assertDenied(new KOMECommandTroops(), f.player, "waypoint", "get", id);
        }
        assertEquals(2, f.data.conquestTiles.size());
        assertFalse(f.data.conquestTiles.containsKey(absent));
        assertReadState();
    }

    @Test public void troopReadsCannotBypassUnitPrivacyAndDoNotRepairOwnership() {
        KOMECommandTroops troops = new KOMECommandTroops();
        assertDenied(troops, f.player, "unit", otherUnit.entity.toString());
        troops.processCommand(f.player, new String[] {"tile", tile.id});
        assertTrue(f.player.messages.toString().contains("Own warrior"));
        assertFalse(f.player.messages.toString().contains("Private warrior"));
        assertFalse(f.player.messages.toString().contains(otherUnit.entity.toString()));
        assertReadState();
    }

    @Test public void publicWarStatusAndServerRecordsRedactEveryDiagnosticSlot() {
        KOMEWar war = diagnosticWar();
        KOMEPlayerProgression progression = f.data.getProgression(other.player.id);
        progression.setPledgedLord("lord-id", "PrivateLord", "gondor");
        new KOMECommandWar().processCommand(f.player, new String[] {"status", war.id});
        new KOMECommandWar().processCommand(f.player, new String[] {"list", "all"});
        String publicText = f.player.messages.toString();
        assertTrue(publicText.contains("Public conflict"));
        assertTrue(publicText.toLowerCase(java.util.Locale.ROOT).contains("gondor"));
        assertTrue(publicText.contains("ACTIVE"));
        assertTrue(publicText.contains("tile captures"));
        assertRedacted(publicText, war);
        String records = KOMEServerRecordBuilder.build(f.world, false).toString();
        assertTrue(records.contains("Public conflict"));
        assertTrue(records.contains("Operator-only"));
        assertFalse(records.contains("PrivateLord"));
        assertRedacted(records, war);
        assertDenied(new KOMECommandWar(), f.player, "cancel", war.id, "forced");
        f.player.operator = true;
        f.player.messages.clear();
        new KOMECommandWar().processCommand(f.player, new String[] {"status", war.id});
        String diagnostics = f.player.messages.toString();
        assertTrue(diagnostics.contains("admin events: 1"));
        assertTrue(diagnostics.contains("Bond:"));
        assertTrue(diagnostics.contains("private-support"));
        assertTrue(diagnostics.contains(war.militarySupportEnrollments.get(0).authorizedKing.toString()));
        String operatorRecords = KOMEServerRecordBuilder.build(f.world, true).toString();
        assertTrue(operatorRecords.contains("private-repair"));
        assertTrue(operatorRecords.contains("PrivateLord"));
        assertFalse(f.data.isDirty());
        assertTrue(f.data.centralAudit.isEmpty());
    }

    @Test public void nonPlayerPrivacyAndPlayerOnlyActionsFailBeforeWorldAccess() {
        for (boolean operator : new boolean[] {false, true}) {
            ICommandSender consoleOrBlock = nonPlayer(new ArrayList<String>(), operator, false);
            assertDenied(new KOMECommandPopulation(), consoleOrBlock, "units");
            assertDenied(new KOMECommandProgression(), consoleOrBlock, "get");
            assertDenied(new KOMECommandProgression(), consoleOrBlock, "list", "baseline");
            assertDenied(new KOMECommandProgression(), consoleOrBlock, "pledge");
            assertDenied(new KOMECommandTroops(), consoleOrBlock, "companies");
            assertDenied(new KOMECommandKome(), consoleOrBlock, "tile", tile.id);
            assertDenied(new KOMECommandSeason(), consoleOrBlock, "finale");
            assertDenied(new KOMECommandBuild(), consoleOrBlock, "grant", tile.id, "rohan");
            assertDenied(new KOMECommandAlliance(), consoleOrBlock, "goods", "gondor", "rohan");
            if (!operator) {
                for (String action : new String[] {"units", "get", "gui"})
                    assertDenied(new KOMECommandPopulation(), consoleOrBlock, action, "OtherTester");
                assertDenied(new KOMECommandProgression(), consoleOrBlock, "get", "OtherTester");
                assertDenied(new KOMECommandProgression(), consoleOrBlock, "list", "OtherTester", "baseline");
                assertDenied(new KOMECommandAlliance(), consoleOrBlock, "list");
                assertDenied(new KOMECommandConquest(), consoleOrBlock, "trade", tile.id, "rohan");
                assertDenied(new KOMECommandConquest(), consoleOrBlock, "canceltrade", tile.id);
            }
        }
        assertReadState();
    }

    @Test public void consoleAggregateReadsAndAuthorizedAdministrationRemainSafe() {
        List<String> messages = new ArrayList<String>();
        ICommandSender console = nonPlayer(messages, true, true);
        new KOMECommandKome().processCommand(console, new String[0]);
        new KOMECommandPopulation().processCommand(console, new String[] {"faction", "gondor"});
        new KOMECommandPopulation().processCommand(console, new String[] {"tile", tile.id});
        new KOMECommandProgression().processCommand(console, new String[] {"status"});
        new KOMECommandConquest().processCommand(console, new String[] {"get", tile.id});
        new KOMECommandSeason().processCommand(console, new String[] {"status"});
        new KOMECommandWar().processCommand(console, new String[] {"list"});
        assertTrue(messages.toString().contains("/kome"));
        assertReadState();
        new KOMECommandProgression().processCommand(console, new String[] {"disable"});
        assertFalse(f.data.isProgressionEnabled());
        assertTrue(f.data.isDirty());
    }

    @Test public void realMinecraftCommandHandlerDispatchesPublicAndRejectsPrivateTargets() {
        CommandHandler dispatcher = new CommandHandler();
        dispatcher.registerCommand(new KOMECommandKome());
        dispatcher.registerCommand(new KOMECommandPopulation());
        dispatcher.registerCommand(new KOMECommandProgression());
        dispatcher.registerCommand(new KOMECommandWar());
        assertEquals(1, dispatcher.executeCommand(f.player, "/kome"));
        assertTrue(f.network.messages.get(0) instanceof KOMEPacketPopulationGui);
        f.network.messages.clear();
        assertEquals(1, dispatcher.executeCommand(f.player, "/progression get"));
        assertEquals(0, dispatcher.executeCommand(f.player, "/population units OtherTester"));
        assertEquals(0, dispatcher.executeCommand(f.player, "/progression get OtherTester"));
        assertEquals(0, dispatcher.executeCommand(f.player, "/war create gondor rohan"));
        assertTrue(f.network.messages.isEmpty());
        assertTrue(f.data.progressions.isEmpty());
        assertReadState();
    }

    @Test public void authorizedHistoryInspectionDoesNotReconcileOrDirty() {
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
        order.id = "M1"; order.owner = f.player.id; order.ownerFaction = "gondor";
        f.data.armyMovements.put(order.id, order); // deliberately absent history snapshot
        new KOMECommandTroops().processCommand(f.player, new String[] {"history"});
        new KOMEPacketMovementHistoryRequest.Handler().onMessage(
            new KOMEPacketMovementHistoryRequest("gondor", false), f.context);
        assertEquals(2, f.network.messages.size());
        assertTrue(f.data.movementHistory.isEmpty());
        assertReadState();
    }

    private KOMEHiredUnitRecord unit(UUID owner, String name) {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = UUID.randomUUID(); record.owner = owner; record.unitName = name;
        record.populationOwningFaction = "gondor"; record.cost = 25; record.populationSpent = 25;
        record.currentTile = tile.id; record.sourceTileId = tile.id;
        f.data.hiredUnits.put(record.entity, record);
        return record;
    }

    private KOMEWar diagnosticWar() {
        KOMEWar war = new KOMEWar();
        war.id = "W1"; war.displayName = "Public conflict";
        war.addFaction(1, "gondor"); war.addFaction(2, "rohan");
        war.endingReason = "private-ending\n\u00a7cinternal";
        war.addAdministrativeEvent("Staff", "REPAIR", "private-repair", 1L);
        KOMEWar.BondEscrow bond = new KOMEWar.BondEscrow();
        bond.faction = "gondor"; bond.amount = 1234567; war.bondEscrows.add(bond);
        KOMEWar.MilitarySupportEnrollment enrollment = new KOMEWar.MilitarySupportEnrollment();
        enrollment.authorizedKing = UUID.randomUUID(); enrollment.authorizedKingName = "King";
        enrollment.supportingFaction = "gondor"; enrollment.nativeFaction = "rohan";
        enrollment.reason = "private-support"; enrollment.state = "CONTRADICTION";
        war.militarySupportEnrollments.add(enrollment);
        f.data.wars.put(war.id, war);
        return war;
    }

    private void assertRedacted(String text, KOMEWar war) {
        for (String secret : new String[] {"admin events", "private-repair", "private-support", "private-ending",
                "1234567", "CONTRADICTION", "WARNING", "Last hostile pressure", "inactivity threshold",
                war.militarySupportEnrollments.get(0).authorizedKing.toString()}) assertFalse(secret, text.contains(secret));
    }

    private void assertReadState() {
        assertFalse(f.data.isDirty());
        assertTrue(f.data.centralAudit.isEmpty());
        assertEquals(" GONDOR ", tile.currentRulingFaction);
        assertEquals("rohan", tile.ownerFaction);
        assertSame(ownUnit, f.data.hiredUnits.get(ownUnit.entity));
        assertSame(otherUnit, f.data.hiredUnits.get(otherUnit.entity));
        assertTrue(f.data.factionPopulations.isEmpty());
    }

    private static void assertDenied(ICommand command, ICommandSender sender, String... args) {
        try { command.processCommand(sender, args); fail("Expected denial: " + command.getCommandName()); }
        catch (CommandException expected) { assertNotNull(expected.getMessage()); }
    }

    private ICommandSender nonPlayer(List<String> messages, boolean operator, boolean allowWorld) {
        return (ICommandSender) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ICommandSender.class},
            (proxy, method, args) -> {
                if ("canCommandSenderUseCommand".equals(method.getName())) return operator;
                if ("getEntityWorld".equals(method.getName())) {
                    if (!allowWorld) throw new AssertionError("Denied request accessed WorldData");
                    return f.world;
                }
                if ("getCommandSenderName".equals(method.getName())) return operator ? "console" : "command-block";
                if ("addChatMessage".equals(method.getName())) { messages.add(((IChatComponent) args[0]).getUnformattedText()); return null; }
                return null;
            });
    }
}
