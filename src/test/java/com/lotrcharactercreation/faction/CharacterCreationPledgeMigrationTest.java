package com.lotrcharactercreation.faction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import com.lotrcharactercreation.network.CharacterCreationRequiredMessage;
import com.lotrcharactercreation.network.CharacterFinalizationMessage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class CharacterCreationPledgeMigrationTest {

    @Test
    public void noPledgeUsesNormalFactionAuthorizationAndEligibilityPath() throws Exception {
        assertFalse(StartingFactionApplication.isReplacementRequired(true, "", "gondor"));
        assertTrue(StartingFactionApplication.isFinalizationAuthorized(true, false, "", "gondor", false, ""));

        String application = source("main/java/com/lotrcharactercreation/faction/StartingFactionApplication.java");
        assertTrue(application.contains("!lotrData.canMakeNewPledge()"));
        assertTrue(application.contains("lotrData.getFactionsPreventingPledgeTo(selectedPledge)"));
        assertTrue(application.contains("if (!lotrData.canPledgeTo(selectedPledge))"));
    }

    @Test
    public void samePledgeNeedsNoReplacementAndRemainsAuthorized() throws Exception {
        assertFalse(StartingFactionApplication.isReplacementRequired(true, "gondor", "gondor"));
        assertTrue(
            StartingFactionApplication
                .isFinalizationAuthorized(true, false, "gondor", "gondor", false, "gondor"));

        String application = source("main/java/com/lotrcharactercreation/faction/StartingFactionApplication.java");
        assertBefore(
            application,
            "if (lotrData.isPledgedTo(selectedPledge))",
            "} else if (existingPledge != null)");
        assertTrue(application.contains("grantRequiredAlignment(lotrData, selectedPledge, pledgeAlignment);"));
    }

    @Test
    public void differingPledgeRequiresExplicitConfirmation() {
        assertTrue(StartingFactionApplication.isReplacementRequired(true, "gondor", "mordor"));
        assertFalse(
            StartingFactionApplication
                .isFinalizationAuthorized(true, false, "gondor", "mordor", false, "gondor"));
        assertTrue(
            StartingFactionApplication
                .isFinalizationAuthorized(true, false, "gondor", "mordor", true, "gondor"));
    }

    @Test
    public void differingPledgeWithoutConfirmationCannotReachMutation() throws Exception {
        assertFalse(
            StartingFactionApplication
                .isFinalizationAuthorized(true, false, "gondor", "mordor", false, "gondor"));

        String network = source("main/java/com/lotrcharactercreation/network/ModNetwork.java");
        assertBefore(
            network,
            "if (!StartingFactionApplication.isFinalizationAuthorized(",
            "StartingFaction appliedFaction = StartingFactionApplication.tryApply(");
    }

    @Test
    public void changedExistingPledgeIsRejectedEvenWhenReplacementWasConfirmed() {
        assertFalse(
            StartingFactionApplication
                .isFinalizationAuthorized(true, false, "isengard", "mordor", true, "gondor"));
    }

    @Test
    public void existingPledgeToWandererRequiresConfirmation() {
        assertTrue(StartingFactionApplication.isReplacementRequired(true, "gondor", ""));
        assertFalse(
            StartingFactionApplication.isFinalizationAuthorized(true, false, "gondor", "", false, "gondor"));
        assertTrue(
            StartingFactionApplication.isFinalizationAuthorized(true, false, "gondor", "", true, "gondor"));
    }

    @Test
    public void disabledAutomaticAllegiancePreservesUnconditionalFinalization() throws Exception {
        assertFalse(StartingFactionApplication.isReplacementRequired(false, "gondor", "mordor"));
        assertTrue(
            StartingFactionApplication
                .isFinalizationAuthorized(false, false, "isengard", "mordor", false, "gondor"));

        String application = source("main/java/com/lotrcharactercreation/faction/StartingFactionApplication.java");
        assertBefore(
            application,
            "if (!ModConfiguration.isAutomaticStartingAllegianceEnabled())",
            "LOTRPlayerData lotrData = LOTRLevelData.getData(player);");
    }

    @Test
    public void completedCharacterCreationCannotAuthorizeOverride() throws Exception {
        assertFalse(
            StartingFactionApplication
                .isFinalizationAuthorized(true, true, "gondor", "mordor", true, "gondor"));

        String application = source("main/java/com/lotrcharactercreation/faction/StartingFactionApplication.java");
        assertTrue(application.contains("if (PlayerRaceData.isCharacterCreationComplete(player)"));
    }

    @Test
    public void replacementUsesNormalRevocationThenNarrowAuthoritativePledge() throws Exception {
        String application = source("main/java/com/lotrcharactercreation/faction/StartingFactionApplication.java");
        String replacement = application.substring(
            application.indexOf("private static void replaceExistingPledge"),
            application.indexOf("private static void grantRequiredAlignment"));

        assertBefore(replacement, "lotrData.revokePledgeFaction(player, true);", "grantRequiredAlignment(");
        assertBefore(replacement, "grantRequiredAlignment(", "getFactionsPreventingPledgeTo(selectedPledge)");
        assertBefore(replacement, "getFactionsPreventingPledgeTo(selectedPledge)", "canPledgeTo(selectedPledge)");
        assertBefore(replacement, "canPledgeTo(selectedPledge)", "setPledgeFaction(selectedPledge)");
        assertTrue(replacement.contains("getAlignment(blockingFaction) > 0.0F"));
        assertTrue(replacement.contains("setAlignment(blockingFaction, 0.0F)"));
        assertFalse(application.contains("setPledgeBreakCooldown("));
        assertFalse(application.contains("setBrokenPledgeFaction("));
        assertFalse(application.contains("KOMEPledgeReleaseService"));
    }

    @Test
    public void messagesRoundTripConfirmationAndServerPledgeState() {
        ByteBuf finalizationBuffer = Unpooled.buffer();
        try {
            CharacterFinalizationMessage originalFinalization = new CharacterFinalizationMessage(true, "gondor");
            originalFinalization.toBytes(finalizationBuffer);
            CharacterFinalizationMessage decodedFinalization = new CharacterFinalizationMessage();
            decodedFinalization.fromBytes(finalizationBuffer);
            assertTrue(decodedFinalization.isReplacementConfirmed());
            assertEquals("gondor", decodedFinalization.getExpectedExistingPledgeCode());
        } finally {
            finalizationBuffer.release();
        }

        ByteBuf requiredBuffer = Unpooled.buffer();
        try {
            CharacterCreationRequiredMessage originalRequired = new CharacterCreationRequiredMessage(
                "confirmation",
                "man",
                "male",
                "mordor",
                "preset",
                "gondor",
                true);
            originalRequired.toBytes(requiredBuffer);
            CharacterCreationRequiredMessage decodedRequired = new CharacterCreationRequiredMessage();
            decodedRequired.fromBytes(requiredBuffer);
            assertEquals("mordor", decodedRequired.getSerializedFactionId());
            assertEquals("gondor", decodedRequired.getCurrentPledgeCode());
            assertTrue(decodedRequired.isAutomaticStartingAllegiance());
        } finally {
            requiredBuffer.release();
        }
    }

    @Test
    public void finalizationPacketCannotChooseTheTargetFaction() throws Exception {
        for (Field field : CharacterFinalizationMessage.class.getDeclaredFields()) {
            assertFalse(field.getName(), field.getName().toLowerCase().contains("faction"));
        }

        String network = source("main/java/com/lotrcharactercreation/network/ModNetwork.java");
        assertTrue(network.contains("StartingFaction selectedFaction = PlayerRaceData.getStartingFaction(player);"));
        assertFalse(source("main/java/com/lotrcharactercreation/network/CharacterFinalizationMessage.java")
            .contains("selectedFaction"));
    }

    @Test
    public void confirmationScreenWarnsAndBackDoesNotMutateLotrState() throws Exception {
        String gui = source("main/java/com/lotrcharactercreation/client/gui/GuiCharacterConfirmation.java");
        assertTrue(gui.contains("PLEDGE REPLACEMENT WARNING"));
        assertTrue(gui.contains("Break Pledge & Confirm"));
        assertTrue(gui.contains("pledge and leave you unpledged."));
        assertTrue(gui.contains("Normal pledge-departure cleanup will run"));

        String goBack = gui.substring(gui.indexOf("private void goBack()"), gui.indexOf("@Override", gui.indexOf("private void goBack()")));
        assertTrue(goBack.contains("ModNetwork.sendCharacterCreationBack(CharacterCreationStage.CONFIRMATION);"));
        assertFalse(goBack.contains("LOTR"));
    }

    private static String source(String relativePath) throws IOException {
        Path path = Paths.get("src").resolve(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void assertBefore(String text, String first, String second) {
        int firstIndex = text.indexOf(first);
        int secondIndex = text.indexOf(second);
        assertTrue("Missing expected first value: " + first, firstIndex >= 0);
        assertTrue("Missing expected second value: " + second, secondIndex >= 0);
        assertTrue(first + " must precede " + second, firstIndex < secondIndex);
    }
}
