package kome.common.network;

import io.netty.buffer.*;
import java.util.*;
import kome.common.tactical.edit.*;
import kome.common.tactical.edit.KOMETacticalComplexCatalog.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEPacketTacticalComplexCatalogTest {
    @Test public void everyPageKindRoundTripsBoundedImmutableTypedMetadata() {
        for (Kind kind : Kind.values()) {
            KOMETacticalComplexCatalog page = new KOMETacticalComplexCatalog(kind, "T100", kind == Kind.COMPLEXES ? null : "FORT", -1, 20, 0, 1,
                Collections.singletonList(new Row("ID", "Display", "NOT READY", "RELATED", 7, 3)));
            ByteBuf b = Unpooled.buffer();
            try {
                new KOMEPacketTacticalComplexCatalog(page).toBytes(b); assertTrue(b.readableBytes() <= KOMEPacketTacticalComplexCatalog.MAX_BYTES);
                KOMEPacketTacticalComplexCatalog copy = new KOMEPacketTacticalComplexCatalog(); copy.fromBytes(b);
                assertNotNull(copy.getCatalog()); assertEquals(kind, copy.getCatalog().kind); assertEquals(-1, copy.getCatalog().dimension);
                assertEquals("RELATED", copy.getCatalog().rows.get(0).relatedId); assertEquals(3, copy.getCatalog().rows.get(0).assignedBuildCount);
                assertEquals(7, copy.getCatalog().rows.get(0).revision); assertThrows(UnsupportedOperationException.class, () -> copy.getCatalog().rows.clear());
            } finally { b.release(); }
        }
    }
    @Test public void maximumUnicodePageFitsEnvelopeAndDecodes() {
        List<Row> rows = new ArrayList<Row>();
        for (int i = 0; i < 5; i++) rows.add(new Row("ID" + i, text(256), text(512), text(128), Long.MAX_VALUE, Integer.MAX_VALUE));
        ByteBuf b = Unpooled.buffer();
        try {
            new KOMEPacketTacticalComplexCatalog(new KOMETacticalComplexCatalog(Kind.BUILDS, "T100", "FORT", -1, Long.MAX_VALUE, 0, 5, rows)).toBytes(b);
            assertTrue(b.readableBytes() <= KOMEPacketTacticalComplexCatalog.MAX_BYTES);
            KOMEPacketTacticalComplexCatalog copy = new KOMEPacketTacticalComplexCatalog(); copy.fromBytes(b); assertNotNull(copy.getCatalog());
        } finally { b.release(); }
    }
    private String text(int count) { char[] chars = new char[count]; Arrays.fill(chars, '\uFFFF'); return new String(chars); }
    @Test public void invalidKindCollectionSizeTruncationTrailingBytesAndEnvelopeAreRejected() {
        ByteBuf b = Unpooled.buffer();
        try {
            b.writeByte(1); b.writeByte(255); reject(b); b.clear();
            b.writeByte(1); b.writeByte(Kind.COMPLEXES.ordinal()); KOMETacticalEditWire.writeText(b, "T100", 128); b.writeBoolean(false);
            b.writeInt(-1); b.writeLong(20); b.writeInt(0); b.writeInt(255); b.writeByte(255); reject(b); b.clear();
            new KOMEPacketTacticalComplexCatalog(new KOMETacticalComplexCatalog(Kind.COMPLEXES, "T100", null, -1, 20, 0, 0, Collections.emptyList())).toBytes(b);
            ByteBuf shortPacket = b.copy(0, b.readableBytes() - 1);
            try { reject(shortPacket); } finally { shortPacket.release(); }
            b.writeByte(0); reject(b); b.clear(); b.writeZero(KOMEPacketTacticalComplexCatalog.MAX_BYTES + 1); reject(b);
        } finally { b.release(); }
    }
    @Test public void forgedScopeAndOversizedMetadataAreNotRepresentable() {
        assertThrows(IllegalArgumentException.class, () -> new KOMETacticalComplexCatalog(Kind.BUILDS, "T100", null, 0, 0, 0, 0, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> new Row("ID", text(257), "", null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Row("ID", "", text(513), null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new KOMETacticalEditRequest(KOMETacticalEditRequest.Action.BROWSE_BUILDS,
            new KOMETacticalEditScope(KOMETacticalEditScope.Type.TILE_FORCE_DEPLOYMENT_AREA, "T100", null, "ID", 0), null, 0, new byte[0]));
    }
    private void reject(ByteBuf buffer) { KOMEPacketTacticalComplexCatalog p = new KOMEPacketTacticalComplexCatalog(); p.fromBytes(buffer); assertNull(p.getCatalog()); }
    @Test public void gatePagesPreserveBuildScopedIdentityAndBothNewBrowseRequestsRoundTrip() {
        List<Row> rows=Arrays.asList(new Row("G1","West fort","Physical UNKNOWN","B1",0,0),new Row("G1","East fort","Physical VERIFIED","B2",0,0));
        ByteBuf b=Unpooled.buffer();
        try {
            new KOMEPacketTacticalComplexCatalog(new KOMETacticalComplexCatalog(Kind.GATES,"T100","FORT",0,10,0,2,rows)).toBytes(b);
            KOMEPacketTacticalComplexCatalog copy=new KOMEPacketTacticalComplexCatalog(); copy.fromBytes(b); assertNotNull(copy.getCatalog());
            assertEquals("B1",copy.getCatalog().rows.get(0).relatedId); assertEquals("B2",copy.getCatalog().rows.get(1).relatedId);
            assertEquals("G1",copy.getCatalog().rows.get(0).id); assertEquals("G1",copy.getCatalog().rows.get(1).id);
            for (Kind kind : Arrays.asList(Kind.GATES,Kind.CONNECTIONS)) {
                b.clear(); KOMETacticalEditRequest request=KOMETacticalEditRequest.complexPage(kind,"T100",0,"FORT",1);
                new KOMEPacketTacticalEditRequest(request).toBytes(b); KOMEPacketTacticalEditRequest decoded=new KOMEPacketTacticalEditRequest(); decoded.fromBytes(b);
                assertTrue(decoded.isValid()); assertEquals(request.getAction(),decoded.getRequest().getAction()); assertEquals(1,decoded.getRequest().getExpectedSequence());
                assertEquals("FORT",decoded.getRequest().getScope().getComplexId());
                assertThrows(IllegalArgumentException.class,()->new KOMETacticalEditRequest(request.getAction(),new KOMETacticalEditScope(
                    KOMETacticalEditScope.Type.TILE_FORCE_DEPLOYMENT_AREA,"T100",null,"FIELD",0),null,0,new byte[0]));
            }
        } finally { b.release(); }
    }
}
