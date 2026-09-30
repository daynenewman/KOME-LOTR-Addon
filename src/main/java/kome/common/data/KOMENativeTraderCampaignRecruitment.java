package kome.common.data;

import kome.common.KOMEReflection;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRSquadrons;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHireableBase;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import lotr.common.entity.npc.LOTRUnitTradeEntry;
import lotr.common.entity.npc.LOTRUnitTradeable;
import lotr.common.fac.LOTRFaction;
import lotr.common.inventory.LOTRContainerUnitTrade;
import lotr.common.item.LOTRItemCoin;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.util.UUID;

/** Captain/native-trader adapter for the reusable campaign recruitment transaction. */
public final class KOMENativeTraderCampaignRecruitment {
    private KOMENativeTraderCampaignRecruitment() { }

    public static KOMECampaignRecruitmentService.Result recruit(EntityPlayerMP player,
            int traderEntityId, int tradeIndex, String squadron) {
        UUID playerId = player == null ? null : KOMEReflection.getEntityUUID(player);
        KOMECampaignRecruitmentService.Request request =
            new KOMECampaignRecruitmentService.Request(playerId,
                player == null ? "" : player.getCommandSenderName(),
                KOMECampaignRecruitmentService.SourceKind.NATIVE_TRADER,
                "native-trader:" + traderEntityId, "trade:" + tradeIndex);
        return KOMECampaignRecruitmentService.recruit(request,
            new NativeTraderSource(player, traderEntityId, tradeIndex, squadron));
    }

    private static final class NativeTraderSource
            implements KOMECampaignRecruitmentService.RecruitmentSource {
        private final EntityPlayerMP player;
        private final int traderEntityId;
        private final int tradeIndex;
        private final String squadron;

        NativeTraderSource(EntityPlayerMP player, int traderEntityId,
                int tradeIndex, String squadron) {
            this.player = player;
            this.traderEntityId = traderEntityId;
            this.tradeIndex = tradeIndex;
            this.squadron = LOTRSquadrons.checkAcceptableLength(
                squadron == null ? "" : squadron);
        }

        @Override
        public KOMECampaignRecruitmentService.PreparedRecruitment prepare(
                KOMECampaignRecruitmentService.Request request)
                throws KOMECampaignRecruitmentService.RecruitmentFailure {
            if (player == null || player.worldObj == null || player.worldObj.isRemote
                    || !KOMEReflection.getEntityUUID(player).equals(request.playerId)) {
                throw failure(KOMECampaignRecruitmentService.Code.INVALID_SOURCE,
                    "The server player or world is unavailable.");
            }
            if (!KOMEProgressionPermissions.has(player,
                    KOMEProgressionPermissions.HIRE_UNITS)) {
                throw failure(KOMECampaignRecruitmentService.Code.INVALID_SOURCE,
                    "You have not unlocked Hire Units yet.");
            }
            Container open = player.openContainer;
            if (!(open instanceof LOTRContainerUnitTrade)
                    || !open.canInteractWith(player)) {
                throw failure(KOMECampaignRecruitmentService.Code.INVALID_SOURCE,
                    "The native unit-trade interaction is no longer open or valid.");
            }
            LOTRContainerUnitTrade container = (LOTRContainerUnitTrade) open;
            LOTREntityNPC traderNpc = container.theLivingTrader;
            LOTRHireableBase hireable = container.theUnitTrader;
            Entity worldTrader = player.worldObj.getEntityByID(traderEntityId);
            if (traderNpc == null || worldTrader != traderNpc
                    || traderNpc.getEntityId() != traderEntityId
                    || !traderNpc.isEntityAlive()
                    || !(hireable instanceof LOTRUnitTradeable)
                    || hireable != traderNpc || !hireable.canTradeWith(player)) {
                throw failure(KOMECampaignRecruitmentService.Code.INVALID_SOURCE,
                    "The requested native unit trader is stale or invalid.");
            }
            LOTRUnitTradeEntry[] entries =
                ((LOTRUnitTradeable) hireable).getUnits().tradeEntries;
            if (entries == null || tradeIndex < 0 || tradeIndex >= entries.length
                    || entries[tradeIndex] == null) {
                throw failure(KOMECampaignRecruitmentService.Code.INVALID_UNIT,
                    "The requested native unit entry is invalid.");
            }
            LOTRUnitTradeEntry trade = entries[tradeIndex];
            if (!supportsDirectCampaignHire(trade)) {
                throw failure(KOMECampaignRecruitmentService.Code.INVALID_UNIT,
                    "This custom native trade requires a dedicated campaign recruitment adapter.");
            }
            if (trade.task != LOTRHiredNPCInfo.Task.WARRIOR) {
                throw failure(KOMECampaignRecruitmentService.Code.FARMHAND_UNSUPPORTED,
                    "Only native warrior entries support Campaign Hire.");
            }
            if (!trade.hasRequiredCostAndAlignment(player, hireable)) {
                int cost = trade.getCost(player, hireable);
                if (LOTRItemCoin.getInventoryValue(player, false) < cost) {
                    throw failure(KOMECampaignRecruitmentService.Code.INSUFFICIENT_COINS,
                        "Not enough native LOTR coins. Required: " + cost + ".");
                }
                throw failure(KOMECampaignRecruitmentService.Code.INVALID_UNIT,
                    "Native alignment or pledge requirements are not met.");
            }
            String ownerFaction = pledgedFaction(player);
            if (ownerFaction.length() == 0) {
                throw failure(KOMECampaignRecruitmentService.Code.INVALID_SOURCE,
                    "A current pledged faction is required to spend population.");
            }
            Candidate candidate = createCandidate(player, trade, hireable,
                ownerFaction);
            int nativeCost = trade.getCost(player, hireable);
            return new KOMECampaignRecruitmentService.PreparedRecruitment(
                candidate.data, candidate.record, candidate.payingFaction,
                nativeCost,
                new NativeHireEffect(player, hireable, traderNpc, trade,
                    candidate.npc, candidate.mount, squadron, nativeCost));
        }
    }

    /** True when reproducing LOTRUnitTradeEntry's audited v36.15 purchase sequence is safe. */
    public static boolean supportsDirectCampaignHire(LOTRUnitTradeEntry trade) {
        if (trade == null) return false;
        try {
            return trade.getClass().getMethod("hireUnit", EntityPlayer.class,
                LOTRHireableBase.class, String.class).getDeclaringClass()
                    == LOTRUnitTradeEntry.class;
        } catch (ReflectiveOperationException unavailable) {
            return false;
        }
    }

    private static Candidate createCandidate(EntityPlayerMP player, LOTRUnitTradeEntry trade,
            LOTRHireableBase hireable,
            String ownerFaction)
            throws KOMECampaignRecruitmentService.RecruitmentFailure {
        World world = player.worldObj;
        LOTREntityNPC npc;
        EntityLiving mount;
        try {
            npc = trade.getOrCreateHiredNPC(world);
            mount = trade.mountClass == null ? null : trade.createHiredMount(world);
        } catch (RuntimeException failure) {
            throw failure(KOMECampaignRecruitmentService.Code.NATIVE_HIRE_FAILED,
                "Native LOTR unit creation failed: " + cleanFailure(failure));
        }
        if (npc == null || world.loadedEntityList.contains(npc)
                || trade.mountClass != null && mount == null) {
            discard(npc, mount);
            throw failure(KOMECampaignRecruitmentService.Code.NATIVE_HIRE_FAILED,
                "Native LOTR did not create a fresh unit candidate.");
        }
        LOTRFaction npcFaction = npc.getFaction() == null
            ? hireable.getFaction() : npc.getFaction();
        String unitFaction = npcFaction == null ? ""
            : KOMEAlliance.normalizeFactionKey(npcFaction.codeName());
        KOMEWorldData data = KOMEWorldData.get(world);
        UUID owner = KOMEReflection.getEntityUUID(player);
        if (owner == null) {
            discard(npc, mount);
            throw failure(KOMECampaignRecruitmentService.Code.INVALID_SOURCE,
                "The authoritative hiring player is unavailable.");
        }
        return candidate(data, npc, mount, trade, owner, ownerFaction,
            unitFaction);
    }

    private static Candidate candidate(KOMEWorldData data, LOTREntityNPC npc,
            EntityLiving mount, LOTRUnitTradeEntry trade, UUID owner,
            String ownerFaction, String unitFaction)
            throws KOMECampaignRecruitmentService.RecruitmentFailure {
        boolean allied = unitFaction.length() > 0 && !ownerFaction.equals(unitFaction);
        boolean stewardship = allied && !data.hasFactionKing(unitFaction)
            && KOMEWarService.supportingKingDecision(data, unitFaction, ownerFaction, owner).allowed;
        if (allied && !stewardship) {
            discard(npc, mount);
            throw failure(KOMECampaignRecruitmentService.Code.INVALID_UNIT,
                "Allied combat hiring requires valid kingless wartime stewardship authority.");
        }
        boolean mounted = mount != null || trade.mountClass != null;
        int baseCost = Math.max(1, MathHelper.ceiling_float_int(
            KOMEReflection.getMaxHealthOrFallback(npc, KOMEEvents.defaultUnitCost)));
        String entityId = EntityList.getEntityString(npc);
        entityId = entityId == null ? "" : entityId.toLowerCase(java.util.Locale.ROOT);
        int populationCost = KOMEUnitPopulationCostService.calculate(
            entityId, baseCost, mounted, false);
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = KOMEReflection.getEntityUUID(npc);
        record.owner = owner;
        record.type = KOMEPopulationType.OFFENSIVE;
        record.cost = populationCost;
        record.baseCost = baseCost;
        record.populationSpent = populationCost;
        record.unitEntityId = entityId;
        record.level = Math.max(1, npc.hiredNPCInfo.xpLevel);
        record.mounted = mounted;
        record.unitName = unitName(npc);
        record.sourcePlayer = owner;
        String payingFaction = stewardship ? unitFaction : ownerFaction;
        if (stewardship) KOMEPopulationService.recordStewardshipCombatHirePayment(record, unitFaction);
        else KOMEPopulationService.recordCombatHirePayment(record, ownerFaction);
        record.unitFaction = unitFaction;
        record.alliancePair = allied ? KOMEAlliance.pairKey(ownerFaction, unitFaction) : "";
        record.benefitSource = stewardship ? "MILITARY_T3_STEWARDSHIP" : "";
        record.spawningFaction = ownerFaction;
        record.controller = owner;
        record.controllerAuthority = stewardship
            ? KOMEArmyCompany.AUTHORITY_STEWARDSHIP : KOMEArmyCompany.AUTHORITY_NATIVE;
        record.stewardshipWarIds = "";
        return new Candidate(data, record, npc, mount, payingFaction);
    }

    private static final class NativeHireEffect
            implements KOMECampaignRecruitmentService.RecruitmentEffect {
        final EntityPlayerMP player;
        final LOTRHireableBase hireable;
        final LOTREntityNPC trader;
        final LOTRUnitTradeEntry trade;
        final LOTREntityNPC npc;
        final EntityLiving mount;
        final String squadron;
        final int coinCost;
        boolean charged;
        boolean spawnedNpc;
        boolean spawnedMount;
        String deploymentTile = "";
        KOMEStrategicDeploymentResolver.Anchor deploymentAnchor;

        NativeHireEffect(EntityPlayerMP player, LOTRHireableBase hireable,
                LOTREntityNPC trader, LOTRUnitTradeEntry trade, LOTREntityNPC npc,
                EntityLiving mount, String squadron, int coinCost) {
            this.player = player; this.hireable = hireable; this.trader = trader;
            this.trade = trade; this.npc = npc; this.mount = mount;
            this.squadron = squadron; this.coinCost = coinCost;
        }

        @Override public void prepareDeployment(KOMEWorldData data,
                String payingFaction, String strategicTile) {
            KOMEStrategicDeploymentResolver.Validation deployment =
                KOMERecruitmentDeploymentService.resolve(data, player.worldObj,
                    payingFaction, strategicTile, npc, mount);
            if (!deployment.valid || deployment.anchor == null) {
                throw new IllegalStateException(deployment.reason);
            }
            deploymentTile = KOMEConquestTile.normalizeId(strategicTile);
            deploymentAnchor = deployment.anchor;
            positionCandidateAtDeployment(npc, mount, deploymentAnchor);
        }

        @Override public void performNativeHire() {
            requireDeployment();
            if (!trade.hasRequiredCostAndAlignment(player, hireable)
                    || LOTRItemCoin.getInventoryValue(player, false) < coinCost) {
                throw new IllegalStateException("Native LOTR purchase requirements changed before commit.");
            }
            hireable.onUnitTrade(player);
            LOTRItemCoin.takeCoins(coinCost, player);
            charged = true;
            trader.playTradeSound();
            npc.hiredNPCInfo.hireUnit(player, true, hireable.getFaction(),
                trade, squadron, mount);
            if (!npc.hiredNPCInfo.isActive
                    || npc.hiredNPCInfo.getTask() != LOTRHiredNPCInfo.Task.WARRIOR
                    || !KOMEReflection.getEntityUUID(player).equals(
                        npc.hiredNPCInfo.getHiringPlayerUUID())) {
                throw new IllegalStateException("Native LOTR did not establish the requested hired warrior.");
            }
            positionCandidateAtDeployment(npc, mount, deploymentAnchor);
        }

        @Override public void applyCampaignState(KOMEHiredUnitRecord record) {
            requireDeployment();
            if (!deploymentTile.equals(KOMEConquestTile.normalizeId(record.currentTile))) {
                throw new IllegalStateException("Campaign strategic tile differs from the resolved deployment tile.");
            }
            if (!KOMEHaltedUnitProtection.canApplyHornHalt(npc)) {
                throw new IllegalStateException("The new campaign unit cannot enter the campaign halt state.");
            }
            npc.hiredNPCInfo.halt();
            record.level = Math.max(1, npc.hiredNPCInfo.xpLevel);
            record.stationedEntityData = KOMEEntitySnapshots.snapshot(npc);
            if (record.stationedEntityData == null) {
                throw new IllegalStateException("The halted campaign unit could not be snapshotted.");
            }
        }

        @Override public void spawn() {
            requireDeployment();
            positionCandidateAtDeployment(npc, mount, deploymentAnchor);
            spawnedNpc = player.worldObj.spawnEntityInWorld(npc);
            if (!spawnedNpc) throw new IllegalStateException("Native LOTR unit spawn failed.");
            if (mount != null) {
                spawnedMount = player.worldObj.spawnEntityInWorld(mount);
                if (!spawnedMount) throw new IllegalStateException("Native LOTR mount spawn failed.");
            }
        }

        @Override public void rollback() {
            discard(npc, mount);
            if (charged) {
                LOTRItemCoin.giveCoins(coinCost, player);
                charged = false;
            }
        }

        private void requireDeployment() {
            if (deploymentAnchor == null || deploymentTile.length() == 0) {
                throw new IllegalStateException("Campaign recruitment deployment was not prepared.");
            }
        }
    }

    static void positionCandidateAtDeployment(Entity npc, Entity mount,
            KOMEStrategicDeploymentResolver.Anchor anchor) {
        if (npc == null || anchor == null) {
            throw new IllegalArgumentException("Campaign recruitment deployment candidate is incomplete.");
        }
        KOMEEvents.positionEntityTree(npc, anchor.x, anchor.y, anchor.z);
        if (mount != null && KOMEReflection.getRidingEntity(npc) != mount) {
            KOMEEvents.positionEntityTree(mount, anchor.x, anchor.y, anchor.z);
        }
    }

    private static final class Candidate {
        final KOMEWorldData data;
        final KOMEHiredUnitRecord record;
        final LOTREntityNPC npc;
        final EntityLiving mount;
        final String payingFaction;
        Candidate(KOMEWorldData data, KOMEHiredUnitRecord record,
                LOTREntityNPC npc, EntityLiving mount, String payingFaction) {
            this.data = data; this.record = record; this.npc = npc;
            this.mount = mount; this.payingFaction = payingFaction;
        }
    }

    private static String pledgedFaction(EntityPlayerMP player) {
        LOTRFaction faction = LOTRLevelData.getData(player).getPledgeFaction();
        return faction == null ? "" : KOMEAlliance.normalizeFactionKey(faction.codeName());
    }

    private static String unitName(LOTREntityNPC npc) {
        String name = npc.getCommandSenderName();
        return name == null || name.trim().length() == 0
            ? npc.getClass().getSimpleName() : name;
    }

    private static void discard(Entity npc, EntityLiving mount) {
        if (npc != null) KOMEReflection.setDead(npc);
        if (mount != null) KOMEReflection.setDead(mount);
    }

    private static KOMECampaignRecruitmentService.RecruitmentFailure failure(
            KOMECampaignRecruitmentService.Code code, String reason) {
        return new KOMECampaignRecruitmentService.RecruitmentFailure(code, reason);
    }

    private static String cleanFailure(Throwable failure) {
        String value = failure == null ? "unknown failure" : failure.getMessage();
        return value == null || value.trim().length() == 0
            ? failure.getClass().getSimpleName() : value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
