package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import java.math.BigInteger;
import kome.common.data.KOMEPopulationProjection;

/** Shared exact projection codec and one matching-artifact protocol identity. */
public final class KOMEPopulationWire {
    public static final String VERSION = "1.0.8-integration-g2";
    public static final int MAX_DECIMAL_DIGITS = 64;
    public static final int MAX_ROWS = 4096;
    public static final int MAX_TEXT_BYTES = 4096;
    public static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;
    public static final int MAX_COMPRESSED_NBT_BYTES = Short.MAX_VALUE;
    public static final int MAX_DECOMPRESSED_NBT_BYTES = 2 * 1024 * 1024;
    private KOMEPopulationWire() { }

    public static boolean accepts(String version) { return VERSION.equals(version); }

    public static void writeHeader(ByteBuf buf) { writeText(buf, VERSION); }
    public static void readHeader(ByteBuf buf) {
        if (buf.readableBytes() > MAX_PACKET_BYTES) throw new IllegalArgumentException("Oversized KOME packet");
        if (!accepts(readText(buf))) throw new IllegalArgumentException("KOME protocol mismatch; matching client/server required");
    }
    public static String readText(ByteBuf buf) {
        int length = ByteBufUtils.readVarInt(buf, 2);
        if (length < 0 || length > MAX_TEXT_BYTES || length > buf.readableBytes())
            throw new IllegalArgumentException("Invalid KOME string length");
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        try {
            return java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException invalid) {
            throw new IllegalArgumentException("Invalid KOME UTF-8", invalid);
        }
    }
    public static void writeText(ByteBuf buf, String value) {
        String text = value == null ? "" : value;
        if (text.length() > MAX_TEXT_BYTES) throw new IllegalArgumentException("KOME string is too long");
        try {
            java.nio.ByteBuffer encoded = java.nio.charset.StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(text));
            if (encoded.remaining() > MAX_TEXT_BYTES) throw new IllegalArgumentException("KOME string is too long");
            ByteBufUtils.writeVarInt(buf, encoded.remaining(), 2);
            buf.writeBytes(encoded);
        } catch (java.nio.charset.CharacterCodingException invalid) {
            throw new IllegalArgumentException("Invalid KOME UTF-8", invalid);
        }
    }
    public static void checkWritten(ByteBuf buf, int start) {
        if (buf.writerIndex() - start > MAX_PACKET_BYTES) throw new IllegalArgumentException("Oversized KOME packet");
    }

    public static void requireFullyRead(ByteBuf buf) {
        if (buf.isReadable()) throw new IllegalArgumentException("Trailing bytes in KOME packet");
    }

    /** Validate into a bounded temporary buffer; a rejected writer publishes no payload bytes. */
    public static void writePacket(ByteBuf destination, java.util.function.Consumer<ByteBuf> writer) {
        ByteBuf candidate = io.netty.buffer.Unpooled.buffer(256, MAX_PACKET_BYTES);
        try {
            writer.accept(candidate);
            destination.writeBytes(candidate);
        } finally {
            candidate.release();
        }
    }

    /** Complete codec validation and a deep defensive copy before enqueuing client publication. */
    public static <T extends cpw.mods.fml.common.network.simpleimpl.IMessage> T copyForPublication(
            T message, java.util.function.Supplier<T> factory) {
        ByteBuf encoded = io.netty.buffer.Unpooled.buffer(256, MAX_PACKET_BYTES);
        try {
            message.toBytes(encoded);
            T copy = factory.get();
            copy.fromBytes(encoded);
            requireFullyRead(encoded);
            return copy;
        } finally {
            encoded.release();
        }
    }

    public static void validateText(String value) {
        ByteBuf encoded = io.netty.buffer.Unpooled.buffer(32, MAX_TEXT_BYTES + 2);
        try { writeText(encoded, value); } finally { encoded.release(); }
    }

    /** The same signed-short GZIP envelope used by Forge 1.7.10 ByteBufUtils.writeTag. */
    public static byte[] prepareNbt(net.minecraft.nbt.NBTTagCompound tag) {
        if (tag == null) throw new IllegalArgumentException("Missing conquest packet data");
        validateNbtText(tag, 0);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream output = new java.io.DataOutputStream(new LimitedOutput(
                new java.util.zip.GZIPOutputStream(new LimitedOutput(bytes, MAX_COMPRESSED_NBT_BYTES)),
                MAX_DECOMPRESSED_NBT_BYTES))) {
            net.minecraft.nbt.CompressedStreamTools.write(tag, output);
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("KOME NBT exceeds its wire envelope", failure);
        }
        byte[] compressed = bytes.toByteArray();
        decodeNbt(compressed); // Also enforce the receiver's NBTSizeTracker accounting before dispatch.
        return compressed;
    }

    public static void writePreparedNbt(ByteBuf buf, byte[] compressed) {
        if (compressed.length == 0 || compressed.length > MAX_COMPRESSED_NBT_BYTES)
            throw new IllegalArgumentException("Invalid compressed KOME NBT length");
        buf.writeShort(compressed.length);
        buf.writeBytes(compressed);
    }

    public static net.minecraft.nbt.NBTTagCompound readNbt(ByteBuf buf) {
        int length = buf.readShort();
        if (length <= 0 || length > MAX_COMPRESSED_NBT_BYTES || length > buf.readableBytes())
            throw new IllegalArgumentException("Invalid compressed KOME NBT length");
        byte[] compressed = new byte[length];
        buf.readBytes(compressed);
        return decodeNbt(compressed);
    }

    private static net.minecraft.nbt.NBTTagCompound decodeNbt(byte[] compressed) {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.InputStream input = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(compressed));
                java.io.OutputStream limited = new LimitedOutput(bytes, MAX_DECOMPRESSED_NBT_BYTES)) {
            byte[] chunk = new byte[4096];
            int count;
            while ((count = input.read(chunk)) != -1) limited.write(chunk, 0, count);
            java.io.DataInputStream data = new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()));
            net.minecraft.nbt.NBTTagCompound result = net.minecraft.nbt.CompressedStreamTools.func_152456_a(
                data, new net.minecraft.nbt.NBTSizeTracker(MAX_DECOMPRESSED_NBT_BYTES));
            if (data.available() != 0) throw new IllegalArgumentException("Trailing decompressed KOME NBT bytes");
            validateNbtText(result, 0);
            return result;
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("Invalid or oversized compressed KOME NBT", failure);
        }
    }

    private static void validateNbtText(net.minecraft.nbt.NBTBase tag, int depth) {
        if (depth > 512) throw new IllegalArgumentException("KOME NBT nesting is too deep");
        if (tag instanceof net.minecraft.nbt.NBTTagCompound) {
            net.minecraft.nbt.NBTTagCompound compound = (net.minecraft.nbt.NBTTagCompound) tag;
            for (Object key : compound.func_150296_c()) {
                validateText((String) key);
                validateNbtText(compound.getTag((String) key), depth + 1);
            }
        } else if (tag instanceof net.minecraft.nbt.NBTTagList) {
            net.minecraft.nbt.NBTTagList list = (net.minecraft.nbt.NBTTagList) tag;
            count(list.tagCount());
            if (list.func_150303_d() == 8) {
                for (int i = 0; i < list.tagCount(); i++) validateText(list.getStringTagAt(i));
            } else if (list.func_150303_d() == 10) {
                for (int i = 0; i < list.tagCount(); i++) validateNbtText(list.getCompoundTagAt(i), depth + 1);
            } else if (list.func_150303_d() == 9) {
                // 1.7.10 exposes no generic list getter. Remove from the END of a defensive copy.
                net.minecraft.nbt.NBTTagList copy = (net.minecraft.nbt.NBTTagList) list.copy();
                for (int i = copy.tagCount() - 1; i >= 0; i--) validateNbtText(copy.removeTag(i), depth + 1);
            }
        } else if (tag instanceof net.minecraft.nbt.NBTTagString) {
            validateText(((net.minecraft.nbt.NBTTagString) tag).func_150285_a_());
        }
    }

    private static final class LimitedOutput extends java.io.FilterOutputStream {
        private final int limit;
        private int written;
        LimitedOutput(java.io.OutputStream output, int limit) { super(output); this.limit = limit; }
        private void reserve(int length) throws java.io.IOException {
            if (length < 0 || length > limit - written) throw new java.io.IOException("KOME NBT size limit " + limit);
            written += length;
        }
        @Override public void write(int value) throws java.io.IOException { reserve(1); out.write(value); }
        @Override public void write(byte[] value, int offset, int length) throws java.io.IOException {
            reserve(length); out.write(value, offset, length);
        }
    }
    public static net.minecraft.nbt.NBTTagList compoundRows(net.minecraft.nbt.NBTTagCompound tag, String key) {
        if (tag == null) throw new IllegalArgumentException("Missing KOME packet data");
        if (!tag.hasKey(key)) return new net.minecraft.nbt.NBTTagList();
        if (!tag.hasKey(key, 9)) throw new IllegalArgumentException("Invalid KOME list: " + key);
        net.minecraft.nbt.NBTTagList rows = (net.minecraft.nbt.NBTTagList) tag.getTag(key);
        count(rows.tagCount());
        if (rows.tagCount() > 0 && rows.func_150303_d() != 10)
            throw new IllegalArgumentException("Invalid KOME compound rows: " + key);
        return rows;
    }
    public static int count(int count) {
        if (count < 0 || count > MAX_ROWS) throw new IllegalArgumentException("Invalid KOME row count: " + count);
        return count;
    }
    public static long nonnegative(long value) {
        if (value < 0L) throw new IllegalArgumentException("Negative KOME population/time");
        return value;
    }
    public static BigInteger readExact(ByteBuf buf) {
        return parseExact(readText(buf));
    }
    public static BigInteger parseExact(String decimal) {
        if (decimal == null || decimal.length() > MAX_DECIMAL_DIGITS || !decimal.matches("0|[1-9][0-9]*"))
            throw new IllegalArgumentException("Invalid exact KOME population/rate");
        return new BigInteger(decimal);
    }
    public static void writeExact(ByteBuf buf, BigInteger value) {
        if (value == null || value.signum() < 0 || value.toString().length() > MAX_DECIMAL_DIGITS)
            throw new IllegalArgumentException("Invalid exact KOME population/rate");
        writeText(buf, value.toString());
    }
    public static void writeProjection(ByteBuf buf, KOMEPopulationProjection value) {
        writeText(buf, value.faction);
        buf.writeLong(nonnegative(value.availablePopulationCenti));
        writeExact(buf, value.activePopulationCenti);
        writeExact(buf, value.dailyRateUnits);
        buf.writeBoolean(value.capEnabled);
        buf.writeLong(nonnegative(value.capCenti));
    }
    public static KOMEPopulationProjection readProjection(ByteBuf buf) {
        String faction = readText(buf);
        long available = nonnegative(buf.readLong());
        BigInteger active = readExact(buf), rate = readExact(buf);
        boolean cap = buf.readBoolean();
        long capCenti = nonnegative(buf.readLong());
        return new KOMEPopulationProjection(faction, available, active, rate, cap, capCenti);
    }

    public static net.minecraft.nbt.NBTTagCompound projectionTag(KOMEPopulationProjection value) {
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
        tag.setString("Faction", value.faction);
        tag.setLong("AvailablePopulationCenti", value.availablePopulationCenti);
        tag.setString("ActivePopulationCenti", value.activePopulationCenti.toString());
        tag.setString("DailyRateUnits", value.dailyRateUnits.toString());
        tag.setBoolean("CapEnabled", value.capEnabled);
        tag.setLong("CapCenti", value.capCenti);
        projectionFromTag(tag); // Identical validation at both wire boundaries.
        return tag;
    }

    public static KOMEPopulationProjection projectionFromTag(net.minecraft.nbt.NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("Faction", 8) || !tag.hasKey("CapEnabled", 1)
                || !tag.hasKey("AvailablePopulationCenti", 4) || !tag.hasKey("ActivePopulationCenti", 8)
                || !tag.hasKey("DailyRateUnits", 8) || !tag.hasKey("CapCenti", 4))
            throw new IllegalArgumentException("Missing exact KOME projection fields");
        validateText(tag.getString("Faction"));
        return new KOMEPopulationProjection(tag.getString("Faction"), tag.getLong("AvailablePopulationCenti"),
                parseExact(tag.getString("ActivePopulationCenti")), parseExact(tag.getString("DailyRateUnits")),
                tag.getBoolean("CapEnabled"), tag.getLong("CapCenti"));
    }
}
