package kome.common.item;
import java.util.List;import net.minecraft.entity.player.EntityPlayer;import net.minecraft.item.Item;import net.minecraft.item.ItemStack;
public class KOMEItemSealedMessage extends Item { public KOMEItemSealedMessage(){setUnlocalizedName("kome.sealedMessage");setMaxStackSize(1);} public void addInformation(ItemStack s,EntityPlayer p,List l,boolean a){if(s.hasTagCompound())l.add("Sealed message from "+s.getTagCompound().getString("MasterName"));} }
