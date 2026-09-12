package kome.common.data;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

import static org.junit.Assert.*;

public class KOMEBaseIsolationTest {
    @Test
    public void stockLotrJarRemainsAnUnmodifiedBinaryBuildInput() throws Exception {
        Path addon = Paths.get("").toAbsolutePath().normalize();
        Path stockLotrJar = addon.resolve("libs/LOTRMod v36.15.jar");

        assertFalse("Base LOTR source must not be vendored into KOME",
            Files.exists(addon.resolve("src/main/java/lotr")));

        assertTrue("Missing stock LOTRMod v36.15.jar",
            Files.isRegularFile(stockLotrJar));

        assertEquals("Stock LOTRMod v36.15.jar was modified",
            "4F296E749C0D4739ECF859217A526B4218A2A45A768C08D3D551AF0D0D3D5635",
            sha256(stockLotrJar));

        String buildScript = read(addon.resolve("build.gradle.kts"));

        assertTrue(buildScript.contains(
            "rfg.deobf(project.files(\"libs/LOTRMod v36.15.jar\"))"));

        assertFalse(buildScript.contains("kome.lotrClassesDir"));
        assertFalse(buildScript.contains("kome.lotrResourcesDir"));
        assertFalse(buildScript.contains("kome.lotrRuntimeJar"));
    }

    @Test
    public void waypointOverlayDoesNotDirectlyAccessDeployedLotrMapFields() throws Exception {
        Path addon = Paths.get("").toAbsolutePath().normalize();
        String overlay = read(addon.resolve("src/main/java/kome/client/KOMEWaypointMapOverlay.java"));
        assertFalse(overlay.contains("map.selectedWaypoint"));
        assertTrue(overlay.contains("getDeclaredField(\"selectedWaypoint\")"));
        assertTrue(overlay.contains("native map rendering will continue"));
    }

    @Test
    public void provisionalProduceRuntimeIsAbsentWhileMigrationCleanupRemains() throws Exception {
        Path addon = Paths.get("").toAbsolutePath().normalize();
        Path main = addon.resolve("src/main/java");
        assertFalse(Files.exists(main.resolve("kome/common/data/KOMEProduceFarmerService.java")));
        assertFalse(Files.exists(main.resolve("kome/common/data/KOMEProduceSlot.java")));
        assertFalse(Files.exists(main.resolve("kome/client/gui/KOMEGuiAllianceProduction.java")));

        String allianceCommand = read(main.resolve("kome/common/command/KOMECommandAlliance.java"));
        String allianceGui = read(main.resolve("kome/client/gui/KOMEGuiAllianceUnified.java"));
        String worldData = read(main.resolve("kome/common/data/KOMEWorldData.java"));
        assertFalse(allianceCommand.contains("/alliance production"));
        assertFalse(allianceCommand.contains("tradeProduceSlots"));
        assertFalse(allianceGui.contains("Production"));
        assertTrue(worldData.contains("nbt.removeTag(\"AllianceProduceSlots\")"));
        assertTrue(worldData.contains("nbt.removeTag(\"TradeProduceSlotsMaximum\")"));
        assertFalse(worldData.contains("nbt.setTag(\"AllianceProduceSlots\""));
    }

    @Test
    public void militarySupportAndAllianceWarGuiRulesContainNoExampleFactionIds() throws Exception {
        Path main = Paths.get("").toAbsolutePath().normalize().resolve("src/main/java");
        String sources = (read(main.resolve("kome/common/data/KOMEWarService.java")) + "\n"
            + read(main.resolve("kome/common/data/KOMEWartimeStewardshipService.java")) + "\n"
            + read(main.resolve("kome/client/gui/KOMEGuiServerRecords.java")) + "\n"
            + read(main.resolve("kome/client/KOMEConquestMapOverlay.java"))).toLowerCase();
        for (String example : new String[] {"gondor", "rohan", "isengard", "mordor", "dunedain", "rhudel"}) {
            assertFalse("Example faction was hardcoded in generic war support/GUI rules: " + example,
                sources.contains("\"" + example));
        }
    }

    @Test
    public void endingFilterAndTypedAllianceActionsRemainWired() throws Exception {
        Path main = Paths.get("").toAbsolutePath().normalize().resolve("src/main/java");
        String warCommand = read(main.resolve("kome/common/command/KOMECommandWar.java"));
        String packetHandler = read(main.resolve("kome/common/network/KOMEPacketHandler.java"));
        String actionPacket = read(main.resolve("kome/common/network/KOMEPacketAllianceAction.java"));
        String troopPacket = read(main.resolve("kome/common/network/KOMEPacketTroopGuiAction.java"));
        String recordBuilder = read(main.resolve("kome/common/data/KOMEAllianceRecordBuilder.java"));
        String allianceGui = read(main.resolve("kome/client/gui/KOMEGuiAllianceUnified.java"));
        assertTrue(warCommand.contains("list [active|ending|ended|all]"));
        assertTrue(warCommand.contains("\"ending\".equals(filter) && !war.isEnding()"));
        assertTrue(packetHandler.contains("KOMEPacketAllianceAction.Handler.class"));
        assertTrue(packetHandler.contains("KOMEPacketTroopGuiAction.Handler.class"));
        assertTrue(actionPacket.contains("new KOMECommandAlliance().processCommand(player, command)"));
        assertTrue(actionPacket.contains("KOMEAllianceRecordBuilder.build(data, player)"));
        assertTrue(actionPacket.contains("new KOMECommandTroops().processCommand(player, new String[] {\"companies\"})"));
        assertTrue(recordBuilder.contains("STAGE_RELATION\\t"));
        assertTrue(recordBuilder.contains("REQUEST_OPTION_V2\\t"));
        assertFalse(recordBuilder.contains("MILITARY_CONTEXT\\t"));
        assertFalse(recordBuilder.contains("MILITARY_COMPANY\\t"));
        assertTrue(allianceGui.contains("\"STAGE_RELATION\".equals(parts[0])"));
        assertTrue(allianceGui.contains("\"REQUEST_OPTION_V2\".equals(parts[0])"));
        assertTrue(allianceGui.contains("KOMEGuiConfirmation"));
        assertFalse(allianceGui.contains("sendChat(\"/troops"));
        assertTrue(troopPacket.contains("Unknown troop GUI action"));
        assertTrue(troopPacket.contains("Troop action rejected:"));
    }

    @Test
    public void readOnlyBuildAndWarCommandsArePublicWhileMutationsRemainStaffOnly() throws Exception {
        Path commands = Paths.get("").toAbsolutePath().normalize()
            .resolve("src/main/java/kome/common/command");
        String build = read(commands.resolve("KOMECommandBuild.java")).replace("\r\n", "\n");
        String war = read(commands.resolve("KOMECommandWar.java")).replace("\r\n", "\n");

        assertTrue(build.contains("public int getRequiredPermissionLevel() {\n        return 0;"));
        assertTrue(war.contains("public int getRequiredPermissionLevel() {\n        return 0;"));

        assertTrue(build.contains("if (\"list\".equals(action))"));
        assertTrue(build.contains("if (\"inspect\".equals(action)"));
        assertTrue(build.contains("Only administrators may modify Build records or configuration."));
        assertEquals(3, occurrences(build, "requireStaff(sender);"));

        assertTrue(war.contains("if (\"list\".equals(action))"));
        assertTrue(war.contains("if (\"status\".equals(action))"));
        assertTrue(war.contains("Only administrators may modify war records."));
        assertEquals(4, occurrences(war, "requireStaff(sender);"));
    }

    private static String sha256(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(path));

        StringBuilder result = new StringBuilder();
        for (byte value : digest) {
            result.append(String.format("%02X", value & 0xff));
        }
        return result.toString();
    }

    private static int occurrences(String text, String value) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }

    private static String read(Path path) throws Exception {
        assertTrue("Missing source: " + path, Files.isRegularFile(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
