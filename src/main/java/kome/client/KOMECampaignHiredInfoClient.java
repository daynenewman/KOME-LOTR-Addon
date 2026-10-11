package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.*;
import java.util.function.Supplier;
import kome.common.network.KOMEPacketCampaignHiredInfo;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/** Bounded native presentation delivery; no Campaign gameplay/health authority is stored here. */
public final class KOMECampaignHiredInfoClient {
    private static final int LIMIT=128, TTL=100;
    private final Supplier<World> world;
    private final Map<UUID,Pending> pending=new LinkedHashMap<>();
    private World sessionWorld;
    public KOMECampaignHiredInfoClient(Supplier<World> world){this.world=world;}
    public void accept(KOMEPacketCampaignHiredInfo message){
        World current=world.get();
        if(current!=sessionWorld){clear();sessionWorld=current;}
        if(current==null||!current.isRemote||current.provider.dimensionId!=message.dimension
            ||message.nativeInfo.hiringPlayer==null)return;
        if(apply(current,message)){pending.remove(message.physicalId);return;}
        if(pending.containsKey(message.physicalId)||pending.size()<LIMIT)
            pending.put(message.physicalId,new Pending(message));
    }
    private boolean apply(World current,KOMEPacketCampaignHiredInfo message){
        Entity entity=current.getEntityByID(message.entityId);
        if(!(entity instanceof LOTREntityNPC)||entity.isDead||!message.physicalId.equals(entity.getUniqueID()))return false;
        ((LOTREntityNPC)entity).hiredNPCInfo.receiveBasicData(message.nativeInfo);return true;
    }
    public void tick(){
        World current=world.get();
        if(current!=sessionWorld){clear();sessionWorld=current;return;}
        if(current==null)return;
        Iterator<Pending> it=pending.values().iterator();
        while(it.hasNext()){
            Pending p=it.next();if(apply(current,p.message)||--p.remaining<=0)it.remove();
        }
    }
    @SubscribeEvent public void onTick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END)tick();}
    public void clear(){pending.clear();sessionWorld=null;}
    int pendingCount(){return pending.size();}
    private static final class Pending{
        final KOMEPacketCampaignHiredInfo message;int remaining=TTL;
        Pending(KOMEPacketCampaignHiredInfo message){this.message=message;}
    }
}
