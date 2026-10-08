package kome.common.command;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import kome.common.KOMEAccessFixture;
import kome.common.data.*;
import net.minecraft.command.WrongUsageException;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises the real command dispatcher, including operator-only mutation paths. */
public class KOMECommandRetreatReservationTest {
    private static final class Fixture {
        final KOMEAccessFixture access = new KOMEAccessFixture();
        final KOMEArmyMovementOrder order = order("M1", "C1");
        Fixture() throws Exception { access.player.operator=true; }
        KOMEArmyMovementOrder order(String id,String companyId) {
            KOMEArmyMovementOrder o=new KOMEArmyMovementOrder();
            o.id=id;o.companyId=companyId;o.owner=access.player.id;
            o.status=KOMEArmyMovementOrder.WAITING_NEXT_STEP;
            o.originTile="T101";o.destinationTile="T100";
            o.currentStepOriginTile="T101";o.currentStepDestinationTile="T100";
            o.routeTiles.addAll(Arrays.asList("T101","T100"));
            KOMEArmyCompany c=new KOMEArmyCompany();c.id=companyId;c.owner=o.owner;
            c.faction="gondor";c.currentTile="T101";c.movementOrderId=id;
            KOMEHiredUnitRecord u=new KOMEHiredUnitRecord();u.entity=UUID.randomUUID();
            u.owner=o.owner;u.companyId=companyId;u.currentTile="T101";u.movementOrderId=id;
            o.units.add(u.entity);c.units.add(u.entity);
            access.data.armyMovements.put(id,o);access.data.armyCompanies.put(companyId,c);
            access.data.hiredUnits.put(u.entity,u);return o;
        }
        void reserve(KOMEFormalRetreatBatch.Progress progress,boolean finalized) {
            order.formalRetreatBatch=new KOMEFormalRetreatBatch(UUID.randomUUID(),access.player.id,
                "CF1","T101","",1L,Collections.singletonList(new KOMEFormalRetreatBatch.Member(
                    "C1","M1",access.player.id,Arrays.asList("T101","T100"),1,0,progress)),
                finalized?KOMEFormalRetreatBatch.Phase.FINALIZED:KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING,false);
        }
        void command(String... args) { new KOMECommandTroops().processCommand(access.player,args); }
        void denied(String... args) {
            NBTTagCompound before=order.writeToNBT();
            try { command(args);fail("Reserved movement mutated: "+Arrays.toString(args)); }
            catch(WrongUsageException expected) {
                assertEquals("This movement order is reserved by an unfinished Formal Retreat.",expected.getMessage());
            }
            assertEquals(before,order.writeToNBT());
            assertEquals("M1",access.data.armyCompanies.get("C1").movementOrderId);
            assertEquals("M1",access.data.hiredUnits.get(order.units.get(0)).movementOrderId);
        }
    }
    @Test public void realStopRejectsEveryUnfinishedMemberPhaseEvenForOperator() throws Exception {
        for(KOMEFormalRetreatBatch.Progress phase:KOMEFormalRetreatBatch.Progress.values()) {
            Fixture f=new Fixture();f.reserve(phase,false);f.denied("movement","stop","M1");
        }
    }
    @Test public void alternateAndBulkCommandsCannotRewriteAcceptedAuthority() throws Exception {
        Fixture f=new Fixture();f.reserve(KOMEFormalRetreatBatch.Progress.RELEASED,false);
        KOMEArmyMovementOrder unrelated=f.order("M2","C2");NBTTagCompound before=unrelated.writeToNBT();
        for(String action:new String[]{"halt","next","retreat","resume","continue","stay",
                "retry","pause","cancelspawn","retarget","advance","complete"})
            f.denied("movement",action,"M1");
        f.denied("movement","ticknow");f.denied("movement","advanceall","1");
        f.denied("movecompany","C1","T100");
        assertEquals(before,unrelated.writeToNBT());
    }
    @Test public void reservationSurvivesMovementOrderPersistence() throws Exception {
        for(KOMEFormalRetreatBatch.Progress phase:KOMEFormalRetreatBatch.Progress.values()) {
            Fixture f=new Fixture();f.reserve(phase,false);
            if(phase!=KOMEFormalRetreatBatch.Progress.PREPARED) {
                NBTTagCompound release=new NBTTagCompound();release.setInteger("SchemaVersion",2);release.setBoolean("HasRelease",true);
                release.setString("ConflictId","CF1");release.setLong("ConflictRevision",1L);
                release.setString("OrderId","M1");release.setString("CompanyId","C1");
                release.setString("Outcome","FORMAL_RETREAT");release.setString("Code","CANCELED_BY_FORMAL_RETREAT");
                release.setString("Reason","test accepted batch");release.setLong("AppliedAtMillis",1L);
                f.order.conflictRelease=KOMEConflictMovementHandoff.Receipt.read(release);
            }
            KOMEArmyMovementOrder loaded=new KOMEArmyMovementOrder();loaded.readFromNBT(f.order.writeToNBT());
            f.access.data.armyMovements.put("M1",loaded);f.denied("movement","stop","M1");
            assertEquals(KOMEArmyMovementOrder.WAITING_NEXT_STEP,loaded.status);
        }
    }
    @Test public void nonHolderMemberRemainsReservedWhileHolderAlreadyComplete() throws Exception {
        Fixture f=new Fixture();KOMEArmyMovementOrder member=f.order("M2","C2");
        f.order.formalRetreatBatch=new KOMEFormalRetreatBatch(UUID.randomUUID(),f.access.player.id,
            "CF1","T101","",1L,Arrays.asList(
                new KOMEFormalRetreatBatch.Member("C1","M1",f.access.player.id,Arrays.asList("T101","T100"),1,0,KOMEFormalRetreatBatch.Progress.COMPLETE),
                new KOMEFormalRetreatBatch.Member("C2","M2",f.access.player.id,Arrays.asList("T101","T100"),1,0,KOMEFormalRetreatBatch.Progress.RELEASED)),
            KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING,false);
        NBTTagCompound before=member.writeToNBT();
        f.denied("movement","stop","M2");assertEquals(before,member.writeToNBT());
        assertEquals("M2",f.access.data.armyCompanies.get("C2").movementOrderId);
    }
    @Test public void finalizedAndUnrelatedOrdersKeepNormalStopBehavior() throws Exception {
        try(KOMEPopulationTestConfig config=new KOMEPopulationTestConfig()) {
            Fixture f=new Fixture();f.reserve(KOMEFormalRetreatBatch.Progress.COMPLETE,true);
            f.command("movement","stop","M1");assertEquals(KOMEArmyMovementOrder.STOPPED,f.order.status);
            assertEquals("",f.access.data.armyCompanies.get("C1").movementOrderId);
            Fixture other=new Fixture();other.reserve(KOMEFormalRetreatBatch.Progress.RELEASED,false);
            KOMEArmyMovementOrder unrelated=other.order("M2","C2");
            other.command("movement","stop","M2");assertEquals(KOMEArmyMovementOrder.STOPPED,unrelated.status);
            assertEquals("M1",other.access.data.armyCompanies.get("C1").movementOrderId);
        }
    }
}
