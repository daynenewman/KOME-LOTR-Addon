package kome.verification;

import com.mojang.authlib.GameProfile;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kome.common.data.*;
import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/** Real Forge command dispatch and canonical file/cold-restart checks; no connected client or force deployment. */
@Mod(modid="campaignverification", name="Disposable campaign lifecycle verification", version="1", dependencies="required-after:kome")
public final class CampaignLifecycleVerification {
    private int ticks;
    private boolean ran;
    private final StringBuilder evidence = new StringBuilder();
    private static final UUID PLAYER = UUID.fromString("8b78f403-e7b8-47c2-8c4d-caa502e66d14");
    @Mod.EventHandler public void started(FMLServerStartedEvent event) { FMLCommonHandler.instance().bus().register(this); }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ran || ++ticks < 40) return;
        ran = true; String phase = "unknown";
        MinecraftServer server = MinecraftServer.getServer();
        try {
            phase = new String(Files.readAllBytes(Paths.get("verification-phase.txt")), StandardCharsets.UTF_8).trim();
            WorldServer world = server.worldServers[0]; KOMEWorldData data = KOMEWorldData.get(world);
            check(data.isIntegratedRootInitialized(), "normal server lifecycle initialized canonical root");
            Player player = new Player(world);
            if ("first".equals(phase)) first(server, world, data, player); else restart(data);
            dispatch(server, player, "kome diagnostics daily status", true, "Status=");
            dispatch(server, player, "kome diagnostics muster gondor", true, "No reserve call");
            dispatch(server, player, "kome diagnostics governance " + PLAYER, true, "SUBMITTED");
            dispatch(server, player, "kome audit list 1", true, "Audit page");
            player.staff = false;
            dispatch(server, player, "kome diagnostics daily status", false, "");
            dispatch(server, player, "governance status W1", true, "SUBMITTED");
            check(server.getCommandManager().executeCommand(server, "save-all") == 1, "actual save-all command completed");
            KOMEWorldCheckpoint.forWorld(data, world).save(data);
            evidence.append("PASS phase=").append(phase).append("; real Forge commands and canonical save; no connected client or muster force spawned\n");
        } catch (Throwable failure) {
            evidence.append("FAIL phase=").append(phase).append(' ').append(failure).append('\n');
            failure.printStackTrace();
        } finally {
            try { Files.write(Paths.get("campaign-" + phase + ".txt"), evidence.toString().getBytes(StandardCharsets.UTF_8)); }
            catch (Exception failure) { failure.printStackTrace(); }
            server.initiateShutdown();
        }
    }

    private void first(MinecraftServer server, WorldServer world, KOMEWorldData data, Player player) throws Exception {
        long now = System.currentTimeMillis();
        data.warSeason.recordLegalConflict(now, -1L);
        KOMEWar war = new KOMEWar(); war.id = "W1"; war.initiatingFaction = "gondor"; war.defendingFaction = "mordor";
        war.sideOneFactions.add("gondor"); war.sideOneFactions.add("rohan"); war.sideTwoFactions.add("mordor");
        war.lastActivePressureAtMillis = now; war.createdAtMillis = now; data.wars.put(war.id, war);
        data.lastKnownPlayerFactions.put(PLAYER, "gondor");
        data.conquestTiles.get(KOMEFactionCapitalService.getCapitalTileId(data, "gondor")).claim("mordor", now);
        KOMEFactionDefeatService.reconcile(data, now);
        check(data.warSeason.isFactionDefeated("gondor"), "actual live-state defeat service published result");
        check(KOMEGovernanceService.record(data, PLAYER, "W1") != null, "defeat captured player origin");
        dispatch(server, player, "governance submit W1", true, "SUBMITTED");
        check(!KOMEGovernanceService.militaryAction(data, PLAYER, "rohan").allowed, "Submitted service participation denied");
        LOTRFactionRelations.overrideRelations(KOMEAlliance.findLotrFaction("gondor"), KOMEAlliance.findLotrFaction("rohan"), LOTRFactionRelations.Relation.ALLY);
        dispatch(server, player, "governance exile W1 rohan", true, "EXILED");
        check(KOMEGovernanceService.militaryAction(data, PLAYER, "rohan").allowed, "Exiled host service participation allowed");
        check("gondor".equals(data.getPlayerFactionKey(PLAYER)), "native faction not spoofed");
        dispatch(server, player, "governance submit W1", true, "SUBMITTED");

        KOMEPlayerBuild build = new KOMEPlayerBuild(); build.id = "B-CAMPAIGN-VERIFY"; build.type = KOMEBuildType.NORMAL;
        build.tileId = KOMEFactionCapitalService.getCapitalTileId(data, "rohan"); build.populationFaction = "rohan";
        KOMEBuildContribution hours = new KOMEBuildContribution(); hours.id = "H-VERIFY"; hours.status = KOMEBuildContribution.APPROVED; hours.centiHours = 10000;
        build.contributions.add(hours); data.builds.put(build.id, build);
        Instant instant = Instant.ofEpochMilli(now);
        KOMEPopulationPayoutRuntime runtime = new KOMEPopulationPayoutRuntime(); check(runtime.onStartup(data, instant).success, "real population startup anchored");
        KOMEWorldCheckpoint checkpoint = KOMEWorldCheckpoint.forWorld(data, world);
        KOMEDailyCoordinator coordinator = new KOMEDailyCoordinator(); coordinator.startSession(data, instant); coordinator.process(data, checkpoint);
        Instant due = KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis));
        KOMEDailyCoordinator atBoundary = new KOMEDailyCoordinator(java.time.Clock.fixed(due, java.time.ZoneOffset.UTC));
        check(atBoundary.process(data, checkpoint).handled, "available real daily batch executed");
        check("COMPLETE".equals(data.dailyJournal.status()), "daily completion checkpoint exists");
        check(build.developedNativeCentiHours == 100 && KOMEPopulationService.getAvailablePopulationCenti(data, "rohan") == 10, "development then payout applied once");
        int before = world.loadedEntityList.size(), units = 0, mounted = 0;
        for (String faction : KOMEAlliance.allFactionKeys()) for (KOMEMusterRoster.Unit unit : new KOMEMusterNativeRoster(world).resolve(faction)) {
            units++; if (unit.mounted()) mounted++;
        }
        check(world.loadedEntityList.size() == before, "native roster probes did not spawn entities");
        evidence.append("Native factory/registry probes: units=").append(units).append("; mounted entries=").append(mounted).append(" (not deployment)\n");
    }

    private void restart(KOMEWorldData data) {
        KOMEPlayerGovernance record = KOMEGovernanceService.record(data, PLAYER, "W1");
        check(record != null && record.state == KOMEPlayerGovernance.State.SUBMITTED && "gondor".equals(record.origin), "cold startup retained governance and origin");
        check(data.dailyJournal.lastComplete() >= 0, "cold startup retained completed daily boundary");
        check(data.builds.get("B-CAMPAIGN-VERIFY").developedNativeCentiHours == 100, "cold startup did not replay development");
        check(KOMEPopulationService.getAvailablePopulationCenti(data, "rohan") == 10, "cold startup did not duplicate payout");
        int summaries = 0; for (KOMEAuditEntry entry : data.centralAudit) if ("DAILY".equals(entry.domain) && "SUMMARY".equals(entry.action)) summaries++;
        check(summaries == 1, "one durable daily summary receipt after cold restart");
        check(!KOMEGovernanceService.militaryAction(data, PLAYER, "rohan").allowed, "cold startup retained Submitted restriction");
    }
    private void dispatch(MinecraftServer server, Player player, String command, boolean success, String expected) {
        player.messages.clear(); int result = server.getCommandManager().executeCommand(player, command);
        check((result == 1) == success, "dispatch " + command + " result=" + result + " messages=" + player.messages);
        if (!expected.isEmpty()) check(player.messages.toString().contains(expected), "command response contains " + expected);
    }
    private void check(boolean condition, String text) { if (!condition) throw new IllegalStateException(text); evidence.append("OK ").append(text).append('\n'); }
    private static final class Player extends FakePlayer {
        boolean staff = true;
        final List<String> messages = new ArrayList<String>();
        Player(WorldServer world) { super(world, new GameProfile(PLAYER, "CampaignVerify")); }
        @Override public boolean canCommandSenderUseCommand(int level, String name) { return staff || level == 0; }
        @Override public void addChatMessage(IChatComponent message) { messages.add(message.getUnformattedText()); }
        @Override public void addChatComponentMessage(IChatComponent message) { messages.add(message.getUnformattedText()); }
    }
}
