package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import kome.common.network.KOMEPacketCampaignHiredInfoRequest;
import kome.common.network.KOMEPacketHandler;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;

/** Bounded spawn acknowledgments only. Native LOTR packets own all client hired-info state. */
public final class KOMECampaignHiredInfoClient {
    private static final int LIMIT=128, TTL=100, PER_TICK=8;
    private final Supplier<World> world;
    private final Consumer<KOMEPacketCampaignHiredInfoRequest> send;
    private final Consumer<Runnable> schedule;
    private final Map<Instance,Integer> pending=new LinkedHashMap<>();
    private World sessionWorld;
    public KOMECampaignHiredInfoClient(Supplier<World> world){this(world,p->KOMEPacketHandler.network.sendToServer(p));}
    public KOMECampaignHiredInfoClient(Supplier<World> world,Consumer<KOMEPacketCampaignHiredInfoRequest> send){this(world,send,Runnable::run);}
    public KOMECampaignHiredInfoClient(Supplier<World> world,Consumer<KOMEPacketCampaignHiredInfoRequest> send,Consumer<Runnable> schedule){
        this.world=world;this.send=send;this.schedule=schedule;
    }
    public void joined(Entity entity){
        World current=world.get();
        if(current!=sessionWorld){clear();sessionWorld=current;}
        if(!(entity instanceof LOTREntityNPC)||current==null||!current.isRemote||entity.worldObj!=current)return;
        Instance key=new Instance((LOTREntityNPC)entity);
        if(pending.containsKey(key)||pending.size()<LIMIT)pending.put(key,TTL);
    }
    public void tick(){
        World current=world.get();
        if(current!=sessionWorld){clear();sessionWorld=current;return;}
        if(current==null)return;
        int sent=0;
        Iterator<Map.Entry<Instance,Integer>> it=pending.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry<Instance,Integer> entry=it.next();LOTREntityNPC npc=entry.getKey().npc;
            if(npc.isDead||npc.worldObj!=current||entry.getValue()<=1){it.remove();continue;}
            if(sent<PER_TICK&&current.getEntityByID(npc.getEntityId())==npc){
                send.accept(new KOMEPacketCampaignHiredInfoRequest(current.provider.dimensionId,npc.getEntityId()));
                sent++;it.remove();
            } else entry.setValue(entry.getValue()-1);
        }
    }
    @SubscribeEvent public void onJoined(EntityJoinWorldEvent event){
        if(event.world.isRemote)try{schedule.accept(()->joined(event.entity));}
        catch(java.util.concurrent.RejectedExecutionException disconnectedOrFull){ }
    }
    @SubscribeEvent public void onTick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END)tick();}
    @SubscribeEvent public void onUnload(WorldEvent.Unload event){if(event.world==sessionWorld)clear();}
    public void clear(){pending.clear();sessionWorld=null;}
    int pendingCount(){return pending.size();}
    // Native spawn changes Entity.entityId, which also changes Entity.equals/hashCode.
    private static final class Instance {
        final LOTREntityNPC npc;Instance(LOTREntityNPC npc){this.npc=npc;}
        @Override public int hashCode(){return System.identityHashCode(npc);}
        @Override public boolean equals(Object other){return other instanceof Instance&&((Instance)other).npc==npc;}
    }
}
