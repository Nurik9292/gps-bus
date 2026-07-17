package biz.ugur.busroutebackend.replay.core;

import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import biz.ugur.busroutebackend.prediction.core.GeometryFixture;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalCityDepartureScenariosTest {

    private static final GeometryFixture G61_0 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir0.json");
    private static final GeometryFixture G61_1 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir1.json");
    private static final CoreConfig CFG = CoreConfig.defaults();

    private static GpsFix fixOn(GeometryFixture g, double s, double speedKmh, long tSec) {
        double[] p = g.pointAtS(s);
        return new GpsFix("test-veh", "TEST 01", g.routeNumber(), p[0], p[1],
                speedKmh, 0.0, speedKmh > 1, Instant.ofEpochSecond(tSec), g.direction(),
                0.8, 12, 0.0, Instant.ofEpochSecond(tSec));
    }

    private record Arrived(MotionFilterCore core, RouteTopology topo, long tSec) {
    }

    private static Arrived arriveAndPinAtTerminalD0() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_0, G61_1);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        double terminal = G61_0.totalMeters();
        long t = 1000;
        List<GpsFix> fixes = new ArrayList<>();
        for (double s = terminal - 400; s < terminal - 10; s += 40, t += 5) {
            fixes.add(fixOn(G61_0, s, 28.0, t));
        }
        for (int i = 0; i < 8; i++, t += 5) {
            fixes.add(fixOn(G61_0, terminal - 4, 0.0, t));
        }
        String mode = "";
        for (GpsFix fx : fixes) {
            mode = core.onFix(fx, topo).mode();
        }
        assertThat(mode).as("прегейт сценария: борт запинен на конечной").isEqualTo("AT_TERMINAL");
        return new Arrived(core, topo, t);
    }

    @Test
    void cityDepartureWithTrafficLightStopsUnsticksTerminalPin() {
        Arrived a = arriveAndPinAtTerminalD0();
        long t = a.tSec();

        double s1 = 30;
        String mode = "";
        for (int block = 0; block < 6; block++) {
            for (int i = 0; i < 3; i++, t += 8) {
                s1 += 55;
                mode = a.core().onFix(fixOn(G61_1, s1, 26.0, t), a.topo()).mode();
            }
            for (int i = 0; i < 2; i++, t += 8) {
                mode = a.core().onFix(fixOn(G61_1, s1, 0.0, t), a.topo()).mode();
            }
        }

        assertThat(a.core().bank().leader().variantId())
                .as("городской выезд со светофорами разлепляет терминальный пин")
                .isEqualTo("61#d1");
        assertThat(a.core().tripId()).as("новый рейс").isEqualTo(2);
        assertThat(mode).isNotEqualTo("AT_TERMINAL");
    }

    @Test
    void slowCrawlDepartureUnsticksViaEscapeDistance() {
        Arrived a = arriveAndPinAtTerminalD0();
        long t = a.tSec();

        double s1 = 30;
        for (int i = 0; i < 2; i++, t += 8) {
            s1 += 90;
            a.core().onFix(fixOn(G61_1, s1, 26.0, t), a.topo());
        }
        for (int i = 0; i < 26; i++, t += 8) {
            s1 += 25;
            a.core().onFix(fixOn(G61_1, s1, 4.0, t), a.topo());
        }

        assertThat(a.core().bank().leader().variantId())
                .as("ползучий выезд: ход ≥dTermEscapeMeters снимает контроль без серии движущихся тиков")
                .isEqualTo("61#d1");
        assertThat(a.core().tripId()).isEqualTo(2);
    }

}
