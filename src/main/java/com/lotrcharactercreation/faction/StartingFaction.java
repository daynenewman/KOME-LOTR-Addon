package com.lotrcharactercreation.faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.lotrcharactercreation.race.PlayerRace;

import lotr.common.fac.LOTRFaction;

public enum StartingFaction {

    ANGMAR("angmar", "Angmar", "The ruined realm of the Witch-king.", LOTRFaction.ANGMAR,
        "lotr:item/banner/banner_angmar.png", PlayerRace.MAN, PlayerRace.ORC),
    BREE_LAND("bree_land", "Bree-land", "The Big and Little Folk of Bree.", LOTRFaction.BREE,
        "lotr:item/banner/banner_bree.png", PlayerRace.MAN, PlayerRace.HOBBIT),
    DALE("dale", "Dale", "The kingdom of the Northmen.", LOTRFaction.DALE, "lotr:item/banner/banner_dale.png",
        PlayerRace.MAN),
    DORWINION("dorwinion", "Dorwinion", "The wine-merchants by the shores of Rhûn.", LOTRFaction.DORWINION,
        "lotr:item/banner/banner_dorwinion.png", PlayerRace.MAN, PlayerRace.ELF),
    DUNEDAIN_NORTH("dunedain_north", "Dúnedain of the North", "The scattered remnants of fallen Arnor.",
        LOTRFaction.RANGER_NORTH, "lotr:item/banner/banner_ranger.png", PlayerRace.MAN),
    BLUE_MOUNTAINS("blue_mountains", "Blue Mountains", "The Dwarves of Ered Luin.", LOTRFaction.BLUE_MOUNTAINS,
        "lotr:item/banner/banner_blueMountains.png", PlayerRace.DWARF),
    DUNLAND("dunland", "Dunland", "The Dunlending hill-tribes.", LOTRFaction.DUNLAND,
        "lotr:item/banner/banner_dunland.png", PlayerRace.MAN, PlayerRace.DWARF),
    DURINS_FOLK("durins_folk", "Durin's Folk", "The Longbeard Dwarves of Khazad-dûm.", LOTRFaction.DURINS_FOLK,
        "lotr:item/banner/banner_durin.png", PlayerRace.DWARF),
    GONDOR("gondor", "Gondor", "The South-kingdom of the Dúnedain.", LOTRFaction.GONDOR,
        "lotr:item/banner/banner_gondor.png", PlayerRace.MAN),
    HIGH_ELVES("high_elves", "High Elves", "The Noldor and Sindar of Lindon and Imladris.", LOTRFaction.HIGH_ELF,
        "lotr:item/banner/banner_highElf.png", PlayerRace.ELF),
    LOTHLORIEN("lothlorien", "Lothlórien", "The Galadhrim Elves of the Golden Wood.", LOTRFaction.LOTHLORIEN,
        "lotr:item/banner/banner_lothlorien.png", PlayerRace.ELF),
    WOODLAND_REALM("woodland_realm", "Woodland Realm", "The Silvan Elves of Mirkwood.", LOTRFaction.WOOD_ELF,
        "lotr:item/banner/banner_mirkwood.png", PlayerRace.ELF),
    HOBBITS("hobbits", "Hobbits", "The Little Folk of the Shire.", LOTRFaction.HOBBIT,
        "lotr:item/banner/banner_hobbit.png", PlayerRace.HOBBIT),
    DOL_GULDUR("dol_guldur", "Dol Guldur", "The spawn of Dol Guldur.", LOTRFaction.DOL_GULDUR,
        "lotr:item/banner/banner_dolGuldur.png", PlayerRace.ORC),
    GUNDABAD("gundabad", "Gundabad", "The Orc-hordes of the North.", LOTRFaction.GUNDABAD,
        "lotr:item/banner/banner_gundabad.png", PlayerRace.ORC, PlayerRace.URUK_HAI),
    ISENGARD("isengard", "Isengard", "The White Hand of Saruman.", LOTRFaction.ISENGARD,
        "lotr:item/banner/banner_isengard.png", PlayerRace.ORC, PlayerRace.URUK_HAI),
    MORDOR("mordor", "Mordor", "The legions of the Black Land.", LOTRFaction.MORDOR,
        "lotr:item/banner/banner_mordor.png", PlayerRace.MAN, PlayerRace.ORC, PlayerRace.URUK_HAI),
    MORWAITH("morwaith", "Morwaith", "The tribal Haradrim of the grasslands.", LOTRFaction.MORWAITH,
        "lotr:item/banner/banner_moredain.png", PlayerRace.MAN),
    NEAR_HARAD("near_harad", "Near Harad", "The Southrons from beyond the Harnen.", LOTRFaction.NEAR_HARAD,
        "lotr:item/banner/banner_nearHarad.png", PlayerRace.MAN),
    EASTERLINGS("easterlings", "Easterlings", "The golden Easterlings of Rhûn.", LOTRFaction.RHUDEL,
        "lotr:item/banner/banner_rhun.png", PlayerRace.MAN),
    ROHAN("rohan", "Rohan", "The horse-lords of the Rohirrim.", LOTRFaction.ROHAN, "lotr:item/banner/banner_rohan.png",
        PlayerRace.MAN),
    TAURETHRIM("taurethrim", "Taurethrim", "The Haradrim of the great southern jungles.", LOTRFaction.TAURETHRIM,
        "lotr:item/banner/banner_tauredain.png", PlayerRace.MAN),
    WANDERER("wanderer", "Wanderer",
        "Begin your journey unaffiliated.\nYou may pledge yourself to a faction later during your travels.", null, null,
        PlayerRace.values());

    private final String serializedId;
    private final String displayName;
    private final String description;
    private final LOTRFaction lotrFaction;
    private final String visualResource;
    private final Set<PlayerRace> allowedRaces;

    StartingFaction(String serializedId, String displayName, String description, LOTRFaction lotrFaction,
        String visualResource, PlayerRace... allowedRaces) {
        this.serializedId = serializedId;
        this.displayName = displayName;
        this.description = description;
        this.lotrFaction = lotrFaction;
        this.visualResource = visualResource;

        EnumSet<PlayerRace> races = EnumSet.noneOf(PlayerRace.class);
        Collections.addAll(races, allowedRaces);
        this.allowedRaces = Collections.unmodifiableSet(races);
    }

    public String getSerializedId() {
        return serializedId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public LOTRFaction getLotrFaction() {
        return lotrFaction;
    }

    public String getVisualResource() {
        return visualResource;
    }

    public boolean isAllowedFor(PlayerRace race) {
        return race != null && allowedRaces.contains(race);
    }

    public static StartingFaction findBySerializedId(String serializedId) {
        if (serializedId == null) {
            return null;
        }

        for (StartingFaction faction : values()) {
            if (faction.serializedId.equals(serializedId)) {
                return faction;
            }
        }

        return null;
    }

    public static StartingFaction fromSerializedId(String serializedId) {
        StartingFaction faction = findBySerializedId(serializedId);
        return faction == null ? WANDERER : faction;
    }

    public static List<StartingFaction> getAllowedForRace(PlayerRace race) {
        List<StartingFaction> factions = new ArrayList<>();
        for (StartingFaction faction : values()) {
            if (faction.isAllowedFor(race)) {
                factions.add(faction);
            }
        }

        return Collections.unmodifiableList(factions);
    }
}
