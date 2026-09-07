package com.lotrcharactercreation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Test;

import com.lotrcharactercreation.body.RaceBodyDefinition;
import com.lotrcharactercreation.network.PlayerAppearanceSyncMessage;
import com.lotrcharactercreation.race.PlayerRace;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class CharacterCreationRuntimeContractTest {

    private static final float FLOAT_TOLERANCE = 0.000001F;

    @Test
    public void raceBodyDefinitionsRemainExact() {
        assertEquals(6, RaceBodyDefinition.values().length);
        assertBody(RaceBodyDefinition.MAN, PlayerRace.MAN, 0.60F, 1.80F, Float.NaN, 1.0F, false);
        assertBody(RaceBodyDefinition.ELF, PlayerRace.ELF, 0.60F, 1.80F, 1.53F, 1.0F, true);
        assertBody(RaceBodyDefinition.DWARF, PlayerRace.DWARF, 0.50F, 1.50F, 1.275F, 0.8125F, true);
        assertBody(RaceBodyDefinition.HOBBIT, PlayerRace.HOBBIT, 0.45F, 1.20F, 1.02F, 0.75F, true);
        assertBody(RaceBodyDefinition.ORC, PlayerRace.ORC, 0.50F, 1.55F, 1.3175F, 0.85F, true);
        assertBody(RaceBodyDefinition.URUK_HAI, PlayerRace.URUK_HAI, 0.60F, 1.80F, 1.53F, 1.0F, true);
    }

    @Test
    public void playerAppearanceSyncMessageRoundTripsPopulatedValues() {
        UUID playerId = UUID.fromString("1ac3b65e-833d-4be4-8a88-cd65674b6b9f");
        PlayerAppearanceSyncMessage decoded = roundTrip(
            new PlayerAppearanceSyncMessage(
                playerId,
                417,
                PlayerRace.ELF.getSerializedId(),
                "female",
                "elf_galadhrim_f_0",
                true));

        assertEquals(playerId, decoded.getPlayerId());
        assertEquals(417, decoded.getEntityId());
        assertEquals(PlayerRace.ELF.getSerializedId(), decoded.getSerializedRaceId());
        assertEquals("female", decoded.getSerializedSexId());
        assertEquals("elf_galadhrim_f_0", decoded.getAppearancePresetId());
        assertTrue(decoded.isCharacterCreationComplete());
    }

    @Test
    public void playerAppearanceSyncMessageRoundTripsAbsentOptionalValues() {
        UUID playerId = UUID.fromString("8e4ca6c8-5273-472e-a4cc-87d03f35a49d");
        PlayerAppearanceSyncMessage decoded = roundTrip(
            new PlayerAppearanceSyncMessage(playerId, -1, null, null, null, false));

        assertEquals(playerId, decoded.getPlayerId());
        assertEquals(-1, decoded.getEntityId());
        assertNull(decoded.getSerializedRaceId());
        assertNull(decoded.getSerializedSexId());
        assertNull(decoded.getAppearancePresetId());
        assertFalse(decoded.isCharacterCreationComplete());
    }

    private static void assertBody(RaceBodyDefinition definition, PlayerRace race, float width, float height,
        float eyeHeight, float renderScale, boolean prototypeSizeEnabled) {
        assertSame(race, definition.getRace());
        assertSame(definition, RaceBodyDefinition.forRace(race));
        assertEquals(width, definition.getWidth(), FLOAT_TOLERANCE);
        assertEquals(height, definition.getHeight(), FLOAT_TOLERANCE);
        if (Float.isNaN(eyeHeight)) {
            assertFalse(definition.hasTargetEyeHeight());
            assertTrue(Float.isNaN(definition.getTargetEyeHeight()));
        } else {
            assertTrue(definition.hasTargetEyeHeight());
            assertEquals(eyeHeight, definition.getTargetEyeHeight(), FLOAT_TOLERANCE);
        }
        assertEquals(renderScale, definition.getRenderScale(), FLOAT_TOLERANCE);
        assertEquals(prototypeSizeEnabled, definition.isPrototypeSizeEnabled());
    }

    private static PlayerAppearanceSyncMessage roundTrip(PlayerAppearanceSyncMessage original) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            original.toBytes(buffer);
            PlayerAppearanceSyncMessage decoded = new PlayerAppearanceSyncMessage();
            decoded.fromBytes(buffer);
            return decoded;
        } finally {
            buffer.release();
        }
    }
}
