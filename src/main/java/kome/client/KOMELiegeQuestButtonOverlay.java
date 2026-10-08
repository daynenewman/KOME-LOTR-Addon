package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import kome.common.data.KOMELiegeOfferQuest;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketRelationshipAction;
import lotr.client.gui.LOTRGuiTradeUnitTradeInteract;
import lotr.client.gui.LOTRGuiUnitTradeInteract;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.quest.LOTRMiniQuest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;

/** Adds a KOME Quest entry point to an eligible prospective Liege's ordinary LOTR interaction GUI. */
public final class KOMELiegeQuestButtonOverlay {
    private static final int QUEST_BUTTON_ID = 26020;
    private static final KOMEStandingTrialEligibilityState ELIGIBILITY =
        new KOMEStandingTrialEligibilityState();
    private static GuiScreen activeGui;
    private static List<GuiButton> activeButtons;

    public static void updateEligibility(
            int entityId,
            UUID entityUuid,
            boolean eligible,
            boolean passiveOffer,
            boolean offering,
            int offerColor) {
        ELIGIBILITY.update(entityId, entityUuid, eligible, passiveOffer);
        updateNativeIndicator(entityId, entityUuid, offering, offerColor);
        addQuestButton(activeGui, activeButtons);
    }

    public static void reset() {
        ELIGIBILITY.clear();
        activeGui = null;
        activeButtons = null;
    }

    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        activeGui = event.gui;
        activeButtons = event.buttonList;
        addQuestButton(event.gui, event.buttonList);
    }

    private static void addQuestButton(GuiScreen gui, List<GuiButton> buttons) {
        LOTREntityNPC npc = npc(gui);
        syncQuestButton(gui, buttons, isStandingTrialAvailableFor(
            Minecraft.getMinecraft().thePlayer, npc));
    }

    static void syncQuestButton(
            GuiScreen gui,
            List<GuiButton> buttons,
            boolean eligible) {
        if (!eligible) {
            GuiButton existing = findButton(buttons, QUEST_BUTTON_ID);
            if (existing != null) {
                buttons.remove(existing);
                if(gui instanceof LOTRGuiUnitTradeInteract) {
                    GuiButton talk=findButton(buttons,0),hire=findButton(buttons,1);
                    if(talk!=null)talk.xPosition=gui.width/2-65;
                    if(hire!=null)hire.xPosition=gui.width/2+5;
                } else if(gui instanceof LOTRGuiTradeUnitTradeInteract) {
                    GuiButton hire=findButton(buttons,-1);
                    if(hire!=null){hire.xPosition=gui.width/2-65;hire.width=130;}
                }
            }
            return;
        }
        if (findButton(buttons, QUEST_BUTTON_ID) != null) return;

        if (gui instanceof LOTRGuiUnitTradeInteract) {
            int center = gui.width / 2;
            int y = gui.height / 5 * 3;
            GuiButton talk = findButton(buttons, 0);
            GuiButton hire = findButton(buttons, 1);
            if (talk == null || hire == null) return;
            talk.xPosition = center - 100;
            hire.xPosition = center - 30;
            buttons.add(new GuiButton(
                QUEST_BUTTON_ID, center + 40, y, 60, 20, "Quest"));
            return;
        }

        if (gui instanceof LOTRGuiTradeUnitTradeInteract) {
            int center = gui.width / 2;
            int y = gui.height / 5 * 3 + 50;
            GuiButton hire = findButton(buttons, -1);
            if (hire == null) return;
            hire.xPosition = center - 65;
            hire.width = 60;
            buttons.add(new GuiButton(
                QUEST_BUTTON_ID, center + 5, y, 60, 20, "Quest"));
        }
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.button == null || event.button.id != QUEST_BUTTON_ID) return;
        LOTREntityNPC npc = npc(event.gui);
        if (!isStandingTrialAvailableFor(Minecraft.getMinecraft().thePlayer, npc)) return;

        KOMEPacketHandler.network.sendToServer(new KOMEPacketRelationshipAction(
            npc.getEntityId(),
            KOMEPacketRelationshipAction.LIEGE,
            KOMEPacketRelationshipAction.SERVICE));
        // The authoritative action updates this same progression view; geographic status exposes Show Me.
        for(kome.common.data.KOMEVisualMarker marker:KOMEVisualMarkerClientState.markers())
            if(marker.role==kome.common.data.KOMEVisualMarker.Role.KNIGHT_LIEGE
                    &&marker.entityUuid.equals(npc.getUniqueID().toString())){
                Minecraft.getMinecraft().displayGuiScreen(kome.client.gui.KOMEGuiProgression.dutyView());break;
            }
        event.setCanceled(true);
    }

    private static GuiButton findButton(List<?> buttons, int id) {
        if (buttons == null) return null;
        for (Object element : buttons) {
            if (element instanceof GuiButton && ((GuiButton) element).id == id) {
                return (GuiButton) element;
            }
        }
        return null;
    }

    private static LOTREntityNPC npc(GuiScreen gui) {
        if (!(gui instanceof LOTRGuiUnitTradeInteract)
                && !(gui instanceof LOTRGuiTradeUnitTradeInteract)) {
            return null;
        }

        for (Class<?> type = gui.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("theEntity");
                field.setAccessible(true);
                Object value = field.get(gui);
                if (value instanceof LOTREntityNPC) return (LOTREntityNPC) value;
            } catch (ReflectiveOperationException ignored) {
                // Try the next class in the hierarchy.
            }
        }
        return null;
    }

    static boolean isStandingTrialAvailableFor(
            net.minecraft.entity.player.EntityPlayer localPlayer,
            LOTREntityNPC npc) {
        if (localPlayer == null || npc == null || !npc.isEntityAlive()
                || localPlayer.worldObj == null || npc.worldObj != localPlayer.worldObj) return false;
        net.minecraft.entity.Entity current = localPlayer.worldObj.getEntityByID(npc.getEntityId());
        if (current != npc || !current.getUniqueID().equals(npc.getUniqueID())) return false;
        return ELIGIBILITY.isStandingTrialAvailable(
            npc.getEntityId(), npc.getUniqueID(), hasNativeStandingTrialOffer(localPlayer, npc));
    }

    private static boolean hasNativeStandingTrialOffer(
            net.minecraft.entity.player.EntityPlayer localPlayer,
            LOTREntityNPC npc) {
        if (npc.questInfo == null) return false;
        LOTRMiniQuest offer = npc.questInfo.getOfferFor(localPlayer);
        return offer instanceof KOMELiegeOfferQuest
            && (((KOMELiegeOfferQuest) offer).isStandingTrialOffer()
                ||((KOMELiegeOfferQuest) offer).isReplacementOffer()||((KOMELiegeOfferQuest) offer).isCommissionOffer() || ((KOMELiegeOfferQuest)offer).isLordshipOffer());
    }

    private static void updateNativeIndicator(
            int entityId,
            UUID entityUuid,
            boolean offering,
            int offerColor) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null) return;
        net.minecraft.entity.Entity entity = minecraft.theWorld.getEntityByID(entityId);
        if (!(entity instanceof LOTREntityNPC)
                || !entity.getUniqueID().equals(entityUuid)) return;
        LOTREntityNPC npc = (LOTREntityNPC) entity;
        if (npc.questInfo == null) return;
        npc.questInfo.clientIsOffering = offering;
        npc.questInfo.clientOfferColor = offerColor;
    }
}
