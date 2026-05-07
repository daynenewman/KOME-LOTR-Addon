package kome.common.data;

public class KOMEClientData extends KOMEWorldData {
    public static final KOMEClientData INSTANCE = new KOMEClientData();
    public KOMEPopulationType hireType = KOMEPopulationType.OFFENSIVE;

    private KOMEClientData() {
        super("KOME_ClientData");
    }
}
