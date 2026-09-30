package kome.common.data;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMEProgressionNpcInteractionServiceTest {
    @Test
    public void directWorldInteractionOwnsEveryActionFormerlyHiddenBehindMenus()
            throws Exception {
        String source=
            new String(
                Files.readAllBytes(
                    Paths.get(
                        "src/main/java/kome/common/data/KOMEProgressionNpcInteractionService.java")),
                StandardCharsets.UTF_8);

        assertTrue(source.contains(
            "validateCurrentMasterInteraction"));

        assertTrue(source.contains(
            "KOMESerfProvisioningService.deliver"));

        assertTrue(source.contains(
            "KOMESerfProfessionService.deliver"));

        assertTrue(source.contains(
            "KOMECourierService.reportToMaster"));

        assertTrue(source.contains(
            "KOMECourierService.hasDispatch"));

        assertTrue(source.contains(
            "KOMESerfdomMasterService.requestDuty"));

        assertTrue(source.contains(
            "KOMESerfKnightService.assignTrial"));

        assertTrue(source.contains(
            "KOMESerfKnightRecoveryService.deliver"));

        assertTrue(source.contains(
            "KOMESerfKnightRecoveryService.activate"));

        assertTrue(source.contains(
            "KOMESerfKnightDefenseService.activate"));

        assertTrue(source.contains(
            "KOMESerfKnightEscortService.activate"));

        assertTrue(source.contains(
            "KOMESerfdomMasterService.conferKnighthood"));

        assertFalse(source.contains(
            "sendMenu"));

        assertFalse(source.contains(
            "sendHub"));
    }

    @Test
    public void eventOnlyCancelsWhenKomeActuallyPerformsAProgressionAction()
            throws Exception {
        String source=
            new String(
                Files.readAllBytes(
                    Paths.get(
                        "src/main/java/kome/common/data/KOMEEvents.java")),
                StandardCharsets.UTF_8);

        String call=
            "KOMEProgressionNpcInteractionService.interact(player, data, npc)";

        assertTrue(source.contains(call));

        int interaction=source.indexOf(call);
        int cancel=source.indexOf(
            "event.setCanceled(true)",
            interaction);

        assertTrue(interaction>=0);
        assertTrue(cancel>interaction);

        assertFalse(source.contains(
            "KOMEPacketRelationshipAction.sendHub"));
    }
}
