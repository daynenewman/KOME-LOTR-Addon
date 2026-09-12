package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.Main;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class GateRepairSyncPacket implements IMessage {

    private int dimensionId;
    private int x;
    private int y;
    private int z;
    private boolean active;
    private int purchasedHealth;
    private int appliedHealth;
    private int activeTicks;
    private long pauseUntilTick;
    private int purchasedCoinValue;

    public GateRepairSyncPacket() {
    }

    public GateRepairSyncPacket(
            int dimensionId,
            int x,
            int y,
            int z,
            boolean active,
            int purchasedHealth,
            int appliedHealth,
            int activeTicks,
            long pauseUntilTick,
            int purchasedCoinValue
    ) {
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.active = active;
        this.purchasedHealth = purchasedHealth;
        this.appliedHealth = appliedHealth;
        this.activeTicks = activeTicks;
        this.pauseUntilTick = pauseUntilTick;
        this.purchasedCoinValue = purchasedCoinValue;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        dimensionId = buffer.readInt();
        x = buffer.readInt();
        y = buffer.readInt();
        z = buffer.readInt();
        active = buffer.readBoolean();
        purchasedHealth = buffer.readInt();
        appliedHealth = buffer.readInt();
        activeTicks = buffer.readInt();
        pauseUntilTick = buffer.readLong();
        purchasedCoinValue = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(dimensionId);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeBoolean(active);
        buffer.writeInt(purchasedHealth);
        buffer.writeInt(appliedHealth);
        buffer.writeInt(activeTicks);
        buffer.writeLong(pauseUntilTick);
        buffer.writeInt(purchasedCoinValue);
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

    public boolean isActive() {
        return active;
    }

    public int getPurchasedHealth() {
        return purchasedHealth;
    }

    public int getAppliedHealth() {
        return appliedHealth;
    }

    public int getActiveTicks() {
        return activeTicks;
    }

    public long getPauseUntilTick() {
        return pauseUntilTick;
    }

    public int getPurchasedCoinValue() {
        return purchasedCoinValue;
    }

    public static class Handler implements IMessageHandler<
            GateRepairSyncPacket,
            IMessage> {

        @Override
        public IMessage onMessage(
                GateRepairSyncPacket message,
                MessageContext context
        ) {
            Main.proxy.handleGateRepairSync(message);
            return null;
        }
    }
}
