package biz.ugur.busroutebackend.replay.core;

import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.GeometryFixture;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.StopAware;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CityZoneScenariosTest {

    private static final GeometryFixture G61_0 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir0.json");
    private static final GeometryFixture G61_1 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir1.json");
    private static final CoreConfig CFG = CoreConfig.defaults();
    private static final double P_LAT = 38.058718;
    private static final double P_LON = 58.175667;
    private static final double DEG_PER_METER_LAT = 1.0 / 111320.0;

    private static RouteTopology cityTopo() {
        double[] deep = G61_0.pointAtS(G61_0.totalMeters());
        return RouteTopology.thereAndBack(G61_0, G61_1)
                .withCityZone(new RouteTopology.CityZone(deep[0], deep[1], P_LAT, P_LON));
    }

    private static GpsFix fixOn(GeometryFixture g, double s, double speedKmh, long tSec) {
        double[] p = g.pointAtS(s);
        return fixAt(p[0], p[1], speedKmh, tSec);
    }

    private static GpsFix fixAt(double lat, double lon, double speedKmh, long tSec) {
        return new GpsFix("test-veh", "TEST 01", "61", lat, lon,
                speedKmh, 0.0, speedKmh > 1, Instant.ofEpochSecond(tSec), 0,
                0.8, 12, 0.0, Instant.ofEpochSecond(tSec));
    }

    private record Drive(List<String> modes, List<StopAware.StopEvent> events, long tEnd) {}

    private static Drive drive(MotionFilterCore core, RouteTopology topo, List<GpsFix> fixes) {
        List<String> modes = new ArrayList<>();
        for (GpsFix fx : fixes) {
            modes.add(core.onFix(fx, topo).mode());
        }
        long tEnd = fixes.get(fixes.size() - 1).timestamp().getEpochSecond();
        return new Drive(modes, core.drainEvents(), tEnd);
    }

    private static List<GpsFix> approachOnAxisTo(double sEnd, long tStart) {
        List<GpsFix> out = new ArrayList<>();
        long t = tStart;
        for (double s = sEnd - 3000; s <= sEnd; s += 150, t += 10) {
            out.add(fixOn(G61_0, s, 45.0, t));
        }
        return out;
    }

    private static long lastT(List<GpsFix> fixes) {
        return fixes.get(fixes.size() - 1).timestamp().getEpochSecond();
    }

    private static List<GpsFix> plateauCrawl(long tStart, int seconds, int stepSec) {
        return plateauCrawl(tStart, seconds, stepSec, 0.0);
    }

    private static List<GpsFix> plateauCrawl(long tStart, int seconds, int stepSec,
                                             double baseOffsetMeters) {
        List<GpsFix> out = new ArrayList<>();
        long t = tStart;
        int i = 0;
        for (int done = 0; done <= seconds; done += stepSec, t += stepSec, i++) {
            double offset = (baseOffsetMeters + (i % 4) * 40.0) * DEG_PER_METER_LAT;
            double speed = i % 2 == 0 ? 3.0 : 8.0;
            out.add(fixAt(P_LAT + offset, P_LON, speed, t));
        }
        return out;
    }

    private static String cityEventTag(List<StopAware.StopEvent> events) {
        return events.stream()
                .filter(e -> e.stopId().startsWith("city-zone"))
                .map(StopAware.StopEvent::stopId)
                .findFirst().orElse("");
    }

    @Test
    void crawlingAtPlateauAccumulatesSpanAndFiresSingleArrival() {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        RouteTopology topo = cityTopo();
        List<GpsFix> fixes = approachOnAxisTo(29500, 1000);
        fixes.addAll(plateauCrawl(lastT(fixes) + 10, 200, 10));
        Drive d = drive(core, topo, fixes);

        assertThat(core.mode().name()).isEqualTo("AT_TERMINAL");
        assertThat(cityEventTag(d.events())).isEqualTo("city-zone-A");
        long cityEvents = d.events().stream()
                .filter(e -> e.stopId().startsWith("city-zone")).count();
        assertThat(cityEvents).as("одно событие A на пребывание").isEqualTo(1);
    }

    @Test
    void outOfZoneFixBreaksSpan() {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        RouteTopology topo = cityTopo();
        List<GpsFix> fixes = approachOnAxisTo(29500, 1000);
        fixes.addAll(plateauCrawl(lastT(fixes) + 10, 50, 10));
        long t = lastT(fixes) + 10;
        fixes.add(fixAt(P_LAT + 2000 * DEG_PER_METER_LAT, P_LON, 30.0, t));
        fixes.addAll(plateauCrawl(t + 10, 50, 10));
        Drive d = drive(core, topo, fixes);

        assertThat(core.mode().name()).isNotEqualTo("AT_TERMINAL");
        assertThat(cityEventTag(d.events())).isEmpty();
    }

    @Test
    void feedGapOverGBreaksSpanThenReaccumulates() {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        RouteTopology topo = cityTopo();
        List<GpsFix> fixes = approachOnAxisTo(29500, 1000);
        fixes.addAll(plateauCrawl(lastT(fixes) + 10, 50, 10, 300.0));
        long tAfterGap = lastT(fixes) + (long) CFG.gCitySpanGapSec() + 100;
        fixes.addAll(plateauCrawl(tAfterGap, 100, 10, 300.0));
        Drive d = drive(core, topo, fixes);
        assertThat(cityEventTag(d.events()))
                .as("span сброшен гэпом: 50с + 100с не сливаются").isEmpty();

        List<GpsFix> more = plateauCrawl(d.tEnd() + 10, 50, 10, 300.0);
        Drive d2 = drive(core, topo, more);
        assertThat(cityEventTag(d2.events()))
                .as("новый span 100+50 >= T_dwell — событие A").isEqualTo("city-zone-A");
    }

    @Test
    void dwellBelowThresholdDoesNotFire() {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        RouteTopology topo = cityTopo();
        List<GpsFix> fixes = approachOnAxisTo(29500, 1000);
        fixes.addAll(plateauCrawl(lastT(fixes) + 10, 100, 10));
        Drive d = drive(core, topo, fixes);

        assertThat(core.mode().name()).isNotEqualTo("AT_TERMINAL");
        assertThat(cityEventTag(d.events())).isEmpty();
    }

    @Test
    void deepNodeEntryByRunWithoutDwell() {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        RouteTopology topo = cityTopo();
        List<GpsFix> fixes = approachOnAxisTo(28000, 1000);
        long t = lastT(fixes) + 10;
        for (int i = 0; i < CFG.mCityFixes() + 1; i++, t += 10) {
            fixes.add(fixOn(G61_0, G61_0.totalMeters() - 5, 15.0, t));
        }
        Drive d = drive(core, topo, fixes);

        assertThat(core.mode().name()).isEqualTo("AT_TERMINAL");
        assertThat(cityEventTag(d.events()))
                .as("x̂ отстал (№42-класс) — вход даёт зона, без dwell-условия узла D")
                .isEqualTo("city-zone-A");
        assertThat(core.direction()).isEqualTo(0);
    }

    @Test
    void regularXEntryHasPriorityOverZone() {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        RouteTopology topo = cityTopo();
        List<GpsFix> fixes = approachOnAxisTo(G61_0.totalMeters() - 40, 1000);
        long t = lastT(fixes) + 10;
        for (int i = 0; i < 6; i++, t += 10) {
            fixes.add(fixOn(G61_0, G61_0.totalMeters() - 5, 4.0, t));
        }
        Drive d = drive(core, topo, fixes);

        assertThat(core.mode().name()).isEqualTo("AT_TERMINAL");
        List<StopAware.StopEvent> arrivals = d.events().stream()
                .filter(e -> e.type() == StopAware.StopEventType.AT_TERMINAL).toList();
        assertThat(arrivals).as("ровно один вход AT_TERMINAL").hasSize(1);
        assertThat(arrivals.get(0).stopId())
                .as("штатный x-вход имеет приоритет").isEqualTo("terminal");
    }

    @Test
    void wakeReanchorInsideZoneRestoresPin() {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        RouteTopology topo = cityTopo();
        List<GpsFix> fixes = approachOnAxisTo(29000, 1000);
        Drive warmup = drive(core, topo, fixes);
        assertThat(warmup.modes().get(warmup.modes().size() - 1)).isEqualTo("TRACKING");

        long t = warmup.tEnd() + 600;
        double sWake = G61_0.totalMeters() - 200;
        List<GpsFix> wake = new ArrayList<>();
        for (int i = 0; i < 3; i++, t += 10) {
            wake.add(fixOn(G61_0, sWake, 5.0, t));
        }
        Drive d = drive(core, topo, wake);

        assertThat(core.mode().name()).isEqualTo("AT_TERMINAL");
        assertThat(cityEventTag(d.events())).isEqualTo("city-zone-B");
    }

    @Test
    void sustainedExitConfirmsNewTripWithoutWindow() {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        RouteTopology topo = cityTopo();
        List<GpsFix> fixes = approachOnAxisTo(29500, 1000);
        fixes.addAll(plateauCrawl(lastT(fixes) + 10, 200, 10));
        drive(core, topo, fixes);
        assertThat(core.mode().name()).isEqualTo("AT_TERMINAL");
        long tripBefore = core.tripId();

        long t = lastT(fixes) + 10;
        List<GpsFix> departure = new ArrayList<>();
        for (double s = 400; s <= 2400; s += 250, t += 10) {
            departure.add(fixOn(G61_1, s, 35.0, t));
        }
        drive(core, topo, departure);

        assertThat(core.tripId()).as("граница отправления взята").isEqualTo(tripBefore + 1);
        assertThat(core.direction()).isEqualTo(1);
    }

    @Test
    void offCorridorDepartureStillGoesOffRoute() {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        RouteTopology topo = cityTopo();
        List<GpsFix> fixes = approachOnAxisTo(29500, 1000);
        fixes.addAll(plateauCrawl(lastT(fixes) + 10, 200, 10));
        drive(core, topo, fixes);
        assertThat(core.mode().name()).isEqualTo("AT_TERMINAL");
        long tripBefore = core.tripId();

        long t = lastT(fixes) + 10;
        List<GpsFix> away = new ArrayList<>();
        for (int i = 1; i <= 10; i++, t += 10) {
            away.add(fixAt(P_LAT - i * 300 * DEG_PER_METER_LAT,
                    P_LON - i * 300 * DEG_PER_METER_LAT, 40.0, t));
        }
        drive(core, topo, away);

        assertThat(core.mode().name())
                .as("выезд вне коридора освобождает пин через К-2").isEqualTo("OFF_ROUTE");
        assertThat(core.tripId()).isEqualTo(tripBefore);
    }

    @Test
    void wakeInPlateauOnlyPinsWithoutTerminal() {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        RouteTopology topo = cityTopo();
        Drive warmup = drive(core, topo, approachOnAxisTo(20000, 1000));
        double sNearP = 0;
        double bestDp = Double.MAX_VALUE;
        for (double s = 0; s <= G61_0.totalMeters(); s += 25) {
            double[] q = G61_0.pointAtS(s);
            double dp = GeometryFixture.haversineMeters(q[0], q[1], P_LAT, P_LON);
            if (dp < bestDp && GeometryFixture.haversineMeters(q[0], q[1],
                    G61_0.pointAtS(G61_0.totalMeters())[0],
                    G61_0.pointAtS(G61_0.totalMeters())[1]) > CFG.rCityDeepMeters()) {
                bestDp = dp;
                sNearP = s;
            }
        }
        assertThat(bestDp).as("на оси есть точка в P-радиусе вне D").isLessThan(CFG.rCityPlateauMeters());
        long t = warmup.tEnd() + 600;
        List<GpsFix> wake = new ArrayList<>();
        for (int i = 0; i < 4; i++, t += 10) {
            wake.add(fixOn(G61_0, sNearP + i * 30, 60.0, t));
        }
        Drive d = drive(core, topo, wake);

        assertThat(core.mode().name())
                .as("транзит-wake в P на скорости: AT_TERMINAL не рождается (У-1)")
                .isNotEqualTo("AT_TERMINAL");
        assertThat(cityEventTag(d.events())).isEmpty();
        assertThat(core.cityPinActive()).isTrue();
    }

    @Test
    void transitPassWithoutDwellKeepsCSilent() {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        RouteTopology topo = cityTopo();
        List<GpsFix> fixes = approachOnAxisTo(29500, 1000);
        long tripBefore = -1;
        long t = lastT(fixes) + 10;
        for (double s = 200; s <= 2600; s += 300, t += 10) {
            fixes.add(fixOn(G61_1, s, 45.0, t));
        }
        Drive d = drive(core, topo, fixes);

        assertThat(cityEventTag(d.events()))
                .as("въезд-проезд без стоянки: ни A, ни C (У-2, флап-класс эп.15)")
                .isEmpty();
        assertThat(d.modes()).doesNotContain("NEW_TRIP");
    }

    @Test
    void gurtlyTerminalUntouchedByCityZone() {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0)
                .withCityZone(new RouteTopology.CityZone(
                        G61_0.pointAtS(G61_0.totalMeters())[0],
                        G61_0.pointAtS(G61_0.totalMeters())[1], P_LAT, P_LON));
        List<GpsFix> arrival = new ArrayList<>();
        long t = 1000;
        for (double s = 23800; s <= G61_1.totalMeters(); s += 150, t += 10) {
            arrival.add(fixOn(G61_1, Math.min(s, G61_1.totalMeters()), 40.0, t));
        }
        for (int i = 0; i < 40; i++, t += 10) {
            arrival.add(fixOn(G61_1, G61_1.totalMeters(), 3.0, t));
        }
        Drive d = drive(core, topo, arrival);

        assertThat(core.mode().name()).isEqualTo("AT_TERMINAL");
        assertThat(cityEventTag(d.events()))
                .as("Gurtly-терминал не порождает city-событий").isEmpty();
        List<StopAware.StopEvent> arrivals = d.events().stream()
                .filter(e -> e.type() == StopAware.StopEventType.AT_TERMINAL).toList();
        assertThat(arrivals).hasSize(1);
        assertThat(arrivals.get(0).stopId()).isEqualTo("terminal");
    }

    private static double sNearPlateauOutsideDeep() {
        double sNearP = 0;
        double bestDp = Double.MAX_VALUE;
        for (double s = 0; s <= G61_0.totalMeters(); s += 25) {
            double[] q = G61_0.pointAtS(s);
            double dp = GeometryFixture.haversineMeters(q[0], q[1], P_LAT, P_LON);
            if (dp < bestDp && GeometryFixture.haversineMeters(q[0], q[1],
                    G61_0.pointAtS(G61_0.totalMeters())[0],
                    G61_0.pointAtS(G61_0.totalMeters())[1]) > CFG.rCityDeepMeters()) {
                bestDp = dp;
                sNearP = s;
            }
        }
        return sNearP;
    }

    private static MotionFilterCore wakePinnedCore(RouteTopology topo, long[] tOut) {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        Drive warmup = drive(core, topo, approachOnAxisTo(20000, 1000));
        double sNearP = sNearPlateauOutsideDeep();
        long t = warmup.tEnd() + 600;
        List<GpsFix> wake = new ArrayList<>();
        for (int i = 0; i < 4; i++, t += 10) {
            wake.add(fixOn(G61_0, sNearP + i * 30, 60.0, t));
        }
        drive(core, topo, wake);
        tOut[0] = t;
        return core;
    }

    @Test
    void pinnedStandingVehicleWithRejectedSnapsHasZeroXDrift() {
        RouteTopology topo = cityTopo();
        long[] t = new long[1];
        MotionFilterCore core = wakePinnedCore(topo, t);
        assertThat(core.cityPinActive()).isTrue();
        double offLat = P_LAT + 300 * DEG_PER_METER_LAT;
        double x0 = core.onFix(fixAt(offLat, P_LON, 0.0, t[0] += 10), topo).s();
        double maxDrift = 0;
        for (int i = 0; i < 4; i++) {
            double xi = core.onFix(fixAt(offLat, P_LON, 0.0, t[0] += 10), topo).s();
            maxDrift = Math.max(maxDrift, Math.abs(xi - x0));
        }
        assertThat(maxDrift)
                .as("дрейф x при пине и отвергнутых снапах (до persist-механики)")
                .isEqualTo(0.0);
    }

    @Test
    void pinnedXFollowsAcceptedSnap() {
        RouteTopology topo = cityTopo();
        long[] t = new long[1];
        MotionFilterCore core = wakePinnedCore(topo, t);
        double sNearP = sNearPlateauOutsideDeep();
        double before = core.onFix(fixOn(G61_0, sNearP + 120, 20.0, t[0] += 10), topo).s();
        double after = before;
        for (int i = 1; i <= 5; i++) {
            after = core.onFix(fixOn(G61_0, sNearP + 120 + i * 60, 30.0, t[0] += 10), topo).s();
        }
        assertThat(after).as("x следует за принятыми снапами при пине").isGreaterThan(before + 100);
    }

    @Test
    void unpinnedDynamicsResumeAfterZoneExit() {
        RouteTopology topo = cityTopo();
        long[] t = new long[1];
        MotionFilterCore core = wakePinnedCore(topo, t);
        double sNearP = sNearPlateauOutsideDeep();
        for (int i = 1; i <= 30 && core.cityPinActive(); i++) {
            core.onFix(fixOn(G61_0, sNearP - i * 150, 45.0, t[0] += 10), topo);
        }
        assertThat(core.cityPinActive()).as("пин снят устойчивым выходом").isFalse();
        long tripBefore = core.tripId();
        for (int i = 0; i < 5; i++) {
            core.onFix(fixOn(G61_0, sNearP - 4500 - i * 150, 45.0, t[0] += 10), topo);
        }
        assertThat(core.tripId()).as("выход wake-пина без квалификации не рождает границу")
                .isEqualTo(tripBefore);
    }

    @Test
    void transitWakeWithShortDwellProducesNoExitBoundary() {
        RouteTopology topo = cityTopo();
        long[] t = new long[1];
        MotionFilterCore core = wakePinnedCore(topo, t);
        double sNearP = sNearPlateauOutsideDeep();
        for (int done = 0; done <= 60; done += 10) {
            core.onFix(fixOn(G61_0, sNearP, 2.0, t[0] += 10), topo);
        }
        long tripBefore = core.tripId();
        List<String> modes = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            modes.add(core.onFix(fixAt(P_LAT + i * 250 * DEG_PER_METER_LAT, P_LON,
                    40.0, t[0] += 10), topo).mode());
        }
        assertThat(core.tripId())
                .as("стоянка 60с < tCityDwellSec: выход не рождает границу").isEqualTo(tripBefore);
        assertThat(modes).doesNotContain("NEW_TRIP");
    }
}
