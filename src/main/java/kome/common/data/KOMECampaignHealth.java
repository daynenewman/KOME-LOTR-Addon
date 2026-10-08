package kome.common.data;

import kome.common.KOMEReflection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;

/** Exact survivor health authority, separate from potentially stale stationary entity geometry/AI NBT. */
public final class KOMECampaignHealth {
    private KOMECampaignHealth() { }

    public static NBTTagCompound capture(Entity entity) {
        NBTTagCompound health = new NBTTagCompound();
        health.setString("EntityId", entity.getUniqueID().toString());
        if (entity instanceof EntityLivingBase) health.setFloat("Current", ((EntityLivingBase) entity).getHealth());
        Entity mount = KOMEReflection.getRidingEntity(entity);
        if (mount != null) health.setTag("Riding", capture(mount));
        return health;
    }

    public static boolean observe(KOMEHiredUnitRecord record, Entity entity) {
        if (record == null || !KOMEHiredUnitClassification.isCampaignUnit(record) || entity == null) return false;
        record.healthObservedEntity = new java.lang.ref.WeakReference<Entity>(entity);
        Entity mount = KOMEReflection.getRidingEntity(entity);
        Entity previouslyObserved = record.healthObservedMount == null ? null : record.healthObservedMount.get();
        if (mount != null && mount != previouslyObserved && record.survivingHealth != null
                && record.survivingHealth.hasKey("Riding", 10)) {
            NBTTagCompound known = record.survivingHealth.getCompoundTag("Riding");
            // A mount can be attached AFTER the rider's EntityJoinWorld event. Apply its saved
            // health once when that same physical identity first becomes observable again.
            if (!known.hasKey("EntityId") || mount.getUniqueID().toString().equals(known.getString("EntityId")))
                restoreLoaded(mount, known);
        }
        record.healthObservedMount = mount == null ? null : new java.lang.ref.WeakReference<Entity>(mount);
        NBTTagCompound health = capture(entity);
        // EntityJoinWorld can precede attaching the reloaded mount. Do not discard its authority.
        if (record.mounted && !health.hasKey("Riding") && record.survivingHealth != null
                && record.survivingHealth.hasKey("Riding", 10))
            health.setTag("Riding", record.survivingHealth.getCompoundTag("Riding").copy());
        if (health.equals(record.survivingHealth)) return false;
        record.survivingHealth = health;
        return true;
    }

    /** At save time use the actual still-tracked entity, not last tick's observation. */
    public static void refresh(KOMEHiredUnitRecord record) {
        Entity entity = record.healthObservedEntity == null ? null : record.healthObservedEntity.get();
        if (entity != null && (!entity.isDead || entity instanceof EntityLivingBase
                && ((EntityLivingBase) entity).getHealth() <= 0F)) observe(record, entity);
    }

    public static float exact(NBTTagCompound snapshot) {
        // Vanilla 1.7.10 writes HealF as float and Health as rounded-up short.
        String key = snapshot.hasKey("HealF") ? "HealF" : "Health";
        if (!snapshot.hasKey(key, 99)) throw new IllegalArgumentException("Missing survivor current health");
        float health = snapshot.getFloat(key);
        requirePositive(health);
        return health;
    }

    private static void requirePositive(float health) {
        if (Float.isNaN(health) || Float.isInfinite(health) || health <= 0.0F)
            throw new IllegalArgumentException("Invalid survivor current health; recovery requires a valid live observation");
    }

    /** Detached data used by every movement reconstruction/retry. Never invent Max HP. */
    public static NBTTagCompound movementSnapshot(KOMEHiredUnitRecord record, NBTTagCompound stored) {
        if (stored == null) throw new IllegalArgumentException("Missing survivor snapshot");
        refresh(record);
        NBTTagCompound result = (NBTTagCompound) stored.copy();
        applyToSnapshot(result, record.survivingHealth);
        return result;
    }

    private static void applyToSnapshot(NBTTagCompound snapshot, NBTTagCompound health) {
        float value;
        if (health != null) {
            if (!health.hasKey("Current", 5)) throw new IllegalArgumentException("Missing authoritative survivor current health");
            value = health.getFloat("Current");
            requirePositive(value);
        } else value = exact(snapshot); // compatibility with a valid pre-9 snapshot, never missing/corrupt data
        snapshot.setFloat("HealF", value);
        snapshot.setFloat("Health", value);
        if (snapshot.hasKey("Riding", 10)) {
            if (health != null && !health.hasKey("Riding", 10))
                throw new IllegalArgumentException("Mount survivor health is unresolved");
            applyToSnapshot(snapshot.getCompoundTag("Riding"), health == null ? null : health.getCompoundTag("Riding"));
        } else if (health != null && health.hasKey("Riding")) {
            throw new IllegalArgumentException("Survivor mount snapshot is missing");
        }
    }

    /** A reloaded stale entity must not heal a fresher persisted observation. */
    public static void reconcileLoaded(KOMEHiredUnitRecord record, Entity entity) {
        if (record == null || record.survivingHealth == null) { observe(record, entity); return; }
        restoreLoaded(entity, record.survivingHealth);
        observe(record, entity);
    }

    private static void restoreLoaded(Entity entity, NBTTagCompound health) {
        if (entity instanceof EntityLivingBase) {
            float known = health.getFloat("Current");
            // Nonpositive metadata cannot be interpreted as a healthy survivor, even on reload.
            ((EntityLivingBase) entity).setHealth(Float.isNaN(known) || Float.isInfinite(known) ? 0F
                : Math.min(((EntityLivingBase) entity).getHealth(), Math.max(0F, known)));
        }
        Entity mount = KOMEReflection.getRidingEntity(entity);
        if (mount != null && health.hasKey("Riding", 10)) restoreLoaded(mount, health.getCompoundTag("Riding"));
    }
}
