    package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response;

    import lombok.Builder;
    import lombok.Data;

    import java.util.List;

    @Data
    @Builder
    public class MobileStopListResponse {
        private List<MobileStopResponse> stops;
        private Integer totalCount;
        private Long activeCount;
    }
