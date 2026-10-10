package kome.common.data;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

/** Bounded, loaded-only open-ground search. Places actors, never changes blocks. */
public final class KOMEProgressionEncounterSites {
    private KOMEProgressionEncounterSites() {}
    static double[] attacker(World world,double x,double z,KOMEProgressionNpcRef liege,String token,int index){
        int seed=(token==null?0:token.hashCode())+index*211;
        for(int attempt=0;attempt<32;attempt++){
            double angle=Math.floorMod(seed+attempt*199,6283)/1000D;
            double distance=48+Math.floorMod(seed+attempt*7,9);
            int px=(int)Math.floor(x+Math.cos(angle)*distance),pz=(int)Math.floor(z+Math.sin(angle)*distance);
            double[] site=KOMEKnightCommissionService.safeSite(world,px,pz);
            if(site==null||!openGround(world,site))continue;
            if(squared(site[0]-x,site[2]-z)<40*40||squared(site[0]-x,site[2]-z)>64*64)continue;
            if(liege!=null&&liege.isSet()&&squared(site[0]-liege.x,site[2]-liege.z)<40*40)continue;
            return site;
        }
        return null;
    }
    private static double squared(double x,double z){return x*x+z*z;}
    static boolean openGround(World world,double[] site){
        int x=(int)Math.floor(site[0]),y=(int)site[1],z=(int)Math.floor(site[2]);
        if(!KOMEKnightCommissionService.loadedAround(world,x,z))return false;
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
            int sy=world.getTopSolidOrLiquidBlock(x+dx,z+dz);
            if(Math.abs(sy-y)>2||!natural(world.getBlock(x+dx,sy-1,z+dz)))return false;
            for(int dy=-2;dy<=3;dy++){
                if(world.getTileEntity(x+dx,sy+dy,z+dz)!=null)return false;
                Block block=world.getBlock(x+dx,sy+dy,z+dz);
                if(KOMEProgressionDestinations.built(block))return false;
            }
        }
        return world.isAirBlock(x,y,z)&&world.isAirBlock(x,y+1,z)&&world.isAirBlock(x,y+2,z);
    }
    private static boolean natural(Block block){
        return block==Blocks.grass||block==Blocks.dirt||block==Blocks.stone||block==Blocks.sand
            ||block==Blocks.gravel||block==Blocks.snow||block==Blocks.mycelium;
    }
}
