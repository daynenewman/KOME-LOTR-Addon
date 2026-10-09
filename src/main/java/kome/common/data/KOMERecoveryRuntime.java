package kome.common.data;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;

/** Session-only lookup/work cursors. Journal, v3 and health projections remain the authorities. */
final class KOMERecoveryRuntime {
    final KOMEWorldData data;
    final KOMERecoveryStartup startup;
    String cursor = "";
    private final Map<UUID,Set<KOMEHiredUnitRecord>> owners = new HashMap<UUID,Set<KOMEHiredUnitRecord>>();
    private final Map<KOMEHiredUnitRecord,Set<UUID>> recorded = new IdentityHashMap<KOMEHiredUnitRecord,Set<UUID>>();
    private final Map<UUID,Map<Integer,WeakReference<Entity>>> loaded = new HashMap<UUID,Map<Integer,WeakReference<Entity>>>();
    private final Map<UUID,KOMECampaignHealthProjection> startupHealth;
    KOMERecoveryRuntime(KOMEWorldData data, KOMERecoveryStartup startup, Map<UUID,KOMECampaignHealthProjection> evidence) {
        this.data = data; this.startup = startup; startupHealth = new HashMap<UUID,KOMECampaignHealthProjection>(evidence);
        // One startup inventory, not a recurring world scan.
        for (KOMEHiredUnitRecord unit : data.hiredUnits.values()) track(unit);
    }
    void inventoryLoaded(net.minecraft.server.MinecraftServer server) {
        if (server == null || server.worldServers == null) return;
        // Catch callbacks that preceded readiness exactly once; subsequent updates are event-driven.
        for (net.minecraft.world.WorldServer world : server.worldServers) if (world != null && world.loadedEntityList != null)
            for (Object value : world.loadedEntityList) if (value instanceof Entity) observe((Entity)value);
    }
    void track(KOMEHiredUnitRecord unit) {
        Set<UUID> old = recorded.remove(unit);
        if (old != null) for (UUID target : old) { Set<KOMEHiredUnitRecord> set = owners.get(target); if (set != null) { set.remove(unit); if (set.isEmpty()) owners.remove(target); } }
        unit.healthAuthorityData = data;
        if (!KOMEHiredUnitClassification.isCampaignUnit(unit)) return;
        List<KOMECampaignHealthProjection> projections = new ArrayList<KOMECampaignHealthProjection>();
        KOMERecoveryHealthEvidence.collect(unit.survivingHealth, projections);
        Set<UUID> ids = new HashSet<UUID>();
        for (KOMECampaignHealthProjection p : projections) {
            ids.add(p.target); Set<KOMEHiredUnitRecord> set = owners.get(p.target);
            if (set == null) { set = Collections.newSetFromMap(new IdentityHashMap<KOMEHiredUnitRecord,Boolean>()); owners.put(p.target,set); }
            set.add(unit);
        }
        recorded.put(unit, ids);
    }
    List<KOMEHiredUnitRecord> owners(UUID target) {
        List<KOMEHiredUnitRecord> result = new ArrayList<KOMEHiredUnitRecord>(); Set<KOMEHiredUnitRecord> found = owners.get(target);
        if (found != null) for (KOMEHiredUnitRecord r : found)
            if (data.hiredUnits.get(r.entity) == r && KOMEHiredUnitClassification.isCampaignUnit(r)) result.add(r);
        Collections.sort(result, (a,b) -> a.entity.compareTo(b.entity)); return result;
    }
    KOMECampaignHealthProjection health(UUID target, Component component) {
        KOMECampaignHealthProjection result = startupHealth.get(target);
        if (result != null) result = KOMERecoveryStartupPlan.historicalProjection(startup.journal(),result);
        for (KOMEHiredUnitRecord unit : owners(target)) {
            KOMECampaignHealthProjection p = KOMECampaignHealth.retained(unit, target, component);
            if (p == null) continue;
            p = KOMERecoveryStartupPlan.historicalProjection(startup.journal(), p);
            result = result == null ? p : startup.reconcileHealth(result, p);
        }
        return result;
    }
    void publish(KOMECampaignHealthProjection p) {
        List<KOMEHiredUnitRecord> records = owners(p.target);
        require(!records.isEmpty(), "Recovery lacks retained Campaign health owner");
        for (KOMEHiredUnitRecord unit : records) KOMECampaignHealth.updateProjection(unit,p);
        startupHealth.put(p.target,p); data.markDirty();
    }
    void observe(Entity entity) {
        if (!(entity instanceof EntityLivingBase) || entity.worldObj == null || entity.worldObj.isRemote) return;
        if (KOMECampaignHealthEntity.projection(entity) == null && data.getOrdinaryBattleRecovery().targetForPhysical(entity.getUniqueID()) == null) return;
        Map<Integer,WeakReference<Entity>> aliases = loaded.get(entity.getUniqueID());
        if (aliases == null) { aliases = new HashMap<Integer,WeakReference<Entity>>(); loaded.put(entity.getUniqueID(),aliases); }
        aliases.put(entity.getEntityId(),new WeakReference<Entity>(entity));
    }
    void forget(Entity entity) {
        Map<Integer,WeakReference<Entity>> aliases = loaded.get(entity.getUniqueID());
        if (aliases != null) { aliases.remove(entity.getEntityId()); if (aliases.isEmpty()) loaded.remove(entity.getUniqueID()); }
    }
    EntityLivingBase find(UUID physical) {
        Map<Integer,WeakReference<Entity>> aliases = loaded.get(physical);
        if (aliases == null) return null;
        EntityLivingBase result = null;
        for (Iterator<WeakReference<Entity>> it = aliases.values().iterator(); it.hasNext();) {
            Entity entity = it.next().get();
            if (!available(entity)) { it.remove(); continue; }
            require(result == null, "Duplicate loaded recovery alias"); result = (EntityLivingBase)entity;
        }
        if (aliases.isEmpty()) loaded.remove(physical);
        return result;
    }
    static boolean available(Entity entity) {
        return entity instanceof EntityLivingBase && entity.worldObj != null && !entity.worldObj.isRemote
            && !entity.isDead && entity.isEntityAlive() && entity.worldObj.getEntityByID(entity.getEntityId()) == entity;
    }
}
