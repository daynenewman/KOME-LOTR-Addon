package kome.common.data;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.Component;

/** Immutable current-health projection. Max HP and recovery obligations deliberately live elsewhere. */
public final class KOMECampaignHealthProjection {
    public static final int FORMAT = 1;
    public static final String ENTITY_TAG = "KOME:CampaignHealth";
    public final UUID target, alias, transition;
    public final Component component;
    public final float hp;
    public final long revision;
    public final boolean terminal;
    public final Fence fence;

    public static final class Fence {
        public final UUID journal, transition;
        public final long sequence;
        public Fence(UUID journal, long sequence, UUID transition) {
            this.journal = Objects.requireNonNull(journal); this.transition = Objects.requireNonNull(transition);
            require(sequence > 0 && sequence < Long.MAX_VALUE, "Invalid recovery fence sequence"); this.sequence = sequence;
        }
        @Override public boolean equals(Object value) {
            if (!(value instanceof Fence)) return false;
            Fence f = (Fence)value; return journal.equals(f.journal) && sequence == f.sequence && transition.equals(f.transition);
        }
        @Override public int hashCode() { return Objects.hash(journal, sequence, transition); }
    }
    public KOMECampaignHealthProjection(UUID target, Component component, UUID alias, float hp, long revision,
            UUID transition, boolean terminal, Fence fence) {
        this.target = Objects.requireNonNull(target); this.component = Objects.requireNonNull(component);
        this.alias = Objects.requireNonNull(alias); this.transition = Objects.requireNonNull(transition);
        require(Float.isFinite(hp) && hp >= 0F, "Invalid current HP");
        require(revision >= 0 && revision < Long.MAX_VALUE, "Invalid health revision");
        this.hp = hp; this.revision = revision; this.terminal = terminal; this.fence = fence;
    }
    public KOMECampaignHealthProjection change(float nextHP, boolean dead) {
        require(!terminal || dead && Float.compare(nextHP, hp) == 0, "Terminal target cannot change health");
        if (Float.compare(nextHP, hp) == 0 && dead == terminal) return this;
        require(revision < Long.MAX_VALUE - 1, "Health revision exhausted");
        return new KOMECampaignHealthProjection(target, component, alias, nextHP, revision + 1, UUID.randomUUID(), dead, fence);
    }
    /** Only the verified reconstruction transaction may authorize this copy. */
    public KOMECampaignHealthProjection rekey(UUID expectedAlias, UUID replacement) {
        require(alias.equals(expectedAlias) && !terminal, "Stale/terminal reconstruction source");
        return new KOMECampaignHealthProjection(target, component, replacement, hp, revision, transition, false, fence);
    }
    public static KOMECampaignHealthProjection reconcile(KOMECampaignHealthProjection a, KOMECampaignHealthProjection b) {
        require(a.target.equals(b.target) && a.alias.equals(b.alias) && a.component == b.component, "Health identity mismatch");
        if (a.revision == b.revision) {
            require(a.transition.equals(b.transition) && Float.compare(a.hp, b.hp) == 0 && a.terminal == b.terminal
                && Objects.equals(a.fence, b.fence), "Contradictory health state at revision " + a.revision + " target=" + a.target);
            return a;
        }
        KOMECampaignHealthProjection newer = a.revision > b.revision ? a : b, older = newer == a ? b : a;
        require(!older.terminal || newer.terminal, "Terminal evidence cannot be superseded by live state");
        require(older.fence == null || older.fence.equals(newer.fence), "Recovery fence ancestry lost or ambiguous");
        return newer;
    }
    public NBTTagCompound write() {
        NBTTagCompound n = new NBTTagCompound();
        n.setInteger("HealthFormatVersion", FORMAT); n.setString("LogicalTargetId", target.toString());
        n.setString("Component", component.name()); n.setString("PhysicalAlias", alias.toString());
        n.setFloat("CurrentHP", hp); n.setLong("HealthRevision", revision);
        n.setString("LastTransitionId", transition.toString()); n.setBoolean("Terminal", terminal);
        // Legacy readers use these two read-only mirrors; never an alternative authority.
        n.setString("EntityId", alias.toString()); n.setFloat("Current", hp);
        if (fence != null) {
            NBTTagCompound f = new NBTTagCompound(); f.setString("JournalId", fence.journal.toString());
            f.setLong("PrepareSequence", fence.sequence); f.setString("RecoveryTransitionId", fence.transition.toString());
            n.setTag("RecoveryFence", f);
        }
        return n;
    }
    public static KOMECampaignHealthProjection read(NBTTagCompound n) {
        type(n, "HealthFormatVersion", 3); require(n.getInteger("HealthFormatVersion") == FORMAT, "Unknown health format");
        type(n, "Component", 8); type(n, "CurrentHP", 5); type(n, "HealthRevision", 4); type(n, "Terminal", 1);
        require(n.getByte("Terminal") == 0 || n.getByte("Terminal") == 1, "Invalid terminal flag");
        Fence fence = null;
        if (n.hasKey("RecoveryFence")) {
            type(n, "RecoveryFence", 10); NBTTagCompound f = n.getCompoundTag("RecoveryFence"); type(f, "PrepareSequence", 4);
            fence = new Fence(uuid(f, "JournalId"), f.getLong("PrepareSequence"), uuid(f, "RecoveryTransitionId"));
        }
        KOMECampaignHealthProjection p = new KOMECampaignHealthProjection(uuid(n, "LogicalTargetId"),
            Component.valueOf(n.getString("Component")), uuid(n, "PhysicalAlias"), n.getFloat("CurrentHP"),
            n.getLong("HealthRevision"), uuid(n, "LastTransitionId"), n.getBoolean("Terminal"), fence);
        type(n, "EntityId", 8); type(n, "Current", 5);
        require(p.alias.toString().equals(n.getString("EntityId")) && Float.compare(p.hp, n.getFloat("Current")) == 0,
            "Contradictory legacy health mirrors");
        return p;
    }
    static UUID uuid(NBTTagCompound n, String key) {
        type(n, key, 8); String value = n.getString(key); require(value.length() == 36, "Invalid UUID " + key);
        UUID id = UUID.fromString(value); require(id.toString().equals(value), "Noncanonical UUID " + key); return id;
    }
    static void type(NBTTagCompound n, String key, int type) { require(n.hasKey(key, type), "Invalid/missing health field " + key); }
    static void require(boolean yes, String reason) { if (!yes) throw new IllegalArgumentException("CAMPAIGN_HEALTH: " + reason); }
}
