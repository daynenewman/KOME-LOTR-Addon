package kome.client;

import cpw.mods.fml.client.FMLClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourceManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class KOMEMinecraftClient {
    public static Minecraft get() {
        return FMLClientHandler.instance().getClient();
    }

    public static GuiScreen currentScreen() {
        return (GuiScreen) getField(get(), "currentScreen", "field_71462_r");
    }

    public static EntityClientPlayerMP player() {
        return (EntityClientPlayerMP) getField(get(), "thePlayer", "field_71439_g");
    }

    public static int displayWidth() {
        return ((Integer) getField(get(), "displayWidth", "field_71443_c")).intValue();
    }

    public static int displayHeight() {
        return ((Integer) getField(get(), "displayHeight", "field_71440_d")).intValue();
    }

    public static int screenWidth(GuiScreen screen) {
        return ((Integer) getField(screen, "width", "field_146294_l")).intValue();
    }

    public static int screenHeight(GuiScreen screen) {
        return ((Integer) getField(screen, "height", "field_146295_m")).intValue();
    }

    public static FontRenderer fontRenderer() {
        return (FontRenderer) getField(get(), "fontRenderer", "field_71466_p");
    }

    public static IResourceManager resourceManager() {
        return (IResourceManager) invoke(get(), new String[] {"getResourceManager", "func_110442_L"}, new Class[0], new Object[0]);
    }

    public static TextureManager textureManager() {
        return (TextureManager) invoke(get(), new String[] {"getTextureManager", "func_110434_K"}, new Class[0], new Object[0]);
    }

    public static void displayGui(GuiScreen screen) {
        invoke(get(), new String[] {"displayGuiScreen", "func_147108_a"}, new Class[] {GuiScreen.class}, new Object[] {screen});
    }

    public static void sendChat(String message) {
        EntityClientPlayerMP player = player();
        if (player != null) {
            invoke(player, new String[] {"sendChatMessage", "func_71165_d"}, new Class[] {String.class}, new Object[] {message});
        }
    }

    public static String playerName() {
        EntityClientPlayerMP player = player();
        return player == null ? "" : (String) invoke(player, new String[] {"getCommandSenderName", "func_70005_c_"}, new Class[0], new Object[0]);
    }

    public static void closePlayerScreen() {
        EntityClientPlayerMP player = player();
        if (player != null) {
            invoke(player, new String[] {"closeScreen", "func_71053_j"}, new Class[0], new Object[0]);
        }
    }

    private static Object getField(Object target, String deobfName, String obfName) {
        Class clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(deobfName);
                field.setAccessible(true);
                return field.get(target);
            } catch (Exception ignored) {
            }
            try {
                Field field = clazz.getDeclaredField(obfName);
                field.setAccessible(true);
                return field.get(target);
            } catch (Exception ignored) {
            }
            clazz = clazz.getSuperclass();
        }
        throw new RuntimeException("Missing field " + deobfName + "/" + obfName + " on " + target.getClass());
    }

    private static Object invoke(Object target, String[] names, Class[] parameterTypes, Object[] args) {
        for (Class clazz = target.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            for (int i = 0; i < names.length; i++) {
                try {
                    Method method = clazz.getDeclaredMethod(names[i], parameterTypes);
                    method.setAccessible(true);
                    return method.invoke(target, args);
                } catch (Exception ignored) {
                }
            }
        }
        throw new RuntimeException("Missing method " + names[0] + " on " + target.getClass());
    }
}
