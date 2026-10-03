package kome.common.data;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import lotr.common.item.LOTRItemMug;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMESerfProvisioningAssignmentTest {
    @Test public void quantitiesUseTargetStackLimitIncludingSixteenStackFood(){
        Random random=new Random(42);for(int i=0;i<100;i++) {
            int single=KOMESerfProvisioningAssignment.foodQuantity(1,random),sixteen=KOMESerfProvisioningAssignment.foodQuantity(16,random),normal=KOMESerfProvisioningAssignment.foodQuantity(64,random);
            assertTrue(single>=3&&single<=5);assertTrue(sixteen>=16&&sixteen<=48);assertEquals(0,sixteen%16);assertTrue(normal>=64&&normal<=192);assertEquals(0,normal%64);
        }
        Item item=new Item(){@Override public int getItemStackLimit(ItemStack stack){return stack.getItemDamage()==2?16:64;}};
        assertEquals(16,KOMESerfProvisioningAssignment.Candidate.runtime(item,2).maxStack);
    }
    private static List<KOMESerfProvisioningAssignment.Candidate> foods(){return Arrays.asList(candidate("bread",64),candidate("apple",64),candidate("mutton",64),candidate("stew",1));}
    private static List<KOMESerfProvisioningAssignment.Candidate> drinks(){return Arrays.asList(candidate("ale",1),candidate("cider",1));}
    private static KOMESerfProvisioningAssignment.Candidate candidate(String key,int max){return new KOMESerfProvisioningAssignment.Candidate(key,key.hashCode(),0,max,key);}
    private static KOMESerfProvisioningAssignment generate(long seed){return KOMESerfProvisioningAssignment.generate(foods(),drinks(),Arrays.asList("MUG","BOTTLE"),new Random(seed));}
    private static KOMESerfProvisioningAssignment.CandidateResolver resolver(){final Map<String,KOMESerfProvisioningAssignment.Candidate> candidates=new HashMap<String,KOMESerfProvisioningAssignment.Candidate>();for(KOMESerfProvisioningAssignment.Candidate c:foods())candidates.put(c.key,c);for(KOMESerfProvisioningAssignment.Candidate c:drinks())candidates.put(c.key,c);return new KOMESerfProvisioningAssignment.CandidateResolver(){public KOMESerfProvisioningAssignment.Candidate resolve(String key,int id,int damage){return candidates.get(key);}};}

    @Test public void pureGenerationIsDeterministicDistinctAndUsesRequiredRanges(){KOMESerfProvisioningAssignment a=generate(4),b=generate(4);assertEquals(3,a.foods.size());assertNotNull(a.drink);assertEquals(a.writeToNBT().toString(),b.writeToNBT().toString());for(int i=0;i<3;i++){KOMESerfProvisioningAssignment.Requirement r=a.foods.get(i);if("stew".equals(r.itemKey)){assertTrue(r.required>=3);assertTrue(r.required<=5);}else{assertTrue(r.required>=64);assertTrue(r.required<=3*64);assertEquals(0,r.required%64);}for(int j=i+1;j<3;j++)assertNotEquals(r.itemKey,a.foods.get(j).itemKey);}assertTrue(a.drink.required>=3&&a.drink.required<=5);}

    @Test public void descriptorRoundTripPreservesIdentityCountsVesselAndPartialProgress(){KOMESerfProvisioningAssignment a=generate(8);a.foods.get(0).delivered=Math.min(2,a.foods.get(0).required);a.drink.delivered=2;KOMESerfProvisioningAssignment b=KOMESerfProvisioningAssignment.readFromNBT(a.writeToNBT(),resolver());assertNotNull(b);assertEquals(a.foods.get(0).itemKey,b.foods.get(0).itemKey);assertEquals(a.foods.get(0).damage,b.foods.get(0).damage);assertEquals(a.foods.get(0).delivered,b.foods.get(0).delivered);assertEquals(a.drink.itemKey,b.drink.itemKey);assertEquals(a.drink.vessel,b.drink.vessel);assertEquals(2,b.drink.delivered);}

    @Test public void malformedPayloadFailsClosed(){NBTTagCompound bad=generate(2).writeToNBT();bad.removeTag("Drink");assertNull(KOMESerfProvisioningAssignment.readFromNBT(bad,resolver()));NBTTagCompound badCount=generate(3).writeToNBT();badCount.getTagList("Foods",10).getCompoundTagAt(0).setInteger("Required",0);assertNull(KOMESerfProvisioningAssignment.readFromNBT(badCount,resolver()));}

    @Test public void pureMatchingRequiresExactFoodAndDrinkVessel(){KOMESerfProvisioningAssignment a=generate(2);KOMESerfProvisioningAssignment.Requirement food=a.foods.get(0),drink=a.drink;assertTrue(KOMESerfProvisioningService.matchesIdentity(new KOMESerfProvisioningService.StackView(food.itemId,food.damage,1,""),food));assertFalse(KOMESerfProvisioningService.matchesIdentity(new KOMESerfProvisioningService.StackView(food.itemId+1,food.damage,1,""),food));assertTrue(KOMESerfProvisioningService.matchesIdentity(new KOMESerfProvisioningService.StackView(drink.itemId,drink.damage,1,drink.vessel),drink));assertFalse(KOMESerfProvisioningService.matchesIdentity(new KOMESerfProvisioningService.StackView(drink.itemId+1,drink.damage,1,drink.vessel),drink));assertFalse(KOMESerfProvisioningService.matchesIdentity(new KOMESerfProvisioningService.StackView(drink.itemId,drink.damage,1,"SKIN"),drink));}

    @Test public void pureDeliveryPlanCombinesStacksKeepsSurplusAndDoesNotCreditDuringCalculation(){KOMESerfProvisioningAssignment a=generate(5);KOMESerfProvisioningAssignment.Requirement food=a.foods.get(0);food.delivered=food.required-25;KOMESerfProvisioningService.StackView[] stacks={new KOMESerfProvisioningService.StackView(food.itemId,food.damage,10,""),new KOMESerfProvisioningService.StackView(12345,0,99,""),new KOMESerfProvisioningService.StackView(food.itemId,food.damage,30,"")};int before=food.delivered;int[] plan=KOMESerfProvisioningService.calculateTakeAmounts(a,stacks);assertArrayEquals(new int[]{10,0,15},plan);assertEquals(before,food.delivered);}

    @Test public void persistedDutyDoesNotRerollOnRepeatedRequest(){KOMESerfKnightProgression state=new KOMESerfKnightProgression();KOMEProgressionNpcRef master=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Master","rohan",0,0,0,0);assertTrue(KOMESerfKnightService.setSerfdomMaster(state,master).success);NBTTagCompound first=generate(1).writeToNBT(),replacement=generate(9).writeToNBT();assertTrue(KOMESerfdomMasterService.requestDuty(state,master,10L,first,new Random(1)).success);String persisted=state.getDuty(KOMESerfKnightDutyType.PROVISIONING).getAssignmentData().toString();assertFalse(KOMESerfdomMasterService.requestDuty(state,master,10L,replacement,new Random(9)).success);assertEquals(persisted,state.getDuty(KOMESerfKnightDutyType.PROVISIONING).getAssignmentData().toString());}

    @Test public void allFourRequirementsGateCanonicalCompletionWithoutChangingCadence(){KOMESerfKnightProgression state=new KOMESerfKnightProgression();KOMEProgressionNpcRef master=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Master","rohan",0,0,0,0);assertTrue(KOMESerfKnightService.setSerfdomMaster(state,master).success);KOMESerfProvisioningAssignment a=generate(6);assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.PROVISIONING,a.writeToNBT(),10L).success);for(KOMESerfProvisioningAssignment.Requirement food:a.foods)food.delivered=food.required;a.drink.delivered=a.drink.required-1;assertFalse(a.complete());assertFalse(state.getDuty(KOMESerfKnightDutyType.PROVISIONING).isCompleted());a.drink.delivered=a.drink.required;state.setDutyAssignmentData(KOMESerfKnightDutyType.PROVISIONING,a.writeToNBT());assertTrue(a.complete());assertTrue(KOMESerfKnightService.completeDuty(state,KOMESerfKnightDutyType.PROVISIONING).success);assertEquals("",state.getActiveAssignmentKind());assertEquals(10L,state.getLastAssignmentEpochDay());assertFalse(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.PROFESSION,null,10L).success);}

    @Test public void leaveMasterClearsPayloadAndPartialProgressButKeepsConsumedDay(){KOMESerfKnightProgression state=new KOMESerfKnightProgression();KOMEProgressionNpcRef master=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Master","rohan",0,0,0,0);assertTrue(KOMESerfKnightService.setSerfdomMaster(state,master).success);KOMESerfProvisioningAssignment a=generate(7);a.foods.get(0).delivered=3;assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.PROVISIONING,a.writeToNBT(),12L).success);assertTrue(KOMESerfKnightService.leaveSerfdomMaster(state).success);assertFalse(state.getDuty(KOMESerfKnightDutyType.PROVISIONING).isAssigned());assertNull(state.getDuty(KOMESerfKnightDutyType.PROVISIONING).getAssignmentData());assertEquals(12L,state.getLastAssignmentEpochDay());}

    @Test public void approvedVesselsAreFactionSafe(){List<String> good=KOMESerfProvisioningCatalog.vesselsForFaction("rohan"),evil=KOMESerfProvisioningCatalog.vesselsForFaction("mordor");for(String basic:new String[]{"SKIN","BOTTLE","GOBLET_WOOD","MUG","HORN","GOBLET_COPPER"}){assertTrue(good.contains(basic));assertTrue(evil.contains(basic));}assertTrue(good.contains("GLASS"));assertFalse(good.contains("SKULL"));assertTrue(evil.contains("SKULL"));assertFalse(evil.contains("GLASS"));for(String luxury:new String[]{"MUG_CLAY","GOBLET_SILVER","GOBLET_GOLD","HORN_GOLD"}){assertFalse(good.contains(luxury));assertFalse(evil.contains(luxury));}}

    @Test public void nativeDrinkMetadataMatchesRequestedVesselAndPotency() {
        LOTRItemMug ale=new LOTRItemMug(1F);int id=Item.getIdFromItem(ale);
        KOMESerfProvisioningAssignment.Requirement r=new KOMESerfProvisioningAssignment.Requirement("ale",id,0,2,0,"Ale","BOTTLE",true,3);
        ItemStack stack=new ItemStack(ale,2,0);LOTRItemMug.setVessel(stack,LOTRItemMug.Vessel.BOTTLE,true);LOTRItemMug.setStrengthMeta(stack,3);
        assertEquals(LOTRItemMug.Vessel.BOTTLE,LOTRItemMug.getVessel(stack));assertEquals(3,LOTRItemMug.getStrengthMeta(stack));
        assertTrue(KOMESerfProvisioningService.matches(stack,r));
        assertTrue(r.description().contains("Bottle"));assertTrue(r.description().contains("Strong"));assertTrue(r.description().contains("Ale"));
        LOTRItemMug.setStrengthMeta(stack,1);assertFalse(KOMESerfProvisioningService.matches(stack,r));
        LOTRItemMug.setStrengthMeta(stack,3);LOTRItemMug.setVessel(stack,LOTRItemMug.Vessel.MUG,true);assertFalse(KOMESerfProvisioningService.matches(stack,r));
        assertEquals(2,r.required);assertEquals(0,r.delivered);
    }

    @Test public void nativeNonbrewableDrinkHasNoFakePotency() {
        LOTRItemMug juice=new LOTRItemMug(true,false);int id=Item.getIdFromItem(juice);
        KOMESerfProvisioningAssignment.Requirement r=new KOMESerfProvisioningAssignment.Requirement("juice",id,0,1,0,"Apple Juice","GOBLET_WOOD",true,-1);
        ItemStack stack=new ItemStack(juice,1,0);LOTRItemMug.setVessel(stack,LOTRItemMug.Vessel.GOBLET_WOOD,true);
        assertTrue(KOMESerfProvisioningService.matches(stack,r));
        assertFalse(r.description().contains("Strong"));assertFalse(r.description().contains("Moderate"));
        LOTRItemMug.setVessel(stack,LOTRItemMug.Vessel.MUG,true);assertFalse(KOMESerfProvisioningService.matches(stack,r));
    }

    @Test public void legacyDrinkRemainsBroadAndStaysBroadAfterSave() {
        NBTTagCompound old=generate(8).writeToNBT();old.removeTag("RequirementVersion");
        KOMESerfProvisioningAssignment legacy=KOMESerfProvisioningAssignment.readFromNBT(old,resolver());
        assertNotNull(legacy);assertEquals("",legacy.drink.vessel);assertEquals(-1,legacy.drink.strength);
        assertTrue(KOMESerfProvisioningService.matchesIdentity(new KOMESerfProvisioningService.StackView(legacy.drink.itemId,803,1,"BOTTLE",3),legacy.drink));
        assertTrue(legacy.drink.description().contains("any vessel or strength"));
        KOMESerfProvisioningAssignment again=KOMESerfProvisioningAssignment.readFromNBT(legacy.writeToNBT(),resolver());
        assertNotNull(again);assertEquals("",again.drink.vessel);assertTrue(again.drink.description().contains("any vessel or strength"));
    }

    @Test public void fallbackAndPresentationIntegrationRemainWired() throws Exception {assertTrue(foods().size()>=3);String summary=source("src/main/java/kome/common/data/KOMEProgressionSummary.java");assertTrue(summary.contains("Current Duty: Provisioning"));assertTrue(summary.contains("a.progressList"));String ranks=source("src/main/java/kome/common/data/KOMEProgressionRankSummary.java");assertTrue(ranks.contains("assignment.progressList"));String gui=source("src/main/java/kome/client/gui/KOMEGuiSerfdomMaster.java");assertTrue(gui.contains("Deliver provisions"));assertFalse(gui.contains("Highlight master"));String packet=source("src/main/java/kome/common/network/KOMEPacketSerfdomMasterAction.java");assertTrue(packet.contains("a.progressList"));assertTrue(packet.contains("KOMESerfProvisioningService.deliver"));assertTrue(packet.contains("completeDuty(progression,active)"));assertTrue(packet.contains("detectAndSendChanges"));}
    private static String source(String path)throws Exception{return new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8);}
}
