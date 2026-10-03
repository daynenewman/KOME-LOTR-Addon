package kome.client;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

/** Local display preferences only; never part of server rules or world persistence. */
public final class KOMEClientConfig {
    private static volatile KOMEClientConfig instance;

    private final Configuration configuration;
    private boolean showCurrentTile;
    private boolean useRankTitles;

    public KOMEClientConfig(File file) {
        configuration = new Configuration(file);
        configuration.load();
        showCurrentTile = configuration.get("hud", "showCurrentTile", true,
            "Show the current geographic tile. Toggle through Controls > KOME > Toggle current tile HUD.").getBoolean(true);
        useRankTitles = configuration.get("factions", "useRankTitles", true,
            "Use KOME progression titles instead of vanilla alignment titles beneath alignment bars.").getBoolean(true);
        if (configuration.hasChanged()) configuration.save();
        instance = this;
    }

    public static KOMEClientConfig get() { return instance; }

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
