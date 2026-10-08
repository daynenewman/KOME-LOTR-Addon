package kome.common.data;
import java.util.*;
import java.lang.reflect.Field;
import java.io.InputStream;
import kome.common.KOMEAccessFixture;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.spawning.*;
import net.minecraft.world.World;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

/** Reads each real native biome constructor's conquest/spawn and invasion references. */
@RunWith(Parameterized.class)
public class KOMEProgressionRegionalEnemyMatrixTest {
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> cases(){
        return Arrays.asList(new Object[][]{
            {"Shire",LOTRFaction.HOBBIT},{"Breeland",LOTRFaction.BREE},{"Angmar",LOTRFaction.ANGMAR},
            {"BlueMountains",LOTRFaction.BLUE_MOUNTAINS},{"Lindon",LOTRFaction.HIGH_ELF},
            {"WoodlandRealm",LOTRFaction.WOOD_ELF},{"Dale",LOTRFaction.DALE},{"IronHills",LOTRFaction.DURINS_FOLK},
            {"Lothlorien",LOTRFaction.LOTHLORIEN},{"Dunland",LOTRFaction.DUNLAND},{"NanCurunir",LOTRFaction.ISENGARD},
            {"Fangorn",LOTRFaction.FANGORN},{"Rohan",LOTRFaction.ROHAN},{"Gondor",LOTRFaction.GONDOR},
            {"Mordor",LOTRFaction.MORDOR},{"Dorwinion",LOTRFaction.DORWINION},{"Rhun",LOTRFaction.RHUDEL},
            {"Umbar",LOTRFaction.NEAR_HARAD},{"FarHarad",LOTRFaction.MORWAITH},
            {"FarHaradJungle",LOTRFaction.TAURETHRIM},{"Pertorogwaith",LOTRFaction.HALF_TROLL},
            {"MirkwoodCorrupted",LOTRFaction.DOL_GULDUR},{"MistyMountains",LOTRFaction.GUNDABAD},{"Eriador",LOTRFaction.RANGER_NORTH}
        });
    }
    private final String region;private final LOTRFaction defender;
    public KOMEProgressionRegionalEnemyMatrixTest(String region,LOTRFaction defender){this.region=region;this.defender=defender;}
    @Test public void allChosenEnemiesAreNativeRegionalConquestOrRaidForcesAndHostile()throws Exception{
        try(KOMEProgressionHardeningNativeFixture nativeData=new KOMEProgressionHardeningNativeFixture()){
            KOMEAccessFixture f=new KOMEAccessFixture();f.world.rand=new Random(1);
            KOMECourierGeographyTest.TestBiome biome=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestBiome.class);
            RegionalSpawns spawn=KOMEAccessFixture.allocate(RegionalSpawns.class);spawn.factions=new HashSet<LOTRFaction>();biome.npcSpawnList=spawn;
            LOTRBiomeInvasionSpawns raids=new LOTRBiomeInvasionSpawns(biome);Set<LOTRInvasions> nativeRaids=new HashSet<LOTRInvasions>();
            String type="lotr/common/world/biome/LOTRBiomeGen"+region;
            while(type.startsWith("lotr/common/world/biome/LOTRBiomeGen")){
                ClassNode node=new ClassNode();InputStream resource=getClass().getResourceAsStream("/"+type+".class");assertNotNull(type,resource);
                try(InputStream in=resource){new ClassReader(in).accept(node,0);}
                for(MethodNode method:node.methods)if("<init>".equals(method.name))
                    for(AbstractInsnNode insn=method.instructions.getFirst();insn!=null;insn=insn.getNext())if(insn instanceof FieldInsnNode&&insn.getOpcode()==Opcodes.GETSTATIC){
                        FieldInsnNode field=(FieldInsnNode)insn;
                        if(field.owner.equals("lotr/common/world/spawning/LOTRInvasions")){
                            LOTRInvasions invasion=LOTRInvasions.valueOf(field.name);nativeRaids.add(invasion);raids.addInvasion(invasion,LOTREventSpawner.EventChance.UNCOMMON);
                        }
                        if(field.owner.equals("lotr/common/world/spawning/LOTRSpawnList")){
                            LOTRSpawnList list=(LOTRSpawnList)LOTRSpawnList.class.getField(field.name).get(null);
                            for(LOTRSpawnEntry entry:list.getReadOnlyList()){
                                LOTREntityNPC npc=(LOTREntityNPC)KOMEAccessFixture.allocate(entry.entityClass);npc.worldObj=f.world;
                                LOTRFaction faction=npc.getFaction();if(faction!=null)spawn.factions.add(faction);
                            }
                        }
                    }
                type=node.superName;
            }
            Field r=LOTRBiome.class.getDeclaredField("invasionSpawns");r.setAccessible(true);r.set(biome,raids);
            KOMECourierGeographyTest.TestManager manager=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestManager.class);manager.biome=biome;f.world.provider.worldChunkMgr=manager;
            List<LOTRInvasions> choices=KOMEProgressionRegionalEnemies.choices(f.world,defender,0,0);
            Set<LOTRInvasions> expected=new LinkedHashSet<LOTRInvasions>();
            for(LOTRInvasions invasion:LOTRInvasions.values())if(KOMESerfKnightDefenseService.hostile(defender,invasion.invasionFaction)
                &&invasion.invasionMobs!=null&&!invasion.invasionMobs.isEmpty()&&(spawn.factions.contains(invasion.invasionFaction)||nativeRaids.contains(invasion)))expected.add(invasion);
            assertEquals(expected,new LinkedHashSet<LOTRInvasions>(choices));
            for(LOTRInvasions invasion:choices){assertNotEquals(defender,invasion.invasionFaction);assertFalse(defender.isGoodRelation(invasion.invasionFaction));assertTrue(KOMESerfKnightDefenseService.hostile(defender,invasion.invasionFaction));}
            if(region.equals("Shire")){assertFalse(choices.isEmpty());assertTrue(choices.stream().anyMatch(i->i.invasionFaction==LOTRFaction.ANGMAR||i.invasionFaction==LOTRFaction.ISENGARD));}
        }
    }
    private static class RegionalSpawns extends LOTRBiomeSpawnList {
        Set<LOTRFaction> factions;private RegionalSpawns(){super("native-matrix");}
        @Override public boolean isFactionPresent(World world,LOTRFaction faction){return factions.contains(faction);}
    }
}
