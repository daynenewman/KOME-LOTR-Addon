package kome.common.data;

import java.io.*;
import java.nio.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;
import static kome.common.data.KOMERecoveryJournalRecord.*;
import static kome.common.data.KOMERecoveryJournalCodec.*;
import static kome.common.data.KOMERecoveryJournalTestSupport.*;

public class KOMERecoveryJournalTest {
    @Test public void allRecordTypesRoundTripAndIndexesRetainExactContext() throws Exception {
        MemoryIO io=empty(); KOMERecoveryJournal wal=open(io);
        KOMERecoveryJournal.Acknowledgment c=append(wal,context());
        KOMERecoveryJournal.Acknowledgment p=append(wal,prepare(c,Component.RIDER));
        Fence fence=new Fence(JOURNAL,p.sequence,((Prepare)wal.view().records.get(p.sequence).record).recoveryTransitionId);
        UUID replacement=new UUID(9,1);
        append(wal,new Lineage(binding(c,Component.RIDER),RIDER_ALIAS,replacement,new UUID(9,2),0,6,new UUID(9,3),fence));
        append(wal,new Terminal(binding(c,Component.RIDER),replacement,7,new UUID(9,4),Disposition.DEAD,30,"confirmed death",fence));
        KOMERecoveryJournalView scanned=KOMERecoveryJournal.scan(new MemoryIO(io.forced),"roundtrip",JOURNAL,WORLD);
        assertEquals(4,scanned.records.size()); assertEquals(1,scanned.contexts.size()); assertEquals(1,scanned.preparations.size());
        assertEquals(3,scanned.lineages.get(RIDER).sequence); assertEquals(4,scanned.terminals.get(RIDER).sequence);
        EpisodeContext restored=(EpisodeContext)scanned.contexts.get("CF1:E1").record;
        assertEquals(Disposition.FORMAL_RETREAT,restored.episode.participants.get(RIDER).disposition);
        assertEquals(2,restored.episode.grants.size()); assertEquals(2,restored.nextConflictSequence);
        assertEquals(21L,restored.episode.intent.closedAt.longValue()); assertEquals(context().intentFingerprint(),restored.intentFingerprint());
        assertEquals(wal.view().lastHash,scanned.lastHash);
        assertEquals(HEADER_SIZE,scanned.records.get(1L).offset);
    }
    @Test public void acknowledgmentFollowsForceAndShortWritesAreRetried() throws Exception {
        MemoryIO io=empty(); io.maximumWrite=3; KOMERecoveryJournal wal=open(io); int before=io.forces;
        KOMERecoveryJournal.Acknowledgment ack=append(wal,context());
        assertEquals(1,ack.sequence); assertEquals(before+1,io.forces); assertTrue(io.writes>10);
        assertEquals("force",io.calls.get(io.calls.size()-1)); assertArrayEquals(io.bytes,io.forced);
    }
    @Test public void reopenForcesCompletePrefixThenAppendsNextSequence() throws Exception {
        MemoryIO io=empty(); KOMERecoveryJournal first=open(io); KOMERecoveryJournal.Acknowledgment c=append(first,context()); first.close();
        MemoryIO restarted=new MemoryIO(io.bytes); KOMERecoveryJournal second=open(restarted); assertEquals(1,restarted.forces);
        assertEquals(2,append(second,prepare(c,Component.RIDER)).sequence);
        assertEquals(2,KOMERecoveryJournal.scan(restarted,"reopen",JOURNAL,WORLD).lastSequence);
    }
    @Test public void identicalAppendRetryReturnsOriginalAcknowledgmentWithoutWriting() throws Exception {
        MemoryIO io=empty(); KOMERecoveryJournal wal=open(io); KOMERecoveryJournal.Acknowledgment c=append(wal,context());
        Prepare prepare=prepare(c,Component.RIDER); KOMERecoveryJournal.Acknowledgment first=append(wal,prepare); int forces=io.forces,writes=io.writes;
        assertEquals(first.sequence,append(wal,prepare).sequence); assertEquals(forces,io.forces); assertEquals(writes,io.writes);
        assertEquals(2,wal.view().lastSequence);
    }
    @Test public void staleEndpointRejectedBeforeAnyWrite() throws Exception {
        MemoryIO io=empty(); KOMERecoveryJournal wal=open(io); String initial=wal.view().lastHash; append(wal,context()); int writes=io.writes;
        rejects(()->wal.append(context(),0,initial)); assertEquals(writes,io.writes); assertEquals(KOMERecoveryJournal.State.READY,wal.state());
    }
    @Test public void failedWritePoisonsAndCannotAllocateAnotherSequence() throws Exception {
        MemoryIO io=empty(); KOMERecoveryJournal wal=open(io); io.failWriteAt=HEADER_SIZE+27;
        rejects(()->append(wal,context())); assertEquals(KOMERecoveryJournal.State.POISONED,wal.state()); assertEquals(0,wal.view().lastSequence);
        int writes=io.writes; rejects(()->append(wal,context())); assertEquals(writes,io.writes);
        assertTrue(wal.diagnostic().contains("offset=68")); assertTrue(wal.diagnostic().contains("sequence=1"));
        assertEquals(HEADER_SIZE,io.forced.length);
    }
    @Test public void failedForceLeavesUncertainCompleteFrameAndRetryReusesItAfterRescan() throws Exception {
        MemoryIO io=empty(); KOMERecoveryJournal wal=open(io); io.failForceAt=io.forces+1;
        rejects(()->append(wal,context())); assertEquals(KOMERecoveryJournal.State.POISONED,wal.state()); assertEquals(0,wal.view().lastSequence);
        rejects(()->append(wal,context()));
        MemoryIO survived=new MemoryIO(io.bytes); KOMERecoveryJournal restarted=open(survived);
        assertEquals(KOMERecoveryJournal.State.READY,restarted.state()); assertEquals(1,survived.forces);
        assertEquals(1,append(restarted,context()).sequence); assertEquals(0,survived.writes);
    }
    @Test public void openForceFailureDoesNotIssueAcknowledgments() throws Exception {
        MemoryIO io=empty(); io.failForceAt=1; KOMERecoveryJournal wal=open(io);
        assertEquals(KOMERecoveryJournal.State.POISONED,wal.state()); rejects(()->append(wal,context()));
    }
    @Test public void zeroProgressWriteFailsRatherThanHanging() throws Exception {
        MemoryIO io=empty(); KOMERecoveryJournal wal=open(io); io.maximumWrite=0;
        rejects(()->append(wal,context())); assertEquals(KOMERecoveryJournal.State.POISONED,wal.state()); assertEquals(17,io.writes);
    }
    @Test public void everyFinalFrameTruncationRetainsOnlyValidatedPrefix() throws Exception {
        MemoryIO complete=empty(); KOMERecoveryJournal wal=open(complete); KOMERecoveryJournal.Acknowledgment c=append(wal,context());
        int prefix=complete.bytes.length; append(wal,prepare(c,Component.MOUNT)); byte[] all=complete.bytes;
        for(int end=prefix;end<all.length;end++) {
            MemoryIO cut=new MemoryIO(Arrays.copyOf(all,end)); KOMERecoveryJournal reopened=open(cut);
            assertEquals("byte "+end,1,reopened.view().lastSequence);
            assertEquals(prefix,reopened.view().validLength);
            if(end==prefix)assertEquals(KOMERecoveryJournal.State.READY,reopened.state());
            else {
                assertEquals("byte "+end,KOMERecoveryJournal.State.BLOCKED,reopened.state());
                rejects(()->append(reopened,prepare(c,Component.MOUNT)));
                reopened.repairTail(); assertEquals(KOMERecoveryJournal.State.READY,reopened.state());
                assertEquals(prefix,cut.bytes.length); assertArrayEquals(cut.bytes,cut.forced);
                assertEquals(2,append(reopened,prepare(c,Component.MOUNT)).sequence);
            }
            reopened.close();
        }
    }
    @Test public void everyInitialFrameTruncationRetainsValidFileHeader() throws Exception {
        MemoryIO complete=empty(); KOMERecoveryJournal wal=open(complete); append(wal,context());
        for(int end=HEADER_SIZE+1;end<complete.bytes.length;end++) {
            MemoryIO cut=new MemoryIO(Arrays.copyOf(complete.bytes,end)); KOMERecoveryJournal reopened=open(cut);
            assertEquals("byte "+end,KOMERecoveryJournal.State.BLOCKED,reopened.state()); assertEquals(0,reopened.view().lastSequence);
            reopened.repairTail(); assertEquals(HEADER_SIZE,cut.bytes.length); reopened.close();
        }
    }
    @Test public void corruptCompleteFinalFrameIsNeverRepairableTail() throws Exception {
        MemoryIO io=empty(); KOMERecoveryJournal wal=open(io); append(wal,context()); io.bytes[io.bytes.length-21]^=1;
        KOMERecoveryJournal damaged=open(new MemoryIO(io.bytes)); assertEquals(KOMERecoveryJournal.State.CORRUPT,damaged.state());
        assertTrue(damaged.diagnostic().contains("expected=")); assertTrue(damaged.diagnostic().contains("actual=")); rejects(damaged::repairTail);
    }
    @Test public void midFileCorruptionDeletionAndReorderingFailClosed() throws Exception {
        MemoryIO io=empty(); KOMERecoveryJournal wal=open(io); KOMERecoveryJournal.Acknowledgment c=append(wal,context());
        append(wal,prepare(c,Component.RIDER)); append(wal,prepare(c,Component.MOUNT));
        byte[] original=io.bytes; byte[] corrupted=original.clone(); corrupted[HEADER_SIZE+FRAME_HEADER+10]^=1;
        assertCorrupt(corrupted);
        int second=(int)wal.view().records.get(2L).offset, third=(int)wal.view().records.get(3L).offset;
        ByteArrayOutputStream deleted=new ByteArrayOutputStream(); deleted.write(original,0,second); deleted.write(original,third,original.length-third);
        assertCorrupt(deleted.toByteArray());
        ByteArrayOutputStream reordered=new ByteArrayOutputStream(); reordered.write(original,0,second); reordered.write(original,third,original.length-third);
        reordered.write(original,second,third-second); assertCorrupt(reordered.toByteArray());
    }
    @Test public void malformedPartialHeaderCannotBeDiscardedAsCrashTail() throws Exception {
        MemoryIO io=empty(); byte[] malformed=Arrays.copyOf(io.bytes,HEADER_SIZE+1); malformed[HEADER_SIZE]=0;
        assertCorrupt(malformed);
    }
    @Test public void tailTruncationForceFailurePoisonsWriter() throws Exception {
        MemoryIO io=empty(); KOMERecoveryJournal wal=open(io); append(wal,context());
        MemoryIO cut=new MemoryIO(Arrays.copyOf(io.bytes,io.bytes.length-1)); KOMERecoveryJournal restarted=open(cut); cut.failForceAt=1;
        rejects(restarted::repairTail); assertEquals(KOMERecoveryJournal.State.POISONED,restarted.state()); rejects(()->append(restarted,context()));
    }
    @Test public void exclusiveLockFailureIsBlocked() throws Exception {
        MemoryIO io=empty(); io.denyLock=true; KOMERecoveryJournal wal=open(io);
        assertEquals(KOMERecoveryJournal.State.BLOCKED,wal.state()); assertNull(wal.view()); assertEquals(0,io.reads); rejects(()->wal.append(context(),0,ZERO_HASH));
    }
    @Test public void snapshotsAreImmutableAndLaterAppendsDoNotChangeThem() throws Exception {
        MemoryIO io=empty(); KOMERecoveryJournal wal=open(io); KOMERecoveryJournalView before=wal.view(); append(wal,context());
        assertTrue(before.records.isEmpty()); assertEquals(0,before.lastSequence);
        try { wal.view().records.clear(); fail(); } catch(UnsupportedOperationException expected) { }
        try { context().targets.clear(); fail(); } catch(UnsupportedOperationException expected) { }
    }
    @Test public void wrongThreadCannotAppendRepairOrClose() throws Exception {
        KOMERecoveryJournal wal=open(empty()); List<Throwable> errors=new ArrayList<Throwable>();
        Thread wrong=new Thread(()-> { try { append(wal,context()); } catch(Throwable t) { errors.add(t); }
            try { wal.close(); } catch(Throwable t) { errors.add(t); } }); wrong.start(); wrong.join();
        assertEquals(2,errors.size()); for(Throwable t:errors)assertTrue(t instanceof IllegalStateException);
        assertEquals(0,wal.view().lastSequence); wal.close(); wal.close(); assertEquals(KOMERecoveryJournal.State.CLOSED,wal.state());
    }
    @Test public void completeInvalidPayloadWithIncompleteTrailerIsCorruptNotRepairable() throws Exception {
        byte[] header=header(JOURNAL,WORLD,false), payload=encode(context()); payload[4]=(byte)255;
        byte[] bad=frame(Type.EPISODE_CONTEXT,1,hex(sha(header)),payload);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); bytes.write(header); bytes.write(bad,0,FRAME_HEADER+payload.length);
        assertCorrupt(bytes.toByteArray());
    }
    @Test public void damagedPartialChecksumAndOversizedPartialLengthAreCorrupt() throws Exception {
        MemoryIO io=empty(); KOMERecoveryJournal wal=open(io); append(wal,context());
        byte[] partial=Arrays.copyOf(io.bytes,io.bytes.length-FRAME_TRAILER+1); partial[partial.length-1]^=1; assertCorrupt(partial);
        byte[] oversized=Arrays.copyOf(io.bytes,HEADER_SIZE+17); oversized[HEADER_SIZE+16]=(byte)127; assertCorrupt(oversized);
    }
    @Test public void changedTailCannotBeTruncatedUsingStaleScan() throws Exception {
        MemoryIO io=empty(); KOMERecoveryJournal wal=open(io); append(wal,context());
        MemoryIO cut=new MemoryIO(Arrays.copyOf(io.bytes,io.bytes.length-2)); KOMERecoveryJournal restarted=open(cut);
        cut.bytes=Arrays.copyOf(cut.bytes,cut.bytes.length-1); byte[] evidence=cut.bytes.clone();
        rejects(restarted::repairTail); assertArrayEquals(evidence,cut.bytes); assertEquals(KOMERecoveryJournal.State.POISONED,restarted.state());
    }
    @Test public void truncationExceptionPoisonsAndPreservesSuffix() throws Exception {
        MemoryIO io=empty(); KOMERecoveryJournal wal=open(io); append(wal,context());
        MemoryIO cut=new MemoryIO(Arrays.copyOf(io.bytes,io.bytes.length-1)); KOMERecoveryJournal restarted=open(cut);
        cut.failTruncate=true; byte[] evidence=cut.bytes.clone(); rejects(restarted::repairTail);
        assertArrayEquals(evidence,cut.bytes); assertEquals(KOMERecoveryJournal.State.POISONED,restarted.state());
    }
    @Test public void unknownFrameVersionTypeAndBrokenHashChainFailClosed() throws Exception {
        MemoryIO io=empty(); KOMERecoveryJournal wal=open(io); append(wal,context());
        for(int offset:new int[]{HEADER_SIZE+5,HEADER_SIZE+7,HEADER_SIZE+20}) {
            byte[] bytes=io.bytes.clone(); bytes[offset]^=127; assertCorrupt(bytes);
        }
    }
    @Test public void reentrantForceCallbackCannotAppendOrClose() throws Exception {
        MemoryIO io=empty(); KOMERecoveryJournal wal=open(io);
        io.onForce=()-> { rejects(()->append(wal,context())); rejects(wal::close); };
        assertEquals(1,append(wal,context()).sequence); assertEquals(1,wal.view().lastSequence);
        assertEquals(KOMERecoveryJournal.State.READY,wal.state());
    }
    @Test public void terminalContextEvidenceIsIncludedInTargetIndex() throws Exception {
        EpisodeContext original=context(); Map<UUID,Target> targets=new TreeMap<UUID,Target>(original.targets);
        targets.put(MOUNT,new Target(MOUNT,Component.MOUNT,MOUNT_ALIAS,Collections.singleton(MOUNT_ALIAS),true));
        Episode e=original.episode; Map<String,Grant> grants=new TreeMap<String,Grant>(e.grants); grants.remove("MOUNT:"+MOUNT);
        Episode deadMount=new Episode(e.conflictId,e.tileId,e.episodeId,e.startedAt,e.endedAt,e.coverage,e.actualHostileCombatObserved,e.participants,e.intent,grants);
        EpisodeContext c=new EpisodeContext(deadMount,targets,2,8,1,5);
        MemoryIO io=empty(); KOMERecoveryJournal wal=open(io); append(wal,c);
        assertTrue(wal.view().terminalTargets.contains(MOUNT)); assertFalse(wal.view().terminalTargets.contains(RIDER));
        KOMERecoveryJournalView scanned=KOMERecoveryJournal.scan(io,"terminal-context",JOURNAL,WORLD);
        assertTrue(scanned.terminalTargets.contains(MOUNT)); assertEquals(MOUNT_ALIAS,scanned.currentPhysicalAliases.get(MOUNT));
        try { scanned.terminalTargets.clear(); fail(); } catch(UnsupportedOperationException expected) { }
    }
    private static void assertCorrupt(byte[] bytes) {
        KOMERecoveryJournal wal=open(new MemoryIO(bytes)); assertEquals(wal.diagnostic(),KOMERecoveryJournal.State.CORRUPT,wal.state());
    }
}
