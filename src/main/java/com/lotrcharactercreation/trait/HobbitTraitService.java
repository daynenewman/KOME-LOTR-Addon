package com.lotrcharactercreation.trait;

import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.potion.Potion;

public final class HobbitTraitService {

    private HobbitTraitService() {}

    public static void applyAdditionalUnderwaterAirLoss(EntityPlayerMP player) {
        if (!RaceTraitService.hasHobbitAdvancedTrait(player) || !player.isEntityAlive()
            || player.capabilities.disableDamage
            || player.getAir() <= 0
            || !player.isInsideOfMaterial(Material.water)
            || player.canBreatheUnderwater()
            || player.isPotionActive(Potion.waterBreathing.id)) {
            return;
        }

        int respiration = EnchantmentHelper.getRespiration(player);
        if (respiration <= 0 || player.getRNG()
            .nextInt(respiration + 1) == 0) {
            player.setAir(player.getAir() - 1);
        }
    }
}
