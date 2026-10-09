package kome.common.data;

import java.lang.ref.WeakReference;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;
import static kome.common.data.KOMECampaignHealthProjection.*;

/** Detached entity acknowledgment. Serialization bridges run outside Forge's swallowed property exceptions. */
public final class KOMECampaignHealthEntity implements IExtendedEntityProperties {
    private static final String KEY = "KOME.CampaignHealth";
    KOMECampaignHealthProjection projection, reading;
    boolean loading, projecting;
    WeakReference<KOMEHiredUnitRecord> owner;
    private final EntityLivingBase entity;
    private KOMECampaignHealthEntity(EntityLivingBase entity) { this.entity = entity; }
    static KOMECampaignHealthEntity state(Entity entity, boolean create) {
        if (!(entity instanceof EntityLivingBase) || entity.worldObj != null && entity.worldObj.isRemote) return null;
        KOMECampaignHealthEntity s = (KOMECampaignHealthEntity)entity.getExtendedProperties(KEY);
        if (s == null && create) {
            s = new KOMECampaignHealthEntity((EntityLivingBase)entity);
            require(KEY.equals(entity.registerExtendedProperties(KEY, s)), "Duplicate health extension");
        }
        return s;
    }
    public static KOMECampaignHealthProjection projection(Entity entity) {
        KOMECampaignHealthEntity s = state(entity, false); return s == null ? null : s.projection;
    }
    /** Native setHealth RETURN and snapshot fallback. No I/O and no recovery execution. */
    public static void changed(Entity entity) {
        KOMECampaignHealthEntity s = state(entity, false);
        if (s == null || s.loading || s.projecting || s.projection == null) return;
        if (!KOMERecoveryStartup.healthReady(entity) || !KOMERecoveryStartup.validateProjection(entity, s.projection)) return;
        require(s.projection.alias.equals(entity.getUniqueID()), "Unverified physical alias mutation");
        if (s.projection.hp != s.entity.getHealth())
            require(KOMERecoveryStartup.permitsHealthChange(entity, s.projection), "Recovery reservation prevents publishing branched health");
        KOMECampaignHealthProjection next = s.projection.change(s.entity.getHealth(), s.projection.terminal);
        if (next != s.projection) { s.projection = next; s.publish(); }
    }
    /** Validate before native mutation, so terminal/overflow cannot produce an unversioned HP side effect. */
    public static void beforeChange(Entity entity, float requested) {
        KOMECampaignHealthEntity s = state(entity, false);
        if (s == null || s.loading || s.projecting || s.projection == null) return;
        KOMEHiredUnitRecord record = s.owner == null ? null : s.owner.get();
        if (record != null) {
            NBTTagCompound n = s.projection.component == KOMEOrdinaryBattleRecoveryRegistry.Component.RIDER
                ? record.survivingHealth : KOMECampaignHealth.mountHealth(record.survivingHealth, s.projection.alias);
            if (KOMECampaignHealth.versioned(n)) {
                KOMECampaignHealthProjection latest = KOMECampaignHealth.reconcile(record,s.projection,read(n));
                require(latest.revision == s.projection.revision, "Target requires reconciliation before another mutation");
            }
        }
        require(Float.isFinite(requested), "Nonfinite native HP mutation");
        float next = Math.max(0F, Math.min(s.entity.getMaxHealth(), requested));
        if (Float.compare(next, s.projection.hp) != 0) {
            require(KOMERecoveryStartup.permitsHealthChange(entity, s.projection), "Recovery reservation awaits reconciliation");
            require(!s.projection.terminal, "Terminal target cannot change health");
            require(s.projection.revision < Long.MAX_VALUE - 1, "Health revision exhausted");
        }
    }
    static void install(Entity entity, KOMECampaignHealthProjection p, KOMEHiredUnitRecord owner, boolean project) {
        KOMECampaignHealthEntity s = state(entity, true);
        require(s != null && p.alias.equals(entity.getUniqueID()), "Health alias does not match entity");
        if (project) {
            require(KOMERecoveryStartup.mayInstallHP(entity, p), "Recovery startup cannot apply a prepared HP transition");
            require(p.hp <= s.entity.getMaxHealth(), "Authoritative HP exceeds validated live Max HP");
            s.projecting = true;
            try { s.entity.setHealth(p.hp); } finally { s.projecting = false; }
            require(Float.compare(s.entity.getHealth(), p.hp) == 0, "Entity rejected health projection");
        }
        s.projection = p; if (owner != null) s.owner = new WeakReference<KOMEHiredUnitRecord>(owner);
        KOMERecoveryStartup.observeEntity(entity);
    }
    /** Explicit authorized recovery: exactly one WAL transition, including capped/zero-amount grants. */
    static KOMECampaignHealthProjection adoptRecovery(KOMEWorldData data, EntityLivingBase entity,
            KOMERecoveryJournalView.Entry entry) {
        KOMERecoveryStartup startup = data.recoveryStartup;
        KOMERecoveryJournalRecord.Prepare prepare = (KOMERecoveryJournalRecord.Prepare)entry.record;
        KOMECampaignHealthEntity s = state(entity,false);
        require(s != null && !s.loading && !s.projecting && s.projection != null && !s.projection.terminal,
            "Recovery requires a reconciled live component");
        KOMECampaignHealthProjection live = s.projection;
        require(live.hp == entity.getHealth() && entity.getMaxHealth() == prepare.maxHp && entity.isEntityAlive(),
            "Recovery physical source changed");
        require(live.target.equals(prepare.binding.targetId) && live.component == prepare.binding.component
            && live.alias.equals(startup.journal().currentPhysicalAliases.get(live.target)), "Recovery physical identity changed");
        require(KOMERecoveryStartupPlan.reconcile(startup.journal(),entry,live) == KOMERecoveryStartupPlan.Adoption.PENDING
            && live.revision <= prepare.sourceRevision, "Recovery source is no longer reserved");
        // PREPARE durably captured S even when both ordinary saves still contain an older revision.
        KOMECampaignHealthProjection source = new KOMECampaignHealthProjection(live.target,live.component,live.alias,
            prepare.sourceHp,prepare.sourceRevision,prepare.sourceTransitionId,false,
            prepare.inheritedFence == null ? null : new Fence(prepare.inheritedFence.journalId,
                prepare.inheritedFence.prepareSequence,prepare.inheritedFence.transitionId));
        startup.beginAdoption(entry,source);
        KOMECampaignHealthProjection adopted = new KOMECampaignHealthProjection(live.target,live.component,live.alias,
            prepare.targetHp,prepare.recoveryRevision,prepare.recoveryTransitionId,false,
            new Fence(prepare.binding.journalId,entry.sequence,prepare.recoveryTransitionId));
        // Native HEAD/RETURN hooks see the authorized projection scope and cannot allocate R+1.
        // A failure deliberately retains R/quarantine; never roll authority back to S.
        s.projecting = true; s.projection = adopted;
        try {
            entity.setHealth(adopted.hp);
            require(entity.getHealth() == adopted.hp && s.projection == adopted && !entity.isDead,
                "Native entity rejected prepared recovery");
            NBTTagCompound acknowledgment = new NBTTagCompound(); entity.writeToNBT(acknowledgment);
            endWrite(entity,acknowledgment);
            require(acknowledgment.getCompoundTag(ENTITY_TAG).equals(adopted.write()) && entity.getHealth() == adopted.hp
                && entity.getMaxHealth() == prepare.maxHp && entity.isEntityAlive() && !entity.isDead && s.projection == adopted,
                "Recovery serialization acknowledgment failed");
            return adopted;
        } finally { s.projecting = false; }
    }
    private void publish() {
        KOMEHiredUnitRecord record = owner == null ? null : owner.get();
        if (record != null) KOMECampaignHealth.updateProjection(record, projection);
        if (entity.worldObj != null) KOMEWorldData.get(entity.worldObj).markDirty();
    }
    public static void terminal(Entity entity) {
        KOMECampaignHealthEntity s = state(entity, false);
        if (s == null || s.projection == null) return;
        if (!KOMERecoveryStartup.healthReady(entity)) return;
        KOMEWorldData data = KOMEWorldData.get(entity.worldObj);
        KOMERecoveryTargetJournal.terminal(data, entity.getUniqueID(),
            KOMEOrdinaryBattleRecoveryRegistry.Disposition.DEAD, System.currentTimeMillis(), "Authoritative living death");
        s.projection = KOMERecoveryTargetJournal.terminalProjection(data, s.projection); s.publish();
    }
    public static void beginRead(Entity entity, NBTTagCompound nbt) {
        KOMECampaignHealthEntity existing = state(entity, false);
        if (!nbt.hasKey(ENTITY_TAG)) {
            require(existing == null || existing.projection == null, "Cannot replace versioned entity with unversioned NBT"); return;
        }
        type(nbt, ENTITY_TAG, 10);
        KOMECampaignHealthEntity s = state(entity, true); if (s == null) return;
        KOMECampaignHealthProjection incoming = read(nbt.getCompoundTag(ENTITY_TAG));
        if (s.projection != null) {
            require(!s.projection.terminal || incoming.terminal, "Terminal entity cannot load a live snapshot");
            s.projection = KOMECampaignHealthProjection.reconcile(s.projection, incoming);
        } else s.projection = incoming;
        s.reading = incoming; s.loading = true;
    }
    public static void endRead(Entity entity, NBTTagCompound nbt) {
        KOMECampaignHealthEntity s = state(entity, false); if (s == null || !s.loading) return;
        require(s.projection.alias.equals(entity.getUniqueID()), "Saved entity alias contradicts health acknowledgment");
        require(nbt.hasKey("HealF", 5) && Float.compare(nbt.getFloat("HealF"), s.reading.hp) == 0
            && Float.compare(s.entity.getHealth(), s.reading.hp) == 0, "Saved HP contradicts health acknowledgment");
        s.loading = false; s.reading = null;
        KOMERecoveryStartup.observeEntity(entity);
        if (!KOMERecoveryStartup.healthReady(entity) || !KOMERecoveryStartup.validateProjection(entity, s.projection)) return;
        install(entity, s.projection, null, Float.compare(s.entity.getHealth(), s.projection.hp) != 0);
    }
    public static void endWrite(Entity entity, NBTTagCompound nbt) {
        KOMECampaignHealthEntity s = state(entity, false); if (s == null || s.projection == null) return;
        changed(entity);
        require(!s.loading && nbt.hasKey("HealF", 5) && Float.compare(nbt.getFloat("HealF"), s.projection.hp) == 0,
            "Entity serialization changed HP during snapshot");
        nbt.setTag(ENTITY_TAG, s.projection.write()); // fresh compound, never retained by reference
    }
    // Actual codec runs in Entity read/write endpoint hooks: Forge catches and suppresses property failures.
    @Override public void saveNBTData(NBTTagCompound nbt) { }
    @Override public void loadNBTData(NBTTagCompound nbt) { }
    @Override public void init(Entity entity, World world) { }
}
