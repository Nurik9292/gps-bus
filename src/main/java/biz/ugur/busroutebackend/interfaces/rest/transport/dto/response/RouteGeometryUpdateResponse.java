package biz.ugur.busroutebackend.interfaces.rest.transport.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RouteGeometryUpdateResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("details")
    private String details;

    public RouteGeometryUpdateResponse(boolean success, String message, String details) {
        this.success = success;
        this.message = message;
        this.details = details;
    }
}
