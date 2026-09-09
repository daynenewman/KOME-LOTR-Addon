package com.enovak.lotrmoremobs.compat;

import cpw.mods.fml.common.registry.IThrowableEntity;
import java.util.Iterator;
import java.util.List;
import lotr.common.entity.LOTREntityInvasionSpawner;
import lotr.common.entity.LOTREntityNPCRespawner;
import lotr.common.entity.item.LOTREntityTraderRespawn;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityFishHook;

/**
 * Makes only the exact LOTR respawn-marker entities transparent to entity
 * collision queries made by projectiles. Player selection and interaction
 * queries retain the original marker collision behavior.
 */
public final class RespawnMarkerProjectileCollisionHook {
    private RespawnMarkerProjectileCollisionHook() {
    }

    public static List filterProjectileMarkerCollisions(
            List entities,
            Entity queryingEntity
    ) {
        if (entities == null
                || entities.isEmpty()
                || !isProjectile(queryingEntity)) {
            return entities;
        }

        Iterator iterator = entities.iterator();
        while (iterator.hasNext()) {
            Object candidate = iterator.next();
            if (candidate instanceof Entity
                    && isExactRespawnMarker((Entity)candidate)) {
                iterator.remove();
            }
        }
        return entities;
    }

    private static boolean isProjectile(Entity entity) {
        return entity instanceof IProjectile
                || entity instanceof IThrowableEntity
                || entity instanceof EntityFireball
                || entity instanceof EntityFishHook;
    }

    private static boolean isExactRespawnMarker(Entity entity) {
        Class entityClass = entity.getClass();
        return entityClass == LOTREntityInvasionSpawner.class
                || entityClass == LOTREntityTraderRespawn.class
                || entityClass == LOTREntityNPCRespawner.class;
    }
}
