package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;
import kome.common.KOMEReflection;
import kome.common.config.KOMEConfigRegistry;
import kome.common.data.KOMEServerRecordBuilder;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

public class KOMEPacketServerRecordRequest implements IMessage {
    // FML constructs a fresh message on the receiving server before decoding; never transmitted by the client.
    final long receivedAtNanos;
    public KOMEPacketServerRecordRequest() { this(System.nanoTime()); }
    KOMEPacketServerRecordRequest(long receivedAtNanos) { this.receivedAtNanos = receivedAtNanos; }
    @Override public void fromBytes(ByteBuf buf) { }
    @Override public void toBytes(ByteBuf buf) { }

    public static class Handler implements IMessageHandler<KOMEPacketServerRecordRequest, IMessage> {
        private final LongSupplier clock;
        private final BiFunction<EntityPlayerMP, Boolean, List> projection;
        public Handler() {
            this(System::nanoTime, (player, administrative) -> KOMEServerRecordBuilder.build(KOMEReflection.getWorld(player), administrative));
        }
        Handler(LongSupplier clock, Function<EntityPlayerMP, List> projection) {
            this(clock, (player, administrative) -> projection.apply(player));
        }
        private Handler(LongSupplier clock, BiFunction<EntityPlayerMP, Boolean, List> projection) {
            this.clock = clock; this.projection = projection;
        }
        @Override public IMessage onMessage(KOMEPacketServerRecordRequest message, MessageContext ctx) {
            KOMEPacketHandler.Requester requester = KOMEPacketHandler.captureRequester(ctx);
            if (requester == null || !requester.isCurrent()) return null;
            KOMEServerRecordCooldown.Result result = KOMEPacketHandler.SERVER_RECORD_COOLDOWNS.acquire(
                requester.connection, message.receivedAtNanos, clock.getAsLong(), KOMEConfigRegistry.network().getServerRecordCooldownMillis());
            if (!result.accepted) {
                if (result.notify) requester.player.addChatMessage(new ChatComponentText(result.retryMillis < 0
                    ? "Server records unavailable: rate-limit capacity reached; retry shortly."
                    : "Server records request rejected: received during cooldown; request fresh records in " + result.retryMillis + " ms."));
                return null;
            }
            boolean administrative = requester.player.canCommandSenderUseCommand(2, "kome");
            List rows = projection.apply(requester.player, administrative);
            KOMEPacketServerRecordData.sendChunked(rows, requester.player, () -> requester.isCurrent()
                && (!administrative || requester.player.canCommandSenderUseCommand(2, "kome")));
            return null;
        }
    }
}
