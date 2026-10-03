package kome.common.tactical.edit;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kome.common.network.KOMEPacketTacticalEditRequest;
import kome.common.network.KOMEPacketTacticalEditSnapshot;
import kome.common.siege.*;
import kome.common.siege.geometry.*;
import kome.common.tactical.KOMEForceDeploymentArea;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.CompressedStreamTools;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMETacticalEditFoundationTest {
    private static KOMETacticalEditScope scope() { return new KOMETacticalEditScope(KOMETacticalEditScope.Type.TILE_FORCE_DEPLOYMENT_AREA, "T100", null, "FIELD", -1); }
    private static KOMETacticalEditDraft draft() { return new KOMETacticalEditDraft(new KOMEForceDeploymentArea("FIELD", "T100", -1, "Area",
        new KOMEPolygonPrism(KOMEPolygon.of(new KOMEXZPoint(Integer.MIN_VALUE, -10), new KOMEXZPoint(1, -10),
            new KOMEXZPoint(1, Integer.MAX_VALUE)), -20, 40), 7)); }
    private static KOMETacticalEditSnapshot snapshot(long generation, long publication, long sequence, boolean closed, UUID player, UUID token) {
        return new KOMETacticalEditSnapshot(player, token, scope(), generation, publication, 11, 7, sequence, 11, closed, draft(), null);
    }
    @Test public void scopeCanonicalizesOnlyOwnershipAndRejectsBadIdsOrMixedOwnership() {
        assertEquals("FIELD", new KOMETacticalEditScope(KOMETacticalEditScope.Type.TILE_FORCE_DEPLOYMENT_AREA, " t100 ", null, " field ", 0).getTargetId());
        assertThrows(IllegalArgumentException.class, () -> new KOMETacticalEditScope(KOMETacticalEditScope.Type.SIEGE_COMPLEX, "T100", "A", "B", 0));
        assertThrows(IllegalArgumentException.class, () -> new KOMETacticalEditScope(KOMETacticalEditScope.Type.TILE_FORCE_DEPLOYMENT_AREA, "T100", "A", "FIELD", 0));
        for (String id : new String[] {"", " ", "BAD\nID", String.join("", Collections.nCopies(129, "a"))}) {
            assertThrows(IllegalArgumentException.class, () -> new KOMETacticalEditScope(KOMETacticalEditScope.Type.TILE_FORCE_DEPLOYMENT_AREA, "T100", null, id, 0));
        }
    }
    @Test public void draftWirePreservesExtremeCoordinatesVertexOrderAndSignedDimension() {
        KOMETacticalEditDraft decoded = KOMETacticalEditWire.decodeDraft(KOMETacticalEditWire.encodeDraft(draft()));
        assertEquals(draft().encode(), decoded.encode()); assertEquals(-1, decoded.getDimensionId());
        assertEquals(Integer.MIN_VALUE, decoded.getArea().getPrism().getPolygon().getVertices().get(0).getX());
        assertEquals(Integer.MAX_VALUE, decoded.getArea().getPrism().getPolygon().getVertices().get(2).getZ());
    }
    @Test public void localIdsAndConceptualExteriorKeepTheirExistingSemantics() {
        KOMESiegeComplex complex = KOMESiegeReadinessFixtures.minimal("A", "T100", -1, null);
        KOMESiegeConnection local = KOMESiegeConnection.gateLess("entryMixed", KOMESiegeAreaRef.exterior(), KOMESiegeAreaRef.normal("A"), "T_ENTRY");
        complex = new KOMESiegeComplex("A", "T100", -1, 17, complex.getNormalSegments(), complex.getWallZones(), complex.getTransitionZones(), null, Arrays.asList(local));
        KOMESiegeComplex copy = KOMETacticalEditWire.decodeDraft(KOMETacticalEditWire.encodeDraft(new KOMETacticalEditDraft(complex))).getComplex();
        assertEquals("entryMixed", copy.getConnections().get(0).getId()); assertTrue(copy.getConnections().get(0).getEndpointA().isExterior());
        assertFalse(copy.getPreferredForceDeploymentAreaId().isPresent());
    }
    @Test public void draftMembershipRoundTripsAndDoesNotBecomeAnAssignmentAuthority() {
        KOMETacticalEditDraft original = new KOMETacticalEditDraft(KOMESiegeReadinessFixtures.empty("A", "T100", 0))
            .withMembership(KOMETacticalEditDraft.MembershipAction.REASSIGN, " b1 ", " b ");
        KOMETacticalEditDraft copy = KOMETacticalEditWire.decodeDraft(KOMETacticalEditWire.encodeDraft(original));
        assertEquals(KOMETacticalEditDraft.MembershipAction.REASSIGN, copy.getMembershipAction());
        assertEquals("B1", copy.getBuildId()); assertEquals("B", copy.getExpectedOldComplexId());
        assertEquals(0, copy.encode().getTagList("DefensiveBuildAssignments", 10).tagCount());
    }
    @Test public void authoredDataBoundsRejectOversizedIdsLabelsAndCollections() {
        assertThrows(IllegalArgumentException.class, () -> KOMETacticalEditWire.encodeDraft(new KOMETacticalEditDraft(new KOMEForceDeploymentArea(
            "FIELD", "T100", 0, String.join("", Collections.nCopies(257, "x")), draft().getArea().getPrism(), 1))));
        KOMESiegeComplex base = KOMESiegeReadinessFixtures.minimal("A", "T100", 0, null);
        KOMESiegeComplex tooMany = new KOMESiegeComplex("A", "T100", 0, 1, Collections.nCopies(33, base.getNormalSegments().get(0)),
            Collections.emptyList(), Collections.emptyList(), null, Collections.emptyList());
        assertThrows(IllegalArgumentException.class, () -> KOMETacticalEditWire.encodeDraft(new KOMETacticalEditDraft(tooMany)));
        KOMESiegeComplex badId = new KOMESiegeComplex("A", "T100", 0, 1, Collections.singletonList(new KOMENormalSegment("bad\nid", "", base.getNormalSegments().get(0).getPrism())),
            Collections.emptyList(), Collections.emptyList(), null, Collections.emptyList());
        assertThrows(IllegalArgumentException.class, () -> KOMETacticalEditWire.encodeDraft(new KOMETacticalEditDraft(badId)));
    }
    @Test public void payloadsAreDefensiveCopiesAndTrailingDraftDataIsRejected() {
        byte[] bytes = KOMETacticalEditWire.encodeDraft(draft());
        KOMETacticalEditRequest request = new KOMETacticalEditRequest(KOMETacticalEditRequest.Action.UPDATE, scope(), UUID.randomUUID(), 0, bytes);
        bytes[0] = 0; byte[] leaked = request.getPayload(); leaked[0] = 0;
        assertNotEquals(0, request.getPayload()[0]);
        assertThrows(IllegalArgumentException.class, () -> KOMETacticalEditWire.decodeDraft(new byte[KOMETacticalEditWire.MAX_DRAFT_BYTES + 1]));
        byte[] original = KOMETacticalEditWire.encodeDraft(draft());
        assertThrows(IllegalArgumentException.class, () -> KOMETacticalEditWire.decodeDraft(Arrays.copyOf(original, original.length + 1)));
    }
    @Test public void requestPacketRoundTripsEveryActionAndDoesNotDecodeDraftOnIntake() {
        UUID token = UUID.randomUUID();
        for (KOMETacticalEditRequest.Action action : KOMETacticalEditRequest.Action.values()) {
            KOMETacticalEditRequest intent = new KOMETacticalEditRequest(action, scope(), action == KOMETacticalEditRequest.Action.OPEN ? null : token,
                0, action == KOMETacticalEditRequest.Action.UPDATE ? new byte[] {1, 2, 3} : new byte[0]);
            ByteBuf buffer = Unpooled.buffer();
            try {
                new KOMEPacketTacticalEditRequest(intent).toBytes(buffer);
                KOMEPacketTacticalEditRequest copy = new KOMEPacketTacticalEditRequest(); copy.fromBytes(buffer);
                assertTrue(copy.isValid()); assertEquals(action, copy.getRequest().getAction()); assertEquals(scope(), copy.getRequest().getScope());
                assertArrayEquals(intent.getPayload(), copy.getRequest().getPayload()); assertFalse(buffer.isReadable());
            } finally { buffer.release(); }
        }
    }
    @Test public void unknownActionsScopesTruncationAndTrailingPacketBytesAreRejected() {
        ByteBuf good = Unpooled.buffer();
        try {
            new KOMEPacketTacticalEditRequest(KOMETacticalEditRequest.open(scope())).toBytes(good);
            for (int index : new int[] {1, 2}) {
                ByteBuf corrupt = good.copy(); corrupt.setByte(index, 255); assertInvalid(corrupt);
            }
            ByteBuf truncated = good.copy(0, good.readableBytes() - 1); assertInvalid(truncated);
            ByteBuf trailing = good.copy(); trailing.writeByte(0); assertInvalid(trailing);
            ByteBuf huge = Unpooled.buffer(); huge.writeZero(KOMETacticalEditWire.MAX_DRAFT_BYTES + 2049); assertInvalid(huge);
        } finally { good.release(); }
    }
    @Test public void packetRejectsOversizedScopeIdAndNegativeSequenceBeforeQueueing() {
        ByteBuf raw = Unpooled.buffer();
        try {
            raw.writeByte(1); raw.writeByte(0); raw.writeByte(0);
            kome.common.network.KOMEPopulationWire.writeText(raw, "T100");
            kome.common.network.KOMEPopulationWire.writeText(raw, "");
            kome.common.network.KOMEPopulationWire.writeText(raw, String.join("", Collections.nCopies(129, "A")));
            raw.writeInt(0); raw.writeBoolean(false); raw.writeLong(0); raw.writeInt(0);
            KOMEPacketTacticalEditRequest bad = new KOMEPacketTacticalEditRequest(); bad.fromBytes(raw); assertFalse(bad.isValid());
        } finally { raw.release(); }
        assertThrows(IllegalArgumentException.class, () -> new KOMETacticalEditRequest(KOMETacticalEditRequest.Action.SAVE, scope(), UUID.randomUUID(), -1, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new KOMETacticalEditRequest(KOMETacticalEditRequest.Action.SAVE, scope(), new UUID(0, 0), 0, new byte[0]));
    }
    private static void assertInvalid(ByteBuf buffer) {
        try { KOMEPacketTacticalEditRequest packet = new KOMEPacketTacticalEditRequest(); packet.fromBytes(buffer); assertFalse(packet.isValid()); }
        finally { buffer.release(); }
    }
    @Test public void boundedQueuePreservesOrderAndCleanupReleasesPlayerAndGlobalCapacity() {
        KOMETacticalEditRequestQueue<String> queue = new KOMETacticalEditRequestQueue<String>();
        UUID first = UUID.randomUUID();
        for (int i = 0; i < 8; i++) assertTrue(queue.offer(first, "First" + i, KOMETacticalEditRequest.open(scope())));
        assertFalse(queue.offer(first, "Overflow", KOMETacticalEditRequest.open(scope())));
        for (int i = 1; i < 8; i++) {
            UUID player = UUID.randomUUID(); for (int j = 0; j < 8; j++) assertTrue(queue.offer(player, "Other", KOMETacticalEditRequest.open(scope())));
        }
        assertEquals(64, queue.size()); assertFalse(queue.offer(UUID.randomUUID(), "Overflow", KOMETacticalEditRequest.open(scope())));
        assertEquals("First0", queue.poll().getHandle()); queue.clearPlayer(first); assertEquals(56, queue.size());
        assertTrue(queue.offer(first, "New", KOMETacticalEditRequest.open(scope()))); queue.clear(); assertEquals(0, queue.size());
    }
    @Test public void requestLimiterBoundsSavePreflightAndPlayerKeysWithDeterministicRefill() {
        AtomicLong clock = new AtomicLong(); KOMETacticalEditRequestLimiter limiter = new KOMETacticalEditRequestLimiter(clock::get);
        UUID player = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(player, KOMETacticalEditRequest.Action.SAVE)); assertTrue(limiter.tryAcquire(player, KOMETacticalEditRequest.Action.SAVE));
        assertFalse(limiter.tryAcquire(player, KOMETacticalEditRequest.Action.SAVE));
        for (int i = 0; i < 4; i++) assertTrue(limiter.tryAcquire(player, KOMETacticalEditRequest.Action.PREFLIGHT));
        assertFalse(limiter.tryAcquire(player, KOMETacticalEditRequest.Action.PREFLIGHT));
        clock.set(2000000000L); assertTrue(limiter.tryAcquire(player, KOMETacticalEditRequest.Action.SAVE));
        for (int i = 1; i < 128; i++) assertTrue(limiter.tryAcquire(UUID.randomUUID(), KOMETacticalEditRequest.Action.OPEN));
        assertFalse(limiter.tryAcquire(UUID.randomUUID(), KOMETacticalEditRequest.Action.OPEN)); assertEquals(128, limiter.trackedPlayers());
        clock.set(400L * 1000000000L); assertTrue(limiter.tryAcquire(UUID.randomUUID(), KOMETacticalEditRequest.Action.OPEN)); assertEquals(1, limiter.trackedPlayers());
        limiter.clearPlayer(player); limiter.clear(); assertEquals(0, limiter.trackedPlayers());
    }
    @Test public void snapshotsRoundTripAndMirrorRejectsStaleWrongPlayerAndClosedReplay() {
        UUID player = UUID.randomUUID(), token = UUID.randomUUID(); KOMETacticalEditClientMirror mirror = new KOMETacticalEditClientMirror();
        KOMETacticalEditSnapshot newer = snapshot(1, 2, 1, false, player, token);
        ByteBuf buffer = Unpooled.buffer();
        try {
            new KOMEPacketTacticalEditSnapshot(KOMETacticalEditSessionManager.Status.UPDATED, newer).toBytes(buffer);
            KOMEPacketTacticalEditSnapshot copy = new KOMEPacketTacticalEditSnapshot(); copy.fromBytes(buffer); assertTrue(copy.isValid());
            assertEquals(newer.getDraft().encode(), copy.getSnapshot().getDraft().encode());
            assertTrue(mirror.accept(player, -1, copy.getSnapshot()));
        } finally { buffer.release(); }
        assertFalse(mirror.accept(player, -1, snapshot(1, 1, 0, false, player, token)));
        assertFalse(mirror.accept(UUID.randomUUID(), -1, snapshot(1, 3, 1, false, player, token)));
        assertFalse(mirror.accept(player, 0, snapshot(1, 3, 1, false, player, token)));
        assertFalse(mirror.accept(player, -1, snapshot(1, 3, 0, false, player, token)));
        assertTrue(mirror.accept(player, -1, snapshot(1, 4, 1, true, player, token))); assertFalse(mirror.hasActiveSession());
        assertFalse(mirror.accept(player, -1, snapshot(1, 5, 2, false, player, token)));
        assertTrue(mirror.accept(player, -1, snapshot(2, 6, 0, false, player, UUID.randomUUID())));
        assertFalse(mirror.accept(player, -1, snapshot(1, 7, 1, false, player, token)));
    }
    @Test public void worldScopeCleanupPreservesSnapshotReplayProtectionUntilConnectionReset() {
        UUID player = UUID.randomUUID(), token = UUID.randomUUID(); KOMETacticalEditClientMirror mirror = new KOMETacticalEditClientMirror();
        assertTrue(mirror.accept(player, -1, snapshot(1, 1, 0, false, player, token))); mirror.clearScope();
        assertFalse(mirror.hasActiveSession()); assertNull(mirror.getSnapshot());
        assertFalse(mirror.accept(player, -1, snapshot(1, 2, 0, false, player, token)));
        mirror.reset(); assertTrue(mirror.accept(player, -1, snapshot(1, 1, 0, false, player, token)));
    }
    @Test public void largePreflightSummaryIsBoundedAndExplicitlyReportsOmittedDiagnostics() {
        UUID player = UUID.randomUUID(), token = UUID.randomUUID();
        KOMETacticalEditPreflight preflight = new KOMETacticalEditPreflight(true, true, KOMETacticalEditPreflight.State.INCOMPLETE, Collections.nCopies(200, "INCOMPLETE"));
        KOMETacticalEditSnapshot s = new KOMETacticalEditSnapshot(player, token, scope(), 1, 1, 11, 7, 0, 11, false, draft(), preflight);
        ByteBuf buffer = Unpooled.buffer();
        try {
            new KOMEPacketTacticalEditSnapshot(KOMETacticalEditSessionManager.Status.VALIDATED, s).toBytes(buffer);
            KOMEPacketTacticalEditSnapshot copy = new KOMEPacketTacticalEditSnapshot(); copy.fromBytes(buffer); assertTrue(copy.isValid());
            assertEquals(128, copy.getSnapshot().getPreflight().getDiagnostics().size()); assertEquals(200, copy.getSnapshot().getPreflight().getTotalDiagnosticCount());
            assertTrue(copy.getSnapshot().getPreflight().isSummaryTruncated()); assertFalse(copy.getSnapshot().getPreflight().isReady());
        } finally { buffer.release(); }
    }

    private interface RawWriter { void write(DataOutputStream output) throws IOException; }
    private static byte[] raw(RawWriter writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeByte(10); output.writeUTF(""); writer.write(output); output.flush(); return bytes.toByteArray();
    }
    private static byte[] rawNbt(NBTTagCompound tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.write(tag, new DataOutputStream(bytes)); return bytes.toByteArray();
    }
    private static void assertScanRejects(byte[] bytes) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> KOMETacticalEditWire.decodeDraft(bytes));
        assertTrue("Expected structural rejection before native parse: " + failure,
            failure.getMessage().startsWith("Invalid draft NBT structure:"));
    }
    @Test public void byteAndIntArraysAreRejectedBeforeNativeParserCanReadOrAllocateThem() throws Exception {
        for (int type : new int[] {7, 11}) {
            // A missing name/length also proves the scanner rejects at the tag type itself.
            assertScanRejects(raw(out -> out.writeByte(type)));
            for (int length : new int[] {0, -1, 1 << 27, Integer.MAX_VALUE}) {
                assertScanRejects(raw(out -> { out.writeByte(type); out.writeUTF("Array"); out.writeInt(length); }));
            }
        }
    }
    @Test public void negativeHugeAndWrongTypeListDeclarationsAreRejectedBeforeIteration() throws Exception {
        for (int length : new int[] {-1, Integer.MIN_VALUE, 129, Integer.MAX_VALUE}) {
            assertScanRejects(raw(out -> { out.writeByte(9); out.writeUTF("Rows"); out.writeByte(10); out.writeInt(length); }));
        }
        for (int type : new int[] {0, 7, 11, 255}) {
            assertScanRejects(raw(out -> { out.writeByte(9); out.writeUTF("Rows"); out.writeByte(type); out.writeInt(1); out.writeByte(0); }));
        }
    }
    @Test public void declaredStringAndCollectionLengthsMustFitRemainingPayload() throws Exception {
        assertScanRejects(raw(out -> { out.writeByte(8); out.writeUTF("Label"); out.writeShort(4); out.writeByte('x'); }));
        assertScanRejects(raw(out -> { out.writeByte(9); out.writeUTF("Rows"); out.writeByte(10); out.writeInt(2); out.writeByte(0); }));
        assertScanRejects(raw(out -> { out.writeByte(8); out.writeUTF("Label"); out.writeShort(65535); }));
    }
    @Test public void nestingAndCompoundTraversalAreBoundedBeforeNativeParsing() throws Exception {
        assertScanRejects(raw(out -> {
            for (int i = 0; i < 13; i++) { out.writeByte(10); out.writeUTF("Nested"); }
            for (int i = 0; i < 14; i++) out.writeByte(0);
        }));
        assertScanRejects(raw(out -> {
            for (int i = 0; i < 33; i++) { out.writeByte(3); out.writeUTF("Value" + i); out.writeInt(0); }
            out.writeByte(0);
        }));
        // Truncated fixed-size scalar/compound terminators cannot pass the scan either.
        assertScanRejects(raw(out -> { out.writeByte(4); out.writeUTF("Revision"); out.writeInt(0); }));
    }
    @Test public void totalTagCountAndUnsupportedScalarTypesAreRejectedBeforeNativeParsing() throws Exception {
        assertScanRejects(raw(out -> {
            out.writeByte(9); out.writeUTF("Rows"); out.writeByte(10); out.writeInt(128);
            for (int i = 0; i < 128; i++) {
                out.writeByte(9); out.writeUTF("Rows"); out.writeByte(10); out.writeInt(128);
                for (int j = 0; j < 128; j++) out.writeByte(0);
                out.writeByte(0);
            }
            out.writeByte(0);
        }));
        for (int type : new int[] {1, 2, 5, 6, 12, 255}) assertScanRejects(raw(out -> out.writeByte(type)));
    }
    private static KOMEPolygonPrism polygon(int vertices) {
        List<KOMEXZPoint> points = new ArrayList<KOMEXZPoint>();
        for (int i = 0; i < vertices - 2; i++) points.add(new KOMEXZPoint(i, 0));
        points.add(new KOMEXZPoint(vertices - 3, 10)); points.add(new KOMEXZPoint(0, 10));
        return new KOMEPolygonPrism(new KOMEPolygon(points), 0, 10);
    }
    private static KOMETacticalEditDraft areaWithVertices(int vertices) {
        return new KOMETacticalEditDraft(new KOMEForceDeploymentArea("FIELD", "T100", -1, "Area", polygon(vertices), 7));
    }
    private static KOMETacticalEditDraft complexWithCounts(int connections, int... vertexCounts) {
        List<KOMENormalSegment> normals = new ArrayList<KOMENormalSegment>();
        for (int i = 0; i < vertexCounts.length; i++) normals.add(new KOMENormalSegment("N" + i, "", polygon(vertexCounts[i])));
        List<KOMESiegeConnection> edges = new ArrayList<KOMESiegeConnection>();
        for (int i = 0; i < connections; i++) edges.add(KOMESiegeConnection.gateLess("ENTRY" + i,
            KOMESiegeAreaRef.exterior(), KOMESiegeAreaRef.normal("N0"), "T0"));
        return new KOMETacticalEditDraft(new KOMESiegeComplex("A", "T100", -1, 17, normals,
            Collections.emptyList(), Collections.emptyList(), null, edges));
    }
    private static void assertRoundTrip(KOMETacticalEditDraft draft) throws IOException {
        assertEquals(draft.encode(), KOMETacticalEditWire.decodeDraft(KOMETacticalEditWire.encodeDraft(draft)).encode());
        assertEquals(draft.encode(), KOMETacticalEditWire.decodeDraft(rawNbt(draft.encode())).encode());
    }
    @Test public void exactly128PolygonVerticesRoundTripAnd129AreRejectedOnBothPaths() throws Exception {
        assertRoundTrip(areaWithVertices(128));
        assertThrows(IllegalArgumentException.class, () -> KOMETacticalEditWire.encodeDraft(areaWithVertices(129)));
        assertScanRejects(rawNbt(areaWithVertices(129).encode()));
    }
    @Test public void exactly512TotalVerticesRoundTripAnd513AreRejectedOnBothPaths() throws Exception {
        assertRoundTrip(complexWithCounts(0, 128, 128, 128, 128));
        KOMETacticalEditDraft excessive = complexWithCounts(0, 128, 128, 128, 125, 4);
        assertThrows(IllegalArgumentException.class, () -> KOMETacticalEditWire.encodeDraft(excessive));
        assertScanRejects(rawNbt(excessive.encode()));
    }
    @Test public void exactly64ConnectionsRoundTripAnd65AreRejectedOnBothPaths() throws Exception {
        assertRoundTrip(complexWithCounts(64, 4));
        KOMETacticalEditDraft excessive = complexWithCounts(65, 4);
        assertThrows(IllegalArgumentException.class, () -> KOMETacticalEditWire.encodeDraft(excessive));
        assertScanRejects(rawNbt(excessive.encode()));
    }
    @Test public void zoneCountsRemainBoundedOnRawDecodeAsWellAsEncode() throws Exception {
        int[] counts = new int[33]; Arrays.fill(counts, 4);
        KOMETacticalEditDraft excessive = complexWithCounts(0, counts);
        assertThrows(IllegalArgumentException.class, () -> KOMETacticalEditWire.encodeDraft(excessive));
        assertScanRejects(rawNbt(excessive.encode()));
    }
    @Test public void full64KiBRepresentableDraftWithMaximumCollectionsStillRoundTrips() throws Exception {
        List<KOMENormalSegment> normals = new ArrayList<KOMENormalSegment>();
        List<KOMEWallZone> walls = new ArrayList<KOMEWallZone>();
        List<KOMETransitionZone> transitions = new ArrayList<KOMETransitionZone>();
        for (int i = 0; i < 32; i++) {
            normals.add(new KOMENormalSegment("N" + i, "", polygon(i == 0 ? 128 : i == 1 ? 8 : 4)));
            walls.add(new KOMEWallZone("W" + i, "", polygon(4), Collections.emptySet()));
            transitions.add(new KOMETransitionZone("T" + i, "", polygon(4)));
        }
        KOMESiegeComplex base = complexWithCounts(64, 4).getComplex();
        KOMETacticalEditDraft draft = new KOMETacticalEditDraft(new KOMESiegeComplex("A", "T100", -1, 17,
            normals, walls, transitions, null, base.getConnections()));
        NBTTagCompound tag = draft.encode();
        NBTTagCompound complex = tag.getTagList("SiegeComplexes", 10).getCompoundTagAt(0);
        int remaining = KOMETacticalEditWire.MAX_DRAFT_BYTES - rawNbt(tag).length;
        assertTrue(remaining > 0);
        // Authored labels fill the byte envelope; no ignored padding tags or altered geometry.
        for (String key : new String[] {"NormalSegments", "WallZones", "TransitionZones"}) {
            NBTTagList zones = complex.getTagList(key, 10);
            for (int i = 0; i < zones.tagCount() && remaining > 0; i++) {
                int bytes = Math.min(remaining, 768);
                while (bytes / 3 + bytes % 3 > 256) --bytes;
                String label = String.join("", Collections.nCopies(bytes / 3, "\u0800"))
                    + String.join("", Collections.nCopies(bytes % 3, "x"));
                zones.getCompoundTagAt(i).setString("Label", label); remaining -= bytes;
            }
        }
        assertEquals(0, remaining);
        byte[] bytes = rawNbt(tag); assertEquals(65536, bytes.length);
        KOMETacticalEditDraft decoded = KOMETacticalEditWire.decodeDraft(bytes);
        assertEquals(tag, decoded.encode());
        assertArrayEquals(bytes, KOMETacticalEditWire.encodeDraft(decoded));
        assertThrows(IllegalArgumentException.class, () -> KOMETacticalEditWire.decodeDraft(Arrays.copyOf(bytes, bytes.length + 1)));
    }
}
