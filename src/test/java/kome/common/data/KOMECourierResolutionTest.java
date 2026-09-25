package kome.common.data;

import java.util.UUID;
import kome.common.KOMEAccessFixture;
import kome.common.network.KOMEPacketHandler;
import lotr.common.LOTRDimension;
import lotr.common.entity.npc.LOTREntityRohirrimWarrior;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMECourierResolutionTest {
    @Test public void ambientNpcDoesNotTakeRecipientBindingWhileDestinationIsUnloaded() throws Exception {
        KOMEAccessFixture fixture=new KOMEAccessFixture();
        fixture.world.provider.dimensionId=LOTRDimension.MIDDLE_EARTH.dimensionID;
        fixture.player.dimension=LOTRDimension.MIDDLE_EARTH.dimensionID;
        fixture.player.posX=LOTRWaypoint.EDORAS.getXCoord();fixture.player.posY=64;fixture.player.posZ=LOTRWaypoint.EDORAS.getZCoord();
        fixture.player.inventory=new InventoryPlayer(fixture.player);
        fixture.player.inventoryContainer=new TestContainer();
        fixture.pledge(LOTRFaction.ROHAN);
        KOMEPlayerProgression progression=fixture.data.getProgression(fixture.player.id);
        progression.setCanonicalRank(KOMEProgressionRank.SERF);
        KOMEProgressionNpcRef master=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Aldor","rohan",
            fixture.player.dimension,fixture.player.posX+900,64,fixture.player.posZ+900);
        assertTrue(KOMESerfKnightService.setSerfdomMaster(progression.getSerfKnightProgression(),master).success);
        KOMESerfCourierAssignment assignment=KOMESerfCourierAssignment.create(master,LOTRWaypoint.EDORAS);
        assertTrue(KOMESerfKnightService.assignDuty(progression.getSerfKnightProgression(),KOMESerfKnightDutyType.COURIER,assignment.writeToNBT(),1L).success);
        fixture.player.inventory.mainInventory[0]=KOMECourierService.message(assignment,fixture.player,master);

        TestNpc npc=KOMEAccessFixture.allocate(TestNpc.class);npc.id=UUID.randomUUID();npc.name="Háma";
        npc.setUniqueID(npc.id);npc.worldObj=fixture.world;npc.posX=assignment.destinationX+300;npc.posY=64;npc.posZ=assignment.destinationZ;
        npc.hiredNPCInfo=new LOTRHiredNPCInfo(npc);fixture.world.loadedEntityList.add(npc);
        cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper previous=KOMEPacketHandler.network;
        try {
            KOMEPacketHandler.network=fixture.network;KOMECourierService.tickPlayer(fixture.player);
        } finally { KOMEPacketHandler.network=previous; }

        KOMESerfCourierAssignment bound=KOMESerfCourierAssignment.readFromNBT(
            progression.getSerfKnightProgression().getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
        assertFalse(bound.recipient.isSet());
        KOMEVisualMarker marker=KOMEVisualLocationService.markersFor(progression).get(1);
        assertEquals(assignment.destinationX,marker.x,0D);

        assertEquals("binding must preserve exactly one physical dispatch",1,countDispatches(fixture.player.inventory,assignment.token));
        net.minecraft.item.ItemStack dispatch=findDispatch(fixture.player.inventory,assignment.token);
        assertTrue("rewritten dispatch must still match the assignment",KOMECourierService.matching(dispatch,bound,fixture.player.id,master));
    }

    private static int countDispatches(InventoryPlayer inventory,String token){int count=0;for(net.minecraft.item.ItemStack stack:inventory.mainInventory)if(stack!=null&&stack.hasTagCompound()&&stack.getTagCompound().hasKey("KOMECourier",10)&&token.equals(stack.getTagCompound().getCompoundTag("KOMECourier").getString("Assignment")))count++;return count;}
    private static net.minecraft.item.ItemStack findDispatch(InventoryPlayer inventory,String token){for(net.minecraft.item.ItemStack stack:inventory.mainInventory)if(stack!=null&&stack.hasTagCompound()&&stack.getTagCompound().hasKey("KOMECourier",10)&&token.equals(stack.getTagCompound().getCompoundTag("KOMECourier").getString("Assignment")))return stack;return null;}

    private static final class TestContainer extends Container {
        @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player){return true;}
    }

    public static final class TestNpc extends LOTREntityRohirrimWarrior {
        UUID id;String name;private TestNpc(){super(null);}
        @Override public UUID getUniqueID(){return id;}
        @Override public String getNPCName(){return name;}
        @Override public LOTRFaction getFaction(){return LOTRFaction.ROHAN;}
        @Override public boolean isEntityAlive(){return true;}
        @Override public boolean isChild(){return false;}
    }
}
