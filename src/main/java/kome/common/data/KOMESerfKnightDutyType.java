package kome.common.data;

public enum KOMESerfKnightDutyType {
    PROVISIONING("provisioning", "Provisioning"), PROFESSION("profession", "Trade Assistance"), COURIER("courier", "Courier"),
    ESCORT("escort", "Local Escort"), RECOVERY("recovery", "Property Recovery"), DEFENSE("defense", "Local Defense");
    public final String key, displayName;
    KOMESerfKnightDutyType(String key, String displayName) { this.key = key; this.displayName=displayName; }
    public static KOMESerfKnightDutyType forKey(String key) {
        if (key == null) return null;
        for (KOMESerfKnightDutyType type : values()) if (type.key.equalsIgnoreCase(key)) return type;
        return null;
    }
}
