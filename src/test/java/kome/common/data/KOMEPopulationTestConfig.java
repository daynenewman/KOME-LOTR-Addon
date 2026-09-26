package kome.common.data;

import cpw.mods.fml.relauncher.FMLInjectionData;
import kome.common.config.KOMEConfigRegistry;
import net.minecraftforge.common.config.Configuration;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;

/** Real typed-config fixture; restores the prior registry snapshot after each test. */
public final class KOMEPopulationTestConfig implements AutoCloseable {
    private final KOMEConfigRegistry.ValidatedConfig previous = KOMEConfigRegistry.currentValidated();
    private final File file;
    public KOMEPopulationTestConfig() throws Exception {
        Field home = FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true); home.set(null, new File(".").getAbsoluteFile());
        file = Files.createTempFile("kome-payout-test", ".cfg").toFile();
        KOMEConfigRegistry.onServerStop();
        KOMEConfigRegistry.load(file);
    }
    public void set(String... pairs) {
        Configuration config = new Configuration(file); config.load();
        for (int i = 0; i < pairs.length; i += 2) {
            int dot = pairs[i].indexOf('.');
            config.get(pairs[i].substring(0, dot), pairs[i].substring(dot + 1), pairs[i + 1]).set(pairs[i + 1]);
        }
        config.save();
        KOMEConfigRegistry.load(file);
    }
    /** Exercises the real Build/config/rate path, not the retired saturated adapter. */
    public static java.math.BigInteger rateFromApprovedCentiHours(long amount, long configuredHours) throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            config.set("population.hoursPerPopulationPoint", java.math.BigDecimal.valueOf(configuredHours, 2).toPlainString());
            KOMEWorldData data = new KOMEWorldData("rate");
            KOMEConquestTile tile = new KOMEConquestTile("T100");
            tile.claim("gondor", 0L); data.conquestTiles.put(tile.id, tile);
            KOMEPlayerBuild build = new KOMEPlayerBuild();
            build.id = "B"; build.type = KOMEBuildType.NORMAL; build.tileId = tile.id;
            build.populationFaction = "gondor"; build.active = true;
            KOMEBuildContribution contribution = new KOMEBuildContribution();
            contribution.id = "H"; contribution.centiHours = amount; contribution.status = KOMEBuildContribution.APPROVED;
            build.contributions.add(contribution); build.developedNativeCentiHours = amount;
            data.builds.put(build.id, build);
            return KOMEPopulationRateService.getExactDailyPopulationRates(data, KOMEConfigRegistry.population()).get("gondor");
        }
    }

    @Override public void close() throws Exception {
        KOMEConfigRegistry.onServerStop();
        Field current = KOMEConfigRegistry.class.getDeclaredField("current");
        current.setAccessible(true); current.set(null, previous);
        Files.deleteIfExists(file.toPath());
    }
}
