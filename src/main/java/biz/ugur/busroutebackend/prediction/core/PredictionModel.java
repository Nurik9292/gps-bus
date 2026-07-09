package biz.ugur.busroutebackend.prediction.core;

public interface PredictionModel {

    record Estimate(double s, double speedMs, String mode, double varianceS) {}

    Estimate onFix(GpsFix fix, GeometryFixture geometry);

    default void reset() {}
}
