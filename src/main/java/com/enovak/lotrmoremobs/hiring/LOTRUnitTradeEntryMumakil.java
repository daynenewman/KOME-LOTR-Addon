package com.enovak.lotrmoremobs.hiring;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.entity.animal.MumakilFormationOrigin;
import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHirePreviewDriver;
import com.enovak.lotrmoremobs.handler.MumakilAchievementEventHandler;
import com.enovak.lotrmoremobs.spawning.MumakilWarFormationFactory;
import java.lang.reflect.Method;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTREntitySouthronChampion;
import lotr.common.item.LOTRItemCoin;
import lotr.common.entity.npc.LOTRUnitTradeEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

public class LOTRUnitTradeEntryMumakil extends LOTRUnitTradeEntry {
    public static final int MUMAKIL_HIRE_COST = 1000;
    public static final float MUMAKIL_ALIGNMENT_REQUIRED = 2000.0F;

    private static final float HIRE_GUI_DISPLAY_WIDTH = 14.0F;
    private static final float HIRE_GUI_DISPLAY_HEIGHT = 30.0F;

    public LOTRUnitTradeEntryMumakil() {
        super(
                LOTREntitySouthronChampion.class,
                LOTREntityMumakil.class,
                "Mumakil_Howdah",
                MUMAKIL_HIRE_COST,
                MUMAKIL_ALIGNMENT_REQUIRED
        );
        this.setMountArmor(Main.mumakilHowdah, 1.0F);
        this.setPledgeExclusive();
        this.setExtraInfo("Mumakil_Howdah");
    }

    @Override
    public LOTREntityNPC getOrCreateHiredNPC(World world) {
        if (!world.isRemote) {
            return super.getOrCreateHiredNPC(world);
        }

        LOTREntityMumakilHirePreviewDriver driver = new LOTREntityMumakilHirePreviewDriver(world);
        driver.initCreatureForHire(null);
        driver.refreshCurrentAttackMode();
        driver.setCurrentItemOrArmor(0, null);
        return driver;
    }

    @Override
    public void hireUnit(
            net.minecraft.entity.player.EntityPlayer player,
            lotr.common.entity.npc.LOTRHireableBase hireable,
            String squadron
    ) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            super.hireUnit(player, hireable, squadron);
            return;
        }

        if (!(hireable instanceof LOTREntityNPC)) {
            super.hireUnit(player, hireable, squadron);
            return;
        }

        /*
         * If native hiring requirements are not met, let native LOTR handle
         * the failed transaction and do not run Mumak placement/relocation.
         */
        if (!this.hasRequiredCostAndAlignment(player, hireable)) {
            super.hireUnit(player, hireable, squadron);
            return;
        }

        LOTREntityNPC hiringNpc = (LOTREntityNPC)hireable;

        /*
         * Build the exact entities before payment, while they are still
         * unspawned. This lets the placement search validate the real rider
         * envelope and avoids native LOTR's temporary player-position spawn.
         */
        LOTREntityNPC driver = this.getOrCreateHiredNPC(player.worldObj);
        EntityLiving mount = this.createHiredMount(player.worldObj);
        if (driver == null || !(mount instanceof LOTREntityMumakil)) {
            this.discardUnspawnedHire(driver, mount);
            return;
        }
        LOTREntityMumakil mumakil = (LOTREntityMumakil)mount;

        MumakilWarFormationFactory.FormationPlacementSearchResult placement =
                MumakilWarFormationFactory.findPlayerHiredFormationPlacement(
                        player.worldObj,
                        player,
                        hiringNpc,
                        driver,
                        mumakil,
                        player.rotationYaw
                );

        if (!placement.isFound()) {
            /*
             * Native LOTR charges inside its superclass method. Refuse the
             * transaction before entering it when no clear Mumak-sized
             * location exists.
             */
            player.addChatMessage(new ChatComponentText(
                    "There is not enough open ground within 30 blocks of "
                            +  "this warrior to hire this unit. "
                            +  "Move to a larger clear area and try again."
            ));
            this.discardUnspawnedHire(driver, mumakil);
            return;
        }

        /*
         * Preserve native LOTR ordering, but only after the custom Mumak-sized
         * placement preflight has accepted the transaction. A refused hire
         * must not trigger the Warlord trade callback/achievement.
         */
        int hireCost = this.getCost(player, hireable);
        hireable.onUnitTrade(player);
        LOTRItemCoin.takeCoins(hireCost, player);
        hiringNpc.playTradeSound();

        boolean alreadyLoaded = player.worldObj.loadedEntityList.contains(driver);
        driver.hiredNPCInfo.hireUnit(
                player,
                !alreadyLoaded,
                hireable.getFaction(),
                this,
                squadron,
                mumakil
        );
        if (alreadyLoaded) {
            return;
        }

        mumakil.setLocationAndAngles(
                placement.getX(),
                placement.getY(),
                placement.getZ(),
                player.rotationYaw,
                0.0F
        );
        driver.mountEntity(mumakil);
        mumakil.positionRiderAtMumakilAnchor(driver);
        mumakil.updateRiderPosition();

        if (!player.worldObj.spawnEntityInWorld(driver)) {
            LOTRItemCoin.giveCoins(hireCost, player);
            this.discardUnspawnedHire(driver, mumakil);
            player.addChatMessage(new ChatComponentText(
                    "The Mumakil formation could not be deployed. Your coins were returned."
            ));
            return;
        }
        if (!player.worldObj.spawnEntityInWorld(mumakil)) {
            LOTRItemCoin.giveCoins(hireCost, player);
            driver.setDead();
            mumakil.setDead();
            player.addChatMessage(new ChatComponentText(
                    "The Mumakil formation could not be deployed. Your coins were returned."
            ));
            return;
        }
    }

    private void discardUnspawnedHire(
            LOTREntityNPC driver,
            EntityLiving mount
    ) {
        if (driver != null && !driver.worldObj.loadedEntityList.contains(driver)) {
            driver.setDead();
        }
        if (mount != null && !mount.worldObj.loadedEntityList.contains(mount)) {
            mount.setDead();
        }
    }

    @Override
    public EntityLiving createHiredMount(World world) {
        LOTREntityMumakil mumakil = new LOTREntityMumakil(world);
        mumakil.bypassNaturalSpawnSpacing();
        mumakil.onSpawnWithEgg(null);
        if (!MumakilWarFormationFactory.initializeMount(
                mumakil,
                MumakilFormationOrigin.PLAYER_HIRED
        )) {
            System.out.println(
                    "[LOTRMoreMobs] Could not fully equip player-hired Mumak."
            );
        }

        if (world.isRemote) {
            this.applyHireGuiDisplayScale(mumakil);
            mumakil.setMumakilHowdahPreviewEquipped(true);
        } else {
            /*
             * The mount created by this server-side native trade path is the
             * only formation eligible for the hiring achievement. The event
             * handler waits until the paid driver and all seventeen archers
             * exist, so failed transactions and old loaded formations cannot
             * award it.
             */
            MumakilAchievementEventHandler
                    .markHiringAchievementPending(mumakil);
        }

        return mumakil;
    }

    private void applyHireGuiDisplayScale(LOTREntityMumakil mumakil) {
        /*
         * LOTR's hire GUI computes preview size from entity width/height.
         * This only runs for the temporary client-side GUI preview entity.
         */
        Method setSize = this.findMethod(Entity.class, "setSize", new Class[] { Float.TYPE, Float.TYPE });
        if (setSize == null) {
            setSize = this.findMethod(Entity.class, "func_70105_a", new Class[] { Float.TYPE, Float.TYPE });
        }

        if (setSize != null) {
            try {
                setSize.invoke(mumakil, new Object[] {
                        Float.valueOf(HIRE_GUI_DISPLAY_WIDTH),
                        Float.valueOf(HIRE_GUI_DISPLAY_HEIGHT)
                });
            } catch (Exception e) {
            }
        }
    }

    private Method findMethod(Class type, String name, Class[] parameterTypes) {
        Class current = type;
        while (current != null && current != Object.class) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

}
