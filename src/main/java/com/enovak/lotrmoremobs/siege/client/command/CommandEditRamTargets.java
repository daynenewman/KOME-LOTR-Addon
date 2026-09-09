package com.enovak.lotrmoremobs.siege.client.command;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.network.RamControlActionPacket;
import com.enovak.lotrmoremobs.siege.ram.RamControlManager;
import com.enovak.lotrmoremobs.siege.ram.EntityBattleRam;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class CommandEditRamTargets extends CommandBase {

    @Override
    public String getCommandName() {
        return "ramtargets";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/ramtargets <dimension> <ramEntityId>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public List addTabCompletionOptions(
            ICommandSender sender,
            String[] args
    ) {
        Minecraft minecraft =
                Minecraft.getMinecraft();

        if (args == null
                || args.length == 0
                || minecraft.theWorld == null) {
            return null;
        }

        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(
                    args,
                    Integer.toString(
                            minecraft.theWorld.provider.dimensionId
                    )
            );
        }

        if (args.length == 2
                && minecraft.thePlayer != null) {

            List<String> ramIds =
                    new ArrayList<String>();

            for (Object loaded : minecraft.theWorld.loadedEntityList) {
                if (!(loaded instanceof EntityBattleRam)) {
                    continue;
                }

                EntityBattleRam ram =
                        (EntityBattleRam)loaded;

                ramIds.add(
                        Integer.toString(
                                ram.getEntityId()
                        )
                );
            }

            return getListOfStringsMatchingLastWord(
                    args,
                    ramIds.toArray(
                            new String[ramIds.size()]
                    )
            );
        }

        return null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args == null || args.length != 2) {
            return;
        }

        int dimensionId;
        int ramEntityId;

        try {
            dimensionId = Integer.parseInt(args[0]);
            ramEntityId = Integer.parseInt(args[1]);
        } catch (NumberFormatException ignored) {
            return;
        }

        if (ramEntityId <= 0) {
            return;
        }

        Main.network.sendToServer(new RamControlActionPacket(
                RamControlManager.ENTER_TARGET_MODE_REMOTE,
                dimensionId,
                ramEntityId
        ));

        Minecraft.getMinecraft().displayGuiScreen(null);
    }
}
