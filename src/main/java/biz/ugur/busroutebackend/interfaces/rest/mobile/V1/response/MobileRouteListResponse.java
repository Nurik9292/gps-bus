    package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response;

    import lombok.Builder;
    import lombok.Data;

    import java.util.List;

    @Data
    @Builder
    public class MobileRouteListResponse {
        private List<MobileRouteResponse> routes;
        private Integer totalCount;
        private Long activeCount;
    }
