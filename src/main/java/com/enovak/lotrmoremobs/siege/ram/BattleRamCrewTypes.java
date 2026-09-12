package com.enovak.lotrmoremobs.siege.ram;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import lotr.common.entity.npc.LOTREntityAngmarOrc;
import lotr.common.entity.npc.LOTREntityBlueDwarfWarrior;
import lotr.common.entity.npc.LOTREntityBreeGuard;
import lotr.common.entity.npc.LOTREntityDaleSoldier;
import lotr.common.entity.npc.LOTREntityDolGuldurOrc;
import lotr.common.entity.npc.LOTREntityDorwinionElfWarrior;
import lotr.common.entity.npc.LOTREntityDunlendingWarrior;
import lotr.common.entity.npc.LOTREntityDwarfWarrior;
import lotr.common.entity.npc.LOTREntityEasterlingWarrior;
import lotr.common.entity.npc.LOTREntityGaladhrimWarrior;
import lotr.common.entity.npc.LOTREntityGondorSoldier;
import lotr.common.entity.npc.LOTREntityGundabadOrc;
import lotr.common.entity.npc.LOTREntityHalfTrollWarrior;
import lotr.common.entity.npc.LOTREntityHighElfWarrior;
import lotr.common.entity.npc.LOTREntityHobbitBounder;
import lotr.common.entity.npc.LOTREntityMordorOrc;
import lotr.common.entity.npc.LOTREntityMoredainWarrior;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTREntityNearHaradrimWarrior;
import lotr.common.entity.npc.LOTREntityRangerNorth;
import lotr.common.entity.npc.LOTREntityRohirrimWarrior;
import lotr.common.entity.npc.LOTREntityRuffianBrute;
import lotr.common.entity.npc.LOTREntityTauredainWarrior;
import lotr.common.entity.npc.LOTREntityUrukHai;
import lotr.common.entity.npc.LOTREntityUtumnoOrc;
import lotr.common.entity.npc.LOTREntityWoodElfWarrior;
import lotr.common.fac.LOTRFaction;

public final class BattleRamCrewTypes {

    private static final Map<
            LOTRFaction,
            Class<? extends LOTREntityNPC>> TYPES;

    static {
        EnumMap<LOTRFaction, Class<? extends LOTREntityNPC>> types =
                new EnumMap<
                        LOTRFaction,
                        Class<? extends LOTREntityNPC>>(
                        LOTRFaction.class
                );
        types.put(LOTRFaction.HOBBIT, LOTREntityHobbitBounder.class);
        types.put(LOTRFaction.BREE, LOTREntityBreeGuard.class);
        types.put(LOTRFaction.RANGER_NORTH, LOTREntityRangerNorth.class);
        types.put(
                LOTRFaction.BLUE_MOUNTAINS,
                LOTREntityBlueDwarfWarrior.class
        );
        types.put(LOTRFaction.HIGH_ELF, LOTREntityHighElfWarrior.class);
        types.put(LOTRFaction.GUNDABAD, LOTREntityGundabadOrc.class);
        types.put(LOTRFaction.ANGMAR, LOTREntityAngmarOrc.class);
        types.put(LOTRFaction.WOOD_ELF, LOTREntityWoodElfWarrior.class);
        types.put(LOTRFaction.DOL_GULDUR, LOTREntityDolGuldurOrc.class);
        types.put(LOTRFaction.DALE, LOTREntityDaleSoldier.class);
        types.put(LOTRFaction.DURINS_FOLK, LOTREntityDwarfWarrior.class);
        types.put(LOTRFaction.LOTHLORIEN, LOTREntityGaladhrimWarrior.class);
        types.put(LOTRFaction.DUNLAND, LOTREntityDunlendingWarrior.class);
        types.put(LOTRFaction.ISENGARD, LOTREntityUrukHai.class);
        types.put(LOTRFaction.ROHAN, LOTREntityRohirrimWarrior.class);
        types.put(LOTRFaction.GONDOR, LOTREntityGondorSoldier.class);
        types.put(
                LOTRFaction.DORWINION,
                LOTREntityDorwinionElfWarrior.class
        );
        types.put(
                LOTRFaction.RHUDEL,
                LOTREntityEasterlingWarrior.class
        );
        types.put(
                LOTRFaction.NEAR_HARAD,
                LOTREntityNearHaradrimWarrior.class
        );
        types.put(LOTRFaction.MORWAITH, LOTREntityMoredainWarrior.class);
        types.put(LOTRFaction.TAURETHRIM, LOTREntityTauredainWarrior.class);
        types.put(LOTRFaction.HALF_TROLL, LOTREntityHalfTrollWarrior.class);
        types.put(LOTRFaction.MORDOR, LOTREntityMordorOrc.class);
        types.put(LOTRFaction.RUFFIAN, LOTREntityRuffianBrute.class);
        types.put(LOTRFaction.UTUMNO, LOTREntityUtumnoOrc.class);
        TYPES = Collections.unmodifiableMap(types);
    }

    private BattleRamCrewTypes() {
    }

    public static Class<? extends LOTREntityNPC> getCrewClass(
            LOTRFaction faction
    ) {
        return faction == null ? null : TYPES.get(faction);
    }

    public static boolean isSupported(LOTRFaction faction) {
        return getCrewClass(faction) != null;
    }

    /**
     * A ram carrier is intentionally the one exact ordinary ground-troop class
     * mapped for its faction. Subclasses, captains, heroes, cavalry and other
     * LOTR NPCs are not interchangeable merely because they share a faction.
     */
    public static boolean isApprovedGroundCrew(
            LOTRFaction faction,
            LOTREntityNPC crew
    ) {
        Class<? extends LOTREntityNPC> expected = getCrewClass(faction);
        return crew != null
                && expected != null
                && crew.getClass() == expected
                && crew.ridingEntity == null;
    }

    public static Map<
            LOTRFaction,
            Class<? extends LOTREntityNPC>> getSupportedTypes() {
        return TYPES;
    }
}
