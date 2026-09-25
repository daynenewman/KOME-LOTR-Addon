package kome.common.command;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;
import kome.common.KOMEAccessFixture;
import net.minecraft.command.WrongUsageException;

/** Command routing is runtime-only; this guards its authoritative shape without a client fixture. */
public class KOMECommandKomeRelationshipTest {
    @Test public void relationshipForceCommandIsStaffOnlyServerTargetedAndUsesCanonicalService() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/command/KOMECommandKome.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("requireStaff(sender)"));
        assertTrue(source.contains("progression relationship <force <serf|knight|lord>|clear>"));
        assertTrue(source.contains("KOMESerfKnightRelationshipService.force"));
        assertTrue(source.contains("KOMESerfKnightRelationshipService.clear"));
        assertTrue(source.contains("targetedNpc(EntityPlayerMP player)"));
        assertTrue(source.contains("getEntitiesWithinAABBExcludingEntity"));
        assertTrue(source.contains("KOMESerfKnightEscortService.cleanup"));
        assertTrue(source.contains("KOMESerfKnightRecoveryService.cleanup"));
        assertTrue(source.contains("KOMESerfKnightDefenseService.cleanup"));
    }
    @Test public void exactVanillaCommandTokensReachRelationshipDispatchBeforeTargetValidation() throws Exception {
        KOMEAccessFixture fixture=new KOMEAccessFixture(); fixture.player.operator=true; KOMECommandKome command=new KOMECommandKome();
        for(String[] args:new String[][]{{"progression","relationship","force","serf"},{"progression","relationship","force","knight"},{"progression","relationship","force","lord"},{"progression","relationship","clear"}}) {
            try { command.processCommand(fixture.player,args); fail("Expected no-target validation"); }
            catch(WrongUsageException expected){assertTrue(expected.getMessage().contains("valid living LOTR faction NPC"));}
        }
        try {command.processCommand(fixture.player,new String[]{"progression","relationship","force"});fail("Expected relationship usage");}
        catch(WrongUsageException expected){assertTrue(expected.getMessage().contains("relationship <force"));}
    }
}
