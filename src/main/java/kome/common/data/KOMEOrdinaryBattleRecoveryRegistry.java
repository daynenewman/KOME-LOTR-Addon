package kome.common.data;

import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import static kome.common.data.KOMEConflictRecord.*;

/** ConflictData-owned history. No entity references, health writes, or pruning live here. */
public final class KOMEOrdinaryBattleRecoveryRegistry {
    public enum Coverage { COMPLETE, LEGACY_INCOMPLETE, INCOMPLETE }
    public enum Component { RIDER, MOUNT }
    public enum Disposition { PARTICIPATING, FORMAL_RETREAT, DEPARTED, DEFEATED, DESTROYED, DEAD, UNAVAILABLE }
    public enum GrantState { PENDING, APPLIED, TERMINAL }
    public enum ResolutionKind { CONQUEST, NO_TRANSFER }
    /** Closure can precede health execution in B1; closedAt is independent of this forward-only stage. */
    public enum Stage { PREPARED, PUBLICATION_STARTED, PUBLICATION_COMPLETE, RECOVERY_PENDING, CLOSURE_PENDING, COMPLETE }

    public static final class Target {
        public final UUID id;
        public final Component component;
        public final UUID physicalId;
        public final Set<UUID> aliases;
        public final boolean terminal;
        Target(UUID id, Component component, UUID physical, Set<UUID> aliases, boolean terminal) {
            this.id = Objects.requireNonNull(id); this.component = Objects.requireNonNull(component);
            physicalId = Objects.requireNonNull(physical);
            this.aliases = Collections.unmodifiableSet(new TreeSet<UUID>(aliases));
            this.terminal = terminal;
            require(this.aliases.contains(physical), "Current physical identity is not an alias");
        }
    }
    public static final class Participant {
        public final UUID id;
        public final String companyId, factionId;
        public final long admittedAt;
        public final Disposition disposition;
        public final Long exitedAt;
        public final String exitReason;
        public final Set<UUID> mountIds;
        Participant(UUID id, String company, String faction, long admitted, Disposition disposition,
                Long exited, String reason, Set<UUID> mounts) {
            this.id = Objects.requireNonNull(id); companyId = text(company); factionId = text(faction);
            require(factionId.equals(KOMEAlliance.normalizeFactionKey(factionId)), "Noncanonical faction");
            nonnegative(admitted); admittedAt = admitted; this.disposition = Objects.requireNonNull(disposition);
            exitedAt = exited; exitReason = Objects.requireNonNull(reason);
            require((disposition == Disposition.PARTICIPATING) == (exited == null), "Invalid participant exit");
            require(exited == null ? reason.isEmpty() : exited >= admitted && !reason.trim().isEmpty(), "Invalid exit metadata");
            mountIds = Collections.unmodifiableSet(new TreeSet<UUID>(mounts));
        }
        Participant exit(Disposition disposition, long at, String reason) {
            return new Participant(id, companyId, factionId, admittedAt, disposition,
                Long.valueOf(Math.max(at, admittedAt)), reason, mountIds);
        }
    }
    public static final class Intent {
        public final String conflictId, episodeId, expectedOwner, winner;
        public final long expectedRevision;
        public final State expectedState;
        public final ResolutionKind kind;
        public final float recoveryAmount;
        public final Stage stage;
        public final Long closedAt;
        Intent(String conflict, String episode, long revision, State state, ResolutionKind kind,
                String owner, String winner, float amount, Stage stage, Long closed) {
            conflictId = text(conflict); episodeId = text(episode); expectedRevision = revision;
            require(revision > 0 && state == State.ORDINARY, "Invalid resolution precondition");
            expectedState = state; this.kind = Objects.requireNonNull(kind);
            expectedOwner = Objects.requireNonNull(owner); this.winner = text(winner);
            require(owner.equals(KOMEAlliance.normalizeFactionKey(owner))
                && winner.equals(KOMEAlliance.normalizeFactionKey(winner)), "Noncanonical resolution faction");
            require((kind == ResolutionKind.NO_TRANSFER) == owner.equals(winner), "Contradictory resolution kind");
            amount(amount); recoveryAmount = amount; this.stage = Objects.requireNonNull(stage);
            closedAt = closed; if (closed != null) nonnegative(closed);
        }
        Intent advance(Stage next, Long closed) {
            require(next.ordinal() >= stage.ordinal() && next.ordinal() <= stage.ordinal() + 1,
                "Resolution stages must advance one step at a time");
            require(closedAt == null || closed == null || closedAt.equals(closed), "Conflicting closure timestamp");
            return new Intent(conflictId, episodeId, expectedRevision, expectedState, kind,
                expectedOwner, winner, recoveryAmount, next, closed == null ? closedAt : closed);
        }
    }
    /** Future health executor must durably bind a transition token to the target's health authority. */
    public static final class Transition {
        public final UUID token, physicalId;
        public final long sourceRevision;
        public final float sourceHealth, targetHealth, maxHealth;
        public Transition(UUID token, UUID physical, long revision, float source, float target, float max) {
            this.token = Objects.requireNonNull(token); physicalId = Objects.requireNonNull(physical);
            nonnegative(revision); sourceRevision = revision;
            amount(source); amount(target); amount(max);
            require(source > 0 && max > 0 && target >= source && target <= max, "Invalid health transition");
            sourceHealth = source; targetHealth = target; maxHealth = max;
        }
    }
    public static final class Grant {
        public final UUID participantId, targetId;
        public final Component component;
        public final float amount;
        public final GrantState state;
        public final Transition transition;
        public final String terminalReason;
        Grant(UUID participant, UUID target, Component component, float amount,
                GrantState state, Transition transition, String reason) {
            participantId = Objects.requireNonNull(participant); targetId = Objects.requireNonNull(target);
            this.component = Objects.requireNonNull(component); amount(amount); this.amount = amount;
            this.state = Objects.requireNonNull(state); this.transition = transition;
            terminalReason = Objects.requireNonNull(reason);
            require(state != GrantState.APPLIED || transition != null, "Applied grant lacks health transition");
            require(state == GrantState.TERMINAL ? !reason.trim().isEmpty() : reason.isEmpty(), "Invalid terminal grant reason");
            if (transition != null) require(transition.targetHealth == Math.min(transition.maxHealth,
                transition.sourceHealth + amount), "Transition does not match grant amount");
        }
        String key() { return component.name() + ":" + targetId; }
    }
    public static final class Episode {
        public final String conflictId, tileId, episodeId;
        public final long startedAt;
        public final Long endedAt;
        public final Coverage coverage;
        public final boolean actualHostileCombatObserved;
        public final Map<UUID, Participant> participants;
        public final Intent intent;
        public final Map<String, Grant> grants;
        Episode(String conflict, String tile, String episode, long started, Long ended,
                Coverage coverage, boolean observed, Map<UUID, Participant> participants,
                Intent intent, Map<String, Grant> grants) {
            conflictId = text(conflict); tileId = text(tile); episodeId = text(episode);
            require(conflict.matches("CF[1-9][0-9]*") && episode.matches(conflict + ":E[1-9][0-9]*"), "Invalid episode identity");
            KOMEConflictIdAllocator.sequenceOf(conflict);
            require(Long.parseLong(episode.substring(episode.lastIndexOf('E') + 1)) > 0, "Invalid episode sequence");
            require(tile.equals(KOMEConquestTile.normalizeId(tile)), "Noncanonical episode tile");
            nonnegative(started); startedAt = started; endedAt = ended;
            require(ended == null || ended >= started, "Invalid episode end");
            this.coverage = Objects.requireNonNull(coverage); actualHostileCombatObserved = observed;
            this.participants = Collections.unmodifiableMap(new TreeMap<UUID, Participant>(participants));
            this.intent = intent; this.grants = Collections.unmodifiableMap(new TreeMap<String, Grant>(grants));
            if (intent != null) require(conflict.equals(intent.conflictId) && episode.equals(intent.episodeId)
                && (intent.closedAt == null || ended != null && intent.closedAt >= ended), "Intent episode/closure mismatch");
            for (Participant p : participants.values()) require(p.admittedAt >= started
                && (ended == null || p.admittedAt <= ended), "Admission outside episode");
            require(grants.isEmpty() || coverage == Coverage.COMPLETE && observed && ended != null,
                "Recovery grants require complete, resolved hostile-combat coverage");
        }
        Episode update(Long ended, boolean observed, Map<UUID, Participant> people, Intent intent, Map<String, Grant> grants) {
            return new Episode(conflictId, tileId, episodeId, startedAt, ended, coverage,
                observed, people, intent, grants);
        }
    }

    private final Map<String, Episode> episodes = new TreeMap<String, Episode>();
    private final Map<UUID, Target> targets = new TreeMap<UUID, Target>();
    private final Map<UUID, UUID> aliases = new HashMap<UUID, UUID>();
    public synchronized Map<String, Episode> episodes() { return Collections.unmodifiableMap(new TreeMap<String, Episode>(episodes)); }
    public synchronized Map<UUID, Target> targets() { return Collections.unmodifiableMap(new TreeMap<UUID, Target>(targets)); }
    public synchronized Episode get(String conflict, String episode) {
        Episode result = episodes.get(episode);
        return result != null && result.conflictId.equals(conflict) ? result : null;
    }
    public synchronized Target targetForPhysical(UUID physical) { UUID id = aliases.get(physical); return id == null ? null : targets.get(id); }
    public synchronized Target target(UUID id) { return targets.get(id); }
    public synchronized void replaceFrom(KOMEOrdinaryBattleRecoveryRegistry other) {
        if (other == this) return;
        episodes.clear(); episodes.putAll(other.episodes()); targets.clear(); targets.putAll(other.targets());
        aliases.clear(); for (Target t : targets.values()) for (UUID alias : t.aliases) aliases.put(alias, t.id);
    }
    synchronized boolean begin(KOMEConflictRecord conflict, Coverage coverage) {
        CombatEpisode episode = conflict.getCombatEpisode();
        if (episode.sequence == 0 || conflict.getState() != State.ORDINARY) return false;
        Episode existing = get(conflict.getConflictId(), episode.episodeId);
        if (existing != null) {
            require(existing.tileId.equals(conflict.getTileId()) && existing.startedAt == episode.startedAtMillis,
                "Contradictory episode history"); return false;
        }
        episodes.put(episode.episodeId, new Episode(conflict.getConflictId(), conflict.getTileId(),
            episode.episodeId, episode.startedAtMillis, episode.endedAtMillis, coverage, false,
            Collections.<UUID, Participant>emptyMap(), null, Collections.<String, Grant>emptyMap()));
        return true;
    }
    synchronized boolean incomplete(KOMEConflictRecord conflict) {
        Episode e = get(conflict.getConflictId(), conflict.getCombatEpisode().episodeId);
        if (e == null || e.coverage != Coverage.COMPLETE) return false;
        episodes.put(e.episodeId, new Episode(e.conflictId, e.tileId, e.episodeId, e.startedAt,
            e.endedAt, Coverage.INCOMPLETE, e.actualHostileCombatObserved, e.participants, e.intent, e.grants)); return true;
    }
    synchronized boolean observeCombat(KOMEConflictRecord conflict, boolean hostilePair) {
        Episode e = get(conflict.getConflictId(), conflict.getCombatEpisode().episodeId);
        if (e == null || e.endedAt != null || !hostilePair || e.actualHostileCombatObserved) return false;
        episodes.put(e.episodeId, e.update(null, true, e.participants, e.intent, e.grants)); return true;
    }
    private Target identify(UUID physical, Component kind, UUID logical) {
        Target existing = targetForPhysical(physical);
        if (existing != null) {
            require(existing.component == kind && existing.physicalId.equals(physical) && !existing.terminal,
                "Stale or terminal recovery target alias"); require(logical == null || logical.equals(existing.id), "Health target identity conflicts with registry"); return existing;
        }
        require(logical == null || !targets.containsKey(logical), "Logical target already has another alias");
        Target created = new Target(logical == null ? UUID.randomUUID() : logical, kind, physical, Collections.singleton(physical), false);
        return created;
    }
    synchronized boolean enroll(KOMEConflictRecord conflict, UUID physical, String company, String faction,
            long now, UUID mountPhysical) {
        return enroll(conflict, physical, company, faction, now, mountPhysical, null, null);
    }
    synchronized boolean enroll(KOMEConflictRecord conflict, UUID physical, String company, String faction,
            long now, UUID mountPhysical, UUID riderLogical, UUID mountLogical) {
        Episode e = get(conflict.getConflictId(), conflict.getCombatEpisode().episodeId);
        if (e == null || e.endedAt != null || e.intent != null) return false;
        require(physical != null && !physical.equals(mountPhysical), "Rider and mount identities collide");
        Target rider = identify(physical, Component.RIDER, riderLogical);
        Target mount = mountPhysical == null ? null : identify(mountPhysical, Component.MOUNT, mountLogical);
        require(mount == null || !rider.id.equals(mount.id), "Rider and mount logical identities collide");
        Participant previous = e.participants.get(rider.id);
        Set<UUID> mounts = new TreeSet<UUID>();
        if (previous != null) mounts.addAll(previous.mountIds);
        if (mount != null) mounts.add(mount.id);
        if (previous != null && previous.disposition == Disposition.PARTICIPATING && previous.mountIds.equals(mounts)) return false;
        Participant next = new Participant(rider.id, previous == null ? company : previous.companyId,
            previous == null ? faction : previous.factionId, previous == null ? Math.max(now, e.startedAt) : previous.admittedAt,
            Disposition.PARTICIPATING, null, "", mounts);
        Map<UUID, Participant> people = new TreeMap<UUID, Participant>(e.participants); people.put(next.id, next);
        Episode updated = e.update(null, e.actualHostileCombatObserved, people, e.intent, e.grants);
        targets.put(rider.id, rider); aliases.put(physical, rider.id);
        if (mount != null) { targets.put(mount.id, mount); aliases.put(mountPhysical, mount.id); }
        episodes.put(e.episodeId, updated); return true;
    }
    synchronized boolean exit(KOMEConflictRecord conflict, Collection<UUID> physicalIds,
            Disposition disposition, long now, String reason) {
        Episode e = get(conflict.getConflictId(), conflict.getCombatEpisode().episodeId);
        if (e == null) return false;
        Map<UUID, Participant> people = new TreeMap<UUID, Participant>(e.participants); boolean changed = false;
        for (UUID physical : physicalIds) {
            Target target = targetForPhysical(physical); Participant p = target == null ? null : people.get(target.id);
            if (p == null || terminal(p.disposition) || p.disposition == disposition) continue;
            people.put(p.id, p.exit(disposition, now, reason)); changed = true;
        }
        if (changed) episodes.put(e.episodeId, e.update(e.endedAt, e.actualHostileCombatObserved, people, e.intent, e.grants));
        return changed;
    }
    /** A verified reconstruction maps physical aliases, never transfers authority to a remount. */
    public synchronized boolean rekeyVerified(UUID oldPhysical, UUID newPhysical, Component component) {
        Target t = targetForPhysical(oldPhysical); if (t == null || oldPhysical.equals(newPhysical)) return false;
        require(t.component == component && !t.terminal && t.physicalId.equals(oldPhysical), "Rekey source is stale/terminal");
        require(targetForPhysical(newPhysical) == null, "Reconstruction aliases collide");
        Set<UUID> next = new TreeSet<UUID>(t.aliases); next.add(newPhysical);
        targets.put(t.id, new Target(t.id, component, newPhysical, next, false)); aliases.put(newPhysical, t.id); return true;
    }
    public synchronized boolean terminalize(UUID physical, Disposition disposition, long now, String reason) {
        require(terminal(disposition), "Terminal disposition required");
        nonnegative(now); text(reason);
        Target t = targetForPhysical(physical); if (t == null) return false;
        require(t.physicalId.equals(physical), "Obsolete physical alias cannot terminalize current body");
        if (t.terminal && disposition != Disposition.DEAD) return false;
        targets.put(t.id, new Target(t.id, t.component, t.physicalId, t.aliases, true));
        for (Episode e : new ArrayList<Episode>(episodes.values())) {
            Map<UUID, Participant> people = new TreeMap<UUID, Participant>(e.participants);
            Participant p = people.get(t.id);
            if (p != null) people.put(p.id, p.exit(disposition, now, reason));
            Map<String, Grant> grants = new TreeMap<String, Grant>(e.grants);
            for (Grant g : e.grants.values()) if (g.targetId.equals(t.id) && g.state == GrantState.PENDING)
                grants.put(g.key(), new Grant(g.participantId, g.targetId, g.component, g.amount, GrantState.TERMINAL, g.transition, reason));
            episodes.put(e.episodeId, e.update(e.endedAt, e.actualHostileCombatObserved, people, e.intent, grants));
        }
        return true;
    }
    synchronized boolean end(KOMEConflictRecord conflict, long now) {
        Episode e = get(conflict.getConflictId(), conflict.getCombatEpisode().episodeId);
        if (e == null || e.endedAt != null) return false;
        episodes.put(e.episodeId, e.update(Long.valueOf(Math.max(now, e.startedAt)), e.actualHostileCombatObserved,
            e.participants, e.intent, e.grants)); return true;
    }
    /** Exact initial revision is mandatory; retries retain the original amount and ownership decision. */
    public synchronized Intent prepareIntent(KOMEConflictRecord live, long expectedRevision,
            String episodeId, ResolutionKind kind, String owner, String winner, float amount) {
        require(live != null && live.isActive() && live.getState() == State.ORDINARY
            && live.getRevision() == expectedRevision && live.getCombatEpisode().episodeId.equals(episodeId), "Stale resolution intent precondition");
        Episode e = get(live.getConflictId(), episodeId); require(e != null, "Missing episode recovery authority");
        Intent old = e.intent;
        if (old != null) {
            require(old.kind == kind && old.winner.equals(winner)
                && old.expectedOwner.equals(owner), "Conflicting second resolution intent"); return old;
        }
        float snapshottedAmount = e.grants.isEmpty() ? amount : e.grants.values().iterator().next().amount;
        Intent intent = new Intent(e.conflictId, e.episodeId, expectedRevision, live.getState(), kind,
            owner, winner, snapshottedAmount, Stage.PREPARED, null);
        episodes.put(e.episodeId, e.update(e.endedAt, e.actualHostileCombatObserved, e.participants, intent, e.grants)); return intent;
    }
    public synchronized boolean hasIntent(KOMEConflictRecord conflict) {
        for (Episode e : episodes.values()) if (e.conflictId.equals(conflict.getConflictId()) && e.intent != null) return true;
        return false;
    }
    synchronized void advance(String conflict, String episode, Stage stage) {
        Episode e = get(conflict, episode); require(e != null && e.intent != null, "Missing resolution intent");
        if (stage == Stage.COMPLETE) {
            require(e.intent.closedAt != null, "Resolution is not closed");
            for (Grant g : e.grants.values()) require(g.state != GrantState.PENDING, "Recovery remains pending");
        }
        Intent intent = e.intent.advance(stage, null);
        episodes.put(e.episodeId, e.update(e.endedAt, e.actualHostileCombatObserved, e.participants, intent, e.grants));
    }
    synchronized void closed(KOMEConflictRecord conflict, long now) {
        end(conflict, now); Episode e = get(conflict.getConflictId(), conflict.getCombatEpisode().episodeId);
        if (e == null || e.intent == null) return;
        episodes.put(e.episodeId, e.update(e.endedAt, e.actualHostileCombatObserved, e.participants,
            e.intent.advance(e.intent.stage, Long.valueOf(now)), e.grants));
    }
    /** Freeze obligations only for proven complete coverage. HP is deliberately untouched. */
    public synchronized boolean prepareGrants(String conflict, String episode, float amount) {
        amount(amount); Episode e = get(conflict, episode); require(e != null && e.endedAt != null, "Episode has not resolved");
        if (e.coverage != Coverage.COMPLETE || !e.actualHostileCombatObserved) return false;
        if (e.intent != null) require(amount == e.intent.recoveryAmount, "Recovery amount differs from intent");
        Map<String, Grant> grants = new TreeMap<String, Grant>(e.grants);
        for (Participant p : e.participants.values()) {
            if (terminal(p.disposition)) continue;
            Target rider = targets.get(p.id); if (rider.terminal) continue;
            addGrant(grants, new Grant(p.id, rider.id, Component.RIDER, amount, GrantState.PENDING, null, ""));
            for (UUID mount : p.mountIds) if (!targets.get(mount).terminal)
                addGrant(grants, new Grant(p.id, mount, Component.MOUNT, amount, GrantState.PENDING, null, ""));
        }
        boolean changed = grants.size() != e.grants.size();
        episodes.put(e.episodeId, e.update(e.endedAt, e.actualHostileCombatObserved, e.participants, e.intent, grants)); return changed;
    }
    private static void addGrant(Map<String, Grant> grants, Grant next) {
        Grant old = grants.get(next.key());
        if (old == null) grants.put(next.key(), next);
        else require(old.amount == next.amount && old.component == next.component, "Contradictory component grant");
        // Shared mount paths select the first participant deterministically and never grant twice.
    }
    static boolean terminal(Disposition d) { return d == Disposition.DEAD || d == Disposition.DESTROYED || d == Disposition.UNAVAILABLE; }
    static void require(boolean valid, String message) { if (!valid) throw new IllegalArgumentException(message); }
    static String text(String value) { require(value != null && !value.isEmpty() && value.equals(value.trim()), "Invalid identity text"); return value; }
    static void nonnegative(long value) { require(value >= 0, "Negative timestamp/revision"); }
    static void amount(float value) { require(!Float.isNaN(value) && !Float.isInfinite(value) && value >= 0, "Invalid recovery/health amount"); }

    public synchronized NBTTagCompound write() { return KOMEOrdinaryBattleRecoveryPersistence.write(this); }
    public static KOMEOrdinaryBattleRecoveryRegistry read(NBTTagCompound tag) { return KOMEOrdinaryBattleRecoveryPersistence.read(tag); }
    static KOMEOrdinaryBattleRecoveryRegistry restore(Map<UUID, Target> targets, Map<String, Episode> episodes) {
        KOMEOrdinaryBattleRecoveryRegistry result = new KOMEOrdinaryBattleRecoveryRegistry();
        result.targets.putAll(targets); result.episodes.putAll(episodes);
        for (Target t : targets.values()) for (UUID alias : t.aliases)
            require(result.aliases.put(alias, t.id) == null, "Duplicate recovery physical alias");
        Set<UUID> referenced = new HashSet<UUID>();
        Set<UUID> transitionTokens = new HashSet<UUID>();
        for (Episode e : episodes.values()) {
            for (Participant p : e.participants.values()) {
                Target rider = targets.get(p.id); require(rider != null && rider.component == Component.RIDER, "Participant target missing");
                referenced.add(p.id);
                for (UUID id : p.mountIds) { Target mount = targets.get(id);
                    require(mount != null && mount.component == Component.MOUNT, "Mount lineage missing"); referenced.add(id); }
            }
            Float episodeAmount = null;
            for (Grant g : e.grants.values()) {
                require(episodeAmount == null || episodeAmount.floatValue() == g.amount, "Contradictory episode recovery amounts");
                episodeAmount = Float.valueOf(g.amount);
                Participant p = e.participants.get(g.participantId); Target t = targets.get(g.targetId);
                require(p != null && t != null && t.component == g.component && (g.component == Component.RIDER
                    ? p.id.equals(g.targetId) : p.mountIds.contains(g.targetId)), "Unproven component grant lineage");
                require(e.intent == null || g.amount == e.intent.recoveryAmount, "Grant amount contradicts intent");
                if (g.transition != null) require(t.aliases.contains(g.transition.physicalId)
                    && transitionTokens.add(g.transition.token), "Invalid or duplicate health transition identity");
            }
            if (e.intent != null && e.intent.stage == Stage.COMPLETE) {
                require(e.intent.closedAt != null, "Complete intent has no closure");
                for (Grant g : e.grants.values()) require(g.state != GrantState.PENDING, "Complete intent has pending recovery");
            }
        }
        require(referenced.equals(targets.keySet()), "Unreferenced recovery target");
        return result;
    }
}
