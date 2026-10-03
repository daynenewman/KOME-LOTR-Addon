package kome.common.data;

/** Compact generation policies shared by related Master occupations. */
public enum KOMESerfMaterialProfile {
    SMITH("smith"), MINING("mining"), BUILDING("building"), LUMBER("lumber"),
    FARMING("farming"), GROWING("growing"), BAKING("baking"), BREWING("brewing"),
    HUSBANDRY("husbandry"), FISHING("fishing"), HUNTING("hunting"),
    PRECIOUS_METAL("precious_metal"), GENERAL_TRADE("general_trade"), RITUAL("ritual"),
    SCAVENGING("scavenging"), GENERAL_LABOR("general_labor");

    public final String key;
    KOMESerfMaterialProfile(String key) { this.key=key; }
    public static KOMESerfMaterialProfile forKey(String key) {
        if(key==null)return null;
        for(KOMESerfMaterialProfile value:values())if(value.key.equals(key))return value;
        return null;
    }
}
