package kome.common.item;
import java.util.List;import net.minecraft.entity.player.EntityPlayer;import net.minecraft.item.Item;import net.minecraft.item.ItemStack;
public class KOMEItemSealedParcel extends Item { public KOMEItemSealedParcel(){setUnlocalizedName("kome.sealedParcel");setMaxStackSize(1);} public void addInformation(ItemStack s,EntityPlayer p,List l,boolean a){if(s.hasTagCompound()){l.add("From: "+s.getTagCompound().getString("MasterName"));l.add("Contents:");for(String x:s.getTagCompound().getString("Cargo").split("\\|"))if(x.length()>0)l.add(x);}} }
