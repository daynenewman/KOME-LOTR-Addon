package kome.common.data;

import java.util.Random;
import lotr.common.item.LOTRItemMug;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;

/** Shared physical-goods rules; a request is multiple units, not one overfull stack. */
public final class KOMEProgressionGoodsRules {
    private KOMEProgressionGoodsRules() {}
    public static boolean consumable(Item item) {
        return item instanceof ItemFood||item instanceof LOTRItemMug
            &&item!=lotr.common.LOTRMod.mug||item==Items.milk_bucket||item==Items.potionitem;
    }
    static int quantity(int stackLimit,Random random) {
        return stackLimit<=1?1+random.nextInt(16):32+random.nextInt(33);
    }
    static int types(int stackLimit,int available,Random random) {
        if(stackLimit>1&&available<2)return 0;
        return Math.min(available,stackLimit<=1?1+random.nextInt(2):2+random.nextInt(2));
    }
    static String name(ItemStack stack,String savedName) {
        if(stack==null)return raw(savedName)?"Requested item":savedName;
        String display=stack.getDisplayName();if(!raw(display))return display;
        String translated=nativeName(String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem())),stack.getItem().getUnlocalizedName(stack));
        return translated!=null?translated:!raw(savedName)?savedName:"Requested item";
    }
    private static boolean raw(String name){return name==null||name.isEmpty()||name.startsWith("item.")||name.startsWith("tile.")||name.contains(".stackableFood")||name.contains(".unstackableFood");}
    /** Dedicated servers may not load client language packs; use the item's native English name. */
    static String nativeName(String registryKey,String unlocalized){
        String path=registryKey.substring(registryKey.lastIndexOf(':')+1);
        for(String key:new String[]{"item.lotr:"+path+".name","tile.lotr:"+path+".name","item."+path+".name","tile."+path+".name",unlocalized+".name"}){
            String value=Names.ENGLISH.get(key);if(value!=null)return value;
        }return null;
    }
    private static final class Names {
        static final java.util.Map<String,String> ENGLISH=load();
        static java.util.Map<String,String> load(){
            java.util.Map<String,String> values=new java.util.HashMap<String,String>();
            for(String path:new String[]{"/assets/minecraft/lang/en_US.lang","/assets/lotr/lang/en_US.lang"})
                try(java.io.InputStream in=KOMEProgressionGoodsRules.class.getResourceAsStream(path)){
                    if(in==null)continue;
                    try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(in,"UTF-8"))){
                        String line;while((line=reader.readLine())!=null){int eq=line.indexOf('=');if(eq>0&&!line.trim().startsWith("#"))values.put(line.substring(0,eq).trim(),line.substring(eq+1).trim());}
                    }
                }catch(java.io.IOException unreadable){throw new IllegalStateException("Native item language resource could not be read",unreadable);}
            return values;
        }
    }
}
