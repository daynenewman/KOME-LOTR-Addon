package kome.common.data;

import java.lang.reflect.Method;
import java.util.UUID;
import kome.common.network.KOMEPacketSerfdomMasterAction;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

/** The Master menu must publish actual implemented ordinary objectives without mutating authority. */
public class KOMEMasterDutyPresentationTest {
    @Test public void ordinaryObjectiveMenuUsesSavedInstructionsAndRetainsReportAction() throws Exception {
        Method status=KOMEPacketSerfdomMasterAction.class.getDeclaredMethod("dutyStatus",KOMESerfKnightProgression.class);
        status.setAccessible(true);
        for(KOMESerfKnightDutyType type:new KOMESerfKnightDutyType[]{
                KOMESerfKnightDutyType.ESCORT,KOMESerfKnightDutyType.RECOVERY,KOMESerfKnightDutyType.DEFENSE}) {
            KOMESerfKnightProgression state=new KOMESerfKnightProgression();
            KOMEProgressionNpcRef master=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Master","rohan",0,0,65,0);
            state.setSerfdomMaster(master);
            NBTTagCompound data=new NBTTagCompound();data.setBoolean("OrdinaryDuty",true);
            data.setDouble(KOMESerfKnightEscortService.DEST_X,400);
            data.setDouble(KOMESerfKnightEscortService.DEST_Z,400);
            data.setString(KOMESerfKnightEscortService.DEST_NAME,"the saved refuge");
            data.setTag(KOMEProgressionDestinations.PROOF,new NBTTagCompound());
            KOMESerfKnightTrialAssignment assignment=KOMESerfKnightTrialAssignment.create(
                KOMESerfKnightTrial.forId(type.key),master,1,0)
                .withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data);
            state.assignDuty(type,assignment.writeToNBT());state.setTrial(assignment);
            NBTTagCompound before=state.writeToNBT();
            String text=(String)status.invoke(null,state);
            assertFalse(type+": "+text,text.contains("details pending"));
            assertTrue(text,text.startsWith(type.displayName+" duty - "));
            if(type==KOMESerfKnightDutyType.ESCORT)assertTrue(text,text.contains("the saved refuge"));
            else if(type==KOMESerfKnightDutyType.RECOVERY)assertTrue(text,text.contains("Find what was lost"));
            else assertTrue(text,text.contains("Defeat those who threaten"));
            assertEquals(before,state.writeToNBT());
            KOMESerfKnightService.markTrialObjectiveComplete(state);
            assertTrue(((String)status.invoke(null,state)).startsWith("Duty complete"));
            assertFalse(state.getDuty(type).isCompleted()); // Menu projection cannot grant the report credit.
        }
    }
}
