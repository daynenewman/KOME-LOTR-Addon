package com.lotrcharactercreation.sound;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraftforge.event.entity.PlaySoundAtEntityEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;
import com.lotrcharactercreation.trait.RaceTraitService;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class RacialPlayerSoundHandler {

    public static final String VANILLA_PLAYER_HURT_SOUND = "game.player.hurt";
    public static final String ORC_HURT_SOUND = "lotr:orc.hurt";
    public static final String DWARF_HURT_SOUND = "lotr:dwarf.hurt";
    public static final String ELF_ATTACK_SOUND = "lotr:elf.male.attack";

    private final Map<EntityPlayerMP, UUID> previousElfAttackTargets = new WeakHashMap<EntityPlayerMP, UUID>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void replaceServerPlayerHurtSound(PlaySoundAtEntityEvent event) {
        if (!(event.entity instanceof EntityPlayerMP) || event.entity.worldObj.isRemote
            || !VANILLA_PLAYER_HURT_SOUND.equals(event.name)) {
            return;
        }

        String racialHurtSound = getHurtSoundForRace(RaceTraitService.getActiveRace((EntityPlayerMP) event.entity));
        if (racialHurtSound != null) {
            event.name = racialHurtSound;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void playElfAttackSoundForNewTarget(LivingAttackEvent event) {
        if (event.entityLiving.worldObj.isRemote || !(event.source.getEntity() instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP attacker = (EntityPlayerMP) event.source.getEntity();
        if (!isPlayerMeleeOrArrowAttack(event, attacker) || RaceTraitService.getActiveRace(attacker) != PlayerRace.ELF
            || PlayerRaceData.getSex(attacker) != PlayerSex.MALE) {
            return;
        }

        UUID targetId = event.entityLiving.getUniqueID();
        if (targetId.equals(previousElfAttackTargets.get(attacker))) {
            return;
        }
        previousElfAttackTargets.put(attacker, targetId);

        float pitch = 1.0F + (attacker.getRNG()
            .nextFloat()
            - attacker.getRNG()
                .nextFloat())
            * 0.2F;
        attacker.worldObj.playSoundAtEntity(attacker, ELF_ATTACK_SOUND, 1.0F, pitch);
    }

    private static boolean isPlayerMeleeOrArrowAttack(LivingAttackEvent event, EntityPlayerMP attacker) {
        if (event.source.getSourceOfDamage() == attacker) {
            return "player".equals(event.source.getDamageType());
        }
        return event.source.getSourceOfDamage() instanceof EntityArrow && "arrow".equals(event.source.getDamageType());
    }

    public static String getHurtSoundForRace(PlayerRace race) {
        if (race == PlayerRace.ORC || race == PlayerRace.URUK_HAI) {
            return ORC_HURT_SOUND;
        }
        if (race == PlayerRace.DWARF) {
            return DWARF_HURT_SOUND;
        }
        return null;
    }
}
