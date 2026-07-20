package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.GeometryFixture;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.RouteLine;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InitLateralGateContractTest {

    private static final Instant T0 = Instant.parse("2026-07-20T05:00:00Z");
    private static final double FAR_OFFSET_DEG = 0.08;
    private static final double TERMINAL_PARKING_OFFSET_DEG = 0.0004;

    private RouteTopology topology() throws Exception {
        GeometryFixture d0 = GeometryFixture.loadClasspath("/fixtures/geometry/route-57-dir0.json");
        GeometryFixture d1 = GeometryFixture.loadClasspath("/fixtures/geometry/route-57-dir1.json");
        return RouteTopology.thereAndBack(d0, d1);
    }

    private GpsFix fix(double lat, double lon, double speedKmh, double course, Instant ts) {
        return new GpsFix("veh-init-gate", "0000 TST", "57",
                lat, lon, speedKmh, course, speedKmh > 2, ts, 0, null, null, null, ts);
    }

    @Test
    void firstFixFarFromAxisInitializesOffRouteNotPhantomTracking() throws Exception {
        RouteTopology topo = topology();
        RouteLine g = topo.first();
        double[] onAxis = g.pointAtS(10000);
        MotionFilterCore core = new MotionFilterCore(CoreConfig.defaults());
        core.reset();

        var first = core.onFix(fix(onAxis[0] + FAR_OFFSET_DEG, onAxis[1], 0, 0, T0), topo);
        assertThat(first.mode())
                .as("стоянка в ~9 км от оси: init не имеет права объявлять ведение")
                .isEqualTo("OFF_ROUTE");

        Instant t = T0;
        String mode = first.mode();
        for (int i = 0; i < 6; i++) {
            t = t.plusSeconds(20);
            mode = core.onFix(fix(onAxis[0] + FAR_OFFSET_DEG, onAxis[1], 0, 0, t), topo).mode();
        }
        assertThat(mode)
                .as("стоячие фиксы вдали от оси не выводят из OFF_ROUTE")
                .isEqualTo("OFF_ROUTE");
    }

    @Test
    void firstFixOnAxisInitializesTrackingAsBefore() throws Exception {
        RouteTopology topo = topology();
        RouteLine g = topo.first();
        double[] onAxis = g.pointAtS(10000);
        MotionFilterCore core = new MotionFilterCore(CoreConfig.defaults());
        core.reset();

        core.onFix(fix(onAxis[0], onAxis[1], 30, 0, T0), topo);
        var second = core.onFix(fix(g.pointAtS(10150)[0], g.pointAtS(10150)[1], 30, 0,
                T0.plusSeconds(20)), topo);
        assertThat(second.mode()).isEqualTo("TRACKING");
    }

    @Test
    void restartWithBusParkedAtTerminalLoopSmallLateralIsNotOffRoute() throws Exception {
        RouteTopology topo = topology();
        RouteLine g = topo.first();
        double[] terminal = g.pointAtS(g.totalMeters());
        MotionFilterCore core = new MotionFilterCore(CoreConfig.defaults());
        core.reset();

        Instant t = T0;
        String mode = core.onFix(fix(terminal[0] + TERMINAL_PARKING_OFFSET_DEG, terminal[1],
                0, 0, t), topo).mode();
        assertThat(mode)
                .as("стоянка в ~45 м от конца оси (разворотная петля) — допустимая инициализация")
                .isNotEqualTo("OFF_ROUTE");
        for (int i = 0; i < 10; i++) {
            t = t.plusSeconds(20);
            mode = core.onFix(fix(terminal[0] + TERMINAL_PARKING_OFFSET_DEG, terminal[1],
                    0, 0, t), topo).mode();
            assertThat(mode)
                    .as("стоячие фиксы у конечной не должны сваливаться в OFF_ROUTE")
                    .isNotEqualTo("OFF_ROUTE");
        }
    }

    @Test
    void busLeavingFarParkingIsPickedUpByExistingReacquireChannel() throws Exception {
        RouteTopology topo = topology();
        RouteLine g = topo.first();
        double[] parking = g.pointAtS(10000);
        MotionFilterCore core = new MotionFilterCore(CoreConfig.defaults());
        core.reset();

        Instant t = T0;
        core.onFix(fix(parking[0] + FAR_OFFSET_DEG, parking[1], 0, 0, t), topo);
        for (int i = 0; i < 3; i++) {
            t = t.plusSeconds(20);
            core.onFix(fix(parking[0] + FAR_OFFSET_DEG, parking[1], 0, 0, t), topo);
        }

        var last = core.onFix(fix(parking[0] + FAR_OFFSET_DEG, parking[1], 0, 0, t), topo);
        double s = 10000;
        for (int i = 0; i < 20; i++) {
            t = t.plusSeconds(20);
            s += 220;
            double[] p = g.pointAtS(s);
            double[] pNext = g.pointAtS(Math.min(s + 50, g.totalMeters()));
            double course = Math.toDegrees(Math.atan2(pNext[1] - p[1], pNext[0] - p[0]));
            last = core.onFix(fix(p[0], p[1], 40, course, t), topo);
        }
        assertThat(last.mode())
                .as("после выезда на ось существующий глобальный reacq обязан подхватить ведение")
                .isNotEqualTo("OFF_ROUTE");
        assertThat(Math.abs(last.s() - s))
                .as("x̂ после подхвата ведёт борт, а не фантом")
                .isLessThan(500);
    }
}
