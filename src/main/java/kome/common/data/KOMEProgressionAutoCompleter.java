package kome.common.data;

import kome.common.KOMEReflection;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketProgressionData;
import lotr.common.LOTRAchievement;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.LOTRShields;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class KOMEProgressionAutoCompleter {
    private static final Set<String> DERIVED_BASELINE_UNLOCKS = new HashSet<>();

    static {
        addDerived("baseline.fast_travel", "baseline.fire", "baseline.stonework", "baseline.cooking", "baseline.alcohol_pipeweed");
        addDerived("baseline.hunting", "baseline.house_waypoints", "baseline.farming", "baseline.npc_trade", "baseline.miniquests");
        addDerived("baseline.non_faction_gear", "baseline.brewing", "baseline.non_faction_armor", "baseline.mounts", "baseline.pledge");
        addDerived("baseline.faction_armor", "baseline.hire_units", "baseline.pouches", "baseline.meat");
        addDerived("baseline.faction_gear", "baseline.fellowship", "baseline.take_waypoints", "baseline.reclaim_waypoints");
        addDerived("baseline.redstone", "baseline.scrolls_modifiers", "baseline.grow_population", "baseline.protective_banners");
        addDerived("baseline.free_war", "baseline.mithril_gear", "baseline.enchanted_books");
    }

    public static int runForPlayer(EntityPlayerMP player, boolean notify) {
        UUID playerID = KOMEReflection.getEntityUUID(player);
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        KOMEPlayerProgression progression = data.getProgression(playerID);
        LOTRPlayerData lotrData = LOTRLevelData.getData(player);
        int changed = 0;

        changed += grantIf(progression, "wanderer.expert_traveler", hasAchievement(lotrData, LOTRAchievement.travel10));
        changed += grantIf(progression, "wanderer.dangerous_business", hasAchievement(lotrData, LOTRAchievement.travel20));
        changed += grantIf(progression, "wanderer.travel_30km", hasAchievement(lotrData, LOTRAchievement.travel30));
        changed += grantIf(progression, "wanderer.find_serf_lord", progression.hasPledgedLord());

        changed += grantIf(progression, "serf.quest_seeker", lotrData.getCompletedMiniQuestsTotal() >= 5);
        changed += grantIf(progression, "serf.alignment_100", hasAnyAlignmentAtLeast(lotrData, 100.0f));
        changed += grantIf(progression, "serf.pledge", lotrData.getPledgeFaction() != null);
        changed += grantIf(progression, "serf.defeat_invasion", hasAchievement(lotrData, LOTRAchievement.defeatInvasion));
        changed += grantIf(progression, "serf.brewing", hasAchievement(lotrData, LOTRAchievement.brewDrinkInBarrel));

        changed += grantIf(progression, "knight.alignment_2000", hasAnyAlignmentAtLeast(lotrData, 2000.0f));
        changed += grantIf(progression, "knight.hooligan", hasAchievement(lotrData, LOTRAchievement.killWhileDrunk));
        changed += grantIf(progression, "knight.speared", hasAchievement(lotrData, LOTRAchievement.useSpearFromFar));
        changed += grantIf(progression, "knight.fanny_pack", hasAchievement(lotrData, LOTRAchievement.getPouch));
        changed += grantIf(progression, "knight.faction_1", hasAssignedFactionAlignment(lotrData, progression.getAssignment("knight.faction_1")));
        changed += grantIf(progression, "knight.faction_2", hasAssignedFactionAlignment(lotrData, progression.getAssignment("knight.faction_2")));

        changed += grantIf(progression, "lord.early_beginnings", data.getPopulation(playerID).getCombinedTotal() >= 200);
        changed += grantIf(progression, "lord.alignment_3000", hasAnyAlignmentAtLeast(lotrData, 3000.0f));
        changed += grantIf(progression, "lord.global_alignment", hasEveryPlayableAlignmentAtLeastAbsolute(lotrData, 250.0f));
        changed += grantIf(progression, "lord.fluttering_by", hasAchievement(lotrData, LOTRAchievement.catchButterfly));
        changed += grantIf(progression, "lord.protective_banners", hasAchievement(lotrData, LOTRAchievement.bannerProtect));
        changed += grantIf(progression, "lord.fell_beast", hasFellBeastAchievement(lotrData, progression.getAssignment("lord.fell_beast")));

        changed += grantIf(progression, "prince_king.true_silver", lotrData.getShield() == LOTRShields.ACHIEVEMENT_MITHRIL);
        changed += KOMEProgressionQuotas.applyCompletedQuotas(progression);

        changed += applyUnlocks(progression);
        if (changed > 0) {
            data.markDirty();
            syncPlayer(player, progression);
            if (notify) {
                player.addChatMessage(new ChatComponentText("KOME Progression auto-completed " + changed + " step" + (changed == 1 ? "." : "s.")));
            }
        }
        return changed;
    }

    public static int applyUnlocks(KOMEPlayerProgression progression) {
        int changed = 0;

        changed += grantAfter(progression, "wanderer.expert_traveler", "baseline.fast_travel");
        changed += grantAfter(progression, "wanderer.red_flower", "baseline.fire");
        changed += grantAfter(progression, "wanderer.masonry", "baseline.stonework");
        changed += grantAfter(progression, "wanderer.learn_to_cook", "baseline.cooking");
        changed += grantAfter(progression, "wanderer.smoke_drink", "baseline.alcohol_pipeweed");
        changed += grantAfter(progression, "wanderer.find_serf_lord", "baseline.hunting", "baseline.house_waypoints", "baseline.farming");

        changed += grantAfter(progression, "serf.bartering", "baseline.npc_trade");
        changed += grantAfter(progression, "serf.quest_seeker", "baseline.miniquests");
        changed += grantAfter(progression, "serf.smithery", "baseline.non_faction_gear");
        changed += grantAfter(progression, "serf.brewing", "baseline.brewing");
        changed += grantAfter(progression, "serf.non_faction_armor", "baseline.non_faction_armor");
        changed += grantAfter(progression, "serf.tame_ride", "baseline.mounts");
        changed += grantAfter(progression, "serf.pledge", "baseline.pledge");
        changed += grantAfter(progression, "serf.title_knight", "baseline.faction_armor", "baseline.hire_units");

        changed += grantAfter(progression, "knight.fanny_pack", "baseline.pouches");
        changed += grantAfter(progression, "knight.meat", "baseline.meat");
        changed += grantAfter(progression, "knight.craftsman", "baseline.faction_gear");
        changed += grantAfter(progression, "knight.fellowship", "baseline.fellowship");
        changed += grantAfter(progression, "knight.title_lord", "baseline.take_waypoints", "baseline.reclaim_waypoints");

        changed += grantAfter(progression, "lord.redstone", "baseline.redstone");
        changed += grantAfter(progression, "lord.smithing_apprentice", "baseline.scrolls_modifiers");
        changed += grantAfter(progression, "lord.early_beginnings", "baseline.grow_population");
        changed += grantAfter(progression, "lord.protective_banners", "baseline.protective_banners");

        changed += grantAfter(progression, "prince_king.new_growth", "baseline.grow_population");
        changed += grantAfter(progression, "prince_king.this_is_war", "baseline.free_war");
        changed += grantAfter(progression, "prince_king.true_silver", "baseline.mithril_gear");
        changed += grantAfter(progression, "prince_king.master_smith", "baseline.enchanted_books");

        return changed;
    }

    public static int recomputeUnlocks(KOMEPlayerProgression progression) {
        int changed = 0;
        for (String id : DERIVED_BASELINE_UNLOCKS) {
            if (progression.revoke(id)) {
                changed++;
            }
        }
        return changed + applyUnlocks(progression);
    }

    public static void syncPlayer(EntityPlayerMP player, KOMEPlayerProgression progression) {
        List completed = new ArrayList();
        for (KOMEProgressionAchievement achievement : KOMEProgressionAchievement.ALL) {
            if (progression.isCompleted(achievement)) {
                completed.add(achievement.id);
            }
        }
        KOMEPacketHandler.network.sendTo(new KOMEPacketProgressionData(player.getCommandSenderName(), completed, progression.getAssignments()), player);
    }

    private static int grantAfter(KOMEPlayerProgression progression, String requiredID, String... unlockedIDs) {
        KOMEProgressionAchievement required = KOMEProgressionAchievement.forID(requiredID);
        if (!progression.isCompleted(required)) {
            return 0;
        }
        int changed = 0;
        for (String id : unlockedIDs) {
            changed += grantIf(progression, id, true);
        }
        return changed;
    }

    private static int grantIf(KOMEPlayerProgression progression, String id, boolean condition) {
        return condition && progression.grant(id) ? 1 : 0;
    }

    private static boolean isComplete(KOMEPlayerProgression progression, String id) {
        return progression.isCompleted(KOMEProgressionAchievement.forID(id));
    }

    private static void addDerived(String... ids) {
        for (String id : ids) {
            DERIVED_BASELINE_UNLOCKS.add(id);
        }
    }

    private static boolean hasAchievement(LOTRPlayerData data, LOTRAchievement achievement) {
        return achievement != null && data.hasAchievement(achievement);
    }

    private static boolean hasAnyAlignmentAtLeast(LOTRPlayerData data, float amount) {
        for (LOTRFaction faction : LOTRFaction.values()) {
            if (faction.isPlayableAlignmentFaction() && data.getAlignment(faction) >= amount) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEveryPlayableAlignmentAtLeastAbsolute(LOTRPlayerData data, float amount) {
        boolean checked = false;
        for (LOTRFaction faction : LOTRFaction.values()) {
            if (!faction.isPlayableAlignmentFaction()) {
                continue;
            }
            checked = true;
            if (Math.abs(data.getAlignment(faction)) < amount) {
                return false;
            }
        }
        return checked;
    }

    private static boolean hasAssignedFactionAlignment(LOTRPlayerData data, String assignment) {
        LOTRFaction faction = KOMEProgressionFactionQuotas.getAssignedFaction(assignment);
        return faction != null && Math.abs(data.getAlignment(faction)) >= KOMEProgressionFactionQuotas.getThreshold(faction);
    }

    private static boolean hasFellBeastAchievement(LOTRPlayerData data, String assignment) {
        String normalized = normalize(assignment);
        if (normalized.contains("balrog")) {
            return hasAchievement(data, LOTRAchievement.killBalrog);
        }
        if (normalized.contains("forest") || normalized.contains("chieftain")) {
            return hasAchievement(data, LOTRAchievement.killMallornEnt)
                || hasAchievement(data, LOTRAchievement.killMountainTrollChieftain);
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
