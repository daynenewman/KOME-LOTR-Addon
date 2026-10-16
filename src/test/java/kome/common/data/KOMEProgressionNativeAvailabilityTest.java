package kome.common.data;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import kome.common.KOMEAccessFixture;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.world.World;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

/** Actual native constructors/predicates and generator bytecode. This does not prove generated-world density. */
public class KOMEProgressionNativeAvailabilityTest {
    private static final String NPC="lotr/common/entity/npc/", GEN="lotr/common/world/structure2/";
    private static final String[][] ROWS={
        {"hobbit","Hobbit","HobbitTavern","HobbitShirriff"},
        {"bree","BreeMan","BreeOffice","BreeCaptain"},
        {"dunedain","DunedainBlacksmith","RangerCamp","RangerNorthCaptain"},
        {"bluemountains","BlueMountainsSmith","BlueMountainsStronghold","BlueDwarfCommander"},
        {"highelves","RivendellSmith","HighElvenTower","HighElfLord"},
        {"gundabad","GundabadOrcTrader","GundabadCamp","GundabadOrcMercenaryCaptain"},
        {"angmar","AngmarOrcTrader","AngmarHillmanChieftainHouse","AngmarHillmanChieftain"},
        {"woodelf","WoodElfSmith","WoodElfTower","WoodElfCaptain"},
        {"dolguldur","DolGuldurOrcTrader","DolGuldurTower","DolGuldurOrcChieftain"},
        {"dale","DaleMan","DaleFortress","DaleCaptain"},
        {"durinsfolk","DwarfSmith","DwarvenTower","DwarfCommander"},
        {"lothlorien","GaladhrimSmith","ElfLordHouse","GaladhrimLord"},
        {"dunland","DunlendingBartender","DunlandHillFort","DunlendingWarlord"},
        {"isengard","UrukHaiTrader","UrukCamp","UrukHaiMercenaryCaptain"},
        {"rohan","RohanMan","RohanFortress","RohirrimMarshal"},
        {"gondor","GondorMan","GondorFortress","GondorianCaptain"},
        {"mordor","MordorOrcTrader","MordorTower","MordorOrcMercenaryCaptain"},
        {"dorwinion","DorwinionMan","DorwinionCaptainTent","DorwinionCaptain"},
        {"rhudel","Easterling","EasterlingFortress","EasterlingWarlord"},
        {"harad","SouthronBaker","SouthronFortress","NearHaradrimWarlord"},
        {"morwaith","MoredainHuntsman","MoredainHutChieftain","MoredainChieftain"},
        {"taurethrim","Tauredain","TauredainChieftainPyramid","TauredainChieftain"},
        {"halftroll","","HalfTrollWarlordHouse","HalfTrollWarlord"}
    };
    private static ClassNode node(JarFile jar,String name)throws Exception {
        JarEntry entry=jar.getJarEntry(name+".class");assertNotNull(name,entry);
        try(InputStream in=jar.getInputStream(entry)){ClassNode n=new ClassNode();new ClassReader(in).accept(n,0);return n;}
    }
    private static LOTREntityNPC npc(World world,String suffix)throws Exception {
        return (LOTREntityNPC)Class.forName((NPC+"LOTREntity"+suffix).replace('/','.')).getConstructor(World.class).newInstance(world);
    }
    private static String host(JarFile jar,String suffix) {
        String name="LOTRWorldGen"+suffix;
        return jar.getJarEntry(GEN+name+".class")!=null?GEN+name:"lotr/common/world/structure/"+name;
    }
    private static boolean constructs(JarFile jar,String host,String type)throws Exception {
        for(String current=host;current.startsWith("lotr/common/world/structure");) {
            ClassNode n=node(jar,current);
            for(MethodNode m:n.methods)for(AbstractInsnNode i:m.instructions.toArray())
                if(i instanceof TypeInsnNode&&i.getOpcode()==org.objectweb.asm.Opcodes.NEW&&type.equals(((TypeInsnNode)i).desc))return true;
                else if(i instanceof MethodInsnNode) {
                    MethodInsnNode call=(MethodInsnNode)i;
                    if("createCaptain".equals(call.name)&&call.owner.endsWith("LOTRWorldGenGondorStructure$GondorFiefdom")) {
                        for(MethodNode factory:node(jar,call.owner).methods)if("createCaptain".equals(factory.name))
                            for(AbstractInsnNode instruction:factory.instructions.toArray())if(instruction instanceof TypeInsnNode
                                    &&instruction.getOpcode()==org.objectweb.asm.Opcodes.NEW&&type.equals(((TypeInsnNode)instruction).desc))return true;
                    }
                }
            current=n.superName;
        }
        return false;
    }
    @Test public void supportedRegionalFactionsHaveActualGeneratedCombatTraderTypes()throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();KOMEProgressionHardeningNativeFixture nativeData=new KOMEProgressionHardeningNativeFixture();JarFile jar=new JarFile("libs/LOTRMod v36.15.jar")) {
            KOMEAccessFixture f=new KOMEAccessFixture();f.world.isRemote=true;
            for(String[] row:ROWS) {
                LOTREntityNPC noble=npc(f.world,row[3]);
                assertTrue(row[0]+" / "+row[3],KOMEProgressionLords.isStandingTrialLiegeCandidate(noble));
                assertTrue(row[0],KOMEProgressionFactionResolver.matches(row[0],noble.getFaction()));
                assertEquals(row[0],KOMEProgressionServiceLocation.supportedHosts().get("LOTRWorldGen"+row[2]));
                assertTrue(row[2]+" must construct the actual noble, including inherited factories",constructs(jar,host(jar,row[2]),NPC+"LOTREntity"+row[3]));
            }
        }
    }
    @Test public void twentyTwoFactionsHaveNativeCivilianMasterTypesWithGenerationReferences()throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();KOMEProgressionHardeningNativeFixture nativeData=new KOMEProgressionHardeningNativeFixture();JarFile jar=new JarFile("libs/LOTRMod v36.15.jar")) {
            KOMEAccessFixture f=new KOMEAccessFixture();f.world.isRemote=true;int count=0;
            for(String[] row:ROWS)if(!row[1].isEmpty()) {
                LOTREntityNPC master=npc(f.world,row[1]);
                assertTrue(row[0]+" / "+row[1],KOMESerfdomMasterService.ordinaryMaster(master));
                assertTrue(row[0],KOMEProgressionFactionResolver.matches(row[0],master.getFaction()));
                String needle=NPC+"LOTREntity"+row[1];boolean referenced=false;
                for(Enumeration<JarEntry> e=jar.entries();e.hasMoreElements()&&!referenced;) {
                    String name=e.nextElement().getName();if(!name.endsWith(".class")||!name.startsWith("lotr/common/world/"))continue;
                    for(MethodNode m:node(jar,name.substring(0,name.length()-6)).methods)for(AbstractInsnNode i:m.instructions.toArray()) {
                        if(i instanceof TypeInsnNode&&needle.equals(((TypeInsnNode)i).desc))referenced=true;
                        if(i instanceof LdcInsnNode&&((LdcInsnNode)i).cst instanceof org.objectweb.asm.Type
                                &&needle.equals(((org.objectweb.asm.Type)((LdcInsnNode)i).cst).getInternalName()))referenced=true;
                    }
                }
                assertTrue(row[1]+" must be referenced by native world generation/spawn code",referenced);count++;
            }
            assertEquals(22,count);
        }
    }
    @Test public void fangornAndHalfTrollCandidatesDoNotGainArtificialMasterExceptions()throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems()) {
            KOMEAccessFixture f=new KOMEAccessFixture();f.world.isRemote=true;
            for(String name:new String[]{"Ent","Huorn","HalfTroll","HalfTrollWarrior","HalfTrollScavenger","HalfTrollWarlord"}) {
                LOTREntityNPC candidate=npc(f.world,name);assertFalse(name,KOMESerfdomMasterService.ordinaryMaster(candidate));
                if("Ent".equals(name)||"Huorn".equals(name))assertFalse(KOMEProgressionLords.isStandingTrialLiegeCandidate(candidate));
            }
            assertFalse(KOMEProgressionServiceLocation.supportedHosts().containsValue("fangorn"));
        }
    }
    @Test public void allowlistedWatchtowersDoNotAutomaticallySupplyNobles()throws Exception {
        try(JarFile jar=new JarFile("libs/LOTRMod v36.15.jar")) {
            assertFalse(constructs(jar,host(jar,"RohanWatchtower"),NPC+"LOTREntityRohirrimMarshal"));
            assertFalse(constructs(jar,host(jar,"DaleWatchtower"),NPC+"LOTREntityDaleCaptain"));
        }
    }
}
