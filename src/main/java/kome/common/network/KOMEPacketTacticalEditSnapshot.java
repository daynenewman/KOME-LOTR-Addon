package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kome.common.KOMEAddon;
import kome.common.tactical.edit.*;

/** Complete detached draft and bounded diagnostic summary; never a mutable live tactical store. */
public final class KOMEPacketTacticalEditSnapshot implements IMessage {
    public static final int MAX_DIAGNOSTICS = 128;
    private KOMETacticalEditSessionManager.Status status;
    private KOMETacticalEditSnapshot snapshot;
    private boolean valid;
    public KOMEPacketTacticalEditSnapshot() { }
    public KOMEPacketTacticalEditSnapshot(KOMETacticalEditSessionManager.Status status, KOMETacticalEditSnapshot snapshot) {
        if (status == null) throw new IllegalArgumentException("Editor result required.");
        this.status = status; this.snapshot = snapshot; valid = true;
    }
    public boolean isValid() { return valid; }
    public KOMETacticalEditSessionManager.Status getStatus() { return status; }
    public KOMETacticalEditSnapshot getSnapshot() { return snapshot; }
    @Override public void toBytes(ByteBuf buffer) {
        if (!valid) throw new IllegalStateException("Invalid editor snapshot packet.");
        KOMEPopulationWire.writePacket(buffer, out -> {
            out.writeByte(1); out.writeByte(status.ordinal()); out.writeBoolean(snapshot != null);
            if (snapshot == null) return;
            writeUuid(out, snapshot.getPlayerId()); writeUuid(out, snapshot.getToken());
            KOMETacticalEditWire.writeScope(out, snapshot.getScope());
            out.writeLong(snapshot.getGeneration()); out.writeLong(snapshot.getPublicationSequence());
            out.writeLong(snapshot.getBaseRevision()); out.writeLong(snapshot.getBaseObjectRevision());
            out.writeLong(snapshot.getDraftSequence()); out.writeLong(snapshot.getCurrentRevision()); out.writeBoolean(snapshot.isClosed());
            out.writeInt(snapshot.getTotalAssignedBuildCount());
            int builds = Math.min(128, snapshot.getAssignedBuildIds().size()); out.writeShort(builds);
            for (int i = 0; i < builds; i++) KOMETacticalEditWire.writeText(out, snapshot.getAssignedBuildIds().get(i), 128);
            KOMETacticalEditWire.writePayload(out, KOMETacticalEditWire.encodeDraft(snapshot.getDraft()));
            KOMETacticalEditPreflight preflight = snapshot.getPreflight(); out.writeBoolean(preflight != null);
            if (preflight != null) {
                out.writeBoolean(preflight.canSave()); out.writeBoolean(preflight.isStructurallyValid()); out.writeByte(preflight.getState().ordinal());
                out.writeInt(preflight.getTotalDiagnosticCount());
                int count = Math.min(MAX_DIAGNOSTICS, preflight.getDiagnostics().size());
                boolean truncated = preflight.isSummaryTruncated() || count < preflight.getDiagnostics().size();
                for (int i = 0; i < count; i++) truncated |= preflight.getDiagnostics().get(i).length() > 1024;
                out.writeBoolean(truncated); out.writeShort(count);
                for (int i = 0; i < count; i++) {
                    String text = preflight.getDiagnostics().get(i);
                    if (text.length() > 1024) {
                        int end = Character.isHighSurrogate(text.charAt(1022)) ? 1022 : 1023;
                        text = text.substring(0, end) + "\u2026";
                    }
                    KOMETacticalEditWire.writeText(out, text, 1024);
                }
            }
        });
    }
    @Override public void fromBytes(ByteBuf buffer) {
        valid = false; snapshot = null; status = null;
        try {
            if (buffer.readableBytes() > 640 * 1024 || buffer.readUnsignedByte() != 1) throw new IllegalArgumentException("Invalid editor snapshot size/version.");
            int value = buffer.readUnsignedByte();
            if (value >= KOMETacticalEditSessionManager.Status.values().length) throw new IllegalArgumentException("Invalid editor status.");
            status = KOMETacticalEditSessionManager.Status.values()[value];
            if (buffer.readBoolean()) {
                UUID player = readUuid(buffer), token = readUuid(buffer);
                KOMETacticalEditScope scope = KOMETacticalEditWire.readScope(buffer);
                long generation = buffer.readLong(), publication = buffer.readLong(), base = buffer.readLong(), object = buffer.readLong();
                long sequence = buffer.readLong(), current = buffer.readLong(); boolean closed = buffer.readBoolean();
                int totalBuilds = buffer.readInt(), builds = buffer.readUnsignedShort();
                if (builds > 128 || totalBuilds < builds) throw new IllegalArgumentException("Invalid membership count.");
                List<String> assigned = new ArrayList<String>();
                for (int i = 0; i < builds; i++) {
                    String buildId = KOMETacticalEditWire.readText(buffer, 128);
                    KOMETacticalEditScope.validateId(buildId); assigned.add(buildId);
                }
                KOMETacticalEditDraft draft = KOMETacticalEditWire.decodeDraft(KOMETacticalEditWire.readPayload(buffer));
                KOMETacticalEditPreflight preflight = null;
                if (buffer.readBoolean()) {
                    boolean canSave = buffer.readBoolean(), structural = buffer.readBoolean(); int state = buffer.readUnsignedByte();
                    if (state >= KOMETacticalEditPreflight.State.values().length) throw new IllegalArgumentException("Invalid preflight state.");
                    int total = buffer.readInt(); boolean truncated = buffer.readBoolean(); int count = buffer.readUnsignedShort();
                    if (count > MAX_DIAGNOSTICS || total < count) throw new IllegalArgumentException("Invalid diagnostic count.");
                    List<String> diagnostics = new ArrayList<String>();
                    for (int i = 0; i < count; i++) diagnostics.add(KOMETacticalEditWire.readText(buffer, 1024));
                    preflight = new KOMETacticalEditPreflight(canSave, structural, KOMETacticalEditPreflight.State.values()[state], diagnostics, total, truncated);
                }
                snapshot = new KOMETacticalEditSnapshot(player, token, scope, generation, publication, base, object, sequence, current, closed, draft, preflight, assigned, totalBuilds);
            }
            KOMEPopulationWire.requireFullyRead(buffer); valid = true;
        } catch (RuntimeException invalid) { valid = false; snapshot = null; status = null; }
    }
    private static void writeUuid(ByteBuf out, UUID id) { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static UUID readUuid(ByteBuf in) { return new UUID(in.readLong(), in.readLong()); }
    public static final class Handler implements IMessageHandler<KOMEPacketTacticalEditSnapshot, IMessage> {
        @Override public IMessage onMessage(KOMEPacketTacticalEditSnapshot message, MessageContext context) {
            if (message != null && message.isValid()) KOMEAddon.proxy.acceptTacticalEditSnapshot(message);
            return null;
        }
    }
}
