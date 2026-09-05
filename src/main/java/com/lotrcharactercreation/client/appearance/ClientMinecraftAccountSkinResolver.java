package com.lotrcharactercreation.client.appearance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderPlayerEvent;

import com.lotrcharactercreation.appearance.AppearancePreset;
import com.lotrcharactercreation.appearance.AppearancePresetRegistry;
import com.lotrcharactercreation.appearance.AppearanceSourceType;
import com.lotrcharactercreation.client.appearance.ClientPlayerAppearanceCache.SynchronizedPlayerAppearance;
import com.lotrcharactercreation.race.PlayerRace;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.authlib.minecraft.MinecraftSessionService;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ClientMinecraftAccountSkinResolver {

    private static final ClientMinecraftAccountSkinResolver INSTANCE = new ClientMinecraftAccountSkinResolver();

    private final Map<UUID, ResourceLocation> resolvedSkins = new HashMap<UUID, ResourceLocation>();
    private final Set<UUID> pendingProfiles = new HashSet<UUID>();
    private final Set<UUID> failedProfiles = new HashSet<UUID>();

    private ClientMinecraftAccountSkinResolver() {}

    public static ClientMinecraftAccountSkinResolver getInstance() {
        return INSTANCE;
    }

    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
    public void beforePlayerRender(RenderPlayerEvent.Pre event) {
        EntityPlayer player = event.entityPlayer;
        if (!(player instanceof AbstractClientPlayer) || !usesMinecraftAccountSkin(player)) {
            return;
        }

        ResourceLocation skin = resolve(player.getGameProfile());
        if (skin != null) {
            ((AbstractClientPlayer) player).func_152121_a(Type.SKIN, skin);
        }
    }

    public ResourceLocation resolve(GameProfile profile) {
        if (profile == null || profile.getId() == null) {
            return null;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer != null && profile.getId()
            .equals(minecraft.thePlayer.getUniqueID())) {
            return minecraft.thePlayer.getLocationSkin();
        }

        ResourceLocation resolved = resolvedSkins.get(profile.getId());
        if (resolved != null) {
            return resolved;
        }
        request(profile);
        return null;
    }

    private void request(final GameProfile profile) {
        final UUID playerId = profile.getId();
        if (pendingProfiles.contains(playerId) || failedProfiles.contains(playerId)) {
            return;
        }
        pendingProfiles.add(playerId);

        final Minecraft minecraft = Minecraft.getMinecraft();
        final MinecraftSessionService sessionService = minecraft.func_152347_ac();
        Thread worker = new Thread(new Runnable() {

            @Override
            public void run() {
                MinecraftProfileTexture skin = null;
                try {
                    Map<Type, MinecraftProfileTexture> textures = sessionService.getTextures(profile, false);
                    skin = textures.get(Type.SKIN);
                    if (skin == null) {
                        GameProfile filledProfile = sessionService
                            .fillProfileProperties(new GameProfile(profile.getId(), profile.getName()), false);
                        if (filledProfile != null) {
                            skin = sessionService.getTextures(filledProfile, false)
                                .get(Type.SKIN);
                        }
                    }
                } catch (RuntimeException ignored) {
                    skin = null;
                }

                final MinecraftProfileTexture resolvedSkin = skin;
                minecraft.func_152344_a(new Runnable() {

                    @Override
                    public void run() {
                        complete(playerId, resolvedSkin);
                    }
                });
            }
        }, "LOTR Character Creation account skin resolver");
        worker.setDaemon(true);
        worker.start();
    }

    private void complete(UUID playerId, MinecraftProfileTexture profileTexture) {
        pendingProfiles.remove(playerId);
        if (profileTexture == null) {
            failedProfiles.add(playerId);
            return;
        }

        ResourceLocation skin = Minecraft.getMinecraft()
            .func_152342_ad()
            .func_152792_a(profileTexture, Type.SKIN);
        resolvedSkins.put(playerId, skin);
        applyToLoadedPlayers(playerId, skin);
    }

    private static void applyToLoadedPlayers(UUID playerId, ResourceLocation skin) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) {
            return;
        }

        for (Object entry : world.playerEntities) {
            if (entry instanceof AbstractClientPlayer) {
                AbstractClientPlayer player = (AbstractClientPlayer) entry;
                if (playerId.equals(player.getUniqueID())) {
                    player.func_152121_a(Type.SKIN, skin);
                }
            }
        }
    }

    private static boolean usesMinecraftAccountSkin(EntityPlayer player) {
        SynchronizedPlayerAppearance appearance = ClientPlayerAppearanceCache.getInstance()
            .get(player);
        if (appearance == null || !appearance.isCharacterCreationComplete() || appearance.getRace() != PlayerRace.MAN) {
            return false;
        }

        String presetId = appearance.getAppearancePresetId();
        AppearancePreset preset = AppearancePresetRegistry.findById(presetId);
        return preset != null && AppearancePresetRegistry.isPresetValid(PlayerRace.MAN, appearance.getSex(), presetId)
            && preset.getSourceType() == AppearanceSourceType.MINECRAFT_ACCOUNT;
    }
}
