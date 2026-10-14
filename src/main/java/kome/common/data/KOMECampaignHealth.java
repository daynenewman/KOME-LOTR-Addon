package kome.common.data;

import java.util.*;
import java.lang.ref.WeakReference;
import kome.common.KOMEReflection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import static kome.common.data.KOMECampaignHealthProjection.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.Component;

/** Versioned Campaign health, independent of stationary geometry and ConflictData's obligation layout. */
public final class KOMECampaignHealth {
    private KOMECampaignHealth() { }
    static boolean versioned(NBTTagCompound n) { return n != null && n.hasKey("HealthFormatVersion"); }
    public static UUID logicalTarget(NBTTagCompound n, UUID alias, Component kind) {
        if (!versioned(n)) return null;
        KOMECampaignHealthProjection p = read(n);
        require(p.alias.equals(alias) && p.component == kind, "Participant health binding mismatch"); return p.target;
    }
    private static KOMEOrdinaryBattleRecoveryRegistry.Target known(Entity entity, Component kind) {
        if (entity.worldObj == null) return null;
        KOMEOrdinaryBattleRecoveryRegistry.Target t = KOMEWorldData.get(entity.worldObj).getOrdinaryBattleRecovery().targetForPhysical(entity.getUniqueID());
        if (t != null) require(t.component == kind && t.physicalId.equals(entity.getUniqueID()) && !t.terminal,
            "Stale/terminal ConflictData target alias");
        return t;
    }
    private static KOMECampaignHealthProjection reconcile(KOMEHiredUnitRecord record, Entity entity,
            NBTTagCompound saved, Component kind) {
        require(entity instanceof EntityLivingBase, "Campaign target is not living");
        require(KOMERecoveryStartup.healthReady(entity), "Recovery reconciliation is not ready");
        KOMECampaignHealthProjection initial = KOMECampaignHealthEntity.projection(entity);
        require(initial == null || KOMERecoveryStartup.validateProjection(entity, initial), "Entity health is quarantined");
        require(!versioned(saved) || KOMERecoveryStartup.validateProjection(entity, read(saved)), "Saved health is quarantined");
        KOMECampaignHealthEntity.changed(entity);
        KOMECampaignHealthProjection live = KOMECampaignHealthEntity.projection(entity);
        KOMECampaignHealthProjection disk = versioned(saved) ? read(saved) : null;
        KOMEOrdinaryBattleRecoveryRegistry.Target target = known(entity, kind);
        if (disk != null) require(disk.alias.equals(entity.getUniqueID()) && disk.component == kind, "Persisted health alias mismatch");
        if (live != null) require(live.alias.equals(entity.getUniqueID()) && live.component == kind, "Entity health alias mismatch");
        KOMECampaignHealthProjection chosen;
        if (disk != null && live != null) chosen = reconcile(record,disk,live);
        else if (disk != null) {
            // Missing acknowledgment is not proof that a live projection is older.
            require(Float.compare(((EntityLivingBase)entity).getHealth(), disk.hp) == 0 && disk.fence == null,
                "Unacknowledged entity health is ambiguous"); chosen = disk;
        } else if (live != null) chosen = live;
        else {
            float hp = ((EntityLivingBase)entity).getHealth();
            if (saved != null) {
                require(saved.hasKey("EntityId", 8) && entity.getUniqueID().toString().equals(saved.getString("EntityId"))
                    && saved.hasKey("Current", 5) && Float.compare(saved.getFloat("Current"), hp) == 0,
                    "Legacy health has no coherent live ordering");
            }
            chosen = new KOMECampaignHealthProjection(target == null ? UUID.randomUUID() : target.id, kind,
                entity.getUniqueID(), hp, 0, UUID.randomUUID(), false, null);
        }
        require(target == null || target.id.equals(chosen.target), "ConflictData logical health identity mismatch");
        require(!chosen.terminal, "Terminal target cannot rejoin/reconstruct");
        KOMECampaignHealthEntity.install(entity, chosen, record, Float.compare(((EntityLivingBase)entity).getHealth(), chosen.hp) != 0);
        return chosen;
    }
    public static boolean observe(KOMEHiredUnitRecord record, Entity entity) {
        if (record == null || !KOMEHiredUnitClassification.isCampaignUnit(record) || entity == null) return false;
        if (!KOMERecoveryStartup.healthReady(entity)) return false;
        bind(record, entity);
        if (entity.worldObj != null && !KOMERecoveryStartup.permitsTarget(KOMEWorldData.get(entity.worldObj), entity.getUniqueID())) return false;
        KOMECampaignHealthProjection live = KOMECampaignHealthEntity.projection(entity);
        if (live != null && !KOMERecoveryStartup.validateProjection(entity, live)
            || versioned(record.survivingHealth) && !KOMERecoveryStartup.validateProjection(entity, read(record.survivingHealth))) return false;
        if (versioned(record.survivingHealth)) {
            KOMECampaignHealthProjection saved = read(record.survivingHealth);
            if ((live == null || saved.revision > live.revision) && entity instanceof EntityLivingBase
                    && saved.hp != ((EntityLivingBase)entity).getHealth() && !KOMERecoveryStartup.mayInstallHP(entity, saved)) return false;
        }
        require(record.entity.equals(entity.getUniqueID()), "Campaign record physical alias mismatch");
        NBTTagCompound before = record.survivingHealth == null ? null : (NBTTagCompound)record.survivingHealth.copy();
        NBTTagCompound associated = KOMECampaignAssociationIndex.mount(record);
        Entity previouslyObserved = record.healthObservedEntity == null ? null : record.healthObservedEntity.get();
        KOMECampaignHealthProjection rider = reconcile(record, entity, record.survivingHealth, Component.RIDER);
        updateProjection(record, rider); record.healthObservedEntity = new WeakReference<Entity>(entity);
        // Preserve deployment-proven association even if first observation occurs dismounted.
        // Legacy HP remains legacy evidence; no new mount revision or physical effect is invented.
        if (associated != null && !record.survivingHealth.hasKey("Riding", 10))
            record.survivingHealth.setTag("Riding", associated.copy());
        Entity mount = KOMEReflection.getRidingEntity(entity);
        if (mount instanceof EntityLivingBase && KOMECampaignAssociationIndex.accepts(record, mount.getUniqueID())) {
            NBTTagCompound saved = mountHealth(record.survivingHealth, mount.getUniqueID());
            KOMECampaignHealthProjection p = reconcile(record, mount, saved, Component.MOUNT);
            updateProjection(record, p); record.survivingHealth.setTag("Riding", p.write());
            record.healthObservedMount = new WeakReference<Entity>(mount);
            record.survivingHealth.setString("AttachedMountAlias", mount.getUniqueID().toString());
        } else if (previouslyObserved == entity || !record.mounted || before == null || !before.hasKey("AttachedMountAlias", 8)) {
            record.survivingHealth.setString("AttachedMountAlias", "");
        } // EntityJoinWorld can precede attachment: keep the saved association unresolved until the next observation.
        if (record.healthAuthorityData != null) record.healthAuthorityData.campaignAssociations.track(record);
        return !record.survivingHealth.equals(before);
    }
    /** Preserve all previously associated mounts, including an independently surviving dismounted mount. */
    static void updateProjection(KOMEHiredUnitRecord record, KOMECampaignHealthProjection p) {
        NBTTagCompound old = record.survivingHealth;
        if (p.component == Component.RIDER) {
            if (versioned(old)) p = reconcile(record, read(old), p);
            NBTTagCompound next = p.write();
            if (old != null) for (String key : new String[]{"Riding", "Mounts", "AttachedMountAlias"}) if (old.hasKey(key)) next.setTag(key, old.getTag(key).copy());
            record.survivingHealth = next;
        } else {
            if (old == null) return;
            Map<UUID, KOMECampaignHealthProjection> mounts = mounts(old);
            if (mounts.containsKey(p.target)) p = reconcile(record, mounts.get(p.target), p);
            mounts.put(p.target, p);
            NBTTagCompound next = (NBTTagCompound)old.copy(); NBTTagList list = new NBTTagList();
            for (KOMECampaignHealthProjection m : mounts.values()) list.appendTag(m.write()); next.setTag("Mounts", list);
            if (old.hasKey("Riding", 10) && p.target.toString().equals(old.getCompoundTag("Riding").getString("LogicalTargetId")))
                next.setTag("Riding", p.write());
            record.survivingHealth = next;
        }
        if (record.healthAuthorityData != null) record.healthAuthorityData.campaignAssociations.track(record);
        if (record.healthAuthorityData != null && record.healthAuthorityData.recoveryStartup != null
                && record.healthAuthorityData.recoveryStartup.runtime != null)
            record.healthAuthorityData.recoveryStartup.runtime.track(record);
    }
    static KOMECampaignHealthProjection reconcile(KOMEHiredUnitRecord record, KOMECampaignHealthProjection a, KOMECampaignHealthProjection b) {
        KOMERecoveryStartup startup = record == null || record.healthAuthorityData == null ? null : record.healthAuthorityData.recoveryStartup;
        return startup == null ? KOMECampaignHealthProjection.reconcile(a,b) : startup.reconcileHealth(a,b);
    }
    private static void bind(KOMEHiredUnitRecord record, Entity entity) {
        if (entity.worldObj == null) return;
        KOMEWorldData data = KOMEWorldData.get(entity.worldObj);
        data.campaignAssociations.track(record);
        if (data.recoveryStartup != null && data.recoveryStartup.runtime != null) data.recoveryStartup.runtime.track(record);
        KOMERecoveryStartup.observeEntity(entity);
    }
    static KOMECampaignHealthProjection retained(KOMEHiredUnitRecord record, UUID target, Component component) {
        KOMECampaignHealthProjection p = component == Component.MOUNT ? mounts(record.survivingHealth).get(target)
            : versioned(record.survivingHealth) ? read(record.survivingHealth) : null;
        return p != null && p.target.equals(target) ? p : null;
    }
    private static Map<UUID, KOMECampaignHealthProjection> mounts(NBTTagCompound root) {
        Map<UUID, KOMECampaignHealthProjection> result = new TreeMap<UUID, KOMECampaignHealthProjection>();
        if (root == null) return result;
        if (root.hasKey("Mounts")) {
            type(root, "Mounts", 9); NBTTagList list = (NBTTagList)root.getTag("Mounts");
            require(list.tagCount() == 0 || list.func_150303_d() == 10, "Invalid mount history element type");
            require(list.tagCount() <= 4096, "Mount history exceeds bound");
            for (int i = 0; i < list.tagCount(); i++) {
                KOMECampaignHealthProjection p = read(list.getCompoundTagAt(i));
                require(p.component == Component.MOUNT && result.put(p.target, p) == null, "Duplicate/invalid mount history");
            }
        }
        if (versioned(root.getCompoundTag("Riding"))) {
            KOMECampaignHealthProjection p = read(root.getCompoundTag("Riding"));
            require(p.component == Component.MOUNT, "Invalid associated mount kind");
            if (result.containsKey(p.target)) require(result.get(p.target).write().equals(p.write()), "Contradictory selected mount projection");
            else result.put(p.target, p);
        }
        Set<UUID> aliases = new HashSet<UUID>();
        for (KOMECampaignHealthProjection p : result.values()) require(aliases.add(p.alias), "Duplicate mount alias");
        return result;
    }
    static NBTTagCompound mountHealth(NBTTagCompound root, UUID alias) {
        if (root == null) return null;
        for (KOMECampaignHealthProjection p : mounts(root).values()) if (p.alias.equals(alias)) return p.write();
        NBTTagCompound riding = root.hasKey("Riding", 10) ? root.getCompoundTag("Riding") : null;
        return riding != null && alias.toString().equals(riding.getString("EntityId")) ? riding : null;
    }
    public static void validate(NBTTagCompound root) {
        if (!versioned(root)) return;
        require(read(root).component == Component.RIDER, "Invalid rider kind"); mounts(root);
        if (root.hasKey("AttachedMountAlias")) {
            type(root, "AttachedMountAlias", 8);
            if (!root.getString("AttachedMountAlias").isEmpty()) {
                UUID alias = uuid(root, "AttachedMountAlias");
                require(mountHealth(root, alias) != null, "Attached mount lacks health lineage");
            }
        }
    }
    public static void refresh(KOMEHiredUnitRecord record) {
        Entity e = record.healthObservedEntity == null ? null : record.healthObservedEntity.get();
        if (e != null && !e.isDead && !terminal(record.survivingHealth)) observe(record, e);
        Entity m = record.healthObservedMount == null ? null : record.healthObservedMount.get();
        if (m != null) KOMECampaignHealthEntity.changed(m);
    }
    private static boolean terminal(NBTTagCompound n) { return versioned(n) && read(n).terminal; }
    public static void markTerminal(KOMEHiredUnitRecord record, UUID physical) {
        markTerminal(null, record, physical);
    }
    static void markTerminal(KOMEWorldData data, KOMEHiredUnitRecord record, UUID physical) {
        if (record == null) return;
        NBTTagCompound n = record.entity.equals(physical) ? record.survivingHealth : mountHealth(record.survivingHealth, physical);
        if (versioned(n)) {
            KOMECampaignHealthProjection p = read(n);
            updateProjection(record, data == null ? p.change(p.hp, true) : KOMERecoveryTargetJournal.terminalProjection(data, p));
        }
    }
    public static float exact(NBTTagCompound snapshot) {
        String key = snapshot.hasKey("HealF") ? "HealF" : "Health";
        require(snapshot.hasKey(key, 99), "Missing survivor current health"); float hp = snapshot.getFloat(key); positive(hp); return hp;
    }
    private static void positive(float hp) { require(Float.isFinite(hp) && hp > 0F, "Invalid survivor current health"); }
    public static NBTTagCompound movementSnapshot(KOMEHiredUnitRecord record, NBTTagCompound stored) {
        require(stored != null, "Missing survivor snapshot"); refresh(record);
        NBTTagCompound result = (NBTTagCompound)stored.copy(); applyToSnapshot(result, record.survivingHealth, record);
        return result;
    }
    private static void applyToSnapshot(NBTTagCompound snapshot, NBTTagCompound health, KOMEHiredUnitRecord record) {
        float hp;
        if (versioned(health)) {
            KOMECampaignHealthProjection p = read(health);
            KOMERecoveryStartup.requireDetachedReconciliation(p, false);
            if (p.component == Component.RIDER) {
                String attached = health.getString("AttachedMountAlias");
                if (snapshot.hasKey("Riding", 10)) {
                    require(health.hasKey("AttachedMountAlias", 8) && attached.equals(snapshotId(snapshot.getCompoundTag("Riding")).toString()),
                        "Geometry mount no longer matches current attachment");
                } else require(attached.isEmpty(), "Current mount geometry is missing");
            }
            require(!p.terminal && p.alias.equals(snapshotId(snapshot)), "Stale/terminal snapshot lineage"); hp = p.hp;
            if (snapshot.hasKey(ENTITY_TAG)) {
                type(snapshot, ENTITY_TAG, 10);
                p = reconcile(record,p,read(snapshot.getCompoundTag(ENTITY_TAG)));
                require(!p.terminal, "Terminal snapshot cannot reconstruct"); hp = p.hp;
            }
            snapshot.setTag(ENTITY_TAG, p.write());
        } else if (health != null) {
            require(health.hasKey("Current", 5), "Missing authoritative survivor HP");
            require(health.hasKey("EntityId", 8) && health.getString("EntityId").equals(snapshotId(snapshot).toString()), "Legacy snapshot lineage is unresolved");
            hp = health.getFloat("Current");
        } else hp = exact(snapshot);
        positive(hp); snapshot.setFloat("HealF", hp); snapshot.setFloat("Health", hp);
        if (snapshot.hasKey("Riding", 10)) {
            NBTTagCompound child = snapshot.getCompoundTag("Riding");
            NBTTagCompound mount = versioned(health) ? mountHealth(health, snapshotId(child))
                : health == null ? null : health.hasKey("Riding", 10) ? health.getCompoundTag("Riding") : null;
            require(health == null || mount != null, "Mount survivor lineage is unresolved"); applyToSnapshot(child, mount, record);
        } else if (health != null && !versioned(health) && health.hasKey("Riding"))
            throw new IllegalArgumentException("CAMPAIGN_HEALTH: Survivor mount snapshot is missing");
    }
    private static UUID snapshotId(NBTTagCompound n) {
        require(n.hasKey("UUIDMost", 4) && n.hasKey("UUIDLeast", 4), "Snapshot lacks verified physical identity");
        return new UUID(n.getLong("UUIDMost"), n.getLong("UUIDLeast"));
    }
    /** Prepare detached replacement UUIDs before deserialization, only for a validated movement snapshot. */
    public static void prepareReconstruction(NBTTagCompound snapshot) {
        if (snapshot.hasKey(ENTITY_TAG)) {
            KOMECampaignHealthProjection p = read(snapshot.getCompoundTag(ENTITY_TAG)); UUID old = snapshotId(snapshot), next = UUID.randomUUID();
            KOMERecoveryStartup.requireDetachedReconciliation(p, true);
            KOMERecoveryStartup.stageAlias(p, next);
            p = p.rekey(old, next); snapshot.setTag(ENTITY_TAG, p.write());
            snapshot.setLong("UUIDMost", next.getMostSignificantBits()); snapshot.setLong("UUIDLeast", next.getLeastSignificantBits());
            snapshot.removeTag("PersistentIDMSB"); snapshot.removeTag("PersistentIDLSB");
        } else { snapshot.removeTag("UUIDMost"); snapshot.removeTag("UUIDLeast"); snapshot.removeTag("PersistentIDMSB"); snapshot.removeTag("PersistentIDLSB"); }
        if (snapshot.hasKey("Riding", 10)) prepareReconstruction(snapshot.getCompoundTag("Riding"));
    }
    public static void discardReconstruction(NBTTagCompound snapshot) {
        if (snapshot.hasKey(ENTITY_TAG)) KOMERecoveryStartup.discardAlias(read(snapshot.getCompoundTag(ENTITY_TAG)).alias);
        if (snapshot.hasKey("Riding", 10)) discardReconstruction(snapshot.getCompoundTag("Riding"));
    }
    public static void discardReconstruction(UUID rider, UUID mount) {
        KOMERecoveryStartup.discardAlias(rider); KOMERecoveryStartup.discardAlias(mount);
    }
    /** Publish only after verified spawn; the caller's existing transaction restores record NBT on rollback. */
    public static void reconstructed(KOMEHiredUnitRecord record, UUID oldRider, UUID newRider, UUID oldMount, UUID newMount) {
        if (record == null || !versioned(record.survivingHealth)) return;
        KOMERecoveryStartup.requireAliasPublication(read(record.survivingHealth), newRider);
        KOMECampaignHealthProjection p = read(record.survivingHealth).rekey(oldRider, newRider);
        NBTTagCompound old = record.survivingHealth;
        require((oldMount == null) == (newMount == null), "Incomplete mount reconstruction lineage");
        KOMECampaignHealthProjection mount = null;
        if (oldMount != null) {
            NBTTagCompound savedMount = mountHealth(old, oldMount);
            require(versioned(savedMount), "Missing versioned mount reconstruction source");
            KOMERecoveryStartup.requireAliasPublication(read(savedMount), newMount);
            mount = read(savedMount).rekey(oldMount, newMount);
        }
        NBTTagCompound next = p.write();
        Map<UUID, KOMECampaignHealthProjection> history = mounts(old);
        if (mount != null) history.put(mount.target, mount);
        NBTTagList list = new NBTTagList(); for (KOMECampaignHealthProjection m : history.values()) list.appendTag(m.write());
        if (!history.isEmpty()) next.setTag("Mounts", list);
        if (old.hasKey("Riding", 10)) next.setTag("Riding", mount == null ? old.getCompoundTag("Riding").copy() : mount.write());
        if (old.hasKey("AttachedMountAlias", 8)) {
            String attached = old.getString("AttachedMountAlias");
            next.setString("AttachedMountAlias", mount != null && attached.equals(oldMount.toString()) ? newMount.toString() : attached);
        }
        record.survivingHealth = next;
        record.healthObservedEntity = null; record.healthObservedMount = null;
        if (record.healthAuthorityData != null) record.healthAuthorityData.campaignAssociations.track(record);
    }
    /** Independently loaded/dismounted mounts reconcile against every retained exact-alias projection. */
    public static void reconcileMount(KOMEWorldData data, Entity entity) {
        if (!KOMERecoveryStartup.healthReady(entity) || !KOMERecoveryStartup.permitsTarget(data, entity.getUniqueID())) return;
        KOMECampaignHealthProjection live = KOMECampaignHealthEntity.projection(entity);
        if (live == null || live.component != Component.MOUNT) return;
        if (!KOMERecoveryStartup.validateProjection(entity, live)) return;
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (!KOMEHiredUnitClassification.isCampaignUnit(record)) continue;
            NBTTagCompound saved = mountHealth(record.survivingHealth, entity.getUniqueID());
            if (saved == null) continue;
            if (versioned(saved)) {
                KOMECampaignHealthProjection p = read(saved);
                if (!KOMERecoveryStartup.validateProjection(entity, p)) return;
                if (p.revision > live.revision && p.hp != ((EntityLivingBase)entity).getHealth()
                        && !KOMERecoveryStartup.mayInstallHP(entity, p)) return;
            }
            KOMECampaignHealthProjection p = reconcile(record, entity, saved, Component.MOUNT);
            updateProjection(record, p); record.healthObservedMount = new WeakReference<Entity>(entity); data.markDirty();
        }
        KOMEOrdinaryBattleRecoveryRegistry.Target t = data.getOrdinaryBattleRecovery().targetForPhysical(entity.getUniqueID());
        if (t != null) require(t.component == Component.MOUNT && t.physicalId.equals(entity.getUniqueID()) && t.id.equals(live.target),
            "Mount acknowledgment contradicts B1 identity");
        // B1 UNAVAILABLE also retires grants for living released units; it is not proof of death.
        require(!KOMECampaignHealthEntity.projection(entity).terminal, "Terminal mount cannot rejoin");
    }
    /** Unloaded lookup across retained owners; shared mounts must not use an arbitrary old owner's copy. */
    public static KOMECampaignHealthProjection persisted(KOMEWorldData data, UUID target, Component kind) {
        if (data.recoveryStartup != null && data.recoveryStartup.runtime != null)
            return data.recoveryStartup.runtime.health(target,kind);
        KOMECampaignHealthProjection result = null;
        KOMEOrdinaryBattleRecoveryRegistry.Target identity = data.getOrdinaryBattleRecovery().target(target);
        if (identity != null) require(identity.component == kind, "Persisted target component mismatch");
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (!KOMEHiredUnitClassification.isCampaignUnit(record)) continue;
            KOMECampaignHealthProjection p = kind == Component.MOUNT ? mounts(record.survivingHealth).get(target)
                : versioned(record.survivingHealth) ? read(record.survivingHealth) : null;
            if (p == null || !p.target.equals(target)) continue;
            if (identity != null) {
                require(identity.aliases.contains(p.alias), "Persisted alias lacks verified B1 lineage");
                p = new KOMECampaignHealthProjection(p.target, p.component, identity.physicalId, p.hp,
                    p.revision, p.transition, p.terminal, p.fence);
            }
            result = result == null ? p : KOMECampaignHealthProjection.reconcile(result, p);
        }
        return result; // missing health is unresolved, never synthesized from geometry or Max HP
    }
    public static void reconcileLoaded(KOMEHiredUnitRecord record, Entity entity) { observe(record, entity); }
}
