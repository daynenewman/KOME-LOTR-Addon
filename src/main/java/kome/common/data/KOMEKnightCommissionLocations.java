package kome.common.data;

import java.util.Random;
import lotr.common.LOTRDimension;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/** Knight geography has its own profiles; courier cadence and recipient rules cannot alter it. */
public final class KOMEKnightCommissionLocations {
    private KOMEKnightCommissionLocations() {}
    public static final class Destination {
        public final double x,z;public final String name;
        Destination(double x,double z,String name){this.x=x;this.z=z;this.name=name;}
    }
    static int minimum(KOMEKnightCommission.Type type){return 500;}
    static int maximum(KOMEKnightCommission.Type type){return 1500;}
    public static boolean coordinate(double value){return Double.isFinite(value)&&Math.abs(value)<29999900D;}
    static boolean territory(World world,LOTRFaction faction,LOTRBiome biome,int x,int z){
        if(world==null||faction==null||biome==null||biome==LOTRBiome.utumno||biome.isWateryBiome()
                ||biome.heightBaseParameter>1.25F||biome.heightBaseParameter<-.5F)return false;
        return faction.inDefinedControlZone(world,x,64,z)
            ||(biome.npcSpawnList!=null&&biome.npcSpawnList.isFactionPresent(world,faction));
    }
    static boolean usable(World world,LOTRFaction faction,double x,double z){
        if(world==null||world.provider==null||world.provider.dimensionId!=LOTRDimension.MIDDLE_EARTH.dimensionID
                ||world.getWorldChunkManager()==null||!coordinate(x)||!coordinate(z))return false;
        BiomeGenBase base=world.getWorldChunkManager().getBiomeGenAt((int)x,(int)z);
        if(!(base instanceof LOTRBiome)||!territory(world,faction,(LOTRBiome)base,(int)x,(int)z))return false;
        // Do not force chunks into memory. Native biome data rejects obvious water/mountains;
        // already loaded sites must also have a usable solid surface. Spawn rechecks on arrival.
        return !KOMEKnightCommissionService.loaded(world,x,z)||KOMEKnightCommissionService.safeSite(world,(int)x,(int)z)!=null;
    }
    static double height(World world,double x,double z){
        if(!KOMEKnightCommissionService.loaded(world,x,z))return 64D;
        double[] surface=KOMEKnightCommissionService.safeSite(world,(int)x,(int)z);return surface==null?Double.NaN:surface[1];
    }
    static Destination choose(KOMEProgressionNpcRef liege,World world,KOMEKnightCommission.Type type,String token){
        if(liege==null||!liege.isSet()||world==null||world.provider==null||liege.dimension!=world.provider.dimensionId
                ||!coordinate(liege.x)||!coordinate(liege.z))return null;
        LOTRFaction faction=KOMEProgressionFactionResolver.resolve(liege.factionKey);if(faction==null)return null;
        Random random=new Random((token==null?0:token.hashCode())*31L+Double.doubleToLongBits(liege.x)+Double.doubleToLongBits(liege.z));
        Destination best=null;int bestScore=-1;
        for(int attempt=0;attempt<192;attempt++){
            double distance=minimum(type)+random.nextDouble()*(maximum(type)-minimum(type));double angle=random.nextDouble()*Math.PI*2;
            double rawX=liege.x+Math.cos(angle)*distance,rawZ=liege.z+Math.sin(angle)*distance;
            if(!coordinate(rawX)||!coordinate(rawZ))continue;
            int x=(((int)Math.round(rawX))&~15)+8,z=(((int)Math.round(rawZ))&~15)+8;
            if(!range(liege,type,x,z)||!usable(world,faction,x,z))continue;
            LOTRBiome biome=(LOTRBiome)world.getWorldChunkManager().getBiomeGenAt(x,z);
            String name=biome.getBiomeDisplayName();if(name==null||name.isEmpty())name="Faction lands";
            Destination candidate=new Destination(x,z,name+" roadside refuge");
            if(type!=KOMEKnightCommission.Type.BORDER_INCURSION){best=candidate;break;}
            int score=KOMEKnightCommissionService.frontierScore(world,faction,x,z);
            if(score>bestScore){best=candidate;bestScore=score;}if(score>=3)break;
        }
        if(best==null)return null;
        if(type!=KOMEKnightCommission.Type.BORDER_INCURSION)for(LOTRWaypoint waypoint:LOTRWaypoint.values())
            if(waypoint.faction==faction&&!waypoint.isHidden()&&range(liege,type,waypoint.getXCoord(),waypoint.getZCoord())
                    &&usable(world,faction,waypoint.getXCoord(),waypoint.getZCoord()))
                return new Destination(waypoint.getXCoord(),waypoint.getZCoord(),"Roadside refuge near "+waypoint.getDisplayName());
        return best;
    }
    private static boolean range(KOMEProgressionNpcRef origin,KOMEKnightCommission.Type type,double x,double z){
        if(!coordinate(x)||!coordinate(z))return false;double dx=x-origin.x,dz=z-origin.z,d=dx*dx+dz*dz;
        return d>=minimum(type)*(double)minimum(type)&&d<=maximum(type)*(double)maximum(type);
    }
}
