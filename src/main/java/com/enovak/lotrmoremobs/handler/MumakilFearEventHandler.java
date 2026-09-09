package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;

public class MumakilFearEventHandler {
    private static final float AVOID_DISTANCE = 16.0F;
    private static final double FAR_SPEED = 1.2D;
    private static final double NEAR_SPEED = 1.5D;
    private static final int FEAR_SCAN_INTERVAL_TICKS = 10;
    private static final int FLEE_REPATH_COOLDOWN_TICKS = 16;
    private static final int MAX_CANDIDATES_PER_SCAN = 64;
    private static final int MAX_FLEE_PATHS_PER_SCAN = 24;
    private static final int FLEE_HORIZONTAL_DISTANCE = 16;
    private static final int FLEE_VERTICAL_DISTANCE = 7;
    private static final String[] LOTR_CIVILIAN_NAME_HINTS = new String[] {
            "hobbit", "villager", "farmer", "trader", "bartender", "child", "woman", "man", "civilian"
    };
    private static final String[] LOTR_WARRIOR_NAME_HINTS = new String[] {
            "soldier", "warrior", "archer", "guard", "knight", "orc", "uruk", "ranger", "warg", "troll", "bandit", "raider"
    };

    /*
     * Only creatures that have actually encountered a Mumak receive state.
     * Weak keys let unloaded entities disappear without explicit world cleanup.
     */
    private final Map<EntityCreature, FleeState> activeFleePaths =
            new WeakHashMap<EntityCreature, FleeState>();

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!(event.entityLiving instanceof LOTREntityMumakil)) {
            return;
        }

        LOTREntityMumakil mumakil =
                (LOTREntityMumakil)event.entityLiving;
        if (mumakil.worldObj == null
                || mumakil.worldObj.isRemote
                || mumakil.isDead
                || !mumakil.isEntityAlive()) {
            return;
        }

        /*
         * The old class-based EntityAIAvoidEntity task reacted to every Mumak
         * mode, including babies. Preserve that behavior while moving the scan
         * owner from every passive creature to the much rarer Mumak entities.
         */
        int stagger = mumakil.getEntityId() & Integer.MAX_VALUE;
        if ((mumakil.ticksExisted + stagger)
                % FEAR_SCAN_INTERVAL_TICKS != 0) {
            return;
        }

        this.frightenNearbyCreatures(mumakil);
    }

    private void frightenNearbyCreatures(LOTREntityMumakil mumakil) {
        List nearby = mumakil.worldObj.getEntitiesWithinAABB(
                EntityCreature.class,
                mumakil.boundingBox.expand(
                        AVOID_DISTANCE,
                        3.0D,
                        AVOID_DISTANCE
                )
        );

        long worldTime = mumakil.worldObj.getTotalWorldTime();
        int inspected = 0;
        int pathsStarted = 0;

        for (int i = 0;
             i < nearby.size()
                     && inspected < MAX_CANDIDATES_PER_SCAN
                     && pathsStarted < MAX_FLEE_PATHS_PER_SCAN;
             ++i) {
            Object object = nearby.get(i);
            if (!(object instanceof EntityCreature)) {
                continue;
            }

            ++inspected;
            EntityCreature creature = (EntityCreature)object;
            if (!this.shouldFearMumakil(creature)
                    || !creature.isEntityAlive()
                    || !creature.getEntitySenses().canSee(mumakil)) {
                continue;
            }

            PathNavigate navigator = creature.getNavigator();
            FleeState previous = this.activeFleePaths.get(creature);
            if (previous != null
                    && previous.path == navigator.getPath()
                    && !navigator.noPath()
                    && worldTime < previous.nextRepathTick) {
                continue;
            }

            Vec3 destination =
                    RandomPositionGenerator.findRandomTargetBlockAwayFrom(
                            creature,
                            FLEE_HORIZONTAL_DISTANCE,
                            FLEE_VERTICAL_DISTANCE,
                            Vec3.createVectorHelper(
                                    mumakil.posX,
                                    mumakil.posY,
                                    mumakil.posZ
                            )
                    );
            if (destination == null
                    || mumakil.getDistanceSq(
                    destination.xCoord,
                    destination.yCoord,
                    destination.zCoord
            ) < mumakil.getDistanceSqToEntity(creature)) {
                continue;
            }

            PathEntity path = navigator.getPathToXYZ(
                    destination.xCoord,
                    destination.yCoord,
                    destination.zCoord
            );
            if (path == null || !path.isDestinationSame(destination)) {
                continue;
            }

            double speed = creature.getDistanceSqToEntity(mumakil) < 49.0D
                    ? NEAR_SPEED
                    : FAR_SPEED;
            if (navigator.setPath(path, speed)) {
                this.activeFleePaths.put(
                        creature,
                        new FleeState(
                                path,
                                worldTime + FLEE_REPATH_COOLDOWN_TICKS
                        )
                );
                ++pathsStarted;
            }
        }
    }

    private boolean shouldFearMumakil(Entity entity) {
        if (!(entity instanceof EntityCreature)
                || entity instanceof LOTREntityMumakil
                || entity instanceof IMob) {
            return false;
        }

        if (entity instanceof EntityTameable && ((EntityTameable)entity).isTamed()) {
            return false;
        }

        if (entity instanceof EntityHorse && ((EntityHorse)entity).isTame()) {
            return false;
        }

        if (entity instanceof EntityAnimal || entity instanceof EntityVillager) {
            return true;
        }

        if (entity instanceof LOTREntityNPC) {
            return this.isPassiveLOTRNpc((LOTREntityNPC)entity);
        }

        return false;
    }

    private boolean isPassiveLOTRNpc(LOTREntityNPC npc) {
        if (npc.hiredNPCInfo.isActive) {
            return false;
        }

        String className = npc.getClass().getName().toLowerCase(Locale.ROOT);
        if (this.containsAny(className, LOTR_WARRIOR_NAME_HINTS)) {
            return false;
        }

        if (this.containsAny(className, LOTR_CIVILIAN_NAME_HINTS)) {
            return true;
        }

        ItemStack heldItem = npc.getHeldItem();
        if (heldItem != null) {
            return false;
        }

        IAttributeInstance attackDamage = npc.getEntityAttribute(SharedMonsterAttributes.attackDamage);
        return attackDamage == null || attackDamage.getAttributeValue() <= 2.0D;
    }

    private boolean containsAny(String value, String[] terms) {
        for (int i = 0; i < terms.length; ++i) {
            if (value.contains(terms[i])) {
                return true;
            }
        }

        return false;
    }

    private static final class FleeState {
        private final PathEntity path;
        private final long nextRepathTick;

        private FleeState(PathEntity path, long nextRepathTick) {
            this.path = path;
            this.nextRepathTick = nextRepathTick;
        }
    }
}
