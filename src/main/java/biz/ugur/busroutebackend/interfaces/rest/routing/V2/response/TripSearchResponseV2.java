package biz.ugur.busroutebackend.interfaces.rest.routing.V2.response;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public record TripSearchResponseV2(
        String status,
        String message,
        String errorType,
        LocalDateTime searchTime,
        List<TripOptionV2DTO> tripOptions
) {

    public static TripSearchResponseV2 success(String message, List<TripOptionV2DTO> tripOptions) {
        return new TripSearchResponseV2("success", message, null, LocalDateTime.now(),
                tripOptions != null ? tripOptions : Collections.emptyList());
    }

    public static TripSearchResponseV2 error(String message, String errorType) {
        return new TripSearchResponseV2("error", message, errorType, LocalDateTime.now(), Collections.emptyList());
    }
}
