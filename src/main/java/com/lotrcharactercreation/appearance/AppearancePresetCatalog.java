package com.lotrcharactercreation.appearance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lotrcharactercreation.race.PlayerRace;

/** Immutable lookup view over built-in presets and one external preset snapshot. */
public final class AppearancePresetCatalog {

    private final Map<String, AppearancePreset> presetsById;

    AppearancePresetCatalog(Collection<AppearancePreset> presets) {
        Map<String, AppearancePreset> copiedPresets = new LinkedHashMap<String, AppearancePreset>();
        for (AppearancePreset preset : presets) {
            if (preset == null) {
                throw new IllegalArgumentException("appearance preset cannot be null");
            }
            if (copiedPresets.put(preset.getId(), preset) != null) {
                throw new IllegalArgumentException("duplicate appearance preset ID: " + preset.getId());
            }
        }
        presetsById = Collections.unmodifiableMap(copiedPresets);
    }

    public static AppearancePresetCatalog combine(AppearancePresetCatalog builtIns,
        Collection<AppearancePreset> externalPresets) {
        if (builtIns == null || externalPresets == null) {
            throw new IllegalArgumentException("appearance catalogs cannot be null");
        }

        List<AppearancePreset> combined = new ArrayList<AppearancePreset>(
            builtIns.presetsById.size() + externalPresets.size());
        combined.addAll(builtIns.presetsById.values());
        combined.addAll(externalPresets);
        return new AppearancePresetCatalog(combined);
    }

    public AppearancePreset findById(String id) {
        return id == null || id.isEmpty() ? null : presetsById.get(id);
    }

    public List<AppearancePreset> getPresets(PlayerRace race, PlayerSex sex) {
        return getPresets(race, sex, null);
    }

    public List<AppearancePreset> getPresets(PlayerRace race, PlayerSex sex, String groupId) {
        if (!AppearancePresetRegistry.isSexValidForRace(race, sex)) {
            return Collections.emptyList();
        }

        List<AppearancePreset> matches = new ArrayList<AppearancePreset>();
        for (AppearancePreset preset : presetsById.values()) {
            if (preset.getRace() == race && preset.getSex() == sex
                && (groupId == null || groupId.equals(preset.getGroupId()))) {
                matches.add(preset);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    public Map<String, AppearancePreset> getPresetsById() {
        return presetsById;
    }
}
