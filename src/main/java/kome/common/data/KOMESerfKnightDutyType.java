package kome.common.data;

public enum KOMESerfKnightDutyType {
    PROVISIONING("provisioning"), PROFESSION("profession"), COURIER("courier");
    public final String key;
    KOMESerfKnightDutyType(String key) { this.key = key; }
    public static KOMESerfKnightDutyType forKey(String key) {
        if (key == null) return null;
        for (KOMESerfKnightDutyType type : values()) if (type.key.equalsIgnoreCase(key)) return type;
        return null;
    }
}
