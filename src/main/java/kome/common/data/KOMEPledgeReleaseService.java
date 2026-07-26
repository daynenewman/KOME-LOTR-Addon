package kome.common.data;

import cpw.mods.fml.common.FMLCommonHandler;
import kome.common.KOMEReflection;
import kome.common.command.KOMECommandTroops;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Transactional, persisted cleanup for a base-LOTR pledge transition. */
public final class KOMEPledgeReleaseService {
    public static final int MAX_AUDIT = 250;

    private KOMEPledgeReleaseService() {
    }

    /** Observes the actual LOTR pledge. KOME progression data is never used to hide an unpledge. */
    public static Result observePledge(KOMEWorldData data, EntityPlayerMP player, String currentFaction, long nowMillis) {
        if (data == null || player == null) return Result.noop("No player context.");
        UUID playerId = KOMEReflection.getEntityUUID(player);
        String current = KOMEAlliance.normalizeFactionKey(currentFaction);
        if (!data.lastKnownPlayerFactions.containsKey(playerId)) {
            String migrationBaseline = KOMEAlliance.normalizeFactionKey(data.getPlayerFactionKey(playerId));
            data.lastKnownPlayerFactions.put(playerId, current);
            if (migrationBaseline.length() > 0 && !migrationBaseline.equals(current)) {
                return release(data, playerId, player.getCommandSenderName(), migrationBaseline, current,
                    nowMillis, "Schema-5 initial pledge reconciliation");
            }
            data.markDirty();
            return Result.noop("Pledge baseline recorded.");
        }
        String former = KOMEAlliance.normalizeFactionKey(data.lastKnownPlayerFactions.get(playerId));
        if (former.equals(current)) return Result.noop("Pledge unchanged.");
        data.lastKnownPlayerFactions.put(playerId, current);
        if (former.length() == 0) {
            data.markDirty();
            return Result.noop("New pledge recorded.");
        }
        return release(data, playerId, player.getCommandSenderName(), former, current, nowMillis, "LOTR pledge transition");
    }

    public static Preview preview(KOMEWorldData data, UUID player, String formerFaction) {
        Preview result = new Preview();
        if (data == null || player == null) return result;
        result.player = player;
        result.formerFaction = KOMEAlliance.normalizeFactionKey(formerFaction);
        Set<String> companies = new HashSet<String>();
        Set<String> movements = new HashSet<String>();
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (!isOwnedFormerFactionUnit(record, player, result.formerFaction)) continue;
            result.units++;
            if (record.farmhand) result.farmhands++;
            else if (record.type == KOMEPopulationType.DEFENSIVE) result.defensivePopulation += Math.max(0, record.cost);
            else result.offensivePopulation += Math.max(0, record.cost);
            incrementSource(result, record.sourceType, Math.max(0, record.cost));
            if (record.companyId != null && record.companyId.length() > 0) companies.add(record.companyId);
            if (record.movementOrderId != null && record.movementOrderId.length() > 0) movements.add(record.movementOrderId);
        }
        for (KOMEArmyCompany company : data.armyCompanies.values()) {
            if (company != null && player.equals(company.owner)
                    && result.formerFaction.equals(KOMEAlliance.normalizeFactionKey(KOMEWartimeStewardshipService.nativeFaction(company)))) {
                companies.add(company.id);
                if (company.movementOrderId != null && company.movementOrderId.length() > 0) movements.add(company.movementOrderId);
                if (company.transferRecipient != null) result.transferOffers++;
            }
        }
        result.companies = companies.size();
        result.movements = movements.size();
        for (KOMEPledgeReleaseTombstone tombstone : data.pledgeReleaseTombstones.values()) {
            if (tombstone != null && player.equals(tombstone.formerOwner) && !tombstone.entityRemoved) result.pendingUnloaded++;
        }
        return result;
    }

    public static Result release(KOMEWorldData data, UUID player, String playerName, String formerFaction,
            String newFaction, long nowMillis, String reason) {
        if (data == null || player == null) return Result.failure("Missing release context.");
        String former = KOMEAlliance.normalizeFactionKey(formerFaction);
        String current = KOMEAlliance.normalizeFactionKey(newFaction);
        if (former.length() == 0 || former.equals(current)) return Result.noop("No former-faction cleanup is required.");

        Result result = new Result();
        result.changed = true;
        result.formerFaction = former;
        result.newFaction = current;
        result.wasKing = data.isFactionKing(former, player);
        if (result.wasKing) data.reconcilePlayerKingship(current, player, playerName, false);

        // Revoke both delegations issued by the departing king and temporary control held by the departing player.
        for (KOMEArmyCompany company : data.armyCompanies.values()) {
            if (company == null) continue;
            boolean issued = player.equals(company.delegatedBy);
            boolean controlled = player.equals(company.temporaryController);
            if (!issued && !controlled) continue;
            if (KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)) {
                company.temporaryController = null;
                company.temporaryControllerName = "";
                company.delegationRevocationReason = "Controller pledge ended";
                company.withdrawalState = company.stewardshipCreated
                    ? KOMEArmyCompany.CLEANUP_WITHDRAWAL : KOMEArmyCompany.CLEANUP_NONE;
                KOMEWartimeStewardshipService.revalidateCompany(data, company, nowMillis, "Controller pledge ended");
            } else {
                company.clearTemporaryController("Delegating king or controller left " + KOMEAlliance.displayFactionName(former));
            }
            result.temporaryAuthoritiesRevoked++;
        }

        // Stop every affected order before touching entity or population state.
        Set<String> cancelledOrders = new HashSet<String>();
        for (KOMEArmyMovementOrder order : new ArrayList<KOMEArmyMovementOrder>(data.armyMovements.values())) {
            KOMEArmyCompany company = order == null ? null : data.armyCompanies.get(order.companyId);
            boolean affected = order != null && player.equals(order.owner)
                && former.equals(KOMEAlliance.normalizeFactionKey(order.ownerFaction));
            affected = affected || company != null && player.equals(company.owner)
                && former.equals(KOMEAlliance.normalizeFactionKey(KOMEWartimeStewardshipService.nativeFaction(company)));
            if (!affected) continue;
            order.status = KOMEArmyMovementOrder.CANCELLED;
            order.accessLossReason = "Owner pledge ended before the next movement step";
            order.nextStepDepartureMillis = 0L;
            order.nextStepAvailableMillis = 0L;
            data.updateMovementHistory(order, KOMEMovementHistoryRecord.CANCELLED);
            cancelledOrders.add(order.id);
            data.armyMovements.remove(order.id);
            result.movementsCancelled++;
        }

        for (KOMEHiredUnitRecord record : new ArrayList<KOMEHiredUnitRecord>(data.hiredUnits.values())) {
            if (!isOwnedFormerFactionUnit(record, player, former)) continue;
            KOMEPledgeReleaseTombstone tombstone = tombstone(data, record, former, player, nowMillis, reason);
            boolean snapshotBacked = record.isMoving() || cancelledOrders.contains(record.movementOrderId);
            if (snapshotBacked) {
                record.movingEntityData = null;
                record.stationedEntityData = null;
                tombstone.entityRemoved = true;
                result.snapshotUnitsRemoved++;
            } else {
                Entity loaded = findLoadedEntity(record.entity);
                if (loaded != null) {
                    KOMEReflection.setDead(loaded);
                    tombstone.entityRemoved = true;
                    result.loadedUnitsRemoved++;
                } else {
                    result.pendingUnloaded++;
                }
            }

            if (record.farmhand) {
                tombstone.populationReturned = true;
                result.farmhandsReleased++;
            } else if (KOMEHiredUnitRecord.SOURCE_STEWARDSHIP_RESERVATION.equals(record.sourceType)) {
                // Native population remains committed until native demobilization, never because a controller leaves.
                tombstone.quarantined = true;
                data.pledgeReleaseQuarantine.put(record.entity, record.writeToNBT());
                result.quarantined++;
            } else if (returnPopulation(data, record, tombstone)) {
                if (record.type == KOMEPopulationType.DEFENSIVE) result.defensiveReturned += Math.max(0, record.cost);
                else result.offensiveReturned += Math.max(0, record.cost);
            } else {
                tombstone.quarantined = true;
                data.pledgeReleaseQuarantine.put(record.entity, record.writeToNBT());
                result.quarantined++;
            }
            if (tombstone.complete() && tombstone.completedTimestamp <= 0L) tombstone.completedTimestamp = nowMillis;
            data.removeUnitFromCompany(record);
            data.hiredUnits.remove(record.entity);
            result.unitsReleased++;
        }

        for (String companyId : new ArrayList<String>(data.armyCompanies.keySet())) {
            KOMEArmyCompany company = data.armyCompanies.get(companyId);
            if (company == null) continue;
            if (player.equals(company.transferRecipient) || player.equals(company.transferOfferedBy)) company.clearTransferOffer();
            if (company.units.isEmpty() && player.equals(company.owner)
                    && former.equals(KOMEAlliance.normalizeFactionKey(KOMEWartimeStewardshipService.nativeFaction(company)))) {
                data.armyCompanies.remove(companyId);
                result.companiesRemoved++;
            }
        }
        closeStaleAllocations(data, player, former);
        data.activeRecruitmentTiles.remove(KOMEWorldData.recruitmentTileKey(former, player));
        KOMECommandTroops.revalidateTemporaryControllers(data, nowMillis, "Player pledge changed");
        result.summary = summary(playerName, result);
        data.pledgeReleaseLastResults.put(player, result.summary);
        appendAudit(data, nowMillis + " player=" + player + " name=" + safe(playerName) + " former=" + former
            + " new=" + current + " reason=" + safe(reason) + " " + result.summary);
        data.markDirty();
        data.syncConquestTiles();
        notifyPlayerAndOperators(player, result.summary);
        return result;
    }

    public static Result retry(KOMEWorldData data, UUID player, long nowMillis) {
        Result result = new Result();
        if (data == null || player == null) return Result.failure("Missing player.");
        for (KOMEPledgeReleaseTombstone tombstone : data.pledgeReleaseTombstones.values()) {
            if (tombstone == null || !player.equals(tombstone.formerOwner) || tombstone.entityRemoved) continue;
            tombstone.lastRetry = nowMillis;
            Entity loaded = findLoadedEntity(tombstone.unitUuid);
            if (loaded != null) {
                KOMEReflection.setDead(loaded);
                tombstone.entityRemoved = true;
                if (tombstone.complete()) tombstone.completedTimestamp = nowMillis;
                result.loadedUnitsRemoved++;
            } else result.pendingUnloaded++;
        }
        result.changed = result.loadedUnitsRemoved > 0;
        result.summary = "Pledge-release retry: removed=" + result.loadedUnitsRemoved + ", still pending unloaded=" + result.pendingUnloaded + ".";
        appendAudit(data, nowMillis + " retry player=" + player + " " + result.summary);
        data.markDirty();
        return result;
    }

    public static Result resolve(KOMEWorldData data, UUID unit, String resolution, String operator, long nowMillis) {
        if (data == null || unit == null) return Result.failure("Unknown unit UUID.");
        KOMEPledgeReleaseTombstone tombstone = data.pledgeReleaseTombstones.get(unit);
        if (tombstone == null) return Result.failure("No pledge-release tombstone exists for " + unit + ".");
        String value = resolution == null ? "" : resolution.trim().toLowerCase(Locale.ROOT);
        if ("removed".equals(value)) {
            Entity loaded = findLoadedEntity(unit);
            if (loaded != null) KOMEReflection.setDead(loaded);
            tombstone.entityRemoved = true;
        } else if ("quarantine".equals(value)) {
            tombstone.quarantined = true;
            tombstone.entityRemoved = true;
        } else return Result.failure("Resolution must be removed or quarantine.");
        tombstone.lastRetry = nowMillis;
        if (tombstone.complete()) tombstone.completedTimestamp = nowMillis;
        appendAudit(data, nowMillis + " operator=" + safe(operator) + " resolvedUnit=" + unit + " as=" + value);
        data.markDirty();
        Result result = new Result();
        result.changed = true;
        result.summary = "Resolved " + unit + " as " + value + ". PopulationReturned=" + tombstone.populationReturned
            + ", quarantined=" + tombstone.quarantined + ".";
        return result;
    }

    /** Called before normal hired-unit registration so a late-loaded released entity can never act. */
    public static boolean interceptReleasedEntity(KOMEWorldData data, Entity entity, long nowMillis) {
        if (data == null || entity == null) return false;
        UUID id = KOMEReflection.getEntityUUID(entity);
        KOMEPledgeReleaseTombstone tombstone = data.pledgeReleaseTombstones.get(id);
        if (tombstone == null) return false;
        KOMEReflection.setDead(entity);
        tombstone.entityRemoved = true;
        tombstone.lastRetry = nowMillis;
        if (tombstone.complete()) tombstone.completedTimestamp = nowMillis;
        data.markDirty();
        return true;
    }

    public static String status(KOMEWorldData data, UUID player) {
        if (data == null || player == null) return "No pledge-release state.";
        int complete = 0, pending = 0, quarantined = 0;
        for (KOMEPledgeReleaseTombstone tombstone : data.pledgeReleaseTombstones.values()) {
            if (tombstone == null || !player.equals(tombstone.formerOwner)) continue;
            if (tombstone.complete()) complete++; else pending++;
            if (tombstone.quarantined) quarantined++;
        }
        String previous = data.pledgeReleaseLastResults.get(player);
        return "Pledge release: complete tombstones=" + complete + ", pending=" + pending + ", quarantined=" + quarantined
            + (previous == null || previous.length() == 0 ? "." : ". Last result: " + previous);
    }

    private static KOMEPledgeReleaseTombstone tombstone(KOMEWorldData data, KOMEHiredUnitRecord record,
            String formerFaction, UUID player, long nowMillis, String reason) {
        KOMEPledgeReleaseTombstone tombstone = data.pledgeReleaseTombstones.get(record.entity);
        if (tombstone == null) {
            tombstone = new KOMEPledgeReleaseTombstone();
            tombstone.unitUuid = record.entity;
            tombstone.formerOwner = player;
            tombstone.formerFaction = formerFaction;
            tombstone.fundingSource = record.sourceType;
            tombstone.sourceTile = record.sourceTileId;
            tombstone.sourceFaction = record.sourceFaction;
            tombstone.sourcePlayer = record.sourcePlayer;
            tombstone.populationType = record.type;
            tombstone.populationAmount = record.farmhand ? 0 : Math.max(0, record.cost);
            tombstone.releaseReason = reason == null ? "Pledge ended" : reason;
            tombstone.createdTimestamp = nowMillis;
            data.pledgeReleaseTombstones.put(record.entity, tombstone);
        }
        return tombstone;
    }

    private static boolean returnPopulation(KOMEWorldData data, KOMEHiredUnitRecord record,
            KOMEPledgeReleaseTombstone tombstone) {
        if (tombstone.populationReturned || record.populationReturned) {
            tombstone.populationReturned = true;
            return true;
        }
        int amount = Math.max(0, record.cost);
        if (KOMEHiredUnitRecord.SOURCE_PLAYER_RESERVE.equals(record.sourceType)) {
            UUID source = record.sourcePlayer == null ? record.owner : record.sourcePlayer;
            if (source == null) return false;
            data.getPopulation(source).release(record.type, amount);
        } else if (KOMEHiredUnitRecord.SOURCE_TILE_ALLOCATION.equals(record.sourceType)) {
            KOMETilePopulation pool = data.getFundingPool(record);
            UUID allocationPlayer = record.allocationPlayer == null ? record.owner : record.allocationPlayer;
            KOMEPlayerTilePopulationAllocation allocation = data.getAllocation(record.allocationTileId,
                record.allocationFaction, allocationPlayer);
            if (pool == null || allocation == null) return false;
            pool.release(record.type, amount);
            allocation.release(record.type, amount);
        } else if (KOMEHiredUnitRecord.SOURCE_TILE_POOL.equals(record.sourceType)) {
            KOMETilePopulation pool = data.getFundingPool(record);
            if (pool == null) return false;
            pool.release(record.type, amount);
        } else return false;
        record.populationReturned = true;
        data.releaseFundingBuild(record);
        record.releaseState = "PLEDGE_RELEASED";
        tombstone.populationReturned = true;
        return true;
    }

    private static boolean isOwnedFormerFactionUnit(KOMEHiredUnitRecord record, UUID player, String formerFaction) {
        if (record == null || !player.equals(record.owner)) return false;
        String former = KOMEAlliance.normalizeFactionKey(formerFaction);
        String source = KOMEAlliance.normalizeFactionKey(record.sourceFaction);
        String spawning = KOMEAlliance.normalizeFactionKey(record.spawningFaction);
        String unitFaction = KOMEAlliance.normalizeFactionKey(record.unitFaction);
        return former.equals(source) || former.equals(spawning) || source.length() == 0 && former.equals(unitFaction);
    }

    private static void closeStaleAllocations(KOMEWorldData data, UUID player, String formerFaction) {
        for (String key : new ArrayList<String>(data.populationAllocations.keySet())) {
            KOMEPlayerTilePopulationAllocation allocation = data.populationAllocations.get(key);
            if (allocation == null || !player.equals(allocation.playerUuid)
                    || !formerFaction.equals(KOMEAlliance.normalizeFactionKey(allocation.faction))) continue;
            if (allocation.offensiveUsed == 0 && allocation.defensiveUsed == 0) data.populationAllocations.remove(key);
            else {
                allocation.setAllocated(KOMEPopulationType.OFFENSIVE, allocation.offensiveUsed);
                allocation.setAllocated(KOMEPopulationType.DEFENSIVE, allocation.defensiveUsed);
            }
        }
    }

    private static Entity findLoadedEntity(UUID id) {
        MinecraftServer server = currentServer();
        if (server == null || server.worldServers == null || id == null) return null;
        for (WorldServer world : server.worldServers) {
            if (world == null) continue;
            for (Object object : world.loadedEntityList) {
                if (object instanceof Entity && id.equals(KOMEReflection.getEntityUUID((Entity) object))) return (Entity) object;
            }
        }
        return null;
    }

    private static void incrementSource(Preview result, String source, int amount) {
        String key = source == null || source.length() == 0 ? KOMEHiredUnitRecord.SOURCE_OTHER_LEGACY : source;
        Integer old = result.fundingSources.get(key);
        result.fundingSources.put(key, Integer.valueOf((old == null ? 0 : old.intValue()) + amount));
    }

    private static void appendAudit(KOMEWorldData data, String entry) {
        data.pledgeReleaseAudit.add(entry);
        while (data.pledgeReleaseAudit.size() > MAX_AUDIT) data.pledgeReleaseAudit.remove(0);
    }

    private static String summary(String playerName, Result result) {
        return "Pledge cleanup for " + safe(playerName) + ": released " + result.unitsReleased + " units, removed "
            + result.companiesRemoved + " empty companies, cancelled " + result.movementsCancelled + " movements, returned "
            + result.offensiveReturned + " offensive and " + result.defensiveReturned + " defensive population; pending unloaded="
            + result.pendingUnloaded + ", quarantined=" + result.quarantined + ", temporary authorities revoked="
            + result.temporaryAuthoritiesRevoked + ".";
    }

    private static void notifyPlayerAndOperators(UUID player, String message) {
        MinecraftServer server = currentServer();
        if (server == null || server.getConfigurationManager() == null) return;
        for (Object object : server.getConfigurationManager().playerEntityList) {
            if (!(object instanceof EntityPlayerMP)) continue;
            EntityPlayerMP target = (EntityPlayerMP) object;
            if (player.equals(KOMEReflection.getEntityUUID(target)) || target.canCommandSenderUseCommand(2, "troops")) {
                target.addChatMessage(new ChatComponentText(message));
            }
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }

    private static MinecraftServer currentServer() {
        try {
            return FMLCommonHandler.instance().getMinecraftServerInstance();
        } catch (Throwable unavailableOutsideForgeRuntime) {
            return null;
        }
    }

    public static final class Preview {
        public UUID player;
        public String formerFaction = "";
        public int units;
        public int farmhands;
        public int companies;
        public int movements;
        public int transferOffers;
        public int offensivePopulation;
        public int defensivePopulation;
        public int pendingUnloaded;
        public final java.util.Map<String, Integer> fundingSources = new java.util.LinkedHashMap<String, Integer>();

        public String describe(String playerName) {
            return "Leaving " + KOMEAlliance.displayFactionName(formerFaction) + " will release " + units + " hired units in "
                + companies + " companies and return up to " + offensivePopulation + " offensive/" + defensivePopulation
                + " defensive population to recorded sources. Active movements=" + movements + ", transfer offers="
                + transferOffers + ", farmhands=" + farmhands + ", funding=" + fundingSources
                + ". Transfer any companies you wish to preserve before continuing.";
        }
    }

    public static class Result {
        public boolean changed;
        public boolean failed;
        public boolean wasKing;
        public String formerFaction = "";
        public String newFaction = "";
        public int unitsReleased;
        public int loadedUnitsRemoved;
        public int snapshotUnitsRemoved;
        public int pendingUnloaded;
        public int farmhandsReleased;
        public int companiesRemoved;
        public int movementsCancelled;
        public int offensiveReturned;
        public int defensiveReturned;
        public int quarantined;
        public int temporaryAuthoritiesRevoked;
        public String summary = "";

        static Result noop(String message) { Result result = new Result(); result.summary = message; return result; }
        static Result failure(String message) { Result result = new Result(); result.failed = true; result.summary = message; return result; }
    }
}
