package kome.common.data;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import static kome.common.data.KOMERecoveryJournalCodec.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.require;

/** Explicit lifecycle API only; no event registration, world-data loading, or health execution. */
public final class KOMERecoveryJournalManager implements Closeable {
    public static final String FILE_NAME="kome-ordinary-battle-recovery.wal";
    public static final String MARKER_NAME="kome-ordinary-battle-recovery.identity";
    private static final Map<File,KOMERecoveryJournalManager> SESSIONS=new HashMap<File,KOMERecoveryJournalManager>();
    private final File root, file, marker;
    private final Object session;
    private final Thread owner;
    private final String worldHash;
    private final KOMERecoveryJournalIO.Factory factory;
    private KOMERecoveryJournal journal;
    private KOMERecoveryJournal.State state;
    private String diagnostic;

    /** Register from the authoritative server lifecycle thread, just like KOME's packet session. */
    public static synchronized KOMERecoveryJournalManager forServer(MinecraftServer server) throws IOException {
        require(server!=null&&MinecraftServer.getServer()==server,"Current server session required");
        WorldServer overworld=server.worldServerForDimension(0);
        require(overworld!=null&&!overworld.isRemote&&overworld.provider.dimensionId==0,"Authoritative overworld required");
        File directory=overworld.getSaveHandler().getWorldDirectory();
        require(directory!=null,"World save directory unavailable"); File canonical=directory.getCanonicalFile();
        KOMERecoveryJournalManager existing=SESSIONS.get(canonical);
        if(existing!=null) { require(existing.session==server,"Journal belongs to another server session"); existing.owner(); return existing; }
        KOMERecoveryJournalManager created=new KOMERecoveryJournalManager(canonical,server,KOMERecoveryJournalIO.FILES);
        SESSIONS.put(canonical,created); return created;
    }
    // Package-only filesystem fixture seam. Production callers cannot supply a path.
    KOMERecoveryJournalManager(File saveDirectory,Object session,KOMERecoveryJournalIO.Factory factory) throws IOException {
        root=saveDirectory.getCanonicalFile(); require(root.isDirectory(),"Existing world save directory required");
        this.session=Objects.requireNonNull(session); this.factory=Objects.requireNonNull(factory); owner=Thread.currentThread();
        file=new File(new File(root,"data"),FILE_NAME).getCanonicalFile(); marker=new File(new File(root,"data"),MARKER_NAME).getCanonicalFile();
        require(file.toPath().startsWith(root.toPath())&&marker.toPath().startsWith(root.toPath()),"Journal path escapes world save");
        worldHash=hex(sha(root.getPath().getBytes(StandardCharsets.UTF_8)));
        inspect();
    }
    public File file() { return file; }
    public KOMERecoveryJournal.State state() { return journal==null?state:journal.state(); }
    public String diagnostic() { return journal==null?diagnostic:journal.diagnostic(); }
    public KOMERecoveryJournalView view() { return journal==null?null:journal.view(); }
    private void inspect() {
        boolean wal=file.exists(), identity=marker.exists();
        state=!wal&&!identity?KOMERecoveryJournal.State.UNPROVISIONED:KOMERecoveryJournal.State.BLOCKED;
        diagnostic=!wal&&!identity?"Explicit recovery journal provisioning required: "+file
            :identity&&!wal?"Established recovery journal is missing; replacement prohibited: "+file
            :wal&&!identity?"Journal identity marker missing; adoption prohibited: "+marker:"Journal not opened: "+file;
    }
    /** Marker is forced BEFORE creating the WAL. Interrupted bootstrap remains blocked for operator inspection. */
    public void bootstrap() throws IOException {
        owner(); require(state()!=KOMERecoveryJournal.State.CLOSED,"Manager closed");
        require(journal==null,"Close existing journal before provisioning"); inspect();
        require(state==KOMERecoveryJournal.State.UNPROVISIONED,"Provisioning refused: "+diagnostic);
        try {
            Files.createDirectories(file.getParentFile().toPath()); UUID id=UUID.randomUUID();
            try(KOMERecoveryJournalIO markerIO=factory.open(marker,true)) {
                require(markerIO.tryLock(),"Identity marker is locked");
                KOMERecoveryJournal.writeFully(markerIO,header(id,worldHash,true),0); markerIO.force();
                try(KOMERecoveryJournalIO walIO=factory.open(file,true)) {
                    require(walIO.tryLock(),"Journal is locked during bootstrap");
                    KOMERecoveryJournal.writeFully(walIO,header(id,worldHash,false),0); walIO.force();
                }
            }
            open();
        } catch(IOException | RuntimeException failure) {
            state=KOMERecoveryJournal.State.POISONED; diagnostic="Bootstrap uncertain; preserve evidence at "+file+": "+failure;
            throw new IOException(diagnostic,failure);
        }
    }
    /** Explicit rescan is the only route back from uncertain append. Never creates either file. */
    public void open() throws IOException {
        owner(); require(state()!=KOMERecoveryJournal.State.CLOSED,"Manager closed");
        if(journal!=null) { journal.close(); journal=null; }
        inspect(); if(state==KOMERecoveryJournal.State.UNPROVISIONED||!file.exists()||!marker.exists())return;
        UUID expectedId;
        try(KOMERecoveryJournalIO markerIO=factory.open(marker,false)) {
            if(!markerIO.tryLock()) { diagnostic="Identity marker lock unavailable: "+marker; return; }
            require(markerIO.size()==HEADER_SIZE,"Malformed identity marker length");
            expectedId=readHeader(KOMERecoveryJournal.readFully(markerIO,HEADER_SIZE,0),worldHash,true);
            markerIO.force();
        } catch(IllegalArgumentException invalid) {
            state=KOMERecoveryJournal.State.CORRUPT; diagnostic="Invalid identity marker "+marker+": "+invalid.getMessage(); return;
        } catch(IOException failure) {
            state=KOMERecoveryJournal.State.BLOCKED; diagnostic="Identity marker open/force failed "+marker+": "+failure; return;
        }
        try { journal=KOMERecoveryJournal.open(factory.open(file,false),file.getPath(),expectedId,worldHash); }
        catch(IOException failure) { state=KOMERecoveryJournal.State.BLOCKED; diagnostic="Journal open failed "+file+": "+failure; }
    }
    public KOMERecoveryJournal.Acknowledgment append(KOMERecoveryJournalRecord record,long expectedSequence,String expectedHash) throws IOException {
        owner(); require(journal!=null,"Journal is not open: "+diagnostic); return journal.append(record,expectedSequence,expectedHash);
    }
    public void repairTail() throws IOException { owner(); require(journal!=null,"Journal is not open"); journal.repairTail(); }
    private void owner() { if(Thread.currentThread()!=owner)throw new IllegalStateException("Recovery journal lifecycle requires owning server thread"); }
    public void close() throws IOException {
        owner(); if(state()==KOMERecoveryJournal.State.CLOSED)return;
        try { if(journal!=null)journal.close(); }
        finally {
            journal=null; state=KOMERecoveryJournal.State.CLOSED; diagnostic="Closed: "+file;
            synchronized(KOMERecoveryJournalManager.class) { if(SESSIONS.get(root)==this)SESSIONS.remove(root); }
        }
    }
}
