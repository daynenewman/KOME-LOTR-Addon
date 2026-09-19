package com.enovak.lotrmoremobs.config;

import cpw.mods.fml.relauncher.FMLInjectionData;
import net.minecraftforge.common.config.Configuration;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;

public class MumakilConfigTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass public static void initializeForgeConfigurationBasePath() throws Exception {
        Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, new File(".").getAbsoluteFile());
    }

    @Test public void baselineRamDamageDefaultsToTwenty() throws Exception {
        MumakilConfig.load(configFile());
        assertEquals(20, MumakilConfig.ramSiegeDamage);
    }

    @Test public void baselineSiegeGateHealthDefaultsToTwoHundred() throws Exception {
        MumakilConfig.load(configFile());
        assertEquals(200, MumakilConfig.defaultGateHealth);
    }

    @Test public void baselineRamDamageRemainsConfigurable() throws Exception {
        File file = configFile();
        Configuration configuration = new Configuration(file);
        configuration.load();
        configuration.get(MumakilConfig.CATEGORY_BATTLE_RAMS,
            "ramDamagePerImpact", 20).set(37);
        configuration.save();

        MumakilConfig.load(file);
        assertEquals(37, MumakilConfig.ramSiegeDamage);
    }

    @Test public void explicitExistingGateHealthIsNotOverwrittenByNewDefault() throws Exception {
        File file = configFile();
        Configuration configuration = new Configuration(file);
        configuration.load();
        configuration.get(MumakilConfig.CATEGORY_SIEGE_GATES,
            "defaultGateHealth", 200).set(1000);
        configuration.save();

        MumakilConfig.load(file);
        assertEquals(1000, MumakilConfig.defaultGateHealth);
    }

    private File configFile() throws Exception {
        return new File(temporaryFolder.newFolder(), "lotrmoremobs.cfg");
    }
}
