package kome.common.data;
import static org.junit.Assert.*;import java.util.UUID;import net.minecraft.nbt.NBTTagCompound;import org.junit.Test;
public class KOMESerfCourierAssignmentTest {
 private KOMEProgressionNpcRef master(){return new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Aldor","rohan",0,10,64,10);}
 @Test public void payloadRoundTripPreservesOneAssignmentTokenAndDistancePolicy(){KOMESerfCourierAssignment a=KOMESerfCourierAssignment.create(master());KOMESerfCourierAssignment b=KOMESerfCourierAssignment.readFromNBT(a.writeToNBT());assertNotNull(b);assertEquals(a.token,b.token);assertEquals(KOMESerfCourierAssignment.MIN_COURIER_DISTANCE,b.minimumDistance);assertFalse(b.farEnough(0,265,10));assertTrue(b.farEnough(0,266,10));assertFalse(b.farEnough(1,1000,10));}
 @Test public void malformedOrWrongRulePayloadFailsClosed(){KOMESerfCourierAssignment a=KOMESerfCourierAssignment.create(master());NBTTagCompound bad=a.writeToNBT();bad.setString("RecipientRuleKey","cross_faction");assertNull(KOMESerfCourierAssignment.readFromNBT(bad));}
 @Test public void deliveryStatePersistsRecipientWithoutCompletingDuty(){KOMESerfCourierAssignment a=KOMESerfCourierAssignment.create(master());a.recipient=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Recipient","rohan",0,300,64,10);a.stage=KOMESerfCourierAssignment.Stage.DELIVERED;KOMESerfCourierAssignment b=KOMESerfCourierAssignment.readFromNBT(a.writeToNBT());assertEquals(KOMESerfCourierAssignment.Stage.DELIVERED,b.stage);assertTrue(b.recipient.isSet());}
}
