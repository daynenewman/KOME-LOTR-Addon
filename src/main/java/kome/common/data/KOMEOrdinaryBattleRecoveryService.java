package kome.common.data;

import java.util.*;
import kome.common.KOMEReflection;
import kome.common.config.KOMEConfigRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import static kome.common.data.KOMEConflictRecord.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;

/** Authoritative producers only. This service never restores, heals, or spawns entities. */
public final class KOMEOrdinaryBattleRecoveryService {
    private KOMEOrdinaryBattleRecoveryService() { }
    static void episodeStarted(KOMEWorldData data, KOMEConflictRecord conflict, long now, boolean hostilePair) {
        if (!KOMERecoveryStartup.permits(data, conflict)) return;
        data.ensureWritable();
        if (data.getOrdinaryBattleRecovery().begin(conflict, Coverage.COMPLETE)) data.markDirty();
        observe(data, conflict, now, hostilePair);
    }
    public static void observe(KOMEWorldData data, KOMEConflictRecord conflict, long now) {
        if (!KOMERecoveryStartup.permits(data, conflict)) return;
        if (!active(conflict)) return;
        KOMECombatStateEvaluator.Result military = new KOMECombatStateEvaluator().evaluate(data, conflict);
        observe(data, conflict, now, military.hostilePairPresent);
    }
    static void observe(KOMEWorldData data, KOMEConflictRecord conflict, long now, boolean hostilePair) {
        if (!KOMERecoveryStartup.permits(data, conflict)) return;
        if (!active(conflict)) return;
        data.ensureWritable();
        KOMEOrdinaryBattleRecoveryRegistry next = copy(data);
        boolean changed = next.begin(conflict, Coverage.LEGACY_INCOMPLETE);
        changed |= next.observeCombat(conflict, hostilePair);
        // Reuse the same strategic classification as the military evaluator, never entity proximity.
        KOMECombatStateEvaluator.Result military = new KOMECombatStateEvaluator().evaluate(data, conflict);
        for (KOMECombatStateEvaluator.MilitaryFaction faction : military.factions.values()) {
            for (String companyId : faction.campaignDetachmentIds) {
                KOMEArmyCompany company = data.armyCompanies.get(companyId);
                if (company == null) continue;
                for (UUID id : new TreeSet<UUID>(company.units)) {
                    KOMEHiredUnitRecord unit = data.hiredUnits.get(id);
                    if (unit == null) continue;
                    Target known = next.targetForPhysical(id);
                    if (known != null && known.terminal) continue;
                    UUID mountId = associatedMount(unit);
                    data.campaignAssociations.track(unit);
                    changed |= next.enroll(conflict, id, companyId, faction.factionId, now, mountId,
                        KOMECampaignHealth.logicalTarget(unit.survivingHealth, id, Component.RIDER),
                        mountId == null ? null : KOMECampaignHealth.logicalTarget(
                            KOMECampaignAssociationIndex.mount(unit), mountId, Component.MOUNT));
                    if (unit.populationReturned || terminalHealth(unit)) {
                        KOMERecoveryTargetJournal.terminal(data, id, Disposition.UNAVAILABLE, now, "Terminal Campaign authority");
                        changed |= next.terminalize(id, Disposition.UNAVAILABLE, now, "Terminal Campaign authority");
                    }
                }
            }
        }
        Set<String> knownCompanies = new HashSet<String>();
        for (KOMECombatStateEvaluator.MilitaryFaction faction : military.factions.values())
            knownCompanies.addAll(faction.campaignDetachmentIds);
        Episode captured = next.get(conflict.getConflictId(), conflict.getCombatEpisode().episodeId);
        for (String id : conflict.getCommitments().keySet()) {
            if (knownCompanies.contains(id)) continue;
            // Terminal cleanup may remove a previously captured company. Its durable roster
            // remains complete; only an uncaptured member/commitment creates a coverage gap.
            KOMEArmyCompany company = data.armyCompanies.get(id);
            boolean capturedCompany = false;
            for (Participant p : captured.participants.values()) capturedCompany |= id.equals(p.companyId);
            if (company == null || company.units.isEmpty()) {
                if (!capturedCompany) changed |= next.incomplete(conflict);
            } else for (UUID physical : company.units) {
                Target target = next.targetForPhysical(physical);
                if (target == null || !captured.participants.containsKey(target.id)) changed |= next.incomplete(conflict);
            }
        }
        if (changed) { data.getOrdinaryBattleRecovery().replaceFrom(next); data.markDirty(); }
    }
    static KOMEOrdinaryBattleRecoveryRegistry copy(KOMEWorldData data) {
        KOMEOrdinaryBattleRecoveryRegistry next = new KOMEOrdinaryBattleRecoveryRegistry();
        next.replaceFrom(data.getOrdinaryBattleRecovery()); return next;
    }
    static void endEpisode(KOMEWorldData data, KOMEConflictRecord conflict, long now) {
        if (!KOMERecoveryStartup.permits(data, conflict)) return;
        KOMEOrdinaryBattleRecoveryRegistry registry = data.getOrdinaryBattleRecovery();
        Episode e = registry.get(conflict.getConflictId(), conflict.getCombatEpisode().episodeId);
        if (e == null) return;
        registry.end(conflict, now);
        float amount = e.intent != null ? e.intent.recoveryAmount : !e.grants.isEmpty()
            ? e.grants.values().iterator().next().amount : KOMEConfigRegistry.battle().getPostBattleRecoveryAmount();
        registry.prepareGrants(e.conflictId, e.episodeId, amount);
        data.markDirty();
    }
    static void conflictClosed(KOMEWorldData data, KOMEConflictRecord before, KOMEConflictRecord conflict, long now) {
        if (!KOMERecoveryStartup.permits(data, conflict)) return;
        endEpisode(data, conflict, now);
        KOMEConquestTile tile = data.getConquestTileIfPresent(conflict.getTileId());
        try {
            data.getOrdinaryBattleRecovery().bindClosedConflict(before,
                tile == null ? null : KOMEAlliance.normalizeFactionKey(tile.projectRulingFaction()),now);
        } catch (IllegalArgumentException contradiction) {
            // Ending/handoff is canonical even if recovery lacks a representable ownership decision.
            if (data.recoveryStartup != null) for (Episode e : data.getOrdinaryBattleRecovery().episodes().values())
                if (e.conflictId.equals(conflict.getConflictId())) for (Grant grant : e.grants.values())
                    data.recoveryStartup.quarantine(grant.targetId,"Canonical closure recovery intent: " + contradiction.getMessage());
        }
        data.markDirty();
    }
    static void departed(KOMEWorldData data, KOMEConflictRecord before, Collection<UUID> members,
            KOMEConflictMovementHandoff.Outcome outcome, long now) {
        if (!KOMERecoveryStartup.permits(data, before)) return;
        Disposition disposition = outcome == KOMEConflictMovementHandoff.Outcome.FORMAL_RETREAT ? Disposition.FORMAL_RETREAT
            : outcome == KOMEConflictMovementHandoff.Outcome.DESTROYED ? Disposition.DESTROYED
            : outcome == KOMEConflictMovementHandoff.Outcome.DEFEATED ? Disposition.DEFEATED : Disposition.DEPARTED;
        if (disposition == Disposition.DESTROYED) for (UUID id : members)
            terminal(data, id, disposition, now, outcome.name());
        if (data.getOrdinaryBattleRecovery().exit(before, members, disposition, now, outcome.name())) data.markDirty();
    }
    public static void terminal(KOMEWorldData data, UUID physical, Disposition disposition, long now, String reason) {
        require(KOMERecoveryStartup.permitsTarget(data, physical)
            || data.getOrdinaryBattleRecovery().targetForPhysical(physical) == null
            || data.getOrdinaryBattleRecovery().targetForPhysical(physical).terminal, "Terminal recovery authority is blocked");
        data.ensureWritable();
        Target known = data.getOrdinaryBattleRecovery().targetForPhysical(physical);
        if (known != null && !physical.equals(known.physicalId)) return;
        // For an already journal-bound target, seal the caller's exact irreversible disposition
        // before observation can classify zero HP more generally as UNAVAILABLE.
        KOMERecoveryTargetJournal.terminal(data, physical, disposition, now, reason);
        // Observe BEFORE removing strategic membership; this may be the last hostile-pair proof.
        KOMEHiredUnitRecord unit = data.hiredUnits.get(physical);
        if (unit != null) observe(data, data.getConflictService().get(unit.currentTile), now);
        if (data.getOrdinaryBattleRecovery().terminalize(physical, disposition, now, reason)) data.markDirty();
        if (disposition == Disposition.DEAD || disposition == Disposition.DESTROYED) {
            for (KOMEHiredUnitRecord record : data.hiredUnits.values()) KOMECampaignHealth.markTerminal(data, record, physical);
            data.markDirty();
        }
    }
    public static boolean isTracked(KOMEWorldData data, UUID physical) {
        return data.getOrdinaryBattleRecovery().targetForPhysical(physical) != null;
    }
    /** Called only from the verified, reversible strategic reconstruction transaction. */
    public static void reconstructed(KOMEWorldData data, UUID oldRider, UUID newRider, UUID oldMount, UUID newMount) {
        require(KOMERecoveryStartup.permitsTarget(data, oldRider)
            && (oldMount == null || KOMERecoveryStartup.permitsTarget(data, oldMount)), "Recovery lineage is not ready");
        data.ensureWritable(); KOMEOrdinaryBattleRecoveryRegistry next = copy(data);
        boolean changed = next.rekeyVerified(oldRider, newRider, Component.RIDER);
        if (oldMount != null && newMount != null) changed |= next.rekeyVerified(oldMount, newMount, Component.MOUNT);
        // Validate the whole replacement before any irreversible WAL activation.
        KOMERecoveryJournalRecord.Lineage rider = KOMERecoveryTargetJournal.lineage(data, oldRider, newRider, false);
        KOMERecoveryJournalRecord.Lineage mount = oldMount == null || newMount == null ? null
            : KOMERecoveryTargetJournal.lineage(data, oldMount, newMount, false);
        KOMERecoveryTargetJournal.activate(data, rider);
        KOMERecoveryTargetJournal.activate(data, mount);
        if (changed) { data.getOrdinaryBattleRecovery().replaceFrom(next); data.markDirty(); }
    }
    public static void rollbackReconstruction(KOMEWorldData data, KOMEOrdinaryBattleRecoveryRegistry before) {
        KOMERecoveryTargetJournal.rollback(data, before);
    }
    /** Decode a source snapshot's physical mount identity before reconstruction clears UUIDs. */
    public static UUID snapshotMount(NBTTagCompound snapshot) {
        if (snapshot == null || !snapshot.hasKey("Riding",10)) return null;
        NBTTagCompound mount = snapshot.getCompoundTag("Riding");
        if (mount.hasKey("UUIDMost",4) && mount.hasKey("UUIDLeast",4))
            return new UUID(mount.getLong("UUIDMost"),mount.getLong("UUIDLeast"));
        return null;
    }
    private static UUID associatedMount(KOMEHiredUnitRecord unit) {
        NBTTagCompound mount = KOMECampaignAssociationIndex.mount(unit);
        if (mount == null || !mount.hasKey("Current", 5) || !(mount.getFloat("Current") > 0)) return null;
        if (KOMECampaignHealth.versioned(mount) && KOMECampaignHealthProjection.read(mount).terminal) return null;
        UUID alias = UUID.fromString(mount.getString("EntityId"));
        Entity rider = unit.healthObservedEntity == null ? null : unit.healthObservedEntity.get();
        if (rider != null && unit.entity.equals(rider.getUniqueID())) {
            Entity attached = KOMEReflection.getRidingEntity(rider);
            return attached instanceof EntityLivingBase && alias.equals(attached.getUniqueID())
                && attached.isEntityAlive() && ((EntityLivingBase)attached).getHealth() > 0 ? alias : null;
        }
        if (KOMECampaignHealth.versioned(unit.survivingHealth)
                && unit.survivingHealth.hasKey("AttachedMountAlias", 8)
                && !alias.toString().equals(unit.survivingHealth.getString("AttachedMountAlias"))) return null;
        return alias;
    }
    private static boolean terminalHealth(KOMEHiredUnitRecord unit) {
        return unit.survivingHealth != null && unit.entity.toString().equals(unit.survivingHealth.getString("EntityId"))
            && unit.survivingHealth.hasKey("Current",5) && !(unit.survivingHealth.getFloat("Current") > 0);
    }
    private static boolean active(KOMEConflictRecord conflict) {
        return conflict != null && conflict.isActive() && conflict.getState() == State.ORDINARY
            && conflict.getCombatEpisode().state == EpisodeState.ACTIVE;
    }
}
