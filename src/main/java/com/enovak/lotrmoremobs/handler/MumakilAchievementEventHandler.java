package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.achievement.MumakilAchievements;
import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.entity.animal.MumakilFormationOrigin;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.Map;
import java.util.WeakHashMap;
import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

public final class MumakilAchievementEventHandler {
    public static final String TRAVEL_PROGRESS_NBT_KEY =
            "lotrmoremobs_mumakTravelBlocks";
    private static final String HIRING_AWARD_NBT_KEY =
            "lotrmoremobs_hiringAchievementAwarded";
    private static final String HIRING_AWARD_PENDING_NBT_KEY =
            "lotrmoremobs_hiringAchievementPending";
    private static final double TRAVEL_TARGET_BLOCKS = 5000.0D;
    private static final double MAX_NORMAL_TICK_TRAVEL = 8.0D;
    private static final Map<EntityPlayer, TravelSample> TRAVEL_SAMPLES =
            new WeakHashMap<EntityPlayer, TravelSample>();

    @SubscribeEvent
    public void onMumakUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!(event.entityLiving instanceof LOTREntityMumakil)
                || event.entityLiving.worldObj.isRemote
                || event.entityLiving.ticksExisted % 20 != 0) {
            return;
        }

        LOTREntityMumakil mumakil =
                (LOTREntityMumakil)event.entityLiving;
        if (mumakil.getFormationOrigin()
                != MumakilFormationOrigin.PLAYER_HIRED
                || !mumakil.getEntityData().getBoolean(
                HIRING_AWARD_PENDING_NBT_KEY
        )
                || mumakil.getEntityData().getBoolean(
                HIRING_AWARD_NBT_KEY
        )
                || !mumakil.hasMumakilSaddleEquipped()
                || !mumakil.hasMumakilHowdahEquipped()
                || !(mumakil.riddenByEntity instanceof LOTREntityNPC)
                || MumakilHowdahArcherEventHandler
                .getLiveAttachedHowdahArcherCount(mumakil)
                != 17) {
            return;
        }

        LOTREntityNPC driver =
                (LOTREntityNPC)mumakil.riddenByEntity;
        if (!driver.isEntityAlive()
                || !driver.hiredNPCInfo.isActive) {
            return;
        }

        mumakil.capturePlayerHiredFormationOwner(driver);
        EntityPlayer owner =
                mumakil.getOnlinePlayerHiredFormationOwner();
        if (owner == null) {
            return;
        }

        MumakilAchievements.award(
                owner,
                MumakilAchievements.hireFormation
        );
        mumakil.getEntityData().setBoolean(
                HIRING_AWARD_NBT_KEY,
                true
        );
        mumakil.getEntityData().removeTag(
                HIRING_AWARD_PENDING_NBT_KEY
        );
    }

    public static void markHiringAchievementPending(
            LOTREntityMumakil mumakil
    ) {
        if (mumakil != null
                && mumakil.worldObj != null
                && !mumakil.worldObj.isRemote
                && mumakil.getFormationOrigin()
                == MumakilFormationOrigin.PLAYER_HIRED) {
            mumakil.getEntityData().setBoolean(
                    HIRING_AWARD_PENDING_NBT_KEY,
                    true
            );
        }
    }

    @SubscribeEvent
    public void onMumakDeath(LivingDeathEvent event) {
        if (!(event.entityLiving instanceof LOTREntityMumakil)
                || event.entityLiving.worldObj.isRemote
                || event.entityLiving.getEntityData().getBoolean(
                MumakilFormationCreditEventHandler
                        .CREDIT_SOURCE_REWRITTEN_KEY
        )) {
            return;
        }

        Entity attacker =
                event.source == null ? null : event.source.getEntity();
        if (attacker instanceof EntityPlayer) {
            MumakilAchievements.award(
                    (EntityPlayer)attacker,
                    MumakilAchievements.slayMumak
            );
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.player == null
                || event.player.worldObj == null
                || event.player.worldObj.isRemote) {
            return;
        }

        EntityPlayer player = event.player;
        if (LOTRLevelData.getData(player).hasAchievement(
                MumakilAchievements.travelOnMumak
        )) {
            TRAVEL_SAMPLES.remove(player);
            return;
        }

        if (!(player.ridingEntity instanceof LOTREntityMumakil)) {
            TRAVEL_SAMPLES.remove(player);
            return;
        }

        LOTREntityMumakil mumakil =
                (LOTREntityMumakil)player.ridingEntity;
        TravelSample previous = TRAVEL_SAMPLES.get(player);
        if (previous == null
                || previous.dimension != player.dimension
                || previous.mountEntityId != mumakil.getEntityId()) {
            TRAVEL_SAMPLES.put(
                    player,
                    new TravelSample(
                            player.posX,
                            player.posZ,
                            player.dimension,
                            mumakil.getEntityId()
                    )
            );
            return;
        }

        double dx = player.posX - previous.x;
        double dz = player.posZ - previous.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        previous.x = player.posX;
        previous.z = player.posZ;

        /*
         * Normal riding is well below this limit. Command-horn teleports,
         * dimension transfers, chunk corrections, and reposition jumps are
         * discarded as a whole instead of inflating progress.
         */
        if (horizontalDistance <= 0.0D
                || horizontalDistance > MAX_NORMAL_TICK_TRAVEL) {
            return;
        }

        NBTTagCompound persisted = getPersistentPlayerData(player);
        double progress =
                persisted.getDouble(TRAVEL_PROGRESS_NBT_KEY)
                        + horizontalDistance;
        persisted.setDouble(
                TRAVEL_PROGRESS_NBT_KEY,
                Math.min(progress, TRAVEL_TARGET_BLOCKS)
        );
        if (progress >= TRAVEL_TARGET_BLOCKS) {
            MumakilAchievements.award(
                    player,
                    MumakilAchievements.travelOnMumak
            );
            TRAVEL_SAMPLES.remove(player);
        }
    }

    private static NBTTagCompound getPersistentPlayerData(
            EntityPlayer player
    ) {
        NBTTagCompound entityData = player.getEntityData();
        if (!entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            entityData.setTag(
                    EntityPlayer.PERSISTED_NBT_TAG,
                    new NBTTagCompound()
            );
        }
        return entityData.getCompoundTag(
                EntityPlayer.PERSISTED_NBT_TAG
        );
    }

    private static final class TravelSample {
        private double x;
        private double z;
        private final int dimension;
        private final int mountEntityId;

        private TravelSample(
                double x,
                double z,
                int dimension,
                int mountEntityId
        ) {
            this.x = x;
            this.z = z;
            this.dimension = dimension;
            this.mountEntityId = mountEntityId;
        }
    }
}
