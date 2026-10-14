package kome.common.data;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;

/** Minimal positional I/O seam. Production has no userspace write buffering. */
public interface KOMERecoveryJournalIO extends Closeable {
    long size() throws IOException;
    int read(ByteBuffer destination, long position) throws IOException;
    int write(ByteBuffer source, long position) throws IOException;
    void force() throws IOException;
    void truncate(long length) throws IOException;
    boolean tryLock() throws IOException;

    interface Factory { KOMERecoveryJournalIO open(File file, boolean createNew) throws IOException; }
    Factory FILES = new Factory() {
        @Override public KOMERecoveryJournalIO open(File file, boolean createNew) throws IOException {
            // No CREATE on reopen: an unexpectedly deleted established file is never recreated.
            FileChannel channel = createNew
                ? FileChannel.open(file.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE)
                : FileChannel.open(file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE);
            return new ChannelIO(channel);
        }
    };
    final class ChannelIO implements KOMERecoveryJournalIO {
        private final FileChannel channel;
        private FileLock lock;
        ChannelIO(FileChannel channel) { this.channel=channel; }
        public long size() throws IOException { return channel.size(); }
        public int read(ByteBuffer bytes,long position) throws IOException { return channel.read(bytes,position); }
        public int write(ByteBuffer bytes,long position) throws IOException { return channel.write(bytes,position); }
        public void force() throws IOException { channel.force(true); }
        public void truncate(long length) throws IOException { channel.truncate(length); }
        public boolean tryLock() throws IOException {
            try { lock=channel.tryLock(); return lock!=null; }
            catch(OverlappingFileLockException busy) { return false; }
        }
        public void close() throws IOException { channel.close(); }
    }
}
