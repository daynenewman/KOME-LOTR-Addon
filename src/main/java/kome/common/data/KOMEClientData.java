package kome.common.data;

public class KOMEClientData extends KOMEWorldData {
    public static final KOMEClientData INSTANCE = new KOMEClientData();
    public KOMEPopulationType hireType = KOMEPopulationType.OFFENSIVE;
    public int conquestRevision;

    private KOMEClientData() {
        super("KOME_ClientData");
    }
}
