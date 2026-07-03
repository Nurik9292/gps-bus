package biz.ugur.busroutebackend.replay.core;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.GpsFix;
import biz.ugur.busroutebackend.replay.PredictionModel;

import java.time.Instant;

public class MotionFilterCore implements PredictionModel, InnovationAware, StopAware {

    public enum Mode {
        ACQUIRING, TRACKING, DECELERATING, DWELL, DEPARTING, GPS_LOST, NO_GPS, RECOVERING,
        OFF_ROUTE, AT_TERMINAL, TURNING, NEW_TRIP
    }

    private final CoreConfig cfg;

    private boolean initialized;
    private double x;
    private double v;
    private double p00, p01, p10, p11;
    private Mode mode = Mode.ACQUIRING;
    private Instant lastFixTime;

    private int persistCounter;
    private int reanchorConfirms;
    private double reanchorCandidateS;
    private boolean recoveringFromFreeze;
    private int slowTicks;
    private int movingTicks;
    private int decelTicks;
    private double dwellSec;
    private long absDeviationEvents;

    private double lastNu = Double.NaN;
    private double lastS = Double.NaN;
    private boolean lastUpdateAccepted;

    private int nextStopIdx;
    private double lastRawSpeedKmh = Double.NaN;
    private double lastAcceptedZx = Double.NaN;
    private double minSpeedKmhInZone = Double.MAX_VALUE;
    private boolean dwellOutlierFlagged;
    private final java.util.List<StopEvent> events = new java.util.ArrayList<>();
    private java.util.List<Eta> lastEtas = java.util.List.of();

    public MotionFilterCore(CoreConfig cfg) {
        this.cfg = cfg;
    }

    @Override
    public void reset() {
        initialized = false;
        mode = Mode.ACQUIRING;
        lastFixTime = null;
        persistCounter = 0;
        reanchorConfirms = 0;
        slowTicks = movingTicks = decelTicks = 0;
        dwellSec = 0;
        absDeviationEvents = 0;
        lastNu = Double.NaN;
        lastS = Double.NaN;
        recoveringFromFreeze = false;
        nextStopIdx = 0;
        minSpeedKmhInZone = Double.MAX_VALUE;
        dwellOutlierFlagged = false;
        events.clear();
        lastEtas = java.util.List.of();
    }

    public long absDeviationEvents() {
        return absDeviationEvents;
    }

    public Mode mode() {
        return mode;
    }

    public boolean driftEventActive() {
        return persistCounter > 0 || mode == Mode.RECOVERING;
    }

    @Override
    public double lastInnovation() {
        return lastNu;
    }

    @Override
    public double lastInnovationVariance() {
        return lastS;
    }

    @Override
    public boolean lastUpdateAccepted() {
        return lastUpdateAccepted;
    }

    @Override
    public Estimate onFix(GpsFix fix, GeometryFixture g) {
        if (!initialized) {
            return initialize(fix, g);
        }
        double dTau = Math.max(0.0,
                (fix.timestamp().toEpochMilli() - lastFixTime.toEpochMilli()) / 1000.0);
        predictOver(dTau, g);
        lastFixTime = fix.timestamp();
        lastRawSpeedKmh = fix.speedKmh();

        if (mode == Mode.NO_GPS) {
            mode = Mode.RECOVERING;
            recoveringFromFreeze = true;
        }

        Snap snap = snapInWindow(fix, g, dTau);

        if (recoveringFromFreeze && snap.snapped()
                && Math.abs(snap.sOnLine() - x) > cfg.dReanchorMeters()) {
            reinitAt(snap.sOnLine(), fix);
            recoveringFromFreeze = false;
            lastNu = 0;
            lastS = cfg.pInitPos();
            lastUpdateAccepted = true;
            clampToLine(g);
            resyncNextStop(g);
            lastEtas = java.util.List.of();
            return new Estimate(x, v, Mode.RECOVERING.name(), Math.max(p00, 1e-6));
        }
        if (recoveringFromFreeze && snap.snapped()) {
            mode = Mode.TRACKING;
            recoveringFromFreeze = false;
        }
        double measSigma = measurementSigma(fix, snap.dSnap());
        double r = measSigma * measSigma;

        double nu = snap.sOnLine() - x;
        double s = p00 + r;
        lastNu = nu;
        lastS = s;
        boolean gatePassed = snap.snapped() && Math.abs(nu) <= cfg.gammaGate() * Math.sqrt(s);

        if (gatePassed && mode == Mode.RECOVERING) {
            mode = Mode.TRACKING;
            reanchorConfirms = 0;
        }

        if (gatePassed) {
            kalmanUpdate(nu, r, dTau);
            weakSpeedUpdate(fix);
            lastAcceptedZx = snap.sOnLine();
            lastUpdateAccepted = true;
        } else {
            lastUpdateAccepted = false;
            handleRejected(fix, snap);
        }

        clampToLine(g);
        controlAbsoluteDeviation(fix, g, snap);
        if (g.stops().isEmpty()) {
            stepModeBySpeed(fix, dTau);
        } else {
            stepStopLayer(fix, g, dTau);
        }
        recomputeEtas(fix, g);

        return new Estimate(x, v, mode.name(), Math.max(p00, 1e-6));
    }

    @Override
    public java.util.List<Eta> etas() {
        return lastEtas;
    }

    @Override
    public java.util.List<StopEvent> drainEvents() {
        var out = java.util.List.copyOf(events);
        events.clear();
        return out;
    }

    private void stepStopLayer(GpsFix fix, GeometryFixture g, double dTau) {
        if (mode == Mode.RECOVERING || mode == Mode.NO_GPS || mode == Mode.GPS_LOST) {
            if (mode == Mode.GPS_LOST) mode = Mode.TRACKING;
            resyncNextStop(g);
            return;
        }
        var stops = g.stops();
        if (x >= g.totalMeters() - cfg.epsTermMeters() && mode != Mode.AT_TERMINAL) {
            mode = Mode.AT_TERMINAL;
            events.add(new StopEvent(StopEventType.AT_TERMINAL, "terminal", fix.timestamp()));
            return;
        }
        if (mode == Mode.AT_TERMINAL) return;

        if (nextStopIdx >= stops.size()) return;
        var stop = stops.get(nextStopIdx);
        double sStop = stop.sMeters();
        double dist = sStop - x;
        double rawKmh = fix.speedKmh();

        if (dist >= -cfg.epsStopMeters() && dist <= cfg.epsArrMeters()) {
            minSpeedKmhInZone = Math.min(minSpeedKmhInZone, rawKmh);
        }

        switch (mode) {
            case TRACKING -> {
                if (dist <= cfg.epsArrMeters() && dist >= 0) {
                    decelTicks++;
                    if (decelTicks >= 1) {
                        mode = Mode.DECELERATING;
                        events.add(new StopEvent(StopEventType.DECEL_ENTER, stop.stopId(), fix.timestamp()));
                    }
                } else {
                    decelTicks = 0;
                }
                checkSkip(g, fix, stop, sStop);
            }
            case DECELERATING -> {
                if (Math.abs(sStop - x) <= cfg.epsStopMeters() && rawKmh < cfg.vStopKmh()) {
                    slowTicks++;
                    if (slowTicks >= cfg.hStop()) {
                        mode = Mode.DWELL;
                        x = sStop;
                        v = 0;
                        dwellSec = 0;
                        dwellOutlierFlagged = false;
                        events.add(new StopEvent(StopEventType.DWELL_ENTER, stop.stopId(), fix.timestamp()));
                    }
                } else {
                    slowTicks = 0;
                }
                checkSkip(g, fix, stop, sStop);
            }
            case DWELL -> {
                dwellSec += Math.max(dTau, cfg.dtSec());
                x = sStop;
                if (dwellSec > cfg.dwellMaxSec() && !dwellOutlierFlagged) {
                    dwellOutlierFlagged = true;
                    events.add(new StopEvent(StopEventType.DWELL_OUTLIER, stop.stopId(), fix.timestamp()));
                }
                boolean expired = dwellSec >= cfg.dwellExpectedSec();
                boolean moving = rawKmh >= cfg.vMoveKmh();
                if ((expired || moving) && dwellSec >= cfg.dwellMinSec()) {
                    mode = Mode.DEPARTING;
                    events.add(new StopEvent(StopEventType.DWELL_EXIT, stop.stopId(), fix.timestamp()));
                    advanceNextStop(stops.size());
                }
            }
            case DEPARTING -> {
                movingTicks = rawKmh >= cfg.vMoveKmh() ? movingTicks + 1 : 0;
                if (movingTicks >= cfg.hDep()) {
                    mode = Mode.TRACKING;
                    decelTicks = 0;
                }
            }
            default -> { }
        }
    }

    private void checkSkip(GeometryFixture g, GpsFix fix, GeometryFixture.StopPoint stop, double sStop) {
        double progress = Double.isNaN(lastAcceptedZx) ? x : Math.max(x, lastAcceptedZx);
        if (progress > sStop + cfg.epsStopMeters()) {
            if (minSpeedKmhInZone >= cfg.vMoveKmh()) {
                events.add(new StopEvent(StopEventType.SKIP, stop.stopId(), fix.timestamp()));
                v = Math.max(v, fix.speedKmh() / 3.6 * 0.8);
            }
            mode = Mode.TRACKING;
            advanceNextStop(g.stops().size());
        }
    }

    private void advanceNextStop(int total) {
        nextStopIdx = Math.min(nextStopIdx + 1, total);
        minSpeedKmhInZone = Double.MAX_VALUE;
        slowTicks = 0;
        decelTicks = 0;
        movingTicks = 0;
    }

    private void resyncNextStop(GeometryFixture g) {
        var stops = g.stops();
        int i = 0;
        while (i < stops.size() && stops.get(i).sMeters() < x - cfg.epsStopMeters()) i++;
        nextStopIdx = i;
        minSpeedKmhInZone = Double.MAX_VALUE;
    }

    private void recomputeEtas(GpsFix fix, GeometryFixture g) {
        var stops = g.stops();
        if (stops.isEmpty()) {
            lastEtas = java.util.List.of();
            return;
        }
        boolean reliable = mode != Mode.NO_GPS && mode != Mode.GPS_LOST && mode != Mode.RECOVERING;
        double vNow = v >= cfg.vMoveKmh() / 3.6 ? v : 0.0;
        double vCruise = cfg.vTargetMs();
        double a = cfg.aDepMs2();
        var out = new java.util.ArrayList<Eta>();
        double t = 0;
        double from = x;
        for (int i = Math.max(nextStopIdx, 0); i < stops.size() && out.size() < 5; i++) {
            double sStop = stops.get(i).sMeters();
            if (sStop < x - cfg.epsStopMeters()) continue;
            double dist = Math.max(0, sStop - from);
            if (i == nextStopIdx && mode != Mode.DWELL) {
                t += timeToStopKinematic(dist, vNow, vCruise, a);
            } else {
                t += segmentTimeStopToStop(dist, vCruise, a);
            }
            out.add(new Eta(stops.get(i).stopId(), t, reliable));
            if (mode == Mode.DWELL && i == nextStopIdx) {
                t += Math.max(0, cfg.dwellExpectedSec() - dwellSec);
            } else {
                t += cfg.dwellExpectedSec();
            }
            from = sStop;
        }
        lastEtas = java.util.List.copyOf(out);
    }

    private static double timeToStopKinematic(double dist, double vNow, double vCruise, double a) {
        double brakeFromNow = vNow * vNow / (2 * a);
        if (dist <= brakeFromNow) {
            return vNow > 0.1 ? 2 * dist / vNow : Math.sqrt(2 * dist / a);
        }
        double vPeak = Math.min(vCruise, Math.sqrt(a * dist + vNow * vNow / 2));
        double tAccel = (vPeak - vNow) / a;
        double tBrake = vPeak / a;
        double dAccel = (vPeak * vPeak - vNow * vNow) / (2 * a);
        double dBrake = vPeak * vPeak / (2 * a);
        double dCruise = Math.max(0, dist - dAccel - dBrake);
        return tAccel + tBrake + dCruise / vPeak;
    }

    private static double segmentTimeStopToStop(double dist, double vCruise, double a) {
        return timeToStopKinematic(dist, 0.0, vCruise, a);
    }

    private Estimate initialize(GpsFix fix, GeometryFixture g) {
        Snap snap = wholeLineSnap(fix, g);
        x = snap.sOnLine();
        v = Math.max(0, fix.speedKmh() / 3.6);
        p00 = cfg.pInitPos();
        p11 = cfg.pInitVel();
        p01 = p10 = 0;
        initialized = true;
        lastFixTime = fix.timestamp();
        mode = Mode.TRACKING;
        resyncNextStop(g);
        lastNu = 0;
        lastS = p00 + cfg.sigmaMeasDefaultMeters() * cfg.sigmaMeasDefaultMeters();
        lastUpdateAccepted = true;
        return new Estimate(x, v, Mode.ACQUIRING.name(), p00);
    }

    private void predictOver(double dTauSec, GeometryFixture g) {
        double remaining = dTauSec;
        while (remaining > 1e-9) {
            double dt = Math.min(cfg.dtSec(), remaining);
            double tauSinceFix = dTauSec - remaining;
            boolean lost = tauSinceFix > cfg.tLostSec();
            boolean frozen = tauSinceFix > cfg.tMaxSec();
            if (frozen) {
                if (mode != Mode.NO_GPS) mode = Mode.NO_GPS;
                v = 0;
            } else if (lost && isTrackingLike()) {
                mode = Mode.GPS_LOST;
            }
            if (mode == Mode.DEPARTING) {
                v = Math.min(v + cfg.aDepMs2() * dt, cfg.vTargetMs());
            }
            if (mode == Mode.DECELERATING && nextStopIdx < g.stops().size()
                    && tauSinceFix > 2 * cfg.dtSec()
                    && !Double.isNaN(lastRawSpeedKmh)
                    && lastRawSpeedKmh < cfg.vTargetMs() * 3.6 * 0.95) {
                double delta = Math.max(0, g.stops().get(nextStopIdx).sMeters() - x);
                double fDecel = delta < cfg.dDecelMeters() ? delta / cfg.dDecelMeters() : 1.0;
                v = Math.min(v, Math.max(0.5, cfg.vTargetMs() * fDecel));
            }
            if (mode != Mode.DWELL && mode != Mode.NO_GPS) {
                x = Math.min(x + v * dt, g.totalMeters());
            }
            p00 += 2 * p01 * dt + p11 * dt * dt + cfg.qPos() * dt;
            p01 += p11 * dt;
            p10 = p01;
            p11 += cfg.qVel() * dt;
            remaining -= dt;
        }
    }

    private boolean isTrackingLike() {
        return mode == Mode.TRACKING || mode == Mode.DECELERATING
                || mode == Mode.DEPARTING || mode == Mode.DWELL;
    }

    private record Snap(double sOnLine, double dSnap, boolean snapped) {}

    private Snap snapInWindow(GpsFix fix, GeometryFixture g, double dTau) {
        double window = cfg.w0Meters() + cfg.kWindowPerSpeed() * Math.max(v, 1.0) * Math.max(dTau, cfg.dtSec());
        Snap windowed = snapBetween(fix, g, x - window, x + window);
        if (windowed.snapped()) return windowed;
        return wholeLineSnap(fix, g);
    }

    private Snap wholeLineSnap(GpsFix fix, GeometryFixture g) {
        return snapBetween(fix, g, 0, g.totalMeters());
    }

    private Snap snapBetween(GpsFix fix, GeometryFixture g, double sFrom, double sTo) {
        var pts = g.points();
        double[] cum = g.cumDist();
        double best = Double.MAX_VALUE;
        double bestS = x;
        for (int i = 0; i < pts.size() - 1; i++) {
            if (cum[i + 1] < sFrom || cum[i] > sTo) continue;
            double[] a = pts.get(i);
            double[] b = pts.get(i + 1);
            double mLat = 111320.0;
            double mLon = 111320.0 * Math.cos(Math.toRadians((a[0] + b[0]) / 2));
            double dx = (b[1] - a[1]) * mLon;
            double dy = (b[0] - a[0]) * mLat;
            double l2 = dx * dx + dy * dy;
            double t = 0;
            if (l2 > 0) {
                double px = (fix.longitude() - a[1]) * mLon;
                double py = (fix.latitude() - a[0]) * mLat;
                t = Math.max(0, Math.min(1, (px * dx + py * dy) / l2));
            }
            double projLat = a[0] + t * (b[0] - a[0]);
            double projLon = a[1] + t * (b[1] - a[1]);
            double d = GeometryFixture.haversineMeters(fix.latitude(), fix.longitude(), projLat, projLon);
            if (d < best) {
                best = d;
                bestS = cum[i] + t * (cum[i + 1] - cum[i]);
            }
        }
        return new Snap(bestS, best, best <= cfg.dSnapMeters());
    }

    private double measurementSigma(GpsFix fix, double dSnap) {
        double base = cfg.sigmaMeasDefaultMeters();
        if (fix.accuracy() != null && fix.accuracy() > 0) {
            base = Math.max(cfg.accuracyRefMeters(), fix.accuracy());
        } else if (fix.hdop() != null && fix.hdop() > 0) {
            base = Math.max(cfg.accuracyRefMeters(), cfg.accuracyRefMeters() * fix.hdop());
        }
        double offTrackFactor = 1.0 + dSnap / cfg.dSnapMeters();
        return base * offTrackFactor;
    }

    private void kalmanUpdate(double nu, double r, double dTau) {
        double s = p00 + r;
        double kx = p00 / s;
        double kv = p10 / s;

        double dxRaw = kx * nu;
        double rMax = cfg.rMaxRate() * Math.max(v, 1.0) * Math.max(dTau, cfg.dtSec()) + cfg.rMaxBaseMeters();
        double dxApplied = Math.max(-rMax, Math.min(rMax, dxRaw));

        double dvRaw = kv * nu;
        double dvMax = cfg.aMaxMs2() * Math.max(dTau, cfg.dtSec());
        double dvApplied = Math.max(-dvMax, Math.min(dvMax, dvRaw));

        x += dxApplied;
        v += dvApplied;
        v = Math.max(0, Math.min(v, cfg.vMaxMs()));

        double onePkx = 1 - kx;
        double np00 = onePkx * p00;
        double np01 = onePkx * p01;
        double np10 = p10 - kv * p00;
        double np11 = p11 - kv * p01;
        p00 = np00;
        p01 = np01;
        p10 = np10;
        p11 = np11;
    }

    private void weakSpeedUpdate(GpsFix fix) {
        double zv = Math.max(0, fix.speedKmh() / 3.6);
        v = v + cfg.weakZvWeight() * (zv - v);
    }

    private void reinitAt(double sOnLine, GpsFix fix) {
        x = sOnLine;
        v = Math.max(0, fix.speedKmh() / 3.6);
        p00 = cfg.pInitPos();
        p01 = p10 = 0;
        p11 = cfg.pInitVel();
        mode = Mode.RECOVERING;
        persistCounter = 0;
        reanchorConfirms = 0;
    }

    private void handleRejected(GpsFix fix, Snap snap) {
        if (!snap.snapped()) return;
        if (mode == Mode.RECOVERING) {
            if (Math.abs(snap.sOnLine() - reanchorCandidateS) <= 150.0) {
                reanchorConfirms++;
            } else {
                reanchorCandidateS = snap.sOnLine();
                reanchorConfirms = 1;
            }
            if (reanchorConfirms >= cfg.mReanchor()) {
                double dTauEff = cfg.dtSec();
                double pull = (cfg.rMaxRate() * Math.max(v, 1.0) * dTauEff + cfg.rMaxBaseMeters())
                        * cfg.recoveryPullFactor();
                double delta = snap.sOnLine() - x;
                x += Math.max(-pull, Math.min(pull, delta));
                if (Math.abs(snap.sOnLine() - x) < 1.0) {
                    v = Math.max(0, fix.speedKmh() / 3.6);
                    p00 = cfg.pInitPos();
                    p01 = p10 = 0;
                    p11 = cfg.pInitVel();
                    mode = Mode.TRACKING;
                    persistCounter = 0;
                    reanchorConfirms = 0;
                }
            }
        }
    }

    private void clampToLine(GeometryFixture g) {
        x = Math.max(0, Math.min(x, g.totalMeters()));
    }

    private void controlAbsoluteDeviation(GpsFix fix, GeometryFixture g, Snap snap) {
        double[] est = g.pointAtS(x);
        double absDev = GeometryFixture.haversineMeters(fix.latitude(), fix.longitude(), est[0], est[1]);
        if (absDev > cfg.dMaxMeters()) {
            persistCounter++;
            absDeviationEvents++;
            if (persistCounter >= cfg.nPersist() && mode != Mode.RECOVERING) {
                mode = Mode.RECOVERING;
                reanchorCandidateS = snap.sOnLine();
                reanchorConfirms = 1;
            }
        } else {
            persistCounter = 0;
        }
    }

    private void stepModeBySpeed(GpsFix fix, double dTau) {
        double rawKmh = fix.speedKmh();
        if (mode == Mode.RECOVERING || mode == Mode.NO_GPS || mode == Mode.GPS_LOST) {
            if (mode == Mode.GPS_LOST) mode = Mode.TRACKING;
            return;
        }
        if (rawKmh < cfg.vStopKmh()) {
            slowTicks++;
            movingTicks = 0;
        } else if (rawKmh >= cfg.vMoveKmh()) {
            movingTicks++;
            slowTicks = 0;
        }
        switch (mode) {
            case TRACKING -> {
                if (rawKmh < cfg.vMoveKmh()) {
                    decelTicks++;
                    if (decelTicks >= cfg.hDec()) mode = Mode.DECELERATING;
                } else {
                    decelTicks = 0;
                }
            }
            case DECELERATING -> {
                if (slowTicks >= cfg.hStop()) {
                    mode = Mode.DWELL;
                    dwellSec = 0;
                    v = 0;
                } else if (movingTicks >= cfg.hDep()) {
                    mode = Mode.TRACKING;
                    decelTicks = 0;
                }
            }
            case DWELL -> {
                dwellSec += Math.max(dTau, cfg.dtSec());
                if (dwellSec >= cfg.dwellMinSec() && movingTicks >= 1) {
                    mode = Mode.DEPARTING;
                }
            }
            case DEPARTING -> {
                if (movingTicks >= cfg.hDep()) mode = Mode.TRACKING;
            }
            default -> { }
        }
    }
}
