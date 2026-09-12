package com.lotrcharactercreation.faction;

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

public class CharacterCreationAlignmentSyncTest {

    @Test
    public void completedFirstTimeCreationSendsNativeLotrPlayerDataAfterWaypointApplication() throws Exception {
        String finalizer = finalizerSource();
        int waypointApplication = finalizer.indexOf("applyStartingWaypointIfReady(player);");
        int completionGuard = finalizer.indexOf(
            "if (PlayerRaceData.isCharacterCreationComplete(player))",
            waypointApplication);
        int lotrRefresh = finalizer.indexOf("LOTRLevelData.sendPlayerData(player);", completionGuard);
        int appearanceRefresh = finalizer.indexOf("sendPlayerAppearanceToTrackingAndSelf(player);", lotrRefresh);

        assertOrdered(waypointApplication, completionGuard, lotrRefresh, appearanceRefresh);
    }

    @Test
    public void recreationReturnsBeforeTheFirstTimeLotrRefresh() throws Exception {
        String finalizer = finalizerSource();
        int recreationBranch = finalizer.indexOf("if (CharacterRecreationService.isInProgress(player))");
        int recreationCompletion = finalizer.indexOf("CharacterRecreationService.complete(player)", recreationBranch);
        int recreationReturn = finalizer.indexOf("return;", recreationCompletion);
        int lotrRefresh = finalizer.indexOf("LOTRLevelData.sendPlayerData(player);");

        assertOrdered(recreationBranch, recreationCompletion, recreationReturn, lotrRefresh);
    }

    @Test
    public void refreshDoesNotMutateAlignmentOrPledgeAndOccursOnlyOnce() throws Exception {
        String network = source("main/java/com/lotrcharactercreation/network/ModNetwork.java");
        String completionBranch = network.substring(
            network.indexOf("LOTRLevelData.sendPlayerData(player);"),
            network.indexOf("private static void applyStartingWaypointIfReady"));

        assertEquals(1, occurrences(network, "LOTRLevelData.sendPlayerData(player);"));
        assertFalse(completionBranch.contains("setAlignment("));
        assertFalse(completionBranch.contains("addAlignment("));
        assertFalse(completionBranch.contains("setPledgeFaction("));
        assertFalse(completionBranch.contains("revokePledgeFaction("));
    }

    @Test
    public void existingOneTimeStartingApplicationsRemainSingleGuardedCalls() throws Exception {
        String finalizer = finalizerSource();

        assertEquals(1, occurrences(finalizer, "StartingFactionApplication.tryApply("));
        assertEquals(1, occurrences(finalizer, "applyStartingWaypointIfReady(player);"));
        assertTrue(finalizer.indexOf("StartingFactionApplication.tryApply(")
            < finalizer.indexOf("LOTRLevelData.sendPlayerData(player);"));
    }

    @Test
    public void alignmentRefreshAddsNoCharacterCreationPacketDiscriminator() throws Exception {
        String network = source("main/java/com/lotrcharactercreation/network/ModNetwork.java");
        List<Integer> discriminators = new ArrayList<Integer>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile(
                "registerMessage\\([^;]*?,\\s*(\\d+),\\s*Side\\.(?:CLIENT|SERVER)\\s*\\);",
                java.util.regex.Pattern.DOTALL)
            .matcher(network);
        while (matcher.find()) {
            discriminators.add(Integer.valueOf(Integer.parseInt(matcher.group(1))));
        }

        Collections.sort(discriminators);
        assertEquals(29, discriminators.size());
        for (int index = 0; index <= 28; index++) {
            assertEquals(index, discriminators.get(index).intValue());
        }
    }

    private static String finalizerSource() throws Exception {
        String network = source("main/java/com/lotrcharactercreation/network/ModNetwork.java");
        return network.substring(
            network.indexOf("private static void finalizeCharacterCreation"),
            network.indexOf("private static void applyStartingWaypointIfReady"));
    }

    private static String source(String relativePath) throws Exception {
        Path path = Paths.get("src").resolve(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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

    private static void assertOrdered(int... indexes) {
        for (int index : indexes) {
            assertTrue("Missing expected source fragment", index >= 0);
        }
        for (int index = 1; index < indexes.length; index++) {
            assertTrue("Expected source fragments in lifecycle order", indexes[index - 1] < indexes[index]);
        }
    }
}
