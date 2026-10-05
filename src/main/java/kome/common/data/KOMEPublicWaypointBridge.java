package kome.common.data;
import java.util.*;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import kome.common.KOMEReflection;
import kome.common.network.KOMEPacketHandler;
import lotr.common.*;
import lotr.common.world.map.*;
import lotr.common.network.LOTRPacketFastTravel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

/** Presentation hooks and server-queued native/public travel; public policy uses only the reserved namespace. */
public final class KOMEPublicWaypointBridge {
    private KOMEPublicWaypointBridge(){ }
    // Re-enter the transformed original handler only on the executing server thread.
    private static final ThreadLocal<MessageContext> NATIVE_REQUEST_REPLAY=new ThreadLocal<MessageContext>();
    public static List<LOTRAbstractWaypoint> compose(List<LOTRAbstractWaypoint> nativePoints,LOTRPlayerData data) {
        if(!LOTRMod.proxy.isClient()) return nativePoints;
        return compose(nativePoints,data.getPlayerUUID(),KOMEPublicWaypointClientState.INSTANCE.snapshot());
    }
    public static List<LOTRAbstractWaypoint> compose(List<LOTRAbstractWaypoint> nativePoints,UUID playerId,KOMEPublicWaypointSnapshot snapshot) {
        Set<String> associated=new HashSet<String>();
        for(KOMEPublicWaypointRegistry.View view:snapshot.entries) if(view.record.source==KOMEPublicWaypoint.Source.NATIVE) associated.add(view.record.sourceKey);
        List<LOTRAbstractWaypoint> result=new ArrayList<LOTRAbstractWaypoint>();
        for(LOTRAbstractWaypoint point:nativePoints) {
            if(point instanceof KOMEPublicWaypointAdapter) continue;
            if(point instanceof LOTRWaypoint && associated.contains(point.getCodeName())) continue;
            String legacy=legacyIdentity(point,playerId);
            if(legacy!=null && snapshot.cutover.containsKey(legacy)) continue;
            result.add(point);
        }
        for(KOMEPublicWaypointRegistry.View view:snapshot.entries) result.add(new KOMEPublicWaypointAdapter(view));
        return result;
    }
    public static String legacyIdentity(LOTRAbstractWaypoint point,UUID playerId) {
        if(!(point instanceof LOTRCustomWaypoint) || point instanceof KOMEPublicWaypointAdapter || point.getID()<0) return null;
        LOTRCustomWaypoint custom=(LOTRCustomWaypoint)point;
        UUID owner=custom.getSharingPlayerID(); if(owner==null) owner=playerId;
        return owner==null?null:owner+":"+point.getID();
    }
    /** Client-only namespace lookup covers LOTR's completion screen/use-count packets, without allowing server hide/unlock writes. */
    public static boolean isPublicOwner(UUID owner){ return KOMEPublicWaypointAdapter.NAMESPACE.equals(owner); }
    public static LOTRCustomWaypoint lookup(LOTRPlayerData data,UUID owner,int wire) {
        if(!KOMEPublicWaypointAdapter.NAMESPACE.equals(owner) || !LOTRMod.proxy.isClient()) return null;
        KOMEPublicWaypointRegistry.View view=KOMEPublicWaypointClientState.INSTANCE.snapshot().byWire(wire);
        return view==null?null:new KOMEPublicWaypointAdapter(view);
    }
    public static KOMEPublicWaypointRegistry.View current(EntityPlayer player,UUID id) {
        if(player==null) return null;
        if(KOMEReflection.isRemote(player.worldObj)) return KOMEPublicWaypointClientState.INSTANCE.snapshot().byId(id);
        KOMEWorldData data=KOMEWorldData.get(player.worldObj); return data.publicWaypoints.view(data,id);
    }
    public static boolean sameDestination(KOMEPublicWaypoint before,KOMEPublicWaypoint after) {
        return before.id.equals(after.id) && before.wireId==after.wireId && before.tileId.equals(after.tileId)
            && before.dimension==after.dimension && before.x==after.x && before.y==after.y && before.z==after.z
            && before.source==after.source && before.sourceKey.equals(after.sourceKey);
    }
    /** Capture immutable intent on the network thread; all approval/policy/native mutations run at server tick START. */
    public static boolean handleRequest(LOTRPacketFastTravel packet,MessageContext context) {
        if(NATIVE_REQUEST_REPLAY.get()==context && context!=null) return false;
        // The deployed v36.15 jar keeps packet fields private. Use its exact public wire API.
        io.netty.buffer.ByteBuf intent=io.netty.buffer.Unpooled.buffer(22,22);
        final int wire; final boolean custom; final UUID owner;
        try {
            packet.toBytes(intent); custom=intent.readBoolean(); wire=intent.readInt();
            owner=intent.readBoolean()?new UUID(intent.readLong(),intent.readLong()):null;
            if(intent.isReadable()) throw new IllegalArgumentException("Unexpected native travel wire shape");
        } finally { intent.release(); }
        final KOMEPacketHandler.Requester requester=KOMEPacketHandler.captureRequester(context);
        if(requester==null) return true;
        final EntityPlayerMP player=requester.player;
        KOMEPacketHandler.enqueueServerTask(requester,()->{
            if(!requester.isCurrent()) return;
            if(!KOMEPublicWaypointAdapter.NAMESPACE.equals(owner)) {
                // Preserve the exact native body and its existing KOME guard, with immutable wire intent.
                io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer(22,22);
                LOTRPacketFastTravel nativeIntent=new LOTRPacketFastTravel();
                try {
                    bytes.writeBoolean(custom);bytes.writeInt(wire);bytes.writeBoolean(owner!=null);
                    if(owner!=null){bytes.writeLong(owner.getMostSignificantBits());bytes.writeLong(owner.getLeastSignificantBits());}
                    nativeIntent.fromBytes(bytes);
                } finally {bytes.release();}
                NATIVE_REQUEST_REPLAY.set(context);
                try {new LOTRPacketFastTravel.Handler().onMessage(nativeIntent,context);}
                finally {NATIVE_REQUEST_REPLAY.remove();}
                return;
            }
            if(!custom || wire>=0) { deny(player,"Invalid public waypoint identity."); return; }
            KOMEWorldData data=KOMEWorldData.get(player.worldObj);
            KOMEPublicWaypointRegistry.View view=null;
            for(KOMEPublicWaypointRegistry.View candidate:data.publicWaypoints.views(data)) if(candidate.record.wireId==wire) { view=candidate; break; }
            if(view==null) { deny(player,"Public waypoint is no longer approved/available."); return; }
            KOMEPublicWaypointAdapter target=new KOMEPublicWaypointAdapter(view);
            if(!travelConditions(player,target) || !KOMEWaypointAccessService.allowNativeRequest(player,target)) return;
            LOTRLevelData.getData(player).setTargetFTWaypoint(target);
        });
        return true;
    }
    /** Keep the native completion path and final guard, but execute completion on the authoritative server tick. */
    public static boolean handleBounce(MessageContext context) {
        final KOMEPacketHandler.Requester requester=KOMEPacketHandler.captureRequester(context);
        if(requester==null) return true;
        final EntityPlayerMP player=requester.player;
        KOMEPacketHandler.enqueueServerTask(requester,()->{
            if(!requester.isCurrent()) return;
            LOTRLevelData.getData(player).receiveFTBouncePacket();
        });
        return true;
    }
    public static boolean travelConditions(EntityPlayer player,KOMEPublicWaypointAdapter target) {
        LOTRPlayerData nativeData=LOTRLevelData.getData(player);
        String reason=!LOTRConfig.enableFastTravel?"Fast travel is disabled."
            :player.dimension!=target.view.record.dimension?"Public destinations require the configured destination dimension."
            :nativeData.getTimeSinceFT()<nativeData.getWaypointFTTime(target,player)?"Native fast-travel cooldown has not elapsed."
            :!nativeData.canFastTravel()?"Native combat restrictions prevent travel."
            :player.isPlayerSleeping()?"Cannot fast travel while sleeping.":null;
        if(reason!=null) { deny(player,reason); return false; } return true;
    }
    private static void deny(EntityPlayer player,String reason){ player.addChatMessage(new ChatComponentText("Fast travel denied: "+reason)); }
}
