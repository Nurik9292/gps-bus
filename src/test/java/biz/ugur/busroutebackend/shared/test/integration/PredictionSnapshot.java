package biz.ugur.busroutebackend.shared.test.integration;

import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePredictionState;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Comparator;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "vehicleId", "licensePlate", "routeNumber", "direction",
        "predictedLatitude", "predictedLongitude",
        "fractionOnRoute", "lastGpsFraction",
        "speedKmh", "smoothedSpeedKmh",
        "inMotion"
})
public record PredictionSnapshot(
        String vehicleId,
        String licensePlate,
        String routeNumber,
        int direction,
        double predictedLatitude,
        double predictedLongitude,
        double fractionOnRoute,
        double lastGpsFraction,
        double speedKmh,
        double smoothedSpeedKmh,
        boolean inMotion
) {

    public static PredictionSnapshot from(VehiclePredictionState state) {
        return new PredictionSnapshot(
                state.getVehicleId(),
                state.getLicensePlate(),
                state.getRouteNumber(),
                state.getDirection(),
                round(state.getPredictedLatitude(), 5),
                round(state.getPredictedLongitude(), 5),
                round(state.getFractionOnRoute(), 4),
                round(state.getLastGpsFraction(), 4),
                round(state.getSpeedKmh(), 1),
                round(state.getSmoothedSpeedKmh(), 1),
                state.isInMotion()
        );
    }

    public static List<PredictionSnapshot> fromAll(List<VehiclePredictionState> states) {
        return states.stream()
                .map(PredictionSnapshot::from)
                .sorted(Comparator.comparing(PredictionSnapshot::vehicleId))
                .toList();
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
