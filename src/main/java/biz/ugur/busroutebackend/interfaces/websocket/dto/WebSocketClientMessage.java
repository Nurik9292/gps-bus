package biz.ugur.busroutebackend.interfaces.websocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebSocketClientMessage {

    @JsonProperty("type")
    private String type;

    @JsonProperty("routes")
    private List<String> routes;

    @JsonProperty("cityId")
    private String cityId;

    @JsonProperty("bounds")
    private List<Double> bounds;

    public boolean hasRoutes() {
        return routes != null && !routes.isEmpty();
    }

    public boolean hasValidBounds() {
        return bounds != null && bounds.size() == 4;
    }
}
