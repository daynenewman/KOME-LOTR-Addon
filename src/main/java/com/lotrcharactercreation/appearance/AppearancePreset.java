package com.lotrcharactercreation.appearance;

import net.minecraft.util.ResourceLocation;

import com.lotrcharactercreation.race.PlayerRace;

public final class AppearancePreset {

    private final String id;
    private final PlayerRace race;
    private final PlayerSex sex;
    private final String groupId;
    private final AppearanceSourceType sourceType;
    private final String displayName;
    private final ResourceLocation texture;
    private final String externalRelativePath;

    AppearancePreset(String id, PlayerRace race, PlayerSex sex, String groupId, String textureNamespace,
        String texturePath) {
        this(
            id,
            race,
            sex,
            groupId,
            AppearanceSourceType.RESOURCE,
            null,
            new ResourceLocation(textureNamespace, texturePath),
            null);
    }

    AppearancePreset(String id, PlayerRace race, PlayerSex sex, String groupId, AppearanceSourceType sourceType,
        String displayName, ResourceLocation texture) {
        this(id, race, sex, groupId, sourceType, displayName, texture, null);
    }

    AppearancePreset(String id, PlayerRace race, PlayerSex sex, String groupId, AppearanceSourceType sourceType,
        String displayName, ResourceLocation texture, String externalRelativePath) {
        if (sourceType == null) {
            throw new IllegalArgumentException("appearance source type cannot be null");
        }
        switch (sourceType) {
            case RESOURCE:
                if (texture == null || externalRelativePath != null) {
                    throw new IllegalArgumentException("resource appearance must have only a resource texture");
                }
                break;
            case MINECRAFT_ACCOUNT:
                if (texture != null || externalRelativePath != null) {
                    throw new IllegalArgumentException("Minecraft account appearance cannot have a texture path");
                }
                break;
            case EXTERNAL:
                if (texture != null || !isNormalizedRelativePath(externalRelativePath)) {
                    throw new IllegalArgumentException("external appearance must have only a relative file path");
                }
                break;
            default:
                throw new IllegalArgumentException("unsupported appearance source type: " + sourceType);
        }

        this.id = id;
        this.race = race;
        this.sex = sex;
        this.groupId = groupId;
        this.sourceType = sourceType;
        this.displayName = displayName;
        this.texture = texture;
        this.externalRelativePath = externalRelativePath;
    }

    public String getId() {
        return id;
    }

    public PlayerRace getRace() {
        return race;
    }

    public PlayerSex getSex() {
        return sex;
    }

    public String getGroupId() {
        return groupId;
    }

    public AppearanceSourceType getSourceType() {
        return sourceType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ResourceLocation getTexture() {
        return texture;
    }

    public String getTextureNamespace() {
        return texture == null ? null : texture.getResourceDomain();
    }

    public String getTexturePath() {
        return texture == null ? null : texture.getResourcePath();
    }

    public String getTextureResource() {
        return texture == null ? null : texture.toString();
    }

    public String getExternalRelativePath() {
        return externalRelativePath;
    }

    private static boolean isNormalizedRelativePath(String path) {
        if (path == null || path.isEmpty()
            || path.startsWith("/")
            || path.endsWith("/")
            || path.contains("\\")
            || path.contains("//")) {
            return false;
        }
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }
}
