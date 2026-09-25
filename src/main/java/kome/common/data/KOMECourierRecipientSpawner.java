package kome.common.data;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lotr.common.entity.npc.*;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.spawning.LOTRSpawnEntry;
import lotr.common.world.spawning.LOTRSpawnList;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Creates one ordinary native LOTR recipient when the destination is naturally loaded. */
final class KOMECourierRecipientSpawner {
    private static final Logger LOGGER=LogManager.getLogger("KOMECourier");
    static final String TOKEN="KOMECourierRecipientToken";
    private static final int SEARCH_ATTEMPTS=64;
    private static final String[] TRADERS={
        "LOTREntityRohanMarketTrader","LOTREntityGondorMarketTrader","LOTREntityBreeMarketTrader",
        "LOTREntityDaleMerchant","LOTREntityBlueDwarfMerchant","LOTREntityIronHillsMerchant",
        "LOTREntityRivendellTrader","LOTREntityGaladhrimTrader","LOTREntityWoodElfTrader",
        "LOTREntityMordorOrcTrader","LOTREntityGundabadOrcTrader","LOTREntityUrukHaiTrader",
        "LOTREntityDorwinionMerchantMan","LOTREntityDorwinionMerchantElf",
        "LOTREntityUmbarTrader","LOTREntitySouthronTrader","LOTREntityEasterlingMarketTrader",
        "LOTREntityHarnedorTrader","LOTREntityNomadTrader","LOTREntityGulfTrader",
        "LOTREntityRohanBaker","LOTREntityRohanBlacksmith","LOTREntityGondorBaker","LOTREntityGondorBlacksmith",
        "LOTREntityBreeBaker","LOTREntityBreeBlacksmith","LOTREntityDaleBaker","LOTREntityDaleBlacksmith",
        "LOTREntityEasterlingBaker","LOTREntityEasterlingBlacksmith","LOTREntityUmbarBaker","LOTREntityUmbarBlacksmith",
        "LOTREntitySouthronBaker","LOTREntityGulfBaker","LOTREntityHarnedorBaker",
        "LOTREntityDwarfSmith","LOTREntityGaladhrimSmith","LOTREntityWoodElfSmith","LOTREntityRivendellSmith"};
    private static final String[] CAPTAINS={
        "LOTREntityGondorianCaptain","LOTREntityBreeCaptain","LOTREntityDaleCaptain",
        "LOTREntityRangerNorthCaptain","LOTREntityRangerIthilienCaptain","LOTREntityWoodElfCaptain",
        "LOTREntityDorwinionCaptain","LOTREntityDorwinionElfCaptain","LOTREntityBlackUrukCaptain",
        "LOTREntityUmbarCaptain","LOTREntityCorsairCaptain","LOTREntityLamedonCaptain",
        "LOTREntityLebenninCaptain","LOTREntityPelargirCaptain","LOTREntityDolAmrothCaptain"};
    private KOMECourierRecipientSpawner() {}

    static LOTREntityNPC findOwned(World world,String token){
        for(Object value:world.loadedEntityList)if(value instanceof LOTREntityNPC){LOTREntityNPC npc=(LOTREntityNPC)value;if(npc.isEntityAlive()&&token.equals(npc.getEntityData().getString(TOKEN)))return npc;}
        return null;
    }
    static UUID recipientId(KOMESerfCourierAssignment assignment){return UUID.nameUUIDFromBytes(("KOME:courier:"+assignment.token+":"+assignment.recipientDeaths).getBytes(StandardCharsets.UTF_8));}
    static boolean duplicateOwned(LOTREntityNPC joining){
        String token=joining.getEntityData().getString(TOKEN);if(token.length()==0)return false;
        for(Object value:joining.worldObj.loadedEntityList)if(value instanceof LOTREntityNPC&&value!=joining){LOTREntityNPC existing=(LOTREntityNPC)value;
            if(existing.isEntityAlive()&&existing.getUniqueID().equals(joining.getUniqueID())&&token.equals(existing.getEntityData().getString(TOKEN)))return true;
        }
        return false;
    }
    static void reconcileMarkerOnLoad(KOMEWorldData world,LOTREntityNPC npc){
        String token=npc.getEntityData().getString(TOKEN);if(token.length()==0)return;
        for(KOMEPlayerProgression progression:world.progressions.values()){
            KOMESerfKnightProgression state=progression.getSerfKnightProgression();
            if(!"courier".equals(state.getActiveAssignmentKind()))continue;
            KOMESerfCourierAssignment assignment=KOMESerfCourierAssignment.readFromNBT(state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
            if(assignment!=null&&assignment.stage==KOMESerfCourierAssignment.Stage.OUTBOUND&&token.equals(assignment.token))return;
        }
        npc.getEntityData().removeTag(TOKEN);
    }
    static LOTREntityNPC spawn(EntityPlayerMP player,KOMESerfCourierAssignment assignment){
        World world=player.worldObj;
        int cx=(int)Math.floor(assignment.destinationX),cz=(int)Math.floor(assignment.destinationZ);
        if(world.getChunkProvider()==null||!world.getChunkProvider().chunkExists(cx>>4,cz>>4)){
            if(LOGGER.isDebugEnabled())LOGGER.debug("Courier spawn pending token={} destination chunk=({}, {}) unloaded",assignment.token,cx>>4,cz>>4);
            return null;
        }
        BiomeGenBase base=world.getWorldChunkManager().getBiomeGenAt(cx,cz);
        if(!(base instanceof LOTRBiome)){
            if(LOGGER.isDebugEnabled())LOGGER.debug("Courier spawn pending token={} biome={}",assignment.token,base);
            return null;
        }
        LOTRFaction faction=KOMEProgressionFactionResolver.resolve(assignment.destinationFactionKey);
        if(faction==null){if(LOGGER.isDebugEnabled())LOGGER.debug("Courier spawn pending token={} unknown faction={}",assignment.token,assignment.destinationFactionKey);return null;}
        List<Class<? extends LOTREntityNPC>> types=eligibleClasses(world,(LOTRBiome)base,faction);
        if(types.isEmpty()){if(LOGGER.isDebugEnabled())LOGGER.debug("Courier spawn pending token={} no native class for faction={} biome={}",assignment.token,faction,base);return null;}
        Random random=new Random(assignment.token.hashCode()*31L+0x4b4f4d45L);
        int first=random.nextInt(types.size());Set<Class<? extends LOTREntityNPC>> attempted=new HashSet<Class<? extends LOTREntityNPC>>();
        for(int i=0;i<types.size();i++){
            Class<? extends LOTREntityNPC> type=types.get((first+i)%types.size());if(!attempted.add(type))continue;
            LOTREntityNPC npc=create(world,type,faction,true);if(npc==null){if(LOGGER.isDebugEnabled())LOGGER.debug("Courier class rejected token={} class={} construction/faction",assignment.token,type.getName());continue;}
            if(LOGGER.isDebugEnabled())LOGGER.debug("Courier class constructed token={} class={}",assignment.token,type.getName());
            double[] position=safeLoadedPosition(world,player,assignment,npc);if(position==null)return null;
            npc.setLocationAndAngles(position[0],position[1],position[2],random.nextFloat()*360F,0F);
            npc.spawnRidingHorse=false;
            try{npc.onSpawnWithEgg(null);npc.onArtificalSpawn();}catch(RuntimeException nativeInitializationFailed){if(LOGGER.isDebugEnabled())LOGGER.debug("Courier native initialization failed token={} class={}",assignment.token,type.getName(),nativeInitializationFailed);continue;}
            if(npc.ridingEntity!=null||npc.bossInfo!=null||npc.isTraderEscort||npc.isChild()
                    ||npc.hiredNPCInfo==null||npc.hiredNPCInfo.isActive||npc.getFaction()!=faction){if(LOGGER.isDebugEnabled())LOGGER.debug("Courier native class unsafe after initialization token={} class={}",assignment.token,type.getName());continue;}
            npc.getEntityData().setString(TOKEN,assignment.token);
            npc.setUniqueID(recipientId(assignment));
            if(!world.spawnEntityInWorld(npc)){if(LOGGER.isDebugEnabled())LOGGER.debug("Courier spawnEntityInWorld rejected token={} class={} uuid={} chunk=({}, {})",assignment.token,type.getName(),npc.getUniqueID(),((int)Math.floor(npc.posX))>>4,((int)Math.floor(npc.posZ))>>4);return null;}
            if(LOGGER.isDebugEnabled())LOGGER.debug("Courier spawned token={} class={} uuid={} at=({}, {}, {})",assignment.token,type.getName(),npc.getUniqueID(),npc.posX,npc.posY,npc.posZ);
            return npc;
        }
        return null;
    }
    static List<Class<? extends LOTREntityNPC>> eligibleClasses(World world,LOTRBiome biome,LOTRFaction faction){
        Map<String,Class<? extends LOTREntityNPC>> ordinary=new TreeMap<String,Class<? extends LOTREntityNPC>>(),captains=new TreeMap<String,Class<? extends LOTREntityNPC>>(),traders=new TreeMap<String,Class<? extends LOTREntityNPC>>();
        if(biome.npcSpawnList!=null)for(LOTRSpawnEntry entry:biome.npcSpawnList.getAllSpawnEntries(world))consider(world,entry.entityClass,faction,ordinary,captains,traders);
        if(ordinary.isEmpty())for(Field field:LOTRSpawnList.class.getFields())if(field.getType()==LOTRSpawnList.class)try{
            LOTRSpawnList list=(LOTRSpawnList)field.get(null);
            if(list!=null&&list.getListCommonFaction(world)==faction)for(LOTRSpawnEntry entry:list.getReadOnlyList())consider(world,entry.entityClass,faction,ordinary,captains,traders);
        }catch(IllegalAccessException ignored){}catch(IllegalArgumentException mixedNativeList){}
        // These four native factions have no reliable independent humanoid entry in the shared biome list.
        if(faction==LOTRFaction.HIGH_ELF)ordinary.put(LOTREntityHighElf.class.getName(),LOTREntityHighElf.class);
        if(faction==LOTRFaction.LOTHLORIEN)ordinary.put(LOTREntityGaladhrimElf.class.getName(),LOTREntityGaladhrimElf.class);
        if(faction==LOTRFaction.FANGORN)ordinary.put(LOTREntityEnt.class.getName(),LOTREntityEnt.class);
        if(faction==LOTRFaction.HALF_TROLL)ordinary.put(LOTREntityHalfTroll.class.getName(),LOTREntityHalfTroll.class);
        for(String name:CAPTAINS)try{consider(world,Class.forName("lotr.common.entity.npc."+name),faction,ordinary,captains,traders);}catch(ClassNotFoundException ignored){}
        for(String name:TRADERS)try{consider(world,Class.forName("lotr.common.entity.npc."+name),faction,ordinary,captains,traders);}catch(ClassNotFoundException ignored){}
        List<Class<? extends LOTREntityNPC>> weighted=new ArrayList<Class<? extends LOTREntityNPC>>();
        for(Class<? extends LOTREntityNPC> type:ordinary.values())for(int i=0;i<8;i++)weighted.add(type);
        for(Class<? extends LOTREntityNPC> type:captains.values())for(int i=0;i<2;i++)weighted.add(type);
        weighted.addAll(traders.values());
        return weighted;
    }
    @SuppressWarnings("unchecked") private static void consider(World world,Class raw,LOTRFaction faction,Map<String,Class<? extends LOTREntityNPC>> ordinary,Map<String,Class<? extends LOTREntityNPC>> captains,Map<String,Class<? extends LOTREntityNPC>> traders){
        if(raw==null||!LOTREntityNPC.class.isAssignableFrom(raw)||java.lang.reflect.Modifier.isAbstract(raw.getModifiers()))return;
        String name=raw.getSimpleName().replaceFirst("^LOTREntity","").toLowerCase(Locale.ROOT);
        if(name.contains("boss")||name.contains("warg")||name.contains("rider")||name.contains("mounted")||(name.contains("troll")&&!name.equals("halftroll"))||name.contains("spider")||(name.contains("ent")&&!name.equals("ent"))||name.contains("huorn")||name.contains("banner")||name.contains("bombardier")||name.contains("invasion")||name.contains("mercenary")||name.contains("escort"))return;
        LOTREntityNPC sample=create(world,(Class<? extends LOTREntityNPC>)raw,faction);
        if(sample==null||sample.bossInfo!=null||sample.isTraderEscort||sample instanceof LOTRNPCMount||sample.getFaction()!=faction)return;
        Class<? extends LOTREntityNPC> type=(Class<? extends LOTREntityNPC>)raw;
        if(sample.isTrader())traders.put(type.getName(),type);
        else if(name.contains("captain")||KOMEProgressionLords.isCombatUnitHiringNpc(sample))captains.put(type.getName(),type);
        else ordinary.put(type.getName(),type);
    }
    private static LOTREntityNPC create(World world,Class<? extends LOTREntityNPC> type,LOTRFaction faction){return create(world,type,faction,false);}
    private static LOTREntityNPC create(World world,Class<? extends LOTREntityNPC> type,LOTRFaction faction,boolean reportFailure){
        try{LOTREntityNPC npc=type.getConstructor(World.class).newInstance(world);return npc.getFaction()==faction?npc:null;}
        catch(Exception failure){if(reportFailure&&LOGGER.isDebugEnabled())LOGGER.debug("Courier constructor exception class={}",type.getName(),failure);return null;}
    }
    static double[] safeLoadedPosition(World world,EntityPlayerMP player,KOMESerfCourierAssignment assignment,LOTREntityNPC npc){
        // A failed search tries different loaded columns next time without changing the destination.
        Random random=new Random(assignment.token.hashCode()*131L+17L+(world.getTotalWorldTime()/100L)*104729L);
        int cx=(int)Math.floor(assignment.destinationX),cz=(int)Math.floor(assignment.destinationZ);
        int[] rejected=new int[5]; // unloaded/edge, too close, height, surface, collision
        for(int i=0;i<SEARCH_ATTEMPTS;i++){
            double angle=random.nextDouble()*Math.PI*2D, distance=24D+random.nextDouble()*72D;
            int x=cx+(int)Math.round(Math.cos(angle)*distance),z=cz+(int)Math.round(Math.sin(angle)*distance);
            // Keep collision and standing-space probes inside this loaded chunk too.
            if((x&15)<2||(x&15)>13||(z&15)<2||(z&15)>13
                    ||!world.getChunkProvider().chunkExists(x>>4,z>>4)){rejected[0]++;continue;}
            double dx=x-player.posX,dz=z-player.posZ;if(dx*dx+dz*dz<48D*48D){rejected[1]++;continue;}
            int y=world.getTopSolidOrLiquidBlock(x,z);
            if(y<2||y>world.getActualHeight()-3){rejected[2]++;continue;}
            Block floor=world.getBlock(x,y-1,z);
            if(floor==null||!floor.getMaterial().isSolid()||!floor.isOpaqueCube()||floor.getMaterial().isLiquid()||!world.isAirBlock(x,y,z)||!world.isAirBlock(x,y+1,z)){rejected[3]++;continue;}
            npc.setLocationAndAngles(x+0.5D,y,z+0.5D,0F,0F);
            if(!world.getCollidingBoundingBoxes(npc,npc.boundingBox).isEmpty()||!world.checkNoEntityCollision(npc.boundingBox,npc)){rejected[4]++;continue;}
            if(LOGGER.isDebugEnabled())LOGGER.debug("Courier safe site token={} tested={} rejected=[unloaded/edge:{}, near:{}, height:{}, surface:{}, collision:{}] at=({}, {}, {})",assignment.token,i+1,rejected[0],rejected[1],rejected[2],rejected[3],rejected[4],x,y,z);
            return new double[]{x+0.5D,y,z+0.5D};
        }
        if(LOGGER.isDebugEnabled())LOGGER.debug("Courier no safe site token={} tested={} rejected=[unloaded/edge:{}, near:{}, height:{}, surface:{}, collision:{}]",assignment.token,SEARCH_ATTEMPTS,rejected[0],rejected[1],rejected[2],rejected[3],rejected[4]);
        return null;
    }
}
