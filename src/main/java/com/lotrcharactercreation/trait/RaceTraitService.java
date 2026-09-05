package com.lotrcharactercreation.trait;

import java.util.UUID;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;

public final class RaceTraitService {

    private static final UUID MAX_HEALTH_MODIFIER_ID = UUID.fromString("c079dc17-91d4-4a1c-9667-bb0c6a38a7a9");
    private static final UUID KNOCKBACK_RESISTANCE_MODIFIER_ID = UUID
        .fromString("c0f1fe52-2e19-4caf-a2f6-c9a1f187bb77");
    private static final UUID DWARF_MINING_SYNC_MARKER_ID = UUID.fromString("7d7cc5a4-96f4-430e-9fc5-f0e17bb2e851");
    private static final UUID ELF_JUMP_SYNC_MARKER_ID = UUID.fromString("e8cae462-8d1c-4b67-b2ae-411fd9b95a77");
    private static final UUID URUK_HAI_CHOPPING_SYNC_MARKER_ID = UUID
        .fromString("5f056a14-fd6f-46d6-aef0-c68b10a1a135");
    private static final UUID HOBBIT_ADVANCED_TRAIT_SYNC_MARKER_ID = UUID
        .fromString("42d0ea45-c808-4276-82bc-d33fa72514a5");
    private static final UUID ORC_ENVIRONMENT_SPEED_MODIFIER_ID = UUID
        .fromString("cea1d223-6ebd-4e3f-b6d0-1b597e346112");
    private static final String MAX_HEALTH_MODIFIER_NAME = "LOTR Character Creation racial max health";
    private static final String KNOCKBACK_RESISTANCE_MODIFIER_NAME = "LOTR Character Creation racial knockback resistance";
    private static final String DWARF_MINING_SYNC_MARKER_NAME = "LOTR Character Creation Dwarf mining sync";
    private static final String ELF_JUMP_SYNC_MARKER_NAME = "LOTR Character Creation Elf jump sync";
    private static final String URUK_HAI_CHOPPING_SYNC_MARKER_NAME = "LOTR Character Creation Uruk-hai chopping sync";
    private static final String HOBBIT_ADVANCED_TRAIT_SYNC_MARKER_NAME = "LOTR Character Creation Hobbit advanced trait sync";
    private static final String ORC_ENVIRONMENT_SPEED_MODIFIER_NAME = "LOTR Character Creation Orc environment speed";

    private RaceTraitService() {}

    public static boolean areTraitsActive(EntityPlayerMP player) {
        return player != null && PlayerRaceData.isCharacterCreationComplete(player);
    }

    public static PlayerRace getAuthoritativeRace(EntityPlayerMP player) {
        return PlayerRaceData.getRace(player);
    }

    public static PlayerRace getActiveRace(EntityPlayerMP player) {
        return areTraitsActive(player) ? getAuthoritativeRace(player) : null;
    }

    public static boolean hasDwarfMiningTrait(EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            return getActiveRace((EntityPlayerMP) player) == PlayerRace.DWARF;
        }
        return player != null && player.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
            .getModifier(DWARF_MINING_SYNC_MARKER_ID) != null;
    }

    public static boolean hasOrcEnvironmentTrait(EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            return getActiveRace((EntityPlayerMP) player) == PlayerRace.ORC;
        }
        return hasOrcEnvironmentSpeedModifier(player);
    }

    public static boolean hasElfJumpTrait(EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            return getActiveRace((EntityPlayerMP) player) == PlayerRace.ELF;
        }
        return player != null && player.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
            .getModifier(ELF_JUMP_SYNC_MARKER_ID) != null;
    }

    public static boolean hasUrukHaiChoppingTrait(EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            return getActiveRace((EntityPlayerMP) player) == PlayerRace.URUK_HAI;
        }
        return player != null && player.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
            .getModifier(URUK_HAI_CHOPPING_SYNC_MARKER_ID) != null;
    }

    public static boolean hasHobbitAdvancedTrait(EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            return getActiveRace((EntityPlayerMP) player) == PlayerRace.HOBBIT;
        }
        return player != null && player.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
            .getModifier(HOBBIT_ADVANCED_TRAIT_SYNC_MARKER_ID) != null;
    }

    static boolean hasOrcEnvironmentSpeedModifier(EntityPlayer player) {
        return player != null && player.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
            .getModifier(ORC_ENVIRONMENT_SPEED_MODIFIER_ID) != null;
    }

    public static void refreshDerivedAttributes(EntityPlayerMP player) {
        if (player == null) {
            return;
        }

        PlayerRace race = getActiveRace(player);
        boolean orcDaylightExposed = race == PlayerRace.ORC && OrcEnvironmentService.isDaylightExposed(player);
        replaceModifier(
            player.getEntityAttribute(SharedMonsterAttributes.maxHealth),
            MAX_HEALTH_MODIFIER_ID,
            MAX_HEALTH_MODIFIER_NAME,
            race == null ? 0.0D : getMaxHealthModifier(race, orcDaylightExposed));
        refreshKnockbackResistance(player);
        replaceMarker(
            player.getEntityAttribute(SharedMonsterAttributes.movementSpeed),
            DWARF_MINING_SYNC_MARKER_ID,
            DWARF_MINING_SYNC_MARKER_NAME,
            race == PlayerRace.DWARF);
        replaceMarker(
            player.getEntityAttribute(SharedMonsterAttributes.movementSpeed),
            ELF_JUMP_SYNC_MARKER_ID,
            ELF_JUMP_SYNC_MARKER_NAME,
            race == PlayerRace.ELF);
        replaceMarker(
            player.getEntityAttribute(SharedMonsterAttributes.movementSpeed),
            URUK_HAI_CHOPPING_SYNC_MARKER_ID,
            URUK_HAI_CHOPPING_SYNC_MARKER_NAME,
            race == PlayerRace.URUK_HAI);
        replaceMarker(
            player.getEntityAttribute(SharedMonsterAttributes.movementSpeed),
            HOBBIT_ADVANCED_TRAIT_SYNC_MARKER_ID,
            HOBBIT_ADVANCED_TRAIT_SYNC_MARKER_NAME,
            race == PlayerRace.HOBBIT);
        replaceModifier(
            player.getEntityAttribute(SharedMonsterAttributes.movementSpeed),
            ORC_ENVIRONMENT_SPEED_MODIFIER_ID,
            ORC_ENVIRONMENT_SPEED_MODIFIER_NAME,
            race == PlayerRace.ORC ? orcDaylightExposed ? -0.15D : 0.25D : 0.0D,
            2);

        float maximumHealth = player.getMaxHealth();
        if (player.getHealth() > maximumHealth) {
            player.setHealth(maximumHealth);
        }
        OrcEnvironmentService.onTraitsRefreshed(player, race);
        DwarfTraitService.refresh(player);
        UrukHaiTraitService.refresh(player);
    }

    static void prepareMaxHealthForLoad(EntityPlayerMP player) {
        if (player == null) {
            return;
        }

        PlayerRace race = getActiveRace(player);
        replaceModifier(
            player.getEntityAttribute(SharedMonsterAttributes.maxHealth),
            MAX_HEALTH_MODIFIER_ID,
            MAX_HEALTH_MODIFIER_NAME,
            race == null ? 0.0D : getMaxHealthModifier(race, false));
    }

    private static double getMaxHealthModifier(PlayerRace race, boolean orcDaylightExposed) {
        switch (race) {
            case ELF:
                return 10.0D;
            case DWARF:
            case URUK_HAI:
                return 6.0D;
            case HOBBIT:
                return -4.0D;
            case ORC:
                return orcDaylightExposed ? -2.0D : 2.0D;
            case MAN:
            default:
                return 0.0D;
        }
    }

    static void refreshKnockbackResistance(EntityPlayerMP player) {
        PlayerRace race = getActiveRace(player);
        replaceModifier(
            player.getEntityAttribute(SharedMonsterAttributes.knockbackResistance),
            KNOCKBACK_RESISTANCE_MODIFIER_ID,
            KNOCKBACK_RESISTANCE_MODIFIER_NAME,
            getKnockbackResistanceModifier(player, race));
    }

    private static double getKnockbackResistanceModifier(EntityPlayerMP player, PlayerRace race) {
        if (race == PlayerRace.DWARF) {
            return DwarfTraitService.getFeastKnockbackResistance(PlayerRaceData.getDwarfFeast(player));
        }
        if (race == PlayerRace.URUK_HAI) {
            return 0.50D;
        }
        return 0.0D;
    }

    private static void replaceModifier(IAttributeInstance attribute, UUID modifierId, String name, double amount) {
        replaceModifier(attribute, modifierId, name, amount, 0);
    }

    private static void replaceModifier(IAttributeInstance attribute, UUID modifierId, String name, double amount,
        int operation) {
        AttributeModifier oldModifier = attribute.getModifier(modifierId);
        if (oldModifier != null) {
            attribute.removeModifier(oldModifier);
        }
        if (amount != 0.0D) {
            attribute.applyModifier(new AttributeModifier(modifierId, name, amount, operation).setSaved(false));
        }
    }

    private static void replaceMarker(IAttributeInstance attribute, UUID modifierId, String name, boolean active) {
        AttributeModifier oldModifier = attribute.getModifier(modifierId);
        if (oldModifier != null) {
            attribute.removeModifier(oldModifier);
        }
        if (active) {
            attribute.applyModifier(new AttributeModifier(modifierId, name, 0.0D, 0).setSaved(false));
        }
    }
}
