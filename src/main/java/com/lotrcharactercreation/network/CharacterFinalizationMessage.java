package com.lotrcharactercreation.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class CharacterFinalizationMessage implements IMessage {

    private boolean replacementConfirmed;
    private String expectedExistingPledgeCode;

    public CharacterFinalizationMessage() {}

    public CharacterFinalizationMessage(boolean replacementConfirmed, String expectedExistingPledgeCode) {
        this.replacementConfirmed = replacementConfirmed;
        this.expectedExistingPledgeCode = expectedExistingPledgeCode;
    }

    public boolean isReplacementConfirmed() {
        return replacementConfirmed;
    }

    public String getExpectedExistingPledgeCode() {
        return expectedExistingPledgeCode;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        replacementConfirmed = buffer.readBoolean();
        expectedExistingPledgeCode = ByteBufUtils.readUTF8String(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(replacementConfirmed);
        ByteBufUtils.writeUTF8String(buffer, expectedExistingPledgeCode == null ? "" : expectedExistingPledgeCode);
    }

    public static class Handler implements IMessageHandler<CharacterFinalizationMessage, IMessage> {

        @Override
        public IMessage onMessage(CharacterFinalizationMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ModNetwork.enqueueCharacterFinalization(
                player,
                message.isReplacementConfirmed(),
                message.getExpectedExistingPledgeCode());
            return null;
        }
    }
}
