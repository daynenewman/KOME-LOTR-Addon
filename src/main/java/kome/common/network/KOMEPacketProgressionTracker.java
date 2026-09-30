package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;
import kome.common.data.KOMEProgressionTrackerSnapshot;

/** One-way, presentation-only snapshot for the movable KOME progression tracker. */
public final class KOMEPacketProgressionTracker implements IMessage {
    public KOMEProgressionTrackerSnapshot snapshot=
        KOMEProgressionTrackerSnapshot.EMPTY;

    public KOMEPacketProgressionTracker() {
    }

    public KOMEPacketProgressionTracker(
            KOMEProgressionTrackerSnapshot snapshot) {
        this.snapshot=
            snapshot==null
                ?KOMEProgressionTrackerSnapshot.EMPTY
                :snapshot;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        snapshot=
            new KOMEProgressionTrackerSnapshot(
                buffer.readBoolean(),
                ByteBufUtils.readUTF8String(buffer),
                ByteBufUtils.readUTF8String(buffer),
                ByteBufUtils.readUTF8String(buffer),
                buffer.readFloat());
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        KOMEProgressionTrackerSnapshot value=
            snapshot==null
                ?KOMEProgressionTrackerSnapshot.EMPTY
                :snapshot;

        buffer.writeBoolean(value.visible);
        ByteBufUtils.writeUTF8String(buffer,value.iconKey);
        ByteBufUtils.writeUTF8String(buffer,value.objective);
        ByteBufUtils.writeUTF8String(buffer,value.progress);
        buffer.writeFloat(value.completion);
    }

    public static final class Handler
            implements IMessageHandler<
                KOMEPacketProgressionTracker,
                IMessage> {
        @Override
        public IMessage onMessage(
                KOMEPacketProgressionTracker message,
                MessageContext context) {
            final KOMEProgressionTrackerSnapshot value=
                message.snapshot;

            KOMEAddon.proxy.enqueueClientTask(
                new Runnable() {
                    @Override
                    public void run() {
                        KOMEAddon.proxy
                            .updateProgressionTracker(value);
                    }
                });

            return null;
        }
    }
}
