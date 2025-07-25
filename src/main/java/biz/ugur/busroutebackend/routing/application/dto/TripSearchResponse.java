package biz.ugur.busroutebackend.routing.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TripSearchResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("message")
    private String message;

    @JsonProperty("search_time")
    private LocalDateTime searchTime;

    @JsonProperty("trip_options")
    private List<TripOptionDTO> tripOptions;

    @JsonProperty("summary")
    private SearchSummary summary;

    public TripSearchResponse(String status, String message, List<TripOptionDTO> tripOptions) {
        this.status = status;
        this.message = message;
        this.searchTime = LocalDateTime.now();
        this.tripOptions = tripOptions;
        this.summary = new SearchSummary(tripOptions);
    }

    @Data
    public static class SearchSummary {
        @JsonProperty("total_options")
        private int totalOptions;

        @JsonProperty("direct_routes")
        private int directRoutes;

        @JsonProperty("transfer_routes")
        private int transferRoutes;

        @JsonProperty("fastest_option_minutes")
        private Integer fastestOptionMinutes;

        @JsonProperty("option_with_fewest_transfers")
        private Integer optionWithFewestTransfers;

        public SearchSummary(List<TripOptionDTO> options) {
            this.totalOptions = options.size();
            this.directRoutes = (int) options.stream().filter(o -> "direct".equals(o.getTripType())).count();
            this.transferRoutes = totalOptions - directRoutes;
            this.fastestOptionMinutes = options.stream()
                    .mapToInt(TripOptionDTO::getTotalTravelMinutes)
                    .min().orElse(0);
            this.optionWithFewestTransfers = options.stream()
                    .mapToInt(TripOptionDTO::getTransfersCount)
                    .min().orElse(0);
        }
    }
}
