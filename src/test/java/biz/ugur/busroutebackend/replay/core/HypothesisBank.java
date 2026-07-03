package biz.ugur.busroutebackend.replay.core;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.GpsFix;
import biz.ugur.busroutebackend.replay.RouteTopology;

import java.util.ArrayList;
import java.util.List;

public class HypothesisBank {

    private static final int RESEED_AFTER_CONSECUTIVE_MISSES = 10;

    public static final class Hypothesis {
        private final String variantId;
        private final int direction;
        private final GeometryFixture geom;
        private double x;
        private double v;
        private double score;
        private boolean seeded;
        private boolean snappedLast;
        private int missStreak;
        private double lastZ = Double.NaN;

        private Hypothesis(GeometryFixture geom) {
            this.variantId = geom.routeNumber() + "#d" + geom.direction();
            this.direction = geom.direction();
            this.geom = geom;
        }

        public String variantId() {
            return variantId;
        }

        public int direction() {
            return direction;
        }

        public GeometryFixture geom() {
            return geom;
        }

        public double x() {
            return x;
        }

        public double v() {
            return v;
        }

        public double score() {
            return score;
        }

        public boolean snappedLast() {
            return snappedLast;
        }
    }

    private final CoreConfig cfg;
    private final List<Hypothesis> hyps = new ArrayList<>();
    private RouteTopology builtFrom;
    private int leaderIdx;
    private int candidateIdx = -1;
    private int candidateStreak;
    private long switchCount;

    public HypothesisBank(CoreConfig cfg) {
        this.cfg = cfg;
    }

    public void ensureBuilt(RouteTopology topo) {
        if (topo.equals(builtFrom) && !hyps.isEmpty()) return;
        hyps.clear();
        for (GeometryFixture g : topo.allGeometries()) {
            if (hyps.size() >= cfg.maxHypotheses()) {
                System.out.printf("банк гипотез: превышен N_hyp=%d, вариант %s не подключён%n",
                        cfg.maxHypotheses(), g.routeNumber());
                break;
            }
            hyps.add(new Hypothesis(g));
        }
        builtFrom = topo;
        leaderIdx = 0;
        candidateIdx = -1;
        candidateStreak = 0;
    }

    public int size() {
        return hyps.size();
    }

    public Hypothesis leader() {
        return hyps.get(leaderIdx);
    }

    public List<Hypothesis> hypotheses() {
        return List.copyOf(hyps);
    }

    public long switchCount() {
        return switchCount;
    }

    public void onFix(GpsFix fix, double dTau) {
        for (Hypothesis h : hyps) {
            updateHypothesis(h, fix, dTau);
        }
    }

    private void updateHypothesis(Hypothesis h, GpsFix fix, double dTau) {
        double dt = Math.max(dTau, cfg.dtSec());
        if (!h.seeded) {
            var p = h.geom.projectOntoRange(fix.latitude(), fix.longitude(), 0, h.geom.totalMeters(), 0);
            h.x = p.s();
            h.v = Math.max(0, fix.speedKmh() / 3.6);
            h.seeded = true;
            h.missStreak = 0;
            h.lastZ = Double.NaN;
            h.snappedLast = p.distMeters() <= cfg.dSnapMeters();
            if (h.snappedLast) h.lastZ = p.s();
            return;
        }
        h.x = Math.max(0, Math.min(h.x + h.v * dTau, h.geom.totalMeters()));
        double window = cfg.w0Meters() + cfg.kWindowPerSpeed() * Math.max(h.v, 1.0) * dt;
        var p = h.geom.projectOntoRange(fix.latitude(), fix.longitude(),
                h.x - window, h.x + window, h.x);
        double w;
        if (p.distMeters() <= cfg.dSnapMeters()) {
            double residual = p.s() - h.x;
            h.x += 0.5 * residual;
            h.v = Math.max(0, Math.min(h.v + 0.2 * residual / dt, cfg.vMaxMs()));
            boolean progress = !Double.isNaN(h.lastZ)
                    && p.s() - h.lastZ > 0.5
                    && p.s() - h.lastZ <= cfg.vMaxMs() * dt + 15.0;
            double norm = p.distMeters() / cfg.dSnapMeters();
            w = -(norm * norm) + (progress ? cfg.scoreProgressBonus() : 0.0);
            h.lastZ = p.s();
            h.snappedLast = true;
            h.missStreak = 0;
        } else {
            w = -1.0 - cfg.scoreRejectPenalty();
            h.snappedLast = false;
            h.missStreak++;
            if (h.missStreak >= RESEED_AFTER_CONSECUTIVE_MISSES) {
                h.seeded = false;
            }
        }
        h.score = cfg.scoreLambda() * h.score + (1 - cfg.scoreLambda()) * w;
    }

    public boolean noneSnapped() {
        for (Hypothesis h : hyps) {
            if (h.snappedLast) return false;
        }
        return !hyps.isEmpty();
    }

    public Hypothesis pollConfirmedSwitch() {
        if (hyps.size() < 2) return null;
        int best = leaderIdx;
        for (int i = 0; i < hyps.size(); i++) {
            if (hyps.get(i).score > hyps.get(best).score) best = i;
        }
        if (best == leaderIdx
                || hyps.get(best).score - hyps.get(leaderIdx).score < cfg.sSwitch()) {
            candidateIdx = -1;
            candidateStreak = 0;
            return null;
        }
        if (candidateIdx != best) {
            candidateIdx = best;
            candidateStreak = 1;
        } else {
            candidateStreak++;
        }
        if (candidateStreak < cfg.hSwitch()) return null;
        return hyps.get(candidateIdx);
    }

    public void commitSwitch() {
        leaderIdx = candidateIdx;
        candidateIdx = -1;
        candidateStreak = 0;
        switchCount++;
        resetScores();
    }

    public void alignLeaderTo(int direction) {
        for (int i = 0; i < hyps.size(); i++) {
            if (hyps.get(i).direction == direction) {
                leaderIdx = i;
                break;
            }
        }
        candidateIdx = -1;
        candidateStreak = 0;
        resetScores();
    }

    public void reseedAll() {
        candidateIdx = -1;
        candidateStreak = 0;
        resetScores();
    }

    private void resetScores() {
        for (Hypothesis h : hyps) {
            h.score = 0;
            h.seeded = false;
            h.missStreak = 0;
            h.lastZ = Double.NaN;
        }
    }
}
