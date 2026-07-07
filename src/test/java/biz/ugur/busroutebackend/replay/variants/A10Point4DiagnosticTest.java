package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.GpsFix;
import biz.ugur.busroutebackend.replay.RouteTopology;
import biz.ugur.busroutebackend.replay.core.CoreConfig;
import biz.ugur.busroutebackend.replay.core.MotionFilterCore;
import biz.ugur.busroutebackend.replay.pipeline.CorpusLoader;
import biz.ugur.busroutebackend.replay.pipeline.Episode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class A10Point4DiagnosticTest {

    private static final CoreConfig CFG = CoreConfig.defaults();
    private static final double[] PAD = {38.046798, 58.200888};
    private static final Instant PHYS_TURN = Instant.parse("2026-07-06T01:43:44Z");
    private static final String TARGET_VID = "62cb1460";
    private static final String TARGET_START = "2026-07-06T01:09:39Z";
    private static final Instant TURN_WIN_FROM = Instant.parse("2026-07-06T01:40:00Z");
    private static final Instant TURN_WIN_TO = Instant.parse("2026-07-06T01:50:00Z");

    private record Tick(Instant ts, double t, String leader, int dir, double s, double[] geo,
                        String mode, boolean newTrip, boolean accepted, double nis,
                        double rawSpeedKmh, double[] rawLatLon) {}

    private record RunOut(List<List<Tick>> episodes, List<String> episodeKeys, String streamSha) {}

    private RunOut run(String corpusDir, RouteTopology topo) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        List<List<Tick>> episodes = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (Episode ep : CorpusLoader.load(Path.of(corpusDir), CFG.tMaxSec(), 10)) {
            if (!ep.routeNumber().equals("25")) continue;
            MotionFilterCore core = new MotionFilterCore(CFG);
            core.reset();
            List<Tick> ticks = new ArrayList<>();
            long t0 = ep.fixes().get(0).timestamp().toEpochMilli();
            String prevMode = "";
            for (GpsFix fx : ep.fixes()) {
                var est = core.onFix(fx, topo);
                String leader = core.bank().leader().variantId();
                double[] geo = core.bank().leader().geom().pointAtS(est.s());
                digest.update(String.format(Locale.ROOT, "%s|%s|%.1f|%s%n",
                        fx.timestamp(), leader, est.s(), est.mode()).getBytes());
                double nis = core.lastUpdateAccepted() && core.lastInnovationVariance() > 0
                        ? core.lastInnovation() * core.lastInnovation() / core.lastInnovationVariance()
                        : Double.NaN;
                ticks.add(new Tick(fx.timestamp(),
                        (fx.timestamp().toEpochMilli() - t0) / 1000.0,
                        leader, core.direction(), est.s(), geo, est.mode(),
                        est.mode().equals("NEW_TRIP") && !prevMode.equals("NEW_TRIP"),
                        core.lastUpdateAccepted(), nis, fx.speedKmh(),
                        new double[]{fx.latitude(), fx.longitude()}));
                prevMode = est.mode();
            }
            episodes.add(ticks);
            keys.add(ep.vehicleId().substring(0, 8) + "@" + ep.fixes().get(0).timestamp());
        }
        return new RunOut(episodes, keys,
                java.util.HexFormat.of().formatHex(digest.digest()).substring(0, 12));
    }

    private static String local(Instant ts) {
        return ts.plusSeconds(5 * 3600).toString().substring(0, 16).replace("T", " ");
    }

    private static boolean isShort(String leader) {
        return leader.contains("-short");
    }

    private static boolean isWeekend(Instant ts) {
        String day = ts.plusSeconds(5 * 3600).toString().substring(0, 10);
        return day.equals("2026-07-04") || day.equals("2026-07-05");
    }

    @Test
    @EnabledIfSystemProperty(named = "a10.p4diag", matches = "true")
    void diagnosticPrefixBankOnWeekdayCorpus() throws Exception {
        String corpusDir = System.getProperty("corpus.dir");
        assertThat(corpusDir).isNotBlank();

        GeometryFixture f0 = Variant25FixturesTest.FULL_0;
        GeometryFixture f1 = Variant25FixturesTest.FULL_1;
        RouteTopology topoA = RouteTopology.thereAndBack(f0, f1);
        RouteTopology topoB = topoA.withVariants(List.of(
                Variant25FixturesTest.short0().shortVariant(),
                Variant25FixturesTest.short1().shortVariant()));

        RunOut a = run(corpusDir, topoA);
        RunOut b = run(corpusDir, topoB);
        System.out.printf("П.0: эпизодов 25 = %d; SHA потока A (без вариантов) = %s; "
                + "SHA потока B (актуальный каталог) = %s%n", a.episodes().size(),
                a.streamSha(), b.streamSha());

        int target = -1;
        for (int i = 0; i < b.episodeKeys().size(); i++) {
            if (b.episodeKeys().get(i).startsWith(TARGET_VID)
                    && b.episodeKeys().get(i).contains(TARGET_START)) target = i;
        }
        assertThat(target).as("прицельный эпизод найден").isNotNegative();
        reportP1(a.episodes().get(target), b.episodes().get(target));
        reportP2(b, target);
        reportP3(a, b);
        reportP4(f1);
    }

    private void reportP1(List<Tick> ticksA, List<Tick> ticksB) {
        System.out.println("=== П.1 эпизод 62cb1460 (25/0) ===");
        long n = ticksB.stream().filter(t -> isShort(t.leader())).count();
        Instant first = null;
        Instant last = null;
        for (Tick t : ticksB) {
            if (isShort(t.leader())) {
                if (first == null) first = t.ts();
                last = t.ts();
            }
        }
        System.out.printf("short-лидерство: n=%d, окно local %s → %s%n",
                n, first == null ? "—" : local(first), last == null ? "—" : local(last));

        Tick firstSwitch = null;
        for (int i = 1; i < ticksB.size(); i++) {
            if (isShort(ticksB.get(i).leader()) && !isShort(ticksB.get(i - 1).leader())) {
                firstSwitch = ticksB.get(i);
                break;
            }
        }
        if (firstSwitch != null) {
            double dPad = GeometryFixture.haversineMeters(
                    firstSwitch.rawLatLon()[0], firstSwitch.rawLatLon()[1], PAD[0], PAD[1]);
            System.out.printf("первая смена на short: t=%s local; s_full=%.0f м "
                            + "(префикс без сдвига: s_short=s_full); борт→площадка=%.0f м; %s физразворота%n",
                    local(firstSwitch.ts()), firstSwitch.s(), dPad,
                    firstSwitch.ts().isBefore(PHYS_TURN) ? "ДО" : "ПОСЛЕ");
        } else {
            System.out.println("смен на short в эпизоде нет");
        }

        for (String label : List.of("A", "B")) {
            List<Tick> ticks = label.equals("A") ? ticksA : ticksB;
            double maxImpl = 0;
            long over100 = 0;
            long nonMono = 0;
            for (int i = 1; i < ticks.size(); i++) {
                Tick p = ticks.get(i - 1);
                Tick c = ticks.get(i);
                if (c.ts().isBefore(TURN_WIN_FROM) || c.ts().isAfter(TURN_WIN_TO)) continue;
                double dt = Math.max(c.t() - p.t(), 1);
                double vImpl = GeometryFixture.haversineMeters(
                        p.geo()[0], p.geo()[1], c.geo()[0], c.geo()[1]) / dt * 3.6;
                maxImpl = Math.max(maxImpl, vImpl);
                if (vImpl > 100) over100++;
                if (c.s() < p.s() && c.rawSpeedKmh() > 5) nonMono++;
            }
            System.out.printf("вещание на развороте (окно %s–%s local), прогон %s: "
                            + "max v_impl=%.0f км/ч; тиков v_impl>100: %d; немонотонностей s при v_land>5: %d%n",
                    local(TURN_WIN_FROM), local(TURN_WIN_TO).substring(11), label,
                    maxImpl, over100, nonMono);
        }

        List<Tick> newTrips = ticksB.stream().filter(Tick::newTrip).toList();
        System.out.printf("NEW_TRIP-события: n=%d%n", newTrips.size());
        for (int i = 0; i < newTrips.size(); i++) {
            Tick nt = newTrips.get(i);
            int idx = ticksB.indexOf(nt);
            int dirBefore = idx > 0 ? ticksB.get(idx - 1).dir() : nt.dir();
            System.out.printf("  NEW_TRIP@%s local, dir %d→%d%n", local(nt.ts()), dirBefore, nt.dir());
        }
        long quietSwitches = 0;
        for (int i = 1; i < ticksB.size(); i++) {
            if (!ticksB.get(i).leader().equals(ticksB.get(i - 1).leader())
                    && ticksB.get(i).dir() == ticksB.get(i - 1).dir()) quietSwitches++;
        }
        System.out.printf("смен лидера внутри d (тихих, №23): %d%n", quietSwitches);
    }

    private void reportP2(RunOut b, int targetIdx) {
        System.out.println("=== П.2 остальные эпизоды 25 (без 62cb1460) ===");
        long ticksShort = 0;
        long switchesToShort = 0;
        long newTrips = 0;
        long[] zones = new long[3];
        List<Double> segmentsSec = new ArrayList<>();
        long weekday = 0;
        long weekend = 0;
        for (int e = 0; e < b.episodes().size(); e++) {
            if (e == targetIdx) continue;
            List<Tick> ticks = b.episodes().get(e);
            Double segStart = null;
            for (int i = 0; i < ticks.size(); i++) {
                Tick t = ticks.get(i);
                if (t.newTrip()) newTrips++;
                boolean sh = isShort(t.leader());
                if (sh) {
                    ticksShort++;
                    if (isWeekend(t.ts())) weekend++;
                    else weekday++;
                    double s = t.s();
                    if (s < 15000) zones[0]++;
                    else if (s < 17200) zones[1]++;
                    else zones[2]++;
                    if (segStart == null) segStart = t.t();
                    if (i > 0 && !isShort(ticks.get(i - 1).leader())) switchesToShort++;
                } else if (segStart != null) {
                    segmentsSec.add(t.t() - segStart);
                    segStart = null;
                }
            }
            if (segStart != null && !ticks.isEmpty()) {
                segmentsSec.add(ticks.get(ticks.size() - 1).t() - segStart);
            }
        }
        double maxSeg = segmentsSec.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double medSeg = segmentsSec.isEmpty() ? 0
                : segmentsSec.stream().sorted().toList().get(segmentsSec.size() / 2);
        System.out.printf("short-тики=%d; смен на short=%d; NEW_TRIP=%d%n",
                ticksShort, switchesToShort, newTrips);
        System.out.printf("гистограмма по s-зонам (дуга направления лидера): кольцо [0;15000)=%d; "
                + "складка [15000;17200)=%d; хвост [17200;32880]=%d%n", zones[0], zones[1], zones[2]);
        System.out.printf("отрезки short-лидерства: n=%d, max=%.0fс, median=%.0fс%n",
                segmentsSec.size(), maxSeg, medSeg);
        System.out.printf("разбивка: будни=%d, выходные=%d%n", weekday, weekend);
    }

    private void reportP3(RunOut a, RunOut b) {
        System.out.println("=== П.3 сводное A vs B (весь корпус 25) ===");
        for (String label : List.of("A", "B")) {
            RunOut r = label.equals("A") ? a : b;
            double flightMax = 0;
            long flightViol = 0;
            List<Double> nis = new ArrayList<>();
            long shortTicks = 0;
            long total = 0;
            for (List<Tick> ticks : r.episodes()) {
                for (int i = 0; i < ticks.size(); i++) {
                    Tick t = ticks.get(i);
                    total++;
                    if (isShort(t.leader())) shortTicks++;
                    if (!Double.isNaN(t.nis())) nis.add(t.nis());
                    if (i == 0) continue;
                    Tick p = ticks.get(i - 1);
                    boolean sanctioned = t.mode().equals("RECOVERING") || t.mode().equals("NEW_TRIP")
                            || !t.leader().equals(p.leader());
                    if (sanctioned) continue;
                    double dt = Math.max(t.t() - p.t(), 1);
                    double ratio = GeometryFixture.haversineMeters(
                            p.geo()[0], p.geo()[1], t.geo()[0], t.geo()[1]) / (dt * CFG.vMaxMs());
                    flightMax = Math.max(flightMax, ratio);
                    if (ratio > 1.5) flightViol++;
                }
            }
            double med = nis.isEmpty() ? Double.NaN
                    : nis.stream().sorted().toList().get(nis.size() / 2);
            long over = nis.stream().filter(v -> v > 3.84).count();
            System.out.printf(Locale.ROOT,
                    "%s: полёт max=%.2f, нарушений=%d; NIS n=%d, median=%.2f, >3.84=%.1f%%; "
                            + "доля short-тиков=%.2f%% (%d/%d)%n",
                    label, flightMax, flightViol, nis.size(), med,
                    nis.isEmpty() ? 0 : 100.0 * over / nis.size(),
                    total > 0 ? 100.0 * shortTicks / total : 0, shortTicks, total);
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "a10.p5dump", matches = "true")
    void dumpEp62TurnWindowTicks() throws Exception {
        String corpusDir = System.getProperty("corpus.dir");
        assertThat(corpusDir).isNotBlank();
        RouteTopology topoB = RouteTopology.thereAndBack(
                        Variant25FixturesTest.FULL_0, Variant25FixturesTest.FULL_1)
                .withVariants(List.of(Variant25FixturesTest.short0().shortVariant(),
                        Variant25FixturesTest.short1().shortVariant()));
        Instant winFrom = Instant.parse("2026-07-06T01:20:00Z");
        Instant winTo = Instant.parse("2026-07-06T01:50:00Z");

        for (Episode ep : CorpusLoader.load(Path.of(corpusDir), CFG.tMaxSec(), 10)) {
            if (!ep.routeNumber().equals("25")
                    || !ep.vehicleId().startsWith(TARGET_VID)
                    || !ep.fixes().get(0).timestamp().toString().startsWith(TARGET_START.substring(0, 19))) {
                continue;
            }
            MotionFilterCore core = new MotionFilterCore(CFG);
            core.reset();
            List<String> rows = new ArrayList<>();
            List<Object[]> win = new ArrayList<>();
            String prevLeader = null;
            java.util.Map<String, Double> prevScores = new java.util.HashMap<>();
            java.util.Map<String, Integer> challengerStreak = new java.util.HashMap<>();
            List<String> switchNotes = new ArrayList<>();
            for (GpsFix fx : ep.fixes()) {
                var est = core.onFix(fx, topoB);
                var leaderHyp = core.bank().leader();
                String leader = leaderHyp.variantId();
                double dSnap = leaderHyp.geom().projectOntoRange(
                        fx.latitude(), fx.longitude(), 0, leaderHyp.geom().totalMeters(), 0).distMeters();
                double leaderScore = leaderHyp.score();
                for (var h : core.bank().hypotheses()) {
                    if (h.variantId().equals(leader)) continue;
                    if (h.score() > leaderScore - CFG.sSwitch()) {
                        challengerStreak.merge(h.variantId(), 1, Integer::sum);
                    } else {
                        challengerStreak.put(h.variantId(), 0);
                    }
                }
                boolean inWin = !fx.timestamp().isBefore(winFrom) && !fx.timestamp().isAfter(winTo);
                if (inWin) {
                    rows.add(String.format(Locale.ROOT, "| %s | %.6f | %.6f | %.0f | %s | %.0f | %.0f |",
                            local(fx.timestamp()).substring(11), fx.latitude(), fx.longitude(),
                            fx.speedKmh(), leader, est.s(), dSnap));
                    win.add(new Object[]{fx.timestamp(), fx.speedKmh(),
                            GeometryFixture.haversineMeters(fx.latitude(), fx.longitude(), PAD[0], PAD[1]),
                            leader, est.s(), core.direction()});
                    if (prevLeader != null && !prevLeader.equals(leader)) {
                        switchNotes.add(String.format(Locale.ROOT,
                                "смена %s → %s @ %s local; ΔS-серия претендента до смены: %d тиков",
                                prevLeader, leader, local(fx.timestamp()),
                                challengerStreak.getOrDefault(leader, 0)));
                        challengerStreak.clear();
                    }
                }
                prevLeader = leader;
                prevScores.put(leader, leaderScore);
            }
            java.nio.file.Path out = Path.of("docs", "data", "a10_5_ep62cb1460_ticks.md");
            List<String> md = new ArrayList<>();
            md.add("# A10.5 П.0 — тик-таблица эпизода 62cb1460, окно 06:20–06:50 local");
            md.add("");
            md.add("Каталог банка: {full#d0, full#d1, short-prefix#d0 15.79, short#d1 17.34}. "
                    + "Источник: A10Point4DiagnosticTest#dumpEp62TurnWindowTicks (D3-манифест).");
            md.add("");
            md.add("| t local | lat | lon | speed | leader | s_leader, м | d_snap_leader, м |");
            md.add("|---|---|---|---|---|---|---|");
            md.addAll(rows);
            java.nio.file.Files.write(out, md);
            System.out.println("тик-файл: " + out + " (" + rows.size() + " тиков)");

            System.out.println("(а) стоянки (speed<3 длительностью >60с):");
            Instant standStart = null;
            double standDist = 0;
            Object[] prev = null;
            for (Object[] w : win) {
                double sp = (double) w[1];
                if (sp < 3) {
                    if (standStart == null) {
                        standStart = (Instant) w[0];
                        standDist = (double) w[2];
                    }
                } else if (standStart != null) {
                    long dur = java.time.Duration.between(standStart, (Instant) prev[0]).getSeconds();
                    if (dur > 60) {
                        System.out.printf(Locale.ROOT,
                                "  %s → %s (%d с), место: %.0f м до площадки%n",
                                local(standStart), local((Instant) prev[0]), dur, standDist);
                    }
                    standStart = null;
                }
                prev = w;
            }
            if (standStart != null && prev != null) {
                long dur = java.time.Duration.between(standStart, (Instant) prev[0]).getSeconds();
                if (dur > 60) {
                    System.out.printf(Locale.ROOT, "  %s → конец окна (%d с), место: %.0f м до площадки%n",
                            local(standStart), dur, standDist);
                }
            }
            System.out.println("(б) смены лидера окна:");
            switchNotes.forEach(s -> System.out.println("  " + s));
            double minS = Double.MAX_VALUE;
            double maxS = 0;
            for (Object[] w : win) {
                if (((Instant) w[0]).isAfter(PHYS_TURN.minusSeconds(0))) continue;
                if ((int) w[5] != 0) continue;
                minS = Math.min(minS, (double) w[4]);
                maxS = Math.max(maxS, (double) w[4]);
            }
            System.out.printf(Locale.ROOT, "(в) s_full окна до 06:43 (тики dir=0): min=%.0f, max=%.0f%n",
                    minS, maxS);
        }
    }

    private void reportP4(GeometryFixture full1) {
        System.out.println("=== П.4 инвентаризация 25-short#d1 (A9, read-only) ===");
        var cut = Variant25FixturesTest.short1();
        GeometryFixture g = cut.shortVariant();
        System.out.printf("сегмент Γ_25/1: клип s=[%.0f; %.0f] из %.0f м → %s%n",
                cut.trunkStartS(), cut.trunkEndS(), full1.totalMeters(),
                cut.trunkStartS() == 0 ? "ПРЕФИКС" : "не префикс");
        double minD = Double.MAX_VALUE;
        for (double[] p : g.points()) {
            minD = Math.min(minD, GeometryFixture.haversineMeters(p[0], p[1], PAD[0], PAD[1]));
        }
        double[] end = g.pointAtS(g.totalMeters());
        double dEndPad = GeometryFixture.haversineMeters(end[0], end[1], PAD[0], PAD[1]);
        double[] start = g.pointAtS(0);
        double[] fullStart = full1.pointAtS(0);
        double dStartCommon = GeometryFixture.haversineMeters(
                start[0], start[1], fullStart[0], fullStart[1]);
        System.out.printf("min дистанция сегмента до площадки: %.0f м; конец сегмента → площадка: %.0f м; "
                + "старт сегмента → общий с full терминал (Γ_25/1(0)): %.0f м%n", minD, dEndPad, dStartCommon);
        System.out.printf("№27-констатация: разворот в теле родительской линии — %s "
                        + "(конец клипа в %.0f м от физплощадки; решение о судьбе фикстуры — за владельцем)%n",
                dEndPad <= 500 ? "ДА" : "НЕТ", dEndPad);
    }
}
