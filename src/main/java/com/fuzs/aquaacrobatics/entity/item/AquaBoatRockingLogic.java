package com.fuzs.aquaacrobatics.entity.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

import com.fuzs.aquaacrobatics.config.ConfigHandler;

/** Shared EntityBoat bubble-column rocking state and policy. */
public final class AquaBoatRockingLogic {

    private static final Map<EntityBoat, RockingState> ROCKING_STATES = Collections
        .synchronizedMap(new WeakHashMap<EntityBoat, RockingState>());

    private AquaBoatRockingLogic() {}

    public static void onEnterBubbleColumnWithAirAbove(EntityBoat boat, boolean downwards) {
        RockingState state = state(boat);
        IAquaBoatVanillaAccess access = (IAquaBoatVanillaAccess) boat;
        if (!boat.worldObj.isRemote) {
            state.rocking = true;
            state.rockingDownwards = downwards;
            if (getRockingTicks(boat) == 0) setRockingTicks(boat, 60);
        }

        boat.worldObj.spawnParticle(
            "splash",
            boat.posX + (double) access.aqua$getRandom().nextFloat(),
            boat.posY + 0.7D,
            boat.posZ + (double) access.aqua$getRandom().nextFloat(),
            0.0D,
            0.0D,
            0.0D);
        if (access.aqua$getRandom().nextInt(20) == 0) {
            boat.worldObj.playSound(
                boat.posX,
                boat.posY,
                boat.posZ,
                access.aqua$getSplashSound(),
                1.0F,
                0.8F + 0.4F * access.aqua$getRandom().nextFloat(),
                false);
        }
    }

    public static void registerData(EntityBoat boat) {
        boat.getDataWatcher().addObject(ConfigHandler.MiscellaneousConfig.BoatId, 0);
    }

    public static void updateRocking(EntityBoat boat) {
        RockingState state = state(boat);
        if (boat.worldObj.isRemote) {
            int ticks = getRockingTicks(boat);
            if (ticks > 0) {
                state.rockingIntensity += 0.05F;
            } else {
                state.rockingIntensity -= 0.1F;
            }

            state.rockingIntensity = MathHelper.clamp_float(state.rockingIntensity, 0.0F, 1.0F);
            state.previousRockingAngle = state.rockingAngle;
            state.rockingAngle = 10.0F * (float) Math.sin((double) (0.5F * (float) boat.worldObj.getTotalWorldTime()))
                * state.rockingIntensity;
        } else {
            if (!state.rocking) setRockingTicks(boat, 0);

            int ticks = getRockingTicks(boat);
            if (ticks > 0) {
                --ticks;
                setRockingTicks(boat, ticks);
                int elapsedTicks = 60 - ticks - 1;
                if (elapsedTicks > 0 && ticks == 0) {
                    setRockingTicks(boat, 0);
                    if (state.rockingDownwards) {
                        boat.motionY -= 0.7D;
                        removePassengers(boat);
                    } else {
                        boat.motionY = isPlayerRiding(boat) ? 2.7D : 0.6D;
                    }
                }

                state.rocking = false;
            }
        }
    }

    public static float getRockingAngle(EntityBoat boat, float partialTicks) {
        if (!ConfigHandler.MiscellaneousConfig.bubbleColumns) return 0.0F;
        RockingState state = state(boat);
        return state.previousRockingAngle + (state.rockingAngle - state.previousRockingAngle) * partialTicks;
    }

    private static void setRockingTicks(EntityBoat boat, int ticks) {
        if (!ConfigHandler.MiscellaneousConfig.bubbleColumns) return;
        boat.getDataWatcher().updateObject(ConfigHandler.MiscellaneousConfig.BoatId, ticks);
    }

    private static int getRockingTicks(EntityBoat boat) {
        if (!ConfigHandler.MiscellaneousConfig.bubbleColumns) return 0;
        return boat.getDataWatcher().getWatchableObjectInt(ConfigHandler.MiscellaneousConfig.BoatId);
    }

    private static boolean isPlayerRiding(EntityBoat boat) {
        for (Entity entity : passengers(boat)) {
            if (EntityPlayer.class.isAssignableFrom(entity.getClass())) return true;
        }
        return false;
    }

    private static void removePassengers(EntityBoat boat) {
        if (boat.riddenByEntity != null) {
            boat.riddenByEntity.mountEntity(null);
            boat.riddenByEntity = null;
        }
    }

    private static List<Entity> passengers(EntityBoat boat) {
        List<Entity> passengers = new ArrayList<Entity>();
        if (boat.riddenByEntity != null) passengers.add(boat.riddenByEntity);
        return passengers;
    }

    private static RockingState state(EntityBoat boat) {
        synchronized (ROCKING_STATES) {
            RockingState state = ROCKING_STATES.get(boat);
            if (state == null) {
                state = new RockingState();
                ROCKING_STATES.put(boat, state);
            }
            return state;
        }
    }

    private static final class RockingState {

        boolean rocking;
        boolean rockingDownwards;
        float rockingIntensity;
        float rockingAngle;
        float previousRockingAngle;
    }
}
