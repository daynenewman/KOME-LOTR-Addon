package kome.common.data;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMESerfKnightCadenceOverrideTest {
    private static KOMEProgressionNpcRef ref(String name){return new KOMEProgressionNpcRef(UUID.randomUUID().toString(),name,"rohan",0,0,64,0);}
    @After public void clear(){KOMESerfKnightCadenceOverride.clearAll();}

    @Test public void normalDailyGateStillBlocksDutyAndTrialAssignments(){
        KOMESerfKnightProgression state=new KOMESerfKnightProgression();assertTrue(KOMESerfKnightService.setSerfdomMaster(state,ref("Master")).success);
        assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.PROVISIONING,null,10L).success);assertTrue(KOMESerfKnightService.completeDuty(state,KOMESerfKnightDutyType.PROVISIONING).success);assertFalse(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.PROFESSION,null,10L).success);
        finishDuties(state,11L);assertTrue(KOMESerfKnightService.setProspectiveLiege(state,ref("Liege")).success);assertTrue(KOMESerfKnightService.assignTrial(state,new Random(1L),20L).success);
        assertTrue(KOMESerfKnightService.completeTrial(state).success);KOMESerfKnightService.leaveProspectiveLiege(state);assertTrue(KOMESerfKnightService.setProspectiveLiege(state,ref("Replacement")).success);assertFalse(KOMESerfKnightService.assignTrial(state,new Random(2L),20L).success);
    }

    @Test public void overrideIsPerPlayerAndDoesNotMutateStoredCadence(){
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();KOMESerfKnightProgression state=new KOMESerfKnightProgression();assertTrue(KOMESerfKnightService.setSerfdomMaster(state,ref("Master")).success);assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.PROVISIONING,null,10L,first).success);assertTrue(KOMESerfKnightService.completeDuty(state,KOMESerfKnightDutyType.PROVISIONING).success);long stored=state.getLastAssignmentEpochDay();assertFalse(KOMESerfKnightService.mayIssueAssignment(state,10L,second));KOMESerfKnightCadenceOverride.set(first,true);assertTrue(KOMESerfKnightService.mayIssueAssignment(state,10L,first));assertFalse(KOMESerfKnightService.mayIssueAssignment(state,10L,second));assertEquals(stored,state.getLastAssignmentEpochDay());KOMESerfKnightCadenceOverride.set(first,false);assertFalse(KOMESerfKnightService.mayIssueAssignment(state,10L,first));
    }

    @Test public void overridePermitsAnotherDutySameDayAndTrialSameDayAfterOtherRequirements(){
        UUID player=UUID.randomUUID();KOMESerfKnightProgression state=new KOMESerfKnightProgression();assertTrue(KOMESerfKnightService.setSerfdomMaster(state,ref("Master")).success);assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.PROVISIONING,null,10L,player).success);assertTrue(KOMESerfKnightService.completeDuty(state,KOMESerfKnightDutyType.PROVISIONING).success);KOMESerfKnightCadenceOverride.set(player,true);assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.PROFESSION,null,10L,player).success);assertTrue(KOMESerfKnightService.completeDuty(state,KOMESerfKnightDutyType.PROFESSION).success);assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.COURIER,null,10L,player).success);assertTrue(KOMESerfKnightService.completeDuty(state,KOMESerfKnightDutyType.COURIER).success);assertTrue(KOMESerfKnightService.setProspectiveLiege(state,ref("Liege")).success);assertTrue(KOMESerfKnightService.assignTrial(state,new Random(1L),10L,player).success);assertTrue(KOMESerfKnightService.completeTrial(state).success);KOMESerfKnightService.leaveProspectiveLiege(state);assertTrue(KOMESerfKnightService.setProspectiveLiege(state,ref("Replacement")).success);assertTrue(KOMESerfKnightService.assignTrial(state,new Random(2L),10L,player).success);KOMESerfKnightCadenceOverride.set(player,false);assertTrue(KOMESerfKnightService.completeTrial(state).success);KOMESerfKnightService.leaveProspectiveLiege(state);assertTrue(KOMESerfKnightService.setProspectiveLiege(state,ref("Final")).success);assertFalse(KOMESerfKnightService.assignTrial(state,new Random(3L),10L,player).success);
    }

    @Test public void commandIsStaffOnlyServerHierarchyAndRuntimeOnly() throws Exception {
        String command=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/command/KOMECommandKome.java")),StandardCharsets.UTF_8);assertTrue(command.contains("progression cooldown <on|off>"));assertTrue(command.contains("requireStaff(sender)"));assertTrue(command.contains("KOMESerfKnightCadenceOverride.set"));String events=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEEvents.java")),StandardCharsets.UTF_8);assertTrue(events.contains("KOMESerfKnightCadenceOverride.clear"));String service=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMESerfKnightCadenceOverride.java")),StandardCharsets.UTF_8);assertFalse(service.contains("writeToNBT"));assertFalse(service.contains("EntityPlayerSP"));
    }

    private static void finishDuties(KOMESerfKnightProgression state,long day){for(KOMESerfKnightDutyType type:new KOMESerfKnightDutyType[]{KOMESerfKnightDutyType.PROFESSION,KOMESerfKnightDutyType.COURIER}){assertTrue(KOMESerfKnightService.assignDuty(state,type,null,day++).success);assertTrue(KOMESerfKnightService.completeDuty(state,type).success);}}
}
