package kome.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEConquestTile;
import lotr.common.fac.LOTRFaction;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMETerritoryStyleTest {
    @Test public void defaultsAndChoicesAreIndependent() {
        KOMETerritoryStyle style = new KOMETerritoryStyle();
        assertEquals(KOMETerritoryStyle.Palette.CLASSIC_LOTR, style.palette);
        assertEquals(KOMETerritoryStyle.DisplayMode.FULL_COLOR, style.mode);
        assertEquals(KOMETerritoryStyle.Opacity.HALF,style.opacity);assertFalse(style.showTileBorders);
        style.mode=style.mode.next(); assertFalse(style.mode.fillsTerritory());
        assertEquals(KOMETerritoryStyle.Palette.CLASSIC_LOTR, style.palette);
        style.palette=style.palette.next(); assertEquals(KOMETerritoryStyle.DisplayMode.BORDERS_ONLY,style.mode);
        assertTrue(style.mode.next().fillsTerritory());
        assertEquals(KOMETerritoryStyle.Palette.CLASSIC_LOTR,style.palette.next());
    }

    @Test public void classicColorsCoverBundledFactionsAndLegacyPaletteRetainsNativeColorAndHashFallback() throws Exception {
        assertEquals(0xF9F9F9,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb("gondor"));
        assertEquals(0x351F1F,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb("mordor"));
        assertEquals(0x358727,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb("rohan"));
        assertEquals(KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb("Durin's Folk"),KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb("durinsfolk"));
        for(LOTRFaction faction:LOTRFaction.values()) {
            assertEquals(faction.getFactionColor() & 0xFFFFFF,KOMETerritoryStyle.Palette.CURRENT_KOME.rgb(faction.codeName()));
        }
        String unknown="unknown faction";int hash=unknown.hashCode();
        int expected=(80+(hash&127))<<16 | (80+(hash>>8&127))<<8 | 80+(hash>>16&127);
        assertEquals(expected,KOMETerritoryStyle.Palette.CURRENT_KOME.rgb(unknown));
        assertEquals(expected,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb(unknown));
    }

    @Test public void classicResolvesEveryBundledCanonicalKeyAndAllNativeNamesIncludingUtumno() {
        String[] keys={"hobbit","bree","dunedain","bluemountains","highelves","gundabad","angmar","woodelf",
            "dolguldur","dale","durinsfolk","lothlorien","dunland","isengard","fangorn","rohan","gondor",
            "mordor","dorwinion","rhudel","harad","morwaith","taurethrim","halftroll"};
        LOTRFaction[] nativeFactions={LOTRFaction.HOBBIT,LOTRFaction.BREE,LOTRFaction.RANGER_NORTH,LOTRFaction.BLUE_MOUNTAINS,
            LOTRFaction.HIGH_ELF,LOTRFaction.GUNDABAD,LOTRFaction.ANGMAR,LOTRFaction.WOOD_ELF,LOTRFaction.DOL_GULDUR,
            LOTRFaction.DALE,LOTRFaction.DURINS_FOLK,LOTRFaction.LOTHLORIEN,LOTRFaction.DUNLAND,LOTRFaction.ISENGARD,
            LOTRFaction.FANGORN,LOTRFaction.ROHAN,LOTRFaction.GONDOR,LOTRFaction.MORDOR,LOTRFaction.DORWINION,
            LOTRFaction.RHUDEL,LOTRFaction.NEAR_HARAD,LOTRFaction.MORWAITH,LOTRFaction.TAURETHRIM,LOTRFaction.HALF_TROLL};
        for(int i=0;i<keys.length;i++)assertEquals(keys[i],nativeFactions[i].getFactionColor() & 0xFFFFFF,
            KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb(keys[i]));
        for(LOTRFaction faction:LOTRFaction.values()) {
            for(String name:Arrays.asList(faction.codeName(),faction.name(),faction.name().toLowerCase(Locale.ROOT)))
                assertEquals(name,faction.getFactionColor() & 0xFFFFFF,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb(name));
            for(String alias:faction.listAliases())assertEquals(alias,faction.getFactionColor() & 0xFFFFFF,
                KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb(alias));
        }
        assertEquals(LOTRFaction.RANGER_NORTH.getFactionColor() & 0xFFFFFF,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb("RANGER_NORTH"));
        assertEquals(LOTRFaction.NEAR_HARAD.getFactionColor() & 0xFFFFFF,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb("NEAR_HARAD"));
        assertEquals(LOTRFaction.HALF_TROLL.getFactionColor() & 0xFFFFFF,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb("Half-trolls"));
        assertEquals(LOTRFaction.RHUDEL.getFactionColor() & 0xFFFFFF,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb("Rhun"));
    }

    @Test public void nonNativeFallbacksAreExplicitAndStableWhileCurrentKomeRetainsItsOriginalLocaleHash() {
        assertEquals(0x76603D,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb("Far Harad"));
        assertEquals(0x356B78,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb("Umbar"));
        assertEquals(0xC4A76A,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb("Nomad"));
        Locale previous=Locale.getDefault();
        try {
            String name="UNKNOWN_I";
            Locale.setDefault(Locale.US);int classic=KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb(name);
            Locale.setDefault(new Locale("tr","TR"));
            assertEquals(classic,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb(name));
            int hash=name.toLowerCase().hashCode();
            assertEquals((80+(hash&127))<<16 | (80+(hash>>8&127))<<8 | 80+(hash>>16&127),
                KOMETerritoryStyle.Palette.CURRENT_KOME.rgb(name));
            assertEquals(0xF9F9F9,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb("GONDOR"));
            assertEquals(0x358727,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb("ROHAN"));
            assertEquals(KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb(null),KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb(""));
        } finally {Locale.setDefault(previous);}
    }

    @Test public void paletteInvalidatesOnlyVisualCacheAndModesReuseItWithoutChangingOwnership() throws Exception {
        KOMEClientData client=KOMEClientData.INSTANCE;
        Map saved=new HashMap(client.conquestTiles);
        KOMETerritoryStyle.Palette palette=KOMEConquestMapOverlay.territoryStyle.palette;
        KOMETerritoryStyle.DisplayMode mode=KOMEConquestMapOverlay.territoryStyle.mode;
        Map<String,Integer> ids=(Map<String,Integer>)field("tileColorsById").get(null);
        Map<String,Integer> oldIds=new HashMap<>(ids);
        Method prepare=KOMEConquestMapOverlay.class.getDeclaredMethod("prepareTerritoryColors");prepare.setAccessible(true);
        try {
            client.conquestTiles.clear();ids.clear();ids.put("T001",1);
            KOMEConquestTile tile=new KOMEConquestTile("T001");tile.claim("gondor",0L);
            client.conquestTiles.put(tile.id,tile);client.completeConquestUpdate();
            Map<String,String> owners=client.conquestRenderOwners();int revision=client.conquestRevision;
            String current=tile.currentRulingFaction,legacy=tile.ownerFaction;
            KOMEConquestMapOverlay.territoryStyle.palette=KOMETerritoryStyle.Palette.CLASSIC_LOTR;
            prepare.invoke(null);Object first=field("territoryColors").get(null);
            prepare.invoke(null);assertSame(first,field("territoryColors").get(null));
            KOMEConquestMapOverlay.territoryStyle.mode=KOMETerritoryStyle.DisplayMode.BORDERS_ONLY;
            prepare.invoke(null);assertSame(first,field("territoryColors").get(null));
            KOMEConquestMapOverlay.territoryStyle.palette=KOMETerritoryStyle.Palette.CURRENT_KOME;
            prepare.invoke(null);assertNotSame(first,field("territoryColors").get(null));
            assertNotEquals(first,field("territoryColors").get(null));
            assertTrue(field("claimTextureDirty").getBoolean(null));
            assertSame(owners,client.conquestRenderOwners());assertEquals(revision,client.conquestRevision);
            assertEquals(current,tile.currentRulingFaction);assertEquals(legacy,tile.ownerFaction);
            client.conquestTiles.clear();client.completeConquestUpdate();prepare.invoke(null);
            assertTrue(((Map)field("territoryOwners").get(null)).isEmpty());
            assertTrue(((Map)field("territoryColors").get(null)).isEmpty());
        } finally {
            KOMEConquestMapOverlay.territoryStyle.palette=palette;KOMEConquestMapOverlay.territoryStyle.mode=mode;
            client.conquestTiles.clear();client.conquestTiles.putAll(saved);client.completeConquestUpdate();
            ids.clear();ids.putAll(oldIds);field("renderedPalette").set(null,null);
        }
    }

    public static class UploadTexture extends net.minecraft.client.renderer.texture.DynamicTexture {
        int[] pixels; int uploads;
        public UploadTexture(){super(1,1);}
        @Override public int[] getTextureData(){return pixels;}
        @Override public void updateDynamicTexture(){uploads++;}
    }

    @Test public void bordersPaletteAndOwnerChangesUploadLatestPixelsExactlyOnceOnReturnToFullColor() throws Exception {
        KOMEClientData client=KOMEClientData.INSTANCE;
        Map saved=new HashMap(client.conquestTiles);
        Map<String,Integer> ids=(Map<String,Integer>)field("tileColorsById").get(null);
        Map<String,Integer> oldIds=new HashMap<>(ids);
        Object oldTexture=field("claimedTexture").get(null),oldPixels=field("tileMaskPixels").get(null);
        KOMETerritoryStyle style=KOMEConquestMapOverlay.territoryStyle;
        KOMETerritoryStyle.Palette oldPalette=style.palette;KOMETerritoryStyle.DisplayMode oldMode=style.mode;
        KOMETerritoryStyle.Opacity oldOpacity=style.opacity;
        KOMEConquestMapOverlay.ClaimTextureState cache=(KOMEConquestMapOverlay.ClaimTextureState)field("claimTextureState").get(null);
        Method prepare=KOMEConquestMapOverlay.class.getDeclaredMethod("prepareTerritoryColors");prepare.setAccessible(true);
        UploadTexture texture=kome.common.KOMEAccessFixture.allocate(UploadTexture.class);texture.pixels=new int[2];
        try {
            field("claimedTexture").set(null,texture);field("tileMaskPixels").set(null,new int[]{0xFF000001,0x00000001});
            ids.clear();ids.put("T001",1);client.conquestTiles.clear();
            KOMEConquestTile tile=new KOMEConquestTile("T001");tile.claim("gondor",0L);client.conquestTiles.put(tile.id,tile);
            client.completeConquestUpdate();cache.clear();
            style.palette=KOMETerritoryStyle.Palette.CLASSIC_LOTR;style.mode=KOMETerritoryStyle.DisplayMode.FULL_COLOR;
            KOMEConquestMapOverlay.updateClaimedTexture();assertEquals(1,texture.uploads);
            assertEquals(0xF9F9F9,texture.pixels[0]&0xFFFFFF);assertEquals(0,texture.pixels[1]);
            style.mode=KOMETerritoryStyle.DisplayMode.BORDERS_ONLY;
            style.palette=KOMETerritoryStyle.Palette.CURRENT_KOME;prepare.invoke(null);
            tile.claim("mordor",0L);client.completeConquestUpdate();prepare.invoke(null);
            KOMEConquestMapOverlay.updateClaimedTexture();assertEquals(1,texture.uploads);
            assertEquals(0xF9F9F9,texture.pixels[0]&0xFFFFFF);
            style.mode=KOMETerritoryStyle.DisplayMode.FULL_COLOR;
            KOMEConquestMapOverlay.updateClaimedTexture();assertEquals(2,texture.uploads);
            assertEquals(style.palette.rgb("mordor"),texture.pixels[0]&0xFFFFFF);
            assertEquals(0,texture.pixels[1]);assertFalse(field("claimTextureDirty").getBoolean(null));
            KOMEConquestMapOverlay.updateClaimedTexture();assertEquals(2,texture.uploads);
            style.palette=KOMETerritoryStyle.Palette.CLASSIC_LOTR;KOMEConquestMapOverlay.updateClaimedTexture();
            assertEquals(3,texture.uploads);assertEquals(0x351F1F,texture.pixels[0]&0xFFFFFF);
            Object colorCache=field("territoryColors").get(null);
            style.opacity=KOMETerritoryStyle.Opacity.QUARTER;KOMEConquestMapOverlay.updateClaimedTexture();
            assertSame(colorCache,field("territoryColors").get(null));assertEquals(4,texture.uploads);
            assertEquals(64,texture.pixels[0]>>>24);
            KOMEConquestMapOverlay.updateClaimedTexture();assertEquals(4,texture.uploads);
            style.mode=KOMETerritoryStyle.DisplayMode.BORDERS_ONLY;style.opacity=KOMETerritoryStyle.Opacity.FULL;
            prepare.invoke(null);KOMEConquestMapOverlay.updateClaimedTexture();assertEquals(4,texture.uploads);
            style.mode=KOMETerritoryStyle.DisplayMode.FULL_COLOR;KOMEConquestMapOverlay.updateClaimedTexture();
            assertEquals(5,texture.uploads);assertEquals(255,texture.pixels[0]>>>24);
            style.opacity=KOMETerritoryStyle.Opacity.ZERO;KOMEConquestMapOverlay.updateClaimedTexture();
            assertEquals(6,texture.uploads);assertEquals(0,texture.pixels[0]>>>24);assertEquals(0,texture.pixels[1]);
        } finally {
            field("claimedTexture").set(null,oldTexture);field("tileMaskPixels").set(null,oldPixels);
            style.palette=oldPalette;style.mode=oldMode;client.conquestTiles.clear();client.conquestTiles.putAll(saved);
            style.opacity=oldOpacity;
            client.completeConquestUpdate();ids.clear();ids.putAll(oldIds);cache.clear();field("renderedPalette").set(null,null);
        }
    }

    private static Field field(String name)throws Exception {
        Field f=KOMEConquestMapOverlay.class.getDeclaredField(name);f.setAccessible(true);return f;
    }
}
