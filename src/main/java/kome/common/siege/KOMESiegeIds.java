package kome.common.siege;
import java.util.Locale;
final class KOMESiegeIds {
    private KOMESiegeIds(){}
    static String id(String value){return value==null?"":value.trim();}
    static String tile(String value){return id(value).toUpperCase(Locale.ROOT);}
}
