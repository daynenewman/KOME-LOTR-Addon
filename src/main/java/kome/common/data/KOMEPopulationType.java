package kome.common.data;

public enum KOMEPopulationType {
    OFFENSIVE("offensive"),
    DEFENSIVE("defensive");

    public final String key;

    KOMEPopulationType(String key) {
        this.key = key;
    }

    public static KOMEPopulationType forName(String name) {
        for (KOMEPopulationType type : values()) {
            if (type.key.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
