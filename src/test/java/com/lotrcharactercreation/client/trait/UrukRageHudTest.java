package com.lotrcharactercreation.client.trait;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import org.junit.Test;

import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundList;
import net.minecraft.client.audio.SoundListSerializer;
import net.minecraft.util.ResourceLocation;

import paulscode.sound.codecs.CodecJOrbis;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.lotrcharactercreation.network.UrukHeavyBlowActivationMessage;
import com.lotrcharactercreation.network.UrukRageStateMessage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class UrukRageHudTest {

    @Test
    public void zeroRageAndInvalidClientConditionsRenderNothing() {
        assertFalse(UrukRageHudModel.shouldRender(true, true, true, false, 0.0F, 0));
        assertFalse(UrukRageHudModel.shouldRender(false, true, true, false, 80.0F, 0));
        assertFalse(UrukRageHudModel.shouldRender(true, false, true, false, 80.0F, 0));
        assertFalse(UrukRageHudModel.shouldRender(true, true, false, false, 80.0F, 0));
        assertFalse(UrukRageHudModel.shouldRender(true, true, true, true, 80.0F, 0));
        assertTrue(UrukRageHudModel.shouldRender(true, true, true, false, 1.0F, 0));
        assertTrue(UrukRageHudModel.shouldRender(true, true, true, false, 0.0F, 1));
        assertEquals(0.0F, UrukRageHudModel.baseIntensity(Float.NaN), 0.0F);
    }

    @Test
    public void normalOpacityIsLinearThroughFiftyAndThenStaysAtItsMaximum() {
        assertEquals(0.0F, UrukRageHudModel.baseIntensity(0.0F), 0.0F);
        assertEquals(0.012F, UrukRageHudModel.baseIntensity(1.0F), 0.00001F);
        assertEquals(0.30F, UrukRageHudModel.baseIntensity(25.0F), 0.00001F);
        assertEquals(0.588F, UrukRageHudModel.baseIntensity(49.0F), 0.00001F);
        assertFalse(UrukRageHudModel.isReady(49.999F));
        assertTrue(UrukRageHudModel.isReady(50.0F));
        assertEquals(UrukRageHudModel.MAX_NORMAL_OPACITY, UrukRageHudModel.baseIntensity(50.0F), 0.0F);
        assertEquals(UrukRageHudModel.MAX_NORMAL_OPACITY, UrukRageHudModel.baseIntensity(75.0F), 0.0F);
        assertEquals(UrukRageHudModel.MAX_NORMAL_OPACITY, UrukRageHudModel.baseIntensity(100.0F), 0.0F);
    }

    @Test
    public void thicknessHeartbeatStartsAtFiftyAcceleratesAndStaysBounded() {
        assertEquals(30.0F, UrukRageHudModel.heartbeatPeriodTicks(50.0F), 0.0F);
        assertEquals(23.0F, UrukRageHudModel.heartbeatPeriodTicks(75.0F), 0.0F);
        assertEquals(16.0F, UrukRageHudModel.heartbeatPeriodTicks(100.0F), 0.0F);
        assertEquals(0.0F, UrukRageHudModel.thicknessSwelling(49.999F, 3.0F), 0.0F);
        assertEquals(1.0F, UrukRageHudModel.thicknessSwelling(50.0F, 3.0F), 0.00001F);
        assertEquals(1.0F, UrukRageHudModel.thicknessSwelling(75.0F, 2.3F), 0.00001F);
        assertEquals(1.0F, UrukRageHudModel.thicknessSwelling(100.0F, 1.6F), 0.00001F);
        assertEquals(0.0F, UrukRageHudModel.thicknessSwelling(49.0F, 1.6F), 0.0F);
        for (int tick = 0; tick < 100; tick++) {
            float swell = UrukRageHudModel.thicknessSwelling(100.0F, tick);
            assertTrue(swell >= 0.0F && swell <= UrukRageHudModel.MAX_THICKNESS_SWELL);
            assertEquals(UrukRageHudModel.MAX_NORMAL_OPACITY,
                UrukRageHudModel.intensity(100.0F, tick, 0), 0.0F);
        }
        assertEquals(UrukRageHudModel.normalRed(50.0F), UrukRageHudModel.normalRed(100.0F), 0.0F);
        assertEquals(UrukRageHudModel.MAX_NORMAL_OPACITY,
            UrukRageHudModel.intensity(50.0F, 3.0F, 0), 0.0F);
    }

    @Test
    public void heartbeatSelectsStrokeFramesAndFlashAlwaysUsesApprovedOriginal() {
        assertEquals(0, UrukRageHudModel.heartbeatFrame(49.999F, 3.0F, 0));
        assertEquals(0, UrukRageHudModel.heartbeatFrame(50.0F, 0.0F, 0));
        assertEquals(8, UrukRageHudModel.heartbeatFrame(50.0F, 3.0F, 0));
        assertEquals(8, UrukRageHudModel.heartbeatFrame(100.0F, 1.6F, 0));
        for (int flash = 1; flash <= 5; flash++) {
            assertEquals(0, UrukRageHudModel.heartbeatFrame(100.0F, 1.6F, flash));
            assertEquals(0, UrukRageHudModel.heartbeatFrame(0.0F, 1.6F, flash));
            assertEquals(1.0F, UrukRageHudModel.intensity(0.0F, 1.6F, flash), 0.0F);
        }
        for (int rage = 50; rage <= 100; rage++) {
            for (int step = 0; step < 300; step++) {
                int frame = UrukRageHudModel.heartbeatFrame(rage, step / 10.0F, 0);
                assertTrue(frame >= 0 && frame < UrukRageHudModel.HEARTBEAT_FRAMES);
                assertEquals(0.60F, UrukRageHudModel.intensity(rage, step / 10.0F, 0), 0.0F);
            }
        }
    }

    @Test
    public void confirmedHitFlashesTheSameVesselsAtFullOpacityForFiveTicks() {
        assertEquals(5, UrukRageHudModel.FLASH_TICKS);
        assertEquals(1.0F, UrukRageHudModel.flashBlend(5), 0.0F);
        assertEquals(1.0F, UrukRageHudModel.flashBlend(1), 0.0F);
        assertEquals(0.0F, UrukRageHudModel.flashBlend(0), 0.0F);
        assertEquals(1.0F, UrukRageHudModel.colorChannel(0.62F, 1.0F, 1.0F), 0.0F);
        assertEquals(0.87F, UrukRageHudModel.colorChannel(0.045F, 0.87F, 1.0F), 0.0F);
        assertEquals(0.85F, UrukRageHudModel.colorChannel(0.035F, 0.85F, 1.0F), 0.0F);
        assertEquals(0.045F, UrukRageHudModel.colorChannel(0.045F, 0.87F, 0.0F), 0.0F);

        assertEquals(1.0F, UrukRageHudModel.intensity(0.0F, 12.0F, 5), 0.0F);
        assertEquals(0.0F, UrukRageHudModel.thicknessSwelling(0.0F, 12.0F), 0.0F);
        assertEquals(1.0F, UrukRageHudModel.intensity(25.0F, 12.0F, 1), 0.0F);
        assertEquals(0.30F, UrukRageHudModel.intensity(25.0F, 12.0F, 0), 0.0F);
        assertEquals(0.0F, UrukRageHudModel.intensity(0.0F, 12.0F, 0), 0.0F);
    }

    @Test
    public void activationEventsAreConnectionScopedAndRejectDuplicatesAndStaleIds() {
        UrukHeavyBlowFeedback feedback = new UrukHeavyBlowFeedback();
        assertFalse(feedback.accept(1L, false, true));
        assertFalse(feedback.accept(1L, true, false));
        assertTrue(feedback.accept(4L, true, true));
        assertEquals(UrukRageHudModel.FLASH_TICKS, feedback.getFlashTicksRemaining());
        assertFalse(feedback.accept(4L, true, true));
        assertFalse(feedback.accept(3L, true, true));
        assertEquals(4L, feedback.getLastActivationId());
        feedback.tick(true);
        assertEquals(UrukRageHudModel.FLASH_TICKS - 1, feedback.getFlashTicksRemaining());
        for (int tick = 1; tick < UrukRageHudModel.FLASH_TICKS; tick++) {
            feedback.tick(true);
        }
        assertEquals(0, feedback.getFlashTicksRemaining());
        feedback.tick(true);
        assertEquals(0, feedback.getFlashTicksRemaining());
        feedback.clearTransient();
        assertEquals(0, feedback.getFlashTicksRemaining());
        assertFalse(feedback.accept(4L, true, true));
        feedback.resetConnection();
        assertEquals(0L, feedback.getLastActivationId());
        assertTrue(feedback.accept(1L, true, true));
        feedback.tick(false);
        assertEquals(0, feedback.getFlashTicksRemaining());
    }

    @Test
    public void activeCueCleanupStopsEveryTrackedInstanceButNoOtherSound() {
        UrukEmpoweredHitAudio audio = new UrukEmpoweredHitAudio();
        ISound first = PositionedSoundRecord.func_147673_a(ClientUrukRageState.EMPOWERED_HIT_SOUND);
        ISound second = PositionedSoundRecord.func_147673_a(ClientUrukRageState.EMPOWERED_HIT_SOUND);
        ISound normalOrcHurt = PositionedSoundRecord.func_147673_a(new ResourceLocation("lotr", "orc.hurt"));
        List<ISound> stopped = new ArrayList<>();
        audio.track(first);
        audio.track(second);
        audio.stopAll(stopped::add);
        assertEquals(2, stopped.size());
        assertTrue(stopped.get(0) == first);
        assertTrue(stopped.get(1) == second);
        assertFalse(stopped.contains(normalOrcHurt));
        assertFalse(audio.hasActive());
        audio.stopAll(stopped::add);
        assertEquals(2, stopped.size());
    }

    @Test
    public void finishedCuesArePrunedAndOnlyLongRecordingIsStoppedOnReset() throws Exception {
        UrukEmpoweredHitAudio audio = new UrukEmpoweredHitAudio();
        ISound finished = PositionedSoundRecord.func_147673_a(ClientUrukRageState.EMPOWERED_HIT_SOUND);
        ISound stillPlaying = PositionedSoundRecord.func_147673_a(ClientUrukRageState.EMPOWERED_HIT_SOUND);
        audio.track(finished);
        audio.track(stillPlaying);
        audio.forgetFinished(sound -> sound == stillPlaying);
        List<ISound> stopped = new ArrayList<>();
        audio.stopAll(stopped::add);
        assertEquals(1, stopped.size());
        assertTrue(stopped.get(0) == stillPlaying);
        String state = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/lotrcharactercreation/client/trait/ClientUrukRageState.java")),
            StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertTrue(state.contains("feedback.clearTransient();\n            stopActiveAudio(minecraft);"));
        assertTrue(state.contains("feedback.clearTransient();\n            stopActiveAudio(Minecraft.getMinecraft());"));
        assertTrue(state.contains("private void clearConnection() {\n        stopActiveAudio(Minecraft.getMinecraft());"));
        assertTrue(state.contains("audio.stopAll(minecraft.getSoundHandler()::stopSound)"));
    }

    @Test
    public void existingRageStateWireContractIsUnchangedAndActivationIsOneLong() {
        UrukRageStateMessage sentState = new UrukRageStateMessage(true, 63.25F);
        ByteBuf stateBuffer = Unpooled.buffer();
        sentState.toBytes(stateBuffer);
        assertEquals(5, stateBuffer.readableBytes());
        UrukRageStateMessage receivedState = new UrukRageStateMessage();
        receivedState.fromBytes(stateBuffer);
        assertTrue(receivedState.isActive());
        assertEquals(63.25F, receivedState.getRage(), 0.0F);
        assertEquals(0, stateBuffer.readableBytes());

        UrukHeavyBlowActivationMessage sentEvent = new UrukHeavyBlowActivationMessage(9876543210L);
        ByteBuf eventBuffer = Unpooled.buffer();
        sentEvent.toBytes(eventBuffer);
        assertEquals(8, eventBuffer.readableBytes());
        UrukHeavyBlowActivationMessage receivedEvent = new UrukHeavyBlowActivationMessage();
        receivedEvent.fromBytes(eventBuffer);
        assertEquals(9876543210L, receivedEvent.getActivationId());
        assertEquals(0, eventBuffer.readableBytes());
    }

    @Test
    public void serverConfirmationFollowsTheExistingSuccessfulConsumptionPath() throws Exception {
        String service = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/lotrcharactercreation/trait/UrukHaiTraitService.java")), StandardCharsets.UTF_8);
        String heavyBlow = service.substring(service.indexOf("private static void tryHeavyBlow"),
            service.indexOf("private static void gainRageFromDamage"));
        int damage = heavyBlow.indexOf("event.ammount += HEAVY_BLOW_DAMAGE_BONUS;");
        int knockback = heavyBlow.indexOf("PENDING_HEAVY_BLOWS.add(");
        int consumption = heavyBlow.indexOf("state.rage = UrukHeavyBlowRules.remainingRage(state.rage, HEAVY_BLOW_RAGE_COST);");
        int sync = heavyBlow.indexOf("synchronize(attacker, state, true);");
        int feedback = heavyBlow.indexOf("ModNetwork.sendUrukHeavyBlowActivation(");
        assertTrue(damage >= 0 && damage < knockback);
        assertTrue(knockback < consumption);
        assertTrue(consumption < sync);
        assertTrue(sync < feedback);
        assertEquals(feedback, heavyBlow.lastIndexOf("ModNetwork.sendUrukHeavyBlowActivation("));
        assertTrue(service.contains("HEAVY_BLOW_RAGE_COST = 50.0F"));
        assertTrue(service.contains("HEAVY_BLOW_DAMAGE_BONUS = 1.0F"));
        assertTrue(service.contains("HEAVY_BLOW_STRENGTH = 0.25F"));
        assertTrue(heavyBlow.contains("|| !(state.rage >= HEAVY_BLOW_RAGE_COST)"));
        assertTrue(heavyBlow.contains("getHeavyBlowChance(state.rage)"));
        assertTrue(service.contains("UrukHeavyBlowRules.chance(rage, HEAVY_BLOW_RAGE_COST, MAX_RAGE)"));
    }

    @Test
    public void vesselAtlasKeepsTheCenterTransparentAndUsesEveryScreenEdge() throws Exception {
        BufferedImage image = ImageIO.read(getClass().getResourceAsStream(
            "/assets/lotrcharactercreation/textures/gui/uruk_rage_vessels.png"));
        assertNotNull(image);
        assertEquals(256, image.getWidth());
        assertEquals(256, image.getHeight());
        boolean left = false;
        boolean right = false;
        boolean top = false;
        boolean bottom = false;
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                if (x >= 48 && x < 208 && y >= 48 && y < 208) {
                    assertEquals("center alpha at " + x + "," + y, 0, alpha);
                }
                if (alpha > 0) {
                    left |= x == 0;
                    right |= x == 255;
                    top |= y == 0;
                    bottom |= y == 255;
                }
            }
        }
        assertTrue(left && right && top && bottom);
    }

    @Test
    public void swellingFramesWidenActualStrokesAndPreserveCoreAndClearCenter() throws Exception {
        BufferedImage core = ImageIO.read(getClass().getResourceAsStream(
            "/assets/lotrcharactercreation/textures/gui/uruk_rage_vessels.png"));
        BufferedImage sheet = ImageIO.read(getClass().getResourceAsStream(
            "/assets/lotrcharactercreation/textures/gui/uruk_rage_vessels_swell.png"));
        assertNotNull(core);
        assertNotNull(sheet);
        assertEquals(1024, sheet.getWidth());
        assertEquals(1024, sheet.getHeight());
        int widenedPixels = 0;
        for (int y = 0; y < core.getHeight(); y++) {
            for (int x = 0; x < core.getWidth(); x++) {
                int coreAlpha = core.getRGB(x, y) >>> 24;
                assertEquals(core.getRGB(x, y), sheet.getRGB(x, y));
                int previousAlpha = coreAlpha;
                for (int frame = 1; frame < 16; frame++) {
                    int alpha = sheet.getRGB(x + (frame % 4) * 256, y + (frame / 4) * 256) >>> 24;
                    if (frame >= UrukRageHudModel.HEARTBEAT_FRAMES) {
                        assertEquals("unused tile", 0, alpha);
                        continue;
                    }
                    assertTrue("stroke coverage must grow monotonically", alpha >= previousAlpha);
                    previousAlpha = alpha;
                    if (coreAlpha >= 100) {
                        assertEquals("core opacity changed", coreAlpha, alpha);
                    }
                    if (x >= 48 && x < 208 && y >= 48 && y < 208) {
                        assertEquals("center must remain clear", 0, alpha);
                    }
                    if (alpha > coreAlpha) {
                        boolean neighbor = false;
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                int nx = x + dx;
                                int ny = y + dy;
                                neighbor |= nx >= 0 && nx < 256 && ny >= 0 && ny < 256
                                    && (core.getRGB(nx, ny) >>> 24) >= 100;
                            }
                        }
                        assertTrue("growth must touch an existing stroke", neighbor);
                    }
                    if (frame == 8 && coreAlpha < 100 && alpha >= 100) {
                        widenedPixels++;
                    }
                }
            }
        }
        assertTrue("peak must widen actual strokes, not just a faint halo", widenedPixels > 1000);
    }

    @Test
    public void nativeOrcFilesAreRegisteredWithoutMissingOptionalOverride() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/assets/lotrcharactercreation/sounds.json");
        assertNotNull(stream);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] chunk = new byte[256];
        int read;
        while ((read = stream.read(chunk)) >= 0) {
            output.write(chunk, 0, read);
        }
        stream.close();
        String json = new String(output.toByteArray(), StandardCharsets.UTF_8);
        JsonObject sounds = new JsonParser().parse(json).getAsJsonObject();
        JsonObject entry = sounds.getAsJsonObject("uruk.empowered_hit");
        assertNotNull(entry);
        SoundList registered = new SoundListSerializer().deserialize(entry, SoundList.class, null);
        assertEquals(SoundCategory.PLAYERS, registered.getSoundCategory());
        assertEquals(2, registered.getSoundList().size());
        for (int variant = 1; variant <= 2; variant++) {
            SoundList.SoundEntry file = (SoundList.SoundEntry)registered.getSoundList().get(variant - 1);
            assertEquals(SoundList.SoundEntry.Type.FILE, file.getSoundEntryType());
            ResourceLocation name = new ResourceLocation(file.getSoundEntryName());
            assertEquals("lotr", name.getResourceDomain());
            assertEquals("orc/hurt" + variant, name.getResourcePath());
            ResourceLocation resolved = new ResourceLocation(name.getResourceDomain(),
                "sounds/" + name.getResourcePath() + ".ogg");
            assertEquals("lotr:sounds/orc/hurt" + variant + ".ogg", resolved.toString());
        }
        assertEquals(1, sounds.entrySet().size());
        assertEquals("lotrcharactercreation", ClientUrukRageState.EMPOWERED_HIT_SOUND.getResourceDomain());
        assertEquals("uruk.empowered_hit", ClientUrukRageState.EMPOWERED_HIT_SOUND.getResourcePath());
        assertEquals(null, getClass().getResource(
            "/assets/lotrcharactercreation/sounds/uruk/empowered_hit.ogg"));
    }

    @Test
    public void resourcePackCanReplaceOnlyTheKomeEventAtTheEstablishedOggPath() {
        JsonObject packSounds = new JsonParser().parse("{\"uruk.empowered_hit\":{\"replace\":true,"
            + "\"category\":\"player\",\"sounds\":[{\"name\":\"lotrcharactercreation:uruk/empowered_hit\"}]}}")
            .getAsJsonObject();
        SoundList replacement = new SoundListSerializer().deserialize(
            packSounds.getAsJsonObject("uruk.empowered_hit"), SoundList.class, null);
        assertTrue(replacement.canReplaceExisting());
        assertEquals(SoundCategory.PLAYERS, replacement.getSoundCategory());
        assertEquals(1, replacement.getSoundList().size());
        SoundList.SoundEntry file = (SoundList.SoundEntry)replacement.getSoundList().get(0);
        assertEquals(SoundList.SoundEntry.Type.FILE, file.getSoundEntryType());
        assertEquals("lotrcharactercreation:uruk/empowered_hit", file.getSoundEntryName());
        assertFalse(packSounds.has("orc.hurt"));
    }

    @Test
    public void installedLotrModProvidesAndDecodesBothOrcHurtVariants() throws Exception {
        File lotrJar = Paths.get("libs", "LOTRMod v36.15.jar").toFile();
        assertTrue(lotrJar.isFile());
        ZipFile zip = new ZipFile(lotrJar);
        try {
            JsonObject nativeSounds = new JsonParser().parse(new InputStreamReader(
                zip.getInputStream(zip.getEntry("assets/lotr/sounds.json")), StandardCharsets.UTF_8))
                .getAsJsonObject();
            JsonObject hurt = nativeSounds.getAsJsonObject("orc.hurt");
            assertNotNull(hurt);
            assertEquals(2, hurt.getAsJsonArray("sounds").size());
            for (int variant = 1; variant <= 2; variant++) {
                String name = "orc/hurt" + variant;
                assertEquals(name, hurt.getAsJsonArray("sounds").get(variant - 1).getAsString());
                String path = "assets/lotr/sounds/" + name + ".ogg";
                assertNotNull(zip.getEntry(path));
                CodecJOrbis codec = new CodecJOrbis();
                try {
                    assertTrue("native variant must decode: " + path,
                        codec.initialize(new java.net.URL("jar:" + lotrJar.toURI().toURL() + "!/" + path)));
                    assertNotNull(codec.readAll());
                } finally {
                    codec.cleanup();
                }
            }
        } finally {
            zip.close();
        }
        ISound cue = PositionedSoundRecord.func_147673_a(ClientUrukRageState.EMPOWERED_HIT_SOUND);
        assertEquals(1.0F, cue.getVolume(), 0.0F);
        assertEquals(1.0F, cue.getPitch(), 0.0F);
        assertEquals(ISound.AttenuationType.NONE, cue.getAttenuationType());
        assertFalse(cue.canRepeat());
    }
}
