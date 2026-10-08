package kome.common.data;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import net.minecraft.util.StatCollector;

/** Native localization first; packaged English prevents raw keys on a headless server. */
final class KOMEProgressionLanguage {
    private static final Properties ENGLISH=new Properties();
    static {
        try(InputStream in=KOMEProgressionLanguage.class.getResourceAsStream("/assets/kome/lang/en_US.lang")){
            if(in!=null)ENGLISH.load(new InputStreamReader(in,"UTF-8"));
        }catch(java.io.IOException failure){cpw.mods.fml.common.FMLLog.warning("KOME could not read progression language: %s",failure.getMessage());}
    }
    private KOMEProgressionLanguage(){}
    static String text(String key,Object... args){
        String translated=StatCollector.translateToLocalFormatted(key,args);
        if(!translated.equals(key))return translated;
        String fallback=ENGLISH.getProperty(key,"Speak with your Liege for instructions.");
        try{return String.format(java.util.Locale.ROOT,fallback,args);}catch(java.util.IllegalFormatException malformed){return "Speak with your Liege for instructions.";}
    }
}
