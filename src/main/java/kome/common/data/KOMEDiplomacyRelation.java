package kome.common.data;
public enum KOMEDiplomacyRelation {
    NEUTRAL("neutral", "Neutral", 0), FRIENDS("friends", "Friends", 1), ALLIES("allies", "Allies", 2);
    public final String key, displayName; private final int rank;
    KOMEDiplomacyRelation(String key,String displayName,int rank){this.key=key;this.displayName=displayName;this.rank=rank;}
    public int rank(){return rank;}
    public static KOMEDiplomacyRelation parse(String value){if(value==null)throw new IllegalArgumentException("Diplomacy relation is required");for(KOMEDiplomacyRelation r:values())if(r.key.equals(value.trim().toLowerCase(java.util.Locale.ROOT)))return r;throw new IllegalArgumentException("Unknown diplomacy relation: "+value);}
}
