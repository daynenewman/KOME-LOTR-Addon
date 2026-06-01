package kome.common.data;

public class KOMEClientData extends KOMEWorldData {
    public static final KOMEClientData INSTANCE = new KOMEClientData();
    public KOMEPopulationType hireType = KOMEPopulationType.OFFENSIVE;
    public final java.util.Map<String, KOMETileTroopSummary> troopSummaries = new java.util.HashMap<String, KOMETileTroopSummary>();
    public int conquestRevision;

    private KOMEClientData() {
        super("KOME_ClientData");
    }
}
