package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import kome.common.KOMEAddon;
import kome.common.data.KOMEVisualMarker;

/** One-way, server-authored snapshot containing presentation data only. */
public final class KOMEPacketVisualMarkers implements IMessage {
    private static final int MAX_MARKERS = 8;
    public List<KOMEVisualMarker> markers = new ArrayList<KOMEVisualMarker>();

    public KOMEPacketVisualMarkers() { }
    public KOMEPacketVisualMarkers(List<KOMEVisualMarker> markers) {
        if (markers != null) this.markers.addAll(markers);
    }

    @Override public void fromBytes(ByteBuf buffer) {
        int count = buffer.readUnsignedByte();
        if (count > MAX_MARKERS) throw new IllegalArgumentException("Too many KOME visual markers.");
        markers = new ArrayList<KOMEVisualMarker>(count);
        for (int i = 0; i < count; i++) {
            KOMEVisualMarker.Role role = KOMEVisualMarker.Role.forKey(ByteBufUtils.readUTF8String(buffer));
            String entityUuid = ByteBufUtils.readUTF8String(buffer);
            String title = ByteBufUtils.readUTF8String(buffer);
            String subtitle = ByteBufUtils.readUTF8String(buffer);
            int dimension = buffer.readInt();
            double x = buffer.readDouble(), y = buffer.readDouble(), z = buffer.readDouble();
            if (role == null) throw new IllegalArgumentException("Unknown KOME visual marker role.");
            markers.add(new KOMEVisualMarker(role, entityUuid, title, subtitle, dimension, x, y, z));
        }
    }

    @Override public void toBytes(ByteBuf buffer) {
        if (markers.size() > MAX_MARKERS) throw new IllegalArgumentException("Too many KOME visual markers.");
        buffer.writeByte(markers.size());
        for (KOMEVisualMarker marker : markers) {
            ByteBufUtils.writeUTF8String(buffer, marker.role.key);
            ByteBufUtils.writeUTF8String(buffer, marker.entityUuid);
            ByteBufUtils.writeUTF8String(buffer, marker.title);
            ByteBufUtils.writeUTF8String(buffer, marker.subtitle);
            buffer.writeInt(marker.dimension);
            buffer.writeDouble(marker.x); buffer.writeDouble(marker.y); buffer.writeDouble(marker.z);
        }
    }

    public static final class Handler implements IMessageHandler<KOMEPacketVisualMarkers, IMessage> {
        @Override public IMessage onMessage(KOMEPacketVisualMarkers message, MessageContext context) {
            final List<KOMEVisualMarker> snapshot = new ArrayList<KOMEVisualMarker>(message.markers);
            KOMEAddon.proxy.enqueueClientTask(new Runnable() {
                @Override public void run() { KOMEAddon.proxy.updateVisualMarkers(snapshot); }
            });
            return null;
        }
    }
}
