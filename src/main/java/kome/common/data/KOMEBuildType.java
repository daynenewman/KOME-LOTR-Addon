package kome.common.data;

/** Canonical Build classification; split population types are not Build types. */
public enum KOMEBuildType {
    NORMAL("NORMAL"),
    DEFENSIVE("DEFENSIVE");

    public final String key;

    KOMEBuildType(String key) { this.key = key; }

    public static KOMEBuildType forKey(String value) {
        for (KOMEBuildType type : values()) if (type.key.equals(value)) return type;
        throw new IllegalArgumentException("Invalid Build type: " + value);
    }
}
