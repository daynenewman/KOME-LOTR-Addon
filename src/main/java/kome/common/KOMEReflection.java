package kome.common;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public class KOMEReflection {
    private static Field entityWorldField;
    private static Field entityUUIDField;
    private static Field ridingEntityField;
    private static Field riddenByEntityField;
    private static Field worldRemoteField;
    private static Field worldMapStorageField;
    private static Method setDeadMethod;
    private static Method entityIdMethod;
    private static Method getMaxHealthMethod;
    private static Method getTotalWorldTimeMethod;
    private static Method hiredInfoMarkDirtyMethod;
    private static Method hiredInfoSendClientPacketMethod;

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

    public static boolean isRemote(World world) {
        try {
            if (worldRemoteField == null) {
                try {
                    worldRemoteField = World.class.getDeclaredField("isRemote");
                } catch (NoSuchFieldException e) {
                    worldRemoteField = World.class.getDeclaredField("field_72995_K");
                }
                worldRemoteField.setAccessible(true);
            }
            return ((Boolean) worldRemoteField.get(world)).booleanValue();
        } catch (Exception e) {
            throw new RuntimeException("Could not read world remote flag", e);
        }
    }

    public static long getTotalWorldTime(World world) {
        try {
            if (getTotalWorldTimeMethod == null) {
                try {
                    getTotalWorldTimeMethod = World.class.getDeclaredMethod("getTotalWorldTime");
                } catch (NoSuchMethodException e) {
                    getTotalWorldTimeMethod = World.class.getDeclaredMethod("func_82737_E");
                }
                getTotalWorldTimeMethod.setAccessible(true);
            }
            return ((Number) getTotalWorldTimeMethod.invoke(world)).longValue();
        } catch (Exception e) {
            throw new RuntimeException("Could not read world time", e);
        }
    }

    public static MapStorage getMapStorage(World world) {
        try {
            if (worldMapStorageField == null) {
                try {
                    worldMapStorageField = World.class.getDeclaredField("mapStorage");
                } catch (NoSuchFieldException e) {
                    worldMapStorageField = World.class.getDeclaredField("field_72988_C");
                }
                worldMapStorageField.setAccessible(true);
            }
            return (MapStorage) worldMapStorageField.get(world);
        } catch (Exception e) {
            throw new RuntimeException("Could not read world map storage", e);
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

    public static int getEntityId(Entity entity) {
        try {
            if (entityIdMethod == null) {
                try {
                    entityIdMethod = Entity.class.getDeclaredMethod("getEntityId");
                } catch (NoSuchMethodException e) {
                    entityIdMethod = Entity.class.getDeclaredMethod("func_145782_y");
                }
                entityIdMethod.setAccessible(true);
            }
            return ((Integer) entityIdMethod.invoke(entity)).intValue();
        } catch (Exception e) {
            throw new RuntimeException("Could not read entity ID", e);
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

    public static Entity getRiddenByEntity(Entity entity) {
        try {
            if (riddenByEntityField == null) {
                try {
                    riddenByEntityField = Entity.class.getDeclaredField("riddenByEntity");
                } catch (NoSuchFieldException e) {
                    riddenByEntityField = Entity.class.getDeclaredField("field_70153_n");
                }
                riddenByEntityField.setAccessible(true);
            }
            return (Entity) riddenByEntityField.get(entity);
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

    public static void markHiredInfoDirty(Object hiredInfo) {
        try {
            if (hiredInfoMarkDirtyMethod == null) {
                hiredInfoMarkDirtyMethod = hiredInfo.getClass().getDeclaredMethod("markDirty");
                hiredInfoMarkDirtyMethod.setAccessible(true);
            }
            hiredInfoMarkDirtyMethod.invoke(hiredInfo);
        } catch (Exception ignored) {
        }
    }

    public static void sendHiredInfoClientPacket(Object hiredInfo, boolean openGui) {
        try {
            if (hiredInfoSendClientPacketMethod == null) {
                hiredInfoSendClientPacketMethod = hiredInfo.getClass().getDeclaredMethod("sendClientPacket", Boolean.TYPE);
                hiredInfoSendClientPacketMethod.setAccessible(true);
            }
            hiredInfoSendClientPacketMethod.invoke(hiredInfo, Boolean.valueOf(openGui));
        } catch (Exception ignored) {
        }
    }
}
