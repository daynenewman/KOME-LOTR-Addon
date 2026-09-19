package com.enovak.lotrmoremobs.siege.repair;

import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GateOpeningDirection;
import com.enovak.lotrmoremobs.siege.gate.GateOrientation;
import com.enovak.lotrmoremobs.siege.gate.GatePartData;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import kome.common.data.KOMEBuildContribution;
import kome.common.data.KOMEBuildType;
import kome.common.data.KOMEGateSizeCalculator;
import kome.common.data.KOMEPhysicalGateInspection;
import kome.common.data.KOMEPlayerBuild;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GateManagementKOMEInitialHealthTest {
    @Test public void fiftyApprovedHoursAtBaselineInitializesFiveThousandHp() {
        GateManagementManager.InitialLinkHealth result =
            GateManagementManager.calculateInitialLinkHealth(defensiveBuild(5000L),
                inspection(5, 5), OptionalDouble.of(100.0D),
                KOMEGateSizeCalculator.Parameters.defaults());

        assertTrue(result.isAvailable());
        assertEquals(5000, result.getMaxHp());
    }

    @Test public void exactCentiHoursAndSizeMultiplierFeedPhysicalIntegerRounding() {
        GateManagementManager.InitialLinkHealth result =
            GateManagementManager.calculateInitialLinkHealth(defensiveBuild(150L),
                inspection(5, 5), OptionalDouble.of(10.0D),
                KOMEGateSizeCalculator.Parameters.defaults());

        assertTrue(result.isAvailable());
        assertEquals(15, result.getMaxHp());
    }

    @Test public void unavailableConfiguredRateDoesNotSubstituteDefault() {
        GateManagementManager.InitialLinkHealth result =
            GateManagementManager.calculateInitialLinkHealth(defensiveBuild(5000L),
                inspection(5, 5), OptionalDouble.empty(),
                KOMEGateSizeCalculator.Parameters.defaults());

        assertFalse(result.isAvailable());
        assertEquals(0, result.getMaxHp());
        assertTrue(result.getMessage().contains("unavailable/TBD"));
    }

    @Test public void nonReliableDimensionsCannotCreatePartiallyInitializedLink() {
        KOMEPhysicalGateInspection.Result irregular = inspection(5, 5, false);
        GateManagementManager.InitialLinkHealth result =
            GateManagementManager.calculateInitialLinkHealth(defensiveBuild(5000L),
                irregular, OptionalDouble.of(100.0D),
                KOMEGateSizeCalculator.Parameters.defaults());

        assertFalse(result.isAvailable());
        assertTrue(result.getMessage().contains("reliable physical gate dimensions"));
    }

    private static KOMEPlayerBuild defensiveBuild(long approvedCentiHours) {
        KOMEPlayerBuild build = new KOMEPlayerBuild();
        build.id = "B1";
        build.type = KOMEBuildType.DEFENSIVE;
        build.active = true;
        KOMEBuildContribution contribution = new KOMEBuildContribution();
        contribution.id = "H1";
        contribution.centiHours = approvedCentiHours;
        contribution.status = KOMEBuildContribution.APPROVED;
        build.contributions.add(contribution);
        return build;
    }

    private static KOMEPhysicalGateInspection.Result inspection(int width, int height) {
        return inspection(width, height, true);
    }

    private static KOMEPhysicalGateInspection.Result inspection(int width, int height,
            boolean filled) {
        List<GatePartData> parts = new ArrayList<GatePartData>();
        for (int y = 0; y < height; y++) {
            for (int x = 1; x <= width; x++) {
                if (!filled && x == width && y == height - 1) continue;
                int center = (width + 1) / 2;
                GateLeaf leaf = x < center ? GateLeaf.LEFT
                    : x > center ? GateLeaf.RIGHT : GateLeaf.SPLIT_CENTER;
                parts.add(new GatePartData(x, y, 0, leaf));
            }
        }
        return KOMEPhysicalGateInspection.inspect(new KOMEPhysicalGateInspection.Snapshot(
            0, 10, 64, 20, UUID.randomUUID(), 1, true, false, true,
            GateOrientation.WIDTH_X, GateOpeningDirection.FORWARD,
            new GateHinge(1, 0), new GateHinge(width, 0), parts));
    }
}
