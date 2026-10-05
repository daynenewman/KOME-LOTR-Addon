package kome.common.data;

import java.util.Random;
import java.util.UUID;
import kome.common.KOMEAccessFixture;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMEProgressionLifecycleAuditTest {
    private static KOMEProgressionNpcRef npc(String name,String faction) {
        return new KOMEProgressionNpcRef(
            UUID.randomUUID().toString(),
            name,
            faction,
            0,
            10,
            64,
            10);
    }

    private static void completeDuties(KOMESerfKnightProgression state) {
        long day=10L;
        for(KOMESerfKnightDutyType type:KOMESerfKnightDutyType.values()) {
            assertTrue(
                KOMESerfKnightService.assignDuty(
                    state,type,null,day++).success);
            assertTrue(
                KOMESerfKnightService.completeDuty(
                    state,type).success);
        }
    }

    @Test
    public void postPromotionLiegeDeparturePreservesEarnedKnighthood() {
        KOMEPlayerProgression progression=new KOMEPlayerProgression();
        KOMESerfKnightProgression state=
            progression.getSerfKnightProgression();

        assertTrue(
            KOMESerfKnightService.setSerfdomMaster(
                state,npc("Master","rohan")).success);

        completeDuties(state);

        KOMEProgressionNpcRef liege=npc("Liege","rohan");

        assertTrue(
            KOMESerfKnightService.commitLiegeForTrial(
                state,liege).success);

        assertTrue(
            KOMESerfKnightService.assignTrial(
                state,new Random(1L),30L).success);

        assertTrue(KOMESerfKnightService.completeTrial(state).success);
        assertTrue(KOMESerfKnightService.recordPartingGift(state).success);
        assertTrue(KOMESerfKnightService.markPromoted(state,150D).success);

        state.retireSerfdomMasterAfterPromotion();
        progression.setCanonicalRank(KOMEProgressionRank.KNIGHT);

        String trial=state.getTrialId();

        assertTrue(
            KOMESerfKnightService.leaveLiege(
                progression).success);

        assertFalse(state.getLiege().isSet());
        assertEquals(trial,state.getTrialId());
        assertTrue(state.isTrialCompleted());
        assertTrue(state.hasPartingGift());
        assertTrue(state.isPromoted());
        assertEquals(KOMESerfKnightPhase.COMPLETE,state.getPhase());
        assertEquals(
            KOMEProgressionRank.KNIGHT,
            progression.getCanonicalRank());
    }

    @Test
    public void pledgeChangeRetiresIncompatibleSerfRelationshipWithoutDemotion()
            throws Exception {
        KOMEAccessFixture fixture=new KOMEAccessFixture();
        fixture.player.inventory=new InventoryPlayer(fixture.player);

        KOMEPlayerProgression progression=
            fixture.data.getProgression(fixture.player.id);

        progression.setCanonicalRank(KOMEProgressionRank.SERF);

        assertTrue(
            KOMESerfKnightService.setSerfdomMaster(
                progression.getSerfKnightProgression(),
                npc("Rohan Master","rohan")).success);

        progression.setPledgedLord(
            UUID.randomUUID().toString(),
            "Legacy Rohan Lord",
            "Rohan");

        assertTrue(
            KOMEProgressionRelationshipLifecycle.reconcilePledgeChange(
                fixture.data,
                fixture.player,
                "gondor"));

        assertEquals(
            KOMEProgressionRank.SERF,
            progression.getCanonicalRank());

        assertFalse(
            progression.getSerfKnightProgression()
                .getSerfdomMaster().isSet());

        assertFalse(progression.hasPledgedLord());
    }

    @Test
    public void staffRankOverrideClearsSerfSpecificState()
            throws Exception {
        KOMEAccessFixture fixture=new KOMEAccessFixture();
        fixture.player.inventory=new InventoryPlayer(fixture.player);

        KOMEPlayerProgression progression=
            fixture.data.getProgression(fixture.player.id);

        progression.setCanonicalRank(KOMEProgressionRank.SERF);

        KOMESerfKnightProgression state=
            progression.getSerfKnightProgression();

        assertTrue(
            KOMESerfKnightService.setSerfdomMaster(
                state,npc("Master","rohan")).success);

        assertTrue(
            KOMESerfKnightService.assignDuty(
                state,
                KOMESerfKnightDutyType.PROVISIONING,
                new NBTTagCompound(),
                10L).success);

        assertTrue(
            KOMECanonicalRankService.overrideCanonicalRank(
                fixture.data,
                fixture.player,
                KOMEProgressionRank.KNIGHT));

        assertEquals(
            KOMEProgressionRank.KNIGHT,
            progression.getCanonicalRank());

        assertFalse(state.getSerfdomMaster().isSet());
        assertFalse(state.hasActiveAssignment());

        assertTrue(
            progression.isCompleted(
                KOMEProgressionAchievement.forID(
                    "baseline.hunting")));

        assertTrue(
            progression.isCompleted(
                KOMEProgressionAchievement.forID(
                    "baseline.farming")));

        assertTrue(
            progression.isCompleted(
                KOMEProgressionAchievement.forID(
                    "serf.title_knight")));

        assertTrue(
            progression.isCompleted(
                KOMEProgressionAchievement.forID(
                    "baseline.hire_units")));

        assertTrue(
            progression.isCompleted(
                KOMEProgressionAchievement.forID(
                    "baseline.faction_armor")));

        assertTrue(
            KOMECanonicalRankService.overrideCanonicalRank(
                fixture.data,
                fixture.player,
                KOMEProgressionRank.SERF));

        assertFalse(
            progression.isCompleted(
                KOMEProgressionAchievement.forID(
                    "serf.title_knight")));

        assertFalse(
            progression.isCompleted(
                KOMEProgressionAchievement.forID(
                    "baseline.hire_units")));

        assertFalse(
            progression.isCompleted(
                KOMEProgressionAchievement.forID(
                    "baseline.faction_armor")));
    }

    @Test
    public void staleRecoveryAndCourierObjectsAreRemovedButOrdinaryItemsRemain() {
        KOMEPlayerProgression progression=new KOMEPlayerProgression();
        UUID owner=UUID.randomUUID();

        KOMESerfKnightTrialAssignment recovery=
            new KOMESerfKnightTrialAssignment(
                "recovery",
                UUID.randomUUID().toString(),
                npc("Liege","rohan"),
                "rohan",
                10L,
                KOMESerfKnightTrialAssignment.Stage.ACTIVE,
                0,
                new NBTTagCompound());

        ItemStack recoveryStack=
            KOMESerfKnightRecoveryService.assignedStack(
                recovery,
                owner);

        ItemStack courierStack=new ItemStack(Items.written_book);
        NBTTagCompound root=new NBTTagCompound();
        NBTTagCompound hidden=new NBTTagCompound();

        hidden.setString("Assignment",UUID.randomUUID().toString());
        hidden.setString("Owner",owner.toString());
        hidden.setString("Master",UUID.randomUUID().toString());
        hidden.setString("Destination","legacy");

        root.setTag("KOMECourier",hidden);
        courierStack.setTagCompound(root);

        ItemStack ordinary=new ItemStack(Items.gold_ingot);

        ItemStack[] inventory=
            new ItemStack[]{
                recoveryStack,
                courierStack,
                ordinary
            };

        assertEquals(
            2,
            KOMEProgressionInventoryReconciler.reconcile(
                inventory,
                progression,
                owner));

        assertNull(inventory[0]);
        assertNull(inventory[1]);
        assertNotNull(inventory[2]);
    }
}
