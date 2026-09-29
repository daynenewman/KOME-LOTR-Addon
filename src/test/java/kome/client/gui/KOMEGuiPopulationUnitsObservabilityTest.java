package kome.client.gui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KOMEGuiPopulationUnitsObservabilityTest {
    @Test public void listUsesStableUuidPrefix() {
        assertEquals("12345678", KOMEGuiPopulationUnits.shortUnitId(
            "12345678-1234-1234-1234-123456789abc"));
        assertEquals("short-id", KOMEGuiPopulationUnits.shortUnitId("short-id"));
        assertEquals("", KOMEGuiPopulationUnits.shortUnitId(null));
    }
}
