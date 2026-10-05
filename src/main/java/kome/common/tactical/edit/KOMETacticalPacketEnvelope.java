package kome.common.tactical.edit;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.DataFormatException;

/**
 * Tactical-only transport envelope. Forge 1.7.10 C17 custom payloads must be smaller than 32767 bytes.
 * Raw authored NBT keeps its independent scan/byte budget. Inflation is bounded BEFORE native parsing.
 * No truncation or partial writes: an incompressible excessive definition gets a technical-size diagnostic.
 */
public final class KOMETacticalPacketEnvelope {
    public static final int MAX_WIRE_BYTES = 30 * 1024; // Headroom for FML's discriminator/custom-payload envelope.
    public static final int MAX_REQUEST_BYTES = KOMETacticalEditWire.MAX_DRAFT_BYTES + 2048;
    public static final int MAX_SNAPSHOT_BYTES = 640 * 1024;
    private KOMETacticalPacketEnvelope() { }

    /** Reserve enough room for scope/token/revision metadata; rejection reaches the editor before asynchronous sending. */
    public static void requireTransportableDraft(byte[] draft) {
        byte[] packed = deflate(draft);
        if (Math.min(packed.length, draft.length) + 2048 > MAX_WIRE_BYTES) throw size();
    }

    public static void write(ByteBuf destination, int rawLimit, Consumer<ByteBuf> writer) {
        ByteBuf raw = Unpooled.buffer(256, rawLimit);
        try {
            writer.accept(raw);
            byte[] bytes = new byte[raw.readableBytes()]; raw.readBytes(bytes);
            byte[] packed = deflate(bytes);
            boolean compressed = packed.length < bytes.length;
            byte[] payload = compressed ? packed : bytes;
            if (payload.length + 6 > MAX_WIRE_BYTES) throw size();
            destination.writeByte(2); destination.writeByte(compressed ? 1 : 0);
            destination.writeInt(bytes.length); destination.writeBytes(payload);
        } catch (IndexOutOfBoundsException excessive) { throw size(); }
        finally { raw.release(); }
    }
    public static ByteBuf read(ByteBuf source, int rawLimit) {
        if (source.readableBytes() < 6 || source.readableBytes() > MAX_WIRE_BYTES || source.readUnsignedByte() != 2) throw size();
        int mode = source.readUnsignedByte(), length = source.readInt();
        if (mode > 1 || length < 0 || length > rawLimit) throw size();
        byte[] packed = new byte[source.readableBytes()]; source.readBytes(packed);
        if (mode == 0) {
            if (packed.length != length) throw size();
            return Unpooled.wrappedBuffer(packed);
        }
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(packed); ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] chunk = new byte[4096];
            while (!inflater.finished()) {
                int count = inflater.inflate(chunk);
                if ((long) output.size() + count > length) throw size();
                output.write(chunk, 0, count);
                if (count == 0 && !inflater.finished()) throw new IllegalArgumentException("Invalid compressed tactical packet.");
            }
            if (output.size() != length || inflater.getRemaining() != 0) throw size();
            return Unpooled.wrappedBuffer(output.toByteArray());
        } catch (DataFormatException invalid) { throw new IllegalArgumentException("Invalid compressed tactical packet.", invalid); }
        finally { inflater.end(); }
    }
    private static byte[] deflate(byte[] bytes) {
        Deflater compressor = new Deflater();
        try {
            compressor.setInput(bytes); compressor.finish(); ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] chunk = new byte[4096];
            while (!compressor.finished()) {
                int count = compressor.deflate(chunk);
                // Stop producing a rejected packet; allow small raw packets to use their uncompressed representation.
                if ((long) output.size() + count + 6 > MAX_WIRE_BYTES) {
                    if (bytes.length + 6 <= MAX_WIRE_BYTES) return bytes;
                    throw size();
                }
                output.write(chunk, 0, count);
            }
            return output.toByteArray();
        } finally { compressor.end(); }
    }
    public static final class SizeLimitException extends IllegalArgumentException {
        private SizeLimitException() { super("Tactical packet technical size budget reached (30 KiB on the wire). Reduce this definition's size; no authored data was truncated."); }
    }
    private static SizeLimitException size() {
        return new SizeLimitException();
    }
}
