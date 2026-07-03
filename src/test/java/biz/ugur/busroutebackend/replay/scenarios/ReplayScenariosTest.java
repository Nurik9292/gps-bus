package biz.ugur.busroutebackend.replay.scenarios;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.InvariantAssertions;
import biz.ugur.busroutebackend.replay.PredictionModel;
import biz.ugur.busroutebackend.replay.ReplayHarness;
import biz.ugur.busroutebackend.replay.models.GeometricSnapModel;
import biz.ugur.busroutebackend.replay.models.HoldLastModel;
import biz.ugur.busroutebackend.replay.synth.SyntheticScenario;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayScenariosTest {

    private static final GeometryFixture ROUTE_8_FWD =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-8-dir0.json");

    private static final double CRUISE_MS = 12.5;
    private static final double ACCEL_MS2 = 1.0;
    private static final double BIAS_METERS = 20.0;

    @Test
    void determinismSameInputSameHash() {
        SyntheticScenario.Track track = SyntheticScenario.departureRamp(
                ROUTE_8_FWD, SyntheticScenario.Params.defaults(42, "8", 0),
                2000, CRUISE_MS, ACCEL_MS2, 60, 600);
        ReplayHarness.Result r1 = ReplayHarness.run(new GeometricSnapModel(), ROUTE_8_FWD,
                track.fixes(), track.truth(), 300);
        ReplayHarness.Result r2 = ReplayHarness.run(new GeometricSnapModel(), ROUTE_8_FWD,
                track.fixes(), track.truth(), 300);
        assertThat(r1.outputSha256()).isEqualTo(r2.outputSha256());
    }

    @Test
    void sanityZeroNoiseSnapMatchesTruth() {
        SyntheticScenario.Params noiseless = new SyntheticScenario.Params(
                1, 7.0, 0.0, 5.0,
                java.time.Instant.parse("2026-07-03T06:00:00Z"), "veh-syn-1", "SYN 1", "8", 0);
        SyntheticScenario.Track track = SyntheticScenario.departureRamp(
                ROUTE_8_FWD, noiseless, 2000, CRUISE_MS, ACCEL_MS2, 60, 600);
        ReplayHarness.Result r = ReplayHarness.run(new GeometricSnapModel(), ROUTE_8_FWD,
                track.fixes(), track.truth(), 300);
        double epsilon = 1.0;
        assertThat(r.position().maxAbsError())
                .as("zero noise + geometric snap must reproduce s_true within eps=%.1f m", epsilon)
                .isLessThanOrEqualTo(epsilon);
    }

    @Test
    void scenario15SystematicBiasBaseline() {
        SyntheticScenario.Track track = SyntheticScenario.cruiseWithForwardSnapBias(
                ROUTE_8_FWD, SyntheticScenario.Params.defaults(15, "8", 0),
                1000, CRUISE_MS, BIAS_METERS, 900);

        ReplayHarness.Result snap = ReplayHarness.run(new GeometricSnapModel(), ROUTE_8_FWD,
                track.fixes(), track.truth(), 300);

        InvariantAssertions.assertInv2SWithinLine(snap.samples(), ROUTE_8_FWD.totalMeters());
        InvariantAssertions.assertErrorDoesNotGrowUnbounded(snap.samples(), 10, 3.0);
        assertThat(snap.position().meanAbsError()).isBetween(BIAS_METERS * 0.5, BIAS_METERS * 1.5);
        System.out.printf("SC15 baseline snap: meanAbs=%.1fm p95=%.1fm maxStep=%.1fm meanNEES=%.2f hash=%s%n",
                snap.position().meanAbsError(), snap.position().p95AbsError(),
                snap.position().maxStep(), snap.consistency().meanNees(), snap.outputSha256().substring(0, 12));
    }

    @Test
    void scenario16DepartureRampBaseline() {
        long[] seeds = {161, 162, 163, 164, 165};
        double sumNees = 0;
        ReplayHarness.Result last = null;
        for (long seed : seeds) {
            SyntheticScenario.Track track = SyntheticScenario.departureRamp(
                    ROUTE_8_FWD, SyntheticScenario.Params.defaults(seed, "8", 0),
                    2000, CRUISE_MS, ACCEL_MS2, 120, 900);
            last = ReplayHarness.run(new GeometricSnapModel(), ROUTE_8_FWD,
                    track.fixes(), track.truth(), 300);
            InvariantAssertions.assertInv2SWithinLine(last.samples(), ROUTE_8_FWD.totalMeters());
            sumNees += last.consistency().meanNees();
        }
        double mcMeanNees = sumNees / seeds.length;
        System.out.printf("SC16 baseline snap (MC x%d): meanAbs=%.1fm p95=%.1fm meanNEES(MC)=%.2f hash=%s%n",
                seeds.length, last.position().meanAbsError(), last.position().p95AbsError(),
                mcMeanNees, last.outputSha256().substring(0, 12));
        assertThat(last.position().meanAbsError()).isLessThan(20.0);
    }

    @Test
    void scenario16HoldLastLagsAsExpectedNegativeControl() {
        SyntheticScenario.Track track = SyntheticScenario.departureRamp(
                ROUTE_8_FWD, SyntheticScenario.Params.defaults(99, "8", 0),
                2000, CRUISE_MS, ACCEL_MS2, 60, 600);
        ReplayHarness.Result hold = ReplayHarness.run(new HoldLastModel(), ROUTE_8_FWD,
                track.fixes(), track.truth(), 300);
        assertThat(hold.position().maxAbsError())
                .as("hold-last must accumulate large lag on departure (negative control)")
                .isGreaterThan(1000.0);
    }

    @Disabled("Scenario-01..14: нужен реальный сегмент корпуса кампании записи и/или ядро модели v3.1")
    @Test
    void scenario01IdealRoute() {}
}
