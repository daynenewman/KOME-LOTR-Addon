package kome.common.data;

import java.util.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;
import static kome.common.data.KOMERecoveryJournalRecord.*;

/** Durable alias/terminal boundaries; no entity health effects. */
final class KOMERecoveryTargetJournal {
    private KOMERecoveryTargetJournal() { }
    static Lineage lineage(KOMEWorldData data, UUID oldAlias, UUID newAlias, boolean reverse) {
        KOMERecoveryStartup s = data.recoveryStartup;
        Target t = data.getOrdinaryBattleRecovery().targetForPhysical(oldAlias);
        if (s == null || t == null || !s.journal().currentPhysicalAliases.containsKey(t.id) || oldAlias.equals(newAlias)) return null;
        KOMERecoveryJournalView.Entry context = KOMERecoveryReservations.contextFor(s.journal(), t.id);
        s.requireRuntime(((EpisodeContext)context.record).episode.conflictId, t.id);
        require(!t.terminal && oldAlias.equals(t.physicalId), "Terminal/stale reconstruction source");
        KOMECampaignHealthProjection p = KOMECampaignHealth.persisted(data, t.id, t.component);
        require(p != null && !p.terminal && p.alias.equals(oldAlias), "Missing exact reconstruction health source");
        KOMERecoveryStartupPlan.validateFence(s.journal(), p);
        require(s.permitsMutation(p), "Reserved target cannot reconstruct before execution");
        if (!reverse) s.requireStaged(p, newAlias);
        else require(t.aliases.contains(newAlias), "Rollback alias lacks verified history");
        KOMERecoveryJournalView.Entry previous = s.journal().lineages.get(t.id);
        require(!reverse || previous != null, "Rollback lacks prior activation");
        return new Lineage(KOMERecoveryReservations.binding(s.journal(), context, t), oldAlias, newAlias,
            reverse ? Lineage.reverseActivation(((Lineage)previous.record).activationId) : UUID.randomUUID(),
            previous == null ? 0 : previous.sequence, p.revision, p.transition, KOMERecoveryReservations.fence(p));
    }
    static void activate(KOMEWorldData data, Lineage l) {
        if (l == null) return;
        KOMEHiredUnitRecord owner = data.campaignAssociations.owner(l.oldAlias);
        data.recoveryStartup.append(l);
        Map<UUID, Target> targets = new TreeMap<UUID, Target>(data.getOrdinaryBattleRecovery().targets());
        Target t = targets.get(l.binding.targetId); Set<UUID> aliases = new TreeSet<UUID>(t.aliases); aliases.add(l.newAlias);
        targets.put(t.id, new Target(t.id, t.component, l.newAlias, aliases, t.terminal));
        data.getOrdinaryBattleRecovery().replaceFrom(KOMEOrdinaryBattleRecoveryRegistry.restore(targets,
            data.getOrdinaryBattleRecovery().episodes()));
        data.recoveryStartup.activated(l.newAlias); data.markDirty();
        if (owner != null) data.campaignAssociations.track(owner);
    }
    /** Preserve every acknowledged edge when a later strategic transaction rejects publication. */
    static void rollback(KOMEWorldData data, KOMEOrdinaryBattleRecoveryRegistry before) {
        KOMERecoveryStartup s = data.recoveryStartup;
        if (s == null) { data.getOrdinaryBattleRecovery().replaceFrom(before); return; }
        Map<UUID, Target> restored = new TreeMap<UUID, Target>(before.targets());
        for (Target original : before.targets().values()) {
            Target current = data.getOrdinaryBattleRecovery().target(original.id);
            if (current == null || current.physicalId.equals(original.physicalId)) continue;
            if (!s.journal().currentPhysicalAliases.containsKey(current.id)) continue;
            try {
                activate(data, lineage(data, current.physicalId, original.physicalId, true));
                restored.put(original.id, data.getOrdinaryBattleRecovery().target(original.id));
            } catch (RuntimeException failure) {
                s.quarantine(original.id, "Reconstruction rollback requires durable lineage replay: " + failure.getMessage());
                // Do not erase a forced edge even when the writer can no longer force its reverse.
                restored.put(original.id, current);
            }
        }
        data.getOrdinaryBattleRecovery().replaceFrom(KOMEOrdinaryBattleRecoveryRegistry.restore(restored, before.episodes()));
        data.markDirty();
    }
    static void terminal(KOMEWorldData data, UUID physical, Disposition disposition, long now, String reason) {
        KOMERecoveryStartup s = data.recoveryStartup;
        Target t = data.getOrdinaryBattleRecovery().targetForPhysical(physical);
        if (s == null || t == null || !s.journal().currentPhysicalAliases.containsKey(t.id)) return;
        KOMERecoveryJournalView.Entry existing = s.journal().terminals.get(t.id);
        if (existing != null || s.journal().terminalTargets.contains(t.id)) return;
        KOMERecoveryJournalView.Entry context = KOMERecoveryReservations.contextFor(s.journal(), t.id);
        s.requireRuntime(((EpisodeContext)context.record).episode.conflictId, t.id);
        KOMECampaignHealthProjection p = KOMECampaignHealth.persisted(data, t.id, t.component);
        require(p != null && p.alias.equals(physical), "Terminal boundary lacks exact health authority");
        KOMERecoveryStartupPlan.validateFence(s.journal(), p);
        // Verify every pending source/consumed descendant before constructing the terminal transition.
        for (KOMERecoveryJournalView.Entry entry : s.journal().preparations.values()) {
            Prepare prepare = (Prepare)entry.record;
            if (!prepare.binding.targetId.equals(t.id)) continue;
            KOMERecoveryStartupPlan.Adoption adoption = KOMERecoveryStartupPlan.reconcile(s.journal(), entry, p);
            require(adoption != KOMERecoveryStartupPlan.Adoption.PENDING || p.revision == prepare.sourceRevision,
                "Terminal source predates durable reservation");
        }
        KOMECampaignHealthProjection terminal = p.change(p.hp, true);
        s.append(new Terminal(KOMERecoveryReservations.binding(s.journal(), context, t), physical,
            terminal.revision, terminal.transition, disposition, now, reason, KOMERecoveryReservations.fence(terminal)));
    }
    static KOMECampaignHealthProjection terminalProjection(KOMEWorldData data, KOMECampaignHealthProjection p) {
        KOMERecoveryStartup s = data.recoveryStartup;
        KOMERecoveryJournalView.Entry entry = s == null || s.journal() == null ? null : s.journal().terminals.get(p.target);
        if (entry == null) return p.change(p.hp, true);
        Terminal t = (Terminal)entry.record;
        require(t.physicalAlias.equals(p.alias) && t.healthRevision >= p.revision, "Terminal projection contradicts WAL");
        return new KOMECampaignHealthProjection(p.target, p.component, p.alias, p.hp, t.healthRevision,
            t.transitionId, true, t.fence == null ? null : new KOMECampaignHealthProjection.Fence(
                t.fence.journalId, t.fence.prepareSequence, t.fence.transitionId));
    }
}
