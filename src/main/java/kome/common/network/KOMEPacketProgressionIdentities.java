package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import java.util.*;
import kome.common.KOMEAddon;

/** A bounded public roster, independent of private quest data and scoreboard teams. */
public final class KOMEPacketProgressionIdentities implements IMessage {
    public Map<UUID,String> suffixes=new LinkedHashMap<UUID,String>();
    public KOMEPacketProgressionIdentities() {}
    public KOMEPacketProgressionIdentities(Map<UUID,String> values){suffixes.putAll(values);}
    public void toBytes(ByteBuf b){if(suffixes.size()>1024)throw new IllegalArgumentException("Identity roster too large");b.writeShort(suffixes.size());for(Map.Entry<UUID,String> e:suffixes.entrySet()){b.writeLong(e.getKey().getMostSignificantBits());b.writeLong(e.getKey().getLeastSignificantBits());ByteBufUtils.writeUTF8String(b,e.getValue());}}
    public void fromBytes(ByteBuf b){int count=b.readUnsignedShort();if(count>1024)throw new IllegalArgumentException("Identity roster too large");suffixes.clear();for(int i=0;i<count;i++){UUID id=new UUID(b.readLong(),b.readLong());String suffix=ByteBufUtils.readUTF8String(b);if(suffix.length()>256)throw new IllegalArgumentException("Identity too long");suffixes.put(id,suffix);}}
    public static final class Handler implements IMessageHandler<KOMEPacketProgressionIdentities,IMessage>{public IMessage onMessage(final KOMEPacketProgressionIdentities m,MessageContext c){KOMEAddon.proxy.enqueueClientTask(()->KOMEAddon.proxy.updateProgressionIdentities(m.suffixes));return null;}}
}
