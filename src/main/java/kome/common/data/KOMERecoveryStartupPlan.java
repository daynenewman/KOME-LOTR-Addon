package kome.common.data;

import java.util.*;
import net.minecraft.nbt.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;
import static kome.common.data.KOMERecoveryJournalRecord.*;

/** Detached replay. Nothing here writes entities, the journal, ownership, or live world data. */
final class KOMERecoveryStartupPlan {
    enum Adoption { PENDING, CONSUMED, TERMINAL }
    final NBTTagCompound candidate;
    final Set<String> quarantinedConflicts;
    final Map<UUID,String> quarantinedTargets;
    final Map<String,Adoption> preparations;
    final int repairedEpisodes;
    final boolean changed;

    private KOMERecoveryStartupPlan(NBTTagCompound candidate, Set<String> conflicts,
            Map<String,Adoption> preparations, int repaired, Map<UUID,String> targets, boolean changed) {
        this.candidate = candidate;
        quarantinedConflicts = Collections.unmodifiableSet(new TreeSet<String>(conflicts));
        this.preparations = Collections.unmodifiableMap(new TreeMap<String,Adoption>(preparations));
        repairedEpisodes = repaired;
        quarantinedTargets = Collections.unmodifiableMap(new TreeMap<UUID,String>(targets));
        this.changed = changed;
    }

    static KOMERecoveryStartupPlan build(NBTTagCompound persisted, KOMERecoveryJournalView wal) {
        return build(persisted, wal, Collections.<KOMECampaignHealthProjection>emptyList());
    }
    static KOMERecoveryStartupPlan build(NBTTagCompound persisted, KOMERecoveryJournalView wal,
            Collection<KOMECampaignHealthProjection> physicalEvidence) {
        require(wal != null && !wal.hasIncompleteTail(), "Complete validated WAL required");
        NBTTagCompound candidate = (NBTTagCompound)persisted.copy();
        if (!candidate.hasKey(KOMEConflictPersistence.SCHEMA_KEY)) {
            require(wal.records.isEmpty(), "WAL history has no ConflictData root");
            validateHealthTree(candidate, wal, new TreeMap<String,Adoption>());
            for (KOMECampaignHealthProjection p : physicalEvidence) validateFence(wal, p);
            return new KOMERecoveryStartupPlan(candidate, Collections.<String>emptySet(),
                Collections.<String,Adoption>emptyMap(), 0, Collections.<UUID,String>emptyMap(), false);
        }
        int schema = candidate.getInteger(KOMEConflictPersistence.SCHEMA_KEY);
        require(schema >= 1 && schema <= 3, "Unsupported ConflictData schema");
        KOMEOrdinaryBattleRecoveryPersistence.Candidate decoded;
        if (schema == 3 && candidate.hasKey(KOMEConflictPersistence.RECOVERY_KEY)) {
            require(candidate.hasKey(KOMEConflictPersistence.RECOVERY_KEY, 10), "Malformed recovery section");
            decoded = KOMEOrdinaryBattleRecoveryPersistence.decode(candidate.getCompoundTag(KOMEConflictPersistence.RECOVERY_KEY));
        } else {
            // Legacy migration remains the canonical codec's responsibility.
            if (schema < 3) {
                KOMEConflictPersistence.Loaded legacy = KOMEConflictPersistence.readSection(candidate);
                candidate = copyConflictSection(candidate, KOMEConflictPersistence.write(legacy.conflicts, legacy.joinBattleReceipts, legacy.recovery));
                decoded = KOMEOrdinaryBattleRecoveryPersistence.decode(candidate.getCompoundTag(KOMEConflictPersistence.RECOVERY_KEY));
            } else {
                require(!wal.contexts.isEmpty(), "Missing recovery section without journal evidence");
                decoded = new KOMEOrdinaryBattleRecoveryPersistence.Candidate(new TreeMap<UUID,Target>(), new TreeMap<String,Episode>());
            }
        }
        Map<UUID,Target> targets = decoded.targets;
        Map<String,Episode> episodes = decoded.episodes;
        Map<UUID,Target> journalTargets = new TreeMap<UUID,Target>();
        long watermark = candidate.getLong(KOMEConflictPersistence.SEQUENCE_KEY);
        require(candidate.hasKey(KOMEConflictPersistence.SEQUENCE_KEY, 4) && watermark > 0, "Invalid conflict allocator");
        Set<String> quarantine = new TreeSet<String>();
        int repaired = 0;
        for (KOMERecoveryJournalView.Entry entry : wal.records.values()) {
            if (entry.record instanceof EpisodeContext) {
                EpisodeContext context = (EpisodeContext)entry.record;
                Episode old = episodes.get(context.episode.episodeId);
                Episode merged = mergeEpisode(old, context);
                episodes.put(merged.episodeId, merged);
                if (old != merged) repaired++;
                watermark = Math.max(watermark, context.nextConflictSequence);
                for (Target t : context.targets.values()) {
                    Target prior = journalTargets.get(t.id);
                    Set<UUID> aliases = new TreeSet<UUID>(t.aliases);
                    if (prior != null) aliases.addAll(prior.aliases);
                    journalTargets.put(t.id, new Target(t.id, t.component, t.physicalId, aliases, t.terminal));
                }
            } else if (entry.record instanceof Lineage) {
                Lineage l = (Lineage)entry.record;
                Target t = journalTargets.get(l.binding.targetId);
                Set<UUID> aliases = new TreeSet<UUID>(t.aliases); aliases.add(l.newAlias);
                journalTargets.put(t.id, new Target(t.id, t.component, l.newAlias, aliases, t.terminal));
            }
        }
        for (Target t : journalTargets.values()) {
            Target old = targets.get(t.id);
            require(old == null || old.component == t.component && t.aliases.containsAll(old.aliases)
                && t.aliases.contains(old.physicalId), "Unproven recovery lineage target=" + t.id);
            targets.put(t.id, new Target(t.id, t.component, t.physicalId, t.aliases,
                t.terminal || old != null && old.terminal || wal.terminalTargets.contains(t.id)));
        }
        // A persisted transition requires the exact independently validated PREPARE, even if APPLIED.
        for (Episode e : new ArrayList<Episode>(episodes.values())) {
            Map<String,Grant> grants = new TreeMap<String,Grant>(e.grants);
            for (Grant g : e.grants.values()) {
                KOMERecoveryJournalView.Entry entry = wal.preparations.get(e.episodeId + ":" + g.key());
                require(g.transition == null || entry != null, "ConflictData transition absent from WAL: " + e.episodeId + ":" + g.key());
                if (entry == null) {
                    Target target = targets.get(g.targetId);
                    if (target != null && target.terminal && g.state == GrantState.PENDING)
                        grants.put(g.key(), new Grant(g.participantId, g.targetId, g.component, g.amount,
                            GrantState.TERMINAL, null, "Durable terminal target"));
                    continue;
                }
                Prepare p = (Prepare)entry.record;
                require(g.participantId.equals(p.participantId) && g.amount == p.recoveryAmount, "Grant contradicts PREPARE");
                Transition t = new Transition(p.recoveryTransitionId, p.physicalAlias, p.sourceRevision, p.sourceHp, p.targetHp, p.maxHp);
                require(g.transition == null || sameTransition(g.transition, t), "ConflictData/PREPARE transition mismatch");
                boolean terminal = targets.get(g.targetId).terminal && g.state == GrantState.PENDING;
                grants.put(g.key(), new Grant(g.participantId, g.targetId, g.component, g.amount,
                    terminal ? GrantState.TERMINAL : g.state, t, terminal ? "Durable terminal target" : g.terminalReason));
            }
            episodes.put(e.episodeId, e.update(e.endedAt, e.actualHostileCombatObserved, e.participants, e.intent, grants));
        }
        KOMEOrdinaryBattleRecoveryRegistry recovery = KOMEOrdinaryBattleRecoveryRegistry.restore(targets, episodes);
        for (KOMERecoveryJournalView.Entry entry : wal.terminals.values()) {
            Terminal t = (Terminal)entry.record;
            recovery.terminalize(t.physicalAlias, t.disposition, t.observedAt, t.reason);
        }
        candidate.setLong(KOMEConflictPersistence.SEQUENCE_KEY, watermark);
        candidate.setTag(KOMEConflictPersistence.RECOVERY_KEY, recovery.write());
        // Full ordinary validation is unchanged. Unsupported revision/episode contradictions fail closed.
        KOMEConflictPersistence.Loaded validated = KOMEConflictPersistence.readSection(candidate);
        for (KOMERecoveryJournalView.Entry entry : wal.contexts.values()) {
            EpisodeContext c = (EpisodeContext)entry.record;
            KOMEConflictRecord live = validated.conflicts.get(c.episode.tileId);
            if (live != null && live.getConflictId().equals(c.episode.conflictId) && live.isActive()
                    && (live.getRevision() < c.conflictRevision || live.getCombatEpisode().sequence < c.episodeCheckpoint
                        || live.getCombatEpisode().episodeId.equals(c.episode.episodeId)
                        && (live.getCombatEpisode().state == KOMEConflictRecord.EpisodeState.ACTIVE || c.episode.intent != null)))
                quarantine.add(live.getConflictId());
        }
        Map<String,Adoption> adoption = new TreeMap<String,Adoption>();
        for (String key : wal.preparations.keySet()) adoption.put(key, Adoption.PENDING);
        List<KOMECampaignHealthProjection> all = new ArrayList<KOMECampaignHealthProjection>(physicalEvidence);
        KOMERecoveryHealthEvidence.collect(candidate, all);
        Map<UUID,KOMECampaignHealthProjection> latest = new TreeMap<UUID,KOMECampaignHealthProjection>();
        Map<UUID,String> quarantinedTargets = new TreeMap<UUID,String>();
        Map<String,KOMECampaignHealthProjection> revisions = new TreeMap<String,KOMECampaignHealthProjection>();
        for (KOMECampaignHealthProjection p : all) {
            try {
                KOMECampaignHealthProjection proven = historicalProjection(wal, p);
                validateHealthTree(proven.write(), wal, adoption);
                KOMECampaignHealthProjection same = revisions.put(proven.target + ":" + proven.revision, proven);
                if (same != null) KOMECampaignHealthProjection.reconcile(same, proven);
                KOMECampaignHealthProjection old = latest.get(proven.target);
                if (old != null && old.revision != proven.revision) {
                    require(!(old.terminal && !proven.terminal && proven.revision > old.revision
                        || proven.terminal && !old.terminal && old.revision > proven.revision), "Terminal projection superseded by live health");
                    // For non-WAL health, retain A2's strict ancestry validation too.
                    if (!wal.currentPhysicalAliases.containsKey(proven.target)) KOMECampaignHealthProjection.reconcile(old, proven);
                }
                if (old == null || proven.revision > old.revision) latest.put(proven.target, proven);
            } catch (RuntimeException contradiction) {
                quarantinedTargets.put(p.target, contradiction.getMessage());
                UUID owner = wal.logicalTargetsByAlias.get(p.alias);
                if (owner != null) quarantinedTargets.put(owner, contradiction.getMessage());
            }
        }
        for (Episode e : recovery.episodes().values()) for (Grant g : e.grants.values()) {
            if (g.state == GrantState.APPLIED && adoption.get(e.episodeId + ":" + g.key()) == Adoption.PENDING)
                quarantinedTargets.put(g.targetId, "Persisted APPLIED grant lacks durable consumption evidence");
        }
        for (Episode e : recovery.episodes().values()) for (Participant p : e.participants.values()) {
            boolean affected = quarantinedTargets.containsKey(p.id);
            for (UUID mount : p.mountIds) affected |= quarantinedTargets.containsKey(mount);
            if (affected) quarantine.add(e.conflictId);
        }
        for (KOMERecoveryJournalView.Entry e : wal.preparations.values()) {
            Prepare p = (Prepare)e.record;
            if (quarantinedTargets.containsKey(p.binding.targetId)) adoption.put(p.grantIdentity(), Adoption.PENDING);
            else if (wal.terminalTargets.contains(p.binding.targetId)) adoption.put(p.grantIdentity(), Adoption.TERMINAL);
        }
        Map<String,NBTTagCompound> beforeEpisodes = episodeRows(persisted), afterEpisodes = episodeRows(candidate);
        repaired = 0;
        for (Map.Entry<String,NBTTagCompound> e : afterEpisodes.entrySet()) if (!e.getValue().equals(beforeEpisodes.get(e.getKey()))) repaired++;
        return new KOMERecoveryStartupPlan(candidate, quarantine, adoption, repaired, quarantinedTargets, !candidate.equals(persisted));
    }
    /** Only startup inventory may interpret an old physical copy through verified activation evidence. */
    private static KOMECampaignHealthProjection historicalProjection(KOMERecoveryJournalView wal, KOMECampaignHealthProjection p) {
        UUID alias = p.alias;
        for (KOMERecoveryJournalView.Entry entry : wal.records.values()) if (entry.record instanceof Lineage) {
            Lineage l = (Lineage)entry.record;
            if (!l.binding.targetId.equals(p.target) || !l.oldAlias.equals(alias)) continue;
            require(p.component == l.binding.component && p.revision <= l.healthRevision
                && (p.revision != l.healthRevision || p.transition.equals(l.healthTransitionId)), "Historical projection contradicts LINEAGE activation");
            alias = l.newAlias;
        }
        return alias.equals(p.alias) ? p : new KOMECampaignHealthProjection(p.target, p.component, alias,
            p.hp, p.revision, p.transition, p.terminal, p.fence);
    }
    private static Map<String,NBTTagCompound> episodeRows(NBTTagCompound root) {
        Map<String,NBTTagCompound> result = new TreeMap<String,NBTTagCompound>();
        NBTTagList rows = root.getCompoundTag(KOMEConflictPersistence.RECOVERY_KEY).getTagList("Episodes", 10);
        for (int i = 0; i < rows.tagCount(); i++) { NBTTagCompound row = rows.getCompoundTagAt(i); result.put(row.getString("EpisodeId"), row); }
        return result;
    }

    private static NBTTagCompound copyConflictSection(NBTTagCompound root, NBTTagCompound section) {
        for (Object key : section.func_150296_c()) root.setTag((String)key, section.getTag((String)key).copy());
        return root;
    }

    private static Episode mergeEpisode(Episode old, EpisodeContext context) {
        Episode durable = context.episode;
        if (old == null) return durable;
        require(old.conflictId.equals(durable.conflictId) && old.tileId.equals(durable.tileId)
            && old.startedAt == durable.startedAt && (old.endedAt == null || old.endedAt.equals(durable.endedAt)), "Episode/context contradiction");
        require(!old.actualHostileCombatObserved || durable.actualHostileCombatObserved, "Hostile combat evidence contradicts sealed context");
        Map<UUID,Participant> people = new TreeMap<UUID,Participant>(durable.participants);
        for (Participant p : old.participants.values()) {
            Participant q = people.get(p.id);
            require(q != null && p.companyId.equals(q.companyId) && p.factionId.equals(q.factionId)
                && p.admittedAt == q.admittedAt && q.mountIds.containsAll(p.mountIds), "Participant not proven by durable context");
            if (terminal(p.disposition)) people.put(p.id, p);
            else require(p.disposition == Disposition.PARTICIPATING || p.disposition == q.disposition
                && Objects.equals(p.exitedAt, q.exitedAt) && p.exitReason.equals(q.exitReason), "Participant disposition contradiction");
        }
        Intent intent = durable.intent;
        if (old.intent != null) {
            Intent a = old.intent, b = durable.intent;
            require(b != null && a.expectedRevision == b.expectedRevision && a.expectedState == b.expectedState
                && a.kind == b.kind && a.expectedOwner.equals(b.expectedOwner) && a.winner.equals(b.winner)
                && a.recoveryAmount == b.recoveryAmount && (a.closedAt == null || b.closedAt == null || a.closedAt.equals(b.closedAt)),
                "Resolution intent contradiction");
            intent = new Intent(b.conflictId, b.episodeId, b.expectedRevision, b.expectedState, b.kind, b.expectedOwner,
                b.winner, b.recoveryAmount, a.stage.ordinal() > b.stage.ordinal() ? a.stage : b.stage,
                a.closedAt == null ? b.closedAt : a.closedAt);
        }
        Map<String,Grant> grants = new TreeMap<String,Grant>(durable.grants);
        for (Grant g : old.grants.values()) {
            Grant d = grants.get(g.key());
            if (d == null && g.state == GrantState.TERMINAL && context.targets.containsKey(g.targetId)
                    && context.targets.get(g.targetId).terminal && g.amount == context.recoveryAmount) {
                // This obligation was retired before context sealing. Preserve its v3 receipt exactly;
                // the journal proves terminal identity, never fabricates a retirement reason.
                grants.put(g.key(), g); continue;
            }
            require(d != null && d.participantId.equals(g.participantId) && d.amount == g.amount, "Grant absent/contradictory in context");
            grants.put(g.key(), g);
        }
        return new Episode(durable.conflictId, durable.tileId, durable.episodeId, durable.startedAt, durable.endedAt,
            durable.coverage, durable.actualHostileCombatObserved, people, intent, grants);
    }

    private static boolean sameTransition(Transition a, Transition b) {
        return a.token.equals(b.token) && a.physicalId.equals(b.physicalId) && a.sourceRevision == b.sourceRevision
            && a.sourceHealth == b.sourceHealth && a.targetHealth == b.targetHealth && a.maxHealth == b.maxHealth;
    }

    static Adoption reconcile(KOMERecoveryJournalView wal, KOMERecoveryJournalView.Entry entry, KOMECampaignHealthProjection p) {
        Prepare prepare = (Prepare)entry.record;
        require(p.target.equals(prepare.binding.targetId) && p.component == prepare.binding.component,
            "Health/PREPARE component identity mismatch");
        require(p.alias.equals(wal.currentPhysicalAliases.get(p.target)), "Unknown/obsolete recovery alias target=" + p.target);
        validateFence(wal, p);
        if (wal.terminalTargets.contains(p.target) || p.terminal) return Adoption.TERMINAL;
        if (p.revision == prepare.sourceRevision) require(p.transition.equals(prepare.sourceTransitionId)
            && p.hp == prepare.sourceHp, "Contradictory prepared source health target=" + p.target);
        if (p.revision < prepare.recoveryRevision) return Adoption.PENDING;
        require(descends(wal, p.fence, entry.sequence), "Health-fence ancestry mismatch target=" + p.target);
        if (p.revision == prepare.recoveryRevision)
            require(p.transition.equals(prepare.recoveryTransitionId) && p.hp == prepare.targetHp
                && p.fence.sequence == entry.sequence, "Contradictory health at reserved revision target=" + p.target);
        return Adoption.CONSUMED;
    }

    private static boolean descends(KOMERecoveryJournalView wal, KOMECampaignHealthProjection.Fence fence, long sequence) {
        if (fence == null || !wal.journalId.equals(fence.journal)) return false;
        long cursor = fence.sequence;
        while (cursor >= sequence) {
            if (cursor == sequence) return true;
            KOMERecoveryJournalView.Entry e = wal.records.get(cursor);
            if (e == null || !(e.record instanceof Prepare)) return false;
            Fence inherited = ((Prepare)e.record).inheritedFence;
            if (inherited == null || inherited.prepareSequence >= cursor) return false;
            cursor = inherited.prepareSequence;
        }
        return false;
    }

    static void validateFence(KOMERecoveryJournalView wal, KOMECampaignHealthProjection p) {
        UUID knownTarget = wal.logicalTargetsByAlias.get(p.alias);
        require(knownTarget == null || knownTarget.equals(p.target), "Health alias claims an unrelated logical target=" + p.target);
        Component knownComponent = wal.targetComponents.get(p.target);
        require(knownComponent == null || knownComponent == p.component
            && p.alias.equals(wal.currentPhysicalAliases.get(p.target)), "Health target has an unknown/obsolete component alias=" + p.target);
        if (p.fence == null) return;
        require(wal.journalId.equals(p.fence.journal), "Health projection references unknown journal target=" + p.target);
        KOMERecoveryJournalView.Entry e = wal.records.get(p.fence.sequence);
        require(e != null && e.record instanceof Prepare, "Health fence references absent PREPARE target=" + p.target);
        Prepare prepare = (Prepare)e.record;
        require(prepare.binding.targetId.equals(p.target) && prepare.binding.component == p.component
            && prepare.recoveryTransitionId.equals(p.fence.transition) && p.revision >= prepare.recoveryRevision,
            "Health-fence mismatch target=" + p.target);
        if (p.revision == prepare.recoveryRevision) require(p.transition.equals(prepare.recoveryTransitionId)
            && p.hp == prepare.targetHp && !p.terminal, "Contradictory health at recovery fence revision target=" + p.target);
    }

    static void validateHealthTree(NBTBase nbt, KOMERecoveryJournalView wal, Map<String,Adoption> results) {
        if (nbt instanceof NBTTagCompound) {
            NBTTagCompound tag = (NBTTagCompound)nbt;
            if (tag.hasKey("HealthFormatVersion")) {
                KOMECampaignHealthProjection p = KOMECampaignHealthProjection.read(tag);
                validateFence(wal, p);
                for (KOMERecoveryJournalView.Entry e : wal.preparations.values()) {
                    Prepare prepare = (Prepare)e.record;
                    if (!prepare.binding.targetId.equals(p.target)) continue;
                    Adoption adoption = reconcile(wal, e, p);
                    Adoption old = results.get(prepare.grantIdentity());
                    if (old == null || adoption.ordinal() > old.ordinal()) results.put(prepare.grantIdentity(), adoption);
                }
            }
            for (Object key : tag.func_150296_c()) validateHealthTree(tag.getTag((String)key), wal, results);
        } else if (nbt instanceof NBTTagList) {
            NBTTagList list = (NBTTagList)nbt;
            if (list.func_150303_d() == 10) for (int i = 0; i < list.tagCount(); i++) validateHealthTree(list.getCompoundTagAt(i), wal, results);
        }
    }

    static boolean claimsJournal(NBTBase nbt) {
        if (nbt instanceof NBTTagCompound) {
            NBTTagCompound tag = (NBTTagCompound)nbt;
            if (tag.hasKey("RecoveryFence") || tag.hasKey("Transition") && tag.hasKey("TargetId") && tag.hasKey("Amount")) return true;
            for (Object key : tag.func_150296_c()) if (claimsJournal(tag.getTag((String)key))) return true;
        } else if (nbt instanceof NBTTagList) {
            NBTTagList list = (NBTTagList)nbt;
            if (list.func_150303_d() == 10) for (int i = 0; i < list.tagCount(); i++) if (claimsJournal(list.getCompoundTagAt(i))) return true;
        }
        return false;
    }
}
