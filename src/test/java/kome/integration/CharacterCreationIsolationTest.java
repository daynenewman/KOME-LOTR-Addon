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
    public void characterCreationLifecycleIsExplicitlyOwnedByKome() throws Exception {
        Path mainJava = addon().resolve("src/main/java");
        String coordinator = read(mainJava.resolve("com/lotrcharactercreation/LOTRCharacterCreation.java"));

        assertFalse(coordinator.contains("@Mod("));
        assertFalse(coordinator.contains("@Mod.EventHandler"));
        assertFalse(coordinator.contains("@SidedProxy"));
        assertTrue(coordinator.contains("public static CommonProxy proxy;"));
        assertTrue(coordinator.contains("public void commonPreInitialize(FMLPreInitializationEvent event)"));
        assertTrue(coordinator.contains("public void initializeSidedProxy()"));
        assertTrue(coordinator.contains("public void registerServerCommands(FMLServerStartingEvent event)"));
        assertTrue(coordinator.contains(
            "new File(event.getModConfigurationDirectory(), \"lotrcharactercreation.cfg\")"));
        assertEquals(1, occurrences(coordinator, "ModNetwork.initialize();"));
        assertEquals(1, occurrences(coordinator, "MinecraftForge.EVENT_BUS.register(raceTraitEventHandler);"));
        assertEquals(1, occurrences(coordinator, "MinecraftForge.EVENT_BUS.register(racialPlayerSoundHandler);"));

        String komeAddon = read(mainJava.resolve("kome/common/KOMEAddon.java"));
        assertTrue(komeAddon.contains("public void preInit(FMLPreInitializationEvent event)"));
        assertEquals(1, occurrences(komeAddon, "characterCreation.commonPreInitialize(event);"));
        assertEquals(1, occurrences(komeAddon, "characterCreation.initializeSidedProxy();"));
        assertEquals(1, occurrences(komeAddon, "characterCreation.registerServerCommands(event);"));
    }

    @Test
    public void characterCreationProxyCompositionPreservesTheSidedBoundary() throws Exception {
        Path mainJava = addon().resolve("src/main/java");
        String commonProxy = read(mainJava.resolve("kome/common/KOMECommonProxy.java"));
        String clientProxy = read(mainJava.resolve("kome/client/KOMEClientProxy.java"));
        String coordinator = read(mainJava.resolve("com/lotrcharactercreation/LOTRCharacterCreation.java"));

        assertTrue(commonProxy.contains("private final CommonProxy characterCreationProxy;"));
        assertTrue(commonProxy.contains("this(new CommonProxy());"));
        assertEquals(1, occurrences(commonProxy, "LOTRCharacterCreation.proxy = characterCreationProxy;"));
        assertEquals(1, occurrences(clientProxy, "super(new ClientProxy());"));
        assertEquals(1, occurrences(coordinator, "proxy.initialize(customSkinRoot);"));

        List<Path> clientProxyReferences = new ArrayList<>();
        for (Path source : javaSources(mainJava)) {
            if (read(source).contains("com.lotrcharactercreation.proxy.ClientProxy")) {
                clientProxyReferences.add(mainJava.relativize(source));
            }
        }
        assertEquals(Arrays.asList(Paths.get("kome/client/KOMEClientProxy.java")), clientProxyReferences);
    }

    @Test
    public void komeMapInitializationPrecedesCharacterCreationClientInitialization() throws Exception {
        Path mainJava = addon().resolve("src/main/java");
        String komeAddon = read(mainJava.resolve("kome/common/KOMEAddon.java"));
        String komeClientProxy = read(mainJava.resolve("kome/client/KOMEClientProxy.java"));
        String characterClientProxy = read(mainJava.resolve("com/lotrcharactercreation/proxy/ClientProxy.java"));

        assertBefore(komeAddon, "proxy.init();", "characterCreation.initializeSidedProxy();");
        assertTrue(komeClientProxy.contains("new KOMEWaypointMapOverlay()"));
        assertTrue(komeClientProxy.contains("new KOMEConquestMapOverlay()"));
        assertTrue(characterClientProxy.contains("new LOTRMapPlayerAppearanceHandler()"));
    }

    @Test
    public void komeAndCharacterCreationKeepSeparateNetworkChannels() throws Exception {
        Path mainJava = addon().resolve("src/main/java");
        String characterNetwork = read(mainJava.resolve("com/lotrcharactercreation/network/ModNetwork.java"));
        String komeNetwork = read(mainJava.resolve("kome/common/network/KOMEPacketHandler.java"));
        String komeAddon = read(mainJava.resolve("kome/common/KOMEAddon.java"));

        assertTrue(characterNetwork.contains("newSimpleChannel(\"lotrcreation\")"));
        assertTrue(komeNetwork.contains("newSimpleChannel(KOMEAddon.MODID)"));
        assertTrue(komeAddon.contains("public static final String MODID = \"kome\";"));
    }

    @Test
    public void characterCreationCommandsRetainTheirNamesAndSingleRegistrationPath() throws Exception {
        Path characterCreation = addon().resolve("src/main/java/com/lotrcharactercreation");
        String coordinator = read(characterCreation.resolve("LOTRCharacterCreation.java"));

        assertEquals(1, occurrences(coordinator, "event.registerServerCommand(new CommandCharacter());"));
        assertEquals(1, occurrences(coordinator, "event.registerServerCommand(new CommandLotrCreation());"));
        assertEquals(1, occurrences(coordinator, "event.registerServerCommand(new CommandLotrRace());"));
        assertTrue(read(characterCreation.resolve("command/CommandCharacter.java")).contains("return \"character\";"));
        assertTrue(read(characterCreation.resolve("command/CommandLotrCreation.java")).contains("return \"lotrcreation\";"));
        assertTrue(read(characterCreation.resolve("command/CommandLotrRace.java")).contains("return \"lotrrace\";"));

        String clientProxy = read(characterCreation.resolve("proxy/ClientProxy.java"));
        assertEquals(1, occurrences(clientProxy, "ClientCommandHandler.instance.registerCommand(manSkinReviewCommand);"));
    }

    @Test
    public void commonBootstrapSourcesRemainClientClassFree() throws Exception {
        Path mainJava = addon().resolve("src/main/java");
        for (String relativePath : new String[] {
            "kome/common/KOMEAddon.java",
            "kome/common/KOMECommonProxy.java",
            "com/lotrcharactercreation/LOTRCharacterCreation.java",
            "com/lotrcharactercreation/proxy/CommonProxy.java"
        }) {
            String source = read(mainJava.resolve(relativePath));
            assertFalse(relativePath, source.contains("com.lotrcharactercreation.proxy.ClientProxy"));
            assertFalse(relativePath, source.contains("net.minecraft.client."));
            assertFalse(relativePath, source.contains("lotr.client."));
            assertFalse(relativePath, source.contains("org.lwjgl."));
        }
    }

    @Test
    public void legacyPersistenceNetworkAndFilesystemIdentifiersRemainExact() throws Exception {
        Path characterCreation = addon().resolve("src/main/java/com/lotrcharactercreation");
        String playerData = read(characterCreation.resolve("race/PlayerRaceData.java"));

        assertEquals("PlayerPersisted", EntityPlayer.PERSISTED_NBT_TAG);
        assertTrue(playerData.contains("private static final String MOD_DATA_TAG = \"lotrcharactercreation\";"));
        assertTrue(playerData.contains("EntityPlayer.PERSISTED_NBT_TAG"));
        assertTrue(playerData.contains("entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)"));
        assertTrue(playerData.contains("persistedData.getCompoundTag(MOD_DATA_TAG)"));
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
        assertTrue(Pattern.compile(
            "new\\s+File\\s*\\(\\s*new\\s+File\\s*\\(\\s*event\\.getModConfigurationDirectory\\(\\),"
                + "\\s*\"lotrcharactercreation\"\\s*\\),\\s*\"custom_skins\"\\s*\\)")
            .matcher(coordinator)
            .find());

        String raceTraits = read(characterCreation.resolve("trait/CommonRaceTraitEventHandler.java"));
        assertTrue(raceTraits.contains("\"lotrcharactercreationRacialHealthLoad\""));

        String hobbitThrowable = read(characterCreation.resolve("trait/HobbitThrowableService.java"));
        assertTrue(hobbitThrowable.contains("\"lotrcharactercreationHobbitChargedProjectile\""));
        assertTrue(hobbitThrowable.contains("\"lotrcharactercreationHobbitChargedDamage\""));
    }

    @Test
    public void komeSourcesDoNotOwnCharacterCreationConfigOrPlayerStorage() throws Exception {
        Path komeSources = addon().resolve("src/main/java/kome");
        for (Path source : javaSources(komeSources)) {
            String relativePath = komeSources.relativize(source).toString();
            String text = read(source);

            assertFalse(relativePath, text.contains("\"lotrcharactercreation.cfg\""));
            assertFalse(relativePath, text.contains("\"automaticStartingAllegiance\""));
            assertFalse(relativePath, text.contains("com.lotrcharactercreation.config.ModConfiguration"));
            assertFalse(relativePath, Pattern.compile("\\bgetEntityData\\s*\\(").matcher(text).find());
            assertFalse(relativePath, text.contains("EntityPlayer.PERSISTED_NBT_TAG"));
            assertFalse(relativePath, text.contains("\"ForgeData\""));
            assertFalse(relativePath, text.contains("\"PlayerPersisted\""));
            assertFalse(relativePath, text.contains("\"lotrcharactercreation\""));
        }
    }

    @Test
    public void komePledgeObserverStillReadsAndObservesTheActualLotrPledge() throws Exception {
        String events = read(addon().resolve("src/main/java/kome/common/data/KOMEEvents.java"));
        String login = between(events, "public void onPlayerLogin", "public void onPlayerLogout");
        String tick = between(events, "public void onPlayerTick", "public void onServerTick");
        String actualPledge = between(events, "private String getActualPledgeFactionKey", "private boolean factionMatches");

        assertEquals(2, occurrences(events, "KOMEPledgeReleaseService.observePledge("));
        assertTrue(login.contains("KOMEPledgeReleaseService.observePledge("));
        assertTrue(login.contains("getActualPledgeFactionKey(event.player)"));
        assertTrue(tick.contains("TickEvent.Phase.END"));
        assertTrue(tick.contains("% 20L == 0L"));
        assertTrue(tick.contains("KOMEPledgeReleaseService.observePledge("));
        assertTrue(tick.contains("getActualPledgeFactionKey(event.player)"));
        assertTrue(actualPledge.contains("LOTRLevelData.getData(player).getPledgeFaction()"));
        assertTrue(actualPledge.contains("KOMEAlliance.normalizeFactionKey(pledge.codeName())"));
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

    private static int occurrences(String text, String value) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }

    private static void assertBefore(String text, String first, String second) {
        int firstIndex = text.indexOf(first);
        int secondIndex = text.indexOf(second);
        assertTrue("Missing expected first value: " + first, firstIndex >= 0);
        assertTrue("Missing expected second value: " + second, secondIndex >= 0);
        assertTrue(first + " must precede " + second, firstIndex < secondIndex);
    }

    private static String between(String text, String start, String end) {
        int startIndex = text.indexOf(start);
        int endIndex = text.indexOf(end, startIndex);
        assertTrue("Missing expected start value: " + start, startIndex >= 0);
        assertTrue("Missing expected end value: " + end, endIndex >= 0);
        return text.substring(startIndex, endIndex);
    }

    private static String read(Path path) throws IOException {
        assertTrue("Missing repository file: " + path, Files.isRegularFile(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
