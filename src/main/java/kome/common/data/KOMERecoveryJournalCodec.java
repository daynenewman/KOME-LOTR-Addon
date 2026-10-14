package kome.common.data;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import java.util.zip.CRC32;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;
import static kome.common.data.KOMERecoveryJournalRecord.*;

/** Version 1, big endian. All allocations from disk are explicitly bounded. */
final class KOMERecoveryJournalCodec {
    static final int VERSION = 1, HEADER_SIZE = 68, FRAME_HEADER = 56, FRAME_TRAILER = 48;
    static final int MAX_PAYLOAD = 4 * 1024 * 1024, MAX_TEXT = 4096, MAX_ITEMS = 4096;
    static final long FILE_MAGIC = 0x4b4f4d4557414c31L, MARKER_MAGIC = 0x4b4f4d4557494431L;
    static final int FRAME_MAGIC = 0x4b575231, END_MAGIC = 0x31454e44;
    static final String ZERO_HASH = String.join("", Collections.nCopies(64, "0"));
    private KOMERecoveryJournalCodec() { }
    static byte[] sha(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    static String hex(byte[] value) {
        StringBuilder out = new StringBuilder(); for (byte b : value) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
    static String hash(String value) {
        require(value != null && value.matches("[0-9a-f]{64}"), "Invalid SHA-256 identity"); return value;
    }
    static byte[] unhex(String value) {
        hash(value); byte[] result = new byte[32];
        for (int i=0;i<32;i++) result[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16); return result;
    }
    static int crc(byte[] bytes, int length) {
        CRC32 crc = new CRC32(); crc.update(bytes,0,length); return (int)crc.getValue();
    }
    static byte[] header(UUID id, String worldHash, boolean marker) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeLong(marker ? MARKER_MAGIC : FILE_MAGIC); out.writeInt(VERSION); out.writeInt(HEADER_SIZE);
        uuid(out,id); digest(out,worldHash); out.writeInt(crc(bytes.toByteArray(),HEADER_SIZE-4)); return bytes.toByteArray();
    }
    static UUID readHeader(byte[] bytes, String worldHash, boolean marker) throws IOException {
        require(bytes.length == HEADER_SIZE, "Incomplete file header");
        DataInputStream in = input(bytes);
        require(in.readLong() == (marker ? MARKER_MAGIC : FILE_MAGIC), "Wrong file magic");
        require(in.readInt() == VERSION, "Unsupported journal format");
        require(in.readInt() == HEADER_SIZE, "Wrong file header length"); UUID id=uuid(in);
        String actualWorld=digest(in); require(actualWorld.equals(worldHash), "World identity mismatch expected="+worldHash+" actual="+actualWorld);
        int actual=in.readInt(), expected=crc(bytes,HEADER_SIZE-4);
        require(actual==expected,"Header checksum mismatch expected="+expected+" actual="+actual); return id;
    }
    static byte[] frame(Type type, long sequence, String previous, byte[] payload) throws IOException {
        require(sequence > 0 && sequence < Long.MAX_VALUE, "Journal sequence exhausted");
        require(payload.length <= MAX_PAYLOAD, "Payload too large");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(FRAME_MAGIC); out.writeShort(VERSION); out.writeShort(type.ordinal()+1); out.writeLong(sequence);
        out.writeInt(payload.length); digest(out,previous); out.writeInt(crc(bytes.toByteArray(),FRAME_HEADER-4)); out.write(payload);
        byte[] trailer=trailer(payload.length,sequence);
        ByteArrayOutputStream signed=new ByteArrayOutputStream(); signed.write(bytes.toByteArray()); signed.write(trailer);
        out.write(sha(signed.toByteArray())); out.write(trailer); return bytes.toByteArray();
    }
    private static byte[] trailer(int payloadLength,long sequence) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(b);
        out.writeInt(FRAME_HEADER+payloadLength+FRAME_TRAILER); out.writeLong(sequence); out.writeInt(END_MAGIC); return b.toByteArray();
    }
    static Type frameType(byte[] prefix,long sequence,String previous) throws IOException {
        require(prefix.length == FRAME_HEADER,"Incomplete frame header"); DataInputStream in=input(prefix);
        require(in.readInt()==FRAME_MAGIC,"Wrong record magic"); require(in.readUnsignedShort()==VERSION,"Unsupported frame version");
        int type=in.readUnsignedShort(); require(type>=1 && type<=Type.values().length,"Invalid record type");
        long actualSequence=in.readLong(); require(actualSequence==sequence && sequence<Long.MAX_VALUE,
            "Sequence mismatch expected="+sequence+" actual="+actualSequence);
        int length=in.readInt(); require(length>=0 && length<=MAX_PAYLOAD,"Oversized/invalid payload length="+length);
        String actual=digest(in); require(actual.equals(previous),"Previous hash mismatch expected="+previous+" actual="+actual);
        int checksum=in.readInt(), expected=crc(prefix,FRAME_HEADER-4);
        require(checksum==expected,"Frame header checksum mismatch expected="+expected+" actual="+checksum);
        return Type.values()[type-1];
    }
    static int payloadLength(byte[] prefix) { return ByteBuffer.wrap(prefix).getInt(16); }
    /** A short header must still agree with every structural byte already present. Never resynchronize. */
    static void partialHeader(byte[] prefix,long sequence,String previous) throws IOException {
        require(sequence>0 && sequence<Long.MAX_VALUE,"Journal sequence exhausted");
        byte[] prototype=frame(Type.EPISODE_CONTEXT,sequence,previous,new byte[0]);
        for(int i=0;i<Math.min(prefix.length,6);i++) require(prefix[i]==prototype[i],"Corrupt partial frame prefix");
        if(prefix.length>=8) { int type=ByteBuffer.wrap(prefix).getShort(6)&65535; require(type>=1&&type<=4,"Invalid partial record type"); }
        else if(prefix.length==7) require(prefix[6]==0,"Invalid partial record type");
        for(int i=8;i<Math.min(prefix.length,16);i++) require(prefix[i]==prototype[i],"Corrupt partial sequence");
        if(prefix.length>16) {
            byte[] partialLength=new byte[4]; System.arraycopy(prefix,16,partialLength,0,Math.min(4,prefix.length-16));
            int minimum=ByteBuffer.wrap(partialLength).getInt(); require(minimum>=0&&minimum<=MAX_PAYLOAD,"Oversized partial payload");
        }
        for(int i=20;i<Math.min(prefix.length,52);i++) require(prefix[i]==prototype[i],"Corrupt partial previous hash");
        if(prefix.length>52) {
            byte[] expected=ByteBuffer.allocate(4).putInt(crc(prefix,52)).array();
            for(int i=52;i<prefix.length;i++) require(prefix[i]==expected[i-52],"Corrupt partial header checksum");
        }
    }
    static String validateFrame(byte[] bytes,Type type,long sequence,String previous) throws IOException {
        byte[] prefix=Arrays.copyOf(bytes,FRAME_HEADER); frameType(prefix,sequence,previous);
        int length=payloadLength(prefix); byte[] payload=Arrays.copyOfRange(bytes,FRAME_HEADER,FRAME_HEADER+length);
        byte[] expected=frame(type,sequence,previous,payload);
        require(Arrays.equals(bytes,expected),"Record checksum/trailer mismatch expected="+recordHash(expected)
            +" actual="+recordHash(bytes)); return recordHash(bytes);
    }
    static void partialBody(byte[] bytes,Type type,long sequence,String previous) throws IOException {
        int payload=payloadLength(bytes);
        if(bytes.length<FRAME_HEADER+payload) return;
        byte[] expected=frame(type,sequence,previous,Arrays.copyOfRange(bytes,FRAME_HEADER,FRAME_HEADER+payload));
        for(int i=FRAME_HEADER+payload;i<bytes.length;i++) require(bytes[i]==expected[i],"Corrupt partial record checksum/trailer");
    }
    static String recordHash(byte[] bytes) {
        int start=FRAME_HEADER+payloadLength(bytes); return hex(Arrays.copyOfRange(bytes,start,start+32));
    }
    static byte[] encode(KOMERecoveryJournalRecord record) throws IOException {
        ByteArrayOutputStream bytes=new BoundedOutput(); DataOutputStream out=new DataOutputStream(bytes);
        switch(record.type) {
        case EPISODE_CONTEXT:
            EpisodeContext c=(EpisodeContext)record; Episode e=c.episode;
            text(out,e.conflictId); text(out,e.episodeId); text(out,e.tileId); out.writeLong(e.startedAt); optional(out,e.endedAt);
            enumeration(out,e.coverage); out.writeBoolean(e.actualHostileCombatObserved);
            out.writeLong(c.nextConflictSequence); out.writeLong(c.conflictRevision); out.writeLong(c.episodeCheckpoint); out.writeFloat(c.recoveryAmount);
            count(out,c.targets.size()); for(Target t:c.targets.values()) {
                uuid(out,t.id); enumeration(out,t.component); uuid(out,t.physicalId); out.writeBoolean(t.terminal); uuids(out,t.aliases);
            }
            count(out,e.participants.size()); for(Participant p:e.participants.values()) {
                uuid(out,p.id); text(out,p.companyId); text(out,p.factionId); out.writeLong(p.admittedAt); enumeration(out,p.disposition);
                optional(out,p.exitedAt); text(out,p.exitReason); uuids(out,p.mountIds);
            }
            intent(out,e.intent); count(out,e.grants.size()); for(Grant g:e.grants.values()) {
                uuid(out,g.participantId); uuid(out,g.targetId); enumeration(out,g.component); out.writeFloat(g.amount);
            } break;
        case PREPARE:
            Prepare p=(Prepare)record; binding(out,p.binding); uuid(out,p.participantId); uuid(out,p.physicalAlias); out.writeLong(p.lineageSequence);
            out.writeLong(p.sourceRevision); uuid(out,p.sourceTransitionId); out.writeFloat(p.sourceHp); fence(out,p.inheritedFence);
            out.writeLong(p.recoveryRevision); uuid(out,p.recoveryTransitionId); out.writeFloat(p.targetHp); out.writeFloat(p.recoveryAmount);
            out.writeFloat(p.maxHp); digest(out,p.intentFingerprint); break;
        case TERMINAL:
            Terminal t=(Terminal)record; binding(out,t.binding); uuid(out,t.physicalAlias); out.writeLong(t.healthRevision); uuid(out,t.transitionId);
            enumeration(out,t.disposition); out.writeLong(t.observedAt); text(out,t.reason); fence(out,t.fence); break;
        case LINEAGE:
            Lineage l=(Lineage)record; binding(out,l.binding); uuid(out,l.oldAlias); uuid(out,l.newAlias); uuid(out,l.activationId);
            out.writeLong(l.previousLineageSequence); out.writeLong(l.healthRevision); uuid(out,l.healthTransitionId); fence(out,l.fence); break;
        default: throw new IllegalArgumentException("Unsupported record type");
        }
        require(bytes.size()<=MAX_PAYLOAD,"Payload too large"); return bytes.toByteArray();
    }
    static KOMERecoveryJournalRecord decode(Type type,byte[] bytes) throws IOException {
        require(bytes.length<=MAX_PAYLOAD,"Payload too large"); DataInputStream in=input(bytes); KOMERecoveryJournalRecord result;
        switch(type) {
        case EPISODE_CONTEXT:
            String conflict=text(in), episode=text(in), tile=text(in); long started=in.readLong(); Long ended=optional(in);
            Coverage coverage=enumeration(in,Coverage.values()); boolean observed=bool(in);
            long next=in.readLong(), revision=in.readLong(), checkpoint=in.readLong(); float amount=in.readFloat();
            Map<UUID,Target> targets=new TreeMap<UUID,Target>(); int n=count(in);
            for(int i=0;i<n;i++) {
                UUID id=uuid(in); Component component=enumeration(in,Component.values()); UUID physical=uuid(in); boolean dead=bool(in);
                Target target=new Target(id,component,physical,uuids(in),dead);
                require(targets.put(id,target)==null,"Duplicate context target");
            }
            Map<UUID,Participant> people=new TreeMap<UUID,Participant>(); n=count(in);
            for(int i=0;i<n;i++) {
                UUID id=uuid(in); Participant person=new Participant(id,text(in),text(in),in.readLong(),enumeration(in,Disposition.values()),
                    optional(in),text(in),uuids(in)); require(people.put(id,person)==null,"Duplicate participant");
            }
            Intent intent=intent(in); Map<String,Grant> grants=new TreeMap<String,Grant>(); n=count(in);
            for(int i=0;i<n;i++) {
                Grant grant=new Grant(uuid(in),uuid(in),enumeration(in,Component.values()),in.readFloat(),GrantState.PENDING,null,"");
                require(grants.put(grant.key(),grant)==null,"Duplicate pending grant");
            }
            result=new EpisodeContext(new Episode(conflict,tile,episode,started,ended,coverage,observed,people,intent,grants),targets,next,revision,checkpoint,amount); break;
        case PREPARE:
            result=new Prepare(binding(in),uuid(in),uuid(in),in.readLong(),in.readLong(),uuid(in),in.readFloat(),fence(in),
                in.readLong(),uuid(in),in.readFloat(),in.readFloat(),in.readFloat(),digest(in)); break;
        case TERMINAL:
            result=new Terminal(binding(in),uuid(in),in.readLong(),uuid(in),enumeration(in,Disposition.values()),in.readLong(),text(in),fence(in)); break;
        case LINEAGE:
            result=new Lineage(binding(in),uuid(in),uuid(in),uuid(in),in.readLong(),in.readLong(),uuid(in),fence(in)); break;
        default: throw new IllegalArgumentException("Unsupported record type");
        }
        require(in.available()==0,"Trailing payload bytes");
        require(Arrays.equals(bytes,encode(result)),"Noncanonical payload encoding/order"); return result;
    }
    private static void binding(DataOutputStream out,Binding b) throws IOException {
        uuid(out,b.journalId); out.writeLong(b.contextSequence); digest(out,b.contextHash); text(out,b.conflictId); text(out,b.episodeId);
        uuid(out,b.targetId); enumeration(out,b.component);
    }
    private static Binding binding(DataInputStream in) throws IOException {
        return new Binding(uuid(in),in.readLong(),digest(in),text(in),text(in),uuid(in),enumeration(in,Component.values()));
    }
    private static void fence(DataOutputStream out,Fence fence) throws IOException {
        out.writeBoolean(fence!=null); if(fence!=null) { uuid(out,fence.journalId); out.writeLong(fence.prepareSequence); uuid(out,fence.transitionId); }
    }
    private static Fence fence(DataInputStream in) throws IOException { return bool(in)?new Fence(uuid(in),in.readLong(),uuid(in)):null; }
    private static void intent(DataOutputStream out,Intent intent) throws IOException {
        out.writeBoolean(intent!=null); if(intent==null) return;
        text(out,intent.conflictId); text(out,intent.episodeId); out.writeLong(intent.expectedRevision); enumeration(out,intent.expectedState);
        enumeration(out,intent.kind); text(out,intent.expectedOwner); text(out,intent.winner); out.writeFloat(intent.recoveryAmount);
        enumeration(out,intent.stage); optional(out,intent.closedAt);
    }
    private static Intent intent(DataInputStream in) throws IOException {
        return bool(in)?new Intent(text(in),text(in),in.readLong(),enumeration(in,KOMEConflictRecord.State.values()),
            enumeration(in,ResolutionKind.values()),text(in),text(in),in.readFloat(),enumeration(in,Stage.values()),optional(in)):null;
    }
    static String intentFingerprint(Intent intent) {
        try { ByteArrayOutputStream bytes=new ByteArrayOutputStream(); intent(new DataOutputStream(bytes),intent); return hex(sha(bytes.toByteArray())); }
        catch(IOException impossible) { throw new IllegalArgumentException(impossible); }
    }
    private static void text(DataOutputStream out,String value) throws IOException {
        require(value!=null && value.length()<=MAX_TEXT,"Missing/oversized string");
        ByteBuffer encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
        require(encoded.remaining()<=MAX_TEXT,"String too large"); byte[] bytes=new byte[encoded.remaining()]; encoded.get(bytes);
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static String text(DataInputStream in) throws IOException {
        int n=in.readInt(); require(n>=0&&n<=MAX_TEXT&&n<=in.available(),"Invalid string length"); byte[] bytes=new byte[n]; in.readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static void uuid(DataOutputStream out,UUID id) throws IOException { KOMERecoveryJournalRecord.uuid(id); out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static UUID uuid(DataInputStream in) throws IOException { return KOMERecoveryJournalRecord.uuid(new UUID(in.readLong(),in.readLong())); }
    private static void digest(DataOutputStream out,String hash) throws IOException { out.write(unhex(hash)); }
    private static String digest(DataInputStream in) throws IOException { byte[] bytes=new byte[32]; in.readFully(bytes); return hex(bytes); }
    private static void enumeration(DataOutputStream out,Enum<?> value) throws IOException { text(out,value.name()); }
    private static <T extends Enum<T>> T enumeration(DataInputStream in,T[] values) throws IOException {
        String name=text(in); for(T value:values)if(value.name().equals(name))return value;
        throw new IllegalArgumentException("Invalid enum value="+name);
    }
    private static boolean bool(DataInputStream in) throws IOException { int b=in.readUnsignedByte(); require(b<=1,"Invalid boolean"); return b==1; }
    private static void optional(DataOutputStream out,Long value) throws IOException { out.writeBoolean(value!=null); if(value!=null)out.writeLong(value); }
    private static Long optional(DataInputStream in) throws IOException { return bool(in)?Long.valueOf(in.readLong()):null; }
    private static void count(DataOutputStream out,int count) throws IOException { require(count>=0&&count<=MAX_ITEMS,"Collection too large"); out.writeInt(count); }
    private static int count(DataInputStream in) throws IOException { int n=in.readInt(); require(n>=0&&n<=MAX_ITEMS,"Invalid collection count"); return n; }
    private static void uuids(DataOutputStream out,Set<UUID> ids) throws IOException { count(out,ids.size()); for(UUID id:ids)uuid(out,id); }
    private static Set<UUID> uuids(DataInputStream in) throws IOException {
        Set<UUID> result=new TreeSet<UUID>(); int n=count(in); for(int i=0;i<n;i++)require(result.add(uuid(in)),"Duplicate UUID"); return result;
    }
    private static final class BoundedOutput extends ByteArrayOutputStream {
        @Override public synchronized void write(int value) {
            require(count<MAX_PAYLOAD,"Payload too large"); super.write(value);
        }
        @Override public synchronized void write(byte[] bytes,int offset,int length) {
            require(length>=0 && length<=MAX_PAYLOAD-count,"Payload too large"); super.write(bytes,offset,length);
        }
    }
    private static DataInputStream input(byte[] bytes) { return new DataInputStream(new ByteArrayInputStream(bytes)); }
}
