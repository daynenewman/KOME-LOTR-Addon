package kome.common.data;

import kome.common.KOMEReflection;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class KOMEHaltedUnitProtection {
    private static final long COMBAT_HALT_COOLDOWN_MILLIS = 15000L;
    private static final Map<UUID, Long> lastCombatMillisByUnit = new HashMap<UUID, Long>();
    private static boolean applyingInactiveState;

    private KOMEHaltedUnitProtection() {
    }

    public static boolean canApplyHornHalt(LOTREntityNPC npc) {
        if (!isActiveHiredWarrior(npc)) {
            return true;
        }
        if (npc.getAttackTarget() != null || npc.hiredNPCInfo.inCombat || npc.hurtTime > 0) {
            return false;
        }
        Long lastCombat = lastCombatMillisByUnit.get(KOMEReflection.getEntityUUID(npc));
        return lastCombat == null || System.currentTimeMillis() - lastCombat.longValue() >= COMBAT_HALT_COOLDOWN_MILLIS;
    }

    public static void onHornHalted(LOTREntityNPC npc) {
        applyInactiveState(npc);
    }

    public static void onHornReady(LOTREntityNPC npc) {
    }

    public static boolean isProtected(LOTREntityNPC npc) {
        return isActiveHiredWarrior(npc) && npc.hiredNPCInfo.isHalted();
    }

    public static boolean isProtectedRecord(KOMEWorldData data, World world, KOMEHiredUnitRecord record) {
        if (record == null || record.farmhand) {
            return false;
        }
        EntityLivingBase live = world == null ? null : findLiveEntity(world, record.entity);
        if (live instanceof LOTREntityNPC) {
            return isProtected((LOTREntityNPC) live);
        }
        return isHaltedSnapshot(record.stationedEntityData) || isHaltedSnapshot(record.movingEntityData);
    }

    public static void noteCombat(EntityLivingBase entity) {
        if (entity instanceof LOTREntityNPC && isActiveHiredWarrior((LOTREntityNPC) entity)) {
            lastCombatMillisByUnit.put(KOMEReflection.getEntityUUID(entity), System.currentTimeMillis());
        }
    }

    public static void applyInactiveState(LOTREntityNPC npc) {
        if (npc == null) {
            return;
        }
        if (applyingInactiveState) {
            return;
        }
        applyingInactiveState = true;
        try {
            if (npc.isBurning()) {
                npc.extinguish();
            }
            if (npc.fallDistance != 0.0F) {
                npc.fallDistance = 0.0F;
            }
            if (npc.getAITarget() != null) {
                npc.setRevengeTarget(null);
            }
            if (npc.getAttackTarget() != null) {
                npc.setAttackTarget(null);
            }
            if (!npc.getNavigator().noPath()) {
                npc.getNavigator().clearPathEntity();
            }
            if (npc.ridingEntity instanceof EntityLivingBase) {
                EntityLivingBase mount = (EntityLivingBase) npc.ridingEntity;
                if (mount.isBurning()) {
                    mount.extinguish();
                }
                if (mount.fallDistance != 0.0F) {
                    mount.fallDistance = 0.0F;
                }
                if (mount instanceof LOTREntityNPC) {
                    LOTREntityNPC mountNpc = (LOTREntityNPC) mount;
                    if (mountNpc.getAITarget() != null) {
                        mountNpc.setRevengeTarget(null);
                    }
                    if (mountNpc.getAttackTarget() != null) {
                        mountNpc.setAttackTarget(null);
                    }
                    if (!mountNpc.getNavigator().noPath()) {
                        mountNpc.getNavigator().clearPathEntity();
                    }
                }
            }
        } finally {
            applyingInactiveState = false;
        }
    }

    public static boolean isApplyingInactiveState() {
        return applyingInactiveState;
    }

    private static boolean isActiveHiredWarrior(LOTREntityNPC npc) {
        if (npc == null || npc.worldObj == null || npc.worldObj.isRemote || npc.hiredNPCInfo == null
                || !npc.hiredNPCInfo.isActive || npc.hiredNPCInfo.getTask() != LOTRHiredNPCInfo.Task.WARRIOR) {
            return false;
        }
        return true;
    }

    private static EntityLivingBase findLiveEntity(World world, UUID uuid) {
        if (world == null || uuid == null) {
            return null;
        }
        for (Object object : world.loadedEntityList) {
            if (object instanceof EntityLivingBase && uuid.equals(KOMEReflection.getEntityUUID((EntityLivingBase) object))) {
                return (EntityLivingBase) object;
            }
        }
        return null;
    }

    private static boolean isHaltedSnapshot(NBTTagCompound snapshot) {
        if (snapshot == null || !snapshot.hasKey("HiredNPCInfo", 10)) {
            return false;
        }
        NBTTagCompound info = snapshot.getCompoundTag("HiredNPCInfo");
        return info.getBoolean("IsActive")
            && info.getInteger("Task") == LOTRHiredNPCInfo.Task.WARRIOR.ordinal()
            && !info.getBoolean("GuardMode")
            && !info.getBoolean("CanMove");
    }

    private static void snapshotTrackedUnit(LOTREntityNPC npc) {
        if (npc == null || npc.worldObj == null || npc.worldObj.isRemote) {
            return;
        }
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(npc));
        KOMEHiredUnitRecord record = data.hiredUnits.get(KOMEReflection.getEntityUUID(npc));
        if (record != null && !record.isMoving()) {
            record.stationedEntityData = KOMEEntitySnapshots.snapshot(npc);
            data.markDirty();
        }
    }
}
