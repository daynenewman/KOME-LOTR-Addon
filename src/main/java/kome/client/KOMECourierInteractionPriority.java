package kome.client;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import kome.common.data.KOMEVisualMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** C02 entity interaction has already been sent; suppress the fallback C08/book GUI. */
public final class KOMECourierInteractionPriority {
    static boolean deliveryTarget(ItemStack held,String id,int dimension) {
        if(held==null||held.getItem()!=net.minecraft.init.Items.written_book||!held.hasTagCompound()||!held.getTagCompound().hasKey("KOMECourier",10))return false;
        for(KOMEVisualMarker marker:KOMEVisualMarkerClientState.markers())
            if(marker.role==KOMEVisualMarker.Role.COURIER&&marker.dimension==dimension&&marker.entityUuid.equals(id))return true;
        return false;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void onEntity(net.minecraftforge.event.entity.player.EntityInteractEvent event){
        // Vanilla sends C02 before posting this event. Cancelling avoids a client native offer GUI.
        Minecraft mc=Minecraft.getMinecraft();
        if(event.entityPlayer==mc.thePlayer&&event.target!=null
            &&deliveryTarget(event.entityPlayer.getHeldItem(),event.target.getUniqueID().toString(),event.entityPlayer.dimension))
            event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent event) {
        Minecraft mc=Minecraft.getMinecraft();
        if(event.action!=PlayerInteractEvent.Action.RIGHT_CLICK_AIR||event.entityPlayer!=mc.thePlayer
            ||mc.objectMouseOver==null)return;
        Entity target=mc.objectMouseOver.entityHit;
        if(target!=null&&deliveryTarget(mc.thePlayer.getHeldItem(),target.getUniqueID().toString(),mc.thePlayer.dimension))
            event.setCanceled(true);
    }
}
