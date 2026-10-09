package kome.common.data;

import java.util.*;
import net.minecraft.entity.EntityLivingBase;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;
import static kome.common.data.KOMERecoveryJournalRecord.*;

/** Bounded server-thread adoption of absolute WAL transitions. Never creates ownership authority. */
public final class KOMERecoveryExecutor {
    public static final int COMPONENTS_PER_CAMPAIGN_TICK = 8;
    public enum Outcome { APPLIED, RECONCILED, TERMINAL, UNAVAILABLE, INELIGIBLE, QUARANTINED }
    private KOMERecoveryExecutor() { }

    public static Outcome execute(KOMEWorldData data, String conflict, String episode, Component component,
            UUID targetId, EntityLivingBase entity) {
        KOMERecoveryStartup s = data == null ? null : data.recoveryStartup;
        if (s == null) return Outcome.UNAVAILABLE;
        try {
            s.requireRuntime(conflict,targetId); data.ensureWritable();
            KOMEOrdinaryBattleRecoveryRegistry registry = data.getOrdinaryBattleRecovery();
            Episode e = registry.get(conflict,episode); Target target = registry.target(targetId);
            Grant grant = e == null ? null : e.grants.get(component + ":" + targetId);
            if (grant == null || target == null || e.coverage != Coverage.COMPLETE || !e.actualHostileCombatObserved
                    || e.endedAt == null || e.intent == null || e.intent.stage.ordinal() < Stage.RECOVERY_PENDING.ordinal())
                return Outcome.INELIGIBLE;
            require(target.component == component, "Recovery component mismatch");
            Participant participant = e.participants.get(grant.participantId);
            require(participant != null && (component == Component.RIDER ? participant.id.equals(target.id)
                : participant.mountIds.contains(target.id)), "Recovery participant mismatch");
            KOMERecoveryJournalView.Entry entry = s.journal().preparations.get(episode + ":" + grant.key());
            if (entry != null) KOMERecoveryReservations.requireContext(s.journal(),e,registry);
            if (target.terminal || s.journal().terminalTargets.contains(targetId) || grant.state == GrantState.TERMINAL) {
                require(target.terminal || s.journal().terminalTargets.contains(targetId), "TERMINAL grant lacks terminal target authority");
                if (grant.state == GrantState.PENDING) {
                    registry.replaceGrant(e,new Grant(grant.participantId,targetId,component,grant.amount,GrantState.TERMINAL,
                        grant.transition,"Durable terminal target")); data.markDirty();
                }
                progress(data,conflict,episode); return Outcome.TERMINAL;
            }
            if (terminal(participant.disposition)) return Outcome.INELIGIBLE;
            Target rider = registry.target(participant.id);
            KOMEHiredUnitRecord unit = rider == null ? null : data.hiredUnits.get(rider.physicalId);
            if (unit == null || !KOMEHiredUnitClassification.isCampaignUnit(unit) || unit.populationReturned) return Outcome.UNAVAILABLE;
            KOMECampaignHealthProjection evidence = s.runtime.health(targetId,component);
            if (entity != null && !KOMERecoveryRuntime.available(entity)) entity = null;
            if (entity != null) {
                require(KOMEWorldData.get(entity.worldObj) == data, "Recovery entity belongs to another world session");
                require(entity.getUniqueID().equals(target.physicalId), "Unrelated recovery replacement");
                s.runtime.observe(entity);
                KOMECampaignHealthProjection live = KOMECampaignHealthEntity.projection(entity);
                if (live != null) {
                    require(live.target.equals(targetId) && live.component == component
                        && live.alias.equals(target.physicalId) && live.hp == entity.getHealth(), "Incoherent live recovery health");
                    evidence = evidence == null ? live : s.reconcileHealth(evidence,live);
                    if (entry == null && evidence.revision > live.revision) {
                        require(KOMERecoveryStartup.mayInstallHP(entity,evidence), "Persisted health cannot safely reproject");
                        KOMECampaignHealthEntity.install(entity,evidence,unit,true);
                    }
                }
            }
            if (entry != null && evidence != null) {
                KOMERecoveryStartupPlan.Adoption adoption = KOMERecoveryStartupPlan.reconcile(s.journal(),entry,evidence);
                if (adoption == KOMERecoveryStartupPlan.Adoption.TERMINAL) {
                    KOMEOrdinaryBattleRecoveryService.terminal(data,target.physicalId,Disposition.DEAD,
                        System.currentTimeMillis(),"Verified terminal health evidence");
                    progress(data,conflict,episode); return Outcome.TERMINAL;
                }
                if (adoption == KOMERecoveryStartupPlan.Adoption.CONSUMED) {
                    // No physical HP operation here, including a later damaged descendant.
                    s.runtime.publish(evidence);
                    applied(data,e,grant,entry,evidence); progress(data,conflict,episode);
                    return Outcome.RECONCILED;
                }
            }
            require(grant.state == GrantState.PENDING, "APPLIED grant lacks consumed health evidence");
            if (entity == null || s.runtime.find(target.physicalId) != entity) return Outcome.UNAVAILABLE;
            KOMERecoveryReservations.Result reservation = KOMERecoveryReservations.prepareForExecution(data,conflict,episode,component,targetId,entity);
            if (reservation.status == KOMERecoveryReservations.Status.UNAVAILABLE) return Outcome.UNAVAILABLE;
            if (reservation.status == KOMERecoveryReservations.Status.INELIGIBLE) return Outcome.INELIGIBLE;
            require(reservation.status == KOMERecoveryReservations.Status.RESERVED, "Recovery authority changed during reservation");
            entry = reservation.evidence;
            KOMECampaignHealthProjection adopted = KOMECampaignHealthEntity.adoptRecovery(data,entity,entry);
            s.runtime.publish(adopted);
            e = registry.get(conflict,episode); grant = e.grants.get(component + ":" + targetId);
            applied(data,e,grant,entry,adopted);
            s.finishAdoption(targetId);
            progress(data,conflict,episode); return Outcome.APPLIED;
        } catch (RuntimeException failure) {
            s.quarantine(targetId,"Recovery execution/reconciliation failed: " + failure.getMessage());
            return Outcome.QUARANTINED;
        }
    }
    private static void applied(KOMEWorldData data, Episode e, Grant grant, KOMERecoveryJournalView.Entry entry,
            KOMECampaignHealthProjection health) {
        KOMERecoveryStartup s = data.recoveryStartup; Prepare p = (Prepare)entry.record;
        require(s.journal().records.get(entry.sequence) == entry && p.binding.conflictId.equals(e.conflictId)
            && p.binding.episodeId.equals(e.episodeId) && p.participantId.equals(grant.participantId)
            && p.binding.targetId.equals(grant.targetId) && p.binding.component == grant.component && p.recoveryAmount == grant.amount
            && KOMERecoveryStartupPlan.reconcile(s.journal(),entry,health) == KOMERecoveryStartupPlan.Adoption.CONSUMED,
            "APPLIED requires exact PREPARE and consumed health ancestry");
        Transition transition = new Transition(p.recoveryTransitionId,p.physicalAlias,p.sourceRevision,p.sourceHp,p.targetHp,p.maxHp);
        if (grant.transition != null) require(grant.transition.token.equals(transition.token)
            && grant.transition.physicalId.equals(transition.physicalId) && grant.transition.sourceRevision == transition.sourceRevision
            && grant.transition.sourceHealth == transition.sourceHealth && grant.transition.targetHealth == transition.targetHealth
            && grant.transition.maxHealth == transition.maxHealth,"Grant/PREPARE contradiction");
        if (grant.state != GrantState.APPLIED) {
            require(grant.state == GrantState.PENDING,"Terminal obligation cannot become APPLIED");
            data.getOrdinaryBattleRecovery().replaceGrant(e,new Grant(grant.participantId,grant.targetId,grant.component,
                grant.amount,GrantState.APPLIED,transition,"")); data.markDirty();
        }
    }
    static void progress(KOMEWorldData data, String conflict, String episode) {
        Episode e = data.getOrdinaryBattleRecovery().get(conflict,episode);
        if (e == null || e.intent == null || e.intent.stage.ordinal() < Stage.RECOVERY_PENDING.ordinal()) return;
        for (Grant g : e.grants.values()) if (g.state == GrantState.PENDING) return;
        if (e.intent.stage == Stage.RECOVERY_PENDING) {
            data.getOrdinaryBattleRecovery().advance(conflict,episode,Stage.CLOSURE_PENDING); data.markDirty();
        }
        // closedAt is written only by canonical Phase 3 ending/handoff, never by this executor.
        e = data.getOrdinaryBattleRecovery().get(conflict,episode);
        if (e.intent.stage == Stage.CLOSURE_PENDING && e.intent.closedAt != null) {
            data.getOrdinaryBattleRecovery().advance(conflict,episode,Stage.COMPLETE); data.markDirty();
        }
    }
    public static int processCampaignTick(KOMEWorldData data) {
        KOMERecoveryStartup s = data.recoveryStartup;
        if (s == null || s.runtime == null || s.state() != KOMERecoveryStartup.State.READY) return 0;
        Set<String> visited = new HashSet<String>(); int attempts = 0;
        for (int slot = 0; slot < COMPONENTS_PER_CAMPAIGN_TICK; slot++) {
            Work work = data.getOrdinaryBattleRecovery().nextWork(s.runtime.cursor);
            if (work == null || !visited.add(work.key)) break;
            s.runtime.cursor = work.key;
            UUID target = work.grant == null ? null : work.grant.targetId;
            if (!s.permitsWork(work.episode.conflictId,target)) continue;
            try {
                s.requireRuntime(work.episode.conflictId,target);
                if (work.grant == null) { progress(data,work.episode.conflictId,work.episode.episodeId); continue; }
                Target identity = data.getOrdinaryBattleRecovery().target(target);
                EntityLivingBase entity = identity == null ? null : s.runtime.find(identity.physicalId);
                execute(data,work.episode.conflictId,work.episode.episodeId,work.grant.component,target,entity); attempts++;
            } catch (RuntimeException failure) { s.quarantine(target,failure.getMessage()); }
        }
        return attempts;
    }
    static void reconcileStartup(KOMEWorldData data) {
        KOMERecoveryStartup s = data.recoveryStartup;
        for (KOMERecoveryJournalView.Entry entry : s.journal().preparations.values()) {
            Prepare p = (Prepare)entry.record;
            if (!s.permitsWork(p.binding.conflictId,p.binding.targetId)) continue;
            try {
                KOMECampaignHealthProjection health = s.runtime.health(p.binding.targetId,p.binding.component);
                if (health != null && KOMERecoveryStartupPlan.reconcile(s.journal(),entry,health) == KOMERecoveryStartupPlan.Adoption.CONSUMED)
                    execute(data,p.binding.conflictId,p.binding.episodeId,p.binding.component,p.binding.targetId,null);
            } catch (RuntimeException failure) { s.quarantine(p.binding.targetId,failure.getMessage()); }
        }
    }
}
