package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NearbyRoutesResponse {

    private List<NearbyRouteItem> routes;
    private int totalCount;

    @Data
    @Builder
    public static class NearbyRouteItem {
        private String id;
        private String routeNumber;
        private String routeColor;
    }
}
