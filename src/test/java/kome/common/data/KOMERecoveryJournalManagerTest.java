package kome.common.data;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static kome.common.data.KOMERecoveryJournalTestSupport.*;
import static kome.common.data.KOMERecoveryJournalCodec.*;

public class KOMERecoveryJournalManagerTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private KOMERecoveryJournalManager manager(File root) throws IOException { return new KOMERecoveryJournalManager(root,new Object(),KOMERecoveryJournalIO.FILES); }
    @Test public void explicitBootstrapAndReopenPreserveIdentity() throws Exception {
        File root=temporary.newFolder(); UUID id;
        try(KOMERecoveryJournalManager m=manager(root)) {
            assertEquals(KOMERecoveryJournal.State.UNPROVISIONED,m.state()); m.open(); assertFalse(m.file().exists());
            m.bootstrap(); assertEquals(KOMERecoveryJournal.State.READY,m.state()); id=m.view().journalId;
            assertEquals(new File(new File(root,"data"),KOMERecoveryJournalManager.FILE_NAME).getCanonicalFile(),m.file());
            m.append(context(),0,m.view().lastHash); rejects(m::bootstrap);
        }
        try(KOMERecoveryJournalManager m=manager(root)) { m.open(); assertEquals(KOMERecoveryJournal.State.READY,m.state()); assertEquals(id,m.view().journalId); assertEquals(1,m.view().lastSequence); }
    }
    @Test public void establishedMissingWalIsNeverRecreated() throws Exception {
        File root=temporary.newFolder(); File wal;
        try(KOMERecoveryJournalManager m=manager(root)) { m.bootstrap(); wal=m.file(); }
        Files.delete(wal.toPath());
        try(KOMERecoveryJournalManager m=manager(root)) {
            assertEquals(KOMERecoveryJournal.State.BLOCKED,m.state()); m.open(); rejects(m::bootstrap);
            assertFalse(wal.exists()); assertTrue(m.diagnostic().contains("missing"));
        }
    }
    @Test public void orphanWalIsNotSilentlyAdopted() throws Exception {
        File root=temporary.newFolder();
        try(KOMERecoveryJournalManager m=manager(root)) { m.bootstrap(); }
        Files.delete(new File(new File(root,"data"),KOMERecoveryJournalManager.MARKER_NAME).toPath());
        try(KOMERecoveryJournalManager m=manager(root)) { m.open(); assertEquals(KOMERecoveryJournal.State.BLOCKED,m.state()); rejects(m::bootstrap); }
    }
    @Test public void realExclusiveFileLockPreventsSecondWriter() throws Exception {
        File root=temporary.newFolder();
        try(KOMERecoveryJournalManager first=manager(root); KOMERecoveryJournalManager second=manager(root)) {
            first.bootstrap(); second.open(); assertEquals(KOMERecoveryJournal.State.READY,first.state());
            assertEquals(KOMERecoveryJournal.State.BLOCKED,second.state()); assertTrue(second.diagnostic().contains("lock"));
        }
    }
    @Test public void copiedJournalAndMarkerCannotBeOpenedAsAnotherWorld() throws Exception {
        File first=temporary.newFolder(), second=temporary.newFolder();
        try(KOMERecoveryJournalManager m=manager(first)) { m.bootstrap(); }
        File destination=new File(second,"data"); assertTrue(destination.mkdir());
        for(String name:new String[]{KOMERecoveryJournalManager.FILE_NAME,KOMERecoveryJournalManager.MARKER_NAME})
            Files.copy(new File(new File(first,"data"),name).toPath(),new File(destination,name).toPath());
        try(KOMERecoveryJournalManager m=manager(second)) { m.open(); assertEquals(KOMERecoveryJournal.State.CORRUPT,m.state()); assertTrue(m.diagnostic().contains("World identity mismatch")); }
    }
    @Test public void walUuidMustMatchMarkerEvenWithValidHeaderChecksum() throws Exception {
        File root=temporary.newFolder(); File wal;
        try(KOMERecoveryJournalManager m=manager(root)) { m.bootstrap(); wal=m.file(); }
        String world=hex(sha(root.getCanonicalPath().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Files.write(wal.toPath(),header(new UUID(99,1),world,false));
        try(KOMERecoveryJournalManager m=manager(root)) { m.open(); assertEquals(KOMERecoveryJournal.State.CORRUPT,m.state()); assertTrue(m.diagnostic().contains("Journal identity mismatch")); }
    }
    @Test public void markerCorruptionFailsClosedAndPreservesEvidence() throws Exception {
        File root=temporary.newFolder();
        try(KOMERecoveryJournalManager m=manager(root)) { m.bootstrap(); }
        File marker=new File(new File(root,"data"),KOMERecoveryJournalManager.MARKER_NAME); byte[] bytes=Files.readAllBytes(marker.toPath()); bytes[bytes.length-1]^=1; Files.write(marker.toPath(),bytes);
        try(KOMERecoveryJournalManager m=manager(root)) { m.open(); assertEquals(KOMERecoveryJournal.State.CORRUPT,m.state()); rejects(m::bootstrap); }
        assertArrayEquals(bytes,Files.readAllBytes(marker.toPath()));
    }
    @Test public void interruptedBootstrapMarkerBlocksAutomaticReplacement() throws Exception {
        File root=temporary.newFolder();
        KOMERecoveryJournalIO.Factory broken=(file,create)-> {
            KOMERecoveryJournalIO actual=KOMERecoveryJournalIO.FILES.open(file,create);
            return new Delegating(actual) { @Override public void force() throws IOException { throw new IOException("injected marker force failure"); } };
        };
        try(KOMERecoveryJournalManager m=new KOMERecoveryJournalManager(root,new Object(),broken)) {
            rejects(m::bootstrap); assertEquals(KOMERecoveryJournal.State.POISONED,m.state()); assertFalse(m.file().exists());
        }
        try(KOMERecoveryJournalManager m=manager(root)) { m.open(); assertEquals(KOMERecoveryJournal.State.BLOCKED,m.state()); rejects(m::bootstrap); }
    }
    @Test public void openFailureIsBlockedNotAnEmptyReadyJournal() throws Exception {
        File root=temporary.newFolder(); try(KOMERecoveryJournalManager m=manager(root)) { m.bootstrap(); }
        KOMERecoveryJournalIO.Factory broken=(file,create)-> { throw new IOException("injected open failure"); };
        try(KOMERecoveryJournalManager m=new KOMERecoveryJournalManager(root,new Object(),broken)) {
            m.open(); assertEquals(KOMERecoveryJournal.State.BLOCKED,m.state()); assertNull(m.view()); assertTrue(m.diagnostic().contains("open/force failed"));
        }
    }
    private static class Delegating implements KOMERecoveryJournalIO {
        final KOMERecoveryJournalIO actual; Delegating(KOMERecoveryJournalIO actual) { this.actual=actual; }
        public long size() throws IOException { return actual.size(); }
        public int read(java.nio.ByteBuffer bytes,long position) throws IOException { return actual.read(bytes,position); }
        public int write(java.nio.ByteBuffer bytes,long position) throws IOException { return actual.write(bytes,position); }
        public void force() throws IOException { actual.force(); }
        public void truncate(long length) throws IOException { actual.truncate(length); }
        public boolean tryLock() throws IOException { return actual.tryLock(); }
        public void close() throws IOException { actual.close(); }
    }
}
