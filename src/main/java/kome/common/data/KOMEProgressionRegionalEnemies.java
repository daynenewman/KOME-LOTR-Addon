package kome.common.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.spawning.LOTREventSpawner.EventChance;
import lotr.common.world.spawning.LOTRInvasions;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/** Native biome faction containers include zero-base-weight conquest forces. */
public final class KOMEProgressionRegionalEnemies {
    private KOMEProgressionRegionalEnemies(){}
    public static List<LOTRInvasions> choices(World world,LOTRFaction defender,double x,double z){
        List<LOTRInvasions> result=new ArrayList<LOTRInvasions>();
        if(world==null||world.getWorldChunkManager()==null)return result;
        BiomeGenBase base=world.getWorldChunkManager().getBiomeGenAt((int)Math.floor(x),(int)Math.floor(z));
        if(!(base instanceof LOTRBiome))return result;
        LOTRBiome biome=(LOTRBiome)base;
        Set<LOTRInvasions> raids=new LinkedHashSet<LOTRInvasions>();
        for(EventChance chance:EventChance.values())if(chance!=EventChance.NEVER&&biome.invasionSpawns!=null)
            raids.addAll(biome.invasionSpawns.getInvasionsForChance(chance));
        List<LOTRInvasions> conquest=new ArrayList<LOTRInvasions>(),invasions=new ArrayList<LOTRInvasions>();
        for(LOTRInvasions invasion:LOTRInvasions.values()){
            if(!KOMESerfKnightDefenseService.hostile(defender,invasion.invasionFaction)||invasion.invasionMobs==null||invasion.invasionMobs.isEmpty())continue;
            if(biome.npcSpawnList.isFactionPresent(world,invasion.invasionFaction))conquest.add(invasion);
            else if(raids.contains(invasion))invasions.add(invasion);
        }
        result.addAll(conquest);result.addAll(invasions);return result;
    }
}
