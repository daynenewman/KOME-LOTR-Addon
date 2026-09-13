package kome.common.data;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import java.util.List;

/** Non-authoritative notification boundary. External sinks are optional and failure-safe. */
public final class KOMENotificationService {
    public interface Sink { void send(String message); }
    public interface MinecraftBroadcaster { void broadcast(String message); }
    private static volatile Sink externalSink;
    private static final MinecraftBroadcaster DEFAULT_MINECRAFT_BROADCASTER = new MinecraftBroadcaster() {
        public void broadcast(String message) {
            try {
                MinecraftServer server = MinecraftServer.getServer();
                if (server != null && server.getConfigurationManager() != null)
                    server.getConfigurationManager().sendChatMsg(new ChatComponentText(message));
            } catch (RuntimeException ignored) { }
        }
    };
    private static volatile MinecraftBroadcaster minecraftBroadcaster = DEFAULT_MINECRAFT_BROADCASTER;
    private KOMENotificationService() { }
    public static void setExternalSink(Sink sink) { externalSink = sink; }
    public static void setMinecraftBroadcaster(MinecraftBroadcaster broadcaster) { minecraftBroadcaster = broadcaster == null ? DEFAULT_MINECRAFT_BROADCASTER : broadcaster; }
    public static void resetMinecraftBroadcaster() { minecraftBroadcaster = DEFAULT_MINECRAFT_BROADCASTER; }
    public static void global(String message) { deliverMinecraft(message); deliverExternal(message); }
    public static void timeSensitive(String message) { deliverMinecraft(message); deliverExternal(message); }
    public static void dailySummary(List<String> lines) { if (lines != null && !lines.isEmpty()) { String message = "Daily audit: " + join(lines); deliverMinecraft(message); deliverExternal(message); } }
    public static void broadcast(String message) {
        deliverMinecraft(message); deliverExternal(message);
    }
    private static void deliverMinecraft(String message) {
        if (message == null || message.length() == 0) return;
        try { minecraftBroadcaster.broadcast(message); } catch (RuntimeException ignored) { }
    }
    private static void deliverExternal(String message) {
        Sink sink = externalSink; if (sink == null || message == null || message.length() == 0) return;
        try { sink.send(message); } catch (RuntimeException ignored) { /* notification must not fail gameplay */ }
    }
    private static String join(List<String> lines) { StringBuilder b = new StringBuilder(); for (String line : lines) { if (b.length() > 0) b.append(", "); b.append(line); } return b.toString(); }
}
