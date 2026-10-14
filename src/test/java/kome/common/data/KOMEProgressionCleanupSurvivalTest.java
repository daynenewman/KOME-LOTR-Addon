package kome.common.data;

import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import kome.common.KOMEAccessFixture;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import lotr.common.inventory.LOTRContainerHobbitOven;
import lotr.common.tileentity.LOTRTileEntityHobbitOven;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class KOMEProgressionCleanupSurvivalTest {
    @Test public void torchPlacementRemainsAvailableAndOtherWorldProtectionStillCancelsIt() throws Exception {
        try (KOMEProgressionFollowupTest.Session s = new KOMEProgressionFollowupTest.Session()) {
            s.p.reset();
            java.lang.reflect.Constructor<net.minecraft.block.BlockTorch> constructor =
                net.minecraft.block.BlockTorch.class.getDeclaredConstructor(); constructor.setAccessible(true);
            final net.minecraft.block.Block torch = constructor.newInstance();
            java.lang.reflect.Method raw = net.minecraft.block.Block.blockRegistry.getClass()
                .getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class); raw.setAccessible(true);
            raw.invoke(net.minecraft.block.Block.blockRegistry,3929,"kome:cleanup_torch",torch);
            net.minecraftforge.common.util.BlockSnapshot snapshot = new net.minecraftforge.common.util.BlockSnapshot(
                s.f.world,0,64,0,net.minecraft.init.Blocks.grass,0) {
                @Override public net.minecraft.block.Block getCurrentBlock() {return torch;}
            };
            net.minecraftforge.event.world.BlockEvent.PlaceEvent place = place(snapshot,s);
            dispatch(new KOMEEvents(),place); assertFalse(place.isCanceled());
            place=place(snapshot,s); place.setCanceled(true);
            dispatch(new KOMEEvents(),place); assertTrue(place.isCanceled());
        }
    }
    private static net.minecraftforge.event.world.BlockEvent.PlaceEvent place(
            net.minecraftforge.common.util.BlockSnapshot snapshot,KOMEProgressionFollowupTest.Session s) {
        return new net.minecraftforge.event.world.BlockEvent.PlaceEvent(snapshot,net.minecraft.init.Blocks.grass,s.f.player) {
            @Override public boolean isCancelable() {return true;}
        };
    }

    @Test public void nativeGameplayDoesNotNeedLegacyFlagsBeforeOrAfterSaveReload() throws Exception {
        try (KOMEProgressionFollowupTest.Session s = new KOMEProgressionFollowupTest.Session()) {
            s.p.reset();
            for (String id : new String[]{"baseline.fire", "baseline.cooking", "baseline.stonework",
                    "baseline.farming", "baseline.meat", "baseline.alcohol_pipeweed", "baseline.mounts",
                    "baseline.npc_trade", "baseline.pouches", "baseline.hire_units"}) {
                assertTrue(id, KOMEProgressionPermissions.has(s.f.player, id));
                assertFalse(id, s.p.isCompleted(KOMEProgressionAchievement.forID(id)));
            }
            s.p.readFromNBT(s.p.writeToNBT());
            assertEquals(KOMEProgressionRank.WANDERER, s.p.getCanonicalRank());
            assertTrue(KOMEProgressionPermissions.has(s.f.player, KOMEProgressionPermissions.COOKING));
            assertTrue(KOMEProgressionPermissions.has(s.f.player, KOMEProgressionPermissions.MOUNTS));
        }
    }

    @Test public void normalUseEventsPermitFoodDrinkToolsFireFishingAndTilling() throws Exception {
        try (KOMEProgressionFollowupTest.NativeItems items = new KOMEProgressionFollowupTest.NativeItems();
                KOMEProgressionFollowupTest.Session s = new KOMEProgressionFollowupTest.Session()) {
            s.p.reset(); KOMEEvents events = new KOMEEvents();
            for (Item item : new Item[]{Items.flint_and_steel, Items.fishing_rod, Items.stone_hoe,
                    Items.stone_pickaxe, Items.stone_sword, Items.cooked_beef, Items.fish,
                    lotr.common.LOTRMod.mugWater, lotr.common.LOTRMod.mugMead}) {
                ItemStack stack = new ItemStack(item);
                s.f.player.inventory.mainInventory[0] = stack;
                PlayerInteractEvent interact = new PlayerInteractEvent(s.f.player,
                    PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, 0, 64, 0, 1, s.f.world);
                events.onPlayerInteract(interact); assertFalse(interact.isCanceled());
                PlayerUseItemEvent.Start use = new PlayerUseItemEvent.Start(s.f.player, stack, 32);
                events.onPlayerUseItemStart(use); assertFalse(use.isCanceled()); assertEquals(32, use.duration);
            }
            PlayerInteractEvent protectedAction = new PlayerInteractEvent(s.f.player,
                PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, 0, 64, 0, 1, s.f.world) {
                @Override public boolean isCancelable() {return true;}
            };
            protectedAction.setCanceled(true);
            events.onPlayerInteract(protectedAction);
            assertTrue("Other protection remains cancelled", protectedAction.isCanceled());
        }
    }

    @Test public void furnaceHopperOutputAndNativeOvenAreNotEditedByPlayerTickOrSmeltEvent() throws Exception {
        try (KOMEProgressionFollowupTest.NativeItems items = new KOMEProgressionFollowupTest.NativeItems();
                KOMEProgressionFollowupTest.Session s = new KOMEProgressionFollowupTest.Session()) {
            s.p.reset(); s.f.world.testWorldTime = 1L;
            TileEntityFurnace furnace = new TileEntityFurnace();
            assertNull(TileEntityHopper.func_145889_a(furnace, new ItemStack(Items.beef, 3), 1));
            furnace.setInventorySlotContents(2, new ItemStack(Items.cooked_beef, 3));
            s.f.player.openContainer = new ContainerFurnace(s.f.player.inventory, furnace);
            KOMEEvents events = new KOMEEvents();
            events.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, s.f.player));
            assertEquals(3, furnace.getStackInSlot(0).stackSize);
            assertEquals(3, furnace.getStackInSlot(2).stackSize);
            s.f.player.inventory.mainInventory[0] = new ItemStack(Items.cooked_beef, 3);
            dispatch(events,new PlayerEvent.ItemSmeltedEvent(s.f.player, new ItemStack(Items.cooked_beef, 3)));
            assertEquals(3, s.f.player.inventory.mainInventory[0].stackSize);
            LOTRTileEntityHobbitOven oven = new LOTRTileEntityHobbitOven();
            oven.setInventorySlotContents(0, new ItemStack(Items.beef, 2));
            s.f.player.openContainer = new LOTRContainerHobbitOven(s.f.player.inventory, oven);
            events.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, s.f.player));
            assertEquals(2, oven.getStackInSlot(0).stackSize);
            NBTTagCompound saved = new NBTTagCompound(); furnace.writeToNBT(saved);
            TileEntityFurnace loaded = new TileEntityFurnace(); loaded.readFromNBT(saved);
            assertEquals(3, loaded.getStackInSlot(0).stackSize);
            assertEquals(3, loaded.getStackInSlot(2).stackSize);
        }
    }

    @Test public void ordinaryAnimalInteractionsAndNativeProtectionAreIndependentOfStanding() throws Exception {
        try (KOMEProgressionFollowupTest.Session s = new KOMEProgressionFollowupTest.Session()) {
            s.p.reset(); KOMEEvents events = new KOMEEvents();
            EntityHorse horse = KOMEAccessFixture.allocate(EntityHorse.class); horse.worldObj = s.f.world;
            EntityInteractEvent mount = new EntityInteractEvent(s.f.player, horse);
            events.onEntityInteract(mount); assertFalse(mount.isCanceled());
            EntityPig pig = KOMEAccessFixture.allocate(EntityPig.class); pig.worldObj = s.f.world;
            s.f.player.inventory.mainInventory[0] = new ItemStack(Items.carrot);
            EntityInteractEvent breed = new EntityInteractEvent(s.f.player, pig);
            events.onEntityInteract(breed); assertFalse(breed.isCanceled());
            assertTrue(KOMEGearRestrictionService.evaluate(
                KOMEGearRestrictionService.ruleForId("minecraft:stone_pickaxe"),
                KOMEGearRestrictionService.Action.USE, false, "").allowed);
            assertFalse(KOMEGearRestrictionService.evaluate(
                KOMEGearRestrictionService.ruleForId("lotr:swordMithril"),
                KOMEGearRestrictionService.Action.USE, false, "").allowed);
        }
    }

    @Test public void nativeWarriorAndFarmhandRegistrationDoNotUseMilitaryAdvancement() throws Exception {
        try (KOMEProgressionFollowupTest.Session s = new KOMEProgressionFollowupTest.Session()) {
            s.p.reset(); s.p.setCanonicalRank(KOMEProgressionRank.KNIGHT);
            s.f.data.grantFactionPopulationCenti("rohan", 100000L);
            java.lang.reflect.Method hire = KOMEEvents.class.getDeclaredMethod("handleHiredUnit",
                lotr.common.entity.npc.LOTREntityNPC.class); hire.setAccessible(true);
            KOMEEvents events = new KOMEEvents();
            NativeHire warrior = hired(s, LOTRHiredNPCInfo.Task.WARRIOR);
            long before = KOMEPopulationService.getAvailablePopulationCenti(s.f.data, "rohan");
            hire.invoke(events, warrior);
            KOMEHiredUnitRecord record = s.f.data.hiredUnits.get(warrior.getUniqueID());
            assertNotNull(record); assertEquals(KOMEHiredUnitClass.ORDINARY, KOMEHiredUnitClassification.getUnitClass(record));
            assertTrue(record.populationSpent > 0);
            assertEquals(before - KOMEPopulationService.wholeToCenti(record.populationSpent),
                KOMEPopulationService.getAvailablePopulationCenti(s.f.data, "rohan"));
            long paid = KOMEPopulationService.getAvailablePopulationCenti(s.f.data, "rohan");
            hire.invoke(events, warrior); assertEquals(paid, KOMEPopulationService.getAvailablePopulationCenti(s.f.data, "rohan"));
            s.p.setCanonicalRank(KOMEProgressionRank.WANDERER);
            NativeHire worker = hired(s, LOTRHiredNPCInfo.Task.FARMER); hire.invoke(events, worker);
            KOMEHiredUnitRecord farmhand = s.f.data.hiredUnits.get(worker.getUniqueID());
            assertNotNull(farmhand); assertTrue(farmhand.farmhand); assertEquals(0, farmhand.populationSpent);
            assertEquals(paid, KOMEPopulationService.getAvailablePopulationCenti(s.f.data, "rohan"));
            assertTrue(s.f.data.armyCompanies.isEmpty());
            // Native foreign bodyguards spend the recruiter's bank, never a foreign military reserve.
            s.f.pledge(lotr.common.fac.LOTRFaction.GONDOR);
            s.f.data.grantFactionPopulationCenti("gondor",100000L);
            NativeHire foreign = hired(s,LOTRHiredNPCInfo.Task.WARRIOR); hire.invoke(events,foreign);
            KOMEHiredUnitRecord foreignRecord=s.f.data.hiredUnits.get(foreign.getUniqueID());
            assertNotNull(foreignRecord); assertEquals("rohan",foreignRecord.unitFaction);
            assertEquals("gondor",foreignRecord.populationOwningFaction);
            assertEquals(KOMEHiredUnitClass.ORDINARY,KOMEHiredUnitClassification.getUnitClass(foreignRecord));
            assertEquals(98000L,KOMEPopulationService.getAvailablePopulationCenti(s.f.data,"gondor"));
            assertTrue(s.f.data.armyCompanies.isEmpty());
        }
    }

    private static NativeHire hired(KOMEProgressionFollowupTest.Session s, LOTRHiredNPCInfo.Task task) throws Exception {
        NativeHire npc = KOMEAccessFixture.allocate(NativeHire.class); npc.worldObj = s.f.world;
        net.minecraft.entity.DataWatcher watcher = new net.minecraft.entity.DataWatcher(npc);
        watcher.addObject(0,(byte)0); watcher.addObject(6,20F); watcher.addObject(10,"");
        java.lang.reflect.Field dataWatcher = net.minecraft.entity.Entity.class.getDeclaredField("dataWatcher");
        dataWatcher.setAccessible(true); dataWatcher.set(npc,watcher);
        net.minecraft.entity.ai.attributes.ServersideAttributeMap attributes = new net.minecraft.entity.ai.attributes.ServersideAttributeMap();
        attributes.registerAttribute(net.minecraft.entity.SharedMonsterAttributes.maxHealth).setBaseValue(20D);
        java.lang.reflect.Field field = net.minecraft.entity.EntityLivingBase.class.getDeclaredField("attributeMap");
        field.setAccessible(true); field.set(npc, attributes);
        npc.setUniqueID(UUID.randomUUID()); npc.hiredNPCInfo = new InertHire(npc);
        npc.hiredNPCInfo.setHiringPlayer(s.f.player); npc.hiredNPCInfo.setTask(task); npc.hiredNPCInfo.isActive = true;
        return npc;
    }
    /** Invoke the actual subscribed adapters without bootstrapping Forge's LaunchClassLoader. */
    private static void dispatch(KOMEEvents events, cpw.mods.fml.common.eventhandler.Event event) throws Exception {
        for(java.lang.reflect.Method method:KOMEEvents.class.getMethods())
            if(method.isAnnotationPresent(cpw.mods.fml.common.eventhandler.SubscribeEvent.class)
                    && method.getParameterTypes().length==1 && method.getParameterTypes()[0].isInstance(event))
                method.invoke(events,event);
    }
    private static class InertHire extends LOTRHiredNPCInfo {
        net.minecraft.entity.player.EntityPlayer owner; Task task;
        InertHire(lotr.common.entity.npc.LOTREntityNPC npc) {super(npc);}
        @Override public void setHiringPlayer(net.minecraft.entity.player.EntityPlayer player) {owner=player;}
        @Override public net.minecraft.entity.player.EntityPlayer getHiringPlayer() {return owner;}
        @Override public UUID getHiringPlayerUUID() {return owner==null?null:owner.getUniqueID();}
        @Override public void setTask(Task value) {task=value;}
        @Override public Task getTask() {return task;}
    }
    public static class NativeHire extends lotr.common.entity.npc.LOTREntityRohanMan {
        private NativeHire() { super(null); }
        @Override public String getNPCName() { return "Ordinary hire"; }
        @Override public boolean isEntityAlive() { return !isDead; }
        @Override public boolean isChild() { return false; }
        @Override public void setDead() {isDead=true;}
        @Override public void writeToNBT(NBTTagCompound nbt) {
            nbt.setString("id","RohanMan"); nbt.setLong("UUIDMost",getUniqueID().getMostSignificantBits());
            nbt.setLong("UUIDLeast",getUniqueID().getLeastSignificantBits()); nbt.setFloat("Health",20F);
        }
    }
}
