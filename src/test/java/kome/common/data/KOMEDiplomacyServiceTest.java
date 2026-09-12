package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class KOMEDiplomacyServiceTest {
    @Test public void absentPairsAreNeutralWithoutCreatingState() {
        KOMEWorldData data=new KOMEWorldData("d");
        assertEquals(KOMEDiplomacyRelation.NEUTRAL,KOMEDiplomacyService.getRelation(data,"Gondor","Rohan"));
        assertTrue(data.canonicalDiplomacyRecords.isEmpty()); assertEquals(KOMEDiplomacyRelation.ALLIES,KOMEDiplomacyService.getRelation(data,"Gondor","Gondor"));
    }
    @Test public void kingRequestAndReceivingKingAcceptanceAreBilateral() {
        KOMEWorldData data=new KOMEWorldData("d"); UUID g=UUID.randomUUID(),r=UUID.randomUUID(); data.reconcilePlayerKingship("gondor",g,"G",true); data.reconcilePlayerKingship("rohan",r,"R",true);
        KOMEDiplomacyService.Result request=KOMEDiplomacyService.requestIncrease(data,"gondor","rohan",KOMEDiplomacyRelation.ALLIES,g,10L);
        assertTrue(request.accepted); assertEquals(KOMEDiplomacyRelation.NEUTRAL,KOMEDiplomacyService.getRelation(data,"rohan","gondor"));
        assertFalse(KOMEDiplomacyService.acceptPendingIncrease(data,"gondor","rohan",g,11L).accepted);
        assertTrue(KOMEDiplomacyService.acceptPendingIncrease(data,"rohan","gondor",r,12L).accepted); assertEquals(KOMEDiplomacyRelation.ALLIES,KOMEDiplomacyService.getRelation(data,"rohan","gondor")); assertNull(data.canonicalDiplomacyRecords.get("gondor|rohan").pendingTarget);
    }
    @Test public void requestsRequireKingsAndReceiverKing() {
        KOMEWorldData data=new KOMEWorldData("d"); UUID g=UUID.randomUUID(); data.reconcilePlayerKingship("gondor",g,"G",true);
        assertFalse(KOMEDiplomacyService.requestIncrease(data,"gondor","rohan",KOMEDiplomacyRelation.FRIENDS,UUID.randomUUID(),1L).accepted);
        assertFalse(KOMEDiplomacyService.requestIncrease(data,"gondor","rohan",KOMEDiplomacyRelation.FRIENDS,g,1L).accepted);
        assertFalse(KOMEDiplomacyService.requestIncrease(data,"gondor","gondor",KOMEDiplomacyRelation.ALLIES,g,1L).accepted);
    }
    @Test public void canonicalPersistenceRoundTripsPendingAndAcceptedRecords() {
        KOMEWorldData data=new KOMEWorldData("d"); UUID g=UUID.randomUUID(),r=UUID.randomUUID(); data.reconcilePlayerKingship("gondor",g,"G",true);data.reconcilePlayerKingship("rohan",r,"R",true); KOMEDiplomacyService.requestIncrease(data,"gondor","rohan",KOMEDiplomacyRelation.FRIENDS,g,42L);
        NBTTagCompound n=new NBTTagCompound();data.writeToNBT(n);KOMEWorldData loaded=new KOMEWorldData("l");loaded.readFromNBT(n);assertEquals(KOMEDiplomacyRelation.NEUTRAL,KOMEDiplomacyService.getRelation(loaded,"rohan","gondor"));assertEquals(KOMEDiplomacyRelation.FRIENDS,loaded.canonicalDiplomacyRecords.get("gondor|rohan").pendingTarget);
        NBTTagCompound malformed=new NBTTagCompound(); malformed.setString("FactionA","gondor");malformed.setString("FactionB","gondor");malformed.setString("Relation","allies"); KOMEWorldData bad=new KOMEWorldData("b"); NBTTagCompound root=new NBTTagCompound(); net.minecraft.nbt.NBTTagList list=new net.minecraft.nbt.NBTTagList();list.appendTag(malformed);root.setTag("CanonicalDiplomacyRecords",list);bad.readFromNBT(root);assertTrue(bad.canonicalDiplomacyRecords.isEmpty());
    }
}
