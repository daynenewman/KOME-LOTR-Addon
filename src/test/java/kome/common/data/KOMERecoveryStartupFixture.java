package kome.common.data;

import java.io.File;
import java.lang.reflect.Field;
import net.minecraft.server.MinecraftServer;

/** Establish real journal readiness for inert WorldServer adapters; restore the surrounding session. */
final class KOMERecoveryStartupFixture implements AutoCloseable {
    private final Field singleton;
    private final Object previous;
    private final KOMERecoveryStartup startup;
    private final KOMERecoveryJournalManager manager;
    KOMERecoveryStartupFixture(KOMEWorldData data, File root) throws Exception {
        singleton = KOMERecoveryStartup.class.getDeclaredField("session"); singleton.setAccessible(true);
        previous = singleton.get(null);
        startup = new KOMERecoveryStartup(MinecraftServer.getServer());
        manager = new KOMERecoveryJournalManager(root, new Object(), KOMERecoveryJournalIO.FILES);
        try {
            startup.initialize(manager, KOMEConflictPersistence.write(data.getConflictService(),
                data.getJoinBattleDeploymentReceipts(), data.getOrdinaryBattleRecovery()), data);
            if (startup.state() != KOMERecoveryStartup.State.READY) throw new AssertionError(startup.diagnostic());
            singleton.set(null, startup);
        } catch (Exception failure) { manager.close(); throw failure; }
    }
    @Override public void close() throws Exception {
        try { startup.stop(); manager.close(); } finally { singleton.set(null, previous); }
    }
}
