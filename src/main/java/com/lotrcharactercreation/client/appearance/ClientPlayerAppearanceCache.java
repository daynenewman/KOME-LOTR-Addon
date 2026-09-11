package com.lotrcharactercreation.client.appearance;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.lotrcharactercreation.appearance.AppearancePresetRegistry;
import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.body.PlayerRaceSizeService;
import com.lotrcharactercreation.race.PlayerRace;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

public final class ClientPlayerAppearanceCache {

    private static final int ENTITY_REAPPLY_DELAY_TICKS = 1;
    private static final ClientPlayerAppearanceCache INSTANCE = new ClientPlayerAppearanceCache();

    private final Map<UUID, SynchronizedPlayerAppearance> appearances = new HashMap<UUID, SynchronizedPlayerAppearance>();
    private final Map<UUID, Integer> pendingReapplies = new HashMap<UUID, Integer>();

    private ClientPlayerAppearanceCache() {}

    public static ClientPlayerAppearanceCache getInstance() {
        return INSTANCE;
    }

    public SynchronizedPlayerAppearance get(UUID playerId) {
        return appearances.get(playerId);
    }

    public SynchronizedPlayerAppearance get(EntityPlayer player) {
        if (player == null) {
            return null;
        }

        SynchronizedPlayerAppearance appearance = get(player.getUniqueID());
        if (!isLocalPlayer(player)) {
            return appearance;
        }

        if (appearance != null) {
            return appearance;
        }
        return findByEntityId(player.getEntityId());
    }

    public void update(UUID playerId, int entityId, PlayerRace race, PlayerSex sex, String appearancePresetId,
        boolean characterCreationComplete) {
        if (playerId == null || race == null) {
            return;
        }

        PlayerSex safeSex = AppearancePresetRegistry.isSexValidForRace(race, sex) ? sex : null;
        String safePresetId = AppearancePresetRegistry.isPresetValid(
            ClientCustomSkinManager.getInstance().getCatalog(), race, safeSex, appearancePresetId)
            ? appearancePresetId
            : null;
        SynchronizedPlayerAppearance appearance = new SynchronizedPlayerAppearance(
            playerId,
            entityId,
            race,
            safeSex,
            safePresetId,
            characterCreationComplete);
        appearances.put(playerId, appearance);
        applyToCurrentEntity(appearance);
        scheduleReapply(playerId);
    }

    @SubscribeEvent
    public void entityJoinedWorld(EntityJoinWorldEvent event) {
        if (!event.world.isRemote || !(event.entity instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) event.entity;
        SynchronizedPlayerAppearance appearance = get(player);
        if (appearance == null) {
            return;
        }

        appearances.put(appearance.getPlayerId(), appearance.withEntityId(player.getEntityId()));
        PlayerRaceSizeService.applyRaceSize(player, appearance.getRace());
        scheduleReapply(appearance.getPlayerId());
    }

    @SubscribeEvent
    public void playerWokeUp(PlayerWakeUpEvent event) {
        if (event.entityPlayer.worldObj.isRemote) {
            SynchronizedPlayerAppearance appearance = get(event.entityPlayer);
            if (appearance != null) {
                scheduleReapply(appearance.getPlayerId());
            }
        }
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pendingReapplies.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, Integer>> iterator = pendingReapplies.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            if (entry.getValue()
                .intValue() > 0) {
                entry.setValue(
                    Integer.valueOf(
                        entry.getValue()
                            .intValue() - 1));
                continue;
            }

            SynchronizedPlayerAppearance appearance = appearances.get(entry.getKey());
            if (appearance != null) {
                applyToCurrentEntity(appearance);
            }
            iterator.remove();
        }
    }

    @SubscribeEvent
    public void worldUnloaded(WorldEvent.Unload event) {
        if (!event.world.isRemote) {
            return;
        }

        pendingReapplies.clear();
        for (Map.Entry<UUID, SynchronizedPlayerAppearance> entry : appearances.entrySet()) {
            entry.setValue(
                entry.getValue()
                    .withEntityId(-1));
        }
    }

    @SubscribeEvent
    public void connected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        clear();
    }

    @SubscribeEvent
    public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        clear();
    }

    private void applyToCurrentEntity(SynchronizedPlayerAppearance appearance) {
        Minecraft minecraft = Minecraft.getMinecraft();
        World world = minecraft.theWorld;
        if (world == null) {
            return;
        }

        Entity entity = appearance.getEntityId() < 0 ? null : world.getEntityByID(appearance.getEntityId());
        if (isAppearanceForEntity(appearance, entity)) {
            PlayerRaceSizeService.applyRaceSize((EntityPlayer) entity, appearance.getRace());
            return;
        }

        for (Object entry : world.playerEntities) {
            if (entry instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) entry;
                if (isAppearanceForEntity(appearance, player)) {
                    appearances.put(appearance.getPlayerId(), appearance.withEntityId(player.getEntityId()));
                    PlayerRaceSizeService.applyRaceSize(player, appearance.getRace());
                    return;
                }
            }
        }
    }

    private SynchronizedPlayerAppearance findByEntityId(int entityId) {
        if (entityId < 0) {
            return null;
        }

        for (SynchronizedPlayerAppearance appearance : appearances.values()) {
            if (appearance.getEntityId() == entityId) {
                return appearance;
            }
        }
        return null;
    }

    private boolean isAppearanceForEntity(SynchronizedPlayerAppearance appearance, Entity entity) {
        if (!(entity instanceof EntityPlayer)) {
            return false;
        }

        EntityPlayer player = (EntityPlayer) entity;
        if (isLocalPlayer(player)) {
            return appearance.getEntityId() == player.getEntityId();
        }
        return appearance.getPlayerId()
            .equals(player.getUniqueID());
    }

    private boolean isLocalPlayer(EntityPlayer player) {
        return player == Minecraft.getMinecraft().thePlayer;
    }

    private void scheduleReapply(UUID playerId) {
        if (appearances.containsKey(playerId)) {
            pendingReapplies.put(playerId, Integer.valueOf(ENTITY_REAPPLY_DELAY_TICKS));
        }
    }

    private void clear() {
        appearances.clear();
        pendingReapplies.clear();
    }

    public static final class SynchronizedPlayerAppearance {

        private final UUID playerId;
        private final int entityId;
        private final PlayerRace race;
        private final PlayerSex sex;
        private final String appearancePresetId;
        private final boolean characterCreationComplete;

        private SynchronizedPlayerAppearance(UUID playerId, int entityId, PlayerRace race, PlayerSex sex,
            String appearancePresetId, boolean characterCreationComplete) {
            this.playerId = playerId;
            this.entityId = entityId;
            this.race = race;
            this.sex = sex;
            this.appearancePresetId = appearancePresetId;
            this.characterCreationComplete = characterCreationComplete;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public int getEntityId() {
            return entityId;
        }

        public PlayerRace getRace() {
            return race;
        }

        public PlayerSex getSex() {
            return sex;
        }

        public String getAppearancePresetId() {
            return appearancePresetId;
        }

        public boolean isCharacterCreationComplete() {
            return characterCreationComplete;
        }

        private SynchronizedPlayerAppearance withEntityId(int newEntityId) {
            return new SynchronizedPlayerAppearance(
                playerId,
                newEntityId,
                race,
                sex,
                appearancePresetId,
                characterCreationComplete);
        }
    }
}
