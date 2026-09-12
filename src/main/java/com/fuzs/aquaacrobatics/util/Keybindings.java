package com.fuzs.aquaacrobatics.util;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.fuzs.aquaacrobatics.config.ConfigHandler;

import cpw.mods.fml.client.registry.ClientRegistry;

public class Keybindings {

    public static KeyBinding forceCrawling = null;

    public static void register() {
        if (ConfigHandler.MovementConfig.enableToggleCrawling) {
            forceCrawling = new KeyBinding(
                "key.aquaacrobatics.toggle_crawling",
                Keyboard.KEY_C,
                "key.aquaacrobatics.category");
            ClientRegistry.registerKeyBinding(forceCrawling);
        }
    }
}
