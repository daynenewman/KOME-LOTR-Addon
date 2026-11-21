package kome.common.data;

import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRSpeech;
import net.minecraft.entity.player.EntityPlayerMP;

/** Presentation-only bridge to LOTR's native immersive NPC speech behavior. */
public final class KOMEProgressionNpcSpeech {
    interface Sender { void send(EntityPlayerMP player,LOTREntityNPC npc,String text); }
    // Package-local transport seam; all gameplay decisions remain in server services.
    static Sender sender=new Sender(){public void send(EntityPlayerMP player,LOTREntityNPC npc,String text){LOTRSpeech.sendSpeech(player, npc, text);}};
    private KOMEProgressionNpcSpeech() {}

    public static void say(EntityPlayerMP player, LOTREntityNPC npc, String text) {
        if (player != null && npc != null && text != null && text.trim().length() != 0) sender.send(player, npc, text);
    }

    public static void commission(EntityPlayerMP player, LOTREntityNPC npc, KOMEKnightCommission assignment, String event) {
        say(player,npc,KOMEKnightCommissionPresentation.speech(assignment,event));
    }

    public static void welcomeSerf(EntityPlayerMP player, LOTREntityNPC master, boolean enteredSerfdom) {
        say(player, master, enteredSerfdom
            ? "Very well, " + player.getCommandSenderName() + ". You are in my service. See that my work is done."
            : "You may serve me. Prove your worth.");
    }

    public static void assignDuty(EntityPlayerMP player, LOTREntityNPC master, KOMESerfKnightDutyType duty) {
        if(KOMESerfServiceObjectives.objective(duty)){say(player,master,KOMESerfKnightService.trialSpeech(KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID()).getSerfKnightProgression().getTrialAssignment()));return;}
        String culture=master.getFaction().codeName();
        String household="rohan".equals(culture)?"Our hearths must be fed while the riders are away. ":
            "gondor".equals(culture)?"Our people need supplies in these troubled days. ":"Our household needs your help. ";
        say(player, master, duty == KOMESerfKnightDutyType.PROVISIONING
            ? household+"Bring the provisions I have listed."
            : duty == KOMESerfKnightDutyType.PROFESSION
                ? KOMEProgressionDialogue.materials(master,false)
                : "I have work for you. See to it.");
    }

    public static void sameDay(EntityPlayerMP player, LOTREntityNPC master) {
        say(player, master, "Finish your present duty before asking for another.");
    }
    public static void assignCourier(EntityPlayerMP player, LOTREntityNPC master) {
        KOMESerfKnightProgression state=KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID()).getSerfKnightProgression();
        KOMESerfCourierAssignment a=KOMESerfCourierAssignment.readFromNBT(state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
        String recipient=a!=null&&a.recipient.isSet()?a.recipient.displayName:a==null?"one of our people":"the recipient marked on your map";
        say(player,master,"Carry this message to "+recipient+". Keep it safe, then return to me.");
    }
    public static void replaceCourierMessage(EntityPlayerMP player, LOTREntityNPC master) { say(player,master,"Lost it? Take another, and guard it well."); }
    public static void receiveCourier(EntityPlayerMP player, LOTREntityNPC recipient, String masterName) { say(player,recipient,"I have it. Tell "+masterName+" the message arrived."); }
    public static void completeCourier(EntityPlayerMP player, LOTREntityNPC master) { say(player,master,"The message is delivered. Well done."); }

    public static void viewDuty(EntityPlayerMP player, LOTREntityNPC master, KOMESerfKnightProgression state) {
        if (state != null && "provisioning".equals(state.getActiveAssignmentKind())) say(player, master, "I still need those provisions.");
        else if (state != null && "profession".equals(state.getActiveAssignmentKind())) say(player, master, "I still need those materials.");
        else if (state != null && state.getActiveAssignmentKind().length() != 0) say(player, master, "Your duty remains unfinished.");
        else if (state != null && state.getDuty(KOMESerfKnightDutyType.PROVISIONING).isCompleted()) say(player, master, "Your service is noted. Return for another duty.");
        else say(player, master, "I have no work for you now.");
    }

    public static void noMatchingProvisions(EntityPlayerMP player, LOTREntityNPC master) {say(player, master, "None of that was requested.");}
    public static void partialProvisions(EntityPlayerMP player, LOTREntityNPC master) {say(player, master, "Good. Bring the rest.");}
    public static void completedProvisions(EntityPlayerMP player, LOTREntityNPC master) {say(player, master, "That is everything. Well done.");}
    public static void noMatchingProfessionMaterials(EntityPlayerMP player, LOTREntityNPC master) {say(player, master, "I have no use for those materials.");}
    public static void partialProfessionMaterials(EntityPlayerMP player, LOTREntityNPC master) {say(player, master, "Good. Bring the rest.");}
    public static void completedProfessionMaterials(EntityPlayerMP player, LOTREntityNPC master) {say(player, master, KOMEProgressionDialogue.materials(master,true));}
}
