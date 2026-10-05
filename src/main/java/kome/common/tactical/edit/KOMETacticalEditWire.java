package kome.common.tactical.edit;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import kome.common.network.KOMEPopulationWire;
import net.minecraft.nbt.*;

/** Exact uncompressed NBT draft; bounded transport inflation is handled separately. No live-store publication. */
public final class KOMETacticalEditWire {
    /** Per-edit technical budget: bounds parser memory and synchronous geometry work, not persisted polygon counts. */
    public static final int MAX_DRAFT_BYTES = 65536;
    /** Technical parser budgets, not gameplay geometry counts. Every list row consumes at least one encoded byte. */
    private static final int MAX_NBT_NODES = MAX_DRAFT_BYTES;
    private static final long MAX_NBT_ACCOUNTED_BYTES = 64L * MAX_DRAFT_BYTES;
    private KOMETacticalEditWire() { }

    public static byte[] encodeDraft(KOMETacticalEditDraft draft) {
        if (draft == null) throw new IllegalArgumentException("Missing draft.");
        byte[] bytes = encodeNbt(draft.encode());
        KOMETacticalPacketEnvelope.requireTransportableDraft(bytes);
        return bytes;
    }

    public static KOMETacticalEditDraft decodeDraft(byte[] bytes) {
        KOMETacticalEditDraft draft = KOMETacticalEditDraft.decode(decodeNbt(bytes));
        encodeDraft(draft); // Match structural/serialized byte admission on both sides, without polygon count limits.
        return draft;
    }

    private static byte[] encodeNbt(NBTTagCompound tag) {
        checkTree(tag, "", 0, new int[1]);
        ByteBuf buffer = Unpooled.buffer(256, MAX_DRAFT_BYTES);
        try {
            CompressedStreamTools.write(tag, new DataOutputStream(new io.netty.buffer.ByteBufOutputStream(buffer)));
            byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes);
            decodeNbt(bytes); // Match the receiver's memory accounting, too.
            return bytes;
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid draft NBT.", failure);
        } catch (IndexOutOfBoundsException excessive) {
            throw new IllegalArgumentException("Editor technical size budget reached: one definition must fit in 64 KiB of exact NBT. No geometry was truncated.", excessive);
        } finally { buffer.release(); }
    }

    private static NBTTagCompound decodeNbt(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_DRAFT_BYTES) throw new IllegalArgumentException("Invalid draft size: editor technical budget is 64 KiB per definition.");
        try {
            // Minecraft 1.7.10 array accounting can overflow before allocation. Validate
            // every raw tag/count first; NBTSizeTracker and checkTree are additional barriers.
            new DraftNbtScan(bytes).validate();
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            NBTTagCompound tag = CompressedStreamTools.func_152456_a(input, new NBTSizeTracker(MAX_NBT_ACCOUNTED_BYTES));
            if (input.available() != 0) throw new IllegalArgumentException("Trailing draft bytes.");
            checkTree(tag, "", 0, new int[1]);
            return tag;
        } catch (IOException failure) { throw new IllegalArgumentException("Invalid draft NBT.", failure); }
    }

    /** No NBT objects or count-sized arrays are constructed from untrusted declarations. */
    private static final class DraftNbtScan {
        private final DataInputStream input;
        private int nodes;

        DraftNbtScan(byte[] bytes) { input = new DataInputStream(new ByteArrayInputStream(bytes)); }

        void validate() throws IOException {
            if (input.readUnsignedByte() != 10) throw invalid("Root must be a compound.");
            utf(128);
            payload(10, "", 0);
            if (input.available() != 0) throw invalid("Trailing bytes.");
        }

        private void supported(int type) {
            // END is handled only as a compound terminator or the type of an empty list.
            // In particular, byte/int arrays are rejected BEFORE reading their length.
            if (type != 3 && type != 4 && type != 8 && type != 9 && type != 10) {
                throw invalid("Unsupported tag type " + type + ".");
            }
        }

        private void payload(int type, String key, int depth) throws IOException {
            supported(type);
            if (depth > 12 || ++nodes > MAX_NBT_NODES) throw invalid("Technical NBT nesting/node budget reached.");
            switch (type) {
                case 3: skip(4L); break;
                case 4: skip(8L); break;
                case 8: utf("Label".equals(key) ? 256 : KOMETacticalEditScope.MAX_ID_LENGTH); break;
                case 9:
                    remaining(5L);
                    int element = input.readUnsignedByte();
                    long length = input.readInt();
                    if (length < 0L || length > MAX_NBT_NODES || (element != 10 && !(element == 0 && length == 0L))) {
                        throw invalid("Invalid list type/count.");
                    }
                    // Each compound row needs at least its END byte; check before iteration.
                    remaining(length);
                    for (long i = 0; i < length; i++) payload(10, key, depth + 1);
                    break;
                case 10:
                    int fields = 0;
                    while (true) {
                        int child = input.readUnsignedByte();
                        if (child == 0) break;
                        supported(child);
                        if (++fields > 32) throw invalid("Too many compound fields.");
                        String name = utf(128);
                        payload(child, name, depth + 1);
                    }
                    break;
                default: throw invalid("Unsupported tag.");
            }
        }

        private String utf(int characterLimit) throws IOException {
            remaining(2L);
            input.mark(2);
            long bytes = input.readUnsignedShort();
            // Modified UTF-8 uses at most three bytes per UTF-16 code unit.
            if (bytes > 3L * characterLimit) throw invalid("Oversized string.");
            remaining(bytes);
            input.reset();
            String value = input.readUTF(); // Allocation is now bounded by <= 768 checked bytes.
            text(value, characterLimit);
            return value;
        }

        private void remaining(long bytes) throws IOException {
            if (bytes < 0L || bytes > (long) input.available()) throw invalid("Declared length exceeds remaining bytes.");
        }
        private void skip(long bytes) throws IOException {
            remaining(bytes);
            if (input.skipBytes((int) bytes) != bytes) throw invalid("Truncated scalar.");
        }
        private IllegalArgumentException invalid(String detail) {
            return new IllegalArgumentException("Invalid draft NBT structure: " + detail);
        }
    }

    private static void checkTree(NBTBase tag, String key, int depth, int[] count) {
        if (depth > 12 || ++count[0] > MAX_NBT_NODES) throw new IllegalArgumentException("Editor technical NBT nesting/node budget reached.");
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            if (compound.func_150296_c().size() > 32) throw new IllegalArgumentException("Too many draft fields.");
            for (Object name : compound.func_150296_c()) {
                text((String) name, 128);
                checkTree(compound.getTag((String) name), (String) name, depth + 1, count);
            }
        } else if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            if (list.tagCount() > MAX_NBT_NODES || (list.tagCount() != 0 && list.func_150303_d() != 10)) {
                throw new IllegalArgumentException("Invalid or oversized draft list.");
            }
            for (int i = 0; i < list.tagCount(); i++) checkTree(list.getCompoundTagAt(i), key, depth + 1, count);
        } else if (tag instanceof NBTTagString) {
            String value = ((NBTTagString) tag).func_150285_a_();
            text(value, "Label".equals(key) ? 256 : KOMETacticalEditScope.MAX_ID_LENGTH);
            if (!"Label".equals(key)) {
                for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) {
                    throw new IllegalArgumentException("Control character in authored ID.");
                }
            }
        } else if (!(tag instanceof NBTTagInt) && !(tag instanceof NBTTagLong)) {
            throw new IllegalArgumentException("Unsupported draft NBT tag.");
        }
    }

    private static void text(String text, int limit) {
        if (text == null || text.length() > limit) throw new IllegalArgumentException("Oversized editor text.");
        KOMEPopulationWire.validateText(text);
    }
    public static void writeText(ByteBuf buffer, String text, int limit) {
        text(text, limit); KOMEPopulationWire.writeText(buffer, text);
    }
    public static String readText(ByteBuf buffer, int limit) {
        String result = KOMEPopulationWire.readText(buffer); text(result, limit); return result;
    }
    public static void writePayload(ByteBuf buffer, byte[] bytes) {
        if (bytes == null || bytes.length > MAX_DRAFT_BYTES) throw new IllegalArgumentException("Oversized editor payload.");
        buffer.writeInt(bytes.length); buffer.writeBytes(bytes);
    }
    public static byte[] readPayload(ByteBuf buffer) {
        int length = buffer.readInt();
        if (length < 0 || length > MAX_DRAFT_BYTES || length > buffer.readableBytes()) throw new IllegalArgumentException("Invalid editor payload length.");
        byte[] result = new byte[length]; buffer.readBytes(result); return result;
    }
    public static void writeScope(ByteBuf buffer, KOMETacticalEditScope scope) {
        buffer.writeByte(scope.getType().ordinal());
        writeText(buffer, scope.getTileId(), 128);
        writeText(buffer, scope.getComplexId() == null ? "" : scope.getComplexId(), 128);
        writeText(buffer, scope.getTargetId(), 128); buffer.writeInt(scope.getDimensionId());
    }
    public static KOMETacticalEditScope readScope(ByteBuf buffer) {
        int ordinal = buffer.readUnsignedByte();
        if (ordinal >= KOMETacticalEditScope.Type.values().length) throw new IllegalArgumentException("Invalid editor scope.");
        String tile = readText(buffer, 128), complex = readText(buffer, 128), target = readText(buffer, 128);
        return new KOMETacticalEditScope(KOMETacticalEditScope.Type.values()[ordinal], tile,
            complex.isEmpty() ? null : complex, target, buffer.readInt());
    }
}
