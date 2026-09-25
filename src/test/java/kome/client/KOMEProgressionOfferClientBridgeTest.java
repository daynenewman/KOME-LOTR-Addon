package kome.client;
import kome.common.data.KOMESerfdomOfferQuest;import lotr.common.quest.LOTRMiniQuest;import net.minecraft.item.ItemStack;import org.junit.Test;import static org.junit.Assert.*;
public class KOMEProgressionOfferClientBridgeTest {
 private static final class Gui {private LOTRMiniQuest theMiniQuest;private boolean sentClosePacket;Gui(LOTRMiniQuest q,boolean sent){theMiniQuest=q;sentClosePacket=sent;}}
 private static final class NativeQuest extends LOTRMiniQuest {NativeQuest(){super(null);}public String getQuestObjective(){return "";}public String getObjectiveInSpeech(){return "";}public String getProgressedObjectiveInSpeech(){return "";}public String getQuestProgress(){return "";}public String getQuestProgressShorthand(){return "";}public float getCompletionFactor(){return 0;}public ItemStack getQuestIcon(){return null;}public float getAlignmentBonus(){return 0;}public int getCoinBonus(){return 0;}}
 @Test public void passiveCloseIsSuppressedOnlyForKomeOffer(){Gui gui=new Gui(new KOMESerfdomOfferQuest(null),false);assertTrue(KOMEProgressionOfferClientBridge.preservePassiveClose(gui));assertTrue(gui.sentClosePacket);}
 @Test public void explicitAcceptOrDeclinePacketIsNeverSuppressed(){Gui gui=new Gui(new KOMESerfdomOfferQuest(null),true);assertFalse(KOMEProgressionOfferClientBridge.preservePassiveClose(gui));}
 @Test public void nativeLotrOfferCloseBehaviorIsUnchanged(){Gui gui=new Gui(new NativeQuest(),false);assertFalse(KOMEProgressionOfferClientBridge.preservePassiveClose(gui));assertFalse(gui.sentClosePacket);}
}
