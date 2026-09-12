package com.lotrcharactercreation.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class AppearanceSelectionMessage implements IMessage {

    private String presetId;
    private boolean valid;

    public AppearanceSelectionMessage() {}

    public AppearanceSelectionMessage(String presetId) {
        this.presetId = presetId;
        valid = LegacyC2SProtocol
            .isValidRequiredString(presetId, LegacyC2SProtocol.MAX_APPEARANCE_PRESET_ID_BYTES);
    }

    public String getPresetId() {
        return presetId;
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        presetId = null;
        try {
            presetId = LegacyC2SProtocol
                .readRequiredString(buffer, LegacyC2SProtocol.MAX_APPEARANCE_PRESET_ID_BYTES);
            LegacyC2SProtocol.requireFullyRead(buffer);
            valid = true;
        } catch (RuntimeException exception) {
            LegacyC2SProtocol.warnMalformedOnce("AppearanceSelection", exception);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, presetId);
    }

    public static class Handler implements IMessageHandler<AppearanceSelectionMessage, IMessage> {

        @Override
        public IMessage onMessage(AppearanceSelectionMessage message, MessageContext context) {
            if (message.isValid()) {
                EntityPlayerMP player = context.getServerHandler().playerEntity;
                ModNetwork.enqueueAppearanceSelection(player, message.getPresetId());
            }
            return null;
        }
    }
}
