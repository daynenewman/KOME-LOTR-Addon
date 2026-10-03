package kome.common.data;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import kome.common.config.KOMEConfigRegistry;
import lotr.common.entity.LOTREntities;
import lotr.common.entity.npc.LOTREntityEnt;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import lotr.common.entity.npc.LOTRUnitTradeEntries;
import lotr.common.entity.npc.LOTRUnitTradeEntry;
import net.minecraft.world.World;

/** Uses native unit factories/max health and the canonical cost service; never coins or player ownership. */
public final class KOMEMusterNativeRoster implements KOMEMusterService.RosterSource {
    public static final String RESOURCE = "assets/kome/config/kome_muster_roster.csv";
    public static final class Definition {
        public final String faction, source, key;
        public final int index, weight;
        Definition(String faction, String source, int index, int weight) {
            this.faction = faction; this.source = source; this.index = index; this.weight = weight;
            key = (faction + ":" + source + ":" + index).toLowerCase(java.util.Locale.ROOT);
        }
    }
    private static final List<Definition> DEFINITIONS = load();
    private final World world;
    public KOMEMusterNativeRoster(World world) {
        if (world == null || world.isRemote) throw new IllegalArgumentException("Native muster requires a server World.");
        this.world = world;
    }
    public static List<Definition> definitions() { return DEFINITIONS; }
    public List<KOMEMusterRoster.Unit> resolve(String faction) {
        List<KOMEMusterRoster.Unit> result = new ArrayList<KOMEMusterRoster.Unit>();
        Map<String, Integer> overrides = KOMEConfigRegistry.muster().getRosterWeightOverrides();
        for (Definition definition : DEFINITIONS) if (definition.faction.equals(faction)) {
            int weight = overrides.containsKey(definition.key) ? overrides.get(definition.key) : definition.weight;
            if (weight == 0) continue;
            LOTRUnitTradeEntry trade = nativeTrade(definition);
            if (trade.task != LOTRHiredNPCInfo.Task.WARRIOR || "rohan".equals(faction) && trade.mountClass == null)
                throw new IllegalArgumentException("Muster data contains a noncombat/Rohan foot unit: " + definition.key);
            LOTREntityNPC probe = trade.getOrCreateHiredNPC(world);
            if (probe == null) throw new IllegalStateException("Native muster unit is unavailable: " + definition.key);
            try {
                String nativeFaction = probe.getFaction() == null ? ""
                    : KOMEAlliance.normalizeFactionKey(probe.getFaction().codeName());
                if (!faction.equals(nativeFaction)) throw new IllegalArgumentException(
                    "Foreign unit in native muster roster: " + definition.key + " / " + nativeFaction);
                String entity = LOTREntities.getStringFromClass(trade.entityClass);
                String mount = trade.mountClass == null ? "" : LOTREntities.getStringFromClass(trade.mountClass);
                if (trade.mountClass != null && (mount == null || mount.isEmpty()))
                    throw new IllegalArgumentException("Unregistered muster mount: " + definition.key);
                int cost = KOMEUnitPopulationCostService.calculate(entity, (int) Math.ceil(probe.getMaxHealth()),
                    trade.mountClass != null, false);
                result.add(new KOMEMusterRoster.Unit(definition.key, nativeFaction, entity, mount, cost, weight));
            } finally { probe.setDead(); }
        }
        return result;
    }
    static LOTRUnitTradeEntry nativeTrade(Definition definition) {
        if ("ENT".equals(definition.source)) return new LOTRUnitTradeEntry(LOTREntityEnt.class, 0, 0F);
        try {
            LOTRUnitTradeEntries table = (LOTRUnitTradeEntries) LOTRUnitTradeEntries.class.getField(definition.source).get(null);
            if (definition.index < 0 || definition.index >= table.tradeEntries.length)
                throw new IllegalArgumentException("Native trade index is out of bounds: " + definition.key);
            return table.tradeEntries[definition.index];
        } catch (ReflectiveOperationException failure) {
            throw new IllegalArgumentException("Unknown native muster source: " + definition.key, failure);
        }
    }
    private static List<Definition> load() {
        InputStream input = KOMEMusterNativeRoster.class.getClassLoader().getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("Missing native muster roster: " + RESOURCE);
        List<Definition> result = new ArrayList<Definition>();
        java.util.Set<String> keys = new java.util.HashSet<String>(), factions = new java.util.HashSet<String>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
            if (!"faction_id,source,entry_index,weight".equals(reader.readLine()))
                throw new IllegalArgumentException("Invalid muster roster header.");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                String[] fields = line.split(",", -1);
                if (fields.length != 4) throw new IllegalArgumentException("Invalid muster roster row: " + line);
                Definition definition = new Definition(fields[0], fields[1], Integer.parseInt(fields[2]), Integer.parseInt(fields[3]));
                if (!KOMEAlliance.allFactionKeys().contains(definition.faction)
                        || !definition.source.matches("[A-Z_]+") || definition.index < 0 || definition.weight <= 0
                        || !keys.add(definition.key) || "ENT".equals(definition.source)
                            && (!"fangorn".equals(definition.faction) || definition.index != 0))
                    throw new IllegalArgumentException("Invalid/duplicate native muster definition: " + line);
                factions.add(definition.faction); result.add(definition);
            }
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot read muster roster.", failure); }
        if (!factions.equals(new java.util.HashSet<String>(KOMEAlliance.allFactionKeys())))
            throw new IllegalArgumentException("Muster roster must cover all supported factions.");
        return Collections.unmodifiableList(result);
    }
}
