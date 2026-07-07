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
                        double rawSpeedKmh, double[] rawLatLon, boolean leaderPinned,
                        double leaderCum) {}

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
                        new double[]{fx.latitude(), fx.longitude()},
                        core.bank().leader().pinnedAtVariantTerminal(),
                        core.bank().leader().cumStandSec()));
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

    @Test
    @EnabledIfSystemProperty(named = "a10.p5c", matches = "true")
    void phaseBRunCVersusAB() throws Exception {
        String corpusDir = System.getProperty("corpus.dir");
        assertThat(corpusDir).isNotBlank();
        GeometryFixture f0 = Variant25FixturesTest.FULL_0;
        GeometryFixture f1 = Variant25FixturesTest.FULL_1;
        RouteTopology topoA = RouteTopology.thereAndBack(f0, f1);
        RouteTopology topoB = topoA.withVariants(List.of(
                Variant25FixturesTest.short0().shortVariant(),
                Variant25FixturesTest.short1ParkedCandidate().shortVariant()));
        RouteTopology topoC = topoA.withVariants(List.of(
                Variant25FixturesTest.short0().shortVariant(),
                Variant25FixturesTest.short1().shortVariant()));

        System.out.println("(ж) семантика ΔS-серии причинного счётчика: число ПОДРЯД тиков, "
                + "в которых претендент имел score > score(лидера) − sSwitch(0.25) — "
                + "т.е. держался в пределах порога смены от лидера или выше (не ΔS>0 и не строго ΔS≥0.25).");

        RunOut a = run(corpusDir, topoA);
        RunOut b = run(corpusDir, topoB);
        RunOut c = run(corpusDir, topoC);
        System.out.printf("П.5: SHA потока C = %s (A = %s, B[parked-17.34] = %s)%n",
                c.streamSha(), a.streamSha(), b.streamSha());

        int target = -1;
        for (int i = 0; i < c.episodeKeys().size(); i++) {
            if (c.episodeKeys().get(i).startsWith(TARGET_VID)
                    && c.episodeKeys().get(i).contains(TARGET_START)) target = i;
        }
        List<Tick> tc = c.episodes().get(target);

        long shortFam = tc.stream().filter(t -> isShort(t.leader())).count();
        Instant firstSh = null;
        Instant lastSh = null;
        for (Tick t : tc) {
            if (isShort(t.leader())) {
                if (firstSh == null) firstSh = t.ts();
                lastSh = t.ts();
            }
        }
        Tick atTurn = tc.stream().filter(t -> !t.ts().isBefore(PHYS_TURN)).findFirst().orElse(null);
        System.out.printf("эпизод 62cb1460, прогон C: тики short-семейства=%d (окно %s → %s); "
                        + "лидер на 06:43:44 = %s%n",
                shortFam, firstSh == null ? "—" : local(firstSh),
                lastSh == null ? "—" : local(lastSh),
                atTurn == null ? "—" : atTurn.leader());
        List<Tick> newTrips = tc.stream().filter(Tick::newTrip).toList();
        for (Tick nt : newTrips) {
            int idx = tc.indexOf(nt);
            int dirBefore = idx > 0 ? tc.get(idx - 1).dir() : nt.dir();
            long lag = java.time.Duration.between(PHYS_TURN, nt.ts()).getSeconds();
            System.out.printf("  NEW_TRIP@%s local, dir %d→%d, лаг к 06:43:44 = %+d с%n",
                    local(nt.ts()), dirBefore, nt.dir(), lag);
        }

        for (String label : List.of("C", "A", "B")) {
            RunOut r = label.equals("C") ? c : label.equals("A") ? a : b;
            List<Tick> ticks = r.episodes().get(target);
            double maxImpl = 0;
            long over100 = 0;
            long nonMono = 0;
            for (int i = 1; i < ticks.size(); i++) {
                Tick p = ticks.get(i - 1);
                Tick t = ticks.get(i);
                if (t.ts().isBefore(TURN_WIN_FROM) || t.ts().isAfter(TURN_WIN_TO)) continue;
                double dt = Math.max(t.t() - p.t(), 1);
                double vImpl = GeometryFixture.haversineMeters(
                        p.geo()[0], p.geo()[1], t.geo()[0], t.geo()[1]) / dt * 3.6;
                maxImpl = Math.max(maxImpl, vImpl);
                if (vImpl > 100) over100++;
                if (t.s() < p.s() && t.rawSpeedKmh() > 5) nonMono++;
            }
            System.out.printf("окно 06:40–06:50, прогон %s: max v_impl=%.0f км/ч; >100: %d; "
                    + "немонотонностей: %d%n", label, maxImpl, over100, nonMono);
        }

        int firstNt = -1;
        for (int i = 0; i < tc.size(); i++) {
            if (tc.get(i).newTrip() && tc.get(i).ts().isAfter(Instant.parse("2026-07-06T01:40:00Z"))) {
                firstNt = i;
                break;
            }
        }
        if (firstNt >= 0) {
            List<Tick> back = tc.subList(firstNt, tc.size());
            long tailTicks = back.stream().filter(t -> t.leader().contains("short-tail")).count();
            List<Double> dSnaps = new ArrayList<>();
            long within = 0;
            for (Tick t : back) {
                var leaderGeom = t.leader().equals("25#d0") ? f0
                        : t.leader().equals("25#d1") ? f1
                        : t.leader().contains("tail")
                                ? Variant25FixturesTest.short1().shortVariant()
                                : Variant25FixturesTest.short0().shortVariant();
                double d = leaderGeom.projectOntoRange(t.rawLatLon()[0], t.rawLatLon()[1],
                        0, leaderGeom.totalMeters(), 0).distMeters();
                dSnaps.add(d);
                if (d <= CFG.dSnapMeters()) within++;
            }
            var sorted = dSnaps.stream().sorted().toList();
            System.out.printf(Locale.ROOT, "(д) обратный ход (%d тиков после NEW_TRIP): "
                            + "доля лидера short-tail#d1-v2 = %.1f%%; d_snap лидера: median=%.1f м, "
                            + "p95=%.1f м; доля d_snap≤D_snap(80): %.1f%%%n",
                    back.size(), 100.0 * tailTicks / back.size(),
                    sorted.get(sorted.size() / 2), sorted.get((int) (0.95 * (sorted.size() - 1))),
                    100.0 * within / back.size());
        }

        for (String label : List.of("B", "C")) {
            RunOut r = label.equals("B") ? b : c;
            double flightMax = 0;
            long flightViol = 0;
            List<Double> nis = new ArrayList<>();
            long shortTicks = 0;
            long total = 0;
            long switchesToShort = 0;
            long newTripsAll = 0;
            long[] zones = new long[3];
            for (List<Tick> ticks : r.episodes()) {
                for (int i = 0; i < ticks.size(); i++) {
                    Tick t = ticks.get(i);
                    total++;
                    if (t.newTrip()) newTripsAll++;
                    if (isShort(t.leader())) {
                        shortTicks++;
                        double s = t.s();
                        if (s < 15000) zones[0]++;
                        else if (s < 17200) zones[1]++;
                        else zones[2]++;
                        if (i > 0 && !isShort(ticks.get(i - 1).leader())) switchesToShort++;
                    }
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
            System.out.printf(Locale.ROOT, "сводка %s: полёт %.2f/%d; NIS n=%d median=%.2f >3.84=%.1f%%; "
                            + "доля short-семейства=%.2f%%; зоны кольцо/складка/хвост=%d/%d/%d; "
                            + "смен на short=%d; NEW_TRIP=%d%n",
                    label, flightMax, flightViol, nis.size(), med,
                    nis.isEmpty() ? 0 : 100.0 * over / nis.size(),
                    total > 0 ? 100.0 * shortTicks / total : 0,
                    zones[0], zones[1], zones[2], switchesToShort, newTripsAll);
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "a11.p3diag", matches = "true")
    void a11PhaseBDumpBankInternalsAroundPad() throws Exception {
        String corpusDir = System.getProperty("corpus.dir");
        assertThat(corpusDir).isNotBlank();
        RouteTopology topo = RouteTopology.thereAndBack(
                        Variant25FixturesTest.FULL_0, Variant25FixturesTest.FULL_1)
                .withVariants(List.of(Variant25FixturesTest.short0().shortVariant(),
                        Variant25FixturesTest.short1().shortVariant()));
        Instant from = Instant.parse("2026-07-06T01:20:00Z");
        Instant to = Instant.parse("2026-07-06T01:52:00Z");
        for (Episode ep : CorpusLoader.load(Path.of(corpusDir), CFG.tMaxSec(), 10)) {
            if (!ep.routeNumber().equals("25")) continue;
            if (!ep.vehicleId().startsWith(TARGET_VID)) continue;
            System.out.printf("DIAG-эпизод: %s@%s, фиксов=%d%n",
                    ep.vehicleId().substring(0, 8), ep.fixes().get(0).timestamp(), ep.fixes().size());
            if (ep.fixes().get(ep.fixes().size() - 1).timestamp().isBefore(from)) continue;
            if (ep.fixes().get(0).timestamp().isAfter(to)) continue;
            MotionFilterCore core = new MotionFilterCore(CFG);
            core.reset();
            for (GpsFix fx : ep.fixes()) {
                var est = core.onFix(fx, topo);
                if (fx.timestamp().isBefore(from) || fx.timestamp().isAfter(to)) continue;
                double dPad = GeometryFixture.haversineMeters(
                        fx.latitude(), fx.longitude(), PAD[0], PAD[1]);
                StringBuilder sb = new StringBuilder();
                sb.append(String.format(Locale.ROOT,
                        "%s v=%4.1f dPad=%5.0f mode=%-10s lead=%-14s dir=%d",
                        local(fx.timestamp()).substring(11), fx.speedKmh(), dPad,
                        est.mode(), core.bank().leader().variantId(), core.direction()));
                for (var h : core.bank().hypotheses()) {
                    if (!h.variantId().equals("25#d0") && !h.variantId().equals("25-short#d0")) {
                        continue;
                    }
                    sb.append(String.format(Locale.ROOT,
                            " | %s x=%.0f S=%.2f eff=%.2f pin=%s cum=%.0f",
                            h.variantId(), h.x(), h.score(), core.bank().effectiveScoreOf(h),
                            h.pinnedAtVariantTerminal() ? "Y" : "n", h.cumStandSec()));
                }
                System.out.println(sb);
            }
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "a11.final", matches = "true")
    void a11PhaseBFinalCPrime() throws Exception {
        String corpusDir = System.getProperty("corpus.dir");
        assertThat(corpusDir).isNotBlank();
        boolean zoneStripped = Boolean.getBoolean("a11.nozone");
        GeometryFixture short0 = zoneStripped
                ? RingCutout.prefixToS(Variant25FixturesTest.FULL_0,
                        Variant25FixturesTest.SHORT_25_0_S_TURN_RATIFIED_M, "25-short").shortVariant()
                : Variant25FixturesTest.short0().shortVariant();
        RouteTopology topoCPrime = RouteTopology.thereAndBack(
                        Variant25FixturesTest.FULL_0, Variant25FixturesTest.FULL_1)
                .withVariants(List.of(short0, Variant25FixturesTest.short1().shortVariant()));
        if (!zoneStripped) {
            assertThat(short0.terminalZone()).as("зона R_term=750 в активной фикстуре").isNotNull();
        } else {
            System.out.println("П.4-дифф: изолирующий прогон БЕЗ зоны (a11.nozone=true)");
        }

        RunOut c2 = run(corpusDir, topoCPrime);
        System.out.printf("П.3/П.4: SHA потока C' = %s (эпизодов 25 = %d)%n",
                c2.streamSha(), c2.episodes().size());

        int target = -1;
        for (int i = 0; i < c2.episodeKeys().size(); i++) {
            if (c2.episodeKeys().get(i).startsWith(TARGET_VID)
                    && c2.episodeKeys().get(i).contains(TARGET_START)) target = i;
        }
        List<Tick> tc = c2.episodes().get(target);

        Tick atTurn = tc.stream().filter(t -> !t.ts().isBefore(PHYS_TURN)).findFirst().orElse(null);
        System.out.printf("П.3 лидер на 06:43:44 = %s (short-семейство: %s)%n",
                atTurn.leader(), isShort(atTurn.leader()) ? "PASS" : "FAIL");

        double maxImpl = 0;
        long over100 = 0;
        List<String> nonMono = new ArrayList<>();
        for (int i = 1; i < tc.size(); i++) {
            Tick p = tc.get(i - 1);
            Tick t = tc.get(i);
            if (t.ts().isBefore(TURN_WIN_FROM) || t.ts().isAfter(TURN_WIN_TO)) continue;
            double dt = Math.max(t.t() - p.t(), 1);
            double vImpl = GeometryFixture.haversineMeters(
                    p.geo()[0], p.geo()[1], t.geo()[0], t.geo()[1]) / dt * 3.6;
            boolean sanctioned = !t.leader().equals(p.leader())
                    || t.mode().equals("RECOVERING") || t.mode().equals("NEW_TRIP");
            if (sanctioned && vImpl > 100) {
                System.out.printf("  П.3 санкционированный скачок вещания (метрика полёта, №22/П-3): "
                                + "%s v_impl=%.0f, лидер %s→%s, mode %s%n",
                        local(t.ts()), vImpl, p.leader(), t.leader(), t.mode());
                continue;
            }
            maxImpl = Math.max(maxImpl, vImpl);
            if (vImpl > 100) over100++;
            if (t.s() < p.s() && t.rawSpeedKmh() > 5) {
                nonMono.add(String.format(Locale.ROOT, "%s: s %.0f→%.0f, лидер %s→%s, mode %s",
                        local(t.ts()), p.s(), t.s(), p.leader(), t.leader(), t.mode()));
            }
        }
        System.out.printf("П.3 окно 06:40–06:50: max v_impl=%.0f; тиков >100=%d (критерий 0: %s); "
                        + "немонотонностей=%d (критерий ≤2: %s)%n",
                maxImpl, over100, over100 == 0 ? "PASS" : "FAIL",
                nonMono.size(), nonMono.size() <= 2 ? "PASS" : "FAIL");
        nonMono.forEach(s -> System.out.println("  немонотонность: " + s));

        List<Tick> newTrips = tc.stream().filter(Tick::newTrip).toList();
        boolean turnJudged = false;
        for (Tick nt : newTrips) {
            long lag = java.time.Duration.between(PHYS_TURN, nt.ts()).getSeconds();
            if (!turnJudged && lag >= 0) {
                turnJudged = true;
                System.out.printf("П.3 NEW_TRIP разворота @%s, лаг=%+d с (критерий ≤84: %s), "
                                + "лидер после=%s (критерий tail: %s)%n",
                        local(nt.ts()), lag,
                        lag <= 84 ? "PASS" : "FAIL", nt.leader(),
                        nt.leader().contains("short-tail") ? "PASS" : "FAIL");
            } else {
                System.out.printf("П.3 NEW_TRIP вне критерия (не событие разворота 06:43:44) @%s, "
                        + "лаг=%+d с, лидер после=%s%n", local(nt.ts()), lag, nt.leader());
            }
        }

        long zoneTicks = 0;
        long zoneShortTicks = 0;
        for (Tick t : tc) {
            double dPad = GeometryFixture.haversineMeters(
                    t.rawLatLon()[0], t.rawLatLon()[1], PAD[0], PAD[1]);
            if (dPad <= 750) {
                zoneTicks++;
                if (isShort(t.leader())) zoneShortTicks++;
            }
        }
        System.out.printf("П.3 тики short-семейства в зоне: %d из %d тиков борта в зоне (%.0f%%)%n",
                zoneShortTicks, zoneTicks, zoneTicks > 0 ? 100.0 * zoneShortTicks / zoneTicks : 0);

        double flightMax = 0;
        long flightViol = 0;
        List<Double> nis = new ArrayList<>();
        long shortTicks = 0;
        long total = 0;
        long newTripsAll = 0;
        long[] zones = new long[3];
        long pinSpells = 0;
        double pinMaxSec = 0;
        double exitJumpMax = 0;
        long transitFlips = 0;
        for (int e = 0; e < c2.episodes().size(); e++) {
            List<Tick> ticks = c2.episodes().get(e);
            String epKey = c2.episodeKeys().get(e);
            double pinStart = -1;
            for (int i = 0; i < ticks.size(); i++) {
                Tick t = ticks.get(i);
                total++;
                if (t.newTrip()) {
                    newTripsAll++;
                    String prevLeader = i > 0 ? ticks.get(i - 1).leader() : "—";
                    System.out.printf("  NEW_TRIP-реестр: %s @%s, лидер %s→%s%n",
                            epKey, local(t.ts()), prevLeader, t.leader());
                }
                if (isShort(t.leader())) {
                    shortTicks++;
                    double s = t.s();
                    if (s < 15000) zones[0]++;
                    else if (s < 17200) zones[1]++;
                    else zones[2]++;
                }
                if (t.leaderPinned() && pinStart < 0) {
                    pinStart = t.t();
                    pinSpells++;
                }
                if (!t.leaderPinned() && pinStart >= 0) {
                    pinMaxSec = Math.max(pinMaxSec, t.t() - pinStart);
                    pinStart = -1;
                }
                if (i == 0) continue;
                Tick p = ticks.get(i - 1);
                boolean switched = !t.leader().equals(p.leader());
                boolean sameLeaderDirection =
                        t.leader().endsWith("#d1") == p.leader().endsWith("#d1");
                if (switched && p.leaderPinned() && sameLeaderDirection) {
                    double jump = GeometryFixture.haversineMeters(
                            p.geo()[0], p.geo()[1], t.geo()[0], t.geo()[1]);
                    exitJumpMax = Math.max(exitJumpMax, jump);
                    System.out.printf("  exit-forward скачок @%s: %.0f м (гейт 950)%n",
                            local(t.ts()), jump);
                }
                if (switched && isShort(t.leader()) && !isShort(p.leader())
                        && t.leaderCum() < 150 && t.leaderPinned()) {
                    transitFlips++;
                }
                boolean sanctioned = t.mode().equals("RECOVERING") || t.mode().equals("NEW_TRIP")
                        || switched;
                if (!sanctioned) {
                    double dt = Math.max(t.t() - p.t(), 1);
                    double ratio = GeometryFixture.haversineMeters(
                            p.geo()[0], p.geo()[1], t.geo()[0], t.geo()[1]) / (dt * CFG.vMaxMs());
                    flightMax = Math.max(flightMax, ratio);
                    if (ratio > 1.5) flightViol++;
                }
                if (!Double.isNaN(t.nis())) nis.add(t.nis());
            }
        }
        double med = nis.isEmpty() ? Double.NaN
                : nis.stream().sorted().toList().get(nis.size() / 2);
        long over = nis.stream().filter(v -> v > 3.84).count();
        double overPct = nis.isEmpty() ? 0 : 100.0 * over / nis.size();
        System.out.printf(Locale.ROOT,
                "П.4 полёт %.2f/%d (критерий ≤7.03/71: %s); NIS n=%d median=%.4f (крит. 0.55±0.02: %s) "
                        + ">3.84=%.1f%% (крит. 17.9±0.5пп: %s); NEW_TRIP=%d (крит. 150±2: %s)%n",
                flightMax, flightViol,
                flightMax <= 7.04 && flightViol <= 71 ? "PASS" : "FAIL",
                nis.size(), med, Math.abs(med - 0.55) <= 0.02 ? "PASS" : "FAIL",
                overPct, Math.abs(overPct - 17.9) <= 0.5 ? "PASS" : "FAIL",
                newTripsAll, Math.abs(newTripsAll - 150) <= 2 ? "PASS" : "FAIL");
        System.out.printf("П.4 флипы на транзитах без стоянки (cum<150 при пине)=%d (критерий 0: %s); "
                        + "пин-паузы: n=%d, max=%.0f с; exit-скачок max=%.0f м (критерий ≤950: %s)%n",
                transitFlips, transitFlips == 0 ? "PASS" : "FAIL",
                pinSpells, pinMaxSec, exitJumpMax,
                exitJumpMax <= 950 ? "PASS" : "FAIL");
        System.out.printf("П.4 short-тики по зонам кольцо/складка/хвост = %d/%d/%d (C было 241/11/12); "
                + "доля short-семейства=%.2f%%%n", zones[0], zones[1], zones[2],
                total > 0 ? 100.0 * shortTicks / total : 0);
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
