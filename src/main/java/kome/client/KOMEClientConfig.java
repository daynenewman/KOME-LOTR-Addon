package kome.client;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

/** Local display preferences only; never part of server rules or world persistence. */
public final class KOMEClientConfig {
    private final Configuration configuration;
    private boolean showCurrentTile;

    public KOMEClientConfig(File file) {
        configuration = new Configuration(file);
        configuration.load();
        showCurrentTile = configuration.get("hud", "showCurrentTile", true,
            "Show the current geographic tile. Toggle through Controls > KOME > Toggle current tile HUD.").getBoolean(true);
        if (configuration.hasChanged()) configuration.save();
    }

    public boolean showCurrentTile() { return showCurrentTile; }

    public void setShowCurrentTile(boolean enabled) {
        if (showCurrentTile == enabled) return;
        showCurrentTile = enabled;
        configuration.get("hud", "showCurrentTile", true).set(enabled);
        configuration.save();
    }
}