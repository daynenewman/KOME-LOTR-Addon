package kome.common.data;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import kome.common.KOMEReflection;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.MapStorage;

/** Canonical-data-only checkpoint; deliberately makes no entity/chunk durability claim. */
public final class KOMEWorldCheckpoint implements KOMEDailyCoordinator.Checkpoint {
    private final Path target;
    KOMEWorldCheckpoint(Path target) { this.target = target; }

    public static KOMEWorldCheckpoint forWorld(KOMEWorldData data, World world) throws Exception {
        if (world == null || KOMEReflection.isRemote(world)) throw new IOException("Server world unavailable");
        MapStorage storage = KOMEReflection.getMapStorage(world);
        if (storage == null || storage.loadData(KOMEWorldData.class, KOMEWorldData.DATA_NAME) != data)
            throw new IOException("Canonical MapStorage identity unavailable");
        Field field;
        try { field = MapStorage.class.getDeclaredField("saveHandler"); }
        catch (NoSuchFieldException mapped) { field = MapStorage.class.getDeclaredField("field_75751_a"); }
        field.setAccessible(true);
        ISaveHandler handler = (ISaveHandler) field.get(storage);
        File file = handler == null ? null : handler.getMapFileFromName(KOMEWorldData.DATA_NAME);
        if (file == null) throw new IOException("Canonical save handler unavailable");
        return new KOMEWorldCheckpoint(file.toPath());
    }

    @Override public void save(KOMEWorldData data) throws IOException {
        data.ensureWritable();
        NBTTagCompound root = new NBTTagCompound(), envelope = new NBTTagCompound();
        data.writeToNBT(root); envelope.setTag("data", root);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(envelope, bytes);
        Path absolute = target.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), "kome-daily-", ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                output.write(bytes.toByteArray()); output.getFD().sync();
            }
            // No non-atomic fallback: an unavailable checkpoint must stop stage advancement.
            Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            data.setDirty(false);
        } finally { Files.deleteIfExists(temporary); }
    }
}
