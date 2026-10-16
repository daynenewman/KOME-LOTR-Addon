package kome.client;

/** One line per Join result transition, never per render/tick and never player-facing. */
public final class KOMEJoinBattleDiagnostics {
    private static final org.apache.logging.log4j.Logger LOG=org.apache.logging.log4j.LogManager.getLogger("KOME");
    private KOMEJoinBattleDiagnostics() { }
    public static void event(String transition){LOG.info("[Join UI] {}",transition);}
}