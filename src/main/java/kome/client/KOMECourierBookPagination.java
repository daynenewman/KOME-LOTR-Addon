package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import kome.common.data.KOMECourierCorrespondence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.client.event.GuiOpenEvent;

/** Retains the native lore-book GUI and page-turn buttons; only its page copy changes. */
public final class KOMECourierBookPagination {
    @SubscribeEvent public void open(GuiOpenEvent event) {
        if(!(event.gui instanceof GuiScreenBook))return;
        Minecraft mc=Minecraft.getMinecraft();if(mc.thePlayer==null)return;
        ItemStack book=mc.thePlayer.getHeldItem();
        if(book==null||!book.hasTagCompound()||!book.getTagCompound().hasKey("KOMECourier",10))return;
        NBTTagCompound hidden=book.getTagCompound().getCompoundTag("KOMECourier");
        if(!hidden.hasKey("Correspondence",8))return; // Legacy native books remain readable unchanged.
        ItemStack view=book.copy();NBTTagList pages=new NBTTagList();
        for(String page:KOMECourierCorrespondence.pages(hidden.getString("Correspondence"),new KOMECourierCorrespondence.Width(){public int pixels(String text){return Minecraft.getMinecraft().fontRenderer.getStringWidth(text);}}))pages.appendTag(new NBTTagString(page));
        view.getTagCompound().setTag("pages",pages);
        event.gui=new GuiScreenBook(mc.thePlayer,view,false);
    }
}
