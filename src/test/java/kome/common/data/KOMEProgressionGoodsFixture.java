package kome.common.data;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.item.*;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

/** Real nonfood/food roles and native registry keys, unlike the generic access fixture's placeholders. */
final class KOMEProgressionGoodsFixture implements AutoCloseable {
    private final Map<Field,Object> previous=new LinkedHashMap<Field,Object>();
    KOMEProgressionGoodsFixture()throws Exception{
        new kome.common.KOMEAccessFixture();int id=24000;
        Method raw=Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class);raw.setAccessible(true);
        for(String key:new String[]{"coal","iron_ingot","gold_ingot","gold_nugget","leather","string","flint","brick","clay_ball","stick","wheat","wheat_seeds","lead","sugar","egg","water_bucket","arrow","feather","bone","glass_bottle","reeds","bowl"}){
            Field field=net.minecraft.init.Items.class.getField(key);previous.put(field,field.get(null));Item item=new Item();raw.invoke(Item.itemRegistry,id++,"minecraft:"+key,item);set(field,item);
        }
        for(String key:new String[]{"cobblestone","stone","stonebrick","sandstone","planks","log","sapling","wool","hay_block","red_flower","yellow_flower","red_mushroom","brown_mushroom","iron_ore","gold_ore"}){
            Field field=net.minecraft.init.Blocks.class.getField(key);previous.put(field,field.get(null));Block block=field.getType()==Block.class?new Block(Material.wood){}:(Block)kome.common.KOMEAccessFixture.allocate(field.getType());
            Method blocks=Block.blockRegistry.getClass().getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class);blocks.setAccessible(true);int blockId=2200+previous.size();blocks.invoke(Block.blockRegistry,blockId,"minecraft:"+key,block);raw.invoke(Item.itemRegistry,blockId,"minecraft:"+key,new ItemBlock(block));set(field,block);
        }
    }
    private static void set(Field field,Object value)throws Exception{Field mods=Field.class.getDeclaredField("modifiers");mods.setAccessible(true);field.setAccessible(true);mods.setInt(field,field.getModifiers()&~Modifier.FINAL);field.set(null,value);}
    public void close()throws Exception{for(Map.Entry<Field,Object> e:previous.entrySet())set(e.getKey(),e.getValue());}
}
