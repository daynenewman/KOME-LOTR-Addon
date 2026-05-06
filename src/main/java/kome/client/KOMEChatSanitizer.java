package kome.client;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;

import java.util.Iterator;

public class KOMEChatSanitizer {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onClientChat(ClientChatReceivedEvent event) {
        if (event.message == null) {
            event.message = new ChatComponentText("");
            return;
        }
        if (!hasTranslation(event.message)) {
            return;
        }
        try {
            event.message.getUnformattedText();
        } catch (Throwable t) {
            event.message = new ChatComponentText("Command message could not be displayed safely.");
        }
    }

    private boolean hasTranslation(IChatComponent component) {
        if (component instanceof ChatComponentTranslation) {
            Object[] args = ((ChatComponentTranslation) component).getFormatArgs();
            for (Object arg : args) {
                if (arg instanceof IChatComponent && hasTranslation((IChatComponent) arg)) {
                    return true;
                }
            }
            return true;
        }
        for (Iterator it = component.getSiblings().iterator(); it.hasNext();) {
            Object sibling = it.next();
            if (sibling instanceof IChatComponent && hasTranslation((IChatComponent) sibling)) {
                return true;
            }
        }
        return false;
    }
}
