package kome.common.data;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.passive.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import lotr.common.LOTRMod;
import lotr.common.entity.npc.*;
import lotr.common.item.LOTRItemMug;

/** Real Forge/LOTR bootstrap and native AI execution; only a newly generated disposable world. */
@Mod(modid="progressiontestingprobe",name="Disposable progression native verification",version="1",dependencies="required-after:kome")
public final class ProgressionNativeVerification {
    private final StringBuilder evidence=new StringBuilder();
    private int checks;
    private void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);checks++;evidence.append("OK ").append(message).append('\n');}
    private static void set(Object target,String name,int value)throws Exception{Field f=KOMELivestockCropAI.class.getDeclaredField(name);f.setAccessible(true);f.setInt(target,value);}
    private static KOMELivestockCropAI task(EntityAnimal animal){for(Object entry:animal.tasks.taskEntries){EntityAIBase task=((EntityAITasks.EntityAITaskEntry)entry).action;if(task instanceof KOMELivestockCropAI)return (KOMELivestockCropAI)task;}throw new IllegalStateException("Missing livestock AI");}
    private static void eat(KOMELivestockCropAI ai){ai.startExecuting();for(int i=0;i<60;i++)ai.updateTask();ai.resetTask();}
    private static final class AlignmentData extends lotr.common.LOTRPlayerData {
        final java.util.Map<lotr.common.fac.LOTRFaction,Float> values=new java.util.EnumMap<lotr.common.fac.LOTRFaction,Float>(lotr.common.fac.LOTRFaction.class);
        final java.util.Map<lotr.common.fac.LOTRFaction,Integer> writes=new java.util.EnumMap<lotr.common.fac.LOTRFaction,Integer>(lotr.common.fac.LOTRFaction.class);
        AlignmentData(){super(java.util.UUID.randomUUID());}
        @Override public float getAlignment(lotr.common.fac.LOTRFaction f){return values.containsKey(f)?values.get(f):7F;}
        @Override public void setAlignment(lotr.common.fac.LOTRFaction f,float value){values.put(f,value);writes.put(f,writes.containsKey(f)?writes.get(f)+1:1);}
    }
    @Mod.EventHandler public void verify(FMLServerStartedEvent event)throws Exception{
        try{
            World world=MinecraftServer.getServer().worldServerForDimension(0);for(int cx=-2;cx<=2;cx++)for(int cz=-2;cz<=2;cz++)world.getChunkFromChunkCoords(cx,cz);
            int x=4,y=100,z=4;world.setBlock(x,y-1,z,Blocks.farmland);world.setBlockToAir(x,y+1,z);
            EntityCow cow=new EntityCow(world);cow.setPosition(x+0.5,y,z+0.5);KOMELivestockCropAI.install(cow);KOMELivestockCropAI.install(cow);
            int count=0;for(Object entry:cow.tasks.taskEntries)if(((EntityAITasks.EntityAITaskEntry)entry).action instanceof KOMELivestockCropAI)count++;
            check(count==1,"one livestock AI after repeated installation");
            KOMELivestockCropAI ai=task(cow);set(ai,"x",x);set(ai,"y",y);set(ai,"z",z);
            world.getGameRules().setOrCreateGameRule("mobGriefing","true");
            world.setBlock(x,y,z,Blocks.wheat,7,3);eat(ai);check(world.isAirBlock(x,y,z),"native mature wheat consumed at real animal position");
            world.setBlock(x,y,z,Blocks.wheat,6,3);eat(ai);check(world.getBlock(x,y,z)==Blocks.wheat&&world.getBlockMetadata(x,y,z)==6,"immature wheat preserved");
            world.setBlock(x,y,z,LOTRMod.lettuceCrop,7,3);eat(ai);check(world.isAirBlock(x,y,z),"native mature lettuce consumed");
            world.setBlock(x,y,z,Blocks.planks,0,3);eat(ai);check(world.getBlock(x,y,z)==Blocks.planks,"structure block preserved");
            world.setBlock(x,y,z,Blocks.wheat,7,3);world.getGameRules().setOrCreateGameRule("mobGriefing","false");eat(ai);check(world.getBlock(x,y,z)==Blocks.wheat,"mobGriefing false prevents consumption");world.getGameRules().setOrCreateGameRule("mobGriefing","true");
            check(!KOMELivestockCropAI.eligible(EntityWolf.class)&&!KOMELivestockCropAI.eligible(EntityHorse.class),"unrelated animals excluded");
            LOTREntityRohanFarmhand worker=new LOTREntityRohanFarmhand(world);worker.setPosition(x+1.5,y,z+0.5);KOMELivestockCropAI.install(worker);check(world.spawnEntityInWorld(cow),"offending animal inserted into native chunk entity index");
            KOMELivestockCropAI.Deterrence deter=null;for(Object entry:worker.tasks.taskEntries)if(((EntityAITasks.EntityAITaskEntry)entry).action instanceof KOMELivestockCropAI.Deterrence)deter=(KOMELivestockCropAI.Deterrence)((EntityAITasks.EntityAITaskEntry)entry).action;
            check(deter!=null,"native agricultural worker receives deterrence task");
            ai.startExecuting();check(deter.shouldExecute(),"worker reacts to active nearby crop attempt");deter.startExecuting();float health=cow.getHealth();deter.updateTask();
            check(KOMELivestockCropAI.offender(cow)==null,"deterrence stops crop attempt");check(cow.getHealth()==health,"deterrence is nonlethal");check(!deter.continueExecuting(),"worker stops pursuit after deterrence");deter.resetTask();world.removeEntity(cow);
            LOTREntityDunlendingWarlord noble=new LOTREntityDunlendingWarlord(world);
            KOMEProgressionNpcRankRecord rank=new KOMEProgressionNpcRankRecord(noble.getUniqueID(),"dunland",KOMEProgressionNpcRank.PRINCE,"Gair, the Grey");KOMEProgressionNativeAuthority.applyName(noble,rank);
            check(noble.getCommandSenderName().equals(KOMEProgressionNativeAuthority.name("dunland",rank.rank,rank.displayName)),"transformed native NPC name avoids occupation duplication and preserves epithet");
            ItemStack drink=new ItemStack(LOTRMod.mugAle);LOTRItemMug.setVessel(drink,LOTRItemMug.Vessel.BOTTLE,true);LOTRItemMug.setStrengthMeta(drink,3);
            String key=String.valueOf(Item.itemRegistry.getNameForObject(drink.getItem()));KOMESerfProvisioningAssignment.Requirement r=new KOMESerfProvisioningAssignment.Requirement(key,Item.getIdFromItem(drink.getItem()),0,3,0,drink.getDisplayName(),"BOTTLE",true,3);
            check(r.progressText().contains("Bottle")&&r.progressText().contains("Strong")&&KOMESerfProvisioningService.matches(drink,r),"bootstrapped native drink label matches exact delivery");LOTRItemMug.setStrengthMeta(drink,2);check(!KOMESerfProvisioningService.matches(drink,r),"native mismatched potency rejected");
            check(LOTRMod.pouch instanceof lotr.common.item.LOTRItemPouch,"native pouch API available on dedicated side");
            world.setBlockToAir(x,y,z);world.setBlock(x,y-1,z,Blocks.planks);
            net.minecraft.nbt.NBTTagCompound proof=new net.minecraft.nbt.NBTTagCompound();proof.setString("Status","EXISTING_VERIFIED");proof.setInteger("Dimension",0);
            proof.setInteger("RoofX",x);proof.setInteger("RoofY",y+3);proof.setInteger("RoofZ",z);proof.setDouble("ArrivalX",x+0.5);proof.setDouble("ArrivalY",y);proof.setDouble("ArrivalZ",z+0.5);
            for(int dx=0;dx<2;dx++)for(int dz=0;dz<2;dz++)world.setBlock(x+dx,y+3,z+dz,Blocks.planks);
            noble.setPosition(x+3.5,y,z+0.5);check(KOMEProgressionDestinations.arrived(noble,proof),"actual NPC near clear verified arrival accepted");
            noble.setPosition(x+7.5,y,z+0.5);check(!KOMEProgressionDestinations.arrived(noble,proof),"NPC seven blocks from arrival rejected");
            noble.setPosition(x+3.5,y+3,z+0.5);check(!KOMEProgressionDestinations.arrived(noble,proof),"NPC on wrong vertical level rejected");
            noble.setPosition(x+3.5,y,z+0.5);world.setBlock(x+2,y,z,Blocks.planks);world.setBlock(x+2,y+1,z,Blocks.planks);
            check(!KOMEProgressionDestinations.arrived(noble,proof),"NPC across inaccessible wall rejected by native collision ray");
            world.setBlock(x,y,z,Blocks.planks);check(!KOMEProgressionDestinations.valid(world,proof),"blocked saved arrival is invalid rather than silently completing");
            Method hostility=com.lotrcharactercreation.faction.StartingFactionApplication.class.getDeclaredMethod("applyStartingHostility",lotr.common.LOTRPlayerData.class,lotr.common.fac.LOTRFaction.class);hostility.setAccessible(true);
            for(lotr.common.fac.LOTRFaction faction:new lotr.common.fac.LOTRFaction[]{lotr.common.fac.LOTRFaction.GONDOR,lotr.common.fac.LOTRFaction.ROHAN,lotr.common.fac.LOTRFaction.MORDOR,lotr.common.fac.LOTRFaction.HIGH_ELF}){
                java.util.Set<lotr.common.fac.LOTRFaction> enemies=com.lotrcharactercreation.faction.StartingFactionApplication.hostileFactions(faction);check(!enemies.isEmpty(),"bootstrapped native hostility exists for "+faction.codeName());AlignmentData alignment=new AlignmentData();hostility.invoke(null,alignment,faction);
                check(alignment.writes.keySet().equals(enemies),"only native hostile factions changed for "+faction.codeName());boolean once=true;for(lotr.common.fac.LOTRFaction enemy:enemies)once&=alignment.writes.get(enemy)==1&&alignment.getAlignment(enemy)==6F;
                check(once&&alignment.getAlignment(faction)==7F,"exact additive minus one per unique enemy with selected alignment retained for "+faction.codeName());
            }
            for(String faction:new String[]{"dunland","gondor","rohan","highelves","durinsfolk","mordor"})evidence.append("NAME ").append(KOMEProgressionNativeAuthority.name(faction,KOMEProgressionNpcRank.PRINCE,"Gair")).append('\n');
            evidence.append("PASS checks=").append(checks).append("; dedicated Forge execution, no visual acceptance\n");
        }catch(Throwable failure){evidence.append("FAIL ").append(failure).append('\n');throw new RuntimeException("Native progression verification failed",failure);}
        finally{Files.write(Paths.get("progression-native.txt"),evidence.toString().getBytes(StandardCharsets.UTF_8));MinecraftServer.getServer().initiateShutdown();}
    }
}
