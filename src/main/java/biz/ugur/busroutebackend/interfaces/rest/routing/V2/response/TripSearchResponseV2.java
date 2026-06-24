package biz.ugur.busroutebackend.interfaces.rest.routing.V2.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
public class TripSearchResponseV2 {

    @JsonProperty("status")
    private String status;

    @JsonProperty("message")
    private String message;

    @JsonProperty("error_type")
    private String errorType;

    @JsonProperty("search_time")
    private LocalDateTime searchTime;

    @JsonProperty("trip_options")
    private List<TripOptionV2DTO> tripOptions;

    public TripSearchResponseV2(String status, String message, List<TripOptionV2DTO> tripOptions) {
        this.status = status;
        this.message = message;
        this.errorType = null;
        this.searchTime = LocalDateTime.now();
        this.tripOptions = tripOptions != null ? tripOptions : Collections.emptyList();
    }

    public TripSearchResponseV2(String status, String message, String errorType) {
        this.status = status;
        this.message = message;
        this.errorType = errorType;
        this.searchTime = LocalDateTime.now();
        this.tripOptions = Collections.emptyList();
    }
}
