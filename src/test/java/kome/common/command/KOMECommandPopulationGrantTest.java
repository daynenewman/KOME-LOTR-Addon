package kome.common.command;

import java.util.List;
import kome.common.KOMEAccessFixture;
import kome.common.data.KOMEAuditEntry;
import kome.common.data.KOMEPopulationService;
import net.minecraft.command.WrongUsageException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMECommandPopulationGrantTest {
    private KOMEAccessFixture fixture;
    private KOMECommandPopulation command;

    @Before public void setup() throws Exception {
        fixture = new KOMEAccessFixture();
        command = new KOMECommandPopulation();
    }

    @Test public void ordinaryPlayerCannotGrantOrDiscoverAdministrativeMutation() {
        expectRejected("grant", "gondor", "100");
        assertEquals(0L, KOMEPopulationService.getAvailablePopulationCenti(
            fixture.data, "gondor"));
        assertTrue(fixture.data.centralAudit.isEmpty());
        assertFalse(command.addTabCompletionOptions(
            fixture.player, new String[] {"gr"}).contains("grant"));
    }

    @Test public void operatorGrantUsesExactCanonicalBankAndAuditsResult() {
        fixture.player.operator = true;
        KOMEPopulationService.grantCenti(fixture.data, "gondor", 125L);
        fixture.data.centralAudit.clear();
        fixture.data.setDirty(false);

        command.processCommand(fixture.player,
            new String[] {"grant", "Gondor", "100.25"});

        assertEquals(10150L, KOMEPopulationService.getAvailablePopulationCenti(
            fixture.data, "gondor"));
        assertTrue(fixture.data.isDirty());
        assertEquals(1, fixture.data.centralAudit.size());
        KOMEAuditEntry audit = fixture.data.centralAudit.get(0);
        assertEquals("POPULATION", audit.domain);
        assertEquals("ADMIN_GRANT", audit.action);
        assertEquals("gondor", audit.subject);
        assertTrue(audit.details.contains("grantedCenti=10025"));
        assertTrue(audit.details.contains("availableCenti=10150"));
        assertTrue(fixture.player.messages.toString().contains("100.25"));
        assertTrue(fixture.player.messages.toString().contains("101.5"));
    }

    @Test public void invalidFactionAndNonpositiveMalformedOrOverflowAmountsAreAtomic() {
        fixture.player.operator = true;
        for (String[] args : new String[][] {
                {"grant", "not_a_faction", "1"},
                {"grant", "gondor", "0"},
                {"grant", "gondor", "-1"},
                {"grant", "gondor", "one"},
                {"grant", "gondor", "1.001"},
                {"grant", "gondor", "92233720368547758.08"}}) {
            expectRejected(args);
        }
        assertEquals(0L, KOMEPopulationService.getAvailablePopulationCenti(
            fixture.data, "gondor"));
        assertTrue(fixture.data.factionPopulations.isEmpty());
        assertTrue(fixture.data.centralAudit.isEmpty());
    }

    @Test public void existingBalanceOverflowIsRejectedWithoutAuditOrBalanceChange() {
        fixture.player.operator = true;
        KOMEPopulationService.grantCenti(fixture.data, "gondor", Long.MAX_VALUE);
        fixture.data.centralAudit.clear();
        expectRejected("grant", "gondor", "0.01");
        assertEquals(Long.MAX_VALUE,
            KOMEPopulationService.getAvailablePopulationCenti(fixture.data, "gondor"));
        assertTrue(fixture.data.centralAudit.isEmpty());
    }

    @Test public void operatorFactionCompletionUsesSupportedCanonicalKeys() {
        fixture.player.operator = true;
        List values = command.addTabCompletionOptions(
            fixture.player, new String[] {"grant", "gon"});
        assertTrue(values.contains("gondor"));
        assertFalse(values.contains("wanderer"));
    }

    private void expectRejected(String... args) {
        try {
            command.processCommand(fixture.player, args);
            fail(java.util.Arrays.toString(args));
        } catch (WrongUsageException expected) {
            // Expected fail-closed command rejection.
        }
    }
}
