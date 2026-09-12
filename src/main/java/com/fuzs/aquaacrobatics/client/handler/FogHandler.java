package com.fuzs.aquaacrobatics.client.handler;

import java.util.Arrays;
import java.util.HashSet;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.common.BiomeDictionary;

import org.lwjgl.opengl.GL11;

import com.fuzs.aquaacrobatics.biome.BiomeWaterFogColors;
import com.fuzs.aquaacrobatics.config.ConfigHandler;
import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;
import com.fuzs.aquaacrobatics.util.math.MathHelperNew;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Uses Forge events to adjust water rendering so it more closely approximates 1.13+.
 *
 * Some of the code in this class is based off of Minecraft 1.16.
 */
public class FogHandler {

    private int targetFogColor = -1;
    private int prevFogColor = -1;
    private long fogAdjustTime = -1L;

    private static HashSet<String> worldProviderClassNames = null;

    public static void recomputeBlacklist() {
        worldProviderClassNames = new HashSet<>();
        worldProviderClassNames.addAll(Arrays.asList(ConfigHandler.MiscellaneousConfig.providerFogBlacklist));
    }

    private boolean shouldSkipFogOverride(World world) {
        if (!ConfigHandler.BlocksConfig.newWaterFog) return true;
        return worldProviderClassNames.contains(
            world.provider.getClass()
                .getName());
    }

    // @SubscribeEvent
    // public void registerBlockColors(ColorHandlerEvent.Block event){
    // if(ConfigHandler.MiscellaneousConfig.bubbleColumns)
    // event.getBlockColors().registerBlockColorHandler(new IBlockColor()
    // {
    // public int colorMultiplier(IBlockState state, @Nullable IBlockAccess worldIn, @Nullable BlockPos pos, int
    // tintIndex)
    // {
    // return worldIn != null && pos != null ? BiomeColorHelper.getWaterColorAtPos(worldIn, pos) : -1;
    // }
    //
    // }, CommonProxy.BUBBLE_COLUMN);
    // }

    @SubscribeEvent
    public void onRenderFogDensity(EntityViewRenderEvent.FogDensity event) {
        Entity eventEntity = event.entity;
        if (eventEntity instanceof EntityLivingBase
            && ((EntityLivingBase) eventEntity).isPotionActive(Potion.blindness)) return;
        if (event.block.getMaterial() == Material.water && !shouldSkipFogOverride(eventEntity.worldObj)) {
            GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_EXP2);
            // GlStateManager.setFog(GlStateManager.FogMode.EXP2);
            float density = 0.05f;
            if (eventEntity instanceof EntityPlayer) {
                EntityPlayer playerEntity = (EntityPlayer) eventEntity;
                float waterVision = ((IPlayerResizeable) playerEntity).getWaterVision();
                density -= waterVision * waterVision * 0.03F;
                BiomeGenBase biome = playerEntity.worldObj
                    .getBiomeGenForCoords((int) playerEntity.posX, (int) playerEntity.posZ);
                if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.SWAMP)) {
                    density += 0.005F;
                }
            }
            event.density = density;
            event.setCanceled(true);
        }
    }

    /* LOW to override mods like Biomes O' Plenty which force their own underwater fog color */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRenderFogColor(EntityViewRenderEvent.FogColors event) {
        if (!ConfigHandler.BlocksConfig.newWaterColors) return;
        Block blockInside = event.block;
        if ((event.block.getMaterial() == Material.water) && event.entity instanceof EntityPlayer
            && !shouldSkipFogOverride(event.entity.worldObj)) {
            float fogRed, fogGreen, fogBlue;
            EntityPlayer playerEntity = (EntityPlayer) event.entity;
            long i = System.nanoTime() / 1000000L;
            int j = BiomeWaterFogColors.getWaterFogColorForBiome(
                playerEntity.worldObj.getBiomeGenForCoords((int) playerEntity.posX, (int) playerEntity.posZ));
            if (fogAdjustTime < 0L) {
                targetFogColor = j;
                prevFogColor = j;
                fogAdjustTime = i;
            }
            int k = targetFogColor >> 16 & 255;
            int l = targetFogColor >> 8 & 255;
            int i1 = targetFogColor & 255;
            int j1 = prevFogColor >> 16 & 255;
            int k1 = prevFogColor >> 8 & 255;
            int l1 = prevFogColor & 255;
            float f = MathHelper.clamp_float((float) (i - fogAdjustTime) / 5000.0F, 0.0F, 1.0F);
            float f1 = MathHelperNew.lerp(f, (float) j1, (float) k);
            float f2 = MathHelperNew.lerp(f, (float) k1, (float) l);
            float f3 = MathHelperNew.lerp(f, (float) l1, (float) i1);
            fogRed = f1 / 255.0F;
            fogGreen = f2 / 255.0F;
            fogBlue = f3 / 255.0F;
            if (targetFogColor != j) {
                targetFogColor = j;
                prevFogColor = MathHelper.floor_float(f1) << 16 | MathHelper.floor_float(f2) << 8
                    | MathHelper.floor_float(f3);
                fogAdjustTime = i;
            }
            float f6 = ((IPlayerResizeable) playerEntity).getWaterVision();
            float f9 = Math.min(1.0F / fogRed, Math.min(1.0F / fogGreen, 1.0F / fogBlue));
            fogRed = fogRed * (1.0F - f6) + fogRed * f9 * f6;
            fogGreen = fogGreen * (1.0F - f6) + fogGreen * f9 * f6;
            fogBlue = fogBlue * (1.0F - f6) + fogBlue * f9 * f6;

            double blindnessFactor = 1.0;
            if (playerEntity.isPotionActive(Potion.blindness)) {
                int potionDuration = playerEntity.getActivePotionEffect(Potion.blindness)
                    .getDuration();
                if (potionDuration < 20) {
                    blindnessFactor *= (1.0F - (float) i / 20.0F);
                } else {
                    blindnessFactor = 0.0D;
                }
            }

            if (blindnessFactor < 1.0D) {
                if (blindnessFactor < 0.0D) {
                    blindnessFactor = 0.0D;
                }

                blindnessFactor = blindnessFactor * blindnessFactor;
                fogRed = (float) ((double) fogRed * blindnessFactor);
                fogGreen = (float) ((double) fogGreen * blindnessFactor);
                fogBlue = (float) ((double) fogBlue * blindnessFactor);
            }

            event.red = (fogRed);
            event.green = (fogGreen);
            event.blue = (fogBlue);
        } else if ((blockInside == Blocks.lava || blockInside == Blocks.flowing_lava)) {
            event.red = (0.6f);
            event.green = (0.1f);
            event.blue = (0.0f);
            fogAdjustTime = -1L;
        } else {
            fogAdjustTime = -1L;
        }
    }
}
