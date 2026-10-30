package kome.client;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import kome.common.KOMEAccessFixture;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import net.minecraftforge.common.config.Configuration;
import static org.junit.Assert.*;

public class KOMEMapPreferencesTest {
    @Test public void tooltipPreferenceDefaultsDelayedAndPersists() {
        KOMEClientConfig config=new KOMEClientConfig(file);
        assertEquals(KOMEClientConfig.MapTooltipMode.DELAYED,config.mapTooltipMode());
        config.setMapTooltipMode(KOMEClientConfig.MapTooltipMode.OFF);
        assertEquals(KOMEClientConfig.MapTooltipMode.OFF,new KOMEClientConfig(file).mapTooltipMode());
        config.setMapTooltipMode(KOMEClientConfig.MapTooltipMode.IMMEDIATE);
        assertEquals(KOMEClientConfig.MapTooltipMode.IMMEDIATE,new KOMEClientConfig(file).mapTooltipMode());
    }
    @Test public void threeCompanyModesMigrateLegacyChoiceAndSurviveRestart() throws Exception {
        Configuration legacy=new Configuration(file);
        legacy.get("map","troopMarkers",false).set(false);legacy.save();
        KOMEClientConfig config=new KOMEClientConfig(file);
        assertEquals(KOMEMapPreferences.CompanyDisplay.HIDE_COMPANIES,config.mapPreferences().companyDisplay);
        KOMEMapPreferences descriptors=config.mapPreferences().withCompanyDisplay(KOMEMapPreferences.CompanyDisplay.SHIELDS_AND_DESCRIPTORS);
        config.setMapPreferences(descriptors);
        assertEquals(descriptors,new KOMEClientConfig(file).mapPreferences());
        assertTrue(descriptors.troops);
        assertEquals(KOMEMapPreferences.CompanyDisplay.HIDE_COMPANIES,descriptors.companyDisplay.next());
        assertEquals(3,KOMEMapControlRenderer.spriteRow(7,true,true,new KOMETerritoryStyle(),descriptors.companyDisplay));
        assertEquals(2,KOMEMapControlRenderer.spriteRow(7,true,false,new KOMETerritoryStyle(),descriptors.companyDisplay));
    }
    @Test public void existingNewModeWinsAndAbsentLegacyDefaultsToShieldsOnly() {
        assertEquals(KOMEMapPreferences.CompanyDisplay.SHIELDS_ONLY,new KOMEClientConfig(file).mapPreferences().companyDisplay);
        Configuration raw=new Configuration(file);raw.get("map","troopMarkers",true).set(false);
        raw.get("map","companyDisplayMode","SHIELDS_ONLY").set("SHIELDS_AND_DESCRIPTORS");raw.save();
        KOMEMapPreferences restored=new KOMEClientConfig(file).mapPreferences();
        assertEquals(KOMEMapPreferences.CompanyDisplay.SHIELDS_AND_DESCRIPTORS,restored.companyDisplay);
        assertTrue(restored.troops);
        assertEquals(KOMEMapPreferences.CompanyDisplay.SHIELDS_ONLY,
            KOMEMapPreferences.CompanyDisplay.HIDE_COMPANIES.next());
        assertEquals(KOMEMapPreferences.CompanyDisplay.SHIELDS_AND_DESCRIPTORS,
            KOMEMapPreferences.CompanyDisplay.SHIELDS_ONLY.next());
    }
    @Test public void descriptorStateUsesExistingShieldAndMovementGates() {
        KOMEConquestMapOverlay.applyMapPreferences(new KOMEMapPreferences().withCompanyDisplay(
            KOMEMapPreferences.CompanyDisplay.SHIELDS_AND_DESCRIPTORS));
        assertTrue(KOMEConquestMapOverlay.companyMovementVisualsVisible());
        assertTrue(KOMEConquestMapOverlay.mapPreferences().troops);
        KOMEConquestMapOverlay.applyMapPreferences(new KOMEMapPreferences().withCompanyDisplay(
            KOMEMapPreferences.CompanyDisplay.HIDE_COMPANIES));
        assertFalse(KOMEConquestMapOverlay.companyMovementVisualsVisible());
        assertFalse(KOMEConquestMapOverlay.mapPreferences().troops);
    }
    @Rule public final TemporaryFolder temporary=new TemporaryFolder();
    private Object home,instance;private KOMEMapPreferences active;private File file;
    @Before public void setup()throws Exception {
        KOMEAccessFixture.allocate(lotr.client.gui.LOTRGuiMap.class);
        Field root=field(cpw.mods.fml.relauncher.FMLInjectionData.class,"minecraftHome");home=root.get(null);root.set(null,temporary.getRoot());
        instance=field(KOMEClientConfig.class,"instance").get(null);active=KOMEConquestMapOverlay.mapPreferences();file=new File(temporary.getRoot(),"kome-client.cfg");
    }
    @After public void cleanup()throws Exception {
        KOMEConquestMapOverlay.applyMapPreferences(active);field(KOMEClientConfig.class,"instance").set(null,instance);
        field(cpw.mods.fml.relauncher.FMLInjectionData.class,"minecraftHome").set(null,home);
    }
    @Test public void defaultsAndEveryAppearanceAndLayerValueSurviveRestartWithoutChangingUnrelatedValues() {
        Configuration original=new Configuration(file);original.get("hud","showCurrentTile",true).set(false);
        original.get("factions","useRankTitles",true).set(false);original.get("other","untouched","value").set("keep me");original.save();
        KOMEClientConfig config=new KOMEClientConfig(file);assertEquals(new KOMEMapPreferences(),config.mapPreferences());
        KOMEMapPreferences selected=new KOMEMapPreferences(KOMETerritoryStyle.Palette.CURRENT_KOME,KOMETerritoryStyle.DisplayMode.BORDERS_ONLY,
            KOMETerritoryStyle.Opacity.THREE_QUARTERS,true,false,false,false,false,false);
        config.setMapPreferences(selected);KOMEClientConfig restarted=new KOMEClientConfig(file);
        assertEquals(selected,restarted.mapPreferences());assertFalse(restarted.showCurrentTile());assertFalse(restarted.useRankTitles());
        assertEquals("keep me",new Configuration(file).get("other","untouched","").getString());
        KOMEConquestMapOverlay.applyMapPreferences(restarted.mapPreferences());assertEquals(selected,KOMEConquestMapOverlay.mapPreferences());
        for(KOMETerritoryStyle.Opacity opacity:KOMETerritoryStyle.Opacity.values()) {
            selected=new KOMEMapPreferences(KOMETerritoryStyle.Palette.CLASSIC_LOTR,KOMETerritoryStyle.DisplayMode.FULL_COLOR,opacity,false,true,true,true,true);
            restarted.setMapPreferences(selected);restarted=new KOMEClientConfig(file);assertEquals(selected,restarted.mapPreferences());
        }
    }
    @Test public void malformedValuesFallBackToAcceptedDefaultsAndAreNormalizedOnce()throws Exception {
        Configuration invalid=new Configuration(file);invalid.get("map","factionPalette","garbage");invalid.get("map","territoryMode","unknown");
        invalid.get("map","territoryOpacity",17);invalid.get("map","tileBorders","perhaps");invalid.get("map","conquestLayer","nonsense");invalid.save();
        KOMEClientConfig config=new KOMEClientConfig(file);assertEquals(new KOMEMapPreferences(),config.mapPreferences());
        byte[] bytes=Files.readAllBytes(file.toPath());new KOMEClientConfig(file);assertArrayEquals(bytes,Files.readAllBytes(file.toPath()));
        invalid=new Configuration(file);invalid.get("map","territoryOpacity",50).set("not a number");invalid.save();
        assertEquals(KOMETerritoryStyle.Opacity.HALF,new KOMEClientConfig(file).mapPreferences().opacity);
        invalid=new Configuration(file);invalid.getCategory("map").put("factionPalette",new net.minecraftforge.common.config.Property("factionPalette",new String[]{"CURRENT_KOME"},net.minecraftforge.common.config.Property.Type.STRING));
        invalid.save();config=new KOMEClientConfig(file);assertEquals(KOMETerritoryStyle.Palette.CLASSIC_LOTR,config.mapPreferences().palette);
        KOMEMapPreferences selected=new KOMEMapPreferences(KOMETerritoryStyle.Palette.CURRENT_KOME,KOMETerritoryStyle.DisplayMode.FULL_COLOR,KOMETerritoryStyle.Opacity.HALF,false,true,true,true,true);
        config.setMapPreferences(selected);assertEquals(selected,new KOMEClientConfig(file).mapPreferences());
    }
    @Test public void unchangedPreferenceReadsAndAssignmentsDoNotWriteToDisk()throws Exception {
        KOMEClientConfig config=new KOMEClientConfig(file);assertTrue(file.setLastModified(123456000));
        byte[] bytes=Files.readAllBytes(file.toPath());for(int i=0;i<50;i++)config.setMapPreferences(config.mapPreferences());
        assertEquals(123456000,file.lastModified());assertArrayEquals(bytes,Files.readAllBytes(file.toPath()));
    }
    @Test public void buildPreferenceDefaultsForOldConfigsAndRepairsMalformedScalarsAndLists()throws Exception {
        KOMEClientConfig config=new KOMEClientConfig(file);assertTrue(config.mapPreferences().builds);
        Configuration raw=new Configuration(file);raw.getCategory("map").remove("buildMarkers");raw.save();
        assertTrue(new KOMEClientConfig(file).mapPreferences().builds);
        raw=new Configuration(file);raw.get("map","buildMarkers",true).set("invalid");raw.save();
        assertTrue(new KOMEClientConfig(file).mapPreferences().builds);
        raw=new Configuration(file);raw.getCategory("map").put("buildMarkers",new net.minecraftforge.common.config.Property("buildMarkers",new String[]{"false"},net.minecraftforge.common.config.Property.Type.BOOLEAN));raw.save();
        config=new KOMEClientConfig(file);assertTrue(config.mapPreferences().builds);
        KOMEMapPreferences hidden=new KOMEMapPreferences(KOMETerritoryStyle.Palette.CLASSIC_LOTR,KOMETerritoryStyle.DisplayMode.FULL_COLOR,
            KOMETerritoryStyle.Opacity.HALF,false,true,true,true,true,false);
        config.setMapPreferences(hidden);config=new KOMEClientConfig(file);assertEquals(hidden,config.mapPreferences());
        assertFalse(config.mapPreferences().withConquest(false).builds);
        byte[] bytes=Files.readAllBytes(file.toPath());config.setMapPreferences(config.mapPreferences());
        assertArrayEquals(bytes,Files.readAllBytes(file.toPath()));
    }
    @Test public void restoringSettingsInvalidatesPresentationCachesButKeepsOwnershipProjection()throws Exception {
        KOMEClientConfig config=new KOMEClientConfig(file);
        KOMEMapBorders borders=new KOMEMapBorders.Cache().get(kome.common.data.KOMETileTestResources.real(),kome.common.data.KOMETileTestResources.real().copyArgbPixels());
        Field geometry=field(KOMEConquestMapOverlay.class,"mapBorders");Object prior=geometry.get(null);
        java.util.Map owners=kome.common.data.KOMEClientData.INSTANCE.conquestRenderOwners();int revision=kome.common.data.KOMEClientData.INSTANCE.conquestRevision;
        try {
            geometry.set(null,borders);borders.territoryPresentation(KOMEConquestMapOverlay.territoryStyle);
            borders.territoryFrame(new KOMEMapViewport(0,0,40,30,1000,730,1),java.util.Collections.emptyMap(),java.util.Collections.emptyMap());
            assertEquals(1,borders.retainedTerritoryFrames());
            KOMEMapPreferences selected=new KOMEMapPreferences(KOMETerritoryStyle.Palette.CURRENT_KOME,KOMETerritoryStyle.DisplayMode.BORDERS_ONLY,
                KOMETerritoryStyle.Opacity.QUARTER,true,false,false,true,false);
            config.setMapPreferences(selected);KOMEConquestMapOverlay.applyMapPreferences(new KOMEClientConfig(file).mapPreferences());
            assertEquals(0,borders.retainedTerritoryFrames());assertNull(field(KOMEConquestMapOverlay.class,"renderedPalette").get(null));
            assertTrue(field(KOMEConquestMapOverlay.class,"claimTextureDirty").getBoolean(null));assertSame(owners,kome.common.data.KOMEClientData.INSTANCE.conquestRenderOwners());
            assertEquals(revision,kome.common.data.KOMEClientData.INSTANCE.conquestRevision);
        }finally{geometry.set(null,prior);}
    }
    private static Field field(Class<?> type,String name)throws Exception{Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}
}
