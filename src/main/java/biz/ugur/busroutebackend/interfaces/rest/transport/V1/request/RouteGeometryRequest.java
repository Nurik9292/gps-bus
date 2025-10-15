package biz.ugur.busroutebackend.interfaces.rest.transport.V1.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RouteGeometryRequest {

    @JsonProperty("forward_coordinates")
    @NotNull(message = "Forward coordinates are required")
    @Size(min = 2, message = "Route must have at least 2 coordinate points")
    private List<Double[]> forwardCoordinates;

    @JsonProperty("backward_coordinates")
    private List<Double[]> backwardCoordinates;

    @JsonProperty("update_reason")
    private String updateReason;

    @JsonProperty("updated_by")
    private String updatedBy;


    public boolean isValidCoordinates() {
        if (forwardCoordinates == null || forwardCoordinates.isEmpty()) {
            return false;
        }

        // Проверяем каждую координату
        for (Double[] coord : forwardCoordinates) {
            if (coord == null || coord.length != 2) {
                return false;
            }
            double lat = coord[0];
            double lon = coord[1];

            // Координаты должны быть в пределах Туркменистана
            if (lat < 35.0 || lat > 43.0 || lon < 52.0 || lon > 67.0) {
                return false;
            }
        }

        // Проверяем обратные координаты если есть
        if (backwardCoordinates != null) {
            for (Double[] coord : backwardCoordinates) {
                if (coord == null || coord.length != 2) {
                    return false;
                }
                double lat = coord[0];
                double lon = coord[1];

                if (lat < 35.0 || lat > 43.0 || lon < 52.0 || lon > 67.0) {
                    return false;
                }
            }
        }

        return true;
    }

    public int getForwardPointsCount() {
        return forwardCoordinates != null ? forwardCoordinates.size() : 0;
    }

    public int getBackwardPointsCount() {
        return backwardCoordinates != null ? backwardCoordinates.size() : 0;
    }
}
