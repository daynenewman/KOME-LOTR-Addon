package com.fuzs.aquaacrobatics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fuzs.aquaacrobatics.proxy.CommonProxy;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLModIdMappingEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/** Aqua Acrobatics subsystem coordinated by KOME. */
public class AquaAcrobatics {

    public static final String MODID = "aquaacrobatics";
    public static final String NAME = "Aqua Acrobatics";
    public static final String VERSION = "phase3-mixin-free-asm-stable";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    public static CommonProxy proxy;

    public void onPreInit(final FMLPreInitializationEvent evt) {
        proxy.onPreInit(evt);
    }

    public void onInit(final FMLInitializationEvent evt) {
        proxy.onInit(evt);
    }

    public void onPostInit(final FMLPostInitializationEvent evt) {
        proxy.onPostInit(evt);
    }

    public void onMappings(FMLModIdMappingEvent evt) {
        proxy.onMappings();
    }
}