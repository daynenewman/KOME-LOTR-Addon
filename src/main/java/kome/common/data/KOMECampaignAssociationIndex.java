package kome.common.data;

import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;

/** Derived lookup only. Commitment and health remain owned by the hired record and v3 authority. */
final class KOMECampaignAssociationIndex {
    final KOMEWorldData data;
    private final Map<UUID, KOMEHiredUnitRecord> physical = new HashMap<>();
    private final Map<KOMEHiredUnitRecord, Set<UUID>> indexed = new IdentityHashMap<>();

    KOMECampaignAssociationIndex(KOMEWorldData data) { this.data = data; }

    KOMEHiredUnitRecord owner(UUID alias) {
        KOMEHiredUnitRecord r = physical.get(alias);
        return r != null && data.hiredUnits.get(r.entity) == r && KOMEHiredUnitClassification.isCampaignUnit(r) ? r : null;
    }
    void forget(KOMEHiredUnitRecord r) {
        Set<UUID> aliases = indexed.remove(r);
        if (aliases != null) for (UUID alias : aliases) physical.remove(alias, r);
    }
    void track(KOMEHiredUnitRecord r) {
        forget(r);
        r.healthAuthorityData = data;
        if (!KOMEHiredUnitClassification.isCampaignUnit(r) || data.hiredUnits.get(r.entity) != r) return;
        Set<UUID> aliases = new HashSet<>();
        add(aliases, r.entity);
        NBTTagCompound mount = mount(r);
        if (mount != null) add(aliases, UUID.fromString(mount.getString("EntityId")));
        indexed.put(r, aliases);
        for (UUID alias : aliases) physical.put(alias, r);
    }
    private void add(Set<UUID> aliases, UUID alias) {
        aliases.add(alias);
        // In particular retain both sides of a quarantined WAL-ahead-of-root reconstruction.
        Target target = data.getOrdinaryBattleRecovery().targetForPhysical(alias);
        if (target != null) aliases.addAll(target.aliases);
    }
    void rebuild() {
        physical.clear(); indexed.clear();
        for (KOMEHiredUnitRecord r : data.hiredUnits.values()) track(r);
    }
    /** Riding is the retained canonical association, not the currently attached animal. */
    static NBTTagCompound mount(KOMEHiredUnitRecord r) {
        NBTTagCompound m = r.survivingHealth != null && r.survivingHealth.hasKey("Riding", 10)
            ? r.survivingHealth.getCompoundTag("Riding") : null;
        if (m == null && r.mounted) {
            NBTTagCompound snapshot = r.movingEntityData != null ? r.movingEntityData : r.stationedEntityData;
            UUID alias = KOMEOrdinaryBattleRecoveryService.snapshotMount(snapshot);
            if (alias == null) return null;
            NBTTagCompound riding = snapshot.getCompoundTag("Riding");
            if (riding.hasKey(KOMECampaignHealthProjection.ENTITY_TAG, 10))
                m = riding.getCompoundTag(KOMECampaignHealthProjection.ENTITY_TAG);
            else {
                m = new NBTTagCompound(); m.setString("EntityId", alias.toString());
                m.setFloat("Current", riding.getFloat(riding.hasKey("HealF") ? "HealF" : "Health"));
            }
        }
        if (m == null) return null;
        if (!m.hasKey("EntityId", 8)) return null;
        try { UUID.fromString(m.getString("EntityId")); } catch (IllegalArgumentException invalid) { return null; }
        return m;
    }
    static boolean accepts(KOMEHiredUnitRecord r, UUID alias) {
        NBTTagCompound retained = mount(r);
        if (retained != null) return alias.toString().equals(retained.getString("EntityId"));
        // Initial association must already be proven by canonical deployment/reconstruction geometry.
        return r.mounted && (alias.equals(KOMEOrdinaryBattleRecoveryService.snapshotMount(r.stationedEntityData))
            || alias.equals(KOMEOrdinaryBattleRecoveryService.snapshotMount(r.movingEntityData)));
    }
    Map<UUID, KOMEHiredUnitRecord> records() {
        return new HashMap<UUID, KOMEHiredUnitRecord>() {
            @Override public KOMEHiredUnitRecord put(UUID id, KOMEHiredUnitRecord r) {
                KOMEHiredUnitRecord old = super.put(id, r);
                if (old != null) forget(old);
                track(r); return old;
            }
            @Override public void putAll(Map<? extends UUID, ? extends KOMEHiredUnitRecord> records) {
                for (Map.Entry<? extends UUID, ? extends KOMEHiredUnitRecord> e : records.entrySet()) put(e.getKey(), e.getValue());
            }
            @Override public KOMEHiredUnitRecord remove(Object id) {
                KOMEHiredUnitRecord old = super.remove(id); if (old != null) forget(old); return old;
            }
            @Override public void clear() { super.clear(); physical.clear(); indexed.clear(); }
        };
    }
}
