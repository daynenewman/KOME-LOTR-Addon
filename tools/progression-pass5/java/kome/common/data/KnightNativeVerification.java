package kome.common.data;

import com.mojang.authlib.GameProfile;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.*;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import static kome.common.data.KOMEKnightCommission.*;

/** Controlled real-Forge native mechanics fixture. Not a client, natural-world journey, or earned promotion. */
@Mod(modid="knightfieldprobe",name="Disposable Knight field native verification",version="1",dependencies="required-after:kome")
public final class KnightNativeVerification {
    private static final class ProbePlayer extends FakePlayer {
        ProbePlayer(WorldServer world,GameProfile profile){super(world,profile);
            net.minecraft.network.NetworkManager network=new net.minecraft.network.NetworkManager(false);
            new io.netty.channel.embedded.EmbeddedChannel(network); // Local sink; no client/handshake dispatcher.
            new net.minecraft.network.NetHandlerPlayServer(MinecraftServer.getServer(),network,this){
                @Override public void sendPacket(net.minecraft.network.Packet packet){}
            };
        }
        @Override public void addChatMessage(net.minecraft.util.IChatComponent message){} // No connected client transport in this dedicated fixture.
    }
    private final StringBuilder evidence=new StringBuilder();private int checks;
    private void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);checks++;evidence.append("OK ").append(message).append('\n');}
    private static int experience(LOTREntityNPC npc,EntityPlayer player)throws Exception{
        for(Class<?> c=npc.getClass();c!=null;c=c.getSuperclass())for(String name:new String[]{"getExperiencePoints","func_70693_a"})try{Method m=c.getDeclaredMethod(name,EntityPlayer.class);m.setAccessible(true);return ((Number)m.invoke(npc,player)).intValue();}catch(NoSuchMethodException absent){}
        throw new IllegalStateException("Native XP method missing");
    }
    @Mod.EventHandler public void verify(FMLServerStartedEvent event)throws Exception{
        try{
            WorldServer world=MinecraftServer.getServer().worldServerForDimension(0);
            for(int cx=-2;cx<=2;cx++)for(int cz=-2;cz<=2;cz++)world.getChunkFromChunkCoords(cx,cz);
            for(int x=0;x<24;x++)for(int z=0;z<24;z++){world.setBlock(x,99,z,Blocks.stone);for(int y=100;y<104;y++)world.setBlockToAir(x,y,z);}
            FakePlayer player=new ProbePlayer(world,new GameProfile(UUID.randomUUID(),"Pass5Knight"));player.setPosition(8.5,100,6.5);player.setSneaking(true);
            world.playerEntities.add(player); // Supplies ordinary world UUID lookup without a connected network client.
            LOTRLevelData.getData(player).setPledgeFaction(LOTRFaction.ROHAN);
            LOTREntityRohirrimMarshal liege=new LOTREntityRohirrimMarshal(world);liege.setPosition(16.5,100,16.5);
            KOMEWorldData data=KOMEWorldData.get(world);KOMEPlayerProgression p=data.getProgression(player.getUniqueID());p.setCanonicalRank(KOMEProgressionRank.KNIGHT);p.getSerfKnightProgression().setLiege(KOMEProgressionNpcRankService.referenceOf(liege));
            LOTREntityMordorOrcMercenaryCaptain officer=new LOTREntityMordorOrcMercenaryCaptain(world);officer.setLocationAndAngles(8.5,100,8.5,0,0);officer.onSpawnWithEgg(null);officer.onArtificalSpawn();officer.setAttackTarget(null);officer.rotationYaw=officer.rotationYawHead=0;
            KOMEKnightCommission a=new KOMEKnightCommission(Type.WAR_PLANS,p.getSerfKnightProgression().getLiege());KOMEKnightFieldService.profile(a,3);a.complication=Complication.NONE;a.stage=Stage.ACTIVE;a.encounterCreated=true;a.x=8.5;a.y=100;a.z=8.5;a.enemyFaction="mordor";a.targetId=officer.getUniqueID().toString();a.adminForced=true;a.rewardReserved=true;
            a.actors.add(new Actor(a.targetId,officer.getClass().getName(),Role.ENEMY,8.5,100,8.5));check(p.getKnightService().offer(a),"one canonical controlled test assignment");p.getKnightService().testing(true);
            KOMEProgressionEncounterMarker.mark(officer,KOMEKnightCommissionService.MARKER,player.getUniqueID(),a.token);KOMEProgressionEncounterMarker.markTesting(officer);KOMEProgressionNpcRoles.syncPlayer(data,player.getUniqueID());
            check(world.spawnEntityInWorld(officer),"real native captain enters real Forge world with saved ownership");
            check(KOMEKnightEspionage.validTarget(player,officer,a),"native target passes server ownership and exact actor authentication");
            officer.setAttackTarget(player);check(officer.getAttackTarget()==null&&!a.detected,"assigned crouching player can approach behind native officer without omnidirectional target acquisition");
            officer.getRNG().setSeed(0); // Deterministic native success roll; native approach/witness algorithm still executes.
            check(KOMEKnightEspionage.interact(player,officer),"actual transformed v36.15 onInteractOther is invoked");
            ItemStack dispatch=player.getCurrentEquippedItem();check(a.dispatchStolen&&KOMEKnightEspionage.matches(dispatch,a,player.getUniqueID()),"native theft creates authenticated sealed dispatch with native pickpocket metadata");
            check(dispatch.getTagCompound().hasKey("LOTRPickpocket",10),"native helper, inventory placement, sounds and particle path executed");
            check(!KOMEKnightEspionage.validTarget(player,officer,a),"native target cannot mint a second dispatch");
            check(experience(officer,player)==0&&officer.getAlignmentBonus()==0,"staff actor returns no native XP or alignment reward in actual classloader");
            check(!KOMEServicePromotionService.reason(p,a.liege,"rohan",2000).isEmpty(),"test session cannot confer earned Lord standing");
            LOTREntityRohirrimWarrior guard=new LOTREntityRohirrimWarrior(world);guard.onSpawnWithEgg(null);guard.onArtificalSpawn();guard.setPosition(12.5,100,12.5);KOMEKnightFieldService.follow(guard,player);KOMEProgressionEncounterMarker.mark(guard,KOMEKnightCommissionService.MARKER,player.getUniqueID(),a.token);
            NBTTagCompound saved=new NBTTagCompound();guard.writeToNBT(saved);LOTREntityRohirrimWarrior loaded=new LOTREntityRohirrimWarrior(world);loaded.readFromNBT(saved);
            check(loaded.getUniqueID().equals(guard.getUniqueID())&&loaded.hiredNPCInfo.isActive&&player.getUniqueID().equals(loaded.hiredNPCInfo.getHiringPlayerUUID()),"real native follower UUID and hiring identity survive full entity NBT");
            check(!loaded.hiredNPCInfo.teleportAutomatically&&KOMEProgressionEncounterMarker.read(loaded).token.equals(a.token),"native no-teleport flag and encounter provenance survive entity reload");
            lotr.common.entity.LOTREntityInvasionSpawner controller=new lotr.common.entity.LOTREntityInvasionSpawner(world);
            check(!KOMEObjectiveInvasionBridge.endTest(controller,true),"transformed native invasion class verifies and unowned outcome cannot use test bypass");
            KOMEKnightCommission roundTrip=KOMEKnightCommission.readFromNBT(a.writeToNBT());check(roundTrip.dispatchStolen&&roundTrip.targetId.equals(a.targetId)&&roundTrip.tier==3,"commission objective and exact target persist alongside native actor");
            KOMEKnightCommissionService.cleanup(world,player.getUniqueID(),a);check(officer.isDead&&player.getCurrentEquippedItem()==null,"cleanup removes the owned officer and sealed dispatch");
            check(p.getKnightService().qualifyingTypes("rohan").isEmpty()&&data.hiredUnits.isEmpty(),"native controlled fixture earns no canonical service or strategic soldier");
            evidence.append("PASS checks=").append(checks).append("; controlled dedicated Forge fixture; no client or natural-world gameplay acceptance\n");
        }catch(Throwable failure){evidence.append("FAIL ").append(failure).append('\n');throw new RuntimeException("Native Knight verification failed",failure);}
        finally{Files.write(Paths.get("knight-native.txt"),evidence.toString().getBytes(StandardCharsets.UTF_8));MinecraftServer.getServer().initiateShutdown();}
    }
}
