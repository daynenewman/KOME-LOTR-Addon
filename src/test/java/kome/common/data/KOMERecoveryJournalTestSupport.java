package kome.common.data;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;
import static kome.common.data.KOMERecoveryJournalRecord.*;
import static kome.common.data.KOMERecoveryJournalCodec.*;
import static org.junit.Assert.*;

final class KOMERecoveryJournalTestSupport {
    static final UUID JOURNAL=new UUID(1,1), RIDER=new UUID(2,1), MOUNT=new UUID(2,2), RIDER_ALIAS=new UUID(3,1), MOUNT_ALIAS=new UUID(3,2);
    static final String WORLD=hex(sha(new byte[]{1,2,3}));
    static EpisodeContext context() { return context("CF1",1,5); }
    static EpisodeContext context(String conflict,long episodeNumber,float amount) {
        String episode=conflict+":E"+episodeNumber;
        Map<UUID,Target> targets=new TreeMap<UUID,Target>();
        targets.put(RIDER,new Target(RIDER,Component.RIDER,RIDER_ALIAS,Collections.singleton(RIDER_ALIAS),false));
        targets.put(MOUNT,new Target(MOUNT,Component.MOUNT,MOUNT_ALIAS,Collections.singleton(MOUNT_ALIAS),false));
        Participant participant=new Participant(RIDER,"C1","gondor",11,Disposition.FORMAL_RETREAT,19L,"FORMAL_RETREAT",Collections.singleton(MOUNT));
        Map<String,Grant> grants=new TreeMap<String,Grant>();
        for(Component component:Component.values()) {
            Grant g=new Grant(RIDER,component==Component.RIDER?RIDER:MOUNT,component,amount,GrantState.PENDING,null,""); grants.put(g.key(),g);
        }
        Intent intent=new Intent(conflict,episode,7,KOMEConflictRecord.State.ORDINARY,ResolutionKind.CONQUEST,"mordor","gondor",amount,Stage.RECOVERY_PENDING,21L);
        Episode e=new Episode(conflict,"T101",episode,10,20L,Coverage.COMPLETE,true,Collections.singletonMap(RIDER,participant),intent,grants);
        return new EpisodeContext(e,targets,KOMEConflictIdAllocator.sequenceOf(conflict)+1,8,episodeNumber,amount);
    }
    static Binding binding(KOMERecoveryJournal.Acknowledgment context,Component component) {
        return new Binding(context.journalId,context.sequence,context.hash,"CF1","CF1:E1",component==Component.RIDER?RIDER:MOUNT,component);
    }
    static Prepare prepare(KOMERecoveryJournal.Acknowledgment context,Component component) {
        return new Prepare(binding(context,component),RIDER,component==Component.RIDER?RIDER_ALIAS:MOUNT_ALIAS,0,4,new UUID(4,1),
            3.25F,null,5,new UUID(5,component.ordinal()+1),8.25F,5,20,KOMERecoveryJournalTestSupport.context().intentFingerprint());
    }
    static KOMERecoveryJournal.Acknowledgment append(KOMERecoveryJournal journal,KOMERecoveryJournalRecord record) throws IOException {
        return journal.append(record,journal.view().lastSequence,journal.view().lastHash);
    }
    static MemoryIO empty() throws IOException { return new MemoryIO(header(JOURNAL,WORLD,false)); }
    static KOMERecoveryJournal open(MemoryIO io) { return KOMERecoveryJournal.open(io,"test.wal",JOURNAL,WORLD); }
    interface Action { void run() throws Exception; }
    static void rejects(Action action) {
        try { action.run(); fail("Expected fail-closed rejection"); }
        catch(IOException | IllegalArgumentException | IllegalStateException expected) { }
        catch(Exception unexpected) { throw new AssertionError(unexpected); }
    }
    static final class MemoryIO implements KOMERecoveryJournalIO {
        byte[] bytes, forced;
        int maximumWrite=Integer.MAX_VALUE, forces, writes, reads;
        long failWriteAt=Long.MAX_VALUE;
        int failForceAt=-1;
        Runnable onForce;
        boolean locked, closed, denyLock, failTruncate, failRead;
        final List<String> calls=new ArrayList<String>();
        MemoryIO(byte[] bytes) { this.bytes=bytes.clone(); forced=bytes.clone(); }
        public long size() { return bytes.length; }
        public int read(ByteBuffer out,long offset) throws IOException {
            reads++; if(failRead)throw new IOException("injected read failure");
            if(offset>=bytes.length)return -1; int n=Math.min(out.remaining(),bytes.length-(int)offset); out.put(bytes,(int)offset,n); return n;
        }
        public int write(ByteBuffer input,long offset) throws IOException {
            calls.add("write"); writes++;
            if(offset>=failWriteAt)throw new IOException("injected write failure");
            int n=(int)Math.min(Math.min(input.remaining(),maximumWrite),failWriteAt-offset);
            int end=(int)offset+n; if(end>bytes.length)bytes=Arrays.copyOf(bytes,end); input.get(bytes,(int)offset,n); return n;
        }
        public void force() throws IOException {
            calls.add("force"); forces++; if(onForce!=null)onForce.run();
            if(forces==failForceAt)throw new IOException("injected force failure"); forced=bytes.clone();
        }
        public void truncate(long length) throws IOException {
            calls.add("truncate"); if(failTruncate)throw new IOException("injected truncation failure"); bytes=Arrays.copyOf(bytes,(int)length);
        }
        public boolean tryLock() { if(denyLock||locked)return false; locked=true; return true; }
        public void close() { closed=true; locked=false; }
    }
}
