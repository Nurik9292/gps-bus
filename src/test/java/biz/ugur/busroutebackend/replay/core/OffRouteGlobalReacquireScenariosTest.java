package biz.ugur.busroutebackend.replay.core;

import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.GeometryFixture;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.PredictionModel;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OffRouteGlobalReacquireScenariosTest {

    private static final GeometryFixture G61_0 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir0.json");
    private static final GeometryFixture G61_1 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir1.json");
    private static final CoreConfig CFG = CoreConfig.defaults();
    private static final double P_LAT = 38.058718;
    private static final double P_LON = 58.175667;
    private static final double DEG_PER_METER_LAT = 1.0 / 111320.0;

    private static GpsFix fixOn(GeometryFixture g, double s, double speedKmh, long tSec) {
        double[] p = g.pointAtS(s);
        return fixAt(p[0], p[1], speedKmh, tSec);
    }

    private static GpsFix fixAt(double lat, double lon, double speedKmh, long tSec) {
        return new GpsFix("test-veh", "TEST 01", "61", lat, lon,
                speedKmh, 0.0, speedKmh > 1, Instant.ofEpochSecond(tSec), 0,
                0.8, 12, 0.0, Instant.ofEpochSecond(tSec));
    }

    private static GpsFix fixOffAxis(GeometryFixture g, double s, double sideMeters,
                                     double speedKmh, long tSec) {
        double[] p = g.pointAtS(s);
        return fixAt(p[0] + sideMeters * DEG_PER_METER_LAT, p[1], speedKmh, tSec);
    }

    private record Step(String mode, long tripId, double s) {}

    private static List<Step> drive(MotionFilterCore core, RouteTopology topo, List<GpsFix> fixes) {
        List<Step> out = new ArrayList<>();
        for (GpsFix fx : fixes) {
            PredictionModel.Estimate e = core.onFix(fx, topo);
            out.add(new Step(e.mode(), core.tripId(), e.s()));
        }
        return out;
    }

    private static long enterOffRouteAtAbout3000(MotionFilterCore core, RouteTopology topo) {
        List<GpsFix> ride = new ArrayList<>();
        long t = 1000;
        for (double s = 2000; s <= 3000; s += 60, t += 5) {
            ride.add(fixOn(G61_1, s, 40.0, t));
        }
        List<Step> rideSteps = drive(core, topo, ride);
        assertThat(rideSteps.get(rideSteps.size() - 1).mode()).isEqualTo("TRACKING");
        List<GpsFix> away = new ArrayList<>();
        for (int i = 0; i < 8; i++, t += 5) {
            away.add(fixOffAxis(G61_1, 3000, 2000.0, 40.0, t));
        }
        List<Step> awaySteps = drive(core, topo, away);
        assertThat(awaySteps.get(awaySteps.size() - 1).mode()).isEqualTo("OFF_ROUTE");
        return t;
    }

    @Test
    void alongLeaderReturnFarFromFrozenXReacquiresWithoutTripIncrement() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        long t = enterOffRouteAtAbout3000(core, topo);
        long tripBefore = core.tripId();
        int dirBefore = core.direction();

        List<GpsFix> back = new ArrayList<>();
        for (double s = 10000; s <= 10500; s += 100, t += 5) {
            back.add(fixOn(G61_1, s, 40.0, t));
        }
        List<Step> steps = drive(core, topo, back);

        assertThat(steps).anyMatch(st -> st.mode().equals("RECOVERING"));
        int recIdx = steps.stream().map(Step::mode).toList().indexOf("RECOVERING");
        assertThat(recIdx + 1)
                .as("REACQ на K-м подряд on-line фиксе (K=%d)", CFG.kOffRouteReacq())
                .isEqualTo(CFG.kOffRouteReacq());
        assertThat(steps.get(recIdx).s())
                .as("reinitAt(s_glob последнего фикса стрика)")
                .isCloseTo(10000 + 100 * (CFG.kOffRouteReacq() - 1), org.assertj.core.data.Offset.offset(30.0));
        assertThat(core.tripId()).isEqualTo(tripBefore);
        assertThat(core.direction()).isEqualTo(dirBefore);
        assertThat(steps).noneMatch(st -> st.mode().equals("NEW_TRIP"));
    }

    @Test
    void oneFixShortOfKStaysOffRoute() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        long t = enterOffRouteAtAbout3000(core, topo);
        long tripBefore = core.tripId();

        List<GpsFix> back = new ArrayList<>();
        for (int i = 0; i < CFG.kOffRouteReacq() - 1; i++, t += 5) {
            back.add(fixOn(G61_1, 10000 + i * 100, 40.0, t));
        }
        List<Step> steps = drive(core, topo, back);

        assertThat(steps).allMatch(st -> st.mode().equals("OFF_ROUTE"));
        assertThat(core.tripId()).isEqualTo(tripBefore);
    }

    @Test
    void brokenStreakResetsAndStaysOffRoute() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        long t = enterOffRouteAtAbout3000(core, topo);
        long tripBefore = core.tripId();

        List<GpsFix> ragged = new ArrayList<>();
        for (int i = 0; i < 3; i++, t += 5) {
            ragged.add(fixOn(G61_1, 10000 + i * 100, 40.0, t));
        }
        ragged.add(fixOffAxis(G61_1, 10200, 2000.0, 40.0, t));
        t += 5;
        for (int i = 3; i < 5; i++, t += 5) {
            ragged.add(fixOn(G61_1, 10000 + i * 100, 40.0, t));
        }
        List<Step> steps = drive(core, topo, ragged);

        assertThat(steps).allMatch(st -> st.mode().equals("OFF_ROUTE"));
        assertThat(core.tripId()).isEqualTo(tripBefore);
    }

    @Test
    void againstLeaderMotionResolvesThroughBankChannelWithItsGates() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        long t = enterOffRouteAtAbout3000(core, topo);
        long tripBefore = core.tripId();
        int dirBefore = core.direction();

        List<GpsFix> against = new ArrayList<>();
        for (double s = 18500; s >= 16700; s -= 60, t += 5) {
            against.add(fixOn(G61_1, s, 40.0, t));
        }
        List<Step> steps = drive(core, topo, against);

        assertThat(steps).anyMatch(st -> st.mode().equals("NEW_TRIP"));
        assertThat(core.direction()).isNotEqualTo(dirBefore);
        assertThat(core.tripId()).isEqualTo(tripBefore + 1);
    }

    @Test
    void returnNearFrozenXTakesLegacyCorridorExit() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        long t = enterOffRouteAtAbout3000(core, topo);
        long tripBefore = core.tripId();

        List<GpsFix> near = new ArrayList<>();
        for (double s = 3050; s <= 3230; s += 60, t += 5) {
            near.add(fixOn(G61_1, s, 40.0, t));
        }
        List<Step> steps = drive(core, topo, near);

        assertThat(steps.get(steps.size() - 1).mode()).isEqualTo("TRACKING");
        assertThat(steps).noneMatch(st -> st.mode().equals("RECOVERING"));
        assertThat(steps).noneMatch(st -> st.mode().equals("NEW_TRIP"));
        assertThat(core.tripId()).isEqualTo(tripBefore);
    }

    @Test
    void freezeReanchorOnSameAxisUnaffectedByReacquireChannel() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        List<GpsFix> ride = new ArrayList<>();
        long t = 1000;
        for (double s = 8000; s <= 9000; s += 60, t += 5) {
            ride.add(fixOn(G61_1, s, 40.0, t));
        }
        drive(core, topo, ride);
        long tripBefore = core.tripId();

        t += 180;
        List<GpsFix> postGap = new ArrayList<>();
        for (double s = 11000; s <= 12000; s += 60, t += 5) {
            postGap.add(fixOn(G61_1, s, 40.0, t));
        }
        List<Step> steps = drive(core, topo, postGap);

        assertThat(core.tripId()).isEqualTo(tripBefore);
        assertThat(steps.get(steps.size() - 1).mode()).isEqualTo("TRACKING");
        assertThat(steps).noneMatch(st -> st.mode().equals("OFF_ROUTE"));
        assertThat(steps).noneMatch(st -> st.mode().equals("NEW_TRIP"));
    }

    @Test
    void stationaryOnLineVehicleStaysSilent() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        long t = enterOffRouteAtAbout3000(core, topo);
        long tripBefore = core.tripId();

        List<GpsFix> parked = new ArrayList<>();
        for (int i = 0; i < 10; i++, t += 5) {
            parked.add(fixOn(G61_1, 10000, 0.0, t));
        }
        List<Step> steps = drive(core, topo, parked);

        assertThat(steps).allMatch(st -> st.mode().equals("OFF_ROUTE"));
        assertThat(core.tripId()).isEqualTo(tripBefore);
    }

    @Test
    void activeCityPinSuppressesReacquireWhileSameInputWithoutPinFires() {
        double[] deep = G61_0.pointAtS(G61_0.totalMeters());
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0)
                .withCityZone(new RouteTopology.CityZone(deep[0], deep[1], P_LAT, P_LON));

        MotionFilterCore pinned = new MotionFilterCore(CFG);
        pinned.reset();
        long tPinned = rideD1ThenStandOffAxis(pinned, topo, 0);
        assertThat(pinned.cityPinActive()).as("пин установлен разгоном через D").isTrue();
        List<Step> pinnedSteps = driveOnLineRunAlongD1(pinned, topo, tPinned);
        assertThat(pinned.cityPinActive()).as("пин жив весь стрик").isTrue();
        assertThat(pinnedSteps)
                .as("Δ2: пин активен + K on-line — канал молчит")
                .noneMatch(st -> st.mode().equals("RECOVERING"));
        assertThat(pinnedSteps.get(pinnedSteps.size() - 1).mode()).isEqualTo("OFF_ROUTE");

        MotionFilterCore free = new MotionFilterCore(CFG);
        free.reset();
        long tFree = rideD1ThenStandOffAxis(free, topo, 1400);
        assertThat(free.cityPinActive()).isFalse();
        List<Step> freeSteps = driveOnLineRunAlongD1(free, topo, tFree);
        assertThat(freeSteps)
                .as("тот же on-line вход без пина — REACQ по ходу срабатывает")
                .anyMatch(st -> st.mode().equals("RECOVERING"));
    }

    private static long rideD1ThenStandOffAxis(MotionFilterCore core, RouteTopology topo,
                                               double sStart) {
        List<GpsFix> ride = new ArrayList<>();
        long t = 1000;
        if (sStart == 0) {
            for (double s = 0; s <= 270; s += 30, t += 5) {
                ride.add(fixOn(G61_1, s, 40.0, t));
            }
            for (double s = 330; s <= 960; s += 60, t += 5) {
                ride.add(fixOn(G61_1, s, 40.0, t));
            }
        } else {
            for (double s = sStart; s <= sStart + 960; s += 60, t += 5) {
                ride.add(fixOn(G61_1, s, 40.0, t));
            }
        }
        drive(core, topo, ride);
        List<GpsFix> away = new ArrayList<>();
        for (int i = 0; i < 8; i++, t += 5) {
            away.add(fixAt(38.049059, 58.161175, 40.0, t));
        }
        List<Step> awaySteps = drive(core, topo, away);
        assertThat(awaySteps.get(awaySteps.size() - 1).mode()).isEqualTo("OFF_ROUTE");
        return t;
    }

    private static List<Step> driveOnLineRunAlongD1(MotionFilterCore core, RouteTopology topo,
                                                    long t) {
        List<GpsFix> run = new ArrayList<>();
        for (double s = 340; s <= 640; s += 60, t += 5) {
            run.add(fixOn(G61_1, s, 4.0, t));
        }
        return drive(core, topo, run);
    }
}
