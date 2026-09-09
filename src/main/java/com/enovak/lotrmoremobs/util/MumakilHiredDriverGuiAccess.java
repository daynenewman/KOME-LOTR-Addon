package com.enovak.lotrmoremobs.util;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public final class MumakilHiredDriverGuiAccess {
    public static final double HIRED_INTERACT_DISTANCE_SQ = 100.0D;
    public static final double HIRED_COMMAND_DISTANCE_SQ = 64.0D;
    public static final double HIRED_DRIVER_INVENTORY_DISTANCE_SQ = 144.0D;

    private MumakilHiredDriverGuiAccess() {
    }

    public static LOTREntityMumakil getMountedMumakilForDriver(LOTREntityNPC driver) {
        if (driver == null || !driver.isEntityAlive()) {
            return null;
        }

        Entity mount = driver.ridingEntity;
        if (!(mount instanceof LOTREntityMumakil)) {
            return null;
        }

        LOTREntityMumakil mumakil = (LOTREntityMumakil)mount;
        if (!mumakil.isEntityAlive() || mumakil.riddenByEntity != driver) {
            return null;
        }

        LOTRHiredNPCInfo hiredInfo = driver.hiredNPCInfo;
        if (hiredInfo == null
                || !hiredInfo.isActive
                || hiredInfo.getTask() != LOTRHiredNPCInfo.Task.WARRIOR) {
            return null;
        }

        return mumakil;
    }

    public static boolean canUseClientDriverGui(EntityPlayer player, LOTREntityNPC driver, LOTREntityMumakil mumakil, double maxDistanceSq) {
        return canUseDriverGui(player, driver, mumakil, maxDistanceSq, false);
    }

    public static boolean canUseServerDriverGui(EntityPlayer player, LOTREntityNPC driver, LOTREntityMumakil mumakil, double maxDistanceSq) {
        return canUseDriverGui(player, driver, mumakil, maxDistanceSq, true);
    }

    private static boolean canUseDriverGui(EntityPlayer player, LOTREntityNPC driver, LOTREntityMumakil mumakil, double maxDistanceSq, boolean requireHiredWarMarker) {
        if (player == null || driver == null || mumakil == null) {
            return false;
        }

        if (!driver.isEntityAlive() || !mumakil.isEntityAlive()) {
            return false;
        }

        if (driver.ridingEntity != mumakil || mumakil.riddenByEntity != driver) {
            return false;
        }

        if (requireHiredWarMarker && !mumakil.isHiredWarMumakil()) {
            return false;
        }

        LOTRHiredNPCInfo hiredInfo = driver.hiredNPCInfo;
        if (hiredInfo == null
                || !hiredInfo.isActive
                || hiredInfo.getHiringPlayer() != player
                || hiredInfo.getTask() != LOTRHiredNPCInfo.Task.WARRIOR) {
            return false;
        }

        return player.getDistanceSqToEntity(mumakil) <= maxDistanceSq;
    }
}
