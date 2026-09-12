package com.lotrcharactercreation.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class CustomSkinRequestPageMessage implements IMessage {

    private long epoch;
    private long revision;
    private List<CustomSkinRequestIdentity> identities = Collections.emptyList();
    private boolean valid;

    public CustomSkinRequestPageMessage() {}

    public CustomSkinRequestPageMessage(long epoch, long revision, List<CustomSkinRequestIdentity> identities) {
        if (epoch <= 0L || revision < 0L || identities == null || identities.isEmpty()
            || identities.size() > CustomSkinSyncProtocol.MAX_REQUEST_IDENTITIES_PER_PAGE) {
            throw new IllegalArgumentException("custom skin request page is invalid");
        }
        int encodedBytes = 8 + 8 + 4;
        for (CustomSkinRequestIdentity identity : identities) {
            encodedBytes += CustomSkinSyncProtocol.encodedIdentityBytes(
                identity.getPresetId(),
                identity.getSha256());
        }
        if (encodedBytes > CustomSkinSyncProtocol.MAX_ENCODED_PACKET_BYTES) {
            throw new IllegalArgumentException("custom skin request page exceeds the packet limit");
        }
        this.epoch = epoch;
        this.revision = revision;
        this.identities = Collections.unmodifiableList(new ArrayList<CustomSkinRequestIdentity>(identities));
        valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        try {
            CustomSkinSyncProtocol.requirePacketSize(buffer);
            epoch = buffer.readLong();
            revision = buffer.readLong();
            int count = buffer.readInt();
            if (epoch <= 0L || revision < 0L || count <= 0
                || count > CustomSkinSyncProtocol.MAX_REQUEST_IDENTITIES_PER_PAGE) {
                throw new IllegalArgumentException("custom skin request page framing is invalid");
            }
            List<CustomSkinRequestIdentity> decoded = new ArrayList<CustomSkinRequestIdentity>(count);
            for (int index = 0; index < count; index++) {
                String[] identity = CustomSkinSyncProtocol.readIdentity(buffer);
                decoded.add(new CustomSkinRequestIdentity(identity[0], identity[1]));
            }
            CustomSkinSyncProtocol.requireFullyRead(buffer);
            identities = Collections.unmodifiableList(decoded);
            valid = true;
        } catch (RuntimeException exception) {
            identities = Collections.emptyList();
            CustomSkinSyncProtocol.warnMalformedOnce("SkinRequestPage", exception);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!valid) {
            throw new IllegalStateException("invalid custom skin request page message");
        }
        buffer.writeLong(epoch);
        buffer.writeLong(revision);
        buffer.writeInt(identities.size());
        for (CustomSkinRequestIdentity identity : identities) {
            CustomSkinSyncProtocol.writeIdentity(buffer, identity.getPresetId(), identity.getSha256());
        }
    }

    public long getEpoch() { return epoch; }
    public long getRevision() { return revision; }
    public List<CustomSkinRequestIdentity> getIdentities() { return identities; }
    public boolean isValid() { return valid; }

    public static final class Handler implements IMessageHandler<CustomSkinRequestPageMessage, IMessage> {

        @Override
        public IMessage onMessage(CustomSkinRequestPageMessage message, MessageContext context) {
            if (message.isValid()) {
                ServerCustomSkinSyncService.getInstance().enqueueRequests(
                    context.getServerHandler().playerEntity,
                    message.epoch,
                    message.revision,
                    message.identities);
            }
            return null;
        }
    }
}
