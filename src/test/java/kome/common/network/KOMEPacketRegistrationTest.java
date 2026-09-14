package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/** Forge names pipeline entries after handler runtime classes, independently of packet IDs. */
public class KOMEPacketRegistrationTest {
    private static final Set<Integer> EXPECTED_DISCRIMINATORS = new HashSet<Integer>(Arrays.asList(
        0, 3, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
        25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36
    ));

    @After
    public void clearQueue() {
        KOMEPacketHandler.clearPendingServerTasks();
    }

    @Test
    public void currentRegistryHasUniqueDiscriminatorsAndServerHandlerIdentities() throws Exception {
        String source = source("src/main/java/kome/common/network/KOMEPacketHandler.java");
        Pattern registration = Pattern.compile(
            "registerMessage\\(.*?,\\s*(\\d+),\\s*Side\\.(CLIENT|SERVER)\\s*\\);");
        Set<Integer> discriminators = new HashSet<Integer>();
        int registrations = 0;
        int serverRegistrations = 0;
        for (String line : source.split("\\R")) {
            if (!line.contains("registerMessage")) {
                continue;
            }
            Matcher matcher = registration.matcher(line);
            assertTrue("Unrecognized packet registration: " + line, matcher.find());
            Integer discriminator = Integer.valueOf(matcher.group(1));
            assertTrue("Duplicate packet discriminator " + discriminator, discriminators.add(discriminator));
            registrations++;
            if ("SERVER".equals(matcher.group(2))) {
                assertTrue(line, line.contains("new ServerThreadHandler<"));
                assertTrue("Each server registration needs a distinct anonymous runtime class: " + line,
                    line.contains(") {}"));
                serverRegistrations++;
            }
        }

        assertEquals(33, registrations);
        assertEquals(EXPECTED_DISCRIMINATORS, discriminators);
        assertEquals(16, serverRegistrations);

        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        Set<String> handlerNames = new HashSet<String>();
        try {
            for (int index = 1; index <= serverRegistrations; index++) {
                Class<?> handlerClass = Class.forName(KOMEPacketHandler.class.getName() + "$" + index);
                assertTrue(KOMEPacketHandler.ServerThreadHandler.class.isAssignableFrom(handlerClass));
                assertTrue("Duplicate server handler runtime identity", handlerNames.add(handlerClass.getName()));
                channel.pipeline().addLast(handlerClass.getName(), new ChannelInboundHandlerAdapter());
            }
        } finally {
            channel.finish();
        }
    }

    @Test
    public void serverHandlerEnqueuesWithoutExecutingInline() {
        final AtomicInteger calls = new AtomicInteger();
        IMessageHandler<TestMessage, IMessage> delegate = new IMessageHandler<TestMessage, IMessage>() {
            @Override
            public IMessage onMessage(TestMessage message, MessageContext context) {
                calls.incrementAndGet();
                return null;
            }
        };
        KOMEPacketHandler.ServerThreadHandler<TestMessage> handler =
            new KOMEPacketHandler.ServerThreadHandler<TestMessage>(delegate);

        assertNull(handler.onMessage(new TestMessage(), null));
        assertEquals(0, calls.get());
        assertEquals(1, KOMEPacketHandler.pendingServerTaskCount());
        assertEquals(1, KOMEPacketHandler.runPendingServerTasks());
        assertEquals(1, calls.get());
        assertEquals(0, KOMEPacketHandler.pendingServerTaskCount());
    }

    @Test
    public void drainUsesASnapshotAndReportsFailuresWithoutBlockingLaterTasks() {
        final AtomicInteger calls = new AtomicInteger();
        KOMEPacketHandler.enqueueServerTask(new Runnable() {
            @Override
            public void run() {
                calls.incrementAndGet();
                KOMEPacketHandler.enqueueServerTask(new Runnable() {
                    @Override
                    public void run() {
                        calls.addAndGet(100);
                    }
                });
                throw new IllegalStateException("expected test failure");
            }
        });
        KOMEPacketHandler.enqueueServerTask(new Runnable() {
            @Override
            public void run() {
                calls.addAndGet(10);
            }
        });

        assertEquals(2, KOMEPacketHandler.runPendingServerTasks());
        assertEquals(11, calls.get());
        assertEquals(1, KOMEPacketHandler.pendingServerTaskCount());
        assertEquals(1, KOMEPacketHandler.runPendingServerTasks());
        assertEquals(111, calls.get());
    }

    @Test
    public void serverTickStartsWithInitializationThenDrainsAndStopClearsTheQueue() throws Exception {
        String events = source("src/main/java/kome/common/data/KOMEEvents.java");
        String tick = between(events, "public void onServerTick", "private void ensureAutomaticWaypointLinks");
        assertTrue(tick.contains("event.phase == TickEvent.Phase.START"));
        assertTrue(tick.indexOf("initializeIntegratedWorld()") < tick.indexOf("runPendingServerTasks()"));

        String addon = source("src/main/java/kome/common/KOMEAddon.java");
        String stopping = between(addon, "public void serverStopping", "\n    }");
        assertTrue(stopping.contains("KOMEPacketHandler.clearPendingServerTasks()"));
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue("Missing start marker: " + start, startIndex >= 0);
        assertTrue("Missing end marker: " + end, endIndex > startIndex);
        return source.substring(startIndex, endIndex);
    }

    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static final class TestMessage implements IMessage {
        @Override
        public void fromBytes(ByteBuf buf) {
        }

        @Override
        public void toBytes(ByteBuf buf) {
        }
    }
}
