package kome.common.data;

import java.io.IOException;
import java.util.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;
import static kome.common.data.KOMERecoveryJournalRecord.*;

/** Immutable validated journal snapshot. No mutable NBT or entity references. */
public final class KOMERecoveryJournalView {
    public final UUID journalId;
    public final long validLength, fileLength, lastSequence;
    public final String lastHash;
    public final Map<Long, Entry> records;
    public final Map<String, Entry> contexts, preparations;
    public final Map<UUID, Entry> lineages, terminals;
    public final Map<UUID, UUID> currentPhysicalAliases;
    public final Map<UUID, UUID> logicalTargetsByAlias;
    public final Map<UUID, Component> targetComponents;
    /** Includes both explicit TERMINAL records and terminal targets sealed in episode contexts. */
    public final Set<UUID> terminalTargets;
    public boolean hasIncompleteTail() { return fileLength!=validLength; }
    KOMERecoveryJournalView(Index index,long validLength,long fileLength,String lastHash) {
        this.journalId=index.journalId; this.validLength=validLength; this.fileLength=fileLength; this.lastHash=lastHash;
        records=immutable(index.records); contexts=immutable(index.contexts); preparations=immutable(index.preparations);
        lineages=immutable(index.lineages); terminals=immutable(index.terminals);
        currentPhysicalAliases=immutable(new TreeMap<UUID,UUID>(index.currentAlias));
        logicalTargetsByAlias=immutable(new TreeMap<UUID,UUID>(index.aliases));
        targetComponents=immutable(new TreeMap<UUID,Component>(index.components));
        Set<UUID> terminalIds=new TreeSet<UUID>(index.contextTerminal); terminalIds.addAll(index.terminals.keySet());
        terminalTargets=Collections.unmodifiableSet(terminalIds);
        lastSequence=records.isEmpty()?0:index.records.lastKey();
    }
    private static <K,V> Map<K,V> immutable(Map<K,V> source) { return Collections.unmodifiableMap(new LinkedHashMap<K,V>(source)); }
    public static final class Entry {
        public final long sequence, offset, endOffset;
        public final String hash;
        public final KOMERecoveryJournalRecord record;
        Entry(long sequence,long offset,long endOffset,String hash,KOMERecoveryJournalRecord record) {
            this.sequence=sequence; this.offset=offset; this.endOffset=endOffset; this.hash=hash; this.record=record;
        }
    }
    static final class Index {
        final UUID journalId;
        final Map<String,String> conflictTiles=new HashMap<String,String>();
        final TreeMap<Long,Entry> records=new TreeMap<Long,Entry>();
        final Map<String,Entry> contexts=new TreeMap<String,Entry>(), preparations=new TreeMap<String,Entry>(), identities=new HashMap<String,Entry>();
        final Map<UUID,Entry> lineages=new TreeMap<UUID,Entry>(), terminals=new TreeMap<UUID,Entry>(), latestPrepare=new HashMap<UUID,Entry>();
        final Map<UUID,UUID> aliases=new HashMap<UUID,UUID>(), currentAlias=new HashMap<UUID,UUID>();
        final Map<UUID,Component> components=new HashMap<UUID,Component>();
        final Set<UUID> recoveryTokens=new HashSet<UUID>(), contextTerminal=new HashSet<UUID>();
        Index(UUID journalId) { this.journalId=journalId; }
        Entry duplicate(KOMERecoveryJournalRecord record) throws IOException {
            Entry old=identities.get(record.identity());
            if(old!=null) require(Arrays.equals(KOMERecoveryJournalCodec.encode(old.record),KOMERecoveryJournalCodec.encode(record)),
                "Conflicting duplicate identity="+record.identity());
            return old;
        }
        void validate(KOMERecoveryJournalRecord record) {
            require(!identities.containsKey(record.identity()),"Duplicate persisted identity="+record.identity());
            if(record instanceof EpisodeContext) {
                EpisodeContext c=(EpisodeContext)record;
                require(!conflictTiles.containsKey(c.episode.conflictId)||conflictTiles.get(c.episode.conflictId).equals(c.episode.tileId),
                    "Conflicting tile for journal conflict="+c.episode.conflictId);
                for(Target target:c.targets.values()) {
                    Component existing=components.get(target.id);
                    require(existing==null||existing==target.component,"Conflicting logical target component="+target.id);
                    UUID current=currentAlias.get(target.id);
                    require(current==null||current.equals(target.physicalId),"Context changes physical lineage without LINEAGE="+target.id);
                    require(!contextTerminal.contains(target.id)||target.terminal,"Context erases terminal evidence="+target.id);
                    require(!terminals.containsKey(target.id)||target.terminal,"Context erases journal terminal evidence="+target.id);
                    for(UUID alias:target.aliases) require(!aliases.containsKey(alias)||aliases.get(alias).equals(target.id),"Alias belongs to another target="+alias);
                }
                return;
            }
            Binding b=binding(record); require(journalId.equals(b.journalId),"Journal identity mismatch expected="+journalId+" actual="+b.journalId);
            Entry context=records.get(b.contextSequence);
            require(context!=null && context.record instanceof EpisodeContext && context.hash.equals(b.contextHash),"Invalid context sequence/hash reference");
            EpisodeContext c=(EpisodeContext)context.record;
            require(c.episode.conflictId.equals(b.conflictId)&&c.episode.episodeId.equals(b.episodeId),"Context conflict/episode mismatch");
            Target target=c.targets.get(b.targetId);
            require(target!=null&&target.component==b.component,"Unproven component target");
            require(!contextTerminal.contains(b.targetId)&&!terminals.containsKey(b.targetId),"Target already terminal");
            if(record instanceof Prepare) {
                Prepare p=(Prepare)record; Grant grant=c.episode.grants.get(b.component+":"+b.targetId);
                require(grant!=null&&grant.participantId.equals(p.participantId)&&grant.amount==p.recoveryAmount,"Prepare does not match exact pending grant");
                require(c.intentFingerprint().equals(p.intentFingerprint),"Resolution intent fingerprint mismatch");
                alias(b,p.physicalAlias); require(p.lineageSequence==lineageSequence(b.targetId),"Stale lineage reference");
                lineageHealth(b.targetId, p.sourceRevision, p.sourceTransitionId, false);
                require(!recoveryTokens.contains(p.recoveryTransitionId),"Recovery transition token reused");
                fence(b,p.inheritedFence,p.sourceRevision);
                Entry previous=latestPrepare.get(b.targetId);
                if(previous!=null) {
                    Prepare prior=(Prepare)previous.record;
                    require(p.sourceRevision!=prior.recoveryRevision || p.sourceTransitionId.equals(prior.recoveryTransitionId)
                        && p.sourceHp==prior.targetHp,"Contradictory source state at reserved recovery revision");
                }
            } else if(record instanceof Lineage) {
                Lineage l=(Lineage)record; alias(b,l.oldAlias);
                require(!aliases.containsKey(l.newAlias),"Replacement alias is already known");
                require(l.previousLineageSequence==lineageSequence(b.targetId),"Stale lineage predecessor");
                lineageHealth(b.targetId, l.healthRevision, l.healthTransitionId, false);
                fence(b,l.fence,l.healthRevision);
                Entry previous=latestPrepare.get(b.targetId);
                if(previous!=null) {
                    Prepare prior=(Prepare)previous.record;
                    require(l.healthRevision!=prior.recoveryRevision || l.healthTransitionId.equals(prior.recoveryTransitionId),
                        "Lineage changes an existing recovery transition identity");
                }
            } else {
                Terminal t=(Terminal)record; alias(b,t.physicalAlias); fence(b,t.fence,t.healthRevision);
                lineageHealth(b.targetId, t.healthRevision, t.transitionId, true);
                Entry previous=latestPrepare.get(b.targetId);
                require(previous==null || t.healthRevision>((Prepare)previous.record).recoveryRevision,
                    "Terminal health transition must follow prepared live health");
            }
        }
        private void alias(Binding b,UUID physical) { require(physical.equals(currentAlias.get(b.targetId)),"Obsolete/unverified physical alias="+physical); }
        private long lineageSequence(UUID target) { Entry entry=lineages.get(target); return entry==null?0:entry.sequence; }
        private void lineageHealth(UUID target, long revision, UUID transition, boolean terminal) {
            Entry entry=lineages.get(target); if(entry==null)return;
            Lineage prior=(Lineage)entry.record;
            require(revision>prior.healthRevision || !terminal && revision==prior.healthRevision
                && transition.equals(prior.healthTransitionId), "Health revision/transition regresses verified LINEAGE");
        }
        private void fence(Binding b,Fence fence,long revision) {
            Entry latest=latestPrepare.get(b.targetId);
            if(latest==null) { require(fence==null,"Fence references unknown target preparation"); return; }
            Prepare previous=(Prepare)latest.record;
            require(fence!=null&&journalId.equals(fence.journalId)&&fence.prepareSequence==latest.sequence
                &&fence.transitionId.equals(previous.recoveryTransitionId)&&revision>=previous.recoveryRevision,
                "Missing/incompatible inherited recovery fence");
        }
        void add(Entry entry) {
            KOMERecoveryJournalRecord record=entry.record; records.put(entry.sequence,entry); identities.put(record.identity(),entry);
            if(record instanceof EpisodeContext) {
                EpisodeContext c=(EpisodeContext)record; contexts.put(c.episode.episodeId,entry); conflictTiles.put(c.episode.conflictId,c.episode.tileId);
                for(Target t:c.targets.values()) {
                    components.put(t.id,t.component); currentAlias.put(t.id,t.physicalId);
                    for(UUID alias:t.aliases)aliases.put(alias,t.id); if(t.terminal)contextTerminal.add(t.id);
                }
            } else if(record instanceof Prepare) {
                Prepare p=(Prepare)record; preparations.put(p.grantIdentity(),entry); latestPrepare.put(p.binding.targetId,entry); recoveryTokens.add(p.recoveryTransitionId);
            } else if(record instanceof Lineage) {
                Lineage l=(Lineage)record; lineages.put(l.binding.targetId,entry); currentAlias.put(l.binding.targetId,l.newAlias); aliases.put(l.newAlias,l.binding.targetId);
            } else { Terminal t=(Terminal)record; terminals.put(t.binding.targetId,entry); }
        }
        static Binding binding(KOMERecoveryJournalRecord record) {
            if(record instanceof Prepare)return ((Prepare)record).binding;
            if(record instanceof Terminal)return ((Terminal)record).binding;
            return ((Lineage)record).binding;
        }
    }
}
