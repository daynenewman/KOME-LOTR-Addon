package kome.common.data;

import lotr.common.entity.npc.*;

/** Classifies the loaded canonical Master without localized-name heuristics. */
public final class KOMESerfProfessionClassifier {
    public static final class Profession {
        public final String key, displayName;
        public final KOMESerfMaterialProfile materialProfile;
        private Profession(String key,String displayName,KOMESerfMaterialProfile profile){this.key=key;this.displayName=displayName;this.materialProfile=profile;}
    }

    private static final Profession SMITH=p("smith","Blacksmith",KOMESerfMaterialProfile.SMITH);
    private static final Profession BARTENDER=p("bartender","Bartender",KOMESerfMaterialProfile.BREWING);
    private static final Profession FARMHAND=p("farmhand","Farmhand",KOMESerfMaterialProfile.FARMING);
    private static final Profession FARMER=p("farmer","Farmer",KOMESerfMaterialProfile.FARMING);
    private static final Profession MERCHANT=p("merchant","Merchant",KOMESerfMaterialProfile.GENERAL_TRADE);
    private static final Profession MINER=p("miner","Miner",KOMESerfMaterialProfile.MINING);
    private static final Profession BAKER=p("baker","Baker",KOMESerfMaterialProfile.BAKING);
    private static final Profession BREWER=p("brewer","Brewer",KOMESerfMaterialProfile.BREWING);
    private static final Profession VINTNER=p("vintner","Vintner",KOMESerfMaterialProfile.BREWING);
    private static final Profession BUTCHER=p("butcher","Butcher",KOMESerfMaterialProfile.HUSBANDRY);
    private static final Profession FISHMONGER=p("fishmonger","Fishmonger",KOMESerfMaterialProfile.FISHING);
    private static final Profession LUMBER=p("lumber_worker","Lumber Worker",KOMESerfMaterialProfile.LUMBER);
    private static final Profession BUILDER=p("builder","Builder",KOMESerfMaterialProfile.BUILDING);
    private static final Profession HUNTER=p("hunter","Hunter",KOMESerfMaterialProfile.HUNTING);
    private static final Profession GOLDSMITH=p("goldsmith","Goldsmith",KOMESerfMaterialProfile.PRECIOUS_METAL);
    private static final Profession ORCHARD=p("orchard_worker","Orchard Worker",KOMESerfMaterialProfile.GROWING);
    private static final Profession STABLE=p("stable_worker","Stable Worker",KOMESerfMaterialProfile.HUSBANDRY);
    private static final Profession GROWER=p("grower","Grower",KOMESerfMaterialProfile.GROWING);
    private static final Profession SHAMAN=p("shaman","Shaman",KOMESerfMaterialProfile.RITUAL);
    private static final Profession SCAVENGER=p("scavenger","Scavenger",KOMESerfMaterialProfile.SCAVENGING);
    private static final Profession GENERAL=p("general_labor","General Labor",KOMESerfMaterialProfile.GENERAL_LABOR);

    private KOMESerfProfessionClassifier() {}
    private static Profession p(String key,String name,KOMESerfMaterialProfile profile){return new Profession(key,name,profile);}

    public static Profession classify(LOTREntityNPC npc) {
        if(npc==null)return GENERAL;
        boolean smith=npc instanceof LOTRTradeable.Smith;
        boolean bartender=npc instanceof LOTRTradeable.Bartender;
        boolean farmhand=npc instanceof LOTRFarmhand;
        boolean farmer=farmerOnly(npc);
        Profession concrete=concreteProfession(npc);
        Profession pool=tradePoolProfession(npc);
        boolean travelling=npc instanceof LOTRTravellingTrader;
        return classifySignals(smith,bartender,farmhand,farmer,concrete,pool,travelling);
    }

    static Profession classifySignals(boolean smith,boolean bartender,boolean farmhand,boolean farmer,Profession concrete,Profession pool,boolean travelling) {
        if(smith)return SMITH;
        if(bartender)return BARTENDER;
        if(farmhand)return FARMHAND;
        if(farmer)return FARMER;
        if(concrete!=null)return concrete;
        if(pool!=null)return pool;
        if(travelling)return MERCHANT;
        return GENERAL;
    }

    static Profession forKey(String key) {
        Profession[] values={SMITH,BARTENDER,FARMHAND,FARMER,MERCHANT,MINER,BAKER,BREWER,VINTNER,BUTCHER,FISHMONGER,LUMBER,BUILDER,HUNTER,GOLDSMITH,ORCHARD,STABLE,GROWER,SHAMAN,SCAVENGER,GENERAL};
        for(Profession value:values)if(value.key.equals(key))return value;
        return null;
    }

    private static boolean farmerOnly(LOTREntityNPC npc) {
        if(!(npc instanceof LOTRUnitTradeable))return false;
        LOTRUnitTradeEntries entries=((LOTRUnitTradeable)npc).getUnits();
        if(entries==null||entries.tradeEntries==null||entries.tradeEntries.length==0)return false;
        boolean found=false;
        for(LOTRUnitTradeEntry entry:entries.tradeEntries){if(entry==null)continue;found=true;if(entry.task!=LOTRHiredNPCInfo.Task.FARMER)return false;}
        return found;
    }

    private static Profession concreteProfession(LOTREntityNPC npc) {
        if(npc instanceof LOTREntityDwarfMiner||npc instanceof LOTREntityBlueDwarfMiner||npc instanceof LOTREntityHarnedorMiner||npc instanceof LOTREntityGulfMiner||npc instanceof LOTREntitySouthronMiner||npc instanceof LOTREntityUmbarMiner||npc instanceof LOTREntityNomadMiner)return MINER;
        if(npc instanceof LOTREntityDorwinionElfVintner)return VINTNER;
        if(npc instanceof LOTREntityRohanStablemaster)return STABLE;
        if(npc instanceof LOTREntityTauredainShaman)return SHAMAN;
        if(npc instanceof LOTREntityHalfTrollScavenger)return SCAVENGER;
        if(npc instanceof LOTREntityMoredainHuntsman||npc instanceof LOTREntityEasterlingHunter||npc instanceof LOTREntityHarnedorHunter||npc instanceof LOTREntityGulfHunter)return HUNTER;
        if(npc instanceof LOTREntityEasterlingGoldsmith||npc instanceof LOTREntitySouthronGoldsmith||npc instanceof LOTREntityUmbarGoldsmith||npc instanceof LOTREntityGulfGoldsmith)return GOLDSMITH;
        if(npc instanceof LOTREntityHobbitOrcharder||npc instanceof LOTREntityRohanOrcharder)return ORCHARD;
        if(npc instanceof LOTREntityGondorFlorist||npc instanceof LOTREntityBreeFlorist||npc instanceof LOTREntityBreeHobbitFlorist||npc instanceof LOTREntitySouthronFlorist||npc instanceof LOTREntityUmbarFlorist||npc instanceof LOTREntityGondorGreengrocer)return GROWER;
        if(npc instanceof LOTREntityRohanBuilder||npc instanceof LOTREntityMoredainHutmaker||isMason(npc))return BUILDER;
        if(isBaker(npc))return BAKER;
        if(isBrewer(npc))return BREWER;
        if(isButcher(npc))return BUTCHER;
        if(isFishmonger(npc))return FISHMONGER;
        if(isLumberWorker(npc))return LUMBER;
        if(isKnownMerchant(npc))return MERCHANT;
        return null;
    }

    private static Profession tradePoolProfession(LOTREntityNPC npc) {
        if(!(npc instanceof LOTRTradeable))return null;
        LOTRTradeEntries buy=((LOTRTradeable)npc).getBuyPool();
        if(buy==LOTRTradeEntries.DWARF_MINER_BUY||buy==LOTRTradeEntries.BLUE_DWARF_MINER_BUY||buy==LOTRTradeEntries.HARAD_MINER_BUY)return MINER;
        if(buy==LOTRTradeEntries.DORWINION_VINTNER_BUY)return VINTNER;
        if(buy==LOTRTradeEntries.ROHAN_STABLEMASTER_BUY)return STABLE;
        if(buy==LOTRTradeEntries.MOREDAIN_HUNTSMAN_BUY||buy==LOTRTradeEntries.RHUN_HUNTER_BUY||buy==LOTRTradeEntries.HARAD_HUNTER_BUY||buy==LOTRTradeEntries.GULF_HUNTER_BUY)return HUNTER;
        if(buy==LOTRTradeEntries.RHUN_GOLDSMITH_BUY||buy==LOTRTradeEntries.HARAD_GOLDSMITH_BUY)return GOLDSMITH;
        if(buy==LOTRTradeEntries.HOBBIT_ORCHARDER_BUY||buy==LOTRTradeEntries.ROHAN_ORCHARDER_BUY)return ORCHARD;
        if(buy==LOTRTradeEntries.MOREDAIN_HUTMAKER_BUY)return BUILDER;
        if(buy==LOTRTradeEntries.TAUREDAIN_SHAMAN_BUY)return SHAMAN;
        if(buy==LOTRTradeEntries.HALF_TROLL_SCAVENGER_BUY)return SCAVENGER;
        return null;
    }

    private static boolean isBaker(LOTREntityNPC n){return n instanceof LOTREntityBreeBaker||n instanceof LOTREntityBreeHobbitBaker||n instanceof LOTREntityDaleBaker||n instanceof LOTREntityGondorBaker||n instanceof LOTREntityRohanBaker||n instanceof LOTREntityEasterlingBaker||n instanceof LOTREntityHarnedorBaker||n instanceof LOTREntitySouthronBaker||n instanceof LOTREntityUmbarBaker||n instanceof LOTREntityGulfBaker;}
    private static boolean isBrewer(LOTREntityNPC n){return n instanceof LOTREntityBreeBrewer||n instanceof LOTREntityBreeHobbitBrewer||n instanceof LOTREntityGondorBrewer||n instanceof LOTREntityRohanBrewer||n instanceof LOTREntityEasterlingBrewer||n instanceof LOTREntityHarnedorBrewer||n instanceof LOTREntitySouthronBrewer||n instanceof LOTREntityUmbarBrewer||n instanceof LOTREntityGulfBrewer||n instanceof LOTREntityNomadBrewer;}
    private static boolean isButcher(LOTREntityNPC n){return n instanceof LOTREntityBreeButcher||n instanceof LOTREntityBreeHobbitButcher||n instanceof LOTREntityGondorButcher||n instanceof LOTREntityRohanButcher||n instanceof LOTREntityEasterlingButcher||n instanceof LOTREntityHarnedorButcher||n instanceof LOTREntitySouthronButcher||n instanceof LOTREntityUmbarButcher||n instanceof LOTREntityGulfButcher;}
    private static boolean isFishmonger(LOTREntityNPC n){return n instanceof LOTREntityGondorFishmonger||n instanceof LOTREntityRohanFishmonger||n instanceof LOTREntityEasterlingFishmonger||n instanceof LOTREntityHarnedorFishmonger||n instanceof LOTREntitySouthronFishmonger||n instanceof LOTREntityUmbarFishmonger||n instanceof LOTREntityGulfFishmonger;}
    private static boolean isLumberWorker(LOTREntityNPC n){return n instanceof LOTREntityBreeLumberman||n instanceof LOTREntityGondorLumberman||n instanceof LOTREntityRohanLumberman||n instanceof LOTREntityEasterlingLumberman||n instanceof LOTREntityHarnedorLumberman||n instanceof LOTREntitySouthronLumberman||n instanceof LOTREntityUmbarLumberman||n instanceof LOTREntityGulfLumberman;}
    private static boolean isMason(LOTREntityNPC n){return n instanceof LOTREntityBreeMason||n instanceof LOTREntityGondorMason||n instanceof LOTREntityEasterlingMason||n instanceof LOTREntityHarnedorMason||n instanceof LOTREntitySouthronMason||n instanceof LOTREntityUmbarMason||n instanceof LOTREntityGulfMason||n instanceof LOTREntityNomadMason;}
    private static boolean isKnownMerchant(LOTREntityNPC n){return n instanceof LOTREntityBlueDwarfMerchant||n instanceof LOTREntityDaleMerchant||n instanceof LOTREntityIronHillsMerchant||n instanceof LOTREntityNearHaradMerchant||n instanceof LOTREntityNomadMerchant||n instanceof LOTREntityDorwinionMerchantElf||n instanceof LOTREntityDorwinionMerchantMan||n instanceof LOTREntityBreeHobbitTrader||n instanceof LOTREntityHarnedorTrader||n instanceof LOTREntitySouthronTrader||n instanceof LOTREntityUmbarTrader||n instanceof LOTREntityGulfTrader||n instanceof LOTREntityNomadTrader||n instanceof LOTREntityMoredainVillageTrader;}
}
