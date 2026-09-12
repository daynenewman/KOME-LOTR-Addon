package com.lotrcharactercreation.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.lotrcharactercreation.LOTRCharacterCreation;
import com.lotrcharactercreation.appearance.CustomSkinManifestEntry;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class CustomSkinManifestPageMessage implements IMessage {

    private long epoch;
    private int pageIndex;
    private int pageCount;
    private List<CustomSkinManifestEntry> entries = Collections.emptyList();
    private boolean valid;

    public CustomSkinManifestPageMessage() {}

    public CustomSkinManifestPageMessage(long epoch, int pageIndex, int pageCount,
        List<CustomSkinManifestEntry> entries) {
        if (epoch <= 0L || pageCount <= 0 || pageIndex < 0 || pageIndex >= pageCount
            || entries == null || entries.isEmpty()
            || entries.size() > CustomSkinSyncProtocol.MAX_MANIFEST_PAGE_ENTRIES) {
            throw new IllegalArgumentException("custom skin manifest page is invalid");
        }
        int encodedBytes = CustomSkinManifestPages.PAGE_FIXED_BYTES;
        for (CustomSkinManifestEntry entry : entries) {
            encodedBytes += CustomSkinSyncProtocol.encodedManifestEntryBytes(entry);
        }
        if (encodedBytes > CustomSkinSyncProtocol.MAX_ENCODED_PACKET_BYTES) {
            throw new IllegalArgumentException("custom skin manifest page exceeds the packet limit");
        }
        this.epoch = epoch;
        this.pageIndex = pageIndex;
        this.pageCount = pageCount;
        this.entries = Collections.unmodifiableList(new ArrayList<CustomSkinManifestEntry>(entries));
        valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        try {
            CustomSkinSyncProtocol.requirePacketSize(buffer);
            epoch = buffer.readLong();
            pageIndex = buffer.readInt();
            pageCount = buffer.readInt();
            int count = buffer.readInt();
            if (epoch <= 0L || pageCount <= 0 || pageIndex < 0 || pageIndex >= pageCount
                || count <= 0 || count > CustomSkinSyncProtocol.MAX_MANIFEST_PAGE_ENTRIES) {
                throw new IllegalArgumentException("custom skin manifest page framing is invalid");
            }
            List<CustomSkinManifestEntry> decoded = new ArrayList<CustomSkinManifestEntry>(count);
            for (int index = 0; index < count; index++) {
                decoded.add(CustomSkinSyncProtocol.readManifestEntry(buffer));
            }
            CustomSkinSyncProtocol.requireFullyRead(buffer);
            entries = Collections.unmodifiableList(decoded);
            valid = true;
        } catch (RuntimeException exception) {
            entries = Collections.emptyList();
            CustomSkinSyncProtocol.warnMalformedOnce("ManifestPage", exception);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!valid) {
            throw new IllegalStateException("invalid custom skin manifest page message");
        }
        buffer.writeLong(epoch);
        buffer.writeInt(pageIndex);
        buffer.writeInt(pageCount);
        buffer.writeInt(entries.size());
        for (CustomSkinManifestEntry entry : entries) {
            CustomSkinSyncProtocol.writeManifestEntry(buffer, entry);
        }
    }

    public long getEpoch() { return epoch; }
    public int getPageIndex() { return pageIndex; }
    public int getPageCount() { return pageCount; }
    public List<CustomSkinManifestEntry> getEntries() { return entries; }
    public boolean isValid() { return valid; }

    public static final class Handler implements IMessageHandler<CustomSkinManifestPageMessage, IMessage> {

        @Override
        public IMessage onMessage(CustomSkinManifestPageMessage message, MessageContext context) {
            if (message.isValid()) {
                LOTRCharacterCreation.proxy.handleCustomSkinManifestPage(
                    message.epoch,
                    message.pageIndex,
                    message.pageCount,
                    message.entries);
            }
            return null;
        }
    }
}
