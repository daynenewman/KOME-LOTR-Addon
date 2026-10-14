package kome.client;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

/** Local display preferences only; never part of server rules or world persistence. */
public final class KOMEClientConfig {
    private static volatile KOMEClientConfig instance;

    private final Configuration configuration;
    private boolean showCurrentTile;
    private boolean useRankTitles;
    private boolean elvenAura,elvenDynamicIllumination;
    private float elvenIntensity;
    private int elvenParticleInterval;

    public KOMEClientConfig(File file) {
        configuration = new Configuration(file);
        configuration.load();
        showCurrentTile = configuration.get("hud", "showCurrentTile", true,
            "Show the current geographic tile. Toggle through Controls > KOME > Toggle current tile HUD.").getBoolean(true);
        useRankTitles = configuration.get("factions", "useRankTitles", true,
            "Use KOME progression titles instead of vanilla alignment titles beneath alignment bars.").getBoolean(true);
        elvenAura=configuration.get("elves","visualAura",true,"Subtle blue foot particles for legitimate Elves at night.").getBoolean(true);
        elvenDynamicIllumination=configuration.get("elves","dynamicIllumination",true,"Use the optional AtomicStryker 1.7.10 Dynamic Lights API. Without it there is no local illumination. Client presentation only.").getBoolean(true);
        elvenIntensity=clampElvenIntensity((float)configuration.get("elves","visualIntensity",0.28D,"Particle opacity, clamped to 0-0.4.").getDouble(0.28D));
        elvenParticleInterval=clampElvenInterval(configuration.get("elves","particleInterval",8,"Ticks per Elf particle, clamped to 8-40; reduced graphics emits half as often.").getInt(8));
        if (configuration.hasChanged()) configuration.save();
        instance = this;
    }

    public static KOMEClientConfig get() { return instance; }
    static float clampElvenIntensity(float value){return Float.isFinite(value)?Math.max(0F,Math.min(0.4F,value)):0.28F;}
    static int clampElvenInterval(int value){return Math.max(8,Math.min(40,value));}
    static boolean elvenAuraGlobal(){return instance==null||instance.elvenAura;}
    static boolean elvenDynamicGlobal(){return instance==null||instance.elvenDynamicIllumination;}
    static float elvenIntensityGlobal(){return instance==null?0.28F:instance.elvenIntensity;}
    static int elvenIntervalGlobal(){return instance==null?8:instance.elvenParticleInterval;}

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
