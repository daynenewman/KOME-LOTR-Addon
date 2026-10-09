package kome.common.command;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import kome.common.KOMEAccessFixture;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketPopulationGui;
import kome.common.network.KOMEPacketJoinBattleViewRequest;
import kome.common.network.KOMEPacketJoinBattleViewResponse;
import lotr.common.fac.LOTRFaction;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.IChatComponent;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEPublicCommandTest {
    @org.junit.Rule public final kome.common.data.KOMETileTestResources geometry = new kome.common.data.KOMETileTestResources();
    private static ICommand[] commands() {
        return new ICommand[] {new KOMECommandKome(), new KOMECommandPopulation(), new KOMECommandConquest(),
            new KOMECommandBuild(), new KOMECommandTroops(), new KOMECommandProgression(),
            new KOMECommandAlliance(), new KOMECommandWar(), new KOMECommandSeason()};
    }

    @Test public void dispatcherAllowsOrdinaryPlayerEvenWhenVanillaSenderRejectsLevelZero() throws Exception {
        KOMEAccessFixture f = new KOMEAccessFixture();
        for (ICommand command : commands()) {
            assertFalse(f.player.canCommandSenderUseCommand(0, command.getCommandName()));
            assertTrue(command.getCommandName(), command.canCommandSenderUseCommand(f.player));
            assertEquals(0, ((KOMEPublicCommand) command).getRequiredPermissionLevel());
            assertTrue(command.canCommandSenderUseCommand(console(new ArrayList<String>(), true)));
        }
    }

    @Test public void ordinaryRootOpensExistingPopulationScreenAndConsoleGetsHelp() throws Exception {
        KOMEAccessFixture f = new KOMEAccessFixture();
        cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper previous = KOMEPacketHandler.network;
        try {
            KOMEPacketHandler.network = f.network;
            new KOMECommandKome().processCommand(f.player, new String[0]);
            assertEquals(1, f.network.messages.size());
            assertTrue(f.network.messages.get(0) instanceof KOMEPacketPopulationGui);
            assertFalse(f.data.isDirty());
            List<String> messages = new ArrayList<String>();
            new KOMECommandKome().processCommand(console(messages, true), new String[0]);
            assertTrue(messages.toString().contains("/kome tile"));
        } finally { KOMEPacketHandler.network = previous; }
    }

    @Test public void ordinaryPlayerCanOpenReadOnlyJoinBattleViewButConsoleCannot() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();
        cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper previous=KOMEPacketHandler.network;
        try{
            KOMEPacketHandler.network=f.network;f.data.setDirty(false);
            new KOMECommandKome().processCommand(f.player,new String[]{"joinbattle","T100"});
            assertEquals(1,f.network.messages.size());
            assertTrue(f.network.messages.get(0) instanceof kome.common.network.KOMEPacketJoinBattleViewResponse);
            assertEquals("Blocked projection must not carry authority","",
                ((KOMEPacketJoinBattleViewResponse)f.network.messages.get(0)).actionToken);
            assertFalse(f.data.isDirty());assertTrue(f.data.getConflictService().records().isEmpty());
            try { new KOMECommandKome().processCommand(console(new ArrayList<String>(),true),new String[]{"joinbattle","T100"}); fail("console opened Join Battle"); }
            catch (net.minecraft.command.CommandException expected) { assertNotNull(expected.getMessage()); }
            assertContains(new KOMECommandKome(),console(new ArrayList<String>(),false),"joinbattle",true);
        }finally{KOMEPacketHandler.network=previous;}
    }

    @Test public void commandAndRefreshAllowedViewsAlwaysCarryFreshValidTokens() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();makeJoinBattleEligible(f);
        cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper previous=KOMEPacketHandler.network;
        try{
            KOMEPacketHandler.network=f.network;
            new KOMECommandKome().processCommand(f.player,new String[]{"joinbattle","T100"});
            KOMEPacketJoinBattleViewResponse command=(KOMEPacketJoinBattleViewResponse)f.network.messages.get(0);
            assertTrue(command.isAllowed());assertTrue(command.actionToken.matches("[0-9a-f]{32}"));

            KOMEPacketJoinBattleViewResponse refresh=(KOMEPacketJoinBattleViewResponse)
                new KOMEPacketJoinBattleViewRequest.Handler().onMessage(
                    new KOMEPacketJoinBattleViewRequest("T100"),f.context);
            assertTrue(refresh.isAllowed());assertTrue(refresh.actionToken.matches("[0-9a-f]{32}"));
            assertNotEquals(command.actionToken,refresh.actionToken);
        }finally{KOMEPacketHandler.network=previous;}
    }

    @Test public void deniedAdministrativeCommandsDoNotEvenRequestWorldState() {
        ICommandSender nonOperator = console(new ArrayList<String>(), false);
        for(String action:new String[]{"pending","inspect","adjust","approve","reject","rename","move","remove","associate","migration"})
            deny(new KOMECommandKome(),nonOperator,"waypoint",action);
        deny(new KOMECommandKome(), nonOperator, "config");
        deny(new KOMECommandKome(), nonOperator, "audit", "list");
        deny(new KOMECommandKome(), nonOperator, "progression", "relationship", "force", "serf");
        deny(new KOMECommandKome(), nonOperator, "progression", "relationship", "clear");
        deny(new KOMECommandKome(), nonOperator, "repair", "war", "W1");
        deny(new KOMECommandKome(), nonOperator, "ruler", "assign", "gondor", "Someone");
        deny(new KOMECommandKome(), nonOperator, "conquest", "reset");
        deny(new KOMECommandConquest(), nonOperator, "resolve", "100", "189696", "-86016");
        for (String action : new String[] {"claim", "clear", "clearAll", "reset", "purgeLegacy", "waypoint"})
            deny(new KOMECommandConquest(), nonOperator, action, "T001", "gondor");
        for (String action : new String[] {"reassign", "remove", "sethours", "adjust"})
            deny(new KOMECommandBuild(), nonOperator, action, "B1", "normal", "100");
        for (String action : new String[] {"grant", "grantall", "revoke", "reset", "enable", "disable", "reroll"})
            deny(new KOMECommandProgression(), nonOperator, action, "Someone", "wanderer");
        for (String action : new String[] {"create", "side", "end", "finalize", "cancel"})
            deny(new KOMECommandWar(), nonOperator, action);
        for (String action : new String[] {"prewar", "reset", "complete-reset", "repair"})
            deny(new KOMECommandSeason(), nonOperator, action);
        deny(new KOMECommandTroops(), nonOperator, "arrive");
        deny(new KOMECommandTroops(), nonOperator, "arrival", "backfill");
        deny(new KOMECommandTroops(), nonOperator, "movement", "ticknow");
        deny(new KOMECommandTroops(), nonOperator, "route", "addedge", "T001", "T002");
    }

    @Test public void authorizedOperatorKeepsAdministrationAndPublicEntry() throws Exception {
        KOMEAccessFixture f = new KOMEAccessFixture(); f.player.operator = true;
        new KOMECommandProgression().processCommand(f.player, new String[] {"disable"});
        assertFalse(f.data.isProgressionEnabled());
        new KOMECommandProgression().processCommand(f.player, new String[] {"enable"});
        assertTrue(f.data.isProgressionEnabled());
        new KOMECommandKome().processCommand(f.player, new String[] {"config"});
        new KOMECommandKome().processCommand(f.player, new String[] {"audit", "list"});
        assertTrue(new KOMECommandKome().canCommandSenderUseCommand(f.player));
        assertTrue(new KOMECommandKome().hasStaffPermission(f.player));
        f.player.operator = false;
        assertTrue(new KOMECommandKome().canCommandSenderUseCommand(f.player));
        assertFalse(new KOMECommandKome().hasStaffPermission(f.player));
    }

    @Test public void publicHelpAndCompletionsDoNotAdvertiseAdministrativeActions() {
        ICommandSender player = console(new ArrayList<String>(), false);
        ICommandSender op = console(new ArrayList<String>(), true);
        assertContains(new KOMECommandKome(), player, "gui", true);
        assertContains(new KOMECommandKome(), player, "audit", false);
        assertContains(new KOMECommandKome(), op, "audit", true);
        assertContains(new KOMECommandConquest(), player, "resolve", false);
        assertContains(new KOMECommandConquest(), op, "resolve", true);
        assertFalse(new KOMECommandConquest().getCommandUsage(player).contains("resolve"));
        assertTrue(new KOMECommandConquest().getCommandUsage(op).contains("resolve"));
        assertTrue(new KOMECommandConquest().addTabCompletionOptions(player, new String[] {"resolve", ""}).isEmpty());
        assertContains(new KOMECommandConquest(), player, "claim", false);
        assertContains(new KOMECommandConquest(), op, "claim", true);
        assertContains(new KOMECommandProgression(), player, "grant", false);
        assertContains(new KOMECommandProgression(), op, "grant", true);
        assertContains(new KOMECommandBuild(), player, "grant", true); // existing ruler construction grant
        assertContains(new KOMECommandBuild(), player, "sethours", false);
        assertContains(new KOMECommandTroops(), player, "arrive", false);
        assertContains(new KOMECommandTroops(), op, "arrive", true);
        assertContains(new KOMECommandSeason(), player, "reset", false);
        assertContains(new KOMECommandWar(), player, "create", false);
        assertFalse(new KOMECommandKome().getCommandUsage(player).contains("repair"));
        assertFalse(new KOMECommandProgression().getCommandUsage(player).contains("grantall"));
        assertTrue(new KOMECommandTroops().addTabCompletionOptions(player, new String[] {"movement", ""}).contains("resume"));
        assertFalse(new KOMECommandTroops().addTabCompletionOptions(player, new String[] {"movement", ""}).contains("advance"));
        assertTrue(new KOMECommandKome().addTabCompletionOptions(player, new String[] {"ruler", ""}).isEmpty());
    }

    @Test public void staffCoordinateInspectionResolvesWithoutAnyWorldAccess() {
        List<String> messages = new ArrayList<String>();
        ICommandSender staff = console(messages, true); // getEntityWorld throws even for authorized inspection.
        KOMECommandConquest command = new KOMECommandConquest();
        String dimension = Integer.toString(kome.common.data.KOMETileTestResources.dimension());
        command.processCommand(staff, new String[] {"resolve", dimension, "189568", "-86016"});
        assertTrue(messages.get(0), messages.get(0).contains("IN_BOUNDS_GAP"));
        command.processCommand(staff, new String[] {"resolve", dimension, "189696", "-86016"});
        assertTrue(messages.get(1), messages.get(1).contains("RESOLVED"));
        assertTrue(messages.get(1), messages.get(1).contains("tile=T001"));
        command.processCommand(staff, new String[] {"resolve", dimension, "-103681", "-86016"});
        assertTrue(messages.get(2), messages.get(2).contains("OUTSIDE_MASK"));
        deny(command, staff, "resolve", dimension, "2147483648", "0");
        deny(command, staff, "resolve", dimension);
        assertEquals(3, messages.size());
    }

    private static void assertContains(ICommand command, ICommandSender sender, String word, boolean expected) {
        assertEquals(command.getCommandName() + " " + word, expected, command.addTabCompletionOptions(sender, new String[] {""}).contains(word));
    }

    private static void makeJoinBattleEligible(KOMEAccessFixture f) throws Exception {
        f.pledge(LOTRFaction.GONDOR);long now=10L;
        kome.common.data.KOMEConflictService.Result started=f.data.getConflictService().start(
            "T100",kome.common.data.KOMEConflictRecord.State.ORDINARY,
            kome.common.data.KOMEConflictContracts.ExpectedConflict.absent(),
            java.util.Collections.<kome.common.data.KOMEConflictContracts.GarrisonSeed>emptyList(),
            new kome.common.data.KOMEConflictContracts.Context(now++,"test","Join Battle command token"));
        assertTrue(started.isSuccess());kome.common.data.KOMEConflictRecord record=started.record;
        kome.common.data.KOMEConflictService.Result participated=f.data.getConflictService().beginFactionParticipation(
            "T100",kome.common.data.KOMEConflictContracts.ExpectedConflict.at(record.getConflictId(),record.getRevision()),
            "gondor",new kome.common.data.KOMEConflictContracts.Context(now++,"test","Join Battle command token"));
        assertTrue(participated.isSuccess());record=participated.record;
        kome.common.data.KOMEArmyCompany company=new kome.common.data.KOMEArmyCompany();
        company.id="C1";company.owner=java.util.UUID.randomUUID();company.ownerName="Owner";
        company.faction="gondor";company.name="First Company";company.currentTile="T100";
        f.data.lastKnownPlayerFactions.put(company.owner,"gondor");
        kome.common.data.KOMEHiredUnitRecord unit=new kome.common.data.KOMEHiredUnitRecord();
        unit.entity=java.util.UUID.randomUUID();unit.owner=company.owner;unit.companyId="C1";
        unit.companyName=company.name;unit.currentTile="T100";unit.sourceTileId="T001";
        unit.unitFaction="gondor";unit.populationOwningFaction="gondor";
        unit.type=kome.common.data.KOMEPopulationType.OFFENSIVE;
        java.lang.reflect.Field unitClass=kome.common.data.KOMEHiredUnitRecord.class.getDeclaredField("unitClass");
        unitClass.setAccessible(true);unitClass.set(unit,kome.common.data.KOMEHiredUnitClass.CAMPAIGN);
        unit.cost=unit.baseCost=unit.populationSpent=20;company.units.add(unit.entity);
        company.totalPopulation=company.groundPopulation=20;
        f.data.armyCompanies.put(company.id,company);f.data.hiredUnits.put(unit.entity,unit);
        kome.common.data.KOMEConflictService.Result committed=f.data.getConflictService().commit(
            "T100",kome.common.data.KOMEConflictContracts.ExpectedConflict.at(record.getConflictId(),record.getRevision()),
            new kome.common.data.KOMEConflictContracts.CommitmentInput("C1",
                kome.common.data.KOMEHiredUnitClass.CAMPAIGN,
                kome.common.data.KOMEConflictRecord.EntryOrigin.LEGAL_ARRIVAL,"M-C1"),
            new kome.common.data.KOMEConflictContracts.Context(now,"test","Join Battle command token"));
        assertTrue(committed.isSuccess());
    }

    private static void deny(ICommand command, ICommandSender sender, String... args) {
        try { command.processCommand(sender, args); fail("Denied command ran: " + command.getCommandName()); }
        catch (WrongUsageException expected) { assertNotNull(expected.getMessage()); }
    }

    private static ICommandSender console(List<String> messages, boolean operator) {
        return (ICommandSender) Proxy.newProxyInstance(KOMEPublicCommandTest.class.getClassLoader(),
            new Class<?>[] {ICommandSender.class}, (proxy, method, args) -> {
                if ("canCommandSenderUseCommand".equals(method.getName())) return operator;
                if ("getEntityWorld".equals(method.getName())) throw new AssertionError("Denied/admin-help request accessed world");
                if ("addChatMessage".equals(method.getName())) { messages.add(((IChatComponent) args[0]).getUnformattedText()); return null; }
                if ("getCommandSenderName".equals(method.getName())) return "console";
                return null;
            });
    }
}
