package kome.common.data;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.nbt.*;

/** Read-only startup inventory, including unloaded entities. Never opens RegionFile's rw constructor. */
final class KOMERecoveryHealthEvidence {
    private KOMERecoveryHealthEvidence() { }
    static List<KOMECampaignHealthProjection> scan(File root) throws IOException {
        List<KOMECampaignHealthProjection> result = new ArrayList<KOMECampaignHealthProjection>();
        List<File> worlds = new ArrayList<File>(); worlds.add(root);
        for (File child : children(root)) if (child.isDirectory() && new File(child, "region").isDirectory()) {
            rejectLink(child); worlds.add(child);
        }
        for (File world : worlds) {
            File region = new File(world, "region");
            if (!region.exists()) continue;
            rejectLink(region);
            for (File file : children(region)) if (file.getName().matches("r\\.-?[0-9]+\\.-?[0-9]+\\.mc[ar]")) {
                rejectLink(file); readRegion(file, result);
            }
        }
        for (String name : new String[]{"playerdata", "players"}) {
            File directory = new File(root, name); if (!directory.exists()) continue; rejectLink(directory);
            for (File file : children(directory)) if (file.getName().endsWith(".dat")) {
                rejectLink(file);
                try (InputStream in = new FileInputStream(file)) { collect(CompressedStreamTools.readCompressed(in), result); }
            }
        }
        return Collections.unmodifiableList(result);
    }
    private static File[] children(File directory) throws IOException {
        File[] children = directory.listFiles();
        if (children == null) throw new IOException("Cannot inventory recovery evidence: " + directory);
        Arrays.sort(children); return children;
    }
    private static void rejectLink(File file) throws IOException {
        if (Files.isSymbolicLink(file.toPath())) throw new IOException("Recovery evidence path is a symbolic link: " + file);
    }
    private static void readRegion(File file, List<KOMECampaignHealthProjection> result) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            if (in.length() == 0) return;
            if (in.length() < 8192) throw new IOException("Truncated region header: " + file);
            int[] offsets = new int[1024]; for (int i = 0; i < offsets.length; i++) offsets[i] = in.readInt();
            for (int offset : offsets) {
                if (offset == 0) continue;
                long position = (long)(offset >>> 8) * 4096; int allocated = (offset & 255) * 4096;
                if (position < 8192 || allocated == 0 || position + allocated > in.length()) throw new IOException("Invalid region offset: " + file);
                in.seek(position); int length = in.readInt(); int compression = in.readUnsignedByte();
                if (length <= 1 || length > allocated - 4) throw new IOException("Invalid region chunk length: " + file);
                byte[] bytes = new byte[length - 1]; in.readFully(bytes);
                InputStream compressed = new ByteArrayInputStream(bytes);
                if (compression == 1) compressed = new GZIPInputStream(compressed);
                else if (compression == 2) compressed = new InflaterInputStream(compressed);
                else throw new IOException("Unknown region compression: " + file);
                try (DataInputStream chunk = new DataInputStream(compressed)) {
                    collect(CompressedStreamTools.func_152456_a(chunk, new NBTSizeTracker(64L * 1024 * 1024)), result);
                }
            }
        }
    }
    static void collect(NBTBase nbt, List<KOMECampaignHealthProjection> result) {
        if (nbt instanceof NBTTagCompound) {
            NBTTagCompound tag = (NBTTagCompound)nbt;
            if (tag.hasKey("HealthFormatVersion")) result.add(KOMECampaignHealthProjection.read(tag));
            if (tag.hasKey(KOMECampaignHealthProjection.ENTITY_TAG)) {
                KOMECampaignHealthProjection p = KOMECampaignHealthProjection.read(tag.getCompoundTag(KOMECampaignHealthProjection.ENTITY_TAG));
                KOMEOrdinaryBattleRecoveryRegistry.require(tag.hasKey("UUIDMost",4) && tag.hasKey("UUIDLeast",4)
                    && p.alias.equals(new UUID(tag.getLong("UUIDMost"), tag.getLong("UUIDLeast")))
                    && tag.hasKey("HealF",5) && tag.getFloat("HealF") == p.hp, "Durable entity HP/alias contradicts health acknowledgment");
            }
            for (Object key : tag.func_150296_c()) collect(tag.getTag((String)key), result);
        } else if (nbt instanceof NBTTagList) {
            NBTTagList list = (NBTTagList)nbt;
            if (list.func_150303_d() == 10) for (int i = 0; i < list.tagCount(); i++) collect(list.getCompoundTagAt(i), result);
        }
    }
}
