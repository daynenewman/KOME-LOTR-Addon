package kome.client;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

/** Local display preferences only; never part of server rules or world persistence. */
public final class KOMEClientConfig {
    private static volatile KOMEClientConfig instance;

    private final Configuration configuration;
    private boolean showCurrentTile;
    private boolean useRankTitles;
    private KOMEMapPreferences mapPreferences;

    public KOMEClientConfig(File file) {
        configuration = new Configuration(file);
        configuration.load();
        showCurrentTile = configuration.get("hud", "showCurrentTile", true,
            "Show the current geographic tile. Toggle through Controls > KOME > Toggle current tile HUD.").getBoolean(true);
        useRankTitles = configuration.get("factions", "useRankTitles", true,
            "Use KOME progression titles instead of vanilla alignment titles beneath alignment bars.").getBoolean(true);
        for(String key:new String[]{"factionPalette","territoryMode","territoryOpacity","tileBorders","conquestLayer","riverMarkers","bridgeMarkers","troopMarkers","companyDisplayMode","buildMarkers"}) {
            net.minecraftforge.common.config.Property existing=configuration.getCategory("map").get(key);
            // Forge properties retain their scalar/list shape even after set(); replace malformed lists.
            if(existing!=null && existing.isList())configuration.getCategory("map").remove(key);
        }
        KOMETerritoryStyle.Palette palette=readEnum("factionPalette",KOMETerritoryStyle.Palette.CLASSIC_LOTR);
        KOMETerritoryStyle.DisplayMode mode=readEnum("territoryMode",KOMETerritoryStyle.DisplayMode.FULL_COLOR);
        net.minecraftforge.common.config.Property opacityProperty=configuration.get("map","territoryOpacity",50,"Full territory fill: 0, 25, 50, 75 or 100 percent.");
        KOMETerritoryStyle.Opacity opacity=KOMETerritoryStyle.Opacity.HALF;
        for(KOMETerritoryStyle.Opacity candidate:KOMETerritoryStyle.Opacity.values())if(candidate.percent==opacityProperty.getInt(50))opacity=candidate;
        if(!opacityProperty.getString().equals(Integer.toString(opacity.percent)))opacityProperty.set(opacity.percent);
        boolean legacyTroops=readBoolean("troopMarkers",true);
        KOMEMapPreferences.CompanyDisplay fallback=legacyTroops
            ?KOMEMapPreferences.CompanyDisplay.SHIELDS_ONLY:KOMEMapPreferences.CompanyDisplay.HIDE_COMPANIES;
        KOMEMapPreferences.CompanyDisplay companyDisplay=readEnum("companyDisplayMode",fallback);
        mapPreferences=new KOMEMapPreferences(palette,mode,opacity,readBoolean("tileBorders",false),readBoolean("conquestLayer",true),
            readBoolean("riverMarkers",true),readBoolean("bridgeMarkers",true),companyDisplay,readBoolean("buildMarkers",true));
        if (configuration.hasChanged()) configuration.save();
        instance = this;
    }

    public static KOMEClientConfig get() { return instance; }

    KOMEMapPreferences mapPreferences(){return mapPreferences;}
    void setMapPreferences(KOMEMapPreferences next) {
        if(mapPreferences.equals(next))return;
        configuration.get("map","factionPalette","CLASSIC_LOTR").set(next.palette.name());
        configuration.get("map","territoryMode","FULL_COLOR").set(next.mode.name());
        configuration.get("map","territoryOpacity",50).set(next.opacity.percent);
        configuration.get("map","tileBorders",false).set(next.tileBorders);
        configuration.get("map","conquestLayer",true).set(next.conquest);
        configuration.get("map","riverMarkers",true).set(next.rivers);
        configuration.get("map","bridgeMarkers",true).set(next.bridges);
        configuration.get("map","troopMarkers",true).set(next.troops);
        configuration.get("map","companyDisplayMode","SHIELDS_ONLY").set(next.companyDisplay.name());
        configuration.get("map","buildMarkers",true).set(next.builds);
        mapPreferences=next;configuration.save();
    }
    private <E extends Enum<E>> E readEnum(String key,E fallback) {
        net.minecraftforge.common.config.Property property=configuration.get("map",key,fallback.name(),"Local map appearance choice (enum name).");
        E value=fallback;try{value=Enum.valueOf(fallback.getDeclaringClass(),property.getString());}catch(IllegalArgumentException ignored){}
        if(!property.getString().equals(value.name()))property.set(value.name());return value;
    }
    private boolean readBoolean(String key,boolean fallback) {
        net.minecraftforge.common.config.Property property=configuration.get("map",key,fallback,"Local map visibility preference.");
        boolean value=property.getBoolean(fallback);if(!property.isBooleanValue())property.set(value);return value;
    }

    /** Rank titles are the KOME default if the client config has not initialized yet. */
    public static boolean useRankTitlesGlobal() {
        KOMEClientConfig config = instance;
        return config == null || config.useRankTitles();
    }

    public boolean showCurrentTile() { return showCurrentTile; }

    public void setShowCurrentTile(boolean enabled) {
        if (showCurrentTile == enabled) return;
        showCurrentTile = enabled;
        configuration.get("hud", "showCurrentTile", true).set(enabled);
        configuration.save();
    }

    public boolean useRankTitles() { return useRankTitles; }

    public void setUseRankTitles(boolean enabled) {
        if (useRankTitles == enabled) return;
        useRankTitles = enabled;
        configuration.get("factions", "useRankTitles", true).set(enabled);
        configuration.save();
    }
}
