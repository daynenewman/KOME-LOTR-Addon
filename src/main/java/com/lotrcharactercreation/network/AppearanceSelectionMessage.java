package com.lotrcharactercreation.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class AppearanceSelectionMessage implements IMessage {

    private String presetId;

    public AppearanceSelectionMessage() {}

    public AppearanceSelectionMessage(String presetId) {
        this.presetId = presetId;
    }

    public String getPresetId() {
        return presetId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        presetId = ByteBufUtils.readUTF8String(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, presetId);
    }

    public static class Handler implements IMessageHandler<AppearanceSelectionMessage, IMessage> {

        @Override
        public IMessage onMessage(AppearanceSelectionMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ModNetwork.enqueueAppearanceSelection(player, message.getPresetId());
            return null;
        }
    }
}
