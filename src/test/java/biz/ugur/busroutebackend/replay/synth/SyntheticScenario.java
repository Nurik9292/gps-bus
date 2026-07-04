package biz.ugur.busroutebackend.replay.synth;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.GpsFix;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SyntheticScenario {

    public record Params(
            long seed,
            double fixIntervalSec,
            double positionSigmaMeters,
            double tugdkAccuracyMeters,
            Instant startTime,
            String vehicleId,
            String plate,
            String route,
            int direction) {

        public static Params defaults(long seed, String route, int direction) {
            return new Params(seed, 7.0, 5.0, 5.0,
                    Instant.parse("2026-07-03T06:00:00Z"),
                    "veh-syn-" + seed, "SYN " + seed, route, direction);
        }
    }

    public record Track(List<GpsFix> fixes, List<double[]> truth) {}

    public record StopVisit(String stopId, double tArrivalSec, double tDepartSec) {}

    public record MultiStopTrack(List<GpsFix> fixes, List<double[]> truth, List<StopVisit> visits) {}

    public record TurnTrack(List<GpsFix> fixes, List<double[]> truth, double tFlipSec,
                            List<StopVisit> returnVisits) {}

    private SyntheticScenario() {}

    public static MultiStopTrack multiStopRun(GeometryFixture g, Params p,
                                              double startS, double endS,
                                              double cruiseMs, double accelMs2,
                                              double dwellExpectedSec, double dwellJitter,
                                              java.util.Set<String> skipStopIds,
                                              boolean trafficSaw) {
        return multiStopRun(g, p, startS, endS, cruiseMs, accelMs2,
                stopId -> dwellExpectedSec, dwellJitter, skipStopIds, trafficSaw);
    }

    public static MultiStopTrack multiStopRun(GeometryFixture g, Params p,
                                              double startS, double endS,
                                              double cruiseMs, double accelMs2,
                                              java.util.function.ToDoubleFunction<String> dwellExpectedFor,
                                              double dwellJitter,
                                              java.util.Set<String> skipStopIds,
                                              boolean trafficSaw) {
        Random rnd = new Random(p.seed());
        Random dwellRnd = new Random(p.seed() * 31 + 7);
        List<GpsFix> fixes = new ArrayList<>();
        List<double[]> truth = new ArrayList<>();
        List<StopVisit> visits = new ArrayList<>();

        var stops = g.stops().stream()
                .filter(sp -> sp.sMeters() > startS + 1 && sp.sMeters() < endS - 1)
                .toList();

        double s = startS;
        double v = 0;
        double t = 0;
        double nextEmit = 0;
        int stopIdx = 0;
        double dwellLeft = 0;
        Double arrivalT = null;
        String dwellStopId = null;
        double sawPhase = 0;

        double simDt = 0.5;
        double arrZone = 50.0;
        double arrSpeedMs = 5.0 / 3.6;

        while (s < endS - 0.5 && t < 36000) {
            GeometryFixture.StopPoint target = stopIdx < stops.size() ? stops.get(stopIdx) : null;
            boolean skip = target != null && skipStopIds.contains(target.stopId());

            if (dwellLeft > 0) {
                dwellLeft -= simDt;
                v = 0;
                if (dwellLeft <= 0) {
                    visits.add(new StopVisit(dwellStopId, arrivalT, t));
                    arrivalT = null;
                    dwellStopId = null;
                    stopIdx++;
                }
            } else {
                double vLimit = cruiseMs;
                if (trafficSaw) {
                    sawPhase += simDt;
                    if (sawPhase % 60 < 25) vLimit = cruiseMs;
                    else if (sawPhase % 60 < 40) vLimit = 0.0;
                    else vLimit = cruiseMs * 0.5;
                }
                if (target != null && !skip) {
                    double brakeDist = v * v / (2 * accelMs2);
                    if (target.sMeters() - s <= brakeDist + 1) {
                        vLimit = Math.min(vLimit, Math.sqrt(2 * accelMs2 * Math.max(0.3, target.sMeters() - s)));
                    }
                }
                if (v < vLimit) v = Math.min(v + accelMs2 * simDt, vLimit);
                else v = Math.max(v - accelMs2 * simDt, vLimit);
                s = Math.min(s + v * simDt, g.totalMeters());

                if (target != null && !skip
                        && arrivalT == null
                        && Math.abs(target.sMeters() - s) <= arrZone && v < arrSpeedMs) {
                    arrivalT = t;
                }
                if (target != null && !skip && target.sMeters() - s <= 0.5 && v < 1.0) {
                    s = target.sMeters();
                    v = 0;
                    dwellLeft = dwellExpectedFor.applyAsDouble(target.stopId())
                            * (1 - dwellJitter + 2 * dwellJitter * dwellRnd.nextDouble());
                    dwellStopId = target.stopId();
                    if (arrivalT == null) arrivalT = t;
                }
                if (target != null && skip && s > target.sMeters() + 1) {
                    stopIdx++;
                }
            }

            if (t >= nextEmit) {
                truth.add(new double[]{t, s, v});
                fixes.add(fixAtWithPerp(g, p, rnd, t, s, v, 0.0, 0.0));
                nextEmit += p.fixIntervalSec();
            }
            t += simDt;
        }
        return new MultiStopTrack(fixes, truth, visits);
    }

    public static TurnTrack terminalTurnRun(GeometryFixture gOut, GeometryFixture gBack, Params p,
                                            double approachStartS, double cruiseMs, double accelMs2,
                                            double turnDwellSec, double returnEndS,
                                            double stopDwellSec, double dwellJitter) {
        Sim sim = new Sim(p);
        Random dwellRnd = new Random(p.seed() * 31 + 7);
        drive(sim, gOut, dwellRnd, approachStartS, gOut.totalMeters(),
                cruiseMs, accelMs2, stopDwellSec, dwellJitter, 0, new ArrayList<>(), true);
        double tTurn = turnDwellSec * (0.8 + 0.4 * dwellRnd.nextDouble());
        stand(sim, gOut, gOut.totalMeters(), tTurn, 0);
        double tFlip = sim.t;
        List<StopVisit> backVisits = new ArrayList<>();
        drive(sim, gBack, dwellRnd, 0, returnEndS,
                cruiseMs, accelMs2, stopDwellSec, dwellJitter, 1, backVisits, false);
        return new TurnTrack(sim.fixes, sim.truth, tFlip, backVisits);
    }

    public static TurnTrack terminalStandstillRun(GeometryFixture gOut, Params p,
                                                  double approachStartS, double cruiseMs, double accelMs2,
                                                  double stopDwellSec, double standSec) {
        Sim sim = new Sim(p);
        Random dwellRnd = new Random(p.seed() * 31 + 7);
        drive(sim, gOut, dwellRnd, approachStartS, gOut.totalMeters(),
                cruiseMs, accelMs2, stopDwellSec, 0.3, 0, new ArrayList<>(), true);
        stand(sim, gOut, gOut.totalMeters(), standSec, 0);
        return new TurnTrack(sim.fixes, sim.truth, Double.NaN, List.of());
    }

    public static MultiStopTrack loopRun(GeometryFixture g, Params p,
                                         double startS, int laps,
                                         double cruiseMs, double accelMs2,
                                         double stopDwellSec, double dwellJitter) {
        Random rnd = new Random(p.seed());
        Random dwellRnd = new Random(p.seed() * 31 + 7);
        double l = g.totalMeters();
        record ContTarget(String stopId, double sCont) {}
        List<ContTarget> targets = new ArrayList<>();
        for (int lap = 0; lap < laps; lap++) {
            for (GeometryFixture.StopPoint sp : g.stops()) {
                double sCont = sp.sMeters() + lap * l;
                if (sCont > startS + 1) targets.add(new ContTarget(sp.stopId(), sCont));
            }
        }
        targets.sort(java.util.Comparator.comparingDouble(ContTarget::sCont));
        double endS = startS + laps * l;

        List<GpsFix> fixes = new ArrayList<>();
        List<double[]> truth = new ArrayList<>();
        List<StopVisit> visits = new ArrayList<>();
        double s = startS;
        double v = 0;
        double t = 0;
        double nextEmit = 0;
        int stopIdx = 0;
        double dwellLeft = 0;
        Double arrivalT = null;
        String dwellStopId = null;
        double simDt = 0.5;
        double arrZone = 50.0;
        double arrSpeedMs = 5.0 / 3.6;

        while (s < endS - 0.5 && t < 36000) {
            ContTarget target = stopIdx < targets.size() ? targets.get(stopIdx) : null;
            if (dwellLeft > 0) {
                dwellLeft -= simDt;
                v = 0;
                if (dwellLeft <= 0) {
                    visits.add(new StopVisit(dwellStopId, arrivalT, t));
                    arrivalT = null;
                    dwellStopId = null;
                    stopIdx++;
                }
            } else {
                double vLimit = cruiseMs;
                if (target != null) {
                    double brakeDist = v * v / (2 * accelMs2);
                    if (target.sCont() - s <= brakeDist + 1) {
                        vLimit = Math.min(vLimit,
                                Math.sqrt(2 * accelMs2 * Math.max(0.3, target.sCont() - s)));
                    }
                }
                if (v < vLimit) v = Math.min(v + accelMs2 * simDt, vLimit);
                else v = Math.max(v - accelMs2 * simDt, vLimit);
                s += v * simDt;
                if (target != null && arrivalT == null
                        && Math.abs(target.sCont() - s) <= arrZone && v < arrSpeedMs) {
                    arrivalT = t;
                }
                if (target != null && target.sCont() - s <= 0.5 && v < 1.0) {
                    s = target.sCont();
                    v = 0;
                    dwellLeft = stopDwellSec * (1 - dwellJitter + 2 * dwellJitter * dwellRnd.nextDouble());
                    dwellStopId = target.stopId();
                    if (arrivalT == null) arrivalT = t;
                }
            }
            if (t >= nextEmit) {
                double sMod = s % l;
                truth.add(new double[]{t, sMod, v, Math.floor(s / l)});
                fixes.add(fixAtWithPerp(g, p, rnd, t, sMod, v, 0.0, 0.0));
                nextEmit += p.fixIntervalSec();
            }
            t += simDt;
        }
        return new MultiStopTrack(fixes, truth, visits);
    }

    private static final class Sim {
        final Params p;
        final Random rnd;
        double t = 0;
        double nextEmit = 0;
        final List<GpsFix> fixes = new ArrayList<>();
        final List<double[]> truth = new ArrayList<>();

        Sim(Params p) {
            this.p = p;
            this.rnd = new Random(p.seed());
        }

        void emitIfDue(GeometryFixture g, int dir, double s, double v) {
            if (t >= nextEmit) {
                truth.add(new double[]{t, s, v, dir});
                fixes.add(fixAtWithPerp(g, p, rnd, t, s, v, 0.0, 0.0));
                nextEmit += p.fixIntervalSec();
            }
        }
    }

    private static void drive(Sim sim, GeometryFixture g, Random dwellRnd,
                              double fromS, double toS,
                              double cruiseMs, double accelMs2,
                              double stopDwellSec, double dwellJitter,
                              int dir, List<StopVisit> visits, boolean brakeIntoEnd) {
        var stops = g.stops().stream()
                .filter(sp -> sp.sMeters() > fromS + 1 && sp.sMeters() < toS - 1)
                .toList();
        double s = fromS;
        double v = 0;
        int stopIdx = 0;
        double dwellLeft = 0;
        Double arrivalT = null;
        String dwellStopId = null;
        double simDt = 0.5;
        double arrZone = 50.0;
        double arrSpeedMs = 5.0 / 3.6;

        while (s < toS - 0.5 && sim.t < 36000) {
            GeometryFixture.StopPoint target = stopIdx < stops.size() ? stops.get(stopIdx) : null;
            if (dwellLeft > 0) {
                dwellLeft -= simDt;
                v = 0;
                if (dwellLeft <= 0) {
                    visits.add(new StopVisit(dwellStopId, arrivalT, sim.t));
                    arrivalT = null;
                    dwellStopId = null;
                    stopIdx++;
                }
            } else {
                double vLimit = cruiseMs;
                double brakeTarget = target != null ? target.sMeters()
                        : (brakeIntoEnd ? toS : Double.MAX_VALUE);
                if (brakeTarget < Double.MAX_VALUE) {
                    double brakeDist = v * v / (2 * accelMs2);
                    if (brakeTarget - s <= brakeDist + 1) {
                        vLimit = Math.min(vLimit,
                                Math.sqrt(2 * accelMs2 * Math.max(0.3, brakeTarget - s)));
                    }
                }
                if (v < vLimit) v = Math.min(v + accelMs2 * simDt, vLimit);
                else v = Math.max(v - accelMs2 * simDt, vLimit);
                s = Math.min(s + v * simDt, g.totalMeters());
                if (target != null && arrivalT == null
                        && Math.abs(target.sMeters() - s) <= arrZone && v < arrSpeedMs) {
                    arrivalT = sim.t;
                }
                if (target != null && target.sMeters() - s <= 0.5 && v < 1.0) {
                    s = target.sMeters();
                    v = 0;
                    dwellLeft = stopDwellSec * (1 - dwellJitter + 2 * dwellJitter * dwellRnd.nextDouble());
                    dwellStopId = target.stopId();
                    if (arrivalT == null) arrivalT = sim.t;
                }
            }
            sim.emitIfDue(g, dir, s, v);
            sim.t += simDt;
        }
    }

    private static void stand(Sim sim, GeometryFixture g, double atS, double durationSec, int dir) {
        double until = sim.t + durationSec;
        double simDt = 0.5;
        while (sim.t < until) {
            sim.emitIfDue(g, dir, atS, 0.0);
            sim.t += simDt;
        }
    }

    public record OffRouteTrack(List<GpsFix> fixes, List<double[]> truth,
                                double tOffSec, double tReturnSec,
                                double sOffMeters, double sReturnMeters) {}

    public static OffRouteTrack offRouteRun(GeometryFixture g, Params p,
                                            double startS, double cruiseMs,
                                            double preRunSec, double detourSec, double postRunSec,
                                            double perpOffsetMeters, double returnAdvanceMeters,
                                            java.util.Set<Integer> corridorTouchDetourFixIdx,
                                            double stopMidDetourSec) {
        Random rnd = new Random(p.seed());
        List<GpsFix> fixes = new ArrayList<>();
        List<double[]> truth = new ArrayList<>();
        double sOff = startS + cruiseMs * preRunSec;
        double sReturn = sOff + returnAdvanceMeters;
        double driveSec = Math.max(1.0, detourSec - stopMidDetourSec);
        double vDetour = returnAdvanceMeters / driveSec;
        double stopFrom = preRunSec + driveSec / 2;
        double stopTo = stopFrom + stopMidDetourSec;
        double tReturn = preRunSec + detourSec;
        double total = tReturn + postRunSec;
        int detourFixIdx = 0;
        for (double t = 0; t <= total; t += p.fixIntervalSec()) {
            double s;
            double v;
            double perp = 0;
            if (t < preRunSec) {
                s = startS + cruiseMs * t;
                v = cruiseMs;
            } else if (t < tReturn) {
                double td = t - preRunSec;
                double driven = td <= stopFrom - preRunSec ? td
                        : td <= stopTo - preRunSec ? stopFrom - preRunSec
                        : td - stopMidDetourSec;
                s = sOff + vDetour * Math.min(driven, driveSec);
                v = (t >= stopFrom && t < stopTo) ? 0.0 : vDetour;
                perp = corridorTouchDetourFixIdx.contains(detourFixIdx) ? 30.0 : perpOffsetMeters;
                detourFixIdx++;
            } else {
                s = sReturn + cruiseMs * (t - tReturn);
                v = cruiseMs;
            }
            s = Math.min(s, g.totalMeters());
            truth.add(new double[]{t, s, v, perp > 50 ? 1 : 0});
            fixes.add(fixAtWithPerp(g, p, rnd, t, s, v, 0.0, perp));
        }
        return new OffRouteTrack(fixes, truth, preRunSec, tReturn, sOff, sReturn);
    }

    public static Track departureRamp(GeometryFixture g, Params p,
                                      double startS, double cruiseSpeedMs, double accelMs2,
                                      double standstillSec, double totalSec) {
        Random rnd = new Random(p.seed());
        List<GpsFix> fixes = new ArrayList<>();
        List<double[]> truth = new ArrayList<>();
        double s = startS;
        double v = 0;
        for (double t = 0; t <= totalSec; t += p.fixIntervalSec()) {
            if (t >= standstillSec) {
                double vNext = Math.min(v + accelMs2 * p.fixIntervalSec(), cruiseSpeedMs);
                s = Math.min(s + 0.5 * (v + vNext) * p.fixIntervalSec(), g.totalMeters());
                v = vNext;
            }
            truth.add(new double[]{t, s, v});
            fixes.add(fixAt(g, p, rnd, t, s, v, 0.0));
        }
        return new Track(fixes, truth);
    }

    public static Track cruiseWithAccumulatingSnapDrift(GeometryFixture g, Params p,
                                                        double startS, double cruiseSpeedMs,
                                                        double driftRatePerFixMeters, double totalSec) {
        Random rnd = new Random(p.seed());
        List<GpsFix> fixes = new ArrayList<>();
        List<double[]> truth = new ArrayList<>();
        double s = startS;
        int i = 0;
        for (double t = 0; t <= totalSec; t += p.fixIntervalSec()) {
            truth.add(new double[]{t, s, cruiseSpeedMs});
            double drift = driftRatePerFixMeters * i;
            double along = drift / Math.sqrt(2);
            double perp = drift / Math.sqrt(2);
            fixes.add(fixAtWithPerp(g, p, rnd, t, s, cruiseSpeedMs, along, perp));
            s = Math.min(s + cruiseSpeedMs * p.fixIntervalSec(), g.totalMeters());
            i++;
        }
        return new Track(fixes, truth);
    }

    public static Track cruiseClean(GeometryFixture g, Params p,
                                    double startS, double cruiseSpeedMs, double totalSec) {
        return cruiseWithForwardSnapBias(g, p, startS, cruiseSpeedMs, 0.0, totalSec);
    }

    public static Track cruiseWithGaps(GeometryFixture g, Params p,
                                       double startS, double cruiseSpeedMs, double totalSec,
                                       List<double[]> gapsStartDurSec) {
        Track full = cruiseClean(g, p, startS, cruiseSpeedMs, totalSec);
        List<GpsFix> kept = new ArrayList<>();
        List<double[]> keptTruth = new ArrayList<>();
        for (int i = 0; i < full.fixes().size(); i++) {
            double t = full.truth().get(i)[0];
            boolean inGap = gapsStartDurSec.stream().anyMatch(gd -> t >= gd[0] && t < gd[0] + gd[1]);
            if (!inGap) {
                kept.add(full.fixes().get(i));
                keptTruth.add(full.truth().get(i));
            }
        }
        return new Track(kept, keptTruth);
    }

    public static Track stationaryWithNoise(GeometryFixture g, Params p,
                                            double atS, double totalSec) {
        Random rnd = new Random(p.seed());
        List<GpsFix> fixes = new ArrayList<>();
        List<double[]> truth = new ArrayList<>();
        for (double t = 0; t <= totalSec; t += p.fixIntervalSec()) {
            truth.add(new double[]{t, atS, 0.0});
            fixes.add(fixAt(g, p, rnd, t, atS, 0.0, 0.0));
        }
        return new Track(fixes, truth);
    }

    public static Track cruiseWithForwardSnapBias(GeometryFixture g, Params p,
                                                  double startS, double cruiseSpeedMs,
                                                  double biasMeters, double totalSec) {
        Random rnd = new Random(p.seed());
        List<GpsFix> fixes = new ArrayList<>();
        List<double[]> truth = new ArrayList<>();
        double s = startS;
        for (double t = 0; t <= totalSec; t += p.fixIntervalSec()) {
            truth.add(new double[]{t, s, cruiseSpeedMs});
            fixes.add(fixAt(g, p, rnd, t, s, cruiseSpeedMs, biasMeters));
            s = Math.min(s + cruiseSpeedMs * p.fixIntervalSec(), g.totalMeters());
        }
        return new Track(fixes, truth);
    }

    public static GpsFix emitFix(GeometryFixture g, Params p, Random rnd,
                                 double t, double trueS, double speedMs) {
        return fixAtWithPerp(g, p, rnd, t, trueS, speedMs, 0.0, 0.0);
    }

    public static TurnTrack detourThenTerminalStandThenReturn(GeometryFixture gOut, GeometryFixture gBack,
                                                              Params p, double startS,
                                                              double cruiseMs, double accelMs2,
                                                              double detourStartS, double perpMeters,
                                                              double standAtTerminalSec, double returnEndS) {
        Sim sim = new Sim(p);
        Random dwellRnd = new Random(p.seed() * 31 + 7);
        drive(sim, gOut, dwellRnd, startS, detourStartS, cruiseMs, accelMs2, 20, 0.3, 0,
                new ArrayList<>(), false);
        drivePerp(sim, gOut, detourStartS, gOut.totalMeters(), cruiseMs, accelMs2, 0, perpMeters);
        stand(sim, gOut, gOut.totalMeters(), standAtTerminalSec, 0);
        double tFlip = sim.t;
        List<StopVisit> backVisits = new ArrayList<>();
        drive(sim, gBack, dwellRnd, 0, returnEndS, cruiseMs, accelMs2, 20, 0.3, 1, backVisits, false);
        return new TurnTrack(sim.fixes, sim.truth, tFlip, backVisits);
    }

    private static void drivePerp(Sim sim, GeometryFixture g, double fromS, double toS,
                                  double cruiseMs, double accelMs2, int dir, double perpMeters) {
        double simDt = 0.5;
        double s = fromS;
        double v = 0;
        while (s < toS - 0.5 && sim.t < 36000) {
            double brakeDist = v * v / (2 * accelMs2);
            double vLimit = toS - s <= brakeDist + 1
                    ? Math.sqrt(2 * accelMs2 * Math.max(0.3, toS - s))
                    : cruiseMs;
            if (v < vLimit) v = Math.min(v + accelMs2 * simDt, vLimit);
            else v = Math.max(v - accelMs2 * simDt, vLimit);
            s = Math.min(s + v * simDt, g.totalMeters());
            if (sim.t >= sim.nextEmit) {
                sim.truth.add(new double[]{sim.t, s, v, dir});
                sim.fixes.add(fixAtWithPerp(g, sim.p, sim.rnd, sim.t, s, v, 0.0, perpMeters));
                sim.nextEmit += sim.p.fixIntervalSec();
            }
            sim.t += simDt;
        }
    }

    private static GpsFix fixAt(GeometryFixture g, Params p, Random rnd,
                                double t, double trueS, double speedMs, double alongBiasMeters) {
        return fixAtWithPerp(g, p, rnd, t, trueS, speedMs, alongBiasMeters, 0.0);
    }

    private static GpsFix fixAtWithPerp(GeometryFixture g, Params p, Random rnd,
                                        double t, double trueS, double speedMs,
                                        double alongBiasMeters, double perpBiasMeters) {
        double emittedS = Math.min(trueS + alongBiasMeters, g.totalMeters());
        double[] pt = g.pointAtS(emittedS);
        double mLat = 111320.0;
        double mLon = 111320.0 * Math.cos(Math.toRadians(pt[0]));
        if (perpBiasMeters != 0.0) {
            double course = Math.toRadians(courseAt(g, emittedS));
            double perpLat = Math.cos(course + Math.PI / 2);
            double perpLon = Math.sin(course + Math.PI / 2);
            pt = new double[]{pt[0] + perpBiasMeters * perpLat / mLat,
                              pt[1] + perpBiasMeters * perpLon / mLon};
        }
        double lat = pt[0] + rnd.nextGaussian() * p.positionSigmaMeters() / mLat;
        double lon = pt[1] + rnd.nextGaussian() * p.positionSigmaMeters() / mLon;
        Instant ts = p.startTime().plusMillis((long) (t * 1000));
        double hdop = Math.max(0.6, 1.0 + rnd.nextGaussian() * 0.2);
        int sats = Math.max(5, 9 + (int) Math.round(rnd.nextGaussian()));
        return new GpsFix(p.vehicleId(), p.plate(), p.route(),
                round7(lat), round7(lon), speedMs * 3.6, courseAt(g, emittedS),
                speedMs * 3.6 >= 1.0, ts, p.direction(),
                round2(hdop), sats, p.tugdkAccuracyMeters(), ts);
    }

    private static double courseAt(GeometryFixture g, double s) {
        double[] a = g.pointAtS(Math.max(0, s - 5));
        double[] b = g.pointAtS(Math.min(g.totalMeters(), s + 5));
        double dLon = Math.toRadians(b[1] - a[1]);
        double y = Math.sin(dLon) * Math.cos(Math.toRadians(b[0]));
        double x = Math.cos(Math.toRadians(a[0])) * Math.sin(Math.toRadians(b[0]))
                - Math.sin(Math.toRadians(a[0])) * Math.cos(Math.toRadians(b[0])) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    private static double round7(double v) {
        return Math.round(v * 1e7) / 1e7;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    public static void saveTruth(java.nio.file.Path file, List<double[]> truth) {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("t_sec,s_true_m,v_true_ms");
            for (double[] row : truth) {
                lines.add(row[0] + "," + row[1] + "," + row[2]);
            }
            java.nio.file.Files.createDirectories(file.toAbsolutePath().getParent());
            java.nio.file.Files.write(file, lines);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
