package kome.common.data;

import kome.common.KOMEAccessFixture;
import lotr.common.fac.LOTRFaction;
import net.minecraft.nbt.NBTTagCompound;

/** Actual roof/wall blocks and saved evidence for lifecycle tests; empty terrain is tested separately. */
final class KOMEProgressionGameplayFixture {
    static NBTTagCompound shelter(KOMEAccessFixture.TestWorld world,int x,int z,String faction){
        world.shelter(x,z);NBTTagCompound proof=new NBTTagCompound();
        proof.setString("Status","EXISTING_VERIFIED");proof.setString("Structure","fixture:small_shelter");proof.setString("Faction",faction);
        proof.setInteger("Dimension",world.provider.dimensionId);proof.setInteger("RoofX",x);proof.setInteger("RoofY",68);proof.setInteger("RoofZ",z);
        proof.setDouble("ArrivalX",x-3.5);proof.setDouble("ArrivalY",65);proof.setDouble("ArrivalZ",z-3.5);
        return proof;
    }
    static void record(KOMEAccessFixture.TestWorld world,int x,int z,String faction){
        NBTTagCompound proof=shelter(world,x,z,faction);KOMEWorldData.get(world).progressionShelters.put(KOMEProgressionDestinations.key(proof),proof);
    }
    static void border(KOMEAccessFixture.TestWorld world,LOTRFaction attacker)throws Exception{
        KOMECourierGeographyTest.TestManager manager=(KOMECourierGeographyTest.TestManager)world.getWorldChunkManager();
        KOMECourierGeographyTest.TestBiome enemy=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestBiome.class);enemy.heightBaseParameter=.2F;
        EnemySpawns spawns=KOMEAccessFixture.allocate(EnemySpawns.class);spawns.faction=attacker;enemy.npcSpawnList=spawns;
        manager.hostileBiome=enemy;manager.borderZ=(int)((net.minecraft.entity.player.EntityPlayer)world.playerEntities.get(0)).posZ+600;
    }
    public static class EnemySpawns extends lotr.common.world.spawning.LOTRBiomeSpawnList {
        LOTRFaction faction;private EnemySpawns(){super("test");}
        @Override public boolean isFactionPresent(net.minecraft.world.World world,LOTRFaction requested){return requested==faction;}
        @Override public java.util.List<lotr.common.world.spawning.LOTRSpawnEntry> getAllSpawnEntries(net.minecraft.world.World world){return java.util.Collections.emptyList();}
    }
}
