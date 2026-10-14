package kome.common.data;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import static kome.common.data.KOMERecoveryJournalCodec.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.require;
import static kome.common.data.KOMERecoveryJournalRecord.*;
import static kome.common.data.KOMERecoveryJournalView.*;

/** Locked single-writer WAL. A successful append acknowledgment is returned only after force(true). */
public final class KOMERecoveryJournal implements Closeable {
    public enum State { READY, UNPROVISIONED, BLOCKED, CORRUPT, POISONED, CLOSED }
    public static final class Acknowledgment {
        public final UUID journalId;
        public final long sequence;
        public final String hash;
        private Acknowledgment(UUID id,Entry entry) { journalId=id; sequence=entry.sequence; hash=entry.hash; }
    }
    public static final class Failure extends IOException {
        public final String path;
        public final long offset, sequence;
        Failure(String path,long offset,long sequence,String detail,Throwable cause) {
            super("Recovery WAL path="+path+" offset="+offset+" sequence="+sequence+": "+detail,cause);
            this.path=path; this.offset=offset; this.sequence=sequence;
        }
    }
    private final KOMERecoveryJournalIO io;
    private final Thread owner;
    private final String path, worldHash;
    private final UUID expectedId;
    private Index index;
    private volatile KOMERecoveryJournalView view;
    private volatile State state=State.BLOCKED;
    private volatile String diagnostic="Not opened";
    private boolean repairableTail, mutating;
    private KOMERecoveryJournal(KOMERecoveryJournalIO io,String path,UUID expectedId,String worldHash) {
        this.io=io; this.path=path; this.expectedId=expectedId; this.worldHash=worldHash; owner=Thread.currentThread();
    }
    static KOMERecoveryJournal open(KOMERecoveryJournalIO io,String path,UUID expectedId,String worldHash) {
        KOMERecoveryJournal journal=new KOMERecoveryJournal(io,path,expectedId,worldHash);
        try {
            if(!io.tryLock()) { journal.diagnostic="Exclusive journal lock unavailable: "+path; return journal; }
            Scan scanned=scanInternal(io,path,expectedId,worldHash); journal.index=scanned.index; journal.view=scanned.view;
            if(scanned.view.hasIncompleteTail()) {
                journal.repairableTail=true;
                journal.diagnostic="Incomplete final append path="+path+" offset="+scanned.view.validLength
                    +" sequence="+(scanned.view.lastSequence+1)+"; explicit tail repair required";
            } else {
                // A complete frame may have survived an uncertain force. Establish durability again before acknowledgments.
                io.force(); journal.state=State.READY; journal.diagnostic="Validated and forced journal: "+path;
            }
        } catch(Failure invalid) { journal.state=State.CORRUPT; journal.diagnostic=invalid.getMessage(); }
        catch(IOException failure) { journal.state=State.POISONED; journal.diagnostic="Open/force failed: "+path+": "+failure; }
        return journal;
    }
    public State state() { return state; }
    public String diagnostic() { return diagnostic; }
    public KOMERecoveryJournalView view() { return view; }
    public Acknowledgment append(KOMERecoveryJournalRecord record,long expectedSequence,String expectedHash) throws IOException {
        enterMutation();
        try { return appendUnderGuard(record,expectedSequence,expectedHash); }
        finally { mutating=false; }
    }
    private Acknowledgment appendUnderGuard(KOMERecoveryJournalRecord record,long expectedSequence,String expectedHash) throws IOException {
        owner(); require(state==State.READY,"Journal is not ready: "+diagnostic);
        require(expectedSequence==view.lastSequence&&view.lastHash.equals(expectedHash),"Stale journal endpoint");
        byte[] payload=encode(Objects.requireNonNull(record));
        KOMERecoveryJournalRecord detached=decode(record.type,payload);
        Entry old=index.duplicate(detached);
        if(old!=null) return new Acknowledgment(view.journalId,old);
        index.validate(detached);
        require(view.lastSequence<Long.MAX_VALUE-1,"Journal sequence exhausted");
        long sequence=view.lastSequence+1, offset=view.validLength;
        byte[] frame=frame(detached.type,sequence,view.lastHash,payload);
        require(offset<=Long.MAX_VALUE-frame.length,"Journal length overflow");
        try {
            if(io.size()!=offset)throw new IOException("File endpoint changed outside journal owner");
            writeFully(io,frame,offset); io.force();
            Entry entry=new Entry(sequence,offset,offset+frame.length,recordHash(frame),detached);
            index.add(entry); view=new KOMERecoveryJournalView(index,entry.endOffset,entry.endOffset,entry.hash);
            return new Acknowledgment(view.journalId,entry);
        } catch(IOException | RuntimeException failure) {
            state=State.POISONED;
            Failure problem=new Failure(path,offset,sequence,describe(detached)+" append/force uncertain: "+failure,failure);
            diagnostic=problem.getMessage(); throw problem;
        }
    }
    /** Only the validated incomplete EOF suffix is eligible. Full-frame checksum failures never reach this method. */
    public void repairTail() throws IOException {
        enterMutation();
        try { repairTailUnderGuard(); }
        finally { mutating=false; }
    }
    private void repairTailUnderGuard() throws IOException {
        owner(); require(state==State.BLOCKED&&repairableTail,"No validated repairable crash tail");
        try {
            Scan checked=scanInternal(io,path,expectedId,worldHash);
            require(checked.view.validLength==view.validLength&&checked.view.fileLength==view.fileLength
                &&checked.view.lastHash.equals(view.lastHash),"Journal changed before tail repair");
            io.truncate(view.validLength); io.force();
            Scan repaired=scanInternal(io,path,expectedId,worldHash);
            require(!repaired.view.hasIncompleteTail(),"Tail repair did not remove suffix");
            index=repaired.index; view=repaired.view; repairableTail=false; state=State.READY;
            diagnostic="Forced incomplete-tail truncation at "+view.validLength+": "+path;
        } catch(IOException | RuntimeException failure) {
            state=State.POISONED; diagnostic="Tail repair uncertain: "+path+": "+failure; throw new IOException(diagnostic,failure);
        }
    }
    /** Read-only structural validation, NOT a durability acknowledgment. Does not force or repair.
     * Caller must ensure exclusive ownership or supply an immutable I/O snapshot. */
    public static KOMERecoveryJournalView scan(KOMERecoveryJournalIO io,String path,UUID expectedId,String worldHash) throws IOException {
        return scanInternal(io,path,expectedId,worldHash).view;
    }
    private static final class Scan {
        final Index index; final KOMERecoveryJournalView view;
        Scan(Index index,KOMERecoveryJournalView view) { this.index=index; this.view=view; }
    }
    private static Scan scanInternal(KOMERecoveryJournalIO io,String path,UUID expectedId,String worldHash) throws IOException {
        long offset=0, sequence=0; KOMERecoveryJournalRecord decoded=null;
        try {
            long size=io.size(); require(size>=HEADER_SIZE,"Incomplete journal header");
            byte[] header=readFully(io,HEADER_SIZE,0); UUID id=readHeader(header,worldHash,false);
            require(id.equals(expectedId),"Journal identity mismatch expected="+expectedId+" actual="+id);
            String previous=hex(sha(header)); Index index=new Index(id); offset=HEADER_SIZE;
            while(offset<size) {
                sequence++;
                // Sequence is tracked independently below; never allocate using disk-provided lengths before validating them.
                require(sequence>0&&sequence<Long.MAX_VALUE,"Journal sequence exhausted"); decoded=null;
                long remaining=size-offset;
                if(remaining<FRAME_HEADER) {
                    partialHeader(readFully(io,(int)remaining,offset),sequence,previous); break;
                }
                byte[] prefix=readFully(io,FRAME_HEADER,offset); Type type=frameType(prefix,sequence,previous);
                int payloadLength=payloadLength(prefix), length=FRAME_HEADER+payloadLength+FRAME_TRAILER;
                if(remaining<length) {
                    byte[] tail=readFully(io,(int)remaining,offset);
                    partialBody(tail,type,sequence,previous);
                    if(remaining>=FRAME_HEADER+payloadLength) {
                        decoded=decode(type,Arrays.copyOfRange(tail,FRAME_HEADER,FRAME_HEADER+payloadLength));
                        index.validate(decoded);
                    }
                    break;
                }
                byte[] bytes=readFully(io,length,offset); String recordHash=validateFrame(bytes,type,sequence,previous);
                decoded=decode(type,Arrays.copyOfRange(bytes,FRAME_HEADER,FRAME_HEADER+payloadLength)); index.validate(decoded);
                Entry entry=new Entry(sequence,offset,offset+length,recordHash,decoded); index.add(entry);
                previous=recordHash; offset+=length;
            }
            return new Scan(index,new KOMERecoveryJournalView(index,offset,size,previous));
        } catch(IOException | RuntimeException failure) {
            throw new Failure(path,offset,sequence,(decoded==null?"":describe(decoded)+" ")+failure.getMessage(),failure);
        }
    }
    static byte[] readFully(KOMERecoveryJournalIO io,int length,long offset) throws IOException {
        require(length>=0&&length<=MAX_PAYLOAD+FRAME_HEADER+FRAME_TRAILER,"Read allocation exceeds limit");
        ByteBuffer bytes=ByteBuffer.allocate(length); int stalls=0;
        while(bytes.hasRemaining()) {
            int n=io.read(bytes,offset+bytes.position());
            if(n<0)throw new EOFException("File shortened during scan");
            if(n==0&&++stalls>16)throw new IOException("Read made no progress"); if(n>0)stalls=0;
        } return bytes.array();
    }
    static void writeFully(KOMERecoveryJournalIO io,byte[] bytes,long offset) throws IOException {
        ByteBuffer buffer=ByteBuffer.wrap(bytes); int stalls=0;
        while(buffer.hasRemaining()) {
            int n=io.write(buffer,offset+buffer.position());
            if(n<0)throw new IOException("Negative write result");
            if(n==0&&++stalls>16)throw new IOException("Write made no progress"); if(n>0)stalls=0;
        }
    }
    private static String describe(KOMERecoveryJournalRecord r) {
        if(r instanceof EpisodeContext) { EpisodeContext c=(EpisodeContext)r; return "context="+c.episode.episodeId; }
        Binding b=Index.binding(r); return "conflict="+b.conflictId+" episode="+b.episodeId+" target="+b.targetId+" component="+b.component;
    }
    private void enterMutation() { owner(); require(!mutating,"Reentrant journal mutation prohibited"); mutating=true; }
    private void owner() { if(Thread.currentThread()!=owner)throw new IllegalStateException("Recovery journal mutation requires the owning server thread"); }
    public void close() throws IOException {
        if(Thread.currentThread()!=owner && owner.isAlive()) throw new IllegalStateException("Cannot close another live server thread's journal");
        require(!mutating,"Cannot close during journal mutation"); if(state==State.CLOSED)return;
        try { io.close(); } finally { state=State.CLOSED; diagnostic="Closed: "+path; }
    }
}
