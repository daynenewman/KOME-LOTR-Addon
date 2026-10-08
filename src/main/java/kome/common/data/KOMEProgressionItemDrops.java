package kome.common.data;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** Only deliberate progression issuance calls this helper; never general loot. */
public final class KOMEProgressionItemDrops {
    private KOMEProgressionItemDrops() {}
    public static EntityItem drop(World world,double x,double y,double z,ItemStack stack) {
        if(world==null||world.isRemote||stack==null||stack.getItem()==null)return null;
        EntityItem item=new EntityItem(world,x,y,z,stack.copy());item.delayBeforeCanPickup=10;
        return spawn(world,item)?item:null;
    }
    static boolean spawn(World world,EntityItem item) {
        if(world==null||world.isRemote||item==null||!world.spawnEntityInWorld(item))return false;
        world.playSoundAtEntity(item,"random.pop",0.4F,1F);return true;
    }
}
