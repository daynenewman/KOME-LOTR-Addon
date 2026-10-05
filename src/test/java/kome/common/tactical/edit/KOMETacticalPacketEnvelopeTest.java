package kome.common.tactical.edit;

import io.netty.buffer.*;
import java.io.*;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import kome.common.network.*;
import kome.common.siege.KOMESiegeReadinessFixtures;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMETacticalPacketEnvelopeTest {
    private byte[] compressed(byte[] bytes) throws IOException {
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        try(DeflaterOutputStream stream=new DeflaterOutputStream(result)) { stream.write(bytes); }
        return result.toByteArray();
    }
    @Test public void transportFitsRealForgeCustomPayloadAndPreservesLargeExactBytes() {
        byte[] source=new byte[KOMETacticalEditWire.MAX_DRAFT_BYTES]; for(int i=0;i<source.length;i++) source[i]=(byte)(i%127);
        ByteBuf packet=Unpooled.buffer(); ByteBuf decoded=null;
        try {
            packet.writeByte(38); // The same additional discriminator FML writes.
            KOMETacticalPacketEnvelope.write(packet,KOMETacticalPacketEnvelope.MAX_REQUEST_BYTES,out->out.writeBytes(source));
            assertTrue(packet.readableBytes()<32767);
            ByteBuf copy=packet.copy();
            try {
                cpw.mods.fml.common.network.internal.FMLProxyPacket proxy=new cpw.mods.fml.common.network.internal.FMLProxyPacket(copy,"KOME");
                assertNotNull(proxy.toC17Packet());
            } finally { copy.release(); }
            packet.readByte(); decoded=KOMETacticalPacketEnvelope.read(packet,KOMETacticalPacketEnvelope.MAX_REQUEST_BYTES);
            byte[] actual=new byte[decoded.readableBytes()]; decoded.readBytes(actual); assertArrayEquals(source,actual);
        } finally { if(decoded!=null) decoded.release(); packet.release(); }
    }
    @Test public void hostileDeclaredSizesAreRejectedBeforeInflation() {
        for(int length:new int[] {-1,Integer.MAX_VALUE,KOMETacticalPacketEnvelope.MAX_REQUEST_BYTES+1}) {
            ByteBuf packet=Unpooled.buffer();
            try { packet.writeByte(2); packet.writeByte(1); packet.writeInt(length); packet.writeByte(0);
                assertThrows(IllegalArgumentException.class,()->KOMETacticalPacketEnvelope.read(packet,KOMETacticalPacketEnvelope.MAX_REQUEST_BYTES));
            } finally { packet.release(); }
        }
    }
    @Test public void inflationCannotExceedClaimedOrTechnicalSizeAndCannotIgnoreTrailingBytes() throws Exception {
        byte[] data=new byte[KOMETacticalPacketEnvelope.MAX_REQUEST_BYTES+1]; byte[] packed=compressed(data);
        ByteBuf bomb=Unpooled.buffer();
        try { bomb.writeByte(2); bomb.writeByte(1); bomb.writeInt(KOMETacticalPacketEnvelope.MAX_REQUEST_BYTES); bomb.writeBytes(packed);
            assertThrows(IllegalArgumentException.class,()->KOMETacticalPacketEnvelope.read(bomb,KOMETacticalPacketEnvelope.MAX_REQUEST_BYTES));
        } finally { bomb.release(); }
        for(boolean trailing:new boolean[] {false,true}) {
            ByteBuf packet=Unpooled.buffer();
            try {
                KOMETacticalPacketEnvelope.write(packet,KOMETacticalPacketEnvelope.MAX_REQUEST_BYTES,out->out.writeZero(40000));
                if(trailing) packet.writeByte(0); else packet.setByte(packet.writerIndex()-1,packet.getByte(packet.writerIndex()-1)^255);
                assertThrows(IllegalArgumentException.class,()->KOMETacticalPacketEnvelope.read(packet,KOMETacticalPacketEnvelope.MAX_REQUEST_BYTES));
            } finally { packet.release(); }
        }
    }
    @Test public void excessiveIncompressibleOutputFailsWithoutPartialWriteOrTruncation() {
        byte[] bytes=new byte[40000]; new Random(1234).nextBytes(bytes); ByteBuf packet=Unpooled.buffer();
        try {
            packet.writeInt(123); int position=packet.writerIndex();
            IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->KOMETacticalPacketEnvelope.write(packet,KOMETacticalPacketEnvelope.MAX_REQUEST_BYTES,out->out.writeBytes(bytes)));
            assertTrue(failure.getMessage().contains("technical size")); assertEquals(position,packet.writerIndex());
            assertEquals(40000,bytes.length);
        } finally { packet.release(); }
    }
    @Test public void inflatedNbtStillPassesTheRawStructuralScannerBeforeNativeParsing() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(bytes);
        out.writeByte(10); out.writeUTF(""); out.writeByte(11); out.writeUTF("UnsafeArray"); out.writeInt(Integer.MAX_VALUE);
        byte[] invalid=bytes.toByteArray(); ByteBuf wire=Unpooled.buffer(); ByteBuf decoded=null;
        try {
            KOMETacticalPacketEnvelope.write(wire,KOMETacticalPacketEnvelope.MAX_REQUEST_BYTES,raw->raw.writeBytes(invalid));
            decoded=KOMETacticalPacketEnvelope.read(wire,KOMETacticalPacketEnvelope.MAX_REQUEST_BYTES);
            byte[] payload=new byte[decoded.readableBytes()]; decoded.readBytes(payload);
            IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->KOMETacticalEditWire.decodeDraft(payload));
            assertTrue(failure.getMessage().startsWith("Invalid draft NBT structure:"));
        } finally { if(decoded!=null) decoded.release(); wire.release(); }
    }
    @Test public void largeUncompressibleAdvisorySummariesAreExplicitlyShortenedWithoutDroppingAuthoredGeometry() {
        Random random=new Random(456); List<String> diagnostics=new ArrayList<>(); List<String> builds=new ArrayList<>();
        for(int i=0;i<128;i++) {
            StringBuilder text=new StringBuilder(); for(int j=0;j<1024;j++) text.append((char)('!'+random.nextInt(90)));
            diagnostics.add(text.toString()); builds.add("B"+i);
        }
        KOMETacticalEditDraft draft=new KOMETacticalEditDraft(KOMESiegeReadinessFixtures.minimal("A","T100",0,null));
        KOMETacticalEditScope scope=new KOMETacticalEditScope(KOMETacticalEditScope.Type.SIEGE_COMPLEX,"T100","A","A",0);
        KOMETacticalEditSnapshot s=new KOMETacticalEditSnapshot(UUID.randomUUID(),UUID.randomUUID(),scope,1,1,10,draft.getObjectRevision(),0,10,false,draft,
            new KOMETacticalEditPreflight(true,true,KOMETacticalEditPreflight.State.INCOMPLETE,diagnostics),builds,128);
        ByteBuf wire=Unpooled.buffer();
        try {
            new KOMEPacketTacticalEditSnapshot(KOMETacticalEditSessionManager.Status.VALIDATED,s).toBytes(wire);
            assertTrue(wire.readableBytes()<32767); KOMEPacketTacticalEditSnapshot copy=new KOMEPacketTacticalEditSnapshot(); copy.fromBytes(wire);
            assertTrue(copy.isValid()); assertTrue(copy.getSnapshot().getPreflight().isSummaryTruncated());
            assertEquals(128,copy.getSnapshot().getPreflight().getTotalDiagnosticCount()); assertEquals(draft.encode(),copy.getSnapshot().getDraft().encode());
        } finally { wire.release(); }
    }
}
