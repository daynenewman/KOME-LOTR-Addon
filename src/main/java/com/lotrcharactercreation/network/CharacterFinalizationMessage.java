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
    private boolean valid;

    public CharacterFinalizationMessage() {}

    public CharacterFinalizationMessage(boolean replacementConfirmed, String expectedExistingPledgeCode) {
        this.replacementConfirmed = replacementConfirmed;
        this.expectedExistingPledgeCode = expectedExistingPledgeCode;
        valid = LegacyC2SProtocol
            .isValidNullableString(expectedExistingPledgeCode, LegacyC2SProtocol.MAX_PLEDGE_CODE_BYTES);
    }

    public boolean isReplacementConfirmed() {
        return replacementConfirmed;
    }

    public String getExpectedExistingPledgeCode() {
        return expectedExistingPledgeCode;
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        expectedExistingPledgeCode = null;
        try {
            replacementConfirmed = buffer.readBoolean();
            expectedExistingPledgeCode = LegacyC2SProtocol
                .readNullableString(buffer, LegacyC2SProtocol.MAX_PLEDGE_CODE_BYTES);
            LegacyC2SProtocol.requireFullyRead(buffer);
            valid = true;
        } catch (RuntimeException exception) {
            LegacyC2SProtocol.warnMalformedOnce("CharacterFinalization", exception);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(replacementConfirmed);
        ByteBufUtils.writeUTF8String(buffer, expectedExistingPledgeCode == null ? "" : expectedExistingPledgeCode);
    }

    public static class Handler implements IMessageHandler<CharacterFinalizationMessage, IMessage> {

        @Override
        public IMessage onMessage(CharacterFinalizationMessage message, MessageContext context) {
            if (message.isValid()) {
                EntityPlayerMP player = context.getServerHandler().playerEntity;
                ModNetwork.enqueueCharacterFinalization(
                    player,
                    message.isReplacementConfirmed(),
                    message.getExpectedExistingPledgeCode());
            }
            return null;
        }
    }
}
