package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.Main;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class GateHealthSyncPacket implements IMessage {

    private int dimensionId;
    private int x;
    private int y;
    private int z;
    private int currentHealth;
    private int maxHealth;

    public GateHealthSyncPacket() {
    }

    public GateHealthSyncPacket(
            int dimensionId,
            int x,
            int y,
            int z,
            int currentHealth,
            int maxHealth
    ) {
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.currentHealth = currentHealth;
        this.maxHealth = maxHealth;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        dimensionId = buffer.readInt();
        x = buffer.readInt();
        y = buffer.readInt();
        z = buffer.readInt();
        currentHealth = buffer.readInt();
        maxHealth = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(dimensionId);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeInt(currentHealth);
        buffer.writeInt(maxHealth);
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getCurrentHealth() {
        return currentHealth;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public static class Handler implements IMessageHandler<
            GateHealthSyncPacket,
            IMessage> {

        @Override
        public IMessage onMessage(
                GateHealthSyncPacket message,
                MessageContext context
        ) {
            Main.proxy.handleGateHealthSync(message);
            return null;
        }
    }
}
