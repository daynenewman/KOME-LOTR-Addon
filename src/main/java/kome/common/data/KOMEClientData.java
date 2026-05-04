package kome.common.data;

public class KOMEClientData extends KOMEWorldData {
    public static final KOMEClientData INSTANCE = new KOMEClientData();

    private KOMEClientData() {
        super("KOME_ClientData");
    }
}
