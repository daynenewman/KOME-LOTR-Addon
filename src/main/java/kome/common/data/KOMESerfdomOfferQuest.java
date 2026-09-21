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

    public KOMESerfdomOfferQuest(LOTRPlayerData playerData, LOTREntityNPC npc, long window, String story) {
        super(playerData);
        opportunityWindow = window;
        this.story = story == null ? "" : story;
        setNPCInfo(npc);
        quoteStart = this.story;
    }

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
