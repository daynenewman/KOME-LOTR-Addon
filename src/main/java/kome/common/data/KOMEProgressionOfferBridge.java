package kome.common.data;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketStandingTrialEligibility;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTREntityQuestInfo;
import lotr.common.fac.LOTRFaction;
import lotr.common.network.LOTRPacketHandler;
import lotr.common.network.LOTRPacketMiniquestOffer;
import lotr.common.quest.LOTRMiniQuest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;

/** Narrow adapter for KOME-authored offers hosted by LOTR's NPC quest presentation. */
public final class KOMEProgressionOfferBridge {
    interface OfferSender { void send(EntityPlayerMP player,LOTREntityNPC npc,NBTTagCompound tag); }
    static OfferSender offerSender=new OfferSender(){public void send(EntityPlayerMP player,LOTREntityNPC npc,NBTTagCompound tag){
        LOTRPacketHandler.networkWrapper.sendTo(new LOTRPacketMiniquestOffer(npc.getEntityId(),tag),player);
    }};
    public static final int OFFER_DENOMINATOR = 3;
    public static final long OPPORTUNITY_WINDOW_DAYS = 3L;
    private static Field npcField;
    private static boolean registered;

    private KOMEProgressionOfferBridge() { }

    /** Called on both physical sides before packet decoding can load this type. */
    public static synchronized void registerQuestType() {
        if (registered) return;
        try {
            Method method = LOTRMiniQuest.class.getDeclaredMethod("registerQuestType", String.class, Class.class);
            method.setAccessible(true);
            method.invoke(null, KOMESerfdomOfferQuest.TYPE, KOMESerfdomOfferQuest.class);
            method.invoke(null, KOMELiegeOfferQuest.TYPE, KOMELiegeOfferQuest.class);
            registered = true;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to register KOME Serfdom miniquest type", e);
        }
    }

    public static long opportunityWindow(long day) { return Math.max(0L, day) / OPPORTUNITY_WINDOW_DAYS; }
    public static boolean isSelected(UUID player, UUID npc, long window) {
        if (player == null || npc == null) return false;
        long value = player.getMostSignificantBits() ^ player.getLeastSignificantBits()
            ^ npc.getMostSignificantBits() ^ Long.rotateLeft(npc.getLeastSignificantBits(), 17) ^ window;
        value ^= value >>> 33; value *= 0xff51afd7ed558ccdL; value ^= value >>> 33;
        return Math.floorMod(value, OFFER_DENOMINATOR) == 0;
    }

    public static boolean isExternalOffer(LOTRMiniQuest offer) { return offer instanceof KOMESerfdomOfferQuest || offer instanceof KOMELiegeOfferQuest; }
    public static boolean hasExternalOffer(LOTREntityQuestInfo info, EntityPlayer player) {
        return info!=null&&player!=null&&isExternalOffer(info.getOfferFor(player));
    }
    public static boolean canOffer(LOTREntityQuestInfo info, EntityPlayer player) {
        LOTRMiniQuest offer = info == null || player == null ? null : info.getOfferFor(player);
        return offer instanceof KOMESerfdomOfferQuest
            && isOfferActive((KOMESerfdomOfferQuest) offer, player, npc(info))
            || isStandingTrialOffer(offer)
            && canRequestLiegeOfferFrom(player, npc(info));
    }

    /** Reconciles player-specific native offers before interaction so LOTR renders its own quest icon. */
    public static void refreshNearbyOffers(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.boundingBox == null) return;
        for (Object value : player.worldObj.getEntitiesWithinAABB(LOTREntityNPC.class, player.boundingBox.expand(32.0D, 32.0D, 32.0D))) {
            LOTREntityNPC npc = (LOTREntityNPC) value;
            if (player.getDistanceSqToEntity(npc) <= 1024.0D) {
                refreshStandingTrialOffer(player, npc, false);
                ensureSerfdomOffer(player, npc);
            }
        }
    }

    /** Clear only this player's obsolete shells, including NPCs outside the nearby scan. */
    public static void refreshPlayerOffers(EntityPlayerMP player) {
        if(player==null||player.worldObj==null)return;
        for(Object value:player.worldObj.loadedEntityList)if(value instanceof LOTREntityNPC) {
            LOTREntityNPC npc=(LOTREntityNPC)value;
            if(hasExternalOffer(npc.questInfo,player)) {
                ensureSerfdomOffer(player,npc);
                refreshStandingTrialOffer(player,npc,true);
            }
        }
        refreshNearbyOffers(player);
    }

    /**
     * Reconciles and publishes the exact NPC before the ordinary interaction GUI opens.
     * The Forge interaction handler opens LOTR's ordinary unit-trader GUI directly for
     * this one case, bypassing automatic offer opening without removing the native offer
     * or its indicator.
     */
    public static boolean prepareStandingTrialInteraction(EntityPlayerMP player, LOTREntityNPC npc) {
        return refreshStandingTrialOffer(player, npc, true);
    }

    private static boolean refreshStandingTrialOffer(
            EntityPlayerMP player,
            LOTREntityNPC npc,
            boolean publishNegative) {
        if (player == null || npc == null || npc.questInfo == null) return false;
        clearPassiveLiegeSponsorshipOffer(player, npc);
        boolean eligible = canRequestLiegeOfferFrom(player, npc);
        LOTRMiniQuest before = npc.questInfo.getOfferFor(player);
        boolean hadStandingOffer = isStandingTrialOffer(before);

        boolean passiveOffer = false;
        if (eligible) {
            passiveOffer = ensureStandingTrialOffer(player, npc);
            sendStandingTrialEligibility(player, npc, true);
        } else if (hadStandingOffer) {
            npc.questInfo.removeOpenOfferPlayer(player);
            npc.questInfo.clearPlayerSpecificOffer(player);
            npc.questInfo.sendData(player);
            sendStandingTrialEligibility(player, npc, false);
        } else if (publishNegative || KOMEProgressionLords.isStandingTrialLiegeCandidate(npc)) {
            sendStandingTrialEligibility(player, npc, false);
        }
        return eligible && passiveOffer;
    }

    private static boolean ensureStandingTrialOffer(EntityPlayerMP player, LOTREntityNPC npc) {
        LOTRMiniQuest current = npc.questInfo.getOfferFor(player);
        long day = KOMESerfKnightService.calendarDayNow();
        if (isStandingTrialOffer(current)) {
            if (!((KOMELiegeOfferQuest) current).isCommissionOffer() && !((KOMELiegeOfferQuest) current).isLordshipOffer() && !((KOMELiegeOfferQuest) current).expired(day)
                    &&((KOMELiegeOfferQuest) current).isReplacementOffer()==canReplaceLiegeFrom(player,npc)) return true;
            npc.questInfo.clearPlayerSpecificOffer(player);
        } else if (current != null) {
            return false;
        }
        KOMELordshipTrial lordship=KOMELordshipTrialService.eligible(player,npc)?KOMELordshipTrialService.prepare(player,npc):null;
        if(KOMELordshipTrialService.eligible(player,npc)&&lordship==null)return false;
        KOMEKnightCommission commission=KOMEKnightCommissionService.eligible(player,npc)?KOMEKnightCommissionService.prepare(player,npc):null;
        if(KOMEKnightCommissionService.eligible(player,npc)&&commission==null)return false;
        KOMELiegeOfferQuest offer = lordship!=null?KOMELiegeOfferQuest.createLordship(LOTRLevelData.getData(player),npc,day,lordship):commission!=null?KOMELiegeOfferQuest.createCommission(LOTRLevelData.getData(player),npc,day,commission):canReplaceLiegeFrom(player,npc)
            ? KOMELiegeOfferQuest.createReplacement(LOTRLevelData.getData(player),npc,day)
            : KOMELiegeOfferQuest.createStandingTrial(LOTRLevelData.getData(player), npc, day);
        if (offer == null) return false;
        npc.questInfo.setPlayerSpecificOffer(player, offer);
        npc.questInfo.sendData(player);
        return true;
    }

    private static void sendStandingTrialEligibility(
            EntityPlayerMP player,
            LOTREntityNPC npc,
            boolean eligible) {
        LOTRMiniQuest current = npc.questInfo.getOfferFor(player);
        boolean passiveOffer = eligible && isStandingTrialOffer(current);
        boolean offering = current != null && (isStandingTrialOffer(current)
            ? eligible : npc.questInfo.canOfferQuestsTo(player));
        int color = current == null ? 0xFFFFFF : current.getQuestColor();
        KOMEPacketHandler.network.sendTo(
            new KOMEPacketStandingTrialEligibility(
                npc.getEntityId(), npc.getUniqueID(), eligible, passiveOffer, offering, color),
            player);
    }

    private static boolean isStandingTrialOffer(LOTRMiniQuest offer) {
        return offer instanceof KOMELiegeOfferQuest
            && (((KOMELiegeOfferQuest) offer).isStandingTrialOffer()
                ||((KOMELiegeOfferQuest) offer).isReplacementOffer()||((KOMELiegeOfferQuest) offer).isCommissionOffer()||((KOMELiegeOfferQuest) offer).isLordshipOffer());
    }

    /** Creates only a deterministic player-specific opportunity. Called as an NPC becomes relevant. */
    public static boolean ensureSerfdomOffer(EntityPlayerMP player, LOTREntityNPC npc) {
        if (player == null || npc == null || npc.questInfo == null) return false;
        LOTRMiniQuest current = npc.questInfo.getOfferFor(player);
        if(!eligiblePlayer(player)||!eligibleNpc(player,npc)) {
            if(current instanceof KOMESerfdomOfferQuest) {
                npc.questInfo.removeOpenOfferPlayer(player);
                npc.questInfo.clearPlayerSpecificOffer(player);
                npc.questInfo.sendData(player);
                sendStandingTrialEligibility(player,npc,false);
            }
            return false;
        }
        long window = opportunityWindow(KOMESerfKnightService.calendarDayNow());
        if (current instanceof KOMESerfdomOfferQuest) {
            if (!((KOMESerfdomOfferQuest) current).isExpired(window)) {sendStandingTrialEligibility(player,npc,false);return true;}
            npc.questInfo.clearPlayerSpecificOffer(player);
        } else if (current != null) return false;
        if (KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID()).declinedSerfdomOfferToday(npc.getUniqueID().toString(), KOMESerfKnightService.calendarDayNow())) return false;
        if (!isSelected(player.getUniqueID(), npc.getUniqueID(), window)) return false;
        KOMESerfdomOfferQuest offer = KOMESerfdomOfferQuest.create(LOTRLevelData.getData(player), npc, window, storyFor(npc));
        // Never attach a partially initialized offer: LOTR serializes an NPC's offer
        // during interaction and world save, so construction must be valid up front.
        if (offer == null) return false;
        npc.questInfo.setPlayerSpecificOffer(player, offer);
        npc.questInfo.sendData(player);
        sendStandingTrialEligibility(player,npc,false);
        return true;
    }
    /** Opens the normal LOTR miniquest offer screen; the trial is assigned only after Accept. */
    public static boolean openStandingTrialOffer(EntityPlayerMP player,LOTREntityNPC npc){
        if(!canRequestLiegeOfferFrom(player,npc))return false;
        LOTRMiniQuest current=npc.questInfo.getOfferFor(player);long day=KOMESerfKnightService.calendarDayNow();KOMELiegeOfferQuest offer=null;
        if(isStandingTrialOffer(current)){offer=(KOMELiegeOfferQuest)current;if(offer.isCommissionOffer()||offer.isLordshipOffer()||offer.expired(day)||offer.isReplacementOffer()!=canReplaceLiegeFrom(player,npc)){npc.questInfo.clearPlayerSpecificOffer(player);offer=null;}}
        else if(current!=null){player.addChatMessage(new ChatComponentText("Finish or decline this NPC's current quest offer before requesting their Liege offer."));return true;}
        if(offer==null){KOMELordshipTrial lordship=KOMELordshipTrialService.eligible(player,npc)?KOMELordshipTrialService.prepare(player,npc):null;
        if(KOMELordshipTrialService.eligible(player,npc)&&lordship==null)return false;
        KOMEKnightCommission commission=KOMEKnightCommissionService.eligible(player,npc)?KOMEKnightCommissionService.prepare(player,npc):null;if(KOMEKnightCommissionService.eligible(player,npc)&&commission==null)return false;offer=lordship!=null?KOMELiegeOfferQuest.createLordship(LOTRLevelData.getData(player),npc,day,lordship):commission!=null?KOMELiegeOfferQuest.createCommission(LOTRLevelData.getData(player),npc,day,commission):canReplaceLiegeFrom(player,npc)?KOMELiegeOfferQuest.createReplacement(LOTRLevelData.getData(player),npc,day):KOMELiegeOfferQuest.createStandingTrial(LOTRLevelData.getData(player),npc,day);if(offer==null)return false;npc.questInfo.setPlayerSpecificOffer(player,offer);npc.questInfo.sendData(player);}
        return sendOffer(npc.questInfo,player,npc,offer);
    }

    public static boolean handleInteraction(LOTREntityQuestInfo info, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP) || !canOffer(info, player)) return false;
        LOTREntityNPC npc = npc(info);
        LOTRMiniQuest offer = info.getOfferFor(player);
        if (npc == null || offer == null) return false;
        return sendOffer(info, (EntityPlayerMP) player, npc, offer);
    }

    private static boolean sendOffer(LOTREntityQuestInfo info,EntityPlayerMP player,LOTREntityNPC npc,LOTRMiniQuest offer){NBTTagCompound tag=new NBTTagCompound();offer.writeToNBT(tag);offerSender.send(player,npc,tag);info.addOpenOfferPlayer(player);return true;}

    /** Handles response outside LOTR's tracked-quest lifecycle and reward path. */
    public static boolean handleResponse(LOTREntityQuestInfo info, EntityPlayer player, boolean accepted) {
        LOTREntityNPC npc = npc(info);
        LOTRMiniQuest candidate = info == null || player == null ? null : info.getOfferFor(player);
        if (!isExternalOffer(candidate) || npc == null) return false;
        if(candidate instanceof KOMELiegeOfferQuest){
            KOMELiegeOfferQuest liegeOffer=(KOMELiegeOfferQuest)candidate;
            info.removeOpenOfferPlayer(player);
            if(liegeOffer.isLordshipOffer()){
                if(player instanceof EntityPlayerMP&&accepted&&KOMELordshipTrialService.eligible(player,npc)){
                    KOMELordshipTrial t=KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID()).getLordship().assignment();
                    if(t!=null&&t.objective.token.equals(liegeOffer.commissionToken()))KOMELordshipTrialService.acceptOrReport((EntityPlayerMP)player,npc);
                }
                info.clearPlayerSpecificOffer(player);if(player instanceof EntityPlayerMP)info.sendData((EntityPlayerMP)player);return true;
            }
            if(liegeOffer.isCommissionOffer()){
                if(player instanceof EntityPlayerMP&&accepted&&KOMEKnightCommissionService.eligible(player,npc)){
                    KOMEKnightCommission a=KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID()).getKnightService().assignment();
                    if(a!=null&&a.token.equals(liegeOffer.commissionToken()))KOMEKnightCommissionService.acceptOrReport((EntityPlayerMP)player,npc);
                }
                info.clearPlayerSpecificOffer(player);if(player instanceof EntityPlayerMP)info.sendData((EntityPlayerMP)player);return true;
            }
            if(liegeOffer.isStandingTrialOffer()||liegeOffer.isReplacementOffer())return handleStandingTrialResponse(info,player,npc,liegeOffer,accepted);
            // Compatibility cleanup for old saves. Legacy sponsorship can no longer
            // select a Liege independently of accepting a Trial of Standing.
            info.clearPlayerSpecificOffer(player);
            if(player instanceof EntityPlayerMP)info.sendData((EntityPlayerMP)player);
            return true;
        }
        KOMESerfdomOfferQuest offer = (KOMESerfdomOfferQuest) candidate;
        info.removeOpenOfferPlayer(player);
        if (!accepted) { KOMEWorldData data=KOMEWorldData.get(player.worldObj);data.getProgression(player.getUniqueID()).declineSerfdomOffer(npc.getUniqueID().toString(),KOMESerfKnightService.calendarDayNow());data.markDirty();info.clearPlayerSpecificOffer(player); info.sendData((EntityPlayerMP) player); return true; }
        if (!(player instanceof EntityPlayerMP) || !isOfferActive(offer, player, npc) || player.getDistanceSqToEntity(npc) > 64.0D) return true;
        EntityPlayerMP mp = (EntityPlayerMP) player;
        KOMEWorldData data = KOMEWorldData.get(mp.worldObj);
        KOMEPlayerProgression progression = data.getProgression(mp.getUniqueID());
        boolean entered = progression.getCanonicalRank() == KOMEProgressionRank.WANDERER;
        KOMESerfdomMasterService.Result result = KOMESerfdomMasterService.serve(mp, data, npc);
        if (!result.success) return true;
        info.clearPlayerSpecificOffer(mp);
        data.markDirty();
        KOMEProgressionAutoCompleter.syncPlayer(mp, progression);
        KOMEProgressionNpcSpeech.welcomeSerf(mp, npc, entered);
        return true;
    }

    private static boolean handleStandingTrialResponse(LOTREntityQuestInfo info,EntityPlayer player,
            LOTREntityNPC npc,KOMELiegeOfferQuest offer,boolean accepted){
        if(!(player instanceof EntityPlayerMP))return true;
        EntityPlayerMP mp=(EntityPlayerMP)player;
        long day=KOMESerfKnightService.calendarDayNow();
        if(!accepted){
            if(offer.expired(day)||!canRequestLiegeOfferFrom(mp,npc)){
                info.clearPlayerSpecificOffer(mp);
                info.sendData(mp);
                sendStandingTrialEligibility(mp,npc,false);
            }else{
                info.sendData(mp);
                sendStandingTrialEligibility(mp,npc,true);
            }
            return true;
        }
        boolean eligible=offer.isReplacementOffer()?canReplaceLiegeFrom(mp,npc):canRequestStandingTrialFrom(mp,npc);
        if(offer.expired(day)||!eligible||mp.getDistanceSqToEntity(npc)>64.0D){
            info.clearPlayerSpecificOffer(mp);
            info.sendData(mp);
            sendStandingTrialEligibility(mp,npc,false);
            mp.addChatMessage(new ChatComponentText("That Liege offer is no longer available."));
            return true;
        }
        KOMEWorldData data=KOMEWorldData.get(mp.worldObj);
        KOMEPlayerProgression progression=data.getProgression(mp.getUniqueID());
        KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        if(offer.isReplacementOffer()) {
            KOMESerfKnightRelationshipService.Result selected=KOMESerfKnightRelationshipService.establishLiege(mp,data,npc);
            if(!selected.success){mp.addChatMessage(new ChatComponentText(selected.reason));return true;}
            info.clearPlayerSpecificOffer(mp);info.sendData(mp);
            KOMEProgressionAutoCompleter.syncPlayer(mp,progression);
            sendStandingTrialEligibility(mp,npc,false);
            KOMEProgressionNpcSpeech.say(mp,npc,"I will receive your service and offerings. You may look to me as your Liege.");
            return true;
        }
        KOMESerfKnightService.Result result=KOMESerfKnightService.acceptStandingTrial(
            state,data,npc,mp.worldObj.rand,day,mp.getUniqueID());
        if(!result.success){
            mp.addChatMessage(new ChatComponentText(result.reason));
            boolean stillEligible=canRequestStandingTrialFrom(mp,npc);
            if(!stillEligible){
                info.clearPlayerSpecificOffer(mp);
                info.sendData(mp);
            }
            sendStandingTrialEligibility(mp,npc,stillEligible);
            return true;
        }
        info.clearPlayerSpecificOffer(mp);
        info.sendData(mp);
        KOMEProgressionNpcRoles.syncPlayer(data,mp.getUniqueID());
        data.markDirty();
        KOMEProgressionAutoCompleter.syncPlayer(mp,progression);
        sendStandingTrialEligibility(mp,npc,false);
        KOMEProgressionNpcInteractionService.activateTrial(mp,progression,npc,state.getTrialId());
        KOMEProgressionNpcSpeech.say(mp,npc,KOMESerfKnightService.trialSpeech(state.getTrialAssignment()));
        return true;
    }

    public static boolean canRequestStandingTrialFrom(EntityPlayer player,LOTREntityNPC npc){if(player==null||npc==null||player.worldObj==null||npc.worldObj!=player.worldObj||!npc.isEntityAlive()||npc.questInfo==null)return false;KOMEWorldData data=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression progression=data.getProgression(player.getUniqueID());KOMESerfKnightProgression state=progression.getSerfKnightProgression();KOMEProgressionNpcRef ref=KOMEProgressionNpcRankService.referenceOf(npc);LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();long day=KOMESerfKnightService.calendarDayNow();return progression.getCanonicalRank()==KOMEProgressionRank.SERF&&KOMESerfKnightService.canRequestTrialFromProspectiveLiege(state,ref,day,player.getUniqueID())&&pledge!=null&&pledge.isPlayableAlignmentFaction()&&pledge==npc.getFaction()&&KOMEStandingTrialEligibility.meetsAlignment(state,LOTRLevelData.getData(player).getAlignment(pledge),pledge.codeName())&&KOMEProgressionFactionResolver.matches(state.getSerfdomMaster().factionKey,npc.getFaction())&&!npc.isChild()&&KOMEProgressionNpcRankService.isValidFactionNpc(npc)&&KOMEProgressionLords.isStandingTrialLiegeCandidate(npc)&&((lotr.common.entity.npc.LOTRUnitTradeable)npc).canTradeWith(player)&&KOMEProgressionNpcRankService.effectiveRank(data,npc)==KOMEProgressionNpcRank.LORD&&npc.hiredNPCInfo!=null&&!npc.hiredNPCInfo.isActive;}

    public static boolean canRequestLiegeOfferFrom(EntityPlayer player,LOTREntityNPC npc) {
        return canRequestStandingTrialFrom(player, npc)||canReplaceLiegeFrom(player,npc)||KOMEKnightCommissionService.eligible(player,npc)||KOMELordshipTrialService.eligible(player,npc);
    }

    public static boolean canReplaceLiegeFrom(EntityPlayer player,LOTREntityNPC npc) {
        return eligibleLiegeNpc(player,npc)&&KOMESerfKnightRelationshipService.canEstablishLiege(
            KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID()),KOMESerfKnightService.calendarDayNow());
    }

    private static boolean eligibleLiegeNpc(EntityPlayer player,LOTREntityNPC npc) {
        if(player==null||npc==null||player.worldObj==null||player.worldObj.isRemote
                ||npc.worldObj!=player.worldObj||!npc.isEntityAlive()||npc.isChild()||npc.questInfo==null)return false;
        LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);
        return pledge!=null&&pledge.isPlayableAlignmentFaction()&&pledge==npc.getFaction()
            &&LOTRLevelData.getData(player).getAlignment(pledge)>=KOMEStandingTrialEligibility.requiredAlignment(pledge.codeName())
            &&KOMEProgressionNpcRankService.isValidFactionNpc(npc)
            &&KOMEProgressionLords.isStandingTrialLiegeCandidate(npc)
            &&((lotr.common.entity.npc.LOTRUnitTradeable)npc).canTradeWith(player)
            &&KOMEProgressionNpcRankService.effectiveRank(data,npc)==KOMEProgressionNpcRank.LORD
            &&npc.hiredNPCInfo!=null&&!npc.hiredNPCInfo.isActive;
    }

    /** Removes a superseded legacy sponsorship shell without altering Serfdom offers. */
    public static void clearPassiveLiegeSponsorshipOffer(EntityPlayerMP player,LOTREntityNPC npc){if(player==null||npc==null||npc.questInfo==null)return;LOTRMiniQuest current=npc.questInfo.getOfferFor(player);if(current instanceof KOMELiegeOfferQuest&&!isStandingTrialOffer(current)){npc.questInfo.clearPlayerSpecificOffer(player);npc.questInfo.sendData(player);}}

    private static boolean isOfferActive(KOMESerfdomOfferQuest offer, EntityPlayer player, LOTREntityNPC npc) {
        return offer != null && player != null && npc != null && !offer.isExpired(opportunityWindow(KOMESerfKnightService.calendarDayNow()))
            && eligiblePlayer(player) && eligibleNpc(player, npc);
    }
    private static boolean eligiblePlayer(EntityPlayer player) {
        if (player == null) return false;
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        if (pledge == null || !pledge.isPlayableAlignmentFaction()) return false;
        KOMEWorldData data = KOMEWorldData.get(player.worldObj);
        KOMEPlayerProgression progression = data.getProgression(player.getUniqueID());
        KOMEProgressionRank rank = progression.getCanonicalRank();
        if (rank == KOMEProgressionRank.WANDERER) return !progression.getSerfKnightProgression().getSerfdomMaster().isSet();
        return rank == KOMEProgressionRank.SERF && !progression.getSerfKnightProgression().getSerfdomMaster().isSet()
            && KOMESerfKnightService.canSelectReplacement(progression.getSerfKnightProgression(), KOMESerfKnightService.calendarDayNow());
    }
    private static boolean eligibleNpc(EntityPlayer player, LOTREntityNPC npc) {
        if (npc == null || !npc.isEntityAlive() || npc.isChild() || !KOMEProgressionNpcRankService.isValidFactionNpc(npc)) return false;
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        return pledge != null && pledge == npc.getFaction()
            && KOMEProgressionNpcRankService.effectiveRank(KOMEWorldData.get(npc.worldObj), npc) == KOMEProgressionNpcRank.UNRANKED;
    }
    private static String storyFor(LOTREntityNPC npc) {
        String type = npc.getClass().getSimpleName().toLowerCase();
        if (type.contains("smith")) return "There is more work at my forge than I can manage alone. If you mean to earn your place here, I could use another pair of hands.";
        if (type.contains("farmer") || type.contains("farm")) return "There is always more work than daylight on this holding. I could use someone willing to earn their keep.";
        return "I have work enough for another pair of hands. If you are willing to serve faithfully, I may have a place for you.";
    }
    private static LOTREntityNPC npc(LOTREntityQuestInfo info) {
        if (info == null) return null;
        try {
            if (npcField == null) { npcField = LOTREntityQuestInfo.class.getDeclaredField("theNPC"); npcField.setAccessible(true); }
            return (LOTREntityNPC) npcField.get(info);
        } catch (Exception e) { throw new IllegalStateException("Unable to access LOTR quest NPC", e); }
    }
}
