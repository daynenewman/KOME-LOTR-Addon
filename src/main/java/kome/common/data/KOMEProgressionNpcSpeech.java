package kome.common.data;

import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRSpeech;
import net.minecraft.entity.player.EntityPlayerMP;

/** Presentation-only bridge to LOTR's native immersive NPC speech behavior. */
public final class KOMEProgressionNpcSpeech {
    private KOMEProgressionNpcSpeech() {}

    public static void say(EntityPlayerMP player, LOTREntityNPC npc, String text) {
        if (player != null && npc != null && text != null && text.trim().length() != 0) LOTRSpeech.sendSpeech(player, npc, text);
    }

    public static void welcomeSerf(EntityPlayerMP player, LOTREntityNPC master, boolean enteredSerfdom) {
        say(player, master, enteredSerfdom
            ? "Very well, " + player.getCommandSenderName() + ". You are in my service now. When I have work for you, I expect it done."
            : "Very well. You may serve me. See that you prove yourself useful.");
    }

    public static void assignDuty(EntityPlayerMP player, LOTREntityNPC master, KOMESerfKnightDutyType duty) {
        say(player, master, duty == KOMESerfKnightDutyType.PROVISIONING
            ? "I have need of provisions. Bring me what I have asked for."
            : duty == KOMESerfKnightDutyType.PROFESSION
                ? "I have need of materials for my trade. Bring me what I have asked for."
                : "I have work for you. See that it is done.");
    }

    public static void sameDay(EntityPlayerMP player, LOTREntityNPC master) {
        say(player, master, "You have done enough for today. Return tomorrow, and I may have more work for you.");
    }

    public static void viewDuty(EntityPlayerMP player, LOTREntityNPC master, KOMESerfKnightProgression state) {
        if (state != null && "provisioning".equals(state.getActiveAssignmentKind())) say(player, master, "I am still waiting on those provisions.");
        else if (state != null && "profession".equals(state.getActiveAssignmentKind())) say(player, master, "I am still waiting on those materials.");
        else if (state != null && state.getActiveAssignmentKind().length() != 0) say(player, master, "The work I gave you is not yet done.");
        else if (state != null && state.getDuty(KOMESerfKnightDutyType.PROVISIONING).isCompleted()) say(player, master, "You have done what I asked. There is nothing more for you today.");
        else say(player, master, "I have no task for you at present.");
    }

    public static void noMatchingProvisions(EntityPlayerMP player, LOTREntityNPC master) {say(player, master, "I see nothing here that I asked for.");}
    public static void partialProvisions(EntityPlayerMP player, LOTREntityNPC master) {say(player, master, "Good. I will take these. Bring me the rest.");}
    public static void completedProvisions(EntityPlayerMP player, LOTREntityNPC master) {say(player, master, "That is everything I asked for. You have done well.");}
    public static void noMatchingProfessionMaterials(EntityPlayerMP player, LOTREntityNPC master) {say(player, master, "I have no use for what you carry.");}
    public static void partialProfessionMaterials(EntityPlayerMP player, LOTREntityNPC master) {say(player, master, "Good. Bring me the rest.");}
    public static void completedProfessionMaterials(EntityPlayerMP player, LOTREntityNPC master) {say(player, master, "That will serve. You have done well.");}
}
