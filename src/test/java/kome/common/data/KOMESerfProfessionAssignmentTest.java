package kome.common.data;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMESerfProfessionAssignmentTest {
    private static KOMESerfProfessionAssignment.Candidate c(String key,int damage,int max,KOMESerfProfessionAssignment.QuantityTier tier){return new KOMESerfProfessionAssignment.Candidate(key,key.hashCode(),damage,max,key,tier);}
    private static List<KOMESerfProfessionAssignment.Candidate> pool(){return Arrays.asList(c("stone",0,64,KOMESerfProfessionAssignment.QuantityTier.BULK_COMMON),c("iron",0,64,KOMESerfProfessionAssignment.QuantityTier.STANDARD),c("leather",0,64,KOMESerfProfessionAssignment.QuantityTier.STANDARD),c("copper",3,64,KOMESerfProfessionAssignment.QuantityTier.UNCOMMON),c("pearl",0,64,KOMESerfProfessionAssignment.QuantityTier.RARE),c("tool",0,1,KOMESerfProfessionAssignment.QuantityTier.NONSTACKABLE));}
    private static KOMESerfProfessionAssignment generate(long seed){return KOMESerfProfessionAssignment.generate("smith","Blacksmith","smith",pool(),new Random(seed));}
    private static KOMESerfProfessionAssignment.CandidateResolver resolver(){final Map<String,KOMESerfProfessionAssignment.Candidate> map=new HashMap<String,KOMESerfProfessionAssignment.Candidate>();for(KOMESerfProfessionAssignment.Candidate candidate:pool())map.put(candidate.key,candidate);return new KOMESerfProfessionAssignment.CandidateResolver(){public KOMESerfProfessionAssignment.Candidate resolve(String key,int id,int damage){KOMESerfProfessionAssignment.Candidate value=map.get(key);return value!=null&&value.damage==damage?value:null;}};}
    private static KOMEProgressionNpcRef master(){return new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Aldor","rohan",0,0,0,0);}
    private static void finishProvisioning(KOMESerfKnightProgression state,long day){assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.PROVISIONING,new NBTTagCompound(),day).success);assertTrue(KOMESerfKnightService.completeDuty(state,KOMESerfKnightDutyType.PROVISIONING).success);}

    @Test public void classifierOrderingCoversEveryDetectionBoundary(){
        KOMESerfProfessionClassifier.Profession miner=KOMESerfProfessionClassifier.forKey("miner"),baker=KOMESerfProfessionClassifier.forKey("baker");
        assertEquals("smith",KOMESerfProfessionClassifier.classifySignals(true,true,true,true,miner,baker,true).key);
        assertEquals("bartender",KOMESerfProfessionClassifier.classifySignals(false,true,true,true,miner,baker,true).key);
        assertEquals("farmhand",KOMESerfProfessionClassifier.classifySignals(false,false,true,true,miner,baker,true).key);
        assertEquals("farmer",KOMESerfProfessionClassifier.classifySignals(false,false,false,true,miner,baker,true).key);
        assertEquals("miner",KOMESerfProfessionClassifier.classifySignals(false,false,false,false,miner,baker,true).key);
        assertEquals("baker",KOMESerfProfessionClassifier.classifySignals(false,false,false,false,null,baker,true).key);
        assertEquals("merchant",KOMESerfProfessionClassifier.classifySignals(false,false,false,false,null,null,true).key);
        assertEquals("general_labor",KOMESerfProfessionClassifier.classifySignals(false,false,false,false,null,null,false).key);
        assertEquals("smith",KOMESerfProfessionClassifier.forKey("smith").materialProfile.key);
        assertEquals("brewing",KOMESerfProfessionClassifier.forKey("vintner").materialProfile.key);
        assertEquals("building",KOMESerfProfessionClassifier.forKey("builder").materialProfile.key);
    }

    @Test public void runtimeClassifierUsesSemanticInterfacesConcreteClassesPoolsAndNoNameParsing() throws Exception {
        String source=source("src/main/java/kome/common/data/KOMESerfProfessionClassifier.java");
        assertTrue(source.contains("instanceof LOTRTradeable.Smith"));assertTrue(source.contains("instanceof LOTRTradeable.Bartender"));assertTrue(source.contains("instanceof LOTRFarmhand"));assertTrue(source.contains("entry.task!=LOTRHiredNPCInfo.Task.FARMER"));assertTrue(source.contains("concreteProfession(npc)"));assertTrue(source.contains("tradePoolProfession(npc)"));assertTrue(source.contains("instanceof LOTRTravellingTrader"));assertFalse(source.contains("getNPCName()"));assertFalse(source.contains("getSimpleName()"));
    }

    @Test public void pureGenerationIsDeterministicDistinctTieredAndEarlyGame(){
        KOMESerfProfessionAssignment a=generate(4),b=generate(4);assertEquals(a.writeToNBT().toString(),b.writeToNBT().toString());assertEquals(3,a.requirements.size());Set<String> keys=new HashSet<String>();for(KOMESerfProfessionAssignment.Requirement requirement:a.requirements){assertTrue(keys.add(requirement.itemKey));assertTrue(requirement.required>0);assertFalse(requirement.itemKey.toLowerCase().contains("mithril"));}assertEquals(3,keys.size());
        for(KOMESerfProfessionAssignment.QuantityTier tier:KOMESerfProfessionAssignment.QuantityTier.values()){KOMESerfProfessionAssignment.Candidate candidate=c(tier.name(),0,tier==KOMESerfProfessionAssignment.QuantityTier.NONSTACKABLE?1:64,tier);for(int i=0;i<20;i++){int quantity=KOMESerfProfessionAssignment.quantity(candidate,new Random(i));assertTrue(quantity>=tier.minimum&&quantity<=tier.maximum);}}
        assertEquals("botanical",KOMESerfProfessionCatalog.fallbackKind("fangorn"));assertEquals("orc_industrial",KOMESerfProfessionCatalog.fallbackKind("mordor"));assertEquals("southern",KOMESerfProfessionCatalog.fallbackKind("morwaith"));
    }

    @Test public void persistenceRoundTripPreservesTradeProfileIdentityMetadataAndPartialProgress(){
        KOMESerfProfessionAssignment a=generate(7);a.requirements.get(0).delivered=7;KOMESerfProfessionAssignment b=KOMESerfProfessionAssignment.readFromNBT(a.writeToNBT(),resolver());assertNotNull(b);assertEquals("smith",b.tradeKey);assertEquals("Blacksmith",b.tradeDisplayName);assertEquals("smith",b.materialProfileKey);assertEquals(3,b.requirements.size());assertEquals(a.requirements.get(0).itemKey,b.requirements.get(0).itemKey);assertEquals(a.requirements.get(0).damage,b.requirements.get(0).damage);assertEquals(KOMESerfProfessionAssignment.EXACT_METADATA,b.requirements.get(0).metadataPolicy);assertEquals(a.requirements.get(0).required,b.requirements.get(0).required);assertEquals(7,b.requirements.get(0).delivered);assertEquals(a.requirements.get(0).displayName,b.requirements.get(0).displayName);
    }

    @Test public void malformedPayloadFailsClosed(){
        NBTTagCompound missing=generate(1).writeToNBT();missing.removeTag("Requirements");assertNull(KOMESerfProfessionAssignment.readFromNBT(missing,resolver()));NBTTagCompound badVersion=generate(2).writeToNBT();badVersion.setInteger("Version",99);assertNull(KOMESerfProfessionAssignment.readFromNBT(badVersion,resolver()));NBTTagCompound badMetadata=generate(3).writeToNBT();badMetadata.getTagList("Requirements",10).getCompoundTagAt(0).setString("MetadataPolicy","wildcard");assertNull(KOMESerfProfessionAssignment.readFromNBT(badMetadata,resolver()));NBTTagCompound overCredit=generate(4).writeToNBT();NBTTagCompound requirement=overCredit.getTagList("Requirements",10).getCompoundTagAt(0);requirement.setInteger("Delivered",requirement.getInteger("Required")+1);assertNull(KOMESerfProfessionAssignment.readFromNBT(overCredit,resolver()));
    }

    @Test public void matchingAndPlanRequireExactMetadataCombineStacksAndPreserveSurplusWithoutEarlyCredit(){
        KOMESerfProfessionAssignment a=generate(5);KOMESerfProfessionAssignment.Requirement requirement=a.requirements.get(0);requirement.delivered=requirement.required-25;assertTrue(KOMESerfProfessionService.matchesIdentity(new KOMESerfProfessionService.StackView(requirement.itemId,requirement.damage,1),requirement));assertFalse(KOMESerfProfessionService.matchesIdentity(new KOMESerfProfessionService.StackView(requirement.itemId,requirement.damage+1,1),requirement));assertFalse(KOMESerfProfessionService.matchesIdentity(new KOMESerfProfessionService.StackView(requirement.itemId+1,requirement.damage,1),requirement));int before=requirement.delivered;KOMESerfProfessionService.StackView[] stacks={new KOMESerfProfessionService.StackView(requirement.itemId,requirement.damage,10),new KOMESerfProfessionService.StackView(12345,0,99),new KOMESerfProfessionService.StackView(requirement.itemId,requirement.damage,30)};int[] plan=KOMESerfProfessionService.calculateTakeAmounts(a,stacks);assertArrayEquals(new int[]{10,0,15},plan);assertEquals(before,requirement.delivered);
    }

    @Test public void persistedProfessionDoesNotRerollAndCompletionPreservesCadence(){
        KOMEPlayerProgression player=new KOMEPlayerProgression();player.setCanonicalRank(KOMEProgressionRank.SERF);KOMESerfKnightProgression state=player.getSerfKnightProgression();KOMEProgressionNpcRef master=master();assertTrue(KOMESerfKnightService.setSerfdomMaster(state,master).success);finishProvisioning(state,10L);KOMESerfProfessionAssignment first=generate(1),replacement=generate(9);assertTrue(KOMESerfdomMasterService.requestDuty(player,master,11L,first.writeToNBT(),new Random(1)).success);String persisted=state.getDuty(KOMESerfKnightDutyType.PROFESSION).getAssignmentData().toString();assertFalse(KOMESerfdomMasterService.requestDuty(player,master,12L,replacement.writeToNBT(),new Random(9)).success);assertEquals(persisted,state.getDuty(KOMESerfKnightDutyType.PROFESSION).getAssignmentData().toString());assertFalse(first.complete());assertFalse(KOMESerfProfessionService.completeIfReady(player,first));for(KOMESerfProfessionAssignment.Requirement requirement:first.requirements)requirement.delivered=requirement.required;state.setDutyAssignmentData(KOMESerfKnightDutyType.PROFESSION,first.writeToNBT());assertTrue(KOMESerfProfessionService.completeIfReady(player,first));assertFalse(KOMESerfProfessionService.completeIfReady(player,first));assertEquals(11L,state.getLastAssignmentEpochDay());assertFalse(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.COURIER,null,11L).success);assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.COURIER,null,12L).success);
    }

    @Test public void leaveAndDeathUseGenericDutyLifecycle(){
        KOMESerfKnightProgression leave=new KOMESerfKnightProgression();KOMEProgressionNpcRef leaveMaster=master();assertTrue(KOMESerfKnightService.setSerfdomMaster(leave,leaveMaster).success);finishProvisioning(leave,10L);assertTrue(KOMESerfKnightService.assignDuty(leave,KOMESerfKnightDutyType.PROFESSION,generate(2).writeToNBT(),11L).success);assertTrue(KOMESerfKnightService.leaveSerfdomMaster(leave).success);assertFalse(leave.getDuty(KOMESerfKnightDutyType.PROFESSION).isAssigned());assertEquals(11L,leave.getLastAssignmentEpochDay());
        KOMESerfKnightProgression unfinished=new KOMESerfKnightProgression();KOMEProgressionNpcRef dead=master();assertTrue(KOMESerfKnightService.setSerfdomMaster(unfinished,dead).success);finishProvisioning(unfinished,20L);assertTrue(KOMESerfKnightService.assignDuty(unfinished,KOMESerfKnightDutyType.PROFESSION,generate(3).writeToNBT(),21L).success);assertTrue(KOMESerfKnightService.handleNpcDeath(unfinished,dead.entityUuid,false,21L));assertFalse(unfinished.getDuty(KOMESerfKnightDutyType.PROFESSION).isAssigned());assertEquals(21L,unfinished.getLastAssignmentEpochDay());
        KOMESerfKnightProgression completed=new KOMESerfKnightProgression();KOMEProgressionNpcRef completedMaster=master();assertTrue(KOMESerfKnightService.setSerfdomMaster(completed,completedMaster).success);finishProvisioning(completed,30L);assertTrue(KOMESerfKnightService.assignDuty(completed,KOMESerfKnightDutyType.PROFESSION,generate(4).writeToNBT(),31L).success);assertTrue(KOMESerfKnightService.completeDuty(completed,KOMESerfKnightDutyType.PROFESSION).success);assertTrue(KOMESerfKnightService.handleNpcDeath(completed,completedMaster.entityUuid,false,31L));assertTrue(completed.getDuty(KOMESerfKnightDutyType.PROFESSION).isCompleted());
    }

    @Test public void summaryMenuSpeechAndSecurityRemainServerAuthoritative() throws Exception {
        KOMESerfProfessionAssignment assignment=generate(6);assignment.requirements.get(0).delivered=5;String summary=KOMEProgressionSummary.professionText("Rank: Serf\nSerfdom Master: Aldor",assignment);assertTrue(summary.contains("Current Duty: Profession"));assertTrue(summary.contains("Master's Trade: Blacksmith"));for(KOMESerfProfessionAssignment.Requirement material:assignment.requirements)assertTrue(summary.contains(material.displayName+": "+material.delivered+" / "+material.required));assertFalse(summary.contains("Sneak-right-click"));
        String gui=source("src/main/java/kome/client/gui/KOMEGuiSerfdomMaster.java"),packet=source("src/main/java/kome/common/network/KOMEPacketSerfdomMasterAction.java"),speech=source("src/main/java/kome/common/data/KOMEProgressionNpcSpeech.java"),catalog=source("src/main/java/kome/common/data/KOMESerfProfessionCatalog.java");assertTrue(gui.contains("Deliver materials"));assertTrue(gui.contains("dutyStatus.startsWith(\"Profession duty\")"));assertTrue(packet.contains("validateCurrentMasterInteraction"));assertTrue(packet.contains("hasSameIdentity"));assertTrue(packet.contains("KOMESerfProfessionAssignment.readFromNBT"));assertTrue(packet.contains("KOMESerfProfessionService.deliver"));assertTrue(packet.contains("completeIfReady(progression,assignment)"));assertTrue(packet.indexOf("validateCurrentMasterInteraction")<packet.indexOf("message.action==DELIVER_PROFESSION"));assertTrue(speech.contains("materials for my trade"));assertTrue(speech.contains("still waiting on those materials"));assertTrue(speech.contains("no use for what you carry"));assertTrue(speech.contains("That will serve"));assertFalse(catalog.toLowerCase().contains("mithril"));
    }
    private static String source(String path)throws Exception{return new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8);}
}
