package kome.common.command;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import kome.common.KOMEAccessFixture;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketPopulationGui;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.IChatComponent;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEPublicCommandTest {
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

    @Test public void deniedAdministrativeCommandsDoNotEvenRequestWorldState() {
        ICommandSender nonOperator = console(new ArrayList<String>(), false);
        deny(new KOMECommandKome(), nonOperator, "config");
        deny(new KOMECommandKome(), nonOperator, "audit", "list");
        deny(new KOMECommandKome(), nonOperator, "repair", "war", "W1");
        deny(new KOMECommandKome(), nonOperator, "ruler", "assign", "gondor", "Someone");
        deny(new KOMECommandKome(), nonOperator, "conquest", "reset");
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

    private static void assertContains(ICommand command, ICommandSender sender, String word, boolean expected) {
        assertEquals(command.getCommandName() + " " + word, expected, command.addTabCompletionOptions(sender, new String[] {""}).contains(word));
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
