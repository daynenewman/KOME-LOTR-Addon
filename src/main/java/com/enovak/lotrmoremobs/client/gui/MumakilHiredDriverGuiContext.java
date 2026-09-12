package com.enovak.lotrmoremobs.client.gui;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.util.MumakilHiredDriverGuiAccess;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.gui.LOTRGuiHiredNPC;
import lotr.client.gui.LOTRGuiHiredWarriorInventory;
import lotr.client.gui.LOTRGuiNPCInteract;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;

import java.lang.reflect.Field;

@SideOnly(Side.CLIENT)
public final class MumakilHiredDriverGuiContext {
    private static final long PENDING_CONTEXT_MS = 2000L;

    private static int driverEntityId = -1;
    private static int mumakilEntityId = -1;
    private static long pendingUntilMs;
    private static boolean active;

    private static Field npcInteractEntityField;
    private static Field warriorInventoryNpcField;

    private MumakilHiredDriverGuiContext() {
    }

    public static void begin(int driverId, int mumakilId) {
        driverEntityId = driverId;
        mumakilEntityId = mumakilId;
        pendingUntilMs = System.currentTimeMillis() + PENDING_CONTEXT_MS;
        active = false;
    }

    public static boolean hasContext() {
        if (!hasRawContext()) {
            return false;
        }

        if (!active && System.currentTimeMillis() > pendingUntilMs) {
            clear();
            return false;
        }

        return true;
    }

    public static void markActive() {
        if (hasContext()) {
            active = true;
        }
    }

    public static void clear() {
        driverEntityId = -1;
        mumakilEntityId = -1;
        pendingUntilMs = 0L;
        active = false;
    }

    public static void tick() {
        if (!hasRawContext()) {
            return;
        }

        if (!active) {
            if (System.currentTimeMillis() > pendingUntilMs) {
                clear();
            }
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            clear();
            return;
        }

        GuiScreen screen = mc.currentScreen;
        if (screen == null) {
            clear();
            return;
        }

        LOTREntityNPC driver = getGuiDriver(screen);
        if (!isForDriver(driver) || getMumakilForDriver(driver) == null) {
            clear();
        }
    }

    public static boolean isForDriver(LOTREntityNPC driver) {
        return hasContext() && driver != null && driver.getEntityId() == driverEntityId;
    }

    public static LOTREntityMumakil getMumakilForDriver(LOTREntityNPC driver) {
        if (!isForDriver(driver)) {
            return null;
        }

        Entity entity = getEntityById(mumakilEntityId);
        if (!(entity instanceof LOTREntityMumakil)) {
            return null;
        }

        LOTREntityMumakil mumakil = (LOTREntityMumakil)entity;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) {
            return null;
        }

        return MumakilHiredDriverGuiAccess.canUseClientDriverGui(
                mc.thePlayer,
                driver,
                mumakil,
                Double.MAX_VALUE
        ) ? mumakil : null;
    }

    public static boolean canAnchor(LOTREntityNPC driver, LOTREntityMumakil mumakil, double maxDistanceSq) {
        Minecraft mc = Minecraft.getMinecraft();
        return hasContext()
                && driver != null
                && mumakil != null
                && driver.getEntityId() == driverEntityId
                && mumakil.getEntityId() == mumakilEntityId
                && mc != null
                && mc.thePlayer != null
                && MumakilHiredDriverGuiAccess.canUseClientDriverGui(mc.thePlayer, driver, mumakil, maxDistanceSq);
    }

    public static void closeAndClear() {
        clear();

        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null) {
            mc.thePlayer.closeScreen();
        }
    }

    public static SavedDriverPosition pushDriverToMumakil(LOTREntityNPC driver, LOTREntityMumakil mumakil) {
        SavedDriverPosition saved = new SavedDriverPosition(driver);

        driver.posX = mumakil.posX;
        driver.posY = mumakil.posY;
        driver.posZ = mumakil.posZ;
        driver.prevPosX = mumakil.prevPosX;
        driver.prevPosY = mumakil.prevPosY;
        driver.prevPosZ = mumakil.prevPosZ;
        driver.lastTickPosX = mumakil.lastTickPosX;
        driver.lastTickPosY = mumakil.lastTickPosY;
        driver.lastTickPosZ = mumakil.lastTickPosZ;

        return saved;
    }

    public static LOTREntityNPC getGuiDriver(GuiScreen screen) {
        if (screen instanceof LOTRGuiHiredNPC) {
            return ((LOTRGuiHiredNPC)screen).theNPC;
        }

        if (screen instanceof LOTRGuiNPCInteract) {
            return getNpcFromField(screen, LOTRGuiNPCInteract.class, "theEntity");
        }

        if (screen instanceof LOTRGuiHiredWarriorInventory) {
            return getNpcFromField(screen, LOTRGuiHiredWarriorInventory.class, "theNPC");
        }

        return null;
    }

    private static boolean hasRawContext() {
        return driverEntityId >= 0 && mumakilEntityId >= 0;
    }

    private static Entity getEntityById(int entityId) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null || mc.theWorld == null ? null : mc.theWorld.getEntityByID(entityId);
    }

    private static LOTREntityNPC getNpcFromField(GuiScreen screen, Class<?> owner, String fieldName) {
        try {
            Field field = getCachedField(owner, fieldName);
            Object value = field.get(screen);
            return value instanceof LOTREntityNPC ? (LOTREntityNPC)value : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Field getCachedField(Class<?> owner, String fieldName) throws NoSuchFieldException {
        if (owner == LOTRGuiNPCInteract.class) {
            if (npcInteractEntityField == null) {
                npcInteractEntityField = getAccessibleField(owner, fieldName);
            }
            return npcInteractEntityField;
        }

        if (owner == LOTRGuiHiredWarriorInventory.class) {
            if (warriorInventoryNpcField == null) {
                warriorInventoryNpcField = getAccessibleField(owner, fieldName);
            }
            return warriorInventoryNpcField;
        }

        return getAccessibleField(owner, fieldName);
    }

    private static Field getAccessibleField(Class<?> owner, String fieldName) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    public static final class SavedDriverPosition {
        private final double posX;
        private final double posY;
        private final double posZ;
        private final double prevPosX;
        private final double prevPosY;
        private final double prevPosZ;
        private final double lastTickPosX;
        private final double lastTickPosY;
        private final double lastTickPosZ;

        private SavedDriverPosition(LOTREntityNPC driver) {
            this.posX = driver.posX;
            this.posY = driver.posY;
            this.posZ = driver.posZ;
            this.prevPosX = driver.prevPosX;
            this.prevPosY = driver.prevPosY;
            this.prevPosZ = driver.prevPosZ;
            this.lastTickPosX = driver.lastTickPosX;
            this.lastTickPosY = driver.lastTickPosY;
            this.lastTickPosZ = driver.lastTickPosZ;
        }

        public void restore(LOTREntityNPC driver) {
            driver.posX = this.posX;
            driver.posY = this.posY;
            driver.posZ = this.posZ;
            driver.prevPosX = this.prevPosX;
            driver.prevPosY = this.prevPosY;
            driver.prevPosZ = this.prevPosZ;
            driver.lastTickPosX = this.lastTickPosX;
            driver.lastTickPosY = this.lastTickPosY;
            driver.lastTickPosZ = this.lastTickPosZ;
        }
    }
}
