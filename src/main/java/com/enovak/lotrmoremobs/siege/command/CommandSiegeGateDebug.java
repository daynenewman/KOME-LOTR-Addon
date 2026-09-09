package com.enovak.lotrmoremobs.siege.command;

import com.enovak.lotrmoremobs.siege.SiegeRegistry;
import com.enovak.lotrmoremobs.siege.gate.GateRegistry;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import java.util.List;

/**
 * Creative/operator maintenance tools for testing or repairing gate health.
 * Durable EDIT_EXISTING pause/resume test surfaces are intentionally absent
 * from release builds.
 */
public class CommandSiegeGateDebug extends CommandBase {

    private static final double TARGET_DISTANCE = 6.0D;

    @Override
    public String getCommandName() {
        return "siegegate";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/siegegate damage <amount> | "
                + "/siegegate health <full|amount>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);

        if (!player.capabilities.isCreativeMode) {
            throw new CommandException(
                    "The siege-gate maintenance command requires Creative mode."
            );
        }

        /*
         * Existing siege-damage tester.
         */
        if (args.length == 2
                && "damage".equalsIgnoreCase(args[0])) {

            int amount = parseIntBounded(
                    sender,
                    args[1],
                    1,
                    Integer.MAX_VALUE
            );

            TileEntitySiegeGate gate = getLookedAtGate(player);
            if (gate == null || !gate.isFinalized()) {
                throw new CommandException(
                        "Look at a finalized Siege Gate controller or GatePart."
                );
            }

            if (!gate.applySiegeDamage(amount)) {
                throw new CommandException(
                        "Siege damage was not applied; the gate may be breached."
                );
            }

            sender.addChatMessage(new ChatComponentText(
                    "Siege Gate health: "
                            + gate.getCurrentHealth()
                            + " / "
                            + gate.getMaxHealth()
            ));
            return;
        }

        /*
         * Sets the looked-at finalized gate to an absolute health value.
         * "full" restores it to its configured maximum.
         */
        if (args.length == 2
                && "health".equalsIgnoreCase(args[0])) {

            TileEntitySiegeGate gate =
                    getLookedAtGate(
                            player
                    );

            if (gate == null
                    || !gate.isFinalized()) {

                throw new CommandException(
                        "Look at a finalized Siege Gate controller or GatePart."
                );
            }

            int requestedHealth;

            if ("full".equalsIgnoreCase(args[1])
                    || "max".equalsIgnoreCase(args[1])) {

                requestedHealth =
                        gate.getMaxHealth();

            } else {
                requestedHealth =
                        parseIntBounded(
                                sender,
                                args[1],
                                0,
                                gate.getMaxHealth()
                        );
            }

            if (!gate.setHealthForCommand(
                    requestedHealth
            )
                    && gate.getCurrentHealth()
                    != requestedHealth) {

                throw new CommandException(
                        "Siege Gate health could not be changed."
                );
            }

            sender.addChatMessage(
                    new ChatComponentText(
                            "Siege Gate health set to "
                                    + gate.getCurrentHealth()
                                    + " / "
                                    + gate.getMaxHealth()
                    )
            );

            return;
        }

        throw new WrongUsageException(getCommandUsage(sender));
    }

    private static TileEntitySiegeGate getLookedAtGate(
            EntityPlayerMP player
    ) {
        Vec3 start = Vec3.createVectorHelper(
                player.posX,
                player.posY + player.getEyeHeight(),
                player.posZ
        );
        Vec3 look = player.getLookVec();
        Vec3 end = start.addVector(
                look.xCoord * TARGET_DISTANCE,
                look.yCoord * TARGET_DISTANCE,
                look.zCoord * TARGET_DISTANCE
        );
        MovingObjectPosition hit = player.worldObj.func_147447_a(
                start,
                end,
                false,
                false,
                true
        );
        if (hit == null
                || hit.typeOfHit
                != MovingObjectPosition.MovingObjectType.BLOCK) {
            return null;
        }

        TileEntity tileEntity = player.worldObj.getTileEntity(
                hit.blockX,
                hit.blockY,
                hit.blockZ
        );
        if (tileEntity instanceof TileEntitySiegeGate) {
            return (TileEntitySiegeGate)tileEntity;
        }
        if (player.worldObj.getBlock(
                hit.blockX,
                hit.blockY,
                hit.blockZ
        ) == SiegeRegistry.gatePart) {
            return GateRegistry.getController(
                    player.worldObj,
                    hit.blockX,
                    hit.blockY,
                    hit.blockZ
            );
        }
        return null;
    }

    @Override
    public List addTabCompletionOptions(
            ICommandSender sender,
            String[] args
    ) {
        if (args == null
                || args.length == 0) {
            return null;
        }

        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(
                    args,
                    "damage",
                    "health"
            );
        }

        if (args.length == 2
                && "health".equalsIgnoreCase(args[0])) {

            return getListOfStringsMatchingLastWord(
                    args,
                    "full",
                    "100",
                    "500",
                    "1000"
            );
        }

        return null;
    }


}
