package kome.common.data;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class KOMEBaseIsolationTest {
    @Test
    public void restoredLotrWaypointSourcesContainNoKomeAllianceHook() throws Exception {
        Path root = Paths.get("..").toAbsolutePath().normalize();
        String waypoint = read(root.resolve("src/main/java/lotr/common/world/map/LOTRWaypoint.java"));
        String playerData = read(root.resolve("src/main/java/lotr/common/LOTRPlayerData.java"));
        assertFalse(waypoint.contains("isKOMEAlliedWaypointUnlocked"));
        assertFalse(waypoint.contains("kome.common.data.KOMEWaypoint"));
        int start = playerData.indexOf("public void receiveFTBouncePacket()");
        int end = playerData.indexOf("public void rejectFellowshipInvite", start);
        assertTrue(start >= 0 && end > start);
        String method = playerData.substring(start, end);
        assertTrue(method.contains("fastTravelTo(targetFTWaypoint);"));
        assertFalse(method.contains("KOME"));
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
        String allianceDetail = read(main.resolve("kome/client/gui/KOMEGuiAllianceDetail.java"));
        String worldData = read(main.resolve("kome/common/data/KOMEWorldData.java"));
        assertFalse(allianceCommand.contains("/alliance production"));
        assertFalse(allianceCommand.contains("tradeProduceSlots"));
        assertFalse(allianceDetail.contains("Production"));
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
        String allianceGui = read(main.resolve("kome/client/gui/KOMEGuiAlliance.java"));
        String allianceDetail = read(main.resolve("kome/client/gui/KOMEGuiAllianceDetail.java"));
        assertTrue(warCommand.contains("list [active|ending|ended|all]"));
        assertTrue(warCommand.contains("\"ending\".equals(filter) && !war.isEnding()"));
        assertTrue(packetHandler.contains("KOMEPacketAllianceAction.Handler.class"));
        assertTrue(packetHandler.contains("KOMEPacketTroopGuiAction.Handler.class"));
        assertTrue(actionPacket.contains("new KOMECommandAlliance().processCommand(player, command)"));
        assertTrue(actionPacket.contains("KOMEAllianceRecordBuilder.build(data, player)"));
        assertTrue(actionPacket.contains("new KOMECommandTroops().processCommand(player, new String[] {\"companies\"})"));
        assertTrue(recordBuilder.contains("MILITARY_CONTEXT\\t"));
        assertTrue(recordBuilder.contains("MILITARY_COMPANY\\t"));
        assertTrue(allianceGui.contains("\"TRACK\".equals(parts[0])"));
        assertTrue(allianceGui.contains("\"MILITARY_CONTEXT\".equals(parts[0])"));
        assertTrue(allianceDetail.contains("new KOMEPacketMovementHistoryRequest"));
        assertFalse(allianceDetail.contains("sendChat(\"/troops"));
        assertTrue(troopPacket.contains("Unknown troop GUI action"));
        assertTrue(troopPacket.contains("Troop action rejected:"));
    }

    private static String read(Path path) throws Exception {
        assertTrue("Missing source: " + path, Files.isRegularFile(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
