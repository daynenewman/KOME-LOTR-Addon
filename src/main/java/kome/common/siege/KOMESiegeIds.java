package kome.common.siege;
import java.util.Locale;
final class KOMESiegeIds {
    private KOMESiegeIds(){}
    static String id(String value){return value==null?"":value.trim();}
    /** Case-insensitive complex identity canonicalized to uppercase with Locale.ROOT; never derived from a tile or generated on read. */
    static String complex(String value){
        String key=id(value);
        if(key.length()==0)throw new IllegalArgumentException("Siege Complex ID is required.");
        return key.toUpperCase(Locale.ROOT);
    }
    static String tile(String value){return id(value).toUpperCase(Locale.ROOT);}
}
