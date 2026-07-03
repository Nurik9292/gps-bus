package biz.ugur.busroutebackend.replay;

import biz.ugur.busroutebackend.replay.metrics.ConsistencyMetrics;
import biz.ugur.busroutebackend.replay.metrics.PositionMetrics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public final class ReplayHarness {

    public record Sample(double tSec, double sEst, double sTrue, String mode, double varianceS) {}

    public record Result(List<Sample> samples, PositionMetrics position, ConsistencyMetrics consistency,
                         String outputSha256) {}

    private ReplayHarness() {}

    public static Result run(PredictionModel model, GeometryFixture geometry,
                             List<GpsFix> fixes, List<double[]> truthOrNull,
                             double teleportStepMeters) {
        model.reset();
        PositionMetrics pos = new PositionMetrics(teleportStepMeters);
        ConsistencyMetrics cons = new ConsistencyMetrics();
        List<Sample> samples = new ArrayList<>(fixes.size());

        Double prevS = null;
        for (int i = 0; i < fixes.size(); i++) {
            GpsFix fix = fixes.get(i);
            PredictionModel.Estimate est = model.onFix(fix, geometry);
            Double sTrue = truthOrNull != null && i < truthOrNull.size() ? truthOrNull.get(i)[1] : null;
            double tSec = truthOrNull != null && i < truthOrNull.size() ? truthOrNull.get(i)[0] : i;

            pos.add(est.s(), sTrue, est.mode());
            if (sTrue != null) {
                cons.addNees(est.s() - sTrue, est.varianceS());
            }
            if (prevS != null) {
                cons.addNis(est.s() - prevS, Math.max(est.varianceS(), 1e-9));
            }
            prevS = est.s();
            samples.add(new Sample(tSec, est.s(), sTrue != null ? sTrue : Double.NaN, est.mode(), est.varianceS()));
        }
        return new Result(samples, pos, cons, sha256(samples));
    }

    private static String sha256(List<Sample> samples) {
        try {
            StringBuilder sb = new StringBuilder();
            for (Sample s : samples) {
                sb.append(String.format(java.util.Locale.ROOT,
                        "%.3f|%.3f|%.3f|%s|%.6f%n", s.tSec(), s.sEst(), s.sTrue(), s.mode(), s.varianceS()));
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : h) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
