package kome.client;

import java.util.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/** Called after the native visibility/range checks, for world lettering only. */
public final class KOMEPlayerNameplate {
    private static final Map<UUID,String> SUFFIXES=new HashMap<UUID,String>();
    public static void update(Map<UUID,String> values){SUFFIXES.clear();SUFFIXES.putAll(values);}
    public static void clear(){SUFFIXES.clear();}
    public static String format(String nativeName,EntityLivingBase entity){
        if(!(entity instanceof EntityPlayer)||nativeName==null||nativeName.isEmpty())return nativeName;
        String suffix=SUFFIXES.get(entity.getUniqueID());
        return suffix==null?nativeName:nativeName+net.minecraft.util.EnumChatFormatting.RESET+suffix;
    }
}
