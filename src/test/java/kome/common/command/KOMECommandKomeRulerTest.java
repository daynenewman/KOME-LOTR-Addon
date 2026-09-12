package kome.common.command;

import net.minecraft.command.ICommandSender;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KOMECommandKomeRulerTest {
    @Test
    public void rulerOperationsRequireStaffPermission() {
        KOMECommandKome command = new KOMECommandKome();
        assertFalse(command.hasStaffPermission(sender(false)));
        assertTrue(command.hasStaffPermission(sender(true)));
    }

    @Test
    public void rulerCommandUsesCanonicalOperationsAndSurface() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/command/KOMECommandKome.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("\"ruler\""));
        assertTrue(source.contains("\"get\""));
        assertTrue(source.contains("\"assign\""));
        assertTrue(source.contains("\"remove\""));
        assertTrue(source.contains("\"repair\""));
        assertTrue(source.contains("KOMERulerService.assignRuler"));
        assertTrue(source.contains("KOMERulerService.removeRuler"));
        assertTrue(source.contains("KOMERulerService.repair"));
        assertTrue(source.contains("getPlayer(sender, args[3])"));
        assertTrue(source.contains("canCommandSenderUseCommand(2, getCommandName())"));
    }

    private static ICommandSender sender(final boolean staff) {
        return (ICommandSender) Proxy.newProxyInstance(
            KOMECommandKomeRulerTest.class.getClassLoader(),
            new Class<?>[] {ICommandSender.class},
            (proxy, method, args) -> method.getName().equals("canCommandSenderUseCommand") && staff);
    }
}
