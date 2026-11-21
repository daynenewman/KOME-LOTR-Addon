package kome.common.network;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMECompanyCommandAuthority;
import kome.common.data.KOMERulerAuthorization;
import kome.common.data.KOMEWartimeStewardshipService;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEHiredUnitClassification;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEPopulationType;
import kome.common.data.KOMETileTroopSummary;
import kome.common.data.KOMEWorldData;
import kome.common.KOMEReflection;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Recipient-specific, read-only map rows. Never serialize canonical company or order NBT here. */
public final class KOMECompanyMapProjection {
    private KOMECompanyMapProjection() { }
    public static final String SPOTTED_STATUS = "SPOTTED_SHIELD";
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final SecretKeySpec ALIAS_KEY = newAliasKey();

    private static SecretKeySpec newAliasKey() {
        byte[] secret=new byte[32];new SecureRandom().nextBytes(secret);
        return new SecretKeySpec(secret,"HmacSHA256");
    }

    /** Opaque per-recipient, per-tile company key; stable across the two independent publishers. */
    private static String spottedAlias(UUID viewer,String tile,String companyId) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(ALIAS_KEY);
            mac.update(viewer.toString().getBytes(StandardCharsets.UTF_8));mac.update((byte)0);
            mac.update(tile.getBytes(StandardCharsets.UTF_8));mac.update((byte)0);
            byte[] digest=mac.doFinal(companyId.getBytes(StandardCharsets.UTF_8));
            StringBuilder alias=new StringBuilder("~recon:");
            for(byte value:digest) {alias.append(HEX[(value>>>4)&15]);alias.append(HEX[value&15]);}
            return alias.toString();
        } catch(GeneralSecurityException unavailable) {
            throw new IllegalStateException("Required SHA-256 marker alias unavailable",unavailable);
        }
    }

    /** Recipient-local disclosure categories; future tile intelligence can use a narrower category. */
    public enum Information { SHIELD_AND_UNIT_COUNT, MOVEMENT_ROUTE }

    public static View view(KOMEWorldData data, UUID viewer, String currentFaction, boolean operator) {
        return new View(data, viewer, currentFaction, operator);
    }

    /** Current server-side read identity for information-bearing company responses. */
    public static View view(KOMEWorldData data, EntityPlayerMP player) {
        if(player==null)return view(data,null,"",false);
        LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
        return view(data,KOMEReflection.getEntityUUID(player),
            pledge==null?"":pledge.codeName(),player.canCommandSenderUseCommand(2,"troops"));
    }

    /** Map-origin catalogues use presentation consent, not operator command privilege. */
    public static View mapView(KOMEWorldData data, EntityPlayerMP player) {
        if(player==null)return view(data,null,"",false);
        LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
        return view(data,KOMEReflection.getEntityUUID(player),
            pledge==null?"":pledge.codeName(),KOMEAdminMapVision.enabled(player));
    }

    /** Cheap single-record read check for short-lived command/catalogue replies. */
    public static boolean directlyVisible(KOMEWorldData data, EntityPlayerMP player, KOMEArmyCompany company) {
        if(player==null)return false;
        LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
        return directlyVisible(data,KOMEReflection.getEntityUUID(player),pledge==null?"":pledge.codeName(),
            player.canCommandSenderUseCommand(2,"troops"),company);
    }

    private static boolean directlyVisible(KOMEWorldData data,UUID viewer,String faction,
            boolean operator,KOMEArmyCompany company) {
        if(data==null || company==null || company.id==null || company.id.isEmpty()
                || data.armyCompanies.get(company.id)!=company)return false;
        String companyFaction=KOMEAlliance.normalizeFactionKey(company.faction);
        if(companyFaction.isEmpty())return false;
        if(operator)return true;
        String current=KOMEAlliance.normalizeFactionKey(faction);
        if(!current.isEmpty() && current.equals(companyFaction))return true;
        if(nativeRulerCanRead(data,viewer,current,company))return true;
        // Temporary command authority is independently validated by the existing
        // delegation/stewardship boundary. A live pledge may differ from the
        // last recorded faction while that authority remains effective.
        return viewer!=null && viewer.equals(company.temporaryController)
            && KOMECompanyCommandAuthority.canPersonallyCommand(data,company,viewer);
    }

    private static boolean nativeRulerCanRead(KOMEWorldData data,UUID viewer,String current,
            KOMEArmyCompany company) {
        if(data==null || viewer==null || company==null || company.id==null
                || data.armyCompanies.get(company.id)!=company)return false;
        String nativeFaction=KOMEWartimeStewardshipService.nativeFaction(company);
        return !current.isEmpty() && current.equals(KOMEAlliance.normalizeFactionKey(nativeFaction))
            && KOMERulerAuthorization.canActAsRuler(data,nativeFaction,viewer);
    }

    public static final class View {
        private final KOMEWorldData data;
        private final UUID viewer;
        private final String faction;
        private final boolean operator;
        private final Set<String> observedTiles = new HashSet<String>();
        private final Map<String, Integer> presentPopulation = new HashMap<String, Integer>();
        private final Map<String, Integer> presentUnits = new HashMap<String, Integer>();
        private final Map<String, String> spottedIds = new HashMap<String, String>();
        private final Map<String, KOMETileTroopSummary> visibleStationed = new HashMap<String, KOMETileTroopSummary>();

        private View(KOMEWorldData data, UUID viewer, String currentFaction, boolean operator) {
            this.data=data;this.viewer=viewer;
            this.faction=KOMEAlliance.normalizeFactionKey(currentFaction);this.operator=operator;
            if(data==null)return;
            for(KOMEArmyCompany company:data.armyCompanies.values()) {
                if(company==null || company.id==null || data.armyCompanies.get(company.id)!=company)continue;
                Present present=KOMECompanyMapProjection.presentMembership(data,company);
                if(present.units<=0)continue;
                presentPopulation.put(company.id,present.population);
                presentUnits.put(company.id,present.units);
                // Faction peers may see a friendly company, but cannot use it as a spotter.
                // An owner whose current pledge no longer authorizes that company is not a spotter.
                if(viewer!=null && directlyVisible(company)
                        && KOMECompanyCommandAuthority.canPersonallyCommand(data,company,viewer))
                    observedTiles.add(KOMEConquestTile.normalizeId(company.currentTile));
            }
            for(KOMEArmyCompany company:data.armyCompanies.values()) {
                if(!directlyVisible(company) || !presentUnits.containsKey(company.id))continue;
                String tile=KOMEConquestTile.normalizeId(company.currentTile);
                KOMETileTroopSummary summary=visibleStationed.get(tile);
                if(summary==null) {summary=new KOMETileTroopSummary();summary.tileId=tile;visibleStationed.put(tile,summary);}
                Set<UUID> counted=new HashSet<UUID>();
                for(UUID id:company.units) {
                    if(!counted.add(id))continue;
                    KOMEHiredUnitRecord record=data.hiredUnits.get(id);
                    if(!presentMember(data,company,record))continue;
                    int cost=Math.max(0,record.cost);
                    summary.stationedPop=addPopulation(summary.stationedPop,cost);
                    if(record.type==KOMEPopulationType.DEFENSIVE)
                        summary.stationedDefensivePop=addPopulation(summary.stationedDefensivePop,cost);
                    else {
                        summary.stationedOffensivePop=addPopulation(summary.stationedOffensivePop,cost);
                        if(record.mounted)summary.stationedMountedPop=addPopulation(summary.stationedMountedPop,cost);
                    }
                }
            }
            for(KOMEArmyCompany company:data.armyCompanies.values()) {
                if(!spotted(company))continue;
                String alias=spottedAlias(viewer,KOMEConquestTile.normalizeId(company.currentTile),company.id);
                while(data.armyCompanies.containsKey(alias))alias="~"+alias;
                spottedIds.put(company.id,alias);
            }
        }

        public boolean observes(String tile, Information information) {
            return information==Information.SHIELD_AND_UNIT_COUNT
                && observedTiles.contains(KOMEConquestTile.normalizeId(tile));
        }

        public boolean directlyVisible(KOMEArmyCompany company) {
            return KOMECompanyMapProjection.directlyVisible(data,viewer,faction,operator,company);
        }

        /** Existing native-faction ruler read exception, without granting faction peers command rights. */
        public boolean nativeRulerCanRead(KOMEArmyCompany company) {
            return KOMECompanyMapProjection.nativeRulerCanRead(data,viewer,faction,company);
        }

        /** Detail endpoints may never upgrade tile-only spotting into record inspection. */
        public boolean readableCampaignMember(KOMEHiredUnitRecord record) {
            if(record==null || !KOMEHiredUnitClassification.isCampaignUnit(record)
                    || record.companyId==null || record.companyId.isEmpty())return true;
            return data!=null && directlyVisible(data.armyCompanies.get(record.companyId));
        }

        public boolean readableCompanyOrder(KOMEArmyMovementOrder order) {
            if(order==null)return false;
            if(order.companyId==null || order.companyId.isEmpty())return true;
            KOMEArmyCompany company=data==null?null:data.armyCompanies.get(order.companyId);
            // A retained order can outlive its company row. Its authoritative faction
            // still permits a current faction peer, but never a former owner alone.
            return company==null
                ? operator || !faction.isEmpty()
                    && faction.equals(KOMEAlliance.normalizeFactionKey(order.ownerFaction))
                : directlyVisible(company);
        }

        public boolean spotted(KOMEArmyCompany company) {
            return !directlyVisible(company) && company!=null && data!=null
                && company.id!=null && data.armyCompanies.get(company.id)==company
                && !KOMEAlliance.normalizeFactionKey(company.faction).isEmpty()
                && presentUnits.containsKey(company.id)
                && observes(company.currentTile,Information.SHIELD_AND_UNIT_COUNT);
        }

        public boolean visible(KOMEArmyCompany company) {
            return directlyVisible(company) || spotted(company);
        }

        public String presentationId(KOMEArmyCompany company) {
            return spotted(company)?spottedIds.get(company.id):company.id;
        }

        public int soldierCount(KOMEArmyCompany company) {
            Integer value=company==null?null:presentUnits.get(company.id);
            return value==null?0:value.intValue();
        }

        public int stationedPopulation(KOMEArmyCompany company) {
            Integer value=company==null?null:presentPopulation.get(company.id);
            return value==null?0:value.intValue();
        }

        public KOMETileTroopSummary stationedSummary(String tile) {
            return visibleStationed.get(KOMEConquestTile.normalizeId(tile));
        }
    }

    /** Canonical strategic presence is independent of chunk loading and exact NPC coordinates. */
    private static final class Present {
        int units;
        int population;
    }

    private static Present presentMembership(KOMEWorldData data,KOMEArmyCompany company) {
        Present present=new Present();
        if(!presentCompany(data,company))return present;
        Set<UUID> counted=new HashSet<UUID>();long total=0;
        for(UUID id:company.units) {
            if(!counted.add(id))continue;
            KOMEHiredUnitRecord record=data.hiredUnits.get(id);
            if(!presentMember(data,company,record))continue;
            present.units++;
            total+=Math.max(0,record.cost);
        }
        present.population=(int)Math.min(Integer.MAX_VALUE,total);
        return present;
    }

    /** A route ID is not transit: intermediate arrivals retain it until the next departure. */
    static boolean presentCompany(KOMEWorldData data,KOMEArmyCompany company) {
        if(data==null || company==null || company.id==null
                || data.armyCompanies.get(company.id)!=company
                || !KOMEConquestTile.isCanonicalTileId(company.currentTile))return false;
        String id=company.movementOrderId==null?"":company.movementOrderId;
        if(id.isEmpty())return KOMEArmyCompany.STATIONED.equals(company.status)
            || KOMEArmyCompany.WAR_ENDED_HALTED.equals(company.status);
        KOMEArmyMovementOrder order=data.armyMovements.get(id);
        return order!=null && id.equals(order.id) && company.id.equals(order.companyId)
            && KOMEConquestTile.normalizeId(company.currentTile).equals(KOMEConquestTile.normalizeId(order.currentTile))
            && (KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)
                || KOMEArmyMovementOrder.ACCESS_HALTED.equals(order.status)
                || KOMEArmyMovementOrder.HOLDING.equals(order.status)
                || KOMEArmyMovementOrder.WAR_ENDED_HALTED.equals(order.status)
                || KOMEArmyMovementOrder.CONFLICT_HELD.equals(order.status)
                || KOMEArmyMovementOrder.CONFLICT_RELEASED_PAUSED.equals(order.status));
    }

    static boolean presentMember(KOMEWorldData data,KOMEArmyCompany company,KOMEHiredUnitRecord record) {
        if(!presentCompany(data,company))return false;
        String orderId=company.movementOrderId==null?"":company.movementOrderId;
        if(record==null || record.farmhand || record.movingEntityData!=null
                || !orderId.equals(record.movementOrderId==null?"":record.movementOrderId)
                || !orderId.isEmpty() && record.stationedEntityData==null && record.getPhysicalLocator()==null)return false;
        return KOMEHiredUnitClassification.isCampaignUnit(record)
            && KOMEHiredUnitClassification.isWithinCommittedCampaignLifespan(data,record)
            && company.id.equals(record.companyId) && company.units.contains(record.entity)
            && java.util.Objects.equals(company.owner,record.owner)
            && KOMEConquestTile.normalizeId(company.currentTile).equals(KOMEConquestTile.normalizeId(record.currentTile));
    }

    private static int addPopulation(int current,int cost) {
        return (int)Math.min(Integer.MAX_VALUE,(long)current+cost);
    }

    public static boolean visible(KOMEWorldData data, KOMEArmyCompany company, UUID viewer,
            String currentFaction, boolean operator) {
        return view(data,viewer,currentFaction,operator).visible(company);
    }

    public static NBTTagCompound companyTag(KOMEArmyCompany company) {
        return companyTag(company,company.totalPopulation);
    }

    public static NBTTagCompound companyTag(KOMEArmyCompany company,int population) {
        return companyTag(company,population,false);
    }

    /** The client status describes map presence; the separate order row retains route state. */
    public static NBTTagCompound companyTag(KOMEArmyCompany company,int population,boolean present) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Id", company.id);
        tag.setString("Name", company.name == null ? "" : company.name);
        tag.setString("Faction", KOMEAlliance.normalizeFactionKey(company.faction));
        tag.setString("NativeFaction", KOMEAlliance.normalizeFactionKey(company.nativeFaction));
        tag.setString("CurrentTile", KOMEConquestTile.normalizeId(company.currentTile));
        tag.setString("Status", present && company.isMoving()
            ? (KOMEArmyCompany.WAR_ENDED_HALTED.equals(company.status)
                ? KOMEArmyCompany.WAR_ENDED_HALTED : KOMEArmyCompany.STATIONED)
            : company.isMoving() ? KOMEArmyCompany.MOVING : company.status);
        tag.setInteger("Population",Math.max(0,population));
        return tag;
    }

    /** A spotted row supplies only a noncanonical display key and faction shield context. */
    public static NBTTagCompound spottedTag(KOMEArmyCompany company,String presentationId) {
        NBTTagCompound tag=new NBTTagCompound();
        tag.setString("Id",presentationId);
        tag.setString("Faction",KOMEAlliance.normalizeFactionKey(company.faction));
        tag.setString("NativeFaction",KOMEAlliance.normalizeFactionKey(company.faction));
        tag.setString("CurrentTile",KOMEConquestTile.normalizeId(company.currentTile));
        tag.setString("Status",SPOTTED_STATUS);
        return tag;
    }

    public static KOMEArmyCompany readCompany(NBTTagCompound tag) {
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = tag.getString("Id");
        company.name = tag.getString("Name");
        company.faction = KOMEAlliance.normalizeFactionKey(tag.getString("Faction"));
        company.nativeFaction = KOMEAlliance.normalizeFactionKey(tag.getString("NativeFaction"));
        company.currentTile = KOMEConquestTile.normalizeId(tag.getString("CurrentTile"));
        company.status = tag.getString("Status");
        company.totalPopulation = Math.max(0,tag.getInteger("Population"));
        return company;
    }

    public static NBTTagCompound movementTag(KOMEArmyMovementOrder order) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Id", order.id);
        tag.setString("CompanyId", order.companyId);
        tag.setString("CompanyName", order.companyName == null ? "" : order.companyName);
        tag.setString("OriginTile", KOMEConquestTile.normalizeId(order.originTile));
        tag.setString("DestinationTile", KOMEConquestTile.normalizeId(order.destinationTile));
        tag.setString("CurrentTile", KOMEConquestTile.normalizeId(order.currentTile));
        tag.setString("NextTile", KOMEConquestTile.normalizeId(order.nextTile));
        tag.setString("CurrentStepOriginTile", KOMEConquestTile.normalizeId(order.currentStepOriginTile));
        tag.setString("CurrentStepDestinationTile", KOMEConquestTile.normalizeId(order.currentStepDestinationTile));
        tag.setString("Status", order.status);
        tag.setInteger("CurrentRouteIndex", order.currentRouteIndex);
        tag.setInteger("NextRouteIndex", order.nextRouteIndex);
        tag.setInteger("Population", order.population);
        tag.setInteger("MountedUnits", order.mountedUnits);
        tag.setInteger("GroundUnits", order.groundUnits);
        tag.setLong("ArrivalMillis", order.arrivalMillis);
        tag.setLong("NextStepAvailableMillis", order.nextStepAvailableMillis);
        NBTTagList route = new NBTTagList();
        for (String tile : order.routeTiles) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("Tile", KOMEConquestTile.normalizeId(tile));
            route.appendTag(entry);
        }
        tag.setTag("RouteTiles", route);
        return tag;
    }

    public static KOMEArmyMovementOrder readMovement(NBTTagCompound tag) {
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
        order.id = tag.getString("Id");
        order.companyId = tag.getString("CompanyId");
        order.companyName = tag.getString("CompanyName");
        order.originTile = KOMEConquestTile.normalizeId(tag.getString("OriginTile"));
        order.destinationTile = KOMEConquestTile.normalizeId(tag.getString("DestinationTile"));
        order.currentTile = KOMEConquestTile.normalizeId(tag.getString("CurrentTile"));
        order.nextTile = KOMEConquestTile.normalizeId(tag.getString("NextTile"));
        order.currentStepOriginTile = KOMEConquestTile.normalizeId(tag.getString("CurrentStepOriginTile"));
        order.currentStepDestinationTile = KOMEConquestTile.normalizeId(tag.getString("CurrentStepDestinationTile"));
        order.status = tag.getString("Status");
        order.currentRouteIndex = tag.getInteger("CurrentRouteIndex");
        order.nextRouteIndex = tag.getInteger("NextRouteIndex");
        order.population = tag.getInteger("Population");
        order.mountedUnits = tag.getInteger("MountedUnits");
        order.groundUnits = tag.getInteger("GroundUnits");
        order.arrivalMillis = tag.getLong("ArrivalMillis");
        order.nextStepAvailableMillis = tag.getLong("NextStepAvailableMillis");
        NBTTagList route = tag.getTagList("RouteTiles", 10);
        for (int i = 0; i < route.tagCount(); i++)
            order.routeTiles.add(KOMEConquestTile.normalizeId(route.getCompoundTagAt(i).getString("Tile")));
        return order;
    }
}
