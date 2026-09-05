package kome.integration;

import kome.core.KOMECorePlugin;
import kome.core.KOMEWaypointTransformer;
import net.minecraft.entity.player.EntityPlayer;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CharacterCreationIsolationTest {

    private static final Pattern MOD_ANNOTATION = Pattern.compile("(?m)^\\s*@Mod\\s*\\(");

    @Test
    public void komeRemainsTheOnlyForgeModContainer() throws Exception {
        Path mainJava = addon().resolve("src/main/java");
        List<Path> modContainers = new ArrayList<>();

        for (Path source : javaSources(mainJava)) {
            Matcher matcher = MOD_ANNOTATION.matcher(read(source));
            if (matcher.find()) {
                modContainers.add(mainJava.relativize(source));
            }
        }

        assertEquals(Arrays.asList(Paths.get("kome/common/KOMEAddon.java")), modContainers);
    }

    @Test
    public void characterCreationCoordinatorIsDormantAndHasNoInjectedLifecycle() throws Exception {
        Path mainJava = addon().resolve("src/main/java");
        String coordinator = read(mainJava.resolve("com/lotrcharactercreation/LOTRCharacterCreation.java"));

        assertFalse(coordinator.contains("@Mod("));
        assertFalse(coordinator.contains("@Mod.EventHandler"));
        assertFalse(coordinator.contains("@SidedProxy"));
        assertTrue(coordinator.contains("public static CommonProxy proxy;"));
        assertTrue(coordinator.contains(
            "new File(event.getModConfigurationDirectory(), \"lotrcharactercreation.cfg\")"));

        String komeLifecycle = read(mainJava.resolve("kome/common/KOMEAddon.java"))
            + read(mainJava.resolve("kome/common/KOMECommonProxy.java"))
            + read(mainJava.resolve("kome/client/KOMEClientProxy.java"));
        assertFalse(komeLifecycle.contains("LOTRCharacterCreation"));
        assertFalse(komeLifecycle.contains("ModNetwork.initialize()"));
        assertFalse(komeLifecycle.contains("com.lotrcharactercreation"));
    }

    @Test
    public void legacyPersistenceNetworkAndFilesystemIdentifiersRemainExact() throws Exception {
        Path characterCreation = addon().resolve("src/main/java/com/lotrcharactercreation");
        String playerData = read(characterCreation.resolve("race/PlayerRaceData.java"));

        assertEquals("PlayerPersisted", EntityPlayer.PERSISTED_NBT_TAG);
        assertTrue(playerData.contains("EntityPlayer.PERSISTED_NBT_TAG"));
        for (String identifier : new String[] {
            "lotrcharactercreation",
            "race",
            "sex",
            "appearancePreset",
            "appearanceInitialized",
            "raceSelectionComplete",
            "startingFaction",
            "factionSelectionComplete",
            "startingFactionApplied",
            "startingWaypoint",
            "startingWaypointApplied",
            "characterCreationComplete",
            "dwarfStamina",
            "dwarfFeast",
            "dwarfStaminaExhausted",
            "elfGrappleCooldownTicks",
            "elfGrappleCleanupPending"
        }) {
            assertTrue("Missing legacy player-data identifier: " + identifier,
                playerData.contains("\"" + identifier + "\""));
        }

        String network = read(characterCreation.resolve("network/ModNetwork.java"));
        assertTrue(network.contains("newSimpleChannel(\"lotrcreation\")"));

        String coordinator = read(characterCreation.resolve("LOTRCharacterCreation.java"));
        assertTrue(coordinator.contains("\"lotrcharactercreation.cfg\""));
        assertTrue(coordinator.contains("\"lotrcharactercreation\""));
        assertTrue(coordinator.contains("\"custom_skins\""));

        String raceTraits = read(characterCreation.resolve("trait/CommonRaceTraitEventHandler.java"));
        assertTrue(raceTraits.contains("\"lotrcharactercreationRacialHealthLoad\""));

        String hobbitThrowable = read(characterCreation.resolve("trait/HobbitThrowableService.java"));
        assertTrue(hobbitThrowable.contains("\"lotrcharactercreationHobbitChargedProjectile\""));
        assertTrue(hobbitThrowable.contains("\"lotrcharactercreationHobbitChargedDamage\""));
    }

    @Test
    public void characterCreationAccessTransformerIsExact() throws Exception {
        Path accessTransformer = addon().resolve("src/main/resources/META-INF/lotrcharactercreation_at.cfg");
        assertTrue("Missing Character Creation access transformer", Files.isRegularFile(accessTransformer));
        assertEquals(Arrays.asList(
            "public net.minecraft.entity.Entity func_70105_a(FF)V",
            "public net.minecraft.entity.EntityAgeable func_70105_a(FF)V",
            "public net.minecraft.entity.monster.EntityZombie func_70105_a(FF)V"),
            Files.readAllLines(accessTransformer, StandardCharsets.UTF_8));
    }

    @Test
    public void gradleConfiguresTheCharacterCreationAccessTransformer() throws Exception {
        String properties = read(addon().resolve("gradle.properties"));
        assertTrue(Pattern.compile(
            "(?m)^accessTransformersFile\\s*=\\s*lotrcharactercreation_at\\.cfg\\s*$")
            .matcher(properties)
            .find());
    }

    @Test
    public void komeCorePluginStillExposesOnlyTheWaypointTransformer() {
        KOMECorePlugin plugin = new KOMECorePlugin();

        assertArrayEquals(
            new String[] { KOMEWaypointTransformer.class.getName() },
            plugin.getASMTransformerClass());
        assertNull(plugin.getAccessTransformerClass());
    }

    private static Path addon() {
        return Paths.get("").toAbsolutePath().normalize();
    }

    private static List<Path> javaSources(Path root) throws IOException {
        final List<Path> sources = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (file.getFileName().toString().endsWith(".java")) {
                    sources.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return sources;
    }

    private static String read(Path path) throws IOException {
        assertTrue("Missing repository file: " + path, Files.isRegularFile(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
