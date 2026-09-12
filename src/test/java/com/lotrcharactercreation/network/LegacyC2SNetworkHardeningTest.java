package com.lotrcharactercreation.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.lotrcharactercreation.appearance.ExternalAppearancePresetScanner;
import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.appearance.UrukHaiAppearanceGroup;
import com.lotrcharactercreation.race.PlayerRace;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class LegacyC2SNetworkHardeningTest {

    @Test
    public void perPlayerLimitDoesNotStarveAnotherPlayer() {
        LegacyRequestAdmission admission = new LegacyRequestAdmission(
            LegacyC2SProtocol.MAX_PENDING_ACTIONS_PER_PLAYER,
            LegacyC2SProtocol.MAX_PENDING_ACTIONS_TOTAL);
        UUID floodingPlayer = UUID.fromString("3db35270-a931-4f7f-a5c2-4c9854af0710");
        UUID otherPlayer = UUID.fromString("dcf615f9-f9a0-4d88-b6db-c526ad6b3afd");

        for (int index = 0; index < LegacyC2SProtocol.MAX_PENDING_ACTIONS_PER_PLAYER; index++) {
            assertTrue(admission.tryAcquire(floodingPlayer));
        }

        assertFalse(admission.tryAcquire(floodingPlayer));
        assertTrue(admission.tryAcquire(otherPlayer));
        assertEquals(
            LegacyC2SProtocol.MAX_PENDING_ACTIONS_PER_PLAYER,
            admission.getPendingForPlayer(floodingPlayer));
        assertEquals(1, admission.getPendingForPlayer(otherPlayer));
    }

    @Test
    public void globalLimitAndProcessingReleaseAreDeterministic() {
        LegacyRequestAdmission admission = new LegacyRequestAdmission(3, 4);
        UUID firstPlayer = UUID.fromString("0df66baa-7783-432a-ad77-36bb2899239d");
        UUID secondPlayer = UUID.fromString("182994ee-594c-48cf-a8a6-67de72e79290");
        UUID thirdPlayer = UUID.fromString("4fe8eda1-af3a-4984-a9b4-68cc53bf85a0");

        assertTrue(admission.tryAcquire(firstPlayer));
        assertTrue(admission.tryAcquire(firstPlayer));
        assertTrue(admission.tryAcquire(secondPlayer));
        assertTrue(admission.tryAcquire(secondPlayer));
        assertFalse(admission.tryAcquire(thirdPlayer));

        admission.release(firstPlayer);
        assertTrue(admission.tryAcquire(thirdPlayer));
        assertEquals(4, admission.getPendingTotal());
    }

    @Test
    public void disconnectCleanupAndDuplicateReleaseCannotLeakOrGoNegative() {
        LegacyRequestAdmission admission = new LegacyRequestAdmission(4, 8);
        UUID disconnectedPlayer = UUID.fromString("87c21345-6599-47e4-8df7-d72ad6686c42");
        UUID connectedPlayer = UUID.fromString("b6a818cc-b7a5-4907-9156-c1a30b8b2a59");

        assertTrue(admission.tryAcquire(disconnectedPlayer));
        assertTrue(admission.tryAcquire(disconnectedPlayer));
        assertTrue(admission.tryAcquire(connectedPlayer));
        admission.clearPlayer(disconnectedPlayer);
        admission.release(disconnectedPlayer);
        admission.release(disconnectedPlayer);

        assertEquals(0, admission.getPendingForPlayer(disconnectedPlayer));
        assertEquals(1, admission.getPendingForPlayer(connectedPlayer));
        assertEquals(1, admission.getPendingTotal());

        admission.clearAll();
        admission.release(connectedPlayer);
        assertEquals(0, admission.getPendingTotal());
    }

    @Test
    public void legitimateLegacyIdentifiersRoundTrip() {
        RaceSelectionMessage race = decodeRace("uruk_hai");
        StartingFactionSelectionMessage faction = decodeFaction("dunedain_north");
        SexSelectionMessage sex = decodeSex("female");
        CharacterCreationBackMessage back = decodeBack("confirmation");
        CharacterFinalizationMessage finalization = decodeFinalization(true, "blueMountains");

        assertTrue(race.isValid());
        assertEquals("uruk_hai", race.getSerializedRaceId());
        assertTrue(faction.isValid());
        assertEquals("dunedain_north", faction.getSerializedFactionId());
        assertTrue(sex.isValid());
        assertEquals("female", sex.getSerializedSexId());
        assertTrue(back.isValid());
        assertEquals("confirmation", back.getSerializedSourceStageId());
        assertTrue(finalization.isValid());
        assertTrue(finalization.isReplacementConfirmed());
        assertEquals("blueMountains", finalization.getExpectedExistingPledgeCode());
    }

    @Test
    public void longestCurrentCustomPresetIdentityFitsTheAppearanceBound() {
        char[] stemCharacters = new char[ExternalAppearancePresetScanner.MAX_FILENAME_STEM_LENGTH];
        Arrays.fill(stemCharacters, 'z');
        String presetId = ExternalAppearancePresetScanner.createDeterministicPresetId(
            PlayerRace.URUK_HAI,
            PlayerSex.NONE,
            UrukHaiAppearanceGroup.ISENGARD_URUK_HAI.getSerializedId(),
            new String(stemCharacters));

        assertEquals(103, presetId.getBytes(StandardCharsets.UTF_8).length);
        AppearanceSelectionMessage decoded = decodeAppearance(presetId);
        assertTrue(decoded.isValid());
        assertEquals(presetId, decoded.getPresetId());
        assertTrue(LegacyC2SProtocol.MAX_APPEARANCE_PRESET_ID_BYTES >= 103);
        assertEquals(CustomSkinSyncProtocol.MAX_PRESET_ID_BYTES, LegacyC2SProtocol.MAX_APPEARANCE_PRESET_ID_BYTES);
    }

    @Test
    public void oversizedAndTruncatedStringPayloadsAreRejected() {
        char[] oversizedCharacters = new char[LegacyC2SProtocol.MAX_APPEARANCE_PRESET_ID_BYTES + 1];
        Arrays.fill(oversizedCharacters, 'a');
        AppearanceSelectionMessage oversized = decodeAppearance(new String(oversizedCharacters));
        assertFalse(oversized.isValid());
        assertNull(oversized.getPresetId());

        ByteBuf truncatedBuffer = Unpooled.buffer();
        ByteBufUtils.writeVarInt(truncatedBuffer, 8, 2);
        truncatedBuffer.writeByte('m');
        RaceSelectionMessage truncated = new RaceSelectionMessage();
        truncated.fromBytes(truncatedBuffer);
        assertFalse(truncated.isValid());
        assertNull(truncated.getSerializedRaceId());
    }

    @Test
    public void emptyTrailingAndUnexpectedGrapplePayloadsAreRejected() {
        RaceSelectionMessage empty = decodeRace("");
        assertFalse(empty.isValid());

        ByteBuf trailingBuffer = Unpooled.buffer();
        ByteBufUtils.writeUTF8String(trailingBuffer, "man");
        trailingBuffer.writeByte(1);
        RaceSelectionMessage trailing = new RaceSelectionMessage();
        trailing.fromBytes(trailingBuffer);
        assertFalse(trailing.isValid());

        ElfGrappleAttackMessage validGrapple = new ElfGrappleAttackMessage();
        validGrapple.fromBytes(Unpooled.buffer());
        assertTrue(validGrapple.isValid());
        ByteBuf unexpectedGrapplePayload = Unpooled.buffer();
        unexpectedGrapplePayload.writeByte(1);
        ElfGrappleAttackMessage invalidGrapple = new ElfGrappleAttackMessage();
        invalidGrapple.fromBytes(unexpectedGrapplePayload);
        assertFalse(invalidGrapple.isValid());
    }

    @Test
    public void productionQueueAndLifecycleContractsReleaseAllAdmissionCapacity() throws Exception {
        String network = read(source("com/lotrcharactercreation/network/ModNetwork.java"));
        String lifecycle = read(source("com/lotrcharactercreation/LOTRCharacterCreation.java"));

        assertEquals(7, occurrences(network, "new ConcurrentLinkedQueue<>()"));
        assertEquals(8, occurrences(network, "releaseLegacyRequest(request.player)")
            + occurrences(network, "releaseLegacyRequest(selection.player)"));
        assertTrue(network.contains("MAX_PENDING_ACTIONS_PER_PLAYER"));
        assertTrue(network.contains("MAX_PENDING_ACTIONS_TOTAL"));
        assertTrue(network.contains("discardPendingRequests(PENDING_ELF_GRAPPLE_ATTACKS, playerId);"));
        assertTrue(network.contains("LEGACY_REQUEST_ADMISSION.clearPlayer(playerId);"));
        assertTrue(network.contains("LEGACY_REQUEST_ADMISSION.clearAll();"));
        assertTrue(lifecycle.contains("ModNetwork.clearPendingLegacyRequests(player);"));
        assertTrue(lifecycle.contains("ModNetwork.clearAllPendingLegacyRequests();"));
    }

    @Test
    public void packetDiscriminatorsAndPhaseSevenAdmissionRemainUnchanged() throws Exception {
        String network = read(source("com/lotrcharactercreation/network/ModNetwork.java"));
        String phaseSeven = read(source("com/lotrcharactercreation/network/CustomSkinSyncProtocol.java"));
        Matcher matcher = Pattern.compile(",\\s*(\\d+),\\s*Side\\.(?:CLIENT|SERVER)").matcher(network);
        List<Integer> discriminators = new ArrayList<Integer>();
        while (matcher.find()) {
            discriminators.add(Integer.valueOf(Integer.parseInt(matcher.group(1))));
        }
        Collections.sort(discriminators);

        assertEquals(29, discriminators.size());
        for (int discriminator = 0; discriminator <= 28; discriminator++) {
            assertEquals(discriminator, discriminators.get(discriminator).intValue());
        }
        assertTrue(phaseSeven.contains("MAX_PENDING_SERVER_ACTIONS_PER_PLAYER = 128"));
    }

    private static RaceSelectionMessage decodeRace(String value) {
        ByteBuf buffer = Unpooled.buffer();
        ByteBufUtils.writeUTF8String(buffer, value);
        RaceSelectionMessage decoded = new RaceSelectionMessage();
        decoded.fromBytes(buffer);
        return decoded;
    }

    private static StartingFactionSelectionMessage decodeFaction(String value) {
        ByteBuf buffer = Unpooled.buffer();
        ByteBufUtils.writeUTF8String(buffer, value);
        StartingFactionSelectionMessage decoded = new StartingFactionSelectionMessage();
        decoded.fromBytes(buffer);
        return decoded;
    }

    private static SexSelectionMessage decodeSex(String value) {
        ByteBuf buffer = Unpooled.buffer();
        ByteBufUtils.writeUTF8String(buffer, value);
        SexSelectionMessage decoded = new SexSelectionMessage();
        decoded.fromBytes(buffer);
        return decoded;
    }

    private static AppearanceSelectionMessage decodeAppearance(String value) {
        ByteBuf buffer = Unpooled.buffer();
        ByteBufUtils.writeUTF8String(buffer, value);
        AppearanceSelectionMessage decoded = new AppearanceSelectionMessage();
        decoded.fromBytes(buffer);
        return decoded;
    }

    private static CharacterCreationBackMessage decodeBack(String value) {
        ByteBuf buffer = Unpooled.buffer();
        ByteBufUtils.writeUTF8String(buffer, value);
        CharacterCreationBackMessage decoded = new CharacterCreationBackMessage();
        decoded.fromBytes(buffer);
        return decoded;
    }

    private static CharacterFinalizationMessage decodeFinalization(boolean replacementConfirmed, String pledgeCode) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeBoolean(replacementConfirmed);
        ByteBufUtils.writeUTF8String(buffer, pledgeCode);
        CharacterFinalizationMessage decoded = new CharacterFinalizationMessage();
        decoded.fromBytes(buffer);
        return decoded;
    }

    private static Path source(String relativePath) {
        return Paths.get("src/main/java").resolve(relativePath.replace('/', File.separatorChar));
    }

    private static String read(Path path) throws Exception {
        assertTrue("Missing source file: " + path, Files.isRegularFile(path));
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
}
