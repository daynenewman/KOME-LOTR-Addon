package kome.common.data;

import java.util.*;
import net.minecraft.entity.EntityLivingBase;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;
import static kome.common.data.KOMERecoveryJournalRecord.*;

/** Server-thread durable reservation API. Deliberately has no health execution operation. */
public final class KOMERecoveryReservations {
    private KOMERecoveryReservations() { }
    public enum Status { UNAVAILABLE, INELIGIBLE, RESERVED, CONSUMED, TERMINAL }
    public static final class Result {
        public final Status status;
        public final KOMERecoveryJournalView.Entry evidence;
        public final Fence expectedFence;
        private Result(Status status, KOMERecoveryJournalView.Entry entry) {
            this.status = status; evidence = entry;
            expectedFence = entry == null ? null : new Fence(((Prepare)entry.record).binding.journalId,
                entry.sequence, ((Prepare)entry.record).recoveryTransitionId);
        }
    }

    /** Call immediately before the future bounded executor, never speculatively for unloaded grants. */
    public static Result prepare(KOMEWorldData data, String conflict, String episode, Component component,
            UUID logicalTarget, EntityLivingBase entity) {
        return prepare(data,conflict,episode,component,logicalTarget,entity,false);
    }
    static Result prepareForExecution(KOMEWorldData data, String conflict, String episode, Component component,
            UUID logicalTarget, EntityLivingBase entity) {
        return prepare(data,conflict,episode,component,logicalTarget,entity,true);
    }
    private static Result prepare(KOMEWorldData data, String conflict, String episode, Component component,
            UUID logicalTarget, EntityLivingBase entity, boolean execution) {
        KOMERecoveryStartup s = data.recoveryStartup;
        require(s != null, "Recovery startup authority is required");
        s.requireRuntime(conflict, logicalTarget); data.ensureWritable();
        try {
            KOMEOrdinaryBattleRecoveryRegistry registry = data.getOrdinaryBattleRecovery();
            Episode e = registry.get(conflict, episode);
            Grant g = e == null ? null : e.grants.get(component + ":" + logicalTarget);
            Target t = registry.target(logicalTarget);
            if (e == null || g == null || t == null || e.coverage != Coverage.COMPLETE
                    || !e.actualHostileCombatObserved || e.endedAt == null || e.intent == null)
                return new Result(Status.INELIGIBLE, null);
            Participant p = e.participants.get(g.participantId);
            require(p != null && t.component == component && (component == Component.RIDER
                ? p.id.equals(t.id) : p.mountIds.contains(t.id)), "Grant participant/target mismatch");
            if (t.terminal || terminal(p.disposition) || g.state == GrantState.TERMINAL
                    || s.journal().terminalTargets.contains(t.id)) return new Result(Status.TERMINAL, null);
            if (g.state != GrantState.PENDING) return new Result(Status.INELIGIBLE, null);
            require(g.amount == e.intent.recoveryAmount, "Grant snapshot contradicts resolution intent");
            Target rider = registry.target(p.id);
            KOMEHiredUnitRecord unit = rider == null ? null : data.hiredUnits.get(rider.physicalId);
            if (unit == null || !KOMEHiredUnitClassification.isCampaignUnit(unit) || unit.populationReturned)
                return new Result(Status.UNAVAILABLE, null);

            KOMERecoveryJournalView.Entry old = s.journal().preparations.get(episode + ":" + g.key());
            KOMECampaignHealthProjection saved = KOMECampaignHealth.persisted(data, t.id, component);
            if (old != null) {
                requireContext(s.journal(), e, registry);
                KOMECampaignHealthProjection loaded = entity == null ? null : KOMECampaignHealthEntity.projection(entity);
                if (loaded != null) {
                    require(entity.getUniqueID().equals(t.physicalId), "Unrelated physical retry target");
                    saved = saved == null ? loaded : s.reconcileHealth(saved, loaded);
                }
                Result result = result(s, old, saved);
                if (result.status == Status.RESERVED) {
                    if (!(execution ? KOMERecoveryRuntime.available(entity) : available(entity)) || loaded == null
                            || (execution ? loaded.revision > ((Prepare)old.record).sourceRevision : loaded.revision != ((Prepare)old.record).sourceRevision))
                        result = new Result(Status.UNAVAILABLE, old);
                    else require(loaded.hp == entity.getHealth() && entity.getMaxHealth() == ((Prepare)old.record).maxHp,
                        "Reserved physical health/Max HP changed before execution");
                }
                publish(data, e, g, (Prepare)old.record);
                return result;
            }
            require(g.transition == null, "ConflictData PREPARE absent from WAL");
            if (!(execution ? KOMERecoveryRuntime.available(entity) : available(entity))) return new Result(Status.UNAVAILABLE, null);
            require(KOMERecoveryStartup.healthReady(entity) && KOMEWorldData.get(entity.worldObj) == data,
                "Recovery entity belongs to another world session");
            require(entity.getUniqueID().equals(t.physicalId), "Unrelated physical recovery target");
            KOMECampaignHealthProjection live = KOMECampaignHealthEntity.projection(entity);
            if (live == null || saved == null) return new Result(Status.UNAVAILABLE, null);
            require(live.target.equals(t.id) && live.component == component && live.alias.equals(t.physicalId),
                "Unverified health target identity");
            KOMECampaignHealthProjection source = s.reconcileHealth(saved, live);
            // A4 never installs a newer saved projection onto a physical entity.
            if (source.revision != live.revision) return new Result(Status.UNAVAILABLE, null);
            require(!source.terminal && source.hp > 0 && source.hp == entity.getHealth(), "Physical health is not executable");
            require(s.permitsMutation(source), "Target already has an unresolved reservation");
            KOMERecoveryStartupPlan.validateFence(s.journal(), source);
            float max = entity.getMaxHealth();
            require(Float.isFinite(max) && max > 0 && source.hp <= max, "Invalid normal Max HP");
            require(source.revision < Long.MAX_VALUE - 1, "Recovery revision exhausted");
            KOMERecoveryJournalView.Entry context = requireContext(s.journal(), e, registry);
            if (context == null) {
                KOMEConflictRecord liveConflict = data.getConflictService().get(e.tileId);
                long revision = e.intent.expectedRevision;
                long checkpoint = episodeSequence(e.episodeId);
                if (liveConflict != null && liveConflict.getConflictId().equals(e.conflictId)) {
                    revision = liveConflict.getRevision(); checkpoint = liveConflict.getCombatEpisode().sequence;
                }
                Map<String, Grant> obligations = new TreeMap<String, Grant>(e.grants);
                // WAL v1 encodes outstanding obligations, not historical grant retirement reasons.
                // Already-retired targets/dispositions remain sealed in the complete participant graph.
                for (Grant retired : e.grants.values()) if (retired.state == GrantState.TERMINAL) {
                    require(registry.target(retired.targetId).terminal, "Retired grant lacks terminal target authority");
                    obligations.remove(retired.key());
                }
                Episode sealed = e.update(e.endedAt, e.actualHostileCombatObserved, e.participants, e.intent, obligations);
                context = s.append(new EpisodeContext(sealed, episodeTargets(e, registry),
                    data.getConflictService().getNextConflictSequence(), revision, checkpoint, g.amount));
            }
            KOMERecoveryJournalView.Entry lineage = s.journal().lineages.get(t.id);
            Prepare prepare = new Prepare(binding(s.journal(), context, t), p.id, t.physicalId,
                lineage == null ? 0 : lineage.sequence, source.revision, source.transition, source.hp, fence(source),
                source.revision + 1, UUID.randomUUID(), Math.min(max, source.hp + g.amount), g.amount, max,
                ((EpisodeContext)context.record).intentFingerprint());
            KOMERecoveryJournalView.Entry entry = s.append(prepare);
            publish(data, e, g, prepare);
            return new Result(Status.RESERVED, entry);
        } catch (RuntimeException failure) {
            // An uncertain append blocks the writer; only its exact rescan can unblock it.
            if (s.state() == KOMERecoveryStartup.State.READY) s.quarantine(logicalTarget, failure.getMessage());
            throw failure;
        }
    }
    private static boolean available(EntityLivingBase entity) {
        if (entity == null || entity.worldObj == null || entity.worldObj.isRemote || entity.isDead
                || !entity.worldObj.loadedEntityList.contains(entity) || !entity.isEntityAlive()) return false;
        int aliases = 0;
        for (Object loaded : entity.worldObj.loadedEntityList)
            if (loaded instanceof net.minecraft.entity.Entity
                    && ((net.minecraft.entity.Entity)loaded).getUniqueID().equals(entity.getUniqueID())) aliases++;
        require(aliases == 1, "Ambiguous loaded physical recovery alias");
        return true;
    }
    private static Result result(KOMERecoveryStartup s, KOMERecoveryJournalView.Entry entry, KOMECampaignHealthProjection health) {
        if (health == null) return new Result(Status.UNAVAILABLE, entry);
        KOMERecoveryStartupPlan.Adoption adoption = KOMERecoveryStartupPlan.reconcile(s.journal(), entry, health);
        return new Result(adoption == KOMERecoveryStartupPlan.Adoption.CONSUMED ? Status.CONSUMED
            : adoption == KOMERecoveryStartupPlan.Adoption.TERMINAL ? Status.TERMINAL : Status.RESERVED, entry);
    }
    private static void publish(KOMEWorldData data, Episode e, Grant g, Prepare p) {
        require(p.binding.conflictId.equals(e.conflictId) && p.binding.episodeId.equals(e.episodeId)
            && p.participantId.equals(g.participantId) && p.binding.targetId.equals(g.targetId)
            && p.binding.component == g.component && p.recoveryAmount == g.amount, "Durable PREPARE contradicts grant");
        Transition t = new Transition(p.recoveryTransitionId, p.physicalAlias, p.sourceRevision, p.sourceHp, p.targetHp, p.maxHp);
        if (g.transition != null) {
            Transition old = g.transition;
            require(old.token.equals(t.token) && old.physicalId.equals(t.physicalId) && old.sourceRevision == t.sourceRevision
                && old.sourceHealth == t.sourceHealth && old.targetHealth == t.targetHealth && old.maxHealth == t.maxHealth,
                "Conflicting persisted reservation"); return;
        }
        data.getOrdinaryBattleRecovery().replaceGrant(e,
            new Grant(g.participantId,g.targetId,g.component,g.amount,g.state,t,g.terminalReason)); data.markDirty();
    }
    private static Map<UUID, Target> episodeTargets(Episode e, KOMEOrdinaryBattleRecoveryRegistry registry) {
        Map<UUID, Target> targets = new TreeMap<UUID, Target>();
        for (Participant p : e.participants.values()) {
            targets.put(p.id, registry.target(p.id));
            for (UUID mount : p.mountIds) targets.put(mount, registry.target(mount));
        }
        return targets;
    }
    /** Compare immutable episode facts; only proven terminal/lineage and monotone intent progress may differ. */
    static KOMERecoveryJournalView.Entry requireContext(KOMERecoveryJournalView view, Episode e,
            KOMEOrdinaryBattleRecoveryRegistry registry) {
        KOMERecoveryJournalView.Entry entry = view.contexts.get(e.episodeId);
        if (entry == null) return null;
        EpisodeContext c = (EpisodeContext)entry.record; Episode d = c.episode;
        require(e.conflictId.equals(d.conflictId) && e.tileId.equals(d.tileId) && e.startedAt == d.startedAt
            && Objects.equals(e.endedAt, d.endedAt) && e.coverage == d.coverage
            && e.actualHostileCombatObserved == d.actualHostileCombatObserved
            && e.participants.keySet().equals(d.participants.keySet()) && e.grants.keySet().containsAll(d.grants.keySet()), "Conflicting sealed episode context");
        Intent a = e.intent, b = d.intent;
        require(a != null && b != null && a.expectedRevision == b.expectedRevision && a.expectedState == b.expectedState
            && a.kind == b.kind && a.expectedOwner.equals(b.expectedOwner) && a.winner.equals(b.winner)
            && a.recoveryAmount == b.recoveryAmount && a.stage.ordinal() >= b.stage.ordinal()
            && (b.closedAt == null || b.closedAt.equals(a.closedAt)), "Contradictory sealed resolution intent");
        for (Participant p : e.participants.values()) {
            Participant q = d.participants.get(p.id);
            require(p.companyId.equals(q.companyId) && p.factionId.equals(q.factionId) && p.admittedAt == q.admittedAt
                && p.mountIds.equals(q.mountIds) && (p.disposition == q.disposition && Objects.equals(p.exitedAt, q.exitedAt)
                && p.exitReason.equals(q.exitReason) || terminal(p.disposition) && view.terminalTargets.contains(p.id)), "Sealed participant contradiction");
        }
        for (Grant g : e.grants.values()) {
            Grant original = d.grants.get(g.key());
            if (original == null) {
                require(g.state == GrantState.TERMINAL && c.targets.containsKey(g.targetId) && c.targets.get(g.targetId).terminal
                    && g.amount == c.recoveryAmount, "Unsealed outstanding grant"); continue;
            }
            require(g.participantId.equals(original.participantId) && g.amount == original.amount, "Sealed grant contradiction");
        }
        for (Target t : episodeTargets(e, registry).values()) require(c.targets.containsKey(t.id)
            && t.component == c.targets.get(t.id).component && t.physicalId.equals(view.currentPhysicalAliases.get(t.id)),
            "Sealed target lineage contradiction");
        return entry;
    }
    static Binding binding(KOMERecoveryJournalView view, KOMERecoveryJournalView.Entry entry, Target target) {
        Episode e = ((EpisodeContext)entry.record).episode;
        return new Binding(view.journalId, entry.sequence, entry.hash, e.conflictId, e.episodeId, target.id, target.component);
    }
    static Fence fence(KOMECampaignHealthProjection p) {
        return p.fence == null ? null : new Fence(p.fence.journal, p.fence.sequence, p.fence.transition);
    }
    static KOMERecoveryJournalView.Entry contextFor(KOMERecoveryJournalView view, UUID target) {
        KOMERecoveryJournalView.Entry result = null;
        for (KOMERecoveryJournalView.Entry e : view.contexts.values())
            if (((EpisodeContext)e.record).targets.containsKey(target)) result = e;
        return result;
    }
}
