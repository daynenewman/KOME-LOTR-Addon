package kome.common.data;
import java.lang.reflect.Field;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.spawning.*;

/** Explicit native raid metadata for isolated generation fixtures, restored with their biome. */
final class KOMEProgressionRegionalFixture {
    static void threats(LOTRBiome biome,LOTRFaction defender)throws Exception {
        LOTRBiomeInvasionSpawns raids=new LOTRBiomeInvasionSpawns(biome);
        for(LOTRInvasions invasion:LOTRInvasions.values())
            if(KOMESerfKnightDefenseService.hostile(defender,invasion.invasionFaction)&&invasion.invasionMobs!=null&&!invasion.invasionMobs.isEmpty())
                raids.addInvasion(invasion,LOTREventSpawner.EventChance.UNCOMMON);
        Field field=LOTRBiome.class.getDeclaredField("invasionSpawns");field.setAccessible(true);field.set(biome,raids);
    }
}
