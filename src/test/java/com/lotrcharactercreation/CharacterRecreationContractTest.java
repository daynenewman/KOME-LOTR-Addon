package com.lotrcharactercreation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class CharacterRecreationContractTest {

    @Test
    public void komeCommandUsesTheServerOwnedRecreationServiceAndStaffGate() throws Exception {
        String command = source("main/java/kome/common/command/KOMECommandKome.java");
        int recreateBranch = command.indexOf("\"character\".equalsIgnoreCase(args[0])");
        int staffGate = command.indexOf("requireStaff(sender);", recreateBranch);
        int targetLookup = command.indexOf("getPlayer(sender, args[2])", recreateBranch);
        int recreationStart = command.indexOf("CharacterRecreationService.begin(target)", recreateBranch);
        int stateRefresh = command.indexOf("LOTRCharacterCreation.refreshPlayerStateAndSynchronize(target)", recreationStart);
        int guiOpen = command.indexOf("ModNetwork.sendCharacterCreationRequired(target)", stateRefresh);

        assertTrue(recreateBranch >= 0);
        assertTrue(staffGate > recreateBranch);
        assertTrue(targetLookup > staffGate);
        assertTrue(recreationStart > targetLookup);
        assertTrue(stateRefresh > recreationStart);
        assertTrue(guiOpen > stateRefresh);
        assertTrue(command.contains("canCommandSenderUseCommand(2, getCommandName())"));
        assertTrue(command.contains("MinecraftServer.getServer().getAllUsernames()"));
    }

    @Test
    public void recreationStartImmediatelyRefreshesNeutralIncompletePresentation() throws Exception {
        String lifecycle = source("main/java/com/lotrcharactercreation/LOTRCharacterCreation.java");
        String network = source("main/java/com/lotrcharactercreation/network/ModNetwork.java");
        String service = source("main/java/com/lotrcharactercreation/creation/CharacterRecreationService.java");

        assertTrue(lifecycle.contains("public static void refreshPlayerStateAndSynchronize(EntityPlayerMP player)"));
        assertTrue(lifecycle.contains("CharacterRecreationService.isAwaitingRaceSelection(player) ? PlayerRace.MAN"));
        assertTrue(lifecycle.contains("PlayerRaceSizeService.applyRaceSize(player, presentationRace)"));
        assertTrue(lifecycle.contains("PlayerRaceEyeService.applyServerEyeHeight(player, presentationRace)"));
        assertTrue(lifecycle.contains("RaceTraitService.refreshDerivedAttributes(player)"));
        assertTrue(lifecycle.contains("ModNetwork.sendPlayerAppearanceToTrackingAndSelf(player)"));

        assertTrue(network.contains("CharacterRecreationService.isAwaitingRaceSelection(player)"));
        assertTrue(network.contains("PlayerRace race = awaitingRecreationRace ? PlayerRace.MAN"));
        assertTrue(network.contains("PlayerSex sex = awaitingRecreationRace ? null"));
        assertTrue(network.contains("String presetId = awaitingRecreationRace ? null"));

        assertTrue(service.contains("isInProgress(player) && !PlayerRaceData.isRaceSelectionComplete(player)"));
        assertFalse(service.contains("PlayerRaceData.setRace(player"));
        assertFalse(service.contains("PlayerRaceData.setSex(player"));
        assertFalse(service.contains("PlayerRaceData.setAppearancePresetId(player"));
    }

    @Test
    public void recreationFinalizationSkipsOneTimeApplicationsAndRefreshesPhysicalState() throws Exception {
        String network = source("main/java/com/lotrcharactercreation/network/ModNetwork.java");
        int finalizer = network.indexOf("private static void finalizeCharacterCreation");
        int recreationBranch = network.indexOf("CharacterRecreationService.isInProgress(player)", finalizer);
        int normalFactionApplication = network.indexOf("StartingFactionApplication.tryApply(", finalizer);
        int normalWaypointApplication = network.indexOf("applyStartingWaypointIfReady(player)", finalizer);

        assertTrue(recreationBranch > finalizer);
        assertTrue(normalFactionApplication > recreationBranch);
        assertTrue(normalWaypointApplication > recreationBranch);
        assertTrue(network.contains("CharacterRecreationService.complete(player)"));
        assertTrue(network.contains("PlayerRaceSizeService.applyStoredRaceSize(player)"));
        assertTrue(network.contains("PlayerRaceEyeService.applyStoredServerEyeHeight(player)"));
        assertTrue(network.contains("RaceTraitService.refreshDerivedAttributes(player)"));
        assertTrue(network.contains("sendPlayerAppearanceToTrackingAndSelf(player)"));
        assertTrue(network.contains("sendCharacterRecreationCompleted(player)"));
        assertTrue(
            network.contains("!CharacterRecreationService.isInProgress(player)"));
    }

    @Test
    public void legacyResetCannotClearCompletionOrBypassAuthorization() throws Exception {
        String legacyCommand = source("main/java/com/lotrcharactercreation/command/CommandLotrCreation.java");
        int resetBranch = legacyCommand.indexOf("arguments[0].equalsIgnoreCase(\"reset\")");
        int nextBranch = legacyCommand.indexOf("} else {", resetBranch + 1);
        String resetBody = legacyCommand.substring(resetBranch, nextBranch);

        assertTrue(resetBody.contains("Unsafe reset is disabled"));
        assertTrue(resetBody.contains("/kome character recreate <player>"));
        assertFalse(resetBody.contains("setCharacterCreationComplete(player, false)"));
        assertFalse(resetBody.contains("setCharacterEditAuthorized"));
    }

    @Test
    public void recreationServiceHasNoClientLotrOrOneTimeApplicationDependency() throws Exception {
        String service = source("main/java/com/lotrcharactercreation/creation/CharacterRecreationService.java");

        assertFalse(service.contains("net.minecraft.client"));
        assertFalse(service.contains("StartingFactionApplication"));
        assertFalse(service.contains("StartingWaypointApplication"));
        assertFalse(service.contains("LOTRLevelData"));
        assertFalse(service.contains("setStartingFactionApplied"));
        assertFalse(service.contains("setStartingWaypointApplied"));
        assertTrue(service.contains("PlayerRaceData.setCharacterEditAuthorized(player, true)"));
        assertTrue(service.contains("PlayerRaceData.setCharacterCreationComplete(player, false)"));
        assertTrue(service.contains("PlayerRaceData.setRaceSelectionComplete(player, false)"));
    }

    @Test
    public void recreationAddsNoNetworkDiscriminator() throws Exception {
        String network = source("main/java/com/lotrcharactercreation/network/ModNetwork.java");
        List<Integer> discriminators = new ArrayList<Integer>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("registerMessage\\([^;]*?,\\s*(\\d+),\\s*Side\\.(?:CLIENT|SERVER)\\s*\\);",
                java.util.regex.Pattern.DOTALL)
            .matcher(network);
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            discriminators.add(Integer.valueOf(value));
        }

        Collections.sort(discriminators);
        assertEquals(29, discriminators.size());
        for (int index = 0; index <= 28; index++) {
            assertEquals(index, discriminators.get(index).intValue());
        }
    }

    @Test
    public void recreationCompletionReusesExistingClientMessageAndClosesConfirmation() throws Exception {
        String network = source("main/java/com/lotrcharactercreation/network/ModNetwork.java");
        String client = source("main/java/com/lotrcharactercreation/proxy/ClientProxy.java");

        assertTrue(network.contains("CharacterCreationStage.COMPLETE.getSerializedId()"));
        assertTrue(client.contains("stage == CharacterCreationStage.COMPLETE"));
        assertTrue(client.contains("minecraft.currentScreen instanceof GuiCharacterConfirmation"));
        assertTrue(client.contains("Character recreation complete."));
    }

    private static String source(String relativePath) throws Exception {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path direct = root.resolve("src").resolve(relativePath);
        Path nested = root.resolve(relativePath);
        Path path = Files.exists(direct) ? direct : nested;
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
