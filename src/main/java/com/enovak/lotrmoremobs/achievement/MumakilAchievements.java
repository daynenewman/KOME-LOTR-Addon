package com.enovak.lotrmoremobs.achievement;

import com.enovak.lotrmoremobs.Main;
import lotr.common.LOTRAchievement;
import lotr.common.LOTRLevelData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Addon achievements registered in LOTR's Near Harad category.
 */
public final class MumakilAchievements {
    public static final int BREED_CALF_ID = 1200;
    public static final int SLAY_MUMAK_ID = 1201;
    public static final int HIRE_FORMATION_ID = 1202;
    public static final int TRAVEL_MUMAK_ID = 1203;

    public static LOTRAchievement breedCalf;
    public static LOTRAchievement slayMumak;
    public static LOTRAchievement hireFormation;
    public static LOTRAchievement travelOnMumak;

    private static boolean registered;

    private MumakilAchievements() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        registered = true;
        LOTRAchievement.Category category =
                LOTRAchievement.Category.NEAR_HARAD;
        breedCalf = registerOne(
                category,
                BREED_CALF_ID,
                Main.mumakilCalfSpawnEgg,
                "lotrmoremobsBreedMumakCalf"
        );
        slayMumak = registerOne(
                category,
                SLAY_MUMAK_ID,
                Main.mumakilTusk,
                "lotrmoremobsSlayMumak"
        );
        hireFormation = registerOne(
                category,
                HIRE_FORMATION_ID,
                Main.mumakilHowdah,
                "lotrmoremobsHireMumakHowdah"
        );
        travelOnMumak = registerOne(
                category,
                TRAVEL_MUMAK_ID,
                Items.saddle,
                "lotrmoremobsTravelMumak"
        );
    }

    private static LOTRAchievement registerOne(
            LOTRAchievement.Category category,
            int id,
            Item icon,
            String codeName
    ) {
        LOTRAchievement existing =
                LOTRAchievement.achievementForCategoryAndID(category, id);
        if (existing != null) {
            if (codeName.equals(existing.getCodeName())) {
                return existing;
            }
            System.err.println(
                    "[LOTRMoreMobs] Mumak achievement ID collision: "
                            + category.name()
                            + "/"
                            + id
            );
            return null;
        }

        return new LOTRAchievement(
                category,
                id,
                new ItemStack(icon),
                codeName
        );
    }

    public static void award(
            EntityPlayer player,
            LOTRAchievement achievement
    ) {
        if (player == null
                || player.worldObj == null
                || player.worldObj.isRemote
                || achievement == null) {
            return;
        }
        LOTRLevelData.getData(player).addAchievement(achievement);
    }
}
