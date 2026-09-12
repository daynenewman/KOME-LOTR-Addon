package com.enovak.lotrmoremobs.siege.edit;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
/** Lifecycle cleanup for transient edit sessions only. */
public final class GateEditSessionEventHandler {
 @SubscribeEvent public void onServerTick(TickEvent.ServerTickEvent e){if(e.phase==TickEvent.Phase.END)GateEditSessionManager.tick();}
 @SubscribeEvent public void onLogout(PlayerEvent.PlayerLoggedOutEvent e){close(e.player);}
 @SubscribeEvent public void onDimension(PlayerEvent.PlayerChangedDimensionEvent e){close(e.player);}
 @SubscribeEvent public void onRespawn(PlayerEvent.PlayerRespawnEvent e){close(e.player);}
 private static void close(net.minecraft.entity.player.EntityPlayer p){if(p instanceof EntityPlayerMP){EntityPlayerMP mp=(EntityPlayerMP)p;GateEditRequestManager.clearPlayer(mp.getUniqueID());GateEditSessionManager.closeForPlayer(mp);}}
}
