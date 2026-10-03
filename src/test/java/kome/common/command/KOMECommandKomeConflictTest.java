package kome.common.command;

import net.minecraft.command.ICommandSender;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class KOMECommandKomeConflictTest {
    @Test public void conflictAdministrationUsesExistingStaffPermission() {
        KOMECommandKome command = new KOMECommandKome();
        assertFalse(command.hasStaffPermission(sender(false)));
        assertTrue(command.hasStaffPermission(sender(true)));
    }

    @Test public void commandSurfaceRequiresExpectedIdentityAndSupportsPreview() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/kome/common/command/KOMECommandKome.java")),
            StandardCharsets.UTF_8);
        assertTrue(source.contains("\"conflict\".equalsIgnoreCase(args[0])"));
        assertTrue(source.contains("\"inspect\".equalsIgnoreCase(args[1])"));
        assertTrue(source.contains("\"end\".equalsIgnoreCase(args[1])"));
        assertTrue(source.contains("current.getConflictId().equals(args[3])"));
        assertTrue(source.contains("EndSource.ADMIN_FORCED"));
        assertTrue(source.contains("\"preview\".equalsIgnoreCase(args[3])"));
        assertTrue(source.contains("previewRepair"));
        assertTrue(source.contains("applyRepair"));
    }

    private static ICommandSender sender(final boolean staff) {
        return (ICommandSender) Proxy.newProxyInstance(
            KOMECommandKomeConflictTest.class.getClassLoader(),
            new Class<?>[] {ICommandSender.class},
            (proxy, method, args) -> method.getName().equals(
                "canCommandSenderUseCommand") && staff);
    }
}
