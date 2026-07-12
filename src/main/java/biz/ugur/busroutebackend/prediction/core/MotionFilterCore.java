package biz.ugur.busroutebackend.prediction.core;





import biz.ugur.busroutebackend.prediction.core.history.SegmentDwellHistory;

import java.time.Instant;
import java.time.ZoneId;

public class MotionFilterCore implements PredictionModel, InnovationAware, StopAware {

    public static final ZoneId HISTORY_ZONE = ZoneId.of("Asia/Ashgabat");

    public enum Mode {
        ACQUIRING, TRACKING, DECELERATING, DWELL, DEPARTING, GPS_LOST, NO_GPS, RECOVERING,
        OFF_ROUTE, AT_TERMINAL, TURNING, NEW_TRIP
    }

    private final CoreConfig cfg;

    private SegmentDwellHistory history = SegmentDwellHistory.empty();

    private boolean initialized;
    private boolean loop;
    private int direction;
    private long tripId = 1;
    private int lapCount;
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

    private int turnStreak;
    private double turnFirstZx;
    private double turnLastZx;
    private int revertStreak;
    private double heldTurnS;
    private double currentVTarget;

    private int offRouteMisses;
    private double offRouteSec;
    private long offRouteTransitions;
    private int offRouteExitStreak;
    private double offRouteExitLastZx = Double.NaN;
    private Mode prevTravelMode = Mode.TRACKING;

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

    private HypothesisBank bank;
    private HypothesisBank.Hypothesis pendingDirectionSwitch;
    private boolean freezeReanchorGate;
    private boolean tripPartial;

    private RouteTopology.CityZone cityZone;
    private int cityEndDirection = Integer.MIN_VALUE;
    private int cityRunDeep;
    private int cityRunPlateau;
    private double citySpanFirstEpoch = Double.NaN;
    private double citySpanLastEpoch = Double.NaN;
    private double cityPrevFixEpoch = Double.NaN;
    private boolean cityZoneOccupied;
    private long cityTripIdAtZoneEntry = -1;
    private boolean cityPinActive;
    private int cityExitStreak;
    private double cityPrevZoneDist = Double.NaN;
    private double cityLastDeepDist = Double.NaN;
    private double cityLastPlateauDist = Double.NaN;

    public MotionFilterCore(CoreConfig cfg) {
        this.cfg = cfg;
        this.currentVTarget = cfg.vTargetMs();
        this.bank = new HypothesisBank(cfg);
    }

    public HypothesisBank bank() {
        return bank;
    }

    public MotionFilterCore withHistory(SegmentDwellHistory readOnlyHistory) {
        this.history = readOnlyHistory;
        return this;
    }

    @Override
    public void reset() {
        initialized = false;
        loop = false;
        direction = 0;
        tripId = 1;
        lapCount = 0;
        mode = Mode.ACQUIRING;
        lastFixTime = null;
        persistCounter = 0;
        reanchorConfirms = 0;
        slowTicks = movingTicks = decelTicks = 0;
        dwellSec = 0;
        absDeviationEvents = 0;
        turnStreak = 0;
        revertStreak = 0;
        heldTurnS = Double.NaN;
        currentVTarget = cfg.vTargetMs();
        offRouteMisses = 0;
        offRouteSec = 0;
        offRouteTransitions = 0;
        offRouteExitStreak = 0;
        offRouteExitLastZx = Double.NaN;
        prevTravelMode = Mode.TRACKING;
        termDepartMoveTicks = 0;
        termDepartMisses = 0;
        bank = new HypothesisBank(cfg);
        pendingDirectionSwitch = null;
        lastNu = Double.NaN;
        lastS = Double.NaN;
        recoveringFromFreeze = false;
        nextStopIdx = 0;
        freezeReanchorGate = false;
        tripPartial = false;
        minSpeedKmhInZone = Double.MAX_VALUE;
        dwellOutlierFlagged = false;
        events.clear();
        lastEtas = java.util.List.of();
        cityZone = null;
        cityEndDirection = Integer.MIN_VALUE;
        cityRunDeep = 0;
        cityRunPlateau = 0;
        citySpanFirstEpoch = Double.NaN;
        citySpanLastEpoch = Double.NaN;
        cityPrevFixEpoch = Double.NaN;
        cityZoneOccupied = false;
        cityTripIdAtZoneEntry = -1;
        cityPinActive = false;
        cityExitStreak = 0;
        cityPrevZoneDist = Double.NaN;
        cityLastDeepDist = Double.NaN;
        cityLastPlateauDist = Double.NaN;
    }

    public long absDeviationEvents() {
        return absDeviationEvents;
    }

    public Mode mode() {
        return mode;
    }

    public int direction() {
        return direction;
    }

    public long tripId() {
        return tripId;
    }

    public boolean currentTripPartial() {
        return tripPartial;
    }

    public int lapCount() {
        return lapCount;
    }

    public long offRouteTransitions() {
        return offRouteTransitions;
    }

    public boolean driftEventActive() {
        return persistCounter > 0 || mode == Mode.RECOVERING || mode == Mode.OFF_ROUTE;
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
    public Estimate onFix(GpsFix fix, RouteLine g) {
        return onFix(fix, RouteTopology.of(g));
    }

    public Estimate onFix(GpsFix fix, RouteTopology topo) {
        this.loop = topo.isLoop();
        bank.ensureBuilt(topo);
        if (!initialized) {
            Estimate first = initialize(fix, topo.first());
            bank.alignLeaderTo(direction);
            return first;
        }
        RouteLine g = bank.leader().geom();
        double dTau = Math.max(0.0,
                (fix.timestamp().toEpochMilli() - lastFixTime.toEpochMilli()) / 1000.0);
        predictOver(dTau, g);
        lastFixTime = fix.timestamp();
        lastRawSpeedKmh = fix.speedKmh();

        if (mode == Mode.NO_GPS) {
            mode = Mode.RECOVERING;
            recoveringFromFreeze = true;
        }

        bank.onFix(fix, dTau);
        cityZoneTrack(fix, topo);
        if (pendingDirectionSwitch != null) {
            return applyPendingDirectionSwitch(fix);
        }
        if (bankSwitchAllowed()) {
            bank.markLeaderAtFullTerminal(mode == Mode.AT_TERMINAL
                    || Math.abs(g.totalMeters() - x) <= cfg.epsArrMeters());
            HypothesisBank.Hypothesis cand = bank.pollConfirmedSwitch();
            if (cand != null) {
                Estimate switched = applyLeaderSwitch(cand, fix, g);
                if (switched != null) return switched;
                g = bank.leader().geom();
            }
        }

        if (leaderPinnedAtVariantTerminal()) {
            return broadcastVariantTerminal(fix);
        }
        cityArrivalStep(fix);
        if ((mode == Mode.AT_TERMINAL || mode == Mode.TURNING) && topo.hasOpposite(direction)) {
            Estimate handled = terminalTurnStep(fix, topo);
            if (handled != null) return handled;
            g = topo.geom(direction);
        }

        if (mode == Mode.OFF_ROUTE) {
            Estimate handled = offRouteStep(fix, g, dTau);
            if (handled != null) return handled;
        }

        currentVTarget = resolveVTarget(fix, g);
        Snap snap = snapInWindow(fix, g, dTau);
        boolean allHypothesesMissed = !snap.snapped() && bank.noneSnapped();
        offRouteMisses = allHypothesesMissed ? offRouteMisses + 1 : 0;
        if (freezeReanchorGate && bank.leader().progressStreak() >= cfg.kConfirmFreeze()) {
            freezeReanchorGate = false;
        }

        if (mode == Mode.AT_TERMINAL) {
            Estimate departedOffAxis = terminalDepartureStep(fix, allHypothesesMissed, g);
            if (departedOffAxis != null) return departedOffAxis;
            Estimate cityExit = cityExitStep(fix, topo);
            if (cityExit != null) return cityExit;
        }

        if (recoveringFromFreeze && snap.snapped()
                && Math.abs(forwardDelta(snap.sOnLine(), x, g)) > cfg.dReanchorMeters()) {
            reinitAt(snap.sOnLine(), fix);
            recoveringFromFreeze = false;
            freezeReanchorGate = true;
            bank.reseedAll();
            lastNu = 0;
            lastS = cfg.pInitPos();
            lastUpdateAccepted = true;
            clampToLine(g);
            resyncNextStop(g);
            lastEtas = java.util.List.of();
            if (cityWakePin(fix)) {
                recomputeEtas(fix, bank.leader().geom());
                return new Estimate(x, v, Mode.AT_TERMINAL.name(), Math.max(p00, 1e-6));
            }
            return new Estimate(x, v, Mode.RECOVERING.name(), Math.max(p00, 1e-6));
        }
        if (recoveringFromFreeze && snap.snapped()) {
            mode = Mode.TRACKING;
            recoveringFromFreeze = false;
            freezeReanchorGate = true;
            bank.reseedAll();
            cityWakePin(fix);
        }
        double measSigma = measurementSigma(fix, snap.dSnap());
        double r = measSigma * measSigma;

        double nu = forwardDelta(snap.sOnLine(), x, g);
        double s = p00 + r;
        lastNu = nu;
        lastS = s;
        boolean gatePassed = snap.snapped() && (cfg.rHdopEnabled()
                ? nu * nu <= cfg.gateNisThreshold() * s
                : Math.abs(nu) <= cfg.gammaGate() * Math.sqrt(s));

        if (gatePassed && (mode == Mode.RECOVERING || mode == Mode.NEW_TRIP)) {
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
            handleRejected(fix, snap, g);
        }

        clampToLine(g);
        controlAbsoluteDeviation(fix, g, snap);
        if (offRouteMisses >= cfg.kOffRoute() && isTravelMode()) {
            prevTravelMode = mode;
            mode = Mode.OFF_ROUTE;
            offRouteTransitions++;
            offRouteSec = 0;
            lastUpdateAccepted = false;
            recomputeEtas(fix, g);
            return new Estimate(x, v, Mode.OFF_ROUTE.name(), Math.max(p00, 1e-6));
        }
        if (g.stops().isEmpty()) {
            stepModeBySpeed(fix, dTau);
        } else {
            stepStopLayer(fix, g, dTau);
        }
        recomputeEtas(fix, g);

        return new Estimate(x, v, mode.name(), Math.max(p00, 1e-6));
    }

    private boolean leaderPinnedAtVariantTerminal() {
        return bank.leader().pinnedAtVariantTerminal()
                && bank.leader().geom().terminalZone() != null;
    }

    private Estimate broadcastVariantTerminal(GpsFix fix) {
        RouteLine gLeader = bank.leader().geom();
        x = gLeader.totalMeters();
        v = 0;
        if (mode != Mode.AT_TERMINAL) {
            mode = Mode.AT_TERMINAL;
            turnStreak = 0;
            revertStreak = 0;
            persistCounter = 0;
            reanchorConfirms = 0;
            termDepartMoveTicks = 0;
            termDepartMisses = 0;
            events.add(new StopEvent(StopEventType.AT_TERMINAL, "variant-terminal", fix.timestamp()));
        }
        lastUpdateAccepted = false;
        recomputeEtas(fix, gLeader);
        return new Estimate(x, v, Mode.AT_TERMINAL.name(), Math.max(p00, 1e-6));
    }

    private boolean isTravelMode() {
        return mode == Mode.TRACKING || mode == Mode.DECELERATING || mode == Mode.DEPARTING;
    }

    private int termDepartMoveTicks;
    private int termDepartMisses;

    private Estimate terminalDepartureStep(GpsFix fix, boolean allHypothesesMissed, RouteLine g) {
        if (fix.speedKmh() < cfg.vMoveKmh()) {
            termDepartMoveTicks = 0;
            return null;
        }
        termDepartMoveTicks++;
        if (!allHypothesesMissed) {
            termDepartMisses = 0;
            return null;
        }
        if (termDepartMoveTicks < cfg.nDepMoveConfirm()) {
            return null;
        }
        termDepartMisses++;
        if (termDepartMisses < cfg.kTermMissOffRoute()) {
            return null;
        }
        prevTravelMode = Mode.TRACKING;
        mode = Mode.OFF_ROUTE;
        offRouteTransitions++;
        offRouteSec = 0;
        offRouteMisses = 0;
        offRouteExitStreak = 0;
        termDepartMisses = 0;
        termDepartMoveTicks = 0;
        System.out.printf("терминал: выезд вне коридора (№15′) — AT_TERMINAL→OFF_ROUTE, "
                + "x̂ заморожен на %.1f%n", x);
        lastUpdateAccepted = false;
        recomputeEtas(fix, g);
        return new Estimate(x, v, Mode.OFF_ROUTE.name(), Math.max(p00, 1e-6));
    }

    private boolean bankSwitchAllowed() {
        return isTravelMode() || mode == Mode.AT_TERMINAL || mode == Mode.OFF_ROUTE
                || (mode == Mode.RECOVERING && !recoveringFromFreeze);
    }

    private Estimate applyLeaderSwitch(HypothesisBank.Hypothesis cand, GpsFix fix, RouteLine gOld) {
        double[] pOld = gOld.pointAtS(x);
        double[] pNew = cand.geom().pointAtS(cand.x());
        double dp = RouteLine.haversineMeters(pOld[0], pOld[1], pNew[0], pNew[1]);
        double forkGap = gOld.projectOntoRange(pNew[0], pNew[1], 0, gOld.totalMeters(), 0).distMeters();
        System.out.printf("банк: fork_gap в точке смены = %.1fм%n", forkGap);
        boolean wasOffRoute = mode == Mode.OFF_ROUTE;
        if (wasOffRoute) {
            offRouteTransitions++;
            offRouteMisses = 0;
            offRouteSec = 0;
            offRouteExitStreak = 0;
        }
        if (cand.direction() != direction) {
            if (freezeReanchorGate && cand.progressStreak() < cfg.kConfirmFreeze()) {
                System.out.printf("№22′: смена d после freeze не подтверждена (prog=%d < %d) — "
                        + "RECOVERING без trip_id++%n", cand.progressStreak(), cfg.kConfirmFreeze());
                mode = Mode.RECOVERING;
                lastUpdateAccepted = false;
                return new Estimate(x, v, Mode.RECOVERING.name(), Math.max(p00, 1e-6));
            }
            double closeTailGap = cand.geom().totalMeters() - cand.x();
            if (Math.abs(closeTailGap) <= cfg.epsCloseTailMeters()) {
                bank.commitSwitch();
                direction = cand.direction();
                x = Math.min(cand.x(), cand.geom().totalMeters());
                v = 0;
                mode = Mode.AT_TERMINAL;
                turnStreak = 0;
                revertStreak = 0;
                persistCounter = 0;
                reanchorConfirms = 0;
                System.out.printf("банк: закрывающая смена d у терминала (№22″) → %s, x=%.1f "
                                + "(|s−L|=%.1fм ≤ ε=%.0f), гео-скачок |dp|=%.1fм — тихая коррекция, "
                                + "без NEW_TRIP/trip_id++%n",
                        cand.variantId(), x, Math.abs(closeTailGap), cfg.epsCloseTailMeters(), dp);
                resyncNextStop(cand.geom());
                lastUpdateAccepted = false;
                lastEtas = java.util.List.of();
                return new Estimate(x, v, Mode.AT_TERMINAL.name(), Math.max(p00, 1e-6));
            }
            bank.commitSwitch();
            pendingDirectionSwitch = cand;
            mode = Mode.RECOVERING;
            System.out.printf("банк: смена лидера со сменой d → %s, |dp|=%.1fм (RECOVERING→NEW_TRIP)%n",
                    cand.variantId(), dp);
            lastUpdateAccepted = false;
            lastEtas = java.util.List.of();
            return new Estimate(x, v, Mode.RECOVERING.name(), Math.max(p00, 1e-6));
        }
        boolean exitFromVariantTerminal = leaderPinnedAtVariantTerminal();
        double quietThreshold = exitFromVariantTerminal
                ? bank.leader().geom().terminalZone().radiusMeters() + cfg.dReanchorMeters()
                : cfg.dSwitchSmoothMeters();
        bank.commitSwitch();
        double dpArc = Math.abs(cand.x() - x);
        if (exitFromVariantTerminal) {
            System.out.printf("банк: выход из терминальной зоны варианта (№28г): гео-скачок=%.1fм "
                    + "(гейт R_term+D_reanchor=%.0fм)%n", dp, quietThreshold);
        }
        if (dpArc <= quietThreshold) {
            System.out.printf("банк: ранняя смена лидера → %s, |dp|дуга=%.1fм, |dp|гео=%.1fм "
                            + "(дуга согласована — стягивание серией R_max; гео-переход на верную ветку)%n",
                    cand.variantId(), dpArc, dp);
            if (wasOffRoute) mode = prevTravelMode;
            else if (mode == Mode.AT_TERMINAL || mode == Mode.TURNING) mode = Mode.TRACKING;
            resyncNextStop(cand.geom());
            return null;
        }
        System.out.printf("банк: поздняя смена лидера → %s, скачок |dp|дуга=%.1fм, |dp|гео=%.1fм "
                        + "(П-3: измерен, не замаскирован)%n",
                cand.variantId(), dpArc, dp);
        reinitAt(cand.x(), fix);
        clampToLine(cand.geom());
        resyncNextStop(cand.geom());
        lastNu = 0;
        lastS = cfg.pInitPos();
        lastUpdateAccepted = true;
        lastEtas = java.util.List.of();
        return new Estimate(x, v, Mode.RECOVERING.name(), Math.max(p00, 1e-6));
    }

    private Estimate applyPendingDirectionSwitch(GpsFix fix) {
        HypothesisBank.Hypothesis target = pendingDirectionSwitch;
        pendingDirectionSwitch = null;
        direction = target.direction();
        x = target.x();
        v = Math.max(0, fix.speedKmh() / 3.6);
        p00 = cfg.pInitPos();
        p01 = p10 = 0;
        p11 = cfg.pInitVel();
        tripId++;
        mode = Mode.NEW_TRIP;
        cityPinActive = false;
        cityExitStreak = 0;
        tripPartial = freezeReanchorGate;
        if (tripPartial) {
            System.out.printf("№22′: NEW_TRIP (partial) — freeze-ре-привязка, start-of-trip = "
                    + "точка ре-привязки x=%.1f; леги вне стоп-стоп-истории №12%n", x);
        }
        freezeReanchorGate = false;
        turnStreak = 0;
        revertStreak = 0;
        persistCounter = 0;
        reanchorConfirms = 0;
        offRouteMisses = 0;
        offRouteSec = 0;
        slowTicks = movingTicks = decelTicks = 0;
        dwellSec = 0;
        dwellOutlierFlagged = false;
        lastAcceptedZx = Double.NaN;
        resyncNextStop(target.geom());
        lastEtas = java.util.List.of();
        lastNu = 0;
        lastS = cfg.pInitPos();
        lastUpdateAccepted = true;
        return new Estimate(x, v, Mode.NEW_TRIP.name(), Math.max(p00, 1e-6));
    }

    private void cityZoneTrack(GpsFix fix, RouteTopology topo) {
        cityZone = topo.cityZone();
        if (cityZone == null) return;
        if (cityEndDirection == Integer.MIN_VALUE) {
            cityEndDirection = resolveCityEndDirection(topo);
        }
        cityLastDeepDist = RouteLine.haversineMeters(fix.latitude(), fix.longitude(),
                cityZone.deepLat(), cityZone.deepLon());
        cityLastPlateauDist = RouteLine.haversineMeters(fix.latitude(), fix.longitude(),
                cityZone.plateauLat(), cityZone.plateauLon());
        boolean inDeep = cityLastDeepDist <= cfg.rCityDeepMeters();
        boolean inPlateau = cityLastPlateauDist <= cfg.rCityPlateauMeters();
        double epoch = fix.timestamp().toEpochMilli() / 1000.0;
        boolean occupiedNow = inDeep || inPlateau;
        if (occupiedNow && !cityZoneOccupied) {
            cityTripIdAtZoneEntry = tripId;
        }
        cityZoneOccupied = occupiedNow;
        cityRunDeep = inDeep ? cityRunDeep + 1 : 0;
        cityRunPlateau = inPlateau ? cityRunPlateau + 1 : 0;
        boolean feedGapBroken = !Double.isNaN(cityPrevFixEpoch)
                && epoch - cityPrevFixEpoch > cfg.gCitySpanGapSec();
        if (!inPlateau || feedGapBroken) {
            citySpanFirstEpoch = Double.NaN;
            citySpanLastEpoch = Double.NaN;
        }
        if (inPlateau && fix.speedKmh() <= cfg.vMoveKmh()) {
            if (Double.isNaN(citySpanFirstEpoch)) citySpanFirstEpoch = epoch;
            citySpanLastEpoch = epoch;
        }
        cityPrevFixEpoch = epoch;
        double zoneDist = Math.min(cityLastDeepDist, cityLastPlateauDist);
        if (!Double.isNaN(cityPrevZoneDist) && zoneDist > cityPrevZoneDist) {
            cityExitStreak++;
        } else {
            cityExitStreak = 0;
        }
        cityPrevZoneDist = zoneDist;
        if (cityPinActive && cityExitStreak >= cfg.kCityExit()
                && zoneDist > cfg.rCityPlateauMeters() + cfg.dCityExitDeltaMeters()) {
            cityPinActive = false;
            System.out.printf("М5-C: пин-флаг city снят устойчивым выходом при лидере d%d "
                    + "(k=%d, dist=%.0fм) — граница за банком%n",
                    direction, cityExitStreak, zoneDist);
        }
    }

    private int resolveCityEndDirection(RouteTopology topo) {
        RouteLine best = null;
        double bestDist = Double.MAX_VALUE;
        for (RouteLine g : topo.allGeometries()) {
            double[] end = g.pointAtS(g.totalMeters());
            double d = RouteLine.haversineMeters(end[0], end[1],
                    cityZone.deepLat(), cityZone.deepLon());
            if (d < bestDist) {
                bestDist = d;
                best = g;
            }
        }
        return best == null ? Integer.MIN_VALUE : best.direction();
    }

    private boolean cityDwellSpanReached() {
        return !Double.isNaN(citySpanFirstEpoch)
                && citySpanLastEpoch - citySpanFirstEpoch >= cfg.tCityDwellSec();
    }

    private void cityArrivalStep(GpsFix fix) {
        if (cityZone == null) return;
        if (mode == Mode.AT_TERMINAL || mode == Mode.TURNING
                || mode == Mode.NEW_TRIP || mode == Mode.DWELL) return;
        boolean fire = cityRunDeep >= cfg.mCityFixes()
                || (cityRunPlateau >= cfg.mCityFixes() && cityDwellSpanReached());
        if (!fire) return;
        if (tripId != cityTripIdAtZoneEntry) return;
        if (direction == cityEndDirection) {
            RouteLine g = bank.leader().geom();
            double xOld = x;
            enterCityTerminal(fix, g, "city-zone-A");
            System.out.printf("М5-A: city-прибытие по зоне (runD=%d, runP=%d, span=%.0fс, "
                    + "dD=%.0f, dP=%.0f) — AT_TERMINAL(city), x %.1f→%.1f%n",
                    cityRunDeep, cityRunPlateau,
                    Double.isNaN(citySpanFirstEpoch) ? 0.0 : citySpanLastEpoch - citySpanFirstEpoch,
                    cityLastDeepDist, cityLastPlateauDist, xOld, x);
        } else if (!cityPinActive) {
            cityPinActive = true;
            System.out.printf("М5-A: гео-прибытие в зону city при лидере d%d — пин-флаг "
                    + "без смены mode (выбор оси за банком)%n", direction);
        }
    }

    private boolean cityWakePin(GpsFix fix) {
        if (cityZone == null) return false;
        double dDeep = RouteLine.haversineMeters(fix.latitude(), fix.longitude(),
                cityZone.deepLat(), cityZone.deepLon());
        double dPlateau = RouteLine.haversineMeters(fix.latitude(), fix.longitude(),
                cityZone.plateauLat(), cityZone.plateauLon());
        if (dDeep > cfg.rCityDeepMeters() && dPlateau > cfg.rCityPlateauMeters()) return false;
        if (direction == cityEndDirection) {
            enterCityTerminal(fix, bank.leader().geom(), "city-zone-B");
            System.out.printf("М5-B: пробуждение/ре-анкер в зоне city (dD=%.0f, dP=%.0f) — "
                    + "пин восстановлен, AT_TERMINAL(city)%n", dDeep, dPlateau);
            return true;
        }
        if (!cityPinActive) {
            cityPinActive = true;
            System.out.printf("М5-B: пробуждение в зоне city при лидере d%d — пин-флаг "
                    + "без смены mode%n", direction);
        }
        return false;
    }

    private void enterCityTerminal(GpsFix fix, RouteLine g, String eventTag) {
        mode = Mode.AT_TERMINAL;
        x = g.totalMeters();
        v = 0;
        turnStreak = 0;
        revertStreak = 0;
        persistCounter = 0;
        reanchorConfirms = 0;
        termDepartMoveTicks = 0;
        termDepartMisses = 0;
        lastUpdateAccepted = false;
        events.add(new StopEvent(StopEventType.AT_TERMINAL, eventTag, fix.timestamp()));
    }

    private Estimate cityExitStep(GpsFix fix, RouteTopology topo) {
        if (cityZone == null || direction != cityEndDirection
                || !topo.hasOpposite(direction)) return null;
        if (cityExitStreak < cfg.kCityExit()) return null;
        if (Double.isNaN(cityPrevZoneDist)
                || cityPrevZoneDist <= cfg.rCityPlateauMeters() + cfg.dCityExitDeltaMeters()) {
            return null;
        }
        RouteLine gOpp = topo.opposite(direction);
        double sWindow = Math.min(gOpp.totalMeters(),
                cfg.rCityPlateauMeters() + cfg.dCityExitDeltaMeters() + cfg.wTurnWindowMeters());
        RouteLine.Projection pr = gOpp.projectOntoRange(
                fix.latitude(), fix.longitude(), 0, sWindow, 0);
        System.out.printf("М5-C: устойчивый выход из зоны city (k=%d, dist=%.0fм > R+ΔR=%.0f) — "
                + "NEW_TRIP без оконного требования [0;wTurn], старт x=%.1f%n",
                cityExitStreak, cityPrevZoneDist,
                cfg.rCityPlateauMeters() + cfg.dCityExitDeltaMeters(), pr.s());
        turnFirstZx = pr.s();
        cityExitStreak = 0;
        return startNewTrip(fix, gOpp);
    }

    private Estimate offRouteStep(GpsFix fix, RouteLine g, double dTau) {
        offRouteSec += dTau;
        double wBack = cfg.w0Meters();
        double wFwd = cfg.w0Meters() + cfg.vTargetMs() * offRouteSec;
        Snap corridor = snapBetween(fix, g, x - wBack, x + wFwd);
        boolean advancing = corridor.snapped()
                && (offRouteExitStreak == 0
                    || (corridor.sOnLine() - offRouteExitLastZx > 0
                        && corridor.sOnLine() - offRouteExitLastZx
                           <= cfg.vMaxMs() * Math.max(dTau, cfg.dtSec()) + 15.0));
        if (advancing) {
            offRouteExitStreak++;
            offRouteExitLastZx = corridor.sOnLine();
        } else if (corridor.snapped()) {
            offRouteExitStreak = 1;
            offRouteExitLastZx = corridor.sOnLine();
        } else {
            offRouteExitStreak = 0;
        }
        if (offRouteExitStreak < cfg.mOffRouteExit()) {
            lastUpdateAccepted = false;
            recomputeEtas(fix, g);
            return new Estimate(x, v, Mode.OFF_ROUTE.name(), Math.max(p00, 1e-6));
        }
        offRouteTransitions++;
        offRouteMisses = 0;
        offRouteSec = 0;
        offRouteExitStreak = 0;
        bank.reseedAll();
        double gap = Math.abs(forwardDelta(corridor.sOnLine(), x, g));
        if (gap > cfg.dReanchorMeters()) {
            reinitAt(corridor.sOnLine(), fix);
            lastNu = 0;
            lastS = cfg.pInitPos();
            lastUpdateAccepted = true;
            clampToLine(g);
            resyncNextStop(g);
            lastEtas = java.util.List.of();
            if (cityWakePin(fix)) {
                recomputeEtas(fix, bank.leader().geom());
                return new Estimate(x, v, Mode.AT_TERMINAL.name(), Math.max(p00, 1e-6));
            }
            return new Estimate(x, v, Mode.RECOVERING.name(), Math.max(p00, 1e-6));
        }
        mode = prevTravelMode;
        return null;
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

    private Estimate terminalTurnStep(GpsFix fix, RouteTopology topo) {
        RouteLine gOpp = topo.opposite(direction);
        Snap opp = snapBetween(fix, gOpp, 0, cfg.wTurnWindowMeters());
        boolean advancing = opp.snapped()
                && (turnStreak == 0 || opp.sOnLine() > turnLastZx + 0.5);
        if (advancing) {
            if (turnStreak == 0) turnFirstZx = opp.sOnLine();
            turnLastZx = opp.sOnLine();
            turnStreak++;
        } else if (opp.snapped()) {
            turnStreak = 1;
            turnFirstZx = turnLastZx = opp.sOnLine();
        } else {
            turnStreak = 0;
        }

        if (mode == Mode.AT_TERMINAL) {
            boolean confirmed = turnStreak >= cfg.nTurnConfirm()
                    && turnLastZx - turnFirstZx >= cfg.dTurnConfirmMeters();
            if (confirmed) {
                mode = Mode.TURNING;
                heldTurnS = x;
                revertStreak = 0;
                return new Estimate(heldTurnS, 0.0, Mode.TURNING.name(), Math.max(p00, 1e-6));
            }
            return null;
        }

        if (advancing) {
            return startNewTrip(fix, gOpp);
        }
        RouteLine g = topo.geom(direction);
        Snap back = snapBetween(fix, g,
                g.totalMeters() - cfg.wTurnWindowMeters(), g.totalMeters());
        revertStreak = back.snapped() ? revertStreak + 1 : 0;
        if (revertStreak >= cfg.kTurnRevert()) {
            mode = Mode.AT_TERMINAL;
            turnStreak = 0;
            revertStreak = 0;
        }
        return new Estimate(heldTurnS, 0.0, mode.name(), Math.max(p00, 1e-6));
    }

    private Estimate startNewTrip(GpsFix fix, RouteLine gNew) {
        direction = gNew.direction();
        x = turnFirstZx;
        v = Math.max(0, fix.speedKmh() / 3.6);
        p00 = cfg.pInitPos();
        p01 = p10 = 0;
        p11 = cfg.pInitVel();
        tripId++;
        mode = Mode.NEW_TRIP;
        tripPartial = false;
        freezeReanchorGate = false;
        cityPinActive = false;
        cityExitStreak = 0;
        turnStreak = 0;
        revertStreak = 0;
        persistCounter = 0;
        reanchorConfirms = 0;
        offRouteMisses = 0;
        offRouteSec = 0;
        slowTicks = movingTicks = decelTicks = 0;
        dwellSec = 0;
        dwellOutlierFlagged = false;
        lastAcceptedZx = Double.NaN;
        resyncNextStop(gNew);
        bank.alignLeaderTo(direction);
        lastEtas = java.util.List.of();
        lastNu = 0;
        lastS = cfg.pInitPos();
        lastUpdateAccepted = true;
        return new Estimate(x, v, Mode.NEW_TRIP.name(), Math.max(p00, 1e-6));
    }

    private boolean terminalOwned(RouteLine.StopPoint stop, RouteLine g) {
        return !loop && (stop.sMeters() >= g.totalMeters() - cfg.epsTermMeters()
                || stop.sMeters() <= cfg.epsTermMeters());
    }

    private void stepStopLayer(GpsFix fix, RouteLine g, double dTau) {
        if (mode == Mode.RECOVERING || mode == Mode.NO_GPS || mode == Mode.GPS_LOST) {
            if (mode == Mode.GPS_LOST) mode = Mode.TRACKING;
            resyncNextStop(g);
            return;
        }
        var stops = g.stops();
        if (!loop && x >= g.totalMeters() - cfg.epsTermMeters()
                && mode != Mode.AT_TERMINAL && mode != Mode.TURNING
                && mode != Mode.NEW_TRIP && mode != Mode.DWELL) {
            mode = Mode.AT_TERMINAL;
            turnStreak = 0;
            termDepartMoveTicks = 0;
            termDepartMisses = 0;
            events.add(new StopEvent(StopEventType.AT_TERMINAL, "terminal", fix.timestamp()));
            return;
        }
        if (mode == Mode.AT_TERMINAL) {
            x = g.totalMeters();
            v = 0;
            return;
        }
        if (mode == Mode.TURNING || mode == Mode.NEW_TRIP || mode == Mode.OFF_ROUTE) return;

        while (nextStopIdx < stops.size() && terminalOwned(stops.get(nextStopIdx), g)) nextStopIdx++;
        if (nextStopIdx >= stops.size()) {
            if (loop && !stops.isEmpty()) nextStopIdx = 0;
            else return;
        }
        var stop = stops.get(nextStopIdx);
        double sStop = stop.sMeters();
        double dist = forwardDelta(sStop, x, g);
        double rawKmh = fix.speedKmh();
        double distMeas = lastUpdateAccepted && !Double.isNaN(lastAcceptedZx)
                ? forwardDelta(sStop, lastAcceptedZx, g)
                : Double.NaN;
        boolean inZoneByMeas = !Double.isNaN(distMeas)
                && distMeas >= -cfg.epsStopMeters() && distMeas <= cfg.epsArrMeters();

        if ((dist >= -cfg.epsStopMeters() && dist <= cfg.epsArrMeters()) || inZoneByMeas) {
            minSpeedKmhInZone = Math.min(minSpeedKmhInZone, rawKmh);
        }

        switch (mode) {
            case TRACKING -> {
                if ((dist <= cfg.epsArrMeters() && dist >= 0) || inZoneByMeas) {
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
                if (Math.abs(forwardDelta(sStop, x, g)) <= cfg.epsStopMeters() && rawKmh < cfg.vStopKmh()) {
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
                boolean moving = rawKmh >= cfg.vMoveKmh();
                if (moving && dwellSec >= cfg.dwellMinSec()) {
                    mode = Mode.DEPARTING;
                    events.add(new StopEvent(StopEventType.DWELL_EXIT, stop.stopId(), fix.timestamp()));
                    advanceNextStop(g);
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

    private void checkSkip(RouteLine g, GpsFix fix, RouteLine.StopPoint stop, double sStop) {
        double progress;
        if (Double.isNaN(lastAcceptedZx)) {
            progress = x;
        } else if (loop) {
            progress = forwardDelta(lastAcceptedZx, x, g) > 0 ? lastAcceptedZx : x;
        } else {
            progress = Math.max(x, lastAcceptedZx);
        }
        if (forwardDelta(sStop, progress, g) < -cfg.epsStopMeters()) {
            if (minSpeedKmhInZone >= cfg.vMoveKmh()) {
                events.add(new StopEvent(StopEventType.SKIP, stop.stopId(), fix.timestamp()));
                v = Math.max(v, fix.speedKmh() / 3.6 * 0.8);
            }
            mode = Mode.TRACKING;
            advanceNextStop(g);
        }
    }

    private void advanceNextStop(RouteLine g) {
        int total = g.stops().size();
        if (loop && total > 0) {
            nextStopIdx = (nextStopIdx + 1) % total;
        } else {
            nextStopIdx = Math.min(nextStopIdx + 1, total);
        }
        minSpeedKmhInZone = Double.MAX_VALUE;
        slowTicks = 0;
        decelTicks = 0;
        movingTicks = 0;
    }

    private void resyncNextStop(RouteLine g) {
        var stops = g.stops();
        if (loop) {
            double l = g.totalMeters();
            int best = 0;
            double bestFwd = Double.MAX_VALUE;
            for (int i = 0; i < stops.size(); i++) {
                double fwd = (((stops.get(i).sMeters() - x + cfg.epsStopMeters()) % l) + l) % l;
                if (fwd < bestFwd) {
                    bestFwd = fwd;
                    best = i;
                }
            }
            nextStopIdx = best;
        } else {
            int i = 0;
            while (i < stops.size() && stops.get(i).sMeters() < x - cfg.epsStopMeters()) i++;
            nextStopIdx = i;
        }
        minSpeedKmhInZone = Double.MAX_VALUE;
    }

    private double dwellExpectedFor(String stopId) {
        return history.dwellSec(stopId, cfg.historyNMin()).orElse(cfg.dwellExpectedSec());
    }

    private double resolveVTarget(GpsFix fix, RouteLine g) {
        var stops = g.stops();
        if (stops.isEmpty() || nextStopIdx >= stops.size()) return cfg.vTargetMs();
        int prevIdx = nextStopIdx == 0 ? (loop ? stops.size() - 1 : -1) : nextStopIdx - 1;
        if (prevIdx < 0) return cfg.vTargetMs();
        var prev = stops.get(prevIdx);
        var next = stops.get(nextStopIdx);
        var zdt = fix.timestamp().atZone(HISTORY_ZONE);
        var hist = history.segTravelSec(prev.stopId(), next.stopId(),
                zdt.getHour(), zdt.getDayOfWeek().getValue() >= 6, cfg.historyNMin());
        if (hist.isEmpty()) return cfg.vTargetMs();
        double segLen = legDistance(next.sMeters(), prev.sMeters(), g, false);
        double vSeg = segLen / Math.max(1.0, hist.getAsDouble());
        return Math.max(2.0, Math.min(vSeg, cfg.vMaxMs()));
    }

    private void recomputeEtas(GpsFix fix, RouteLine g) {
        var stops = g.stops();
        if (stops.isEmpty()) {
            lastEtas = java.util.List.of();
            return;
        }
        boolean reliable = mode != Mode.NO_GPS && mode != Mode.GPS_LOST
                && mode != Mode.RECOVERING && mode != Mode.OFF_ROUTE;
        double vNow = v >= cfg.vMoveKmh() / 3.6 ? v : 0.0;
        double vCruise = cfg.vTargetMs();
        double a = cfg.aDepMs2();
        var zdt = fix.timestamp().atZone(HISTORY_ZONE);
        int hourBin = zdt.getHour();
        boolean weekend = zdt.getDayOfWeek().getValue() >= 6;
        var out = new java.util.ArrayList<Eta>();
        double t = 0;
        double from = x;
        String prevStopId = null;
        for (int k = 0; k < stops.size() && out.size() < 5; k++) {
            int i;
            if (loop) {
                i = (nextStopIdx % stops.size() + k) % stops.size();
            } else {
                i = nextStopIdx + k;
                if (i >= stops.size()) break;
            }
            var sp = stops.get(i);
            double sStop = sp.sMeters();
            if (!loop && sStop < x - cfg.epsStopMeters()) continue;
            double dist = legDistance(sStop, from, g, prevStopId == null);
            if (prevStopId != null) {
                var hist = history.segTravelSec(prevStopId, sp.stopId(), hourBin, weekend, cfg.historyNMin());
                t += hist.isPresent() ? hist.getAsDouble() : segmentTimeStopToStop(dist, vCruise, a);
            } else if (k == 0 && mode != Mode.DWELL) {
                t += timeToStopKinematic(dist, vNow, vCruise, a);
            } else {
                t += segmentTimeStopToStop(dist, vCruise, a);
            }
            out.add(new Eta(sp.stopId(), t, reliable));
            if (mode == Mode.DWELL && k == 0) {
                t += Math.max(0, dwellExpectedFor(sp.stopId()) - dwellSec);
            } else {
                t += dwellExpectedFor(sp.stopId());
            }
            from = sStop;
            prevStopId = sp.stopId();
        }
        lastEtas = java.util.List.copyOf(out);
    }

    private double legDistance(double toS, double fromS, RouteLine g, boolean firstLeg) {
        if (!loop) return Math.max(0, toS - fromS);
        double l = g.totalMeters();
        double d = (((toS - fromS) % l) + l) % l;
        if (firstLeg && d > l - cfg.epsStopMeters()) return 0;
        return d;
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

    private Estimate initialize(GpsFix fix, RouteLine g) {
        direction = g.direction();
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

    private void predictOver(double dTauSec, RouteLine g) {
        double remaining = dTauSec;
        while (remaining > 1e-9) {
            double dt = Math.min(cfg.dtSec(), remaining);
            double tauSinceFix = dTauSec - remaining;
            boolean lost = tauSinceFix > cfg.tLostSec();
            boolean frozen = tauSinceFix > cfg.tMaxSec();
            if (frozen) {
                if (mode != Mode.NO_GPS) mode = Mode.NO_GPS;
                v = 0;
            } else if (lost && mode == Mode.TURNING) {
                mode = Mode.GPS_LOST;
                turnStreak = 0;
                revertStreak = 0;
            } else if (lost && isTrackingLike()) {
                mode = Mode.GPS_LOST;
            }
            if (mode == Mode.DEPARTING) {
                v = Math.min(v + cfg.aDepMs2() * dt, currentVTarget);
            }
            if (mode == Mode.DECELERATING && nextStopIdx < g.stops().size()
                    && tauSinceFix > 2 * cfg.dtSec()
                    && !Double.isNaN(lastRawSpeedKmh)
                    && lastRawSpeedKmh < currentVTarget * 3.6 * 0.95) {
                double delta = Math.max(0, forwardDelta(g.stops().get(nextStopIdx).sMeters(), x, g));
                double fDecel = delta < cfg.dDecelMeters() ? delta / cfg.dDecelMeters() : 1.0;
                v = Math.min(v, Math.max(0.5, currentVTarget * fDecel));
            }
            if (mode != Mode.DWELL && mode != Mode.NO_GPS && mode != Mode.TURNING
                    && mode != Mode.OFF_ROUTE) {
                x = x + v * dt;
                if (loop) {
                    double l = g.totalMeters();
                    while (x >= l) {
                        x -= l;
                        lapCount++;
                    }
                } else {
                    x = Math.min(x, g.totalMeters());
                }
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

    private double forwardDelta(double toS, double fromS, RouteLine g) {
        double d = toS - fromS;
        if (!loop) return d;
        double l = g.totalMeters();
        d = d % l;
        if (d > l / 2) d -= l;
        if (d < -l / 2) d += l;
        return d;
    }

    private record Snap(double sOnLine, double dSnap, boolean snapped) {}

    private Snap snapInWindow(GpsFix fix, RouteLine g, double dTau) {
        double window = cfg.w0Meters() + cfg.kWindowPerSpeed() * Math.max(v, 1.0) * Math.max(dTau, cfg.dtSec());
        Snap windowed;
        if (loop) {
            windowed = snapAroundLoop(fix, g, x, window);
        } else {
            windowed = snapBetween(fix, g, x - window, x + window);
        }
        if (windowed.snapped()) return windowed;
        return wholeLineSnap(fix, g);
    }

    private Snap snapAroundLoop(GpsFix fix, RouteLine g, double center, double window) {
        double l = g.totalMeters();
        double lo = center - window;
        double hi = center + window;
        if (lo >= 0 && hi <= l) return snapBetween(fix, g, lo, hi);
        Snap a = snapBetween(fix, g, Math.max(0, lo), Math.min(l, hi));
        Snap b = lo < 0
                ? snapBetween(fix, g, l + lo, l)
                : snapBetween(fix, g, 0, hi - l);
        return a.dSnap() <= b.dSnap() ? a : b;
    }

    private Snap wholeLineSnap(GpsFix fix, RouteLine g) {
        return snapBetween(fix, g, 0, g.totalMeters());
    }

    private Snap snapBetween(GpsFix fix, RouteLine g, double sFrom, double sTo) {
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
            double d = RouteLine.haversineMeters(fix.latitude(), fix.longitude(), projLat, projLon);
            if (d < best) {
                best = d;
                bestS = cum[i] + t * (cum[i + 1] - cum[i]);
            }
        }
        return new Snap(bestS, best, best <= cfg.dSnapMeters());
    }

    private double measurementSigma(GpsFix fix, double dSnap) {
        double base;
        if (cfg.rHdopEnabled()) {
            double h = fix.hdop() != null && fix.hdop() > 0 ? fix.hdop() : 1.0;
            double floor = cfg.rHdopAMeters() + cfg.rHdopBMetersPerHdop() * 0.5;
            base = Math.max(floor, cfg.rHdopAMeters() + cfg.rHdopBMetersPerHdop() * h);
        } else {
            base = cfg.sigmaMeasDefaultMeters();
            if (fix.accuracy() != null && fix.accuracy() > 0) {
                base = Math.max(cfg.accuracyRefMeters(), fix.accuracy());
            } else if (fix.hdop() != null && fix.hdop() > 0) {
                base = Math.max(cfg.accuracyRefMeters(), cfg.accuracyRefMeters() * fix.hdop());
            }
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
        offRouteMisses = 0;
        offRouteSec = 0;
    }

    private void handleRejected(GpsFix fix, Snap snap, RouteLine g) {
        if (!snap.snapped()) return;
        if (mode == Mode.RECOVERING) {
            if (Math.abs(forwardDelta(snap.sOnLine(), reanchorCandidateS, g)) <= 150.0) {
                reanchorConfirms++;
            } else {
                reanchorCandidateS = snap.sOnLine();
                reanchorConfirms = 1;
            }
            if (reanchorConfirms >= cfg.mReanchor()) {
                double dTauEff = cfg.dtSec();
                double pull = (cfg.rMaxRate() * Math.max(v, 1.0) * dTauEff + cfg.rMaxBaseMeters())
                        * cfg.recoveryPullFactor();
                double delta = forwardDelta(snap.sOnLine(), x, g);
                x += Math.max(-pull, Math.min(pull, delta));
                if (Math.abs(forwardDelta(snap.sOnLine(), x, g)) < 1.0) {
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

    private void clampToLine(RouteLine g) {
        if (loop) {
            double l = g.totalMeters();
            while (x >= l) {
                x -= l;
                lapCount++;
            }
            while (x < 0) {
                x += l;
                lapCount = Math.max(0, lapCount - 1);
            }
        } else {
            x = Math.max(0, Math.min(x, g.totalMeters()));
        }
    }

    private void controlAbsoluteDeviation(GpsFix fix, RouteLine g, Snap snap) {
        double[] est = g.pointAtS(x);
        double absDev = RouteLine.haversineMeters(fix.latitude(), fix.longitude(), est[0], est[1]);
        if (absDev > cfg.dMaxMeters()) {
            persistCounter++;
            absDeviationEvents++;
            boolean terminalBranchOwns = mode == Mode.AT_TERMINAL || mode == Mode.TURNING;
            boolean corridorMiss = !snap.snapped();
            if (!terminalBranchOwns && !corridorMiss
                    && persistCounter >= cfg.nPersist() && mode != Mode.RECOVERING) {
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
