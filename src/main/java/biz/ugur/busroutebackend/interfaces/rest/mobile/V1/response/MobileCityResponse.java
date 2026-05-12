package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response;

import biz.ugur.busroutebackend.client.application.dto.city.MobileCityResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MobileCityResponse {

    private String id;
    private String name;
    private String nameTm;
    private Integer displayOrder;
    private Double latitude;
    private Double longitude;

    public static MobileCityResponse from(MobileCityResult result) {
        return MobileCityResponse.builder()
                .id(result.id())
                .name(result.name())
                .nameTm(result.nameTm())
                .displayOrder(result.displayOrder())
                .latitude(result.latitude())
                .longitude(result.longitude())
                .build();
    }
}
