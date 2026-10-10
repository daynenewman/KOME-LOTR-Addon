package kome.common.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Curated equipment shared with the existing faction gift pool, never invented artifacts. */
public final class KOMEProgressionLostItems {
    private KOMEProgressionLostItems() {}
    static ItemStack choose(String faction,Random random){
        List<Item> pool=new ArrayList<Item>();
        for(Item item:KOMEPartingGiftService.equipment(faction))if(item!=null
                &&Item.itemRegistry.getNameForObject(item)!=null&&!pool.contains(item))pool.add(item);
        if(lotr.common.LOTRMod.silver!=null&&Item.itemRegistry.getNameForObject(lotr.common.LOTRMod.silver)!=null)pool.add(lotr.common.LOTRMod.silver);
        return pool.isEmpty()?null:new ItemStack(pool.get(random.nextInt(pool.size())),1);
    }
    static String name(KOMEKnightCommission a){
        if(a.goods.isEmpty())return "the stolen property";
        KOMEKnightCommission.Goods g=a.goods.get(0);Item item=(Item)Item.itemRegistry.getObject(g.itemKey);
        return item==null?"the stolen property":new ItemStack(item,1,g.damage).getDisplayName();
    }
}
