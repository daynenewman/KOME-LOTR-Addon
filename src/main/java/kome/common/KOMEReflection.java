package kome.common;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public class KOMEReflection {
    private static Field entityWorldField;
    private static Field entityUUIDField;
    private static Field ridingEntityField;
    private static Method setDeadMethod;
    private static Method getMaxHealthMethod;

    public static World getWorld(Entity entity) {
        try {
            if (entityWorldField == null) {
                try {
                    entityWorldField = Entity.class.getDeclaredField("worldObj");
                } catch (NoSuchFieldException e) {
                    entityWorldField = Entity.class.getDeclaredField("field_70170_p");
                }
                entityWorldField.setAccessible(true);
            }
            return (World) entityWorldField.get(entity);
        } catch (Exception e) {
            throw new RuntimeException("Could not read entity world", e);
        }
    }

    public static UUID getEntityUUID(Entity entity) {
        try {
            if (entityUUIDField == null) {
                try {
                    entityUUIDField = Entity.class.getDeclaredField("entityUniqueID");
                } catch (NoSuchFieldException e) {
                    entityUUIDField = Entity.class.getDeclaredField("field_96093_i");
                }
                entityUUIDField.setAccessible(true);
            }
            return (UUID) entityUUIDField.get(entity);
        } catch (Exception e) {
            throw new RuntimeException("Could not read entity UUID", e);
        }
    }

    public static Entity getRidingEntity(Entity entity) {
        try {
            if (ridingEntityField == null) {
                try {
                    ridingEntityField = Entity.class.getDeclaredField("ridingEntity");
                } catch (NoSuchFieldException e) {
                    ridingEntityField = Entity.class.getDeclaredField("field_70154_o");
                }
                ridingEntityField.setAccessible(true);
            }
            return (Entity) ridingEntityField.get(entity);
        } catch (Exception e) {
            return null;
        }
    }

    public static void setDead(Entity entity) {
        try {
            if (setDeadMethod == null) {
                try {
                    setDeadMethod = Entity.class.getDeclaredMethod("setDead");
                } catch (NoSuchMethodException e) {
                    setDeadMethod = Entity.class.getDeclaredMethod("func_70106_y");
                }
                setDeadMethod.setAccessible(true);
            }
            setDeadMethod.invoke(entity);
        } catch (Exception e) {
            throw new RuntimeException("Could not remove entity", e);
        }
    }

    public static float getMaxHealth(EntityLivingBase entity) {
        try {
            if (getMaxHealthMethod == null) {
                try {
                    getMaxHealthMethod = EntityLivingBase.class.getMethod("getMaxHealth");
                } catch (NoSuchMethodException e) {
                    getMaxHealthMethod = EntityLivingBase.class.getMethod("func_110138_aP");
                }
                getMaxHealthMethod.setAccessible(true);
            }
            return ((Number) getMaxHealthMethod.invoke(entity)).floatValue();
        } catch (Exception e) {
            throw new RuntimeException("Could not read entity max health", e);
        }
    }

    public static float getMaxHealthOrFallback(EntityLivingBase entity, float fallback) {
        try {
            float maxHealth = getMaxHealth(entity);
            if (Float.isNaN(maxHealth) || Float.isInfinite(maxHealth) || maxHealth <= 0.0f) {
                return fallback;
            }
            return maxHealth;
        } catch (Throwable e) {
            return fallback;
        }
    }
}
