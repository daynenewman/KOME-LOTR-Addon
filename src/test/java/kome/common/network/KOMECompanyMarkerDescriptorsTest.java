package kome.common.network;

import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEUnitMapMarker;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;
import org.junit.BeforeClass;
import kome.common.KOMEAccessFixture;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import kome.common.config.KOMEConfigRegistry;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import static org.junit.Assert.*;

public class KOMECompanyMarkerDescriptorsTest {
    @BeforeClass public static void bootstrapItems() throws Exception {
        Class.forName(KOMEAccessFixture.class.getName());
        java.lang.reflect.Field home = cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true); home.set(null, new java.io.File(".").getAbsoluteFile());
    }
    private static String id(Item item) { return String.valueOf(Item.itemRegistry.getNameForObject(item)); }
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static KOMEHiredUnitRecord member(ItemStack melee, ItemStack ranged) {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = UUID.randomUUID();
        NBTTagCompound snapshot = new NBTTagCompound();
        NBTTagList inventory = new NBTTagList();
        if (melee != null) { NBTTagCompound entry = melee.writeToNBT(new NBTTagCompound()); entry.setByte("Slot", (byte) 1); inventory.appendTag(entry); }
        if (ranged != null) { NBTTagCompound entry = ranged.writeToNBT(new NBTTagCompound()); entry.setByte("Slot", (byte) 2); inventory.appendTag(entry); }
        snapshot.setTag("NPCItemsInv", inventory);
        record.stationedEntityData = snapshot;
        return record;
    }

    @Test public void rangedFallbackMeleeIsNotMixedButSeparateMeleeMemberIs() {
        KOMEHiredUnitRecord archer = member(new ItemStack(Items.iron_sword), new ItemStack(Items.bow));
        KOMEUnitMapMarker onlyRanged = new KOMEUnitMapMarker();
        KOMECompanyMarkerDescriptors.projectWeapons(onlyRanged, Collections.singletonList(archer));
        assertEquals(id(Items.bow), onlyRanged.rangedItemId);
        assertEquals("", onlyRanged.meleeItemId);
        KOMEHiredUnitRecord fighter = member(new ItemStack(Items.iron_sword), null);
        KOMEUnitMapMarker mixed = new KOMEUnitMapMarker();
        KOMECompanyMarkerDescriptors.projectWeapons(mixed, Arrays.asList(archer, fighter));
        assertEquals(id(Items.bow), mixed.rangedItemId);
        assertEquals(id(Items.iron_sword), mixed.meleeItemId);
    }

    @Test public void absentSnapshotProducesUnknownEquipmentAndMovementCredit() {
        KOMEUnitMapMarker marker = new KOMEUnitMapMarker();
        KOMECompanyMarkerDescriptors.projectWeapons(marker, Collections.singletonList(new KOMEHiredUnitRecord()));
        assertEquals("", marker.meleeItemId);
        assertEquals("", marker.rangedItemId);
        KOMEArmyCompany company = new KOMEArmyCompany();
        KOMECompanyMarkerDescriptors.projectMovement(marker, company);
        assertEquals(KOMEUnitMapMarker.CREDIT_UNKNOWN, marker.movementCredit);
    }

    @Test public void selectionIsStableAcrossMemberOrderAndMalformedOrUnloadedSnapshotsAreOmitted() {
        KOMEHiredUnitRecord sword = member(new ItemStack(Items.iron_sword), null);
        KOMEHiredUnitRecord axe = member(new ItemStack(Items.iron_axe), null);
        KOMEHiredUnitRecord unknown = new KOMEHiredUnitRecord();
        KOMEHiredUnitRecord corrupt = member(new ItemStack(Items.iron_sword), null);
        corrupt.stationedEntityData.getTagList("NPCItemsInv", 10).getCompoundTagAt(0).setString("id", "missing:item");
        KOMEUnitMapMarker first = new KOMEUnitMapMarker(), reversed = new KOMEUnitMapMarker();
        KOMECompanyMarkerDescriptors.projectWeapons(first, Arrays.asList(sword, axe, unknown, corrupt));
        KOMECompanyMarkerDescriptors.projectWeapons(reversed, Arrays.asList(corrupt, unknown, axe, sword));
        assertEquals(id(Items.iron_axe), first.meleeItemId);
        assertEquals(first.meleeItemId, reversed.meleeItemId);
    }

    @Test public void malformedRangedSlotCannotTurnFallbackSwordIntoMeleeRole() {
        KOMEHiredUnitRecord archer = member(new ItemStack(Items.iron_sword), new ItemStack(Items.bow));
        NBTTagList inventory = archer.stationedEntityData.getTagList("NPCItemsInv", 10);
        inventory.getCompoundTagAt(1).setString("id", "missing:item");
        KOMEUnitMapMarker marker = new KOMEUnitMapMarker();
        KOMECompanyMarkerDescriptors.projectWeapons(marker, Collections.singletonList(archer));
        assertEquals("", marker.meleeItemId);
        assertEquals("", marker.rangedItemId);
    }

    @Test public void movementDescriptorUsesCanonicalEntitlementAndInitializedCredit() throws Exception {
        KOMEConfigRegistry.ValidatedConfig original = KOMEConfigRegistry.currentValidated();
        KOMEConfigRegistry.onServerStop();
        KOMEConfigRegistry.load(temporary.newFile("movement.cfg"));
        try {
            KOMEArmyCompany company = new KOMEArmyCompany();
            company.totalPopulation = company.mountedPopulation = 30;
            company.movementAllowanceInitialized = true;
            company.movementAllowance = 1;
            KOMEUnitMapMarker marker = new KOMEUnitMapMarker();
            KOMECompanyMarkerDescriptors.projectMovement(marker, company);
            assertEquals(KOMEUnitMapMarker.ENTITLEMENT_MOUNTED, marker.movementEntitlement);
            assertEquals(KOMEUnitMapMarker.CREDIT_AVAILABLE, marker.movementCredit);
            company.groundPopulation = 10;
            company.movementAllowance = 0;
            marker = new KOMEUnitMapMarker();
            KOMECompanyMarkerDescriptors.projectMovement(marker, company);
            assertEquals(KOMEUnitMapMarker.ENTITLEMENT_FOOT_OR_MIXED, marker.movementEntitlement);
            assertEquals(KOMEUnitMapMarker.CREDIT_EXHAUSTED, marker.movementCredit);
            company.movementAllowanceInitialized = false;
            marker = new KOMEUnitMapMarker();
            KOMECompanyMarkerDescriptors.projectMovement(marker, company);
            assertEquals(KOMEUnitMapMarker.CREDIT_UNKNOWN, marker.movementCredit);
        } finally {
            KOMEConfigRegistry.onServerStop();
            java.lang.reflect.Field active = KOMEConfigRegistry.class.getDeclaredField("current");
            active.setAccessible(true); active.set(null, original);
        }
    }

    @Test public void fixedPacketRoundTripAndBoundsRejectInvalidDescriptorFields() {
        KOMEUnitMapMarker marker = new KOMEUnitMapMarker();
        marker.meleeItemId = "minecraft:iron_sword";
        marker.meleeItemMetadata = 4;
        marker.rangedItemId = "minecraft:bow";
        marker.movementEntitlement = KOMEUnitMapMarker.ENTITLEMENT_MOUNTED;
        marker.movementCredit = KOMEUnitMapMarker.CREDIT_AVAILABLE;
        marker.movementItemId = "minecraft:iron_horse_armor";
        marker.movementItemMetadata = 2;
        ByteBuf wire = Unpooled.buffer();
        try {
            marker.writeToBytes(wire);
            KOMEUnitMapMarker copy = new KOMEUnitMapMarker(); copy.readFromBytes(wire);
            assertEquals(marker.meleeItemId, copy.meleeItemId);
            assertEquals(4, copy.meleeItemMetadata);
            assertEquals(marker.rangedItemId, copy.rangedItemId);
            assertEquals(marker.movementEntitlement, copy.movementEntitlement);
            assertEquals(marker.movementCredit, copy.movementCredit);
            assertEquals(marker.movementItemId, copy.movementItemId);
            assertEquals(marker.movementItemMetadata, copy.movementItemMetadata);
            assertFalse(wire.isReadable());
            marker.meleeItemId = "not-namespaced";
            try { marker.writeToBytes(Unpooled.buffer()); fail("invalid ID accepted"); }
            catch (IllegalArgumentException expected) { /* bound enforced */ }
        } finally { wire.release(); }
    }

    @Test public void savedArmorExamplesAreSelectedFromActualEquipmentAndRidingMount() throws Exception {
        net.minecraft.item.ItemArmor boots = new net.minecraft.item.ItemArmor(
            net.minecraft.item.ItemArmor.ArmorMaterial.CLOTH,0,3);
        java.lang.reflect.Method add=Item.itemRegistry.getClass().getDeclaredMethod(
            "addObjectRaw",int.class,String.class,Object.class);
        add.setAccessible(true);add.invoke(Item.itemRegistry,31988,"kome:descriptor_test_boots",boots);
        KOMEHiredUnitRecord foot=member(null,null);
        NBTTagList equipment=new NBTTagList();
        for(int i=0;i<5;i++)equipment.appendTag(i==1
            ?new ItemStack(boots).writeToNBT(new NBTTagCompound()):new NBTTagCompound());
        foot.stationedEntityData.setTag("Equipment",equipment);
        KOMEUnitMapMarker marker=new KOMEUnitMapMarker();
        marker.movementEntitlement=KOMEUnitMapMarker.ENTITLEMENT_FOOT_OR_MIXED;
        KOMECompanyMarkerDescriptors.projectWeapons(marker,Collections.singletonList(foot));
        assertEquals("kome:descriptor_test_boots",marker.movementItemId);

        KOMEHiredUnitRecord mounted=member(null,null);
        NBTTagCompound riding=new NBTTagCompound();
        riding.setTag("ArmorItem",new ItemStack(Items.iron_horse_armor).writeToNBT(new NBTTagCompound()));
        mounted.stationedEntityData.setTag("Riding",riding);
        marker=new KOMEUnitMapMarker();marker.movementEntitlement=KOMEUnitMapMarker.ENTITLEMENT_MOUNTED;
        KOMECompanyMarkerDescriptors.projectWeapons(marker,Collections.singletonList(mounted));
        assertEquals(id(Items.iron_horse_armor),marker.movementItemId);
    }

    @Test public void absentOrMalformedArmorKeepsFallbackRatherThanClaimingEquipment() {
        KOMEHiredUnitRecord member=member(null,null);
        NBTTagList equipment=new NBTTagList();equipment.appendTag(new NBTTagCompound());
        equipment.appendTag(new ItemStack(Items.iron_sword).writeToNBT(new NBTTagCompound()));
        member.stationedEntityData.setTag("Equipment",equipment);
        KOMEUnitMapMarker foot=new KOMEUnitMapMarker();
        foot.movementEntitlement=KOMEUnitMapMarker.ENTITLEMENT_FOOT_OR_MIXED;
        KOMECompanyMarkerDescriptors.projectWeapons(foot,Collections.singletonList(member));
        assertEquals("",foot.movementItemId);
        KOMEUnitMapMarker mounted=new KOMEUnitMapMarker();
        mounted.movementEntitlement=KOMEUnitMapMarker.ENTITLEMENT_MOUNTED;
        KOMECompanyMarkerDescriptors.projectWeapons(mounted,Collections.singletonList(member));
        assertEquals("",mounted.movementItemId);
    }

    @Test public void lotrMountArmorUsesItsNativeSavedItemAndMovingSnapshotWins() throws Exception {
        lotr.common.item.LOTRItemMountArmor armor = new lotr.common.item.LOTRItemMountArmor(
            net.minecraft.item.ItemArmor.ArmorMaterial.IRON,
            lotr.common.item.LOTRItemMountArmor.Mount.HORSE);
        java.lang.reflect.Method add=Item.itemRegistry.getClass().getDeclaredMethod(
            "addObjectRaw",int.class,String.class,Object.class);
        add.setAccessible(true);add.invoke(Item.itemRegistry,31989,"kome:descriptor_test_lotr_mount_armor",armor);
        KOMEHiredUnitRecord member=member(null,null);
        NBTTagCompound riding=new NBTTagCompound();
        NBTTagCompound savedArmor=new ItemStack(armor).writeToNBT(new NBTTagCompound());
        assertSame(armor,ItemStack.loadItemStackFromNBT(savedArmor).getItem());
        riding.setTag("LOTRMountArmorItem",savedArmor);
        member.movingEntityData=new NBTTagCompound();member.movingEntityData.setTag("Riding",riding);
        member.movementOrderId="STEP";
        KOMEUnitMapMarker marker=new KOMEUnitMapMarker();
        marker.movementEntitlement=KOMEUnitMapMarker.ENTITLEMENT_MOUNTED;
        KOMECompanyMarkerDescriptors.projectWeapons(marker,Collections.singletonList(member));
        assertEquals("kome:descriptor_test_lotr_mount_armor",marker.movementItemId);
    }

    @Test public void armorSelectionIsDeterministicAndInvalidMountOrBootTagsAreIgnored() throws Exception {
        KOMEHiredUnitRecord iron=member(null,null),gold=member(null,null);
        NBTTagCompound a=new NBTTagCompound(),b=new NBTTagCompound();
        a.setTag("ArmorItem",new ItemStack(Items.iron_horse_armor).writeToNBT(new NBTTagCompound()));
        b.setTag("ArmorItem",new ItemStack(Items.golden_horse_armor).writeToNBT(new NBTTagCompound()));
        iron.stationedEntityData.setTag("Riding",a);gold.stationedEntityData.setTag("Riding",b);
        KOMEUnitMapMarker first=new KOMEUnitMapMarker(),reverse=new KOMEUnitMapMarker();
        first.movementEntitlement=reverse.movementEntitlement=KOMEUnitMapMarker.ENTITLEMENT_MOUNTED;
        KOMECompanyMarkerDescriptors.projectWeapons(first,Arrays.asList(iron,gold));
        KOMECompanyMarkerDescriptors.projectWeapons(reverse,Arrays.asList(gold,iron));
        assertEquals(first.movementItemId,reverse.movementItemId);
        gold.stationedEntityData.getCompoundTag("Riding").getCompoundTag("ArmorItem")
            .setString("id","missing:armor");
        KOMEUnitMapMarker malformed=new KOMEUnitMapMarker();
        malformed.movementEntitlement=KOMEUnitMapMarker.ENTITLEMENT_MOUNTED;
        KOMECompanyMarkerDescriptors.projectWeapons(malformed,Collections.singletonList(gold));
        assertEquals("",malformed.movementItemId);
    }
}
