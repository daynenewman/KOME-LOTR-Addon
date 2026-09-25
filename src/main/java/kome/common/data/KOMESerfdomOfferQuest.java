package kome.common.data;

import lotr.common.LOTRPlayerData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.quest.LOTRMiniQuest;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** A presentation-only native miniquest. It is never added to the player's quest log. */
public final class KOMESerfdomOfferQuest extends LOTRMiniQuest {
    public static final String TYPE = "KOMESerfdomOffer";
    private long opportunityWindow;
    private String story = "";

    public KOMESerfdomOfferQuest(LOTRPlayerData playerData) { super(playerData); }

    private KOMESerfdomOfferQuest(LOTRPlayerData playerData, LOTREntityNPC npc, long window, String story, LOTRMiniQuest nativeTemplate) {
        super(playerData);
        opportunityWindow = window;
        this.story = story == null ? "" : story;
        copyNativePresentation(nativeTemplate);
        setNPCInfo(npc);
        quoteStart = this.story;
    }

    /**
     * Native miniquests are created through a factory which initializes the inherited
     * speech-bank and quote fields before they are serialized. KOME offers are not in
     * that factory, so borrow the NPC's valid native presentation template instead.
     */
    public static KOMESerfdomOfferQuest create(LOTRPlayerData playerData, LOTREntityNPC npc, long window, String story) {
        if (npc == null || story == null || story.trim().length() == 0) return null;
        LOTRMiniQuest template = npc.createMiniQuest();
        if (!hasNativePresentation(template)) return null;
        return new KOMESerfdomOfferQuest(playerData, npc, window, story, template);
    }

    private void copyNativePresentation(LOTRMiniQuest template) {
        questGroup = template.questGroup;
        speechBankStart = template.speechBankStart;
        speechBankProgress = template.speechBankProgress;
        speechBankComplete = template.speechBankComplete;
        speechBankTooMany = template.speechBankTooMany;
        quoteComplete = template.quoteComplete;
        quotesStages.addAll(template.quotesStages);
    }

    private static boolean hasNativePresentation(LOTRMiniQuest quest) {
        return quest != null && quest.questGroup != null && nonEmpty(quest.speechBankStart)
            && nonEmpty(quest.speechBankProgress) && nonEmpty(quest.speechBankComplete)
            && nonEmpty(quest.speechBankTooMany) && nonEmpty(quest.quoteStart)
            && nonEmpty(quest.quoteComplete);
    }

    private static boolean nonEmpty(String value) { return value != null && value.length() != 0; }

    public long getOpportunityWindow() { return opportunityWindow; }
    public boolean isExpired(long currentWindow) { return currentWindow != opportunityWindow; }
    public String getStory() { return story; }

    @Override public String getQuestObjective() { return "Enter the service of " + (entityNameFull == null || entityNameFull.length() == 0 ? "this worker" : entityNameFull); }
    @Override public String getObjectiveInSpeech() { return "enter my service"; }
    @Override public String getProgressedObjectiveInSpeech() { return getObjectiveInSpeech(); }
    @Override public String getQuestProgress() { return "Offer of service"; }
    @Override public String getQuestProgressShorthand() { return "Service"; }
    @Override public float getCompletionFactor() { return 0.0F; }
    @Override public ItemStack getQuestIcon() { return new ItemStack(Items.wooden_hoe); }
    @Override public float getAlignmentBonus() { return 0.0F; }
    @Override public int getCoinBonus() { return 0; }
    @Override public boolean canPlayerAccept(net.minecraft.entity.player.EntityPlayer player) { return true; }

    @Override public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setLong("KOMEOpportunityWindow", opportunityWindow);
        tag.setString("KOMEStory", story);
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        opportunityWindow = tag.getLong("KOMEOpportunityWindow");
        story = tag.getString("KOMEStory");
        quoteStart = story;
    }
}
