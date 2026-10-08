package kome.common.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** Loaded-world evidence, never a village generation prediction or a planned building. */
public final class KOMEProgressionDestinations {
    static final String PROOF="DestinationStructure";
    private KOMEProgressionDestinations() {}
    static String key(NBTTagCompound proof){return proof.getString("Faction")+"|"+proof.getInteger("Dimension")+"|"+proof.getInteger("RoofX")+"|"+proof.getInteger("RoofZ");}
    public static void observe(KOMEWorldData data,LOTREntityNPC npc){
        if(npc.getFaction()==null||!npc.getFaction().isPlayableAlignmentFaction())return;
        if(npc.ticksExisted%1200!=20||(!npc.getHasSpecificLocationName()&&!npc.hasHome())||npc.worldObj.isRemote||!npc.isEntityAlive()
                ||KOMEProgressionEncounterMarker.read(npc)!=null)return;
        String faction=npc.getFaction().codeName();
        for(NBTTagCompound saved:data.progressionShelters.values())if(saved.getString("Faction").equals(faction)
                &&saved.getInteger("Dimension")==npc.worldObj.provider.dimensionId
                &&npc.getDistanceSq(saved.getDouble("ArrivalX"),saved.getDouble("ArrivalY"),saved.getDouble("ArrivalZ"))<32*32)return;
        KOMEKnightCommissionLocations.Destination observed=verify(npc.worldObj,npc.getFaction(),npc.posX,npc.posZ,"native:"+npc.getClass().getSimpleName());
        if(observed!=null&&data.progressionShelters.size()<256){data.progressionShelters.put(key(observed.proof),observed.proof);data.markDirty();}
    }

    static KOMEKnightCommissionLocations.Destination find(World world,KOMEProgressionNpcRef origin,int minimum,int maximum){
        if(world==null||world.provider==null||origin==null||world.provider.dimensionId!=origin.dimension)return null;
        LOTRFaction faction=KOMEProgressionFactionResolver.resolve(origin.factionKey);
        if(faction==null)return null;
        KOMEWorldData data=KOMEWorldData.get(world);
        NBTTagCompound nearest=null;double nearestDistance=maximum*(double)maximum;
        java.util.Iterator<NBTTagCompound> savedSites=data.progressionShelters.values().iterator();
        while(savedSites.hasNext()){
            NBTTagCompound saved=savedSites.next();
            if(saved.getInteger("Dimension")!=origin.dimension||!KOMEProgressionFactionResolver.matches(saved.getString("Faction"),faction))continue;
            double dx=saved.getDouble("ArrivalX")-origin.x,dz=saved.getDouble("ArrivalZ")-origin.z,d=dx*dx+dz*dz;
            if(!valid(world,saved)){savedSites.remove();data.markDirty();continue;}
            if(d>=minimum*(double)minimum&&d<=nearestDistance){nearest=saved;nearestDistance=d;}
        }
        if(nearest!=null)return new KOMEKnightCommissionLocations.Destination(nearest.getDouble("ArrivalX"),nearest.getDouble("ArrivalZ"),"the shelter marked on your map",nearest);
        List<LOTREntityNPC> witnesses=new ArrayList<LOTREntityNPC>();
        for(Object value:world.loadedEntityList)if(value instanceof LOTREntityNPC){
            LOTREntityNPC npc=(LOTREntityNPC)value;
            double dx=npc.posX-origin.x,dz=npc.posZ-origin.z,d=dx*dx+dz*dz;
            if(npc.isEntityAlive()&&npc.getFaction()==faction&&!npc.isChild()&&(npc.getHasSpecificLocationName()||npc.hasHome())
                    &&d>=minimum*(double)minimum&&d<=maximum*(double)maximum)witnesses.add(npc);
        }
        witnesses.sort(Comparator.comparingDouble(n->(n.posX-origin.x)*(n.posX-origin.x)+(n.posZ-origin.z)*(n.posZ-origin.z)));
        for(int i=0;i<Math.min(32,witnesses.size());i++){
            LOTREntityNPC npc=witnesses.get(i);
            KOMEKnightCommissionLocations.Destination result=verify(world,faction,npc.posX,npc.posZ,"native:"+npc.getClass().getSimpleName());
            if(result!=null){if(data.progressionShelters.size()<256){data.progressionShelters.put(key(result.proof),result.proof);data.markDirty();}return result;}
        }
        for(lotr.common.world.map.LOTRWaypoint waypoint:lotr.common.world.map.LOTRWaypoint.values()){
            double x=waypoint.getXCoord(),z=waypoint.getZCoord(),dx=x-origin.x,dz=z-origin.z,d=dx*dx+dz*dz;
            if(waypoint.faction!=faction||waypoint.isHidden()||d<minimum*(double)minimum||d>maximum*(double)maximum)continue;
            KOMEKnightCommissionLocations.Destination result=verify(world,faction,x,z,"waypoint:"+waypoint.getCodeName());
            if(result!=null)return result;
        }
        // The shipped structure generators do not expose an authoritative placement/protection
        // transaction. Fail closed rather than overwrite terrain based on player-build heuristics.
        return null;
    }

    static KOMEKnightCommissionLocations.Destination verify(World world,LOTRFaction faction,double x,double z,String identity){
        if(!KOMEKnightCommissionService.loadedAround(world,x,z)||!KOMEKnightCommissionLocations.usable(world,faction,x,z))return null;
        double[] arrival=KOMEKnightCommissionService.safeSite(world,(int)x,(int)z);
        if(arrival==null)return null;
        int baseY=(int)arrival[1];
        for(int dx=-12;dx<=12;dx+=3)for(int dz=-12;dz<=12;dz+=3)for(int dy=2;dy<=8;dy++){
            int px=(int)x+dx,py=baseY+dy,pz=(int)z+dz;
            if(!built(world.getBlock(px,py,pz))||!built(world.getBlock(px+1,py,pz))
                    ||!built(world.getBlock(px,py,pz+1))||!built(world.getBlock(px+1,py,pz+1)))continue;
            // Roof alone is insufficient: require a nearby supporting wall and a clear approach.
            boolean support=false;
            for(int sx=-2;sx<=2;sx++)for(int sz=-2;sz<=2;sz++)
                if(built(world.getBlock(px+sx,baseY+1,pz+sz))&&built(world.getBlock(px+sx,baseY+2,pz+sz)))support=true;
            if(!support)continue;
            NBTTagCompound proof=new NBTTagCompound();
            proof.setString("Structure",identity);proof.setString("Faction",faction.codeName());
            proof.setString("Status","EXISTING_VERIFIED");proof.setInteger("Dimension",world.provider.dimensionId);
            proof.setInteger("RoofX",px);proof.setInteger("RoofY",py);proof.setInteger("RoofZ",pz);
            proof.setDouble("ArrivalX",arrival[0]);proof.setDouble("ArrivalY",arrival[1]);proof.setDouble("ArrivalZ",arrival[2]);
            return new KOMEKnightCommissionLocations.Destination(arrival[0],arrival[2],"the shelter marked on your map",proof);
        }
        return null;
    }

    static boolean built(Block block){
        if(block==null)return false;
        if(block==Blocks.planks||block==Blocks.stonebrick||block==Blocks.brick_block||block==Blocks.cobblestone
                ||block==Blocks.sandstone||block==Blocks.wool)return true;
        String key=String.valueOf(Block.blockRegistry.getNameForObject(block)).toLowerCase(java.util.Locale.ROOT);
        return block.getMaterial().isSolid()&&key.startsWith("lotr:")
            &&(key.contains("brick")||key.contains("plank")||key.contains("thatch"));
    }

    /** Unloaded evidence is retained. Callers must still require the charge's real arrival. */
    static boolean valid(World world,NBTTagCompound proof){
        if(proof==null||!"EXISTING_VERIFIED".equals(proof.getString("Status"))
                ||world==null||world.provider.dimensionId!=proof.getInteger("Dimension"))return false;
        int x=proof.getInteger("RoofX"),y=proof.getInteger("RoofY"),z=proof.getInteger("RoofZ");
        if(y<2||y>=world.getActualHeight()-1||!KOMEKnightCommissionLocations.coordinate(x)||!KOMEKnightCommissionLocations.coordinate(z)
                ||!KOMEKnightCommissionLocations.coordinate(proof.getDouble("ArrivalX"))||!KOMEKnightCommissionLocations.coordinate(proof.getDouble("ArrivalZ")))return false;
        if(!KOMEKnightCommissionService.loadedAround(world,x,z))return true;
        return built(world.getBlock(x,y,z))&&built(world.getBlock(x+1,y,z))
            &&built(world.getBlock(x,y,z+1))&&built(world.getBlock(x+1,y,z+1))
            &&KOMEKnightCommissionService.safeSite(world,(int)proof.getDouble("ArrivalX"),(int)proof.getDouble("ArrivalZ"))!=null;
    }
}
