package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MobileCityListResponse {

    @JsonProperty("cities")
    private List<MobileCityResponse> cities;

    @JsonProperty("count")
    private Integer count;
}
