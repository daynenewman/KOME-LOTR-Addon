package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.command.KOMECommandPopulation;
import kome.common.command.KOMECommandTroops;
import kome.common.command.KOMECompanyMutationAuthority;
import kome.common.KOMEReflection;
import kome.common.data.KOMEWorldData;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import java.util.Locale;

/** Whitelisted troop/company GUI intent; identity and authority are recomputed by the command path. */
public class KOMEPacketTroopGuiAction implements IMessage {
    public String action = "";
    public String companyId = "";
    public String value = "";
    public String tileId = "";
    public String previewToken = "";
    public long mapRequestId;

    public KOMEPacketTroopGuiAction() {
    }

    public KOMEPacketTroopGuiAction(String action, String companyId, String value, String tileId) {
        this(action, companyId, value, tileId, "");
    }

    public KOMEPacketTroopGuiAction(String action, String companyId, String value, String tileId, String previewToken) {
        this.action = safe(action);
        this.companyId = safe(companyId);
        this.value = safe(value);
        this.tileId = safe(tileId);
        this.previewToken = safe(previewToken);
        String normalized=this.action.trim().toLowerCase(Locale.ROOT);
        if ("list".equals(normalized) || "population_units".equals(normalized)
                || "tendency".equals(normalized) || "rename".equals(normalized)
                || "movement".equals(normalized) || "reclaim".equals(normalized)
                || "disband".equals(normalized) || "move".equals(normalized)) {
            if (kome.common.KOMEAddon.proxy != null)
                mapRequestId=kome.common.KOMEAddon.proxy.beginMapDetailRequest(
                    "population_units".equals(normalized)?"units":"companies",this.tileId,"");
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = ByteBufUtils.readUTF8String(buf);
        companyId = ByteBufUtils.readUTF8String(buf);
        value = ByteBufUtils.readUTF8String(buf);
        tileId = ByteBufUtils.readUTF8String(buf);
        previewToken = ByteBufUtils.readUTF8String(buf);
        mapRequestId = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, action);
        ByteBufUtils.writeUTF8String(buf, companyId);
        ByteBufUtils.writeUTF8String(buf, value);
        ByteBufUtils.writeUTF8String(buf, tileId);
        ByteBufUtils.writeUTF8String(buf, previewToken);
        buf.writeLong(mapRequestId);
    }

    public static class Handler implements IMessageHandler<KOMEPacketTroopGuiAction, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketTroopGuiAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            String action = safe(message.action).trim().toLowerCase(Locale.ROOT);
            String companyId = safe(message.companyId).trim();
            String rawValue = safe(message.value).trim();
            String value = rawValue.toLowerCase(Locale.ROOT);
            String tileId = safe(message.tileId).trim();
            String previewToken = safe(message.previewToken).trim();
            try {
                KOMECommandTroops troops = new KOMECommandTroops();
                if ("list".equals(action)) {
                    troops.listMapCompanies(player,tileId,message.mapRequestId);
                } else if ("create".equals(action)) {
                    throw new IllegalArgumentException("Manual company creation is retired; Campaign Hire uses canonical local Campaign Detachment admission.");
                } else if ("tendency".equals(action)) {
                    requireCompany(companyId);
                    if (!"aggressive".equals(value) && !"conservative".equals(value))
                        throw new IllegalArgumentException("Unknown company tendency.");
                    troops.processCommand(player, new String[] {"company", companyId, "tendency", value});
                    refresh(troops, player, tileId, message.mapRequestId);
                } else if ("rename".equals(action)) {
                    requireCompany(companyId);
                    require(rawValue, "Enter a company name.");
                    KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
                    if (!KOMECompanyMutationAuthority.currentOwner(data, player, data.armyCompanies.get(companyId)))
                        throw new IllegalArgumentException("Company unavailable.");
                    if (!data.renameHiringCompany(companyId, KOMEReflection.getEntityUUID(player), rawValue)) {
                        throw new IllegalArgumentException("Only the company owner may choose a valid company name.");
                    }
                    refresh(troops, player, tileId, message.mapRequestId);
                } else if ("movement".equals(action)) {
                    requireCompany(companyId);
                    if (!"stay".equals(value) && !"retreat".equals(value) && !"resume".equals(value)
                            && !"continue".equals(value) && !"halt".equals(value))
                        throw new IllegalArgumentException("Unknown movement response.");
                    troops.processCommand(player, new String[] {"movement", value, companyId});
                    refresh(troops, player, tileId, message.mapRequestId);
                } else if ("reclaim".equals(action) || "disband".equals(action)) {
                    requireCompany(companyId);
                    troops.processCommand(player, new String[] {"company", companyId, action});
                    refresh(troops, player, tileId, message.mapRequestId);
                } else if ("move".equals(action)) {
                    requireCompany(companyId);
                    require(value, "A destination tile is required.");
                    require(previewToken, "Movement confirmation has expired; preview the route again.");
                    troops.processCommand(player, new String[] {"movecompany", companyId, value, previewToken});
                    refresh(troops, player, tileId, message.mapRequestId);
                } else if ("recruit".equals(action)) {
                    require(tileId, "A recruitment tile is required.");
                    troops.processCommand(player, new String[] {"recruit", tileId});
                } else if ("population_units".equals(action)) {
                    new KOMECommandPopulation().sendMapUnitBreakdown(player,tileId,message.mapRequestId);
                } else {
                    throw new IllegalArgumentException("Unknown troop GUI action.");
                }
            } catch (CommandException error) {
                player.addChatMessage(new ChatComponentText("Troop action rejected: " + error.getMessage()));
            } catch (RuntimeException error) {
                player.addChatMessage(new ChatComponentText("Troop action rejected: " + error.getMessage()));
            }
            return null;
        }

        private static void refresh(KOMECommandTroops troops, EntityPlayerMP player, String tileId,
                long requestId) throws CommandException {
            troops.listMapCompanies(player,tileId,requestId);
        }

        private static void requireCompany(String companyId) {
            require(companyId, "A company record is required.");
        }

        private static void require(String value, String reason) {
            if (value == null || value.length() == 0) throw new IllegalArgumentException(reason);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
