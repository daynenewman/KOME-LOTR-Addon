package kome.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import kome.common.data.KOMEAlliance;
import lotr.common.fac.LOTRFaction;

/** Active client presentation. Installation preferences are saved separately; never sent to the server. */
final class KOMETerritoryStyle {
    enum Palette {
        CLASSIC_LOTR("Classic LOTR"), CURRENT_KOME("Current KOME");
        final String label;
        Palette(String label) { this.label = label; }
        Palette next() { return values()[(ordinal() + 1) % values().length]; }
        int rgb(String factionName) {
            if (this == CLASSIC_LOTR) {
                LOTRFaction nativeFaction = NATIVE_FACTIONS.get(ownerKey(factionName));
                if (nativeFaction == null) nativeFaction = NATIVE_FACTIONS.get(canonicalKey(factionName));
                if (nativeFaction != null) return nativeFaction.getFactionColor() & 0xFFFFFF;
                Integer fallback = CLASSIC_FALLBACKS.get(ownerKey(factionName));
                if (fallback != null) return fallback;
                return hashRgb(factionName == null ? "" : factionName.toLowerCase(Locale.ROOT));
            }
            // Current KOME deliberately preserves the old case-sensitive lookup and locale-dependent hash.
            LOTRFaction faction = LOTRFaction.forName(factionName);
            if (faction != null) return faction.getFactionColor() & 0xFFFFFF;
            return hashRgb(factionName == null ? "" : factionName.toLowerCase());
        }
    }

    enum DisplayMode {
        FULL_COLOR("Full territory color"), BORDERS_ONLY("Borders only");
        final String label;
        DisplayMode(String label) { this.label = label; }
        DisplayMode next() { return values()[(ordinal() + 1) % values().length]; }
        boolean fillsTerritory() { return this == FULL_COLOR; }
    }

    Palette palette = Palette.CLASSIC_LOTR;
    DisplayMode mode = DisplayMode.FULL_COLOR;
    enum Opacity {
        ZERO(0), QUARTER(25), HALF(50), THREE_QUARTERS(75), FULL(100);
        final int percent, alpha;
        Opacity(int percent) { this.percent = percent; alpha = Math.round(percent * 255 / 100F); }
        Opacity next() { return values()[(ordinal() + 1) % values().length]; }
    }
    Opacity opacity = Opacity.HALF;
    boolean showTileBorders;

    static String ownerKey(String owner) {
        return owner == null ? "" : owner.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "")
            .replace("'", "").replace("-", "");
    }

    private static int hashRgb(String name) {
        int hash = name.hashCode();
        return (80 + (hash & 0x7F)) << 16 | (80 + (hash >> 8 & 0x7F)) << 8 | 80 + (hash >> 16 & 0x7F);
    }

    private static String canonicalKey(String name) {
        // Normalize ASCII case before the shared alias resolver; no localized factionName() calls.
        return KOMEAlliance.normalizeFactionKey(name == null ? "" : name.toLowerCase(Locale.ROOT));
    }

    private static final Map<String, LOTRFaction> NATIVE_FACTIONS;
    private static final Map<String, Integer> CLASSIC_FALLBACKS;
    static {
        Map<String, LOTRFaction> nativeFactions = new HashMap<>();
        for (LOTRFaction faction : LOTRFaction.values()) {
            nativeFactions.put(ownerKey(faction.codeName()), faction);
            nativeFactions.put(ownerKey(faction.name()), faction);
            String canonical = canonicalKey(faction.codeName());
            if (!canonical.isEmpty()) nativeFactions.put(canonical, faction);
            for (String alias : faction.listAliases()) nativeFactions.put(ownerKey(alias), faction);
        }
        nativeFactions.put("rhun", LOTRFaction.RHUDEL);
        nativeFactions.put("halftrolls", LOTRFaction.HALF_TROLL);
        NATIVE_FACTIONS = Collections.unmodifiableMap(nativeFactions);
        // No native counterpart: retain explicit visual fallbacks, then a stable hash for unknowns.
        Map<String, Integer> fallbacks = new HashMap<>();
        fallbacks.put("farharad", 0x76603D);
        fallbacks.put("umbar", 0x356B78);
        fallbacks.put("nomad", 0xC4A76A);
        CLASSIC_FALLBACKS = Collections.unmodifiableMap(fallbacks);
    }
}
