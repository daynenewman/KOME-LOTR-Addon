package com.fuzs.aquaacrobatics.client.resource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.minecraft.client.Minecraft;

import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// this is experimental code, it may not work as expected
@SideOnly(Side.CLIENT)
public class WaterResourcePackInstaller {

    private static final String RESOURCE_PACK_DIR_NAME = "aquaacrobatics_override";
    private static final String RESOURCE_ROOT = "/assets/aquaacrobatics/overrides/textures/blocks/";
    private static final String[] FILES_TO_COPY = new String[] { "assets/minecraft/textures/blocks/water_still.png",
        "assets/minecraft/textures/blocks/water_still.png.mcmeta", "assets/minecraft/textures/blocks/water_flow.png",
        "assets/minecraft/textures/blocks/water_flow.png.mcmeta", "pack.mcmeta" };

    public static void install(FMLPostInitializationEvent event) {
        File resourcePacksDir = new File(Minecraft.getMinecraft().mcDataDir, "resourcepacks");
        File packDir = new File(resourcePacksDir, RESOURCE_PACK_DIR_NAME);

        if (!packDir.exists()) {
            try {
                copyPackFromJar(packDir);
                System.out.println("[AquaAcrobatics] Resource pack installed: " + packDir.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("[AquaAcrobatics] Failed to extract resource pack:");
                e.printStackTrace();
            }
        } else {
            System.out.println("[AquaAcrobatics] Resource pack already exists: " + packDir.getAbsolutePath());
        }
    }

    private static void copyPackFromJar(File targetDir) throws IOException {
        for (String path : FILES_TO_COPY) {
            String sourcePath;
            if (path.equals("pack.mcmeta")) {
                sourcePath = "/water_pack.mcmeta";
            } else {
                // Map minecraft paths to aquaacrobatics paths
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                sourcePath = RESOURCE_ROOT + fileName;
            }

            InputStream in = WaterResourcePackInstaller.class.getResourceAsStream(sourcePath);
            if (in == null) {
                System.err.println("Missing resource in JAR: " + sourcePath);
                continue;
            }

            String outputPath = path.equals("pack.mcmeta") ? "pack.mcmeta" : path;
            File outFile = new File(targetDir, outputPath);
            outFile.getParentFile()
                .mkdirs();

            try (OutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }

            in.close();
        }
    }
}
