package kome.common.data;

import java.io.IOException;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/** Session readiness, deliberately independent of first-tick world initialization. */
public final class KOMERecoveryStartup {
    public enum State { UNINITIALIZED, READY, BLOCKED_MISSING, BLOCKED_CORRUPT, BLOCKED_CONTRADICTORY, STOPPING }
    private static KOMERecoveryStartup session;
    private static final ThreadLocal<KOMERecoveryStartup> LOADING = new ThreadLocal<KOMERecoveryStartup>();
    private final Object server;
    private KOMERecoveryJournalManager manager;
    private State state = State.UNINITIALIZED;
    private String diagnostic = "Awaiting canonical root-world recovery reconciliation";
    private KOMERecoveryJournalView view;
    private KOMERecoveryStartupPlan plan;
    private KOMEWorldData data;
    private boolean loading, provision, claimsJournal;
    private List<KOMECampaignHealthProjection> physicalEvidence = Collections.emptyList();
    private RuntimeException loadFailure;
    private final Set<UUID> quarantinedTargets = new TreeSet<UUID>();
    private final Thread owner = Thread.currentThread();
    private KOMERecoveryJournalRecord uncertain;
    private final Map<UUID, KOMECampaignHealthProjection> provisionalAliases = new HashMap<UUID, KOMECampaignHealthProjection>();
    KOMERecoveryRuntime runtime;
    private final Set<UUID> adopting = new HashSet<UUID>();

    boolean permitsWork(String conflict, UUID target) {
        return ready() && (target == null || !quarantinedTargets.contains(target))
            && (plan == null || !plan.quarantinedConflicts.contains(conflict))
            && (data == null || !data.getOrdinaryBattleRecovery().quarantined(conflict, quarantinedTargets));
    }
    KOMECampaignHealthProjection reconcileHealth(KOMECampaignHealthProjection a, KOMECampaignHealthProjection b) {
        KOMERecoveryStartupPlan.validateFence(view,a); KOMERecoveryStartupPlan.validateFence(view,b);
        KOMEOrdinaryBattleRecoveryRegistry.require(a.target.equals(b.target) && a.component == b.component && a.alias.equals(b.alias),
            "Recovery health identity mismatch");
        if (a.revision == b.revision) return KOMECampaignHealthProjection.reconcile(a,b);
        KOMECampaignHealthProjection newer = a.revision > b.revision ? a : b, older = newer == a ? b : a;
        KOMEOrdinaryBattleRecoveryRegistry.require(!older.terminal || newer.terminal, "Terminal health cannot be superseded");
        KOMEOrdinaryBattleRecoveryRegistry.require(older.fence == null || KOMERecoveryStartupPlan.descends(view,newer.fence,older.fence.sequence),
            "Recovery health ancestry lost");
        KOMERecoveryJournalView.Entry prepare = view.latestPreparations.get(newer.target);
        if (prepare != null) KOMERecoveryStartupPlan.reconcile(view,prepare,newer);
        return newer;
    }
    void beginAdoption(KOMERecoveryJournalView.Entry entry, KOMECampaignHealthProjection source) {
        KOMERecoveryJournalRecord.Prepare p = (KOMERecoveryJournalRecord.Prepare)entry.record;
        requireRuntime(p.binding.conflictId,p.binding.targetId);
        KOMEOrdinaryBattleRecoveryRegistry.require(view.records.get(entry.sequence) == entry
            && view.latestPreparations.get(source.target) == entry && !adopting.contains(source.target)
            && KOMERecoveryStartupPlan.reconcile(view,entry,source) == KOMERecoveryStartupPlan.Adoption.PENDING
            && source.revision == p.sourceRevision && source.transition.equals(p.sourceTransitionId) && source.hp == p.sourceHp,
            "Executor does not own exact reserved source");
        adopting.add(source.target);
    }
    void finishAdoption(UUID target) { adopting.remove(target); }
    static void observeEntity(Entity entity) { if (session != null && session.runtime != null) session.runtime.observe(entity); }
    static void forgetEntity(Entity entity) { if (session != null && session.runtime != null) session.runtime.forget(entity); }

    KOMERecoveryJournalView journal() { return view; }
    void requireRuntime(String conflict, UUID target) {
        KOMEOrdinaryBattleRecoveryRegistry.require(Thread.currentThread() == owner, "Recovery requires owning server thread");
        rescanUncertain();
        KOMEOrdinaryBattleRecoveryRegistry.require(permitsWork(conflict,target), "Recovery authority blocked: " + diagnostic);
    }
    void quarantine(UUID target, String reason) {
        if (!quarantinedTargets.add(target)) return;
        diagnostic = "Recovery target quarantined=" + target + ": " + reason; log();
    }
    KOMERecoveryJournalView.Entry append(KOMERecoveryJournalRecord record) {
        KOMEOrdinaryBattleRecoveryRegistry.require(Thread.currentThread() == owner && ready(), "Journal writer is not ready");
        try {
            KOMERecoveryJournal.Acknowledgment ack = manager.append(record, view.lastSequence, view.lastHash);
            view = manager.view();
            return view.records.get(ack.sequence);
        } catch (IOException | RuntimeException failure) {
            uncertain = record;
            block(State.BLOCKED_CORRUPT, "Durable " + record.type + " acknowledgment failed; rescan required: " + failure);
            throw new IllegalArgumentException(diagnostic, failure);
        }
    }
    private void rescanUncertain() {
        if (uncertain == null || state == State.STOPPING) return;
        try {
            manager.open();
            if (manager.state() != KOMERecoveryJournal.State.READY) return;
            KOMERecoveryJournalView rescanned = manager.view();
            for (KOMERecoveryJournalView.Entry entry : rescanned.records.values()) {
                if (!entry.record.identity().equals(uncertain.identity())) continue;
                KOMEOrdinaryBattleRecoveryRegistry.require(Arrays.equals(KOMERecoveryJournalCodec.encode(entry.record),
                    KOMERecoveryJournalCodec.encode(uncertain)), "Uncertain append identity contradicts WAL");
                view = rescanned;
                // Alias/terminal activation belongs to its transaction, never to an unrelated retry.
                if (uncertain instanceof KOMERecoveryJournalRecord.Lineage || uncertain instanceof KOMERecoveryJournalRecord.Terminal) return;
                uncertain = null; state = State.READY;
                diagnostic = "Recovery READY; uncertain append found and forced by full WAL rescan"; log(); return;
            }
            block(State.BLOCKED_CORRUPT, "Uncertain append absent after rescan; replacement identity prohibited");
        } catch (IOException | RuntimeException failure) { block(State.BLOCKED_CORRUPT, "Uncertain append rescan failed: " + failure); }
    }
    static void stageAlias(KOMECampaignHealthProjection source, UUID next) {
        if (session != null && session.view != null && session.view.currentPhysicalAliases.containsKey(source.target))
            session.provisionalAliases.put(next, source);
    }
    void activated(UUID alias) { provisionalAliases.remove(alias); }
    static void discardAlias(UUID alias) { if (session != null) session.provisionalAliases.remove(alias); }
    void requireStaged(KOMECampaignHealthProjection p, UUID next) {
        KOMECampaignHealthProjection staged = provisionalAliases.get(next);
        KOMEOrdinaryBattleRecoveryRegistry.require(staged != null && staged.write().equals(p.write()),
            "Replacement lacks exact verified reconstruction source");
    }

    KOMERecoveryStartup(Object server) { this.server = server; }
    public State state() { return state; }
    public String diagnostic() { return diagnostic; }
    public static void beginSession(Object server) {
        stopSession(); session = new KOMERecoveryStartup(server);
    }
    public static void stopSession() {
        if (session != null) session.stop();
    }
    void stop() {
        if (state == State.STOPPING) return;
        state = State.STOPPING;
        diagnostic = "Recovery startup STOPPING; accepting no new work";
        if (manager != null) try { manager.close(); }
        catch (IOException | RuntimeException failure) { diagnostic += "; close failed: " + failure; log(); }
    }
    public static void worldUnloaded(World world) {
        if (world != null && !world.isRemote && world.provider.dimensionId == 0) stopSession();
    }
    static World root(World world) {
        if (!(world instanceof WorldServer)) return world;
        MinecraftServer server = MinecraftServer.getServer();
        World root = world.provider.dimensionId == 0 ? world : server == null ? null : server.worldServerForDimension(0);
        return root == null ? world : root;
    }
    /** Called before MapStorage.loadData: decode can only see this session's independently opened journal. */
    static KOMERecoveryStartup beforeLoad(World world) {
        if (!(world instanceof WorldServer)) return null;
        MinecraftServer current = MinecraftServer.getServer();
        if (session == null || session.server != current) beginSession(current);
        KOMERecoveryStartup s = session;
        if (s.data != null || s.loading || s.state != State.UNINITIALIZED) return s;
        if (!(world instanceof WorldServer) || world.provider.dimensionId != 0) return s;
        try {
            s.manager = KOMERecoveryJournalManager.forRoot((WorldServer)world, current);
            s.open();
        } catch (IOException | RuntimeException failure) { s.block(State.BLOCKED_CORRUPT, "Journal open failed: " + failure); }
        s.loading = true; LOADING.set(s);
        return s;
    }
    void open() throws IOException {
        provision = manager.state() == KOMERecoveryJournal.State.UNPROVISIONED;
        if (provision) { inventory(); return; }
        manager.open();
        if (manager.state() != KOMERecoveryJournal.State.READY) {
            block(manager.state() == KOMERecoveryJournal.State.CORRUPT ? State.BLOCKED_CORRUPT : State.BLOCKED_MISSING,
                manager.diagnostic()); return;
        }
        view = manager.view();
        inventory();
    }
    private void inventory() throws IOException {
        physicalEvidence = KOMERecoveryHealthEvidence.scan(manager.file().getParentFile().getParentFile());
        for (KOMECampaignHealthProjection p : physicalEvidence) claimsJournal |= p.fence != null;
        if (provision && claimsJournal) block(State.BLOCKED_CONTRADICTORY, "Unloaded durable health claims a WAL fence; bootstrap refused");
    }
    static NBTTagCompound prepareCandidate(NBTTagCompound persisted) {
        KOMERecoveryStartup s = LOADING.get();
        return s == null ? persisted : s.prepare(persisted);
    }
    static KOMERecoveryStartup loadingSession() { return LOADING.get(); }
    NBTTagCompound prepare(NBTTagCompound persisted) {
        if (persisted == null) return null;
        claimsJournal |= KOMERecoveryStartupPlan.claimsJournal(persisted);
        if (provision && claimsJournal) block(State.BLOCKED_CONTRADICTORY,
            "First-deployment provisioning refused: durable state claims WAL-backed PREPARE/fence");
        if (view == null || state != State.UNINITIALIZED) return persisted;
        try {
            plan = KOMERecoveryStartupPlan.build(persisted, view, physicalEvidence);
            quarantinedTargets.addAll(plan.quarantinedTargets.keySet());
            return plan.candidate;
        }
        catch (RuntimeException contradiction) {
            block(State.BLOCKED_CONTRADICTORY, "Journal/ConflictData or health contradiction: " + contradiction.getMessage());
            // The unchanged canonical decoder still rejects malformed v3. No partial plan is published.
            return persisted;
        }
    }
    static void afterLoad(KOMERecoveryStartup s, KOMEWorldData data) {
        if (s == null) return;
        data.recoveryStartup = s;
        data.getConflictService().bindRecoveryStartup(data);
        if (s.data != null || !s.loading) return;
        s.data = data;
        try {
            if (s.state != State.UNINITIALIZED) return;
            if (s.provision) {
                if (s.claimsJournal) { s.block(State.BLOCKED_CONTRADICTORY, "Persisted WAL claims prohibit bootstrap"); return; }
                s.manager.bootstrap(); s.view = s.manager.view();
            }
            if (s.view == null || s.manager.state() != KOMERecoveryJournal.State.READY) {
                s.block(State.BLOCKED_CORRUPT, "No fully validated journal available"); return;
            }
            if (s.plan == null) {
                if (!s.view.records.isEmpty()) { s.block(State.BLOCKED_CONTRADICTORY, "WAL history exists but canonical world recovery candidate was not decoded"); return; }
                for (KOMECampaignHealthProjection p : s.physicalEvidence)
                    KOMERecoveryStartupPlan.validateFence(s.view, p);
            }
            s.state = State.READY;
            s.runtime = new KOMERecoveryRuntime(data,s,s.plan == null
                ? Collections.<UUID,KOMECampaignHealthProjection>emptyMap() : s.plan.health);
            if (s.server == MinecraftServer.getServer()) s.runtime.inventoryLoaded(MinecraftServer.getServer());
            KOMERecoveryExecutor.reconcileStartup(data);
            s.diagnostic = "Recovery READY; " + (s.provision ? "first-deployment bootstrap" : "established journal open")
                + "; journal=" + s.view.journalId + "; repaired episodes=" + (s.plan == null ? 0 : s.plan.repairedEpisodes)
                + "; quarantined conflicts=" + (s.plan == null ? "[]" : s.plan.quarantinedConflicts)
                + "; quarantined targets=" + s.quarantinedTargets
                + "; quarantine reasons=" + (s.plan == null ? "{}" : s.plan.quarantinedTargets)
                + "; PREPARE evidence=" + (s.plan == null ? "{}" : s.plan.preparations)
                + "; consumed grants reconciled from verified health; no startup HP effects";
            if (s.plan != null && s.plan.changed) data.markDirty();
            s.log();
        } catch (IOException | RuntimeException failure) { s.block(State.BLOCKED_CORRUPT, "Recovery initialization failed: " + failure); }
    }
    static void endLoad(KOMERecoveryStartup s) {
        if (s != null && LOADING.get() == s) { LOADING.remove(); s.loading = false; }
    }
    static void failedLoad(RuntimeException failure) {
        KOMERecoveryStartup s = LOADING.get();
        if (s != null) { s.loadFailure = failure; s.block(State.BLOCKED_CONTRADICTORY, "Canonical world load rejected: " + failure.getMessage()); }
    }
    void requireLoaded() {
        if (loadFailure != null) throw new IllegalStateException("Refusing replacement after failed canonical world decode", loadFailure);
    }
    void block(State reason, String text) { state = reason; diagnostic = "Recovery " + reason + ": " + text; log(); }
    private void log() { System.err.println("[KOME] " + diagnostic); }
    static boolean permits(KOMEWorldData data, KOMEConflictRecord conflict) {
        KOMERecoveryStartup s = data == null ? null : data.recoveryStartup;
        // Detached metadata fixtures/candidates have no server authority. Every production get binds a session.
        if (s == null) return true;
        if (!s.ready()) return false;
        if (conflict == null) return true;
        if (s.plan != null && s.plan.quarantinedConflicts.contains(conflict.getConflictId())) return false;
        return !data.getOrdinaryBattleRecovery().quarantined(conflict.getConflictId(),s.quarantinedTargets);
    }
    private boolean ready() {
        if (state == State.READY && (manager == null || manager.state() != KOMERecoveryJournal.State.READY))
            block(State.BLOCKED_CORRUPT, "Journal lost readiness: " + (manager == null ? "missing manager" : manager.diagnostic()));
        return state == State.READY;
    }
    static boolean permitsTarget(KOMEWorldData data, UUID physical) {
        KOMERecoveryStartup s = data.recoveryStartup;
        if (s == null) return true;
        KOMEOrdinaryBattleRecoveryRegistry.Target t = data.getOrdinaryBattleRecovery().targetForPhysical(physical);
        return s.ready() && (t == null || !s.quarantinedTargets.contains(t.id)
            && !s.view.terminalTargets.contains(t.id));
    }
    static boolean healthReady(Entity entity) {
        if (entity == null || !(entity.worldObj instanceof WorldServer) || entity.worldObj.isRemote) return true;
        // Do not recursively load world data from a native health callback during entity deserialization.
        return session != null && session.server == MinecraftServer.getServer() && session.ready();
    }
    static boolean validateProjection(Entity entity, KOMECampaignHealthProjection projection) {
        if (!(entity.worldObj instanceof WorldServer) || entity.worldObj.isRemote) return true;
        if (!healthReady(entity)) return false;
        KOMERecoveryStartup s = session;
        // A detached, verified-source reconstruction is not authoritative until LINEAGE is forced.
        if (s.provisionalAliases.containsKey(projection.alias)) return false;
        if (s.quarantinedTargets.contains(projection.target)) return false;
        try {
            KOMERecoveryStartupPlan.validateFence(s.view,projection);
            KOMERecoveryJournalView.Entry latest = s.view.latestPreparations.get(projection.target);
            if (latest != null) KOMERecoveryStartupPlan.reconcile(s.view,latest,projection);
            if (s.view.terminalTargets.contains(projection.target) && !projection.terminal) return false;
            return true;
        } catch (RuntimeException failure) {
            s.quarantinedTargets.add(projection.target);
            UUID owner = s.view.logicalTargetsByAlias.get(projection.alias);
            if (owner != null) s.quarantinedTargets.add(owner);
            System.err.println("[KOME] Recovery target quarantined=" + projection.target + ": " + failure.getMessage());
            return false;
        }
    }
    static boolean permitsHealthChange(Entity entity, KOMECampaignHealthProjection projection) {
        if (!(entity.worldObj instanceof WorldServer) || entity.worldObj.isRemote) return true;
        if (!validateProjection(entity, projection)) return false;
        return session.permitsMutation(projection);
    }
    boolean permitsMutation(KOMECampaignHealthProjection projection) {
        if (!ready() || adopting.contains(projection.target) || quarantinedTargets.contains(projection.target)
                || view.terminalTargets.contains(projection.target)) return false;
        KOMERecoveryJournalView.Entry e = view.latestPreparations.get(projection.target);
        return e == null || KOMERecoveryStartupPlan.reconcile(view,e,projection) == KOMERecoveryStartupPlan.Adoption.CONSUMED;
    }
    static boolean mayInstallHP(Entity entity, KOMECampaignHealthProjection p) {
        if (!(entity.worldObj instanceof WorldServer) || entity.worldObj.isRemote) return true;
        if (!healthReady(entity) || session.quarantinedTargets.contains(p.target) || session.adopting.contains(p.target)) return false;
        if (!session.view.currentPhysicalAliases.containsKey(p.target)) return p.fence == null;
        // Reproject already-proven persisted ancestry, never infer adoption from PREPARE alone.
        KOMECampaignHealthProjection known = session.runtime == null ? null : session.runtime.health(p.target,p.component);
        KOMERecoveryJournalView.Entry prepare = session.view.latestPreparations.get(p.target);
        return known != null && known.write().equals(p.write()) && (prepare == null ? p.fence == null
            : KOMERecoveryStartupPlan.reconcile(session.view,prepare,known) == KOMERecoveryStartupPlan.Adoption.CONSUMED);
    }
    static void requireDetachedReconciliation(KOMECampaignHealthProjection p, boolean rekey) {
        if (session == null || !(session.server instanceof MinecraftServer)) return;
        KOMEOrdinaryBattleRecoveryRegistry.require(session.state == State.READY,
            "Recovery health reconciliation awaits startup: " + session.state);
        KOMEOrdinaryBattleRecoveryRegistry.require(!session.quarantinedTargets.contains(p.target), "Recovery target quarantined");
        KOMERecoveryStartupPlan.validateFence(session.view, p);
        if (rekey) KOMEOrdinaryBattleRecoveryRegistry.require(session.permitsMutation(p), "Pending reservation prevents reconstruction");
    }
    static void requireAliasPublication(KOMECampaignHealthProjection p, UUID next) {
        if (session == null) return;
        if (session.server instanceof MinecraftServer) KOMEOrdinaryBattleRecoveryRegistry.require(session.ready(),
            "Alias publication awaits recovery readiness");
        if (session.view == null || !session.view.currentPhysicalAliases.containsKey(p.target)) return;
        if (p.alias.equals(next)) { KOMERecoveryStartupPlan.validateFence(session.view, p); return; }
        KOMERecoveryJournalView.Entry entry = session.view.lineages.get(p.target);
        KOMEOrdinaryBattleRecoveryRegistry.require(entry != null, "Missing durable LINEAGE activation");
        KOMERecoveryJournalRecord.Lineage l = (KOMERecoveryJournalRecord.Lineage)entry.record;
        KOMEOrdinaryBattleRecoveryRegistry.require(session.ready() && l.oldAlias.equals(p.alias) && l.newAlias.equals(next)
            && l.healthRevision == p.revision && l.healthTransitionId.equals(p.transition), "Health publication contradicts durable LINEAGE");
    }
    static String reason(KOMEWorldData data) {
        return "Recovery authority is awaiting reconciliation or quarantined; see the server recovery diagnostics.";
    }
    // Real filesystem fixture, using the same open/prepare/publication lifecycle as the root-world adapter.
    void initialize(KOMERecoveryJournalManager manager, NBTTagCompound persisted, KOMEWorldData target) throws IOException {
        this.manager = manager; open(); loading = true;
        NBTTagCompound candidate = prepare(persisted);
        if (candidate != null && candidate.hasKey(KOMEConflictPersistence.SCHEMA_KEY)) {
            KOMEConflictPersistence.Loaded loaded = KOMEConflictPersistence.readSection(candidate);
            target.getConflictService().replaceFrom(loaded.conflicts);
            target.getJoinBattleDeploymentReceipts().replaceFrom(loaded.joinBattleReceipts);
            target.getOrdinaryBattleRecovery().replaceFrom(loaded.recovery);
        }
        afterLoad(this, target); loading = false;
    }
}
