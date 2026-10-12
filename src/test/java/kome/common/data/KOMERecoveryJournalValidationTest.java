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

public class KOMERecoveryJournalValidationTest {
    @Test public void malformedHeadersFailClosed() throws Exception {
        byte[] original=header(JOURNAL,WORLD,false);
        for(int offset:new int[]{0,11,15,HEADER_SIZE-1}) {
            byte[] damaged=original.clone(); damaged[offset]^=1;
            KOMERecoveryJournal wal=open(new MemoryIO(damaged)); assertEquals(wal.diagnostic(),KOMERecoveryJournal.State.CORRUPT,wal.state());
        }
        for(int length=0;length<HEADER_SIZE;length++)
            assertEquals(KOMERecoveryJournal.State.CORRUPT,open(new MemoryIO(Arrays.copyOf(original,length))).state());
        assertEquals(KOMERecoveryJournal.State.CORRUPT,open(new MemoryIO(header(new UUID(1,9),WORLD,false))).state());
        assertEquals(KOMERecoveryJournal.State.CORRUPT,open(new MemoryIO(header(JOURNAL,ZERO_HASH,false))).state());
    }
    @Test public void payloadAndCollectionBoundsRejectBeforeUnboundedAllocation() throws Exception {
        byte[] h=header(JOURNAL,WORLD,false), framed=frame(Type.EPISODE_CONTEXT,1,hex(sha(h)),encode(context()));
        ByteBuffer.wrap(framed).putInt(16,MAX_PAYLOAD+1); ByteBuffer.wrap(framed).putInt(52,crc(framed,52));
        ByteArrayOutputStream all=new ByteArrayOutputStream(); all.write(h); all.write(framed);
        MemoryIO io=new MemoryIO(all.toByteArray()); KOMERecoveryJournal wal=open(io);
        assertEquals(KOMERecoveryJournal.State.CORRUPT,wal.state()); assertEquals(2,io.reads);
        byte[] payload=encode(context()); int countOffset=contextTargetsCountOffset(payload);
        ByteBuffer.wrap(payload).putInt(countOffset,MAX_ITEMS+1); rejects(()->decode(Type.EPISODE_CONTEXT,payload));
        byte[] string=encode(context()); ByteBuffer.wrap(string).putInt(0,MAX_TEXT+1); rejects(()->decode(Type.EPISODE_CONTEXT,string));
    }
    @Test public void invalidEnumsNilUuidsNonfiniteHealthAndRevisionOverflowRejected() throws Exception {
        KOMERecoveryJournal wal=open(empty()); KOMERecoveryJournal.Acknowledgment c=append(wal,context());
        byte[] context=encode(context()); int enumOffset=contextCoverageOffset(context); context[enumOffset+4]=(byte)88;
        rejects(()->decode(Type.EPISODE_CONTEXT,context));
        byte[] prepare=encode(prepare(c,Component.RIDER)); Arrays.fill(prepare,0,16,(byte)0);
        rejects(()->decode(Type.PREPARE,prepare));
        for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-1}) {
            rejects(()->new Prepare(binding(c,Component.RIDER),RIDER,RIDER_ALIAS,0,4,new UUID(4,1),invalid,null,5,new UUID(5,1),8,5,20,context().intentFingerprint()));
        }
        rejects(()->new Prepare(binding(c,Component.RIDER),RIDER,RIDER_ALIAS,0,Long.MAX_VALUE,new UUID(4,1),3,null,Long.MIN_VALUE,new UUID(5,1),8,5,20,context().intentFingerprint()));
        rejects(()->frame(Type.PREPARE,Long.MAX_VALUE,ZERO_HASH,new byte[0]));
        rejects(()->new Binding(JOURNAL,Long.MAX_VALUE,ZERO_HASH,"CF1","CF1:E1",RIDER,Component.RIDER));
        rejects(()->new Binding(JOURNAL,1,ZERO_HASH,"CF1","CF1:E9223372036854775807",RIDER,Component.RIDER));
        rejects(()->new Binding(JOURNAL,1,ZERO_HASH,"CF01","CF01:E1",RIDER,Component.RIDER));
    }
    @Test public void invalidUtfAndTrailingPayloadBytesRejected() throws Exception {
        byte[] payload=encode(context()); payload[4]=(byte)0xff; rejects(()->decode(Type.EPISODE_CONTEXT,payload));
        byte[] trailing=Arrays.copyOf(encode(context()),encode(context()).length+1); rejects(()->decode(Type.EPISODE_CONTEXT,trailing));
    }
    @Test public void invalidContextReferenceAndIntentFingerprintRejectedWithoutPoisoning() throws Exception {
        MemoryIO io=empty(); KOMERecoveryJournal wal=open(io); KOMERecoveryJournal.Acknowledgment c=append(wal,context());
        Binding wrong=new Binding(JOURNAL,99,c.hash,"CF1","CF1:E1",RIDER,Component.RIDER);
        rejects(()->append(wal,withBinding(prepare(c,Component.RIDER),wrong)));
        Binding wrongHash=new Binding(JOURNAL,c.sequence,ZERO_HASH,"CF1","CF1:E1",RIDER,Component.RIDER);
        rejects(()->append(wal,withBinding(prepare(c,Component.RIDER),wrongHash)));
        Binding wrongWorld=new Binding(new UUID(9,9),c.sequence,c.hash,"CF1","CF1:E1",RIDER,Component.RIDER);
        rejects(()->append(wal,withBinding(prepare(c,Component.RIDER),wrongWorld)));
        Prepare original=prepare(c,Component.RIDER);
        Prepare badIntent=new Prepare(original.binding,RIDER,RIDER_ALIAS,0,4,original.sourceTransitionId,3.25F,null,5,original.recoveryTransitionId,8.25F,5,20,ZERO_HASH);
        rejects(()->append(wal,badIntent)); assertEquals(1,wal.view().lastSequence); assertEquals(KOMERecoveryJournal.State.READY,wal.state());
    }
    @Test public void conflictingPrepareAndForeignMountCannotInheritGrant() throws Exception {
        KOMERecoveryJournal wal=open(empty()); KOMERecoveryJournal.Acknowledgment c=append(wal,context()); Prepare p=prepare(c,Component.MOUNT);
        Prepare foreign=new Prepare(p.binding,RIDER,new UUID(8,8),0,4,p.sourceTransitionId,p.sourceHp,null,5,p.recoveryTransitionId,p.targetHp,5,20,p.intentFingerprint);
        rejects(()->append(wal,foreign)); append(wal,p);
        Prepare conflict=new Prepare(p.binding,RIDER,MOUNT_ALIAS,0,4,p.sourceTransitionId,p.sourceHp,null,5,new UUID(8,9),p.targetHp,5,20,p.intentFingerprint);
        rejects(()->append(wal,conflict)); assertEquals(2,wal.view().lastSequence);
    }
    @Test public void persistedDuplicateIdentityRejectedEvenWithCorrectNewFrameChain() throws Exception {
        MemoryIO io=empty(); KOMERecoveryJournal wal=open(io); KOMERecoveryJournal.Acknowledgment c=append(wal,context());
        byte[] duplicate=frame(Type.EPISODE_CONTEXT,2,c.hash,encode(context()));
        ByteArrayOutputStream all=new ByteArrayOutputStream(); all.write(io.bytes); all.write(duplicate);
        KOMERecoveryJournal duplicated=open(new MemoryIO(all.toByteArray())); assertEquals(KOMERecoveryJournal.State.CORRUPT,duplicated.state());
    }
    @Test public void staleLineageAndReusedAliasFailClosed() throws Exception {
        KOMERecoveryJournal wal=open(empty()); KOMERecoveryJournal.Acknowledgment c=append(wal,context()); UUID next=new UUID(9,1);
        append(wal,new Lineage(binding(c,Component.MOUNT),MOUNT_ALIAS,next,new UUID(9,2),0,4,new UUID(4,1),null));
        rejects(()->append(wal,prepare(c,Component.MOUNT)));
        rejects(()->append(wal,new Lineage(binding(c,Component.MOUNT),next,MOUNT_ALIAS,new UUID(9,3),2,4,new UUID(4,1),null)));
        Prepare p=prepare(c,Component.MOUNT);
        Prepare verified=new Prepare(p.binding,RIDER,next,2,p.sourceRevision,p.sourceTransitionId,p.sourceHp,null,p.recoveryRevision,p.recoveryTransitionId,p.targetHp,5,20,p.intentFingerprint);
        append(wal,verified); assertEquals(3,wal.view().lastSequence);
    }
    @Test public void terminalTargetCannotPrepareOrReconstruct() throws Exception {
        KOMERecoveryJournal wal=open(empty()); KOMERecoveryJournal.Acknowledgment c=append(wal,context());
        append(wal,new Terminal(binding(c,Component.MOUNT),MOUNT_ALIAS,4,new UUID(4,1),Disposition.DEAD,30,"dead",null));
        rejects(()->append(wal,prepare(c,Component.MOUNT)));
        rejects(()->append(wal,new Lineage(binding(c,Component.MOUNT),MOUNT_ALIAS,new UUID(8,1),new UUID(8,2),0,4,new UUID(4,1),null)));
    }
    @Test public void laterPrepareMustInheritExactPreviousRecoveryFence() throws Exception {
        KOMERecoveryJournal wal=open(empty()); KOMERecoveryJournal.Acknowledgment c1=append(wal,context()); Prepare first=prepare(c1,Component.RIDER);
        KOMERecoveryJournal.Acknowledgment p1=append(wal,first); EpisodeContext later=context("CF2",1,5);
        KOMERecoveryJournal.Acknowledgment c2=append(wal,later);
        Binding b=new Binding(JOURNAL,c2.sequence,c2.hash,"CF2","CF2:E1",RIDER,Component.RIDER);
        Prepare missing=new Prepare(b,RIDER,RIDER_ALIAS,0,6,new UUID(8,1),2,null,7,new UUID(8,2),7,5,20,later.intentFingerprint());
        rejects(()->append(wal,missing));
        Prepare valid=new Prepare(b,RIDER,RIDER_ALIAS,0,6,new UUID(8,1),2,new Fence(JOURNAL,p1.sequence,first.recoveryTransitionId),7,new UUID(8,2),7,5,20,later.intentFingerprint());
        append(wal,valid); assertEquals(4,wal.view().lastSequence);
    }
    @Test public void noGameplayStateOrSchemasAreMutated() throws Exception {
        EpisodeContext c=context(); KOMEOrdinaryBattleRecoveryRegistry source=KOMEOrdinaryBattleRecoveryRegistry.restore(c.targets,Collections.singletonMap(c.episode.episodeId,c.episode));
        net.minecraft.nbt.NBTTagCompound before=source.write(); net.minecraft.entity.passive.EntityPig pig=new net.minecraft.entity.passive.EntityPig(null); pig.setHealth(3);
        KOMERecoveryJournal wal=open(empty()); KOMERecoveryJournal.Acknowledgment ack=append(wal,c); append(wal,prepare(ack,Component.RIDER));
        append(wal,new Terminal(binding(ack,Component.MOUNT),MOUNT_ALIAS,1,new UUID(11,1),Disposition.DEAD,30,"dead",null));
        assertEquals(before,source.write()); assertEquals(3,pig.getHealth(),0); assertEquals(12,KOMEWorldData.KOME_DATA_SCHEMA_VERSION);
        assertEquals(3,KOMEConflictPersistence.DATA_SCHEMA_VERSION); assertEquals(1,KOMEHiredUnitPhysicalLocator.DATA_SCHEMA_VERSION);
        for(Grant grant:c.episode.grants.values())assertEquals(GrantState.PENDING,grant.state);
    }
    @Test public void equalReservedRevisionCannotCarryDifferentSourceTransitionOrHp() throws Exception {
        KOMERecoveryJournal wal=open(empty()); KOMERecoveryJournal.Acknowledgment c1=append(wal,context()); Prepare first=prepare(c1,Component.RIDER);
        KOMERecoveryJournal.Acknowledgment p1=append(wal,first); EpisodeContext later=context("CF2",1,5);
        KOMERecoveryJournal.Acknowledgment c2=append(wal,later);
        Binding b=new Binding(JOURNAL,c2.sequence,c2.hash,"CF2","CF2:E1",RIDER,Component.RIDER);
        Fence fence=new Fence(JOURNAL,p1.sequence,first.recoveryTransitionId);
        Prepare wrongToken=new Prepare(b,RIDER,RIDER_ALIAS,0,5,new UUID(8,1),8.25F,fence,6,new UUID(8,2),13.25F,5,20,later.intentFingerprint());
        rejects(()->append(wal,wrongToken));
        Prepare wrongHp=new Prepare(b,RIDER,RIDER_ALIAS,0,5,first.recoveryTransitionId,2,fence,6,new UUID(8,2),7,5,20,later.intentFingerprint());
        rejects(()->append(wal,wrongHp));
    }
    @Test public void exhaustedReservedRevisionAndOversizedTextAreRejected() throws Exception {
        KOMERecoveryJournal wal=open(empty()); KOMERecoveryJournal.Acknowledgment c=append(wal,context());
        rejects(()->new Prepare(binding(c,Component.RIDER),RIDER,RIDER_ALIAS,0,Long.MAX_VALUE-1,new UUID(4,1),3,null,Long.MAX_VALUE,new UUID(5,1),8,5,20,context().intentFingerprint()));
        String large=String.join("",Collections.nCopies(MAX_TEXT+1,"x"));
        Terminal terminal=new Terminal(binding(c,Component.MOUNT),MOUNT_ALIAS,1,new UUID(4,1),Disposition.DEAD,30,large,null);
        rejects(()->append(wal,terminal)); assertEquals(1,wal.view().lastSequence);
    }
    private static Prepare withBinding(Prepare p,Binding b) {
        return new Prepare(b,p.participantId,p.physicalAlias,p.lineageSequence,p.sourceRevision,p.sourceTransitionId,p.sourceHp,p.inheritedFence,
            p.recoveryRevision,p.recoveryTransitionId,p.targetHp,p.recoveryAmount,p.maxHp,p.intentFingerprint);
    }
    private static int contextCoverageOffset(byte[] bytes) throws Exception {
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));
        for(int i=0;i<3;i++)in.skipBytes(in.readInt()); in.readLong(); if(in.readBoolean())in.readLong(); return bytes.length-in.available();
    }
    private static int contextTargetsCountOffset(byte[] bytes) throws Exception { int offset=contextCoverageOffset(bytes); return offset+4+ByteBuffer.wrap(bytes).getInt(offset)+1+8+8+8+4; }
}
