package kome.common.data;

import java.util.Locale;
import lotr.common.fac.LOTRFaction;

/** Resolves KOME-persisted faction keys without changing their save representation. */
public final class KOMEProgressionFactionResolver {
    private KOMEProgressionFactionResolver() {}

    public static LOTRFaction resolve(String storedKey) {
        if(storedKey==null)return null;
        String key=storedKey.trim().toLowerCase(Locale.ROOT);
        if(key.length()==0||!key.matches("[a-z0-9_]+"))return null;
        for(LOTRFaction faction:LOTRFaction.values())
            if(key.equals(faction.codeName().toLowerCase(Locale.ROOT)))return faction;
        // KOME alliance records have historically removed separators and normalized a
        // few faction names. Derive those spellings from LOTR's identifiers too.
        for(LOTRFaction faction:LOTRFaction.values())
            if(key.equals(KOMEAlliance.normalizeFactionKey(faction.codeName())))return faction;
        for(LOTRFaction faction:LOTRFaction.values())for(String alias:faction.listAliases())
            if(key.equals(alias.toLowerCase(Locale.ROOT))||key.equals(KOMEAlliance.normalizeFactionKey(alias)))return faction;
        return null;
    }

    public static boolean matches(String storedKey,LOTRFaction faction) {
        return faction!=null&&resolve(storedKey)==faction;
    }
    /** v36.15 contains Ents/Huorns but no Fangorn LOTRUnitTradeable Liege or Quest host. */
    public static boolean missingNativeLiege(String faction){return resolve(faction)==LOTRFaction.FANGORN;}
}
